package com.hris.document.repository;

import com.hris.document.entity.EmployeeDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface EmployeeDocumentRepository extends JpaRepository<EmployeeDocument, UUID> {

    List<EmployeeDocument> findByEmployeeIdOrderByCreatedAtDesc(UUID employeeId);

    /** Documents whose expiry falls on or before the horizon and were never alerted. */
    List<EmployeeDocument> findByExpiryDateLessThanEqualAndExpiryNotifiedAtIsNull(LocalDate horizon);
}
