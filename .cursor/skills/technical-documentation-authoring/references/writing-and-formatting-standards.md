# Writing and Formatting Standards

## Voice and Tone

- **Present tense** for system behavior: "The service validates input" not "will validate."
- **Active voice** with clear owner: "Platform team deploys weekly" not "deployments are done."
- **Concrete nouns** — name components, APIs, and data stores; avoid "the module" without antecedent.
- **No filler** — delete "It is important to note that", "In order to", "Basically."

## Structure

### Heading hierarchy

Use one `#` title per document. Do not skip levels (`##` then `####`).

```markdown
# Document title
## Major section
### Subsection
```

### Paragraph length

- Target **3–5 sentences** per paragraph.
- Break lists longer than **7 items** into subgroups or tables.
- Put **comparison data in tables**, not comma-separated prose.

### Front-loaded sections

First paragraph under each heading states **what this section contains** in one sentence.

## Markdown Conventions

| Element | Rule |
| --- | --- |
| Code | Fenced blocks with language tag; inline `backticks` for identifiers |
| Paths | Use `path/to/file` consistently; full paths when cross-repo |
| Links | Descriptive text: `[BRD § acceptance criteria](01-brd.md#10-acceptance-criteria)` |
| Diagrams | Mermaid for flows; ASCII when Mermaid unavailable; caption in prose above/below |
| Tables | Header row required; align columns for readability |
| Status badges | Plain text: **Status:** Draft — avoid emoji in formal docs unless org standard |

## Diagrams

Use diagrams when they clarify **structure or sequence**, not decoration.

**Good uses:** system context, request flow, state machine, deployment topology.

**Label:** nodes with service names; edges with protocol or data type.

After each diagram, add **2–3 sentences** explaining assumptions the diagram does not show.

## Acceptance Criteria in Documents

Write criteria so QA or CI can verify:

```markdown
| # | Criterion | Verification |
| --- | --- | --- |
| AC-1 | Export completes for 100k rows within 10 minutes p95 | Load test job + metric |
| AC-2 | Unauthorized users receive 403 on export endpoint | Integration test |
```

Avoid: "System should be performant and secure."

## Terminology

- Define acronyms on first use: "Service level objective (SLO)".
- Use one term per concept (not "client", "consumer", "caller" interchangeably without reason).
- Match codebase names when documenting implementation plans.

## Revision and Status

When updating documents:

```markdown
## Revision history
| Date | Author | Summary |
| --- | --- | --- |
| 2026-06-01 | … | Initial draft |
```

For ADRs, **do not rewrite history** — supersede with a new ADR.

## Review Checklist (Before Publish)

- [ ] Title matches document type and scope
- [ ] Goals and non-goals present (BRD, RFC, plan)
- [ ] Every table has headers; every code block has a language or is plain text intentionally
- [ ] Links resolve within repo or use full URLs
- [ ] Open questions have owners
- [ ] No TODO in **approved** active repo docs (use Draft status instead)
- [ ] Sensitive data, credentials, and PII absent from examples
- [ ] Spelling and heading hierarchy consistent

## Anti-Patterns

| Problem | Fix |
| --- | --- |
| Requirements in solution language | Rewrite as outcome; move tech to design section |
| Duplicate BRD + HLA content | BRD = why/what; HLA = how structured |
| Orphan ADR | Link from HLA index or RFC |
| Paste-only LLM prose | Add specifics: names, numbers, dates |
| Missing verification | Add acceptance criteria or test plan section |

## Repository Conventions (agent-skills)

When authoring docs in this catalog:

- Numbered guides live under `docs/` (`01-getting-started.md`, …).
- Architecture product docs: `docs/architecture/01-brd.md`, `02-high-level-architecture.md`.
- Link to generated catalog paths with full repo-relative paths.

## Related Skills

- `software-design-analysis` — content for requirements and options sections
- `system-architecture-review` — findings to paste into HLA or ADR consequences
