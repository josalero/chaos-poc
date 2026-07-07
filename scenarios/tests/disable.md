# TC-CHAOS-008: Default configuration rollback

| Field | Value |
|-------|-------|
| **Test case ID** | TC-CHAOS-008 |
| **Priority** | P1 |
| **Type** | Manual — UI only |
| **Interface** | Chaos POC Console |
| **URL** | http://localhost:18000 |
| **Category** | Control |
| **Command payload** | [`../disable.json`](../disable.json) |
| **UI scenario label** | Disable assaults |

## Goal

Validate that **Reset configuration** (and the **Disable assaults** scenario) roll back Chaos Monkey and demo data to the **default** state, restoring normal API behaviour.

This test is **self-contained**: it applies a fault within the same transaction, then rolls back.

## Isolation

| Phase | Action |
|-------|--------|
| **Setup** | **Reset configuration** → **Disabled**. |
| **Execute** | Apply any assault, verify fault, then rollback via **Reset configuration** or **Disable assaults** (steps 2–6). |
| **Teardown** | Confirm **Disabled** and **Create order** **HTTP 201** (same as rollback target). |

## Preconditions

- Stack started: `docker compose up --build -d`
- Browser open at http://localhost:18000
- **Connection** card shows **Relay OK** and **Demo OK**

## Test steps

| Step | Action | Expected result |
|------|--------|-----------------|
| 1 | **Setup** — **Reset configuration**. | **Chaos Monkey state** → **Disabled**. |
| 2 | Apply **Bean interceptor** (or any assault scenario). | **APPLIED**; **Chaos Monkey state** → **Enabled**. |
| 3 | **Create order**. | Non-success (assault active). |
| 4 | **Rollback** — either click **Reset configuration** **or** apply **Disable assaults** only. | **Chaos Monkey state** → **Disabled**; assault metrics off. |
| 5 | **Create order**. | **HTTP 201**; normal duration. |
| 6 | Confirm **Connection** badges still green. | **Relay OK** and **Demo OK**. |

## Acceptance criteria

| ID | Given | When | Then |
|----|-------|------|------|
| AC-008-1 | Assault applied in step 2 | **Create order** | Not HTTP 201 |
| AC-008-2 | Rollback in step 4 | Operator views **Chaos Monkey state** | **Disabled** |
| AC-008-3 | After rollback | **Create order** | **HTTP 201** |
| AC-008-4 | Test completed | **Connection** card | Still green |

## Postconditions

- Default configuration: Chaos Monkey **Disabled**, demo data cleared, normal create path works

## Usage

**Reset configuration** is the standard **setup** and **teardown** for every assault test (TC-CHAOS-001–007). This test validates that rollback works. Tests do **not** need to run in a fixed order.
