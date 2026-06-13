package nz.co.ksktech.fundlens.disclose;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import nz.co.ksktech.fundlens.disclose.DiscloseDtos.DiscloseFund;
import nz.co.ksktech.fundlens.disclose.DiscloseDtos.DisclosePage;
import nz.co.ksktech.fundlens.disclose.DiscloseDtos.FundReturn;
import nz.co.ksktech.fundlens.disclose.DiscloseDtos.FundSummary;
import nz.co.ksktech.fundlens.disclose.DiscloseDtos.Offer;
import nz.co.ksktech.fundlens.disclose.DiscloseDtos.OfferSummary;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * MicroProfile REST client for the NZ Disclose Register API
 * (https://api.business.govt.nz/gateway/companies-office/disclose-register/v1). Auto-redirect is
 * disabled (follow-redirects=false): fund-update documents answer 302 and the Location must be
 * downloaded WITHOUT the subscription key.
 */
@Path("/")
@RegisterRestClient(configKey = "disclose-register")
@RegisterProvider(DiscloseAuthFilter.class)
public interface DiscloseRegisterClient {

  /**
   * Searches for offers.
   *
   * @param search the search term
   * @param type the offer type
   * @param status the offer status
   * @param page the page number
   * @param organisationId the organisation ID
   * @return a page of offer summaries
   */
  @GET
  @Path("/offers") // Search Offers
  DisclosePage<OfferSummary> searchOffers(
      @QueryParam("offer-or-issuer-name-or-number") String search,
      @QueryParam("type") String type,
      @QueryParam("status") String status,
      @QueryParam("page") Integer page,
      @HeaderParam("x-organisation") String organisationId);

  /**
   * Gets an offer by its number.
   *
   * @param offerNumber the offer number
   * @param organisationId the organisation ID
   * @return the offer
   */
  @GET
  @Path("/offer/{offer-number}") // Get Offer
  Offer getOffer(
      @PathParam("offer-number") String offerNumber,
      @HeaderParam("x-organisation") String organisationId);

  /**
   * Gets funds within an offer.
   *
   * @param offerNumber the offer number
   * @param page the page number
   * @param organisationId the organisation ID
   * @return a page of fund summaries
   */
  @GET
  @Path("/offer/{offer-number}/funds") // Funds within an offer
  DisclosePage<FundSummary> getOfferFunds(
      @PathParam("offer-number") String offerNumber,
      @QueryParam("page") Integer page,
      @HeaderParam("x-organisation") String organisationId);

  /**
   * Gets fund details by fund number.
   *
   * @param fundNumber the fund number
   * @param organisationId the organisation ID
   * @param etag the ETag
   * @return the disclose fund
   */
  @GET
  @Path("/fund/{fund-number}") // Get Fund details
  DiscloseFund getFund(
      @PathParam("fund-number") String fundNumber,
      @HeaderParam("x-organisation") String organisationId,
      @HeaderParam("If-None-Match") String etag);

  /**
   * Same operation as {@link #getFund} but exposing the raw response, so the sync can read the
   * {@code ETag} header and distinguish 304 Not Modified without exception-mapper gymnastics.
   *
   * @param fundNumber the fund number
   * @param organisationId the organisation ID
   * @param etag the ETag
   * @return the raw response
   */
  @GET
  @Path("/fund/{fund-number}")
  Response getFundRaw(
      @PathParam("fund-number") String fundNumber,
      @HeaderParam("x-organisation") String organisationId,
      @HeaderParam("If-None-Match") String etag);

  /**
   * Gets fund returns by fund number.
   *
   * @param fundNumber the fund number
   * @param page the page number
   * @param organisationId the organisation ID
   * @return a page of fund returns
   */
  @GET
  @Path("/fund/{fund-number}/fund-returns")
  DisclosePage<FundReturn> getFundReturns(
      @PathParam("fund-number") String fundNumber,
      @QueryParam("page") Integer page,
      @HeaderParam("x-organisation") String organisationId);

  /**
   * Gets the fund update document response.
   *
   * @param fundNumber the fund number
   * @param organisationId the organisation ID
   * @return the raw response, typically a redirect
   */
  @GET
  @Path("/fund/{fund-number}/fund-update-document")
  Response getFundUpdateDocument(
      @PathParam("fund-number") String fundNumber,
      @HeaderParam("x-organisation") String organisationId);
}
