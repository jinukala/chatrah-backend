// src/main/java/com/chatrah/school/security/JwtService.java
package com.chatrah.school.security;

import com.chatrah.school.entity.User;
import io.smallrye.jwt.build.Jwt;
import io.smallrye.jwt.build.JwtClaimsBuilder;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * Service responsible for generating signed JWT tokens
 * for authenticated users, using the configured private key.
 */
@ApplicationScoped
public class JwtService {

    private static final String ISSUER = "chatrah-school";

    /**
     * Generate a signed JWT token for the given user.
     *
     * @param user authenticated user entity
     * @return signed JWT as String
     */
    public String generateToken(User user) {
        Set<String> groups = new HashSet<>();
        if (user.getRole() != null) {
            groups.add(user.getRole());
        }

        Instant now = Instant.now();
        Instant expiry = now.plus(Duration.ofHours(1));

        JwtClaimsBuilder builder = Jwt.issuer(ISSUER)
                .upn(user.getUsername())
                .subject(String.valueOf(user.getId()))
                .groups(groups)
                .claim("role", user.getRole());

        if (user.getStudentId() != null) {
            builder.claim("studentId", user.getStudentId());
        }
        if (user.getTeacherId() != null) {
            builder.claim("teacherId", user.getTeacherId());
        }

        return builder.issuedAt(now).expiresAt(expiry).sign();
    }
}
