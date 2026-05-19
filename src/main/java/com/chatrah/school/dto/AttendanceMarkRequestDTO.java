package com.chatrah.school.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Request payload for marking attendance for a class in a given session and date.
 * Used by teacher/principal when submitting attendance.
 */
public class AttendanceMarkRequestDTO {

    /**
     * ID of the class-section for which attendance is being marked.
     */
    private Long classId;

    /**
     * Date for which attendance is being recorded.
     */
    private LocalDate date;

    /**
     * Session of the day: MORNING or AFTERNOON.
     */
    private String session;

    /**
     * List of per-student attendance rows.
     */
    private List<StudentAttendanceRowDTO> students;

    public Long getClassId() {
        return classId;
    }

    public void setClassId(Long classId) {
        this.classId = classId;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public String getSession() {
        return session;
    }

    public void setSession(String session) {
        this.session = session;
    }

    public List<StudentAttendanceRowDTO> getStudents() {
        return students;
    }

    public void setStudents(List<StudentAttendanceRowDTO> students) {
        this.students = students;
    }
}
