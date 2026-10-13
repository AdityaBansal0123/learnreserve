package com.learnreserve.booking;

import com.learnreserve.auth.UserRepository;
import com.learnreserve.availability.AvailabilityRepository;
import com.learnreserve.common.*;
import com.learnreserve.resource.ResourceRepository;
import java.time.Clock;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly = true)
public class BookingService {
    private final BookingRepository bookings;
    private final ResourceRepository resources;
    private final AvailabilityRepository slots;
    private final UserRepository users;
    private final Clock clock;

    public BookingService(BookingRepository bookings, ResourceRepository resources,
                          AvailabilityRepository slots, UserRepository users, Clock clock) {
        this.bookings = bookings;
        this.resources = resources;
        this.slots = slots;
        this.users = users;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookingController.BookingView book(Long learnerId, BookingController.BookingRequest request) {
        var range = new TimeRange(request.startsAt(), request.endsAt());
        range.requireMaximumDays(1);
        // The parent row exists even when there are zero bookings. Every resource mutation takes this lock.
        // READ_COMMITTED ensures a waiting transaction sees the preceding transaction's committed booking.
        var resource = resources.lockById(request.resourceId()).orElseThrow(() -> ApiException.notFound("Resource"));
        range.requireFuture(clock); // Check after waiting for the lock, not just when the request arrived.
        if (!resource.isActive()) {
            throw ApiException.conflict("RESOURCE_INACTIVE", "This resource is not accepting bookings");
        }
        var matching = slots.containing(resource.getId(), range.start(), range.end());
        if (matching.isEmpty()) {
            throw ApiException.conflict("OUTSIDE_AVAILABILITY", "Booking must fit entirely inside one active availability window");
        }
        if (!bookings.conflicting(resource.getId(), range.start(), range.end()).isEmpty()) {
            throw ApiException.conflict("BOOKING_CONFLICT", "This resource is already booked during the requested interval");
        }
        var learner = users.findById(learnerId).orElseThrow(() -> ApiException.notFound("Account"));
        var booking = bookings.saveAndFlush(new Booking(learner, resource, matching.getFirst(),
                range.start(), range.end(), clock.instant()));
        return BookingController.BookingView.from(booking);
    }

    public PageResponse<BookingController.BookingView> history(Long learnerId, Booking.Status status, int page, int size) {
        return PageResponse.from(bookings.history(learnerId, status,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")))
                .map(BookingController.BookingView::from));
    }

    public BookingController.BookingView get(Long learnerId, Long id) {
        return BookingController.BookingView.from(owned(learnerId, id));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookingController.BookingView cancel(Long learnerId, Long id) {
        var resourceId = bookings.ownedResourceId(id, learnerId).orElseThrow(() -> ApiException.notFound("Booking"));
        resources.lockById(resourceId).orElseThrow(() -> ApiException.notFound("Resource"));
        var booking = owned(learnerId, id);
        if (booking.getStatus() == Booking.Status.CANCELLED) {
            return BookingController.BookingView.from(booking); // Idempotent; preserve original cancellation time.
        }
        if (booking.getStatus() != Booking.Status.CONFIRMED || !booking.getStartsAt().isAfter(clock.instant())) {
            throw ApiException.conflict("CANCELLATION_CLOSED", "Only confirmed bookings that have not started can be cancelled");
        }
        booking.cancel(clock.instant());
        return BookingController.BookingView.from(booking);
    }

    private Booking owned(Long learnerId, Long id) {
        // Return 404 for both missing and another learner's booking to avoid leaking ownership.
        return bookings.findByIdAndLearnerId(id, learnerId).orElseThrow(() -> ApiException.notFound("Booking"));
    }
}
