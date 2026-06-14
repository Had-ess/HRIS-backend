package com.hris.compensation.controller;

import com.hris.common.ApiResponse;
import com.hris.compensation.dto.CompensationDtos.CompensationRecordCreateDto;
import com.hris.compensation.dto.CompensationDtos.CompensationRecordDto;
import com.hris.compensation.dto.CompensationDtos.MyCompensationDto;
import com.hris.compensation.dto.CompensationDtos.PayGradeCreateDto;
import com.hris.compensation.dto.CompensationDtos.PayGradeDto;
import com.hris.compensation.dto.CompensationReviewDtos.BudgetPoolDto;
import com.hris.compensation.dto.CompensationReviewDtos.BudgetPoolUpdateDto;
import com.hris.compensation.dto.CompensationReviewDtos.MeritMatrixCellDto;
import com.hris.compensation.dto.CompensationReviewDtos.MeritMatrixUpdateDto;
import com.hris.compensation.dto.CompensationReviewDtos.ProposalDto;
import com.hris.compensation.dto.CompensationReviewDtos.ProposalUpdateDto;
import com.hris.compensation.dto.CompensationReviewDtos.ReviewCycleCreateDto;
import com.hris.compensation.dto.CompensationReviewDtos.ReviewCycleDto;
import com.hris.compensation.dto.CompensationBonusDtos.BonusAwardDto;
import com.hris.compensation.dto.CompensationBonusDtos.BonusAwardUpdateDto;
import com.hris.compensation.dto.CompensationBonusDtos.BonusCycleCreateDto;
import com.hris.compensation.dto.CompensationBonusDtos.BonusCycleDto;
import com.hris.compensation.dto.CompensationBonusDtos.BonusPlanCreateDto;
import com.hris.compensation.dto.CompensationBonusDtos.BonusPlanDto;
import com.hris.compensation.dto.CompensationBonusDtos.BonusPoolDto;
import com.hris.compensation.dto.CompensationBonusDtos.BonusPoolUpdateDto;
import com.hris.compensation.dto.CompensationBonusDtos.SpotAwardCreateDto;
import com.hris.compensation.service.BonusCycleService;
import com.hris.compensation.service.BonusPlanService;
import com.hris.compensation.service.CompensationReviewService;
import com.hris.compensation.service.CompensationService;
import com.hris.compensation.service.MeritMatrixService;
import com.hris.compensation.service.PayGradeService;
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
@RequestMapping("/api/compensation")
@RequiredArgsConstructor
public class CompensationController {

    private final CompensationService compensationService;
    private final PayGradeService payGradeService;
    private final MeritMatrixService meritMatrixService;
    private final CompensationReviewService reviewService;
    private final BonusPlanService bonusPlanService;
    private final BonusCycleService bonusCycleService;
    private final PermissionAuthorizationService permissionAuthorizationService;

    // --- Self-view ------------------------------------------------------------

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MyCompensationDto>> myCompensation(Authentication auth) {
        permissionAuthorizationService.authorize(auth, "COMPENSATION", "VIEW_OWN");
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.ok(ApiResponse.ok(compensationService.getMyCompensation(userId)));
    }

    // --- Pay grades (HR) ------------------------------------------------------

    @GetMapping("/grades")
    public ResponseEntity<ApiResponse<List<PayGradeDto>>> getGrades(
            @RequestParam(name = "activeOnly", defaultValue = "false") boolean activeOnly,
            Authentication auth) {
        permissionAuthorizationService.authorize(auth, "COMPENSATION", "MANAGE");
        return ResponseEntity.ok(ApiResponse.ok(payGradeService.getAll(activeOnly)));
    }

