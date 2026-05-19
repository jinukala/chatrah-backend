package com.chatrah.school.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Represents marks of a student in a subject for a particular exam.
 */
@Entity
@Table(name = "exam_marks")
public class ExamMark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "exam_id")
    private Exam exam;

    @ManyToOne(optional = false)
    @JoinColumn(name = "student_id")
    private Student student;

    @ManyToOne(optional = false)
    @JoinColumn(name = "class_room_id")
    private ClassRoom classRoom;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false)
    private Integer marks;

    @Column(nullable = false)
    private Integer maxMarks = 100;

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

    public Exam getExam() { return exam; }

    public void setExam(Exam exam) { this.exam = exam; }

    public Student getStudent() { return student; }

    public void setStudent(Student student) { this.student = student; }

    public ClassRoom getClassRoom() { return classRoom; }

    public void setClassRoom(ClassRoom classRoom) { this.classRoom = classRoom; }

    public String getSubject() { return subject; }

    public void setSubject(String subject) { this.subject = subject; }

    public Integer getMarks() { return marks; }

    public void setMarks(Integer marks) { this.marks = marks; }

    public Integer getMaxMarks() { return maxMarks; }

    public void setMaxMarks(Integer maxMarks) { this.maxMarks = maxMarks; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
