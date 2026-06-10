package nz.co.ksktech.fundlens.api;

import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import nz.co.ksktech.fundlens.agent.ExplainOrchestrator;
import nz.co.ksktech.fundlens.api.ApiDtos.ExplainRequest;
import nz.co.ksktech.fundlens.api.ApiDtos.ExplainResponse;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/v1/explain")
@Tag(name = "Explain", description = "Compliance-gated fund explanations")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ExplainResource {

  private final ExplainOrchestrator orchestrator;

  public ExplainResource(ExplainOrchestrator orchestrator) {
    this.orchestrator = orchestrator;
  }

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
}
