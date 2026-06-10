package nz.co.ksktech.fundlens.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import nz.co.ksktech.fundlens.api.ApiDtos.SyncRunResponse;
import nz.co.ksktech.fundlens.disclose.DiscloseSyncService;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/v1/sync")
@Tag(name = "Sync", description = "Disclose Register synchronisation (admin)")
@Produces(MediaType.APPLICATION_JSON)
public class SyncResource {

  private final DiscloseSyncService syncService;
  private final ObjectMapper objectMapper;

  public SyncResource(DiscloseSyncService syncService, ObjectMapper objectMapper) {
    this.syncService = syncService;
    this.objectMapper = objectMapper;
  }

  @POST
  @Operation(
      summary = "Trigger an immediate Disclose Register sync",
      description =
          "Synchronises every configured fund number (disclose.sync.fund-numbers) "
              + "and returns the per-fund outcomes.")
  public SyncRunResponse sync() {
    DiscloseSyncService.SyncSummary summary = syncService.run("MANUAL");
    return new SyncRunResponse(
        summary.runId(),
        summary.startedAt(),
        summary.finishedAt(),
        summary.status(),
        objectMapper.valueToTree(summary.outcomes()));
  }
}
