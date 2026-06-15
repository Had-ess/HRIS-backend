package com.hris.recruitment.repository;

import com.hris.recruitment.entity.NewHire;
import com.hris.recruitment.enums.NewHireStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NewHireRepository extends JpaRepository<NewHire, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT n FROM NewHire n WHERE n.id = :id")
    Optional<NewHire> findByIdForUpdate(@Param("id") UUID id);

    Optional<NewHire> findByApplicationId(UUID applicationId);

    List<NewHire> findByStatusOrderByCreatedAtDesc(NewHireStatus status);

    List<NewHire> findAllByOrderByCreatedAtDesc();
}
