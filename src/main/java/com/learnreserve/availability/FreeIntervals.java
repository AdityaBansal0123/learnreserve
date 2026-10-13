package com.learnreserve.availability;

import com.learnreserve.common.TimeRange;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Pure interval subtraction, shared by availability discovery and its unit tests. */
public final class FreeIntervals {
    private FreeIntervals() {}

    public static List<TimeRange> subtract(TimeRange window, List<TimeRange> occupied) {
        List<TimeRange> result = new ArrayList<>();
        Instant cursor = window.start();
        for (var booking : occupied.stream().sorted(Comparator.comparing(TimeRange::start)).toList()) {
            if (!window.overlaps(booking)) { continue; }
            Instant start = booking.start().isBefore(window.start()) ? window.start() : booking.start();
            Instant end = booking.end().isAfter(window.end()) ? window.end() : booking.end();
            if (start.isAfter(cursor)) { result.add(new TimeRange(cursor, start)); }
            if (end.isAfter(cursor)) { cursor = end; }
        }
        if (cursor.isBefore(window.end())) { result.add(new TimeRange(cursor, window.end())); }
        return List.copyOf(result);
    }
}
