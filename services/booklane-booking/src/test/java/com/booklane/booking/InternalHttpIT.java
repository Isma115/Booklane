package com.booklane.booking;

import com.booklane.contracts.WireJson;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

class InternalHttpIT {
    private static final String PASSWORD = "d".repeat(64);
    private static final String INTERNAL_PASSWORD = "1".repeat(64);
    private static final String ALICE = "20000000-0000-4000-8000-000000000001";
    private static final String BRUNO = "20000000-0000-4000-8000-000000000002";
    private static final String ADMIN = "20000000-0000-4000-8000-000000000003";
    private static final String RESOURCE = "10000000-0000-4000-8000-000000000001";
    private static final String BOOKING = "50000000-0000-4000-8000-000000000001";
    private static final String COMMAND = "60000000-0000-4000-8000-000000000001";
    private static final String HASH = "a".repeat(64);

    @Test
    void authenticatesServiceThenActorAndServesOnlyAuthorizedQueries() throws Exception {
        var image = DockerImageName.parse("postgres:18.6-bookworm@sha256:bbf83594798e144bff365a9c191c5d779f181fb3135902064ab8ff11918020fc")
                .asCompatibleSubstituteFor("postgres");
        try (var postgres = new PostgreSQLContainer(image).withDatabaseName("booklane")
                .withUsername("test_owner").withPassword("test_owner_password")) {
            postgres.start();
            try (var db = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 var sql = db.createStatement()) {
                sql.execute("CREATE EXTENSION IF NOT EXISTS btree_gist");
                sql.execute("CREATE ROLE booklane_booking LOGIN PASSWORD '" + PASSWORD + "'");
                sql.execute("CREATE SCHEMA booking AUTHORIZATION booklane_booking");
            }
            try (var app = new SpringApplicationBuilder(BooklaneBookingApplication.class).profiles("test")
                    .run("--spring.autoconfigure.exclude=", "--server.port=0", "--server.address=127.0.0.1",
                         "--spring.datasource.hikari.connection-timeout=1000", "--spring.datasource.hikari.validation-timeout=1000",
                         "--booklane.database-url=" + postgres.getJdbcUrl().split("\\?")[0],
                         "--booklane.database-password=" + PASSWORD);
                 var db = DriverManager.getConnection(postgres.getJdbcUrl(), "booklane_booking", PASSWORD);
                 var sql = db.createStatement();
                 var client = HttpClient.newHttpClient()) {
                sql.execute("""
                        INSERT INTO booking.bookings (id,resource_id,owner_id,title,starts_at,ends_at,status,created_at)
                        VALUES ('50000000-0000-4000-8000-000000000001','10000000-0000-4000-8000-000000000001',
                          '20000000-0000-4000-8000-000000000001','Private meeting','2027-01-02T09:00:00Z',
                          '2027-01-02T10:00:00Z','CONFIRMED','2027-01-01T08:00:00Z')
                        """);
                sql.execute("""
                        INSERT INTO booking.command_results (actor_id,command_id,request_hash,command_type,resource_id,
                          outcome,reason_code,booking_id,resource_version,canonical_request,processed_at)
                        VALUES ('20000000-0000-4000-8000-000000000001','60000000-0000-4000-8000-000000000001',
                          repeat('a',64),'CreateBooking','10000000-0000-4000-8000-000000000001','SUCCEEDED','CONFIRMED',
                          '50000000-0000-4000-8000-000000000001',0,
                          '{"type":"CreateBooking","resourceId":"10000000-0000-4000-8000-000000000001","payload":{"startsAt":"2027-01-02T09:00:00Z","endsAt":"2027-01-02T10:00:00Z","title":"Private meeting"}}',
                          '2027-01-01T08:00:00Z')
                        """);
                sql.execute("""
                        INSERT INTO booking.audit_entries (id,actor_id,command_id,request_hash,action,resource_id,
                          booking_id,outcome,reason_code,reason,occurred_at)
                        VALUES ('70000000-0000-4000-8000-000000000001','20000000-0000-4000-8000-000000000001',
                          '60000000-0000-4000-8000-000000000001',repeat('a',64),'CreateBooking',
                          '10000000-0000-4000-8000-000000000001','50000000-0000-4000-8000-000000000001',
                          'SUCCEEDED','CONFIRMED',NULL,'2027-01-01T08:00:00Z')
                        """);
                int port = ((ServletWebServerApplicationContext) app).getWebServer().getPort();
                String base = "http://127.0.0.1:" + port + "/internal/v1";
                String auth = basic("booklane-internal", INTERNAL_PASSWORD);
                assertThat(send(client, base + "/resources", null, ALICE, "USER").statusCode()).isEqualTo(401);
                assertThat(send(client, base + "/resources", basic("booklane-internal", "wrong"), ADMIN, "ADMIN").statusCode()).isEqualTo(401);
                assertThat(send(client, base + "/resources", auth, ALICE, "ADMIN").statusCode()).isEqualTo(403);
                assertThat(send(client, base + "/resources", auth, null, "USER").statusCode()).isEqualTo(403);
                assertThat(send(client, base + "/resources", auth, "ffffffff-ffff-4fff-8fff-ffffffffffff", "USER").statusCode()).isEqualTo(403);
                assertThat(send(client, base + "/resources", auth, "20000000-0-4000-8000-1", "USER").statusCode()).isEqualTo(403);
                var duplicateRole = HttpRequest.newBuilder(URI.create(base + "/resources"))
                        .header("Authorization", auth).header("X-Actor-Id", ALICE)
                        .header("X-Actor-Role", "USER").header("X-Actor-Role", "ADMIN").GET().build();
                assertThat(client.send(duplicateRole, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);
                assertThat(app.getBeansOfType(org.springframework.security.core.userdetails.UserDetailsService.class)).isEmpty();
                assertThat(send(client, base + "/audit", auth, ALICE, "USER").statusCode()).isEqualTo(403);
                assertThat(send(client, base + "/bookings/" + BOOKING, auth, BRUNO, "USER").statusCode()).isEqualTo(404);
                assertThat(send(client, base + "/operations/" + COMMAND, auth, ADMIN, "ADMIN").statusCode()).isEqualTo(404);
                assertThat(send(client, base + "/operations/" + COMMAND + "?requestHash=" + "b".repeat(64), auth, ALICE, "USER").statusCode()).isEqualTo(409);
                assertThat(send(client, base + "/resources?size=101", auth, ALICE, "USER").statusCode()).isEqualTo(400);
                assertThat(send(client, base + "/resources/" + RESOURCE + "/availability?from=invalid&to=2027-01-03", auth, ALICE, "USER").statusCode()).isEqualTo(400);

                var resources = ok(client, base + "/resources", auth, ALICE, "USER", "ResourcePage");
                assertThat(resources.get("items").size()).isEqualTo(5);
                var availability = ok(client, base + "/resources/" + RESOURCE + "/availability?from=2027-01-02&to=2027-01-03", auth, BRUNO, "USER", "Availability");
                assertThat(availability.get("occupied").size()).isEqualTo(1);
                assertThat(availability.toString()).doesNotContain("Private meeting", ALICE, INTERNAL_PASSWORD);
                ok(client, base + "/bookings/mine?period=ALL", auth, ALICE, "USER", "BookingPage");
                ok(client, base + "/bookings/" + BOOKING, auth, ADMIN, "ADMIN", "Booking");
                ok(client, base + "/operations/" + COMMAND + "?requestHash=" + HASH, auth, ALICE, "USER", "OperationResult");
                ok(client, base + "/audit", auth, ADMIN, "ADMIN", "AuditEntryPage");
                var error = send(client, base + "/bookings/" + BOOKING, auth, BRUNO, "USER");
                writeFixture("ApiError", error.body());
                assertThat(error.body()).doesNotContain(INTERNAL_PASSWORD, "Private meeting");

                var oversized = HttpRequest.newBuilder(URI.create(base + "/bookings/commands"))
                        .header("Authorization", auth).header("X-Actor-Id", ALICE).header("X-Actor-Role", "USER")
                        .POST(HttpRequest.BodyPublishers.ofString("x".repeat(16385))).build();
                assertThat(client.send(oversized, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(413);
                var write = HttpRequest.newBuilder(URI.create(base + "/bookings/commands"))
                        .header("Authorization", auth).header("X-Actor-Id", ALICE).header("X-Actor-Role", "USER")
                        .POST(HttpRequest.BodyPublishers.ofString("{}")).build();
                assertThat(client.send(write, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(405);
                try (var count = sql.executeQuery("SELECT count(*) FROM booking.bookings")) {
                    count.next();
                    assertThat(count.getInt(1)).isEqualTo(1);
                }
                postgres.stop();
                assertThat(send(client, base + "/resources", auth, ALICE, "USER").statusCode()).isEqualTo(503);
            }
        }
    }

    private static String basic(String user, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    private static HttpResponse<String> send(HttpClient client, String url, String auth, String actor, String role) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(url)).GET();
        if (auth != null) request.header("Authorization", auth);
        if (actor != null) request.header("X-Actor-Id", actor);
        if (role != null) request.header("X-Actor-Role", role);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static JsonNode ok(HttpClient client, String url, String auth, String actor, String role, String schema) throws Exception {
        var response = send(client, url, auth, actor, role);
        assertThat(response.statusCode()).as(schema + " HTTP status").isEqualTo(200);
        assertThat(response.body()).doesNotContain(INTERNAL_PASSWORD, PASSWORD);
        writeFixture(schema, response.body());
        return WireJson.read(response.body(), JsonNode.class);
    }

    private static void writeFixture(String name, String body) throws Exception {
        var directory = Path.of("target", "serialized-http-internal");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(name + ".json"), body, StandardCharsets.UTF_8);
    }
}
