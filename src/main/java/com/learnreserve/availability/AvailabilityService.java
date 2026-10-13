package com.learnreserve.availability;

import com.learnreserve.booking.BookingRepository;
import com.learnreserve.common.*;
import com.learnreserve.resource.ResourceRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly = true)
public class AvailabilityService {
    private final AvailabilityRepository slots;
    private final ResourceRepository resources;
    private final BookingRepository bookings;
    private final Clock clock;

    public AvailabilityService(AvailabilityRepository slots, ResourceRepository resources, BookingRepository bookings, Clock clock) {
        this.slots = slots;
        this.resources = resources;
        this.bookings = bookings;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AvailabilityController.SlotView create(Long resourceId, AvailabilityController.SlotRequest request) {
        var range = new TimeRange(request.startsAt(), request.endsAt());
        range.requireMaximumDays(31);
        var resource = resources.lockById(resourceId).orElseThrow(() -> ApiException.notFound("Resource"));
        range.requireFuture(clock);
        if (!resource.isActive()) { throw ApiException.conflict("RESOURCE_INACTIVE", "Activate the resource before adding availability"); }
        if (!slots.overlapping(resourceId, range.start(), range.end()).isEmpty()) {
            throw ApiException.conflict("AVAILABILITY_CONFLICT", "Availability windows for a resource must not overlap");
        }
        return AvailabilityController.SlotView.from(slots.save(new AvailabilitySlot(resource, range.start(), range.end())));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AvailabilityController.SlotView close(Long resourceId, Long slotId) {
        resources.lockById(resourceId).orElseThrow(() -> ApiException.notFound("Resource"));
        var slot = slots.findById(slotId).filter(s -> s.getResource().getId().equals(resourceId))
                .orElseThrow(() -> ApiException.notFound("Availability window"));
        if (bookings.hasUpcomingBooking(slotId, clock.instant())) {
            throw ApiException.conflict("AVAILABILITY_IN_USE", "This window has upcoming or ongoing confirmed bookings");
        }
        slot.close();
        return AvailabilityController.SlotView.from(slot);
    }

    public List<AvailabilityController.WindowView> available(Long resourceId, Instant from, Instant to) {
        var requested = new TimeRange(from, to);
        requested.requireMaximumDays(31);
        var resource = resources.findById(resourceId).orElseThrow(() -> ApiException.notFound("Resource"));
        if (!resource.isActive()) { return List.of(); }
        Instant earliest = clock.instant().truncatedTo(ChronoUnit.SECONDS).plusSeconds(1);
        Instant start = from.isAfter(earliest) ? from : earliest;
        if (!to.isAfter(start)) { return List.of(); }
        var occupied = bookings.conflicting(resourceId, start, to).stream()
                .map(b -> new TimeRange(b.getStartsAt(), b.getEndsAt())).toList();
        List<AvailabilityController.WindowView> result = new ArrayList<>();
        for (var slot : slots.overlapping(resourceId, start, to)) {
            var clipped = new TimeRange(slot.getStartsAt().isAfter(start) ? slot.getStartsAt() : start,
                    slot.getEndsAt().isBefore(to) ? slot.getEndsAt() : to);
            var free = FreeIntervals.subtract(clipped, occupied);
            result.add(new AvailabilityController.WindowView(slot.getId(), slot.getStartsAt(), slot.getEndsAt(),
                    free.stream().map(r -> new AvailabilityController.IntervalView(r.start(), r.end())).toList()));
        }
        return List.copyOf(result);
    }
}
