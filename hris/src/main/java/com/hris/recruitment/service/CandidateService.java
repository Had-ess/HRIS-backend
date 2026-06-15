package com.hris.recruitment.service;

import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.service.AuditLogService;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.recruitment.dto.RecruitmentDtos.CandidateCreateDto;
import com.hris.recruitment.dto.RecruitmentDtos.CandidateDto;
import com.hris.recruitment.dto.RecruitmentDtos.CandidateUpdateDto;
import com.hris.recruitment.entity.Candidate;
import com.hris.recruitment.repository.CandidateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CandidateService {

    private final CandidateRepository candidateRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<CandidateDto> list(String search) {
        List<Candidate> candidates = (search == null || search.isBlank())
            ? candidateRepository.findAllByOrderByCreatedAtDesc()
            : candidateRepository
                .findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCaseOrEmailContainingIgnoreCaseOrderByCreatedAtDesc(
                    search, search, search);
        return candidates.stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public CandidateDto get(UUID id) {
        return toDto(load(id));
    }

    @Transactional
    public CandidateDto create(CandidateCreateDto dto, UUID userId) {
        String email = dto.email().trim();
        if (candidateRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalStateException("A candidate with this email already exists in the talent pool");
        }
        Candidate candidate = Candidate.builder()
            .firstName(dto.firstName().trim())
            .lastName(dto.lastName().trim())
            .email(email)
            .phone(dto.phone())
            .source(dto.source())
            .currentTitle(dto.currentTitle())
            .currentCompany(dto.currentCompany())
            .location(dto.location())
            .resumeUrl(dto.resumeUrl())
            .notes(dto.notes())
            .createdById(userId)
            .build();
        candidate = candidateRepository.save(candidate);
        auditLogService.log(userId, AuditAction.CREATE, "recruitment_candidate", candidate.getId(), null, candidate);
        return toDto(candidate);
    }

    @Transactional
    public CandidateDto update(UUID id, CandidateUpdateDto dto, UUID userId) {
        Candidate candidate = load(id);
        String email = dto.email().trim();
        Optional<Candidate> byEmail = candidateRepository.findByEmailIgnoreCase(email);
        if (byEmail.isPresent() && !byEmail.get().getId().equals(id)) {
            throw new IllegalStateException("Another candidate with this email already exists in the talent pool");
        }
        candidate.setFirstName(dto.firstName().trim());
        candidate.setLastName(dto.lastName().trim());
        candidate.setEmail(email);
        candidate.setPhone(dto.phone());
        candidate.setSource(dto.source());
        candidate.setCurrentTitle(dto.currentTitle());
        candidate.setCurrentCompany(dto.currentCompany());
        candidate.setLocation(dto.location());
        candidate.setResumeUrl(dto.resumeUrl());
        candidate.setNotes(dto.notes());
        candidate = candidateRepository.save(candidate);
        auditLogService.log(userId, AuditAction.UPDATE, "recruitment_candidate", candidate.getId(), null, candidate);
        return toDto(candidate);
    }

    Candidate load(UUID id) {
        return candidateRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Candidate not found"));
    }

    CandidateDto toDto(Candidate c) {
        return new CandidateDto(
            c.getId(), c.getFirstName(), c.getLastName(), c.getEmail(), c.getPhone(),
            c.getSource(), c.getCurrentTitle(), c.getCurrentCompany(), c.getLocation(),
            c.getResumeUrl(), c.getNotes(), c.getCreatedAt());
    }
}
