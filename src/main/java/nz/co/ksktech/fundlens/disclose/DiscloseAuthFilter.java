package nz.co.ksktech.fundlens.disclose;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * Adds the API-gateway subscription key to every Disclose Register request. Registered on {@link
 * DiscloseRegisterClient} via {@code @RegisterProvider}. The key must never travel to redirect
 * targets — the REST client is configured with follow-redirects=false and document downloads go
 * through {@link DocumentFetcher} without this filter.
 */
public class DiscloseAuthFilter implements ClientRequestFilter {

  static final String SUBSCRIPTION_KEY_HEADER = "Ocp-Apim-Subscription-Key";

  /**
   * Filters the client request to add the subscription key header.
   *
   * @param requestContext the client request context
   */
  @Override
  public void filter(ClientRequestContext requestContext) {
    String key = ConfigProvider.getConfig().getValue("disclose.api.subscription-key", String.class);
    requestContext.getHeaders().putSingle(SUBSCRIPTION_KEY_HEADER, key);
  }
}
