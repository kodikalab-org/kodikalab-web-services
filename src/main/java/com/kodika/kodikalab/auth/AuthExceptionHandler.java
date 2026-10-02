package com.kodika.kodikalab.auth;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.kodika.kodikalab.common.exception.BadRequestException;
import com.kodika.kodikalab.common.exception.ConflictException;
import com.kodika.kodikalab.users.Role;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Registration errors only; the rest of the scaffolding retains its behavior. */
@RestControllerAdvice(assignableTypes = AuthController.class)
public class AuthExceptionHandler {
    public record ErrorResponse(String message, Map<String, String> errors) {
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> invalidFields(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        String message = errors.values().stream().findFirst().orElse("Los datos de registro no son válidos");
        return ResponseEntity.badRequest().body(new ErrorResponse(message, errors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> invalidServiceInput(ConstraintViolationException exception) {
        // Never include rejected values: they may contain the password.
        return error(HttpStatus.BAD_REQUEST, "Los datos de registro no son válidos");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> invalidBody(HttpMessageNotReadableException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof InvalidFormatException format && format.getTargetType() == Role.class) {
                return error(HttpStatus.BAD_REQUEST, "El rol debe ser PRACTITIONER, COACH o ADMIN");
            }
        }
        return error(HttpStatus.BAD_REQUEST, "La solicitud debe contener un JSON válido con los campos esperados");
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> badRequest(BadRequestException exception) {
        return error(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> conflict(ConflictException exception) {
        return error(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> unexpectedPersistenceConflict(DataIntegrityViolationException exception) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo completar el registro");
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(message, Map.of()));
    }
}
