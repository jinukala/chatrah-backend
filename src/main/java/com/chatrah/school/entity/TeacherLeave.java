package com.chatrah.school.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "teacher_leaves")
public class TeacherLeave {

    public enum Status { PENDING, APPROVED, REJECTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long teacherId;
    private String teacherName;

    @Column(nullable = false)
    private LocalDate fromDate;
    @Column(nullable = false)
    private LocalDate toDate;
    @Column(nullable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    private String remarks;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() { createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTeacherId() { return teacherId; }
    public void setTeacherId(Long teacherId) { this.teacherId = teacherId; }
    public String getTeacherName() { return teacherName; }
    public void setTeacherName(String n) { this.teacherName = n; }
    public LocalDate getFromDate() { return fromDate; }
    public void setFromDate(LocalDate d) { this.fromDate = d; }
    public LocalDate getToDate() { return toDate; }
    public void setToDate(LocalDate d) { this.toDate = d; }
    public String getReason() { return reason; }
    public void setReason(String r) { this.reason = r; }
    public Status getStatus() { return status; }
    public void setStatus(Status s) { this.status = s; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String r) { this.remarks = r; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
