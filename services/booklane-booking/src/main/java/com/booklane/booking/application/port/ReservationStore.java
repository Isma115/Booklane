package com.booklane.booking.application.port;

import com.booklane.booking.application.CommandOutcome;
import com.booklane.booking.application.CommandRequest;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ReservationStore {
    boolean claim(CommandRequest request);
    StoredResult find(CommandRequest request);
    void complete(CommandRequest request, CommandOutcome outcome);
    void recordConflict(CommandRequest request, Instant occurredAt);
    Optional<ResourceState> lockResource(UUID resourceId);
    boolean overlaps(UUID resourceId, Instant startsAt, Instant endsAt);
    long createBooking(UUID bookingId, UUID resourceId, UUID ownerId, String title,
            Instant startsAt, Instant endsAt, Instant createdAt);
    void recordAvailabilityChange(UUID resourceId, long version, Instant occurredAt);
    Optional<BookingState> lockBooking(UUID resourceId, UUID bookingId);
    long cancelBooking(UUID resourceId, UUID bookingId, Instant cancelledAt);
    long setResourceActive(UUID resourceId, boolean active);

    record StoredResult(String requestHash, CommandOutcome outcome) {}
    record ResourceState(boolean active, long version) {}
    record BookingState(UUID ownerId, Instant startsAt, String status) {}
}
