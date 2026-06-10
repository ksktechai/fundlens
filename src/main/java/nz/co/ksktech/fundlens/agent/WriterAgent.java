package nz.co.ksktech.fundlens.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;

/**
 * Writes the cited, plain-English explanation from the research findings.
 * No RAG and no tools: it may only use what the ResearchAgent found.
 */
@RegisterAiService(
        modelName = "writer",
        retrievalAugmentor = RegisterAiService.NoRetrievalAugmentorSupplier.class)
public interface WriterAgent {

    @SystemMessage(fromResource = "prompts/writer-system.txt")
    @UserMessage("""
            Original question: {question}
            Audience: {audience}

            Research findings:
            {findings}

            Compliance revision notes (address every one; "none" if first draft):
            {revisionNotes}

            Write the explanation now.
            """)
    String write(@V("question") String question,
                 @V("audience") String audience,
                 @V("findings") String findings,
                 @V("revisionNotes") String revisionNotes);
}
