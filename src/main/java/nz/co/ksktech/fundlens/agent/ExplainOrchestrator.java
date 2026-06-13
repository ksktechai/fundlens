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

  /**
   * Receives pipeline progress for streaming responses. Stage details never carry draft content —
   * nothing unapproved may reach a client before the compliance verdict.
   */
  @FunctionalInterface
  public interface ProgressListener {
    ProgressListener NOOP = (stage, status, detail) -> {};

    void onStage(String stage, String status, String detail);
  }

  private final ResearchAgent researchAgent;
  private final WriterAgent writerAgent;
  private final ComplianceGate complianceGate;
  private final AuditService auditService;
  private final RetrievalContext retrievalContext;
  private final ObjectMapper objectMapper;
  private final String writerModelName;

  /**
   * Constructor for ExplainOrchestrator.
   *
   * @param researchAgent the agent for research
   * @param writerAgent the agent for writing
   * @param complianceGate the compliance gate
   * @param auditService the audit service
   * @param retrievalContext the context for retrieval
   * @param objectMapper object mapper for JSON
   * @param writerModelName the writer model name
   */
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

  /**
   * Explains a question given some funds and an audience.
   *
   * @param question the question to answer
   * @param fundIds the funds in scope
   * @param audience the target audience
   * @return the outcome of the explanation
   */
  public Outcome explain(String question, List<Long> fundIds, Audience audience) {
    return explain(question, fundIds, audience, ProgressListener.NOOP);
  }

  /**
   * Explains a question given some funds, an audience, and a progress listener.
   *
   * @param question the question to answer
   * @param fundIds the funds in scope
   * @param audience the target audience
   * @param listener a listener for progress updates
   * @return the outcome of the explanation
   */
  public Outcome explain(
      String question, List<Long> fundIds, Audience audience, ProgressListener listener) {
    long startNanos = System.nanoTime();
    List<Long> scope = fundIds == null ? List.of() : fundIds;
    retrievalContext.setFundIds(scope);

    List<String> drafts = new ArrayList<>();
    List<ComplianceResult> complianceResults = new ArrayList<>();
    String findings = null;

    try {
      listener.onStage("research", "started", null);
      findings = researchAgent.research(question, describeFunds(scope));
      listener.onStage(
          "research", "completed", retrievalContext.getRetrievedChunks().size() + " chunks retrieved");

      listener.onStage("draft", "started", "attempt 1");
      String draft = writerAgent.write(question, audience.name(), findings, "none");
      listener.onStage("draft", "completed", "attempt 1");
      drafts.add(draft);
      listener.onStage("compliance", "started", "attempt 1");
      ComplianceResult result = complianceGate.review(draft, findings);
      listener.onStage("compliance", "completed", verdictDetail(result));
      complianceResults.add(result);

      if (result.verdict() == Verdict.REVISE) {
        listener.onStage("draft", "started", "attempt 2");
        draft =
            writerAgent.write(
                question, audience.name(), findings, String.join("; ", result.issues()));
        listener.onStage("draft", "completed", "attempt 2");
        drafts.add(draft);
        listener.onStage("compliance", "started", "attempt 2");
        result = complianceGate.review(draft, findings);
        listener.onStage("compliance", "completed", verdictDetail(result));
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

  /**
   * Appends an audit record.
   *
   * @param question the question
   * @param fundIds the fund IDs
   * @param audience the audience
   * @param findings the findings
   * @param drafts the drafts
   * @param complianceResults the compliance results
   * @param finalAnswer the final answer
   * @param status the status
   * @param startNanos the start time in nanos
   * @return the UUID of the audit record
   */
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

  /**
   * Gets a formatted string for the verdict detail.
   *
   * @param result the compliance result
   * @return the verdict detail string
   */
  private static String verdictDetail(ComplianceResult result) {
    int issues = result.issues().size();
    return result.verdict() + (issues == 0 ? "" : " (" + issues + " issue" + (issues > 1 ? "s" : "") + ")");
  }

  /**
   * Returns citations.
   *
   * @return a list of citations
   */
  private List<Citation> citations() {
    LinkedHashSet<Citation> unique = new LinkedHashSet<>();
    for (RetrievalContext.RetrievedChunk chunk : retrievalContext.getRetrievedChunks()) {
      if (chunk.title() != null) {
        unique.add(new Citation(chunk.title(), chunk.periodEnd()));
      }
    }
    return List.copyOf(unique);
  }

  /**
   * Describes the given funds.
   *
   * @param fundIds the IDs of the funds
   * @return a description of the funds
   */
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

  /**
   * Converts an object to a JSON string.
   *
   * @param value the object to convert
   * @return the JSON string representation
   */
  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      return "\"<serialisation failed: " + e.getMessage() + ">\"";
    }
  }
}
