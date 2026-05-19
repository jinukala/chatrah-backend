// src/main/java/com/chatrah/school/config/PaymentConfig.java
package com.chatrah.school.config;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Configuration wrapper for Razorpay payment integration.
 * In TEST mode this uses sandbox keys, in live mode you only swap the values.
 */
@ApplicationScoped
public class PaymentConfig {

    @ConfigProperty(name = "razorpay.key_id")
    String keyId;

    @ConfigProperty(name = "razorpay.key_secret")
    String keySecret;

    @ConfigProperty(name = "razorpay.webhook_secret")
    String webhookSecret;

    public String getKeyId() {
        return keyId;
    }

    public String getKeySecret() {
        return keySecret;
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }
}
