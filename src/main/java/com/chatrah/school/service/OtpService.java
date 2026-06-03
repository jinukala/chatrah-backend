// src/main/java/com/chatrah/school/service/OtpService.java
package com.chatrah.school.service;

import com.chatrah.school.entity.OtpToken;
import com.chatrah.school.entity.User;
import com.chatrah.school.repository.OtpTokenRepository;
import com.chatrah.school.repository.UserRepository;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.security.SecureRandom;
import java.time.LocalDateTime;

import static com.chatrah.school.entity.OtpToken.Purpose.FORGOT_PASSWORD;

/**
 * Service that handles password-reset OTP generation, validation and consumption.
 */
@ApplicationScoped
public class OtpService {

    @Inject
    OtpTokenRepository otpTokenRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    Mailer mailer;

    private final SecureRandom random = new SecureRandom();

    /**
     * Step 1: Generate and send OTP to user's email (or mobile, if you adapt it).
     */
    @Transactional
    public void sendPasswordResetOtp(String username) {
        User user = userRepository.find("username", username).firstResult();
        if (user == null || Boolean.FALSE.equals(user.getIsActive())) {
            // You can either:
            // - throw NotFoundException (leaks that user doesn't exist), or
            // - silently return to avoid user enumeration.
            // For now, we'll throw, since this is internal admin-controlled system.
            throw new NotFoundException("User not found or inactive");
        }

        // Invalidate any previous active OTPs
        OtpToken existing = otpTokenRepository.findActiveTokenForUser(user.getId(), FORGOT_PASSWORD);
        if (existing != null) {
            existing.setConsumed(true);
        }

        String code = generateOtpCode(6);

        OtpToken token = new OtpToken();
        token.setUser(user);
        token.setPurpose(FORGOT_PASSWORD);
        token.setCode(code);
        String destination = (user.getEmail() != null && !user.getEmail().isBlank())
                ? user.getEmail() : username;
        token.setDestination(destination);
        token.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        token.setAttempts(0);
        token.setMaxAttempts(5);
        token.setConsumed(false);

        otpTokenRepository.persist(token);

        // Send OTP via email
        sendOtpEmail(destination, code);
    }

    /**
     * Step 2: Validate OTP without consuming it yet.
     * Used both by /otp/verify-reset and /password/reset.
     */
    @Transactional
    public void validatePasswordResetOtp(String username, String otp) {
        User user = userRepository.find("username", username).firstResult();
        if (user == null) {
            throw new NotFoundException("User not found");
        }

        OtpToken token = otpTokenRepository.findActiveTokenForUser(user.getId(), FORGOT_PASSWORD);
        if (token == null) {
            throw new BadRequestException("OTP expired or not requested");
        }

        if (token.isConsumed()) {
            throw new BadRequestException("OTP already used");
        }

        if (token.getAttempts() >= token.getMaxAttempts()) {
            token.setConsumed(true);
            throw new BadRequestException("Too many invalid attempts. Request a new OTP.");
        }

        if (!token.getCode().equals(otp)) {
            token.setAttempts(token.getAttempts() + 1);
            if (token.getAttempts() >= token.getMaxAttempts()) {
                token.setConsumed(true);
            }
            throw new BadRequestException("Invalid OTP");
        }

        // If we reach here, OTP is correct and still active.
        // We do NOT mark consumed yet – that is done by markOtpUsed()
        // after password is actually reset.
    }

    /**
     * Step 3: Mark OTP as consumed after successful password reset.
     */
    @Transactional
    public void markOtpUsed(String username) {
        User user = userRepository.find("username", username).firstResult();
        if (user == null) {
            throw new NotFoundException("User not found");
        }

        OtpToken token = otpTokenRepository.findActiveTokenForUser(user.getId(), FORGOT_PASSWORD);
        if (token != null) {
            token.setConsumed(true);
        }
    }

    private String generateOtpCode(int length) {
        int bound = (int) Math.pow(10, length);
        int base = bound / 10;
        int number = base + random.nextInt(base * 9);
        return String.valueOf(number);
    }

    private void sendOtpEmail(String to, String code) {
        // Simple plain-text mail; you can style it or use NotificationService later
        String subject = "Password Reset OTP - School Portal";
        String body = "Your OTP for password reset is: " + code +
                "\n\nThis OTP is valid for 10 minutes.\n\n" +
                "If you did not request this, please ignore this email.\n\n" +
                "Managed by CHATHRAH";

        mailer.send(Mail.withText(to, subject, body));
    }
}
