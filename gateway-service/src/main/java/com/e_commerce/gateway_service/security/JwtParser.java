package com.e_commerce.gateway_service.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Component
@Slf4j
public class JwtParser {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();

    public record JwtClaims(String userId, String email, String role) {}

    private final byte[] secret;
    private final String issuer;

    public JwtParser(
            @Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.issuer:user-service}") String issuer
    ) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.issuer = issuer;
    }

    public Optional<JwtClaims> parse(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) return Optional.empty();

            String unsigned = parts[0] + "." + parts[1];
            if (!constantTimeEquals(sign(unsigned), parts[2])) return Optional.empty();

            Map<String, String> claims = parsePayload(
                    new String(BASE64_URL_DECODER.decode(parts[1]), StandardCharsets.UTF_8)
            );

            if (!issuer.equals(claims.get("iss"))) return Optional.empty();

            long exp = Long.parseLong(claims.getOrDefault("exp", "0"));
            if (Instant.now().getEpochSecond() >= exp) return Optional.empty();

            return Optional.of(new JwtClaims(
                    claims.get("sub"),
                    claims.get("email"),
                    claims.get("role")
            ));
        } catch (Exception e) {
            log.debug("JWT parse failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return BASE64_URL_ENCODER.encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("JWT signing failed", e);
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
        if (aBytes.length != bBytes.length) return false;
        int result = 0;
        for (int i = 0; i < aBytes.length; i++) result |= aBytes[i] ^ bBytes[i];
        return result == 0;
    }

    private Map<String, String> parsePayload(String json) {
        Map<String, String> map = new LinkedHashMap<>();
        String content = json.trim();
        if (content.startsWith("{")) content = content.substring(1);
        if (content.endsWith("}")) content = content.substring(0, content.length() - 1);
        for (String entry : content.split(",")) {
            String[] pair = entry.split(":", 2);
            if (pair.length == 2) map.put(unquote(pair[0].trim()), unquote(pair[1].trim()));
        }
        return map;
    }

    private String unquote(String v) {
        return (v.startsWith("\"") && v.endsWith("\""))
                ? v.substring(1, v.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\")
                : v;
    }
}
