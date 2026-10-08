package com.finsight.service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.finsight.exception.TooManyRequestsException;

/** In-memory fixed-window limiter for the optional, billable assistant endpoint. */
@Component
public class AssistantRateLimiter {
    private final int maxRequestsPerHour;
    private final Map<String, RequestWindow> windows = new HashMap<>();

    public AssistantRateLimiter(@Value("${finsight.gemini.max-requests-per-hour:10}") int maxRequestsPerHour) {
        if (maxRequestsPerHour < 1) throw new IllegalArgumentException("Assistant rate limit must be positive");
        this.maxRequestsPerHour = maxRequestsPerHour;
    }

    public synchronized void acquire(String userKey) {
        Instant now = Instant.now();
        Instant cutoff = now.minus(Duration.ofHours(1));
        windows.entrySet().removeIf(entry -> entry.getValue().startedAt().isBefore(cutoff));
        RequestWindow window = windows.get(userKey);
        if (window == null || window.startedAt().isBefore(cutoff)) {
            windows.put(userKey, new RequestWindow(now, 1));
            return;
        }
        if (window.count() >= maxRequestsPerHour) {
            throw new TooManyRequestsException("Assistant request limit reached. Try again later.");
        }
        windows.put(userKey, new RequestWindow(window.startedAt(), window.count() + 1));
    }

    private record RequestWindow(Instant startedAt, int count) { }
}
