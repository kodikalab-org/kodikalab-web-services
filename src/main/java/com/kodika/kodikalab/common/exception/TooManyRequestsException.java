package com.kodika.kodikalab.common.exception;

/** El cliente superó un límite de intentos o de envíos; se responde 429. */
public class TooManyRequestsException extends RuntimeException {
    public TooManyRequestsException(String message) {
        super(message);
    }
}
