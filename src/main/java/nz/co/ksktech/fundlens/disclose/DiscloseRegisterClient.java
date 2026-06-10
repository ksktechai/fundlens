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
 * (https://api.business.govt.nz/gateway/companies-office/disclose-register/v1).
 * Auto-redirect is disabled (follow-redirects=false): fund-update documents
 * answer 302 and the Location must be downloaded WITHOUT the subscription key.
 */
@Path("/")
@RegisterRestClient(configKey = "disclose-register")
@RegisterProvider(DiscloseAuthFilter.class)
public interface DiscloseRegisterClient {

    @GET
    @Path("/offers") // Search Offers
    DisclosePage<OfferSummary> searchOffers(
            @QueryParam("offer-or-issuer-name-or-number") String search,
            @QueryParam("type") String type,
            @QueryParam("status") String status,
            @QueryParam("page") Integer page,
            @HeaderParam("x-organisation") String organisationId);

    @GET
    @Path("/offer/{offer-number}") // Get Offer
    Offer getOffer(@PathParam("offer-number") String offerNumber,
                   @HeaderParam("x-organisation") String organisationId);

    @GET
    @Path("/offer/{offer-number}/funds") // Funds within an offer
    DisclosePage<FundSummary> getOfferFunds(@PathParam("offer-number") String offerNumber,
                                            @QueryParam("page") Integer page,
                                            @HeaderParam("x-organisation") String organisationId);

    @GET
    @Path("/fund/{fund-number}") // Get Fund details
    DiscloseFund getFund(@PathParam("fund-number") String fundNumber,
                         @HeaderParam("x-organisation") String organisationId,
                         @HeaderParam("If-None-Match") String etag);

    /**
     * Same operation as {@link #getFund} but exposing the raw response, so the
     * sync can read the {@code ETag} header and distinguish 304 Not Modified
     * without exception-mapper gymnastics.
     */
    @GET
    @Path("/fund/{fund-number}")
    Response getFundRaw(@PathParam("fund-number") String fundNumber,
                        @HeaderParam("x-organisation") String organisationId,
                        @HeaderParam("If-None-Match") String etag);

    @GET
    @Path("/fund/{fund-number}/fund-returns")
    DisclosePage<FundReturn> getFundReturns(@PathParam("fund-number") String fundNumber,
                                            @QueryParam("page") Integer page,
                                            @HeaderParam("x-organisation") String organisationId);

    @GET
    @Path("/fund/{fund-number}/fund-update-document")
    Response getFundUpdateDocument(@PathParam("fund-number") String fundNumber,
                                   @HeaderParam("x-organisation") String organisationId);
}
