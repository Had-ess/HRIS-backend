package com.hris.leave.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("LeaveBalance Entity Unit Tests")
class LeaveBalanceTest {

    @Test
    @DisplayName("getAvailableDays should return totalDays + carryOver - used - pending")
    void shouldCalculateAvailableDays() {
        LeaveBalance balance = LeaveBalance.builder()
            .totalDays(20)
            .usedDays(5)
            .pendingDays(3)
            .carryOverDays(2)
            .build();

        assertThat(balance.getAvailableDays()).isEqualTo(14); // 20 + 2 - 5 - 3
    }

    @Test
    @DisplayName("getAvailableDays should return 0 when fully exhausted")
    void shouldReturnZero_WhenFullyExhausted() {
        LeaveBalance balance = LeaveBalance.builder()
            .totalDays(10)
            .usedDays(7)
            .pendingDays(3)
            .carryOverDays(0)
            .build();

        assertThat(balance.getAvailableDays()).isEqualTo(0);
    }

    @Test
    @DisplayName("deductDays should increase pending days")
    void shouldIncreasePendingDays() {
        LeaveBalance balance = LeaveBalance.builder()
            .totalDays(20)
            .usedDays(0)
            .pendingDays(0)
            .build();

        balance.deductDays(5);

        assertThat(balance.getPendingDays()).isEqualTo(5);
        assertThat(balance.getUsedDays()).isEqualTo(0);
    }

    @Test
    @DisplayName("restoreDays should decrease pending days")
    void shouldDecreasePendingDays() {
        LeaveBalance balance = LeaveBalance.builder()
            .totalDays(20)
            .usedDays(0)
            .pendingDays(5)
            .build();

        balance.restoreDays(5);

        assertThat(balance.getPendingDays()).isEqualTo(0);
    }

    @Test
    @DisplayName("confirmUsage should move days from pending to used")
    void shouldMovePendingToUsed() {
        LeaveBalance balance = LeaveBalance.builder()
            .totalDays(20)
            .usedDays(3)
            .pendingDays(5)
            .build();

        balance.confirmUsage(5);

        assertThat(balance.getUsedDays()).isEqualTo(8);
        assertThat(balance.getPendingDays()).isEqualTo(0);
    }

    @Test
    @DisplayName("equality should be based on ID")
    void shouldBeEqualById() {
        UUID id = UUID.randomUUID();

        LeaveBalance b1 = LeaveBalance.builder().id(id).totalDays(10).build();
        LeaveBalance b2 = LeaveBalance.builder().id(id).totalDays(20).build();

        assertThat(b1).isEqualTo(b2);
    }

    @Test
    @DisplayName("two balances with null IDs should not be equal")
    void shouldNotBeEqual_WhenBothNull() {
        LeaveBalance b1 = LeaveBalance.builder().totalDays(10).build();
        LeaveBalance b2 = LeaveBalance.builder().totalDays(10).build();

        assertThat(b1).isNotEqualTo(b2);
    }
}
