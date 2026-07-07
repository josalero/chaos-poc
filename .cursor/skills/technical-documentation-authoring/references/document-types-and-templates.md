# Document Types and Templates

Pick one primary type per document. Combine types only when sections are clearly labeled (e.g. RFC with embedded ADR).

## Business Requirements Document (BRD)

**Audience:** Product, engineering leads, stakeholders  
**Purpose:** Why build it; what success looks like

```markdown
# [Feature] — Business Requirements

**Status:** Draft | In review | Approved
**Owner:** [role/name]

## 1. Executive summary
(2–4 sentences)

## 2. Problem statement

## 3. Goals and non-goals

## 4. Stakeholders and personas

## 5. User stories
| ID | Story | Priority |

## 6. Functional requirements

## 7. Non-functional requirements

## 8. Acceptance criteria
| # | Criterion | Verification |

## 9. Assumptions and dependencies

## 10. Out of scope / future considerations

## 11. Open questions
| Question | Owner | Due |
```

## High-Level Architecture (HLA / HLD)

**Audience:** Architects, senior engineers, platform teams  
**Purpose:** How the system is structured; as-built or to-be

```markdown
# [System] — High-Level Architecture

**Status:** As-built | Proposed
**Related BRD:** [link]

## 1. Purpose and scope

## 2. System context
(diagram + narrative)

## 3. Architecture principles

## 4. Component view
| Component | Responsibility | Technology |

## 5. Data flows
(sequence or flow diagram)

## 6. Integration points

## 7. Non-functional design
(availability, security, scaling)

## 8. Deployment view

## 9. Risks and mitigations

## 10. Open decisions
```

## Request for Comments (RFC)

**Audience:** Teams affected by a change  
**Purpose:** Propose a change; gather feedback before implementation

```markdown
# RFC-[NNN]: [Title]

**Author:** …
**Status:** Draft | Review | Accepted | Rejected
**Review by:** [date]

## Summary

## Motivation

## Detailed design
(subsections per component or work stream)

## Alternatives considered

## Migration / rollout plan

## Testing and verification

## Drawbacks

## Unresolved questions
```

## Architecture Decision Record (ADR)

**Audience:** Future maintainers  
**Purpose:** Immutable log of one decision

```markdown
# ADR-[NNN]: [Short title]

**Status:** Proposed | Accepted | Deprecated | Superseded by ADR-XXX
**Date:** YYYY-MM-DD

## Context

## Decision

## Consequences
(positive and negative)

## Alternatives considered
```

Keep ADRs **short** (1–2 pages). Link to RFC or HLA for depth.

## Implementation Plan

**Audience:** Engineers executing the work  
**Purpose:** Sequenced delivery with checkpoints

```markdown
# Implementation Plan: [Feature]

## Overview and link to BRD/RFC

## Prerequisites

## Work breakdown
| Phase | Tasks | Owner | Exit criteria |

## Rollout strategy
(feature flags, canary, rollback)

## Testing plan
(unit, integration, UAT)

## Observability and ops
(metrics, alerts, runbook updates)

## Risks and mitigations
```

## Runbook (Operations)

**Audience:** On-call, SRE, support  
**Purpose:** Repeatable procedures under incident or change

```markdown
# Runbook: [Service / scenario]

## When to use

## Prerequisites and access

## Procedure
1. Step with expected outcome
2. …

## Verification

## Rollback

## Escalation
```

## README Section (Feature or Module)

**Audience:** Developers in the repo  
**Purpose:** Orient quickly; link to deeper docs

- One-line purpose
- How to run / test
- Configuration table
- Link to ADR/HLA — do not duplicate architecture treatises in README

## Choosing the Right Type

| Situation | Start with |
| --- | --- |
| New initiative needs approval | BRD |
| Team needs agreement on approach | RFC |
| Decision must be remembered | ADR |
| Build team needs task order | Implementation plan |
| Production operation | Runbook |
| System structure reference | HLA |
