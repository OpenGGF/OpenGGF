---
name: trace-green-fleet
description: Use when driving multiple failing Sonic-engine *TraceReplay tests toward green, especially when the user wants one isolated worktree per trace, parallel triage/fix/verify, trace frontier movement, or a green-fleet style trace cleanup.
---

# Trace Green Fleet

Coordinate failing `*TraceReplay` tests through isolated worktrees and strict trace-parity gates. This is the Codex facsimile of `.claude/workflows/trace-green-fleet.js`: the workflow is native Codex skill guidance, not a standalone JavaScript runner.

## Inputs

Prefer a caller-supplied failing list. Each item should include:

```json
{
  "testClass": "TestS3kAizTraceReplay",
  "game": "s3k",
  "zone": "aiz",
  "firstErrorFrame": 12345,
  "field": "x_pos"
}
```

Optional `greenByGame` may provide same-game passing trace classes:

```json
{
  "greenByGame": {
    "s1": ["TestS1Ghz1TraceReplay"],
    "s2": ["TestS2Ehz1TraceReplay"],
    "s3k": ["TestS3kAiz1SkipHeadless"]
  }
}
```

Optional `excluded` lists routes, trace classes, or fixture prefixes that another owner has
reserved. Treat exclusions as a hard scope boundary, not merely a queue filter:

```json
{
  "excluded": ["<game>/<route>", "TestReservedTraceReplay"]
}
```

If no failing list is supplied, run one discovery sweep from the main checkout.

## Constants

- Repo root: the current checkout — resolve it with `git rev-parse --show-toplevel`, never a hardcoded path
- Worktree: `.worktrees/trace-<game>-<zone>`
- Branch: `bugfix/ai-trace-<game>-<zone>`
- Max parallel traces when explicitly authorized: 4
- Diagnose/fix iterations per trace before verification review: 3
- ROMs: search the repo root for `.gen` files, select each game sensibly by filename/hash, and use the paths actually present; never create aliases or symlinks just to match an example
- S3K fallback green guards: `TestS3kAiz1SkipHeadless`, `TestSonic3kLevelLoading`, `TestSonic3kBootstrapResolver`, `TestSonic3kDecodingUtils`

ROM property names:

| Game | Property |
| --- | --- |
| `s1` | `s1.rom.path` |
| `s2` | `s2.rom.path` |
| `s3k` | `s3k.rom.path` |

Inside `.worktrees/*`, run Maven through `cmd /c "mvn.cmd ..."` when bare `mvn` fails with a Classworlds launcher error. Keep ROM paths double-quoted.

## Non-Negotiable Rules

Always use `trace-replay-bug-fixing` for actual trace investigation or fixes.

- Comparison-only: trace data is read-only diagnostic input. Never hydrate or sync engine state from trace data in the per-frame test loop.
- No zone, route, frame, or "known failing trace" carve-outs. Model ROM state: object id/routine, status/control bits, physics profile, event flag, frame-counter visibility, or data-driven condition.
- Cite disassembly in code comments and summaries when behavior changes.
- Cross-game parity: before changing shared physics, collision, sidekick, oscillation, or shared object code, check all three disassemblies. Universal corrections must keep all games green. Real per-game divergences must use the smallest accurate owner from `docs/architecture/per-game-rule-placement.md`, never `gameId`.
- Environmental flakes are not parity failures. Ignore `UnsatisfiedLinkError` from native extraction races and `TestBundledConfigResource` config contamination unless the targeted trace itself fails on a real `AssertionFailedError` with `First error: frame N -- <field>`.
- Judge the targeted trace by its own Surefire class line and `target/trace-reports/<game>_<zone>_report.json`, not MSE's project-wide `total=NNNN`.
- This repo may have concurrent agent sessions. Stage only files you changed. Never use `git add -A`.
- Never use `git stash`; stash is shared across worktrees. For clean-HEAD A/B checks, copy changed files aside, restore with `git checkout -- <path>`, run the baseline, then restore the copies, or create a throwaway worktree.
- Do not delete other sessions' `.claude/worktrees/*` or `.worktrees/*`.
- Freeze caller exclusions before discovery. Excluded routes/classes must not be discovered,
  run, inspected, assigned, fixed, or documented. Apply the exclusion to shell globs and
  broad validation commands before launching them; never run a wildcard sweep and filter
  the report afterward. Repeat the exclusion ledger in every worker prompt and final
  validation checklist.

