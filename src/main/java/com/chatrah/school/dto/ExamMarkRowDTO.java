package com.chatrah.school.dto;

/**
 * DTO representing a single student's marks for a specific subject in an exam.
 * Used both in parsed Excel rows and API payloads for marks upload.
 */
public class ExamMarkRowDTO {

    /** Student ID in the system (optional if rollNo is used). */
    private Long studentId;

    /** Roll number of the student within the class, useful for Excel mapping. */
    private Integer rollNo;

    /** Student display name (for UI and validation messages). */
    private String studentName;

    /** Subject for which marks are being recorded (e.g., "Maths"). */
    private String subject;

    /** Marks obtained by the student in this subject. */
    private Integer marks;

    /** Maximum marks for the subject in this exam. */
    private Integer maxMarks;

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }

    public Integer getRollNo() {
        return rollNo;
    }

    public void setRollNo(Integer rollNo) {
        this.rollNo = rollNo;
    }

    public String getStudentName() {
        return studentName;
    }

    public void setStudentName(String studentName) {
        this.studentName = studentName;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public Integer getMarks() {
        return marks;
    }

    public void setMarks(Integer marks) {
        this.marks = marks;
    }

    public Integer getMaxMarks() {
        return maxMarks;
    }

    public void setMaxMarks(Integer maxMarks) {
        this.maxMarks = maxMarks;
    }
}
