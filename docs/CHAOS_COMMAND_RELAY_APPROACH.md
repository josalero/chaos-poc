# Chaos Command Relay — Architecture (0.2.0)

## Executive summary

The relay submits a chaos command by asking Eureka for every UP instance of the target application and POSTing the command to each instance. The HTTP response is that instance's result. There is no message broker.

Each push carries a short-lived JWT issued by the local `auth-server` (`client_credentials`, scope `chaos.command`). The starter validates it as an OAuth2 resource server and authorizes `/internal/chaos/**` with `SCOPE_chaos.command`.

The public entry point is the Spring Cloud Gateway. nginx only serves the static verify UI. Runtime settings live in `config-repo/` and are served by the config server.

The as-built component view, the apply-command flow, and the reset-to-default flow are in [ARCHITECTURE.md](ARCHITECTURE.md). Step-by-step sequence diagrams (apply a scenario, token lifecycle, status aggregation, assault, rollback, lease expiry) are in [CHAOS_SCENARIO_SEQUENCES.md](CHAOS_SCENARIO_SEQUENCES.md).

## Stack

| Piece | Version |
| --- | --- |
| Java | 21 |
| Spring Boot | 4.0.6 |
| Spring Cloud | 2025.1.0 |
| Chaos Monkey | 4.0.0 |

These match `com.samba:springboot-4.0-bom:1.0.7`. The build uses `spring-boot-starter-parent` so images resolve from Maven Central. Bump Boot, Cloud, and the comment in the root `pom.xml` together when that BOM moves.

## Goals and non-goals

### Goals

- Apply one Chaos Monkey command to every live instance of a named service.
- Report a per-instance outcome and an aggregate status.
- Discover instance count and addresses from Eureka.
- Keep command delivery, config, and edge routing free of RabbitMQ.

### Non-goals

- Production identity. The local `auth-server` stands in for the platform identity provider (Okta in production). The operator console and submit API are still unauthenticated.
- Replacing Chaos Monkey. `chaos-lib` still calls the Chaos Monkey actuator on loopback.
- Kubernetes EndpointSlices. Documented below as the production alternative that keeps the same `DiscoveryClient` call.

## 2. High-Level Architecture

```text
Browser
  → gateway :18000
       ├─ /api/demo/**        lb://chaos-poc-demo
       ├─ /api/downstream/**  lb://chaos-poc-downstream
       ├─ /api/relay/**       lb://chaos-command-relay
       ├─ /chaos/**           http://chaos-command-relay-ui:80
       └─ /**                 http://chaos-poc-ui:80

Operator console (Vue) → POST /api/relay/internal/v1/chaos/commands
Relay → POST /internal/v1/chaos/commands
  → DiscoveryClient (UP instances only)
  → relay gets a client_credentials JWT from auth-server :9000 (cached until expiry)
  → async POST {instance}/internal/chaos/commands  (Authorization: Bearer <jwt>)
  → chaos-lib validates the JWT and the filter chain checks SCOPE_chaos.command
  → chaos-lib applies the actuator on 127.0.0.1
  → response body is the instance result

chaos-poc-demo → HTTP load-balanced client → chaos-poc-downstream
```

Detailed flows: [CHAOS_SCENARIO_SEQUENCES.md](CHAOS_SCENARIO_SEQUENCES.md).

`prefer-ip-address: true` is required. Without it Eureka advertises the Compose DNS name, and the relay would round-robin instead of reaching each instance.

### Instance count

The relay sets `expectedInstances` to the number of instances the command is sent to. `instanceSelection` defaults to `ALL`, which is every UP instance. `SOME` sends only the discovery instance ids in `instanceIds`; an id that is not UP is `400`. `eurekaUpCount` on the service status stays the live UP count, so a SOME command can be `APPLIED` with `expectedInstances` 1 while two replicas are still UP. The replicas that were not named keep their current assault. An empty registry returns `503` with status `NO_INSTANCES`. Reset and disable omit the selection, so they still reach every UP instance.

Options considered:

| Option | Why it was not used |
| --- | --- |
| Eureka push (chosen) | The call response is the per-instance result, and the same registry feeds the gateway. |
| Kubernetes EndpointSlices | Same `DiscoveryClient` shape via `spring-cloud-kubernetes`. Useful in a cluster. This POC runs on Compose, so Eureka is the registry. |
| Spring Cloud Bus | Needs RabbitMQ or Kafka, which this release removes. |
| Instances pull config | The relay would not get a per-instance result. |
| Config server plus `/actuator/refresh` | Refresh does not return a command result, so the aggregate status cannot be built. |

