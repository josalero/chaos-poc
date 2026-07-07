# TC-CHAOS-001: Bean interceptor

| Field | Value |
|-------|-------|
| **Test case ID** | TC-CHAOS-001 |
| **Priority** | P1 |
| **Type** | Manual — UI only |
| **Interface** | Chaos POC Console |
| **URL** | http://localhost:18000 |
| **Category** | Method target |
| **Command payload** | [`../bean-interceptor.json`](../bean-interceptor.json) |
| **UI scenario label** | Bean interceptor |
| **Watched method** | `OrderService.placeOrder` |

## Goal

Validate that an operator can apply an exception assault on a single service method through the UI, and that the fault is observable when creating an order via the **Demo API playground**.

## Isolation

| Phase | Action |
|-------|--------|
| **Setup** | Click **Reset configuration** (header). Confirm **Chaos Monkey state** → **Disabled**. |
| **Execute** | Steps 2–5 below. |
| **Teardown** | Click **Reset configuration** again. **Create order** must return **HTTP 201**. |

This test does not depend on prior runs or **Apply history**.

## Preconditions

- Stack started: `docker compose up --build -d` (from `chaos-poc` directory)
- Browser open at http://localhost:18000
- **Connection** card shows **Relay OK** and **Demo OK**

## Test steps

| Step | Action | Expected result |
|------|--------|-----------------|
| 1 | **Setup** — click **Reset configuration** (header). | **Chaos Monkey state** → **Disabled**. Scenario selection cleared. |
| 2 | In **Demo API playground** → **Happy paths**, click **Create order**. | **Recent responses** shows **HTTP 201** (baseline within this test). |
| 3 | In **Scenarios** → **Method target**, check **Bean interceptor** only. Click **Apply 1 scenario**. | Latest **Apply history** row reaches **APPLIED**. **Payload preview** shows the fully qualified `OrderService.placeOrder` target and `RuntimeException`. |
| 4 | Review **Chaos Monkey state**. | **Enabled**. **Exception assault** **Active**. **Watched methods** includes `OrderService.placeOrder`. |
| 5 | Click **Create order** again. | **Recent responses** shows non-success (not HTTP 201). |
| 6 | **Teardown** — click **Reset configuration**. | **Chaos Monkey state** → **Disabled**. **Create order** returns **HTTP 201**. |

## Acceptance criteria

| ID | Given | When | Then |
|----|-------|------|------|
| AC-001-1 | Bean interceptor applied after setup | Apply completes | Status **APPLIED** with **1/1** success |
| AC-001-2 | Assault active | Operator reviews **Chaos Monkey state** | Exception assault **Active** on `OrderService.placeOrder` |
| AC-001-3 | Exception assault active | Operator clicks **Create order** | **Recent responses** does not show HTTP 201 |
| AC-001-4 | Teardown completed | Operator clicks **Create order** | **HTTP 201** (default configuration restored) |

## Postconditions

- Chaos Monkey **Disabled** (default configuration)
- Demo order data cleared
- Normal **Create order** path works (**HTTP 201**)
