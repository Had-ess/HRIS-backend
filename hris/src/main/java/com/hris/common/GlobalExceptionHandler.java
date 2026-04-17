package com.hris.common;

import com.hris.common.exception.EntityNotFoundException;
import com.hris.common.exception.DepartmentDeletionNotAllowedException;
import com.hris.common.exception.DuplicateProjectDepartmentAssignmentException;
import com.hris.common.exception.FileAttachmentValidationException;
import com.hris.common.exception.InvalidAdminRequestStateException;
import com.hris.common.exception.InvalidLeavePeriodException;
import com.hris.common.exception.InvalidProjectAssignmentException;
import com.hris.common.exception.InvalidRoleHierarchyException;
import com.hris.common.exception.InsufficientLeaveBalanceException;
import com.hris.common.exception.InvalidWorkflowStateException;
import com.hris.common.exception.MissingDepartmentHeadException;
import com.hris.common.exception.StepAlreadyDecidedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(InsufficientLeaveBalanceException.class)
    public ResponseEntity<ApiResponse<Void>> handleInsufficientBalance(
            InsufficientLeaveBalanceException ex) {
        return ResponseEntity.badRequest()
            .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(EntityNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(StepAlreadyDecidedException.class)
    public ResponseEntity<ApiResponse<Void>> handleStepAlreadyDecided(
            StepAlreadyDecidedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(InvalidWorkflowStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidWorkflowState(
            InvalidWorkflowStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(MissingDepartmentHeadException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingDepartmentHead(
            MissingDepartmentHeadException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(FileAttachmentValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleFileAttachmentValidation(
            FileAttachmentValidationException ex) {
        return ResponseEntity.badRequest()
            .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(InvalidProjectAssignmentException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidProjectAssignment(
            InvalidProjectAssignmentException ex) {
        return ResponseEntity.badRequest()
            .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(DepartmentDeletionNotAllowedException.class)
    public ResponseEntity<ApiResponse<Void>> handleDepartmentDeletionNotAllowed(
            DepartmentDeletionNotAllowedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(DuplicateProjectDepartmentAssignmentException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateProjectDepartmentAssignment(
            DuplicateProjectDepartmentAssignmentException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(InvalidAdminRequestStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidAdminRequestState(
            InvalidAdminRequestStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(InvalidRoleHierarchyException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidRoleHierarchy(
            InvalidRoleHierarchyException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(InvalidLeavePeriodException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidLeavePeriod(
            InvalidLeavePeriodException ex) {
        return ResponseEntity.badRequest()
            .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(
            OptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiResponse.error("Concurrent update detected. Please retry."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(
            MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
            errors.put(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest()
            .body(new ApiResponse<>(errors, "Validation failed", java.time.Instant.now(), false));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
            .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error("An unexpected error occurred. Please contact support."));
    }
}
