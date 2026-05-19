package com.chatrah.school.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Log of notifications (SMS/email) sent by the system for audit and debugging.
 */
@Entity
@Table(name = "notification_logs")
public class NotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** Student to whom this notification relates (if applicable). */
    public Long studentId;

    /** Notification type: FEE, ABSENCE, EXAM_RESULT, EVENT, OTP, etc. */
    @Column(nullable = false)
    public String type;

    /** Text content of the notification (human-readable). */
    @Column(nullable = false, length = 2000)
    public String message;

    /** Mobile number or email target (depending on channel). */
    public String mobile;

    /** Timestamp when notification was sent. */
    public LocalDateTime sentAt;

    @Column(nullable = false, updatable = false)
    public LocalDateTime createdAt;

    public LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
        if (sentAt == null) {
            sentAt = createdAt;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Getters and setters

    public Long getId() { return id; }

    public void setId(Long id) { this.id = id; }

    public Long getStudentId() { return studentId; }

    public void setStudentId(Long studentId) { this.studentId = studentId; }

    public String getType() { return type; }

    public void setType(String type) { this.type = type; }

    public String getMessage() { return message; }

    public void setMessage(String message) { this.message = message; }

    public String getMobile() { return mobile; }

    public void setMobile(String mobile) { this.mobile = mobile; }

    public LocalDateTime getSentAt() { return sentAt; }

    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
