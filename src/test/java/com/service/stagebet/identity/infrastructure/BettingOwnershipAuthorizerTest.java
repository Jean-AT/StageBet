package com.service.stagebet.identity.infrastructure;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class BettingOwnershipAuthorizerTest {

    private final BettingOwnershipAuthorizer authorizer = new BettingOwnershipAuthorizer();

    @Test
    void permitsOnlyTheTenantAndPlayerInTheJwtClaims() {
        UUID tenantId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();

        assertDoesNotThrow(() -> authorizer.authorize(principal(tenantId, playerId), tenantId, playerId));
        assertThrows(AccessDeniedException.class,
                () -> authorizer.authorize(principal(tenantId, playerId), UUID.randomUUID(), playerId));
        assertThrows(AccessDeniedException.class,
                () -> authorizer.authorize(principal(tenantId, playerId), tenantId, UUID.randomUUID()));
    }

    @Test
    void deniesMissingOrMalformedOwnershipClaims() {
        UUID tenantId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();

        assertThrows(AccessDeniedException.class,
                () -> authorizer.authorize(principal(Map.of("tenant_id", tenantId.toString())), tenantId, playerId));
        assertThrows(AccessDeniedException.class,
                () -> authorizer.authorize(principal(Map.of("tenant_id", "not-a-uuid", "player_id", playerId.toString())),
                        tenantId, playerId));
    }

    private JwtAuthenticationToken principal(UUID tenantId, UUID playerId) {
        return principal(Map.of("tenant_id", tenantId.toString(), "player_id", playerId.toString()));
    }

    private JwtAuthenticationToken principal(Map<String, Object> claims) {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .subject("test-player")
                .claims(values -> values.putAll(claims))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();
        return new JwtAuthenticationToken(jwt);
    }
}