## Codex Orchestration Contract

This skill is a conductor workflow. Do not collapse it into "main agent does each phase in a loop" when the user explicitly asks for a fleet, parallel agents, subagents, one agent per trace, or worktree threads.

Two layers:

- **Conductor:** the current Codex thread. Owns discovery, queueing, worker prompts, stage handoffs, result validation, final summary, and any later integration into `develop`. The conductor never edits a trace worktree while a worker owns it.
- **Workers:** bounded Codex worker agents or worktree threads. They do the noisy trace-specific work and return structured summaries. Each worker owns exactly one trace stage and one worktree.

Control flow is the point. The conductor must preserve this structure:

```text
Discover -> [ Triage -> Fix -> Verify ] per failing trace, max 4 active trace pipelines
```

### Authorization

Use subagents only when explicitly authorized by the prompt. Explicit authorization includes the user asking for a fleet, parallel run, subagents, workers, one agent per trace, or invoking this skill specifically to run the fleet. If not authorized, run the same controller serially and say that parallel workers were not used.

### Slot Scheduler

Maintain a queue of failing trace items and at most 4 active trace pipelines.

1. Start triage workers for up to 4 queued traces.
2. When any triage worker returns `setupOk=true`, immediately start that trace's fix worker; do not wait for all triages.
3. When any fix worker returns, immediately start that trace's verify worker.
4. When a verify worker returns, free that trace's pipeline slot and start triage for the next queued trace.
5. Continue until every trace has a verify result or a recorded stage failure.

This simulates the Claude `pipeline(group, stageTriage, stageFix, stageVerify)` behavior: traces flow independently, so one trace may be in Fix while another is still in Triage. Do not insert a global barrier unless a shared-machine failure requires backing off.

With Codex subagent tools, use this pattern:

- `spawn_agent` a fresh worker for Discovery and each execution stage. Use
  `fork_turns="none"` whenever a model override is supplied; pass the
  failing/triage/fix JSON and the rules explicitly instead of forking conductor
  context.
- Keep a conductor-side table keyed by `<game>/<zone>` with `stage`, `agentId`, `worktreePath`, `branch`, and prior stage output.
- `wait_agent` on the active agent IDs as a set. When one completes, validate its JSON, update the table, and immediately spawn the next stage for that trace if eligible.
- While workers run, the conductor should do non-overlapping work: classify discovery output, prepare the next prompts, inspect summaries, or update the result table. Do not duplicate a worker's trace investigation locally.
- Close completed workers once their output has been consumed and recorded.

### Worker Rules

Every worker prompt must include:

- the relevant input object from the prior stage,
- the Non-Negotiable Rules section,
- "You are not alone in the codebase; do not revert others' edits. Stage only specific files you changed. Never use `git add -A` or `git stash`.",
- "Return exactly the requested JSON object, followed by a short human-readable note if needed.",
- "Do not touch other trace worktrees."

Use `worker` agents for stage work when the subagent tool is available. Use local worktree threads only when that is the available orchestration surface.

### Structured Handoff

Stage output is the contract between workers. The conductor must reject or rerun a worker result if required fields are missing or internally inconsistent. Do not parse vague prose as a substitute for the JSON object.

### Model Routing Contract

The active conductor cannot reroute itself. Start a fleet on Sol at medium effort when practical, but that is a launch recommendation, not an in-session override. The conductor selects every child route before spawn and must use only `gpt-5.6-terra` and `gpt-5.6-sol`:

