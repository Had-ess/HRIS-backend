package com.hris.auth.service;

import com.hris.common.exception.EmailDeliveryException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmployeeOnboardingEmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from:no-reply@hris.local}")
    private String fromAddress;

    @Value("${app.onboarding.login-url:http://localhost:4200}")
    private String loginUrl;

    public void sendCredentials(
            String email,
            String firstName,
            String username,
            String password,
            boolean requirePasswordChange) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setFrom(fromAddress);
        message.setSubject("Your HRIS account details");
        message.setText(buildBody(firstName, username, email, password, requirePasswordChange));

        try {
            mailSender.send(message);
        } catch (MailException ex) {
            throw new EmailDeliveryException(
                "Employee account could not be created because the onboarding email could not be sent.",
                ex
            );
        }
    }

    private String buildBody(
            String firstName,
            String username,
            String email,
            String password,
            boolean requirePasswordChange) {
        String passwordNote = requirePasswordChange
            ? "You will be required to change this password the first time you sign in."
            : "You can change this password later from your account settings.";

        return """
            Hello %s,

            Your HRIS account has been created.

            Login URL: %s
            Username: %s
            Email: %s
            Temporary password: %s

            %s

            Please keep these credentials secure.
            """.formatted(firstName, loginUrl, username, email, password, passwordNote);
    }
}
