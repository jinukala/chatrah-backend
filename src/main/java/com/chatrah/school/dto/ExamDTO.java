package com.chatrah.school.dto;

import java.time.LocalDateTime;

/**
 * DTO representing an exam definition such as "Quarterly", "Half Yearly", or "Annual".
 * Used by the principal/clerk to configure and list exams.
 */
public class ExamDTO {

    private Long id;

    /** Name of the exam (e.g., "Quarterly Test", "Annual Exam"). */
    private String name;

    /** Academic year or term to which the exam belongs (e.g., "2024-25"). */
    private String academicYear;

    /** Optional description or notes about this exam. */
    private String description;

    /** User ID of the user who created this exam (typically principal or clerk). */
    private Long createdBy;

    /** Timestamp when the exam was created. */
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAcademicYear() {
        return academicYear;
    }

    public void setAcademicYear(String academicYear) {
        this.academicYear = academicYear;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
