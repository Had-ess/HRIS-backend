package com.hris.auth.controller;

import com.hris.auth.dto.UpdateCurrentUserDto;
import com.hris.auth.dto.UpdateLocaleDto;
import com.hris.auth.dto.UserResponseDto;
import com.hris.auth.service.UserService;
import com.hris.common.ApiResponse;
import com.hris.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponseDto>> getCurrentUser(Authentication auth) {
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.ok(ApiResponse.ok(userService.getCurrentUser(userId)));
    }

    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<UserResponseDto>> updateCurrentUser(
            Authentication auth,
            @Valid @RequestBody UpdateCurrentUserDto dto) {
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.ok(ApiResponse.ok(userService.updateCurrentUser(userId, dto)));
    }

    @PatchMapping("/me/locale")
    public ResponseEntity<ApiResponse<UserResponseDto>> updateLocale(
            Authentication auth,
            @Valid @RequestBody UpdateLocaleDto dto) {
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.ok(ApiResponse.ok(userService.updateLocale(userId, dto)));
    }
}