Eureka can still list an instance for a few seconds after it dies. The relay records `UNREACHABLE` for that address. The operator submits again. There is no broker redelivery.

## 3. Module Structure

| Module | Role |
| --- | --- |
| `chaos-lib` | `POST /internal/chaos/commands`, JWT resource server, actuator apply |
| `chaos-command-relay` | Submit API, discovery, HTTP fan-out |
| `chaos-command-relay-ui` | Vue 3 operator console (scenarios, publish, history, reset) |
| `chaos-poc-demo` | Order service and Chaos Monkey target |
| `chaos-poc-downstream` | Inventory and authorization target |
| `auth-server` | Spring Authorization Server; issues client_credentials JWTs to the relay |
| `discovery-server` | Eureka |
| `config-server` | Native config from `config-repo/` |
| `gateway` | Edge routes and sticky load balancing |
| `chaos-poc-ui` | Static verify UI |

## 4. Message Contract

The JSON body is the same `ChaosCommandMessage` / `ChaosCommandResult` records. Transport is HTTP, not a queue.

### 4.1 Inbound Command (`ChaosCommandMessage`)

`POST /internal/chaos/commands` with header `Authorization: Bearer <jwt>`. The JWT must be signed by the configured issuer and carry the `chaos.command` scope.

| Field | Type | Constraints |
| --- | --- | --- |
| `commandId` | UUID | Assigned by the relay when omitted |
| `environment` | String | Must be `test` |
| `targetApplication` | String | Must match `spring.application.name` |
| `action` | Enum | `CONFIGURE`, `ENABLE`, `DISABLE`, `CONFIGURE_AND_ENABLE` |
| `assault` | Object | Required for `CONFIGURE` and `CONFIGURE_AND_ENABLE` |
| `expiresAt` | Instant | Required for `ENABLE` and `CONFIGURE_AND_ENABLE`; must be in the future |
| `issuedBy` | String | Operator label |
| `correlationId` | String | Optional |

A mismatched environment or application, a missing expiry, or an already-expired command returns **409** with outcome `REJECTED`. A missing, expired, or badly signed token returns **401**. A valid token without the `chaos.command` scope returns **403**.

### 4.2 Assault Configuration (`ChaosAssaultConfig`)

| Field | Maps to the Chaos Monkey actuator |
| --- | --- |
| `level` | Assault frequency (1–10000) |
| `deterministic` | With level 1, every matched call is assaulted |
| `latencyActive` / `exceptionsActive` | Assault toggles |
| `latencyRangeStart` / `latencyRangeEnd` | Milliseconds |
| `watchedCustomServices` | Fully qualified `com.example.Service.method` entries |
| `exception` | Chaos Monkey exception descriptor |

`watchedCustomServices` needs the fully qualified class name. The demo wraps downstream calls in `InventoryGateway` and `AuthorizationGateway` so Chaos Monkey can assault those `@Service` methods.

### 4.3 Outbound Result (`ChaosCommandResult`)

`chaos-lib` returns this JSON as the HTTP body. The relay stores it.

| Field | Description |
| --- | --- |
| `commandId` | Same id as the request |
| `targetApplication` | Echo |
| `podName` | `samba.chaos.command.pod-name`, set to `spring.application.name` |
| `outcome` | `SUCCESS`, `ACTUATOR_ERROR`, `REJECTED`, or `UNREACHABLE` |
| `failedStep` | Actuator path that failed |
| `httpStatus` | Status from the failed actuator call, or 409 when rejected |
| `reportedAt` | Timestamp |

`UNREACHABLE` means the relay could not open a connection, or could not get an access token (logged at ERROR with the OAuth2 error code). `REJECTED` means the instance answered with an error status (409 for a rejected command, 401/403 for a token problem). Any outcome other than `SUCCESS` increments `failureCount`. The aggregate becomes `FAILED` when any instance failed, `APPLIED` when every expected instance succeeded, and `TIMED_OUT` when the status window closes early.

### 4.4 Aggregate Status

