// src/main/java/com/chatrah/school/resource/PaymentResource.java
package com.chatrah.school.resource;

import com.chatrah.school.config.PaymentConfig;
import com.chatrah.school.dto.CreatePaymentOrderResponseDTO;
import com.chatrah.school.dto.OnlineFeePaymentRequestDTO;
import com.chatrah.school.security.SecurityRoles;
import com.chatrah.school.service.PaymentService;
import com.chatrah.school.util.RazorpayWebhookUtil;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.json.JSONObject;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * REST endpoints for fee payment via Razorpay (TEST mode).
 */
@Path("/api/payments")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class PaymentResource {

    @Inject
    PaymentService paymentService;

    @Inject
    PaymentConfig paymentConfig;

    /**
     * Student initiates an online fee payment:
     * creates a Razorpay order and returns data for JS Checkout.
     */
    @POST
    @Path("/student/{studentId}/create-order")
    @RolesAllowed({SecurityRoles.STUDENT, SecurityRoles.PRINCIPAL, SecurityRoles.CLERK})
    public CreatePaymentOrderResponseDTO createOrder(@PathParam("studentId") Long studentId,
                                                     OnlineFeePaymentRequestDTO request) throws Exception {
        return paymentService.createOrder(studentId, request);
    }

    /**
     * Razorpay webhook endpoint (TEST mode & later live).
     * Configure URL in Razorpay Dashboard → Webhooks.
     */
    @POST
    @Path("/webhook")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response handleWebhook(String payload,
                                  @HeaderParam("X-Razorpay-Signature") String signature) {
        boolean valid = RazorpayWebhookUtil.verifySignature(payload, signature, paymentConfig.getWebhookSecret());
        if (!valid) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Invalid signature").build();
        }

        JSONObject json = new JSONObject(payload);
        String event = json.optString("event", "");

        if ("payment.captured".equals(event) || "payment.authorized".equals(event)) {
            JSONObject paymentEntity = json.getJSONObject("payload")
                    .getJSONObject("payment")
                    .getJSONObject("entity");

            String orderId = paymentEntity.getString("order_id");
            String paymentId = paymentEntity.getString("id");
            int amountPaise = paymentEntity.getInt("amount");
            Integer amount = amountPaise / 100;

            long createdEpoch = paymentEntity.optLong("created_at", System.currentTimeMillis() / 1000);
            LocalDateTime paidAt = LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(createdEpoch),
                    ZoneId.systemDefault()
            );

            paymentService.handlePaymentSuccess(orderId, paymentId, amount, signature, paidAt);
        }

        return Response.ok().build();
    }
}
