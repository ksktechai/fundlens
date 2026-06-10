package nz.co.ksktech.fundlens.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "sync_runs")
public class SyncRun extends PanacheEntityBase {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "started_at", nullable = false)
  public Instant startedAt;

  @Column(name = "finished_at")
  public Instant finishedAt;

  /** SCHEDULED or MANUAL. */
  @Column(name = "triggered_by", nullable = false)
  public String triggeredBy;

  /** SUCCESS, PARTIAL or FAILED. */
  @Column(nullable = false)
  public String status;

  /** JSON array of per-fund outcomes. */
  @Column(columnDefinition = "text")
  public String outcomes;
}
