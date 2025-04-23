package com.moderator.exception;

/**
 * Exception thrown when a YouTube URL is invalid.
 */
public class InvalidUrlException extends RuntimeException {

    /**
     * Constructs a new InvalidUrlException with the specified detail message.
     *
     * @param message the detail message
     */
    public InvalidUrlException(String message) {
        super(message);
    }

    /**
     * Constructs a new InvalidUrlException with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause
     */
    public InvalidUrlException(String message, Throwable cause) {
        super(message, cause);
    }
} 