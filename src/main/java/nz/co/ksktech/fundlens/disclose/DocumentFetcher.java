package nz.co.ksktech.fundlens.disclose;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Plain HTTP download of the redirect target of a fund-update-document call.
 * Deliberately NOT the REST client: the Location URL points outside the API
 * gateway and must be fetched without the Ocp-Apim-Subscription-Key or
 * x-organisation headers.
 */
@ApplicationScoped
public class DocumentFetcher {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public byte[] fetch(String url) {
        Log.infof(">>> GET %s (document download, no API headers)", url);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new DiscloseApiException(response.statusCode(), "document download failed for " + url);
            }
            Log.infof("<<< %d %s (%d bytes, %s)", response.statusCode(), url, response.body().length,
                    response.headers().firstValue("Content-Type").orElse("unknown content type"));
            return response.body();
        } catch (IOException e) {
            throw new DiscloseApiException(0, "document download failed for " + url + ": " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DiscloseApiException(0, "document download interrupted for " + url);
        }
    }
}
