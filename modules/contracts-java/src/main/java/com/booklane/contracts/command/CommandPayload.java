package com.booklane.contracts.command;

public sealed interface CommandPayload permits CreateBookingPayload, CancelBookingPayload, SetResourceStatusPayload {}
