package com.hris.recruitment.controller;

import com.hris.common.ApiResponse;
import com.hris.recruitment.dto.RecruitmentDtos.ApplicationCreateDto;
import com.hris.recruitment.dto.RecruitmentDtos.ApplicationDetailDto;
import com.hris.recruitment.dto.RecruitmentDtos.ApplicationDto;
import com.hris.recruitment.dto.RecruitmentDtos.ApplicationMoveDto;
import com.hris.recruitment.dto.RecruitmentDtos.ApplicationRatingDto;
import com.hris.recruitment.dto.RecruitmentDtos.CandidateCreateDto;
import com.hris.recruitment.dto.RecruitmentDtos.CandidateDto;
import com.hris.recruitment.dto.RecruitmentDtos.CandidateUpdateDto;
import com.hris.recruitment.dto.RecruitmentDtos.NewHireDto;
import com.hris.recruitment.dto.RecruitmentDtos.RequisitionCreateDto;
import com.hris.recruitment.dto.RecruitmentDtos.RequisitionDto;
import com.hris.recruitment.dto.RecruitmentDtos.RequisitionUpdateDto;
import com.hris.recruitment.enums.NewHireStatus;
import com.hris.recruitment.enums.RequisitionStatus;
import com.hris.recruitment.service.ApplicationService;
import com.hris.recruitment.service.CandidateService;
import com.hris.recruitment.service.NewHireHandoffService;
import com.hris.recruitment.service.RequisitionService;
import com.hris.security.PermissionAuthorizationService;
import com.hris.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/recruitment")
@RequiredArgsConstructor
public class RecruitmentController {

    private static final String RESOURCE = "RECRUITMENT";
    private static final String MANAGE = "RECRUITMENT_MANAGE";
    private static final String REQUEST = "RECRUITMENT_REQUEST";

    private final RequisitionService requisitionService;
    private final CandidateService candidateService;
    private final ApplicationService applicationService;
    private final NewHireHandoffService newHireHandoffService;
    private final PermissionAuthorizationService permissionAuthorizationService;

    // --- Requisitions ---------------------------------------------------------

    @GetMapping("/requisitions")
    public ResponseEntity<ApiResponse<List<RequisitionDto>>> listRequisitions(
            @RequestParam(required = false) RequisitionStatus status,
            @RequestParam(required = false) UUID departmentId,
            Authentication auth) {
        permissionAuthorizationService.authorize(auth, RESOURCE, "MANAGE");
        return ResponseEntity.ok(ApiResponse.ok(requisitionService.list(status, departmentId)));
    }

    @GetMapping("/requisitions/mine")
    public ResponseEntity<ApiResponse<List<RequisitionDto>>> myRequisitions(Authentication auth) {
        permissionAuthorizationService.authorizeAnyPermissionName(auth, REQUEST, MANAGE);
        return ResponseEntity.ok(ApiResponse.ok(requisitionService.listMine(SecurityUtils.getCurrentUserId(auth))));
    }

    @GetMapping("/requisitions/{id}")
    public ResponseEntity<ApiResponse<RequisitionDto>> getRequisition(@PathVariable UUID id, Authentication auth) {
        permissionAuthorizationService.authorizeAnyPermissionName(auth, REQUEST, MANAGE);
        return ResponseEntity.ok(ApiResponse.ok(requisitionService.get(id)));
    }

