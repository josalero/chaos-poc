# Chaos Command Relay POC

Local proof-of-concept for a **dedicated chaos relay**: operator submits a command, the relay fans out over RabbitMQ, target pods apply Chaos Monkey via loopback actuator, and the operator polls for per-pod apply status.

This repo validates the **control plane** (relay → bus → listener → actuator). It does not ship production auth, persistence, or service discovery.

## What is in scope

| In scope | Out of scope |
|----------|--------------|
| POST/GET chaos command API | Okta / admin RBAC |
| Fanout publish + result aggregation | Redis / durable command store |
| Embeddable listener library | Eureka instance counting |
| Docker Compose full stack | K8s / test-namespace deploy |
| Fake CM actuator for wiring checks | Production auth / multi-env rollout |

## Modules

| Module | Purpose |
|--------|---------|
| `chaos-listener-lib` | Bus message contract (`com.samba.chaos.listener.message`), fanout listener, actuator client |
| `chaos-command-relay` | Validate → publish → aggregate results → status API + console (`com.samba.chaos.relay.model`) |
| `chaos-poc-downstream` | Mock inventory/auth service (Feign target for the demo) |
| `chaos-poc-demo` | Target app with real Chaos Monkey + listener + Feign clients |
| `chaos-poc-ui` | React verify surface — monitor CM state and exercise demo APIs (operator control on relay) |

## Architecture

```
POST /internal/v1/chaos/commands
  → relay (validate, store PENDING)
  → Rabbit fanout chaos.commands.test
  → listener on target pod (filter by targetApplication)
  → POST http://127.0.0.1:8080/actuator/chaosmonkey/*
  → ChaosCommandResult → chaos.command-results
  → GET /internal/v1/chaos/commands/{commandId}  →  APPLIED | FAILED | TIMED_OUT
```

## Quick start (Docker Compose)

```bash
docker compose up --build -d
```

Open the **verify UI**: http://localhost:18000  
Open the **operator console** (apply scenarios, disable, reset): http://localhost:18090/chaos  
(In Docker Compose the verify UI links to the relay console at `/operator/`.)

1. In the **operator console**, select a service, apply a scenario preset, and wait until status is `APPLIED`
2. In the **verify UI**, pick **chaos-poc-demo** or **chaos-poc-downstream**, confirm **Chaos Monkey state** matches the scenario (auto-refreshes every 3s)
3. Use the **API playground** and **Live telemetry** to exercise endpoints and observe latency/errors

Default demo API URL in the verify UI: `/api/demo` (proxied to `chaos-poc-demo`)

### CLI alternative

Submit the default scenario:

```bash
curl -s -X POST http://localhost:18090/internal/v1/chaos/commands \
  -H 'Content-Type: application/json' \
  -d @sample-command.json
```

Poll until terminal status (`APPLIED`, `FAILED`, or `TIMED_OUT`):

```bash
curl -s http://localhost:18090/internal/v1/chaos/commands/{commandId}
```

Confirm Chaos Monkey is loaded on the demo pod:

```bash
curl -s http://localhost:18080/actuator/chaosmonkey/status
curl -s http://localhost:18080/actuator/chaosmonkey/assaults
```

Stop:

```bash
docker compose down
```

### Compose services

| Service | Host port | Role |
|---------|-----------|------|
| `rabbitmq` | 5672, 15672 | Fanout + results queue (UI: `chaos` / `chaos`) |
| `chaos-poc-downstream` | **18081** → 8081 | Mock inventory/auth API |
| `chaos-poc-demo` | **18080** → 8080 | Target app + listener + Chaos Monkey |
| `chaos-command-relay` | **18090** → 8090 | Command API |
| `chaos-poc-ui` | **18000** → 80 | React verify surface (monitor + API playground) |

Host ports `18080` / `18090` / `18000` avoid clashes with other local apps.

## DSU-1671 Plan A alignment

This POC is a **local Docker lab** for the dedicated chaos relay design in `data-solutions-ui-service` (Plan A). It mirrors the production shape without Postgres, Eureka, or Okta.

