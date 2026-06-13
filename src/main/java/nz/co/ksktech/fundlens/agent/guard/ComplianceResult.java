package nz.co.ksktech.fundlens.agent.guard;

import java.util.List;

/** Structured verdict from the ComplianceAgent. */
public record ComplianceResult(Verdict verdict, List<String> issues) {

  /**
   * Creates a new ComplianceResult.
   *
   * @param verdict The compliance verdict.
   * @param issues A list of compliance issues. If null, an empty list is used.
   */
  public ComplianceResult {
    issues = issues == null ? List.of() : List.copyOf(issues);
  }

  /**
   * Creates a ComplianceResult with a BLOCK verdict.
   *
   * @param issue The reason for blocking.
   * @return A new ComplianceResult with a BLOCK verdict.
   */
  public static ComplianceResult block(String issue) {
    return new ComplianceResult(Verdict.BLOCK, List.of(issue));
  }
}
