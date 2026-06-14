package com.hris.performance.controller;

import com.hris.common.ApiResponse;
import com.hris.performance.dto.PerformanceDtos.FeedbackAggregateDto;
import com.hris.performance.dto.PerformanceDtos.FeedbackCandidateDto;
import com.hris.performance.dto.PerformanceDtos.FeedbackNominateDto;
import com.hris.performance.dto.PerformanceDtos.FeedbackRequestDto;
import com.hris.performance.dto.PerformanceDtos.FeedbackSubmitDto;
import com.hris.performance.dto.PerformanceDtos.MyFeedbackRequestDto;
import com.hris.performance.service.FeedbackService;
import com.hris.security.PermissionAuthorizationService;
import com.hris.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 360 / peer feedback. Endpoints are gated by PERFORMANCE_READ/MANAGE; the service enforces
 * the finer split — reviewer-or-HR for nominate/remove/panel/candidates, the nominated rater
 * for their own inbox/submit/decline, and subject-or-reviewer-or-HR for the aggregate.
 */
@RestController
@RequestMapping("/api/performance/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;
    private final PermissionAuthorizationService permissionAuthorizationService;

    // --- Rater inbox ---

    @GetMapping("/requests/me")
    public ResponseEntity<ApiResponse<List<MyFeedbackRequestDto>>> myRequests(Authentication auth) {
        permissionAuthorizationService.authorizeAnyPermissionName(auth, "PERFORMANCE_READ", "PERFORMANCE_MANAGE");
        return ResponseEntity.ok(ApiResponse.ok(
            feedbackService.getMyRequests(SecurityUtils.getCurrentUserId(auth))));
    }

    @GetMapping("/requests/{id}")
    public ResponseEntity<ApiResponse<MyFeedbackRequestDto>> myRequest(@PathVariable UUID id, Authentication auth) {
        permissionAuthorizationService.authorizeAnyPermissionName(auth, "PERFORMANCE_READ", "PERFORMANCE_MANAGE");
        return ResponseEntity.ok(ApiResponse.ok(
            feedbackService.getMyRequest(id, SecurityUtils.getCurrentUserId(auth))));
    }

    @PostMapping("/requests/{id}/submit")
    public ResponseEntity<ApiResponse<MyFeedbackRequestDto>> submit(
            @PathVariable UUID id, @Valid @RequestBody FeedbackSubmitDto dto, Authentication auth) {
        permissionAuthorizationService.authorizeAnyPermissionName(auth, "PERFORMANCE_READ", "PERFORMANCE_MANAGE");
        return ResponseEntity.ok(ApiResponse.ok(
            feedbackService.submit(id, dto, SecurityUtils.getCurrentUserId(auth))));
    }

    @PostMapping("/requests/{id}/decline")
    public ResponseEntity<ApiResponse<MyFeedbackRequestDto>> decline(@PathVariable UUID id, Authentication auth) {
        permissionAuthorizationService.authorizeAnyPermissionName(auth, "PERFORMANCE_READ", "PERFORMANCE_MANAGE");
        return ResponseEntity.ok(ApiResponse.ok(
            feedbackService.decline(id, SecurityUtils.getCurrentUserId(auth))));
    }

    @DeleteMapping("/requests/{id}")
    public ResponseEntity<ApiResponse<Void>> remove(@PathVariable UUID id, Authentication auth) {
        permissionAuthorizationService.authorizeAnyPermissionName(auth, "PERFORMANCE_READ", "PERFORMANCE_MANAGE");
        feedbackService.removeRequest(id, SecurityUtils.getCurrentUserId(auth));
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // --- Reviewer / HR panel management ---

    @GetMapping("/reviews/{reviewId}/candidates")
    public ResponseEntity<ApiResponse<List<FeedbackCandidateDto>>> candidates(
            @PathVariable UUID reviewId, Authentication auth) {
        permissionAuthorizationService.authorizeAnyPermissionName(auth, "PERFORMANCE_READ", "PERFORMANCE_MANAGE");
        return ResponseEntity.ok(ApiResponse.ok(
            feedbackService.getCandidates(reviewId, SecurityUtils.getCurrentUserId(auth))));
    }

    @GetMapping("/reviews/{reviewId}/panel")
    public ResponseEntity<ApiResponse<List<FeedbackRequestDto>>> panel(
            @PathVariable UUID reviewId, Authentication auth) {
        permissionAuthorizationService.authorizeAnyPermissionName(auth, "PERFORMANCE_READ", "PERFORMANCE_MANAGE");
        return ResponseEntity.ok(ApiResponse.ok(
            feedbackService.getPanel(reviewId, SecurityUtils.getCurrentUserId(auth))));
    }

    @PostMapping("/reviews/{reviewId}/nominate")
    public ResponseEntity<ApiResponse<List<FeedbackRequestDto>>> nominate(
            @PathVariable UUID reviewId, @Valid @RequestBody FeedbackNominateDto dto, Authentication auth) {
        permissionAuthorizationService.authorizeAnyPermissionName(auth, "PERFORMANCE_READ", "PERFORMANCE_MANAGE");
        return ResponseEntity.ok(ApiResponse.ok(
            feedbackService.nominate(reviewId, dto, SecurityUtils.getCurrentUserId(auth))));
    }

    // --- Subject anonymized aggregate ---

    @GetMapping("/reviews/{reviewId}/aggregate")
    public ResponseEntity<ApiResponse<FeedbackAggregateDto>> aggregate(
            @PathVariable UUID reviewId, Authentication auth) {
        permissionAuthorizationService.authorizeAnyPermissionName(auth, "PERFORMANCE_READ", "PERFORMANCE_MANAGE");
        return ResponseEntity.ok(ApiResponse.ok(
            feedbackService.getAggregate(reviewId, SecurityUtils.getCurrentUserId(auth))));
    }
}
