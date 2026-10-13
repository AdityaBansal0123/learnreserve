package com.learnreserve;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import com.learnreserve.auth.*;
import com.learnreserve.availability.*;
import com.learnreserve.booking.*;
import com.learnreserve.booking.CompletionService;
import com.learnreserve.common.ApiException;
import com.learnreserve.resource.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/** Runs unchanged on H2 and MySQL. No enclosing test transaction: each operation really commits. */
@SpringBootTest(properties = {"spring.profiles.default=test", "debug=false"})
@AutoConfigureMockMvc
@Import(ApiIntegrationTest.ClockConfig.class)
class ApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired ResourceRepository resources;
    @Autowired AvailabilityRepository slots;
    @Autowired BookingRepository bookings;
    @Autowired BookingService bookingService;
    @Autowired AvailabilityService availabilityService;
    @Autowired ResourceService resourceService;
    @Autowired CompletionService completionService;
    @Autowired PasswordEncoder encoder;
    @Autowired MutableClock clock;

    @TestConfiguration
    static class ClockConfig {
        @Bean @Primary MutableClock testClock() { return new MutableClock(); }
    }

    static class MutableClock extends Clock {
        private volatile Instant current = Instant.parse("2030-01-01T08:00:00Z");
        void set(Instant current) { this.current = current; }
        @Override public Instant instant() { return current; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(current, zone); }
    }

    private final Instant now = Instant.parse("2030-01-01T08:00:00Z");
    private final Instant start = Instant.parse("2030-01-02T09:00:00Z");
    private User alice;
    private User bob;
    private User admin;
    private LearningResource room;
    private AvailabilitySlot slot;
    private static String passwordHash;

    @BeforeEach void setUp() {
        clock.set(now);
        bookings.deleteAllInBatch();
        slots.deleteAllInBatch();
        resources.deleteAllInBatch();
        users.deleteAllInBatch();
        if (passwordHash == null) { passwordHash = encoder.encode("StrongPassword!2026"); }
        alice = users.save(new User("Alice", "alice@example.com", passwordHash, User.Role.LEARNER, now));
        bob = users.save(new User("Bob", "bob@example.com", passwordHash, User.Role.LEARNER, now));
        admin = users.save(new User("Admin", "admin@example.com", passwordHash, User.Role.ADMIN, now));
        room = resources.save(new LearningResource("Quiet Room", "Whiteboard and Wi-Fi", LearningResource.Type.STUDY_ROOM, "Floor 2", now));
        slot = slots.save(new AvailabilitySlot(room, start, start.plusSeconds(8 * 3600)));
    }

    private BookingController.BookingRequest request(int fromHour, int toHour) {
        return new BookingController.BookingRequest(room.getId(), start.plusSeconds(fromHour * 3600L), start.plusSeconds(toHour * 3600L));
    }
    private String body(Object value) throws Exception { return json.writeValueAsString(value); }

    @Test void registersLearnerNormalizesEmailHashesPasswordAndAuthenticates() throws Exception {
        var response = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"New Learner\",\"email\":\"NEW@example.com\",\"password\":\"StrongPassword!2026\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("LEARNER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist()).andReturn();
        assertThat(json.readTree(response.getResponse().getContentAsString()).get("email").asText()).isEqualTo("new@example.com");
        var saved = users.findByEmail("new@example.com").orElseThrow();
        assertThat(saved.getPasswordHash()).isNotEqualTo("StrongPassword!2026");
        assertThat(encoder.matches("StrongPassword!2026", saved.getPasswordHash())).isTrue();
        mvc.perform(get("/api/v1/auth/me").with(httpBasic("NEW@example.com", "StrongPassword!2026")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("New Learner"));
    }

    @Test void duplicateRegistrationReturnsConflict() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Alice\",\"email\":\"ALICE@example.com\",\"password\":\"StrongPassword!2026\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_EXISTS"));
    }

    @Test void validationAndMalformedJsonHaveConsistentProblemResponses() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"email\":\"bad\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED")).andExpect(jsonPath("$.errors").isArray());
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Bad\",\"email\":\"bad@example.com\",\"password\":\"StrongPassword!2026\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test void unicodePasswordsCannotBypassBcryptByteLimit() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(body(new AuthController.RegisterRequest("User", "unicode@example.com", "\u00e9".repeat(40)))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test void publicDiscoverySearchFilterAndPagination() throws Exception {
        mvc.perform(get("/api/v1/resources").param("q", "quiet").param("type", "STUDY_ROOM").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Quiet Room"));
        mvc.perform(get("/api/v1/resources").param("size", "101"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(get("/api/v1/resources").param("type", "INVALID")).andExpect(status().isBadRequest());
    }

    @Test void authenticationAndRoleChecksApply() throws Exception {
        mvc.perform(get("/api/v1/bookings")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(get("/api/v1/bookings").with(httpBasic("alice@example.com", "wrong"))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/resources").with(user(AccountPrincipal.from(alice)))
                .contentType(MediaType.APPLICATION_JSON).content(body(new ResourceController.ResourceRequest("Room", "Description", LearningResource.Type.STUDY_ROOM, "Here"))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(post("/api/v1/bookings").with(user(AccountPrincipal.from(admin)))
                .contentType(MediaType.APPLICATION_JSON).content(body(request(0, 1)))).andExpect(status().isForbidden());
    }

    @Test void adminCreatesAndUpdatesResourceAndAvailability() throws Exception {
        var resourceRequest = new ResourceController.ResourceRequest("Mentor", "Java mentoring", LearningResource.Type.MENTORING, "Online");
        var created = mvc.perform(post("/api/v1/resources").with(user(AccountPrincipal.from(admin)))
                .contentType(MediaType.APPLICATION_JSON).content(body(resourceRequest)))
                .andExpect(status().isCreated()).andExpect(header().exists("Location")).andReturn();
        long id = json.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(put("/api/v1/resources/{id}", id).with(user(AccountPrincipal.from(admin)))
                .contentType(MediaType.APPLICATION_JSON).content(body(resourceRequest))).andExpect(status().isOk());
        mvc.perform(post("/api/v1/resources/{id}/availability", id).with(user(AccountPrincipal.from(admin)))
                .contentType(MediaType.APPLICATION_JSON).content(body(new AvailabilityController.SlotRequest(start, start.plusSeconds(3600)))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.active").value(true));
    }

    @Test void bookingLifecyclePersistsHistoryAndCancellationReleasesInterval() throws Exception {
        var response = mvc.perform(post("/api/v1/bookings").with(user(AccountPrincipal.from(alice)))
                .contentType(MediaType.APPLICATION_JSON).content(body(request(1, 2))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(header().exists("Location")).andReturn();
        long id = json.readTree(response.getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(get("/api/v1/bookings/{id}", id).with(user(AccountPrincipal.from(alice)))).andExpect(status().isOk());
        mvc.perform(post("/api/v1/bookings").with(user(AccountPrincipal.from(bob)))
                .contentType(MediaType.APPLICATION_JSON).content(body(request(1, 2))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BOOKING_CONFLICT"));
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post("/api/v1/bookings/{id}/cancel", id).with(user(AccountPrincipal.from(alice)))
                    .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        }
        bookingService.book(bob.getId(), request(1, 2));
        mvc.perform(get("/api/v1/bookings").param("status", "CANCELLED").with(user(AccountPrincipal.from(alice))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        assertThat(bookings.count()).isEqualTo(2);
    }

    @Test void cannotReadOrCancelAnotherLearnersBooking() throws Exception {
        var booking = bookingService.book(alice.getId(), request(0, 1));
        mvc.perform(get("/api/v1/bookings/{id}", booking.id()).with(user(AccountPrincipal.from(bob))))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/bookings/{id}/cancel", booking.id()).with(user(AccountPrincipal.from(bob)))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isNotFound());
        assertThat(bookings.findById(booking.id()).orElseThrow().getStatus()).isEqualTo(Booking.Status.CONFIRMED);
    }

    @Test void allOverlapShapesConflictButAdjacentIntervalsSucceed() {
        bookingService.book(alice.getId(), request(2, 4));
        for (var candidate : List.of(request(2, 4), request(1, 3), request(3, 5), request(1, 5),
                new BookingController.BookingRequest(room.getId(), start.plusSeconds(9000), start.plusSeconds(10000)))) {
            assertThatThrownBy(() -> bookingService.book(bob.getId(), candidate)).isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).code()).isEqualTo("BOOKING_CONFLICT");
        }
        bookingService.book(bob.getId(), request(0, 2));
        bookingService.book(bob.getId(), request(4, 6));
        assertThat(bookings.count()).isEqualTo(3);
    }

    @Test void rejectsOutsideAvailabilityPastReversedAndFractionalRequests() throws Exception {
        mvc.perform(post("/api/v1/bookings").with(user(AccountPrincipal.from(alice)))
                .contentType(MediaType.APPLICATION_JSON).content(body(request(7, 9))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OUTSIDE_AVAILABILITY"));
        for (var invalid : List.of(request(2, 1), new BookingController.BookingRequest(room.getId(), now.minusSeconds(3600), now),
                new BookingController.BookingRequest(room.getId(), start.plusNanos(100), start.plusSeconds(3600)), request(0, 25))) {
            mvc.perform(post("/api/v1/bookings").with(user(AccountPrincipal.from(alice)))
                    .contentType(MediaType.APPLICATION_JSON).content(body(invalid))).andExpect(status().isBadRequest());
        }
        assertThat(bookings.count()).isZero();
    }

    @Test void availabilitySubtractsBookedIntervalsAndRestoresCancelledOnes() throws Exception {
        var booking = bookingService.book(alice.getId(), request(2, 4));
        mvc.perform(get("/api/v1/resources/{id}/availability", room.getId()).param("from", start.toString()).param("to", start.plusSeconds(8 * 3600).toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].freeIntervals.length()").value(2))
                .andExpect(jsonPath("$[0].freeIntervals[0].endsAt").value(start.plusSeconds(7200).toString()));
        bookingService.cancel(alice.getId(), booking.id());
        assertThat(availabilityService.available(room.getId(), start, start.plusSeconds(8 * 3600)).getFirst().freeIntervals()).hasSize(1);
    }

    @Test void overlappingAvailabilityIsRejectedAndClosingPreservesHistory() {
        assertThatThrownBy(() -> availabilityService.create(room.getId(), new AvailabilityController.SlotRequest(start.plusSeconds(3600), start.plusSeconds(7200))))
                .isInstanceOf(ApiException.class).extracting(ex -> ((ApiException) ex).code()).isEqualTo("AVAILABILITY_CONFLICT");
        var booking = bookingService.book(alice.getId(), request(0, 1));
        assertThatThrownBy(() -> availabilityService.close(room.getId(), slot.getId())).isInstanceOf(ApiException.class);
        bookingService.cancel(alice.getId(), booking.id());
        assertThat(availabilityService.close(room.getId(), slot.getId()).active()).isFalse();
        assertThat(availabilityService.close(room.getId(), slot.getId()).active()).isFalse();
        assertThat(bookings.count()).isEqualTo(1);
        assertThatThrownBy(() -> bookingService.book(bob.getId(), request(0, 1))).isInstanceOf(ApiException.class);
    }

    @Test void deactivationBlocksNewBookingsWithoutDeletingExistingOnes() {
        var booking = bookingService.book(alice.getId(), request(0, 1));
        resourceService.setActive(room.getId(), false);
        assertThatThrownBy(() -> bookingService.book(bob.getId(), request(1, 2)))
                .isInstanceOf(ApiException.class).extracting(ex -> ((ApiException) ex).code()).isEqualTo("RESOURCE_INACTIVE");
        assertThat(availabilityService.available(room.getId(), start, start.plusSeconds(7200))).isEmpty();
        assertThat(bookingService.cancel(alice.getId(), booking.id()).status()).isEqualTo(Booking.Status.CANCELLED);
    }

    @Test void cannotCancelAtStartAndCompletionIsIdempotent() {
        var booking = bookingService.book(alice.getId(), request(0, 1));
        clock.set(start);
        assertThatThrownBy(() -> bookingService.cancel(alice.getId(), booking.id()))
                .isInstanceOf(ApiException.class).extracting(ex -> ((ApiException) ex).code()).isEqualTo("CANCELLATION_CLOSED");
        assertThat(completionService.completeEndedBookings()).isZero();
        clock.set(start.plusSeconds(3600));
        assertThat(completionService.completeEndedBookings()).isEqualTo(1);
        assertThat(completionService.completeEndedBookings()).isZero();
        assertThat(bookingService.get(alice.getId(), booking.id()).status()).isEqualTo(Booking.Status.COMPLETED);
        assertThat(bookingService.get(alice.getId(), booking.id()).completedAt()).isEqualTo(start.plusSeconds(3600));
    }

    @Test void completionNeverChangesCancelledHistory() {
        var booking = bookingService.book(alice.getId(), request(0, 1));
        bookingService.cancel(alice.getId(), booking.id());
        clock.set(start.plusSeconds(7200));
        assertThat(completionService.completeEndedBookings()).isZero();
        assertThat(bookingService.get(alice.getId(), booking.id()).status()).isEqualTo(Booking.Status.CANCELLED);
    }

    @Test void eightSimultaneousRequestsProduceExactlyOneWinner() throws Exception {
        try (var pool = Executors.newFixedThreadPool(8)) {
            var ready = new CountDownLatch(8);
            var go = new CountDownLatch(1);
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                User caller = i % 2 == 0 ? alice : bob;
                results.add(pool.submit(() -> {
                    ready.countDown();
                    if (!go.await(10, TimeUnit.SECONDS)) { throw new IllegalStateException("Race gate timeout"); }
                    return mvc.perform(post("/api/v1/bookings").with(user(AccountPrincipal.from(caller)))
                            .contentType(MediaType.APPLICATION_JSON).content(body(request(0, 1))))
                            .andReturn().getResponse().getStatus();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (var result : results) { statuses.add(result.get(30, TimeUnit.SECONDS)); }
            assertThat(statuses).filteredOn(code -> code == 201).hasSize(1);
            assertThat(statuses).filteredOn(code -> code == 409).hasSize(7);
            assertThat(bookings.count()).isEqualTo(1);
        }
    }

    @Test void concurrentAvailabilityCreationHasOneWinner() throws Exception {
        var next = new AvailabilityController.SlotRequest(start.plus(Duration.ofDays(1)), start.plus(Duration.ofDays(1)).plusSeconds(3600));
        try (var pool = Executors.newFixedThreadPool(2)) {
            var barrier = new CyclicBarrier(2);
            Callable<Boolean> attempt = () -> {
                barrier.await(10, TimeUnit.SECONDS);
                try { availabilityService.create(room.getId(), next); return true; }
                catch (ApiException ex) { assertThat(ex.code()).isEqualTo("AVAILABILITY_CONFLICT"); return false; }
            };
            var a = pool.submit(attempt);
            var b = pool.submit(attempt);
            assertThat(List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
        }
    }

    @Test void openApiAndHealthAreAccessible() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("LearnReserve API"))
                .andExpect(jsonPath("$.paths['/api/v1/bookings'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/register'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/resources'].post.responses['201']").exists())
                .andExpect(jsonPath("$.components.securitySchemes.basicAuth.scheme").value("basic"));
        mvc.perform(get("/v3/api-docs.yaml")).andExpect(status().isOk());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    @Test void bookingAndWindowClosureCannotBothWin() throws Exception {
        var barrier = new CyclicBarrier(2);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var reservation = pool.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                try { bookingService.book(alice.getId(), request(0, 1)); return "BOOKED"; }
                catch (ApiException ex) { return ex.code(); }
            });
            var closure = pool.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                try { availabilityService.close(room.getId(), slot.getId()); return "CLOSED"; }
                catch (ApiException ex) { return ex.code(); }
            });
            String result = reservation.get(30, TimeUnit.SECONDS) + "/" + closure.get(30, TimeUnit.SECONDS);
            assertThat(result).isIn("BOOKED/AVAILABILITY_IN_USE", "OUTSIDE_AVAILABILITY/CLOSED");
            boolean active = slots.findById(slot.getId()).orElseThrow().isActive();
            assertThat(bookings.count()).isEqualTo(active ? 1L : 0L);
        }
    }

    @Test void concurrentCancellationIsIdempotent() throws Exception {
        var booking = bookingService.book(alice.getId(), request(0, 1));
        var barrier = new CyclicBarrier(2);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<BookingController.BookingView> cancel = () -> {
                barrier.await(10, TimeUnit.SECONDS);
                return bookingService.cancel(alice.getId(), booking.id());
            };
            var a = pool.submit(cancel);
            var b = pool.submit(cancel);
            var first = a.get(30, TimeUnit.SECONDS);
            var second = b.get(30, TimeUnit.SECONDS);
            assertThat(first.status()).isEqualTo(Booking.Status.CANCELLED);
            assertThat(second).isEqualTo(first);
            assertThat(bookings.count()).isEqualTo(1);
        }
    }

    @Test void differentResourcesCanBeBookedForTheSameTime() {
        var other = resources.save(new LearningResource("Other Room", "A second room", LearningResource.Type.STUDY_ROOM, "Floor 3", now));
        slots.save(new AvailabilitySlot(other, start, start.plusSeconds(3600)));
        bookingService.book(alice.getId(), request(0, 1));
        bookingService.book(bob.getId(), new BookingController.BookingRequest(other.getId(), start, start.plusSeconds(3600)));
        assertThat(bookings.count()).isEqualTo(2);
    }

    @Test void offsetTimestampsAreNormalizedToTheSameUtcInterval() throws Exception {
        String content = "{\"resourceId\":" + room.getId()
                + ",\"startsAt\":\"2030-01-02T14:30:00+05:30\",\"endsAt\":\"2030-01-02T15:30:00+05:30\"}";
        mvc.perform(post("/api/v1/bookings").with(user(AccountPrincipal.from(alice)))
                .contentType(MediaType.APPLICATION_JSON).content(content))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.startsAt").value(start.toString()));
        assertThatThrownBy(() -> bookingService.book(bob.getId(), request(0, 1))).isInstanceOf(ApiException.class);
    }

    @Test void missingResourceAndMissingBookingUseNotFoundContract() throws Exception {
        mvc.perform(get("/api/v1/resources/99999999")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(get("/api/v1/bookings/99999999").with(user(AccountPrincipal.from(alice))))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test void availabilityIsClippedAndQuerySpanIsBounded() throws Exception {
        var from = start.plusSeconds(3600);
        var to = start.plusSeconds(7200);
        var free = availabilityService.available(room.getId(), from, to).getFirst().freeIntervals();
        assertThat(free).containsExactly(new AvailabilityController.IntervalView(from, to));
        mvc.perform(get("/api/v1/resources/{id}/availability", room.getId()).param("from", start.toString())
                .param("to", start.plus(Duration.ofDays(32)).toString())).andExpect(status().isBadRequest());
        assertThat(availabilityService.available(room.getId(), now.minusSeconds(7200), now.minusSeconds(3600))).isEmpty();
    }

    @Test void cancelledBookingKeepsOriginalTimestampOnRetry() {
        var booking = bookingService.book(alice.getId(), request(0, 1));
        bookingService.cancel(alice.getId(), booking.id());
        clock.set(now.plusSeconds(60));
        assertThat(bookingService.cancel(alice.getId(), booking.id()).cancelledAt()).isEqualTo(now);
    }

    @Test void formPostsCannotCancelBookings() throws Exception {
        var booking = bookingService.book(alice.getId(), request(0, 1));
        mvc.perform(post("/api/v1/bookings/{id}/cancel", booking.id()).with(user(AccountPrincipal.from(alice)))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).content("ignored=value"))
                .andExpect(status().isUnsupportedMediaType());
        assertThat(bookingService.get(alice.getId(), booking.id()).status()).isEqualTo(Booking.Status.CONFIRMED);
    }
}
