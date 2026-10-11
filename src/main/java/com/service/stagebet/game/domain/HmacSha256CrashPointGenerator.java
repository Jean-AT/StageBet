package com.service.stagebet.game.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class HmacSha256CrashPointGenerator implements CrashPointGenerator {

    private static final BigDecimal HOUSE_EDGE = new BigDecimal("0.99");
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal MINIMUM_MULTIPLIER = new BigDecimal("1.01");

    @Override
    public GeneratedCrashPoint generate(String serverSeed, UUID roundId) {
        try {
            byte[] hash = hmac(serverSeed, roundId.toString());
            long randomBits = 0;
            for (int index = 0; index < Long.BYTES; index++) {
                randomBits = (randomBits << 8) | (hash[index] & 0xffL);
            }
            BigDecimal unit = new BigDecimal(Long.toUnsignedString(randomBits))
                    .divide(new BigDecimal("18446744073709551616"), 18, RoundingMode.DOWN);
            BigDecimal rawMultiplier = HOUSE_EDGE.divide(ONE.subtract(unit), 18, RoundingMode.DOWN);
            BigDecimal multiplier = rawMultiplier.setScale(2, RoundingMode.DOWN)
                    .max(MINIMUM_MULTIPLIER);
            return new GeneratedCrashPoint(multiplier, sha256(serverSeed));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("unable to generate crash point", exception);
        }
    }

    private byte[] hmac(String serverSeed, String message) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(serverSeed.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
    }

    private String sha256(String value) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