| Stage | Exact route |
| --- | --- |
| Discovery | `gpt-5.6-terra/low` |
| Triage | `gpt-5.6-terra/medium` |
| Narrow Fix | `gpt-5.6-terra/medium` |
| Shared/deep Fix | `gpt-5.6-sol/high` |
| Ordinary Verify | `gpt-5.6-terra/medium` |
| Shared, Sol-fixed, disputed, or escalated Verify | `gpt-5.6-sol/high` |

Every Discovery, Triage, Fix, and Verify result embeds this conductor-owned routing object alongside its stage fields. Discovery, Triage, and Verify set `attemptCount` to `0`; the field counts only Fix edit/test attempts, remains cumulative across replacement Fix workers, and is the sole input to `totalAttemptCount`. Discovery sets `complexity` to `mechanical`. `modelRoute` is authored and preserved by the conductor; workers must not invent or amend it. Runtime telemetry is nullable and recorded only when exposed, never estimated.

For a route plan, report each category's default route even when earlier execution-stage fields are not yet known. A `modelRoute` lists the initial route and only a replacement route on an actual model escalation; do not duplicate a route merely because separate scenario branches use the same stage. Thus ordinary Verify is Terra-medium, shared/Sol-fixed/disputed/escalated Verify is Sol-high, a narrow stalled Fix is `[terra-medium, sol-high]`, and an accepted shared Fix is `[sol-high]`.

```json
{
  "requestedModel": "gpt-5.6-terra",
  "requestedEffort": "medium",
  "actualModel": null,
  "actualEffort": null,
  "complexity": "narrow",
  "confidence": "high",
  "beforeFrame": null,
  "afterFrame": null,
  "status": "pending",
  "attemptCount": 0,
  "regressionCount": 0,
  "sharedSurfaces": [],
  "needsEscalation": false,
  "escalationReasons": [],
  "modelRoute": ["gpt-5.6-terra/medium"],
  "usage": {
    "inputTokens": null,
    "cachedInputTokens": null,
    "outputTokens": null,
    "reasoningTokens": null
  },
  "durationMs": null
}
```

Allowed `complexity`: `mechanical`, `narrow`, `shared`, `deep`. Allowed `confidence`: `high`, `medium`, `low`. Allowed `escalationReasons`: `no-frontier-advance`, `multiple-owners`, `missing-rom-basis`, `low-confidence`, `unresolved-ownership`, `reasoning-insufficient`, `shared-surface-discovered`, `cross-game-semantics`, `regression`, `context-contradiction`, `recorder-evidence-required`, `causal-thread-lost`.

Before spawning, the conductor validates required routing fields, enums, and the requested route. It may request one schema-only repair from a completed worker; if unavailable, it may rerun once. A second malformed result is a stage error. `attemptCount` remains cumulative across replacement Fix workers and is always zero for Discovery, Triage, and Verify. Aggregate usage or duration only when every contributing value is exposed; otherwise use `null`.

#### Deterministic routing and ownership

- Terra Triage escalates once to Sol Triage for `multiple-owners`, `low-confidence`, `unresolved-ownership`, or `missing-rom-basis`. Accepted shared/deep Triage goes directly to Sol Fix. If Sol Triage still lacks ROM basis, return `missing-rom-basis` and block Fix.
- Object-local collision/profile changes remain narrow. Route directly to Sol Fix for shared runtime physics, collision, sidekick, camera, oscillation, bootstrap, object lifecycle, recorder/publication contracts, or cross-game semantics.
- A Terra Fix stops after two unsuccessful attempts and escalates once to Sol Fix for one final attempt (three total). Escalate also for no frontier advance, context contradiction, a newly discovered shared surface/cross-game semantics, recorder evidence required, causal-thread loss, or insufficient reasoning. Never change effort in place.
- Route Verify directly to Sol after any Sol Fix, shared edit, disputed ROM evidence, or prior escalation. A Terra Verify that detects a regression returns an escalation handoff; Sol independently repeats Verify before acceptance.
- Workers own a worktree sequentially, never concurrently. On Terra-to-Sol Fix handoff, Terra returns attempt history and dirty-worktree state; retain edits only when each is ROM-backed and listed in `filesTouched`, otherwise restore only its own edits. The conductor ends Terra ownership before spawning Sol. Sol first reviews the retained diff, may restore only predecessor-listed files, then performs at most one edit/test attempt.

