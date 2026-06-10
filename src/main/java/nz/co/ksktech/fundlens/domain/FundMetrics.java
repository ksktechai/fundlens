package nz.co.ksktech.fundlens.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

@Entity
@Table(name = "fund_metrics")
public class FundMetrics extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fund_id")
    public Fund fund;

    @Column(name = "period_end", nullable = false)
    public LocalDate periodEnd;

    @Column(name = "total_annual_fund_charge")
    public BigDecimal totalAnnualFundCharge;

    @Column(name = "managers_basic_fee")
    public BigDecimal managersBasicFee;

    @Column(name = "performance_based_fees")
    public BigDecimal performanceBasedFees;

    @Column(name = "contribution_fee")
    public BigDecimal contributionFee;

    @Column(name = "withdrawal_fee")
    public BigDecimal withdrawalFee;

    @Column(name = "past_year_return_net")
    public BigDecimal pastYearReturnNet;

    @Column(name = "avg_five_year_return_net")
    public BigDecimal avgFiveYearReturnNet;

    @Column(name = "market_index_past_year_return")
    public BigDecimal marketIndexPastYearReturn;

    @Column(name = "total_fund_value")
    public BigDecimal totalFundValue;

    @Column(name = "number_of_investors")
    public Integer numberOfInvestors;

    @Column(name = "investment_mix", columnDefinition = "text")
    public String investmentMix;

    @Column(name = "top_ten_investments", columnDefinition = "text")
    public String topTenInvestments;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();

    public static Optional<FundMetrics> findLatestForFund(Long fundId) {
        return find("fund.id = ?1 order by periodEnd desc", fundId).firstResultOptional();
    }

    public static Optional<FundMetrics> findByFundAndPeriod(Long fundId, LocalDate periodEnd) {
        return find("fund.id = ?1 and periodEnd = ?2", fundId, periodEnd).firstResultOptional();
    }
}
