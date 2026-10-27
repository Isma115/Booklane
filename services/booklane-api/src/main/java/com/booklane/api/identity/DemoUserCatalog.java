package com.booklane.api.identity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** One startup read of the shared demo catalog; immutable in memory. */
public final class DemoUserCatalog {
    public enum Role { USER, ADMIN }
    public record DemoUser(UUID id, String username, Role role, String passwordHash) {
        @Override public String toString() { return "DemoUser[id=" + id + ", role=" + role + "]"; }
    }

    private final Map<UUID, DemoUser> byId;
    private final Map<String, DemoUser> byUsername;

    private DemoUserCatalog(Map<UUID, DemoUser> users, Map<String, DemoUser> names) {
        byId = Map.copyOf(users);
        byUsername = Map.copyOf(names);
    }

    public static DemoUserCatalog load(Path path) {
        try (var input = Files.newInputStream(path)) {
            byte[] data = input.readNBytes(65_537);
            if (data.length > 65_536) throw invalid();
            var json = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
            JsonNode root = json.readTree(data);
            if (!root.isObject() || !Set.copyOf(root.propertyNames()).equals(Set.of("schemaVersion", "users"))
                    || !"1".equals(root.required("schemaVersion").toString())) throw invalid();
            JsonNode users = root.required("users");
            if (!users.isArray() || users.size() != 3) throw invalid();
            Map<UUID, DemoUser> ids = new HashMap<>();
            Map<String, DemoUser> names = new HashMap<>();
            int admins = 0;
            for (JsonNode user : users) {
                if (!user.isObject() || !Set.copyOf(user.propertyNames())
                        .equals(Set.of("id", "username", "role", "passwordHash"))) throw invalid();
                String rawId = field(user, "id");
                UUID id = UUID.fromString(rawId);
                if (!id.toString().equals(rawId)) throw invalid();
                String username = field(user, "username");
                if (!username.matches("[a-z][a-z0-9-]{2,31}")) throw invalid();
                Role role = Role.valueOf(field(user, "role"));
                if (role == Role.ADMIN) admins++;
                String hash = field(user, "passwordHash");
                if (!hash.matches("\\$2[aby]\\$12\\$[./A-Za-z0-9]{53}")) throw invalid();
                var entry = new DemoUser(id, username, role, hash);
                if (ids.putIfAbsent(id, entry) != null || names.putIfAbsent(username, entry) != null) throw invalid();
            }
            if (admins != 1) throw invalid();
            return new DemoUserCatalog(ids, names);
        } catch (Exception ex) {
            // Do not attach parser errors: they can quote input, including password hashes.
            throw invalid();
        }
    }

    private static String field(JsonNode node, String name) {
        JsonNode value = node.required(name);
        if (!value.isString()) throw invalid();
        return value.asString();
    }

    private static IllegalStateException invalid() {
        return new IllegalStateException("Invalid demo user catalog: schema, identities, roles or format");
    }

    public Optional<DemoUser> findById(UUID id) { return Optional.ofNullable(byId.get(id)); }
    public Optional<DemoUser> findByUsername(String username) { return Optional.ofNullable(byUsername.get(username)); }
    public int size() { return byId.size(); }
    @Override public String toString() { return "DemoUserCatalog[size=" + size() + "]"; }
}
