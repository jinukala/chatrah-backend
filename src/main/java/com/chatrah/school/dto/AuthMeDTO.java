// src/main/java/com/chatrah/school/dto/AuthMeDTO.java
package com.chatrah.school.dto;

import java.util.Set;

/**
 * DTO representing the currently authenticated user profile as derived from JWT.
 * Used by /api/auth/me endpoint for frontend to understand logged-in context.
 */
public class AuthMeDTO {

    /** Internal user ID (subject of the JWT). */
    private Long userId;

    /** Login username (UPN in the JWT). */
    private String username;

    /** Primary role of the user (PRINCIPAL, CLERK, TEACHER, STUDENT, SYS_ADMIN). */
    private String role;

    /** Optional associated student ID (if this user is a student). */
    private Long studentId;

    /** Optional associated teacher ID (if this user is a teacher). */
    private Long teacherId;

    private String displayName;

    /** All JWT groups (roles) granted to this user. */
    private Set<String> groups;

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
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

    public Set<String> getGroups() {
        return groups;
    }

    public void setGroups(Set<String> groups) {
        this.groups = groups;
    }
}
