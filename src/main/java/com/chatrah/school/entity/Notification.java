// src/main/java/com/chatrah/school/entity/Notification.java
package com.chatrah.school.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Stores notifications sent by the system (SMS/Email).
 * Used for audit and troubleshooting.
 */
@Entity
@Table(name = "notifications")
public class Notification {

    public enum Channel {
        SMS, EMAIL, BOTH
    }

    public enum Type {
        ATTENDANCE_ABSENT,
        FEE_PAYMENT,
        EXAM_RESULT,
        EVENT,
        OTP
    }

    public enum Status {
        PENDING,
        SENT,
        FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Type of notification: fee, exam, attendance, etc. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    /** Channel used: SMS / EMAIL / BOTH. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Channel channel;

    /** Mobile number of recipient (if SMS is used). */
    private String recipientMobile;

    /** Email of recipient (if EMAIL is used). */
    private String recipientEmail;

    /** Optional related student. */
    @ManyToOne
    @JoinColumn(name = "student_id")
    private Student student;

    /** Short title/subject. */
    private String title;

    /** Full message body as sent. */
    @Column(length = 2000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    /** Error message if sending failed. */
    private String errorMessage;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime sentAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }

    // Getters & setters

    public Long getId() { return id; }

    public void setId(Long id) { this.id = id; }

    public Type getType() { return type; }

    public void setType(Type type) { this.type = type; }

    public Channel getChannel() { return channel; }

    public void setChannel(Channel channel) { this.channel = channel; }

    public String getRecipientMobile() { return recipientMobile; }

    public void setRecipientMobile(String recipientMobile) { this.recipientMobile = recipientMobile; }

    public String getRecipientEmail() { return recipientEmail; }

    public void setRecipientEmail(String recipientEmail) { this.recipientEmail = recipientEmail; }

    public Student getStudent() { return student; }

    public void setStudent(Student student) { this.student = student; }

    public String getTitle() { return title; }

    public void setTitle(String title) { this.title = title; }

    public String getMessage() { return message; }

    public void setMessage(String message) { this.message = message; }

    public Status getStatus() { return status; }

    public void setStatus(Status status) { this.status = status; }

    public String getErrorMessage() { return errorMessage; }

    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getSentAt() { return sentAt; }

    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }
}