| Condition before/after a stage | Conductor decision |
| --- | --- |
| Unsupported model ID | Reject before spawn. |
| Required Sol unavailable | Block the escalated stage; never silently fall back. |
| A stage already escalated once | Return a blocker; never escalate twice. |
| Sol Triage still lacks ROM basis | Block Fix with `missing-rom-basis`. |
| Sol worker fails | Return a blocker. |
| Terra Triage has multiple owners, low confidence, unresolved ownership, or no ROM basis | Spawn one Sol Triage. |
| Accepted shared/deep Triage or listed shared/cross-game owner | Spawn Sol Fix directly. |
| Object-local collision/profile owner | Keep Narrow Fix on Terra. |
| Terra Fix has the listed escalation evidence | Stop after at most two unsuccessful attempts and spawn one Sol final attempt. |
| Sol Fix, shared edit, disputed ROM evidence, or escalated handoff | Spawn Sol Verify directly. |
| Terra Verify newly detects regression | Spawn one Sol Verify. |

For Codex, a model override requires an un-forked or explicitly bounded context.
Use `spawn_agent(fork_turns="none", model="gpt-5.6-terra",
reasoning_effort="low"|"medium", ...)` and
`spawn_agent(fork_turns="none", model="gpt-5.6-sol",
reasoning_effort="high", ...)`.

Required stage objects (each also embeds the Model Routing Contract above, including `beforeFrame`, `afterFrame`, `status`, cumulative `attemptCount`, and `regressionCount`):

- Triage object: `setupOk`, `worktreePath`, `branch`, `firstErrorFrame`, `field`, `brief`, `hypothesis`, `disasmCites`
- Fix object: `changed`, `filesTouched`, `beforeFrame`, `afterFrame`, `targetedPasses`, `romCites`, `summary`, `worktreePath`, `branch`
- Verify object: `status`, `accepted`, `genuine`, `reviewerRejected`, `committed`,
  `commit`, `romCitations`, `verificationResults`, `regressionsIntroduced`,
  `afterFrame`, `frontierLogUpdated`, `notes`

Cross-check before spawning the next stage:

- Fix may start only if triage has `setupOk=true`, a worktree path, branch, and a ROM-cited hypothesis.
- Verify may start only if fix returned `worktreePath`, `branch`, `beforeFrame`, and `changed`.
- Commit may be accepted only from Verify, never from Triage or Fix.

### Conductor Integration Boundary

The fleet may commit genuine fixes on per-trace branches inside their worktrees. It must not merge, push, or update `develop`.

After the fleet returns, the conductor reports committed per-trace branches and commits. Integrating them into `develop` is a separate conductor-owned step:

1. Create a fresh integration worktree from current `origin/develop`.
2. Cherry-pick committed genuine trace fixes.
3. Resolve additive `CHANGELOG.md` and `docs/status/trace-frontier-log.md` conflicts deliberately.
4. Compose-verify the advanced/greened traces plus green guards against the current branch.
5. Only then push or hand off for PR/merge, following the user's requested integration path.

The fleet proposes; the conductor disposes.

### Worker Prompt Templates

Every stage prompt begins with this byte-stable prefix; put the dynamic JSON handoff after it. Start from compact first-divergence evidence and paths to large reports/disassemblies, then expand context when causality requires it.

```text
Follow the trace fleet non-negotiable rules. Trace data is comparison-only; model ROM state, cite ROM/disassembly evidence, and do not use zone/route/frame/game carve-outs. Return the requested stage JSON with the conductor-owned routing object unchanged. Do not estimate runtime telemetry. You own this worktree only for this stage; do not revert others' edits, stage only files you changed, and never use git add -A or git stash.
```

