package com.booklane.contracts;

import com.booklane.contracts.command.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import tools.jackson.databind.json.JsonMapper;

/** Wire normalization only: no availability, permission or booking rules. */
public final class RequestFingerprint {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private RequestFingerprint() {}

    public static CommandPayload normalize(CommandPayload payload) {
        Objects.requireNonNull(payload, "payload");
        return switch (payload) {
            case CreateBookingPayload p -> new CreateBookingPayload(
                    instant(p.startsAt()), instant(p.endsAt()), Objects.requireNonNull(p.title(), "title").strip());
            case CancelBookingPayload p -> new CancelBookingPayload(
                    Objects.requireNonNull(p.bookingId(), "bookingId"), text(p.reason()));
            case SetResourceStatusPayload p -> new SetResourceStatusPayload(
                    p.active(), Objects.requireNonNull(p.reason(), "reason").strip(), p.expectedVersion());
        };
    }

    public static String canonicalJson(CommandType type, UUID resourceId, CommandPayload payload) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(resourceId, "resourceId");
        CommandPayload normalized = normalize(payload);
        Map<String, Object> body = new LinkedHashMap<>();
        switch (normalized) {
            case CreateBookingPayload p -> {
                requireType(type, CommandType.CreateBooking);
                body.put("startsAt", p.startsAt().toInstant().toString());
                body.put("endsAt", p.endsAt().toInstant().toString());
                body.put("title", p.title());
            }
            case CancelBookingPayload p -> {
                requireType(type, CommandType.CancelBooking);
                body.put("bookingId", p.bookingId().toString());
                body.put("reason", p.reason());
            }
            case SetResourceStatusPayload p -> {
                requireType(type, CommandType.SetResourceStatus);
                body.put("active", p.active());
                body.put("reason", p.reason());
                body.put("expectedVersion", p.expectedVersion());
            }
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("type", type.name());
        root.put("resourceId", resourceId.toString());
        root.put("payload", body);
        return JSON.writeValueAsString(root);
    }

    public static String calculate(CommandType type, UUID resourceId, CommandPayload payload) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonicalJson(type, resourceId, payload).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static OffsetDateTime instant(OffsetDateTime value) {
        Objects.requireNonNull(value, "timestamp");
        if (value.getNano() != 0) throw new IllegalArgumentException("Subsecond precision is not supported");
        return value.toInstant().atOffset(java.time.ZoneOffset.UTC);
    }

    private static String text(String value) { return value == null ? null : value.strip(); }

    private static void requireType(CommandType actual, CommandType expected) {
        if (actual != expected) throw new IllegalArgumentException("type/payload mismatch");
    }
}
