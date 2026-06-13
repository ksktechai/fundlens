package nz.co.ksktech.fundlens.rag;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.function.Supplier;

/**
 * Retrieval augmentor for the ResearchAgent only (Writer and Compliance run without RAG). Filters
 * by fund_id metadata when the request scopes funds, and records every retrieved chunk into {@link
 * RetrievalContext} for the audit trail.
 */
@ApplicationScoped
public class ResearchAugmentorSupplier implements Supplier<RetrievalAugmentor> {

  private static final int MAX_RESULTS = 8;

  private final RetrievalAugmentor augmentor;

  /**
   * Constructs a ResearchAugmentorSupplier.
   *
   * @param embeddingStore the embedding store
   * @param embeddingModel the embedding model
   * @param retrievalContext the retrieval context
   */
  public ResearchAugmentorSupplier(
      EmbeddingStore<TextSegment> embeddingStore,
      EmbeddingModel embeddingModel,
      RetrievalContext retrievalContext) {
    ContentRetriever retriever =
        EmbeddingStoreContentRetriever.builder()
            .embeddingStore(embeddingStore)
            .embeddingModel(embeddingModel)
            .maxResults(MAX_RESULTS)
            .dynamicFilter(
                query -> {
                  List<Long> fundIds = retrievalContext.getFundIds();
                  if (fundIds.isEmpty()) {
                    return null;
                  }
                  List<String> asStrings = fundIds.stream().map(String::valueOf).toList();
                  return MetadataFilterBuilder.metadataKey("fund_id").isIn(asStrings);
                })
            .build();
    ContentRetriever tracking = new TrackingRetriever(retriever, retrievalContext);
    this.augmentor = DefaultRetrievalAugmentor.builder().contentRetriever(tracking).build();
  }

  /**
   * Gets the RetrievalAugmentor.
   *
   * @return the RetrievalAugmentor
   */
  @Override
  public RetrievalAugmentor get() {
    return augmentor;
  }

  private record TrackingRetriever(ContentRetriever delegate, RetrievalContext context)
      implements ContentRetriever {

    /**
     * Retrieves contents for a query and records the chunks.
     *
     * @param query the query
     * @return the list of contents
     */
    @Override
    public List<Content> retrieve(Query query) {
      List<Content> contents = delegate.retrieve(query);
      for (Content content : contents) {
        String embeddingId =
            String.valueOf(content.metadata().getOrDefault(ContentMetadata.EMBEDDING_ID, ""));
        Object score = content.metadata().getOrDefault(ContentMetadata.SCORE, 0.0d);
        var segmentMetadata = content.textSegment().metadata();
        context.recordRetrieved(
            new RetrievalContext.RetrievedChunk(
                embeddingId,
                segmentMetadata.getString("title"),
                segmentMetadata.getString("period_end"),
                score instanceof Number n ? n.doubleValue() : 0.0d));
      }
      return contents;
    }
  }
}
