package com.hris.settings.calendar.repository;

import com.hris.settings.calendar.entity.HrHoliday;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HrHolidayRepository extends JpaRepository<HrHoliday, UUID> {

    List<HrHoliday> findByCalendarIdOrderByDateAscNameAsc(UUID calendarId);

    List<HrHoliday> findByCalendarIdAndDateBetween(UUID calendarId, LocalDate start, LocalDate end);

    Optional<HrHoliday> findByIdAndCalendarId(UUID holidayId, UUID calendarId);
}
