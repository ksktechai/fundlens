package nz.co.ksktech.fundlens.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import nz.co.ksktech.fundlens.api.ApiDtos.IngestResponse;
import nz.co.ksktech.fundlens.domain.FundDocument;
import nz.co.ksktech.fundlens.ingest.IngestionService;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

@Path("/api/v1/ingest")
@Tag(name = "Ingest", description = "Manual document ingestion (admin)")
@Produces(MediaType.APPLICATION_JSON)
public class IngestResource {

  public static class IngestForm {
    @RestForm @NotBlank public String providerId;

    @RestForm @NotNull public Long fundId;

    @RestForm
    @NotBlank
    @Pattern(
        regexp = "FUND_UPDATE|PDS|FACT_SHEET|OTHER",
        message = "docType must be one of FUND_UPDATE, PDS, FACT_SHEET, OTHER")
    public String docType;

    @RestForm public String periodEnd;

    @RestForm("file")
    @NotNull
    public FileUpload file;
  }

  private final IngestionService ingestionService;

  /**
   * Constructs an IngestResource.
   *
   * @param ingestionService the ingestion service
   */
  public IngestResource(IngestionService ingestionService) {
    this.ingestionService = ingestionService;
  }

  /**
   * Uploads and ingests a fund document (PDF or text).
   * Chunks, embeds and stores the document in the vector store for RAG.
   *
   * @param form the ingestion form containing document details and the file
   * @return the ingestion response
   * @throws BadRequestException if the uploaded file cannot be read or periodEnd is malformed
   */
  @POST
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  @Operation(
      summary = "Upload and ingest a fund document (PDF or text)",
      description = "Chunks, embeds and stores the document in the vector store for RAG.")
  public IngestResponse ingest(@Valid @BeanParam IngestForm form) {
    byte[] data;
    try {
      data = Files.readAllBytes(form.file.uploadedFile());
    } catch (IOException e) {
      throw new BadRequestException("Could not read uploaded file: " + e.getMessage());
    }
    LocalDate periodEnd = parsePeriodEnd(form.periodEnd);
    String fileName = form.file.fileName() != null ? form.file.fileName() : "upload";
    FundDocument document =
        ingestionService.ingest(
            data,
            fileName,
            new IngestionService.IngestionRequest(
                form.fundId, form.providerId, form.docType, periodEnd, fileName, "UPLOAD"));
    return new IngestResponse(document.id, document.title, document.chunkCount);
  }

  /**
   * Parses the period end string to a LocalDate.
   *
   * @param value the period end string in ISO date format (yyyy-MM-dd)
   * @return the parsed LocalDate, or null if the value is null or blank
   * @throws BadRequestException if the value is not a valid ISO date
   */
  private static LocalDate parsePeriodEnd(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return LocalDate.parse(value);
    } catch (DateTimeParseException e) {
      throw new BadRequestException("periodEnd must be an ISO date (yyyy-MM-dd): " + value);
    }
  }
}
