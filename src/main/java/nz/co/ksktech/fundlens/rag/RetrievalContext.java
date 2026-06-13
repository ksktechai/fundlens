package nz.co.ksktech.fundlens.rag;

import jakarta.enterprise.context.RequestScoped;
import java.util.ArrayList;
import java.util.List;

/**
 * Per-request RAG state: which funds to filter retrieval to, and which chunks were actually
 * retrieved (captured for the audit trail and citations).
 */
@RequestScoped
public class RetrievalContext {

  public record RetrievedChunk(String embeddingId, String title, String periodEnd, double score) {}

  private List<Long> fundIds = List.of();
  private final List<RetrievedChunk> retrievedChunks = new ArrayList<>();

  /**
   * Gets the list of fund IDs for the context.
   *
   * @return the list of fund IDs
   */
  public List<Long> getFundIds() {
    return fundIds;
  }

  /**
   * Sets the list of fund IDs for the context.
   *
   * @param fundIds the list of fund IDs to set
   */
  public void setFundIds(List<Long> fundIds) {
    this.fundIds = fundIds == null ? List.of() : List.copyOf(fundIds);
  }

  /**
   * Records a retrieved chunk.
   *
   * @param chunk the retrieved chunk to record
   */
  public void recordRetrieved(RetrievedChunk chunk) {
    retrievedChunks.add(chunk);
  }

  /**
   * Gets the list of retrieved chunks.
   *
   * @return the list of retrieved chunks
   */
  public List<RetrievedChunk> getRetrievedChunks() {
    return List.copyOf(retrievedChunks);
  }
}
