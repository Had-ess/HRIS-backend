package com.hris.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record AdminUserCreateDto(
    @NotBlank(message = "username must not be blank")
    @Size(max = 255, message = "username must be at most 255 characters")
    String username,

    @NotBlank(message = "email must not be blank")
    @Email(message = "email must be valid")
    @Size(max = 255, message = "email must be at most 255 characters")
    String email,

    @NotBlank(message = "firstName must not be blank")
    @Size(max = 255, message = "firstName must be at most 255 characters")
    String firstName,

    @NotBlank(message = "lastName must not be blank")
    @Size(max = 255, message = "lastName must be at most 255 characters")
    String lastName,

    @NotBlank(message = "password must not be blank")
    String password,

    Boolean temporaryPassword,

    @NotEmpty(message = "At least one role must be assigned")
    List<UUID> roleIds
) {
}
