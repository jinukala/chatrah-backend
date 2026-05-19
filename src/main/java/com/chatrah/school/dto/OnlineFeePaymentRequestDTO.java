// src/main/java/com/chatrah/school/dto/OnlineFeePaymentRequestDTO.java
package com.chatrah.school.dto;

/**
 * Request body when a student initiates an online fee payment.
 * They can choose a custom amount up to their pending due.
 */
public class OnlineFeePaymentRequestDTO {

    /**
     * Amount the student wishes to pay in this transaction.
     * Must be greater than 0 and less than or equal to the pending due.
     */
    private Integer amount;

    /**
     * Payment mode used for this transaction. Example: UPI_APP, UPI_QR.
     */
    private String mode;

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
}
