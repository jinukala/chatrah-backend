package com.chatrah.school.dto;

import java.util.List;

/**
 * DTO representing a student's performance in a particular exam,
 * including subject-wise marks and overall aggregates.
 */
public class StudentExamResultDTO {

    /** ID of the exam. */
    private Long examId;

    /** Name of the exam (e.g. "Quarterly", "Half-Yearly"). */
    private String examName;

    /** ID of the student. */
    private Long studentId;

    /** Name of the student. */
    private String studentName;

    /** Total marks obtained across all subjects. */
    private Integer totalMarksObtained;

    /** Total maximum marks across all subjects. */
    private Integer totalMaxMarks;

    /** Overall percentage. */
    private Double percentage;

    /** Subject-wise marks list. */
    private List<SubjectMark> subjects;

    // ---------- Nested DTO for subject-wise marks ----------

    /**
     * Nested DTO representing marks in a single subject for this exam.
     */
    public static class SubjectMark {

        /** Subject name (e.g. "Maths", "Physics"). */
        private String subject;

        /** Marks obtained in this subject. */
        private Integer marks;

        /** Maximum marks for this subject. */
        private Integer maxMarks;

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

    // ---------- Getters & Setters for outer DTO ----------

    public Long getExamId() {
        return examId;
    }

    public void setExamId(Long examId) {
        this.examId = examId;
    }

    public String getExamName() {
        return examName;
    }

    public void setExamName(String examName) {
        this.examName = examName;
    }

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }

    public String getStudentName() {
        return studentName;
    }

    public void setStudentName(String studentName) {
        this.studentName = studentName;
    }

    public Integer getTotalMarksObtained() {
        return totalMarksObtained;
    }

    public void setTotalMarksObtained(Integer totalMarksObtained) {
        this.totalMarksObtained = totalMarksObtained;
    }

    public Integer getTotalMaxMarks() {
        return totalMaxMarks;
    }

    public void setTotalMaxMarks(Integer totalMaxMarks) {
        this.totalMaxMarks = totalMaxMarks;
    }

    public Double getPercentage() {
        return percentage;
    }

    public void setPercentage(Double percentage) {
        this.percentage = percentage;
    }

    public List<SubjectMark> getSubjects() {
        return subjects;
    }

    public void setSubjects(List<SubjectMark> subjects) {
        this.subjects = subjects;
    }
}
