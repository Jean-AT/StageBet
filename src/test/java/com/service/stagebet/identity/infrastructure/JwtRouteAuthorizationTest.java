package com.service.stagebet.identity.infrastructure;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.service.stagebet.payments.infrastructure.StripeWebhookSignatureVerifier;
import com.service.stagebet.payments.application.StripeWebhookService;
import com.service.stagebet.betting.application.BettingService;
import com.service.stagebet.identity.application.CommandRateLimiter;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://issuer.example.test")
class JwtRouteAuthorizationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private StripeWebhookSignatureVerifier signatureVerifier;
    @MockitoBean private JwtDecoder jwtDecoder;
    @MockitoBean private StripeWebhookService webhookService;
    @MockitoBean private BettingService bettingService;
    @MockitoBean private CommandRateLimiter rateLimiter;

    @Test
    void protectedApiRejectsMissingAndInvalidTokens() throws Exception {
        mockMvc.perform(get("/api/tenants/{tenantId}/players/{playerId}/bets", UUID.randomUUID(), UUID.randomUUID()))
                .andExpect(status().isUnauthorized());

        when(jwtDecoder.decode("invalid")).thenThrow(new BadJwtException("invalid test token"));
        mockMvc.perform(get("/api/tenants/{tenantId}/players/{playerId}/bets", UUID.randomUUID(), UUID.randomUUID())
                        .header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void jwtChainEnforcesPrivilegedRoutes() throws Exception {
        when(jwtDecoder.decode("player")).thenReturn(token("player", "PLAYER"));
        when(jwtDecoder.decode("admin")).thenReturn(token("admin", "ADMIN"));

        for (String path : List.of("/api/admin/check", "/api/support/check", "/api/operator/check")) {
            mockMvc.perform(get(path).header("Authorization", "Bearer player"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get(path).header("Authorization", "Bearer admin"))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void signedStripeWebhookDoesNotRequireJwt() throws Exception {
        String payload = "{\"id\":\"evt_" + UUID.randomUUID() + "\"}";
        String signature = signatureVerifier.signatureForTests(payload, Instant.now());
        when(webhookService.process(payload, signature))
                .thenReturn(new StripeWebhookService.WebhookResult("evt_test", true, "ACCEPTED"));

        mockMvc.perform(post("/api/payments/webhooks/stripe")
                        .header("Stripe-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());
    }

    @Test
    void jwtCannotUseAnotherTenantOrPlayersBettingPath() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        when(jwtDecoder.decode("player-token")).thenReturn(token(tenantId, playerId, "PLAYER"));

        mockMvc.perform(post("/api/tenants/{tenantId}/players/{playerId}/rounds/{roundId}/bets",
                        UUID.randomUUID(), playerId, UUID.randomUUID())
                        .header("Authorization", "Bearer player-token")
                        .header("X-Idempotency-Key", "cross-tenant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"betId\":\"" + UUID.randomUUID()
                                + "\",\"amount\":10.00,\"currency\":\"USD\",\"expectedRoundSequence\":1}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/tenants/{tenantId}/players/{playerId}/bets/{betId}/cash-out",
                        tenantId, UUID.randomUUID(), UUID.randomUUID())
                        .header("Authorization", "Bearer player-token")
                        .header("X-Idempotency-Key", "cross-player")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRoundSequence\":1}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(bettingService, rateLimiter);
    }

    private Jwt token(String subject, String role) {
        Instant now = Instant.now();
        return Jwt.withTokenValue(subject)
                .header("alg", "RS256")
                .subject(subject)
                .claim("roles", List.of(role))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();
    }

    private Jwt token(UUID tenantId, UUID playerId, String role) {
        Instant now = Instant.now();
        return Jwt.withTokenValue(playerId.toString())
                .header("alg", "RS256")
                .subject(playerId.toString())
                .claim("tenant_id", tenantId.toString())
                .claim("player_id", playerId.toString())
                .claim("roles", List.of(role))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();
    }
}
