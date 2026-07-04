---
name: orchestrate-blueprint-feature
description: Use when a user provides a high-level blueprint, broad feature request, product spec, or architecture sketch that needs coordinated multi-agent delivery.
---

# Orchestrate Blueprint Feature

## Overview

Convert a high-level blueprint into production code through staged agent teams: requirements extraction, architecture, feature design, implementation planning, parallel delivery, end-to-end review, human review, and merge to `develop`.

Core rule: every stage must produce a named artifact and pass self-review before the next stage starts.

## Team Model

The current Claude Code session is the lead: it owns the blueprint, the artifacts, stage gating, and the final merge decision. Workers are subagents launched with the `Agent` tool. Use available agents conservatively, but prefer independent reviewers — a separate subagent that did not produce an artifact reviews it — whenever the risk warrants it.

Pick the `subagent_type` that matches the stage:

| Work | Preferred agent(s) |
| --- | --- |
| Blueprint extraction | Lead session, optionally a `general-purpose` subagent for a second read |
| Exploration | `Explore` or `feature-dev:code-explorer` subagents; run two on the same bounded question for independent findings |
| Architecture decisions | `Plan` or `feature-dev:code-architect` subagents, fed the exploration synthesis |
| Feature design | `Plan` or `feature-dev:code-architect` subagents |
| Implementation plan | `Plan` subagent to draft, a separate `feature-dev:code-reviewer` subagent to review |
| Development | Parallel `general-purpose` (or `feature-dev:feature-dev`) workers with disjoint file ownership |
| Review | Independent `feature-dev:code-reviewer` subagents; escalate to a fresh high-effort reviewer for high-risk implementation |

Independence is the point: the agent that reviews an artifact should not be the one that wrote it. Where the user explicitly wants a cross-tool second opinion, an external `codex` CLI (available on this machine) can serve as an optional independent-review sidecar — but Claude Code is the primary and the lead.

If subagents are unavailable, run the same stages sequentially in the lead session and record the reduced-parallelism constraint.

## Required Artifacts

Create or update these artifacts during the run. They may be sections in a single plan document for small features, but each heading must exist.

| Artifact | Required contents |
| --- | --- |
| Requirements | Goals, non-goals, constraints, acceptance criteria, assumptions, risks |
| Exploration Synthesis | Paired-agent findings, local file evidence, conflicts, recommendation |
| Architecture Decision | Ownership, boundaries, lifecycle, data flow, migration, rollback |
| Feature Design | Behavior, APIs/contracts, edge cases, acceptance tests |
| Implementation Plan | Task split, file ownership, tests first, verification commands, dependencies |
| Integration Report | Changed files, test evidence, unresolved risks, deferrals |
| End-to-End Review | Findings, fixes, residual risk, human-review checklist |

## Workflow

1. Extract requirements from the blueprint.
   - Identify user-visible goals, non-goals, constraints, data contracts, affected systems, risks, and acceptance criteria.
   - Create an assumptions list. Resolve only blocking ambiguity with the human; otherwise choose conservative defaults.
   - Output: Requirements.
   - Self-review until green: requirements are testable, traceable to the blueprint, and free of hidden implementation guesses.

2. Explore the codebase and prior art.
   - For each major unknown, dispatch paired exploration: two independent subagents (`Explore` and/or `feature-dev:code-explorer`) on the same bounded question.
   - Ask both for evidence: files, symbols, tests, commands, docs, and risks.
   - Compare their outputs. Keep agreements, investigate conflicts, and synthesize a recommendation with citations to local files.
   - Output: Exploration Synthesis.
   - Self-review until green: every architectural decision has evidence, and unresolved conflicts are listed.

3. Decide architecture.
   - Produce an architecture decision record covering boundaries, ownership, lifecycle, data flow, failure modes, migration strategy, and rollback implications.
   - Prefer existing project patterns and runtime-owned frameworks over new local machinery.
   - For object/physics standardization blueprints, decide how `ObjectControlState`, `ObjectPlayerQuery` / `ObjectPlayerParticipationPolicy`, `ObjectLifetimeOps`, canonical solid/touch/lifecycle profiles, compatibility wrappers, guard-baseline ratcheting, coordinate semantics, object ownership, participant state, rewind capture, and trace diagnostics fit before assigning implementation workers.
   - Output: Architecture Decision.
   - Self-review until green: decisions satisfy extracted requirements, avoid needless abstractions, and identify tests needed to prove behavior.

4. Design the feature.
   - Define concrete behavior, APIs, UI/UX if relevant, persistence/configuration changes, compatibility, and observability/debug hooks.
   - Include edge cases and acceptance tests mapped back to requirements.
   - Output: Feature Design.
   - Self-review until green: design is implementable, minimal, testable, and consistent with architecture.

