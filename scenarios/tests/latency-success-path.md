# TC-CHAOS-007: Success path latency (200/201)

| Field | Value |
|-------|-------|
| **Test case ID** | TC-CHAOS-007 |
| **Priority** | P1 |
| **Type** | Manual — UI only |
| **Interface** | Chaos POC Console |
| **URL** | http://localhost:18000 |
| **Category** | HTTP success |
| **Command payload** | [`../latency-success-path.json`](../latency-success-path.json) |
| **UI scenario label** | Success path latency (200/201) |
| **Watched method** | `OrderController.create` |

## Goal

Validate latency on `OrderController.create` without exceptions: **HTTP 201** with measurably increased response time.

## Isolation

| Phase | Action |
|-------|--------|
| **Setup** | **Reset configuration** → **Disabled**. |
| **Execute** | Baseline create, apply scenario, compare timing (steps 2–5). |
| **Teardown** | **Reset configuration** → **Create order** **HTTP 201** at normal speed. |

## Preconditions

- Stack started: `docker compose up --build -d`
- Browser open at http://localhost:18000
- **Connection** card shows **Relay OK** and **Demo OK**

## Test steps

| Step | Action | Expected result |
|------|--------|-----------------|
| 1 | **Setup** — **Reset configuration**. | **Chaos Monkey state** → **Disabled**. |
| 2 | **Create order**; note duration (ms). | **HTTP 201**; baseline within this test. |
| 3 | Apply **Success path latency (200/201)** only. | **APPLIED**. Latency 100–400 ms on `OrderController.create`. |
| 4 | Review **Chaos Monkey state**. | Latency **Active**; exceptions **Off**. |
| 5 | **Create order** again; compare to step 2. | **HTTP 201**; duration ≥ ~100 ms higher than step 2. |
| 6 | **Teardown** — **Reset configuration**. | **Disabled**; **Create order** at normal speed. |

## Acceptance criteria

| ID | Given | When | Then |
|----|-------|------|------|
| AC-007-1 | Scenario applied after setup | Apply completes | **APPLIED** **1/1** |
| AC-007-2 | Assault active | Operator reviews state | Latency on `OrderController.create`; exceptions off |
| AC-007-3 | Latency-only assault | **Create order** | **HTTP 201** with valid body |
| AC-007-4 | Baseline from step 2 | Post-assault **Create order** | Duration exceeds baseline by ~100 ms |
| AC-007-5 | Teardown completed | **Create order** | Normal speed **HTTP 201** |

## Postconditions

- Default configuration restored
