package com.hris.recruitment.service;

import com.hris.analytics.service.AuditLogService;
import com.hris.recruitment.dto.RecruitmentDtos.CandidateCreateDto;
import com.hris.recruitment.entity.Candidate;
import com.hris.recruitment.enums.CandidateSource;
import com.hris.recruitment.repository.CandidateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CandidateServiceTest {

    @Mock private CandidateRepository candidateRepository;
    @Mock private AuditLogService auditLogService;

    @InjectMocks private CandidateService service;

    private final UUID userId = UUID.randomUUID();

    private CandidateCreateDto dto() {
        return new CandidateCreateDto("Jane", "Doe", "jane@doe.co", "123",
            CandidateSource.LINKEDIN, "Engineer", "ACME", "Tunis", null, null);
    }

    @Test
    void create_persistsCandidate() {
        when(candidateRepository.existsByEmailIgnoreCase("jane@doe.co")).thenReturn(false);
        when(candidateRepository.save(any(Candidate.class))).thenAnswer(i -> {
            Candidate c = i.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });

        var result = service.create(dto(), userId);

        assertThat(result.email()).isEqualTo("jane@doe.co");
        assertThat(result.source()).isEqualTo(CandidateSource.LINKEDIN);
    }

    @Test
    void create_rejectsDuplicateEmail() {
        when(candidateRepository.existsByEmailIgnoreCase("jane@doe.co")).thenReturn(true);

        assertThatThrownBy(() -> service.create(dto(), userId))
            .isInstanceOf(IllegalStateException.class);
    }
}
