package nz.co.ksktech.fundlens.disclose;

/** Non-success response from the Disclose Register gateway (429, 5xx, ...). */
public class DiscloseApiException extends RuntimeException {

  private final int status;

  /**
   * Constructs a DiscloseApiException.
   *
   * @param status the HTTP status code
   * @param message the error message
   */
  public DiscloseApiException(int status, String message) {
    super("Disclose Register returned " + status + (message == null ? "" : ": " + message));
    this.status = status;
  }

  /**
   * Gets the HTTP status code.
   *
   * @return the status code
   */
  public int status() {
    return status;
  }
}
