// src/main/java/com/chatrah/school/service/NotificationService.java
package com.chatrah.school.service;

import com.chatrah.school.dto.FeeSummaryDTO;
import com.chatrah.school.dto.SchoolProfileDTO;
import com.chatrah.school.entity.FeePayment;
import com.chatrah.school.entity.Notification;
import com.chatrah.school.entity.Student;
import com.chatrah.school.repository.NotificationRepository;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Central notification service.
 * Right now uses EMAIL via SMTP and logs "SMS" as a stub.
 * Later can be extended to MSG91 for real SMS.
 */
@ApplicationScoped
public class NotificationService {

    @Inject
    NotificationRepository notificationRepository;

    @Inject
    Mailer mailer;

    @Inject
    SchoolProfileService schoolProfileService;

    private String getSchoolName() {
        SchoolProfileDTO profile = schoolProfileService.getProfile();
        return (profile != null && profile.getSchoolName() != null && !profile.getSchoolName().isBlank())
                ? profile.getSchoolName() : "Your School";
    }

    // -------------- Fee payment notification --------------

    @Transactional
    public void sendFeePaymentNotification(Student student, FeePayment payment, FeeSummaryDTO summary) {
        if (student == null || payment == null) {
            return;
        }

        String schoolName = getSchoolName();

        String title = "Fee Payment Update - " + schoolName;
        String body = String.format(
                "Dear Parent,\n\n" +
                        "Fee payment has been updated for your child %s.\n\n" +
                        "Amount paid: ₹%d\n" +
                        "Total fee: ₹%d\n" +
                        "Total paid so far: ₹%d\n" +
                        "Due amount: ₹%d\n\n" +
                        "Transaction ID: %s\n" +
                        "Thank you.\n\n" +
                        "- %s\nManaged by CHATHRAH",
                student.getName(),
                payment.getAmount(),
                summary.getTotalFee(),
                summary.getTotalPaid(),
                summary.getDue(),
                payment.getTransactionId(),
                schoolName
        );

        Notification notification = new Notification();
        notification.setType(Notification.Type.FEE_PAYMENT);
        notification.setChannel(Notification.Channel.EMAIL); // for now
        notification.setStudent(student);
        notification.setRecipientEmail(student.getEmail()); // make sure field exists or adjust
        notification.setRecipientMobile(student.getParentMobile());
        notification.setTitle(title);
        notification.setMessage(body);
        notification.setStatus(Notification.Status.PENDING);

        notificationRepository.persist(notification);

        // Send email if email present
        if (notification.getRecipientEmail() != null && !notification.getRecipientEmail().isBlank()) {
            try {
                mailer.send(Mail.withText(notification.getRecipientEmail(), title, body));
                notification.setStatus(Notification.Status.SENT);
                notification.setSentAt(LocalDateTime.now());
            } catch (Exception e) {
                notification.setStatus(Notification.Status.FAILED);
                notification.setErrorMessage(e.getMessage());
            }
        }

        // Stub SMS - later plug MSG91 here
        if (notification.getRecipientMobile() != null && !notification.getRecipientMobile().isBlank()) {
            // TODO: call MSG91 or any provider.
            // For now we just mark as sent for SMS perspective.
        }
    }

    // -------------- Attendance absent notification (stub) --------------

    @Transactional
    public void sendAttendanceAbsentNotification(Student student, String dateStr) {
        if (student == null) return;

        String schoolName = getSchoolName();

        String title = "Attendance Alert - " + schoolName;
        String body = String.format(
                "Dear Parent,\n\n" +
                        "Your child %s was absent on %s.\n\n" +
                        "- %s\nManaged by CHATHRAH",
                student.getName(),
                dateStr,
                schoolName
        );

        Notification notification = new Notification();
        notification.setType(Notification.Type.ATTENDANCE_ABSENT);
        notification.setChannel(Notification.Channel.EMAIL);
        notification.setStudent(student);
        notification.setRecipientEmail(student.getEmail());
        notification.setRecipientMobile(student.getParentMobile());
        notification.setTitle(title);
        notification.setMessage(body);
        notification.setStatus(Notification.Status.PENDING);

        notificationRepository.persist(notification);

        // send email, same pattern
        if (notification.getRecipientEmail() != null && !notification.getRecipientEmail().isBlank()) {
            try {
                mailer.send(Mail.withText(notification.getRecipientEmail(), title, body));
                notification.setStatus(Notification.Status.SENT);
                notification.setSentAt(LocalDateTime.now());
            } catch (Exception e) {
                notification.setStatus(Notification.Status.FAILED);
                notification.setErrorMessage(e.getMessage());
            }
        }
    }

    // -------------- Exam result notification (stub) --------------

    @Transactional
    public void sendExamResultNotification(Student student, String examName) {
        if (student == null) return;

        String schoolName = getSchoolName();

        String title = "Exam Result Available - " + schoolName;
        String body = String.format(
                "Dear Parent,\n\n" +
                        "Exam results for %s are now available for your child %s.\n" +
                        "Please login to the portal to view detailed marks.\n\n" +
                        "- %s\nManaged by CHATHRAH",
                examName,
                student.getName(),
                schoolName
        );

        Notification notification = new Notification();
        notification.setType(Notification.Type.EXAM_RESULT);
        notification.setChannel(Notification.Channel.EMAIL);
        notification.setStudent(student);
        notification.setRecipientEmail(student.getEmail());
        notification.setRecipientMobile(student.getParentMobile());
        notification.setTitle(title);
        notification.setMessage(body);
        notification.setStatus(Notification.Status.PENDING);

        notificationRepository.persist(notification);

        if (notification.getRecipientEmail() != null && !notification.getRecipientEmail().isBlank()) {
            try {
                mailer.send(Mail.withText(notification.getRecipientEmail(), title, body));
                notification.setStatus(Notification.Status.SENT);
                notification.setSentAt(LocalDateTime.now());
            } catch (Exception e) {
                notification.setStatus(Notification.Status.FAILED);
                notification.setErrorMessage(e.getMessage());
            }
        }
    }

    // -------------- Event notification (stub) --------------

    @Transactional
    public void sendEventNotificationToStudents(List<Student> students, String eventTitle, String eventBody) {
        String schoolName = getSchoolName();

        String fullBody = eventBody + "\n\n- " + schoolName + "\nManaged by CHATHRAH";

        for (Student student : students) {
            Notification notification = new Notification();
            notification.setType(Notification.Type.EVENT);
            notification.setChannel(Notification.Channel.EMAIL);
            notification.setStudent(student);
            notification.setRecipientEmail(student.getEmail());
            notification.setRecipientMobile(student.getParentMobile());
            notification.setTitle(eventTitle);
            notification.setMessage(fullBody);
            notification.setStatus(Notification.Status.PENDING);

            notificationRepository.persist(notification);

            if (notification.getRecipientEmail() != null && !notification.getRecipientEmail().isBlank()) {
                try {
                    mailer.send(Mail.withText(notification.getRecipientEmail(), eventTitle, fullBody));
                    notification.setStatus(Notification.Status.SENT);
                    notification.setSentAt(LocalDateTime.now());
                } catch (Exception e) {
                    notification.setStatus(Notification.Status.FAILED);
                    notification.setErrorMessage(e.getMessage());
                }
            }
        }
    }
}
