package com.booklane.booking.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record CommandOutcome(Status status, String reasonCode, UUID bookingId, Long resourceVersion,
        Boolean alreadyCancelled, Instant processedAt) {
    public enum Status { SUCCEEDED, REJECTED }

    public CommandOutcome {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(processedAt, "processedAt");
        if (reasonCode == null || !reasonCode.matches("[A-Z][A-Z0-9_]{0,63}")) {
            throw new IllegalArgumentException("invalid reason code");
        }
        if (resourceVersion != null && (resourceVersion < 0 || resourceVersion > 9007199254740991L)) {
            throw new IllegalArgumentException("invalid resource version");
        }
        if (processedAt.getNano() != 0) {
            throw new IllegalArgumentException("processedAt must have whole-second precision");
        }
    }
}
