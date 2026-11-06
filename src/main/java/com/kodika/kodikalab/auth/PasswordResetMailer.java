package com.kodika.kodikalab.auth;

/** Envía por correo el código para restablecer la contraseña. */
public interface PasswordResetMailer {

    /** Hay un remitente configurado, es decir, el servidor puede enviar correos. */
    boolean isConfigured();

    /**
     * Envía el código sin bloquear la petición y sin propagar fallos: así el tiempo de respuesta y el resultado no
     * dependen de si la cuenta existe. Un fallo de envío queda en el registro, nunca el código ni el destinatario.
     */
    void sendCode(String to, String fullName, String code);
}
