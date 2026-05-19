package com.hris.leave.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("LeaveBalance Entity Unit Tests")
class LeaveBalanceTest {

    @Test
    @DisplayName("getAvailableDays should return totalDays + carryOver - used - pending")
    void shouldCalculateAvailableDays() {
        LeaveBalance balance = LeaveBalance.builder()
            .totalDays(BigDecimal.valueOf(20))
            .usedDays(BigDecimal.valueOf(5))
            .pendingDays(BigDecimal.valueOf(3))
            .carryOverDays(BigDecimal.valueOf(2))
            .build();

        assertThat(balance.getAvailableDays()).isEqualByComparingTo(BigDecimal.valueOf(14)); // 20 + 2 - 5 - 3
    }

    @Test
    @DisplayName("getAvailableDays should return 0 when fully exhausted")
    void shouldReturnZero_WhenFullyExhausted() {
        LeaveBalance balance = LeaveBalance.builder()
            .totalDays(BigDecimal.valueOf(10))
            .usedDays(BigDecimal.valueOf(7))
            .pendingDays(BigDecimal.valueOf(3))
            .carryOverDays(BigDecimal.ZERO)
            .build();

        assertThat(balance.getAvailableDays()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("deductDays should increase pending days")
    void shouldIncreasePendingDays() {
        LeaveBalance balance = LeaveBalance.builder()
            .totalDays(BigDecimal.valueOf(20))
            .usedDays(BigDecimal.ZERO)
            .pendingDays(BigDecimal.ZERO)
            .build();

        balance.deductDays(BigDecimal.valueOf(5));

        assertThat(balance.getPendingDays()).isEqualByComparingTo(BigDecimal.valueOf(5));
        assertThat(balance.getUsedDays()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("restoreDays should decrease pending days")
    void shouldDecreasePendingDays() {
        LeaveBalance balance = LeaveBalance.builder()
            .totalDays(BigDecimal.valueOf(20))
            .usedDays(BigDecimal.ZERO)
            .pendingDays(BigDecimal.valueOf(5))
            .build();

        balance.restoreDays(BigDecimal.valueOf(5));

        assertThat(balance.getPendingDays()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("confirmUsage should move days from pending to used")
    void shouldMovePendingToUsed() {
        LeaveBalance balance = LeaveBalance.builder()
            .totalDays(BigDecimal.valueOf(20))
            .usedDays(BigDecimal.valueOf(3))
            .pendingDays(BigDecimal.valueOf(5))
            .build();

        balance.confirmUsage(BigDecimal.valueOf(5));

        assertThat(balance.getUsedDays()).isEqualByComparingTo(BigDecimal.valueOf(8));
        assertThat(balance.getPendingDays()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("equality should be based on ID")
    void shouldBeEqualById() {
        UUID id = UUID.randomUUID();

        LeaveBalance b1 = LeaveBalance.builder().id(id).totalDays(BigDecimal.valueOf(10)).build();
        LeaveBalance b2 = LeaveBalance.builder().id(id).totalDays(BigDecimal.valueOf(20)).build();

        assertThat(b1).isEqualTo(b2);
    }

    @Test
    @DisplayName("two balances with null IDs should not be equal")
    void shouldNotBeEqual_WhenBothNull() {
        LeaveBalance b1 = LeaveBalance.builder().totalDays(BigDecimal.valueOf(10)).build();
        LeaveBalance b2 = LeaveBalance.builder().totalDays(BigDecimal.valueOf(10)).build();

        assertThat(b1).isNotEqualTo(b2);
    }
}
