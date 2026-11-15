package app.stocklist.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import app.stocklist.user.AppUser;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class SmtpPasswordResetMailerTest {
    private AppUser owner() {
        AppUser u = mock(AppUser.class);
        org.mockito.Mockito.when(u.getEmail()).thenReturn("owner@example.com");
        org.mockito.Mockito.when(u.getFullName()).thenReturn("Asha Sharma");
        org.mockito.Mockito.when(u.getUsername()).thenReturn("asha");
        return u;
    }

    @Test
    void emailsTheCodeToTheOwner() {
        JavaMailSender sender = mock(JavaMailSender.class);
        new SmtpPasswordResetMailer(sender, "Shop <no-reply@example.com>", Duration.ofMinutes(30)).send(owner(), "CODE-123");
        ArgumentCaptor<SimpleMailMessage> m = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(m.capture());
        assertEquals("owner@example.com", m.getValue().getTo()[0]);
        assertEquals("Shop <no-reply@example.com>", m.getValue().getFrom());
        assertTrue(m.getValue().getText().contains("CODE-123"));
        assertTrue(m.getValue().getText().contains("expires in 30 minutes"));
        assertTrue(m.getValue().getText().contains("\"asha\""));
    }

    @Test
    void aMailFailureIsNotThrownToTheCaller() {
        JavaMailSender sender = mock(JavaMailSender.class);
        doThrow(new MailSendException("down")).when(sender).send(any(SimpleMailMessage.class));
        new SmtpPasswordResetMailer(sender, "x@example.com", Duration.ofMinutes(30)).send(owner(), "CODE-123");
    }
}
