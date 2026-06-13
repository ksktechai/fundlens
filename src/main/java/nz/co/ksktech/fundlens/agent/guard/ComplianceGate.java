package nz.co.ksktech.fundlens.agent.guard;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import nz.co.ksktech.fundlens.agent.ComplianceAgent;

/**
 * Fail-safe wrapper around the ComplianceAgent: any malformed LLM output, parse failure or upstream
 * error maps to BLOCK — a compliance check can never throw to the client and never silently passes.
 */
@ApplicationScoped
public class ComplianceGate {

  private final ComplianceAgent complianceAgent;

  /**
   * Constructs a ComplianceGate.
   *
   * @param complianceAgent the compliance agent
   */
  public ComplianceGate(ComplianceAgent complianceAgent) {
    this.complianceAgent = complianceAgent;
  }

  /**
   * Reviews a draft given findings. Returns BLOCK on failure.
   *
   * @param draft the drafted text
   * @param findings the research findings
   * @return the result of the compliance review
   */
  public ComplianceResult review(String draft, String findings) {
    try {
      return normalise(complianceAgent.review(draft, findings));
    } catch (Exception e) {
      Log.warnf(e, "Compliance review failed; blocking response");
      return ComplianceResult.block("Compliance evaluation failed: " + e.getMessage());
    }
  }

  /**
   * Normalises the result. Converts nulls to a BLOCK result.
   *
   * @param result the compliance result
   * @return the normalised compliance result
   */
  static ComplianceResult normalise(ComplianceResult result) {
    if (result == null || result.verdict() == null) {
      return ComplianceResult.block("Compliance agent returned no usable verdict");
    }
    return result;
  }
}
