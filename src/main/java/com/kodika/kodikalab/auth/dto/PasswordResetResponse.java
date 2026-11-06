package com.kodika.kodikalab.auth.dto;

/** Respuesta de los tres pasos del restablecimiento: solo un mensaje, nunca el código ni datos de la cuenta. */
public record PasswordResetResponse(String message) {
}
