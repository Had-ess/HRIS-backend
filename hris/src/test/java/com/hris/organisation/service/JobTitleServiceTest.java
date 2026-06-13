package com.hris.organisation.service;

import com.hris.analytics.service.AuditLogService;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.organisation.dto.JobTitleCreateDto;
import com.hris.organisation.dto.JobTitleDto;
import com.hris.organisation.entity.JobTitle;
import com.hris.organisation.repository.JobTitleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobTitleServiceTest {

    @Mock private JobTitleRepository jobTitleRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private AuditLogService auditLogService;

    @InjectMocks
    private JobTitleService service;

    private final UUID jobTitleId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();

    private JobTitle jobTitle;

    @BeforeEach
    void setUp() {
        jobTitle = JobTitle.builder()
            .id(jobTitleId).name("Software Engineer").family("Engineering").level(3).isActive(true)
            .build();
        lenient().when(jobTitleRepository.findById(jobTitleId)).thenReturn(Optional.of(jobTitle));
        lenient().when(jobTitleRepository.save(any(JobTitle.class)))
            .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("renaming a title re-syncs the denormalized employees.job_title copy")
    void renameSyncsEmployeesTextColumn() {
        when(jobTitleRepository.existsByNameIgnoreCaseAndIdNot("Senior Software Engineer", jobTitleId))
            .thenReturn(false);

        JobTitleDto result = service.update(jobTitleId,
            new JobTitleCreateDto("Senior Software Engineer", "Engineering", 4, true), actorId);

        assertThat(result.name()).isEqualTo("Senior Software Engineer");
        verify(employeeRepository).syncJobTitleName(jobTitleId, "Senior Software Engineer");
    }

    @Test
    @DisplayName("updating without a rename does not touch employees")
    void updateWithoutRenameSkipsSync() {
        when(jobTitleRepository.existsByNameIgnoreCaseAndIdNot("Software Engineer", jobTitleId))
            .thenReturn(false);

        service.update(jobTitleId,
            new JobTitleCreateDto("Software Engineer", "Engineering", 4, true), actorId);

        verify(employeeRepository, never()).syncJobTitleName(any(), any());
    }

    @Test
    @DisplayName("duplicate names are rejected on create")
    void createRejectsDuplicateName() {
        when(jobTitleRepository.existsByNameIgnoreCase("Software Engineer")).thenReturn(true);

        assertThatThrownBy(() -> service.create(
            new JobTitleCreateDto("Software Engineer", null, null, null), actorId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");
        verify(jobTitleRepository, never()).save(any());
    }

    @Test
    @DisplayName("deletion is blocked while employees hold the title")
    void deleteBlockedWhileInUse() {
        when(employeeRepository.existsByJobTitleId(jobTitleId)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(jobTitleId, actorId))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("assigned");
        verify(jobTitleRepository, never()).delete(any());
    }

    @Test
    @DisplayName("unused titles can be deleted")
    void deleteUnusedTitle() {
        when(employeeRepository.existsByJobTitleId(jobTitleId)).thenReturn(false);

        service.delete(jobTitleId, actorId);

        verify(jobTitleRepository).delete(jobTitle);
    }
}
