// src/main/java/com/chatrah/school/dto/AttendanceSummaryDTO.java
package com.chatrah.school.dto;

/**
 * High-level attendance summary used in dashboards
 * for students, classes, or whole-school analytics.
 */
public class AttendanceSummaryDTO {

    private Long targetId;
    private String targetType;   // STUDENT / CLASS / SCHOOL
    private Integer totalDays;
    private Integer presentDays;
    private Integer absentDays;
    private Double attendancePercentage;

    public Long getTargetId() {
        return targetId;
    }

    public void setTargetId(Long targetId) {
        this.targetId = targetId;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public Integer getTotalDays() {
        return totalDays;
    }

    public void setTotalDays(Integer totalDays) {
        this.totalDays = totalDays;
    }

    public Integer getPresentDays() {
        return presentDays;
    }

    public void setPresentDays(Integer presentDays) {
        this.presentDays = presentDays;
    }

    public Integer getAbsentDays() {
        return absentDays;
    }

    public void setAbsentDays(Integer absentDays) {
        this.absentDays = absentDays;
    }

    public Double getAttendancePercentage() {
        return attendancePercentage;
    }

    public void setAttendancePercentage(Double attendancePercentage) {
        this.attendancePercentage = attendancePercentage;
    }
}
