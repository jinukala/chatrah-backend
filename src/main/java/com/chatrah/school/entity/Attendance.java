package com.chatrah.school.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Represents attendance of a student for a date & session (MORNING/AFTERNOON).
 */
@Entity
@Table(name = "attendance")
public class Attendance {

    public enum Session {
        MORNING,
        AFTERNOON
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Date for which attendance is recorded. */
    @Column(nullable = false)
    private LocalDate date;

    /** Morning or afternoon session. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Session session;

    /** Student for whom attendance is recorded. */
    @ManyToOne
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    /** ClassRoom snapshot used for analytics. */
    @ManyToOne
    @JoinColumn(name = "class_room_id", nullable = false)
    private ClassRoom classRoom;

    /** True if present, false if absent. */
    @Column(nullable = false)
    private Boolean present;

    /** User ID of the teacher or staff who marked attendance. */
    private Long markedByUserId;

    private LocalDateTime markedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
        if (markedAt == null) {
            markedAt = createdAt;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Getters and setters

    public Long getId() { return id; }

    public void setId(Long id) { this.id = id; }

    public LocalDate getDate() { return date; }

    public void setDate(LocalDate date) { this.date = date; }

    public Session getSession() { return session; }

    public void setSession(Session session) { this.session = session; }

    public Student getStudent() { return student; }

    public void setStudent(Student student) { this.student = student; }

    public ClassRoom getClassRoom() { return classRoom; }

    public void setClassRoom(ClassRoom classRoom) { this.classRoom = classRoom; }

    public Boolean getPresent() { return present; }

    public void setPresent(Boolean present) { this.present = present; }

    public Long getMarkedByUserId() { return markedByUserId; }

    public void setMarkedByUserId(Long markedByUserId) { this.markedByUserId = markedByUserId; }

    public LocalDateTime getMarkedAt() { return markedAt; }

    public void setMarkedAt(LocalDateTime markedAt) { this.markedAt = markedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
