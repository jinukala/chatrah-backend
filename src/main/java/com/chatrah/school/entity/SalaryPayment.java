package com.chatrah.school.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Represents a salary payment transaction made to a teacher.
 */
@Entity
@Table(name = "salary_payments")
public class SalaryPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** Teacher to whom the salary was paid. */
    public Long teacherId;

    /** Amount paid. */
    @Column(nullable = false)
    public Integer amount;

    /** Month to which the salary relates (e.g. "2024-06"). */
    @Column(nullable = false)
    public String month;

    /** Payment mode such as BANK_TRANSFER, UPI. */
    @Column(nullable = false)
    public String mode;

    /** SUCCESS, FAILED, PENDING. */
    @Column(nullable = false)
    public String status;

    public LocalDateTime paidOn;

    public String transactionId;

    public String utrNumber;

    /** School bank account from which this salary was debited. */
    public Long creditedFromAccountId;

    @Column(nullable = false, updatable = false)
    public LocalDateTime createdAt;

    public LocalDateTime updatedAt;

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

    public Long getTeacherId() { return teacherId; }

    public void setTeacherId(Long teacherId) { this.teacherId = teacherId; }

    public Integer getAmount() { return amount; }

    public void setAmount(Integer amount) { this.amount = amount; }

    public String getMonth() { return month; }

    public void setMonth(String month) { this.month = month; }

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

    public Long getCreditedFromAccountId() { return creditedFromAccountId; }

    public void setCreditedFromAccountId(Long creditedFromAccountId) { this.creditedFromAccountId = creditedFromAccountId; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
