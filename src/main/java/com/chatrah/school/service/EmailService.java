// src/main/java/com/chatrah/school/service/EmailService.java
package com.chatrah.school.service;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Service responsible for sending emails using the configured SMTP server.
 * This is used for OTP delivery, notifications, and other email-based flows.
 */
@ApplicationScoped
public class EmailService {

    @Inject
    Mailer mailer;

    /**
     * Send a simple text email.
     *
     * @param to      recipient email address
     * @param subject subject of the email
     * @param text    plain text body
     */
    public void sendTextMail(String to, String subject, String text) {
        Mail mail = Mail.withText(to, subject, text);
        mailer.send(mail);
    }
}
