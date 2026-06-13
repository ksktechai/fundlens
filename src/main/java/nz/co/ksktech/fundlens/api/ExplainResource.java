package nz.co.ksktech.fundlens.api;

import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.List;
import nz.co.ksktech.fundlens.agent.ExplainOrchestrator;
import nz.co.ksktech.fundlens.api.ApiDtos.ExplainRequest;
import nz.co.ksktech.fundlens.api.ApiDtos.ExplainResponse;
import nz.co.ksktech.fundlens.api.ApiDtos.ExplainStreamEvent;
import nz.co.ksktech.fundlens.config.LlmUnavailableException;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestStreamElementType;

@Path("/api/v1/explain")
@Tag(name = "Explain", description = "Compliance-gated fund explanations")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ExplainResource {

  /** Streamed answer chunks aim for this many characters, split on word boundaries. */
  private static final int ANSWER_CHUNK_CHARS = 120;

  private final ExplainOrchestrator orchestrator;

  /**
   * Constructor for ExplainResource.
   *
   * @param orchestrator the explain orchestrator
   */
  public ExplainResource(ExplainOrchestrator orchestrator) {
    this.orchestrator = orchestrator;
  }

  /**
   * Asks a natural-language question about KiwiSaver funds.
   *
   * @param request the explain request
   * @return the explain response
   */
  @POST
  @Operation(
      summary = "Ask a natural-language question about KiwiSaver funds",
      description =
          "Runs the research → write → compliance agent pipeline and returns a cited, "
              + "compliance-checked answer plus the audit id of the full decision trail.")
  public ExplainResponse explain(@Valid ExplainRequest request) {
    ExplainOrchestrator.Outcome outcome =
        orchestrator.explain(request.question(), request.fundIds(), request.audienceOrDefault());
    return new ExplainResponse(
        outcome.answer(), outcome.citations(), outcome.verdict().name(), outcome.auditId());
  }

  /**
   * Asks a question and streams the pipeline progress and the approved answer.
   *
   * @param request the explain request
   * @return a Multi of explain stream events
   */
  @POST
  @Path("/stream")
  @Blocking // request filters read the body; never allowed on the IO thread
  @Produces(MediaType.SERVER_SENT_EVENTS)
  @RestStreamElementType(MediaType.APPLICATION_JSON)
  @Operation(
      summary = "Ask a question and stream pipeline progress + the approved answer (SSE)",
      description =
          "Emits 'stage' events live as the pipeline runs (research, draft, compliance), then the "
              + "compliance-approved answer as 'answer-chunk' events, then one 'complete' event "
              + "with citations, verdict and auditId. Failures arrive as an in-band 'error' event. "
              + "Draft content is never streamed before the compliance verdict.")
  public Multi<ExplainStreamEvent> explainStream(@Valid ExplainRequest request) {
    return Multi.createFrom()
        .<ExplainStreamEvent>emitter(
            emitter -> {
              try {
                ExplainOrchestrator.Outcome outcome =
                    orchestrator.explain(
                        request.question(),
                        request.fundIds(),
                        request.audienceOrDefault(),
                        (stage, status, detail) ->
                            emitter.emit(ExplainStreamEvent.stage(stage, status, detail)));
                for (String chunk : chunkAnswer(outcome.answer())) {
                  emitter.emit(ExplainStreamEvent.answerChunk(chunk));
                }
                emitter.emit(
                    ExplainStreamEvent.complete(
                        new ExplainResponse(
                            outcome.answer(),
                            outcome.citations(),
                            outcome.verdict().name(),
                            outcome.auditId())));
              } catch (LlmUnavailableException e) {
                emitter.emit(ExplainStreamEvent.error(503, "LLM backend unavailable", e.auditId()));
              } catch (RuntimeException e) {
                emitter.emit(ExplainStreamEvent.error(500, "Unexpected pipeline failure", null));
              } finally {
                emitter.complete();
              }
            })
        .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
  }

  /**
   * Chunks an answer into smaller pieces.
   *
   * @param answer the answer to chunk
   * @return a list of chunks
   */
  static List<String> chunkAnswer(String answer) {
    List<String> chunks = new ArrayList<>();
    String[] words = answer.split("(?<= )");
    StringBuilder current = new StringBuilder();
    for (String word : words) {
      if (current.length() + word.length() > ANSWER_CHUNK_CHARS && !current.isEmpty()) {
        chunks.add(current.toString());
        current.setLength(0);
      }
      current.append(word);
    }
    if (!current.isEmpty()) {
      chunks.add(current.toString());
    }
    return chunks;
  }
}
