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
  "testClass": "TestSonic3kAiz1TraceReplay",
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
    "s1": ["TestSonic1Ghz1TraceReplay"],
    "s2": ["TestSonic2Ehz1TraceReplay"],
    "s3k": ["TestS3kAiz1SkipHeadless"]
  }
}
```

If no failing list is supplied, run one discovery sweep from the main checkout.

## Constants

- Repo root: `C:\Users\farre\IdeaProjects\sonic-engine`
- Worktree: `.worktrees/trace-<game>-<zone>`
- Branch: `bugfix/ai-trace-<game>-<zone>`
- Max parallel traces when explicitly authorized: 4
- Diagnose/fix iterations per trace before verification review: 3
- ROM aliases in repo root: `s1.gen`, `s2.gen`, `s3k.gen`
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
- Cross-game parity: before changing shared physics, collision, sidekick, oscillation, or shared object code, check all three disassemblies. Universal corrections must keep all games green. Real per-game divergences must be gated by `PhysicsFeatureSet` or the narrowest owning abstraction, never `gameId`.
- Environmental flakes are not parity failures. Ignore `UnsatisfiedLinkError` from native extraction races and `TestBundledConfigResource` config contamination unless the targeted trace itself fails on a real `AssertionFailedError` with `First error: frame N -- <field>`.
- Judge the targeted trace by its own Surefire class line and `target/trace-reports/<game>_<zone>_report.json`, not MSE's project-wide `total=NNNN`.
- This repo may have concurrent agent sessions. Stage only files you changed. Never use `git add -A`.
- Never use `git stash`; stash is shared across worktrees. For clean-HEAD A/B checks, copy changed files aside, restore with `git checkout -- <path>`, run the baseline, then restore the copies, or create a throwaway worktree.
- Do not delete other sessions' `.claude/worktrees/*` or `.worktrees/*`.

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

- `spawn_agent` a `worker` for each stage task. Do not fork broad context unless the worker needs it; pass the failing/triage/fix JSON and the rules instead.
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

Required stage objects:

- Triage object: `setupOk`, `worktreePath`, `branch`, `firstErrorFrame`, `field`, `brief`, `hypothesis`, `disasmCites`
- Fix object: `changed`, `filesTouched`, `beforeFrame`, `afterFrame`, `targetedPasses`, `romCites`, `summary`, `worktreePath`, `branch`
- Verify object: `status`, `genuine`, `committed`, `commit`, `regressionsIntroduced`, `afterFrame`, `frontierLogUpdated`, `notes`

Cross-check before spawning the next stage:

- Fix may start only if triage has `setupOk=true`, a worktree path, branch, and a ROM-cited hypothesis.
- Verify may start only if fix returned `worktreePath`, `branch`, `beforeFrame`, and `changed`.
- Commit may be accepted only from Verify, never from Triage or Fix.

### Conductor Integration Boundary

The fleet may commit genuine fixes on per-trace branches inside their worktrees. It must not merge, push, or update `develop`.

After the fleet returns, the conductor reports committed per-trace branches and commits. Integrating them into `develop` is a separate conductor-owned step:

1. Create a fresh integration worktree from current `origin/develop`.
2. Cherry-pick committed genuine trace fixes.
3. Resolve additive `CHANGELOG.md` and `docs/TRACE_FRONTIER_LOG.md` conflicts deliberately.
4. Compose-verify the advanced/greened traces plus green guards against the current branch.
5. Only then push or hand off for PR/merge, following the user's requested integration path.

The fleet proposes; the conductor disposes.

### Worker Prompt Templates

Triage worker:

```text
Triage failing trace <testClass> (<game> <zone>) in an isolated worktree.
Input: <FAILING_ITEM_JSON>

Create or reuse worktree .worktrees/trace-<game>-<zone> on bugfix/ai-trace-<game>-<zone> from develop. Rerun the targeted trace, run TraceTriageTool, inspect the relevant disassembly, and return the TRIAGE JSON object. Do not edit engine code.

<NON_NEGOTIABLE_RULES>
```

Fix worker:

```text
Implement a trace fix for <testClass> in <worktreePath> on <branch>.
Input: <TRIAGE_JSON>

Use the triage hypothesis and disassembly cites. Iterate diagnose->fix up to 3 times, rerunning the targeted trace after each edit. Capture beforeFrame and afterFrame. Do not commit.

<NON_NEGOTIABLE_RULES>
```

Verify worker:

```text
Independently verify <testClass> in <worktreePath> on <branch>.
Input: <FIX_JSON>

Rerun the targeted trace and same-game green guard. Apply the genuineness gate. Commit only if genuine=true, changed=true, and status is green, advanced, or advanced-with-regression. Return the VERIFY JSON object.

<NON_NEGOTIABLE_RULES>
```

## Phase 0: Discover

If the caller supplied `failing`, use it. Otherwise run one sweep from the repo root:

```powershell
cmd /c "mvn.cmd -q -Dmse=relaxed ""-Ds1.rom.path=C:\Users\farre\IdeaProjects\sonic-engine\s1.gen"" ""-Ds2.rom.path=C:\Users\farre\IdeaProjects\sonic-engine\s2.gen"" ""-Ds3k.rom.path=C:\Users\farre\IdeaProjects\sonic-engine\s3k.gen"" ""-Dtest=*TraceReplay"" test"
```

Then read `target/surefire-reports/*TraceReplay*.txt`.

Classify:

- Failing: report has `AssertionFailedError` with `First error: frame N -- <field>`.
- Passing: report has `Tests run: N, Failures: 0, Errors: 0` with `N >= 1`.
- Ignored flake: only `UnsatisfiedLinkError`, missing `lwjgl.dll` / `glfw.dll`, or config contamination. If unsure, rerun that one class in isolation.

Skip abstract bases and non-replay guard tests such as `TestTraceReplayInvariantGuard`.

Attach same-game green guards to each failing item. For S3K, add the fallback green guards listed above. Exclude the failing class itself.

## Phase 1: Triage

For each failing item:

1. Create or reuse a persistent worktree from `develop`:

   ```powershell
   git worktree add -b bugfix/ai-trace-<game>-<zone> .worktrees/trace-<game>-<zone> develop
   ```

   If the branch or path already exists, reuse it. Do not delete unrelated worktrees.

2. In the worktree, rerun the targeted trace:

   ```powershell
   cmd /c "mvn.cmd -q -Dmse=relaxed -Dsurefire.forkCount=1 -DreuseForks=true ""-D<romProp>=C:\Users\farre\IdeaProjects\sonic-engine\<rom>.gen"" ""-Dtest=<testClass>#replayMatchesTrace"" test"
   ```

3. Run the triage tool:

   ```powershell
   cmd /c "mvn.cmd -q -Dmse=relaxed exec:java ""-Dexec.mainClass=com.openggf.tools.TraceTriageTool"" ""-Dexec.args=<game> <zone>"""
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
  "disasmCites": ["docs/skdisasm/sonic3k.asm:12345"]
}
```

## Phase 2: Fix

Implement only after triage has a confirmed first divergence and ROM-backed hypothesis.

Loop up to 3 times:

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
  "branch": "bugfix/ai-trace-s3k-aiz"
}
```

## Phase 3: Verify

Verification must be independent of the fix attempt.

1. Rerun the targeted trace in the worktree.
2. Run the same-game green regression guard if available:

   ```powershell
   cmd /c "mvn.cmd -q -Dmse=relaxed -Dsurefire.forkCount=1 -DreuseForks=true ""-D<romProp>=C:\Users\farre\IdeaProjects\sonic-engine\<rom>.gen"" ""-Dtest=<comma-separated-green-classes>"" test"
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

Commit requirements:

- Stage only changed source files plus `CHANGELOG.md` and `docs/TRACE_FRONTIER_LOG.md` when required.
- Update `docs/TRACE_FRONTIER_LOG.md` with exact command, worktree, branch, status, error count, before/after first-error frame/field, and any `REGRESSION INTRODUCED:` lines.
- This touches `src/main` for real fixes, so update and stage `CHANGELOG.md`.
- Run `git config core.hooksPath .githooks`.
- Subject: `fix(trace): <zone> <one-line root cause>`. If it introduces a regression, add `(regresses <trace>@<frame>, follow-up)`.
- Fill every required trailer: `Changelog`, `Guide`, `Known-Discrepancies`, `S3K-Known-Discrepancies`, `Agent-Docs`, `Configuration-Docs`, `Skills`.
- Do not use `--no-verify`.

Verify output:

```json
{
  "trace": "TestSonic3kAiz1TraceReplay",
  "game": "s3k",
  "zone": "aiz",
  "status": "advanced",
  "genuine": true,
  "committed": true,
  "commit": "abcdef1",
  "regressionsIntroduced": [],
  "afterFrame": 13000,
  "frontierLogUpdated": true,
  "notes": "verification summary"
}
```

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
  "regressionQueue": [
    {
      "causedBy": "TestSonic3kAiz1TraceReplay",
      "commit": "abcdef1",
      "regression": "TestSonic3kHcz1TraceReplay: green -> frame 100/x_pos"
    }
  ],
  "results": []
}
```

Also summarize human-readable:

- which traces greened,
- which traces advanced,
- which commits landed,
- which worktrees remain for review,
- any introduced regressions that need follow-up,
- any blocked traces and why.
