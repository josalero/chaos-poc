# Chaos Scenario Sequences

How a Chaos Monkey scenario travels from an operator to every instance of a target service, and how it is observed, expired, and rolled back. JSON request bodies sit under the diagrams that send them. The component view and the reset-to-default flow are in [ARCHITECTURE.md](ARCHITECTURE.md). Field rules and configuration are in [CHAOS_COMMAND_RELAY_APPROACH.md](CHAOS_COMMAND_RELAY_APPROACH.md).

| # | Diagram | Read it when you want to know… |
| --- | --- | --- |
| 1 | [Platform startup](#1-platform-startup) | what must be running before a command can be sent |
| 2 | [Apply a scenario (end to end)](#2-apply-a-scenario-end-to-end) | the full path of one command |
| 3 | [Inside one instance](#3-inside-one-instance-chaos-lib) | how chaos-lib authorizes and applies a command |
| 4 | [Access token lifecycle](#4-access-token-lifecycle) | when the relay calls the auth server |
| 5 | [Command status and aggregation](#5-command-status-and-aggregation) | how `APPLIED`, `FAILED`, and `TIMED_OUT` are decided |
| 6 | [Assault in action](#6-assault-in-action) | what happens to a real request while chaos is on |
| 7 | [Rollback and lease expiry](#7-rollback-and-lease-expiry) | how chaos is turned off, deliberately or automatically |
| 8 | [Automated scenario run](#8-automated-scenario-run) | what `verify-scenarios.sh` does |

Ports are the Compose defaults: gateway `18000`, relay `18090`, auth server `9000`, Eureka `8761`, config server `8888`.

## 1. Platform startup

Every Java service pulls its configuration from the config server and registers in Eureka. The auth server has no dependencies, so it starts on its own. Demo and downstream instances wait for it because chaos-lib validates tokens against its key set.

```mermaid
sequenceDiagram
    autonumber
    participant Eureka as discovery-server :8761
    participant Config as config-server :8888
    participant Auth as auth-server :9000
    participant Target as chaos-poc-demo / downstream (x2 each)
    participant Relay as chaos-command-relay :8090
    participant Gateway as gateway :18000

    Config->>Eureka: register
    Note over Auth: generates an RSA signing key<br/>and registers the relay client
    Target->>Config: GET config (application.yml + {app}.yml)
    Target->>Eureka: register with prefer-ip-address (one entry per container)
    Note over Target: chaos command starter active when<br/>samba.chaos.command.enabled=true
    Relay->>Config: GET config
    Relay->>Eureka: register, then fetch the registry every 5s
    Gateway->>Config: GET routes
    Gateway->>Eureka: fetch the registry for lb:// routes
```

## 2. Apply a scenario (end to end)

An operator applies a preset from the Vue console (`/chaos`), or a client posts a command to the relay API. The console builds the JSON (including a two-hour lease) and posts it. Both go through `ChaosCommandService.submit`. The relay answers **202** straight away and pushes the command to each UP instance in parallel on virtual threads.

```mermaid
sequenceDiagram
    autonumber
    actor Operator
    participant UI as chaos-command-relay-ui
    participant Gateway as gateway :18000
    participant Relay as chaos-command-relay
    participant Eureka as discovery-server
    participant Auth as auth-server
    participant PodA as demo instance A (chaos-lib)
    participant PodB as demo instance B (chaos-lib)

    Operator->>UI: Apply scenario at /chaos
    UI->>UI: load preset, set 2h lease (expiresAt)
    UI->>Gateway: POST /api/relay/internal/v1/chaos/commands
    Gateway->>Relay: POST /internal/v1/chaos/commands
    Relay->>Relay: validate environment, allowlist, action, assault
    alt validation fails
        Relay-->>UI: 400 field errors
        UI-->>Operator: show errors on /chaos
    end
    Relay->>Eureka: DiscoveryClient.getInstances("chaos-poc-demo"), UP only
    alt no UP instances
        Relay-->>UI: 503 NO_INSTANCES
        UI-->>Operator: show the field error
    end
    Relay->>Relay: save command, expectedInstances = 2
    Relay-->>UI: 202 PUBLISHED
    UI-->>Operator: navigate to /chaos/commands/{id}

    par fan-out on virtual threads
        Relay->>Auth: client_credentials token (only if none cached)
        Auth-->>Relay: JWT, scope chaos.command, valid 5 min
        Relay->>PodA: POST /internal/chaos/commands<br/>Authorization: Bearer JWT
        PodA-->>Relay: 200 ChaosCommandResult SUCCESS
    and
        Relay->>PodB: POST /internal/chaos/commands<br/>Authorization: Bearer JWT
        PodB-->>Relay: 200 ChaosCommandResult SUCCESS
    end
    Relay->>Relay: store each result, aggregate becomes APPLIED

    loop status page polling
        UI->>Gateway: GET /api/relay/internal/v1/chaos/commands/{id}
        Gateway->>Relay: GET /internal/v1/chaos/commands/{id}
        Relay-->>UI: status, successCount, per-instance outcomes
    end
```

The same flow without the console, using `scenarios/*.json`:

```bash
curl -s -X POST http://localhost:18090/internal/v1/chaos/commands \
  -H 'Content-Type: application/json' -d @scenarios/bean-interceptor.json
curl -s http://localhost:18090/internal/v1/chaos/commands/{commandId}
```

`POST /internal/v1/chaos/commands` (gateway: `POST /api/relay/internal/v1/chaos/commands`). Omit `instanceSelection` to reach every UP instance. `commandId` is optional; the relay generates it. `environment` must be `test`. `expiresAt` is required for `ENABLE` and `CONFIGURE_AND_ENABLE`. `assault` is required for `CONFIGURE` and `CONFIGURE_AND_ENABLE`.

Every UP instance (`scenarios/latency-success-path.json`):

```json
{
  "environment": "test",
  "targetApplication": "chaos-poc-demo",
  "action": "CONFIGURE_AND_ENABLE",
  "issuedBy": "poc-operator",
  "correlationId": "scenario-latency-200-201",
  "expiresAt": "2026-12-31T23:59:59Z",
  "assault": {
    "level": 1,
    "deterministic": true,
    "latencyActive": true,
    "latencyRangeStart": 100,
    "latencyRangeEnd": 400,
    "exceptionsActive": false,
    "watchedCustomServices": ["com.samba.chaos.demo.web.OrderController.create"]
  }
}
```

One instance (`scenarios/partial-instances.json`). Replace `instanceIds` with a discovery id from `GET /internal/v1/chaos/services/chaos-poc-demo` (`upInstanceIds`). An id that is not UP is **400**. `expectedInstances` becomes the size of `instanceIds`.

```json
{
  "environment": "test",
  "targetApplication": "chaos-poc-demo",
  "action": "CONFIGURE_AND_ENABLE",
  "issuedBy": "poc-operator",
  "correlationId": "scenario-partial-instances",
  "expiresAt": "2026-12-31T23:59:59Z",
  "instanceSelection": "SOME",
  "instanceIds": ["chaos-poc-demo-1"],
  "assault": {
    "level": 1,
    "deterministic": true,
    "latencyActive": true,
    "latencyRangeStart": 100,
    "latencyRangeEnd": 400,
    "exceptionsActive": false,
    "watchedCustomServices": ["com.samba.chaos.demo.web.OrderController.create"]
  }
}
```

Turn assaults off (`scenarios/disable.json`). No `assault` and no `expiresAt`. This is `ALL` unless `instanceSelection` is set.

```json
{
  "environment": "test",
  "targetApplication": "chaos-poc-demo",
  "action": "DISABLE",
  "issuedBy": "poc-operator",
  "correlationId": "scenario-disable"
}
```

Exception assault (`scenarios/exception-http-404.json`). `exception` is the Chaos Monkey exception descriptor.

```json
{
  "environment": "test",
  "targetApplication": "chaos-poc-demo",
  "action": "CONFIGURE_AND_ENABLE",
  "issuedBy": "poc-operator",
  "correlationId": "scenario-http-404",
  "expiresAt": "2026-12-31T23:59:59Z",
  "assault": {
    "level": 1,
    "deterministic": true,
    "latencyActive": false,
    "exceptionsActive": true,
    "watchedCustomServices": ["com.samba.chaos.demo.service.InventoryGateway.getStock"],
    "exception": {
      "type": "com.samba.chaos.demo.exception.ResourceNotFoundException",
      "method": "<init>",
      "arguments": [{ "type": "java.lang.String", "value": "Inventory SKU not found" }]
    }
  }
}
```

## 3. Inside one instance (chaos-lib)

Spring Security runs before the controller. The starter filter chain only matches `/internal/chaos/**`, checks the JWT signature, expiry, and issuer, and requires scope `chaos.command`. The applier rejects commands meant for another environment or application, returns the stored result for a repeated `commandId`, and drives the Chaos Monkey actuator on loopback.

```mermaid
sequenceDiagram
    autonumber
    participant Relay as chaos-command-relay
    participant Chain as chaos-lib SecurityFilterChain
    participant Auth as auth-server
    participant Endpoint as ChaosCommandEndpoint
    participant Applier as ChaosCommandApplier
    participant Actuator as ChaosActuatorClient
    participant CM as Chaos Monkey actuator 127.0.0.1
    participant Guard as ChaosExpiryGuard

    Relay->>Chain: POST /internal/chaos/commands + Bearer JWT
    opt signing key not cached yet
        Chain->>Auth: GET /oauth2/jwks
        Auth-->>Chain: public keys
    end
    alt missing, expired, wrong issuer, or bad signature
        Chain-->>Relay: 401
    end
    Chain->>Endpoint: authenticated JwtAuthenticationToken
    Note over Endpoint: @PreAuthorize("hasAuthority('SCOPE_chaos.command')")
    alt scope missing
        Endpoint-->>Relay: 403
    end
    Endpoint->>Applier: apply(message)
    alt wrong environment or application, or missing / past expiresAt
        Applier-->>Endpoint: REJECTED
        Endpoint-->>Relay: 409 REJECTED
    else commandId already processed
        Applier-->>Endpoint: stored result (assault not reapplied)
        Endpoint-->>Relay: 200 stored result
    else new command
        Applier->>Actuator: apply(action, assault)
        Note over Actuator,CM: 503, 504, and connection errors retry<br/>(max-apply-attempts, apply-backoff-ms)
        alt CONFIGURE_AND_ENABLE
            Actuator->>CM: POST /assaults (reset to defaults)
            Actuator->>CM: POST /assaults (scenario assault)
            Actuator->>CM: POST /enable
        else CONFIGURE
            Actuator->>CM: POST /assaults (reset to defaults)
            Actuator->>CM: POST /assaults (scenario assault)
        else ENABLE
            Actuator->>CM: POST /enable
        else DISABLE
            Actuator->>CM: POST /assaults (reset to defaults)
            Actuator->>CM: POST /disable
        end
        CM-->>Actuator: 200 per step
        Actuator-->>Applier: ok, or failedStep + httpStatus
        Applier->>Guard: afterSuccessfulApply(message)
        Note over Guard: ENABLE / CONFIGURE_AND_ENABLE schedule a DISABLE at expiresAt<br/>DISABLE cancels any pending one
        Applier-->>Endpoint: SUCCESS or ACTUATOR_ERROR
        Endpoint-->>Relay: 200 ChaosCommandResult
    end
```

The relay posts this body to `{instance}/internal/chaos/commands`. It is the command above with the relay-assigned `commandId`. `instanceSelection` and `instanceIds` are not on this body; the relay already chose which instances to call.

```json
{
  "commandId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "environment": "test",
  "targetApplication": "chaos-poc-demo",
  "action": "CONFIGURE_AND_ENABLE",
  "issuedBy": "poc-operator",
  "correlationId": "scenario-latency-200-201",
  "expiresAt": "2026-12-31T23:59:59Z",
  "assault": {
    "level": 1,
    "deterministic": true,
    "latencyActive": true,
    "latencyRangeStart": 100,
    "latencyRangeEnd": 400,
    "exceptionsActive": false,
    "watchedCustomServices": ["com.samba.chaos.demo.web.OrderController.create"]
  }
}
```

The instance answers with `ChaosCommandResult`:

```json
{
  "commandId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "targetApplication": "chaos-poc-demo",
  "podName": "chaos-poc-demo-1",
  "outcome": "SUCCESS",
  "failedStep": null,
  "httpStatus": null,
  "reportedAt": "2026-10-06T18:00:01Z"
}
```

`outcome` is `SUCCESS`, `ACTUATOR_ERROR`, or `REJECTED`. The relay records `UNREACHABLE` itself when the call never completes.

## 4. Access token lifecycle

The relay authenticates as the OAuth2 client `chaos-command-relay` and caches one token for all instances. Dispatch threads have no operator security context, so the token is requested for a fixed relay principal.

```mermaid
sequenceDiagram
    autonumber
    participant Dispatcher as ChaosCommandDispatcher
    participant Client as ChaosCommandClient (@FeignClient)
    participant Interceptor as OAuth2ClientHttpRequestInterceptor
    participant Manager as OAuth2AuthorizedClientManager
    participant Auth as auth-server
    participant Pod as instance (chaos-lib)

    Dispatcher->>Client: apply(UriBuilderFactory(instance URI), message)
    Client->>Interceptor: POST {instance}/internal/chaos/commands
    Interceptor->>Manager: authorize("chaos-command-relay")
    alt cached token still valid
        Manager-->>Interceptor: cached token
    else no token or expired
        Manager->>Auth: POST /oauth2/token<br/>grant_type=client_credentials, scope=chaos.command<br/>Basic client_id:secret
        alt bad secret or auth server down
            Auth-->>Manager: 401, or no response
            Manager-->>Dispatcher: OAuth2AuthorizationException
            Note over Dispatcher: outcome UNREACHABLE<br/>ERROR log with OAuth2 error code only
        end
        Auth-->>Manager: access_token (JWT, 5 min)
        Manager-->>Interceptor: new token
    end
    Interceptor->>Pod: request + Authorization: Bearer JWT
    alt 401 or 403
        Pod-->>Interceptor: rejected
        Interceptor->>Manager: drop cached token
        Interceptor-->>Dispatcher: RestClientResponseException
        Note over Dispatcher: outcome REJECTED with httpStatus<br/>the next command fetches a fresh token
    else accepted
        Pod-->>Dispatcher: ChaosCommandResult
    end
```

## 5. Command status and aggregation

Results are stored as they arrive. The aggregate status is calculated each time someone reads it. `expectedInstances` is the selected set: `ALL` is every UP instance, and `SOME` is the named discovery ids. A SOME command can be `APPLIED` while other replicas were never called.

```mermaid
sequenceDiagram
    autonumber
    participant Dispatcher as dispatch threads
    participant Store as InMemoryChaosCommandStore
    participant Status as ChaosCommandStatusService
    actor Reader as console / UI / script

    Dispatcher->>Store: addResult(instance A result)
    Dispatcher->>Store: addResult(instance B result)
    Reader->>Status: GET /internal/v1/chaos/commands/{id}
    Status->>Store: command + results
    Note over Status: rules are checked in this order
    alt any result is not SUCCESS
        Status-->>Reader: FAILED (UNREACHABLE, REJECTED, ACTUATOR_ERROR)
    else every expected instance reported SUCCESS
        Status-->>Reader: APPLIED
    else window (status-timeout-seconds, default 30) closed
        Status-->>Reader: TIMED_OUT
    else no instance has reported yet
        Status-->>Reader: PENDING
    else some instances reported SUCCESS, others not yet
        Status-->>Reader: PARTIAL
    end
    Note over Store: ChaosCommandStoreCleanup removes commands<br/>older than command-ttl-hours (24)
```

`GET /internal/v1/chaos/commands/{commandId}` has no body. A SOME command that landed on one of two instances looks like this:

```json
{
  "commandId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "status": "APPLIED",
  "targetApplication": "chaos-poc-demo",
  "action": "CONFIGURE_AND_ENABLE",
  "expectedInstances": 1,
  "successCount": 1,
  "failureCount": 0,
  "instanceSelection": "SOME",
  "instanceIds": ["chaos-poc-demo-1"],
  "instances": [
    {
      "podName": "chaos-poc-demo-1",
      "outcome": "SUCCESS",
      "reportedAt": "2026-10-06T18:00:01Z",
      "failedStep": null,
      "httpStatus": 200
    }
  ]
}
```

## 6. Assault in action

Once Chaos Monkey is enabled on an instance, its AOP watchers intercept matching beans. The verify UI and the scenario scripts use this to show the configured effect. The gateway uses sticky sessions, so one browser keeps hitting the same demo instance.

```mermaid
sequenceDiagram
    autonumber
    actor User as verify UI / curl
    participant Gateway as gateway :18000
    participant Demo as chaos-poc-demo
    participant CM as Chaos Monkey watchers (AOP)
    participant Gw as InventoryGateway / AuthorizationGateway
    participant Down as chaos-poc-downstream

    User->>Gateway: POST /api/demo/api/v1/orders/{orderId}/submit
    Gateway->>Demo: StripPrefix=2, sticky instance cookie
    Demo->>CM: OrderController.submit (restController watcher)
    alt latency assault matches
        CM->>CM: sleep latencyRangeStart..End ms
    end
    Demo->>CM: InventoryGateway.reserve (listed in watchedCustomServices)
    alt exception assault matches
        CM-->>Demo: throw the configured exception<br/>e.g. ResourceNotFoundException
        Demo-->>User: ApiExceptionHandler maps it to 403 / 404 / 409 / 500
    else no exception
        CM->>Gw: proceed
        Gw->>Down: HTTP (LoadBalancerClient picks an UP instance)
        Down-->>Gw: response
        Gw-->>Demo: response
        Demo-->>User: 2xx, possibly delayed
    end
```

Which bean is hit, and how, comes from the scenario's `assault` block. For example, `exception-http-404.json` watches `InventoryGateway.getStock` and throws `ResourceNotFoundException`.

The verify UI sends `POST /api/demo/api/v1/orders` (the gateway strips `/api/demo`):

```json
{ "sku": "sku-1", "quantity": 1 }
```

`POST /api/demo/api/v1/orders/{orderId}/submit` has no body.

To show the current state, the relay asks each instance's actuator directly:

```mermaid
sequenceDiagram
    autonumber
    actor UI as verify UI / console
    participant Gateway as gateway :18000
    participant Relay as chaos-command-relay
    participant Eureka as discovery-server
    participant Pods as each UP instance

    UI->>Gateway: GET /api/relay/internal/v1/chaos/services/{app}
    Gateway->>Relay: StripPrefix=2
    Relay->>Eureka: UP instances of {app}
    loop each instance
        Relay->>Pods: GET /actuator/chaosmonkey/status
        Relay->>Pods: GET /actuator/chaosmonkey/assaults
    end
    Relay-->>UI: eurekaUpCount, enabled/disabled per instance, assault, last command
```

## 7. Rollback and lease expiry

There are three ways to turn chaos off. All of them end in a `DISABLE` on each instance. That call first resets assaults to their defaults and then disables Chaos Monkey.

```mermaid
sequenceDiagram
    autonumber
    actor Operator
    participant Relay as chaos-command-relay
    participant Pod as each instance (chaos-lib)
    participant Guard as ChaosExpiryGuard
    participant CM as Chaos Monkey actuator

    rect rgb(235, 245, 255)
    Note over Operator,CM: A. Deliberate rollback (Reset CM configuration / Reset all / scenarios/disable.json)
    Operator->>Relay: POST /internal/v1/chaos/services/{app}/reset<br/>or POST /internal/v1/chaos/commands (DISABLE)
    Relay->>Pod: DISABLE (Bearer JWT), fan-out as in diagram 2
    Pod->>CM: POST /assaults (defaults), POST /disable
    Pod->>Guard: cancel the pending automatic disable
    Pod-->>Relay: SUCCESS
    end

    rect rgb(255, 245, 235)
    Note over Operator,CM: B. Lease expiry (no operator action needed)
    Guard->>Guard: expiresAt reached for the active commandId
    Guard->>CM: POST /assaults (defaults), POST /disable
    Note over Guard: skipped if a newer command replaced it<br/>chaos.expiry.auto_disable counter records success or failure
    end

    rect rgb(240, 255, 240)
    Note over Operator,CM: C. Re-enable the last configuration
    Operator->>Relay: POST /internal/v1/chaos/services/{app}/enable (needs expiresAt)
    Relay->>Relay: find the last CONFIGURE / CONFIGURE_AND_ENABLE assault
    Relay->>Pod: ENABLE, which schedules a new expiry
    end
```

The guard lives in each instance's memory. If an instance restarts, Chaos Monkey starts disabled again (`chaos.monkey.enabled: false`), so no assault survives the restart.

Reset and disable use `POST /internal/v1/chaos/services/{applicationName}/reset` (or `/disable`). `expiresAt` is ignored. Reset waits until the aggregate is terminal; disable returns **202** immediately. Both omit `instanceSelection`, so they reach every UP instance.

```json
{ "issuedBy": "chaos-console", "correlationId": "console-reset" }
```

Re-enable uses `POST /internal/v1/chaos/services/{applicationName}/enable`. `expiresAt` is required. The relay replays the last applied `CONFIGURE` or `CONFIGURE_AND_ENABLE` assault.

```json
{
  "issuedBy": "chaos-console",
  "correlationId": "console-enable",
  "expiresAt": "2026-10-06T20:00:00Z"
}
```

## 8. Automated scenario run

`./scenarios/tests/verify-scenarios.sh` runs every payload in isolation and always rolls back to the default configuration, even when a check fails.

```mermaid
sequenceDiagram
    autonumber
    participant Script as verify-scenarios.sh
    participant Relay as relay :18090
    participant Demo as demo instance (published port)

    Script->>Script: DEMO_URL from docker port chaos-poc-demo-1 8080
    loop each scenario in scenarios/*.json
        Script->>Relay: POST disable.json (setup rollback)
        Script->>Script: rewrite expiresAt to now + 10 min
        Script->>Relay: POST scenario payload
        loop until APPLIED or FAILED
            Script->>Relay: GET /internal/v1/chaos/commands/{id}
        end
        Script->>Demo: GET /actuator/chaosmonkey, /actuator/chaosmonkey/assaults
        Script->>Demo: exercise the endpoint, assert status code or latency
        Script->>Relay: POST disable.json (teardown rollback)
    end
    Script->>Relay: POST disable.json (suite-final)
    Script->>Script: All scenario checks passed (isolated, default configuration restored)
```
