package com.todo.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        List<Map<String, String>> details = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> Map.of(
                        "field", fe.getField(),
                        "message", fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid value"
                ))
                .toList();

        Map<String, Object> error = Map.of(
                "code", "VALIDATION_ERROR",
                "message", "Validation failed",
                "details", details
        );

        return ResponseEntity.unprocessableEntity().body(Map.of("error", error));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        String detail = "Malformed request body";
        Throwable cause = ex.getCause();
        if (cause instanceof com.fasterxml.jackson.databind.exc.InvalidFormatException ife) {
            if (ife.getTargetType() != null && ife.getTargetType().isEnum()) {
                Object[] constants = ife.getTargetType().getEnumConstants();
                StringBuilder valid = new StringBuilder();
                for (int i = 0; i < constants.length; i++) {
                    if (i > 0) valid.append(", ");
                    valid.append(constants[i].toString().toLowerCase());
                }
                detail = "Invalid value '" + ife.getValue()
                        + "'. Must be one of: " + valid;
            }
        }

        Map<String, Object> error = Map.of(
                "code", "VALIDATION_ERROR",
                "message", "Validation failed",
                "details", List.of(Map.of("message", detail))
        );

        return ResponseEntity.unprocessableEntity().body(Map.of("error", error));
    }

    @ExceptionHandler(AuthenticationRequiredException.class)
    public ResponseEntity<Map<String, Object>> handleAuthenticationRequired(AuthenticationRequiredException ex) {
        Map<String, Object> error = Map.of(
                "code", "UNAUTHORIZED",
                "message", "Authentication required"
        );

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", error));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NotFoundException ex) {
        Map<String, Object> error = Map.of(
                "code", "NOT_FOUND",
                "message", ex.getMessage()
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", error));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);

        Map<String, Object> error = Map.of(
                "code", "INTERNAL_ERROR",
                "message", "An unexpected error occurred"
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", error));
    }
}
