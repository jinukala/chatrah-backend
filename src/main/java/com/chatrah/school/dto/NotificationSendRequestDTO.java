package com.chatrah.school.dto;

/**
 * Request DTO used to send a custom notification message
 * to a student's parent mobile number.
 */
public class NotificationSendRequestDTO {

    private Long studentId;
    private String type;   // CUSTOM, FEE, EXAM, ABSENCE, EVENT
    private String messageOverride;

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }


    public String getMessageOverride() {
        return messageOverride;
    }

    public void setMessageOverride(String messageOverride) {
        this.messageOverride = messageOverride;
    }
}
