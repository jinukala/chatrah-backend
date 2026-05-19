// src/main/java/com/chatrah/school/dto/AttendanceAnalyticsDTO.java
package com.chatrah.school.dto;

import java.util.List;

public class AttendanceAnalyticsDTO {

    public static class ClassAttendance {
        private String className;
        private String section;
        private Double averageAttendancePercentage;

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

        public Double getAverageAttendancePercentage() {
            return averageAttendancePercentage;
        }

        public void setAverageAttendancePercentage(Double averageAttendancePercentage) {
            this.averageAttendancePercentage = averageAttendancePercentage;
        }
    }

    private Double schoolAverageAttendance;
    private List<ClassAttendance> classWise;

    public Double getSchoolAverageAttendance() {
        return schoolAverageAttendance;
    }

    public void setSchoolAverageAttendance(Double schoolAverageAttendance) {
        this.schoolAverageAttendance = schoolAverageAttendance;
    }

    public List<ClassAttendance> getClassWise() {
        return classWise;
    }

    public void setClassWise(List<ClassAttendance> classWise) {
        this.classWise = classWise;
    }
}
