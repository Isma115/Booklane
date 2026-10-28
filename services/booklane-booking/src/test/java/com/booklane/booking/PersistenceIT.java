package com.booklane.booking;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
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
import com.booklane.booking.application.CreateBookingHandler;
import com.booklane.booking.application.CancelBookingHandler;
import com.booklane.booking.domain.Actor;
import com.booklane.contracts.RequestFingerprint;
import com.booklane.contracts.command.CommandType;
import com.booklane.contracts.command.CreateBookingPayload;
import com.booklane.contracts.command.CancelBookingPayload;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
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
                verifyCreation(app.getBean(CreateBookingHandler.class), db);
                verifyCancellation(app.getBean(CancelBookingHandler.class), db);
            }
            try (var app = start(postgres); var db = connect(postgres)) {
                assertThat(number(db, "SELECT count(*) FROM booking.resources")).isEqualTo(5);
                assertThat(number(db, "SELECT count(*) FROM booking.bookings")).isEqualTo(7);
                assertThat(number(db, "SELECT count(*) FROM booking.command_results")).isEqualTo(15);
                assertThat(number(db, "SELECT count(*) FROM booking.flyway_schema_history WHERE success")).isEqualTo(4);
            }
        }
    }

    private static void verifyCancellation(CancelBookingHandler handler, Connection db) throws Exception {
        UUID resource = UUID.fromString("10000000-0000-4000-8000-000000000002");
        var alicia = new Actor(UUID.fromString(OWNER), Actor.Role.USER);
        var bruno = new Actor(UUID.fromString("20000000-0000-4000-8000-000000000002"), Actor.Role.USER);
        var admin = new Actor(UUID.fromString("20000000-0000-4000-8000-000000000003"), Actor.Role.ADMIN);
        UUID own = uuid(db, "SELECT id FROM booking.bookings WHERE resource_id = '" + resource + "' AND starts_at = '2027-01-02T10:00:00Z'");
        UUID other = uuid(db, "SELECT id FROM booking.bookings WHERE resource_id = '" + resource + "' AND starts_at = '2027-01-02T11:00:00Z'");
        var unauthorized = cancelRequest(bruno, resource, own, null);
        var denial = handler.cancel(unauthorized, bruno, own, null).outcome();
        assertThat(denial.reasonCode()).isEqualTo("NOT_FOUND");
        assertThat(denial.bookingId()).isNull();
        assertThat(denial.resourceVersion()).isNull();
        UUID wrongResource = UUID.fromString(RESOURCE);
        var wrong = cancelRequest(alicia, wrongResource, own, null);
        assertThat(handler.cancel(wrong, alicia, own, null).outcome().reasonCode()).isEqualTo("NOT_FOUND");

        var cancel = cancelRequest(alicia, resource, own, null);
        var done = handler.cancel(cancel, alicia, own, null).outcome();
        assertThat(done.status()).isEqualTo(CommandOutcome.Status.SUCCEEDED);
        assertThat(done.alreadyCancelled()).isFalse();
        assertThat(done.resourceVersion()).isEqualTo(4);
        assertThat(number(db, "SELECT version FROM booking.bookings WHERE id = '" + own + "'")).isEqualTo(1);
        assertThat(number(db, "SELECT count(*) FROM booking.bookings WHERE id = '" + own + "' AND status = 'CONFIRMED'")).isZero();
        var repeat = cancelRequest(alicia, resource, own, null);
        var repeated = handler.cancel(repeat, alicia, own, null).outcome();
        assertThat(repeated.alreadyCancelled()).isTrue();
        assertThat(repeated.resourceVersion()).isEqualTo(4);
        assertThat(number(db, "SELECT version FROM booking.bookings WHERE id = '" + own + "'")).isEqualTo(1);
        assertThat(number(db, "SELECT count(*) FROM booking.outbox_events WHERE aggregate_id = '" + resource
                + "' AND event_type = 'ResourceAvailabilityChanged' AND payload->>'resourceVersion' = '4'")).isEqualTo(1);

        var badReason = cancelRequest(admin, resource, other, " ");
        assertThat(handler.cancel(badReason, admin, other, " ").outcome().reasonCode()).isEqualTo("INVALID_REASON");
        var adminCancel = cancelRequest(admin, resource, other, "Mantenimiento");
        assertThat(handler.cancel(adminCancel, admin, other, "Mantenimiento").outcome().resourceVersion()).isEqualTo(5);
        assertThat(number(db, "SELECT count(*) FROM booking.audit_entries WHERE command_id = '" + adminCancel.commandId()
                + "' AND reason = 'Mantenimiento'")).isEqualTo(1);
        assertThat(number(db, "SELECT version FROM booking.resources WHERE id = '" + resource + "'")).isEqualTo(5);

        UUID started = UUID.randomUUID();
        execute(db, booking(started.toString(), RESOURCE, "2027-01-01T08:00:00Z", "2027-01-01T09:00:00Z"));
        var tooLate = cancelRequest(alicia, UUID.fromString(RESOURCE), started, null);
        assertThat(handler.cancel(tooLate, alicia, started, null).outcome().reasonCode()).isEqualTo("ALREADY_STARTED");
        assertThat(number(db, "SELECT count(*) FROM booking.bookings WHERE id = '" + started + "' AND status = 'CONFIRMED'")).isEqualTo(1);

        UUID remaining = uuid(db, "SELECT id FROM booking.bookings WHERE resource_id = '" + resource + "' AND status = 'CONFIRMED'");
        var failed = cancelRequest(admin, resource, remaining, "Mantenimiento");
        execute(db, "ALTER TABLE booking.outbox_events ADD CONSTRAINT test_reject_cancel CHECK (false) NOT VALID");
        try {
            assertThatThrownBy(() -> handler.cancel(failed, admin, remaining, "Mantenimiento")).isInstanceOf(RuntimeException.class);
            assertThat(number(db, "SELECT version FROM booking.resources WHERE id = '" + resource + "'")).isEqualTo(5);
            assertThat(number(db, "SELECT count(*) FROM booking.bookings WHERE id = '" + remaining + "' AND status = 'CONFIRMED' AND version = 0")).isEqualTo(1);
            assertThat(number(db, "SELECT count(*) FROM booking.command_results WHERE command_id = '" + failed.commandId() + "'")).isZero();
        } finally {
            execute(db, "ALTER TABLE booking.outbox_events DROP CONSTRAINT test_reject_cancel");
        }
    }

    private static CommandRequest cancelRequest(Actor actor, UUID resource, UUID booking, String reason) {
        var payload = new CancelBookingPayload(booking, reason);
        return new CommandRequest(actor.id(), UUID.randomUUID(), RequestFingerprint.calculate(CommandType.CancelBooking, resource, payload),
                "CancelBooking", resource, RequestFingerprint.canonicalJson(CommandType.CancelBooking, resource, payload), UUID.randomUUID());
    }

    private static UUID uuid(Connection db, String sql) throws SQLException {
        try (var query = db.createStatement().executeQuery(sql)) { query.next(); return query.getObject(1, UUID.class); }
    }

    private static void verifyCreation(CreateBookingHandler handler, Connection db) throws Exception {
        UUID resource = UUID.fromString("10000000-0000-4000-8000-000000000002");
        var alicia = new Actor(UUID.fromString(OWNER), Actor.Role.USER);
        var bruno = new Actor(UUID.fromString("20000000-0000-4000-8000-000000000002"), Actor.Role.USER);
        Instant start = Instant.parse("2027-01-02T09:00:00Z");
        Instant end = Instant.parse("2027-01-02T10:00:00Z");
        var first = createRequest(alicia, resource, start, end);
        var second = createRequest(bruno, resource, start, end);
        var gate = new CountDownLatch(1);
        ProcessingResult one;
        ProcessingResult two;
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> { gate.await(); return handler.create(first, alicia, "Reunión", start, end); });
            var b = executor.submit(() -> { gate.await(); return handler.create(second, bruno, "Reunión", start, end); });
            gate.countDown();
            one = a.get(15, TimeUnit.SECONDS);
            two = b.get(15, TimeUnit.SECONDS);
        }
        assertThat(java.util.List.of(one.outcome().status(), two.outcome().status()))
                .containsExactlyInAnyOrder(CommandOutcome.Status.SUCCEEDED, CommandOutcome.Status.REJECTED);
        var winner = one.outcome().status() == CommandOutcome.Status.SUCCEEDED ? one : two;
        var winnerRequest = one.outcome().status() == CommandOutcome.Status.SUCCEEDED ? first : second;
        var winnerActor = one.outcome().status() == CommandOutcome.Status.SUCCEEDED ? alicia : bruno;
        var loser = one.outcome().status() == CommandOutcome.Status.REJECTED ? one : two;
        assertThat(loser.outcome().reasonCode()).isEqualTo("OVERLAP");
        assertThat(winner.outcome().resourceVersion()).isEqualTo(1);
        assertThat(number(db, "SELECT count(*) FROM booking.bookings WHERE resource_id = '" + resource + "'")).isEqualTo(1);
        assertThat(number(db, "SELECT version FROM booking.resources WHERE id = '" + resource + "'")).isEqualTo(1);
        assertThat(number(db, "SELECT count(*) FROM booking.outbox_events WHERE aggregate_id = '" + resource
                + "' AND payload->>'resourceVersion' = '1' AND event_type IN ('CommandCompleted','ResourceAvailabilityChanged')")).isEqualTo(3);
        var replay = handler.create(winnerRequest, winnerActor, "invalid", Instant.EPOCH, Instant.EPOCH);
        assertThat(replay.state()).isEqualTo(ProcessingResult.State.REPLAYED);
        assertThat(replay.outcome()).isEqualTo(winner.outcome());

        var adjacent = createRequest(alicia, resource, end, end.plusSeconds(3600));
        assertThat(handler.create(adjacent, alicia, "Reunión", end, end.plusSeconds(3600)).outcome().resourceVersion()).isEqualTo(2);
        UUID inactive = UUID.fromString("10000000-0000-4000-8000-000000000003");
        execute(db, "UPDATE booking.resources SET active = false WHERE id = '" + inactive + "'");
        var disabled = createRequest(alicia, inactive, start, end);
        assertThat(handler.create(disabled, alicia, "Reunión", start, end).outcome().reasonCode()).isEqualTo("RESOURCE_INACTIVE");
        assertThat(number(db, "SELECT version FROM booking.resources WHERE id = '" + inactive + "'")).isZero();

        var wrongRole = new Actor(alicia.id(), Actor.Role.ADMIN);
        var invalidActor = createRequest(wrongRole, resource, end.plusSeconds(3600), end.plusSeconds(7200));
        assertThat(handler.create(invalidActor, wrongRole, "Reunión", end.plusSeconds(3600), end.plusSeconds(7200))
                .outcome().reasonCode()).isEqualTo("INVALID_ACTOR");
        assertThat(number(db, "SELECT version FROM booking.resources WHERE id = '" + resource + "'")).isEqualTo(2);

        execute(db, "CREATE SEQUENCE booking.test_retry_sequence");
        execute(db, "CREATE FUNCTION booking.test_retry() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF nextval('booking.test_retry_sequence') = 1 THEN RAISE EXCEPTION 'test exclusion' USING ERRCODE = '23P01'; END IF; RETURN NEW; END; $$");
        execute(db, "CREATE TRIGGER test_retry BEFORE INSERT ON booking.bookings FOR EACH ROW EXECUTE FUNCTION booking.test_retry()");
        try {
            var retry = createRequest(alicia, resource, end.plusSeconds(3600), end.plusSeconds(7200));
            var retried = handler.create(retry, alicia, "Reunión", end.plusSeconds(3600), end.plusSeconds(7200));
            assertThat(retried.outcome().status()).isEqualTo(CommandOutcome.Status.SUCCEEDED);
            assertThat(retried.outcome().resourceVersion()).isEqualTo(3);
            assertThat(number(db, "SELECT last_value FROM booking.test_retry_sequence")).isEqualTo(2);
            assertThat(number(db, "SELECT count(*) FROM booking.command_results WHERE command_id = '" + retry.commandId() + "'")).isEqualTo(1);
        } finally {
            execute(db, "DROP TRIGGER test_retry ON booking.bookings");
            execute(db, "DROP FUNCTION booking.test_retry()");
            execute(db, "DROP SEQUENCE booking.test_retry_sequence");
        }

        var failed = createRequest(alicia, resource, end.plusSeconds(7200), end.plusSeconds(10800));
        execute(db, "ALTER TABLE booking.outbox_events ADD CONSTRAINT test_reject_creation CHECK (false) NOT VALID");
        try {
            assertThatThrownBy(() -> handler.create(failed, alicia, "Reunión", end.plusSeconds(7200), end.plusSeconds(10800)))
                    .isInstanceOf(RuntimeException.class);
            assertThat(number(db, "SELECT version FROM booking.resources WHERE id = '" + resource + "'")).isEqualTo(3);
            assertThat(number(db, "SELECT count(*) FROM booking.bookings WHERE resource_id = '" + resource + "'")).isEqualTo(3);
            assertThat(number(db, "SELECT count(*) FROM booking.command_results WHERE command_id = '" + failed.commandId() + "'")).isZero();
        } finally {
            execute(db, "ALTER TABLE booking.outbox_events DROP CONSTRAINT test_reject_creation");
        }
    }

    private static CommandRequest createRequest(Actor actor, UUID resource, Instant start, Instant end) {
        var payload = new CreateBookingPayload(start.atOffset(ZoneOffset.UTC), end.atOffset(ZoneOffset.UTC), "Reunión");
        return new CommandRequest(actor.id(), UUID.randomUUID(), RequestFingerprint.calculate(CommandType.CreateBooking, resource, payload),
                "CreateBooking", resource, RequestFingerprint.canonicalJson(CommandType.CreateBooking, resource, payload), UUID.randomUUID());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {
        @Bean @Primary
        Clock fixedClock() { return Clock.fixed(Instant.parse("2027-01-01T08:00:00Z"), ZoneOffset.UTC); }
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
        return new SpringApplicationBuilder(BooklaneBookingApplication.class, FixedClockConfiguration.class).web(WebApplicationType.NONE).profiles("test")
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
