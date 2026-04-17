package com.hris.common;

import java.time.Instant;

public record ApiResponse<T>(
    T data,
    String message,
    Instant timestamp,
    boolean success
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(data, "OK", Instant.now(), true);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(null, message, Instant.now(), false);
    }
}
