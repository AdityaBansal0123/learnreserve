package com.learnreserve.common;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Half-open UTC interval: [start, end). Adjacent reservations are allowed. */
public record TimeRange(Instant start, Instant end) {
    public TimeRange {
        if (start == null || end == null || !end.isAfter(start)) {
            throw ApiException.invalid("endsAt must be later than startsAt");
        }
        if (!start.equals(start.truncatedTo(ChronoUnit.SECONDS))
                || !end.equals(end.truncatedTo(ChronoUnit.SECONDS))) {
            throw ApiException.invalid("Use whole-second timestamps without fractional seconds");
        }
    }

    public void requireFuture(Clock clock) {
        if (!start.isAfter(clock.instant())) {
            throw ApiException.invalid("startsAt must be in the future");
        }
    }

    public void requireMaximumDays(int days) {
        if (Duration.between(start, end).compareTo(Duration.ofDays(days)) > 0) {
            throw ApiException.invalid("Time range must not exceed " + days + " days");
        }
    }

    public boolean overlaps(TimeRange other) {
        return start.isBefore(other.end) && end.isAfter(other.start);
    }
}
