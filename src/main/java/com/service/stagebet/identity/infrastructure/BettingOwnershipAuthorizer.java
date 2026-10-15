package com.service.stagebet.identity.infrastructure;

import java.security.Principal;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class BettingOwnershipAuthorizer {

    public void authorize(Principal principal, UUID tenantId, UUID playerId) {
        if (!(principal instanceof JwtAuthenticationToken jwtAuthentication)) {
            throw new AccessDeniedException("A JWT principal is required to access betting resources");
        }

        Jwt jwt = jwtAuthentication.getToken();
        if (!tenantId.equals(uuidClaim(jwt, "tenant_id")) || !playerId.equals(uuidClaim(jwt, "player_id"))) {
            throw new AccessDeniedException("JWT subject does not own the requested betting resource");
        }
    }

    private UUID uuidClaim(Jwt jwt, String claimName) {
        Object claim = jwt.getClaim(claimName);
        if (!(claim instanceof String value)) {
            return null;
        }

        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
