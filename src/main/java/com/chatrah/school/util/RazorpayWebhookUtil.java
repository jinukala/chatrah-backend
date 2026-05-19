package com.chatrah.school.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

/**
 * Utility for verifying Razorpay webhook signature using HMAC-SHA256.
 */
public class RazorpayWebhookUtil {

    private static final Logger LOG = Logger.getLogger(RazorpayWebhookUtil.class.getName());

    public static boolean verifySignature(String payload, String actualSignature, String secret) {
        if (payload == null || actualSignature == null || secret == null) {
            LOG.severe("Webhook verification failed: null payload, signature, or secret");
            return false;
        }
        if (secret.startsWith("placeholder") || secret.startsWith("your_")) {
            LOG.severe("Webhook secret is not configured — rejecting webhook. Set RAZORPAY_WEBHOOK_SECRET env var.");
            return false;
        }
        try {
            String expected = hmacSha256(payload, secret);
            boolean valid = constantTimeEquals(expected, actualSignature);
            if (!valid) {
                LOG.severe("Webhook signature mismatch — possible forgery attempt");
            }
            return valid;
        } catch (Exception e) {
            LOG.severe("Webhook signature verification error: " + e.getMessage());
            return false;
        }
    }

    private static String hmacSha256(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(keySpec);
        byte[] result = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : result) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
