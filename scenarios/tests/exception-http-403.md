# TC-CHAOS-005: HTTP 403 (forbidden)

| Field | Value |
|-------|-------|
| **Test case ID** | TC-CHAOS-005 |
| **Priority** | P1 |
| **Type** | Manual — UI only |
| **Interface** | Chaos POC Console |
| **URL** | http://localhost:18000 |
| **Category** | HTTP errors |
| **Command payload** | [`../exception-http-403.json`](../exception-http-403.json) |
| **UI scenario label** | HTTP 403 |
| **Watched method** | `AuthorizationGateway.checkAccess` |

## Goal

Validate that an exception assault on `AuthorizationGateway.checkAccess` surfaces as a failed submit on the **Create + submit** path (authorization runs during submit, not create).

## Isolation

| Phase | Action |
|-------|--------|
| **Setup** | **Reset configuration** → **Disabled**. |
| **Execute** | Baseline submit, apply scenario, verify failed submit (steps 2–5). |
| **Teardown** | **Reset configuration** → **Create + submit** **HTTP 200**. |

## Preconditions

- Stack started: `docker compose up --build -d`
- Browser open at http://localhost:18000
- **Connection** card shows **Relay OK** and **Demo OK**

## Test steps

| Step | Action | Expected result |
|------|--------|-----------------|
| 1 | **Setup** — **Reset configuration**. | **Chaos Monkey state** → **Disabled**. |
| 2 | **Create + submit** (baseline within this test). | **Submit order (200)** with **HTTP 200**. |
| 3 | Apply **HTTP 403** only. | **APPLIED**. **Payload preview** shows `ForbiddenException` on `AuthorizationGateway.checkAccess`. |
| 4 | Review **Chaos Monkey state**. | Exception assault **Active** on `AuthorizationGateway.checkAccess`. |
| 5 | **Create + submit** again. | Submit fails (**HTTP 403** or error badge). |
| 6 | **Teardown** — **Reset configuration**. | **Disabled**; **Create + submit** **HTTP 200**. |

## Acceptance criteria

| ID | Given | When | Then |
|----|-------|------|------|
| AC-005-1 | HTTP 403 applied after setup | Apply completes | **APPLIED** **1/1** |
| AC-005-2 | Assault active | Operator reviews state | `AuthorizationGateway.checkAccess` watched |
| AC-005-3 | Exception assault active | **Create + submit** | Submit not **HTTP 200** |
| AC-005-4 | Teardown completed | **Create + submit** | **HTTP 200** |

## Postconditions

- Default configuration restored

## Note

The **Forbidden** button under **Error paths** exercises a real downstream 403 via inventory — separate from this assault on `AuthorizationGateway.checkAccess` during submit.
