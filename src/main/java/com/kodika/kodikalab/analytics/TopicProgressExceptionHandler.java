package com.kodika.kodikalab.analytics;

import com.kodika.kodikalab.common.exception.BadRequestException;
import com.kodika.kodikalab.common.exception.ForbiddenException;
import com.kodika.kodikalab.common.exception.NotFoundException;
import com.kodika.kodikalab.common.exception.UnauthorizedException;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = TopicProgressController.class)
public class TopicProgressExceptionHandler {
    public record ErrorResponse(String message, Map<String, String> errors) {
    }

    @ExceptionHandler({BadRequestException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> badRequest(Exception exception) {
        return error(HttpStatus.BAD_REQUEST, "El identificador del equipo debe ser un entero positivo");
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> unauthorized(UnauthorizedException exception) {
        return error(HttpStatus.UNAUTHORIZED, "Debe iniciar sesión para consultar su progreso por tema");
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> forbidden(ForbiddenException exception) {
        return error(HttpStatus.FORBIDDEN, exception.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> notFound(NotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(TopicProgressDataException.class)
    public ResponseEntity<ErrorResponse> invalidData(TopicProgressDataException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(exception.getMessage(), exception.getErrors()));
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    public ResponseEntity<ErrorResponse> unavailable(Exception exception) {
        return error(HttpStatus.SERVICE_UNAVAILABLE,
                "La información necesaria para el progreso por tema no está disponible; vuelva a intentarlo");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unexpected(Exception exception) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR,
                "No se pudo calcular el progreso por tema; vuelva a intentarlo");
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(message, Map.of()));
    }
}
