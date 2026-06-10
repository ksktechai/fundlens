package nz.co.ksktech.fundlens.disclose;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.faulttolerance.api.CircuitBreakerMaintenance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import nz.co.ksktech.fundlens.domain.Fund;
import nz.co.ksktech.fundlens.domain.FundDocument;
import nz.co.ksktech.fundlens.domain.FundMetrics;
import nz.co.ksktech.fundlens.domain.SyncRun;
import nz.co.ksktech.fundlens.testsupport.WireMockTestResource;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * The five offline WireMock scenarios for the Disclose Register sync: (a) 200 upserts metrics +
 * ETag, (b) 304 skips, (c) 302 document redirect fetched manually without the subscription key, (d)
 * failures retry, circuit-break and don't abort other funds, (e) auth headers on every call.
 */
@QuarkusTest
@QuarkusTestResource(WireMockTestResource.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DiscloseSyncTest {

  private static final String FUND = "FND123";
  private static final String ETAG = "W/\"v1\"";

  @Inject DiscloseSyncService syncService;

  @Inject DiscloseService discloseService;

  @Inject EmbeddingStore<TextSegment> embeddingStore;

  @Inject CircuitBreakerMaintenance circuitBreakers;

  @BeforeEach
  void resetStubs() {
    WireMockTestResource.resetToDefaults();
    circuitBreakers.resetAll();
  }

  @Test
  @Order(1)
  void firstSyncUpsertsMetricsStoresEtagAndIngestsDocumentWithoutLeakingKey() throws IOException {
    var server = WireMockTestResource.server();
    String fileUrl = WireMockTestResource.baseUrl() + "/files/" + FUND + ".pdf";
    server.stubFor(
        get(urlPathEqualTo("/disclose/fund/" + FUND))
            .willReturn(okJson(fundJson(FUND, "Disclose Demo Growth")).withHeader("ETag", ETAG)));
    server.stubFor(
        get(urlEqualTo("/disclose/fund/" + FUND + "/fund-update-document"))
            .willReturn(aResponse().withStatus(302).withHeader("Location", fileUrl)));
    server.stubFor(
        get(urlEqualTo("/files/" + FUND + ".pdf"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/pdf")
                    .withBody(
                        tinyPdf("Disclose Demo Growth fund update. Return -4.1% for the year."))));

    DiscloseSyncService.SyncSummary summary = syncService.run(List.of(FUND), "MANUAL");

    assertEquals("SUCCESS", summary.status());
    assertEquals(1, summary.outcomes().size());
    assertEquals("UPDATED", summary.outcomes().getFirst().status());
    assertTrue(summary.outcomes().getFirst().documentIngested());

    // (a) metrics upserted with the right fee/return values, ETag stored
    Fund fund = loadFund(FUND);
    assertEquals("Disclose Demo Growth", fund.name);
    assertEquals(ETAG, fund.discloseEtag);
    FundMetrics metrics = loadLatestMetrics(fund.id);
    assertEquals(0, new BigDecimal("1.05").compareTo(metrics.totalAnnualFundCharge));
    assertEquals(0, new BigDecimal("0.95").compareTo(metrics.managersBasicFee));
    assertEquals(0, new BigDecimal("-4.10").compareTo(metrics.pastYearReturnNet));
    assertEquals(0, new BigDecimal("6.20").compareTo(metrics.avgFiveYearReturnNet));
    assertEquals(98000, metrics.numberOfInvestors.intValue());
    assertNotNull(metrics.investmentMix);

    // (c) the redirect was not auto-followed: exactly one plain download,
    // and the subscription key never left the gateway
    server.verify(1, getRequestedFor(urlEqualTo("/files/" + FUND + ".pdf")));
    server.verify(
        getRequestedFor(urlEqualTo("/files/" + FUND + ".pdf"))
            .withoutHeader("Ocp-Apim-Subscription-Key")
            .withoutHeader("x-organisation"));

    // chunks landed in the embedding store with the fund's metadata
    var hits =
        embeddingStore.search(
            EmbeddingSearchRequest.builder()
                .queryEmbedding(queryVector())
                .filter(
                    MetadataFilterBuilder.metadataKey("fund_id").isEqualTo(String.valueOf(fund.id)))
                .maxResults(10)
                .build());
    assertFalse(hits.matches().isEmpty(), "expected ingested PDF chunks for fund " + fund.id);
    assertEquals(
        "FUND_UPDATE", hits.matches().getFirst().embedded().metadata().getString("doc_type"));

    // (e) every gateway request carried both auth headers
    var gatewayRequests =
        server.getAllServeEvents().stream()
            .filter(e -> e.getRequest().getUrl().startsWith("/disclose/"))
            .toList();
    assertFalse(gatewayRequests.isEmpty());
    for (var event : gatewayRequests) {
      assertEquals(
          "test-key",
          event.getRequest().getHeader("Ocp-Apim-Subscription-Key"),
          "missing subscription key on " + event.getRequest().getUrl());
      assertEquals(
          "test-org",
          event.getRequest().getHeader("x-organisation"),
          "missing x-organisation on " + event.getRequest().getUrl());
    }
  }

  @Test
  @Order(2)
  void secondSyncWithStoredEtagGets304AndSkips() {
    var server = WireMockTestResource.server();
    server.stubFor(
        get(urlPathEqualTo("/disclose/fund/" + FUND))
            .withHeader(
                "If-None-Match", com.github.tomakehurst.wiremock.client.WireMock.equalTo(ETAG))
            .atPriority(1)
            .willReturn(aResponse().withStatus(304)));
    server.stubFor(
        get(urlPathEqualTo("/disclose/fund/" + FUND))
            .atPriority(5)
            .willReturn(
                okJson(fundJson(FUND, "Should Not Be Used")).withHeader("ETag", "W/\"v2\"")));

    long documentsBefore = countDocuments(loadFund(FUND).id);

    DiscloseSyncService.SyncSummary summary = syncService.run(List.of(FUND), "MANUAL");

    assertEquals("SUCCESS", summary.status());
    assertEquals("NOT_MODIFIED", summary.outcomes().getFirst().status());
    assertFalse(summary.outcomes().getFirst().documentIngested());
    assertEquals("Disclose Demo Growth", loadFund(FUND).name, "fund must not be updated on 304");
    assertEquals(documentsBefore, countDocuments(loadFund(FUND).id), "no re-ingestion on 304");
    server.verify(
        0, getRequestedFor(urlEqualTo("/disclose/fund/" + FUND + "/fund-update-document")));
  }

  @Test
  @Order(3)
  void gatewayFailuresRetryAndDoNotAbortOtherFunds() throws IOException {
    var server = WireMockTestResource.server();
    String goodFund = "FND200";
    String fileUrl = WireMockTestResource.baseUrl() + "/files/" + goodFund + ".pdf";
    server.stubFor(
        get(urlPathEqualTo("/disclose/fund/BAD500"))
            .willReturn(aResponse().withStatus(500).withBody("gateway exploded")));
    server.stubFor(
        get(urlPathEqualTo("/disclose/fund/" + goodFund))
            .willReturn(
                okJson(fundJson(goodFund, "Disclose Demo Balanced"))
                    .withHeader("ETag", "W/\"b1\"")));
    server.stubFor(
        get(urlEqualTo("/disclose/fund/" + goodFund + "/fund-update-document"))
            .willReturn(aResponse().withStatus(302).withHeader("Location", fileUrl)));
    server.stubFor(
        get(urlEqualTo("/files/" + goodFund + ".pdf"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/pdf")
                    .withBody(tinyPdf("Disclose Demo Balanced fund update."))));

    DiscloseSyncService.SyncSummary summary =
        syncService.run(List.of("BAD500", goodFund), "MANUAL");

    assertEquals("PARTIAL", summary.status());
    assertEquals("FAILED", summary.outcomes().get(0).status());
    assertEquals("UPDATED", summary.outcomes().get(1).status());

    // @Retry(maxRetries = 2): original call + 2 retries
    server.verify(3, getRequestedFor(urlPathEqualTo("/disclose/fund/BAD500")));

    // the other fund still synced
    assertEquals("Disclose Demo Balanced", loadFund(goodFund).name);

    // the SyncRun row recorded the failure
    SyncRun run = latestRun();
    assertEquals("PARTIAL", run.status);
    assertTrue(run.outcomes.contains("\"BAD500\""));
    assertTrue(run.outcomes.contains("FAILED"));
    assertNotNull(run.startedAt);
    assertNotNull(run.finishedAt);
  }

  @Test
  @Order(4)
  void repeatedGatewayFailuresOpenTheCircuitBreaker() {
    var server = WireMockTestResource.server();
    server.stubFor(
        get(urlPathEqualTo("/disclose/fund/BAD500")).willReturn(aResponse().withStatus(500)));

    boolean circuitOpened = false;
    for (int i = 0; i < 15 && !circuitOpened; i++) {
      try {
        discloseService.fetchFund("BAD500", null);
        fail("expected a failure from the stubbed 500");
      } catch (CircuitBreakerOpenException e) {
        circuitOpened = true;
      } catch (DiscloseApiException expected) {
        // retried, then surfaced — keep hammering
      }
    }
    assertTrue(circuitOpened, "circuit breaker should open after repeated 500s");
  }

  @Test
  @Order(5)
  void manualSyncEndpointRunsConfiguredFundsAndReturnsSummary() {
    var server = WireMockTestResource.server();
    // FND10001 comes from %test.disclose.sync.fund-numbers
    server.stubFor(
        get(urlPathEqualTo("/disclose/fund/FND10001"))
            .willReturn(
                okJson(fundJson("FND10001", "Disclose Demo Conservative"))
                    .withHeader("ETag", "W/\"c1\"")));
    server.stubFor(
        get(urlEqualTo("/disclose/fund/FND10001/fund-update-document"))
            .willReturn(aResponse().withStatus(404)));

    given()
        .when()
        .post("/api/v1/sync")
        .then()
        .statusCode(200)
        .body("runId", notNullValue())
        .body("status", equalTo("SUCCESS"))
        .body("outcomes", hasSize(1))
        .body("outcomes[0].fundNumber", equalTo("FND10001"))
        .body("outcomes[0].status", equalTo("UPDATED"))
        .body("outcomes[0].documentIngested", equalTo(false));
  }

  // --- helpers ---------------------------------------------------------

  @Transactional
  Fund loadFund(String discloseNumber) {
    return Fund.findByDiscloseFundNumber(discloseNumber).orElseThrow();
  }

  @Transactional
  FundMetrics loadLatestMetrics(Long fundId) {
    return FundMetrics.findLatestForFund(fundId).orElseThrow();
  }

  @Transactional
  long countDocuments(Long fundId) {
    return FundDocument.count("fundId", fundId);
  }

  @Transactional
  SyncRun latestRun() {
    return SyncRun.<SyncRun>find("order by id desc").firstResult();
  }

  private static String fundJson(String fundNumber, String name) {
    return """
                {
                  "fundNumber": "%s",
                  "fundName": "%s",
                  "offerNumber": "OFR12345",
                  "fundStatus": "open",
                  "reportingPeriodEndDate": "2026-03-31",
                  "fundClassification": "Growth",
                  "riskIndicator": 4,
                  "fundDescription": "An actively managed growth fund investing mainly in equities.",
                  "totalFundValueAmount": 2150000000.00,
                  "numberOfInvestors": 98000,
                  "pastYearReturnNet": -4.10,
                  "averageFiveYearReturnNet": 6.20,
                  "marketIndexPastYearReturn": -3.50,
                  "totalAnnualFundCharge": 1.05,
                  "managersBasicFee": 0.95,
                  "performanceBasedFees": 0.00,
                  "contributionFeesPercentage": 0.00,
                  "withdrawalFeesPercentage": 0.00,
                  "investmentMix": [
                    {"investmentType": "International equities", "targetPercentageOrRange": "55%%", "actualPercentage": 54.2}
                  ],
                  "topTenInvestments": [
                    {"assetName": "Microsoft Corp", "assetProportion": 2.10, "assetType": "International equity", "assetCountry": "US"}
                  ]
                }"""
        .formatted(fundNumber, name);
  }

  private static byte[] tinyPdf(String text) throws IOException {
    try (PDDocument document = new PDDocument()) {
      PDPage page = new PDPage();
      document.addPage(page);
      try (PDPageContentStream content = new PDPageContentStream(document, page)) {
        content.beginText();
        content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
        content.newLineAtOffset(50, 700);
        content.showText(text);
        content.endText();
      }
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      document.save(out);
      return out.toByteArray();
    }
  }

  private static Embedding queryVector() {
    float[] vector = new float[WireMockTestResource.EMBEDDING_DIMENSION];
    java.util.Arrays.fill(vector, 0.1f);
    return Embedding.from(vector);
  }
}
