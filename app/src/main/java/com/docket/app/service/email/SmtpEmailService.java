package com.docket.app.service.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

// Sends through any SMTP server. Locally that's Mailpit (see compose.yaml).
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "docket.email.provider", havingValue = "smtp")
public class SmtpEmailService implements EmailService {
    private final JavaMailSender mailSender;

    @Value("${docket.email.from}")
    private String from;

    @Override
    public void send(EmailMessage message) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(message.to());
            helper.setSubject(message.subject());
            helper.setText(message.textBody(), message.htmlBody());
            mailSender.send(mimeMessage);
        } catch (MessagingException e) {
            throw new EmailSendException("Could not build email", true, e);
        } catch (MailException e) {
            throw new EmailSendException("SMTP send failed", false, e);
        }
    }
}
