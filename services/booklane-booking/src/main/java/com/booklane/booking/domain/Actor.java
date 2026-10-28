package com.booklane.booking.domain;

import java.util.Objects;
import java.util.UUID;

public record Actor(UUID id, Role role) {
    public Actor {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(role, "role");
    }

    public enum Role { USER, ADMIN }
}
