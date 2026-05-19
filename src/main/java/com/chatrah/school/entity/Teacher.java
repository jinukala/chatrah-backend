package com.chatrah.school.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Represents a teacher or staff member in the school.
 */
@Entity
@Table(name = "teachers")
public class Teacher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Full name of the teacher. */
    @Column(nullable = false)
    private String name;

    /** Primary subject taught (can be extended to a separate mapping later). */
    private String subject;

    /** Comma-separated list of subjects taught. */
    private String subjects;

    /** Highest qualification/degree. */
    private String qualification;

    private String mobile;

    private String email;

    private LocalDate joinDate;

    /** Base salary amount configured for this teacher. */
    private Integer salary;

    /** Whether the teacher is actively employed. */
    private Boolean isActive = true;

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

    public String getName() { return name; }

    public void setName(String name) { this.name = name; }

    public String getSubject() { return subject; }

    public void setSubject(String subject) { this.subject = subject; }

    public String getSubjects() { return subjects; }

    public void setSubjects(String subjects) { this.subjects = subjects; }

    public String getQualification() { return qualification; }

    public void setQualification(String qualification) { this.qualification = qualification; }

    public String getMobile() { return mobile; }

    public void setMobile(String mobile) { this.mobile = mobile; }

    public String getEmail() { return email; }

    public void setEmail(String email) { this.email = email; }

    public LocalDate getJoinDate() { return joinDate; }

    public void setJoinDate(LocalDate joinDate) { this.joinDate = joinDate; }

    public Integer getSalary() { return salary; }

    public void setSalary(Integer salary) { this.salary = salary; }

    public Boolean getIsActive() { return isActive; }

    public void setIsActive(Boolean active) { isActive = active; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
