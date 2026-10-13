# Five-minute reviewer demo

## 1. Start and inspect

Run `./mvnw spring-boot:run -Dspring-boot.run.profiles=demo` (PowerShell: `scripts/run-demo.ps1`). Open `http://localhost:8080/swagger-ui.html`. Health is at `/actuator/health`.

Explain that the demo uses disposable H2 data for convenient review. The default application configuration and Docker Compose use MySQL. Tests run against both database engines.

## 2. Discover a resource and availability

Run `GET /api/v1/resources`, optionally filtering `type=STUDY_ROOM` and `q=quiet`. Note the stable pagination envelope.

For one returned ID, call `GET /api/v1/resources/{id}/availability` with `from` tomorrow at `00:00:00Z` and `to` the following day at `00:00:00Z`. The seed creates windows from tomorrow at 09:00 UTC for seven days. Copy a free interval and choose one hour inside it.

This endpoint requires no authentication and does not reveal learner details.

## 3. Reserve and demonstrate conflict

Authorize as `learner@learnreserve.local` / `DemoLearner!2026`. Submit:

```json
{
  "resourceId": 1,
  "startsAt": "<a future free interval start in ISO-8601>",
  "endsAt": "<one hour later, inside the same window>"
}
```

Use actual timestamps rather than the placeholders. Show `201 Created`, the `Location` header, and the confirmed record. Submit the same reservation again to show `409 BOOKING_CONFLICT`. Change the interval so it starts exactly when the existing booking ends; this is valid if it still fits the window.

Explain the SQL overlap predicate and resource-row lock. The lock works across application instances because MySQL holds it, and it exists even before any booking rows exist.

## 4. Cancel and inspect history

Call `POST /api/v1/bookings/{id}/cancel` with `Content-Type: application/json` and `{}`. Show `CANCELLED`, then book the released interval again. `GET /api/v1/bookings` retains both records. Repeating cancellation returns the same cancellation state.

Register a second learner through `/auth/register` if time permits. Switching credentials and accessing the first learner's booking returns `404`.

## 5. Admin and tests

Authorize as `admin@learnreserve.local` / `DemoAdmin!2026`. Create a resource, add a future window, and demonstrate that an overlapping availability window returns `409`. Deactivate the resource to stop new reservations while preserving existing bookings.

Run `./mvnw verify`. Open `ApiIntegrationTest.eightSimultaneousRequestsProduceExactlyOneWinner`: eight synchronized requests, one persisted success, seven conflicts. Explain that tests do not share a rollback-only transaction, so locking and commit visibility are actually exercised.

For an automated demonstration of the entire REST lifecycle, run `scripts/smoke-test.ps1` against the running demo. It creates a unique user/resource each time, asserts conflict and history behavior, cancels its bookings, and deactivates its resource afterward.
