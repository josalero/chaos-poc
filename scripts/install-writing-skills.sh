#!/usr/bin/env bash
# Install technical writing Cursor skills into this repo (.cursor/).
# Source: local agent-skills clone — does NOT modify agent-skills.
#
# Usage:
#   AGENT_SKILLS_ROOT=../../agent-skills ./scripts/install-writing-skills.sh
#   ./scripts/install-writing-skills.sh   # defaults to ../../../personal/agent-skills

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
AGENT_SKILLS_ROOT="${AGENT_SKILLS_ROOT:-$(cd "$REPO_ROOT/../../agent-skills" && pwd)}"
DIST_CURSOR="$AGENT_SKILLS_ROOT/dist/cursor/.cursor"

SKILLS=(
  technical-article-authoring
  technical-documentation-authoring
)

if [[ ! -d "$DIST_CURSOR/skills" ]]; then
  echo "Error: $DIST_CURSOR/skills not found." >&2
  echo "Run 'make build' in $AGENT_SKILLS_ROOT first." >&2
  exit 1
fi

mkdir -p "$REPO_ROOT/.cursor/skills" "$REPO_ROOT/.cursor/rules"

for id in "${SKILLS[@]}"; do
  src_skill="$DIST_CURSOR/skills/$id"
  src_rule="$DIST_CURSOR/rules/${id}.mdc"
  if [[ ! -d "$src_skill" ]]; then
    echo "Error: missing skill in dist: $id" >&2
    exit 1
  fi
  rm -rf "$REPO_ROOT/.cursor/skills/$id"
  cp -R "$src_skill" "$REPO_ROOT/.cursor/skills/"
  if [[ -f "$src_rule" ]]; then
    cp "$src_rule" "$REPO_ROOT/.cursor/rules/"
  fi
  echo "installed $id -> $REPO_ROOT/.cursor/"
done

echo "Done. Cursor reads skills from $REPO_ROOT/.cursor/ (not from agent-skills)."
