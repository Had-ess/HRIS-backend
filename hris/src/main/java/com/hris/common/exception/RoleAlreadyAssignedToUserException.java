package com.hris.common.exception;

public class RoleAlreadyAssignedToUserException extends RuntimeException {

    public RoleAlreadyAssignedToUserException(String message) {
        super(message);
    }
}
