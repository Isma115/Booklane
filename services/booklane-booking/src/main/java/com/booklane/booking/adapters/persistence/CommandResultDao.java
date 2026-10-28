package com.booklane.booking.adapters.persistence;

import com.booklane.booking.application.CommandOutcome;
import com.booklane.booking.application.CommandRequest;
import com.booklane.booking.application.port.ReservationStore;
import com.booklane.contracts.WireJson;
import com.booklane.contracts.event.CommandCompletedEvent;
import com.booklane.contracts.event.Outcome;
import com.booklane.contracts.event.ResourceAvailabilityChangedEvent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class CommandResultDao implements ReservationStore {
    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public boolean claim(CommandRequest request) {
        return entityManager.createNativeQuery("""
                INSERT INTO booking.command_results (actor_id, command_id, request_hash, command_type, resource_id)
                VALUES (:actor, :command, :hash, :type, :resource)
                ON CONFLICT (actor_id, command_id) DO NOTHING
                """).setParameter("actor", request.actorId()).setParameter("command", request.commandId())
                .setParameter("hash", request.requestHash()).setParameter("type", request.commandType())
                .setParameter("resource", request.resourceId()).executeUpdate() == 1;
    }

    @Override
    public StoredResult find(CommandRequest request) {
        Object[] row = (Object[]) entityManager.createNativeQuery("""
                SELECT request_hash, outcome, reason_code, booking_id, resource_version, already_cancelled, processed_at
                FROM booking.command_results WHERE actor_id = :actor AND command_id = :command
                """).setParameter("actor", request.actorId()).setParameter("command", request.commandId()).getSingleResult();
        return new StoredResult((String) row[0], new CommandOutcome(CommandOutcome.Status.valueOf((String) row[1]),
                (String) row[2], (UUID) row[3], row[4] == null ? null : ((Number) row[4]).longValue(),
                (Boolean) row[5], (Instant) row[6]));
    }

    @Override
    public void complete(CommandRequest request, CommandOutcome outcome) {
        int updated = entityManager.createNativeQuery("""
                UPDATE booking.command_results SET outcome = :outcome, reason_code = :reason,
                    booking_id = :booking, resource_version = :version, already_cancelled = :cancelled,
                    canonical_request = CAST(:canonical AS jsonb), processed_at = :processed
                WHERE actor_id = :actor AND command_id = :command AND outcome IS NULL
                """).setParameter("outcome", outcome.status().name()).setParameter("reason", outcome.reasonCode())
                .setParameter("booking", outcome.bookingId()).setParameter("version", outcome.resourceVersion())
                .setParameter("cancelled", outcome.alreadyCancelled()).setParameter("canonical", request.canonicalRequest())
                .setParameter("processed", outcome.processedAt()).setParameter("actor", request.actorId())
                .setParameter("command", request.commandId()).executeUpdate();
        if (updated != 1) throw new IllegalStateException("claim was not available for completion");
        insertAudit(request, request.commandType(), outcome.status().name(), outcome.reasonCode(),
                outcome.bookingId(), outcome.processedAt());
        insertEvent(request, outcome.status(), outcome.reasonCode(), outcome.bookingId(), outcome.resourceVersion(), outcome.processedAt());
    }

    @Override
    public void recordConflict(CommandRequest request, Instant occurredAt) {
        if (insertAudit(request, "IDEMPOTENCY_CONFLICT", "REJECTED", "IDEMPOTENCY_CONFLICT", null, occurredAt)) {
            insertEvent(request, CommandOutcome.Status.REJECTED, "IDEMPOTENCY_CONFLICT", null, null, occurredAt);
        }
    }

    @Override
    public Optional<ResourceState> lockResource(UUID resourceId) {
        var rows = entityManager.createNativeQuery("SELECT active, version FROM booking.resources WHERE id = :id FOR UPDATE")
                .setParameter("id", resourceId).getResultList();
        if (rows.isEmpty()) return Optional.empty();
        Object[] row = (Object[]) rows.getFirst();
        return Optional.of(new ResourceState((Boolean) row[0], ((Number) row[1]).longValue()));
    }

    @Override
    public boolean overlaps(UUID resourceId, Instant startsAt, Instant endsAt) {
        Number count = (Number) entityManager.createNativeQuery("""
                SELECT count(*) FROM booking.bookings WHERE resource_id = :resource AND status = 'CONFIRMED'
                AND starts_at < :ends AND ends_at > :starts
                """).setParameter("resource", resourceId).setParameter("starts", startsAt)
                .setParameter("ends", endsAt).getSingleResult();
        return count.longValue() != 0;
    }

    @Override
    public long createBooking(UUID bookingId, UUID resourceId, UUID ownerId, String title,
            Instant startsAt, Instant endsAt, Instant createdAt) {
        entityManager.createNativeQuery("""
                INSERT INTO booking.bookings (id, resource_id, owner_id, title, starts_at, ends_at, status, created_at)
                VALUES (:id, :resource, :owner, :title, :starts, :ends, 'CONFIRMED', :created)
                """).setParameter("id", bookingId).setParameter("resource", resourceId).setParameter("owner", ownerId)
                .setParameter("title", title).setParameter("starts", startsAt).setParameter("ends", endsAt)
                .setParameter("created", createdAt).executeUpdate();
        Number version = (Number) entityManager.createNativeQuery("""
                UPDATE booking.resources SET version = version + 1 WHERE id = :id AND version < 9007199254740991
                RETURNING version
                """).setParameter("id", resourceId).getSingleResult();
        return version.longValue();
    }

    @Override
    public void recordAvailabilityChange(UUID resourceId, long version, Instant occurredAt) {
        UUID eventId = UUID.randomUUID();
        var event = new ResourceAvailabilityChangedEvent(1, "ResourceAvailabilityChanged", eventId, resourceId, version, occurredAt);
        entityManager.createNativeQuery("""
                INSERT INTO booking.outbox_events (id, aggregate_id, event_type, payload, created_at)
                VALUES (:id, :aggregate, 'ResourceAvailabilityChanged', CAST(:payload AS jsonb), :created)
                """).setParameter("id", eventId).setParameter("aggregate", resourceId)
                .setParameter("payload", WireJson.write(event)).setParameter("created", occurredAt).executeUpdate();
    }

    @Override
    public Optional<BookingState> lockBooking(UUID resourceId, UUID bookingId) {
        var rows = entityManager.createNativeQuery("""
                SELECT owner_id, starts_at, status FROM booking.bookings
                WHERE resource_id = :resource AND id = :id FOR UPDATE
                """).setParameter("resource", resourceId).setParameter("id", bookingId).getResultList();
        if (rows.isEmpty()) return Optional.empty();
        Object[] row = (Object[]) rows.getFirst();
        return Optional.of(new BookingState((UUID) row[0], (Instant) row[1], (String) row[2]));
    }

    @Override
    public long cancelBooking(UUID resourceId, UUID bookingId, Instant cancelledAt) {
        int updated = entityManager.createNativeQuery("""
                UPDATE booking.bookings SET status = 'CANCELLED', cancelled_at = :at, version = version + 1
                WHERE id = :id AND resource_id = :resource AND status = 'CONFIRMED'
                """).setParameter("at", cancelledAt).setParameter("id", bookingId)
                .setParameter("resource", resourceId).executeUpdate();
        if (updated != 1) throw new IllegalStateException("booking was not available for cancellation");
        Number version = (Number) entityManager.createNativeQuery("""
                UPDATE booking.resources SET version = version + 1 WHERE id = :id AND version < 9007199254740991
                RETURNING version
                """).setParameter("id", resourceId).getSingleResult();
        return version.longValue();
    }

    private boolean insertAudit(CommandRequest request, String action, String outcome, String reason, UUID booking, Instant at) {
        return entityManager.createNativeQuery("""
                INSERT INTO booking.audit_entries (id, actor_id, command_id, request_hash, action,
                    resource_id, booking_id, outcome, reason, occurred_at)
                VALUES (:id, :actor, :command, :hash, :action, :resource, :booking, :outcome,
                    CASE WHEN :action = 'CancelBooking' THEN COALESCE(CAST(:canonical AS jsonb)->'payload'->>'reason', :reason)
                         ELSE :reason END, :at)
                ON CONFLICT (actor_id, command_id, request_hash, action) DO NOTHING
                """).setParameter("id", UUID.randomUUID()).setParameter("actor", request.actorId())
                .setParameter("command", request.commandId()).setParameter("hash", request.requestHash())
                .setParameter("action", action).setParameter("resource", request.resourceId()).setParameter("booking", booking)
                .setParameter("outcome", outcome).setParameter("reason", reason).setParameter("canonical", request.canonicalRequest())
                .setParameter("at", at).executeUpdate() == 1;
    }

    private void insertEvent(CommandRequest request, CommandOutcome.Status status, String reason,
            UUID booking, Long version, Instant at) {
        UUID eventId = UUID.randomUUID();
        var event = new CommandCompletedEvent(1, "CommandCompleted", eventId, request.commandId(), request.actorId(),
                request.requestHash(), Outcome.valueOf(status.name()), reason, booking, request.resourceId(), version, at, request.traceId());
        entityManager.createNativeQuery("""
                INSERT INTO booking.outbox_events (id, aggregate_id, event_type, payload, created_at)
                VALUES (:id, :aggregate, 'CommandCompleted', CAST(:payload AS jsonb), :created)
                """).setParameter("id", eventId).setParameter("aggregate", request.resourceId())
                .setParameter("payload", WireJson.write(event)).setParameter("created", at).executeUpdate();
    }
}
