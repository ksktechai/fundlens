package nz.co.ksktech.fundlens.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import nz.co.ksktech.fundlens.api.ApiDtos.FundMetricsResponse;
import nz.co.ksktech.fundlens.api.ApiDtos.FundResponse;
import nz.co.ksktech.fundlens.domain.Fund;
import nz.co.ksktech.fundlens.domain.FundMetrics;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/v1/funds")
@Tag(name = "Funds", description = "Fund catalogue and latest structured metrics")
@Produces(MediaType.APPLICATION_JSON)
public class FundResource {

  private final ObjectMapper objectMapper;

  /**
   * Constructor for FundResource.
   *
   * @param objectMapper the object mapper
   */
  public FundResource(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /**
   * Lists all funds.
   *
   * @return a list of all funds
   */
  @GET
  @Operation(summary = "List all funds with their latest metrics")
  public List<FundResponse> list() {
    return Fund.<Fund>listAll().stream().map(this::toResponse).toList();
  }

  /**
   * Gets a fund by its ID.
   *
   * @param id the fund ID
   * @return the fund
   * @throws NotFoundException if the fund is not found
   */
  @GET
  @Path("/{id}")
  @Operation(summary = "Get one fund with its latest metrics")
  public FundResponse get(@PathParam("id") Long id) {
    Fund fund = Fund.findById(id);
    if (fund == null) {
      throw new NotFoundException("No fund with id " + id);
    }
    return toResponse(fund);
  }

  /**
   * Converts a Fund entity to a FundResponse DTO.
   *
   * @param fund the fund entity
   * @return the fund response DTO
   */
  private FundResponse toResponse(Fund fund) {
    FundMetricsResponse metrics =
        FundMetrics.findLatestForFund(fund.id)
            .map(
                m ->
                    new FundMetricsResponse(
                        m.periodEnd,
                        m.totalAnnualFundCharge,
                        m.managersBasicFee,
                        m.performanceBasedFees,
                        m.contributionFee,
                        m.withdrawalFee,
                        m.pastYearReturnNet,
                        m.avgFiveYearReturnNet,
                        m.marketIndexPastYearReturn,
                        m.totalFundValue,
                        m.numberOfInvestors,
                        parseJson(m.investmentMix),
                        parseJson(m.topTenInvestments)))
            .orElse(null);
    return new FundResponse(
        fund.id,
        fund.name,
        fund.provider,
        fund.status,
        fund.classification,
        fund.riskIndicator,
        fund.description,
        fund.discloseFundNumber,
        metrics);
  }

  /**
   * Parses a JSON string to a JsonNode.
   *
   * @param json the JSON string
   * @return the JsonNode, or a text node if parsing fails
   */
  private JsonNode parseJson(String json) {
    if (json == null) {
      return null;
    }
    try {
      return objectMapper.readTree(json);
    } catch (JsonProcessingException e) {
      return objectMapper.getNodeFactory().textNode(json);
    }
  }
}