    @PostMapping("/grades")
    public ResponseEntity<ApiResponse<PayGradeDto>> createGrade(
            @Valid @RequestBody PayGradeCreateDto dto, Authentication auth) {
        permissionAuthorizationService.authorize(auth, "COMPENSATION", "MANAGE");
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(payGradeService.create(dto, userId)));
    }

    @PatchMapping("/grades/{id}")
    public ResponseEntity<ApiResponse<PayGradeDto>> updateGrade(
            @PathVariable UUID id, @Valid @RequestBody PayGradeCreateDto dto, Authentication auth) {
        permissionAuthorizationService.authorize(auth, "COMPENSATION", "MANAGE");
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.ok(ApiResponse.ok(payGradeService.update(id, dto, userId)));
    }

    @DeleteMapping("/grades/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteGrade(@PathVariable UUID id, Authentication auth) {
        permissionAuthorizationService.authorize(auth, "COMPENSATION", "MANAGE");
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        payGradeService.delete(id, userId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // --- Employee compensation records (HR) -----------------------------------

    @GetMapping("/employees/{employeeId}/records")
    public ResponseEntity<ApiResponse<List<CompensationRecordDto>>> getRecords(
            @PathVariable UUID employeeId, Authentication auth) {
        permissionAuthorizationService.authorize(auth, "COMPENSATION", "MANAGE");
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.ok(ApiResponse.ok(compensationService.listRecords(employeeId, userId)));
    }

    @PostMapping("/employees/{employeeId}/records")
    public ResponseEntity<ApiResponse<CompensationRecordDto>> addRecord(
            @PathVariable UUID employeeId,
            @Valid @RequestBody CompensationRecordCreateDto dto, Authentication auth) {
        permissionAuthorizationService.authorize(auth, "COMPENSATION", "MANAGE");
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(compensationService.addRecord(employeeId, dto, userId)));
    }

    // --- Merit matrix (HR) ----------------------------------------------------

    @GetMapping("/merit-matrix")
    public ResponseEntity<ApiResponse<List<MeritMatrixCellDto>>> getMeritMatrix(Authentication auth) {
        permissionAuthorizationService.authorize(auth, "COMPENSATION", "MANAGE");
        return ResponseEntity.ok(ApiResponse.ok(meritMatrixService.getMatrix()));
    }

    @PatchMapping("/merit-matrix")
    public ResponseEntity<ApiResponse<List<MeritMatrixCellDto>>> updateMeritMatrix(
            @Valid @RequestBody MeritMatrixUpdateDto dto, Authentication auth) {
        permissionAuthorizationService.authorize(auth, "COMPENSATION", "MANAGE");
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.ok(ApiResponse.ok(meritMatrixService.updateMatrix(dto, userId)));
    }

    // --- Comp-review cycles (HR) ----------------------------------------------

    @GetMapping("/review-cycles")
    public ResponseEntity<ApiResponse<List<ReviewCycleDto>>> getReviewCycles(Authentication auth) {
        permissionAuthorizationService.authorize(auth, "COMPENSATION", "MANAGE");
        return ResponseEntity.ok(ApiResponse.ok(reviewService.getAll()));
    }

    @GetMapping("/review-cycles/{id}")
    public ResponseEntity<ApiResponse<ReviewCycleDto>> getReviewCycle(
            @PathVariable UUID id, Authentication auth) {
        permissionAuthorizationService.authorize(auth, "COMPENSATION", "MANAGE");
        return ResponseEntity.ok(ApiResponse.ok(reviewService.get(id)));
    }

    @PostMapping("/review-cycles")
    public ResponseEntity<ApiResponse<ReviewCycleDto>> createReviewCycle(
            @Valid @RequestBody ReviewCycleCreateDto dto, Authentication auth) {
        permissionAuthorizationService.authorize(auth, "COMPENSATION", "MANAGE");
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(reviewService.create(dto, userId)));
    }

    @PatchMapping("/review-cycles/{id}")
    public ResponseEntity<ApiResponse<ReviewCycleDto>> updateReviewCycle(
            @PathVariable UUID id, @Valid @RequestBody ReviewCycleCreateDto dto, Authentication auth) {
        permissionAuthorizationService.authorize(auth, "COMPENSATION", "MANAGE");
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.ok(ApiResponse.ok(reviewService.update(id, dto, userId)));
    }

    @PostMapping("/review-cycles/{id}/activate")
    public ResponseEntity<ApiResponse<ReviewCycleDto>> activateReviewCycle(
            @PathVariable UUID id, Authentication auth) {
        permissionAuthorizationService.authorize(auth, "COMPENSATION", "MANAGE");
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.ok(ApiResponse.ok(reviewService.activate(id, userId)));
    }

    @PostMapping("/review-cycles/{id}/advance-review")
    public ResponseEntity<ApiResponse<ReviewCycleDto>> advanceReviewCycle(
            @PathVariable UUID id, Authentication auth) {
        permissionAuthorizationService.authorize(auth, "COMPENSATION", "MANAGE");
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.ok(ApiResponse.ok(reviewService.advanceToReview(id, userId)));
    }

    @PostMapping("/review-cycles/{id}/apply")
    public ResponseEntity<ApiResponse<ReviewCycleDto>> applyReviewCycle(
            @PathVariable UUID id, Authentication auth) {
        permissionAuthorizationService.authorize(auth, "COMPENSATION", "MANAGE");
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.ok(ApiResponse.ok(reviewService.applyAndClose(id, userId)));
    }

    // --- Budget pools (HR) ----------------------------------------------------

    @GetMapping("/review-cycles/{id}/pools")
    public ResponseEntity<ApiResponse<List<BudgetPoolDto>>> getPools(
            @PathVariable UUID id, Authentication auth) {
        permissionAuthorizationService.authorize(auth, "COMPENSATION", "MANAGE");
        return ResponseEntity.ok(ApiResponse.ok(reviewService.getPools(id)));
    }

    @PatchMapping("/pools/{poolId}")
    public ResponseEntity<ApiResponse<BudgetPoolDto>> updatePool(
            @PathVariable UUID poolId, @Valid @RequestBody BudgetPoolUpdateDto dto, Authentication auth) {
        permissionAuthorizationService.authorize(auth, "COMPENSATION", "MANAGE");
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.ok(ApiResponse.ok(reviewService.updatePool(poolId, dto, userId)));
    }

    // --- Proposals (HR view + approval) ---------------------------------------

    @GetMapping("/review-cycles/{id}/proposals")
    public ResponseEntity<ApiResponse<List<ProposalDto>>> getProposals(
            @PathVariable UUID id, Authentication auth) {
        permissionAuthorizationService.authorize(auth, "COMPENSATION", "MANAGE");
        return ResponseEntity.ok(ApiResponse.ok(reviewService.listProposals(id)));
    }

    @PostMapping("/proposals/{proposalId}/approve")
    public ResponseEntity<ApiResponse<ProposalDto>> approveProposal(
            @PathVariable UUID proposalId, Authentication auth) {
        permissionAuthorizationService.authorize(auth, "COMPENSATION", "MANAGE");
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.ok(ApiResponse.ok(reviewService.approve(proposalId, userId)));
    }

    @PostMapping("/proposals/{proposalId}/reject")
    public ResponseEntity<ApiResponse<ProposalDto>> rejectProposal(
            @PathVariable UUID proposalId, Authentication auth) {
        permissionAuthorizationService.authorize(auth, "COMPENSATION", "MANAGE");
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.ok(ApiResponse.ok(reviewService.reject(proposalId, userId)));
    }

    // --- Proposals (manager surface, scoped to own reports) -------------------

    @GetMapping("/review-cycles/mine")
    public ResponseEntity<ApiResponse<List<ReviewCycleDto>>> getMyCycles(Authentication auth) {
        permissionAuthorizationService.authorize(auth, "COMPENSATION", "REVIEW");
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.ok(ApiResponse.ok(reviewService.myCycles(userId)));
    }

    @GetMapping("/review-cycles/{id}/my-pools")
    public ResponseEntity<ApiResponse<List<BudgetPoolDto>>> getMyPools(
            @PathVariable UUID id, Authentication auth) {
        permissionAuthorizationService.authorize(auth, "COMPENSATION", "REVIEW");
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.ok(ApiResponse.ok(reviewService.myPools(id, userId)));
    }

    @GetMapping("/review-cycles/{id}/my-proposals")
    public ResponseEntity<ApiResponse<List<ProposalDto>>> getMyProposals(
            @PathVariable UUID id, Authentication auth) {
        permissionAuthorizationService.authorize(auth, "COMPENSATION", "REVIEW");
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.ok(ApiResponse.ok(reviewService.myProposals(id, userId)));
    }

    @PatchMapping("/proposals/{proposalId}")
    public ResponseEntity<ApiResponse<ProposalDto>> saveProposal(
            @PathVariable UUID proposalId, @Valid @RequestBody ProposalUpdateDto dto, Authentication auth) {
        permissionAuthorizationService.authorize(auth, "COMPENSATION", "REVIEW");
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.ok(ApiResponse.ok(reviewService.saveProposal(proposalId, dto, userId)));
    }

    // === Phase 3: variable / bonus pay =======================================

    // --- Bonus plans (HR) -----------------------------------------------------

    @GetMapping("/bonus-plans")
    public ResponseEntity<ApiResponse<List<BonusPlanDto>>> getBonusPlans(
            @RequestParam(name = "activeOnly", defaultValue = "false") boolean activeOnly, Authentication auth) {
        permissionAuthorizationService.authorize(auth, "COMPENSATION", "MANAGE");
        return ResponseEntity.ok(ApiResponse.ok(bonusPlanService.getAll(activeOnly)));
    }

    @PostMapping("/bonus-plans")
    public ResponseEntity<ApiResponse<BonusPlanDto>> createBonusPlan(
            @Valid @RequestBody BonusPlanCreateDto dto, Authentication auth) {
        permissionAuthorizationService.authorize(auth, "COMPENSATION", "MANAGE");
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(bonusPlanService.create(dto, userId)));
    }

    @PatchMapping("/bonus-plans/{id}")
    public ResponseEntity<ApiResponse<BonusPlanDto>> updateBonusPlan(
            @PathVariable UUID id, @Valid @RequestBody BonusPlanCreateDto dto, Authentication auth) {
        permissionAuthorizationService.authorize(auth, "COMPENSATION", "MANAGE");
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.ok(ApiResponse.ok(bonusPlanService.update(id, dto, userId)));
    }

    @DeleteMapping("/bonus-plans/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteBonusPlan(@PathVariable UUID id, Authentication auth) {
        permissionAuthorizationService.authorize(auth, "COMPENSATION", "MANAGE");
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        bonusPlanService.delete(id, userId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // --- Bonus cycles (HR) ----------------------------------------------------

    @GetMapping("/bonus-cycles")
    public ResponseEntity<ApiResponse<List<BonusCycleDto>>> getBonusCycles(Authentication auth) {
        permissionAuthorizationService.authorize(auth, "COMPENSATION", "MANAGE");
        return ResponseEntity.ok(ApiResponse.ok(bonusCycleService.getAll()));
    }

    @GetMapping("/bonus-cycles/{id}")
    public ResponseEntity<ApiResponse<BonusCycleDto>> getBonusCycle(@PathVariable UUID id, Authentication auth) {
        permissionAuthorizationService.authorize(auth, "COMPENSATION", "MANAGE");
        return ResponseEntity.ok(ApiResponse.ok(bonusCycleService.get(id)));
    }

    @PostMapping("/bonus-cycles")
    public ResponseEntity<ApiResponse<BonusCycleDto>> createBonusCycle(
            @Valid @RequestBody BonusCycleCreateDto dto, Authentication auth) {
        permissionAuthorizationService.authorize(auth, "COMPENSATION", "MANAGE");
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(bonusCycleService.create(dto, userId)));
    }

    @PatchMapping("/bonus-cycles/{id}")
    public ResponseEntity<ApiResponse<BonusCycleDto>> updateBonusCycle(
            @PathVariable UUID id, @Valid @RequestBody BonusCycleCreateDto dto, Authentication auth) {
        permissionAuthorizationService.authorize(auth, "COMPENSATION", "MANAGE");
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.ok(ApiResponse.ok(bonusCycleService.update(id, dto, userId)));
    }

    @PostMapping("/bonus-cycles/{id}/activate")
    public ResponseEntity<ApiResponse<BonusCycleDto>> activateBonusCycle(
            @PathVariable UUID id, Authentication auth) {
        permissionAuthorizationService.authorize(auth, "COMPENSATION", "MANAGE");
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.ok(ApiResponse.ok(bonusCycleService.activate(id, userId)));
    }

    @PostMapping("/bonus-cycles/{id}/advance-review")
    public ResponseEntity<ApiResponse<BonusCycleDto>> advanceBonusCycle(
            @PathVariable UUID id, Authentication auth) {
        permissionAuthorizationService.authorize(auth, "COMPENSATION", "MANAGE");
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.ok(ApiResponse.ok(bonusCycleService.advanceToReview(id, userId)));
    }

    @PostMapping("/bonus-cycles/{id}/apply")
    public ResponseEntity<ApiResponse<BonusCycleDto>> applyBonusCycle(
            @PathVariable UUID id, Authentication auth) {
        permissionAuthorizationService.authorize(auth, "COMPENSATION", "MANAGE");
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.ok(ApiResponse.ok(bonusCycleService.applyAndClose(id, userId)));
    }

    // --- Bonus pools (HR) -----------------------------------------------------

    @GetMapping("/bonus-cycles/{id}/pools")
    public ResponseEntity<ApiResponse<List<BonusPoolDto>>> getBonusPools(
            @PathVariable UUID id, Authentication auth) {
        permissionAuthorizationService.authorize(auth, "COMPENSATION", "MANAGE");
        return ResponseEntity.ok(ApiResponse.ok(bonusCycleService.getPools(id)));
    }

    @PatchMapping("/bonus-pools/{poolId}")
    public ResponseEntity<ApiResponse<BonusPoolDto>> updateBonusPool(
            @PathVariable UUID poolId, @Valid @RequestBody BonusPoolUpdateDto dto, Authentication auth) {
        permissionAuthorizationService.authorize(auth, "COMPENSATION", "MANAGE");
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.ok(ApiResponse.ok(bonusCycleService.updatePool(poolId, dto, userId)));
    }

    // --- Bonus awards (HR view + approval + spot) -----------------------------

    @GetMapping("/bonus-cycles/{id}/awards")
    public ResponseEntity<ApiResponse<List<BonusAwardDto>>> getBonusAwards(
            @PathVariable UUID id, Authentication auth) {
        permissionAuthorizationService.authorize(auth, "COMPENSATION", "MANAGE");
        return ResponseEntity.ok(ApiResponse.ok(bonusCycleService.listAwards(id)));
    }

    @PostMapping("/bonus-awards/{awardId}/approve")
    public ResponseEntity<ApiResponse<BonusAwardDto>> approveBonusAward(
            @PathVariable UUID awardId, Authentication auth) {
        permissionAuthorizationService.authorize(auth, "COMPENSATION", "MANAGE");
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.ok(ApiResponse.ok(bonusCycleService.approve(awardId, userId)));
    }

    @PostMapping("/bonus-awards/{awardId}/reject")
    public ResponseEntity<ApiResponse<BonusAwardDto>> rejectBonusAward(
            @PathVariable UUID awardId, Authentication auth) {
        permissionAuthorizationService.authorize(auth, "COMPENSATION", "MANAGE");
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.ok(ApiResponse.ok(bonusCycleService.reject(awardId, userId)));
    }

    @PostMapping("/bonus-awards/spot")
    public ResponseEntity<ApiResponse<BonusAwardDto>> grantSpotBonus(
            @Valid @RequestBody SpotAwardCreateDto dto, Authentication auth) {
        permissionAuthorizationService.authorize(auth, "COMPENSATION", "MANAGE");
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(bonusCycleService.grantSpot(dto, userId)));
    }

    // --- Bonus awards (manager surface, scoped to own reports) ----------------

    @GetMapping("/bonus-cycles/mine")
    public ResponseEntity<ApiResponse<List<BonusCycleDto>>> getMyBonusCycles(Authentication auth) {
        permissionAuthorizationService.authorize(auth, "COMPENSATION", "REVIEW");
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.ok(ApiResponse.ok(bonusCycleService.myCycles(userId)));
    }

    @GetMapping("/bonus-cycles/{id}/my-pools")
    public ResponseEntity<ApiResponse<List<BonusPoolDto>>> getMyBonusPools(
            @PathVariable UUID id, Authentication auth) {
        permissionAuthorizationService.authorize(auth, "COMPENSATION", "REVIEW");
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.ok(ApiResponse.ok(bonusCycleService.myPools(id, userId)));
    }

    @GetMapping("/bonus-cycles/{id}/my-awards")
    public ResponseEntity<ApiResponse<List<BonusAwardDto>>> getMyBonusAwards(
            @PathVariable UUID id, Authentication auth) {
        permissionAuthorizationService.authorize(auth, "COMPENSATION", "REVIEW");
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.ok(ApiResponse.ok(bonusCycleService.myAwards(id, userId)));
    }

    @PatchMapping("/bonus-awards/{awardId}")
    public ResponseEntity<ApiResponse<BonusAwardDto>> saveBonusAward(
            @PathVariable UUID awardId, @Valid @RequestBody BonusAwardUpdateDto dto, Authentication auth) {
        permissionAuthorizationService.authorize(auth, "COMPENSATION", "REVIEW");
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.ok(ApiResponse.ok(bonusCycleService.saveAward(awardId, dto, userId)));
    }
}
