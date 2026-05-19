package com.chatrah.school.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "quiz_attempts", uniqueConstraints = @UniqueConstraint(columnNames = {"quiz_id", "student_id"}))
public class QuizAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    @ManyToOne
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    private Integer totalQuestions;
    private Integer correctAnswers;
    private Integer score; // percentage

    @Column(nullable = false, updatable = false)
    private LocalDateTime submittedAt;

    @PrePersist
    public void prePersist() { submittedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Quiz getQuiz() { return quiz; }
    public void setQuiz(Quiz quiz) { this.quiz = quiz; }
    public Student getStudent() { return student; }
    public void setStudent(Student student) { this.student = student; }
    public Integer getTotalQuestions() { return totalQuestions; }
    public void setTotalQuestions(Integer t) { this.totalQuestions = t; }
    public Integer getCorrectAnswers() { return correctAnswers; }
    public void setCorrectAnswers(Integer c) { this.correctAnswers = c; }
    public Integer getScore() { return score; }
    public void setScore(Integer s) { this.score = s; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
}
