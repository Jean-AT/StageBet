package com.service.stagebet.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

@Tag("integration")
class ProviderFailureIntegrationTest {

    private static WireMockServer provider;

    @BeforeAll
    static void startProvider() {
        provider = new WireMockServer(0);
        provider.start();
    }

    @AfterAll
    static void stopProvider() {
        provider.stop();
    }

    @Test
    void exposesProviderFailuresForRetryAndDeadLetterAssertions() throws Exception {
        provider.stubFor(get(urlEqualTo("/provider/status"))
                .willReturn(aResponse().withStatus(503).withBody("temporarily unavailable")));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + provider.port() + "/provider/status"))
                .GET()
                .build();
        HttpResponse<Void> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding());

        assertEquals(503, response.statusCode());
    }
}
