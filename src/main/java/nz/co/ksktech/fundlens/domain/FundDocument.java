package nz.co.ksktech.fundlens.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "fund_documents")
public class FundDocument extends PanacheEntityBase {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "fund_id")
  public Long fundId;

  @Column(nullable = false)
  public String title;

  public String provider;

  @Column(name = "doc_type", nullable = false)
  public String docType;

  @Column(name = "period_end")
  public LocalDate periodEnd;

  /** Where the document came from: UPLOAD, DISCLOSE_SYNC, DEV_SEED. */
  @Column(nullable = false)
  public String source;

  @Column(name = "chunk_count", nullable = false)
  public int chunkCount;

  @Column(name = "ingested_at", nullable = false)
  public Instant ingestedAt = Instant.now();
}
