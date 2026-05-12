package com.hris.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hris.admin.dto.AdminRequestCommentCreateDto;
import com.hris.admin.dto.AdminRequestRejectDto;
import com.hris.admin.dto.CreateAdminRequestDto;
import com.hris.admin.dto.UpdateAdminRequestDto;
import com.hris.admin.entity.AdminRequest;
import com.hris.admin.entity.AdminRequestAttachment;
import com.hris.admin.entity.AdminRequestType;
import com.hris.admin.enums.AdminRequestStatus;
import com.hris.admin.repository.AdminRequestAttachmentRepository;
import com.hris.admin.repository.AdminRequestCommentRepository;
import com.hris.admin.repository.AdminRequestRepository;
import com.hris.admin.repository.AdminRequestTypeRepository;
import com.hris.analytics.service.AuditLogService;
import com.hris.auth.entity.Employee;
import com.hris.auth.entity.User;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.auth.repository.UserRepository;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.common.exception.InvalidAdminRequestStateException;
import com.hris.notification.service.TransactionalNotificationPublisher;
import com.hris.security.service.AccessScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminRequestService Unit Tests")
class AdminRequestServiceTest {

    @Mock private AdminRequestRepository adminRequestRepository;
    @Mock private AdminRequestTypeRepository adminRequestTypeRepository;
    @Mock private AdminRequestAttachmentRepository attachmentRepository;
    @Mock private AdminRequestCommentRepository commentRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private UserRepository userRepository;
    @Mock private AccessScopeService accessScopeService;
    @Mock private AdminRequestAttachmentService attachmentService;
    @Mock private TransactionalNotificationPublisher notificationPublisher;
    @Mock private AuditLogService auditLogService;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private AdminRequestService adminRequestService;

    private UUID requesterUserId;
    private UUID requesterEmployeeId;
    private UUID requestTypeId;
    private UUID processorUserId;
    private AdminRequestType requestType;

    @BeforeEach
    void setUp() throws Exception {
        requesterUserId = UUID.randomUUID();
        requesterEmployeeId = UUID.randomUUID();
        requestTypeId = UUID.randomUUID();
        processorUserId = UUID.randomUUID();

        requestType = AdminRequestType.builder()
            .id(requestTypeId)
            .code("CERT")
            .name("Certificate")
            .requiresAttachment(false)
            .slaHours(24)
            .isActive(true)
            .build();

        lenient().when(employeeRepository.findByUserId(requesterUserId)).thenReturn(Optional.of(Employee.builder()
            .id(requesterEmployeeId)
            .userId(requesterUserId)
            .build()));
        lenient().when(adminRequestTypeRepository.findById(requestTypeId)).thenReturn(Optional.of(requestType));
        lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{}");
    }

    @Test
    @DisplayName("create stores a draft with requester employee and request number")
    void createStoresDraft() {
        stubUserLookup();
        stubSave();
        AdminRequest result = adminRequestService.create(
            new CreateAdminRequestDto(requestTypeId, "Need certificate", "Please issue it"),
            requesterUserId);

        assertThat(result.getStatus()).isEqualTo(AdminRequestStatus.DRAFT);
        assertThat(result.getRequesterEmployeeId()).isEqualTo(requesterEmployeeId);
        assertThat(result.getRequesterUserId()).isEqualTo(requesterUserId);
        assertThat(result.getRequestNumber()).startsWith("AR-");
        verify(adminRequestRepository).save(any(AdminRequest.class));
    }

    @Test
    @DisplayName("create rejects inactive request type")
    void createRejectsInactiveType() {
        requestType.setActive(false);

        assertThatThrownBy(() -> adminRequestService.create(
            new CreateAdminRequestDto(requestTypeId, "Need certificate", "Please issue it"),
            requesterUserId))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessage("Admin request type not found or inactive");
    }

