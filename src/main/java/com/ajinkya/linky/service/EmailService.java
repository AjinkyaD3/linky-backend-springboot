package com.ajinkya.linky.service;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final Resend resend;
    private final String resendApiKey;

    @Value("${app.mail-from}")
    private String mailFrom;

    public EmailService(@Value("${resend.api-key}") String resendApiKey) {
        this.resendApiKey = resendApiKey;
        // The Resend client doesn't validate the key at construction time, so an
        // unconfigured (blank) key is caught explicitly in sendPasswordResetEmail
        // instead of making a doomed network call on every send.
        this.resend = new Resend(resendApiKey == null || resendApiKey.isBlank() ? "unset" : resendApiKey);
    }

    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        if (resendApiKey == null || resendApiKey.isBlank()) {
            log.warn("RESEND_API_KEY is not set; skipping password reset email to {}", toEmail);
            return;
        }

        CreateEmailOptions params = CreateEmailOptions.builder()
                .from(mailFrom)
                .to(toEmail)
                .subject("Reset your Linky password")
                .html("""
                        <p>You requested a password reset.</p>
                        <p>Reset your password using the link below (valid for 1 hour):</p>
                        <p><a href="%s">%s</a></p>
                        <p>If you didn't request this, you can safely ignore this email.</p>
                        """.formatted(resetLink, resetLink))
                .build();

        try {
            resend.emails().send(params);
        } catch (ResendException e) {
            // Don't let email delivery failures reveal account existence or leak into a 500 to the caller.
            log.error("Failed to send password reset email to {}: {}", toEmail, e.getMessage());
        }
    }
}
