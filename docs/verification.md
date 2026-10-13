# Verification record

Verified locally on **13 October 2026** with Java 21, Maven 3.9.11, and MySQL Community Server 8.4.11 on Windows.

| Check | Result |
| --- | --- |
| Maven build and verification, H2 | 37 tests, 0 failures, 0 errors, 0 skipped |
| Maven build and verification, MySQL 8.4.11 | 37 tests, 0 failures, 0 errors, 0 skipped |
| Concurrent booking race | Eight simultaneous requests: one 201, seven 409s, one persisted booking |
| Concurrent availability creation | One window accepted, overlapping competitor rejected |
| Booking versus window closure | Only one operation can win; no confirmed booking in a closed window |
| Concurrent cancellation | Both responses agree; one retained cancelled booking |
| Live HTTP smoke test | Passed registration, resource creation, availability, booking, conflict, cancellation, rebooking, history, and cleanup |
| Swagger UI | HTTP 200 |
| OpenAPI JSON and YAML | HTTP 200; YAML exported to `docs/openapi.yaml` |
| Health endpoint | `UP` |
| JaCoCo line coverage | 343 / 374 executable lines, **91.7%** |
| JaCoCo branch coverage | 76 / 96 branches, **79.2%** |

The 37 tests consist of 10 pure interval unit tests and 27 Spring integration tests. Both database runs execute the same assertions. The MySQL run used a dedicated local server on port 3307 and a disposable `learnreserve_test` database, not an H2 emulation of MySQL.

Commands:

```powershell
.\mvnw.cmd clean verify
$env:MYSQL_TEST_URL = 'jdbc:mysql://127.0.0.1:3307/learnreserve_test?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true'
.\mvnw.cmd verify '-Dspring.profiles.active=test,mysql-test'
.\scripts\smoke-test.ps1
```

Reports are generated in `target/surefire-reports/` and `target/site/jacoco/`. Re-run `verify` to regenerate them; build outputs are intentionally excluded from Git.

The repository is published at [AdityaBansal0123/learnreserve](https://github.com/AdityaBansal0123/learnreserve). The H2 and MySQL test steps also passed on GitHub's Ubuntu runners in [the initial CI run](https://github.com/AdityaBansal0123/learnreserve/actions/runs/38447109153). The MySQL job runs against a MySQL 8.4.11 service container, and both jobs upload their test and coverage reports as workflow artifacts.

The full Docker Compose application stack has **not been executed locally**, because Docker is not installed in this environment. The runnable JAR, live local demo, and MySQL tests were executed directly; CI additionally verifies the backend against containerized MySQL.

The managed Flyway dependency emits an advisory about its stated tested MySQL version; the actual Flyway migration, Hibernate schema validation, and all tests passed against MySQL 8.4.11. Mockito's Spring test initialization also emits a Java-agent advisory under Java 21. These warnings do not skip or invalidate the tests.
