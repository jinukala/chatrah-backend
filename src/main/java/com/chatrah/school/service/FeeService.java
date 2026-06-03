package com.chatrah.school.service;

import com.chatrah.school.dto.FeePaymentHistoryDTO;
import com.chatrah.school.dto.FeeReceiptDTO;
import com.chatrah.school.dto.FeeSummaryDTO;
import com.chatrah.school.dto.OnlineFeePaymentRequestDTO;
import com.chatrah.school.entity.*;
import com.chatrah.school.exception.GenericExceptionMapper;
import com.chatrah.school.repository.*;
import com.chatrah.school.resource.AuditLogResource;
import io.quarkus.cache.CacheInvalidate;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Handles fee calculation, payments, and receipts.
 */
@ApplicationScoped
public class FeeService {

    @Inject
    StudentRepository studentRepository;

    @Inject
    FeePlanRepository feePlanRepository;

    @Inject
    FeeOverrideRepository feeOverrideRepository;

    @Inject
    StudentCourseEnrollmentRepository studentCourseEnrollmentRepository;

    @Inject
    FeePaymentRepository feePaymentRepository;

    @Inject
    SchoolBankAccountRepository schoolBankAccountRepository;

    @Inject
    NotificationService notificationService;

    @Inject
    AuditLogResource.AuditService auditService;

    @Inject
    com.chatrah.school.websocket.LiveEventService liveEventService;

    /**
     * Compute fee summary for a student:
     * totalFee = base class fee + hostel + transport (+ advanced courses if you add later)
     * totalPaid = sum of successful payments
     * due = totalFee - totalPaid
     */
    @Transactional
    @CacheResult(cacheName = "fee-summary")
    public FeeSummaryDTO computeFeeSummary(Long studentId) {
        Student student = studentRepository.findById(studentId);
        if (student == null) throw new NotFoundException("Student not found");

        boolean isHosteller = Boolean.TRUE.equals(student.getIsHosteller());
        boolean isTransportUser = Boolean.TRUE.equals(student.getIsTransportUser());
        boolean isIitNeet = Boolean.TRUE.equals(student.getIitNeetOpted());

        int baseClassFee = 0;
        int hostelComponent = 0;
        int transportComponent = 0;
        int iitNeetComponent = 0;

        if (student.getClassRoom() != null) {
            FeePlan plan = feePlanRepository.find("classRoom", student.getClassRoom())
                    .firstResult();
            if (plan != null) {
                baseClassFee = safeInt(plan.getTotalFee());
                if (isHosteller) {
                    hostelComponent = safeInt(plan.getHostelFee());
                }
                if (isTransportUser) {
                    transportComponent = safeInt(plan.getTransportFee());
                }
                if (isIitNeet) {
                    iitNeetComponent = safeInt(plan.getIitNeetFee());
                }
            }
        }

        int totalFee = baseClassFee + hostelComponent + transportComponent + iitNeetComponent;

        // Apply concession/discount
        int concession = student.getFeeConcession() != null ? student.getFeeConcession() : 0;
        totalFee = Math.max(0, totalFee - concession);

        // Sum all successful payments
        List<FeePayment> payments = feePaymentRepository.find("student", student).list();
        int totalPaid = payments.stream()
                .filter(p -> "SUCCESS".equalsIgnoreCase(p.getStatus()))
                .mapToInt(p -> safeInt(p.getAmount()))
                .sum();

        int due = totalFee - totalPaid;
        if (due < 0) {
            due = 0; // avoid negative due
        }

        FeeSummaryDTO dto = new FeeSummaryDTO();
        dto.setStudentId(student.getId());
        dto.setStudentName(student.getName());
        if (student.getClassRoom() != null) {
            dto.setClassName(student.getClassRoom().getClassName());
            dto.setSection(student.getClassRoom().getSection());
        }

        dto.setTotalFee(totalFee);
        dto.setTotalPaid(totalPaid);
        dto.setDue(due);

        // Map payment history if you use it
        List<FeePaymentHistoryDTO> history = payments.stream()
                .map(this::toHistoryDTO)
                .collect(Collectors.toList());
        dto.setPayments(history);

        return dto;
    }

    private int safeInt(Integer v) {
        return v != null ? v : 0;
    }

