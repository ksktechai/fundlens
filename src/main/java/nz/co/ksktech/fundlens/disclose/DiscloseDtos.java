package nz.co.ksktech.fundlens.disclose;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** DTOs modelling the Disclose Register API (api.business.govt.nz) response shapes. */
public final class DiscloseDtos {

    private DiscloseDtos() {
    }

    /** Shared pagination envelope. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DisclosePage<T>(
            Integer totalResults,
            Integer pageSize,
            Integer page,
            List<T> results,
            List<PageUrl> url) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PageUrl(String rel, String href) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OfferSummary(
            String offerNumber,
            String offerName,
            String issuerName,
            String offerStatus,
            String offerType) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Offer(
            String offerNumber,
            String offerName,
            String issuerName,
            String offerStatus,
            String offerType,
            String offerDescription) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FundSummary(
            String fundNumber,
            String fundName,
            String fundStatus) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FundReturn(
            String fundNumber,
            LocalDate reportingPeriodEndDate,
            BigDecimal pastYearReturnNet,
            BigDecimal marketIndexPastYearReturn) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InvestmentMixItem(
            String investmentType,
            String targetPercentageOrRange,
            BigDecimal actualPercentage) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TopTenInvestment(
            String assetName,
            BigDecimal assetProportion,
            String assetType,
            String assetCountry) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DiscloseFund(
            String fundNumber,
            String fundName,
            String offerNumber,
            String fundStatus,
            LocalDate reportingPeriodEndDate,
            String fundClassification,
            Integer riskIndicator,
            String fundDescription,
            BigDecimal totalFundValueAmount,
            Integer numberOfInvestors,
            BigDecimal pastYearReturnNet,
            BigDecimal averageFiveYearReturnNet,
            BigDecimal marketIndexPastYearReturn,
            BigDecimal totalAnnualFundCharge,
            BigDecimal managersBasicFee,
            BigDecimal performanceBasedFees,
            BigDecimal contributionFeesPercentage,
            BigDecimal withdrawalFeesPercentage,
            List<InvestmentMixItem> investmentMix,
            List<TopTenInvestment> topTenInvestments) {
    }
}
