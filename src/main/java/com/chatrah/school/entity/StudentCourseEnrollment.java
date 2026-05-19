package com.chatrah.school.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Links a student to an advanced course and stores any fee overrides.
 */
@Entity
@Table(name = "student_course_enrollment")
public class StudentCourseEnrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "student_id")
    private Student student;

    @ManyToOne(optional = false)
    @JoinColumn(name = "course_id")
    private Course course;

    /** Final fee for this course (may include concession). */
    @Column(nullable = false)
    private Integer finalCourseFee;

    private String concessionReason;

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

    public Course getCourse() { return course; }

    public void setCourse(Course course) { this.course = course; }

    public Integer getFinalCourseFee() { return finalCourseFee; }

    public void setFinalCourseFee(Integer finalCourseFee) { this.finalCourseFee = finalCourseFee; }

    public String getConcessionReason() { return concessionReason; }

    public void setConcessionReason(String concessionReason) { this.concessionReason = concessionReason; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
