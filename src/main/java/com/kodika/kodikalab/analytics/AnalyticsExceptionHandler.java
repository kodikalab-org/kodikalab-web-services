package com.kodika.kodikalab.analytics;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.kodika.kodikalab.analytics.dto.LastValidRanking;
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

@RestControllerAdvice(assignableTypes = AnalyticsController.class)
public class AnalyticsExceptionHandler {
    public record ErrorResponse(String message, Map<String, String> errors,
                                @JsonInclude(JsonInclude.Include.NON_NULL) LastValidRanking lastValidRanking) {
        public ErrorResponse(String message, Map<String, String> errors) {
            this(message, errors, null);
        }
    }

    @ExceptionHandler({BadRequestException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> badRequest(Exception exception) {
        return error(HttpStatus.BAD_REQUEST, "El identificador del equipo debe ser un entero positivo");
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> unauthorized(UnauthorizedException exception) {
        return error(HttpStatus.UNAUTHORIZED, "Debe iniciar sesión para consultar el ranking");
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> forbidden(ForbiddenException exception) {
        return error(HttpStatus.FORBIDDEN, exception.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> notFound(NotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(RankingDataException.class)
    public ResponseEntity<ErrorResponse> inconsistentData(RankingDataException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(exception.getMessage(), exception.getErrors()));
    }

    @ExceptionHandler(RankingRecoveryException.class)
    public ResponseEntity<ErrorResponse> recoveredRanking(RankingRecoveryException exception) {
        if (exception.getCause() instanceof RankingDataException cause) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse(cause.getMessage(), cause.getErrors(), exception.getLastValidRanking()));
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse("La información necesaria para el ranking no está disponible", Map.of(),
                        exception.getLastValidRanking()));
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    public ResponseEntity<ErrorResponse> persistenceUnavailable(Exception exception) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "La información necesaria para el ranking no está disponible");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unexpectedError(Exception exception) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo calcular el ranking");
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(message, Map.of()));
    }
}
