package nz.co.ksktech.fundlens.disclose;

/** Non-success response from the Disclose Register gateway (429, 5xx, ...). */
public class DiscloseApiException extends RuntimeException {

    private final int status;

    public DiscloseApiException(int status, String message) {
        super("Disclose Register returned " + status + (message == null ? "" : ": " + message));
        this.status = status;
    }

    public int status() {
        return status;
    }
}