Triage worker:

```text
Follow the trace fleet non-negotiable rules. Trace data is comparison-only; model ROM state, cite ROM/disassembly evidence, and do not use zone/route/frame/game carve-outs. Return the requested stage JSON with the conductor-owned routing object unchanged. Do not estimate runtime telemetry. You own this worktree only for this stage; do not revert others' edits, stage only files you changed, and never use git add -A or git stash.

Routing: <CONDUCTOR_ROUTING_JSON>

Input: <FAILING_ITEM_JSON>

Triage failing trace <testClass> (<game> <zone>) in an isolated worktree.

Create or reuse worktree .worktrees/trace-<game>-<zone> on bugfix/ai-trace-<game>-<zone> from develop. Rerun the targeted trace, run TraceTriageTool, inspect the relevant disassembly, and return the TRIAGE JSON object. Do not edit engine code.

<NON_NEGOTIABLE_RULES>
```

Fix worker:

```text
Follow the trace fleet non-negotiable rules. Trace data is comparison-only; model ROM state, cite ROM/disassembly evidence, and do not use zone/route/frame/game carve-outs. Return the requested stage JSON with the conductor-owned routing object unchanged. Do not estimate runtime telemetry. You own this worktree only for this stage; do not revert others' edits, stage only files you changed, and never use git add -A or git stash.

Routing: <CONDUCTOR_ROUTING_JSON>

Input: <TRIAGE_JSON>

Implement a trace fix for <testClass> in <worktreePath> on <branch>.

Use the triage hypothesis and disassembly cites. Terra makes at most two unsuccessful attempts; a Sol escalation gets exactly one final attempt, with a cumulative cap of three. Rerun the targeted trace after each edit and capture beforeFrame and afterFrame. Do not commit.

<NON_NEGOTIABLE_RULES>
```

Verify worker:

```text
Follow the trace fleet non-negotiable rules. Trace data is comparison-only; model ROM state, cite ROM/disassembly evidence, and do not use zone/route/frame/game carve-outs. Return the requested stage JSON with the conductor-owned routing object unchanged. Do not estimate runtime telemetry. You own this worktree only for this stage; do not revert others' edits, stage only files you changed, and never use git add -A or git stash.

Routing: <CONDUCTOR_ROUTING_JSON>

Input: <FIX_JSON>

Independently verify <testClass> in <worktreePath> on <branch>.

Rerun the targeted trace and same-game green guard. Apply the genuineness gate. Commit only if genuine=true, changed=true, and status is green, advanced, or advanced-with-regression. Return the VERIFY JSON object.

<NON_NEGOTIABLE_RULES>
```

## Phase 0: Discover

If the caller supplied `failing`, use it. Otherwise Discovery is real worker work:
spawn one fresh `gpt-5.6-terra`/low child with `fork_turns="none"` and a
self-contained prompt, then have that child run one sweep from the repo root and
return the Discovery object. The conductor validates and schedules its result; it
must not perform fleet discovery itself.

```bash
mvn -q -Dmse=relaxed "-Ds1.rom.path=$S1_ROM" "-Ds2.rom.path=$S2_ROM" "-Ds3k.rom.path=$S3K_ROM" "-Dtest=*TraceReplay" test
```

Do not use this wildcard form when any exclusion is active. First enumerate concrete,
executable replay classes, remove every excluded class/route, print the resulting allowlist,
then pass only that comma-separated allowlist to `-Dtest=...`.

Then read `target/surefire-reports/*TraceReplay*.txt`.

Classify:

- Failing: report has `AssertionFailedError` with `First error: frame N -- <field>`.
- Passing: report has `Tests run: N, Failures: 0, Errors: 0` with `N >= 1`.
- Ignored flake: only `UnsatisfiedLinkError`, missing `lwjgl.dll` / `glfw.dll`, or config contamination. If unsure, rerun that one class in isolation.

