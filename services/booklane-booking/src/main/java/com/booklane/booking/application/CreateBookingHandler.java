package com.booklane.booking.application;

import com.booklane.booking.application.port.ReservationStore;
import com.booklane.booking.domain.Actor;
import com.booklane.booking.domain.BookingDecision;
import com.booklane.booking.domain.BookingRules;
import com.booklane.booking.security.DemoIdentityCatalog;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CreateBookingHandler {
    private final CommandProcessor processor;
    private final ReservationStore store;
    private final DemoIdentityCatalog identities;
    private final Clock clock;

    public CreateBookingHandler(CommandProcessor processor, ReservationStore store, DemoIdentityCatalog identities, Clock clock) {
        this.processor = processor;
        this.store = store;
        this.identities = identities;
        this.clock = clock;
    }

    public ProcessingResult create(CommandRequest request, Actor actor, String title, Instant startsAt, Instant endsAt) {
        if (!"CreateBooking".equals(request.commandType())) throw new IllegalArgumentException("unexpected command type");
        try {
            return processor.process(request, ignored -> decide(request, actor, title, startsAt, endsAt));
        } catch (RuntimeException error) {
            if (!isExclusionViolation(error)) throw error;
            return processor.process(request, ignored -> decide(request, actor, title, startsAt, endsAt));
        }
    }

    private CommandOutcome decide(CommandRequest request, Actor actor, String title, Instant startsAt, Instant endsAt) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        if (actor == null || !request.actorId().equals(actor.id()) || identities.findById(actor.id())
                .filter(identity -> identity.role().name().equals(actor.role().name())).isEmpty()) {
            return rejected("INVALID_ACTOR", null, now);
        }
        var resource = store.lockResource(request.resourceId());
        if (resource.isEmpty()) return rejected("NOT_FOUND", null, now);
        var state = resource.get();
        if (!state.active()) return rejected("RESOURCE_INACTIVE", state.version(), now);
        BookingDecision decision = new BookingRules(clock).validate(title, startsAt, endsAt);
        if (decision != BookingDecision.VALID) return rejected(decision.name(), state.version(), now);
        if (store.overlaps(request.resourceId(), startsAt, endsAt)) return rejected("OVERLAP", state.version(), now);
        UUID bookingId = UUID.randomUUID();
        long version = store.createBooking(bookingId, request.resourceId(), actor.id(), title.strip(), startsAt, endsAt, now);
        store.recordAvailabilityChange(request.resourceId(), version, now);
        return new CommandOutcome(CommandOutcome.Status.SUCCEEDED, "CONFIRMED", bookingId, version, null, now);
    }

    private static CommandOutcome rejected(String reason, Long version, Instant at) {
        return new CommandOutcome(CommandOutcome.Status.REJECTED, reason, null, version, null, at);
    }

    private static boolean isExclusionViolation(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof SQLException sql && "23P01".equals(sql.getSQLState())) return true;
        }
        return false;
    }
}
