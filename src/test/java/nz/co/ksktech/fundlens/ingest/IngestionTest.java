package nz.co.ksktech.fundlens.ingest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import nz.co.ksktech.fundlens.domain.Fund;
import nz.co.ksktech.fundlens.domain.FundDocument;
import nz.co.ksktech.fundlens.testsupport.WireMockTestResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(WireMockTestResource.class)
class IngestionTest {

  private static final String SAMPLE_DOC =
      """
            Milford Active Growth Fund - Quarterly Fund Update, period ended 31 March 2026.
            The fund returned 2.8% after fees and tax for the year. The total annual fund
            charge is 1.15%. The five-year average annual return is 9.1% after fees and tax.
            """;

  @Inject EmbeddingStore<TextSegment> embeddingStore;

  @BeforeEach
  void resetStubs() {
    WireMockTestResource.resetToDefaults();
  }

  @Test
  void multipartUploadIngestsChunksWithMetadataAndRecordsDocument() {
    Long fundId = milfordId();

    Integer documentId =
        given()
            .multiPart("providerId", "Milford")
            .multiPart("fundId", String.valueOf(fundId))
            .multiPart("docType", "FUND_UPDATE")
            .multiPart("periodEnd", "2026-03-31")
            .multiPart("file", "milford-update.txt", SAMPLE_DOC.getBytes(), "text/plain")
            .when()
            .post("/api/v1/ingest")
            .then()
            .statusCode(200)
            .body("documentId", notNullValue())
            .body("chunkCount", greaterThan(0))
            .body("title", equalTo("milford-update.txt"))
            .extract()
            .path("documentId");

    FundDocument document = findDocument(documentId.longValue());
    assertEquals("FUND_UPDATE", document.docType);
    assertEquals(fundId, document.fundId);
    assertEquals("UPLOAD", document.source);
    assertTrue(document.chunkCount > 0);

    EmbeddingSearchResult<TextSegment> hits =
        embeddingStore.search(
            EmbeddingSearchRequest.builder()
                .queryEmbedding(queryVector())
                .filter(
                    MetadataFilterBuilder.metadataKey("fund_id").isEqualTo(String.valueOf(fundId)))
                .maxResults(10)
                .build());
    assertFalse(
        hits.matches().isEmpty(), "expected chunks in the embedding store for fund " + fundId);
    TextSegment segment = hits.matches().getFirst().embedded();
    assertEquals("milford-update.txt", segment.metadata().getString("title"));
    assertEquals("FUND_UPDATE", segment.metadata().getString("doc_type"));
    assertEquals("2026-03-31", segment.metadata().getString("period_end"));
    assertEquals("Milford", segment.metadata().getString("provider"));
  }

  @Test
  void rejectsInvalidDocType() {
    given()
        .multiPart("providerId", "Milford")
        .multiPart("fundId", "1")
        .multiPart("docType", "NOT_A_TYPE")
        .multiPart("file", "x.txt", "hello".getBytes(), "text/plain")
        .when()
        .post("/api/v1/ingest")
        .then()
        .statusCode(400)
        .body("title", equalTo("Validation failed"));
  }

  @Transactional
  Long milfordId() {
    return Fund.<Fund>find("name", "Milford Active Growth").singleResult().id;
  }

  @Transactional
  FundDocument findDocument(Long id) {
    return FundDocument.findById(id);
  }

  private static Embedding queryVector() {
    float[] vector = new float[WireMockTestResource.EMBEDDING_DIMENSION];
    java.util.Arrays.fill(vector, 0.1f);
    return Embedding.from(vector);
  }
}
