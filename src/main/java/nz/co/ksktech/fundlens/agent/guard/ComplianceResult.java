package nz.co.ksktech.fundlens.agent.guard;

import java.util.List;

/** Structured verdict from the ComplianceAgent. */
public record ComplianceResult(Verdict verdict, List<String> issues) {

    public ComplianceResult {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    public static ComplianceResult block(String issue) {
        return new ComplianceResult(Verdict.BLOCK, List.of(issue));
    }
}
