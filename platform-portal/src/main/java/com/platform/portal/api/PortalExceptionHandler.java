package com.platform.portal.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.Map;

@RestControllerAdvice
public class PortalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(PortalExceptionHandler.class);

    @ExceptionHandler(HttpClientErrorException.class)
    public ResponseEntity<Map<String, String>> handleHttpError(HttpClientErrorException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<Map<String, String>> handleServiceDown(ResourceAccessException ex) {
        log.warn("Backend service unavailable: {}", ex.getMessage());
        return ResponseEntity.status(503)
                .body(Map.of("error", "Backend service unavailable", "detail", ex.getMessage()));
    }
}
