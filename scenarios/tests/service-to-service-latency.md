# TC-CHAOS-002: Service-to-service latency

| Field | Value |
|-------|-------|
| **Test case ID** | TC-CHAOS-002 |
| **Priority** | P1 |
| **Type** | Manual — UI only |
| **Interface** | Chaos POC Console |
| **URL** | http://localhost:18000 |
| **Category** | Downstream |
| **Command payload** | [`../service-to-service-latency.json`](../service-to-service-latency.json) |
| **UI scenario label** | Service-to-service latency |
| **Watched method** | `InventoryGateway.getStock` |

## Goal

Validate that latency injected on an outbound gateway call slows order creation while the UI still reports **HTTP 201**.

## Isolation

| Phase | Action |
|-------|--------|
| **Setup** | **Reset configuration** → **Chaos Monkey state** **Disabled**. |
| **Execute** | Capture baseline timing, apply scenario, compare post-assault timing (steps 2–5). |
| **Teardown** | **Reset configuration** → **Create order** **HTTP 201**. |

Baseline timing is captured **within this test** after setup, not from a prior run.

## Preconditions

- Stack started: `docker compose up --build -d`
- Browser open at http://localhost:18000
- **Connection** card shows **Relay OK** and **Demo OK**

## Test steps

| Step | Action | Expected result |
|------|--------|-----------------|
| 1 | **Setup** — **Reset configuration**. | **Chaos Monkey state** → **Disabled**. |
| 2 | **Create order**; note duration (ms) in **Recent responses**. | **HTTP 201**; baseline duration (typically &lt; 500 ms). |
| 3 | Apply **Service-to-service latency** only. | **APPLIED**. **Payload preview** shows `latencyRangeStart` 2000 and `InventoryGateway.getStock`. |
| 4 | Review **Chaos Monkey state**. | **Latency assault** **Active**; **Exception assault** **Off**. |
| 5 | **Create order** again; compare duration to step 2. | **HTTP 201**; duration ≥ ~2000 ms higher than step 2. |
| 6 | **Teardown** — **Reset configuration**. | **Disabled**; **Create order** **HTTP 201** at normal speed. |

## Acceptance criteria

| ID | Given | When | Then |
|----|-------|------|------|
| AC-002-1 | Scenario applied after setup | Apply completes | **APPLIED** **1/1** |
| AC-002-2 | Assault active | Operator inspects state | Latency on `InventoryGateway.getStock`; exceptions off |
| AC-002-3 | Latency assault active | **Create order** | **HTTP 201** |
| AC-002-4 | Baseline from step 2 | Post-assault **Create order** | Duration exceeds baseline by ~2000 ms |
| AC-002-5 | Teardown completed | **Create order** | **HTTP 201** at normal speed |

## Postconditions

- Default configuration restored (**Disabled**, clean demo data)
