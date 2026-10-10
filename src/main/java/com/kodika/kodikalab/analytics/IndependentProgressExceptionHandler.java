package com.kodika.kodikalab.analytics;

import com.kodika.kodikalab.common.exception.BadRequestException;
import com.kodika.kodikalab.common.exception.ConflictException;
import com.kodika.kodikalab.common.exception.ForbiddenException;
import com.kodika.kodikalab.common.exception.NotFoundException;
import com.kodika.kodikalab.common.exception.UnauthorizedException;
import com.kodika.kodikalab.competitions.problemresolution.ResolutionValidationException;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = IndependentProgressController.class)
public class IndependentProgressExceptionHandler {
    public record ErrorResponse(String message, Map<String, String> errors) {
    }

    @ExceptionHandler(ResolutionValidationException.class)
    public ResponseEntity<ErrorResponse> invalidResolution(ResolutionValidationException exception) {
        return ResponseEntity.badRequest().body(new ErrorResponse(exception.getMessage(), exception.getErrors()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> invalidBody(HttpMessageNotReadableException exception) {
        for (Throwable cause = exception.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof ResolutionValidationException validation) {
                return invalidResolution(validation);
            }
        }
        return error(HttpStatus.BAD_REQUEST, "Debe enviar un JSON válido con los campos esperados");
    }

    @ExceptionHandler({BadRequestException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> invalidContext(Exception exception) {
        return error(HttpStatus.BAD_REQUEST, "Seleccione un contexto de equipo válido y un problema asignado válido");
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> unauthorized(UnauthorizedException exception) {
        return error(HttpStatus.UNAUTHORIZED, "Debe iniciar sesión para consultar o registrar su avance");
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

    @ExceptionHandler(RankingDataException.class)
    public ResponseEntity<ErrorResponse> inconsistentData(RankingDataException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(exception.getMessage(), exception.getErrors()));
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    public ResponseEntity<ErrorResponse> unavailable(Exception exception) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "No se pudo acceder a la información necesaria; vuelva a intentarlo");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unexpected(Exception exception) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo consultar o registrar el avance del equipo");
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(message, Map.of()));
    }
}
