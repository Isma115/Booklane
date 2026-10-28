package com.booklane.booking.domain;

import java.time.Instant;
import java.util.Objects;

public record TimeSlot(Instant startsAt, Instant endsAt) {
    public TimeSlot {
        Objects.requireNonNull(startsAt, "startsAt");
        Objects.requireNonNull(endsAt, "endsAt");
        if (!startsAt.isBefore(endsAt)) {
            throw new IllegalArgumentException("start must be before end");
        }
    }

    public boolean overlaps(TimeSlot other) {
        Objects.requireNonNull(other, "other");
        return startsAt.isBefore(other.endsAt) && other.startsAt.isBefore(endsAt);
    }
}
