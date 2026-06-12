package com.e_commerce.user_service.dto;

public record AuthResponse(
        boolean authenticated,
        UserResponse user,
        String token
) {
}
