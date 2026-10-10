package com.kodika.kodikalab.competitions;

import com.kodika.kodikalab.common.exception.BadRequestException;
import com.kodika.kodikalab.common.exception.ConflictException;
import com.kodika.kodikalab.common.exception.ForbiddenException;
import com.kodika.kodikalab.common.exception.NotFoundException;
import com.kodika.kodikalab.common.exception.UnauthorizedException;
import com.kodika.kodikalab.competitions.competition.CompetitionValidationException;
import com.kodika.kodikalab.competitions.officialresult.OfficialResultValidationException;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = CompetitionController.class)
public class CompetitionExceptionHandler {
    public record ErrorResponse(String message, Map<String, String> errors) {
    }

    @ExceptionHandler(OfficialResultValidationException.class)
    public ResponseEntity<ErrorResponse> invalidResult(OfficialResultValidationException exception) {
        return ResponseEntity.badRequest().body(new ErrorResponse(exception.getMessage(), exception.getErrors()));
    }

    @ExceptionHandler(CompetitionValidationException.class)
    public ResponseEntity<ErrorResponse> invalidCompetition(CompetitionValidationException exception) {
        return ResponseEntity.badRequest().body(new ErrorResponse(exception.getMessage(), exception.getErrors()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> invalidBody(HttpMessageNotReadableException exception) {
        for (Throwable cause = exception.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof OfficialResultValidationException validation) {
                return invalidResult(validation);
            }
            if (cause instanceof CompetitionValidationException validation) {
                return invalidCompetition(validation);
            }
        }
        return error(HttpStatus.BAD_REQUEST, "Debe enviar un JSON válido con los campos esperados");
    }

    @ExceptionHandler({BadRequestException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> badRequest(Exception exception) {
        return error(HttpStatus.BAD_REQUEST, "El identificador debe ser un entero positivo");
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> unauthorized(UnauthorizedException exception) {
        return error(HttpStatus.UNAUTHORIZED, "Debe iniciar sesión para gestionar competencias y resultados oficiales");
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> forbidden(ForbiddenException exception) {
        return error(HttpStatus.FORBIDDEN, exception.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> notFound(NotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> conflict(ConflictException exception) {
        return error(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    public ResponseEntity<ErrorResponse> unavailable(Exception exception) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "La información necesaria no está disponible");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unexpected(Exception exception) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo completar la operación");
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(message, Map.of()));
    }
}
