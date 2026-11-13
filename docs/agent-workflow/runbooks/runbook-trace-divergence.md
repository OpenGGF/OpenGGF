# Runbook: Fix a Trace Replay Divergence

Start with [diagnose one trace replay](../../guide/contributing/testing.md#diagnose-one-trace-replay)
for the concrete class, fixture/movie, profile and printed diagnostic route.
Use the [trace-replay-bug-fixing skill](../../../.agents/skills/trace-replay-bug-fixing/SKILL.md)
for the ROM-backed investigation and repair loop. This runbook routes those
references; it does not maintain another command or report-naming specification.

## 0. Non-negotiables for trace work

Trace physics and auxiliary data are comparison evidence, not authority to write
live gameplay state. Follow root `AGENTS.md` and the skill's bounded timing and
segment-entry contracts. Do not fit tolerances, alter input or add route/frame
exceptions to make a fixture pass. Use the smallest semantic owner described in
[per-game rule placement](../../architecture/per-game-rule-placement.md).

## 1. Discovery: read the trace artifacts

Choose the concrete class's declared lane, including `trace-segments` for
standalone run leaves. Read its metadata and resolve the referenced movie as
specified in the [diagnosis guide](../../guide/contributing/testing.md#diagnose-one-trace-replay).
Run through `maven_queue.py` with Java 25 and check verified ROM discovery.

Use the exact owner-keyed report and `triage:` command printed by the standalone
replay driver; `-Dtrace.print.summary=true` also prints its route for a successful
comparison. Other producers have separate publication paths; see the diagnosis
guide.
Avoid searching for an old flat `<game>_<zone>_report.json` filename. Fresh
Surefire and trace-output directories separate attempts; the owner sidecar
identifies an allocation but does not establish invocation freshness.

## 2. Triage: identify the FIRST divergence before changing code

Check this invocation's process exit and concrete Surefire result before trusting
saved evidence. A missing prerequisite, skip or early setup failure may publish
nothing and leave an older report. Distinguish COMPLETE, INCOMPLETE and no
published gate (UNKNOWN); a zero error count is not a completion witness.

Use the brief's first error and neighboring context/aux to locate the owning
engine and ROM routine. Subsystem and symbol suggestions are search hints, not
proof of a game-specific branch. Start at the
[object ownership map](../../guide/contributing/architecture.md#finding-an-object-lifecycle-owner)
for solid/object lifetime issues, or the [engine map](../../architecture/engine-map.md)
for other owners. Do not assume a segment directory number identifies its act.
Name the CSV frame key, replay row ordinal and native counters separately.

## 3. Fix loop (diagnose → fix → regenerate → verify)

Follow the skill's [diagnose and implement](../../../.agents/skills/trace-replay-bug-fixing/SKILL.md#diagnose-and-implement)
sequence: prove the owning ROM branch, repair its engine model, compare the
focused and affected sibling runs with a matched baseline, and retain exact
failures/skips. Trace regeneration is separate: use
[bizhawk-headless-trace](../../../.agents/skills/bizhawk-headless-trace/SKILL.md)
and its producer guidance. Do not hand-edit recorded observations or advertise
uncaptured auxiliary capabilities.

## 4. Required guard tests + focused regression tests

Keep `TestTraceReplayInvariantGuard`, `TestTraceHydrateSwitchDefault` and
`TestHardwareTimingAuthorityGuard` green. Select affected fixtures and lanes using
the diagnosis guide and root verification policy. A complete trace sweep is a
separate explicit task, not the routine regression command.

## 5. Common failure signatures → fix

- Parser/data code importing live gameplay services, or observed-state setters in
  the harness: restore the comparison-only boundary.
- A matching prefix called a pass: check gate, skips, process exit and freshness.
- An ambiguous positional report lookup: use the printed explicit `--report`.
- A game/route/frame-specific repair: locate the ROM state/rule that owns it.

## 6. ROM / disassembly citation expectations

Cite the owning routine beside changed behavior and explain the shipped
`FixBugs=0` branch. Report the observed first-error field and frame key without
substituting ROM interrupt or level-frame counters.

## 7. Documentation & commit-trailer obligations

Use the [documentation obligation checklist](../documentation-obligation-checklist.md)
for frontier-log changes, reusable pitfalls, skill mirrors and the branch's
versioned changelog. Root `AGENTS.md` owns trailer requirements. This runbook
adds no alternate release, validation or recording policy.
