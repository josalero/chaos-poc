# Chaos Command Relay POC

Local proof-of-concept for a **dedicated chaos relay**: operator submits a command, the relay asks Eureka for every UP instance and pushes the command to each one over HTTP with a short-lived JWT from the local auth server, the instance validates the token, applies Chaos Monkey via its loopback actuator, and the operator polls for per-instance apply status.

## Documentation map

| Doc | Use when you need… |
| --- | --- |
| **This README** | Run the stack, URLs, CLI, UI walkthrough, scenario index |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | As-built architecture, apply-command flow, reset-to-default flow |
| [`docs/CHAOS_COMMAND_RELAY_APPROACH.md`](docs/CHAOS_COMMAND_RELAY_APPROACH.md) | Message contracts, config reference, acceptance criteria, production gaps |
| [`docs/CHAOS_SCENARIO_SEQUENCES.md`](docs/CHAOS_SCENARIO_SEQUENCES.md) | Sequence diagrams: applying a scenario end to end, token flow, status aggregation, assaults, rollback and lease expiry |
| [`scenarios/tests/README.md`](scenarios/tests/README.md) | Step-by-step manual test plans (one file per scenario) |

Avoid duplicating architecture or message-contract detail here — that lives in [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) and the approach doc.

## Quick start (Docker Compose)

```bash
docker compose up -d --build --remove-orphans
```

By default **demo** and **downstream** start with **2 replicas** each (`deploy.replicas`). The relay counts UP instances from Eureka, so scaling does not need a static expected-count setting.

| Surface | URL |
| --- | --- |
| Verify UI (via the gateway) | http://localhost:18000 |
| Operator console | http://localhost:18000/chaos |
| Command API | http://localhost:18000/api/relay/internal/v1/chaos/commands |
| Eureka | http://localhost:8761 |
| Config server | http://localhost:8888 |
| Auth server (token endpoint, JWK set) | http://localhost:9000/oauth2/token, http://localhost:9000/oauth2/jwks |

1. In the **operator console**, select a service, apply a scenario preset, wait for `APPLIED` (expect **2** instance results per target)
2. In the **verify UI**, pick **chaos-poc-demo** or **chaos-poc-downstream** and confirm **Chaos Monkey state** (auto-refreshes every 3s)
3. Use **API playground** and **Live telemetry** to exercise endpoints and observe latency/errors

Stop: `docker compose down`

### Compose services

| Service | Host port | Role |
| --- | --- | --- |
| `chaos-discovery-server` | 8761 | Eureka registry |
| `chaos-config-server` | 8888 | Native config from `config-repo/` |
| `chaos-auth-server` | 9000 | Issues client_credentials JWTs for relay → chaos-lib calls |
| `chaos-gateway` | 18000 | API, console, and UI entry point |
| `chaos-poc-downstream` | 18181–18185 (×2) | Mock inventory/auth + chaos-lib |
| `chaos-poc-demo` | 18080–18084 (×2) | Primary target + chaos-lib + Chaos Monkey |
| `chaos-command-relay` | 18090 | Control plane API + console |
| `chaos-poc-ui` | (internal 80) | Static verify UI |

Host ports in the `1808x` range avoid clashes with other local apps.

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

Automated isolation run: `./scenarios/tests/verify-scenarios.sh`. Compose assigns the demo host port from `18080–18084`; the script uses the published port of `chaos-poc-demo-1` when `DEMO_URL` is unset.

The automated runner replaces preset expiry dates with a ten-minute lease. Every successful enable
also schedules a local automatic disable at `expiresAt`, providing a fail-safe if teardown is skipped.

