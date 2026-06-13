package nz.co.ksktech.fundlens.config;

import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.ext.Provider;
import jakarta.ws.rs.ext.WriterInterceptor;
import jakarta.ws.rs.ext.WriterInterceptorContext;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Logs every API request and response, including JSON/text bodies (truncated). Binary payloads
 * (multipart uploads, PDFs) are logged by size only. Toggle with fundlens.log.http-bodies /
 * LOG_HTTP_BODIES; the one-line access log (quarkus.http.access-log) stays on independently.
 */
@Provider
public class HttpLoggingFilter
    implements ContainerRequestFilter, ContainerResponseFilter, WriterInterceptor {

  private static final String START_NANOS = "fundlens.request.start";
  private static final int BODY_LOG_LIMIT = 5120;

  @Inject
  @ConfigProperty(name = "fundlens.log.http-bodies", defaultValue = "true")
  boolean logBodies;

  /**
   * Filters a request context, logging its method, path, and optionally its body.
   *
   * @param request the container request context
   * @throws IOException if reading the entity fails
   */
  @Override
  public void filter(ContainerRequestContext request) throws IOException {
    request.setProperty(START_NANOS, System.nanoTime());
    String detail = "";
    if (logBodies && request.hasEntity()) {
      if (isTextual(request.getMediaType())) {
        byte[] body = request.getEntityStream().readAllBytes();
        request.setEntityStream(new ByteArrayInputStream(body));
        detail = " body=" + truncate(new String(body, StandardCharsets.UTF_8));
      } else {
        detail = " body=<" + request.getMediaType() + ">";
      }
    }
    Log.infof(">>> %s %s%s", request.getMethod(), pathWithQuery(request), detail);
  }

  /**
   * Filters a response context, logging the status code and time taken.
   *
   * @param request the container request context
   * @param response the container response context
   */
  @Override
  public void filter(ContainerRequestContext request, ContainerResponseContext response) {
    Object start = request.getProperty(START_NANOS);
    long millis = start instanceof Long s ? (System.nanoTime() - s) / 1_000_000 : -1;
    Log.infof(
        "<<< %s %s -> %d (%d ms)",
        request.getMethod(), pathWithQuery(request), response.getStatus(), millis);
  }

  /**
   * Intercepts a response write, optionally logging its textual body.
   *
   * @param context the writer interceptor context
   * @throws IOException if writing to the stream fails
   * @throws WebApplicationException if a web application error occurs
   */
  @Override
  public void aroundWriteTo(WriterInterceptorContext context)
      throws IOException, WebApplicationException {
    if (!logBodies || !isTextual(context.getMediaType())) {
      context.proceed();
      return;
    }
    OutputStream original = context.getOutputStream();
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    context.setOutputStream(buffer);
    try {
      context.proceed();
    } finally {
      byte[] body = buffer.toByteArray();
      original.write(body);
      context.setOutputStream(original);
      Log.infof("<<< response body=%s", truncate(new String(body, StandardCharsets.UTF_8)));
    }
  }

  /**
   * Constructs the full path with query parameters.
   *
   * @param request the request context
   * @return the formatted path and query string
   */
  private static String pathWithQuery(ContainerRequestContext request) {
    var uri = request.getUriInfo().getRequestUri();
    return uri.getPath() + (uri.getQuery() != null ? "?" + uri.getQuery() : "");
  }

  /**
   * Determines whether a given media type is textual.
   *
   * @param mediaType the media type
   * @return true if textual, false otherwise
   */
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

  /**
   * Truncates a given body string if it exceeds a limit.
   *
   * @param body the body string
   * @return the truncated string
   */
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
