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
import nz.co.ksktech.fundlens.api.ApiDtos.FundMetricsResponse;
import nz.co.ksktech.fundlens.api.ApiDtos.FundResponse;
import nz.co.ksktech.fundlens.domain.Fund;
import nz.co.ksktech.fundlens.domain.FundMetrics;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Path("/api/v1/funds")
@Tag(name = "Funds", description = "Fund catalogue and latest structured metrics")
@Produces(MediaType.APPLICATION_JSON)
public class FundResource {

    private final ObjectMapper objectMapper;

    public FundResource(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @GET
    @Operation(summary = "List all funds with their latest metrics")
    public List<FundResponse> list() {
        return Fund.<Fund>listAll().stream().map(this::toResponse).toList();
    }

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

    private FundResponse toResponse(Fund fund) {
        FundMetricsResponse metrics = FundMetrics.findLatestForFund(fund.id)
                .map(m -> new FundMetricsResponse(
                        m.periodEnd, m.totalAnnualFundCharge, m.managersBasicFee,
                        m.performanceBasedFees, m.contributionFee, m.withdrawalFee,
                        m.pastYearReturnNet, m.avgFiveYearReturnNet, m.marketIndexPastYearReturn,
                        m.totalFundValue, m.numberOfInvestors,
                        parseJson(m.investmentMix), parseJson(m.topTenInvestments)))
                .orElse(null);
        return new FundResponse(fund.id, fund.name, fund.provider, fund.status, fund.classification,
                fund.riskIndicator, fund.description, fund.discloseFundNumber, metrics);
    }

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
