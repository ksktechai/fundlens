package nz.co.ksktech.fundlens.testsupport;

import static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder.responseDefinition;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformerV2;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.util.Collections;
import java.util.Map;

/**
 * One WireMock server for everything external: the Ollama API (chat + embeddings), the Disclose
 * Register gateway (under /disclose) and the "CDN" serving redirected fund-update documents (under
 * /files). No test ever talks to the network.
 */
public class WireMockTestResource implements QuarkusTestResourceLifecycleManager {

  public static final int EMBEDDING_DIMENSION = 768;
  public static final String VECTOR_JSON = vectorJson();

  private static WireMockServer server;

  public static WireMockServer server() {
    return server;
  }

  public static String baseUrl() {
    return "http://localhost:" + server.port();
  }

  /** Clears all stubs and the request journal, then re-registers the embedding stubs. */
  public static void resetToDefaults() {
    server.resetAll();
    registerDefaultStubs();
  }

  @Override
  public Map<String, String> start() {
    server = new WireMockServer(wireMockConfig().dynamicPort().extensions(new EmbedTransformer()));
    server.start();
    registerDefaultStubs();
    String url = baseUrl();
    return Map.of(
        "quarkus.langchain4j.ollama.base-url", url,
        "quarkus.langchain4j.ollama.research.base-url", url,
        "quarkus.langchain4j.ollama.writer.base-url", url,
        "quarkus.langchain4j.ollama.compliance.base-url", url,
        "quarkus.rest-client.disclose-register.url", url + "/disclose");
  }

  @Override
  public void stop() {
    if (server != null) {
      server.stop();
      server = null;
    }
  }

  private static void registerDefaultStubs() {
    // Newer Ollama batch endpoint: echoes one vector per input item.
    server.stubFor(
        post(urlEqualTo("/api/embed"))
            .atPriority(10)
            .willReturn(aResponse().withTransformers("embed-transformer")));
    // Legacy single-prompt endpoint.
    server.stubFor(
        post(urlEqualTo("/api/embeddings"))
            .atPriority(10)
            .willReturn(okJson("{\"embedding\":" + VECTOR_JSON + "}")));
  }

  private static String vectorJson() {
    return "[" + String.join(",", Collections.nCopies(EMBEDDING_DIMENSION, "0.1")) + "]";
  }

  /** Returns as many embedding vectors as the request has input strings. */
  public static class EmbedTransformer implements ResponseDefinitionTransformerV2 {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public ResponseDefinition transform(ServeEvent serveEvent) {
      int count = 1;
      try {
        JsonNode root = MAPPER.readTree(serveEvent.getRequest().getBody());
        JsonNode input = root.get("input");
        if (input != null && input.isArray()) {
          count = Math.max(1, input.size());
        }
      } catch (Exception ignored) {
        // fall through with a single vector
      }
      String body =
          "{\"model\":\"nomic-embed-text\",\"embeddings\":["
              + String.join(",", Collections.nCopies(count, VECTOR_JSON))
              + "]}";
      return responseDefinition()
          .withStatus(200)
          .withHeader("Content-Type", "application/json")
          .withBody(body)
          .build();
    }

    @Override
    public String getName() {
      return "embed-transformer";
    }

    @Override
    public boolean applyGlobally() {
      return false;
    }
  }
}
