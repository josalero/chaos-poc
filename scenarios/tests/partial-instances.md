# TC-CHAOS-011: Assault on some instances

| Field | Value |
|-------|-------|
| **Test case ID** | TC-CHAOS-011 |
| **Priority** | P1 |
| **Type** | Manual — UI only |
| **Interface** | Chaos POC Console |
| **URL** | http://localhost:18000/chaos |
| **Category** | Control |
| **Command payload** | [`../partial-instances.json`](../partial-instances.json) |
| **UI path** | Service detail → **Apply to some instances** |

## Goal

Validate that a command can change one demo replica and leave the other replica untouched.

`instanceSelection` `ALL` sends the command to every UP instance. `SOME` sends it only to the discovery instance ids in `instanceIds`. `expectedInstances` is that selected count. The command is `APPLIED` when those instances succeed, even while other replicas stay UP and unchanged.

This case is not in `verify-scenarios.sh`. The instance id is assigned by Eureka at startup, so the JSON file is a template.

## Isolation

| Phase | Action |
|-------|--------|
| **Setup** | **Reset all services** → Chaos Monkey **Disabled** on both demo replicas. |
| **Execute** | Publish the latency assault with **Some instances** and one demo instance id. |
| **Teardown** | **Reset all services** again. Reset uses `ALL`, so both replicas return to **Disabled**. |

## Preconditions

- Stack started: `docker compose up --build -d`
- Operator console open at http://localhost:18000/chaos
- Demo service shows two UP instance ids

## Test steps

| Step | Action | Expected result |
|------|--------|-----------------|
| 1 | **Setup** — **Reset all services**. | Both demo replicas are **Disabled**. |
| 2 | Open **chaos-poc-demo**. Copy one id from **UP instances**. | Two ids are listed. `eurekaUpCount` is 2. |
| 3 | **Apply to some instances**. Choose the success-path latency preset, **Some instances**, and the copied id. Publish. | **202**. `expectedInstances` is 1. Scope is `SOME` plus that id. |
| 4 | Wait for the command page. | **APPLIED**. Per-pod results lists only the selected instance. |
| 5 | Return to the service page. | UP count is still 2. The last command expected 1 instance. |
| 6 | **Teardown** — **Reset all services**. | Both replicas are **Disabled**. Create order in the verify UI returns **HTTP 201**. |

## Acceptance criteria

- Given two UP demo instances, when the operator publishes `SOME` with one id, then only that instance is called and the aggregate is `APPLIED` with `expectedInstances` 1.
- Given an id that is not UP, when the operator publishes `SOME`, then the relay returns **400** and stores no command.
- Given a SOME command that was `APPLIED`, when the operator resets all services, then both replicas are disabled.
