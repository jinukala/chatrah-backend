package com.chatrah.school.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "teacher_attendance",
        uniqueConstraints = @UniqueConstraint(columnNames = {"teacher_id", "date"}))
public class TeacherAttendance {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne @JoinColumn(name = "teacher_id")
    public Teacher teacher;

    public LocalDate date;

    public Boolean present;

    public LocalDateTime markedAt = LocalDateTime.now();
}
