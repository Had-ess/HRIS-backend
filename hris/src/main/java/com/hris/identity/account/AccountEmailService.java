package com.hris.identity.account;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Account-lifecycle emails (activation, password reset), replacing Keycloak's
 * execute-actions emails. Plain-text, bilingual FR/EN like the rest of the app.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountEmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from:no-reply@hris.local}")
    private String fromAddress;

    @Value("${app.onboarding.login-url:http://localhost:4200}")
    private String frontendBaseUrl;

    public void sendActivationEmail(String email, String displayName, String rawToken) {
        String link = frontendBaseUrl + "/auth/activate?token=" + rawToken;
        send(email,
            "Activez votre compte HRIS / Activate your HRIS account",
            """
            Bonjour %s,

            Votre compte HRIS a été créé. Cliquez sur le lien ci-dessous pour choisir \
            votre mot de passe et activer votre compte (valable 24 heures) :

            %s

            ---

            Hello %s,

            Your HRIS account has been created. Use the link below to choose your \
            password and activate your account (valid for 24 hours):

            %s
            """.formatted(displayName, link, displayName, link));
    }

    public void sendPasswordResetEmail(String email, String displayName, String rawToken) {
        String link = frontendBaseUrl + "/auth/reset-password?token=" + rawToken;
        send(email,
            "Réinitialisation de votre mot de passe HRIS / HRIS password reset",
            """
            Bonjour %s,

            Une réinitialisation de mot de passe a été demandée pour votre compte. \
            Si vous êtes à l'origine de cette demande, cliquez sur le lien ci-dessous \
            (valable 15 minutes) :

            %s

            Si vous n'êtes pas à l'origine de cette demande, ignorez cet e-mail.

            ---

            Hello %s,

            A password reset was requested for your account. If you made this request, \
            use the link below (valid for 15 minutes):

            %s

            If you did not request this, you can safely ignore this email.
            """.formatted(displayName, link, displayName, link));
    }

    private void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
        log.info("Account email sent to {}: {}", to, subject);
    }
}
