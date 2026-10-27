package com.booklane.contracts.command;

import java.time.Instant;
import java.util.UUID;

public record CommandEnvelope(int schemaVersion, UUID messageId, UUID commandId, CommandType type,
        UUID resourceId, UUID actorId, ActorRole actorRole, String requestHash, Instant issuedAt,
        UUID traceId, CommandPayload payload) {}
