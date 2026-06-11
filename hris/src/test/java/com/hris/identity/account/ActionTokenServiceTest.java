package com.hris.identity.account;

import com.hris.identity.account.entity.UserActionToken;
import com.hris.identity.account.repository.UserActionTokenRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ActionTokenService Unit Tests")
class ActionTokenServiceTest {

    @Mock
    private UserActionTokenRepository userActionTokenRepository;

    @InjectMocks
    private ActionTokenService actionTokenService;

    @Test
    @DisplayName("issue stores only the hash, never the raw token, and invalidates older tokens")
    void issueStoresHashOnly() {
        UUID userId = UUID.randomUUID();

        String rawToken = actionTokenService.issue(
            userId, UserActionToken.Purpose.ACTIVATION, Duration.ofHours(24));

        verify(userActionTokenRepository).invalidateOutstanding(
            eq(userId), eq(UserActionToken.Purpose.ACTIVATION), any(Instant.class));

        ArgumentCaptor<UserActionToken> captor = ArgumentCaptor.forClass(UserActionToken.class);
        verify(userActionTokenRepository).save(captor.capture());
        UserActionToken saved = captor.getValue();

        assertThat(rawToken).isNotBlank();
        assertThat(saved.getTokenHash())
            .hasSize(64)             // SHA-256 hex
            .isNotEqualTo(rawToken); // raw value is never persisted
        assertThat(saved.getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    @DisplayName("consume burns a valid token and returns the owner")
    void consumeBurnsValidToken() {
        UUID userId = UUID.randomUUID();
        ArgumentCaptor<UserActionToken> issued = ArgumentCaptor.forClass(UserActionToken.class);
        String rawToken = actionTokenService.issue(
            userId, UserActionToken.Purpose.PASSWORD_RESET, Duration.ofMinutes(15));
        verify(userActionTokenRepository).save(issued.capture());

        when(userActionTokenRepository.findByTokenHashAndPurpose(
            issued.getValue().getTokenHash(), UserActionToken.Purpose.PASSWORD_RESET))
            .thenReturn(Optional.of(issued.getValue()));

        UserActionToken resolved = actionTokenService.consume(rawToken, UserActionToken.Purpose.PASSWORD_RESET);

        assertThat(resolved.getUserId()).isEqualTo(userId);
        assertThat(issued.getValue().getUsedAt()).isNotNull();
    }

    @Test
    @DisplayName("consume rejects an already-used token")
    void consumeRejectsUsedToken() {
        UserActionToken used = UserActionToken.builder()
            .userId(UUID.randomUUID())
            .tokenHash("x".repeat(64))
            .purpose(UserActionToken.Purpose.ACTIVATION)
            .expiresAt(Instant.now().plusSeconds(600))
            .usedAt(Instant.now().minusSeconds(60))
            .build();
        when(userActionTokenRepository.findByTokenHashAndPurpose(any(), any()))
            .thenReturn(Optional.of(used));

        assertThatThrownBy(() -> actionTokenService.consume("raw", UserActionToken.Purpose.ACTIVATION))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("TOKEN_INVALID_OR_EXPIRED");
    }

    @Test
    @DisplayName("consume rejects an expired token")
    void consumeRejectsExpiredToken() {
        UserActionToken expired = UserActionToken.builder()
            .userId(UUID.randomUUID())
            .tokenHash("x".repeat(64))
            .purpose(UserActionToken.Purpose.ACTIVATION)
            .expiresAt(Instant.now().minusSeconds(1))
            .build();
        when(userActionTokenRepository.findByTokenHashAndPurpose(any(), any()))
            .thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> actionTokenService.consume("raw", UserActionToken.Purpose.ACTIVATION))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("TOKEN_INVALID_OR_EXPIRED");
    }

    @Test
    @DisplayName("consume rejects an unknown token")
    void consumeRejectsUnknownToken() {
        when(userActionTokenRepository.findByTokenHashAndPurpose(any(), any()))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> actionTokenService.consume("raw", UserActionToken.Purpose.PASSWORD_RESET))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("TOKEN_INVALID_OR_EXPIRED");
    }
}
