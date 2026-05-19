package com.chatrah.school.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "subject_teachers", uniqueConstraints = @UniqueConstraint(columnNames = {"class_room_id", "subject"}))
public class SubjectTeacher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "class_room_id", nullable = false)
    private ClassRoom classRoom;

    @Column(nullable = false)
    private String subject;

    @ManyToOne
    @JoinColumn(name = "teacher_id", nullable = false)
    private Teacher teacher;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public ClassRoom getClassRoom() { return classRoom; }
    public void setClassRoom(ClassRoom classRoom) { this.classRoom = classRoom; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public Teacher getTeacher() { return teacher; }
    public void setTeacher(Teacher teacher) { this.teacher = teacher; }
}
