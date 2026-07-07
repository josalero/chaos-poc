# TC-CHAOS-009: HTTP 500 during create

| Field | Value |
|-------|-------|
| **Test case ID** | TC-CHAOS-009 |
| **Priority** | P1 |
| **Command payload** | [`../exception-http-500-create.json`](../exception-http-500-create.json) |
| **UI scenario label** | HTTP 500 — create |
| **Watched method** | `OrderService.placeOrder` |

## Goal

Validate that an unexpected exception during order creation produces a safe **HTTP 500** response.

## Test steps

| Step | Action | Expected result |
|------|--------|-----------------|
| 1 | Click **Reset configuration**. | Chaos Monkey is **Disabled**. |
| 2 | Apply **HTTP 500 — create**. | Apply history reaches **APPLIED**. |
| 3 | Click **Create order**. | **HTTP 500** with `INTERNAL_SERVER_ERROR`. |
| 4 | Click **Create + submit**. | Its create phase returns **HTTP 500**; submit is not attempted. |
| 5 | Click **Reset configuration**, then **Create order**. | **HTTP 201**. |

## Acceptance criteria

- Both buttons that invoke order creation surface **HTTP 500** during their create phase.
- No stack trace or injected exception detail is returned.
- Reset restores normal **HTTP 201** creation.
