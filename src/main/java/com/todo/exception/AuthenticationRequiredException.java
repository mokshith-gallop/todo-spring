package com.todo.exception;

/**
 * Thrown when a request lacks valid authentication credentials.
 * Mapped to HTTP 401 by {@link GlobalExceptionHandler}.
 */
public class AuthenticationRequiredException extends RuntimeException {

    public AuthenticationRequiredException(String message) {
        super(message);
    }
}
