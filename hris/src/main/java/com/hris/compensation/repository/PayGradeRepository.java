package com.hris.compensation.repository;

import com.hris.compensation.entity.PayGrade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PayGradeRepository extends JpaRepository<PayGrade, UUID> {

    List<PayGrade> findAllByOrderByCodeAsc();

    List<PayGrade> findByIsActiveTrueOrderByCodeAsc();

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);
}
