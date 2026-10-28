package com.booklane.booking.domain;

public enum BookingDecision {
    VALID,
    INVALID_TITLE,
    INVALID_INTERVAL,
    START_NOT_FUTURE,
    OUTSIDE_HORIZON,
    OUTSIDE_OPENING_HOURS,
    OFF_GRID,
    INVALID_DURATION
}
