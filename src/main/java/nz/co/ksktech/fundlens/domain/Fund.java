package nz.co.ksktech.fundlens.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Optional;

@Entity
@Table(name = "funds")
public class Fund extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public String name;

    @Column(nullable = false)
    public String provider;

    @Column(name = "disclose_fund_number")
    public String discloseFundNumber;

    @Column(name = "disclose_offer_number")
    public String discloseOfferNumber;

    @Column(name = "disclose_etag")
    public String discloseEtag;

    public String status;

    public String classification;

    @Column(name = "risk_indicator")
    public Integer riskIndicator;

    @Column(columnDefinition = "text")
    public String description;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt = Instant.now();

    public static Optional<Fund> findByDiscloseFundNumber(String fundNumber) {
        return find("discloseFundNumber", fundNumber).firstResultOptional();
    }
}
