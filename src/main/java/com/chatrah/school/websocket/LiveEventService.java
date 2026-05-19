package com.chatrah.school.websocket;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.Json;
import jakarta.json.JsonObject;

/**
 * Centralized event dispatcher for real-time notifications.
 * Call these methods from services when important actions happen.
 */
@ApplicationScoped
public class LiveEventService {

    public void attendanceMarked(String className, String section, int present, int absent) {
        String msg = json("ATTENDANCE_MARKED", "Attendance marked for Class " + className + "-" + section + " (" + present + " present, " + absent + " absent)");
        NotificationWebSocket.broadcast(msg);
    }

    public void leaveApplied(String studentName, String targetUserId) {
        String msg = json("LEAVE_APPLIED", studentName + " applied for leave");
        NotificationWebSocket.sendToUser(targetUserId, msg);
        NotificationWebSocket.broadcast(msg); // also notify admins
    }

    public void leaveApproved(String studentName, String studentUserId) {
        String msg = json("LEAVE_APPROVED", "Your leave has been approved");
        NotificationWebSocket.sendToUser(studentUserId, msg);
    }

    public void paymentRecorded(String studentName, int amount) {
        String msg = json("PAYMENT_RECORDED", "Payment ₹" + amount + " recorded for " + studentName);
        NotificationWebSocket.broadcast(msg);
    }

    public void examPublished(String examName) {
        String msg = json("EXAM_PUBLISHED", "Results published for " + examName);
        NotificationWebSocket.broadcast(msg);
    }

    public void quizPublished(String quizTitle, String className) {
        String msg = json("QUIZ_PUBLISHED", "New quiz available: " + quizTitle + " for " + className);
        NotificationWebSocket.broadcast(msg);
    }

    public void newBlog(String title, String author) {
        String msg = json("NEW_BLOG", "New blog submitted: \"" + title + "\" by " + author);
        NotificationWebSocket.broadcast(msg);
    }

    public void studentCreated(String name) {
        String msg = json("STUDENT_CREATED", "New student added: " + name);
        NotificationWebSocket.broadcast(msg);
    }

    private String json(String type, String message) {
        return "{\"type\":\"" + type + "\",\"message\":\"" + message + "\",\"timestamp\":\"" + java.time.Instant.now() + "\"}";
    }
}
