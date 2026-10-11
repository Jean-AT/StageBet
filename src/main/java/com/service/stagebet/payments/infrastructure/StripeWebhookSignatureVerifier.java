package com.service.stagebet.payments.infrastructure;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class StripeWebhookSignatureVerifier {

    private final String secret;
    private final long toleranceSeconds;

    public StripeWebhookSignatureVerifier(
            @Value("${stagebet.payments.stripe.webhook-secret:local-webhook-secret}") String secret,
            @Value("${stagebet.payments.stripe.signature-tolerance-seconds:300}") long toleranceSeconds) {
        this.secret = secret;
        this.toleranceSeconds = toleranceSeconds;
    }

    public void verify(String payload, String signatureHeader, Instant now) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            throw new IllegalArgumentException("Stripe webhook signature is required");
        }
        String timestamp = value(signatureHeader, "t");
        String provided = value(signatureHeader, "v1");
        long signedAt;
        try {
            signedAt = Long.parseLong(timestamp);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Stripe webhook timestamp is invalid", exception);
        }
        if (Math.abs(now.getEpochSecond() - signedAt) > toleranceSeconds) {
            throw new IllegalArgumentException("Stripe webhook timestamp is outside the tolerance window");
        }
        String expected = sign(timestamp + "." + payload);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                provided.getBytes(StandardCharsets.US_ASCII))) {
            throw new IllegalArgumentException("Stripe webhook signature is invalid");
        }
    }

    public String signatureForTests(String payload, Instant timestamp) {
        return "t=" + timestamp.getEpochSecond() + ",v1=" + sign(timestamp.getEpochSecond() + "." + payload);
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("unable to sign Stripe webhook payload", exception);
        }
    }

    private String value(String header, String name) {
        return java.util.Arrays.stream(header.split(","))
                .map(String::trim)
                .filter(part -> part.startsWith(name + "="))
                .map(part -> part.substring(name.length() + 1))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Stripe webhook signature is incomplete"));
    }
}
