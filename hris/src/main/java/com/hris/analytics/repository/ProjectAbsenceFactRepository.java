package com.hris.analytics.repository;

import com.hris.analytics.entity.ProjectAbsenceFact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface ProjectAbsenceFactRepository extends JpaRepository<ProjectAbsenceFact, UUID> {
    List<ProjectAbsenceFact> findBySnapshotDate(LocalDate snapshotDate);
    void deleteBySnapshotDate(LocalDate snapshotDate);
}
