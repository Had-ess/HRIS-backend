package com.hris.recruitment.repository;

import com.hris.recruitment.entity.Application;
import com.hris.recruitment.enums.ApplicationStage;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApplicationRepository extends JpaRepository<Application, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Application a WHERE a.id = :id")
    Optional<Application> findByIdForUpdate(@Param("id") UUID id);

    List<Application> findByRequisitionIdOrderByAppliedAtAsc(UUID requisitionId);

    List<Application> findByCandidateIdOrderByAppliedAtDesc(UUID candidateId);

    boolean existsByRequisitionIdAndCandidateId(UUID requisitionId, UUID candidateId);

    long countByRequisitionId(UUID requisitionId);

    long countByRequisitionIdAndStage(UUID requisitionId, ApplicationStage stage);
}
