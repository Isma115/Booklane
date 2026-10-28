package com.booklane.booking.adapters.http;

import java.util.UUID;

public record ApiError(String code, String message, UUID traceId) {
    public ApiError(String code, String message) { this(code, message, UUID.randomUUID()); }
}
