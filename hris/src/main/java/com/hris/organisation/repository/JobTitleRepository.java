package com.hris.organisation.repository;

import com.hris.organisation.entity.JobTitle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JobTitleRepository extends JpaRepository<JobTitle, UUID> {

    List<JobTitle> findAllByOrderByNameAsc();

    List<JobTitle> findByIsActiveTrueOrderByNameAsc();

    @Query("SELECT DISTINCT j.family FROM JobTitle j WHERE j.family IS NOT NULL AND j.family <> '' ORDER BY j.family")
    List<String> findDistinctFamilies();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);
}
