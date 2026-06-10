package nz.co.ksktech.fundlens.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Append-only decision trail for one explain request. Never updated or deleted — a database trigger
 * rejects mutation (see V1 migration).
 */
@Entity
@Table(name = "audit_records")
public class AuditRecord extends PanacheEntityBase {

  @Id public UUID id = UUID.randomUUID();

  @Column(name = "created_at", nullable = false)
  public Instant createdAt = Instant.now();

  @Column(nullable = false, columnDefinition = "text")
  public String question;

  public String audience;

  @Column(name = "fund_ids", columnDefinition = "text")
  public String fundIds;

  @Column(name = "retrieved_chunks", columnDefinition = "text")
  public String retrievedChunks;

  @Column(name = "research_findings", columnDefinition = "text")
  public String researchFindings;

  @Column(columnDefinition = "text")
  public String drafts;

  @Column(name = "compliance_results", columnDefinition = "text")
  public String complianceResults;

  @Column(name = "final_answer", columnDefinition = "text")
  public String finalAnswer;

  /** ANSWERED, BLOCKED or UPSTREAM_ERROR. */
  @Column(nullable = false)
  public String status;

  @Column(name = "model_name")
  public String modelName;

  @Column(name = "latency_ms", nullable = false)
  public long latencyMs;
}
