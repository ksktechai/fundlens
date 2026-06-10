package nz.co.ksktech.fundlens.agent;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static io.restassured.RestAssured.given;
import static nz.co.ksktech.fundlens.testsupport.LlmStubs.COMPLIANCE_MARKER;
import static nz.co.ksktech.fundlens.testsupport.LlmStubs.RESEARCH_MARKER;
import static nz.co.ksktech.fundlens.testsupport.LlmStubs.WRITER_MARKER;
import static nz.co.ksktech.fundlens.testsupport.LlmStubs.chatBody;
import static nz.co.ksktech.fundlens.testsupport.LlmStubs.chatFor;
import static nz.co.ksktech.fundlens.testsupport.LlmStubs.complianceJson;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import nz.co.ksktech.fundlens.testsupport.LlmStubs;
import nz.co.ksktech.fundlens.testsupport.WireMockTestResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * End-to-end WireMock'd LLM pipeline tests: PASS, REVISE→PASS, REVISE→REVISE→BLOCK, upstream
 * timeout, malformed compliance output. Every scenario must leave an audit record.
 */
@QuarkusTest
@QuarkusTestResource(WireMockTestResource.class)
class ExplainFlowTest {

  private static final String FINDINGS =
      "1. Westpac Active Growth returned -4.1% for the year to 31 March 2026 [Westpac fund update, Mar 2026].";
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
  void happyPathPassesOnFirstDraft() {
    LlmStubs.stubAgent(COMPLIANCE_MARKER, complianceJson("PASS"));

    String auditId =
        given()
            .contentType(ContentType.JSON)
            .body(
                "{\"question\":\"Why is the Westpac Active Growth fund down this year?\",\"audience\":\"INVESTOR\"}")
            .when()
            .post("/api/v1/explain")
            .then()
            .statusCode(200)
            .body("answer", containsString("-4.1%"))
            .body("answer", containsString("general information only"))
            .body("complianceVerdict", equalTo("PASS"))
            .body("auditId", notNullValue())
            .extract()
            .path("auditId");

    given()
        .when()
        .get("/api/v1/audit/{id}", auditId)
        .then()
        .statusCode(200)
        .body("status", equalTo("ANSWERED"))
        .body("question", containsString("Westpac Active Growth"))
        .body("researchFindings", containsString("-4.1%"))
        .body("drafts", hasSize(1))
        .body("complianceResults", hasSize(1))
        .body("complianceResults[0].verdict", equalTo("PASS"))
        .body("finalAnswer", containsString("-4.1%"))
        .body("modelName", notNullValue());
  }

  @Test
  void reviseThenPassRetriesWriterOnce() {
    var server = WireMockTestResource.server();
    server.stubFor(
        chatFor(COMPLIANCE_MARKER)
            .inScenario("compliance")
            .whenScenarioStateIs(STARTED)
            .willReturn(
                okJson(chatBody(complianceJson("REVISE", "Missing citation for the -4.1% figure"))))
            .willSetStateTo("second-review"));
    server.stubFor(
        chatFor(COMPLIANCE_MARKER)
            .inScenario("compliance")
            .whenScenarioStateIs("second-review")
            .willReturn(okJson(chatBody(complianceJson("PASS")))));

    String auditId =
        given()
            .contentType(ContentType.JSON)
            .body(
                "{\"question\":\"Explain the Westpac fund's recent performance\",\"audience\":\"ADVISER\"}")
            .when()
            .post("/api/v1/explain")
            .then()
            .statusCode(200)
            .body("complianceVerdict", equalTo("PASS"))
            .extract()
            .path("auditId");

    given()
        .when()
        .get("/api/v1/audit/{id}", auditId)
        .then()
        .statusCode(200)
        .body("status", equalTo("ANSWERED"))
        .body("drafts", hasSize(2))
        .body("complianceResults", hasSize(2))
        .body("complianceResults[0].verdict", equalTo("REVISE"))
        .body("complianceResults[0].issues[0]", containsString("Missing citation"))
        .body("complianceResults[1].verdict", equalTo("PASS"));
  }

  @Test
  void doubleReviseBlocksWithFallbackAnswer() {
    LlmStubs.stubAgent(
        COMPLIANCE_MARKER, complianceJson("REVISE", "Still contains advice phrasing"));

    String auditId =
        given()
            .contentType(ContentType.JSON)
            .body(
                "{\"question\":\"Should I switch to Simplicity Growth?\",\"audience\":\"INVESTOR\"}")
            .when()
            .post("/api/v1/explain")
            .then()
            .statusCode(200)
            .body("complianceVerdict", equalTo("BLOCK"))
            .body("answer", containsString("unable to provide an answer"))
            .body("answer", containsString("general information only"))
            .extract()
            .path("auditId");

    given()
        .when()
        .get("/api/v1/audit/{id}", auditId)
        .then()
        .statusCode(200)
        .body("status", equalTo("BLOCKED"))
        .body("drafts", hasSize(2))
        .body("complianceResults", hasSize(2))
        .body("finalAnswer", containsString("unable to provide an answer"));
  }

  @Test
  void ollamaTimeoutReturns503ProblemDetailAndStillAudits() {
    WireMockTestResource.server()
        .stubFor(
            chatFor(RESEARCH_MARKER)
                .atPriority(1)
                .willReturn(
                    aResponse()
                        .withFixedDelay(8000)
                        .withBody(chatBody("too late"))
                        .withHeader("Content-Type", "application/json")));

    String auditId =
        given()
            .contentType(ContentType.JSON)
            .body(
                "{\"question\":\"Compare fees across all growth funds\",\"audience\":\"ADVISER\"}")
            .when()
            .post("/api/v1/explain")
            .then()
            .statusCode(503)
            .body("title", equalTo("LLM backend unavailable"))
            .body("status", equalTo(503))
            .body("auditId", notNullValue())
            .extract()
            .path("auditId");

    given()
        .when()
        .get("/api/v1/audit/{id}", auditId)
        .then()
        .statusCode(200)
        .body("status", equalTo("UPSTREAM_ERROR"))
        .body("question", containsString("Compare fees"));
  }

  @Test
  void malformedComplianceOutputBlocksInsteadOfThrowing() {
    LlmStubs.stubAgent(COMPLIANCE_MARKER, "sorry, I cannot produce JSON today");

    String auditId =
        given()
            .contentType(ContentType.JSON)
            .body("{\"question\":\"What are the Milford fund fees?\",\"audience\":\"INVESTOR\"}")
            .when()
            .post("/api/v1/explain")
            .then()
            .statusCode(200)
            .body("complianceVerdict", equalTo("BLOCK"))
            .body("answer", containsString("unable to provide an answer"))
            .extract()
            .path("auditId");

    given()
        .when()
        .get("/api/v1/audit/{id}", auditId)
        .then()
        .statusCode(200)
        .body("status", equalTo("BLOCKED"));
  }
}
