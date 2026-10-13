# Design decisions

## 1. Database locking for interval consistency

The resource row is the coordination point. Resource writers acquire an exclusive row lock before reading mutable availability or bookings. This serializes only operations for one resource, works across application instances, and handles the empty-bookings case. Transactions use `READ_COMMITTED` explicitly to make committed results visible after a wait.

Optimistic locking could work with retry logic and a version update on the parent, but every booking must reliably update that version. Pessimistic locking makes the invariant easier to audit for this workload. Arbitrary overlapping intervals cannot be enforced by a simple MySQL unique index. Direct SQL writers are outside the guarantee.

Cancellation reads only a scalar resource ID before locking. It then loads the booking after the lock; loading the entity earlier would risk using stale first-level-cache state when two cancellations race.

The completion job performs a conditional bulk update on ended confirmed bookings. It does not acquire resource locks because those intervals are already in the past and cancellation requires a future start. The predicate makes repeated or concurrent scheduler executions safe, without a separate distributed scheduler lock.

## 2. Exclusive resources, arbitrary subintervals

The assignment says two users must not book the same resource/time. All resource types therefore have capacity one at the reservation level. A workshop is an exclusive session, not an event ticket. Availability is published as windows; learners may reserve subintervals. Windows themselves cannot overlap, which removes ambiguity about the owning window.

Multi-seat workshops would require a separate event inventory/capacity model and a transaction that locks inventory while allocating seats. They should not be bolted onto interval conflict logic with a boolean exception.

## 3. Preserve history

Cancellation changes state; it does not delete the booking. Resources are deactivated and windows closed rather than physically deleted. Completed and cancelled rows retain their timestamps and foreign keys. The resource's current name is included in booking responses; metadata is not versioned. For an audit-grade system, snapshot resource metadata and add an immutable event log.

## 4. Standard authentication over custom token code

HTTP Basic plus Spring Security keeps the assignment focused on correct booking behavior. BCrypt hashes are stored; raw passwords never appear in returned DTOs. Registration always assigns `LEARNER`, and only configured bootstrap credentials create an admin. Basic authentication requires TLS outside local development and incurs password verification per request. For a larger application, an external OIDC provider is preferable to hand-written JWT issuance and revocation.

All state-changing API operations require JSON. Cookie/session authentication and CORS are not enabled. If a separate browser application is introduced, reassess the authentication and CSRF model rather than copying these API-only settings blindly. Password reset, email verification, login throttling, and account lifecycle management are not implemented.

## 5. Time and completion

All application comparisons use `Instant` and an injected UTC `Clock`. API intervals are whole-second precision to prevent disagreement with SQL timestamp precision. Half-open intervals allow back-to-back sessions. No recurring availability or local-time recurrence rules are implemented; clients translate local time with an explicit offset before submitting.

Completion is eventually persisted on a 60-second schedule and catches up after restarts. Cancellation is time-checked independently, so a delayed scheduler does not reopen expired reservations. A job failure is logged by Spring's scheduler, and a later run retries the conditional update.

## 6. Test on the actual database

H2 makes review quick but does not prove MySQL locking behavior. The same integration suite is therefore runnable on MySQL 8.4 and is configured as a separate CI job. Tests use actual committed service transactions and concurrent threads, not repository mocks or a shared rollback-only test transaction. Clock-dependent cases avoid timing sleeps.

## Practical limits and next steps

- Resource-level locks serialize writes to a popular resource, even for unrelated times. Measure contention before considering finer coordination.
- Lock/deadlock failures return a retryable `409 RESOURCE_BUSY`; clients should retry with bounded jitter. A retry after an ambiguous successful creation can return `BOOKING_CONFLICT`; there is no idempotency-key replay mechanism.
- Availability responses are snapshots, never holds. Queries are limited to 31 days but not paginated by window count; extremely dense schedules would benefit from a minimum duration and a bounded window count.
- Booking history is paginated; completion updates all eligible rows in one transaction. Very large history tables would need batched completion and retention policy.
- Admin metadata updates use last-writer-wins ordering under a lock. An ETag/version precondition would let administrators reject stale edits.
- Only ownership-based learner history is exposed. Admin reporting, notifications, waitlists, recurring schedules, and cross-resource learner conflicts are outside scope.
- The initial admin bootstrap is intended for one provisioning instance. Multi-instance first startup should provision the admin once before scaling; the unique email constraint prevents duplicate accounts.
- Add request throttling, audit events, monitoring, backups, secret rotation, and deployment-specific TLS before operating a public production service.

These are stated tradeoffs, not claims that the assignment implementation is a complete production platform.
