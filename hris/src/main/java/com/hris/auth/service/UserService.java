package com.hris.auth.service;

import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.service.AuditLogService;
import com.hris.auth.dto.UpdateLocaleDto;
import com.hris.auth.dto.UserResponseDto;
import com.hris.auth.entity.User;
import com.hris.auth.mapper.UserMapper;
import com.hris.auth.repository.UserRepository;
import com.hris.common.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public UserResponseDto getCurrentUser(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new EntityNotFoundException("User not found"));
        return userMapper.toDto(user);
    }

    @Transactional
    public UserResponseDto updateLocale(UUID userId, UpdateLocaleDto dto) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new EntityNotFoundException("User not found"));

        String previousLocale = user.getLocalePreference();
        user.setLocalePreference(dto.locale());
        User saved = userRepository.save(user);

        auditLogService.log(userId, AuditAction.UPDATE, "user", userId,
            previousLocale, dto.locale());

        return userMapper.toDto(saved);
    }
}
