package com.chatrah.school.dto;

/**
 * Row DTO representing a single student's attendance for a particular date/session.
 */
public class StudentAttendanceRowDTO {

    private Long studentId;
    private Boolean present;

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }

    public Boolean getPresent() {
        return present;
    }

    public void setPresent(Boolean present) {
        this.present = present;
    }
}
