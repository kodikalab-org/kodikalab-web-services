
package com.kodika.kodikalab.teams;

import com.kodika.kodikalab.common.exception.BadRequestException;
import com.kodika.kodikalab.common.exception.ConflictException;
import com.kodika.kodikalab.common.exception.ForbiddenException;
import com.kodika.kodikalab.common.exception.NotFoundException;
import com.kodika.kodikalab.common.exception.UnauthorizedException;
import jakarta.validation.ConstraintViolationException;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = StudyGroupController.class)
public class TeamsExceptionHandler {

    public record ErrorResponse(
            String message,
            Map<String, String> errors
    ) {
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> invalidFields(
            MethodArgumentNotValidException exception) {

        Map<String, String> errors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        error -> error.getField(),
                        error -> error.getDefaultMessage() == null
                                ? "Valor inválido"
                                : error.getDefaultMessage(),
                        (first, second) -> first
                ));

        return ResponseEntity.badRequest()
                .body(new ErrorResponse("Datos inválidos", errors));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> badRequest(
            BadRequestException exception) {
        return error(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> invalidBody(
            HttpMessageNotReadableException exception) {
        return error(HttpStatus.BAD_REQUEST, "El cuerpo de la solicitud no es válido");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> missingParameter(
            MissingServletRequestParameterException exception) {
        return error(HttpStatus.BAD_REQUEST, "Falta un parámetro obligatorio");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> invalidParameter(
            MethodArgumentTypeMismatchException exception) {
        return error(HttpStatus.BAD_REQUEST, "El parámetro de la solicitud no es válido");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> invalidConstraints(
            ConstraintViolationException exception) {
        return error(HttpStatus.BAD_REQUEST, "Datos inválidos");
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> unauthorized(
            UnauthorizedException exception) {
        return error(HttpStatus.UNAUTHORIZED, exception.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> forbidden(
            ForbiddenException exception) {
        return error(HttpStatus.FORBIDDEN, exception.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> notFound(
            NotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> conflict(
            ConflictException exception) {
        return error(HttpStatus.CONFLICT, exception.getMessage());
    }

    private ResponseEntity<ErrorResponse> error(
            HttpStatus status,
            String message) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(message, Map.of()));
    }
}
