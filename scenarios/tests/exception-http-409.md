# TC-CHAOS-006: HTTP 409 (conflict)

| Field | Value |
|-------|-------|
| **Test case ID** | TC-CHAOS-006 |
| **Priority** | P1 |
| **Type** | Manual — UI only |
| **Interface** | Chaos POC Console |
| **URL** | http://localhost:18000 |
| **Category** | HTTP errors |
| **Command payload** | [`../exception-http-409.json`](../exception-http-409.json) |
| **UI scenario label** | HTTP 409 |
| **Watched method** | `OrderService.placeOrder` |

## Goal

Validate that an exception assault on `OrderService.placeOrder` with `DuplicateOrderException` produces **HTTP 409** in **Recent responses**.

## Isolation

| Phase | Action |
|-------|--------|
| **Setup** | **Reset configuration** → **Disabled** (clears order data — required so create is not a real duplicate). |
| **Execute** | Apply HTTP 409; **Create order** (steps 2–4). |
| **Teardown** | **Reset configuration** → **Create order** **HTTP 201**. |

## Preconditions

- Stack started: `docker compose up --build -d`
- Browser open at http://localhost:18000
- **Connection** card shows **Relay OK** and **Demo OK**

## Test steps

| Step | Action | Expected result |
|------|--------|-----------------|
| 1 | **Setup** — **Reset configuration**. | **Chaos Monkey state** → **Disabled**; demo orders cleared. |
| 2 | Apply **HTTP 409** only. | **APPLIED**. **Payload preview** shows `DuplicateOrderException` on `OrderService.placeOrder`. |
| 3 | Review **Chaos Monkey state**. | Exception assault **Active** on `OrderService.placeOrder`. |
| 4 | **Create order**. | **Recent responses** shows **HTTP 409** (conflict). |
| 5 | **Teardown** — **Reset configuration**. | **Disabled**; **Create order** **HTTP 201**. |

## Acceptance criteria

| ID | Given | When | Then |
|----|-------|------|------|
| AC-006-1 | HTTP 409 applied after setup | Apply completes | **APPLIED** **1/1** |
| AC-006-2 | Assault active | Operator reviews state | `OrderService.placeOrder` watched |
| AC-006-3 | Exception assault active | **Create order** | **HTTP 409** |
| AC-006-4 | Teardown completed | **Create order** | **HTTP 201** |

## Postconditions

- Default configuration restored