5. Write the implementation plan.
   - Split work into independently implementable tasks with disjoint file ownership where possible.
   - For each task, specify tests first, implementation scope, verification command, reviewer checklist, and integration dependencies.
   - For compatibility-wrapper, guard, object, or trace work, require inventory/baseline tasks before behavior migrations. Baselines should allow historical violations with reasons and hard-fail new direct shortcuts; assign ownership for shared helpers, per-game object code, trace recorder/comparator changes, and docs/skill updates so workers do not overwrite each other.
   - Output: Implementation Plan.
   - Self-review until green: tasks can run in parallel without unclear ownership or hidden sequencing.

6. Implement in parallel.
   - Assign workers concrete file/module ownership and tell them the workspace may contain other concurrent edits.
   - Require each worker to follow test-first development for behavior changes unless the human explicitly waives it; document any exception.
   - Require each worker to run local verification, self-review, and report changed files.
   - Integrate worker branches/patches carefully. Do not overwrite human or other-agent changes.
   - Output: Integration Report.
   - Loop until green: failing tests, review findings, merge conflicts, or incomplete acceptance criteria go back to the responsible task owner.

7. Run end-to-end review.
   - Review requirements traceability, architecture consistency, code quality, tests, docs, migrations, config, performance, and operational risks.
   - Use independent reviewers (a fresh `feature-dev:code-reviewer` subagent that did not write the code); escalate to a high-effort reviewer for high-risk implementation.
   - Output: End-to-End Review.
   - Fix or explicitly defer findings. Self-review until green: no known blocker remains and all deferrals are human-visible.

8. Ask for final human review.
   - Present the blueprint summary, architecture summary, test evidence, changed files, risks, and open deferrals.
   - Do not merge before explicit human confirmation.

9. Merge after confirmation.
   - Ensure the branch follows repo policy (`feature/ai-` for features, `bugfix/ai-` for fixes).
   - Ensure the feature branch is up to date with `develop`.
   - Run required verification again after the final integration.
   - Respect repo documentation/trailer policy; do not use `--no-verify`.
   - Merge into `develop` only after human confirmation and green verification.

## Self-Review Gates

Each stage is green only when:

- The artifact exists and is specific enough for the next stage.
- Claims are backed by local evidence, tests, or explicit assumptions.
- Risks and tradeoffs are named.
- Relevant acceptance criteria are covered.
- A reviewer could reject the artifact without needing missing context.

Reject the stage and revise when:

- Required file paths, symbols, or line references are missing from exploration or review claims.
- Acceptance criteria are absent or cannot be tested.
- The implementation plan assigns overlapping file ownership without an integration order.
- A task lacks a verification command.
- A deferral is not visible in the final human-review summary.

## Exploration Pairing

Use this prompt shape for paired exploration:

```text
Investigate <bounded question> for <feature/blueprint>.
Return: relevant files/symbols, current behavior, constraints, risks, recommended approach, and verification ideas.
Do not edit files.
```

After both agents report:

- Compare file evidence and conclusions.
- Treat disagreements as leads, not votes.
- Inspect the repo locally for any disputed claim.
- Produce one recommendation that cites the strongest evidence.

## Prompt Templates

Architecture review:

```text
Review the Architecture Decision for <feature>.
Return blockers, missing constraints, mismatches with existing patterns, and tests needed to prove the design.
Do not edit files.
```

Feature design review:

```text
Review the Feature Design for <feature>.
Return ambiguous behavior, missing edge cases, API/data-contract risks, and acceptance-test gaps.
Do not edit files.
```

Implementation plan review:

```text
Review the Implementation Plan for <feature>.
Return task-order problems, overlapping file ownership, missing tests, missing verification commands, and parallelization risks.
Do not edit files.
```

Worker implementation:

```text
Implement task <task id> for <feature>.
Ownership: <files/modules>.
You are not alone in the codebase; do not revert others' edits. Adapt to concurrent changes.
Follow test-first development for behavior changes unless explicitly waived.
Run <verification command>.
Return changed files, tests run, failures, and self-review findings.
```

End-to-end review:

```text
Review the completed feature against Requirements, Architecture Decision, Feature Design, and Implementation Plan.
Return blockers first, then non-blocking risks, missing tests, documentation/policy gaps, and merge readiness.
Do not edit files.
```

## Common Mistakes

| Mistake | Correction |
| --- | --- |
| Starting implementation from the blueprint | Extract requirements, explore, and decide architecture first. |
| Letting one agent's exploration become the plan | Pair exploration and synthesize evidence before deciding. |
| Parallelizing dependent edits | Split by ownership; keep sequencing explicit. |
| Treating self-review as a summary | Require pass/fail criteria and loop until green. |
| Merging after agent review only | Stop for explicit human confirmation before merging to `develop`. |
