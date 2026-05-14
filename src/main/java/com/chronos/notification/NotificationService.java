package com.chronos.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String mailFrom;

    public void sendJobSuccess(String recipientEmail, String jobName) {
        send(recipientEmail,
                "Job completed: " + jobName,
                "Your job '" + jobName + "' completed successfully.");
    }

    public void sendJobDead(String recipientEmail, String jobName, String reason) {
        send(recipientEmail,
                "Job failed permanently: " + jobName,
                "Your job '" + jobName + "' has exhausted all retries and moved to the dead-letter queue.\n"
                        + "Reason: " + reason);
    }

    private void send(String to, String subject, String text) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailFrom);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
        } catch (Exception e) {
            log.warn("Failed to send notification to {}: {}", to, e.getMessage());
        }
    }
}
