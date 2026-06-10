package nz.co.ksktech.fundlens.agent;

public final class Disclaimers {

  public static final String GENERAL_INFO =
      "This is general information only and not financial advice. It does not take your personal "
          + "circumstances into account. Consider seeking advice from a licensed financial advice "
          + "provider before making investment decisions.";

  public static final String FALLBACK_MESSAGE =
      "We are unable to provide an answer to this question that meets our compliance requirements. "
          + "Please rephrase the question, or consult the official fund documentation on the "
          + "Disclose Register (disclose-register.companiesoffice.govt.nz).\n\n"
          + GENERAL_INFO;

  private Disclaimers() {}
}
