// src/main/java/com/chatrah/school/service/PaymentService.java
package com.chatrah.school.service;

import com.chatrah.school.config.PaymentConfig;
import com.chatrah.school.dto.CreatePaymentOrderResponseDTO;
import com.chatrah.school.dto.FeeSummaryDTO;
import com.chatrah.school.dto.OnlineFeePaymentRequestDTO;
import com.chatrah.school.entity.FeePayment;
import com.chatrah.school.entity.Student;
import com.chatrah.school.repository.FeePaymentRepository;
import com.chatrah.school.repository.StudentRepository;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import io.quarkus.cache.CacheInvalidate;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import org.json.JSONObject;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Handles integration with Razorpay (TEST mode).
 * - Creates orders for fee payments
 * - Processes payment success via webhook
 */
@ApplicationScoped
public class PaymentService {

    private static final Logger LOG = Logger.getLogger(PaymentService.class.getName());

    @Inject
    PaymentConfig paymentConfig;

    @Inject
    RazorpayClient razorpayClient;

    @Inject
    StudentRepository studentRepository;

    @Inject
    FeePaymentRepository feePaymentRepository;

    @Inject
    FeeService feeService;

    @Inject
    NotificationService notificationService;

    /**
     * Create a Razorpay order for a student's online fee payment in TEST mode.
     * Also pre-creates a FeePayment row with status PENDING.
     */
    @Transactional
    public CreatePaymentOrderResponseDTO createOrder(Long studentId, OnlineFeePaymentRequestDTO request) throws Exception {
        Student student = studentRepository.findById(studentId);
        if (student == null) throw new NotFoundException("Student not found");

        Integer amount = request.getAmount();
        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException("Amount must be > 0");
        }

        RazorpayClient client = razorpayClient;

        JSONObject orderRequest = new JSONObject();
        orderRequest.put("amount", amount * 100); // Razorpay expects paise
        orderRequest.put("currency", "INR");
        orderRequest.put("receipt", "RCP-" + System.currentTimeMillis());
        orderRequest.put("payment_capture", 1); // auto capture

        Order order = client.Orders.create(orderRequest);

        // Pre-create pending FeePayment row
        FeePayment payment = new FeePayment();
        payment.setStudent(student);
        payment.setAmount(amount);
        payment.setMode("UPI");
        payment.setStatus("PENDING");
        payment.setPgOrderId(order.get("id"));
        payment.setReceiptNo(order.get("receipt"));
        feePaymentRepository.persist(payment);

        // Build response for frontend
        CreatePaymentOrderResponseDTO dto = new CreatePaymentOrderResponseDTO();
        dto.setOrderId(order.get("id"));
        dto.setAmount(amount);
        dto.setCurrency("INR");
        dto.setStudentId(studentId);
        dto.setStudentName(student.getName());
        dto.setRazorpayKeyId(paymentConfig.getKeyId()); // public key for JS

        return dto;
    }

    @CacheInvalidate(cacheName = "fee-summary")
    public void invalidateFeeSummaryCache(Long studentId) {
        // body can be empty; annotation handles cache clear
    }

    /**
     * Handle successful payment from webhook.
     * This is called after verifying Razorpay webhook signature.
     */
    @Transactional
    public void handlePaymentSuccess(String orderId, String paymentId, Integer amount,
                                     String signature, LocalDateTime paidAt) {

        FeePayment payment = feePaymentRepository
                .find("pgOrderId", orderId).firstResult();

        if (payment == null) {
            LOG.warning("Webhook received for unknown order: " + orderId);
            return;
        }

        payment.setStatus("SUCCESS");
        payment.setPgPaymentId(paymentId);
        payment.setPgSignature(signature);
        payment.setPaidOn(paidAt != null ? paidAt : LocalDateTime.now());
        payment.setTransactionId(paymentId);
        if (payment.getReceiptNo() == null) {
            payment.setReceiptNo("RCP-" + UUID.randomUUID());
        }

        feePaymentRepository.persist(payment);

        // Invalidate fee summary cache and recompute
        Student student = payment.getStudent();
        if (student != null) {
            invalidateFeeSummaryCache(student.getId());
            FeeSummaryDTO summary = feeService.computeFeeSummary(student.getId());
            notificationService.sendFeePaymentNotification(student, payment, summary);
        }
    }
}