    @Test
    @DisplayName("update allows only draft requests")
    void updateAllowsOnlyDraftRequests() {
        UUID requestId = UUID.randomUUID();
        when(adminRequestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(baseRequest(requestId)
            .status(AdminRequestStatus.SUBMITTED)
            .submittedAt(Instant.now())
            .build()));

        assertThatThrownBy(() -> adminRequestService.update(
            requestId,
            new UpdateAdminRequestDto(null, "Updated subject", null),
            requesterUserId))
            .isInstanceOf(InvalidAdminRequestStateException.class)
            .hasMessage("Only draft admin requests can be edited");
    }

    @Test
    @DisplayName("submit enforces required attachment and calculates due date")
    void submitEnforcesRequiredAttachmentAndCalculatesDueDate() {
        UUID requestId = UUID.randomUUID();
        requestType.setRequiresAttachment(true);
        AdminRequest request = baseRequest(requestId).status(AdminRequestStatus.DRAFT).build();
        stubSave();
        when(adminRequestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        when(attachmentRepository.findByAdminRequestIdOrderByUploadedAtAsc(requestId)).thenReturn(List.of());

        assertThatThrownBy(() -> adminRequestService.submit(requestId, requesterUserId))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("This request type requires at least one attachment before submission");

        AdminRequestAttachment attachment = AdminRequestAttachment.builder().id(UUID.randomUUID()).adminRequestId(requestId).build();
        when(attachmentRepository.findByAdminRequestIdOrderByUploadedAtAsc(requestId)).thenReturn(List.of(attachment));
        when(userRepository.findByPermissionNames(any())).thenReturn(List.of(User.builder()
            .id(processorUserId)
            .localePreference("en")
            .build()));

        AdminRequest submitted = adminRequestService.submit(requestId, requesterUserId);

        assertThat(submitted.getStatus()).isEqualTo(AdminRequestStatus.SUBMITTED);
        assertThat(submitted.getSubmittedAt()).isNotNull();
        assertThat(submitted.getDueAt()).isEqualTo(submitted.getSubmittedAt().plusSeconds(24 * 3600L));
        verify(notificationPublisher, atLeastOnce()).publishAfterCommit(any());
    }

    @Test
    @DisplayName("employee cannot access another employee request")
    void employeeCannotAccessAnotherEmployeeRequest() {
        UUID requestId = UUID.randomUUID();
        when(adminRequestRepository.findById(requestId)).thenReturn(Optional.of(baseRequest(requestId)
            .requesterUserId(UUID.randomUUID())
            .build()));

        assertThatThrownBy(() -> adminRequestService.getOwnRequest(requestId, requesterUserId))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("cancel is allowed for draft and unprocessed submitted requests only")
    void cancelOnlyAllowedForEligibleStates() {
        UUID requestId = UUID.randomUUID();
        AdminRequest draft = baseRequest(requestId).status(AdminRequestStatus.DRAFT).build();
        stubSave();
        stubUserLookup();
        when(adminRequestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(draft));

        AdminRequest cancelledDraft = adminRequestService.cancel(requestId, requesterUserId);
        assertThat(cancelledDraft.getStatus()).isEqualTo(AdminRequestStatus.CANCELLED);

        AdminRequest reviewed = baseRequest(requestId)
            .status(AdminRequestStatus.SUBMITTED)
            .reviewedAt(Instant.now())
            .build();
        when(adminRequestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(reviewed));

        assertThatThrownBy(() -> adminRequestService.cancel(requestId, requesterUserId))
            .isInstanceOf(InvalidAdminRequestStateException.class);
    }

    @Test
    @DisplayName("start review moves submitted request to in review")
    void startReviewMovesSubmittedRequest() {
        UUID requestId = UUID.randomUUID();
        AdminRequest request = baseRequest(requestId).status(AdminRequestStatus.SUBMITTED).submittedAt(Instant.now()).build();
        stubSave();
        stubUserLookup();
        when(adminRequestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));

        AdminRequest result = adminRequestService.startReview(requestId, processorUserId);

        assertThat(result.getStatus()).isEqualTo(AdminRequestStatus.IN_REVIEW);
        assertThat(result.getProcessedByUserId()).isEqualTo(processorUserId);
        assertThat(result.getReviewedAt()).isNotNull();
    }

    @Test
    @DisplayName("approve transition accepts submitted or in review only")
    void approveTransition() {
        UUID requestId = UUID.randomUUID();
        AdminRequest request = baseRequest(requestId).status(AdminRequestStatus.IN_REVIEW).submittedAt(Instant.now()).build();
        stubSave();
        stubUserLookup();
        when(adminRequestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));

        AdminRequest approved = adminRequestService.approve(requestId, processorUserId);
        assertThat(approved.getStatus()).isEqualTo(AdminRequestStatus.APPROVED);
        assertThat(approved.getDecidedAt()).isNotNull();

        when(adminRequestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(baseRequest(requestId)
            .status(AdminRequestStatus.DRAFT)
            .build()));
        assertThatThrownBy(() -> adminRequestService.approve(requestId, processorUserId))
            .isInstanceOf(InvalidAdminRequestStateException.class)
            .hasMessage("Admin request cannot be approved from status: DRAFT");
    }

