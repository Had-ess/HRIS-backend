package com.hris.timesheet.repository;

import com.hris.timesheet.entity.TimesheetEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TimesheetEntryRepository extends JpaRepository<TimesheetEntry, UUID> {

    List<TimesheetEntry> findByTimesheetIdOrderByWorkDateAscIdAsc(UUID timesheetId);

    void deleteByTimesheetId(UUID timesheetId);
}
