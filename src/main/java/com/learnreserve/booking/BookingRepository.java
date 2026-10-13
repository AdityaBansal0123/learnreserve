package com.learnreserve.booking;

import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    @Query("""
            select b from Booking b where b.resource.id = :resourceId
            and b.status = com.learnreserve.booking.Booking$Status.CONFIRMED
            and b.startsAt < :end and b.endsAt > :start order by b.startsAt
            """)
    List<Booking> conflicting(Long resourceId, Instant start, Instant end);

    @EntityGraph(attributePaths = {"resource", "slot"})
    @Query("select b from Booking b where b.learner.id = :learnerId and (:status is null or b.status = :status)")
    Page<Booking> history(Long learnerId, Booking.Status status, Pageable pageable);

    Optional<Booking> findByIdAndLearnerId(Long id, Long learnerId);

    // Scalar lookup intentionally avoids loading a potentially stale Booking before taking the resource lock.
    @Query("select b.resource.id from Booking b where b.id = :id and b.learner.id = :learnerId")
    Optional<Long> ownedResourceId(Long id, Long learnerId);

    @Query("""
            select count(b) > 0 from Booking b where b.slot.id = :slotId
            and b.status = com.learnreserve.booking.Booking$Status.CONFIRMED and b.endsAt > :now
            """)
    boolean hasUpcomingBooking(Long slotId, Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Booking b set b.status = com.learnreserve.booking.Booking$Status.COMPLETED, b.completedAt = :now
            where b.status = com.learnreserve.booking.Booking$Status.CONFIRMED and b.endsAt <= :now
            """)
    int completeEnded(Instant now);
}
