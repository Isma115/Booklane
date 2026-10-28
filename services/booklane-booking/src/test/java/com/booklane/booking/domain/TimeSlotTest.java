package com.booklane.booking.domain;

import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimeSlotTest {
    private static final TimeSlot BASE = slot("10:00", "11:00");

    @ParameterizedTest
    @MethodSource("intervals")
    void overlapIsSymmetricAndHalfOpen(String start, String end, boolean overlaps) {
        var other = slot(start, end);
        assertThat(BASE.overlaps(other)).isEqualTo(overlaps);
        assertThat(other.overlaps(BASE)).isEqualTo(overlaps);
    }

    static Stream<Arguments> intervals() {
        return Stream.of(Arguments.of("09:00", "10:00", false), Arguments.of("11:00", "12:00", false),
                Arguments.of("09:45", "10:15", true), Arguments.of("10:30", "11:30", true),
                Arguments.of("10:15", "10:45", true), Arguments.of("09:00", "12:00", true));
    }

    @Test
    void rejectsEmptyOrReversedIntervals() {
        assertThatThrownBy(() -> slot("10:00", "10:00")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> slot("11:00", "10:00")).isInstanceOf(IllegalArgumentException.class);
    }

    private static TimeSlot slot(String start, String end) {
        return new TimeSlot(Instant.parse("2027-01-01T" + start + ":00Z"), Instant.parse("2027-01-01T" + end + ":00Z"));
    }
}
