package com.mindscan.controller;

import com.mindscan.dto.Dtos;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Dtos.ApiResponse<Void>> handleValidation(
            MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .collect(Collectors.joining("; "));
        log.warn("Validation failed: {}", msg);
        return ResponseEntity.badRequest()
            .body(Dtos.ApiResponse.<Void>builder().success(false).message(msg).build());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Dtos.ApiResponse<Void>> handleIllegal(IllegalArgumentException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        return ResponseEntity.badRequest()
            .body(Dtos.ApiResponse.<Void>builder().success(false).message(ex.getMessage()).build());
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Dtos.ApiResponse<Void>> handleRuntime(RuntimeException ex) {
        log.error("Runtime error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Dtos.ApiResponse.<Void>builder().success(false)
                .message("Server error: " + ex.getMessage()).build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Dtos.ApiResponse<Void>> handleGeneral(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Dtos.ApiResponse.<Void>builder().success(false)
                .message("An unexpected error occurred").build());
    }
}
