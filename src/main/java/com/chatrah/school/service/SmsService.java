package com.chatrah.school.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.util.logging.Logger;

/**
 * SMS service placeholder. Sends SMS via MSG91 when configured,
 * otherwise logs the message.
 *
 * To enable: set SMS_ENABLED=true and SMS_PROVIDER=msg91 with valid MSG91 credentials.
 * See CREDENTIALS_GUIDE.md for setup.
 */
@ApplicationScoped
public class SmsService {

    private static final Logger LOG = Logger.getLogger(SmsService.class.getName());

    @ConfigProperty(name = "sms.enabled", defaultValue = "false")
    boolean smsEnabled;

    @ConfigProperty(name = "sms.provider", defaultValue = "mock")
    String provider;

    @ConfigProperty(name = "sms.msg91.auth_key", defaultValue = "placeholder_auth_key")
    String msg91AuthKey;

    @ConfigProperty(name = "sms.msg91.sender_id", defaultValue = "CHATRAH")
    String senderId;

    @ConfigProperty(name = "sms.msg91.template_id", defaultValue = "placeholder_template_id")
    String templateId;

    /**
     * Send an SMS to the given phone number.
     * In mock mode, logs the message instead of sending.
     */
    public void sendSms(String phoneNumber, String message) {
        if (!smsEnabled) {
            LOG.info("[SMS-MOCK] To: " + phoneNumber + " | Message: " + message);
            return;
        }

        if ("mock".equals(provider)) {
            LOG.info("[SMS-MOCK] To: " + phoneNumber + " | Message: " + message);
            return;
        }

        // TODO: Implement MSG91 HTTP API call when credentials are configured
        // POST https://api.msg91.com/api/v5/flow/
        // Headers: authkey={msg91AuthKey}
        // Body: { "sender": senderId, "route": "4", "template_id": templateId,
        //         "recipients": [{ "mobiles": phoneNumber, "message": message }] }
        LOG.warning("[SMS] Provider '" + provider + "' not yet implemented. Message not sent to: " + phoneNumber);
    }

    /**
     * Send absence notification to a parent.
     */
    public void sendAbsenceAlert(String parentMobile, String parentName, String studentName, String date) {
        String message = String.format(
            "Dear %s, your child %s was marked absent on %s. Please contact the school for details. - Chatrah School",
            parentName, studentName, date
        );
        sendSms(parentMobile, message);
    }
}
