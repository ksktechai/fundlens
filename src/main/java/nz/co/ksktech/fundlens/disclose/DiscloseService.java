package nz.co.ksktech.fundlens.disclose;

import io.smallrye.faulttolerance.api.CircuitBreakerName;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Response;
import nz.co.ksktech.fundlens.disclose.DiscloseDtos.DiscloseFund;
import nz.co.ksktech.fundlens.disclose.DiscloseDtos.DisclosePage;
import nz.co.ksktech.fundlens.disclose.DiscloseDtos.OfferSummary;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.inject.RestClient;

/**
 * Thin fault-tolerant wrapper around {@link DiscloseRegisterClient}: supplies the x-organisation
 * header from config so callers never handle it, reads ETags, and translates HTTP statuses into
 * typed results/exceptions.
 */
@ApplicationScoped
public class DiscloseService {

  /** Result of a conditional fund fetch. */
  public record FundFetchResult(boolean notModified, DiscloseFund fund, String etag) {
    /**
     * Creates a FundFetchResult indicating the fund was not modified.
     *
     * @return a new FundFetchResult
     */
    public static FundFetchResult unchanged() {
      return new FundFetchResult(true, null, null);
    }
  }

  private final DiscloseRegisterClient client;
  private final String organisationId;

  /**
   * Constructs a DiscloseService.
   *
   * @param client the DiscloseRegisterClient
   * @param organisationId the organisation ID
   */
  public DiscloseService(
      @RestClient DiscloseRegisterClient client,
      @ConfigProperty(name = "disclose.api.organisation-id") String organisationId) {
    this.client = client;
    this.organisationId = organisationId;
  }

  /**
   * Fetches a fund from the Disclose Register.
   *
   * @param fundNumber the fund number
   * @param etag the ETag for conditional fetching
   * @return the fund fetch result
   */
  @Retry(maxRetries = 2, delay = 200, retryOn = DiscloseApiException.class)
  @Timeout(15000)
  @CircuitBreaker(
      requestVolumeThreshold = 8,
      failureRatio = 0.75,
      delay = 1000,
      failOn = DiscloseApiException.class)
  @CircuitBreakerName("disclose-fund")
  public FundFetchResult fetchFund(String fundNumber, String etag) {
    try (Response response =
        invoke(
            () -> client.getFundRaw(fundNumber, organisationId, etag), "getFund " + fundNumber)) {
      int status = response.getStatus();
      if (status == Response.Status.NOT_MODIFIED.getStatusCode()) {
        return FundFetchResult.unchanged();
      }
      if (status == Response.Status.OK.getStatusCode()) {
        DiscloseFund fund = response.readEntity(DiscloseFund.class);
        return new FundFetchResult(false, fund, response.getHeaderString("ETag"));
      }
      throw new DiscloseApiException(status, "getFund " + fundNumber);
    }
  }

  /**
   * Returns the Location header of the 302 response. The REST client never follows the redirect
   * (follow-redirects=false) so the subscription key stays inside the gateway; the caller downloads
   * the Location with {@link DocumentFetcher}.
   *
   * @param fundNumber the fund number
   * @return the location of the fund update document
   */
  @Retry(maxRetries = 2, delay = 200, retryOn = DiscloseApiException.class)
  @Timeout(15000)
  @CircuitBreaker(
      requestVolumeThreshold = 8,
      failureRatio = 0.75,
      delay = 1000,
      failOn = DiscloseApiException.class)
  @CircuitBreakerName("disclose-document")
  public String fetchFundUpdateDocumentLocation(String fundNumber) {
    try (Response response =
        invoke(
            () -> client.getFundUpdateDocument(fundNumber, organisationId),
            "fund-update-document " + fundNumber)) {
      int status = response.getStatus();
      if (status == Response.Status.FOUND.getStatusCode()) {
        String location = response.getHeaderString("Location");
        if (location == null || location.isBlank()) {
          throw new DiscloseApiException(status, "302 without Location for fund " + fundNumber);
        }
        return location;
      }
      throw new DiscloseApiException(status, "fund-update-document " + fundNumber);
    }
  }

  /**
   * Searches for managed fund offers.
   *
   * @param search the search query
   * @param page the page number
   * @return a page of offer summaries
   */
  public DisclosePage<OfferSummary> searchManagedFundOffers(String search, Integer page) {
    return client.searchOffers(search, "managedFund", "open", page, organisationId);
  }

  /**
   * The Quarkus REST client throws WebApplicationException for 4xx/5xx even on Response-returning
   * methods; translate to DiscloseApiException so {@code @Retry}/{@code @CircuitBreaker} treat it
   * as a gateway failure.
   *
   * @param call the function to call
   * @param operation the operation name
   * @return the response
   */
  private static Response invoke(java.util.function.Supplier<Response> call, String operation) {
    try {
      return call.get();
    } catch (jakarta.ws.rs.WebApplicationException e) {
      throw new DiscloseApiException(
          e.getResponse() != null ? e.getResponse().getStatus() : 0, operation);
    } catch (jakarta.ws.rs.ProcessingException e) {
      throw new DiscloseApiException(0, operation + " failed: " + e.getMessage());
    }
  }
}
