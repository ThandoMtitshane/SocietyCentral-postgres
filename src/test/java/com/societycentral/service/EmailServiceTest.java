package com.societycentral.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class EmailServiceTest {

    @Test
    void sendSkipsEmailWhenApiKeyIsBlank() {
        EmailService emailService = new EmailService(
                "",
                "onboarding@resend.dev",
                "SocietyCentral"
        );

        assertDoesNotThrow(() -> emailService.send(
                EmailType.FORGOT_PASSWORD,
                "student@example.com",
                Map.of("resetLink", "http://localhost/reset")
        ));
    }
}
