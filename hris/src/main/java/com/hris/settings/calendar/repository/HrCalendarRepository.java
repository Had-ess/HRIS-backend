package com.hris.settings.calendar.repository;

import com.hris.settings.calendar.entity.HrCalendar;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HrCalendarRepository extends JpaRepository<HrCalendar, UUID> {

    List<HrCalendar> findAllByOrderByNameAsc();

    Optional<HrCalendar> findByCode(String code);
}
