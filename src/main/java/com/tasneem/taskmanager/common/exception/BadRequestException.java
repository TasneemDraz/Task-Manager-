package com.tasneem.taskmanager.common.exception;

/** Mapped to 400 with the same field-error shape as Bean Validation failures. */
public class BadRequestException extends RuntimeException {

    private final String field;

    public BadRequestException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
