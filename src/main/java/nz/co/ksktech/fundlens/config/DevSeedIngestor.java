package nz.co.ksktech.fundlens.config;

import io.quarkus.logging.Log;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.enterprise.event.Observes;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.Map;
import nz.co.ksktech.fundlens.domain.Fund;
import nz.co.ksktech.fundlens.domain.FundDocument;
import nz.co.ksktech.fundlens.ingest.IngestionService;

/**
 * Dev-mode only: ingests the sample fund updates under src/main/resources/seed at startup so RAG
 * answers work without any external setup.
 */
@ApplicationScoped
public class DevSeedIngestor {

  private static final Map<String, String> SEED_FILES =
      Map.of(
          "seed/westpac-active-growth-update-mar-2026.txt", "Westpac Active Growth",
          "seed/simplicity-growth-update-mar-2026.txt", "Simplicity Growth");

  private final IngestionService ingestionService;

  public DevSeedIngestor(IngestionService ingestionService) {
    this.ingestionService = ingestionService;
  }

  @ActivateRequestContext
  void onStart(@Observes StartupEvent event) {
    if (LaunchMode.current() != LaunchMode.DEVELOPMENT) {
      return;
    }
    try {
      if (FundDocument.count("source", "DEV_SEED") > 0) {
        Log.info("Dev seed documents already ingested; skipping");
        return;
      }
      SEED_FILES.forEach(this::ingestSeed);
    } catch (Exception e) {
      // Best-effort only: dev mode must still start when the Ollama host
      // is unreachable or the embedding model isn't pulled yet.
      Log.warnf(
          "Dev seed ingestion skipped: %s (is the embedding model pulled on the Ollama host? "
              + "ollama pull nomic-embed-text)",
          e.getMessage());
    }
  }

  private void ingestSeed(String resourcePath, String fundName) {
    try (InputStream in =
        Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath)) {
      if (in == null) {
        Log.warnf("Seed resource %s not found", resourcePath);
        return;
      }
      byte[] data = in.readAllBytes();
      Long fundId =
          Fund.<Fund>find("name", fundName).firstResultOptional().map(f -> f.id).orElse(null);
      String provider = fundName.split(" ")[0];
      ingestionService.ingest(
          data,
          resourcePath,
          new IngestionService.IngestionRequest(
              fundId,
              provider,
              "FUND_UPDATE",
              LocalDate.of(2026, 3, 31),
              fundName + " fund update (Mar 2026)",
              "DEV_SEED"));
    } catch (IOException | RuntimeException e) {
      Log.warnf("Failed to ingest seed resource %s: %s", resourcePath, e.getMessage());
    }
  }
}
