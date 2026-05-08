package com.hris.settings.calendar.service;

import com.hris.analytics.service.AuditLogService;
import com.hris.settings.calendar.dto.HrCalendarMutationDto;
import com.hris.settings.calendar.dto.HrHolidayMutationDto;
import com.hris.settings.calendar.entity.HrCalendar;
import com.hris.settings.calendar.entity.HrHoliday;
import com.hris.settings.calendar.repository.HrCalendarRepository;
import com.hris.settings.calendar.repository.HrHolidayRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("HrCalendarService Unit Tests")
class HrCalendarServiceTest {

    @Mock private HrCalendarRepository calendarRepository;
    @Mock private HrHolidayRepository holidayRepository;
    @Mock private AuditLogService auditLogService;

    @InjectMocks
    private HrCalendarService service;

    @Test
    @DisplayName("create normalizes calendar fields")
    void createNormalizesCalendarFields() {
        UUID actorId = UUID.randomUUID();
        when(calendarRepository.save(any(HrCalendar.class))).thenAnswer(invocation -> {
            HrCalendar calendar = invocation.getArgument(0);
            calendar.setId(UUID.randomUUID());
            return calendar;
        });

        var result = service.create(
            new HrCalendarMutationDto(" tn_main ", " Tunisia Main ", " tn ", " Africa/Tunis ", 8, " manual ", true),
            actorId
        );

        assertThat(result.code()).isEqualTo("TN_MAIN");
        assertThat(result.name()).isEqualTo("Tunisia Main");
        assertThat(result.country()).isEqualTo("tn");
        assertThat(result.timezone()).isEqualTo("Africa/Tunis");
        assertThat(result.source()).isEqualTo("MANUAL");
        verify(auditLogService).log(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("create holiday stores recurring flag")
    void createHolidayStoresRecurringFlag() {
        UUID calendarId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        when(calendarRepository.existsById(calendarId)).thenReturn(true);
        when(holidayRepository.save(any(HrHoliday.class))).thenAnswer(invocation -> {
            HrHoliday holiday = invocation.getArgument(0);
            holiday.setId(UUID.randomUUID());
            return holiday;
        });

        var result = service.createHoliday(
            calendarId,
            new HrHolidayMutationDto(LocalDate.of(2026, 5, 1), "Labour Day", true),
            actorId
        );

        assertThat(result.calendarId()).isEqualTo(calendarId);
        assertThat(result.recurring()).isTrue();
    }

    @Test
    @DisplayName("resolveHolidayDates returns matching dates only")
    void resolveHolidayDatesReturnsMatchingDatesOnly() {
        UUID calendarId = UUID.randomUUID();
        when(holidayRepository.findByCalendarIdAndDateBetween(
            calendarId,
            LocalDate.of(2026, 5, 1),
            LocalDate.of(2026, 5, 31)
        )).thenReturn(List.of(
            HrHoliday.builder().id(UUID.randomUUID()).calendarId(calendarId).date(LocalDate.of(2026, 5, 1)).name("Labour Day").build(),
            HrHoliday.builder().id(UUID.randomUUID()).calendarId(calendarId).date(LocalDate.of(2026, 5, 25)).name("Holiday").build()
        ));

        Set<LocalDate> result = service.resolveHolidayDates(
            calendarId,
            LocalDate.of(2026, 5, 1),
            LocalDate.of(2026, 5, 31)
        );

        assertThat(result).containsExactlyInAnyOrder(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 25));
    }
}
