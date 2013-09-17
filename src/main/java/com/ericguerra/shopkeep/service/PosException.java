package com.ericguerra.shopkeep.service;

import org.springframework.http.HttpStatus;

import java.util.Map;

public class PosException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final Map<String, Object> details;

    public PosException(HttpStatus status, String code, String message, Map<String, Object> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details;
    }

    public static PosException notFound(String what, Object id) {
        return new PosException(HttpStatus.NOT_FOUND, "NOT_FOUND", what + " " + id + " not found", Map.of("id", String.valueOf(id)));
    }

    public static PosException conflict(String code, String message, Map<String, Object> details) {
        return new PosException(HttpStatus.CONFLICT, code, message, details);
    }

    public static PosException invalid(String message) {
        return new PosException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID", message, Map.of());
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
    public Map<String, Object> getDetails() { return details; }
}
