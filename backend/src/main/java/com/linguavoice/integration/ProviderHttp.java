package com.linguavoice.integration;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.http.client.JdkClientHttpRequestFactory;

/** Shared HTTP settings for calls to AI providers. */
public final class ProviderHttp {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    private ProviderHttp() {
    }

    /** JDK HTTP client with a 5 s connect timeout and the given read timeout. */
    public static JdkClientHttpRequestFactory requestFactory(int readTimeoutSeconds) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));
        return factory;
    }
}
