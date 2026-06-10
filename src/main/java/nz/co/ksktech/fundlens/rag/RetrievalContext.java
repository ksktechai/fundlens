package nz.co.ksktech.fundlens.rag;

import jakarta.enterprise.context.RequestScoped;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-request RAG state: which funds to filter retrieval to, and which chunks
 * were actually retrieved (captured for the audit trail and citations).
 */
@RequestScoped
public class RetrievalContext {

    public record RetrievedChunk(String embeddingId, String title, String periodEnd, double score) {
    }

    private List<Long> fundIds = List.of();
    private final List<RetrievedChunk> retrievedChunks = new ArrayList<>();

    public List<Long> getFundIds() {
        return fundIds;
    }

    public void setFundIds(List<Long> fundIds) {
        this.fundIds = fundIds == null ? List.of() : List.copyOf(fundIds);
    }

    public void recordRetrieved(RetrievedChunk chunk) {
        retrievedChunks.add(chunk);
    }

    public List<RetrievedChunk> getRetrievedChunks() {
        return List.copyOf(retrievedChunks);
    }
}
