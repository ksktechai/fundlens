package nz.co.ksktech.fundlens.disclose;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import nz.co.ksktech.fundlens.disclose.DiscloseDtos.DiscloseFund;
import nz.co.ksktech.fundlens.disclose.DiscloseService.FundFetchResult;
import nz.co.ksktech.fundlens.domain.Fund;
import nz.co.ksktech.fundlens.domain.FundMetrics;
import nz.co.ksktech.fundlens.domain.SyncRun;
import nz.co.ksktech.fundlens.ingest.IngestionService;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Nightly (and manually triggered) ETag-based incremental sync from the Disclose Register. One fund
 * failing never aborts the rest; every run is recorded as a {@link SyncRun} row with per-fund
 * outcomes.
 */
@ApplicationScoped
public class DiscloseSyncService {

  public record FundOutcome(
      String fundNumber, String status, String message, boolean documentIngested) {}

  public record SyncSummary(
      Long runId,
      Instant startedAt,
      Instant finishedAt,
      String status,
      List<FundOutcome> outcomes) {}

  private final DiscloseService discloseService;
  private final DocumentFetcher documentFetcher;
  private final IngestionService ingestionService;
  private final ObjectMapper objectMapper;
  private final Optional<List<String>> configuredFundNumbers;

  /**
   * Constructs a DiscloseSyncService.
   *
   * @param discloseService the disclose service
   * @param documentFetcher the document fetcher
   * @param ingestionService the ingestion service
   * @param objectMapper the object mapper
   * @param configuredFundNumbers the configured fund numbers to sync
   */
  public DiscloseSyncService(
      DiscloseService discloseService,
      DocumentFetcher documentFetcher,
      IngestionService ingestionService,
      ObjectMapper objectMapper,
      @ConfigProperty(name = "disclose.sync.fund-numbers")
          Optional<List<String>> configuredFundNumbers) {
    this.discloseService = discloseService;
    this.documentFetcher = documentFetcher;
    this.ingestionService = ingestionService;
    this.objectMapper = objectMapper;
    this.configuredFundNumbers = configuredFundNumbers;
  }

  /** Nightly scheduled sync job. */
  @Scheduled(cron = "0 0 3 * * ?")
  void nightly() {
    if (configuredFundNumbers.map(List::isEmpty).orElse(true)) {
      Log.info("Disclose sync skipped: no fund numbers configured");
      return;
    }
    run("SCHEDULED");
  }

  /**
   * Runs the sync for configured funds.
   *
   * @param trigger the trigger source (e.g., SCHEDULED or MANUAL)
   * @return the sync summary
   */
  public SyncSummary run(String trigger) {
    return run(configuredFundNumbers.orElse(List.of()), trigger);
  }

  /**
   * Runs the sync for a specific list of fund numbers.
   *
   * @param fundNumbers the list of fund numbers
   * @param trigger the trigger source
   * @return the sync summary
   */
  public SyncSummary run(List<String> fundNumbers, String trigger) {
    Instant started = Instant.now();
    List<FundOutcome> outcomes = new ArrayList<>();
    for (String fundNumber : fundNumbers) {
      try {
        outcomes.add(syncFund(fundNumber.trim()));
      } catch (Exception e) {
        Log.errorf(e, "Disclose sync failed for fund %s", fundNumber);
        outcomes.add(new FundOutcome(fundNumber.trim(), "FAILED", e.getMessage(), false));
      }
    }
    Instant finished = Instant.now();
    String status = overallStatus(outcomes);
    Long runId = persistRun(started, finished, trigger, status, outcomes);
    return new SyncSummary(runId, started, finished, status, outcomes);
  }

  /**
   * Syncs a single fund.
   *
   * @param fundNumber the fund number
   * @return the outcome for the fund
   */
  private FundOutcome syncFund(String fundNumber) {
    String storedEtag =
        QuarkusTransaction.requiringNew()
            .call(
                () ->
                    Fund.findByDiscloseFundNumber(fundNumber)
                        .map(f -> f.discloseEtag)
                        .orElse(null));

    FundFetchResult result = discloseService.fetchFund(fundNumber, storedEtag);
    if (result.notModified()) {
      return new FundOutcome(fundNumber, "NOT_MODIFIED", null, false);
    }

    Long fundId = QuarkusTransaction.requiringNew().call(() -> upsert(fundNumber, result));
    DiscloseFund fund = result.fund();

    try {
      String location = discloseService.fetchFundUpdateDocumentLocation(fundNumber);
      byte[] document = documentFetcher.fetch(location);
      ingestionService.ingest(
          document,
          fund.fundName() + " - fund update.pdf",
          new IngestionService.IngestionRequest(
              fundId,
              providerOf(fund),
              "FUND_UPDATE",
              fund.reportingPeriodEndDate(),
              fund.fundName() + " fund update (" + fund.reportingPeriodEndDate() + ")",
              "DISCLOSE_SYNC"));
      return new FundOutcome(fundNumber, "UPDATED", null, true);
    } catch (Exception e) {
      Log.warnf(e, "Fund %s metrics updated but document ingestion failed", fundNumber);
      return new FundOutcome(
          fundNumber, "UPDATED", "document ingestion failed: " + e.getMessage(), false);
    }
  }

