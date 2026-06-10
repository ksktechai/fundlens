package nz.co.ksktech.fundlens.config;

import java.util.UUID;

/** The LLM backend failed or timed out; maps to a 503 problem detail. */
public class LlmUnavailableException extends RuntimeException {

  private final UUID auditId;

  public LlmUnavailableException(UUID auditId, Throwable cause) {
    super("LLM backend unavailable: " + cause.getMessage(), cause);
    this.auditId = auditId;
  }

  public UUID auditId() {
    return auditId;
  }
}
