// src/main/java/com/chatrah/school/dto/ErrorResponseDTO.java
package com.chatrah.school.dto;

import java.time.LocalDateTime;

/**
 * Standard error response wrapper returned by the global exception mappers.
 * Ensures that all API errors follow a consistent JSON structure.
 */
public class ErrorResponseDTO {

    /** HTTP status code (e.g. 400, 404, 500). */
    private int status;

    /** Short error label, typically the reason phrase (e.g. "Bad Request", "Not Found"). */
    private String error;

    /** Human-readable message describing the error. */
    private String message;

    /** Optional request path (if available). */
    private String path;

    /** Timestamp when the error was generated on the server. */
    private LocalDateTime timestamp = LocalDateTime.now();

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
