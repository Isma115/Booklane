package com.booklane.booking.adapters.persistence.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class CommandResultId implements Serializable {
    public UUID actorId;
    public UUID commandId;

    public CommandResultId() {}

    @Override public boolean equals(Object other) {
        return other instanceof CommandResultId id && Objects.equals(actorId, id.actorId)
                && Objects.equals(commandId, id.commandId);
    }

    @Override public int hashCode() { return Objects.hash(actorId, commandId); }
}
