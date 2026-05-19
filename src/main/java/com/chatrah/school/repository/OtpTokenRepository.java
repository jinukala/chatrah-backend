// src/main/java/com/chatrah/school/repository/OtpTokenRepository.java
package com.chatrah.school.repository;

import com.chatrah.school.entity.OtpToken;
import com.chatrah.school.entity.OtpToken.Purpose;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDateTime;

@ApplicationScoped
public class OtpTokenRepository implements PanacheRepository<OtpToken> {

    /**
     * Find the latest active OTP for a user & purpose.
     */
    public OtpToken findActiveTokenForUser(Long userId, Purpose purpose) {
        return find("user.id = ?1 and purpose = ?2 and consumed = false and expiresAt > ?3",
                userId, purpose, LocalDateTime.now())
                .firstResult();
    }
}
