package nz.co.ksktech.fundlens.agent;

import static io.restassured.RestAssured.given;
import static nz.co.ksktech.fundlens.testsupport.LlmStubs.COMPLIANCE_MARKER;
import static nz.co.ksktech.fundlens.testsupport.LlmStubs.RESEARCH_MARKER;
import static nz.co.ksktech.fundlens.testsupport.LlmStubs.WRITER_MARKER;
import static nz.co.ksktech.fundlens.testsupport.LlmStubs.complianceJson;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.util.ArrayList;
import java.util.List;
import nz.co.ksktech.fundlens.testsupport.LlmStubs;
import nz.co.ksktech.fundlens.testsupport.WireMockTestResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * SSE streaming of the explain pipeline: live stage events, the approved answer as chunks, a
 * complete event — and never any draft content on the wire before/without a PASS verdict.
 */
@QuarkusTest
@QuarkusTestResource(WireMockTestResource.class)
class ExplainStreamTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final String FINDINGS =
      "1. Westpac Active Growth returned -4.1% for the year to 31 March 2026 "
          + "[Westpac fund update, Mar 2026].";
  private static final String GOOD_DRAFT =
      "The Westpac Active Growth fund returned -4.1% for the year [fund update, Mar 2026].\n\n"
          + Disclaimers.GENERAL_INFO;

  @BeforeEach
  void resetStubs() {
    WireMockTestResource.resetToDefaults();
    LlmStubs.stubAgent(RESEARCH_MARKER, FINDINGS);
    LlmStubs.stubAgent(WRITER_MARKER, GOOD_DRAFT);
  }

  @Test
  void streamsStagesThenApprovedAnswerThenComplete() throws Exception {
    LlmStubs.stubAgent(COMPLIANCE_MARKER, complianceJson("PASS"));

    List<JsonNode> events = streamExplain("Why is the Westpac fund down? (stream)");

    List<String> types = events.stream().map(e -> e.get("type").asText()).toList();
    assertEquals("stage", types.getFirst());
    assertEquals("complete", types.getLast());
    assertTrue(types.contains("answer-chunk"));

    List<String> stageLog =
        events.stream()
            .filter(e -> "stage".equals(e.get("type").asText()))
            .map(e -> e.get("stage").asText() + ":" + e.get("status").asText())
            .toList();
    assertEquals(
        List.of(
            "research:started", "research:completed",
            "draft:started", "draft:completed",
            "compliance:started", "compliance:completed"),
        stageLog);

    String reassembled =
        events.stream()
            .filter(e -> "answer-chunk".equals(e.get("type").asText()))
            .map(e -> e.get("chunk").asText())
            .reduce("", String::concat);
    assertEquals(GOOD_DRAFT, reassembled);

    JsonNode complete = events.getLast();
    assertEquals("PASS", complete.get("result").get("complianceVerdict").asText());
    String auditId = complete.get("result").get("auditId").asText();
    assertNotNull(auditId);
    given().when().get("/api/v1/audit/{id}", auditId).then().statusCode(200);
  }

  @Test
  void blockedAnswerNeverLeaksDraftContentOnTheStream() throws Exception {
    LlmStubs.stubAgent(COMPLIANCE_MARKER, complianceJson("REVISE", "advice phrasing"));

    List<JsonNode> events = streamExplain("Should I switch funds? (stream)");

    // two draft/compliance rounds happened, then BLOCK
    JsonNode complete = events.getLast();
    assertEquals("complete", complete.get("type").asText());
    assertEquals("BLOCK", complete.get("result").get("complianceVerdict").asText());

    // the fallback is streamed; the draft (which contains -4.1%) never is
    String streamed = events.toString();
    assertFalse(streamed.contains("-4.1%"), "draft content must not reach the stream");
    assertTrue(
        complete.get("result").get("answer").asText().contains("unable to provide an answer"));
  }

  @Test
  void upstreamFailureArrivesAsInBandErrorEvent() throws Exception {
    WireMockTestResource.server()
        .stubFor(
            LlmStubs.chatFor(RESEARCH_MARKER)
                .atPriority(1)
                .willReturn(
                    com.github.tomakehurst.wiremock.client.WireMock.aResponse()
                        .withFixedDelay(8000)
                        .withHeader("Content-Type", "application/json")
                        .withBody(LlmStubs.chatBody("too late"))));

    List<JsonNode> events = streamExplain("Slow question (stream)");

    JsonNode last = events.getLast();
    assertEquals("error", last.get("type").asText());
    assertEquals(503, last.get("errorStatus").asInt());
    String auditId = last.get("auditId").asText();
    given()
        .when()
        .get("/api/v1/audit/{id}", auditId)
        .then()
        .statusCode(200)
        .body("status", org.hamcrest.Matchers.equalTo("UPSTREAM_ERROR"));
  }

  private static List<JsonNode> streamExplain(String question) throws Exception {
    String raw =
        given()
            .contentType(ContentType.JSON)
            .body("{\"question\":\"" + question + "\",\"audience\":\"INVESTOR\"}")
            .when()
            .post("/api/v1/explain/stream")
            .then()
            .statusCode(200)
            .header("Content-Type", org.hamcrest.Matchers.containsString("text/event-stream"))
            .extract()
            .asString();

    List<JsonNode> events = new ArrayList<>();
    for (String line : raw.split("\n")) {
      if (line.startsWith("data:")) {
        events.add(MAPPER.readTree(line.substring("data:".length()).trim()));
      }
    }
    assertFalse(events.isEmpty(), "expected SSE data frames, got: " + raw);
    return events;
  }
}
