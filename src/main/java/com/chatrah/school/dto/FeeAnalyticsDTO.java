// src/main/java/com/chatrah/school/dto/FeeAnalyticsDTO.java
package com.chatrah.school.dto;

import java.util.List;

public class FeeAnalyticsDTO {

    public static class ClassFeeStats {
        private String className;
        private String section;
        private Integer totalExpected;
        private Integer totalCollected;
        private Integer totalDue;
        // getters/setters...


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

        public Integer getTotalExpected() {
            return totalExpected;
        }

        public void setTotalExpected(Integer totalExpected) {
            this.totalExpected = totalExpected;
        }

        public Integer getTotalCollected() {
            return totalCollected;
        }

        public void setTotalCollected(Integer totalCollected) {
            this.totalCollected = totalCollected;
        }

        public Integer getTotalDue() {
            return totalDue;
        }

        public void setTotalDue(Integer totalDue) {
            this.totalDue = totalDue;
        }
    }

    private Integer schoolTotalExpected;
    private Integer schoolTotalCollected;
    private Integer schoolTotalDue;
    private List<ClassFeeStats> classWise;

    // getters/setters...


    public Integer getSchoolTotalExpected() {
        return schoolTotalExpected;
    }

    public void setSchoolTotalExpected(Integer schoolTotalExpected) {
        this.schoolTotalExpected = schoolTotalExpected;
    }

    public Integer getSchoolTotalCollected() {
        return schoolTotalCollected;
    }

    public void setSchoolTotalCollected(Integer schoolTotalCollected) {
        this.schoolTotalCollected = schoolTotalCollected;
    }

    public Integer getSchoolTotalDue() {
        return schoolTotalDue;
    }

    public void setSchoolTotalDue(Integer schoolTotalDue) {
        this.schoolTotalDue = schoolTotalDue;
    }

    public List<ClassFeeStats> getClassWise() {
        return classWise;
    }

    public void setClassWise(List<ClassFeeStats> classWise) {
        this.classWise = classWise;
    }
}
