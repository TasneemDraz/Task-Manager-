package com.tasneem.taskmanager.common.exception;

/** Mapped to 409: the request is valid but conflicts with the current state. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