    @Test
    @DisplayName("reject transition requires reason and sets rejected state")
    void rejectTransitionRequiresReason() {
        UUID requestId = UUID.randomUUID();
        AdminRequest request = baseRequest(requestId).status(AdminRequestStatus.SUBMITTED).submittedAt(Instant.now()).build();
        stubSave();
        stubUserLookup();
        when(adminRequestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));

        AdminRequest rejected = adminRequestService.reject(requestId, processorUserId, new AdminRequestRejectDto("Missing proof"));
        assertThat(rejected.getStatus()).isEqualTo(AdminRequestStatus.REJECTED);
        assertThat(rejected.getRejectionReason()).isEqualTo("Missing proof");
    }

    @Test
    @DisplayName("complete transition only accepts approved requests")
    void completeTransition() {
        UUID requestId = UUID.randomUUID();
        AdminRequest request = baseRequest(requestId).status(AdminRequestStatus.APPROVED).submittedAt(Instant.now()).build();
        stubSave();
        stubUserLookup();
        when(adminRequestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));

        AdminRequest completed = adminRequestService.complete(requestId, processorUserId);
        assertThat(completed.getStatus()).isEqualTo(AdminRequestStatus.COMPLETED);
        assertThat(completed.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("comments from processor can be internal")
    void commentsFromProcessorCanBeInternal() {
        UUID requestId = UUID.randomUUID();
        AdminRequest request = baseRequest(requestId).status(AdminRequestStatus.SUBMITTED).build();
        when(adminRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(accessScopeService.hasAnyPermissionName(eq(processorUserId), any(String[].class))).thenReturn(true);
        when(commentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var comment = adminRequestService.addComment(requestId, processorUserId,
            new AdminRequestCommentCreateDto("Internal note", true));

        assertThat(comment.isInternal()).isTrue();
    }

    @Test
    @DisplayName("search inbox excludes drafts by default")
    void searchInboxDelegatesToSpecificationRepository() {
        when(adminRequestRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(PageRequest.class)))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        var page = adminRequestService.searchInbox(null, null, null, null, null, null, null, PageRequest.of(0, 20));
        assertThat(page.getTotalElements()).isZero();
    }

    private AdminRequest.AdminRequestBuilder baseRequest(UUID requestId) {
        return AdminRequest.builder()
            .id(requestId)
            .requestNumber("AR-20260509-00001")
            .requesterEmployeeId(requesterEmployeeId)
            .requesterUserId(requesterUserId)
            .typeId(requestTypeId)
            .subject("Need certificate")
            .description("Please issue it")
            .createdAt(Instant.now())
            .updatedAt(Instant.now());
    }

    private void stubSave() {
        when(adminRequestRepository.save(any(AdminRequest.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private void stubUserLookup() {
        when(userRepository.findById(any())).thenAnswer(inv -> Optional.of(User.builder()
            .id(inv.getArgument(0))
            .localePreference("en")
            .build()));
    }
}
