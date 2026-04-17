package com.hris.common.exception;

public class StepAlreadyDecidedException extends RuntimeException {
    public StepAlreadyDecidedException(String message) {
        super(message);
    }
}
