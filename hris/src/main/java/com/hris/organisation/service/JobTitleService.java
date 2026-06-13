package com.hris.organisation.service;

import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.service.AuditLogService;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.organisation.dto.JobTitleCreateDto;
import com.hris.organisation.dto.JobTitleDto;
import com.hris.organisation.entity.JobTitle;
import com.hris.organisation.repository.JobTitleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JobTitleService {

    private final JobTitleRepository jobTitleRepository;
    private final EmployeeRepository employeeRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<JobTitleDto> getAll() {
        return jobTitleRepository.findAllByOrderByNameAsc().stream()
            .map(this::toDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<JobTitleDto> getAllActive() {
        return jobTitleRepository.findByIsActiveTrueOrderByNameAsc().stream()
            .map(this::toDto)
            .toList();
    }

    @Transactional
    public JobTitleDto create(JobTitleCreateDto dto, UUID actorId) {
        String name = dto.name().trim();
        if (jobTitleRepository.existsByNameIgnoreCase(name)) {
            throw new IllegalArgumentException("A job title with this name already exists");
        }

        JobTitle saved = jobTitleRepository.save(JobTitle.builder()
            .name(name)
            .family(dto.family())
            .level(dto.level())
            .isActive(dto.isActive() != null ? dto.isActive() : true)
            .build());

        auditLogService.log(actorId, AuditAction.CREATE, "job_title", saved.getId(), null, saved);
        return toDto(saved);
    }

    @Transactional
    public JobTitleDto update(UUID id, JobTitleCreateDto dto, UUID actorId) {
        JobTitle jobTitle = jobTitleRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Job title not found"));

        JobTitle previous = JobTitle.builder()
            .id(jobTitle.getId())
            .name(jobTitle.getName())
            .family(jobTitle.getFamily())
            .level(jobTitle.getLevel())
            .isActive(jobTitle.isActive())
            .build();

        String name = dto.name().trim();
        if (jobTitleRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new IllegalArgumentException("A job title with this name already exists");
        }

        boolean renamed = !name.equals(jobTitle.getName());
        jobTitle.setName(name);
        jobTitle.setFamily(dto.family());
        jobTitle.setLevel(dto.level());
        if (dto.isActive() != null) {
            jobTitle.setActive(dto.isActive());
        }

        JobTitle saved = jobTitleRepository.save(jobTitle);
        if (renamed) {
            // employees.job_title is a denormalized copy of the catalog name
            employeeRepository.syncJobTitleName(saved.getId(), saved.getName());
        }
        auditLogService.log(actorId, AuditAction.UPDATE, "job_title", saved.getId(), previous, saved);
        return toDto(saved);
    }

    @Transactional
    public void delete(UUID id, UUID actorId) {
        JobTitle jobTitle = jobTitleRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Job title not found"));

        if (employeeRepository.existsByJobTitleId(id)) {
            throw new IllegalStateException(
                "Job title cannot be deleted because employees are assigned to it");
        }

        jobTitleRepository.delete(jobTitle);
        auditLogService.log(actorId, AuditAction.DELETE, "job_title", jobTitle.getId(), jobTitle, null);
    }

    private JobTitleDto toDto(JobTitle jobTitle) {
        return new JobTitleDto(
            jobTitle.getId(),
            jobTitle.getName(),
            jobTitle.getFamily(),
            jobTitle.getLevel(),
            jobTitle.isActive(),
            employeeRepository.countByJobTitleId(jobTitle.getId())
        );
    }
}
