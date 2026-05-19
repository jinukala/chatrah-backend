package com.chatrah.school.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Represents the configured salary structure for a teacher,
 * including base salary and paid leave entitlement.
 */
@Entity
@Table(name = "salary_structure")
public class SalaryStructure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** Teacher ID this structure applies to. */
    @Column(nullable = false, unique = true)
    public Long teacherId;

    /** Base monthly salary. */
    @Column(nullable = false)
    public Integer baseSalary;

    /** Number of paid leaves allowed per period (e.g. month). */
    @Column(nullable = false)
    public Integer paidLeaves;

    @Column(nullable = false, updatable = false)
    public LocalDateTime createdAt;

    public LocalDateTime updatedAt;

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

    public Long getTeacherId() { return teacherId; }

    public void setTeacherId(Long teacherId) { this.teacherId = teacherId; }

    public Integer getBaseSalary() { return baseSalary; }

    public void setBaseSalary(Integer baseSalary) { this.baseSalary = baseSalary; }

    public Integer getPaidLeaves() { return paidLeaves; }

    public void setPaidLeaves(Integer paidLeaves) { this.paidLeaves = paidLeaves; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
