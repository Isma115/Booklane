package com.booklane.booking.application;

public record ProcessingResult(State state, CommandOutcome outcome) {
    public enum State { PROCESSED, REPLAYED, IDEMPOTENCY_CONFLICT }
}
