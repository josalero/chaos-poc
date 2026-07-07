# Agent instructions — chaos-poc

## Writing skills (installed)

Cursor skills for this repo live under **`.cursor/skills/`**. They are not read from `agent-skills` at runtime.

| Skill | Scope |
| --- | --- |
| `technical-article-authoring` | LinkedIn posts, tutorials, deep-dives, opinion pieces |
| `technical-documentation-authoring` | BRDs, RFCs, ADRs, implementation plans, runbooks |

Reinstall from a local [agent-skills](https://github.com/josalero/agent-skills) clone:

```bash
chmod +x scripts/install-writing-skills.sh
./scripts/install-writing-skills.sh
```

Requires `make build` in the agent-skills clone if `dist/` is missing.

## Stack

Java 25, Spring Boot, RabbitMQ, Docker Compose — see [README.md](README.md).
