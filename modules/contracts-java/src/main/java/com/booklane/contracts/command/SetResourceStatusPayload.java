package com.booklane.contracts.command;

public record SetResourceStatusPayload(boolean active, String reason, long expectedVersion)
        implements CommandPayload {}
