---
name: session-open
description: Use at the start of any work session on kingdoms — when the user opens with "let's continue", "what's next", "next on the list", references docs/Status.md, or names a milestone or todo to pick up. Rebuilds context from the written state and checks the working tree before anything is started.
---

# Opening a session

Context comes from the written state, not from memory and not from re-reading the whole codebase.

## 1. Read the state, in this order

1. **`docs/Status.md`** — all of it: where things stand, open decisions, to do next, and the
   latest handover entry (its "Picking this up" line is the starting point).
2. **`CLAUDE.md`** — already in context; consult the section for the area about to be touched.
3. **`docs/Design.md`** — only the sections the work touches. Check §11 for open questions that
   affect it.

## 2. Check the working tree

```sh
git status --short && git log --oneline -3
```

Uncommitted work from a previous session is a question for the user — do not absorb it silently or
clean it up. Once the project builds, also run the build/test command from `CLAUDE.md` §Commands
so the session starts from a known-green (or known-red) state, and say which.

## 3. State the scope back

Before writing anything, say in one or two sentences what this session will do and which todo in
`docs/Status.md` §3 it closes. If it is not the next item on that list, name the deviation.

If the work needs an answer to an open question in Design §11, ask — do not pick the default
silently.
