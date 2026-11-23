# Project instructions

## Git workflow

Work directly on `main`. Do not create new branches for project work unless the user explicitly changes this instruction. Consolidate authorized changes in `main`; when asked to upload them to GitHub, push to `origin/main`. Preserve the existing history and release controls.

## Zero-damage scope invariant

For every project and every task, treat all existing behavior, user interfaces, data, assets, integrations, accessibility features, tests, deployment paths, and documented decisions as protected. Change only what the user explicitly requests or what is strictly necessary to make that request work. Do not remove, replace, hide, weaken, reset, or silently alter protected behavior merely to simplify the implementation.

Before editing, inspect the affected flow and its adjacent states to establish the baseline. Prefer the smallest compatible change. If a requested change conflicts with existing behavior, preserve both through a compatible extension when possible; if the conflict is unavoidable, explain it before making that conflicting change. Before completion or deployment, run meaningful regression checks for the touched flow and adjacent protected behavior, compare the result with the baseline, and report every intentional difference. A new feature passing its own acceptance test is not sufficient evidence of zero damage.
