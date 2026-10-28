package com.rbctcsworld.ecommerce.notification;

import com.rbctcsworld.ecommerce.common.exception.ServiceUnavailableException;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Function;

/** GET of an SNS URL (already checked by SnsMessageVerifier.snsUrl): the signing certificate, or the subscribe link. */
@Component
public class SnsHttp implements Function<URI, byte[]> {

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    @Override
    public byte[] apply(URI uri) {
        try {
            HttpResponse<byte[]> r = http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (r.statusCode() != 200) {
                throw new ServiceUnavailableException("Amazon SNS answered HTTP " + r.statusCode());
            }
            return r.body();
        } catch (IOException e) {
            throw new ServiceUnavailableException("Amazon SNS not reachable");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceUnavailableException("Interrupted");
        }
    }
}
