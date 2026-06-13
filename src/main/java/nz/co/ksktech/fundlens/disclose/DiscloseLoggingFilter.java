package nz.co.ksktech.fundlens.disclose;

import io.quarkus.logging.Log;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.client.ClientResponseContext;
import jakarta.ws.rs.client.ClientResponseFilter;
import jakarta.ws.rs.core.MediaType;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Custom logging filter for REST Client requests/responses. Logs URI, headers (with sensitive
 * values masked), and response body (truncated). Implemented as a JAX-RS Client filter.
 */
public class DiscloseLoggingFilter implements ClientRequestFilter, ClientResponseFilter {

  private static final Set<String> SENSITIVE_HEADERS =
      Set.of(
          "ocp-apim-subscription-key", "x-organisation", "authorization", "cookie", "set-cookie");

  private static final int BODY_LOG_LIMIT = 5120;

  @Override
  public void filter(ClientRequestContext requestContext) {
    Log.infof(">>> Client Request: %s %s", requestContext.getMethod(), requestContext.getUri());
    requestContext
        .getHeaders()
        .forEach(
            (name, values) -> {
              String loggedValue =
                  values.stream()
                      .map(val -> maskHeader(name, String.valueOf(val)))
                      .collect(Collectors.joining(", "));
              Log.infof("  Header: %s: %s", name, loggedValue);
            });
  }

  @Override
  public void filter(ClientRequestContext requestContext, ClientResponseContext responseContext)
      throws IOException {
    Log.infof(
        "<<< Client Response: %s %s -> %d %s",
        requestContext.getMethod(),
        requestContext.getUri(),
        responseContext.getStatus(),
        responseContext.getStatusInfo().getReasonPhrase());

    responseContext
        .getHeaders()
        .forEach(
            (name, values) -> {
              String loggedValue =
                  values.stream()
                      .map(val -> maskHeader(name, String.valueOf(val)))
                      .collect(Collectors.joining(", "));
              Log.infof("  Header: %s: %s", name, loggedValue);
            });

    if (responseContext.hasEntity() && isTextual(responseContext.getMediaType())) {
      InputStream stream = responseContext.getEntityStream();
      if (stream != null) {
        byte[] bodyBytes = stream.readAllBytes();
        responseContext.setEntityStream(new ByteArrayInputStream(bodyBytes));
        String body = new String(bodyBytes, StandardCharsets.UTF_8);
        Log.infof("  Response Body: %s", truncate(body));
      }
    }
  }

  private static String maskHeader(String name, String value) {
    if (name != null && SENSITIVE_HEADERS.contains(name.toLowerCase())) {
      return "********";
    }
    return value;
  }

  private static boolean isTextual(MediaType mediaType) {
    if (mediaType == null) {
      return false;
    }
    String subtype = mediaType.getSubtype();
    return "text".equalsIgnoreCase(mediaType.getType())
        || "json".equalsIgnoreCase(subtype)
        || subtype.endsWith("+json")
        || "x-www-form-urlencoded".equalsIgnoreCase(subtype);
  }

  private static String truncate(String body) {
    String single = body.strip();
    if (single.length() <= BODY_LOG_LIMIT) {
      return single;
    }
    return single.substring(0, BODY_LOG_LIMIT)
        + "…(+"
        + (single.length() - BODY_LOG_LIMIT)
        + " chars)";
  }
}
