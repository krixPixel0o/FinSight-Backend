package com.finsight.dto;

import java.time.Instant;
import java.util.List;

public record ApiError(boolean success, Instant timestamp, int status, String message, String path, List<String> errors) {
    public ApiError(Instant timestamp, int status, String message, String path) {
        this(false, timestamp, status, message, path, List.of(message));
    }
}