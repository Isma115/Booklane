package com.booklane.booking.query;

import com.booklane.booking.query.ReadModels.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReadStore {
    Page<Resource> resources(String type, String search, int page, int size);
    Optional<Resource> resource(UUID id);
    List<OccupiedInterval> occupied(UUID resourceId, Instant from, Instant to);
    Page<Booking> mine(UUID actorId, String status, String period, Instant now, int page, int size, boolean descending);
    Optional<Booking> booking(UUID id, UUID actorId, boolean admin);
    Optional<Operation> operation(UUID actorId, UUID commandId);
    Page<AuditEntry> audit(AuditFilter filter, int page, int size, boolean descending);
}
