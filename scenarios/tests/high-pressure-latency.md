# TC-CHAOS-003: High pressure latency

| Field | Value |
|-------|-------|
| **Test case ID** | TC-CHAOS-003 |
| **Priority** | P2 |
| **Type** | Manual — UI only |
| **Interface** | Chaos POC Console |
| **URL** | http://localhost:18000 |
| **Category** | Load |
| **Command payload** | [`../high-pressure-latency.json`](../high-pressure-latency.json) |
| **UI scenario label** | High pressure latency |
| **Watched method** | `OrderController.submit` |

## Goal

Validate that latency on `OrderController.submit` degrades submit performance (500–3000 ms injected delay per assault).

## Isolation

| Phase | Action |
|-------|--------|
| **Setup** | **Reset configuration** → **Disabled**. |
| **Execute** | Baseline submit, apply scenario, verify slow submit (steps 2–5). |
| **Teardown** | **Reset configuration** → **Create + submit** **HTTP 200** at normal speed. |

## Preconditions

- Stack started: `docker compose up --build -d`
- Browser open at http://localhost:18000
- **Connection** card shows **Relay OK** and **Demo OK**

## Test steps

| Step | Action | Expected result |
|------|--------|-----------------|
| 1 | **Setup** — **Reset configuration**. | **Chaos Monkey state** → **Disabled**. |
| 2 | **Create + submit**; note submit duration. | **HTTP 200**; baseline within this test. |
| 3 | Apply **High pressure latency** only. | **APPLIED**. Latency 500–3000 ms on `OrderController.submit`. |
| 4 | Review **Chaos Monkey state**. | Latency assault **Active** on `OrderController.submit`. |
| 5 | **Create + submit** again (new order). | **HTTP 200**; submit duration ≥ 500 ms. |
| 6 | **Teardown** — **Reset configuration**. | **Disabled**; **Create + submit** at normal speed. |

## Acceptance criteria

| ID | Given | When | Then |
|----|-------|------|------|
| AC-003-1 | Scenario applied after setup | Apply completes | **APPLIED** **1/1** |
| AC-003-2 | Assault active | Operator reviews state | Latency on `OrderController.submit` |
| AC-003-3 | Latency assault active | **Create + submit** | Submit duration ≥ 500 ms |
| AC-003-4 | Teardown completed | **Create + submit** | **HTTP 200** at normal speed |

## Postconditions

- Default configuration restored