Skip abstract bases and non-replay guard tests such as `TestTraceReplayInvariantGuard`.

Attach same-game green guards to each failing item. For S3K, add the fallback green guards listed above. Exclude the failing class itself.

Discovery output example:

```json
{"failing": [], "requestedModel": "gpt-5.6-terra", "requestedEffort": "low", "actualModel": null, "actualEffort": null, "complexity": "mechanical", "confidence": "high", "beforeFrame": null, "afterFrame": null, "status": "completed", "attemptCount": 0, "regressionCount": 0, "sharedSurfaces": [], "needsEscalation": false, "escalationReasons": [], "modelRoute": ["gpt-5.6-terra/low"], "usage": {"inputTokens": null, "cachedInputTokens": null, "outputTokens": null, "reasoningTokens": null}, "durationMs": null}
```

## Phase 1: Triage

For each failing item:

1. Create or reuse a persistent worktree from `develop`:

   ```bash
   git worktree add -b bugfix/ai-trace-<game>-<zone> .worktrees/trace-<game>-<zone> develop
   ```

   If the branch or path already exists, reuse it. Do not delete unrelated worktrees.

2. In the worktree, rerun the targeted trace:

   ```bash
   mvn -q -Dmse=relaxed -Dsurefire.forkCount=1 -DreuseForks=true "-D<romProp>=<discovered rom path>" "-Dtest=<testClass>#replayMatchesTrace" test
   ```

3. Run the triage tool:

   ```bash
   mvn -q -Dmse=relaxed exec:java "-Dexec.mainClass=com.openggf.tools.TraceTriageTool" "-Dexec.args=<game> <zone>"
   ```

4. Read the relevant disassembly around the diverging field/routine:
   - `s1`: use `s1disasm-guide`
   - `s2`: use `s2disasm-guide`
   - `s3k`: use `s3k-disasm-guide`

Do not edit engine code during triage.

Triage output:

```json
{
  "setupOk": true,
  "worktreePath": ".worktrees/trace-s3k-aiz",
  "branch": "bugfix/ai-trace-s3k-aiz",
  "firstErrorFrame": 12345,
  "field": "x_pos",
  "brief": "one concise divergence brief",
  "hypothesis": "ROM-cited fix hypothesis",
  "disasmCites": ["docs/skdisasm/sonic3k.asm:12345"],
  "requestedModel": "gpt-5.6-terra", "requestedEffort": "medium", "actualModel": null, "actualEffort": null,
  "complexity": "narrow", "confidence": "high", "beforeFrame": 12345, "afterFrame": 12345, "status": "accepted",
  "attemptCount": 0, "regressionCount": 0, "sharedSurfaces": [], "needsEscalation": false, "escalationReasons": [],
  "modelRoute": ["gpt-5.6-terra/medium"], "usage": {"inputTokens": null, "cachedInputTokens": null, "outputTokens": null, "reasoningTokens": null}, "durationMs": null
}
```

## Phase 2: Fix

Implement only after triage has a confirmed first divergence and ROM-backed hypothesis.

For a Narrow Terra Fix, make at most two unsuccessful attempts. Return an escalation handoff rather than a third Terra attempt when the frontier does not advance or any deterministic escalation condition applies. A replacement Sol Fix reviews the retained diff and gets the one final attempt; all workers together remain capped at three attempts.

1. Make the smallest disassembly-backed engine change.
2. Run the targeted trace command.
3. Read:
   - `target/trace-reports/<game>_<zone>_report.json`
   - `target/trace-reports/<game>_<zone>_context.txt`
4. Record first-error frame before the first edit and after the last edit.

Stop rather than hack if the fix needs larger missing architecture, unimplemented objects, or unclear ROM basis.

Fix output:

