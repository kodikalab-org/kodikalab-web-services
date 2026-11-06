package com.kodika.kodikalab.common.exception;

/** Una dependencia externa necesaria no está disponible o no está configurada; se responde 503. */
public class ServiceUnavailableException extends RuntimeException {
    public ServiceUnavailableException(String message) {
        super(message);
    }
}
