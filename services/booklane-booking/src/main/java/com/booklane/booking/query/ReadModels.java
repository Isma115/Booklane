package com.booklane.booking.query;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.JsonNode;

public final class ReadModels {
    private ReadModels() {}

    public record Page<T>(List<T> items, int page, int size, long totalElements) {
        public Page { items = List.copyOf(items); }
    }
    public record Resource(UUID id, String code, String name, String type, Integer capacity,
            String description, boolean active, long resourceVersion, String imageUrl) {}
    public record OccupiedInterval(Instant startsAt, Instant endsAt) {}
    public record AvailabilityDay(LocalDate date, Instant opensAt, Instant closesAt, List<Instant> anchors) {
        public AvailabilityDay { anchors = List.copyOf(anchors); }
    }
    public record Availability(UUID resourceId, boolean active, long resourceVersion, String timezone,
            List<AvailabilityDay> days, List<OccupiedInterval> occupied) {
        public Availability { days = List.copyOf(days); occupied = List.copyOf(occupied); }
    }
    public record Booking(UUID id, UUID resourceId, UUID ownerId, String title, Instant startsAt,
            Instant endsAt, String status, Instant createdAt, Instant cancelledAt, long version) {}
    public record Operation(UUID commandId, String requestHash, String outcome, String reasonCode,
            UUID resourceId, UUID bookingId, Long resourceVersion, Instant processedAt,
            JsonNode canonicalRequest, @JsonInclude(JsonInclude.Include.NON_NULL) Boolean alreadyCancelled) {}
    public enum OperationState { FOUND, NOT_OBSERVED, IDEMPOTENCY_CONFLICT }
    public record OperationLookup(OperationState state, Operation result) {}
    public record AuditEntry(UUID id, UUID actorId, UUID commandId, String requestHash, String action,
            UUID resourceId, UUID bookingId, String outcome, String reasonCode, String reason, Instant occurredAt) {}
    public record AuditFilter(UUID resourceId, UUID actorId, String action, String outcome, Instant from, Instant to) {}
}
