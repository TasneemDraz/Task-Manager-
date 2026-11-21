package com.tasneem.taskmanager.common.exception;

/** Mapped to 401. The message is deliberately the same for every login failure. */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
