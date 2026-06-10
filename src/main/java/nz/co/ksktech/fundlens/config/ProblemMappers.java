package nz.co.ksktech.fundlens.config;

import io.quarkus.logging.Log;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.UUID;
import nz.co.ksktech.fundlens.disclose.DiscloseApiException;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/** RFC 7807 problem-detail JSON for every error the API can produce. */
public class ProblemMappers {

  public record Problem(
      String type, String title, int status, String detail, List<String> violations, UUID auditId) {

    static Problem of(String title, int status, String detail) {
      return new Problem("about:blank", title, status, detail, null, null);
    }
  }

  private static final String PROBLEM_JSON = "application/problem+json";

  @ServerExceptionMapper
  public RestResponse<Problem> constraintViolation(ConstraintViolationException e) {
    List<String> violations =
        e.getConstraintViolations().stream()
            .map(v -> v.getPropertyPath() + ": " + v.getMessage())
            .sorted()
            .toList();
    Problem problem =
        new Problem(
            "about:blank", "Validation failed", 400, "Request validation failed", violations, null);
    return problemResponse(problem);
  }

  @ServerExceptionMapper
  public RestResponse<Problem> notFound(NotFoundException e) {
    return problemResponse(Problem.of("Not found", 404, e.getMessage()));
  }

  @ServerExceptionMapper
  public RestResponse<Problem> badRequest(BadRequestException e) {
    return problemResponse(Problem.of("Bad request", 400, e.getMessage()));
  }

  @ServerExceptionMapper
  public RestResponse<Problem> llmUnavailable(LlmUnavailableException e) {
    Log.errorf(e, "LLM backend unavailable");
    Problem problem =
        new Problem(
            "about:blank",
            "LLM backend unavailable",
            503,
            "The language model backend did not respond; the request was audited and can be retried.",
            null,
            e.auditId());
    return problemResponse(problem);
  }

  @ServerExceptionMapper
  public RestResponse<Problem> discloseFailure(DiscloseApiException e) {
    Log.errorf(e, "Disclose Register call failed");
    return problemResponse(Problem.of("Disclose Register unavailable", 502, e.getMessage()));
  }

  @ServerExceptionMapper
  public RestResponse<Problem> fallback(Exception e) {
    if (e instanceof WebApplicationException wae) {
      int status = wae.getResponse().getStatus();
      return problemResponse(
          Problem.of(
              Response.Status.fromStatusCode(status) != null
                  ? Response.Status.fromStatusCode(status).getReasonPhrase()
                  : "Error",
              status,
              wae.getMessage()));
    }
    Log.errorf(e, "Unhandled exception");
    return problemResponse(
        Problem.of("Internal server error", 500, "An unexpected error occurred"));
  }

  private static RestResponse<Problem> problemResponse(Problem problem) {
    return RestResponse.ResponseBuilder.create(
            RestResponse.Status.fromStatusCode(problem.status()), problem)
        .header("Content-Type", PROBLEM_JSON)
        .build();
  }
}
