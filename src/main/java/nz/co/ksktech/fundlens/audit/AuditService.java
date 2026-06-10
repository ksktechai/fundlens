package nz.co.ksktech.fundlens.audit;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.Optional;
import java.util.UUID;
import nz.co.ksktech.fundlens.domain.AuditRecord;

/**
 * Append-only audit persistence. REQUIRES_NEW so the trail is committed even when the surrounding
 * request fails (BLOCK, upstream timeout, ...). There is deliberately no update or delete path, and
 * the table has a trigger rejecting both.
 */
@ApplicationScoped
public class AuditService {

  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public UUID append(AuditRecord record) {
    record.persist();
    return record.id;
  }

  public Optional<AuditRecord> find(UUID id) {
    return AuditRecord.findByIdOptional(id);
  }
}
