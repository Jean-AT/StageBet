package com.service.stagebet.identity.infrastructure;

import com.service.stagebet.identity.domain.Role;
import java.util.Collection;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfiguration {

    private static final Set<String> SUPPORTED_ROLES = Set.of(
            Role.TENANT.name(), Role.OPERATOR.name(), Role.PLAYER.name(), Role.ADMIN.name(), Role.SUPPORT.name());

    @Bean
    JwtDecoder jwtDecoder(@Value("${stagebet.security.oidc.issuer-uri}") String issuerUri,
            @Value("${stagebet.security.oidc.jwk-set-uri}") String jwkSetUri,
            @Value("${stagebet.security.oidc.audience}") String audience) {
        requireConfigured("stagebet.security.oidc.issuer-uri", issuerUri);
        requireConfigured("stagebet.security.oidc.jwk-set-uri", jwkSetUri);
        requireConfigured("stagebet.security.oidc.audience", audience);

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuerUri), audienceValidator(audience), roleValidator()));
        return decoder;
    }

    @Bean
    @Order(1)
    SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/**")
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/payments/webhooks/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/support/**").hasAnyRole("ADMIN", "SUPPORT")
                        .requestMatchers("/api/operator/**").hasAnyRole("ADMIN", "OPERATOR")
                        .requestMatchers("/api/tenants/*/players/**").hasRole("PLAYER")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain applicationSecurity(HttpSecurity http) throws Exception {
        http.securityMatcher("/**")
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/topic/**", "/ws/**").permitAll()
                        .anyRequest().permitAll());
        return http.build();
    }

    private OAuth2TokenValidator<Jwt> audienceValidator(String audience) {
        OAuth2Error error = new OAuth2Error("invalid_token", "JWT audience is not accepted", null);
        return jwt -> jwt.getAudience().contains(audience)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(error);
    }

    private OAuth2TokenValidator<Jwt> roleValidator() {
        OAuth2Error error = new OAuth2Error("invalid_token", "JWT roles claim is missing or invalid", null);
        return jwt -> {
            Object claim = jwt.getClaim("roles");
            if (!(claim instanceof Collection<?> roles)
                    || roles.stream().noneMatch(role -> role instanceof String value && SUPPORTED_ROLES.contains(value))) {
                return OAuth2TokenValidatorResult.failure(error);
            }
            return OAuth2TokenValidatorResult.success();
        };
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    private void requireConfigured(String propertyName, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(propertyName + " must be configured; start the local Keycloak provider with Docker Compose or set the deployed OIDC values");
        }
    }
}
