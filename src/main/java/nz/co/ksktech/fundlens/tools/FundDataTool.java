package nz.co.ksktech.fundlens.tools;

import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import nz.co.ksktech.fundlens.domain.Fund;
import nz.co.ksktech.fundlens.domain.FundMetrics;

/**
 * Deterministic lookups against the FundMetrics table — the LLM is never allowed to "remember" a
 * fee or return figure.
 */
@ApplicationScoped
public class FundDataTool {

  /**
   * Get the latest fees, returns and key facts for a KiwiSaver fund. Accepts the fund name (or part
   * of it) or the numeric fund id.
   *
   * @param fundNameOrId The name or ID of the fund.
   * @return A string containing the latest fund metrics.
   */
  @Tool(
      "Get the latest fees, returns and key facts for a KiwiSaver fund. "
          + "Accepts the fund name (or part of it) or the numeric fund id.")
  public String getLatestFundMetrics(String fundNameOrId) {
    Optional<Fund> fund = resolve(fundNameOrId);
    if (fund.isEmpty()) {
      return "No fund found matching '" + fundNameOrId + "'.";
    }
    Fund f = fund.get();
    Optional<FundMetrics> latest = FundMetrics.findLatestForFund(f.id);
    if (latest.isEmpty()) {
      return "Fund '" + f.name + "' exists but has no metrics recorded.";
    }
    FundMetrics m = latest.get();
    return """
                Fund: %s (provider: %s), period ended %s.
                Total annual fund charge: %s%%. Manager's basic fee: %s%%. Performance-based fees: %s%%.
                Past year return (net): %s%%. Five-year average return (net): %s%%. Market index past year: %s%%.
                Fund size: $%s. Investors: %s. Risk indicator: %s of 7.
                Investment mix: %s
                """
        .formatted(
            f.name,
            f.provider,
            m.periodEnd,
            nullable(m.totalAnnualFundCharge),
            nullable(m.managersBasicFee),
            nullable(m.performanceBasedFees),
            nullable(m.pastYearReturnNet),
            nullable(m.avgFiveYearReturnNet),
            nullable(m.marketIndexPastYearReturn),
            nullable(m.totalFundValue),
            nullable(m.numberOfInvestors),
            nullable(f.riskIndicator),
            m.investmentMix == null ? "not recorded" : m.investmentMix);
  }

  /**
   * Get the latest asset allocation (investment mix) and top ten holdings for a KiwiSaver fund.
   *
   * @param fundNameOrId The name or ID of the fund.
   * @return A string containing the asset allocation details.
   */
  @Tool(
      "Get the latest asset allocation (investment mix) and top ten holdings for a KiwiSaver fund.")
  public String getAssetAllocation(String fundNameOrId) {
    Optional<Fund> fund = resolve(fundNameOrId);
    if (fund.isEmpty()) {
      return "No fund found matching '" + fundNameOrId + "'.";
    }
    Fund f = fund.get();
    Optional<FundMetrics> latest = FundMetrics.findLatestForFund(f.id);
    if (latest.isEmpty()) {
      return "Fund '" + f.name + "' exists but has no metrics recorded.";
    }
    FundMetrics m = latest.get();
    return "Fund: %s, period ended %s.%nInvestment mix: %s%nTop ten investments: %s"
        .formatted(
            f.name,
            m.periodEnd,
            m.investmentMix == null ? "not recorded" : m.investmentMix,
            m.topTenInvestments == null ? "not recorded" : m.topTenInvestments);
  }

  /**
   * Resolves a fund from its name or ID.
   *
   * @param fundNameOrId The name or ID of the fund.
   * @return An Optional containing the Fund if found, otherwise empty.
   */
  private static Optional<Fund> resolve(String fundNameOrId) {
    if (fundNameOrId == null || fundNameOrId.isBlank()) {
      return Optional.empty();
    }
    String trimmed = fundNameOrId.trim();
    if (trimmed.chars().allMatch(Character::isDigit)) {
      return Optional.ofNullable(Fund.findById(Long.parseLong(trimmed)));
    }
    return Fund.<Fund>find("lower(name) like ?1", "%" + trimmed.toLowerCase() + "%")
        .firstResultOptional();
  }

  /**
   * Returns "not recorded" for null values, otherwise the string representation of the object.
   *
   * @param value The object to check.
   * @return The string representation or "not recorded".
   */
  private static String nullable(Object value) {
    return value == null ? "not recorded" : value.toString();
  }
}