```json
{
  "changed": true,
  "filesTouched": ["src/main/java/..."],
  "beforeFrame": 12345,
  "afterFrame": 13000,
  "targetedPasses": false,
  "romCites": ["docs/skdisasm/sonic3k.asm:12345"],
  "summary": "what changed and why",
  "worktreePath": ".worktrees/trace-s3k-aiz",
  "branch": "bugfix/ai-trace-s3k-aiz",
  "requestedModel": "gpt-5.6-terra", "requestedEffort": "medium", "actualModel": null, "actualEffort": null,
  "complexity": "narrow", "confidence": "high", "status": "advanced", "attemptCount": 1, "regressionCount": 0,
  "sharedSurfaces": [], "needsEscalation": false, "escalationReasons": [], "modelRoute": ["gpt-5.6-terra/medium"],
  "usage": {"inputTokens": null, "cachedInputTokens": null, "outputTokens": null, "reasoningTokens": null}, "durationMs": null
}
```

Embed the Model Routing Contract in this object. Set `beforeFrame`, `afterFrame`, `status`, cumulative `attemptCount`, and `regressionCount` from observed results rather than leaving them implicit.

## Phase 3: Verify

Verification must be independent of the fix attempt.

1. Rerun the targeted trace in the worktree.
2. Run the same-game green regression guard if available:

   ```bash
   mvn -q -Dmse=relaxed -Dsurefire.forkCount=1 -DreuseForks=true "-D<romProp>=<discovered rom path>" "-Dtest=<comma-separated-green-classes>" test
   ```

3. Ignore environmental flakes only after confirming they are not real parity divergences.

Genuineness gate. Set `genuine=true` only if all are true:

- The targeted trace advanced (`afterFrame > beforeFrame`) or went green.
- The behavior is backed by disassembly citations.
- The change models actual ROM state or behavior.
- The change is not a zone/route/frame/gameId carve-out, tolerance band, trace-state hydration, or no-op.
- Per-game divergence is gated at the narrowest owning abstraction.

Status:

| Status | Meaning |
| --- | --- |
| `green` | targeted trace passes, genuine, no same-game regressions |
| `advanced` | targeted trace advanced, genuine, no same-game regressions |
| `advanced-with-regression` | targeted trace advanced or greened, genuine, but same-game green trace regressed |
| `no-change` | targeted frame unchanged or worse |
| `rejected-not-genuine` | frame advanced but the gate failed |
| `error` | build or test could not run |

Commit if and only if `genuine=true`, `changed=true`, and status is `green`, `advanced`, or `advanced-with-regression`. A real same-game regression does not block a genuine commit, but it must be recorded.

Preserve `accepted`, `genuine`, and `reviewerRejected` as independent booleans.
Set `reviewerRejected=true` for `rejected-not-genuine`; never rename a detected
regression to `regressed`. Record ROM evidence as structured `romCitations` and
every exact regression-guard class/outcome in `verificationResults`, so aggregate
token, rejection, citation-completeness, and regression-detection metrics can be
computed from results without parsing notes.

Commit requirements:

- Stage only changed source files plus `CHANGELOG.md` and `docs/status/trace-frontier-log.md` when required.
- Update `docs/status/trace-frontier-log.md` with exact command, worktree, branch, status, error count, before/after first-error frame/field, and any `REGRESSION INTRODUCED:` lines.
- This touches `src/main` for real fixes, so update and stage `CHANGELOG.md`.
- Run `git config core.hooksPath .githooks`.
- Subject: `fix(trace): <zone> <one-line root cause>`. If it introduces a regression, add `(regresses <trace>@<frame>, follow-up)`.
- Fill every required trailer: `Changelog`, `Guide`, `Known-Discrepancies`, `S3K-Known-Discrepancies`, `Agent-Docs`, `Configuration-Docs`, `Skills`.
- Do not use `--no-verify`.

Verify output:

