# Briefing a trace round

Distilled from ~25 consecutive S3K/S2 trace rounds on 2026-08-14/15, in which **the
orchestrator's candidate causes were wrong eight rounds running** and every round that ignored
them and instrumented the decision site was right. This is not a confession; it is a measured
result about what makes these rounds succeed. Brief this way.

Companion to [trace-replay-bug-fixing](../../.agents/skills/trace-replay-bug-fixing) — that
skill is the procedure, this is how to hand the work over.

## The one rule that matters most

**Supply the measured symptom. Do not supply your hypothesis.**

If you must mention a candidate cause, label it explicitly as a candidate and tell the round to
rule it out rather than confirm it. Better: tell the round to derive its own list from the ROM.

Rounds that were handed hypotheses tested the hypotheses. Rounds that were handed symptoms
found the causes. Every time.

Worked examples from one session:

| briefed as | actually was |
|---|---|
| "plane-drop landing, 5px low" | the AIZ1 intro **spin dash** — the +5 is the release's `addq.w #5,y_pos` |
| "half-pixel loss with identical `x`" | an artefact of **field sort order**; six fields diverged together |
| "engine still moving where the ROM stopped" | the engine stopped correctly and **resumed too early** |
| "the arena's level load is missing" | the load ran correctly two frames before the failure |
| "eight completions the engine never submitted" | submitted, with a **different sha256** — different art, not absent art |
| "`TOTAL_ZONE_COUNT` is the off-by-one" | that constant has **no reader anywhere** in `src/` |

Four candidate causes offered on the camera round: none was involved.

## Second rule: the first-reported field is alphabetical, not causal

**Tell every round to dump every field that differs on the first failing frame before forming a
hypothesis.** In one session this misled four times, and twice the decisive clue was a column
nobody was looking at:

- `rings` expected 3, actual 53 → the engine had *gained* 50 rings → named the exact ROM branch
  in one step, where the reported `player_animation_id` never would have.
- `x_sub` sorting ahead of `x` produced an entire "half-pixel rounding" theory for what was a
  slope-table hand-off.

## Third rule: name the fitted-constant trap explicitly, in the brief

When a delta is small and constant, the fix that closes it is visible from the outset and is
almost always a rule-3 violation. Name the specific number and forbid it:

> *"Do not add `0x8000`, or half a pixel, anywhere, because the delta equals it. The fix must be
> 'the engine now performs (or omits) the ROM operation at this cited line', never 'the engine
> adds the observed difference'."*

This works. Rounds declined `+6` on a camera, `0x8000` on a sub-pixel, a zeroed speed, and a
one-frame scroll count — each time explaining *why* the ROM produces its value instead.

**The trap wears disguises.** It is not always a physics constant:

- a **zone count** picked to stop an `IndexOutOfBounds` — plumbing is where rule 3 gets waived
  by accident;
- a **frame count** ("scroll for exactly one frame") with no ported ROM owner for the stop;
- an **engine stamp** asserting work it did not do, to match a recorder.

## The discriminator for "my fix reds a green class"

This comes up constantly and the two cases look identical in a test report. They are not.

> **Red with an unlocated owner: hold. Red with a fully-traced propagation chain from a cited
> fix: merge and document.**

Both arose in one session and the rule decides both correctly:

- **Hold.** Removing a camera clamp produced 12 newly-red classes and zero greens. A mechanism
  had been taken out and *nobody could say what performed its job* — the red was a symptom of an
  incomplete model. (It later turned out the removal was simply wrong: a generic setup routine
  reinstated the value ten lines downstream.)
- **Merge.** A ROM-cited badnik fix produced two errors 27,600 frames later, and every link was
  traced and cited: spawn-gate fix → one-pixel position → slot-occupancy permutation → a ROM gate
  keyed on the slot index → a ring collected four frames early. The fixture's own aux confirmed
  the *fixed* side matched the ROM at the divergence point.

The trap to name explicitly: **"the green was thin" is not the argument** — it's what people say
right before landing something wrong. The argument is that the green was **accidental**, and you
have to show that. In the merge case, occupancy already diverged from ROM on 2387 of 2387 sampled
frames *on both trees*, and the class compared no object identity at all: the green was a
slot-phase lottery, not a baseline the fix broke.

**When you do land a knowingly-red class**, use the deliberate-red convention rather than an
inverted assertion pinning the expected failure. A pinned failure fails loudly on *any* change —
including improvements — and teaches the suite that the red is a contract rather than a frontier.
Put the marker in the **class's own javadoc**, so the explanation is one hop from the failure;
the frontier log carries the measured first error and count (its job, updated as things move), and
`known-discrepancies` carries the mechanism and the removal condition.

## A collision to avoid: "don't undo landed fixes" vs "don't fit constants"

Standing briefs accumulate a *do not undo* list — the fixes already landed this session. They
also forbid fitted constants. **Those two collide when a landed "fix" is itself a fitted
constant**, and a round that obeys both literally is stuck.

