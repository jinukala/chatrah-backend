// src/main/java/com/chatrah/school/dto/CreatePaymentOrderResponseDTO.java
package com.chatrah.school.dto;

/**
 * Response sent to UI when creating a Razorpay order
 * for a student's fee payment.
 */
public class CreatePaymentOrderResponseDTO {

    private String orderId;
    private Integer amount;
    private String currency;
    private Long studentId;
    private String studentName;
    private String razorpayKeyId; // public key for JS checkout

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public Integer getAmount() { return amount; }
    public void setAmount(Integer amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }

    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }

    public String getRazorpayKeyId() { return razorpayKeyId; }
    public void setRazorpayKeyId(String razorpayKeyId) { this.razorpayKeyId = razorpayKeyId; }
}
