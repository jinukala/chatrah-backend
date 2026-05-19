package com.chatrah.school.dto;

import java.time.LocalDateTime;

/**
 * DTO representing a teacher's salary structure.
 * Used in the salary configuration screen for the principal/clerk.
 */
public class SalaryStructureDTO {

    private Long id;
    private Long teacherId;
    private String teacherName;
    private Integer baseSalary;
    private Integer paidLeaves;
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTeacherId() {
        return teacherId;
    }

    public void setTeacherId(Long teacherId) {
        this.teacherId = teacherId;
    }

    public String getTeacherName() {
        return teacherName;
    }

    public void setTeacherName(String teacherName) {
        this.teacherName = teacherName;
    }

    public Integer getBaseSalary() {
        return baseSalary;
    }

    public void setBaseSalary(Integer baseSalary) {
        this.baseSalary = baseSalary;
    }

    public Integer getPaidLeaves() {
        return paidLeaves;
    }

    public void setPaidLeaves(Integer paidLeaves) {
        this.paidLeaves = paidLeaves;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
