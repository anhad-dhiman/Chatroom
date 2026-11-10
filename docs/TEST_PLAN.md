# Test plan

## 1. Goals

- Catch regressions in the features users depend on most: signing in, sending and receiving messages in real time, and privacy of direct messages.
- Give fast feedback: most defects should be caught in under a minute by unit and integration tests, before slower browser tests run.
- Make every result visible: each push to GitHub runs the full pipeline and publishes per-test results, an HTML report and failure screenshots.

## 2. Scope

**In scope**
- Authentication: registration, login, logout, session restore, token validation
- Authorization: room and DM membership checks on every read and write, over both REST and WebSocket
- Rooms: create, browse/search, join, leave, name validation and uniqueness
- Direct messages: creation (one per pair), privacy, live appearance for the recipient
- Messages: send, history pagination, edit, soft delete, reactions, length limits
- Real-time behaviour: live delivery, edits, deletes, reactions, unread counts, reconnect re-sync
- UI behaviour: grouping, unread divider, dark mode, mobile layout
- Deployment artifact: the Docker image builds and runs against PostgreSQL

**Out of scope (for now)**
- Load and performance testing (see *Future work*)
- Cross-browser coverage beyond Chrome and Firefox
- Security penetration testing beyond the authorization checks listed above

## 3. Strategy: the test pyramid

| Layer | Tool | Count | What it proves | Runs |
|---|---|---|---|---|
| Unit | JUnit 5, Vitest | 30 | Pure logic is correct (token handling, room-name rules, state reducer, formatting) | Every push, seconds |
| Integration | Spring Boot Test + MockMvc, real STOMP client | 21 | Each REST endpoint and the WebSocket behave correctly with a real database and security config | Every push, ~20 s |
| Component | React Testing Library | 8 | UI components render and respond to user input correctly | Every push, seconds |
| End-to-end | Python + Selenium + pytest | 37 | The deployed app works for a real user in a real browser, including two users chatting live | Every push, after the fast layers pass |
| Smoke (deploy) | Docker Compose + curl | 3 checks | The production image starts, connects to PostgreSQL and serves the app | Every push |

Most tests sit in the fast lower layers. End-to-end tests cover only the critical user journeys, because they are slower and more sensitive to timing.

## 4. Test environment

| Environment | Database | Used by |
|---|---|---|
| In-memory H2 (PostgreSQL mode) | Recreated per run | Backend integration tests, E2E tests |
| PostgreSQL 16 in Docker | Fresh volume per CI run | Docker smoke test, local development |

The same Flyway migrations run in every environment, so schema problems surface in tests.

## 5. Entry and exit criteria

**A change may be merged when:**
- All CI jobs pass: lint, backend, frontend, end-to-end and Docker smoke test
- New behaviour comes with tests at the lowest layer that can prove it
- No test was skipped or disabled to get a green build

**A release is ready when:**
- `main` is green
- The smoke suite (`./run-tests.sh --e2e --smoke`) passes against the deployed URL

## 6. Defect management

Defects are tracked as GitHub issues using the **Bug report** template, which asks for:
summary, steps to reproduce, expected vs actual result, environment, severity and evidence (screenshots, logs or a failing test).

| Severity | Meaning | Example |
|---|---|---|
| Critical | Data loss, security hole, or the app is unusable | A user can read someone else's DMs |
| Major | A core feature is broken with no workaround | Messages don't arrive until refresh |
| Minor | A feature misbehaves but has a workaround | Unread badge doesn't clear until switching rooms |
| Trivial | Cosmetic | Misaligned icon in dark mode |

Every fixed defect gets a regression test at the lowest layer that reproduces it.

## 7. Quality metrics

| Metric | Where to see it |
|---|---|
| Pass/fail per test, per run | GitHub Actions "checks" (JUnit reports) |
| Suite durations | Job summary table written by `run-tests.sh` |
| Failure evidence | `e2e-report` artifact: HTML report + screenshots + server log |
| Build health over time | Actions history and the README status badge |
| Flaky tests | A test that fails and then passes on re-run with no code change is logged as a defect and fixed, not retried silently |

## 8. Risks and mitigations

| Risk | Mitigation |
|---|---|
| Timing-dependent (flaky) browser tests | Explicit waits only, no fixed sleeps; unique data per test; fresh browser per test |
| Tests passing locally but failing in CI | Local runs and CI both go through `run-tests.sh` |
| Test data collisions | Every test creates uniquely named users and rooms |
| In-memory broker and presence don't reflect a multi-instance deployment | Documented limitation; multi-instance testing planned with a broker relay |

## 9. Future work

- Load test of WebSocket fan-out (e.g. k6 or Gatling) to find message latency under concurrent users
- Accessibility checks (axe-core) in the E2E suite
- Run the smoke suite automatically against the deployed environment after each deploy
- Run the CI pipeline on a self-hosted Jenkins as well as GitHub Actions
