# Chaos Command Relay POC

Local proof-of-concept for a **dedicated chaos relay**: operator submits a command, the relay fans out over RabbitMQ, target pods apply Chaos Monkey via loopback actuator, and the operator polls for per-pod apply status.

## Documentation map

| Doc | Use when you need… |
| --- | --- |
| **This README** | Run the stack, URLs, CLI, UI walkthrough, scenario index |
| [`docs/CHAOS_COMMAND_RELAY_APPROACH.md`](docs/CHAOS_COMMAND_RELAY_APPROACH.md) | Architecture, message contracts, config reference, acceptance criteria, production gaps |
| [`scenarios/tests/README.md`](scenarios/tests/README.md) | Step-by-step manual test plans (one file per scenario) |

Avoid duplicating architecture or message-contract detail here — that lives in the approach doc.

## Quick start (Docker Compose)

```bash
docker compose up --build -d
```

| Surface | URL |
| --- | --- |
| Verify UI | http://localhost:18000 |
| Operator console | http://localhost:18090/chaos |
| Command API | http://localhost:18090/internal/v1/chaos/commands |
| RabbitMQ management | http://localhost:15672 (`chaos` / `chaos`) |

1. In the **operator console**, select a service, apply a scenario preset, wait for `APPLIED`
2. In the **verify UI**, pick **chaos-poc-demo** or **chaos-poc-downstream** and confirm **Chaos Monkey state** (auto-refreshes every 3s)
3. Use **API playground** and **Live telemetry** to exercise endpoints and observe latency/errors

Stop: `docker compose down`

### Compose services

| Service | Host port | Role |
| --- | --- | --- |
| `rabbitmq` | 5672, 15672 | Fanout + results queue |
| `chaos-poc-downstream` | 18081 | Mock inventory/auth + listener |
| `chaos-poc-demo` | 18080 | Primary target + listener + Chaos Monkey |
| `chaos-command-relay` | 18090 | Control plane API + console |
| `chaos-poc-ui` | 18000 | Verify UI (proxies demo API + relay console) |

Host ports `18080` / `18090` / `18000` avoid clashes with other local apps.

### CLI

```bash
curl -s -X POST http://localhost:18090/internal/v1/chaos/commands \
  -H 'Content-Type: application/json' \
  -d @scenarios/bean-interceptor.json

curl -s http://localhost:18090/internal/v1/chaos/commands/{commandId}

curl -s http://localhost:18080/actuator/chaosmonkey/status
```

## Scenarios

JSON payloads live in [`scenarios/`](scenarios/). Manual test plans (goal, steps, acceptance criteria) live in [`scenarios/tests/`](scenarios/tests/README.md).

| Test plan | Payload |
| --- | --- |
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

Automated isolation run: `./scenarios/tests/verify-scenarios.sh`

Command fields, assault shapes, and FQN rules: see [Message contract](docs/CHAOS_COMMAND_RELAY_APPROACH.md#4-message-contract) and [Test scenarios](docs/CHAOS_COMMAND_RELAY_APPROACH.md#11-test-scenarios-and-isolation-model) in the approach doc.

## Verify UI

### Live telemetry

- Rolling latency graph, p50/p95, error rate, 2xx/4xx/5xx breakdown
- Live CM enabled/disabled and assault-type indicators
- **Create only** and **Create + submit** probes with selectable pacing

Start the probe before or after applying a scenario. Resetting CM stops the probe and clears telemetry.

### Operator console — reset vs clear

| Action | Scope | Effect |
| --- | --- | --- |
| **Reset CM configuration** | Per service (detail page) | Publishes DISABLE, waits for APPLIED |
| **Clear demo data** | Per service (detail page only) | POST to service admin reset endpoint |
| **Reset all CM** | Dashboard / header | DISABLE on every allowed target — no demo data clear |

### UI hot reload

```bash
docker compose up -d rabbitmq chaos-poc-demo chaos-command-relay
cd chaos-poc-ui && npm install && npm run dev
```

Open http://localhost:5173

## Local Maven (no full compose)

See [§14 Local Development](docs/CHAOS_COMMAND_RELAY_APPROACH.md#14-local-development-without-docker) in the approach doc. Short version:

```bash
docker compose up -d rabbitmq
jenv shell 17 && mvn clean install
cd chaos-poc-demo && mvn spring-boot:run    # terminal A
cd chaos-command-relay && mvn spring-boot:run  # terminal B
```

## Embedding the listener

1. Add dependency `chaos-listener-lib`
2. Declare `spring-boot-starter-actuator` and `chaos-monkey-spring-boot` in the host service (not transitive from the lib)
3. Configure listener + CM + actuator — full YAML: [§9.2 Target Service](docs/CHAOS_COMMAND_RELAY_APPROACH.md#92-target-service-embedding-checklist)
4. Add the target's `spring.application.name` to the relay allowlist

## Tests

```bash
mvn test
mvn -pl chaos-command-relay test -Dtest=ChaosCommandRelayIntegrationTest
```

Strategy and layers: [§15 Testing Strategy](docs/CHAOS_COMMAND_RELAY_APPROACH.md#15-testing-strategy). Integration tests require Docker (skipped when unavailable).

## Stack

Java 17 · Spring Boot 3.0.9 · Spring Cloud 2022.0.4 · Chaos Monkey 3.1.0 · Testcontainers 1.19.3

Modules, architecture, goals/non-goals, and production gaps: [`docs/CHAOS_COMMAND_RELAY_APPROACH.md`](docs/CHAOS_COMMAND_RELAY_APPROACH.md).
