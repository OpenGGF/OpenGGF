# Runbook — Multi-Agent Trace Orchestration Loop (lead)

This runbook documents the **lead/orchestrator** loop for running a fleet of
trace-replay bug-fixing agents continuously. It sits one layer above
[`runbook-trace-divergence.md`](runbook-trace-divergence.md) (which is "how one
agent fixes one trace") and above the `trace-replay-bug-fixing` skill. For the
fleet-scale decision history see
[`../trace-green-fleet-decisions.md`](../trace-green-fleet-decisions.md).

## Purpose

Advance the `*TraceReplay` frontiers (engine vs recorded BizHawk ROM traces)
across all three games as fast as the fixes can be verified, **banking every
net-positive win** and never stalling on a single hard root. The standing
operating mode is *continuous*: there is a deep backlog and the point is to keep
grinding it, not to reach a fixed stopping point.

## The loop

```
        ┌────────────────────────────────────────────────────────────┐
        │ 1. SURVEY    fleet/agent triages frontiers (TraceTriageTool, │
        │              isolated first-error frames). Classify each     │
        │              first divergence (object-local / sidekick-1px / │
        │              camera / phase-timing / boss / RNG).            │
        │ 2. ASSIGN    give each agent a tractable target (see         │
        │              "Targeting"). One owner per cluster; no two     │
        │              agents on the same shared file.                 │
        │ 3. FIX       agent runs the per-trace loop (diagnose → ROM-  │
        │              cite → narrowest-scope fix → broad sweep).      │
        │ 4. GATE      agent self-checks NET-POSITIVE before claiming. │
        │ 5. VERIFY    lead independently checks the branch: commit    │
        │              content (`git show --stat`), merge-base, AND    │
        │              the UNIT tests for any shared-code change.      │
        │ 6. MERGE     net-positive → merge to develop + README        │
        │              release-log + push. Else → bounce back/revert.  │
        │ 7. REASSIGN  immediately feed the agent a fresh target.      │
        └───────────────────────────────┬────────────────────────────┘
                                         └── repeat, no "should we wrap?" ──┐
                                                                            └►
```

There is **no consolidation/wrap step**. After a merge, reassign immediately.

## Targeting (what to feed agents)

Prefer the *shallowest* frontier whose first divergence is **object-local** — the
object's position matches ROM but the contact/seat/landing/state/respawn outcome
differs. This class is tractable, low-blast-radius, and ROM-citable (the recurring
wins: inclusive-right-edge `bhi`, top-landing band, spawn-frame skip, frame-counter
source, platform exit re-seat).

**Defer (document, don't grind)** the deep classes:
- **phase-timing** — object phase not ROM-aligned (oscillation-counter seed/gate,
  placement/init-frame materialization). High-leverage but shared/broad.
- **CPU-Tails-follow 1px** — sidekick AI sub-pixel; trace-only suite, easy to regress.
- **camera vsettle** — boundary-accel transition; frequently non-greenable.
- **RNG / boss-AI sub-frame** — needs BizHawk register traces, deep.

When the shallow object-local backlog is **exhausted** (all three games surveyed,
every frontier deep), the heuristic flips: stop skimming and put each agent on the
**highest-leverage deep CLUSTER** (one owner each, independent code). Cracking one
cluster unblocks a whole family of frontiers — that is still "bank solid wins,"
just bigger and multi-turn. Require a **plan-first** report for any shared change
with real regression surface before the build starts.

## The net-positive gate (non-negotiable)

A "win" is **only** a win if ALL hold:
- the target frontier's first-error frame advances (or the trace greens);
- the must-keep-green set stays green (GHZ2, SYZ2, and the must-keep-green S3K:
  AizSkipHeadless, LevelLoading, Bootstrap, DecodingUtils);
- a **broad cross-game sweep** shows zero regressions; AND
- **the UNIT tests for any shared/physics/contact/lifecycle code you touched pass.**
  The trace sweep alone misses unit regressions — this has bitten us (a ceiling fix
  greened traces but broke `testCalcRoomOverHead…`). Validate units independently of
  the agent's report.

Any regression → **revert**. No zone/route/frame carve-outs, never `if gameId==`,
never hydrate engine state from the trace per-frame (frame-0 bootstrap only).

## Agent lifecycle

- **Spawn** fresh agents with a self-contained prompt (mission rules, the gate, the
  tooling traps below, hygiene, and the focus region). See
  [`../delegation-prompt-templates.md`](../delegation-prompt-templates.md).
- **Respawn** an agent that is killed (rate limits) or stuck in a stale
  cross-session task-queue loop — terminate (`shutdown_request`) and start fresh
  from its memory notes. Don't try to nurse a wedged agent.
- **One owner per shared file.** Two agents editing the same shared resolver/bootstrap
  will collide. Split by game/region/cluster.
- `isolation:"worktree"` is **unreliable** here — agents land in sibling/shared
  worktrees and branch off each other. ALWAYS verify each incoming branch's actual
  commit content + merge-base before merging; never trust the isolation flag.

## Model routing and escalation

The conductor chooses every child route before launch; an active worker cannot
reroute itself. Terra is the default for mechanical and object-local work, while
Sol owns shared/deep work and defined escalation. Record requested and (when
available) actual routes, reasons, cumulative attempts, and nullable telemetry;
never estimate unavailable token or duration values.

| Stage or classification | Exact route |
| --- | --- |
| Discovery | `gpt-5.6-terra/low` |
| Triage | `gpt-5.6-terra/medium` |
| Narrow object-local fix | `gpt-5.6-terra/medium` |
| Shared/deep fix | `gpt-5.6-sol/high` |
| Ordinary verify | `gpt-5.6-terra/medium` |
| Verify after Sol, a shared edit, disputed ROM evidence, or escalation | `gpt-5.6-sol/high` |

Classify shared runtime physics, collision, sidekick, camera, oscillation,
bootstrap, object lifecycle, recorder/publication work, and cross-game semantics
directly to Sol. Terra Triage escalates once to Sol for multiple owners, low
confidence, unresolved ownership, or missing ROM basis. Sol Triage without ROM
basis blocks Fix. Terra narrow Fix stops after two unsuccessful attempts and
hands off once to Sol for one final attempt when there is no frontier advance,
context contradiction, a newly discovered shared/cross-game surface, recorder
evidence need, causal-thread loss, or insufficient reasoning. Never change
effort in place or escalate a stage twice.

Route Verify directly to Sol after Sol, shared, disputed, or escalated work. A
Terra-detected regression is a Sol handoff for independent repeat verification.
An unavailable required Sol route or a failed Sol worker blocks the stage; never
silently fall back. The lead retains sole worktree ownership between sequential
workers: end Terra ownership before launching Sol, retain only ROM-backed edits
listed in `filesTouched`, and let Sol review that retained diff before its one
attempt. Workers never own one worktree concurrently.

### Reproducible routing benchmark

Use the frozen protocol in
[`trace-model-routing-benchmark.json`](../../architecture/validation/trace/trace-model-routing-benchmark.json).
Run every case/policy in a newly-created clean worktree; verify the ROM SHA-1
and fixture SHA-256 values first. Store the result at
`target/trace-model-routing/<policy>/<case>.json` and its patch at the same path
with `.patch`. The parent commit pins the recorded first error as benchmark input.

Retain results and patches by default. Before normal `git worktree remove`,
restore only benchmark-owned enumerated files and confirm `git status --short`
is empty. Never reset, `git clean`, or force-remove a dirty benchmark worktree:
it is evidence to retain and diagnose, not permission to discard work.

## Shared-worktree hygiene (critical — many concurrent sessions)

- **Stage ONLY your own authored files.** NEVER `git add -A`. There is frequently
  foreign WIP in the tree (e.g. a bulk skill-regen touching every `SKILL.md`, or a
  rewind-codec session's staged files in the shared index); never stage or "fix up"
  files you didn't author. Run git from your assigned worktree, not the shared repo
  root — the shared index will surface other sessions' staged work in your
  `git diff --cached`.
- **NEVER `git stash`** for A/B baselining — stash push/pop has eaten changes and
  injected foreign files. Use a separate checkout or `git show HEAD:<file>` diffs.
- To land a change without entangling foreign uncommitted WIP on the same files,
  cherry-pick your isolated commit into a **fresh `git worktree add --detach` off
  `origin/develop`**, verify, then push — the clean tree has none of the WIP.
- Commit trailers: src/main feat/fix needs `Changelog: updated` **and** a CHANGELOG.md
  edit in the **same** commit. No `--no-verify`. Merges into develop need a README
  release-log entry. Update `docs/status/trace-frontier-log.md` whenever a frontier moves.
- A correct, regression-free, ROM-faithful change that does **not** advance a frontier
  yet (e.g. a foundational bootstrap seed gated by a separate root) is committed to
  its **branch** but **not merged to develop** until a frontier actually moves — so
  develop only ever gains advances, and the work is still safe from worktree churn.

## BizHawk tooling traps (hand these to every agent)

- **ROM name:** use `s1.gen` / `s2.gen` / `s3k.gen` (repo root) for the EmuHawk
  `--movie ... <rom>` arg. Paths with spaces/parens/`[!]` have caused EmuHawk to
  load no ROM and hang (~316MB, frames never advance). This was the single biggest
  time-sink; mandate the simple names. (mvn trace tests are unaffected - they use
  `-D<game>.rom.path=...`.)
- **Fast headless trio** (≈100x faster, ~475MB vs ~3.4GB): at the top of the lua,
  `emu.limitframerate(false)` + `client.speedmode(6400)` + `client.invisibleemulation(true)`.
  `--chromeless` alone does NOT do this.
- **Self-exit:** the lua MUST `client.exit()` when done. A bare
  `while true do emu.frameadvance() end` (no exit) or a `client.pause()` tail LEAKS
  EmuHawk at multiple GB. Use `tools/bizhawk/diag_template_fast.lua` (fill only its
  two marked sections).
- **Read-count crash:** >~12–16 `mainmemory.read_*` per frame at speedmode 6400 makes
  EmuHawk silently exit. Drop to `speedmode(100)` in the capture window, or buffer/
  split the scans.
- **Long seek:** seeking to a high BizHawk frame (~190000) takes minutes even at
  6400% — use a 600s+ timeout, and never wrap the EmuHawk launch in bash `timeout`
  that fires mid-seek.
- **System.err is swallowed** by the surefire/MSE harness — for engine-side debug,
  `Files.writeString` to a Windows/relative path (NOT `/tmp`, which throws on the
  Windows JVM and the catch hides it).

## Why this loop

The engine's correctness claim is "play back any BK2 movie pixel-for-pixel from
controller input alone." Each banked frontier advance is one more verified slice of
that claim. The loop optimizes for *continuous verified progress*: small ROM-cited
wins merged immediately, deep roots documented and owned rather than grinded blindly,
and develop kept always-green so every agent starts from a trustworthy base.
