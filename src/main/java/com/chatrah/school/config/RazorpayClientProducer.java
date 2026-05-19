package com.chatrah.school.config;

import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import java.util.logging.Logger;

/**
 * Produces a singleton RazorpayClient instance.
 * If credentials are placeholders, the client is still created but will fail on actual API calls.
 */
@ApplicationScoped
public class RazorpayClientProducer {

    private static final Logger LOG = Logger.getLogger(RazorpayClientProducer.class.getName());

    @Inject
    PaymentConfig paymentConfig;

    @Produces
    @ApplicationScoped
    public RazorpayClient razorpayClient() throws RazorpayException {
        String keyId = paymentConfig.getKeyId();
        if (keyId.contains("placeholder")) {
            LOG.warning("Razorpay using placeholder credentials — payment API calls will fail. Set RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET env vars.");
        }
        return new RazorpayClient(keyId, paymentConfig.getKeySecret());
    }
}