This happened: a per-slot table of 13 hardcoded byte angles, added by two earlier commits, turned
out to be the proximate cause of the frontier under investigation. The round did the right thing —
it did not undo the fix, and it **flagged the conflict explicitly** — but the brief should not
have put it in that position.

**Write the exception in:** *do not undo a landed fix, **unless it is itself a fitted constant or
compensation**, in which case say so prominently and treat removing it as the finding rather than
the fix.* And when a round reports that shape, the right response is usually to document the
mechanism and a removal condition rather than to delete the table — because deleting it may red a
class where the fitted value happens to be right.

## Fourth rule: make "found-not-fixed" a full success, in writing

State it. Rounds will otherwise optimise for a landed diff.

- A round that measured a change as **12 newly red / 0 green** and discarded it produced more
  value than a landed fix would have.
- A round that scoped a cross-cutting change as `larger-than-one-round`, with a four-stage
  decomposition and the note that stage C is not landable without stage A, saved a half-landed
  migration.
- A round that found a mechanism but declined to model it — because doing so required a fitted
  frame count — left the next implementer a clean target instead of a wrong fix.

For changes to **shared machinery**, say outright that a well-scoped `found-not-fixed` beats a
half-landed change, and require the blast radius to be measured before anything is written.

## Fifth rule: verification instructions that have actually caught things

- **Diff the red-class set by name, both directions.** A falling failure count with classes
  vanishing is *comparator starvation*, not progress. This session produced it twice.
  `comm` is **unsafe** here — these class names are not in C collation order and it silently
  mis-reports; use an explicit set difference.
- **Report `framesCompared` for anything that greens.** A green at fewer rows is starvation.
- **Clear `target/surefire-reports` before every run.** A stale XML counts as a pass; a
  truncated run reports *fewer* red. Give the expected total and say that below it is truncated
  and above it is stale.
- **Wait on a terminal marker the run emits, never on the absence of a process.** Polling for
  "no surefire process running" fires within seconds, because Maven spends the first minutes in
  `testCompile` before any surefire process exists — so "not started yet" is indistinguishable
  from "finished". A round that trusted it would have set-diffed a directory of stale XML from
  the previous run and reported it as a clean result. Poll the build log for
  `BUILD SUCCESS|BUILD FAILURE`, which cannot appear before the run starts. This is the
  stale-XML trap above arriving from a different direction, and clearing
  `target/surefire-reports` does not protect against it — an early-firing wait reads the
  *current* run's partial output as final.
- **For the run chains, a control in a *different worktree* is not sufficient — use the same
  worktree.** `TestS3kSonicTailsCompleteEmeraldRunChain` has been observed failing with **two
  different exceptions in two worktrees at the same commit with no local changes** (an
  `awaitBoundary` step-cap walk-failure in one, `IllegalStateException: non-exportable pending
  hardware submission` in the other), and it survives `mvn clean`. A round spent a bisect cycle
  attributing that to its own change before an empty-diff control refuted it. The known mechanism
  is Surefire's filesystem `runOrder` differing per worktree, which changes which order-dependent
  class runs first; `-Dsurefire.runOrder=alphabetical` reduces but has not eliminated it. When a
  chain class changes its *failure mode* rather than its pass/fail, verify by stashing nothing and
  re-running the **same** worktree with the change reverted, before attributing anything.
- **"Gate-clean" is not evidence when nothing observes the changed behaviour.** Before treating an
  empty both-way set-diff as approval to land, ask which committed trace actually compares the
  field the change moves. A round found that routing S1's results mode through the hardware-timed
  scan was gate-clean at 790/4 — and withheld it, because **no committed trace compares S1/S2
  results-screen queue state** except two chains that are already red, so the suite could not have
  seen the one-row preparation shift the change introduces. Where a change alters shared behaviour
  in a span no fixture covers, say so and treat the clean gate as *absence of evidence*, not
  evidence of absence. The honest options are to extend coverage first, or to land it explicitly
  flagged as unobserved — not to quote the green.
- **The comparator's `cascading` flag suppresses cluster ONSETS, not just repeats.** A 2422-error
  `dynamic_art` cluster sat in the first report a round read this session and was passed over
  because it was almost entirely `cascading: true` — only three rows were non-cascading. Scanning
  for non-cascading rows is the right first filter and it will hide any defect whose own onset the
  flag classifies as cascading. Before concluding a report holds nothing, check the cascading rows'
  *distribution* as well as their count.
- **For `dynamic_art`, count drift, not errors.** One wrong edge shifts every later `edge_ordinal`
  and `transfer_id`, so the error count is a multiplier on a small number of real events — 2422
  errors resolved to a handful of discrete timing events plus two bursts. Measure cumulative engine
  edges minus ROM edges and read the *regimes*: a run-ahead followed by a catch-up is a service-rate
  signature, not spurious generation. And do not assume a steady reference rate; the ROM's own edge
  rate across one window ran from 32 to 363 per hundred frames, and the divergence ramp began
  exactly at the burst.
