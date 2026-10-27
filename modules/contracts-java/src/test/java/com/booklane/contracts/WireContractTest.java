package com.booklane.contracts;

import com.booklane.contracts.command.*;
import com.booklane.contracts.event.*;
import java.nio.file.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

class WireContractTest {
    private static final Path EXAMPLES = Path.of(System.getProperty("contracts.root"), "events", "examples");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test void allSixWireMessagesRoundTripAndExportForSchemaValidation() throws Exception {
        Path output = Path.of("target", "serialized-messages");
        Files.createDirectories(output);
        for (String name : new String[] {"CreateBooking", "CancelBooking", "SetResourceStatus"}) {
            String source = Files.readString(EXAMPLES.resolve(name + ".json"));
            CommandEnvelope envelope = WireJson.readCommand(source);
            assertEquals(envelope.requestHash(), RequestFingerprint.calculate(envelope.type(), envelope.resourceId(), envelope.payload()));
            String actual = WireJson.write(envelope);
            assertEquals(JSON.readTree(source), JSON.readTree(actual), name);
            Files.writeString(output.resolve(name + ".json"), actual);
        }
        for (var entry : Map.<String, Class<?>>of("CommandCompleted", CommandCompletedEvent.class,
                "ResourceAvailabilityChanged", ResourceAvailabilityChangedEvent.class,
                "FailedMessage", FailedMessage.class).entrySet()) {
            String source = Files.readString(EXAMPLES.resolve(entry.getKey() + ".json"));
            String actual = WireJson.write(WireJson.read(source, entry.getValue()));
            assertEquals(JSON.readTree(source), JSON.readTree(actual), entry.getKey());
            Files.writeString(output.resolve(entry.getKey() + ".json"), actual);
        }
    }

    @Test void absenceAndNullCancelReasonHaveSameFingerprint() throws Exception {
        String source = Files.readString(EXAMPLES.resolve("CancelBooking.json"));
        CommandEnvelope withNull = WireJson.readCommand(source);
        var tree = (tools.jackson.databind.node.ObjectNode) JSON.readTree(source);
        ((tools.jackson.databind.node.ObjectNode) tree.get("payload")).remove("reason");
        CommandEnvelope absent = WireJson.readCommand(tree.toString());
        assertEquals(withNull.payload(), absent.payload());
        assertEquals(withNull.requestHash(), RequestFingerprint.calculate(absent.type(), absent.resourceId(), absent.payload()));
    }

    @Test void unknownFieldsDuplicateKeysAndArbitraryTypeAreRejected() throws Exception {
        String source = Files.readString(EXAMPLES.resolve("CreateBooking.json"));
        assertThrows(RuntimeException.class, () -> WireJson.readCommand(source.replace("\"schemaVersion\": 1", "\"schemaVersion\": 1, \"schemaVersion\": 1")));
        assertThrows(RuntimeException.class, () -> WireJson.readCommand(source.replace("\"schemaVersion\": 1", "\"schemaVersion\": 1, \"unexpected\": 1")));
        assertThrows(RuntimeException.class, () -> WireJson.readCommand(source.replace("CreateBooking", "java.lang.Runtime")));
        assertThrows(RuntimeException.class, () -> WireJson.readCommand(source + " {}"));
    }

    @Test void payloadFieldOrderDoesNotChangeFingerprint() throws Exception {
        var root = (tools.jackson.databind.node.ObjectNode) JSON.readTree(
                Files.readString(EXAMPLES.resolve("CreateBooking.json")));
        var original = root.get("payload");
        var reversed = JSON.createObjectNode();
        reversed.set("title", original.get("title"));
        reversed.set("endsAt", original.get("endsAt"));
        reversed.set("startsAt", original.get("startsAt"));
        root.set("payload", reversed);
        var command = WireJson.readCommand(root.toString());
        assertEquals(command.requestHash(), RequestFingerprint.calculate(command.type(), command.resourceId(), command.payload()));
    }
}