  /**
   * Upserts the fund data.
   *
   * @param fundNumber the fund number
   * @param result the fetched result
   * @return the fund ID
   */
  private Long upsert(String fundNumber, FundFetchResult result) {
    DiscloseFund source = result.fund();
    Fund fund =
        Fund.findByDiscloseFundNumber(fundNumber)
            .orElseGet(
                () -> {
                  Fund created = new Fund();
                  created.discloseFundNumber = fundNumber;
                  created.provider = "Disclose Register";
                  return created;
                });
    fund.name = source.fundName() != null ? source.fundName() : fund.name;
    fund.discloseOfferNumber = source.offerNumber();
    fund.discloseEtag = result.etag();
    fund.status = source.fundStatus();
    fund.classification = source.fundClassification();
    fund.riskIndicator = source.riskIndicator();
    fund.description = source.fundDescription();
    fund.updatedAt = Instant.now();
    fund.persist();

    FundMetrics metrics =
        FundMetrics.findByFundAndPeriod(fund.id, source.reportingPeriodEndDate())
            .orElseGet(
                () -> {
                  FundMetrics created = new FundMetrics();
                  created.fund = fund;
                  created.periodEnd = source.reportingPeriodEndDate();
                  return created;
                });
    metrics.totalAnnualFundCharge = source.totalAnnualFundCharge();
    metrics.managersBasicFee = source.managersBasicFee();
    metrics.performanceBasedFees = source.performanceBasedFees();
    metrics.contributionFee = source.contributionFeesPercentage();
    metrics.withdrawalFee = source.withdrawalFeesPercentage();
    metrics.pastYearReturnNet = source.pastYearReturnNet();
    metrics.avgFiveYearReturnNet = source.averageFiveYearReturnNet();
    metrics.marketIndexPastYearReturn = source.marketIndexPastYearReturn();
    metrics.totalFundValue = source.totalFundValueAmount();
    metrics.numberOfInvestors = source.numberOfInvestors();
    metrics.investmentMix = toJson(source.investmentMix());
    metrics.topTenInvestments = toJson(source.topTenInvestments());
    metrics.persist();
    return fund.id;
  }

  /**
   * Persists a sync run record.
   *
   * @param started the start time
   * @param finished the end time
   * @param trigger the trigger source
   * @param status the overall status
   * @param outcomes the fund outcomes
   * @return the ID of the persisted run
   */
  private Long persistRun(
      Instant started,
      Instant finished,
      String trigger,
      String status,
      List<FundOutcome> outcomes) {
    return QuarkusTransaction.requiringNew()
        .call(
            () -> {
              SyncRun run = new SyncRun();
              run.startedAt = started;
              run.finishedAt = finished;
              run.triggeredBy = trigger;
              run.status = status;
              run.outcomes = toJson(outcomes);
              run.persist();
              return run.id;
            });
  }

  /**
   * Calculates the overall status from outcomes.
   *
   * @param outcomes the list of outcomes
   * @return the overall status
   */
  private static String overallStatus(List<FundOutcome> outcomes) {
    boolean anyFailed = outcomes.stream().anyMatch(o -> "FAILED".equals(o.status()));
    boolean anySucceeded = outcomes.stream().anyMatch(o -> !"FAILED".equals(o.status()));
    if (!anyFailed) {
      return "SUCCESS";
    }
    return anySucceeded ? "PARTIAL" : "FAILED";
  }

  /**
   * Gets the provider name from a DiscloseFund.
   *
   * @param fund the fund
   * @return the provider string
   */
  private static String providerOf(DiscloseFund fund) {
    return fund.offerNumber() != null
        ? "Disclose offer " + fund.offerNumber()
        : "Disclose Register";
  }

  /**
   * Converts an object to JSON.
   *
   * @param value the object
   * @return the JSON string
   */
  private String toJson(Object value) {
    try {
      return value == null ? null : objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialise sync payload", e);
    }
  }
}
