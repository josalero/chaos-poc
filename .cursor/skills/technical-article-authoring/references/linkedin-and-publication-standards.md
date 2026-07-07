# LinkedIn and Publication Standards

## Hook patterns (use one)

- **Problem → twist:** common failure mode, then unexpected fix.
- **Result first:** lead with metric or outcome, then explain how.
- **Myth bust:** "Most teams do X; here's why Y works better."
- **Story beat:** one specific incident (no PII) that generalizes.

Avoid: "I'm excited to share...", "In today's fast-paced world...", title repeated as first line.

## Formatting for LinkedIn

| Element | Guideline |
| --- | --- |
| Line breaks | Short paragraphs (1–3 lines); blank line between blocks |
| Lists | Use sparingly in feed preview; bullets OK after hook |
| Code | Short fences only; link to gist/repo for long files |
| Bold | Rare; headings in article editor, not fake bold in feed |
| Links | Descriptive anchor text; one primary link in CTA |
| Hashtags | 3–5 relevant tags at end; not mid-sentence spam |

## Voice

- **First person** when sharing experience; **second person** for tutorials ("you").
- Prefer **specifics** over superlatives: versions, latency bands, team size.
- Name **tradeoffs** — readers trust balanced pieces.
- Cut filler: "It's worth noting", "Leverage", "Robust", "Seamless".

## Code and diagrams in articles

- Show **minimal** snippet that proves the point; comment omitted lines.
- State **language and version** in prose near first snippet.
- Mermaid or ASCII for flows; caption what the reader should notice.
- Never paste secrets, tokens, or PII — use placeholders.

## Revision passes

1. **Structure** — outline matches promise of title.
2. **Clarity** — every section passes "so what?" test.
3. **Accuracy** — commands compile; links resolve; claims sourced.
4. **Humanize** — vary sentence length; remove template phrases.
5. **Platform** — hook works as standalone feed preview.

## Pre-publish checklist

- [ ] Title + hook tested as feed preview (first ~210 characters compelling)
- [ ] Technical claims verified or marked as opinion
- [ ] Code snippets syntax-highlighted with language tag
- [ ] No credentials, internal URLs, or customer data
- [ ] CTA clear (comment question, repo, newsletter, part 2)
- [ ] Hashtags reviewed for relevance (not volume chasing)
- [ ] Optional: alt text for images / banner described in prose

## Working with agent-generated drafts

When revising LLM output (e.g. generate → humanize pipelines):

- Preserve **factual** content; rewrite **voice** and **structure**.
- Inject **specific** project details the model cannot invent.
- Split oversized paragraphs; add subheadings the model skipped.
- Replace generic examples with **your** stack and constraints.
