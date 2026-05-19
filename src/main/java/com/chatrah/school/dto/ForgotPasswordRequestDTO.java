// src/main/java/com/chatrah/school/dto/ForgotPasswordRequestDTO.java
package com.chatrah.school.dto;

/**
 * Request payload for initiating a "forgot password" flow.
 * The user supplies their login username, and an OTP is sent to the
 * email address associated with that username.
 */
public class ForgotPasswordRequestDTO {

    /**
     * Login username of the account requesting password reset.
     */
    private String username;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}
