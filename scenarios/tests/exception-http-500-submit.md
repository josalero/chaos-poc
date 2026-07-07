# TC-CHAOS-010: HTTP 500 during submit

| Field | Value |
|-------|-------|
| **Test case ID** | TC-CHAOS-010 |
| **Priority** | P1 |
| **Command payload** | [`../exception-http-500-submit.json`](../exception-http-500-submit.json) |
| **UI scenario label** | HTTP 500 — submit |
| **Watched method** | `OrderService.submitOrder` |

## Goal

Validate that creation remains healthy while an unexpected submit failure produces a safe **HTTP 500** response.

## Test steps

| Step | Action | Expected result |
|------|--------|-----------------|
| 1 | Click **Reset configuration**. | Chaos Monkey is **Disabled**. |
| 2 | Apply **HTTP 500 — submit**. | Apply history reaches **APPLIED**. |
| 3 | Click **Create order**. | **HTTP 201** because submit is the watched method. |
| 4 | Click **Create + submit**. | Create phase is **HTTP 201**, then submit phase is **HTTP 500** with `INTERNAL_SERVER_ERROR`. |
| 5 | Click **Reset configuration**, then **Create + submit**. | Create is **HTTP 201** and submit is **HTTP 200**. |

## Acceptance criteria

- Standalone creation remains **HTTP 201**.
- The compound action clearly shows **201** for create followed by **500** for submit.
- Reset restores normal submit behavior.
