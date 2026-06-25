package com.xiaoc.workbench.common.web;

import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> handleValidationError(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(this::fieldErrorMessage)
                .orElse("request validation failed");
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "validation_failed", "message", message));
    }

    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<Map<String, String>> handleNotFound(NoSuchElementException exception) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "not_found", "message", exception.getMessage()));
    }

    @ExceptionHandler(InvalidStateException.class)
    ResponseEntity<Map<String, String>> handleInvalidState(InvalidStateException exception) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of("error", "invalid_state", "message", exception.getMessage()));
    }

    @ExceptionHandler(RunAlreadyLockedException.class)
    ResponseEntity<Map<String, String>> handleRunAlreadyLocked(RunAlreadyLockedException exception) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of("error", "run_locked", "message", exception.getMessage()));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    ResponseEntity<Map<String, String>> handleRateLimitExceeded(RateLimitExceededException exception) {
        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of("error", "rate_limited", "message", exception.getMessage()));
    }

    @ExceptionHandler(InfrastructureUnavailableException.class)
    ResponseEntity<Map<String, String>> handleInfrastructureUnavailable(InfrastructureUnavailableException exception) {
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "infrastructure_unavailable", "message", exception.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> handleInvalidRequest(IllegalArgumentException exception) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "invalid_request", "message", exception.getMessage()));
    }

    private String fieldErrorMessage(FieldError error) {
        return error.getField() + " " + error.getDefaultMessage();
    }
}
