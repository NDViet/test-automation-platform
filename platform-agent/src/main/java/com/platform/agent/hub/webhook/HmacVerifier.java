package com.platform.agent.hub.webhook;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * Verifies GitHub HMAC-SHA256 webhook signatures.
 * If webhookSecret is blank (dev mode) verification is skipped and always returns true.
 */
@Component
public class HmacVerifier {

    private static final Logger log = LoggerFactory.getLogger(HmacVerifier.class);

    @Value("${platform.agent.github.webhook-secret:}")
    private String webhookSecret;

    @PostConstruct
    void warnIfUnsecured() {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.warn("[Security] platform.agent.github.webhook-secret is not set — " +
                     "GitHub webhook signature verification is DISABLED. " +
                     "Set this property in production to prevent unauthenticated webhook delivery.");
        }
    }

    /**
     * Returns true if the signature header matches the expected "sha256=<hex>" of the payload,
     * or if webhookSecret is blank (dev mode — skip verification).
     */
    public boolean verify(String payload, String signatureHeader) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            return true;
        }
        if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
            return false;
        }
        String expected = "sha256=" + hmacSha256Hex(webhookSecret, payload);
        return constantTimeEquals(expected, signatureHeader);
    }

    private static String hmacSha256Hex(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute HMAC-SHA256", e);
        }
    }

    /** Constant-time string comparison to prevent timing attacks. */
    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
