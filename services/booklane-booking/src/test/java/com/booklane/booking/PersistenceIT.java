package com.booklane.booking;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import com.booklane.booking.application.CommandOutcome;
import com.booklane.booking.application.CommandProcessor;
import com.booklane.booking.application.CommandRequest;
import com.booklane.booking.application.ProcessingResult;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PersistenceIT {
    private static final String PASSWORD = "a".repeat(64);
    private static final String RESOURCE = "10000000-0000-4000-8000-000000000001";
    private static final String OWNER = "20000000-0000-4000-8000-000000000001";
    private static final String ACTOR = "30000000-0000-4000-8000-000000000001";
    private static final String COMMAND = "40000000-0000-4000-8000-000000000001";

    @Test
    void migratesValidatesAndPreservesPostgresConstraintsAcrossRestart() throws Exception {
        var image = DockerImageName.parse("postgres:18.6-bookworm@sha256:bbf83594798e144bff365a9c191c5d779f181fb3135902064ab8ff11918020fc").asCompatibleSubstituteFor("postgres");
        try (var postgres = new PostgreSQLContainer(image).withDatabaseName("booklane")
                .withUsername("test_owner").withPassword("test_owner_password")) {
            postgres.start();
            try (var admin = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
                execute(admin, "CREATE EXTENSION IF NOT EXISTS btree_gist");
                execute(admin, "CREATE ROLE booklane_booking LOGIN PASSWORD '" + PASSWORD + "'");
                execute(admin, "CREATE SCHEMA booking AUTHORIZATION booklane_booking");
            }
            try (var app = start(postgres); var db = connect(postgres)) {
                assertThat(number(db, "SELECT count(*) FROM booking.resources")).isEqualTo(5);
                assertThat(number(db, "SELECT count(*) FROM booking.resources WHERE name = 'Sala Nébula'")).isEqualTo(1);
                execute(db, booking("50000000-0000-4000-8000-000000000001", RESOURCE, "2027-01-01T10:00:00+01:00", "2027-01-01T11:00:00+01:00"));
                execute(db, booking("50000000-0000-4000-8000-000000000002", RESOURCE, "2027-01-01T11:00:00+01:00", "2027-01-01T12:00:00+01:00"));
                assertSqlState(db, booking("50000000-0000-4000-8000-000000000003", RESOURCE, "2027-01-01T10:30:00+01:00", "2027-01-01T11:30:00+01:00"), "23P01");
                assertSqlState(db, booking("50000000-0000-4000-8000-000000000004", "90000000-0000-4000-8000-000000000001", "2027-01-01T10:00:00Z", "2027-01-01T11:00:00Z"), "23503");
                assertSqlState(db, booking("50000000-0000-4000-8000-000000000005", RESOURCE, "2027-01-01T12:00:00Z", "2027-01-01T12:00:00Z"), "23514");
                execute(db, "UPDATE booking.bookings SET status = 'CANCELLED', cancelled_at = now() WHERE id = '50000000-0000-4000-8000-000000000001'");
                execute(db, booking("50000000-0000-4000-8000-000000000006", RESOURCE, "2027-01-01T10:00:00+01:00", "2027-01-01T11:00:00+01:00"));
                String result = "INSERT INTO booking.command_results (actor_id, command_id, request_hash, command_type, resource_id, outcome, reason_code, canonical_request, processed_at) VALUES ('" + ACTOR + "','" + COMMAND + "','" + "a".repeat(64) + "','CreateBooking','90000000-0000-4000-8000-000000000001','REJECTED','NOT_FOUND','{}',now())";
                execute(db, result);
                assertSqlState(db, result, "23505");
                try (var query = db.createStatement().executeQuery("SELECT starts_at FROM booking.bookings WHERE id = '50000000-0000-4000-8000-000000000001'")) {
                    assertThat(query.next()).isTrue();
                    assertThat(query.getObject(1, java.time.OffsetDateTime.class).toInstant()).isEqualTo(Instant.parse("2027-01-01T09:00:00Z"));
                }
                verifyClaims(app.getBean(CommandProcessor.class), db);
            }
            try (var app = start(postgres); var db = connect(postgres)) {
                assertThat(number(db, "SELECT count(*) FROM booking.resources")).isEqualTo(5);
                assertThat(number(db, "SELECT count(*) FROM booking.bookings")).isEqualTo(3);
                assertThat(number(db, "SELECT count(*) FROM booking.command_results")).isEqualTo(2);
                assertThat(number(db, "SELECT count(*) FROM booking.flyway_schema_history WHERE success")).isEqualTo(4);
            }
        }
    }

    private static void verifyClaims(CommandProcessor processor, Connection db) throws Exception {
        UUID actor = UUID.randomUUID();
        UUID command = UUID.randomUUID();
        var first = new CommandRequest(actor, command, "b".repeat(64), "CreateBooking",
                UUID.fromString(RESOURCE), "{}", UUID.randomUUID());
        var changed = new CommandRequest(actor, command, "c".repeat(64), "CreateBooking",
                UUID.fromString("10000000-0000-4000-8000-000000000002"), "{}", UUID.randomUUID());
        var outcome = new CommandOutcome(CommandOutcome.Status.REJECTED, "NOT_FOUND", null, null, null,
                Instant.parse("2027-01-01T08:00:00Z"));
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var secondStarted = new CountDownLatch(1);
        var calls = new AtomicInteger();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var original = executor.submit(() -> processor.process(first, request -> {
                calls.incrementAndGet();
                entered.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("race timed out");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                return outcome;
            }));
            try {
                assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                var duplicate = executor.submit(() -> {
                    secondStarted.countDown();
                    return processor.process(changed, request -> { calls.incrementAndGet(); return outcome; });
                });
                assertThat(secondStarted.await(10, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> duplicate.get(500, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                release.countDown();
                assertThat(original.get(10, TimeUnit.SECONDS).state()).isEqualTo(ProcessingResult.State.PROCESSED);
                assertThat(duplicate.get(10, TimeUnit.SECONDS).state()).isEqualTo(ProcessingResult.State.IDEMPOTENCY_CONFLICT);
            } finally {
                release.countDown();
            }
        }
        assertThat(calls).hasValue(1);
        assertThat(processor.process(first, request -> { throw new AssertionError("replay called handler"); }).outcome()).isEqualTo(outcome);
        assertThat(processor.process(changed, request -> { throw new AssertionError("conflict called handler"); }).state())
                .isEqualTo(ProcessingResult.State.IDEMPOTENCY_CONFLICT);
        assertThat(number(db, "SELECT count(*) FROM booking.audit_entries WHERE command_id = '" + command + "'")).isEqualTo(2);
        assertThat(number(db, "SELECT count(*) FROM booking.outbox_events WHERE payload->>'commandId' = '" + command + "'")).isEqualTo(2);
        assertThat(number(db, "SELECT count(*) FROM booking.outbox_events WHERE payload->>'requestHash' = '" + changed.requestHash() + "' AND payload->>'reasonCode' = 'IDEMPOTENCY_CONFLICT'")).isEqualTo(1);
        assertThat(number(db, "SELECT count(*) FROM booking.command_results WHERE command_id = '" + command + "' AND request_hash = '" + first.requestHash() + "'")).isEqualTo(1);

        var failure = new CommandRequest(actor, UUID.randomUUID(), "d".repeat(64), "CreateBooking",
                UUID.fromString(RESOURCE), "{}", UUID.randomUUID());
        assertThatThrownBy(() -> processor.process(failure, request -> { throw new IllegalStateException("test rollback"); }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(number(db, "SELECT count(*) FROM booking.command_results WHERE command_id = '" + failure.commandId() + "'")).isZero();
        execute(db, "ALTER TABLE booking.outbox_events ADD CONSTRAINT test_reject_outbox CHECK (false) NOT VALID");
        try {
            assertThatThrownBy(() -> processor.process(failure, request -> outcome)).isInstanceOf(RuntimeException.class);
            assertThat(number(db, "SELECT count(*) FROM booking.command_results WHERE command_id = '" + failure.commandId() + "'")).isZero();
            assertThat(number(db, "SELECT count(*) FROM booking.audit_entries WHERE command_id = '" + failure.commandId() + "'")).isZero();
            assertThat(number(db, "SELECT count(*) FROM booking.outbox_events WHERE payload->>'commandId' = '" + failure.commandId() + "'")).isZero();
        } finally {
            execute(db, "ALTER TABLE booking.outbox_events DROP CONSTRAINT test_reject_outbox");
        }
        db.setAutoCommit(false);
        try {
            execute(db, "INSERT INTO booking.command_results (actor_id, command_id, request_hash, command_type, resource_id) VALUES ('"
                    + actor + "','" + failure.commandId() + "','" + failure.requestHash() + "','CreateBooking','" + RESOURCE + "')");
            assertThatThrownBy(db::commit).isInstanceOf(SQLException.class)
                    .extracting(e -> ((SQLException) e).getSQLState()).isEqualTo("23514");
            db.rollback();
        } finally {
            db.setAutoCommit(true);
        }
    }

    private static ConfigurableApplicationContext start(PostgreSQLContainer postgres) {
        return new SpringApplicationBuilder(BooklaneBookingApplication.class).web(WebApplicationType.NONE).profiles("test")
                .run("--spring.autoconfigure.exclude=", "--spring.main.web-application-type=none",
                     "--booklane.database-url=" + postgres.getJdbcUrl().split("\\?")[0],
                     "--booklane.database-password=" + PASSWORD);
    }

    private static Connection connect(PostgreSQLContainer postgres) throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), "booklane_booking", PASSWORD);
    }

    private static String booking(String id, String resource, String start, String end) {
        return "INSERT INTO booking.bookings (id, resource_id, owner_id, title, starts_at, ends_at, status, created_at) VALUES ('"
                + id + "','" + resource + "','" + OWNER + "','Meeting','" + start + "','" + end + "','CONFIRMED',now())";
    }

    private static void execute(Connection db, String sql) throws SQLException {
        try (var statement = db.createStatement()) { statement.execute(sql); }
    }

    private static long number(Connection db, String sql) throws SQLException {
        try (var query = db.createStatement().executeQuery(sql)) { query.next(); return query.getLong(1); }
    }

    private static void assertSqlState(Connection db, String sql, String state) {
        assertThatThrownBy(() -> execute(db, sql)).isInstanceOf(SQLException.class)
                .extracting(e -> ((SQLException) e).getSQLState()).isEqualTo(state);
    }
}
