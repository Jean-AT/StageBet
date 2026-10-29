package com.service.stagebet.identity.infrastructure;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.service.stagebet.betting.application.BettingService;
import com.service.stagebet.identity.application.CommandRateLimiter;
import com.service.stagebet.payments.application.StripeWebhookService;
import com.service.stagebet.payments.infrastructure.StripeWebhookSignatureVerifier;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class JwtRouteAuthorizationTest {

    private static final String ISSUER = "https://issuer.stagebet.test/realms/stagebet";
    private static final String AUDIENCE = "stagebet-api";
    private static RSAKey signingKey;
    private static final WireMockServer JWK_SERVER = startJwkServer();

    @Autowired private MockMvc mockMvc;
    @Autowired private StripeWebhookSignatureVerifier signatureVerifier;
    @MockitoBean private StripeWebhookService webhookService;
    @MockitoBean private BettingService bettingService;
    @MockitoBean private CommandRateLimiter rateLimiter;

    @AfterAll
    static void stopJwkServer() {
        JWK_SERVER.stop();
    }

    private static WireMockServer startJwkServer() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            signingKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                    .privateKey((RSAPrivateKey) keyPair.getPrivate())
                    .keyID("stagebet-test-key")
                    .build();
            WireMockServer server = new WireMockServer();
            server.start();
            server.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo("/jwks"))
                    .willReturn(aResponse().withHeader("Content-Type", "application/json")
                            .withBody(new JWKSet(signingKey.toPublicJWK()).toString())));
            return server;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to start the JWT test issuer", exception);
        }
    }

    @DynamicPropertySource
    static void oidcProperties(DynamicPropertyRegistry registry) {
        registry.add("stagebet.security.oidc.issuer-uri", () -> ISSUER);
        registry.add("stagebet.security.oidc.jwk-set-uri", () -> JWK_SERVER.baseUrl() + "/jwks");
        registry.add("stagebet.security.oidc.audience", () -> AUDIENCE);
    }

    @Test
    void protectedApiRejectsMissingInvalidExpiredAndWrongAudienceTokens() throws Exception {
        String path = "/api/admin/check";
        mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(path).header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(path).header("Authorization", "Bearer " + token("admin", List.of("ADMIN"), List.of(AUDIENCE),
                Instant.now().minusSeconds(600), Instant.now().minusSeconds(300))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(path).header("Authorization", "Bearer " + token("admin", List.of("ADMIN"), List.of("other-api"),
                Instant.now(), Instant.now().plusSeconds(300))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void signedTokensEnforceRolesAtHttpBoundary() throws Exception {
        mockMvc.perform(get("/api/admin/check").header("Authorization", "Bearer " + validToken("admin", List.of("ADMIN"))))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/admin/check").header("Authorization", "Bearer " + validToken("player", List.of("PLAYER"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/check").header("Authorization", "Bearer " + validToken("no-roles", List.of())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void signedStripeWebhookDoesNotRequireJwt() throws Exception {
        String payload = "{\"id\":\"evt_" + UUID.randomUUID() + "\"}";
        String signature = signatureVerifier.signatureForTests(payload, Instant.now());
        org.mockito.Mockito.when(webhookService.process(payload, signature))
                .thenReturn(new StripeWebhookService.WebhookResult("evt_test", true, "ACCEPTED"));

        mockMvc.perform(post("/api/payments/webhooks/stripe")
                        .header("Stripe-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());
    }

    @Test
    void signedJwtCannotUseAnotherTenantOrPlayerBettingPath() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        String token = token(playerId.toString(), List.of("PLAYER"), List.of(AUDIENCE), Instant.now(), Instant.now().plusSeconds(300), tenantId, playerId);

        mockMvc.perform(post("/api/tenants/{tenantId}/players/{playerId}/rounds/{roundId}/bets",
                        UUID.randomUUID(), playerId, UUID.randomUUID())
                        .header("Authorization", "Bearer " + token)
                        .header("X-Idempotency-Key", "cross-tenant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"betId\":\"" + UUID.randomUUID()
                                + "\",\"amount\":10.00,\"currency\":\"USD\",\"expectedRoundSequence\":1}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/tenants/{tenantId}/players/{playerId}/bets/{betId}/cash-out",
                        tenantId, UUID.randomUUID(), UUID.randomUUID())
                        .header("Authorization", "Bearer " + token)
                        .header("X-Idempotency-Key", "cross-player")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRoundSequence\":1}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(bettingService, rateLimiter);
    }

    @Test
    void signedPlayerJwtCanReachOwnedBettingRoute() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        String token = token(playerId.toString(), List.of("PLAYER"), List.of(AUDIENCE), Instant.now(), Instant.now().plusSeconds(300), tenantId, playerId);

        mockMvc.perform(post("/api/tenants/{tenantId}/players/{playerId}/rounds/{roundId}/bets", tenantId, playerId, UUID.randomUUID())
                        .header("Authorization", "Bearer " + token)
                        .header("X-Idempotency-Key", "owned-bet")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"betId\":\"" + UUID.randomUUID()
                                + "\",\"amount\":10.00,\"currency\":\"USD\",\"expectedRoundSequence\":1}"))
                .andExpect(status().isOk());

        verify(bettingService).placeBet(any());
    }

    private String validToken(String subject, List<String> roles) throws Exception {
        return token(subject, roles, List.of(AUDIENCE), Instant.now(), Instant.now().plusSeconds(300));
    }

    private String token(String subject, List<String> roles, List<String> audience, Instant issuedAt, Instant expiresAt) throws Exception {
        return token(subject, roles, audience, issuedAt, expiresAt, null, null);
    }

    private String token(String subject, List<String> roles, List<String> audience, Instant issuedAt, Instant expiresAt,
            UUID tenantId, UUID playerId) throws Exception {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject(subject)
                .audience(audience)
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(expiresAt))
                .claim("roles", roles);
        if (tenantId != null) {
            claims.claim("tenant_id", tenantId.toString()).claim("player_id", playerId.toString());
        }
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(signingKey.getKeyID()).type(JOSEObjectType.JWT).build(), claims.build());
        jwt.sign(new RSASSASigner(signingKey));
        return jwt.serialize();
    }
}
