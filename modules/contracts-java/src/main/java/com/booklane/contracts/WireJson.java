package com.booklane.contracts;

import com.booklane.contracts.command.*;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

/** Explicit allow-list of payload types; never resolves class names from a message. Validate schema first. */
public final class WireJson {
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    private WireJson() {}

    public static String write(Object value) { return JSON.writeValueAsString(value); }

    public static <T> T read(String value, Class<T> type) { return JSON.readValue(value, type); }

    public static CommandEnvelope readCommand(String value) {
        JsonNode root = JSON.readTree(value);
        CommandType type = CommandType.valueOf(root.required("type").asString());
        Class<? extends CommandPayload> payloadType = switch (type) {
            case CreateBooking -> CreateBookingPayload.class;
            case CancelBooking -> CancelBookingPayload.class;
            case SetResourceStatus -> SetResourceStatusPayload.class;
        };
        CommandPayload payload = JSON.treeToValue(root.required("payload"), payloadType);
        var copy = ((tools.jackson.databind.node.ObjectNode) root).deepCopy();
        copy.remove("payload");
        Header h = JSON.treeToValue(copy, Header.class);
        return new CommandEnvelope(h.schemaVersion(), h.messageId(), h.commandId(), h.type(),
                h.resourceId(), h.actorId(), h.actorRole(), h.requestHash(), h.issuedAt(), h.traceId(), payload);
    }

    private record Header(int schemaVersion, java.util.UUID messageId, java.util.UUID commandId,
            CommandType type, java.util.UUID resourceId, java.util.UUID actorId, ActorRole actorRole,
            String requestHash, java.time.Instant issuedAt, java.util.UUID traceId) {}
}
