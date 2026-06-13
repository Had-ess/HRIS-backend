package com.hris.performance.service;

import com.hris.analytics.service.AuditLogService;
import com.hris.auth.entity.Department;
import com.hris.auth.entity.Employee;
import com.hris.auth.enums.EmployeeStatus;
import com.hris.auth.repository.DepartmentRepository;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.performance.entity.PerformanceReview;
import com.hris.performance.entity.PerformanceReviewCycle;
import com.hris.performance.enums.CycleStatus;
import com.hris.performance.enums.ReviewStatus;
import com.hris.performance.repository.PerformanceRatingScaleRepository;
import com.hris.performance.repository.PerformanceReviewCycleDepartmentRepository;
import com.hris.performance.repository.PerformanceReviewCycleRepository;
import com.hris.performance.repository.PerformanceReviewRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ReviewCycleServiceTest {

    @Mock private PerformanceReviewCycleRepository cycleRepository;
    @Mock private PerformanceReviewCycleDepartmentRepository cycleDepartmentRepository;
    @Mock private PerformanceReviewRepository reviewRepository;
    @Mock private PerformanceRatingScaleRepository scaleRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private PerformanceReviewService reviewService;
    @Mock private PerformanceNotificationService notificationService;
    @Mock private CompetencyService competencyService;
    @Mock private AuditLogService auditLogService;

    @InjectMocks private ReviewCycleService service;

    private Employee emp(UUID id, UUID deptId, UUID supervisorId) {
        return Employee.builder().id(id).userId(UUID.randomUUID()).departmentId(deptId)
            .supervisorEmployeeId(supervisorId).jobTitle("Engineer").status(EmployeeStatus.ACTIVE).build();
    }

    @Test
    @DisplayName("reviewer resolves directly to the supervisor when one is set")
    void resolveReviewer_usesSupervisor() {
        UUID supervisor = UUID.randomUUID();
        Employee e = emp(UUID.randomUUID(), UUID.randomUUID(), supervisor);
        assertThat(service.resolveReviewer(e, Map.of())).isEqualTo(supervisor);
    }

    @Test
    @DisplayName("with no supervisor, reviewer escalates to the department head")
    void resolveReviewer_escalatesToDepartmentHead() {
        UUID deptId = UUID.randomUUID();
        UUID head = UUID.randomUUID();
        Employee e = emp(UUID.randomUUID(), deptId, null);
        Department dept = Department.builder().id(deptId).headEmployeeId(head).build();
        assertThat(service.resolveReviewer(e, Map.of(deptId, dept))).isEqualTo(head);
    }

    @Test
    @DisplayName("escalation walks up to a parent department head when the own department has none")
    void resolveReviewer_walksUpToParentHead() {
        UUID childId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        UUID parentHead = UUID.randomUUID();
        Employee e = emp(UUID.randomUUID(), childId, null);
        Department child = Department.builder().id(childId).parentDepartmentId(parentId).build();
        Department parent = Department.builder().id(parentId).headEmployeeId(parentHead).build();
        assertThat(service.resolveReviewer(e, Map.of(childId, child, parentId, parent))).isEqualTo(parentHead);
    }

    @Test
    @DisplayName("a department head reviewing themselves is skipped (no self-review), falling through to null")
    void resolveReviewer_skipsSelfHead() {
        UUID deptId = UUID.randomUUID();
        UUID selfId = UUID.randomUUID();
        Employee e = emp(selfId, deptId, null);
        Department dept = Department.builder().id(deptId).headEmployeeId(selfId).build();
        assertThat(service.resolveReviewer(e, Map.of(deptId, dept))).isNull();
    }

    @Test
    @DisplayName("generation is idempotent: employees that already have a review for the cycle are skipped")
    void generateReviews_isIdempotent() {
        UUID cycleId = UUID.randomUUID();
        PerformanceReviewCycle cycle = PerformanceReviewCycle.builder().id(cycleId)
            .status(CycleStatus.ACTIVE).build();
        UUID existing = UUID.randomUUID();
        UUID fresh = UUID.randomUUID();
        UUID supervisor = UUID.randomUUID();

        when(departmentRepository.findAll()).thenReturn(List.of());
        when(cycleDepartmentRepository.findByCycleId(cycleId)).thenReturn(List.of());
        when(employeeRepository.findByStatus(EmployeeStatus.ACTIVE)).thenReturn(List.of(
            emp(existing, UUID.randomUUID(), supervisor),
            emp(fresh, UUID.randomUUID(), supervisor)));
        when(reviewRepository.existsByCycleIdAndEmployeeId(cycleId, existing)).thenReturn(true);
        when(reviewRepository.existsByCycleIdAndEmployeeId(cycleId, fresh)).thenReturn(false);
        when(reviewRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int created = service.generateReviews(cycle);

        assertThat(created).isEqualTo(1);
        ArgumentCaptor<PerformanceReview> captor = ArgumentCaptor.forClass(PerformanceReview.class);
        verify(reviewRepository).save(captor.capture());
        assertThat(captor.getValue().getEmployeeId()).isEqualTo(fresh);
        assertThat(captor.getValue().getReviewerEmployeeId()).isEqualTo(supervisor);
        assertThat(captor.getValue().getStatus()).isEqualTo(ReviewStatus.SELF_ASSESSMENT);
        // each generated review gets its applicable competencies snapshotted
        verify(competencyService).snapshotForReview(any(), any());
    }

    @Test
    @DisplayName("only draft cycles can be activated")
    void activate_rejectsNonDraft() {
        UUID id = UUID.randomUUID();
        when(cycleRepository.findById(id)).thenReturn(java.util.Optional.of(
            PerformanceReviewCycle.builder().id(id).status(CycleStatus.ACTIVE).build()));
        assertThatThrownBy(() -> service.activate(id, UUID.randomUUID()))
            .isInstanceOf(IllegalStateException.class);
        verify(reviewRepository, never()).save(any());
    }

    @Test
    @DisplayName("closing a cycle emits a fact for each completed review")
    void close_emitsFactsForCompletedReviews() {
        UUID id = UUID.randomUUID();
        PerformanceReviewCycle cycle = PerformanceReviewCycle.builder().id(id).status(CycleStatus.IN_REVIEW).build();
        when(cycleRepository.findById(id)).thenReturn(java.util.Optional.of(cycle));
        PerformanceReview done = PerformanceReview.builder().id(UUID.randomUUID()).cycleId(id)
            .status(ReviewStatus.COMPLETED).build();
        PerformanceReview pending = PerformanceReview.builder().id(UUID.randomUUID()).cycleId(id)
            .status(ReviewStatus.MANAGER_REVIEW).build();
        when(reviewRepository.findByCycleId(id)).thenReturn(List.of(done, pending));
        lenient().when(cycleDepartmentRepository.findByCycleId(id)).thenReturn(List.of());

        service.close(id, UUID.randomUUID());

        assertThat(cycle.getStatus()).isEqualTo(CycleStatus.CLOSED);
        verify(reviewService).emitFact(eq(done), eq(cycle));
        verify(reviewService, never()).emitFact(eq(pending), any());
    }
}