    @CacheInvalidate(cacheName = "fee-summary")
    public void invalidateFeeSummary(Long studentId) {
        // annotation handles cache invalidation by key
    }

    @Transactional
    public FeeSummaryDTO recordManualPayment(Long studentId, int amount, String mode, String remarks, String performedBy, String role) {
        Student student = studentRepository.findById(studentId);
        if (student == null) throw new NotFoundException("Student not found");

        FeePayment payment = new FeePayment();
        payment.setStudent(student);
        payment.setAmount(amount);
        payment.setMode(mode != null ? mode : "CASH");
        payment.setStatus("SUCCESS");
        payment.setPaidOn(java.time.LocalDateTime.now());
        payment.setTransactionId("MANUAL-" + java.util.UUID.randomUUID().toString().substring(0, 8));
        payment.setReceiptNo("RCP-" + System.currentTimeMillis());
        feePaymentRepository.persist(payment);
        auditService.log("CREATED", "Payment", String.valueOf(payment.getId()), "Payment ₹" + amount + " for " + student.getName() + " via " + mode + " by " + performedBy + " (" + role + ")", performedBy, role);
        liveEventService.paymentRecorded(student.getName(), amount);

        invalidateFeeSummary(studentId);
        return computeFeeSummary(studentId);
    }

    private FeePaymentHistoryDTO toHistoryDTO(FeePayment p) {
        FeePaymentHistoryDTO dto = new FeePaymentHistoryDTO();
        dto.setPaymentId(p.getId());
        dto.setAmount(p.getAmount());
        dto.setPaidOn(p.getPaidOn());
        dto.setMode(p.getMode());
        dto.setStatus(p.getStatus());
        dto.setReceiptNo(p.getReceiptNo());
        return dto;
    }
    /**
     * Create a successful online payment record (mock UPI integration).
     */
    @Transactional
    public FeeSummaryDTO initiateOnlinePayment(Long studentId, OnlineFeePaymentRequestDTO request) {
        Student student = studentRepository.findById(studentId);
        if (student == null) throw new NotFoundException("Student not found");

        SchoolBankAccount account = schoolBankAccountRepository.findActiveAccounts()
                .stream().findFirst().orElse(null);

        FeePayment payment = new FeePayment();
        payment.setStudent(student);
        payment.setAmount(request.getAmount());
        payment.setMode(request.getMode() != null ? request.getMode() : "UPI");
        payment.setStatus("SUCCESS");
        payment.setPaidOn(LocalDateTime.now());
        payment.setTransactionId("TXN-" + UUID.randomUUID());
        payment.setUtrNumber("UTR-" + UUID.randomUUID());
        payment.setReceiptNo("RCP-" + System.currentTimeMillis());
        payment.setCreditedToAccountId(account != null ? account.getId() : null);

        feePaymentRepository.persist(payment);

        // Invalidate cache before recomputing
        invalidateFeeSummary(studentId);

        // Compute updated summary
        FeeSummaryDTO summary = computeFeeSummary(studentId);

        // Notify parent via SMS/email
        notificationService.sendFeePaymentNotification(student, payment, summary);

        return summary;
    }

    /**
     * Build a receipt DTO from a payment record.
     */
    public FeeReceiptDTO buildReceipt(Long paymentId) {
        FeePayment p = feePaymentRepository.findById(paymentId);
        if (p == null) throw new NotFoundException("Payment not found");

        FeeSummaryDTO summary = computeFeeSummary(p.getStudent().getId());

        FeeReceiptDTO dto = new FeeReceiptDTO();
        dto.setTransactionId(String.valueOf(p.getId()));
        dto.setStudentId(p.getStudent().getId());
        dto.setStudentName(p.getStudent().getName());
        dto.setAmountPaid(p.getAmount());
        dto.setPaidOn(p.getPaidOn());
        dto.setTransactionId(p.getTransactionId());
        dto.setUtrNumber(p.getUtrNumber());
        dto.setReceiptNo(p.getReceiptNo());
        dto.setTotalFee(summary.getTotalFee());
        dto.setTotalPaid(summary.getTotalPaid());
        dto.setDueAfterPayment(summary.getDue());
        return dto;
    }
}