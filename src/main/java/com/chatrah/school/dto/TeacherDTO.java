// src/main/java/com/chatrah/school/dto/TeacherDTO.java
package com.chatrah.school.dto;

import java.time.LocalDate;

/**
 * DTO for teacher data used by principal/clerk dashboards and salary module.
 */
public class TeacherDTO {

    private Long id;
    private String name;
    private String teacherUniqueId;
    private String subject;
    private String subjects;
    private String qualification;
    private String mobile;
    private String email;
    private LocalDate joinDate;
    private Integer salary;
    private Boolean active;
    private Long classTeacherOfId;
    private String classTeacherOfName;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTeacherUniqueId() { return teacherUniqueId; }
    public void setTeacherUniqueId(String teacherUniqueId) { this.teacherUniqueId = teacherUniqueId; }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public LocalDate getJoinDate() {
        return joinDate;
    }

    public void setJoinDate(LocalDate joinDate) {
        this.joinDate = joinDate;
    }

    public Integer getSalary() {
        return salary;
    }

    public void setSalary(Integer salary) {
        this.salary = salary;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public String getSubjects() { return subjects; }
    public void setSubjects(String subjects) { this.subjects = subjects; }
    public String getQualification() { return qualification; }
    public void setQualification(String qualification) { this.qualification = qualification; }
    public Long getClassTeacherOfId() { return classTeacherOfId; }
    public void setClassTeacherOfId(Long classTeacherOfId) { this.classTeacherOfId = classTeacherOfId; }
    public String getClassTeacherOfName() { return classTeacherOfName; }
    public void setClassTeacherOfName(String classTeacherOfName) { this.classTeacherOfName = classTeacherOfName; }
}
