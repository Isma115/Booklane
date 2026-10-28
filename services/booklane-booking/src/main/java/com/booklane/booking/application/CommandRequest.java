package com.booklane.booking.application;

import java.util.Objects;
import java.util.UUID;

public record CommandRequest(UUID actorId, UUID commandId, String requestHash, String commandType,
        UUID resourceId, String canonicalRequest, UUID traceId) {
    public CommandRequest {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(commandId, "commandId");
        Objects.requireNonNull(resourceId, "resourceId");
        Objects.requireNonNull(traceId, "traceId");
        Objects.requireNonNull(canonicalRequest, "canonicalRequest");
        if (requestHash == null || !requestHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("invalid request hash");
        }
        if (commandType == null || !commandType.matches("[A-Za-z]{1,40}")) {
            throw new IllegalArgumentException("invalid command type");
        }
    }
}