| Plan A concept | POC implementation |
|----------------|-------------------|
| `samba.chaos.command-listener.enabled` | Service-owned YAML: listener flag, RabbitMQ, `chaos.monkey.*`, actuator exposure |
| Listener profile `test` + `chaos-monkey` | Docker uses `docker,test` + `chaos-monkey` include; local uses `local,chaos-monkey` |
| Relay command store | In-memory (POC only) |
| Dashboard CM column | **Actuator primary** — probes `/actuator/chaosmonkey/status` when reachable |
| Reset CM vs clear demo data | **Split** — reset/disable assaults only; clear demo data is per-service on the detail page |
| Reset all | CM only on dashboard (no admin data POST) |
| Presets | DSUI reference commands + downstream-specific presets in relay console |
| Verify UI | Target selector: `chaos-poc-demo` or `chaos-poc-downstream` |

**Non-goals for this repo:** production auth, durable command store, Eureka instance counting, K8s deploy.

## Test scenarios

Use these to exercise Chaos Monkey on the demo target (real CM assaults on `OrderService`, `InventoryClient`, etc.).

**Structured test plans** (goal, steps, acceptance criteria) live in [`scenarios/tests/`](scenarios/tests/README.md) — one Markdown file per command payload. All tests are executed through the **Chaos POC Console** UI.

| Test plan | Command payload |
|-----------|-----------------|
| [bean-interceptor.md](scenarios/tests/bean-interceptor.md) | [bean-interceptor.json](scenarios/bean-interceptor.json) |
| [service-to-service-latency.md](scenarios/tests/service-to-service-latency.md) | [service-to-service-latency.json](scenarios/service-to-service-latency.json) |
| [high-pressure-latency.md](scenarios/tests/high-pressure-latency.md) | [high-pressure-latency.json](scenarios/high-pressure-latency.json) |
| [exception-http-404.md](scenarios/tests/exception-http-404.md) | [exception-http-404.json](scenarios/exception-http-404.json) |
| [exception-http-403.md](scenarios/tests/exception-http-403.md) | [exception-http-403.json](scenarios/exception-http-403.json) |
| [exception-http-409.md](scenarios/tests/exception-http-409.md) | [exception-http-409.json](scenarios/exception-http-409.json) |
| [exception-http-500-create.md](scenarios/tests/exception-http-500-create.md) | [exception-http-500-create.json](scenarios/exception-http-500-create.json) |
| [exception-http-500-submit.md](scenarios/tests/exception-http-500-submit.md) | [exception-http-500-submit.json](scenarios/exception-http-500-submit.json) |
| [latency-success-path.md](scenarios/tests/latency-success-path.md) | [latency-success-path.json](scenarios/latency-success-path.json) |
| [disable.md](scenarios/tests/disable.md) | [disable.json](scenarios/disable.json) |

## Stack versions

Aligned with **data-solutions-ui-service** (`samba-external-parent-bom` 4.0.9):

| Component | Version |
|-----------|---------|
| Java | 17 |
| Spring Boot | 3.0.9 |
| Spring Cloud | 2022.0.4 |
| Testcontainers | 1.19.3 |
| Chaos Monkey | 3.1.0 |

The demo requires the `chaos-monkey` profile and service-owned `chaos.monkey.*` / actuator config (see embedding section). Docker Compose activates the listener via `docker,test`.