| Status | Condition |
| --- | --- |
| `PENDING` | No instance has reported yet, and the window is open |
| `PARTIAL` | Some instances reported `SUCCESS`, the rest have not reported, and the window is open |
| `APPLIED` | Every selected instance reported `SUCCESS` |
| `FAILED` | At least one result is not `SUCCESS` |
| `TIMED_OUT` | The window closed before every instance reported |

## 5. Chaos Library (`chaos-lib`)

Maven artifact `com.samba.chaos:chaos-command-spring-boot-starter` (module directory `chaos-lib`). Packages follow the same layers as the other modules: `config` (properties, security, retry), `web` (the command endpoint), `service` (apply, actuator, expiry, metrics), `command` (the JSON contract), and `exception`. `ChaosAutoConfiguration` stays in `com.samba.chaos` and registers its beans explicitly.

Active when `samba.chaos.command.enabled=true`. The flag defaults to false, so the relay can depend on the starter for the JSON contract without opening the endpoint. When the flag is true, an environment post-processor adds the `chaos-monkey` profile (Chaos Monkey 4 will not load without it), applies idle Chaos Monkey defaults, and refuses to start if the profile or `samba.chaos.command.environment` is production.

The library registers its own `SecurityFilterChain`, matched only on `/internal/chaos/**` and ordered first. It is an OAuth2 resource server: Boot builds the `JwtDecoder` from the host's `spring.security.oauth2.resourceserver.jwt.issuer-uri` and `jwk-set-uri`. The chain requires `SCOPE_chaos.command`. Host routes are untouched, but because Spring Security is on the classpath the host must declare its own chain for its other routes (the demo apps declare a `permitAll` chain). Idempotent repeats return the previous result and do not reapply the assault.

Actuator calls use `RestClient` and Framework 7 `RetryTemplate` (`FixedBackOff`). 503, 504, and connection failures retry. Other HTTP statuses become `ACTUATOR_ERROR`.

## 6. Command Relay (`chaos-command-relay`)

`POST /internal/v1/chaos/commands` validates the body, resolves UP instances, saves the command with `expectedInstances = count`, returns **202**, then fans out on virtual threads.

Fan-out uses `ChaosCommandClient`, a `@FeignClient`. Each call passes the discovered instance `URI`, so one client posts to every pod instead of letting the load balancer pick one. A Feign `RequestInterceptor` adds the Bearer token from the `chaos-command-relay` client registration (`client_credentials`, scope `chaos.command`). If an instance answers 401/403, the cached token is dropped and the next call requests a new one. Probe and admin reset use the same discovery list. There is no static URL map.

## 9.2 Target Service (embedding checklist)

1. Depend on `com.samba.chaos:chaos-command-spring-boot-starter`.
2. Set `samba.chaos.command.enabled=true` and `spring.security.oauth2.resourceserver.jwt.issuer-uri` / `jwk-set-uri` for the issuer the relay uses. Do not enable this in production.
3. Declare a `SecurityFilterChain` for the host's own routes (the starter only secures `/internal/chaos/**`).
4. Register with Eureka under the name in `chaos.relay.allowed-target-applications`.

## 11. Test scenarios and isolation model

Payloads in `scenarios/` are unchanged. `./scenarios/tests/verify-scenarios.sh` submits each one, waits for `APPLIED` or `FAILED`, and rolls back with `disable.json`. The runner rewrites `expiresAt` to a ten-minute lease.

## 14. Local development without Docker

Start Eureka, the config server, and the auth server first (`docker compose up -d chaos-discovery-server chaos-config-server chaos-auth-server`), then:

```bash
jenv shell 21
mvn clean verify
```

`spring.config.import` points at `http://localhost:8888` by default. Tests set `spring.cloud.config.enabled=false` and `eureka.client.enabled=false`.

## 15. Testing strategy

- chaos-lib: full auto-configuration with MockMvc and `jwt()` for 401 (no token), 403 (wrong scope), 200, 409, and host routes left open; `MockRestServiceServer` for actuator retry.
- auth-server: token endpoint (valid grant, wrong secret, unknown scope), JWK set, health-only public surface.
- Relay: `SimpleDiscoveryClient` plus a stub `feign.Client` stands in for two instances. No Testcontainers broker.
- Gateway: route definitions and the sticky-instance response cookie.

## Removed in 0.2.0

Removed in this release, with no compatibility shim:

