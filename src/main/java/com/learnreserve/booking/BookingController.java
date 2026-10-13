package com.learnreserve.booking;

import com.learnreserve.auth.AccountPrincipal;
import com.learnreserve.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/bookings")
@PreAuthorize("hasRole('LEARNER')")
@Tag(name = "Bookings", description = "Learners can access only their own bookings")
public class BookingController {
    private final BookingService service;
    public BookingController(BookingService service) { this.service = service; }

    public record BookingRequest(@NotNull @Positive Long resourceId, @NotNull Instant startsAt, @NotNull Instant endsAt) {}
    public record BookingView(Long id, Long resourceId, String resourceName, Long slotId, Instant startsAt, Instant endsAt,
                              Booking.Status status, Instant createdAt, Instant cancelledAt, Instant completedAt) {
        public static BookingView from(Booking booking) {
            return new BookingView(booking.getId(), booking.getResource().getId(), booking.getResource().getName(),
                    booking.getSlot().getId(), booking.getStartsAt(), booking.getEndsAt(), booking.getStatus(),
                    booking.getCreatedAt(), booking.getCancelledAt(), booking.getCompletedAt());
        }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Reserve a resource interval", description = "At most 24 hours; must be in the future and contained in one active window. Overlap returns 409 BOOKING_CONFLICT. Adjacent bookings are allowed.")
    public ResponseEntity<BookingView> book(@AuthenticationPrincipal AccountPrincipal principal,
                                           @Valid @RequestBody BookingRequest request) {
        var result = service.book(principal.id(), request);
        return ResponseEntity.created(URI.create("/api/v1/bookings/" + result.id())).body(result);
    }

    @GetMapping
    @Operation(summary = "View your paginated booking history")
    public PageResponse<BookingView> history(@AuthenticationPrincipal AccountPrincipal principal,
            @RequestParam(required = false) Booking.Status status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.history(principal.id(), status, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "View your booking")
    public BookingView get(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable @Positive Long id) {
        return service.get(principal.id(), id);
    }

    @PostMapping(value = "/{id}/cancel", consumes = "application/json")
    @Operation(summary = "Cancel your booking", description = "Send Content-Type: application/json with {}. Cancellation is idempotent and allowed only before the session starts.")
    public BookingView cancel(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable @Positive Long id) {
        return service.cancel(principal.id(), id);
    }
}
