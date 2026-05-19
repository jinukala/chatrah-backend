package com.chatrah.school.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "quizzes")
public class Quiz {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "class_room_id", nullable = false)
    private ClassRoom classRoom;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false)
    private String title;

    private Integer durationMinutes;
    private Boolean published = false;
    private Boolean iitNeetOnly = false;
    private Long createdByTeacherId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() { createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public ClassRoom getClassRoom() { return classRoom; }
    public void setClassRoom(ClassRoom classRoom) { this.classRoom = classRoom; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Integer d) { this.durationMinutes = d; }
    public Boolean getPublished() { return published; }
    public void setPublished(Boolean p) { this.published = p; }
    public Boolean getIitNeetOnly() { return iitNeetOnly; }
    public void setIitNeetOnly(Boolean i) { this.iitNeetOnly = i; }
    public Long getCreatedByTeacherId() { return createdByTeacherId; }
    public void setCreatedByTeacherId(Long id) { this.createdByTeacherId = id; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
