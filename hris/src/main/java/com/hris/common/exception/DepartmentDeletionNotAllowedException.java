package com.hris.common.exception;

public class DepartmentDeletionNotAllowedException extends RuntimeException {

    public DepartmentDeletionNotAllowedException(String message) {
        super(message);
    }
}
