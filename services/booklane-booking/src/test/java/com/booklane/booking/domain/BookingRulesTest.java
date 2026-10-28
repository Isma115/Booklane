package com.booklane.booking.domain;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static com.booklane.booking.domain.BookingDecision.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.params.provider.Arguments;

class BookingRulesTest {
    private static final Instant NOW = Instant.parse("2027-03-01T08:00:00Z");
    private static final BookingRules RULES = new BookingRules(Clock.fixed(NOW, ZoneId.of("America/New_York")));

    @ParameterizedTest
    @MethodSource("intervals")
    void validatesBoundariesAndMadridTime(String start, String end, BookingDecision expected) {
        assertThat(RULES.validate("Reunión", instant(start), instant(end))).isEqualTo(expected);
    }

    static Stream<Arguments> intervals() {
        return Stream.of(
                Arguments.of("2027-03-01T09:00:00+01:00", "2027-03-01T09:15:00+01:00", START_NOT_FUTURE),
                Arguments.of("2027-03-01T09:15:00+01:00", "2027-03-01T09:30:00+01:00", VALID),
                Arguments.of("2027-03-02T08:00:00+01:00", "2027-03-02T08:15:00+01:00", VALID),
                Arguments.of("2027-03-02T18:00:00+01:00", "2027-03-02T22:00:00+01:00", VALID),
                Arguments.of("2027-03-02T17:45:00+01:00", "2027-03-02T22:00:00+01:00", INVALID_DURATION),
                Arguments.of("2027-03-02T07:45:00+01:00", "2027-03-02T08:00:00+01:00", OUTSIDE_OPENING_HOURS),
                Arguments.of("2027-03-02T21:45:00+01:00", "2027-03-02T22:15:00+01:00", OUTSIDE_OPENING_HOURS),
                Arguments.of("2027-03-02T09:01:00+01:00", "2027-03-02T09:30:00+01:00", OFF_GRID),
                Arguments.of("2027-03-02T09:00:01+01:00", "2027-03-02T09:30:00+01:00", OFF_GRID),
                Arguments.of("2027-03-02T09:00:00+01:00", "2027-03-02T09:00:00+01:00", INVALID_INTERVAL),
                Arguments.of("2027-03-02T09:00:00+01:00", "2027-03-02T08:00:00+01:00", INVALID_INTERVAL),
                Arguments.of("2027-03-02T21:45:00+01:00", "2027-03-03T00:00:00+01:00", OUTSIDE_OPENING_HOURS),
                Arguments.of("2027-03-31T21:45:00+02:00", "2027-03-31T22:00:00+02:00", VALID),
                Arguments.of("2027-04-01T08:00:00+02:00", "2027-04-01T08:15:00+02:00", OUTSIDE_HORIZON),
                Arguments.of("2027-03-28T08:00:00+02:00", "2027-03-28T08:15:00+02:00", VALID),
                Arguments.of("2027-03-28T06:00:00Z", "2027-03-28T06:15:00Z", VALID),
                Arguments.of("2027-03-28T06:00:00-04:00", "2027-03-28T06:15:00-04:00", VALID));
    }

    @Test
    void autumnClockChangeAndDifferentClockZonesUseMadrid() {
        var now = Instant.parse("2027-10-01T10:00:00Z");
        var start = instant("2027-10-31T08:00:00+01:00");
        var end = instant("2027-10-31T08:15:00+01:00");
        assertThat(new BookingRules(Clock.fixed(now, ZoneId.of("Asia/Tokyo"))).validate("Sala", start, end)).isEqualTo(VALID);
        assertThat(new BookingRules(Clock.fixed(now, ZoneId.of("America/Los_Angeles"))).validate("Sala", start, end)).isEqualTo(VALID);
    }

    @Test
    void validatesTitleAndMissingInstants() {
        var start = instant("2027-03-02T09:00:00+01:00");
        var end = instant("2027-03-02T09:15:00+01:00");
        assertThat(RULES.validate("  ab  ", start, end)).isEqualTo(INVALID_TITLE);
        assertThat(RULES.validate("x".repeat(101), start, end)).isEqualTo(INVALID_TITLE);
        assertThat(RULES.validate("😀".repeat(100), start, end)).isEqualTo(VALID);
        assertThat(RULES.validate("Sala", null, end)).isEqualTo(INVALID_INTERVAL);
        assertThat(RULES.validate(null, start, end)).isEqualTo(INVALID_TITLE);
    }

    @Test
    void actorsAreIndependentAndDoNotLimitSimultaneousRequests() {
        var actor = new Actor(UUID.randomUUID(), Actor.Role.USER);
        assertThat(actor.id()).isNotNull();
        var start = instant("2027-03-02T09:00:00+01:00");
        var end = instant("2027-03-02T10:00:00+01:00");
        assertThat(RULES.validate("Sala", start, end)).isEqualTo(VALID);
        assertThat(RULES.validate("Equipo", start, end)).isEqualTo(VALID);
        assertThatThrownBy(() -> new Actor(null, Actor.Role.USER)).isInstanceOf(NullPointerException.class);
    }

    private static Instant instant(String value) { return OffsetDateTime.parse(value).toInstant(); }
}
