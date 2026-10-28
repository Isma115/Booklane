package com.booklane.booking.application;

import com.booklane.booking.application.port.ReservationStore;
import com.booklane.booking.domain.Actor;
import com.booklane.booking.security.DemoIdentityCatalog;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CancelBookingHandler {
    private final CommandProcessor processor;
    private final ReservationStore store;
    private final DemoIdentityCatalog identities;
    private final Clock clock;

    public CancelBookingHandler(CommandProcessor processor, ReservationStore store, DemoIdentityCatalog identities, Clock clock) {
        this.processor = processor;
        this.store = store;
        this.identities = identities;
        this.clock = clock;
    }

    public ProcessingResult cancel(CommandRequest request, Actor actor, UUID bookingId, String reason) {
        if (!"CancelBooking".equals(request.commandType())) throw new IllegalArgumentException("unexpected command type");
        return processor.process(request, ignored -> decide(request, actor, bookingId, reason));
    }

    private CommandOutcome decide(CommandRequest request, Actor actor, UUID bookingId, String reason) {
        Instant current = clock.instant();
        Instant now = current.truncatedTo(ChronoUnit.SECONDS);
        if (actor == null || !request.actorId().equals(actor.id()) || identities.findById(actor.id())
                .filter(identity -> identity.role().name().equals(actor.role().name())).isEmpty()) {
            return rejected("INVALID_ACTOR", now);
        }
        var resource = store.lockResource(request.resourceId());
        if (resource.isEmpty() || bookingId == null) return rejected("NOT_FOUND", now);
        var booking = store.lockBooking(request.resourceId(), bookingId);
        if (booking.isEmpty()) return rejected("NOT_FOUND", now);
        var state = booking.get();
        boolean owns = state.ownerId().equals(actor.id());
        if (!owns && actor.role() != Actor.Role.ADMIN) return rejected("NOT_FOUND", now);
        String trimmed = reason == null ? "" : reason.strip();
        int reasonLength = trimmed.codePointCount(0, trimmed.length());
        if ((!owns && reasonLength < 3) || reasonLength > 500) return rejected("INVALID_REASON", now);
        if ("CANCELLED".equals(state.status())) {
            return new CommandOutcome(CommandOutcome.Status.SUCCEEDED, "ALREADY_CANCELLED", bookingId,
                    resource.get().version(), true, now);
        }
        if (!state.startsAt().isAfter(current)) return rejected("ALREADY_STARTED", now);
        long version = store.cancelBooking(request.resourceId(), bookingId, now);
        store.recordAvailabilityChange(request.resourceId(), version, now);
        return new CommandOutcome(CommandOutcome.Status.SUCCEEDED, "CANCELLED", bookingId, version, false, now);
    }

    private static CommandOutcome rejected(String reason, Instant at) {
        return new CommandOutcome(CommandOutcome.Status.REJECTED, reason, null, null, null, at);
    }
}
