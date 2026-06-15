package com.e_commerce.user_service.service;

import com.e_commerce.user_service.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class JwtService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();

    private final byte[] secret;
    private final String issuer;
    private final long expirationSeconds;

    public JwtService(
            @Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.issuer:user-service}") String issuer,
            @Value("${security.jwt.expiration-seconds:86400}") long expirationSeconds
    ) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.issuer = issuer;
        this.expirationSeconds = expirationSeconds;
    }

    public String generateToken(User user) {
        Instant now = Instant.now();
        String header = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
        String payload = """
                {"sub":"%s","email":"%s","role":"%s","iss":"%s","iat":%d,"exp":%d}
                """.formatted(
                user.getId(),
                escapeJson(user.getEmail()),
                user.getRole().name(),
                escapeJson(issuer),
                now.getEpochSecond(),
                now.plusSeconds(expirationSeconds).getEpochSecond()
        ).trim();

        String unsignedToken = base64Url(header) + "." + base64Url(payload);
        return unsignedToken + "." + sign(unsignedToken);
    }

    public Optional<JwtClaims> parseToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return Optional.empty();
            }

            String unsignedToken = parts[0] + "." + parts[1];
            if (!constantTimeEquals(sign(unsignedToken), parts[2])) {
                return Optional.empty();
            }

            Map<String, String> claims = parseJsonObject(new String(BASE64_URL_DECODER.decode(parts[1]), StandardCharsets.UTF_8));
            if (!issuer.equals(claims.get("iss"))) {
                return Optional.empty();
            }

            long expiresAt = Long.parseLong(claims.getOrDefault("exp", "0"));
            if (Instant.now().getEpochSecond() >= expiresAt) {
                return Optional.empty();
            }

            return Optional.of(new JwtClaims(
                    Long.valueOf(claims.get("sub")),
                    claims.get("email"),
                    claims.get("role"),
                    Instant.ofEpochSecond(expiresAt)
            ));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private String base64Url(String value) {
        return BASE64_URL_ENCODER.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return BASE64_URL_ENCODER.encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign JWT", exception);
        }
    }

    private boolean constantTimeEquals(String expected, String actual) {
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = actual.getBytes(StandardCharsets.UTF_8);
        if (expectedBytes.length != actualBytes.length) {
            return false;
        }

        int result = 0;
        for (int i = 0; i < expectedBytes.length; i++) {
            result |= expectedBytes[i] ^ actualBytes[i];
        }
        return result == 0;
    }

    private Map<String, String> parseJsonObject(String json) {
        Map<String, String> values = new LinkedHashMap<>();
        String content = json.trim();
        if (content.startsWith("{")) {
            content = content.substring(1);
        }
        if (content.endsWith("}")) {
            content = content.substring(0, content.length() - 1);
        }

        for (String entry : content.split(",")) {
            String[] pair = entry.split(":", 2);
            if (pair.length == 2) {
                values.put(unquote(pair[0].trim()), unquote(pair[1].trim()));
            }
        }
        return values;
    }

    private String unquote(String value) {
        if (value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1)
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
        }
        return value;
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    public record JwtClaims(Long userId, String email, String role, Instant expiresAt) {
    }
}
