package com.learnreserve.common;

import static org.assertj.core.api.Assertions.*;
import java.time.*;
import org.junit.jupiter.api.Test;

class TimeRangeTest {
    private final Instant start = Instant.parse("2030-01-01T10:00:00Z");

    @Test void adjacentIntervalsDoNotOverlap() {
        var first = new TimeRange(start, start.plusSeconds(3600));
        assertThat(first.overlaps(new TimeRange(start.plusSeconds(3600), start.plusSeconds(7200)))).isFalse();
    }
    @Test void containmentAndPartialOverlapConflictInBothDirections() {
        var first = new TimeRange(start, start.plusSeconds(3600));
        for (var other : new TimeRange[]{new TimeRange(start.plusSeconds(60), start.plusSeconds(120)),
                new TimeRange(start.minusSeconds(60), start.plusSeconds(60)),
                new TimeRange(start.minusSeconds(60), start.plusSeconds(7200)), first}) {
            assertThat(first.overlaps(other)).isTrue();
            assertThat(other.overlaps(first)).isTrue();
        }
    }
    @Test void rejectsZeroLengthReversedNullAndFractionalIntervals() {
        assertThatThrownBy(() -> new TimeRange(start, start)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> new TimeRange(start, start.minusSeconds(1))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> new TimeRange(null, start)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> new TimeRange(start.plusNanos(1), start.plusSeconds(60))).isInstanceOf(ApiException.class);
    }
    @Test void startMustBeStrictlyAfterNow() {
        var range = new TimeRange(start, start.plusSeconds(60));
        assertThatThrownBy(() -> range.requireFuture(Clock.fixed(start, ZoneOffset.UTC))).isInstanceOf(ApiException.class);
        assertThatCode(() -> range.requireFuture(Clock.fixed(start.minusSeconds(1), ZoneOffset.UTC))).doesNotThrowAnyException();
    }
    @Test void durationLimitIncludesExactBoundary() {
        assertThatCode(() -> new TimeRange(start, start.plus(Duration.ofDays(1))).requireMaximumDays(1)).doesNotThrowAnyException();
        assertThatThrownBy(() -> new TimeRange(start, start.plus(Duration.ofDays(1)).plusSeconds(1)).requireMaximumDays(1))
                .isInstanceOf(ApiException.class);
    }
}
