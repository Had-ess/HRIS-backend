package com.hris.organisation.service;

import com.hris.organisation.entity.PublicHoliday;
import com.hris.organisation.entity.WorkSchedule;
import com.hris.organisation.repository.PublicHolidayRepository;
import com.hris.organisation.repository.WorkScheduleRepository;
import com.hris.common.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkScheduleService {

    private final WorkScheduleRepository workScheduleRepository;
    private final PublicHolidayRepository publicHolidayRepository;

    @Transactional(readOnly = true)
    public int computeWorkingDays(LocalDate start, LocalDate end, UUID workScheduleId) {
        WorkSchedule schedule = workScheduleRepository.findById(workScheduleId)
            .orElseThrow(() -> new EntityNotFoundException("WorkSchedule not found"));

        Set<DayOfWeek> workingDaysSet = schedule.getWorkingDaysSet();

        List<PublicHoliday> holidays = publicHolidayRepository.findByDateBetween(start, end);
        Set<LocalDate> holidayDates = holidays.stream()
            .map(PublicHoliday::getDate)
            .collect(Collectors.toSet());

        int count = 0;
        LocalDate current = start;

        while (!current.isAfter(end)) {
            if (workingDaysSet.contains(current.getDayOfWeek())
                && !holidayDates.contains(current)) {
                count++;
            }
            current = current.plusDays(1);
        }

        return count;
    }
}
