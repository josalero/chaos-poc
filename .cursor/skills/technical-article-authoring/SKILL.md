---
name: technical-article-authoring
description: Plan, draft, and revise public technical articles for LinkedIn, blogs, and dev publications. Use when writing hooks, outlines, tutorials, opinion pieces, or deep-dives — including narrative flow, code walkthrough pacing, diagrams, takeaways, CTAs, and pre-publish checklists. Not for internal BRDs or RFCs (use technical-documentation-authoring).
---

# Technical Article Authoring

## Workflow

1. **Clarify intent** — article type (tutorial, opinion, deep-dive, announcement, case study), target reader, platform (LinkedIn, blog, newsletter), and desired outcome (learn, persuade, announce).
2. **Research before prose** — gather facts, code paths, metrics, and constraints; label assumptions. For complex topics, run `software-design-analysis` first.
3. **Pick a template** — use `references/article-types-and-outlines.md` for the matching outline (LinkedIn long-form vs short post vs tutorial).
4. **Draft the hook first** — first 2–3 lines must earn the scroll-stop; state the problem or tension, not the tool name alone.
5. **Outline then body** — headings and one-line section summaries before full paragraphs; one idea per section.
6. **Pace technical content** — introduce concept → minimal example → why it matters → next step; avoid dumping full files early.
7. **Apply platform standards** — LinkedIn line breaks, code fence length, hashtag policy, and CTA per `references/linkedin-and-publication-standards.md`.
8. **Humanize without fluff** — concrete examples, specific numbers, named tradeoffs; cut hedge words and generic AI phrasing.
9. **Pre-publish review** — run the publication checklist; verify links, code snippets, and claims.

## References

- Read `references/article-types-and-outlines.md` for tutorial, opinion, deep-dive, announcement, and LinkedIn long-form templates.
- Read `references/linkedin-and-publication-standards.md` for hooks, formatting, tone, diagrams, hashtags, and pre-publish checklist.

## Article Checklist

- Title promises a clear reader outcome (not buzzwords only).
- Hook states problem, stakes, or surprise in the first screen.
- Each section has a scannable heading; no wall-of-text blocks over ~12 lines.
- Code samples are minimal, runnable or clearly excerpted, with language tags.
- Diagrams or tables appear where they reduce cognitive load.
- Jargon defined on first use; acronyms expanded once.
- Takeaways are actionable — reader knows what to do next.
- CTA matches platform (comment prompt, link, follow-up article tease).
- No unverified claims presented as benchmarks; assumptions labeled.

## Output

Deliver article work that includes:

- **Article type, audience, and platform**
- **Working title options** (2–3 when useful)
- **Outline** with section one-liners
- **Draft body** or **revision diff summary** when editing
- **Suggested pull quotes or hook variants** for LinkedIn
- **Pre-publish checklist** status (done / deferred items)
- **Open questions** needing author input

When the piece needs internal spec language (RFC, ADR, runbook), hand off sections to `technical-documentation-authoring`.

## Related Skills

- `technical-documentation-authoring` — BRDs, RFCs, ADRs, runbooks
- `software-design-analysis` — options and tradeoffs before deep technical articles
- `ux-design-review` — reader journey and content hierarchy when UX is central to the story
