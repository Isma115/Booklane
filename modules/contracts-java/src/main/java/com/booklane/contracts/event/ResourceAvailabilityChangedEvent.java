package com.booklane.contracts.event;

import java.time.Instant;
import java.util.UUID;

public record ResourceAvailabilityChangedEvent(int schemaVersion, String type, UUID eventId,
        UUID resourceId, long resourceVersion, Instant occurredAt) {}
