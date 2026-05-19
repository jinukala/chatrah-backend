package com.chatrah.school.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Per-student fee override to handle concessions or special cases.
 */
@Entity
@Table(name = "fee_override")
public class FeeOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Student to whom this override applies. */
    @OneToOne
    @JoinColumn(name = "student_id", nullable = false, unique = true)
    private Student student;

    /** Final total fee for this student after concession. */
    @Column(nullable = false)
    private Integer totalFee;

    /** Human-readable reason for concession. */
    private String reason;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
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

    public Student getStudent() { return student; }

    public void setStudent(Student student) { this.student = student; }

    public Integer getTotalFee() { return totalFee; }

    public void setTotalFee(Integer totalFee) { this.totalFee = totalFee; }

    public String getReason() { return reason; }

    public void setReason(String reason) { this.reason = reason; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