All samples use `targetApplication: "chaos-poc-demo"` (the demo app's `spring.application.name`). Change this to match your target when embedding `chaos-listener-lib`.

| Scenario | Goal | CM levers | Sample file |
|----------|------|-----------|-------------|
| **Bean interceptor** | Fail a specific `@Service` / `@Component` method | `watchedCustomServices` + `exceptionsActive` | `scenarios/bean-interceptor.json` |
| **Service-to-service** | Degrade an outbound HTTP/Feign call | `watchedCustomServices` on client method + `latencyActive` | `scenarios/service-to-service-latency.json` |
| **High pressure** | Slow many calls under load | `latencyActive`, `level`, wide `latencyRange` | `scenarios/high-pressure-latency.json` |
| **HTTP 404** | Downstream not found | `exceptionsActive` + exception mapped to 404 | `scenarios/exception-http-404.json` |
| **HTTP 403** | Forbidden downstream | `exceptionsActive` + exception mapped to 403 | `scenarios/exception-http-403.json` |
| **HTTP 409** | Conflict / duplicate | `exceptionsActive` + exception mapped to 409 | `scenarios/exception-http-409.json` |
| **HTTP 500 — create** | Unexpected failure while creating | `RuntimeException` on `OrderService.placeOrder` | `scenarios/exception-http-500-create.json` |
| **HTTP 500 — submit** | Unexpected failure while submitting | `RuntimeException` on `OrderService.submitOrder` | `scenarios/exception-http-500-submit.json` |
| **Success paths (200/201)** | Latency only — do not throw | `latencyActive` on handler method, `exceptionsActive: false` | `scenarios/latency-success-path.json` |
| **Disable** | Turn assaults off | `action: DISABLE` | `scenarios/disable.json` |

Run any scenario:

```bash
curl -s -X POST http://localhost:18090/internal/v1/chaos/commands \
  -H 'Content-Type: application/json' \
  -d @scenarios/bean-interceptor.json
```

### Bean interceptor

Targets one method via Chaos Monkey's custom watcher:

```json
"watchedCustomServices": ["com.samba.chaos.demo.service.OrderService.placeOrder"]
```

Format: **fully qualified** `ClassName.methodName` (e.g. `com.example.app.service.FooService.bar`). Short names like `OrderService.placeOrder` are **not** matched by Chaos Monkey.

For outbound Feign calls, wrap the client in a `@Service` gateway bean (see `InventoryGateway`, `AuthorizationGateway` in the demo) — JDK Feign proxies are not assaulted directly.

### Service-to-service

Same `watchedCustomServices` mechanism on a **gateway** `@Service` that delegates to Feign:

```json
"watchedCustomServices": ["com.samba.chaos.demo.service.InventoryGateway.getStock"],
"latencyActive": true,
"latencyRangeStart": 2000,
"latencyRangeEnd": 5000
```

Validates timeout handling, circuit breakers, and user-facing error mapping when a dependency is slow.

### High pressure

Inject wide latency on a hot path (e.g. submit) to observe slow responses under assault:

```json
"level": 1,
"latencyActive": true,
"latencyRangeStart": 500,
"latencyRangeEnd": 3000,
"deterministic": true,
"watchedCustomServices": ["com.samba.chaos.demo.web.OrderController.submit"]
```

Use `level: 1` with `deterministic: true` in the POC so every call is assaulted. In production load tests, raise `level` to reduce assault frequency while your load generator runs.

### HTTP status codes (404, 403, 409, …)

Chaos Monkey **exception assaults throw Java exceptions**; HTTP status is determined by your target's exception handling (e.g. `@ControllerAdvice`, Feign error decoder).

Configure the assault exception type your app already maps to the desired status:

| Desired status | Typical approach |
|--------------|------------------|
| 404 | `ResourceNotFoundException` on `InventoryGateway.getStock` (demo maps to 404) |
| 403 | `com.samba.chaos.demo.exception.ForbiddenException` or similar domain type |
| 409 | Domain conflict exception your handler maps to 409 |
| 500 | `RuntimeException` on the create or submit method (demo maps it to a safe 500 payload) |
| 200 / 201 | Prefer **latency** assaults on the success method — exceptions bypass normal success responses |

Example exception block (adjust `type` to match your target):

```json
"exception": {
  "type": "java.lang.IllegalArgumentException",
  "method": "<init>",
  "arguments": [
    { "type": "java.lang.String", "value": "Inventory SKU not found" }
  ]
}
```

Chaos Monkey validates that `type`, `method`, and `arguments` match a real constructor. Types like `feign.FeignException$NotFound` need complex Feign objects and will be rejected with `ACTUATOR_ERROR`.

After enabling, invoke the watched method and assert the target returns the expected status and customer-facing error payload.

### Command fields (all scenarios)

| Field | Notes |
|-------|-------|
| `environment` | Must be `"test"` in this POC |
| `targetApplication` | Must match `spring.application.name` on the target pod |
| `action` | `CONFIGURE`, `ENABLE`, `DISABLE`, `CONFIGURE_AND_ENABLE` |
| `expiresAt` | Required for `ENABLE` / `CONFIGURE_AND_ENABLE`; must be in the future |
| `issuedBy` | Audit label (operator id) |
| `correlationId` | Optional; ties command to a test run id |

## Chaos POC Console (React UI)

### Docker (included in compose)

Included in `docker compose up` — open http://localhost:18000

### Live telemetry

The **Live telemetry** panel charts real demo API responses from both playground actions and an optional repeating probe:

- Rolling response-latency graph with status-colored points
- p50 and p95 latency, error rate, and sample count
- 2xx / 4xx / 5xx response distribution
- Live Chaos Monkey enabled/disabled and assault-type indicators
- **Create only** and **Create + submit** probe paths with selectable pacing

Start the probe before or after applying a scenario to watch the graph change. Stop it before teardown; resetting CM configuration also stops the probe and clears telemetry.

### Operator console — reset vs clear

| Action | Scope | Effect |
|--------|-------|--------|
| **Reset CM configuration** | Per service (detail page) | Publishes DISABLE, waits for APPLIED |
| **Clear demo data** | Per service (detail page only) | POST to service admin reset endpoint |
| **Reset all CM** | Dashboard / header | DISABLE on every allowed target — no demo data clear |

### Local dev (hot reload)

```bash
# Stack must be running (compose or Maven)
docker compose up -d rabbitmq chaos-poc-demo chaos-command-relay

cd chaos-poc-ui
npm install
npm run dev
```

Open http://localhost:5173 — Vite proxies `/relay` and `/demo` to the compose ports when using default `vite.config.js` proxy (optional; UI defaults to direct localhost URLs).

## Quick start (local Maven)

```bash
docker compose up -d rabbitmq
jenv shell 17 && mvn clean install

# Terminal A — demo target
cd chaos-poc-demo && mvn spring-boot:run

# Terminal B — relay
cd chaos-command-relay && mvn spring-boot:run

curl -s -X POST http://localhost:8090/internal/v1/chaos/commands \
  -H 'Content-Type: application/json' \
  -d @scenarios/bean-interceptor.json
```

## Embedding the listener in a real target

1. Add dependency `chaos-listener-lib`
2. Add explicit dependencies on `spring-boot-starter-actuator` and `chaos-monkey-spring-boot` (optional in the lib — not transitive; same pattern as `audit-api` transport credentials in the host service).
3. Configure the listener, RabbitMQ, Chaos Monkey, and actuator exposure in service YAML:

```yaml
spring:
  profiles:
    active: test,chaos-monkey   # listener @Profile("test"); CM needs chaos-monkey

samba:
  chaos:
    command-listener:
      enabled: true
      rabbitmq:
        host: ${RABBITMQ_HOST}
        port: 5672
        username: ${RABBITMQ_USER}
        password: ${RABBITMQ_PASSWORD}

chaos:
  monkey:
    enabled: false
    watcher:
      service: true
      restController: false
    assaults:
      level: 1

management:
  endpoints:
    web:
      exposure:
        include: health,chaosmonkey
  endpoint:
    chaosmonkey:
      enabled: true
```

Override `pod-name` and `actuator-base-url` in environment-specific YAML (e.g. Docker/K8s). Exchange/queue names and retry settings use `ChaosListenerProperties` Java defaults unless overridden under `samba.chaos.command-listener.*`.

4. Add the target's `spring.application.name` to the relay allowlist

## Tests

```bash
mvn test
mvn -pl chaos-command-relay test -Dtest=ChaosCommandRelayIntegrationTest
```

Integration tests require Docker (skipped automatically when unavailable).
