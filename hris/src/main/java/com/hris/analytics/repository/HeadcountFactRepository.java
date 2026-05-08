package com.hris.analytics.repository;

import com.hris.analytics.entity.HeadcountFact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface HeadcountFactRepository extends JpaRepository<HeadcountFact, UUID> {
    List<HeadcountFact> findBySnapshotDate(LocalDate snapshotDate);
    void deleteBySnapshotDate(LocalDate snapshotDate);
}
