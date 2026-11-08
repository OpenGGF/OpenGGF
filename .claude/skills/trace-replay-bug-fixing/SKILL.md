---
name: trace-replay-bug-fixing
description: Diagnose or fix OpenGGF trace replay divergences, bootstrap mismatches, and run-boundary failures using ROM evidence.
---

# Trace replay bug fixing

For ROM image comparisons, enable `DISPLAY_SPRITE_SCANLINE_LIMIT`
(`display.spriteScanlineLimit: true`): normal play defaults to false.
`TraceCaptureTool` enables it for its capture scope and restores the caller's
preference on completion or failure. For other engine screenshot/probe paths,
set it explicitly in the isolated capture configuration and restore it afterward.
Physics/aux comparisons do not require this presentation setting.

Find the earliest causal disagreement between the engine, recorder, and shipped
ROM. A passing fixture proves only the fields and rows it actually compares.

## Establish a trustworthy measurement

Use JDK 25 and absolute ROM paths with the properties in root `AGENTS.md`.
Before reporting suite numbers, read the measurement-hazard table in
[briefing-trace-rounds.md](../../../docs/agent-workflow/briefing-trace-rounds.md).
Confirm the requested concrete test exists and which fixture/segments it drives.

Choose the concrete invocation through
[diagnose one trace replay](../../../docs/guide/contributing/testing.md#diagnose-one-trace-replay),
which gives the HCZ2 segment command, fixture/movie lookup and printed triage route.

The ordinary suite excludes replay tests. `trace-replay` covers the release-6
gate, `trace-replay-r7` the deferred gate, and `trace-segments` standalone run
segments such as the Sonic/Tails HCZ leaves. Check
`pom.xml` for current profile membership; the
[scope record](../../../docs/status/trace-scope-release-6.md) retains release policy
and historical coverage. Run affected lanes separately when shared behavior
reaches several. Do not rely on an exact selector overriding another lane's
filters, or hide an empty selection with `failIfNoTests=false`.
Use fresh reports below this worktree's `target/`; save prior evidence before
clearing generated reports. The pom binds fork count to `surefire.forkCount`
and reports to `openggf.surefire.reports`, not the similarly named bare flags.

The standalone `AbstractTraceReplayTest` driver prints the concrete class,
fixture and exact owner-keyed report path plus a copyable `triage:` command for
`TraceTriageTool` after a failed comparison. `-Dtrace.print.summary=true` also
prints that route on a successful standalone replay.
The tool reads saved evidence, not the live invocation: check the class's fresh
Surefire result and process completion alongside its JSON/context. Failures or
skips before report setup may leave an older report untouched. Prefer fresh
per-invocation report directories when retaining evidence.
Check errors, skips, compared rows, and the prescribed completion gate;
stale XML, a skipped ROM test, or a crashed fork is not a pass. Reproduce suspected
order/native/config contamination in isolation and report it until resolved.

## Diagnose and implement

1. Parse error spans and cascades, including predecessor segment reports. An early
   self-healing blip may be separate from the later cascade. Record both.
2. Inspect existing physics and aux evidence before adding probes. Comparison
   spans use `TraceFrame.frame()`, parsed from the CSV `frame` column; replay
   loops also have a separate row ordinal. Check the binder and each aux lookup
   before assuming those keys coincide. Neither is automatically a ROM clock.
   Numeric physics columns are generally hexadecimal; boolean/enum columns are
   exceptions. Use `TraceFrame`'s parser as the format authority.
3. Align sampling boundaries and name each clock: trace row, manager execution
   count, ROM `V_int_run_count`, and `Level_frame_counter` can differ. Compare a
   shared event/value before calling anything “N frames late.”
4. Locate the owning ROM routine using the relevant disassembly skill. Search
   the engine for that symbol too: the behavior may already exist. Prove the
   suspected branch executes, then compare ordering, widths, state visibility,
   and the shipped `FixBugs=0` path.
5. Change the smallest accurate owner. Cite the ROM routine for each behavioral
   constant or predicate. Shared changes require checking affected games and
   sibling implementations of the same contract.
6. Re-run the target and affected regression tests against a matched baseline.
   Follow root integration/test policy. Append command, commit/worktree context,
   error count, and before/after frame/field to
   [trace-frontier-log.md](../../../docs/status/trace-frontier-log.md) when required.
   Preserve its historic prefix and use repository-relative paths in added entries.

A fix must hold for another BK2: no fixture-fitted constants, tolerances,
zone/route/frame exceptions, or trace-to-engine state hydration. Bootstrap
snapshots are comparison data too. A local reseeding probe must be removed before
committing. Do not force a green result when the causal evidence is incomplete.
The one sanctioned entry import is a run segment's carried run state (rings,
emerald progress, used giant rings, shield, path bits) through
`TraceSegmentCarriedStateImport`. It runs once at segment entry, from recorder
observations. Extend that mechanism with a recorder-observed source; never add a
second import or write anything after entry. See the
[carried-state design](../../../docs/architecture/designs/2026-11-03-segment-carried-run-state.md).

## Hardware timing boundary

`trace_schema: 5` is the sole live contract; recorder names/versions are opaque
provenance and never behavior selectors. Read the
[hardware-timing contract](../../../docs/architecture/designs/2026-07-27-cross-game-hardware-timing-trace-contract.md)
when touching timing authority. It permits bounded scheduling inputs:

- Readiness release delays matching prepared, production-submitted ROM work;
  kind, ordinal, fingerprint, and service boundary must match.
- Per-row lag admission chooses an already-implemented ROM loop, including
  `VBlank_Lag`; it supplies no gameplay values and creates no work.
- Reviewed initialization input may hold/resume a matching, already-prepared
  production load phase and select its observed zero-routine interrupt service.
  Queue readiness outside structural segments uses a separate dedicated
  initialization companion and the existing job identity/boundary checks.
  Interstitial `bk2_frame` stays provenance, never readiness input.

None may use physics/aux comparison values, key on a route/game/frame,
or decide what work exists. S1 PLC, S2 DPLC, and S3K Kosinski timing are in contract
scope; implementation and fixture coverage differ. Consult the contract's
coverage status rather than inferring support. Keep `TestHardwareTimingAuthorityGuard`
green. Derive frame-counted service from the ROM before introducing timing input.

## Read only for the matching failure

- Clocks, slot occupancy, queues, recorder defects, or visual run boundaries:
  [diagnostic reference](references/diagnostics.md).
- Missing recorder evidence or fixture regeneration: use `bizhawk-headless-trace`
  and TraceChaser's capture/behavior docs. Capabilities must describe real captured
  evidence; adding metadata flags cannot create missing observations.
- Video reproduction: use `trace-capture`; a completed video continues through
  desyncs and is not parity evidence.
- Multiple independent trace frontiers: use `trace-green-fleet`.

For shared collision outputs and render sampling, read
[the focused reference](references/shared-state-sampling.md).
