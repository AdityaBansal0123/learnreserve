# Be ready to explain the implementation

Read the source and run the tests before presenting this project. These notes are prompts for understanding, not a script to memorize.

## Why is a transaction alone insufficient?

Two transactions can both read "no conflicts" before either writes. The check and insert must be serialized for the same resource. The parent resource's write lock creates that ordering. A transaction provides atomicity around the sequence; the lock provides coordination.

## Why lock the resource instead of the booking?

There may be no booking row to lock yet. A resource row always exists before booking, and is shared by all reservations for that resource. It also gives availability closure, cancellation, and deactivation one consistent coordination rule.

## What happens when two requests arrive together?

The first locks the row, validates availability, checks overlaps, inserts, and commits. The second waits for the lock. With `READ_COMMITTED`, it sees the first booking, throws a domain conflict, and rolls back. The HTTP response is 409. The test asserts this with eight simultaneous calls and a persisted row count.

## Why can't a unique constraint solve it?

Uniqueness of `(resource, start)` rejects exact start duplicates but permits `10:00–11:00` alongside `10:30–11:30`. Arbitrary intervals require the overlap predicate or database-specific exclusion constraints. This implementation targets MySQL.

## What is the overlap predicate?

`existing.startsAt < requested.endsAt && existing.endsAt > requested.startsAt`.

The strict inequalities mean an end touching another start is allowed. This handles exact matches, partial overlap, containment, and an interval enclosing another interval.

## How is authorization enforced?

Spring Security authenticates the email/password. Method security restricts administration to admins and booking operations to learners. The learner ID comes from the principal. Repository lookups include ownership; another learner's booking looks like a missing booking. A client cannot submit a learner ID or select their own role.

## Why no JWT?

It is not required for the booking problem. Standard Basic authentication gives a small, auditable API implementation, with BCrypt and TLS outside localhost. For a larger system, use an external OIDC provider. Stateless Basic still requires protecting credentials and controlling request volume; it is not a complete identity platform.

## What if the process crashes?

Before commit, MySQL rolls back and releases locks. After commit, the booking persists even if the client did not receive the response. A repeat booking returns conflict; idempotency-key replay could improve that experience. The completion job catches up ended bookings after restart.

## Does this scale?

Different resources can be booked concurrently. Writes for a single popular resource serialize, which is a conscious tradeoff. Conflict queries are indexed, and transactions are short with no external network calls inside them. Measure lock wait time before considering a more complicated scheme.

## Why H2 and MySQL tests?

H2 gives a quick setup, but compatibility mode is not proof of InnoDB behavior. The same suite runs against MySQL locally and in CI. Tests use actual transactions and commits; pure interval behavior is separately unit-tested.

## Where would you improve it next?

Choose based on requirements: idempotency keys for unreliable clients; OIDC and throttling for public access; immutable audit events for compliance; seat inventory for shared workshops; recurring schedules with explicit time-zone rules; batched completion for very large datasets. Avoid claiming these features already exist.
