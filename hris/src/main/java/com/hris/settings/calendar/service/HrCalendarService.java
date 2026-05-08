package com.hris.settings.calendar.service;

import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.service.AuditLogService;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.settings.calendar.dto.HrCalendarDto;
import com.hris.settings.calendar.dto.HrCalendarMutationDto;
import com.hris.settings.calendar.dto.HrHolidayDto;
import com.hris.settings.calendar.dto.HrHolidayMutationDto;
import com.hris.settings.calendar.entity.HrCalendar;
import com.hris.settings.calendar.entity.HrHoliday;
import com.hris.settings.calendar.repository.HrCalendarRepository;
import com.hris.settings.calendar.repository.HrHolidayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HrCalendarService {

    private final HrCalendarRepository calendarRepository;
    private final HrHolidayRepository holidayRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<HrCalendarDto> getAll() {
        return calendarRepository.findAllByOrderByNameAsc().stream()
            .map(this::toDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public HrCalendarDto getById(UUID id) {
        return toDto(findCalendar(id));
    }

    @Transactional
    public HrCalendarDto create(HrCalendarMutationDto dto, UUID actorId) {
        HrCalendar calendar = HrCalendar.builder()
            .code(dto.code().trim().toUpperCase(Locale.ROOT))
            .name(dto.name().trim())
            .country(blankToNull(dto.country()))
            .timezone(dto.timezone().trim())
            .hoursPerDay(dto.hoursPerDay())
            .source(dto.source().trim().toUpperCase(Locale.ROOT))
            .active(dto.active())
            .build();
        HrCalendar saved = calendarRepository.save(calendar);
        auditLogService.log(actorId, AuditAction.CREATE, "hr_calendar", saved.getId(), null, saved);
        return toDto(saved);
    }

    @Transactional
    public HrCalendarDto update(UUID id, HrCalendarMutationDto dto, UUID actorId) {
        HrCalendar calendar = findCalendar(id);
        HrCalendar previous = copyCalendar(calendar);
        calendar.setCode(dto.code().trim().toUpperCase(Locale.ROOT));
        calendar.setName(dto.name().trim());
        calendar.setCountry(blankToNull(dto.country()));
        calendar.setTimezone(dto.timezone().trim());
        calendar.setHoursPerDay(dto.hoursPerDay());
        calendar.setSource(dto.source().trim().toUpperCase(Locale.ROOT));
        calendar.setActive(dto.active());
        HrCalendar saved = calendarRepository.save(calendar);
        auditLogService.log(actorId, AuditAction.UPDATE, "hr_calendar", saved.getId(), previous, saved);
        return toDto(saved);
    }

    @Transactional
    public void deactivate(UUID id, UUID actorId) {
        HrCalendar calendar = findCalendar(id);
        HrCalendar previous = copyCalendar(calendar);
        calendar.setActive(false);
        calendarRepository.save(calendar);
        auditLogService.log(actorId, AuditAction.UPDATE, "hr_calendar", calendar.getId(), previous, calendar);
    }

    @Transactional(readOnly = true)
    public List<HrHolidayDto> getHolidays(UUID calendarId) {
        assertCalendarExists(calendarId);
        return holidayRepository.findByCalendarIdOrderByDateAscNameAsc(calendarId).stream()
            .map(this::toHolidayDto)
            .toList();
    }

    @Transactional
    public HrHolidayDto createHoliday(UUID calendarId, HrHolidayMutationDto dto, UUID actorId) {
        assertCalendarExists(calendarId);
        HrHoliday holiday = HrHoliday.builder()
            .calendarId(calendarId)
            .date(dto.date())
            .name(dto.name().trim())
            .recurring(Boolean.TRUE.equals(dto.recurring()))
            .build();
        HrHoliday saved = holidayRepository.save(holiday);
        auditLogService.log(actorId, AuditAction.CREATE, "hr_holiday", saved.getId(), null, saved);
        return toHolidayDto(saved);
    }

    @Transactional
    public HrHolidayDto updateHoliday(UUID calendarId, UUID holidayId, HrHolidayMutationDto dto, UUID actorId) {
        HrHoliday holiday = holidayRepository.findByIdAndCalendarId(holidayId, calendarId)
            .orElseThrow(() -> new EntityNotFoundException("Holiday not found"));
        HrHoliday previous = copyHoliday(holiday);
        holiday.setDate(dto.date());
        holiday.setName(dto.name().trim());
        holiday.setRecurring(Boolean.TRUE.equals(dto.recurring()));
        HrHoliday saved = holidayRepository.save(holiday);
        auditLogService.log(actorId, AuditAction.UPDATE, "hr_holiday", saved.getId(), previous, saved);
        return toHolidayDto(saved);
    }

    @Transactional
    public void deleteHoliday(UUID calendarId, UUID holidayId, UUID actorId) {
        HrHoliday holiday = holidayRepository.findByIdAndCalendarId(holidayId, calendarId)
            .orElseThrow(() -> new EntityNotFoundException("Holiday not found"));
        holidayRepository.delete(holiday);
        auditLogService.log(actorId, AuditAction.DELETE, "hr_holiday", holiday.getId(), holiday, null);
    }

    @Transactional(readOnly = true)
    public Set<LocalDate> resolveHolidayDates(UUID calendarId, LocalDate start, LocalDate end) {
        if (calendarId == null) {
            return Set.of();
        }
        return holidayRepository.findByCalendarIdAndDateBetween(calendarId, start, end).stream()
            .map(HrHoliday::getDate)
            .collect(Collectors.toSet());
    }

    private HrCalendar findCalendar(UUID id) {
        return calendarRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("HR calendar not found"));
    }

    private void assertCalendarExists(UUID id) {
        if (!calendarRepository.existsById(id)) {
            throw new EntityNotFoundException("HR calendar not found");
        }
    }

    private HrCalendarDto toDto(HrCalendar calendar) {
        return new HrCalendarDto(
            calendar.getId(),
            calendar.getCode(),
            calendar.getName(),
            calendar.getCountry(),
            calendar.getTimezone(),
            calendar.getHoursPerDay(),
            calendar.getSource(),
            calendar.isActive(),
            calendar.getCreatedAt(),
            calendar.getUpdatedAt()
        );
    }

    private HrHolidayDto toHolidayDto(HrHoliday holiday) {
        return new HrHolidayDto(
            holiday.getId(),
            holiday.getCalendarId(),
            holiday.getDate(),
            holiday.getName(),
            holiday.isRecurring(),
            holiday.getCreatedAt(),
            holiday.getUpdatedAt()
        );
    }

    private HrCalendar copyCalendar(HrCalendar calendar) {
        return HrCalendar.builder()
            .id(calendar.getId())
            .code(calendar.getCode())
            .name(calendar.getName())
            .country(calendar.getCountry())
            .timezone(calendar.getTimezone())
            .hoursPerDay(calendar.getHoursPerDay())
            .source(calendar.getSource())
            .active(calendar.isActive())
            .createdAt(calendar.getCreatedAt())
            .updatedAt(calendar.getUpdatedAt())
            .build();
    }

    private HrHoliday copyHoliday(HrHoliday holiday) {
        return HrHoliday.builder()
            .id(holiday.getId())
            .calendarId(holiday.getCalendarId())
            .date(holiday.getDate())
            .name(holiday.getName())
            .recurring(holiday.isRecurring())
            .createdAt(holiday.getCreatedAt())
            .updatedAt(holiday.getUpdatedAt())
            .build();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
