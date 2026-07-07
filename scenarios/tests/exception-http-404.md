# TC-CHAOS-004: HTTP 404 (downstream not found)

| Field | Value |
|-------|-------|
| **Test case ID** | TC-CHAOS-004 |
| **Priority** | P1 |
| **Type** | Manual — UI only |
| **Interface** | Chaos POC Console |
| **URL** | http://localhost:18000 |
| **Category** | HTTP errors |
| **Command payload** | [`../exception-http-404.json`](../exception-http-404.json) |
| **UI scenario label** | HTTP 404 |
| **Watched method** | `InventoryGateway.getStock` |

## Goal

Validate that an exception assault on `InventoryGateway.getStock` produces **HTTP 404** when order creation triggers the outbound inventory call.

## Isolation

| Phase | Action |
|-------|--------|
| **Setup** | **Reset configuration** → **Disabled**. |
| **Execute** | Apply HTTP 404 scenario; **Create order** (steps 2–4). |
| **Teardown** | **Reset configuration** → **Create order** **HTTP 201**. |

## Preconditions

- Stack started: `docker compose up --build -d`
- Browser open at http://localhost:18000
- **Connection** card shows **Relay OK** and **Demo OK**

## Test steps

| Step | Action | Expected result |
|------|--------|-----------------|
| 1 | **Setup** — **Reset configuration**. | **Chaos Monkey state** → **Disabled**. |
| 2 | Apply **HTTP 404** only. | **APPLIED**. **Payload preview** shows `ResourceNotFoundException` on `InventoryGateway.getStock`. |
| 3 | Review **Chaos Monkey state**. | **Enabled**; exception assault **Active** on `InventoryGateway.getStock`. |
| 4 | **Create order**. | **Recent responses** shows **HTTP 404**; body has `error` + `message` (no stack trace). |
| 5 | **Teardown** — **Reset configuration**. | **Disabled**; **Create order** **HTTP 201**. |

## Acceptance criteria

| ID | Given | When | Then |
|----|-------|------|------|
| AC-004-1 | HTTP 404 applied after setup | Apply completes | **APPLIED** **1/1** |
| AC-004-2 | Assault active | Operator reviews state | `InventoryGateway.getStock` watched; exceptions **Active** |
| AC-004-3 | Exception assault active | **Create order** | **HTTP 404** |
| AC-004-4 | 404 response shown | Operator expands body | Customer-safe JSON only |
| AC-004-5 | Teardown completed | **Create order** | **HTTP 201** |

## Postconditions

- Default configuration restored
