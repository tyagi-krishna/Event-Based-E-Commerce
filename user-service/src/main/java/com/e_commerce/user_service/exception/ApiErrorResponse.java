package com.e_commerce.user_service.exception;

import java.time.LocalDateTime;
import java.util.List;

public record ApiErrorResponse(
        LocalDateTime timestamp,
        int status,
        String error,
        List<String> messages
) {
    public static ApiErrorResponse of(int status, String error, List<String> messages) {
        return new ApiErrorResponse(LocalDateTime.now(), status, error, messages);
    }
}
