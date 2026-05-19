package com.chatrah.school.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Represents an access request raised by a teacher,
 * for example to view fee details of a class.
 */
@Entity
@Table(name = "access_requests")
public class AccessRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** Teacher who is requesting access. */
    @Column(nullable = false)
    public Long teacherId;

    /** Class for which access is requested. */
    @Column(nullable = false)
    public Long classId;

    /** Type of access requested: FEE_ACCESS, etc. */
    @Column(nullable = false)
    public String requestType;

    /** PENDING, APPROVED, REJECTED. */
    @Column(nullable = false)
    public String status = "PENDING";

    @Column(nullable = false)
    public LocalDateTime requestedAt = LocalDateTime.now();

    public Long approvedBy;

    public LocalDateTime approvedAt;

    @Column(nullable = false, updatable = false)
    public LocalDateTime createdAt;

    public LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (requestedAt == null) {
            requestedAt = LocalDateTime.now();
        }
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Getters and setters

    public Long getId() { return id; }

    public void setId(Long id) { this.id = id; }

    public Long getTeacherId() { return teacherId; }

    public void setTeacherId(Long teacherId) { this.teacherId = teacherId; }

    public Long getClassId() { return classId; }

    public void setClassId(Long classId) { this.classId = classId; }

    public String getRequestType() { return requestType; }

    public void setRequestType(String requestType) { this.requestType = requestType; }

    public String getStatus() { return status; }

    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getRequestedAt() { return requestedAt; }

    public void setRequestedAt(LocalDateTime requestedAt) { this.requestedAt = requestedAt; }

    public Long getApprovedBy() { return approvedBy; }

    public void setApprovedBy(Long approvedBy) { this.approvedBy = approvedBy; }

    public LocalDateTime getApprovedAt() { return approvedAt; }

    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
