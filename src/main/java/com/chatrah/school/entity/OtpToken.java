// src/main/java/com/chatrah/school/entity/OtpToken.java
package com.chatrah.school.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Represents an OTP generated for password reset or other flows.
 */
@Entity
@Table(name = "otp_tokens")
public class OtpToken {

    public enum Purpose {
        FORGOT_PASSWORD
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** User for whom this OTP is generated. */
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Purpose purpose;

    /** The actual OTP code (e.g. 6-digit). */
    @Column(nullable = false)
    private String code;

    /** Email or mobile where OTP was sent (optional). */
    private String destination;

    /** OTP expiry timestamp. */
    @Column(nullable = false)
    private LocalDateTime expiresAt;

    /** Number of verification attempts made so far. */
    @Column(nullable = false)
    private int attempts = 0;

    /** Maximum allowed attempts before OTP becomes invalid. */
    @Column(nullable = false)
    private int maxAttempts = 5;

    /** True once OTP has been successfully used. */
    @Column(nullable = false)
    private boolean consumed = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }

    // Getters & setters

    public Long getId() { return id; }

    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }

    public void setUser(User user) { this.user = user; }

    public Purpose getPurpose() { return purpose; }

    public void setPurpose(Purpose purpose) { this.purpose = purpose; }

    public String getCode() { return code; }

    public void setCode(String code) { this.code = code; }

    public String getDestination() { return destination; }

    public void setDestination(String destination) { this.destination = destination; }

    public LocalDateTime getExpiresAt() { return expiresAt; }

    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public int getAttempts() { return attempts; }

    public void setAttempts(int attempts) { this.attempts = attempts; }

    public int getMaxAttempts() { return maxAttempts; }

    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }

    public boolean isConsumed() { return consumed; }

    public void setConsumed(boolean consumed) { this.consumed = consumed; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
