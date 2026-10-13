package com.learnreserve.availability;

import static org.assertj.core.api.Assertions.*;
import com.learnreserve.common.TimeRange;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class FreeIntervalsTest {
    private final Instant base = Instant.parse("2030-01-01T00:00:00Z");
    private TimeRange range(int start, int end) { return new TimeRange(base.plusSeconds(start), base.plusSeconds(end)); }

    @Test void returnsEntireWindowWhenEmpty() {
        assertThat(FreeIntervals.subtract(range(0, 100), List.of())).containsExactly(range(0, 100));
    }
    @Test void splitsWindowAndSortsUnorderedBookings() {
        assertThat(FreeIntervals.subtract(range(0, 100), List.of(range(60, 80), range(10, 30))))
                .containsExactly(range(0, 10), range(30, 60), range(80, 100));
    }
    @Test void handlesClippingAdjacentAndOverlappingOccupiedRanges() {
        assertThat(FreeIntervals.subtract(range(0, 100), List.of(range(-10, 20), range(20, 40), range(30, 50), range(80, 120))))
                .containsExactly(range(50, 80));
    }
    @Test void fullyOccupiedWindowHasNoFreeIntervals() {
        assertThat(FreeIntervals.subtract(range(0, 100), List.of(range(-10, 110)))).isEmpty();
    }
    @Test void ignoresBookingsOutsideWindow() {
        assertThat(FreeIntervals.subtract(range(0, 100), List.of(range(-20, 0), range(100, 120))))
                .containsExactly(range(0, 100));
    }
}
