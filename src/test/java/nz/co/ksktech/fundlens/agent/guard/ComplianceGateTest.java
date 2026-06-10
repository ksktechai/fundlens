package nz.co.ksktech.fundlens.agent.guard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import nz.co.ksktech.fundlens.agent.ComplianceAgent;
import org.junit.jupiter.api.Test;

/**
 * The compliance gate must be fail-safe: malformed or missing LLM output maps to BLOCK and never
 * throws to the client.
 */
class ComplianceGateTest {

  @Test
  void validPassResultIsReturnedUnchanged() {
    ComplianceResult pass = new ComplianceResult(Verdict.PASS, List.of());
    ComplianceGate gate = new ComplianceGate(agentReturning(pass));
    assertSame(Verdict.PASS, gate.review("draft", "findings").verdict());
  }

  @Test
  void reviseIssuesArePreserved() {
    ComplianceResult revise = new ComplianceResult(Verdict.REVISE, List.of("missing disclaimer"));
    ComplianceGate gate = new ComplianceGate(agentReturning(revise));
    ComplianceResult result = gate.review("draft", "findings");
    assertSame(Verdict.REVISE, result.verdict());
    assertEquals(List.of("missing disclaimer"), result.issues());
  }

  @Test
  void malformedLlmOutputMapsToBlock() {
    // The AI service throws when the LLM returns something that is not the JSON schema
    ComplianceAgent throwing =
        (draft, findings) -> {
          throw new RuntimeException("Could not parse LLM output: 'sure, here is your answer'");
        };
    ComplianceResult result = new ComplianceGate(throwing).review("draft", "findings");
    assertSame(Verdict.BLOCK, result.verdict());
    assertTrue(result.issues().getFirst().contains("Compliance evaluation failed"));
  }

  @Test
  void nullResultMapsToBlock() {
    ComplianceResult result = new ComplianceGate(agentReturning(null)).review("draft", "findings");
    assertSame(Verdict.BLOCK, result.verdict());
  }

  @Test
  void nullVerdictMapsToBlock() {
    ComplianceResult broken = new ComplianceResult(null, List.of("issue"));
    ComplianceResult result =
        new ComplianceGate(agentReturning(broken)).review("draft", "findings");
    assertSame(Verdict.BLOCK, result.verdict());
  }

  @Test
  void nullIssuesNormalisesToEmptyList() {
    ComplianceResult result = new ComplianceResult(Verdict.PASS, null);
    assertEquals(List.of(), result.issues());
  }

  private static ComplianceAgent agentReturning(ComplianceResult result) {
    return (draft, findings) -> result;
  }
}
