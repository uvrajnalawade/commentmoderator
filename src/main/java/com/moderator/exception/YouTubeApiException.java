package com.moderator.exception;

/**
 * Exception thrown when there is an error with the YouTube API.
 */
public class YouTubeApiException extends RuntimeException {

    /**
     * Constructs a new YouTubeApiException with the specified detail message.
     *
     * @param message the detail message
     */
    public YouTubeApiException(String message) {
        super(message);
    }

    /**
     * Constructs a new YouTubeApiException with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause
     */
    public YouTubeApiException(String message, Throwable cause) {
        super(message, cause);
    }
} 