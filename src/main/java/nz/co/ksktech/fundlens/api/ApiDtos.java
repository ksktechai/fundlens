package nz.co.ksktech.fundlens.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import nz.co.ksktech.fundlens.agent.Audience;
import nz.co.ksktech.fundlens.agent.ExplainOrchestrator.Citation;

/** Request/response records for the public API. */
public final class ApiDtos {

  private ApiDtos() {}

  public record ExplainRequest(
      @NotBlank @Size(max = 2000) String question, List<Long> fundIds, Audience audience) {

    public Audience audienceOrDefault() {
      return audience == null ? Audience.INVESTOR : audience;
    }
  }

  public record ExplainResponse(
      String answer, List<Citation> citations, String complianceVerdict, UUID auditId) {}

  public record FundMetricsResponse(
      LocalDate periodEnd,
      BigDecimal totalAnnualFundCharge,
      BigDecimal managersBasicFee,
      BigDecimal performanceBasedFees,
      BigDecimal contributionFee,
      BigDecimal withdrawalFee,
      BigDecimal pastYearReturnNet,
      BigDecimal avgFiveYearReturnNet,
      BigDecimal marketIndexPastYearReturn,
      BigDecimal totalFundValue,
      Integer numberOfInvestors,
      JsonNode investmentMix,
      JsonNode topTenInvestments) {}

  public record FundResponse(
      Long id,
      String name,
      String provider,
      String status,
      String classification,
      Integer riskIndicator,
      String description,
      String discloseFundNumber,
      FundMetricsResponse latestMetrics) {}

  public record IngestResponse(Long documentId, String title, int chunkCount) {}

  public record AuditResponse(
      UUID id,
      Instant createdAt,
      String question,
      String audience,
      JsonNode fundIds,
      JsonNode retrievedChunks,
      String researchFindings,
      JsonNode drafts,
      JsonNode complianceResults,
      String finalAnswer,
      String status,
      String modelName,
      long latencyMs) {}

  public record SyncRunResponse(
      Long runId, Instant startedAt, Instant finishedAt, String status, JsonNode outcomes) {}

  /**
   * One SSE frame of POST /api/v1/explain/stream. type is one of: stage (pipeline progress),
   * answer-chunk (a piece of the compliance-approved answer), complete (final result), error.
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record ExplainStreamEvent(
      String type,
      String stage,
      String status,
      String detail,
      String chunk,
      ExplainResponse result,
      Integer errorStatus,
      UUID auditId) {

    public static ExplainStreamEvent stage(String stage, String status, String detail) {
      return new ExplainStreamEvent("stage", stage, status, detail, null, null, null, null);
    }

    public static ExplainStreamEvent answerChunk(String chunk) {
      return new ExplainStreamEvent("answer-chunk", null, null, null, chunk, null, null, null);
    }

    public static ExplainStreamEvent complete(ExplainResponse result) {
      return new ExplainStreamEvent("complete", null, null, null, null, result, null, null);
    }

    public static ExplainStreamEvent error(int status, String detail, UUID auditId) {
      return new ExplainStreamEvent("error", null, null, detail, null, null, status, auditId);
    }
  }
}
