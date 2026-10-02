package com.mindpulse;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Date;

@Service
public class JwtService {
    private static final long TTL_MS = 8 * 3600_000L;
    private final SecretKey key;

    public JwtService(@Value("${mindpulse.jwt.secret:}") String secret) {
        byte[] bytes;
        if (secret != null && secret.length() >= 32) bytes = secret.getBytes(StandardCharsets.UTF_8);
        else {
            bytes = new byte[48];
            new SecureRandom().nextBytes(bytes);
            System.out.println("WARN: MINDPULSE_JWT_SECRET not set (needs 32+ chars). Using a random key; tokens reset on restart.");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    public String create(UserStore.User u) {
        long now = System.currentTimeMillis();
        return Jwts.builder().subject(u.email()).claim("role", u.role()).claim("name", u.name())
                .issuedAt(new Date(now)).expiration(new Date(now + TTL_MS)).signWith(key).compact();
    }

    public Claims parse(String token) { return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload(); }
}
