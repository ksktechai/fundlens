package nz.co.ksktech.fundlens.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;
import nz.co.ksktech.fundlens.rag.ResearchAugmentorSupplier;
import nz.co.ksktech.fundlens.tools.FeeCalculatorTool;
import nz.co.ksktech.fundlens.tools.FundDataTool;

/**
 * Gathers facts via RAG over ingested fund documents plus deterministic
 * tools for exact numbers. Output is a structured findings list with sources.
 */
@RegisterAiService(
        modelName = "research",
        tools = {FundDataTool.class, FeeCalculatorTool.class},
        retrievalAugmentor = ResearchAugmentorSupplier.class)
public interface ResearchAgent {

    @SystemMessage(fromResource = "prompts/research-system.txt")
    @UserMessage("""
            Question: {question}
            Funds in scope: {fundContext}

            Gather every fact needed to answer the question. Use the retrieved document
            excerpts and the tools; cite a source title and period for every fact.
            """)
    String research(@V("question") String question, @V("fundContext") String fundContext);
}
