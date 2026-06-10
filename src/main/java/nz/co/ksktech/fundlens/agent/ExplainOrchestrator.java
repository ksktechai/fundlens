package nz.co.ksktech.fundlens.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import nz.co.ksktech.fundlens.agent.guard.ComplianceGate;
import nz.co.ksktech.fundlens.agent.guard.ComplianceResult;
import nz.co.ksktech.fundlens.agent.guard.Verdict;
import nz.co.ksktech.fundlens.audit.AuditService;
import nz.co.ksktech.fundlens.config.LlmUnavailableException;
import nz.co.ksktech.fundlens.domain.AuditRecord;
import nz.co.ksktech.fundlens.domain.Fund;
import nz.co.ksktech.fundlens.rag.RetrievalContext;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * research → write → compliance pipeline with one revise loop. Every request — answered, blocked or
 * failed upstream — leaves an append-only AuditRecord.
 */
@ApplicationScoped
public class ExplainOrchestrator {

  public record Citation(String source, String periodEnd) {}

  public record Outcome(UUID auditId, String answer, Verdict verdict, List<Citation> citations) {}

  private final ResearchAgent researchAgent;
  private final WriterAgent writerAgent;
  private final ComplianceGate complianceGate;
  private final AuditService auditService;
  private final RetrievalContext retrievalContext;
  private final ObjectMapper objectMapper;
  private final String writerModelName;

  public ExplainOrchestrator(
      ResearchAgent researchAgent,
      WriterAgent writerAgent,
      ComplianceGate complianceGate,
      AuditService auditService,
      RetrievalContext retrievalContext,
      ObjectMapper objectMapper,
      @ConfigProperty(name = "quarkus.langchain4j.ollama.writer.chat-model.model-name")
          String writerModelName) {
    this.researchAgent = researchAgent;
    this.writerAgent = writerAgent;
    this.complianceGate = complianceGate;
    this.auditService = auditService;
    this.retrievalContext = retrievalContext;
    this.objectMapper = objectMapper;
    this.writerModelName = writerModelName;
  }

  public Outcome explain(String question, List<Long> fundIds, Audience audience) {
    long startNanos = System.nanoTime();
    List<Long> scope = fundIds == null ? List.of() : fundIds;
    retrievalContext.setFundIds(scope);

    List<String> drafts = new ArrayList<>();
    List<ComplianceResult> complianceResults = new ArrayList<>();
    String findings = null;

    try {
      findings = researchAgent.research(question, describeFunds(scope));

      String draft = writerAgent.write(question, audience.name(), findings, "none");
      drafts.add(draft);
      ComplianceResult result = complianceGate.review(draft, findings);
      complianceResults.add(result);

      if (result.verdict() == Verdict.REVISE) {
        draft =
            writerAgent.write(
                question, audience.name(), findings, String.join("; ", result.issues()));
        drafts.add(draft);
        result = complianceGate.review(draft, findings);
        complianceResults.add(result);
      }

      boolean passed = result.verdict() == Verdict.PASS;
      String answer = passed ? draft : Disclaimers.FALLBACK_MESSAGE;
      Verdict finalVerdict = passed ? Verdict.PASS : Verdict.BLOCK;

      UUID auditId =
          appendAudit(
              question,
              scope,
              audience,
              findings,
              drafts,
              complianceResults,
              answer,
              passed ? "ANSWERED" : "BLOCKED",
              startNanos);
      return new Outcome(auditId, answer, finalVerdict, citations());
    } catch (RuntimeException e) {
      Log.errorf(e, "Explain pipeline failed for question: %s", question);
      UUID auditId =
          appendAudit(
              question,
              scope,
              audience,
              findings,
              drafts,
              complianceResults,
              null,
              "UPSTREAM_ERROR",
              startNanos);
      throw new LlmUnavailableException(auditId, e);
    }
  }

  private UUID appendAudit(
      String question,
      List<Long> fundIds,
      Audience audience,
      String findings,
      List<String> drafts,
      List<ComplianceResult> complianceResults,
      String finalAnswer,
      String status,
      long startNanos) {
    AuditRecord record = new AuditRecord();
    record.question = question;
    record.audience = audience.name();
    record.fundIds = toJson(fundIds);
    record.retrievedChunks = toJson(retrievalContext.getRetrievedChunks());
    record.researchFindings = findings;
    record.drafts = toJson(drafts);
    record.complianceResults = toJson(complianceResults);
    record.finalAnswer = finalAnswer;
    record.status = status;
    record.modelName = writerModelName;
    record.latencyMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    return auditService.append(record);
  }

  private List<Citation> citations() {
    LinkedHashSet<Citation> unique = new LinkedHashSet<>();
    for (RetrievalContext.RetrievedChunk chunk : retrievalContext.getRetrievedChunks()) {
      if (chunk.title() != null) {
        unique.add(new Citation(chunk.title(), chunk.periodEnd()));
      }
    }
    return List.copyOf(unique);
  }

  private static String describeFunds(List<Long> fundIds) {
    if (fundIds.isEmpty()) {
      return "all KiwiSaver funds in the catalogue";
    }
    List<String> names = new ArrayList<>();
    for (Long id : fundIds) {
      Fund fund = Fund.findById(id);
      names.add(fund != null ? fund.name + " (id " + id + ")" : "unknown fund (id " + id + ")");
    }
    return String.join(", ", names);
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      return "\"<serialisation failed: " + e.getMessage() + ">\"";
    }
  }
}
