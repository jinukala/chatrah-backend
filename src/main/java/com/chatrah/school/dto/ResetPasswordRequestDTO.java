// src/main/java/com/chatrah/school/dto/ResetPasswordRequestDTO.java
package com.chatrah.school.dto;

/**
 * Request payload used to reset a user's password after OTP verification.
 * The OTP is validated and, if correct, the password is updated.
 */
public class ResetPasswordRequestDTO {

    /**
     * Login username whose password will be reset.
     */
    private String username;

    /**
     * OTP code received via email.
     */
    private String otp;

    /**
     * New password chosen by the user.
     * It should satisfy the application's password strength rules.
     */
    private String newPassword;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getOtp() {
        return otp;
    }

    public void setOtp(String otp) {
        this.otp = otp;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
