// src/main/java/com/chatrah/school/dto/LoginResponseDTO.java
package com.chatrah.school.dto;

/**
 * Response returned after a successful login.
 * Contains basic user info and the JWT token used for authenticated API calls.
 */
public class LoginResponseDTO {

    /**
     * The generated JWT token that must be sent in the Authorization header.
     */
    private String token;

    /**
     * The display name of the user (student name, teacher name, principal name, etc.).
     */
    private String displayName;

    /**
     * The primary role of the user (PRINCIPAL, CLERK, TEACHER, STUDENT).
     */
    private String role;

    /**
     * Optional associated student ID if the user is a student.
     */
    private Long studentId;

    /**
     * Optional associated teacher ID if the user is a teacher.
     */
    private Long teacherId;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }

    public Long getTeacherId() {
        return teacherId;
    }

    public void setTeacherId(Long teacherId) {
        this.teacherId = teacherId;
    }
}
