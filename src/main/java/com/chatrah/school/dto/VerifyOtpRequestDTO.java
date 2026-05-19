// src/main/java/com/chatrah/school/dto/VerifyOtpRequestDTO.java
package com.chatrah.school.dto;

/**
 * Request payload used when the user submits an OTP code
 * received via email as part of the forgot password process.
 */
public class VerifyOtpRequestDTO {

    /**
     * Login username associated with the OTP.
     */
    private String username;

    /**
     * OTP code received by the user via email.
     */
    private String otp;

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
}
