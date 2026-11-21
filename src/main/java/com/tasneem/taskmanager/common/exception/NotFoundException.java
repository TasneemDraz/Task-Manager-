package com.tasneem.taskmanager.common.exception;

/** Mapped to 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
