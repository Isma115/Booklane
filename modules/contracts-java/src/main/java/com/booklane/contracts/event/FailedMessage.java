package com.booklane.contracts.event;

import java.time.Instant;
import java.util.UUID;

public record FailedMessage(int schemaVersion, UUID diagnosticId, String sourceTopic,
        int sourcePartition, long sourceOffset, String reasonCode, Instant occurredAt, UUID traceId) {}
