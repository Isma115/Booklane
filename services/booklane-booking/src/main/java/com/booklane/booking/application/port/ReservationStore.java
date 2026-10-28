package com.booklane.booking.application.port;

import com.booklane.booking.application.CommandOutcome;
import com.booklane.booking.application.CommandRequest;
import java.time.Instant;

public interface ReservationStore {
    boolean claim(CommandRequest request);
    StoredResult find(CommandRequest request);
    void complete(CommandRequest request, CommandOutcome outcome);
    void recordConflict(CommandRequest request, Instant occurredAt);

    record StoredResult(String requestHash, CommandOutcome outcome) {}
}
