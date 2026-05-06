package com.hris.auth.service;

import com.hris.auth.entity.User;
import com.hris.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserDisplayNameService {

    private final UserRepository userRepository;

    public String toDisplayName(User user) {
        if (user == null) {
            return null;
        }

        String fullName = (safe(user.getFirstName()) + " " + safe(user.getLastName())).trim();
        return fullName.isBlank() ? blankToNull(safe(user.getEmail())) : fullName;
    }

    @Transactional(readOnly = true)
    public String resolveDisplayName(UUID userId) {
        if (userId == null) {
            return null;
        }

        return userRepository.findById(userId)
            .map(this::toDisplayName)
            .orElse(null);
    }

    @Transactional(readOnly = true)
    public Map<UUID, String> resolveDisplayNames(Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }

        return userRepository.findAllById(userIds.stream()
                .filter(id -> id != null)
                .distinct()
                .toList())
            .stream()
            .collect(Collectors.toMap(User::getId, this::toDisplayName));
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
