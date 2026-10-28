package com.booklane.booking.application;

import com.booklane.booking.application.port.ReservationStore;
import com.booklane.booking.domain.Actor;
import com.booklane.booking.security.DemoIdentityCatalog;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;

@Service
public class SetResourceStatusHandler {
    private final CommandProcessor processor;
    private final ReservationStore store;
    private final DemoIdentityCatalog identities;
    private final Clock clock;

    public SetResourceStatusHandler(CommandProcessor processor, ReservationStore store, DemoIdentityCatalog identities, Clock clock) {
        this.processor = processor;
        this.store = store;
        this.identities = identities;
        this.clock = clock;
    }

    public ProcessingResult setStatus(CommandRequest request, Actor actor, boolean active, String reason, long expectedVersion) {
        if (!"SetResourceStatus".equals(request.commandType())) throw new IllegalArgumentException("unexpected command type");
        return processor.process(request, ignored -> decide(request, actor, active, reason, expectedVersion));
    }

    private CommandOutcome decide(CommandRequest request, Actor actor, boolean active, String reason, long expectedVersion) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        if (actor == null || !request.actorId().equals(actor.id()) || identities.findById(actor.id())
                .filter(identity -> identity.role().name().equals(actor.role().name())).isEmpty()) {
            return rejected("INVALID_ACTOR", null, now);
        }
        if (actor.role() != Actor.Role.ADMIN) return rejected("FORBIDDEN", null, now);
        String trimmed = reason == null ? "" : reason.strip();
        int length = trimmed.codePointCount(0, trimmed.length());
        if (length < 3 || length > 500) return rejected("INVALID_REASON", null, now);
        if (expectedVersion < 0 || expectedVersion > 9007199254740991L) return rejected("INVALID_VERSION", null, now);
        var resource = store.lockResource(request.resourceId());
        if (resource.isEmpty()) return rejected("NOT_FOUND", null, now);
        var state = resource.get();
        if (state.version() != expectedVersion) return rejected("VERSION_CONFLICT", state.version(), now);
        if (state.active() == active) return new CommandOutcome(CommandOutcome.Status.SUCCEEDED, "NO_CHANGE", null, state.version(), null, now);
        long version = store.setResourceActive(request.resourceId(), active);
        store.recordAvailabilityChange(request.resourceId(), version, now);
        return new CommandOutcome(CommandOutcome.Status.SUCCEEDED, "STATUS_CHANGED", null, version, null, now);
    }

    private static CommandOutcome rejected(String reason, Long version, Instant at) {
        return new CommandOutcome(CommandOutcome.Status.REJECTED, reason, null, version, null, at);
    }
}
