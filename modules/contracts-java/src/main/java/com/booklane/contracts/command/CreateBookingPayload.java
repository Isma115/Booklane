package com.booklane.contracts.command;

import java.time.OffsetDateTime;

public record CreateBookingPayload(OffsetDateTime startsAt, OffsetDateTime endsAt, String title)
        implements CommandPayload {}
