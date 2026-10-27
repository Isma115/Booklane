package com.booklane.contracts.command;

import java.util.UUID;

public record CancelBookingPayload(UUID bookingId, String reason) implements CommandPayload {}
