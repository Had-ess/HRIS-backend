package com.hris.identity.account;

import com.hris.identity.account.entity.UserCredential;
import com.hris.identity.account.repository.UserCredentialRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CredentialService Unit Tests")
class CredentialServiceTest {

    @Mock
    private UserCredentialRepository userCredentialRepository;

    private CredentialService credentialService;

    @BeforeEach
    void setUp() {
        // bcrypt-4 in tests: same code path, fast hashing
        credentialService = new CredentialService(
            userCredentialRepository,
            new DelegatingPasswordEncoder("bcrypt", Map.of("bcrypt", new BCryptPasswordEncoder(4))));
    }

    @Test
    @DisplayName("rejects passwords shorter than the minimum length")
    void rejectsShortPasswords() {
        assertThatThrownBy(() -> credentialService.validatePolicy("Sh0rt"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("PASSWORD_POLICY_TOO_SHORT");
    }

    @Test
    @DisplayName("rejects passwords missing a character class")
    void rejectsLowComplexityPasswords() {
        assertThatThrownBy(() -> credentialService.validatePolicy("alllowercase1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("PASSWORD_POLICY_COMPLEXITY");
        assertThatThrownBy(() -> credentialService.validatePolicy("ALLUPPERCASE1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("PASSWORD_POLICY_COMPLEXITY");
        assertThatThrownBy(() -> credentialService.validatePolicy("NoDigitsHere"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("PASSWORD_POLICY_COMPLEXITY");
    }

    @Test
    @DisplayName("accepts a compliant password and stores a bcrypt hash with reset counters")
    void storesHashAndResetsCounters() {
        UUID userId = UUID.randomUUID();
        UserCredential existing = UserCredential.builder()
            .userId(userId)
            .passwordHash("{bcrypt}old")
            .passwordUpdatedAt(Instant.now().minusSeconds(3600))
            .failedAttempts(4)
            .lockedUntil(Instant.now().plusSeconds(600))
            .build();
        when(userCredentialRepository.findById(userId)).thenReturn(Optional.of(existing));

        credentialService.setPassword(userId, "Compliant1Password");

        ArgumentCaptor<UserCredential> captor = ArgumentCaptor.forClass(UserCredential.class);
        verify(userCredentialRepository).save(captor.capture());
        UserCredential saved = captor.getValue();
        assertThat(saved.getPasswordHash()).startsWith("{bcrypt}");
        assertThat(saved.getFailedAttempts()).isZero();
        assertThat(saved.getLockedUntil()).isNull();
    }

    @Test
    @DisplayName("verify matches the stored hash")
    void verifyMatchesStoredHash() {
        UUID userId = UUID.randomUUID();
        when(userCredentialRepository.findById(userId)).thenReturn(Optional.empty());
        credentialService.setPassword(userId, "Compliant1Password");

        ArgumentCaptor<UserCredential> captor = ArgumentCaptor.forClass(UserCredential.class);
        verify(userCredentialRepository).save(captor.capture());
        when(userCredentialRepository.findById(userId)).thenReturn(Optional.of(captor.getValue()));

        assertThat(credentialService.verify(userId, "Compliant1Password")).isTrue();
        assertThat(credentialService.verify(userId, "WrongPassword1")).isFalse();
    }

    @Test
    @DisplayName("verify is false when no credentials exist")
    void verifyFalseWithoutCredentials() {
        UUID userId = UUID.randomUUID();
        when(userCredentialRepository.findById(any())).thenReturn(Optional.empty());

        assertThat(credentialService.verify(userId, "whatever")).isFalse();
    }
}
