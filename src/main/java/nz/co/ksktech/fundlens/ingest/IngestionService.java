package nz.co.ksktech.fundlens.ingest;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.quarkus.logging.Log;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import nz.co.ksktech.fundlens.domain.FundDocument;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

/**
 * Turns a fund document (PDF or plain text) into ~500-token chunks with metadata, embeds them into
 * the pgvector store, and records a {@link FundDocument} row.
 */
@ApplicationScoped
public class IngestionService {

  public record IngestionRequest(
      Long fundId,
      String provider,
      String docType,
      LocalDate periodEnd,
      String title,
      String source) {}

  /** ~500 tokens at the usual ~3.5 chars/token for English text. */
  private static final int MAX_CHUNK_CHARS = 1800;

  private static final int CHUNK_OVERLAP_CHARS = 200;

  private final EmbeddingModel embeddingModel;
  private final EmbeddingStore<TextSegment> embeddingStore;

  /**
   * Constructs an IngestionService.
   *
   * @param embeddingModel the embedding model
   * @param embeddingStore the embedding store
   */
  public IngestionService(
      EmbeddingModel embeddingModel, EmbeddingStore<TextSegment> embeddingStore) {
    this.embeddingModel = embeddingModel;
    this.embeddingStore = embeddingStore;
  }

  /**
   * Ingests a document, splitting it into chunks, embedding them, and storing them.
   *
   * @param data the document data
   * @param fileName the name of the file
   * @param request the ingestion request details
   * @return the created FundDocument
   */
  public FundDocument ingest(byte[] data, String fileName, IngestionRequest request) {
    String text = extractText(data, fileName);

    Map<String, String> metadataMap = new HashMap<>();
    metadataMap.put("title", request.title());
    metadataMap.put("doc_type", request.docType());
    if (request.fundId() != null) {
      metadataMap.put("fund_id", String.valueOf(request.fundId()));
    }
    if (request.provider() != null) {
      metadataMap.put("provider", request.provider());
    }
    if (request.periodEnd() != null) {
      metadataMap.put("period_end", request.periodEnd().toString());
    }

    Document document = Document.from(text, Metadata.from(metadataMap));
    DocumentSplitter splitter = DocumentSplitters.recursive(MAX_CHUNK_CHARS, CHUNK_OVERLAP_CHARS);
    List<TextSegment> segments = splitter.split(document);
    List<Embedding> embeddings = embeddingModel.embedAll(segments).content();

    return QuarkusTransaction.requiringNew()
        .call(
            () -> {
              embeddingStore.addAll(embeddings, segments);

              FundDocument fundDocument = new FundDocument();
              fundDocument.fundId = request.fundId();
              fundDocument.title = request.title();
              fundDocument.provider = request.provider();
              fundDocument.docType = request.docType();
              fundDocument.periodEnd = request.periodEnd();
              fundDocument.source = request.source();
              fundDocument.chunkCount = segments.size();
              fundDocument.persist();

              Log.infof(
                  "Ingested '%s' (%d chunks, fund %s)",
                  request.title(), segments.size(), request.fundId());
              return fundDocument;
            });
  }

  /**
   * Extracts text from a byte array, handling PDF and plain text.
   *
   * @param data the byte array
   * @param fileName the name of the file
   * @return the extracted text
   */
  private static String extractText(byte[] data, String fileName) {
    if (isPdf(data)) {
      try (PDDocument pdf = Loader.loadPDF(data)) {
        return new PDFTextStripper().getText(pdf);
      } catch (IOException e) {
        throw new IllegalArgumentException(
            "Could not parse PDF '" + fileName + "': " + e.getMessage(), e);
      }
    }
    return new String(data, StandardCharsets.UTF_8);
  }

  /**
   * Checks if a byte array is a PDF.
   *
   * @param data the byte array
   * @return true if it is a PDF, false otherwise
   */
  private static boolean isPdf(byte[] data) {
    return data.length >= 4 && data[0] == '%' && data[1] == 'P' && data[2] == 'D' && data[3] == 'F';
  }
}
