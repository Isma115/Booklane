package com.booklane.booking.query;

import com.booklane.booking.domain.Actor;
import com.booklane.booking.query.ReadModels.*;
import com.booklane.booking.security.DemoIdentityCatalog;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class BookingReadService {
    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
    private final ReadStore store;
    private final DemoIdentityCatalog identities;
    private final Clock clock;

    public BookingReadService(ReadStore store, DemoIdentityCatalog identities, Clock clock) {
        this.store = store;
        this.identities = identities;
        this.clock = clock;
    }

    public Page<Resource> resources(Actor actor, String type, String search, int page, int size, String sort) {
        requireActor(actor);
        page(page, size);
        if (type != null && !Set.of("ROOM", "EQUIPMENT").contains(type)) throw new IllegalArgumentException("invalid resource type");
        if (search != null && search.codePointCount(0, search.length()) > 100) throw new IllegalArgumentException("search too long");
        if (!"name,asc".equals(sort)) throw new IllegalArgumentException("invalid sort");
        return store.resources(type, search, page, size);
    }

    public Optional<Availability> availability(Actor actor, UUID resourceId, LocalDate from, LocalDate to) {
        requireActor(actor);
        Objects.requireNonNull(resourceId, "resourceId");
        if (from == null || to == null || !from.isBefore(to) || ChronoUnit.DAYS.between(from, to) > 31) {
            throw new IllegalArgumentException("invalid availability range");
        }
        var resource = store.resource(resourceId);
        if (resource.isEmpty()) return Optional.empty();
        var occupied = store.occupied(resourceId, from.atStartOfDay(ZONE).toInstant(), to.atStartOfDay(ZONE).toInstant());
        List<AvailabilityDay> days = new ArrayList<>();
        for (LocalDate date = from; date.isBefore(to); date = date.plusDays(1)) {
            var open = date.atTime(8, 0).atZone(ZONE);
            var close = date.atTime(22, 0).atZone(ZONE);
            List<Instant> anchors = new ArrayList<>();
            for (var cursor = open; !cursor.isAfter(close); cursor = cursor.plusMinutes(15)) anchors.add(cursor.toInstant());
            days.add(new AvailabilityDay(date, open.toInstant(), close.toInstant(), anchors));
        }
        var state = resource.get();
        return Optional.of(new Availability(resourceId, state.active(), state.resourceVersion(), ZONE.getId(), days, occupied));
    }

    public Page<Booking> mine(Actor actor, String status, String period, int page, int size, String sort) {
        requireActor(actor);
        page(page, size);
        if (status != null && !Set.of("CONFIRMED", "CANCELLED").contains(status)) throw new IllegalArgumentException("invalid status");
        if (period == null || !Set.of("ALL", "FUTURE", "PAST").contains(period)) throw new IllegalArgumentException("invalid period");
        if (sort == null || !Set.of("startsAt,asc", "startsAt,desc").contains(sort)) throw new IllegalArgumentException("invalid sort");
        return store.mine(actor.id(), status, period, clock.instant(), page, size, sort.endsWith("desc"));
    }

    public Optional<Booking> booking(Actor actor, UUID id) {
        requireActor(actor);
        return store.booking(Objects.requireNonNull(id, "id"), actor.id(), actor.role() == Actor.Role.ADMIN);
    }

    public OperationLookup operation(Actor actor, UUID commandId, String hash) {
        requireActor(actor);
        Objects.requireNonNull(commandId, "commandId");
        if (hash != null && !hash.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("invalid hash");
        var result = store.operation(actor.id(), commandId);
        if (result.isEmpty()) return new OperationLookup(OperationState.NOT_OBSERVED, null);
        if (hash != null && !hash.equals(result.get().requestHash())) return new OperationLookup(OperationState.IDEMPOTENCY_CONFLICT, null);
        return new OperationLookup(OperationState.FOUND, result.get());
    }

    public Page<AuditEntry> audit(Actor actor, AuditFilter filter, int page, int size, String sort) {
        requireActor(actor);
        if (actor.role() != Actor.Role.ADMIN) throw new SecurityException("admin required");
        Objects.requireNonNull(filter, "filter");
        page(page, size);
        if (filter.action() != null && !Set.of("CreateBooking", "CancelBooking", "SetResourceStatus", "IDEMPOTENCY_CONFLICT").contains(filter.action())) throw new IllegalArgumentException("invalid action");
        if (filter.outcome() != null && !Set.of("SUCCEEDED", "REJECTED").contains(filter.outcome())) throw new IllegalArgumentException("invalid outcome");
        if (filter.from() != null && filter.to() != null && !filter.from().isBefore(filter.to())) throw new IllegalArgumentException("invalid audit range");
        if (sort == null || !Set.of("occurredAt,asc", "occurredAt,desc").contains(sort)) throw new IllegalArgumentException("invalid sort");
        return store.audit(filter, page, size, sort.endsWith("desc"));
    }

    private void requireActor(Actor actor) {
        if (actor == null || identities.findById(actor.id())
                .filter(identity -> identity.role().name().equals(actor.role().name())).isEmpty()) {
            throw new SecurityException("invalid actor");
        }
    }

    private static void page(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("invalid page");
    }
}
