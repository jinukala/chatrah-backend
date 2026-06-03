package com.chatrah.school.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a system login account for any user type:
 * Principal, Clerk, Teacher, Student, or System Admin.
 *
 * This table stores authentication credentials and role information,
 * while teacher/student-specific details reside in their respective tables.
 *
 * The password is stored as a BCrypt hash (never plain text).
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique login username. This may be a phone number, email, or custom username.
     */
    @Column(nullable = false, unique = true)
    @NotBlank(message = "Username is required")
    private String username;

    /**
     * BCrypt-hashed password (never store plain text).
     */
    @Column(nullable = false, name = "password_hash")
    @NotBlank(message = "Password hash is required")
    private String passwordHash;

    /**
     * Role of the user:
     * PRINCIPAL, CLERK, TEACHER, STUDENT, SYS_ADMIN.
     */
    @Column(nullable = false)
    @NotBlank(message = "Role is required")
    private String role;

    /**
     * Linked teacher record, if this login belongs to a teacher.
     */
    @Column(name = "teacherid")
    private Long teacherId;

    /**
     * Linked student record, if this login belongs to a student.
     */
    @Column(name = "studentid")
    private Long studentId;

    /**
     * Email address used for sending OTPs and other alerts.
     * Optional for students if they authenticate via mobile.
     */
    @Email
    private String email;

    /**
     * Mobile number used for notifications (e.g., SMS to parent/student).
     */
    private String mobile;

    /**
     * Indicates whether the user account is active and allowed to log in.
     */
    @NotNull
    @Column(name = "isactive")
    private Boolean isActive = true;

    /**
     * Number of consecutive failed login attempts.
     */
    @Column(name = "failedloginattempts", nullable = false)
    private int failedLoginAttempts = 0;

    /**
     * Account locked until this time after too many failed attempts.
     */
    @Column(name = "lockeduntil")
    private LocalDateTime lockedUntil;

    /**
     * Record creation timestamp.
     */
    @Column(name = "createdat", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Last update timestamp.
     */
    @Column(name = "updatedat")
    private LocalDateTime updatedAt;


    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() { return username; }

    public void setUsername(String username) { this.username = username; }

    public String getPasswordHash() { return passwordHash; }

    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getRole() { return role; }

    public void setRole(String role) { this.role = role; }

    public Long getTeacherId() { return teacherId; }

    public void setTeacherId(Long teacherId) { this.teacherId = teacherId; }

    public Long getStudentId() { return studentId; }

    public void setStudentId(Long studentId) { this.studentId = studentId; }

    public Boolean getIsActive() { return isActive; }

    public void setIsActive(Boolean active) { isActive = active; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getEmail() { return email; }

    public void setEmail(String email) { this.email = email; }

    public String getMobile() { return mobile; }

    public void setMobile(String mobile) { this.mobile = mobile; }

    public int getFailedLoginAttempts() { return failedLoginAttempts; }

    public void setFailedLoginAttempts(int failedLoginAttempts) { this.failedLoginAttempts = failedLoginAttempts; }

    public LocalDateTime getLockedUntil() { return lockedUntil; }

    public void setLockedUntil(LocalDateTime lockedUntil) { this.lockedUntil = lockedUntil; }
}
