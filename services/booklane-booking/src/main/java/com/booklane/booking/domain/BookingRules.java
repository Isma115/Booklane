package com.booklane.booking.domain;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;

public final class BookingRules {
    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
    private static final LocalTime OPEN = LocalTime.of(8, 0);
    private static final LocalTime CLOSE = LocalTime.of(22, 0);
    private final Clock clock;

    public BookingRules(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public BookingDecision validate(String title, Instant startsAt, Instant endsAt) {
        if (title == null) {
            return BookingDecision.INVALID_TITLE;
        }
        String trimmed = title.strip();
        int length = trimmed.codePointCount(0, trimmed.length());
        if (length < 3 || length > 100) {
            return BookingDecision.INVALID_TITLE;
        }
        if (startsAt == null || endsAt == null || !startsAt.isBefore(endsAt)) {
            return BookingDecision.INVALID_INTERVAL;
        }
        Instant now = clock.instant();
        if (!startsAt.isAfter(now)) {
            return BookingDecision.START_NOT_FUTURE;
        }
        var start = startsAt.atZone(ZONE);
        var end = endsAt.atZone(ZONE);
        if (start.toLocalDate().isAfter(now.atZone(ZONE).toLocalDate().plusDays(30))) {
            return BookingDecision.OUTSIDE_HORIZON;
        }
        if (!start.toLocalDate().equals(end.toLocalDate()) || start.toLocalTime().isBefore(OPEN)
                || end.toLocalTime().isAfter(CLOSE)) {
            return BookingDecision.OUTSIDE_OPENING_HOURS;
        }
        if (!onGrid(start.toLocalTime()) || !onGrid(end.toLocalTime())) {
            return BookingDecision.OFF_GRID;
        }
        Duration duration = Duration.between(startsAt, endsAt);
        if (duration.compareTo(Duration.ofMinutes(15)) < 0 || duration.compareTo(Duration.ofHours(4)) > 0) {
            return BookingDecision.INVALID_DURATION;
        }
        return BookingDecision.VALID;
    }

    private static boolean onGrid(LocalTime time) {
        return time.getMinute() % 15 == 0 && time.getSecond() == 0 && time.getNano() == 0;
    }
}
