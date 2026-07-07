---
name: technical-documentation-authoring
description: Write and revise technical documents and plans with professional structure and formatting. Use when drafting or editing BRDs, high-level architecture docs, RFCs, ADRs, implementation plans, design proposals, runbooks, or README sections — including doc style, headings, tables, diagrams, and acceptance criteria sections.
---

# Technical Documentation Authoring

## Workflow

1. **Identify document type** — BRD, HLA/HLD, RFC, ADR, implementation plan, runbook, or README; match audience (executive, architect, implementer, operator).
2. **Confirm inputs** — existing analysis, tickets, constraints, or `software-design-analysis` output; do not invent requirements without labeling assumptions.
3. **Select template** — use the appropriate skeleton from `references/document-types-and-templates.md`.
4. **Draft structure first** — headings and bullet placeholders before prose; ensure every section has a purpose.
5. **Write for scanability** — short paragraphs, tables for comparisons, lists for criteria; one idea per section.
6. **Apply formatting standards** — consistent heading hierarchy, terminology, links, code fences, and diagram placement per `references/writing-and-formatting-standards.md`.
7. **Validate completeness** — goals, scope, acceptance criteria, open questions, and verification steps present where the doc type requires them.
8. **Revise for clarity** — remove filler, passive voice where it hides ownership, and undefined acronyms.

## References

- Read `references/document-types-and-templates.md` for BRD, HLA, RFC, ADR, implementation plan, and runbook outlines.
- Read `references/writing-and-formatting-standards.md` for voice, structure, Markdown conventions, and review checklist.

## Documentation Checklist

- Title and status (draft / review / approved) visible at top when appropriate.
- Audience and scope stated in the first screen of content.
- **Goals** and **non-goals** are explicit.
- Acceptance criteria are **testable** — not vague aspirations.
- Diagrams supplement text; they do not replace requirements.
- Decisions name **owner** and **date**; superseded docs link to replacements.
- No wall-of-text sections longer than ~15 lines without a subheading or table.
- Terminology consistent (same noun for the same concept throughout).

## Output

Deliver documentation that includes:

- **Document type and audience**
- **Structured body** using the correct template sections
- **Tables or diagrams** where they reduce ambiguity (comparison matrices, sequence flows)
- **Acceptance criteria** or **verification** section when the doc drives delivery
- **Open questions** with owners
- **Revision notes** (what changed) when updating an existing doc

When content requires design analysis first, recommend running `software-design-analysis` before expanding prose.

## Related Skills

- `software-design-analysis` — requirements and option analysis before writing
- `system-architecture-review` — architecture findings to embed in HLA or ADR
- `migration-planning` — migration-specific sequencing in plans
- `api-design-review` — API sections in RFCs or OpenAPI companion docs
