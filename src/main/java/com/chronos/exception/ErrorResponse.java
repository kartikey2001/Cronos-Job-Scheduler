package com.chronos.exception;

public record ErrorResponse(
        String timestamp,
        int status,
        String error,
        String message,
        String path
) {}
