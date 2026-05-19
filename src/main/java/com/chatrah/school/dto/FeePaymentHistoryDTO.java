// src/main/java/com/chatrah/school/dto/FeePaymentHistoryDTO.java
package com.chatrah.school.dto;

import java.time.LocalDateTime;

/**
 * DTO representing a single payment record in a student's fee history.
 */
public class FeePaymentHistoryDTO {

    private Long paymentId;
    private LocalDateTime paidOn;
    private Integer amount;
    private String mode;
    private String status;
    private String receiptNo;

    public Long getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(Long paymentId) {
        this.paymentId = paymentId;
    }

    public LocalDateTime getPaidOn() {
        return paidOn;
    }

    public void setPaidOn(LocalDateTime paidOn) {
        this.paidOn = paidOn;
    }

    public Integer getAmount() {
        return amount;
    }

    public void setAmount(Integer amount) {
        this.amount = amount;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getReceiptNo() {
        return receiptNo;
    }

    public void setReceiptNo(String receiptNo) {
        this.receiptNo = receiptNo;
    }
}
