package com.hris.recruitment.repository;

import com.hris.recruitment.entity.ApplicationStageHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ApplicationStageHistoryRepository extends JpaRepository<ApplicationStageHistory, UUID> {

    List<ApplicationStageHistory> findByApplicationIdOrderByChangedAtAsc(UUID applicationId);
}
