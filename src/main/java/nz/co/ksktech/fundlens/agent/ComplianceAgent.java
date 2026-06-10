package nz.co.ksktech.fundlens.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;
import nz.co.ksktech.fundlens.agent.guard.ComplianceResult;

/**
 * Adversarial reviewer; runs on its own named model so production can point
 * it at a different (stronger) model than the writer. Structured output:
 * the framework parses the JSON verdict into {@link ComplianceResult};
 * parse failures are mapped to BLOCK by ComplianceGate.
 */
@RegisterAiService(
        modelName = "compliance",
        retrievalAugmentor = RegisterAiService.NoRetrievalAugmentorSupplier.class)
public interface ComplianceAgent {

    @SystemMessage(fromResource = "prompts/compliance-system.txt")
    @UserMessage("""
            Draft answer to review:
            ---
            {draft}
            ---

            Research findings the draft must be grounded in:
            ---
            {findings}
            ---
            """)
    ComplianceResult review(@V("draft") String draft, @V("findings") String findings);
}
