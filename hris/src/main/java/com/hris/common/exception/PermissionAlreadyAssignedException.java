package com.hris.common.exception;

public class PermissionAlreadyAssignedException extends RuntimeException {

    public PermissionAlreadyAssignedException(String message) {
        super(message);
    }
}
