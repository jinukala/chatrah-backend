// src/main/java/com/chatrah/school/dto/FeeSummaryDTO.java
package com.chatrah.school.dto;

import java.util.List;

/**
 * Summary of a student's fee status used by student dashboard and
 * principal/clerk fee details view.
 */
public class FeeSummaryDTO {

    private Long studentId;
    private String studentName;
    private String className;
    private String section;

    /** Final total fee including class + hostel + transport + advanced courses and concessions. */
    private Integer totalFee;

    /** Sum of all successful payments. */
    private Integer totalPaid;

    /** Remaining amount due. */
    private Integer due;

    /** Optional: base class fee (day scholar). */
    private Integer baseFee;

    /** Optional: hostel fee component applied to this student. */
    private Integer hostelFeeComponent;

    /** Optional: transport fee component applied to this student. */
    private Integer transportFeeComponent;

    /** Optional: advanced courses fee component (if you add courses). */
    private Integer advancedCourseFeeComponent;

    /** List of individual payments made by the student. */
    private List<FeePaymentHistoryDTO> payments;

    // getters/setters...

    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }

    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }

    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }

    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }

    public Integer getTotalFee() { return totalFee; }
    public void setTotalFee(Integer totalFee) { this.totalFee = totalFee; }

    public Integer getTotalPaid() { return totalPaid; }
    public void setTotalPaid(Integer totalPaid) { this.totalPaid = totalPaid; }

    public Integer getDue() { return due; }
    public void setDue(Integer due) { this.due = due; }

    public Integer getBaseFee() { return baseFee; }
    public void setBaseFee(Integer baseFee) { this.baseFee = baseFee; }

    public Integer getHostelFeeComponent() { return hostelFeeComponent; }
    public void setHostelFeeComponent(Integer hostelFeeComponent) { this.hostelFeeComponent = hostelFeeComponent; }

    public Integer getTransportFeeComponent() { return transportFeeComponent; }
    public void setTransportFeeComponent(Integer transportFeeComponent) { this.transportFeeComponent = transportFeeComponent; }

    public Integer getAdvancedCourseFeeComponent() { return advancedCourseFeeComponent; }
    public void setAdvancedCourseFeeComponent(Integer advancedCourseFeeComponent) { this.advancedCourseFeeComponent = advancedCourseFeeComponent; }

    public List<FeePaymentHistoryDTO> getPayments() { return payments; }
    public void setPayments(List<FeePaymentHistoryDTO> payments) { this.payments = payments; }
}
