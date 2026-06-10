package nz.co.ksktech.fundlens.tools;

import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.math.RoundingMode;

@ApplicationScoped
public class FeeCalculatorTool {

    @Tool("Calculate the projected annual fee cost in dollars for a given balance " +
            "and total annual fund charge percentage.")
    public String projectedAnnualFeeCost(double balanceDollars, double annualFeePercent) {
        if (balanceDollars < 0 || annualFeePercent < 0) {
            return "Balance and fee percentage must both be non-negative.";
        }
        BigDecimal cost = BigDecimal.valueOf(balanceDollars)
                .multiply(BigDecimal.valueOf(annualFeePercent))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return "A balance of $%s at a total annual fund charge of %s%% costs approximately $%s per year in fees."
                .formatted(BigDecimal.valueOf(balanceDollars).stripTrailingZeros().toPlainString(),
                        BigDecimal.valueOf(annualFeePercent).stripTrailingZeros().toPlainString(),
                        cost.toPlainString());
    }
}