- **An empty probe output proves nothing until the build is confirmed.** Two rounds produced no
  probe output and nearly read it as "this code path never runs"; both were compilation failures.
  Before drawing any conclusion from absence, show the probe compiled and ran — for Lua, `luac -p`
  plus a hit on a hook you know fires; for engine instrumentation, a line that prints
  unconditionally. This is the same shape as the recorded-stream trap: **absence is only evidence
  once you have shown the thing that would have reported presence was working.**
- **A tidy number is not evidence for the model that produced it.** A round measured an object
  pass ending 21 slots early, observed that `21 x $40 = $540` exactly, and concluded the loop
  counter had been "consumed a whole number of extra times" rather than overwritten. The arithmetic
  was true and the mechanism was wrong: the counter is written absolutely with the constant `$5D`,
  which merely happens to look like a plausible count. The clean factorisation made the wrong model
  feel confirmed, and it was adopted into the record and into the reviewer's vocabulary before the
  disassembly was read. When a number factors neatly, check whether a different mechanism produces
  the same number before treating the factorisation as support.
- **An exception thrown while handling a failure replaces the failure.** A round's real diagnostic
  -- a coordinator invariant violation -- never reached surefire, because `failRun`'s own cleanup
  threw `PendingRecordedSubmissionsException` over one leftover submission and that was reported
  instead. The round spent time on the wrong blocker. Anything routing through a failure handler
  must log its diagnostic **before** cleanup runs, and when a reported error looks like it belongs
  to teardown rather than to the assertion, suspect that it does. Same family as the empty-probe and
  stale-stream traps above: **the reporting channel misleading you about the thing being reported.**
- **When a metric is cumulative, its inflections are not events.** A brief asked what happens at the
  frame where an edge-drift curve resets, on the reasoning that a catch-up is the most informative
  thing in the window. Nothing happens there. Drift is the integral of a per-frame difference, and a
  *constant* engine error against a ROM rate swinging 13 to 105 submissions per fifty rows produces
  ramps where the rate is high and reversals where it is low. One persistent error explained a ramp,
  a reset and a second ramp, none of which is a happening. Before hunting a cause at an inflection,
  check whether the reference rate alone produces it — and prefer the rate profile, which is
  background, over the inflection, which is an artefact.
- **A partial run is not a result, however suggestive.** If a gate is killed or interrupted,
  say so and quote no numbers from it. "465 of 790 with 4 red, consistent with the two known
  regressions" is a reasonable thing to *notice* and an unreasonable thing to *report*.
- **Trace-report filenames collide** between standalone and per-segment classes, so a per-class
  `errorCount` read from a full sweep is not attributable. A class's own surefire assertion
  message is immune — prefer it.
- **Re-run any newly-red class in isolation on *both* trees before attributing it.** Reused-fork
  ambient flakes move counts run to run.
- **Tell the round to establish its own control** rather than diffing against numbers in the
  brief.
- Name a **cross-check**: a class covering the same content by a different route. *"If your fix
  greens the segment and reds the complete-run, the fix is wrong"* is the any-BK2 bar with a
  concrete witness attached.

## Sixth rule: stamp every number with the commit it was measured at

Measured facts go stale at the next landing — that is what a fix is *for*. Two rounds in one
session were briefed against a frontier that had been fixed commits earlier, because the
measurement was carried forward without re-checking. Re-measure the frontier as the **first act**
of briefing, not as something the round confirms in passing.

## Seventh rule: tell the round you are probably wrong

Every brief in the productive stretch ended with a count of the orchestrator's refuted claims and
an instruction to treat the framing as unverified. The rounds took it literally, and it is why
the errors were caught:

> *"Twenty-one of my claims have been factually wrong this session — including, twice, in this
> exact area. Assume the structure above is wrong somewhere and check it. If it does not
> reproduce, that IS the finding and I want it reported, not worked around."*

Rounds then reported corrections as findings rather than working around them silently — including
correcting *each other*, and correcting a previous round's conclusion that had itself been
correct in its measurements and wrong in its scope.

## What to keep out of the brief

- Anything that lets the round satisfy a comparison without modelling the ROM. Say plainly which
  mechanisms are forbidden (trace hydration, the timing port creating work, tolerance changes)
  and *why*, not just that they are.
- Zone/act/route/frame predicates. Say "if zone `$17` needs different treatment it must fall out
  of ROM-derived data or an existing provider" — and point at a precedent. The best fix of one
  session ported the ROM's own literal comparison into a per-game hook that **already carried
  another arm of the same ROM branch**.
- Instructions to make a specific test pass. Name the frontier and the acceptance shape instead;
  several of the most valuable rounds ended with the target class unchanged.