```json
{
  "trace": "TestS3kAizTraceReplay",
  "game": "s3k",
  "zone": "aiz",
  "status": "advanced",
  "accepted": true,
  "genuine": true,
  "reviewerRejected": false,
  "committed": true,
  "commit": "abcdef1",
  "romCitations": [
    {
      "path": "docs/skdisasm/sonic3k.asm",
      "lineStart": 12345,
      "lineEnd": 12350,
      "symbol": "Obj_AIZ",
      "claim": "the object updates ROM x_pos before collision"
    }
  ],
  "verificationResults": [
    {
      "testClass": "com.openggf.tests.TestS3kAiz1SkipHeadless",
      "outcome": "passed",
      "report": "target/surefire-reports/com.openggf.tests.TestS3kAiz1SkipHeadless.txt"
    }
  ],
  "regressionsIntroduced": [],
  "afterFrame": 13000,
  "frontierLogUpdated": true,
  "notes": "verification summary",
  "requestedModel": "gpt-5.6-terra", "requestedEffort": "medium", "actualModel": null, "actualEffort": null,
  "complexity": "narrow", "confidence": "high", "beforeFrame": 12345, "attemptCount": 0, "regressionCount": 0,
  "sharedSurfaces": [], "needsEscalation": false, "escalationReasons": [], "modelRoute": ["gpt-5.6-terra/medium"],
  "usage": {"inputTokens": null, "cachedInputTokens": null, "outputTokens": null, "reasoningTokens": null}, "durationMs": null
}
```

Embed the Model Routing Contract in this object. `regressionCount` is the number of newly detected regressions; a Terra regression requires the Sol Verify handoff above.

## Final Summary

Return:

```json
{
  "discovered": 4,
  "green": 1,
  "advanced": 2,
  "advancedWithRegression": 1,
  "committed": 4,
  "rejectedNotGenuine": 0,
  "routing": {
    "stages": [],
    "totalUsage": {
      "inputTokens": null,
      "cachedInputTokens": null,
      "outputTokens": null,
      "reasoningTokens": null
    },
    "totalDurationMs": null,
    "acceptedResults": 0,
    "tracesGreened": 0,
    "escalations": 0
  },
  "regressionQueue": [
    {
      "causedBy": "TestS3kAizTraceReplay",
      "commit": "abcdef1",
      "regression": "TestS3kHczCompleteRunTraceReplay: green -> frame 100/x_pos"
    }
  ],
  "results": []
}
```

`routing.stages` preserves every completed stage routing object, including
`advanced-with-regression` and `rejected-not-genuine` Verify results.
`acceptedResults` counts results whose Verify object has `accepted=true`; preserve
`accepted`, `genuine`, and `reviewerRejected` rather than inferring one from the
status. `totalUsage` and `totalDurationMs` are aggregates only if every
contributing runtime value is exposed; otherwise their affected aggregate is
`null`. Total attempts count only Fix edit/test attempts.

Also summarize human-readable:

- which traces greened,
- which traces advanced,
- which commits landed,
- which worktrees remain for review,
- any introduced regressions that need follow-up,
- any blocked traces and why.

## Queue and Dynamic-Art Frontiers

Classify queue-aware traces before assigning fixes:

- `queue.*` is a zero-tolerance physical-queue comparator frontier;
- `dynamic_art.*` is a zero-tolerance DPLC/player-art lifecycle frontier;
- a hardware-timing admission error means the schema-2 authority could not
  match a production-submitted prepared S3K job, not that the comparator found
  an ordinary field mismatch.

For every affected trace preserve the first frame, exact field or admission
reason, and total error count. Queue/DPLC failures take precedence over
downstream physics, object, event, or audio symptoms. Do not promote a fixture
to audited status unless native capture used `--load-queue-state` and metadata
advertises `load_queue_state_per_frame`; DPLC/player-art evidence additionally
requires `dynamic_art_transfer_state_per_frame_v1`.

Update `docs/status/trace-frontier-log.md` whenever the first queue or
`dynamic_art` frontier moves, a green regresses, or an admission error changes.
The evidence is comparison-only: agents may fix production queue behavior but
must never hydrate gameplay, submit work, or fabricate readiness from a trace.
