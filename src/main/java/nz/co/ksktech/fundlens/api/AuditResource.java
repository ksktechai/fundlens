package nz.co.ksktech.fundlens.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.UUID;
import nz.co.ksktech.fundlens.api.ApiDtos.AuditResponse;
import nz.co.ksktech.fundlens.audit.AuditService;
import nz.co.ksktech.fundlens.domain.AuditRecord;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/v1/audit")
@Tag(name = "Audit", description = "Append-only decision trail per explain request")
@Produces(MediaType.APPLICATION_JSON)
public class AuditResource {

  private final AuditService auditService;
  private final ObjectMapper objectMapper;

  /**
   * Constructs an AuditResource.
   *
   * @param auditService the audit service
   * @param objectMapper the object mapper
   */
  public AuditResource(AuditService auditService, ObjectMapper objectMapper) {
    this.auditService = auditService;
    this.objectMapper = objectMapper;
  }

  /**
   * Gets the audit record by ID.
   *
   * @param auditId the audit ID
   * @return the audit response
   */
  @GET
  @Path("/{auditId}")
  @Operation(
      summary = "Get the full decision trail for an explain request",
      description =
          "Retrieved chunks, research findings, every draft, every compliance verdict, "
              + "the final answer and latency.")
  public AuditResponse get(@PathParam("auditId") UUID auditId) {
    AuditRecord record =
        auditService
            .find(auditId)
            .orElseThrow(() -> new NotFoundException("No audit record " + auditId));
    return new AuditResponse(
        record.id,
        record.createdAt,
        record.question,
        record.audience,
        parseJson(record.fundIds),
        parseJson(record.retrievedChunks),
        record.researchFindings,
        parseJson(record.drafts),
        parseJson(record.complianceResults),
        record.finalAnswer,
        record.status,
        record.modelName,
        record.latencyMs);
  }

  /**
   * Parses JSON string into a JsonNode.
   *
   * @param json the JSON string
   * @return the JSON node
   */
  private JsonNode parseJson(String json) {
    if (json == null) {
      return null;
    }
    try {
      return objectMapper.readTree(json);
    } catch (JsonProcessingException e) {
      return objectMapper.getNodeFactory().textNode(json);
    }
  }
}
