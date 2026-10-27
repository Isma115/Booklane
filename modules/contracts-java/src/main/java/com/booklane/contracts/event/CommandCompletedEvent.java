package com.booklane.contracts.event;

import java.time.Instant;
import java.util.UUID;

public record CommandCompletedEvent(int schemaVersion, String type, UUID eventId, UUID commandId,
        UUID actorId, String requestHash, Outcome outcome, String reasonCode, UUID bookingId,
        UUID resourceId, Long resourceVersion, Instant occurredAt, UUID traceId) {}
