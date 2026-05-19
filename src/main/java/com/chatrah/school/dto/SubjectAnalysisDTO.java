package com.chatrah.school.dto;

/**
 * DTO for analyzing performance of a particular subject in a class and exam.
 * Used for charts and analytics on principal and teacher dashboards.
 */
public class SubjectAnalysisDTO {

    private Long examId;
    private String examName;

    private Long classId;
    private String className;
    private String section;

    private String subject;

    /** Average marks in this subject across the class. */
    private Double averageMarks;

    /** Maximum marks obtained in this subject. */
    private Integer highestMarks;

    /** Minimum marks obtained in this subject. */
    private Integer lowestMarks;

    /** Pass percentage in this subject based on a configured pass mark. */
    private Double passPercentage;

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

    public Long getClassId() {
        return classId;
    }

    public void setClassId(Long classId) {
        this.classId = classId;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public Double getAverageMarks() {
        return averageMarks;
    }

    public void setAverageMarks(Double averageMarks) {
        this.averageMarks = averageMarks;
    }

    public Integer getHighestMarks() {
        return highestMarks;
    }

    public void setHighestMarks(Integer highestMarks) {
        this.highestMarks = highestMarks;
    }

    public Integer getLowestMarks() {
        return lowestMarks;
    }

    public void setLowestMarks(Integer lowestMarks) {
        this.lowestMarks = lowestMarks;
    }

    public Double getPassPercentage() {
        return passPercentage;
    }

    public void setPassPercentage(Double passPercentage) {
        this.passPercentage = passPercentage;
    }
}
