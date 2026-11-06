package com.kodika.kodikalab.auth;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class SmtpPasswordResetMailerTests {
    private final JavaMailSender sender = mock(JavaMailSender.class);

    private SmtpPasswordResetMailer mailer(String from) {
        // Ejecutor síncrono: en producción el envío corre en segundo plano.
        return new SmtpPasswordResetMailer(sender, from, Runnable::run);
    }

    @Test
    void sendsTheCodeToTheRecipientFromTheConfiguredSender() {
        mailer("no-reply@kodikalab.test").sendCode("camila@upc.edu.pe", "Camila Rojas", "483920");
        var captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertThat(message.getFrom()).isEqualTo("no-reply@kodikalab.test");
        assertThat(message.getTo()).containsExactly("camila@upc.edu.pe");
        assertThat(message.getSubject()).isEqualTo(SmtpPasswordResetMailer.SUBJECT);
        assertThat(message.getText()).contains("Hola Camila Rojas,").contains("483920")
                .contains("vence en 10 minutos").contains("Si no pediste este cambio");
    }

    @Test
    void theBodyDoesNotBreakWithoutAName() {
        assertThat(SmtpPasswordResetMailer.body(null, "123456")).startsWith("Hola ,").contains("123456");
        assertThat(SmtpPasswordResetMailer.body("  ", "123456")).contains("123456");
    }

    @Test
    void aSendFailureIsSwallowedSoTheRequestOutcomeDoesNotDependOnTheAccount() {
        doThrow(new MailSendException("SMTP caído para camila@upc.edu.pe código 483920")).when(sender)
                .send(any(SimpleMailMessage.class));
        assertThatCode(() -> mailer("no-reply@kodikalab.test").sendCode("camila@upc.edu.pe", "Camila", "483920"))
                .doesNotThrowAnyException();
    }

    @Test
    void itIsConfiguredOnlyWithASender() {
        assertThat(mailer("no-reply@kodikalab.test").isConfigured()).isTrue();
        assertThat(mailer("").isConfigured()).isFalse();
        assertThat(mailer("   ").isConfigured()).isFalse();
        mailer("").sendCode("camila@upc.edu.pe", "Camila", "483920");
        verify(sender, never()).send(any(SimpleMailMessage.class));
    }
}
