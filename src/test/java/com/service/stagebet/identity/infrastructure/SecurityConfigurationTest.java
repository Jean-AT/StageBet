package com.service.stagebet.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SecurityConfigurationTest {

    private final SecurityConfiguration configuration = new SecurityConfiguration();

    @Test
    void refusesToStartJwtValidationWithoutEveryOidcSetting() {
        assertThatThrownBy(() -> configuration.jwtDecoder("", "https://issuer.example.test/jwks", "stagebet-api"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("stagebet.security.oidc.issuer-uri must be configured");
        assertThatThrownBy(() -> configuration.jwtDecoder("https://issuer.example.test", "", "stagebet-api"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("stagebet.security.oidc.jwk-set-uri must be configured");
        assertThatThrownBy(() -> configuration.jwtDecoder("https://issuer.example.test", "https://issuer.example.test/jwks", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("stagebet.security.oidc.audience must be configured");
    }
}
