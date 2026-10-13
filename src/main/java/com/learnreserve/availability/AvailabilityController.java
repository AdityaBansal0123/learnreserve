package com.learnreserve.availability;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/resources/{resourceId}/availability")
@Tag(name = "Availability", description = "Availability windows are exclusive-resource windows, not seat inventory")
public class AvailabilityController {
    private final AvailabilityService service;
    public AvailabilityController(AvailabilityService service) { this.service = service; }

    public record SlotRequest(@NotNull Instant startsAt, @NotNull Instant endsAt) {}
    public record SlotView(Long id, Long resourceId, Instant startsAt, Instant endsAt, boolean active) {
        public static SlotView from(AvailabilitySlot slot) {
            return new SlotView(slot.getId(), slot.getResource().getId(), slot.getStartsAt(), slot.getEndsAt(), slot.isActive());
        }
    }
    public record IntervalView(Instant startsAt, Instant endsAt) {}
    public record WindowView(Long slotId, Instant startsAt, Instant endsAt, List<IntervalView> freeIntervals) {}

    @GetMapping
    @SecurityRequirements
    @Operation(summary = "Find remaining bookable intervals", description = "Required from/to timestamps; maximum query span 31 days. Returns active windows with free intervals clipped to the query and future time. Fully booked windows have an empty freeIntervals array. Results are advisory; booking rechecks under a database lock.")
    public List<WindowView> available(@PathVariable @Positive Long resourceId,
                                      @RequestParam Instant from, @RequestParam Instant to) {
        return service.available(resourceId, from, to);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Add an availability window (admin)", description = "Future window, at most 31 days, with no overlap against other active windows.")
    public SlotView create(@PathVariable @Positive Long resourceId, @Valid @RequestBody SlotRequest request) {
        return service.create(resourceId, request);
    }

    @PostMapping(value = "/{slotId}/close", consumes = "application/json")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Close an availability window (admin)", description = "Send application/json with {}. Idempotent. Returns 409 if upcoming or ongoing confirmed bookings exist. History is retained.")
    public SlotView close(@PathVariable @Positive Long resourceId, @PathVariable @Positive Long slotId) {
        return service.close(resourceId, slotId);
    }
}
