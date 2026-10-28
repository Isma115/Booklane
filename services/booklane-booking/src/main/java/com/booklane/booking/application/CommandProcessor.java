package com.booklane.booking.application;

import com.booklane.booking.application.port.ReservationStore;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommandProcessor {
    private final ReservationStore store;
    private final Clock clock;

    public CommandProcessor(ReservationStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    @Transactional
    public ProcessingResult process(CommandRequest request, Function<CommandRequest, CommandOutcome> handler) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(handler, "handler");
        if (store.claim(request)) {
            var outcome = Objects.requireNonNull(handler.apply(request), "handler outcome");
            store.complete(request, outcome);
            return new ProcessingResult(ProcessingResult.State.PROCESSED, outcome);
        }
        var existing = store.find(request);
        if (existing.requestHash().equals(request.requestHash())) {
            return new ProcessingResult(ProcessingResult.State.REPLAYED, existing.outcome());
        }
        store.recordConflict(request, clock.instant().truncatedTo(ChronoUnit.SECONDS));
        return new ProcessingResult(ProcessingResult.State.IDEMPOTENCY_CONFLICT, null);
    }
}