Command fields, assault shapes, and FQN rules: see [Message contract](docs/CHAOS_COMMAND_RELAY_APPROACH.md#4-message-contract) and [Test scenarios](docs/CHAOS_COMMAND_RELAY_APPROACH.md#11-test-scenarios-and-isolation-model) in the approach doc.

## Verify UI

The verify surface is organized as an audience-facing **Chaos Observatory**:

- **System map:** live topology of the audience, demo service, downstream service, Eureka, and relay. Affected request paths turn amber when an active assault targets that route.
- **Service inspector:** effective Chaos Monkey mode, assault type, latency range, watched methods, lease expiry, per-service reset, and command history.
- **Outcome guide:** highlights whether the active configuration should produce a successful response, `403`, `404`, `409`, or `500`.
- **Run experiment:** endpoint exercises grouped by healthy and error paths with expected HTTP outcomes.
- **Live telemetry:** request latency, status distribution, and continuous probes.

The UI reads service history from
`GET /internal/v1/chaos/services/{applicationName}/history` and refreshes control-plane state every
three seconds.

### Live telemetry

- Rolling latency graph, p50/p95, error rate, 2xx/4xx/5xx breakdown
- Live CM enabled/disabled and assault-type indicators
- **Create only** and **Create + submit** probes with selectable pacing

Start the probe before or after applying a scenario. Resetting CM stops the probe and clears telemetry.

The operator console is the Vue 3 app `chaos-command-relay-ui`, served at http://localhost:18000/chaos. It calls the relay through `/api/relay/**`. Scenario presets are bundled in that app.

### Operator console — reset vs clear

| Action | Scope | Effect |
| --- | --- | --- |
| **Reset CM configuration** | Per service (detail page) | Pushes DISABLE, waits for APPLIED |
| **Clear demo data** | Per service (detail page only) | POST to service admin reset endpoint |
| **Reset all CM** | Dashboard / header | DISABLE on every allowed target — no demo data clear |

### UI hot reload

```bash
docker compose up -d chaos-discovery-server chaos-config-server chaos-auth-server chaos-poc-demo chaos-command-relay chaos-gateway
cd chaos-poc-ui && npm install && npm run dev
```

Open the verify UI at http://localhost:5173

Operator console:

```bash
cd chaos-command-relay-ui && npm install && npm run dev
```

Open http://localhost:5174/chaos/ (the dev server proxies `/api/relay` to the relay on port 18090).

## Local Maven (no full compose)

See [§14 Local Development](docs/CHAOS_COMMAND_RELAY_APPROACH.md#14-local-development-without-docker) in the approach doc. Short version:

```bash
docker compose up -d chaos-discovery-server chaos-config-server chaos-auth-server
jenv shell 21 && export JAVA_HOME="$(jenv prefix)" && mvn clean verify
cd chaos-poc-demo && mvn spring-boot:run    # terminal A
cd chaos-command-relay && mvn spring-boot:run  # terminal B
```

## Embedding chaos-lib

1. Add dependency `chaos-lib`
2. Declare `spring-boot-starter-actuator` and `chaos-monkey-spring-boot` in the host service (not transitive from the lib)
3. Point `spring.security.oauth2.resourceserver.jwt.issuer-uri` / `jwk-set-uri` at the auth server, and declare a `SecurityFilterChain` for the host's own routes (chaos-lib secures only `/internal/chaos/**`, requiring scope `chaos.command`)
4. Configure chaos-lib, Chaos Monkey, and the actuator — full YAML: [§9.2 Target Service](docs/CHAOS_COMMAND_RELAY_APPROACH.md#92-target-service-embedding-checklist)
5. Add the target's `spring.application.name` to the relay allowlist

## Tests

```bash
mvn test
mvn -pl chaos-command-relay test -Dtest=ChaosCommandRelayIntegrationTest
```

Strategy and layers: [§15 Testing Strategy](docs/CHAOS_COMMAND_RELAY_APPROACH.md#15-testing-strategy). Relay integration tests use an in-process discovery client and mock HTTP instances.

Runtime safety counters are available under `/actuator/metrics`, including
`chaos.commands.applied`, `chaos.command.results.republished`, and `chaos.expiry.auto_disable`.

## Stack

Java 21 · Spring Boot 4.0.6 · Spring Cloud 2025.1.0 · Chaos Monkey 4.0.0

Versions match `com.samba:springboot-4.0-bom:1.0.7`. This repo keeps the public Spring Boot parent so the Docker build does not need Artifactory.

Architecture and the apply / reset flows: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md). Goals, non-goals, and production gaps: [`docs/CHAOS_COMMAND_RELAY_APPROACH.md`](docs/CHAOS_COMMAND_RELAY_APPROACH.md).
