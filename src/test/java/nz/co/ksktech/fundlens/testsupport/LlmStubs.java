package nz.co.ksktech.fundlens.testsupport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.client.MappingBuilder;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

/**
 * Helpers for stubbing the Ollama /api/chat endpoint. The three agents share
 * the endpoint; requests are told apart by distinctive phrases from their
 * system prompts.
 */
public final class LlmStubs {

    /** Phrase from prompts/research-system.txt. */
    public static final String RESEARCH_MARKER = "research analyst";
    /** Phrase from prompts/writer-system.txt. */
    public static final String WRITER_MARKER = "plain-English explanations";
    /** Phrase from prompts/compliance-system.txt. */
    public static final String COMPLIANCE_MARKER = "adversarial compliance reviewer";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private LlmStubs() {
    }

    /** A complete non-streaming Ollama chat response with the given assistant content. */
    public static String chatBody(String content) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("model", "qwen3:30b");
        root.put("created_at", "2026-06-10T00:00:00Z");
        ObjectNode message = root.putObject("message");
        message.put("role", "assistant");
        message.put("content", content);
        root.put("done", true);
        root.put("done_reason", "stop");
        root.put("total_duration", 1000000L);
        root.put("load_duration", 100000L);
        root.put("prompt_eval_count", 10);
        root.put("eval_count", 20);
        return root.toString();
    }

    public static MappingBuilder chatFor(String agentMarker) {
        return post(urlEqualTo("/api/chat")).withRequestBody(containing(agentMarker));
    }

    public static void stubAgent(String agentMarker, String responseContent) {
        WireMockTestResource.server().stubFor(chatFor(agentMarker).willReturn(okJson(chatBody(responseContent))));
    }

    public static String complianceJson(String verdict, String... issues) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("verdict", verdict);
        var arr = root.putArray("issues");
        for (String issue : issues) {
            arr.add(issue);
        }
        return root.toString();
    }
}
