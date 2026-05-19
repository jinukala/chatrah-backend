package com.chatrah.school.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Represents a single fee payment transaction made by or for a student.
 */
@Entity
@Table(name = "fee_payments")
public class FeePayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Student for whom the payment was made. */
    @ManyToOne(optional = false)
    @JoinColumn(name = "student_id")
    public Student student;

    /** Amount paid in this transaction. */
    @Column(nullable = false)
    public Integer amount;

    /** Payment mode: UPI, CASH, CARD, BANK_TRANSFER, etc. */
    @Column(nullable = false)
    public String mode;

    /** SUCCESS, FAILED, PENDING. */
    @Column(nullable = false)
    public String status;

    /** Date-time when the payment was confirmed. */
    public LocalDateTime paidOn;

    /** Transaction identifier from the payment gateway or bank. */
    public String transactionId;

    /** UTR number or reference number from bank/UPI. */
    public String utrNumber;

    /** Receipt number used for printed/downloadable receipts. */
    public String receiptNo;

    /** Bank account to which amount was credited (school account). */
    public Long creditedToAccountId;

    /** Record creation timestamp. */
    @Column(nullable = false, updatable = false)
    public LocalDateTime createdAt;

    public LocalDateTime updatedAt;

    // inside FeePayment entity
    /** Razorpay Order ID (e.g. order_KlXxxx) */
    private String pgOrderId;

    /** Razorpay Payment ID (e.g. pay_KlYxxx) */
    private String pgPaymentId;

    /** Razorpay signature (for verification, optional storage) */
    private String pgSignature;


    public String getPgOrderId() { return pgOrderId; }
    public void setPgOrderId(String pgOrderId) { this.pgOrderId = pgOrderId; }

    public String getPgPaymentId() { return pgPaymentId; }
    public void setPgPaymentId(String pgPaymentId) { this.pgPaymentId = pgPaymentId; }

    public String getPgSignature() { return pgSignature; }
    public void setPgSignature(String pgSignature) { this.pgSignature = pgSignature; }


    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
        if (paidOn == null && "SUCCESS".equalsIgnoreCase(status)) {
            paidOn = createdAt;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Getters and setters

    public Long getId() { return id; }

    public void setId(Long id) { this.id = id; }

    public Student getStudent() { return student; }

    public void setStudent(Student student) { this.student = student; }

    public Integer getAmount() { return amount; }

    public void setAmount(Integer amount) { this.amount = amount; }

    public String getMode() { return mode; }

    public void setMode(String mode) { this.mode = mode; }

    public String getStatus() { return status; }

    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getPaidOn() { return paidOn; }

    public void setPaidOn(LocalDateTime paidOn) { this.paidOn = paidOn; }

    public String getTransactionId() { return transactionId; }

    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public String getUtrNumber() { return utrNumber; }

    public void setUtrNumber(String utrNumber) { this.utrNumber = utrNumber; }

    public String getReceiptNo() { return receiptNo; }

    public void setReceiptNo(String receiptNo) { this.receiptNo = receiptNo; }

    public Long getCreditedToAccountId() { return creditedToAccountId; }

    public void setCreditedToAccountId(Long creditedToAccountId) { this.creditedToAccountId = creditedToAccountId; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
