package com.chatrah.school.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "class_materials")
public class ClassMaterial {

    public enum Type { NOTES, HOMEWORK, MATERIAL, QUIZ, ANNOUNCEMENT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "class_room_id", nullable = false)
    private ClassRoom classRoom;

    @Column(nullable = false)
    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    @Column(nullable = false)
    private String title;

    @Column(length = 4000)
    private String content;

    /** Base64-encoded file data (PDF) */
    @Column(columnDefinition = "TEXT")
    private String fileData;

    private String fileName;

    private LocalDate dueDate;

    private Long uploadedByTeacherId;

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
    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getFileData() { return fileData; }
    public void setFileData(String fileData) { this.fileData = fileData; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public Long getUploadedByTeacherId() { return uploadedByTeacherId; }
    public void setUploadedByTeacherId(Long id) { this.uploadedByTeacherId = id; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
