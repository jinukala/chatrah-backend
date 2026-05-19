package com.chatrah.school.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Represents a class + section combination in the school.
 * Example: "10" + "A".
 */
@Entity
@Table(name = "class_rooms")
public class ClassRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Class name or grade (e.g. "10", "8", "LKG"). */
    @Column(nullable = false)
    private String className;

    /** Section within the class (e.g. "A", "B"). */
    @Column(nullable = false)
    private String section;

    /** Optional reference to the teacher who is class teacher. */
    private Long classTeacherId;

    /** Comma-separated subjects for this class. */
    private String subjects;

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

    public String getClassName() { return className; }

    public void setClassName(String className) { this.className = className; }

    public String getSection() { return section; }

    public void setSection(String section) { this.section = section; }

    public Long getClassTeacherId() { return classTeacherId; }

    public void setClassTeacherId(Long classTeacherId) { this.classTeacherId = classTeacherId; }

    public String getSubjects() { return subjects; }

    public void setSubjects(String subjects) { this.subjects = subjects; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
