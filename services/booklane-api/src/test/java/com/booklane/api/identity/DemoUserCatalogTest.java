package com.booklane.api.identity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.assertj.core.api.Assertions.*;

class DemoUserCatalogTest {
    @TempDir Path temporary;
    private final Path source = Path.of(System.getProperty("booklane.root"), "infra/demo/users.json");
    private final JsonMapper json = JsonMapper.builder().build();

    @Test void loadsTheSharedCatalogWithoutRetainingUnneededSecrets() {
        var catalog = DemoUserCatalog.load(source);
        assertThat(catalog.size()).isEqualTo(3);
        var alicia = catalog.findById(UUID.fromString("20000000-0000-4000-8000-000000000001")).orElseThrow();
        assertThat(alicia.username()).isEqualTo("alicia");
        assertThat(alicia.role()).isEqualTo(DemoUserCatalog.Role.USER);
        assertThat(catalog.findById(UUID.fromString("20000000-0000-4000-8000-000000000003")).orElseThrow().role())
                .isEqualTo(DemoUserCatalog.Role.ADMIN);
        assertThat(catalog.findById(UUID.randomUUID())).isEmpty();
        assertThat(catalog.toString()).doesNotContain("$2a$");
        var encoder = new BCryptPasswordEncoder(12);
        for (String name : new String[] {"Alicia", "Bruno", "Admin"}) {
            var user = catalog.findByUsername(name.toLowerCase()).orElseThrow();
            assertThat(encoder.matches(name + "Demo!2026", user.passwordHash())).isTrue();
            assertThat(user.toString()).doesNotContain(user.passwordHash());
        }
        assertThat(encoder.matches("wrong-demo-password", alicia.passwordHash())).isFalse();
    }

    @Test void rejectsUnknownRoleDuplicateUuidAndDuplicateUsername() throws Exception {
        for (String mutation : new String[] {"role", "id", "username"}) {
            ObjectNode root = (ObjectNode) json.readTree(Files.readString(source));
            ArrayNode users = (ArrayNode) root.get("users");
            if (mutation.equals("role")) ((ObjectNode) users.get(0)).put("role", "ROOT");
            else ((ObjectNode) users.get(1)).set(mutation, users.get(0).get(mutation));
            Path path = temporary.resolve(mutation + ".json");
            Files.writeString(path, json.writeValueAsString(root));
            assertThatThrownBy(() -> DemoUserCatalog.load(path)).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Invalid demo user catalog").hasNoCause();
        }
    }

    @Test void invalidJsonAndUnexpectedFieldsDoNotLeakInput() throws Exception {
        Path path = temporary.resolve("invalid.json");
        for (String input : new String[] {"{\"schemaVersion\": SENSITIVE_INPUT_MARKER}",
                Files.readString(source).replace("\"schemaVersion\": 1", "\"schemaVersion\": 1, \"password\": \"SENSITIVE_INPUT_MARKER\""),
                Files.readString(source).replace("\"schemaVersion\": 1", "\"schemaVersion\": 1, \"schemaVersion\": 1")}) {
            Files.writeString(path, input);
            assertThatThrownBy(() -> DemoUserCatalog.load(path)).hasMessageNotContaining("SENSITIVE_INPUT_MARKER").hasNoCause();
        }
    }

    @Test void rejectsOversizedMissingAndWronglyStructuredCatalogs() throws Exception {
        Path path = temporary.resolve("too-large.json");
        Files.writeString(path, " ".repeat(65_537));
        assertThatThrownBy(() -> DemoUserCatalog.load(path)).isInstanceOf(IllegalStateException.class).hasNoCause();
        assertThatThrownBy(() -> DemoUserCatalog.load(temporary.resolve("missing.json"))).isInstanceOf(IllegalStateException.class).hasNoCause();
        Files.writeString(path, "{\"schemaVersion\":1,\"users\":[]}");
        assertThatThrownBy(() -> DemoUserCatalog.load(path)).isInstanceOf(IllegalStateException.class).hasNoCause();
    }
}
