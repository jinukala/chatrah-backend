// src/main/java/com/chatrah/school/dto/ExamAnalyticsDTO.java
package com.chatrah.school.dto;

import java.util.List;

public class ExamAnalyticsDTO {

    public static class SubjectStats {
        private String subject;
        private Double averageMarks;
        private Double passPercentage;
        // getters/setters...


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

        public Double getPassPercentage() {
            return passPercentage;
        }

        public void setPassPercentage(Double passPercentage) {
            this.passPercentage = passPercentage;
        }
    }

    private Long examId;
    private String examName;
    private Double overallPassPercentage;
    private List<SubjectStats> subjects;

    // getters/setters...


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

    public Double getOverallPassPercentage() {
        return overallPassPercentage;
    }

    public void setOverallPassPercentage(Double overallPassPercentage) {
        this.overallPassPercentage = overallPassPercentage;
    }

    public List<SubjectStats> getSubjects() {
        return subjects;
    }

    public void setSubjects(List<SubjectStats> subjects) {
        this.subjects = subjects;
    }
}
