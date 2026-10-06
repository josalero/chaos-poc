# Chaos POC — Manual UI test cases

Manual test specifications for the chaos POC.  
**Apply scenarios and teardown** use the **operator console** (`http://localhost:18000/chaos`).  
**Monitor and verify** use the **verify UI** (`http://localhost:18000`).

| Test case ID | Title | Specification | Priority |
|--------------|-------|---------------|----------|
| TC-CHAOS-001 | Bean interceptor | [bean-interceptor.md](bean-interceptor.md) | P1 |
| TC-CHAOS-002 | Service-to-service latency | [service-to-service-latency.md](service-to-service-latency.md) | P1 |
| TC-CHAOS-003 | High pressure latency | [high-pressure-latency.md](high-pressure-latency.md) | P2 |
| TC-CHAOS-004 | HTTP 404 | [exception-http-404.md](exception-http-404.md) | P1 |
| TC-CHAOS-005 | HTTP 403 | [exception-http-403.md](exception-http-403.md) | P1 |
| TC-CHAOS-006 | HTTP 409 | [exception-http-409.md](exception-http-409.md) | P1 |
| TC-CHAOS-007 | Success path latency | [latency-success-path.md](latency-success-path.md) | P1 |
| TC-CHAOS-008 | Default configuration rollback | [disable.md](disable.md) | P1 |
| TC-CHAOS-009 | HTTP 500 during create | [exception-http-500-create.md](exception-http-500-create.md) | P1 |
| TC-CHAOS-010 | HTTP 500 during submit | [exception-http-500-submit.md](exception-http-500-submit.md) | P1 |

## Isolation model (transactional)

Tests are **not history-based**. They do **not** depend on:

- A prior test having run
- Rows in **Apply history**
- Leftover assault configuration from another scenario

Each test (except TC-CHAOS-008, which validates rollback itself) follows three phases:

| Phase | Where | UI action | Expected state |
|-------|--------|-----------|----------------|
| **Setup** | Operator console | **Reset all services** (dashboard or header) | Chaos Monkey **Disabled** on all targets; admin data cleared |
| **Execute** | Operator console + verify UI | Apply one scenario; verify behaviour (steps in each spec) | Assault active only for this test |
| **Teardown** | Operator console | **Reset all services** again | **Disabled**; **Create order** in verify UI returns **HTTP 201** |

Run tests in **any order**. Each test is a self-contained transaction: setup → execute → rollback.

## Document structure

Each Markdown specification includes:

| Section | Purpose |
|---------|---------|
| **Metadata table** | ID, priority, UI label, command payload link, watched method |
| **Isolation** | Setup / execute / teardown phases |
| **Goal** | Test objective — what behaviour or risk is validated |
| **Preconditions** | Environment only (stack up, connections green) |
| **Test steps** | Numbered UI actions with expected result per step |
| **Acceptance criteria** | Pass/fail conditions in Given / When / Then form |
| **Postconditions** | Default configuration restored (same as teardown) |

## Environment setup

1. From the `chaos-poc` directory, start the stack:

   ```bash
   docker compose up --build -d
   ```

2. Wait until all services are healthy (`docker compose ps` — no service stuck in `starting`).

3. Open **verify UI**: http://localhost:18000 and **operator console**: http://localhost:18000/chaos

4. On the verify UI, confirm **Demo target OK** before exercising APIs.

**Do not proceed** with assault verification until the demo target badge is green.

## UI map (reference)

| UI area | Location | Used for |
|---------|----------|----------|
| **Operator console** | http://localhost:18000/chaos | Apply presets, disable CM, reset configuration, command history |
| **Reset all services** | Operator console dashboard or header | **Setup** and **teardown** — disables CM on every registered target and clears admin data |
| **Reset configuration** | Operator console → service detail | Reset a single target only |
| **Chaos Monkey state** | Verify UI (top panel) | Enabled flag, assault metrics, watched methods |
| **Demo API playground** | Verify UI | Trigger demo endpoints; **Recent responses** shows status + timing |
| **Live telemetry** | Verify UI | Latency chart, live probe, status distribution |

## Traceability

Each test case links to its relay command payload (e.g. [`../bean-interceptor.json`](../bean-interceptor.json)).

## Automated verification (optional)

With the stack running:

```bash
./scenarios/tests/verify-scenarios.sh
```

Runs every assault scenario in **isolation** (disable + reset before and after each case) and performs a **final rollback** with a sanity **HTTP 201** create. Mirrors the transactional model above.

To verify a different execution order, pass the scenario IDs as a comma- or space-separated list:

```bash
SCENARIO_ORDER="high-pressure-latency,exception-http-500-submit,exception-http-500-create,exception-http-403,latency-success-path,exception-http-409,exception-http-404,service-to-service-latency,bean-interceptor" \
  ./scenarios/tests/verify-scenarios.sh
```

The verifier also asserts the full canonical actuator state after every rollback, including inactive flags, watched methods, latency range, and exception type. This catches stale settings even when they are temporarily inactive.
