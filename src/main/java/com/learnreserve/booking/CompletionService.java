package com.learnreserve.booking;

import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompletionService {
    private final BookingRepository bookings;
    private final Clock clock;
    public CompletionService(BookingRepository bookings, Clock clock) { this.bookings = bookings; this.clock = clock; }

    @Transactional
    public int completeEndedBookings() {
        // Conditional bulk update is idempotent across application instances and catches up after downtime.
        return bookings.completeEnded(clock.instant());
    }
}
