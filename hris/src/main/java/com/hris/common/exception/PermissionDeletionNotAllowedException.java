package com.hris.common.exception;

public class PermissionDeletionNotAllowedException extends RuntimeException {

    public PermissionDeletionNotAllowedException(String message) {
        super(message);
    }
}
