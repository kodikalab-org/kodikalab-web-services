package com.kodika.kodikalab.auth;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Envío por SMTP con la configuración {@code spring.mail.*}; el remitente sale de {@code kodikalab.mail.from}. */
@Component
public class SmtpPasswordResetMailer implements PasswordResetMailer {
    private static final Logger LOG = LoggerFactory.getLogger(SmtpPasswordResetMailer.class);
    static final String SUBJECT = "Tu código para restablecer la contraseña de Kodikalab";

    private final JavaMailSender sender;
    private final String from;
    private final Executor executor;
    private final ExecutorService ownedExecutor;

    @Autowired
    public SmtpPasswordResetMailer(JavaMailSender sender, @Value("${kodikalab.mail.from:}") String from) {
        this.sender = sender;
        this.from = from == null ? "" : from.strip();
        this.ownedExecutor = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "password-reset-mail");
            thread.setDaemon(true);
            return thread;
        });
        this.executor = ownedExecutor;
    }

    SmtpPasswordResetMailer(JavaMailSender sender, String from, Executor executor) {
        this.sender = sender;
        this.from = from;
        this.executor = executor;
        this.ownedExecutor = null;
    }

    @Override
    public boolean isConfigured() {
        return !from.isBlank();
    }

    @Override
    public void sendCode(String to, String fullName, String code) {
        if (!isConfigured()) {
            return;
        }
        executor.execute(() -> {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(from);
                message.setTo(to);
                message.setSubject(SUBJECT);
                message.setText(body(fullName, code));
                sender.send(message);
            } catch (MailException exception) {
                // Solo el tipo de error: el mensaje puede incluir el destinatario y nunca debe incluir el código.
                LOG.warn("No se pudo enviar el correo de restablecimiento de contraseña ({})",
                        exception.getClass().getSimpleName());
            }
        });
    }

    static String body(String fullName, String code) {
        return "Hola " + (fullName == null || fullName.isBlank() ? "" : fullName.strip()) + ",\n\n"
                + "Recibimos una solicitud para restablecer la contraseña de tu cuenta de Kodikalab.\n\n"
                + "Tu código de verificación es: " + code + "\n\n"
                + "El código vence en 10 minutos y solo sirve una vez. Si no pediste este cambio, ignora este "
                + "mensaje: tu contraseña no se modificará.\n\n"
                + "Por tu seguridad, no compartas este código con nadie.\n\n"
                + "Equipo Kodikalab\n";
    }

    @PreDestroy
    void shutdown() {
        if (ownedExecutor != null) {
            ownedExecutor.shutdown();
        }
    }
}
