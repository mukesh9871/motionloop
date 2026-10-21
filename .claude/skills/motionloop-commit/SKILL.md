---
name: motionloop-commit
description: Commit and push conventions for MotionLoop, including milestone commits and the CI watch after a push. Use whenever committing or pushing in this repo.
---

# MotionLoop commits

## When to commit
- At every milestone: a feature step from the roadmap in `CLAUDE.md`, a review-fix round, or a CI fix.
- Each commit should leave the tree in a state that is meant to compile.

## Message format
```
<area>: <imperative summary, max 72 chars>

- why this change, one bullet per point
- anything a reviewer should know (trade-offs, follow-ups)
```
Areas: `editor`, `render`, `export`, `home`, `data`, `domain`, `build`, `ci`, `docs`, `review`, `test`.

Rules:
- No `Co-Authored-By` or AI attribution trailers (the user's global rule).
- Never commit secrets, keystores, `local.properties` or build outputs.
- Never force-push `main`. (The CI job force-pushes only the `ci-status` branch, which is expected.)

## Before pushing
1. Run the `motionloop-review` skill. Push only when the last review pass is clean.
2. Record every round in `docs/review-log.md` (findings, fixed, accepted with the reason).
3. `git push origin main`.

## Unfinished work and a push
When a clean milestone must go out while other work is in progress, `git stash push -u` the unfinished files, push, then `git stash pop`. A push must never carry uncommitted or unreviewed files.

## After pushing: watch CI
CI publishes the result for each commit to the `ci-status` branch (the repo is private and there is no `gh` CLI here).
```bash
bash tools/wait_ci.sh "$PWD" "$(git rev-parse HEAD)"
```
Run it in the background. It prints `status.txt` (`result=success|failure`) and `errors.txt`.
- On failure: read the errors, fix, commit as `ci: ...` or `build: ...`, review the fix, push again, and watch again.
- Continue until `result=success`.
