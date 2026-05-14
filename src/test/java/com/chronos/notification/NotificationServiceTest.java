package com.chronos.notification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private NotificationService notificationService;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(notificationService, "mailFrom", "noreply@chronos.io");
    }

    @Test
    void sendJobSuccess_sends_email_with_correct_fields() {
        notificationService.sendJobSuccess("alice@example.com", "Nightly Report");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();

        assertThat(message.getFrom()).isEqualTo("noreply@chronos.io");
        assertThat(message.getTo()).containsExactly("alice@example.com");
        assertThat(message.getSubject()).contains("Nightly Report");
        assertThat(message.getText()).contains("successfully");
    }

    @Test
    void sendJobDead_sends_email_with_failure_reason() {
        notificationService.sendJobDead("alice@example.com", "Nightly Report", "HTTP 503");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();

        assertThat(message.getTo()).containsExactly("alice@example.com");
        assertThat(message.getSubject()).contains("failed permanently");
        assertThat(message.getText()).contains("HTTP 503");
    }

    @Test
    void send_swallows_mail_exception() {
        doThrow(new RuntimeException("SMTP down")).when(mailSender).send(any(SimpleMailMessage.class));

        // Should not throw
        notificationService.sendJobSuccess("alice@example.com", "My Job");
    }
}
