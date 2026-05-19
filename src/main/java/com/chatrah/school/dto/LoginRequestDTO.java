// src/main/java/com/chatrah/school/dto/LoginRequestDTO.java
package com.chatrah.school.dto;

/**
 * Request payload for username/password login.
 * Sent from the login page when a user (student, teacher, principal, clerk) attempts to sign in.
 */
public class LoginRequestDTO {

    /**
     * Unique username used to log in.
     * For students, this may be a roll-based ID; for teachers/principal/clerk, a chosen username.
     */
    private String username;

    /**
     * Plain-text password provided by the user.
     * This will be validated against the stored hashed password on the server.
     */
    private String password;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
