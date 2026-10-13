package com.learnreserve.availability;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.*;

public interface AvailabilityRepository extends JpaRepository<AvailabilitySlot, Long> {
    @Query("""
            select s from AvailabilitySlot s where s.resource.id = :resourceId and s.active = true
            and s.startsAt < :end and s.endsAt > :start order by s.startsAt
            """)
    List<AvailabilitySlot> overlapping(Long resourceId, Instant start, Instant end);

    @Query("""
            select s from AvailabilitySlot s where s.resource.id = :resourceId and s.active = true
            and s.startsAt <= :start and s.endsAt >= :end
            """)
    List<AvailabilitySlot> containing(Long resourceId, Instant start, Instant end);
}