    @PostMapping("/requisitions")
    public ResponseEntity<ApiResponse<RequisitionDto>> createRequisition(
            @Valid @RequestBody RequisitionCreateDto dto, Authentication auth) {
        permissionAuthorizationService.authorizeAnyPermissionName(auth, REQUEST, MANAGE);
        RequisitionDto created = requisitionService.create(dto, SecurityUtils.getCurrentUserId(auth));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created));
    }

    @PutMapping("/requisitions/{id}")
    public ResponseEntity<ApiResponse<RequisitionDto>> updateRequisition(
            @PathVariable UUID id, @Valid @RequestBody RequisitionUpdateDto dto, Authentication auth) {
        permissionAuthorizationService.authorizeAnyPermissionName(auth, REQUEST, MANAGE);
        return ResponseEntity.ok(ApiResponse.ok(requisitionService.update(id, dto, SecurityUtils.getCurrentUserId(auth))));
    }

    @PostMapping("/requisitions/{id}/submit")
    public ResponseEntity<ApiResponse<RequisitionDto>> submitRequisition(@PathVariable UUID id, Authentication auth) {
        permissionAuthorizationService.authorizeAnyPermissionName(auth, REQUEST, MANAGE);
        return ResponseEntity.ok(ApiResponse.ok(requisitionService.submit(id, SecurityUtils.getCurrentUserId(auth))));
    }

    @PostMapping("/requisitions/{id}/hold")
    public ResponseEntity<ApiResponse<RequisitionDto>> holdRequisition(@PathVariable UUID id, Authentication auth) {
        permissionAuthorizationService.authorize(auth, RESOURCE, "MANAGE");
        return ResponseEntity.ok(ApiResponse.ok(requisitionService.hold(id, SecurityUtils.getCurrentUserId(auth))));
    }

    @PostMapping("/requisitions/{id}/resume")
    public ResponseEntity<ApiResponse<RequisitionDto>> resumeRequisition(@PathVariable UUID id, Authentication auth) {
        permissionAuthorizationService.authorize(auth, RESOURCE, "MANAGE");
        return ResponseEntity.ok(ApiResponse.ok(requisitionService.resume(id, SecurityUtils.getCurrentUserId(auth))));
    }

    @PostMapping("/requisitions/{id}/close")
    public ResponseEntity<ApiResponse<RequisitionDto>> closeRequisition(@PathVariable UUID id, Authentication auth) {
        permissionAuthorizationService.authorize(auth, RESOURCE, "MANAGE");
        return ResponseEntity.ok(ApiResponse.ok(requisitionService.close(id, SecurityUtils.getCurrentUserId(auth))));
    }

    @PostMapping("/requisitions/{id}/cancel")
    public ResponseEntity<ApiResponse<RequisitionDto>> cancelRequisition(@PathVariable UUID id, Authentication auth) {
        permissionAuthorizationService.authorizeAnyPermissionName(auth, REQUEST, MANAGE);
        return ResponseEntity.ok(ApiResponse.ok(requisitionService.cancel(id, SecurityUtils.getCurrentUserId(auth))));
    }

    // --- Candidates -----------------------------------------------------------

    @GetMapping("/candidates")
    public ResponseEntity<ApiResponse<List<CandidateDto>>> listCandidates(
            @RequestParam(required = false) String search, Authentication auth) {
        permissionAuthorizationService.authorize(auth, RESOURCE, "MANAGE");
        return ResponseEntity.ok(ApiResponse.ok(candidateService.list(search)));
    }

    @GetMapping("/candidates/{id}")
    public ResponseEntity<ApiResponse<CandidateDto>> getCandidate(@PathVariable UUID id, Authentication auth) {
        permissionAuthorizationService.authorize(auth, RESOURCE, "MANAGE");
        return ResponseEntity.ok(ApiResponse.ok(candidateService.get(id)));
    }

    @PostMapping("/candidates")
    public ResponseEntity<ApiResponse<CandidateDto>> createCandidate(
            @Valid @RequestBody CandidateCreateDto dto, Authentication auth) {
        permissionAuthorizationService.authorize(auth, RESOURCE, "MANAGE");
        CandidateDto created = candidateService.create(dto, SecurityUtils.getCurrentUserId(auth));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created));
    }

    @PutMapping("/candidates/{id}")
    public ResponseEntity<ApiResponse<CandidateDto>> updateCandidate(
            @PathVariable UUID id, @Valid @RequestBody CandidateUpdateDto dto, Authentication auth) {
        permissionAuthorizationService.authorize(auth, RESOURCE, "MANAGE");
        return ResponseEntity.ok(ApiResponse.ok(candidateService.update(id, dto, SecurityUtils.getCurrentUserId(auth))));
    }

    @GetMapping("/candidates/{id}/applications")
    public ResponseEntity<ApiResponse<List<ApplicationDto>>> candidateApplications(
            @PathVariable UUID id, Authentication auth) {
        permissionAuthorizationService.authorize(auth, RESOURCE, "MANAGE");
        return ResponseEntity.ok(ApiResponse.ok(applicationService.listByCandidate(id)));
    }

    // --- Applications / pipeline ----------------------------------------------

    @GetMapping("/requisitions/{id}/applications")
    public ResponseEntity<ApiResponse<List<ApplicationDto>>> requisitionPipeline(
            @PathVariable UUID id, Authentication auth) {
        permissionAuthorizationService.authorize(auth, RESOURCE, "MANAGE");
        return ResponseEntity.ok(ApiResponse.ok(applicationService.listByRequisition(id)));
    }

    @GetMapping("/applications/{id}")
    public ResponseEntity<ApiResponse<ApplicationDetailDto>> getApplication(@PathVariable UUID id, Authentication auth) {
        permissionAuthorizationService.authorize(auth, RESOURCE, "MANAGE");
        return ResponseEntity.ok(ApiResponse.ok(applicationService.getDetail(id)));
    }

    @PostMapping("/applications")
    public ResponseEntity<ApiResponse<ApplicationDto>> createApplication(
            @Valid @RequestBody ApplicationCreateDto dto, Authentication auth) {
        permissionAuthorizationService.authorize(auth, RESOURCE, "MANAGE");
        ApplicationDto created = applicationService.create(dto, SecurityUtils.getCurrentUserId(auth));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created));
    }

    @PostMapping("/applications/{id}/move")
    public ResponseEntity<ApiResponse<ApplicationDto>> moveApplication(
            @PathVariable UUID id, @Valid @RequestBody ApplicationMoveDto dto, Authentication auth) {
        permissionAuthorizationService.authorize(auth, RESOURCE, "MANAGE");
        return ResponseEntity.ok(ApiResponse.ok(applicationService.moveStage(id, dto, SecurityUtils.getCurrentUserId(auth))));
    }

    @PostMapping("/applications/{id}/rating")
    public ResponseEntity<ApiResponse<ApplicationDto>> rateApplication(
            @PathVariable UUID id, @Valid @RequestBody ApplicationRatingDto dto, Authentication auth) {
        permissionAuthorizationService.authorize(auth, RESOURCE, "MANAGE");
        return ResponseEntity.ok(ApiResponse.ok(applicationService.setRating(id, dto, SecurityUtils.getCurrentUserId(auth))));
    }

    // --- New-hire handoff -----------------------------------------------------

    @GetMapping("/new-hires")
    public ResponseEntity<ApiResponse<List<NewHireDto>>> listNewHires(
            @RequestParam(required = false) NewHireStatus status, Authentication auth) {
        permissionAuthorizationService.authorize(auth, RESOURCE, "MANAGE");
        return ResponseEntity.ok(ApiResponse.ok(newHireHandoffService.list(status)));
    }

    @GetMapping("/new-hires/{id}")
    public ResponseEntity<ApiResponse<NewHireDto>> getNewHire(@PathVariable UUID id, Authentication auth) {
        permissionAuthorizationService.authorize(auth, RESOURCE, "MANAGE");
        return ResponseEntity.ok(ApiResponse.ok(newHireHandoffService.get(id)));
    }

    @PostMapping("/new-hires/{id}/cancel")
    public ResponseEntity<ApiResponse<NewHireDto>> cancelNewHire(@PathVariable UUID id, Authentication auth) {
        permissionAuthorizationService.authorize(auth, RESOURCE, "MANAGE");
        return ResponseEntity.ok(ApiResponse.ok(newHireHandoffService.cancel(id, SecurityUtils.getCurrentUserId(auth))));
    }
}
