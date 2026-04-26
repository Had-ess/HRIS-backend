package com.hris.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateCurrentUserDto(
    @NotBlank(message = "email must not be blank")
    @Email(message = "email must be valid")
    @Size(max = 255, message = "email must be at most 255 characters")
    String email,

    @NotBlank(message = "firstName must not be blank")
    @Size(max = 255, message = "firstName must be at most 255 characters")
    String firstName,

    @NotBlank(message = "lastName must not be blank")
    @Size(max = 255, message = "lastName must be at most 255 characters")
    String lastName
) {
}