- RabbitMQ, the fanout exchange, the results queue, and Testcontainers RabbitMQ
- `ChaosCommandListener`, `ChaosCommandResultPublisher`, `ChaosCommandPublisher`, `ChaosCommandResultConsumer`
- Spring Retry. The command client uses OpenFeign (`@FeignClient`), the same client style as the platform services.
- Jackson 2 (`com.fasterxml.jackson.databind`). JSON uses Jackson 3 (`tools.jackson`)
- Static `expected-instances-fallback`, `actuator-base-urls`, and `admin-base-urls`
- nginx proxy routes. nginx serves the SPA only
- Legacy prefixes `chaos.listener.*` and `chaos.command-listener.*` (the current prefix is `samba.chaos.command`)
- `CHAOS_EXPECTED_DEMO` and `CHAOS_EXPECTED_DOWNSTREAM`
- The shared `X-Chaos-Token` header, `ChaosCommandTokenVerifier`, `samba.chaos.command-token`, and `CHAOS_COMMAND_TOKEN` (replaced by OAuth2 JWTs)

## Security

Commands are authorized with short-lived (5 minute) JWTs from `auth-server`. The relay authenticates to it with `client_secret_basic`; the secret is `CHAOS_RELAY_CLIENT_SECRET` (POC default `local-poc-only`, set on both `chaos-auth-server` and `chaos-command-relay`). The signing key is generated at auth-server startup, so restarting it invalidates outstanding tokens; resource servers refetch the JWK set on an unknown key id. `CHAOS_AUTH_ISSUER` must be identical on the auth server and the resource servers because it is checked against the `iss` claim. Do not log tokens or the client secret.

Known gaps: the operator console and submit API have no inbound authentication, and the Chaos Monkey actuator (`/actuator/chaosmonkey`) on each instance is still open, so it can be called directly without a JWT. Do not log driver or customer identifiers; this POC does not process that data, and the platform rule still applies to anything added later.

## Operator console

The console is the control plane for `chaos.relay.allowed-target-applications`. The verify UI remains the data-plane observer. **Postman collection** in the console rail downloads `chaos-command-relay.postman_collection.json`. Its `baseUrl` defaults to `http://localhost:18000/api/relay`.

| Page | Route | What it shows |
| --- | --- | --- |
| Services | `/chaos/` | Allowlisted services, Chaos Monkey on/off/unknown, config state, UP count, last command. The browser filters, sorts, and pages that list. |
| Service | `/chaos/services/{name}` | Overview, command history, live actuator (one card per UP instance, refreshed every 5 seconds while the section is open), and Apply. |
| Commands | `/chaos/commands` | Every stored command, newest first. |
| Saved | `/chaos/saved` | Every per-service catalog row. Apply opens that service with the assault filled in. |

`GET /internal/v1/chaos/services` reads the latest stored command and a background cache of `GET /actuator/chaosmonkey/status`. It does not call the actuator. `cmEnabled` is null until a pod has answered, and the list shows unknown. The service page and its actuator section still probe live. The actuator snapshot includes `instances`, one row per UP instance.

`GET /internal/v1/chaos/commands` pages stored commands (`page`, `size` default 50 and at most 200, optional `application`, `status`, and `action`). Status is computed when the page is read. `GET /internal/v1/chaos/catalog` lists saved assaults for the allowlist, with an optional `application` filter.

Command history is an H2 file (`CHAOS_RELAY_DATA_DIR`, Compose volume `chaos-relay-data`). Records stay until someone deletes the volume. Stored fields are command metadata and operator names such as `chaos-console`.

Per-pod results are keyed by the Eureka instance id the relay dispatched to. Two replicas that both report `podName` equal to `spring.application.name` still produce two results.

Each service also has a catalog in the same H2 file. `GET` and `POST /internal/v1/chaos/services/{name}/catalog` list and save a label, action, and assault. Saving the same label again replaces that entry. `DELETE .../catalog/{catalogId}` removes one. Apply still uses `POST /internal/v1/chaos/commands`. The Apply form can save the assault and then publish it.

`POST /internal/v1/chaos/services/reset` takes `{ "issuedBy", "applicationNames" }`. An empty list or a name outside the allowlist is 400 and publishes nothing. Otherwise the relay returns 202 with one command id per name and does not wait. The one-service reset still waits. Presets with no `targetApplication` are templates and can be applied to any allowlisted name.
