package com.e_commerce.user_service.dto;

import com.e_commerce.user_service.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @Email String email,
        @Size(min = 8, max = 100) String password,
        Role role
) {
}
