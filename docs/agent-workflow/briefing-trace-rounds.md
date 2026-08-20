# Briefing a trace round

Distilled from ~25 consecutive S3K/S2 trace rounds on 2026-08-14/15, in which **the
orchestrator's candidate causes were wrong eight rounds running** and every round that ignored
them and instrumented the decision site was right. This is not a confession; it is a measured
result about what makes these rounds succeed. Brief this way.

Companion to [trace-replay-bug-fixing](../../.agents/skills/trace-replay-bug-fixing) — that
skill is the procedure, this is how to hand the work over.

## Index

Forty-three rules and several worked sections, accumulated across many rounds. The narrative
below is the argument for each; this table is for finding one mid-round. **The measurement
hazards are the ones to re-read before reporting a number** — every single one produces output
that looks like a real result.

### Briefing and framing

| Rule | In one line |
|---|---|
| The one that matters most | Supply the measured symptom, never your hypothesis |
| 2 | The first-reported field is alphabetical, not causal |
| 3 | Name the fitted-constant trap explicitly, in the brief |
| 4 | Make "found-not-fixed" a full success, in writing |
| 6 | Stamp every number with the commit it was measured at |
| 7 | Tell the round you are probably wrong |
| 31 | "No field reported" states which check fired, not what is wrong |

### Evidence and inference

| Rule | In one line |
|---|---|
| 5 | Verification instructions that have actually caught things |
| 24 | Diff failure *messages*, never class names |
| 28 | Check whether an absence is in the data or only in your view |
| 29 | Check a suspicious delta against both endpoints |
| 33 | BizHawk reports the PC *after* the storing instruction |
| 32 | A row's emulator frame is `bk2_frame_offset + row + 1` |
| 40 | A comment reasoning about which engine hook runs first — in either direction — is the tell for a fitted constant; they come in families |

### Measurement hazards — all produce plausible output

| Rule | Signature | What it looks like |
|---|---|---|
| 25 | `-Dmse=off` missing | CLI `-D` properties silently never reach the fork |
| 26 | Self-report about process state | A suite *is* running when you were told none was |
| 27 | `git checkout -- <path>` | Restores from the **index**, not HEAD |
| 30 | Mass errors | An environment artefact until proven otherwise |
| 34 | Two Maven runs in one worktree | `target/test-classes` clobbered; "No tests matching pattern" reads as a bad filter |
| 35 | Failed compile | A small log that reads as a short test run; also `-Ptrace-replay` silently ignores a CLI `-Dsurefire.argLine=` |
| 37 | Run-order swap | Equal totals, different members, all passing alone |
| 38 | Backgrounded `mvn` dies with its shell | The wrapper's exit 0 with a log that just stops |
| 41 | Truncated arm | Prove completeness by class-name set and `Tests run: 0,` lines, not by totals |
| 42 | Truncated message diff | Two different failures compare equal on a shared prefix |
| 43 | Too many concurrent rounds | OOMs, GLFW init failures and contended arms that report *fewer* red |

### Operational

| Rule | In one line |
|---|---|
| 36 | The branch is the artifact, the worktree is scratch — disposal is the lead's job, persistence is the round's |
| 39 | Create round worktrees copy-on-write (`cow-git-worktree` skill) |

Two attribution hazards that are not rules but bite the same way: `target/trace-reports/<runId>_seg<N>_report.json`
is keyed on run id and re-based segment index only, so under `forkCount=4` two classes replaying
one run overwrite each other's reports — only single-class runs are safe to quote from report
files. And the default suite carries a large pre-existing red set plus order-dependent churn of
about three classes between two runs of the same tree, so a small delta is noise unless you can
attribute it.

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
- **NEVER conclude *absence* of divergence from a non-cascading filter.** Scanning for
  non-cascading rows is the right way to *find* a defect and the wrong way to *rule one out*. A
  round claimed "no non-cascading error on any physics field, therefore the engine's inertia
  matches" and built four subsequent rounds on it; the physics divergences were present and all
  flagged `cascading: true`. The lane that discovered this blind spot then walked into it twice, so
  treat it as a hard rule rather than an awareness point: to establish that a field does *not*
  diverge, read that field's rows directly.
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
- **When a defect has a sharp onset after a matching run, ask what made the code path observable,
  not what changed in it.** A one-step animation slip began at row 6601 after hundreds of matching
  rows, which looked like proof that the code was correct until something changed. It was not:
  `g_speed` collapsed to zero at 6600, taking the computed frame duration from 0 to 4, and **while
  the duration is zero the ordering under suspicion cannot be observed at all** — a decrement on
  zero is immediately negative, so every frame advances regardless of how decrement, reload and
  advance are sequenced. The defect had been present throughout and only became expressible at 6601.
  This costs one fixture column and can retire an apparent contradiction outright, so ask it before
  concluding that a matching prefix exonerates the mechanism.
- **Diff a rebase against its new base on non-subject paths before trusting it.** Rebasing a trace
  fixture onto current `develop` produced a commit that silently reverted 20 lines of `README.md`
  and 7 of `docs/agent-workflow/briefing-trace-rounds.md`, neither touched by the original commit;
  the conflict markers showed only one file. Check with
  `git diff <new-base>..<rebased> -- ':!<subject-paths>'` and expect it to be empty. This is the
  same hazard as the silent-revert merge already recorded in the frontier log, arriving through
  rebase rather than merge.
- **Never `git stash` in this repository**, including `--keep-index` for a throwaway control. There
  are ~26 stale collaborator stashes and a failed push/pop is destructive. To take a control with a
  change removed, remove the files in place instead. A round that reached for it caught itself, but
  note the measurement would have been false anyway: the run under the stash still had the fixture
  present, so it was a control in name only.
- **A clean break beats stable-or-growing as a first discriminator.** Asking whether a delta is
  stable or growing distinguishes a one-shot discrepancy from a rate error, and both are useful. But
  the sharper question is whether the two sides are *bit-identical* up to the divergence frame: a
  gradual drift shows small disagreements earlier, while a clean break at a single frame says a
  discrete event fired on one side only. On one round that took the lane to an input lock in one
  step rather than into physics, because every position, velocity and status field matched exactly
  through the preceding frame.
- **On an accumulating defect the reported delta is a lower bound, not the target.** The magnitude
  rule below assumes the divergence you can see is the size of the effect. That holds for a
  one-shot discrepancy and inverts for a rate error: a sidekick reported two pixels out turned out to
  be stepping at double the ROM's rate, with the comparator's first hit merely the row where the
  accumulation crossed the threshold. Before sizing a mechanism against a reported delta, check
  whether the delta is *stable* across the window or *growing* — sizing a rate error against its
  first observation aims at the wrong number entirely.
- **Check the magnitude before accepting a signature match.** A documented pattern fitted a
  divergence qualitatively -- rolling-air sliding into a flush wall -- and was proposed as the prime
  candidate. Its arithmetic never fitted: the mechanism offers a 3-pixel shortfall (rolling shrinks
  `x_radius` 9 to 7) against a divergence of eight rows at ~8 px/frame, about 64 pixels. The
  mismatch was computable at the moment the candidate was proposed. A skill that documents a
  signature tells you the shape and not that your instance matches it, so before adopting one, check
  that the effect it predicts is the size of the effect you have.
- **A model must predict outside the case that motivated it.** A round derived a structural rule
  from one segment, where all six of its later gaps matched exactly, and was one step from reporting
  it as general. That segment was a back-to-back drain of a freshly filled queue, which most
  segments are not; the rule survived only after testing on nine continuous-drain segments across a
  28-segment stream, and after restricting the comparison to cases where the quantity means
  anything. **One segment agreeing with a model is the segment-level form of "a green fixture proves
  the fixture."** When setting a falsifiability test, require the prediction to hold somewhere other
  than the case it was derived from — otherwise a model can pass the stated test and still be fitted
  to its origin.
- **The directional check also scopes: use it to exclude families, not just candidates.** Before
  investigating a proposed cause, ask whether it predicts the observed sign — and whether it predicts
  the observed *extent*. "Mode is wrong across twelve rows" cannot be produced by any single-frame
  mechanism, which excludes the entire one-frame class before any member of it is examined, including
  one the same lane had derived an hour earlier. One question, applied to the shape of the symptom
  rather than to a candidate, can retire a family.
- **A chain of true links can still support a false conclusion, if the effect is absorbed.** A round
  established that a hold exists, that its change causes the hold, and concluded the downstream phase
  shifts later. All three statements were true and the conclusion was wrong: the 16-frame hold sits
  inside a 757-row screen whose length is set elsewhere, so it changes nothing measurable. Measure the
  span you claim moved, at both endpoints, with one variable — do not infer that a real delay
  propagates. The most convincing wrong stories are the ones where every individual link checks out.
- **A same-value/different-outcome question needs both sides instrumented, not either.** Offering
  "instrument the engine or probe the ROM, whichever is cheaper" is a false economy when the question
  is why two implementations reach opposite conclusions from the same inputs: the engine side alone
  yields a value with nothing to compare against, and the ROM side alone yields the same. The cost is
  both, structurally, and a brief that presents it as a choice under-budgets the round.
- **Test the premise that admits the cheapest disproof, before elaborating a mechanism.** A thread
  inferred "a character stopped dead at a fixed x means a wall" and spent **nine rounds** eliminating
  probe offsets, plane selection, solidity bits, quadrant dispatch and collision-data layers — every
  elimination correct, and all of them inside a subsystem that was never involved, because the
  layout word at that position is zero and there is no wall. One probe on the layout would have
  settled it at any point. When a chain of eliminations keeps coming back faithful, suspect the
  founding inference rather than the next layer: a subsystem that is faithful everywhere you look may
  be faithful because it is not the one at fault.
- **A layer list must include the layer above it.** Asking "which Chunk, which Block, which solidity
  attribute, which height mapping" presupposes something is there. None of those questions can return
  "the layout is empty", which was the answer. Before enumerating layers, ask whether the thing being
  layered exists at all.
- **Before recording a consequence, ask whether one search settles it.** A round drew a substantial
  conclusion -- "the capture is correct and insufficient" -- and the next round refuted it with a
  single grep of the fixture for a fingerprint, which appears zero times in 242 edges. The expensive
  speculation was disproved by the cheapest possible check, and that check was available before the
  paragraph was written. Pair this with the empty-probe rule: cheap searches belong *before*
  conclusions, not after them.
- **A kill condition that only pays out when it fires is half-used.** A surviving kill condition is
  not a null result: it forced the next measurement rather than ending the line, and that measurement
  is what overturned the round's own consequence. State kill conditions so that surviving tells you
  where to look next, not merely that you may continue.
- **Re-read a source you have already used, when it is the source of a surprising absence.** Four
  rounds turned on a recorded arm that was missing from a stream. The explanation was a comment in
  the recorder, forty lines from the fingerprint function the same lane had used to build its reverse
  map: level-load arms are discarded by design and never reach a trace file. Having read a file once
  makes it *less* likely to be re-read, not more. When an absence is doing load-bearing work, go back
  to the thing that produces the data before reasoning about what the absence means.
- **Ask for an inconvenient result to be explained, not accepted.** A brief pre-committed a round to
  explaining an outcome that would otherwise have looked like confirmation — "if the ROM does use
  AddPLC then the entry should appear in the recording and it does not, so that outcome would need
  explaining". Without it the round would have read `AddPLC`, seen it agree with the engine, and
  stopped. This does work no falsifiability test does: it binds the *agreeable* branch as well as the
  disagreeable one.
- **Survey the mechanism, not just the thing you plan to change.** A brief asked whether a counter
  had a second instance elsewhere -- a sensible pre-implementation check. Asking it surfaced
  something larger: an owner for adjusting that counter already existed, handling the mirror-image
  case, with a guard stating the very invariant the new work needed to preserve. The planned design
  would have added a second axis beside the mechanism built to make the axes meet. "What else does
  this?" is a narrower question than "what already owns this?", and only the second finds a
  collaborator you were about to duplicate.
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

## What a productive round actually looks like

A lane that took one S1 defect from "the chain is blocked" to a landed fix corrected the summary
written about it, and the correction is worth keeping as calibration:

> Eleven rounds, one code change of about forty lines, three retracted claims of my own — two of
> which had already been merged. Most of what I produced was finding out that six earlier
> explanations were wrong, four of them mine, and the method rules came out of those rather than out
> of the fix.

That is a reasonable ratio for a defect nobody understood at the outset, and it is **not** the ratio
a summary implies. Two other threads the same day ran to ten and fifteen rounds with comparable
shapes — one spent nine correct eliminations inside a subsystem that was never involved, because the
founding inference went untested.

Expect this. A round ending in *"I measured X, it is not the cause, here is what that rules out"* is
a good round. A round ending in a landed fix resting on an unmeasured step is not, however green the
gate — and several such fixes were caught here only because someone went back to ground that had
already been accepted.

## A briefing failure mode: the false dichotomy

The general form, arrived at after four instances: **when both offered options presume the current
behaviour is correct-as-specified, neither can express "the spec was already being violated".** That
was the answer twice — an invariant and a traversal both correct with the defect outside the pair,
and a port whose documented contract the implementation already contradicted, so closing the gap
claimed no new authority. Before offering two options, check whether both assume the existing
behaviour matches its own stated contract, and if so add the third: that it does not.


Over one session a reviewer posed four discriminators as exclusive choices and was wrong every
time, in three different ways:

- *"Does recorded admission span a whole visual run, or is it re-activated per prepared level?"* —
  **neither.** It begins once, at the live-to-recorded conversion after control release.
- *"Is the twin-tails DPLC driven by the body's animation state, or does it have its own trigger?"*
  — **both.** The body selects the script on change; the tails then advance on their own timer.
- *"Is the divergence at a selection, or mid-script?"* — **both**, and answering only the first half
  would have produced a partially-effective fix that invited stopping.
- *"Is the invariant wrong, or is the traversal wrong?"* — **neither.** Both are right, and the
  defect is one frame of ROM phase that the traversal newly exposes because before it the wait never
  happened at all.

Each framing pointed at a fix that would look partially effective. Write discriminators as open
questions unless you can state why the alternatives cannot both hold — and expect a third case where
neither holds and the mechanism sits outside the pair. Where a hedge is needed, phrase each
proposition separately: *"if the sequence matches and the divergence is upstream"* is one conditional
bundling two independent claims, and it cannot express the case that occurred, where the sequence did
not match and the upstream was correct.

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

## Eighth rule: a check that is blind on the files at risk is worse than no check

The prescribed merge check used to be "diff the rebased commit against its new base,
restricted to paths the change should not touch, and expect empty". A round reproduced a
known-bad rebase and showed that check **passes on the bad resolution**, because the files
that actually lose content — `README.md` and this document — are paths the change
legitimately *does* touch. The merge policy requires the README entry, so they can never be
on the "should not touch" list.

The real hazard is not a missing conflict marker. Git flags every conflicted file. It is the
**asymmetry of the hunks**: develop's side can be one to two orders of magnitude larger than
the incoming side, and in the worst case the incoming side is **empty**, so a reflexive "take
theirs" deletes content and adds nothing.

So: resolve conflicts **append-both, never take-theirs**, then assert that **no line present
in the base is missing from the result** (multiset difference per file). Treat that check as
a discovery tool, not a verdict — it correctly flags intentional deletions too, and those need
adjudicating rather than suppressing. A merge in which it fires and every hit is a line the
change deliberately replaces is a clean merge; one in which it fires and nobody looked is not.

A check that cannot fail on the case it exists to catch does not produce safety. It produces
confidence, which is worse.

**Amendment: conflict markers are not the trigger — the file being rebased is.** A later round
rebased a branch onto develop and silently lost a 32-line section of the frontier log. Git
raised **no conflict** in that region; the section was simply present in neither develop nor
the rebased result, and it was the round's pre-registered predictions, the thing that made its
whole result legible. It was caught only because the missing-line assertion was run anyway,
after a rebase that reported clean. So run the assertion after **any** rebase or merge that
touches `trace-frontier-log.md`, `briefing-trace-rounds.md`, `README.md` or `CHANGELOG.md` —
not only when markers appear. Silent loss is the failure mode the check exists for, and it
leaves no signal to prompt you.

## Ninth rule: a latent correction can be worth more as evidence than as a fix

The usual value of landing a latent fix is hygiene — the engine was on the wrong branch, so
correct it and note that nothing measured moved. Occasionally the value inverts.

One round modelled a `FixBugs = 0` path and, in doing so, established from the ROM bytes that
a surviving wild read is **inert** rather than merely unlikely: the score cap bounds the
swapped word, odd addresses return before any word access, and every remaining case is
rejected by the following test. The fix itself was latent and could never have moved the
frontier — it only ever *removes* an interaction the engine already had too few of. But
proving inertness **eliminated the site from the candidate list**, which is what the frontier
actually needed.

Ask of a latent correction not only "does this make the engine right" but "does the analysis
that produced it close a candidate". When it does, say so in the report — that is the part
the next round consumes.

## Tenth rule: the symptom's axis is not evidence about the cause's axis

A round spent eleven rounds on a signpost that landed 1,200 frames early. It eliminated six
things about the signpost's vertical behaviour — both timers, the bounce, the cooldown phase,
the bump window, a velocity shift order — and never once diffed the signpost's **horizontal**
position, because the symptom was a landing and landings are about Y. The cause was four
`x_vel` / sub-pixel assignments the ROM does not make, visible as a one-frame divergence on
the axis nobody had looked at.

"Instrument the writer instead of inferring from motion" is the version of this rule that gets
quoted, and it is right, but it is not the whole move. The move that worked was instrumenting
the writer of the variable that was **not** suspected. A symptom tells you where the error
became visible; it is silent about where it was introduced, and an axis is one of the cheapest
things to check and one of the easiest to assume.

When a candidate list has grown long and specific, that is evidence the list is being drawn
from the wrong axis — a correct axis usually yields the cause before six eliminations, not
after. Treat a long ruled-out list as a prompt to widen the *dimension*, not to keep narrowing
within it.

A detector built for the suspected axis inherits the same blind spot. The same round's
kick counter reported fifteen against the ROM's seventeen because it inferred kicks from
direction changes; the honest fix came from the fixture's own structure — `Obj_EndSignFall`
adds `#$C` to `y_vel` every frame, so recorded deltas are monotonic and any upward jump of
two or more is a fresh kick. State a detector's floor when you report its count.

## Eleventh rule: build the disconfirming case by hand before trusting a ratio

A round supported its model with "8 of 8 promotions are visible on their own row". The
detector defined a promotion frame as the frame where the larger `remaining_work` first
appears — so a promotion that was *deferred* past its own row got recounted as a promotion on
the later row, and then tested for a lag one row further on, where there was none. Every
counterexample the hypothesis had was silently reclassified into a confirmation. "8 of 8" was
the definition restated, not evidence, and a change was built on it.

A ratio near unity is the signature of this failure at least as often as it is the signature of
a real effect. Before quoting one, **construct the disconfirming case by hand and check the
detector reports it as disconfirming.** A detector that cannot express the counterexample is
not measuring the hypothesis; it is measuring its own definition.

The same round is also the source of the companion rule: when a fix moved one segment green
and a different one red, leaving the red count unchanged, the round reverted rather than
reporting the count. **A count that holds while its membership moves is not a null result** —
it is two results cancelling, and the one that reads as "no change" is the one hiding a
regression.

## Twelfth rule: some divergences are not frame-derivable, and hunting a predicate is the error

Frame-granularity state cannot always decide a question. One round established, from
`Level_MainLoop` and `VBlank_Lag`, that whether a main-loop tail has run when a lag V-blank
lands depends only on **where in the body the 68000 was interrupted** — and produced two rows
in committed runs with identical frame-visible engine state and opposite outcomes. No
predicate over frame state can separate them, so under hard rule 3 there is nothing legitimate
to write: any discriminator would be fitted by construction.

When a round reaches that point, the finding is the *impossibility*, and it belongs in the log
in those words so the next round does not spend itself re-deriving it. The sanctioned route for
genuine sub-frame hardware timing is the per-movie sidecar of hard rule 4 — not a cleverer
predicate.

## Thirteenth rule: "A did not match B" has a third answer — the index is broken

When a symptom is phrased as "A did not match B", carry three candidates, not two: A is absent,
A is wrong, or **the index they are paired on is not advancing**. The third is invisible from
either side's data, because A and B are each individually correct — nothing about the
submission looks wrong and nothing about the recorded edge looks wrong. It is also cheap to
test directly: read the pairing coordinate and check it moves.

**The round that produced this rule then retracted its own example.** It reported the replay
port's row latch frozen at zero; re-probed, the latch advances 30,667 times, and the reading
had come from the *tail* of a probe log after the port had finished. The real defect was the
first candidate after all — the engine had no matching work pending when the edge arrived.
The frame is kept because it is sound and it is a gap real briefs have; the example is kept
because a rule whose exemplar was withdrawn should say so rather than quietly acquire a new one.

Which is the wider lesson here: **an elegant reframing is not evidence that it describes the
case in front of you.** A frame that would have been right about a different defect is still
wrong about this one.

## Fourteenth rule: closure ownership is the row after, never the row before

Four times in one session a round mis-assigned engine work to the wrong comparison row, and
each time the error survived because the mistaken reading was *coherent*. A closure sits
between two cursor advances and produces the state sampled by the **next** row. So work
observed after the advance for row N belongs to row N+1's sample, not row N's.

The most expensive instance: a round reported a genuine-looking, ROM-citable defect — the
engine servicing PLC patterns on a recorder-lagged row where `VBlank_Lag` services none — and
built a two-compensating-defects model on it, including a retrospective explanation of an
earlier regression. Re-measured, the service belonged to the following row and the engine's
lag handling had been correct all along. The whole model went with it.

Two things make this worth its own rule. First, the retrospective explanation is what bought
the model belief; a wrong model that explains a past surprise is more persuasive than a right
one that does not, so treat "and it explains that regression too" as a reason to re-measure,
not as corroboration. Second, the fourth instance happened *after* the round built the probe
that exists to prevent it — because it read the result back off a quoted excerpt instead of
re-running the probe. An instrument only helps on the runs you actually use it for.

## Fifteenth rule: a profile's includes bound your sweep, not its excludes

`-Ptrace-replay` does not merely exclude a few classes. It carries an `<includes>` block of
`**/tests/trace/**` **and nothing else** (`pom.xml`, the profile block). It runs roughly 792
tests over 156 classes. The default suite runs roughly **15,176 tests over 1,919 classes**.

So a "clean trace sweep" says nothing whatever about the object suite, the unit suite, the
rewind guards, or any test of a shared accessor. For a change confined to trace comparison
logic that is fine. For a change to a shared runtime accessor — a width, a sensor, a lifecycle
predicate — the trace profile is close to no coverage at all, and reporting it as an empty
both-way diff overstates the evidence by two orders of magnitude in class count.

Before treating a sweep as bounding a change's blast radius, **read the profile's `<includes>`
and count what it actually ran**. Ask which suite exercises the thing you edited, and run that
one too. A round that changed an object's on-screen cull width found the entire unit suite
invisible to the profile it had been told to sweep with.

Corollary, learned the same day: **do not re-run a single class inside the worktree whose
surefire XML is your evidence.** A solo `-Dtest=` run regenerates `target/surefire-reports` and
overwrites the suite run's result, and the surviving XML then reports the solo outcome. An
isolated run cannot settle an order-dependent failure anyway, so the re-run destroys the record
without answering the question. Copy the reports out first, or run the check in a separate
worktree.

## Sixteenth rule: pin a control by commit hash — `origin/develop` moves under you

A round created its control worktree with `git worktree add … origin/develop` *after* cutting
its branch, and develop advanced in between. The control then showed a different S1 chain error
count from the branch — which looked exactly like a cross-game regression caused by an
S3K-only object change. It was **reproducible**, and it **survived isolated `-Dtest=` re-runs
in both worktrees**, so every flakiness check passed it through. Re-detaching the control to the
branch's actual base made the discrepancy vanish.

The failure is silent, reproducible, and points at the change under test. On a busy day develop
can move several times an hour, so `origin/develop` is not a base — it is whatever the last
fetch happened to see.

**`git rev-parse` the base once, and create both worktrees from that hash.** Quote the hash in
the report. A control that is not provably at the branch's base measures two changes at once
and attributes both to yours.

## Seventeenth rule: a compensation stack is load-bearing — remove it in one move

When a defect has been absorbed elsewhere, fixing the real cause alone makes things dramatically
worse before better. One round fixed a one-dispatch trigger latency and watched a segment go
from 292 errors to 50,060, because three separate compensations had been tuned around the
original defect and now over-corrected: a one-frame deferral, a `+ 1` on a timer, and a
re-acquisition hatch for a lost state bit. All three had to come out with the fix, in the same
change.

Two things follow. **Expect a real fix to look catastrophic mid-flight**, and do not revert on
the first number — find what was absorbing the defect. The compensations name themselves if you
look: one of them carried a comment describing the very behaviour it was working around.

And **a green test may be green for the wrong reason**. Removing the compensation stack turned
a passing trace red, and the reason was that the compensation had been supplying a status bit
the ROM supplies by a different route. That trace had been passing on a coincidence; the fix
that reddened it is what made it pass for the right reason.

## Eighteenth rule: an identical error fingerprint means the same lever, whatever the site

Two attempts at the S2 title-card seam were implemented at different sites, one deliberately
chosen to differ *in kind* from the other, with a design note arguing exactly that. They
produced **byte-identical** failures: same error count, same frame, same field, same two values,
same three failing axes. The site changed; the lever did not.

An error profile that reproduces to the value is a fingerprint of the *mechanism being moved*,
not of the code that moved it. When a new approach reproduces a rejected approach's numbers
exactly, the new approach is the old one wearing different clothes — stop and find what both
are actually perturbing, rather than looking for a third site.

The same round adds the negative form: a mitigation that changes **nothing** — not the count,
frame, field or values — was not insufficient, it was irrelevant. Its hypothesis was already
refutable from code the round had read, since the locked title-card rows take the same bare
return every frame without harm. Before adding compensating work to a production path on a
hypothesis, check whether an existing path already does the thing you fear.

Corollary for briefs: when a round proposes a new site for a previously rejected change,
require it to predict whether the error profile will change. A prediction of "same numbers,
different site" is a prediction that the site is not the lever — and it is cheap to check
before the code is written.

## Nineteenth rule: port the READ, not the event that last wrote the field

A routine that reads a field at its own entry is not the same as a callback that fires when
something writes that field. They differ exactly when **something else writes in between** — and
that is the case a trace catches and code review does not.

One round chased a missing ride transfer, a missing bit-clear and a pass-ordering defect, and
killed all three with probes. The engine had every piece of state right — the bits, the clears,
the interleaving — and set its trigger from an `onSolidContact` callback where the ROM re-reads
`status(a0) & standing_mask` at every dispatch entry. A latch taken at contact time cannot see a
clear that arrives afterwards; a fresh read at the ROM's moment can.

So when porting, ask *when the ROM looks*, not *when the value last changed*. A sticky flag set
at event time is the natural-feeling translation and it is wrong whenever a second writer exists.
This is invisible to any probe that only checks state **values** — the values were all correct.

## Twentieth rule: an unstable test class is not an attribution instrument

The same round's first default-suite read was control 15,111/56 against fix 15,034/54 — a 77-test
gap with two classes apparently *fixed* by the change. All of it was instability: the entire 77
was one class reporting 83 tests in one run and 6 in another, **the same fix tree run twice** gave
15,034/54 then 15,111/55, and every implicated class passed in isolation in both worktrees.

A class whose *test count* varies between runs cannot support any claim about a change, in either
direction — including a flattering one. Compare runs of equal size, name the unstable classes, and
say plainly that nothing outside the measured area is attributable. Banking two accidental green
classes would have been the easiest thing in the world and would have poisoned the next round's
baseline.

## Twenty-first rule: only a same-tree revert separates the change from the checkout

Every red-set comparison in this project has compared a fix worktree against a control
worktree. That comparison carries a **per-run noise term of one to two classes**, drawn from a
pool of at least four known-flaky classes, emitted independently of the code under test.

A round measured a class red twice on the fix side and zero times on control, which is exactly
the pattern the two-runs-per-tree protocol exists to catch — and held the fix rather than
explain it away. It then ran the decisive control: **the same worktree with the change reverted
in place**. The reverted run produced *two extras of its own*, sharing none with the applied
runs, while the 53-class core stayed identical across all three. So a two-of-two appearance on
one side is a draw from the pool, not evidence.

Two-runs-per-tree catches a *single* flake. It does **not** catch this, because the same flake
can land on the same side twice by chance. Only a same-tree revert holds the checkout, the
symlinked resources and the machine's neighbours constant while varying the code — and it costs
one suite run.

So: for any single-class difference that survives the two-runs protocol, **revert in place in
the fix tree and re-run there** before believing it. Copy the file aside; never `git stash` in
this repo. And note what this implies retroactively — a "both directions empty" cross-worktree
diff is evidence about the core, not about the one or two classes drifting around it.

## Twenty-second rule: rebasing a stacked branch duplicates append-only files

When a branch is stacked on another that has since been merged **under a rebased hash**,
rebasing the child replays the parent's commits — because the hashes differ, git cannot tell
they are already applied. Code hunks resolve cleanly as already-applied. **Append-only files do
not**: a CHANGELOG entry or a frontier-log append is a pure insertion at a moving offset, so it
is reapplied and the file ends up with the same block twice, a few lines below the original.

A round caught this in its own rebase — a duplicated "### Fixed" block in `CHANGELOG.md` — and
fixed it by resetting and cherry-picking only its own commit rather than rebasing the stack.

So: after rebasing a stacked branch onto a develop that already contains its parent's content,
**diff the result against develop and check the file list before pushing**. A clean rebase is
not evidence; the duplication produces no conflict. The tell is a file appearing in the diff
that your change never touched.

This applies to whoever merges as much as to whoever rebases — when merges rewrite hashes,
every stacked child inherits this hazard.

## Twenty-third rule: the headline field is not the earliest divergence — read the histogram

A chain segment's report gives `errorCount` and a **five-entry ring buffer of recent
mismatches**. There is no full `errors` list; parsing it as one reports zero. So the field named
in "first non-camera mismatch at frame N" is whatever the comparator happened to hit first on
the *physics* axis, and it is routinely the least informative field in the set.

At one frontier the headline was a one-pixel `sidekick_y`. Instrumenting
`LiveTraceComparator.absorbDivergentFields` to build a real per-field histogram showed the same
frame also carried `g_speed` `0x24` against `0x800`, `rolling` 0 against 1, `Status_Roll` set,
and a different animation id — the engine was performing a **whole spindash the ROM never
performs**, and the pixel was just the roll-height adjustment on the way out.

The same histogram showed two animation axes first diverging **thousands of frames earlier**
than the physics frontier everyone was watching.

**Split the histogram by frame range before reading it.** A round applied this rule, aggregated
the value pairs across a whole segment, saw a two-way mixture and called it a cascade — then
found that rows 800-900 carry exactly one pair in one direction, and the mixture belonged
entirely to rows after a *later* frame where position genuinely diverges. That is the same
error as trusting a headline field, one level up: an aggregate over the wrong window answers a
question you did not ask. A distribution is only evidence about the window it was taken over.

So: before briefing or chasing a frontier, build the per-field histogram and read the *earliest*
divergence per field, not the reported one. The headline tells you where the comparator stopped,
not where the engine went wrong. And a report format that looks like a full error list but is a
ring buffer will quietly answer "no errors" — check what the file actually contains before
trusting a parse of it.

## Twenty-fourth rule: diff failure messages, not class names

A red-set diff by class name **cannot detect a change that alters why an already-red class
fails**. A lane's own clean sweep contained the evidence that its assertion broke another game's
chain — that chain class was already red in both arms, so the diff reported "identical red sets"
while the change had silently replaced the failure underneath it. The regression reached develop
and blocked an entire game's measurement for an hour.

This is strictly worse than the count-holds-while-membership-moves trap, because here the
membership did not change either. Nothing about the shape of the result was wrong; the shape was
simply not a fine enough instrument.

**For any change to shared harness or comparator code, diff the first failure *message* per
class, not the class list.** Two arms can agree on every class and disagree on every reason.

Companion trap from the same session: `grep "Tests run:.*(Fail|Err)"` matches every line, because
`Failures: 0` contains `Fail`. It reported 156 red classes out of 162. The correct filter is
`Failures: [1-9]|Errors: [1-9]`, which gives 4. A filter that matches everything looks like a
catastrophic regression and is indistinguishable, at a glance, from one.

## Twenty-fifth rule: `-Dmse=off` or your `-D` properties never arrive

Maven Silent Extension is enabled by default in this repo (`-Dmse=relaxed` via
`.mvn/maven.config`), and it **silently swallows CLI `-D` properties**. A round found
`-Dtest=X` running the entire 15,000-test suite because the filter never reached surefire, and
the ROM-path properties never arriving either — producing 3,861 errors and 663 apparently-red
classes that were pure artefact.

This is the truncated-run trap in reverse: instead of measuring less than you think and reading
it as a fix, you measure *everything* and read it as a catastrophe. Both mislead, and this one
also silently ignores the class filter you believed you were running.

**Always pass `-Dmse=off` when measuring.** Any round that quotes a red count from a run without
it — with `-Dtest=` or with ROM paths — is quoting a number about a different command than the
one it thinks it ran. The tell is a red count in the hundreds, or a `-Dtest=` run whose duration
matches a full suite.

## Twenty-sixth rule: never accept a self-report about process state

Machine state is directly observable. `pgrep -af` plus `readlink /proc/<pid>/cwd` answers "who is
running what, in which worktree" in one line, and it is never in doubt.

Two coordination failures in one session came from a lane's self-report standing in for that
check. A lane reported no suite running while a full suite was forking in its worktree; the
report was relayed as fact, two suites contended through the symlinked shared resources, and the
contended run died at 1,657 of ~1,919 classes. Its terminal line read **12,971 tests / 46
failures** against a complete run's **15,185 / 55**, and a grep for the one class under
investigation returned zero — which at face value reads as *"nine fewer red classes and the
regression is gone"*. A truncated run masquerading as an improvement, for the second time that
night.

Two consequences:
- **Before assigning or standing down a lane, check the machine yourself.** Never run two suites
  concurrently, and confirm that before starting one, not after.
- **A timer left armed after its owner stands down is the same hazard as a stale branch left on
  the remote**: it fires into whatever is running next. Kill watchers explicitly and verify the
  script is gone, rather than assuming the process exited with its round.

Companion trap, from the same lane checking its own disarm: a `pgrep` for its watcher matched
**its own shell command containing the grep string**, reporting STILL ARMED when nothing was.
The same shape as `Failures: 0` matching a grep for `Fail` — a filter that matches the searcher.
Exclude your own command line, or match on the parent process rather than the pattern.

## Twenty-seventh rule: `git checkout -- <path>` restores from the INDEX, not from HEAD

A round mid-sweep restored its experiment tree with `git checkout -- src/`. The index still held
the reverted control files, so the "experiment" arm started against **control code**. It caught
this within seconds because `git status` showed staged `M` entries — but the failure is silent by
construction, and it would have produced a perfectly plausible, entirely worthless result: two
arms agreeing on everything, because they were the same code.

That is the most dangerous shape a measurement can take. An empty both-way diff is exactly what a
clean change looks like, so nothing about the output would have prompted a second look.

When switching a worktree between control and experiment arms, use `git reset --hard <hash>` or
`git restore --source=<hash>`, and check `git status` is clean **before** launching each arm. If
you have staged anything during a revert, `git checkout --` will hand it straight back to you.

The general form: a restore command that reads from a mutable intermediate (the index) rather
than a named commit will silently return whatever you last put there. Name the source.

## Twenty-eighth rule: check whether the absence is in the data or only in your view

Four times in one session a round nearly concluded something from a *missing* entry, and each
time the entry was missing from the **view** rather than from the data: an aggregated histogram
that hid a single-direction origin, a class-name diff that could not see a changed failure
reason, a grep of one skill mirror that concluded a rule was unrecorded when it lived in another
file, and an axis list truncated by the round's own `head -20` — where the item was *also* never
in that list by design.

The last one is the sharpest. A round read a cold-start axis list, saw no segment 0, and drafted
"segment 22 does not reproduce from a cold start, therefore it is carry-in" — **the exact
opposite of the truth**. Reading the report file settled it. A false attribution presented as a
measured fact is worse than an open question, because it looks settled and nobody re-checks it.

So before concluding anything from an absence, ask: could this be absent because of how I
looked? Check the pipeline (`head`, `grep`, a filter), check whether the thing is excluded by
design, and check a second source that would show it if it were there. **A negative result is
scoped to where you looked**, and the cost of confirming the scope is one command.

## Twenty-ninth rule: check a suspicious delta against both endpoints

A round measured that a cursor entered a segment correct and arrived short at its exit, and read
that as "the segment consumed fewer frames than its rows". It hadn't: the segment consumed every
row. The shortfall was the **untraversed gap between that segment's last row and the next
segment's offset** — the movie frames a level load occupies between them.

One subtraction would have caught it: the segment's `offset + rowCount` against the reported
cursor. They matched exactly. The delta was real and the interpretation was wrong, in the
direction that sends the next reader hunting inside the segment for a leak that does not exist.

So when a delta appears between two points, compute **both** endpoints from first principles
before deciding which end moved. "Correct here, wrong there" has at least three readings — this
end is right, that end is wrong, or the span between them is not what you think it is — and the
third is the one that gets skipped.

The tell that was available and missed: the round's own story left a loose thread unexplained
(why a cold start behaved differently), and it did not pull it. **An explanation that leaves one
of your own observations unaccounted for is not finished**, however well it fits the rest.

## Thirtieth rule: a mass-error run is an environment artefact until proven otherwise

A default-suite run reported 13,834 tests and 3,745 errors. The cause was
`UnsatisfiedLinkError: Failed to locate library: libglfw.so` — a race on the shared
`/tmp/lwjgl_farrell` native-extraction directory against a concurrent run in another worktree.
The clean re-run gave 15,193. The round discarded the first rather than reporting it.

Two tells, and they are cheap: the failures are `NoClassDefFoundError` / `UnsatisfiedLinkError`
on natives rather than assertions, and the test *count* is far below the known total. Grep the
log for `libglfw` before reading anything into the numbers.

Note this is a **different** artefact from a self-collision between two Maven runs in one
worktree, and it does not require that: two runs in *different* worktrees are enough, because the
native extraction directory is shared across the machine. So "I only have one run in my tree" is
not protection.

The general form, which now has three instances this session: **before believing a number that
would be a big result, check whether the run that produced it was healthy.** A truncated run
reports fewer red and reads as an improvement; a native-extraction race reports thousands of
errors and reads as a catastrophe; both are the environment, not the change.

## Thirty-first rule: "no field reported" is a statement about which check fired

A segment was described as **camera-only** in every brief for a full day. It wasn't: 205 of its
250 errors were animation fields on both characters from row 0, with camera a separate cluster
7,700 rows later. The chain prints the first non-camera **PHYSICS** mismatch, and animation
fields belong to the ANIMATION group — so "no field printed" meant "no *physics* error", and was
read as "only camera errors".

Nothing was wrong with the report. The phrasing described *which check fired*, and it was
inherited as a description of *what is wrong with the segment* — through several rounds and
several briefs, mine included.

This is the same family as diffing class names instead of failure messages, and as reading an
absence off a truncated view: **an instrument's silence is scoped to what that instrument
examines.** Before accepting a characterisation like "camera-only", "queue-only" or "no physics
divergence", build the per-field histogram and confirm the claim covers every verification group,
not just the one the headline is drawn from.

The tell is a phrase that has been repeated across rounds without anyone re-measuring it. Those
are exactly the claims that stop being checked.

## Thirty-second rule: a row's emulator frame is `bk2_frame_offset + row + 1`

Six rounds on one S3K segment ran aground on an apparent impossibility: the trace's row 0
carried an animation value and a frame counter that, according to every probe, never held those
values at the same time. Rounds proposed a mislabelled fixture, a composite row sampled at
different instants, and a recorder that read some other byte. All three were wrong.

The row was simply one frame later than everyone was looking. The recorder's arm gate calls
`start_new_segment` — which stamps `bk2_frame_offset = emu.framecount()` — and then **returns
without recording that frame**. So the arm frame carries no row, and a row's emulator frame is
`offset + row + 1`. Every contiguous segment succession in the run satisfies it.

The trap is that `offset + row` is also correct — for a *different quantity*. It is the 0-based
**BK2 input index**, which is what the input-mask helper computes, and its own source comment
warns that `emu.framecount()` runs one ahead of it in the recorder loop. One publication
sentence states both relations, and both are true. Conflating them is what manufactured the
contradiction.

**When you write a probe, compare `emu.framecount()` to `bk2_frame_offset + row + 1`.** Comparing
to `offset + row` silently lands on the arm frame — which is the *pre-settle* frame, still
carrying the load's lag count with status and animation not yet written — and that frame looks
exactly like a genuine engine divergence at row 0. It cost this segment several rounds, and it
retracted a writer list that, re-indexed, turned out to describe events fifty rows downstream.

The general form: when a measurement and a fixture disagree about *when*, suspect the index
convention before suspecting either instrument. Two off-by-one-related quantities with similar
names, both documented, is the setup.

## Thirty-third rule: BizHawk reports the PC *after* the storing instruction

A probe hooking a memory write logs `M68K PC`, and that value is the address of the
instruction **following** the one that performed the store. Read literally, every write-site
result in a chain of rounds points one instruction downstream of the code that actually wrote
the field.

This was found only because a lane built an exact address-to-label mapping and noticed the
field BizHawk named did not belong to the instruction at the reported PC — twice, independently,
in the same capture. Without a real mapping the discrepancy is invisible: a PC a few bytes off
still lands inside the right routine most of the time, so the label looks plausible and the
error survives. It is the small, consistent kind of wrongness that a plausibility check cannot
catch.

**Build the mapping, don't bracket it.** For S3K, `docs/skdisasm/skbuilt.bin` is byte-identical
to the locked-on ROM's entire S&K half, so `docs/skdisasm/sonic3k.lst` — the AS listing with an
address column — gives an exact address→label lookup rather than a guess between two known
labels. Verify the byte-identity yourself; it is what licenses the lookup. (This is label and
offset discovery, which the disassembly tree is *for*; it is not a hard-rule-1 asset read.)

Corollary, and the reason this rule is worth its length: a writer list derived from unadjusted
PCs was published, briefed, and built on for two rounds before being retracted wholesale. State
your PC→label method and your confidence whenever you report writers, so the next round can
check the derivation instead of inheriting the conclusion.

## Thirty-fourth rule: two Maven invocations in one worktree corrupt each other

A lane ran a second Maven invocation in a worktree that already had one running. The second
clobbered `target/test-classes` while the first was reading it, and the first reported **"No
tests matching pattern"** — a message that reads as "your `-Dtest` filter is wrong" or "that
class does not exist", not as "another process deleted the class files underneath me".

It joins the family this document keeps returning to: `liblwjgl` extraction failures that
surface as ~100 errors at 0.002s, contended suites that die partway and report *fewer* red than
the baseline, and truncated runs that look like progress. Every one of them produces output
that is structurally indistinguishable from a real result unless you know the signature.

**One Maven invocation per worktree, and run arms serially.** If you need parallelism, use
separate worktrees — they are cheap, and the control-arm discipline wants a separate tree
anyway. When a run reports something about *tests not existing* rather than tests failing,
check for a concurrent build before believing it.

The general rule, which now has four instances behind it: **before reporting a number, ask what
else could have produced this output.** A measurement you cannot distinguish from an
environment artefact is not evidence, and the artefacts here are not rare — four separate ones
turned up in a single day.

## Thirty-fifth rule: a failed compile looks like a short test run

A lane lost four probe iterations to an uncaught `cannot find symbol`. A Maven run whose
compilation fails produces a log of a few kilobytes that, skimmed, looks like a test run that
simply finished quickly — and if you are grepping for `Tests run:` or for your probe's output,
the *absence* of both reads as "the probe did not fire", which is a plausible experimental
result rather than a build failure.

**Grep every run for `COMPILATION ERROR` and `BUILD FAILURE` before interpreting anything
else.** A probe that produced no output because it never compiled is not evidence about the
engine.

**The nastier variant: a failed compile leaves the *previous* run's reports in place.** A round
today hit a main-source compile failure and then grepped `target/surefire-reports/TEST-*.xml`,
which still held the prior run's results — a complete, well-formed, entirely stale answer to a
question the build never asked. It caught the problem only because its new probe strings were
absent from the output. So the check is not merely "did this run fail"; it is **"is this report
from this run"**. Confirm the build succeeded before reading any report file, and if you are
probing, confirm your own probe's output is present rather than inferring from what is missing.

Same family: `-Ptrace-replay` sets its own `<surefire.argLine>` in the profile's
`<properties>`, so a CLI `-Dsurefire.argLine=...` is **silently ignored** — no warning, and the
run proceeds looking exactly like one that honoured the flag. Pass probe switches by environment
variable instead. (This is a second instance of the `-Dmse=off` lesson: a property that never
reaches the fork produces a confident, wrong measurement rather than an error.)

Both belong to the rule this document keeps re-deriving: **an experiment that could not have
worked returns the same shape of nothing as an experiment that worked and found nothing.**
Before believing a negative result, prove the instrument ran.

## Thirty-sixth rule: the branch is the artifact, the worktree is scratch

Rounds accumulate worktrees. In one day a session left 47GB across 60 of them, most belonging
to rounds that had finished hours earlier — including several whose code was deliberately *not*
merged, which is the case that makes naive cleanup feel unsafe.

It isn't unsafe, because of where the work actually lives. **Once a round commits to a branch,
its worktree holds nothing the branch does not.** Removing it is lossless and reversible: the
branch keeps every commit, including work that was correctly refused a merge. So the disposal
decision belongs to the lead, and it is cheap — no judgement about whether the work was any good
is required, only whether it is committed.

**The worker's obligation is therefore to persist, never to discard.** A round must not be asked
to delete its own work as a condition of finishing — whether unmerged work is worth keeping is
not the worker's call, and a round that reverted a candidate or had its premise retracted has
often produced the most valuable output of the day. What a round owes at stand-down is:

- every change committed to its branch, including candidates that were built and rejected —
  commit them with the measurement that rejected them, so the next round does not rebuild them;
- the branch name and commit hash in its report;
- probes and temporary instrumentation reverted, so the branch shows the round's actual position;
- nothing of value living only in the working tree.

**The lead's obligation is to sweep after standing a round down**: confirm the branch carries
the commits, then remove the worktree. Skip any tree with uncommitted changes — that is the one
state where the worktree holds something unique, and it means the round did not finish cleanly.
Ask it to commit; do not resolve it by deleting.

Two adjacent habits worth keeping: stop a round when you stand it down rather than letting
finished rounds pool idle, and send the closing message *before* stopping it — messaging a
stopped round resumes it.

## Thirty-seventh rule: equalise run order before comparing failure sets across worktrees

Two arms of a comparison were each run in their own worktree and both reported **5 failures**.
The sets were not the same: the control failed one S1 trace class, the candidate failed a
different one. Read as a count, nothing moved; read as a set, it looked exactly like "my change
broke a test and fixed another". Both classes passed in isolation.

The cause is already recorded elsewhere in this project: Surefire's default filesystem run order
varies per worktree and survives `mvn clean`, so **which** order-dependent test fails is a
property of the checkout, not of the change. `-Dsurefire.runOrder=alphabetical` on *both* arms
produced identical sets.

**The signature is a swap** — equal totals, different members, all of them passing alone. Note
that isolated runs cannot settle it: an order-dependent failure passes in isolation by
definition, so "it passes alone" is evidence of nothing. Equalise the run order and re-measure.

Corollary for any cross-worktree comparison: the two trees differ in more than your diff.

## Thirty-eighth rule: a backgrounded Maven that dies with its shell reports success

A round backgrounded a `mvn` invocation whose launching shell then exited. The build died with
it, leaving a 33-line log that ends mid-`testCompile` — no `Tests run:` line, no failure, no
stack — while the harness reported the *shell's* exit status of 0. Skimmed, that is a pass.

This is the same family as the failed-compile hazard (thirty-fifth rule) and it fails the same
way: **the absence of test output reads as "nothing went wrong" rather than "nothing ran".**

Detach properly (`setsid ... </dev/null`) and poll for an explicit `BUILD SUCCESS` or
`BUILD FAILURE` line before reading any total. An exit code from a wrapper is not the build's
verdict, and a log that simply stops is not a result.

## Thirty-ninth rule: create round worktrees copy-on-write

Rounds want their own worktree, and often a second one for a same-tree control arm. A full
checkout of this repository is roughly 800MB, so a day of parallel rounds accumulates tens of
gigabytes of near-identical trees.

On a filesystem with native reflink support (btrfs, XFS with `reflink=1`, APFS), that cost is
almost entirely avoidable: the **`cow-git-worktree` skill** creates a worktree by reflinking
unchanged tracked files from an existing clean worktree instead of checking them out afresh.
Measured here: a new worktree reported ~810MB apparent size and **zero exclusive bytes** — every
block shared with its source until something writes to it. Creation took about 20 seconds and
the resulting tree's `git status --short` was empty.

Reflinks are copy-on-write, not hard links: writing to a file in either tree diverges it
normally, so the two worktrees stay fully independent. **Never** substitute hard links here —
they would make an edit in one round silently corrupt another.

Use the skill for creating a round's worktree and its control arm. Use ordinary git for
`list`, `move`, `remove` and everything else. The helper falls back to a normal checkout when
the source and target are on different filesystems or the platform lacks reflinks, and reports
which path it took — check that report rather than assuming the optimisation applied.

**Pass `--from` an explicitly clean worktree.** Only clean, committed content is eligible to be
shared, so a source with local modifications degrades toward a full checkout. A round reported
`fallback=yes reason=copy-on-write-unavailable` on the same filesystem where an explicit clean
source gave `reflinked=7770 backend=ficlone fallback=no` — same machine, same btrfs volume,
minutes apart. If you see `fallback=yes` on a filesystem you expect to support reflinks, suspect
the source before the platform.

This pairs with the thirty-sixth rule: worktrees become cheap to create *and* the branch remains
the artifact, so there is no reason either to hoard finished trees or to skimp on a proper
control arm.

## Fortieth rule: a comment narrating engine bookkeeping is the tell for a fitted constant

Three compensations in one S3K camera controller — a pre-charged accumulator, a bare `delta += 2`,
and an airborne first-dispatch early return — were all fitted, all wrong, and all had **passed
review**. Together they were the entire remaining error count on a chain segment; removing them
took it to zero.

They survived because each carried a comment. The comments explained the number by narrating the
*engine's* bookkeeping — a phrase of the shape "has already crossed the native creation-pass
carry" — rather than citing a ROM routine. That reads as diligence. It is the opposite: a value
justified by how the engine happens to arrive at a moment is a value fitted to the engine's
current behaviour, and it will desync the first recording that arrives at that moment differently.

**A constant is justified by a ROM citation or it is not justified.** When reviewing or writing
one, ask what the comment cites. "Because `CreateChild1_Normal` allocates via
`AllocateObjectAfterCurrent`, which by construction returns a slot after the creating object, so
`Process_Sprites` always reaches the worker in the creating pass" is an argument. "Because the
engine has already crossed the carry by this point" is a description of a symptom.

Two corollaries earned alongside it:

- **Compensations come in families.** Removing two of the three took the target segment to zero
  and put a previously-green fixture at 2 errors, because a second route enters the same handoff
  airborne and the third compensation then fired. Stopping at the first green would have shipped a
  45→0 / 0→2 trade as a win. When you remove a fitted constant, look for its siblings before
  measuring.
- **The pattern propagates.** The same accumulator is ported at six other sites in this codebase,
  at least one carrying a comment with the same premise. A single ROM argument usually settles
  every instance in one direction — so when you kill one, audit the family.

**Widened after the audit: the tell is directional, and the mirror image is just as common.**
Enumerating all thirteen sites turned up pre-charging once more, and its opposite — a *skipped*
creation dispatch — twice. One site ran its gradual update before the switch that arms it, so the
creation-frame dispatch never happened and every later one ran a frame late. Another held a flag
whose comment explained why the creation dispatch should be skipped, and which was **never set to
true**: dead code that changed nothing and would have re-taught the defect to the next reader.

So the review tell is not "a comment justifying a seeded constant". It is **any comment reasoning
about which engine hook runs first**, in either direction. If a comment's argument is about
engine ordering rather than about a ROM routine, the code under it is guessing at a question the
ROM answers by construction.

A corollary from the same audit, worth copying: where a reserved slot genuinely *is* behind the
live cursor, the honest fix is to expose an explicit method that runs the allocation-frame
dispatch — not to pre-charge an accumulator to fake having run it.

## Forty-first rule: prove an arm was not truncated, don't infer it from its total

Two arms reporting the same total is weak evidence that both ran completely. A contended or
memory-starved arm dies partway, and if the classes it lost happened to be green, the total is
unchanged. Every hazard in rules 30-38 shares this shape: the output of a run that did less looks
like the output of a run that found less.

**Know your suite's full denominator and refuse any total below it.** A round today saw a
candidate default run report `13986 / 41F / 3139E`; the same tree re-run gave `15196 / 53F /
71E`. The complete default suite is ~15,196 tests — a run reporting materially fewer did not
finish, and its failure counts mean nothing. The same discipline applies per profile: know the
number, and treat anything short of it as a dead run rather than a result.

Three checks that actually establish completeness, in increasing order of strength:

1. **Compare the set of test classes run, by name.** A truncated arm shows a *short class list*,
   not merely a lower failure count. `diff` the `-- in <class>` lines between arms; an empty diff
   is worth more than matching totals.
2. **Compare the `Tests run: 0,` lines themselves, not their count.** Equal counts can hide a
   silent victim that happened to land symmetrically with a pre-existing empty class. Identical
   *classes* reporting zero means those are genuinely empty or skipped, not casualties.
3. **Grep for `forked VM terminated` and `Corrupted STDOUT`.** These name a fork death directly
   and appear in neither a healthy run nor, importantly, in the totals.

A round today ran its two default-suite arms concurrently — the most contended thing it did — and
established soundness this way rather than by hoping: identical class sets, identical zero-run
classes, no fork deaths. That is a defensible measurement taken under bad conditions. Matching
totals alone would not have been.

## Forty-second rule: diff the whole failure message, not a truncated prefix

A round diffing its two arms truncated each failure message to 200 characters to keep the output
readable. Two genuinely different S3K failures compared **equal** under that truncation — they
shared a prefix and differed only in the cursor value near the end.

This is the same family as diffing class names instead of messages (the twenty-fourth rule), and
it fails the same way: the comparison silently loses the distinguishing part and reports
"identical", which is exactly the answer a round wants to hear about its control arm.

Truncate for *display* if you must. Never truncate the thing you compare.

The same round caught two contaminated arms in the same session by comparing the **set of class
names** — a forked-VM crash that cost one arm a whole chain class, and a default-suite run that
lost three forks. Both had plausible-looking totals. That is the forty-first rule paying for
itself twice in one round.

## Forty-third rule: concurrent rounds are a resource budget, not free parallelism

Five rounds ran Maven suites concurrently on a 32-core machine with 30GB of RAM. Load average
reached 34 and free memory reached 4GB, and the consequences were not slowness — they were
measurement failures of exactly the kind this document keeps cataloguing:

- the default profile OOMs on some chain classes even with memory to spare; under pressure it
  can hit anything, and an OOM produces **"Java heap space", 0 tests run, no report**, which
  reads as a test that silently did nothing rather than a build that died;
- GLFW headless-init failures (`Failed to initialize`) appear under contention and land
  unevenly across arms — one round had to reclassify a three-error delta that made its own change
  look better, because the *control* arm was the contaminated one;
- a contended suite that dies partway reports **fewer** failures than the baseline, which reads
  as improvement.

**Two to three concurrent suite-running rounds is the honest ceiling on a machine like this.**
Beyond that you are not parallelising, you are queueing with extra steps, and degrading the one
thing every round depends on.

Practical scheduling, learned the same day:

- Much of a round's work is **static** — reading the disassembly, enumerating sites, tracing an
  engine path. That part never contends. Sequence a round so the static work happens first and
  the sweep happens once, at the end, for whatever actually changed.
- A round with an empty runtime diff **owes no sweep at all**: both arms are the same tree, so
  the measurement can only re-measure the baseline. Say so rather than spending an hour on it.
- Prefer the cheapest harness that reproduces the defect. A single chain class runs in 11-50
  seconds where a full profile run costs minutes, and per-class runs are also the only ones safe
  to quote report files from.
- When the box is loaded, do not kill a round mid-measurement to reclaim it. That wastes the work
  *and* produces the truncated logs you then have to tell apart from real results.

## Forty-fourth rule: an axis count is only comparable when both arms reach the same depth

Two chain arms produced 22 axis lines and 9. The 9 was not better: the candidate's walk **aborted
at segment 27** where the control reached segment 33, so four segments and seven gap axes were
never asserted at all. Fewer reported failures, strictly less evidence.

The same day produced the exact inverse. A fix took a chain from 2 axes to 3 — and that *was*
progress: one axis was removed, and two appeared, one of which only became **evaluable** because
the walk now completed far enough to evaluate it.

So the count moves for three unrelated reasons — defects fixed, defects introduced, and depth
changed — and a bare number cannot distinguish them. **Report reach and the axis list, not the
count.** Name the deepest segment each arm asserts, and diff the lists by message. If the arms
stop at different depths, say so in the same breath as any count you quote, because an
unqualified number reads as progress to whoever finds it later.

The same trap applies one level down: two arms that both assert a segment can still differ inside
it. A round compared reach and axis lists carefully, and separately found the candidate made an
already-red segment **worse by 901 errors** — a widening invisible to both the count and the list,
caught only by diffing per-segment error counts across the segments both arms actually assert.

## Forty-fifth rule: before believing a null result, verify the instrument is installed

A round needed a held branch's fixture applied to make a defect observable. `git apply --3way`
reported a conflict on one already-modified file and, in the same invocation, **silently created
none of the 28 new files**. Two full arms were then measured with no fixture present. They came
out byte-identical to base — which reads exactly like *"the defect does not bite"*, and the round
nearly reported that as overturning the premise it had been sent to test.

It was caught by counting the fixture files on disk and noticing the defect's signature string
appeared nowhere in either log.

This is the sharpest form of a pattern this document keeps returning to: **an experiment that
could not have worked produces the same output as one that worked and found nothing.** The
earlier instances were about builds that never ran; this one is about a build that ran perfectly
on the wrong tree.

**A null result is a claim about the instrument as much as about the subject.** Before reporting
one, prove the instrument was live: count the files, grep for the signature the defect would
produce, or run the arm you expect to *fail* and confirm it does. A partial `git apply` is
especially dangerous because it does part of its job and reports a conflict you may reasonably
attribute to a file you did not care about.

## Forty-sixth rule: a silent probe may be a build that never compiled

A probe inserted between an `@Override` annotation and its method made the build fail, and
`mvn -q` printed **nothing at all**. The empty output read exactly like "this code path never
runs", and briefly retired a hypothesis that was correct.

This is the failed-compile hazard again with the loudest signal removed: `-q` suppresses the very
line that would have told you. **Re-run without `-q` before believing a silent probe**, and treat
"my probe produced no output" as a claim about the build until proven otherwise — the same way a
null result is a claim about the instrument.

A companion from the same round: **`DynamicArtLifecycleService.movieLogicalFrame` is not a live
movie-row clock across a special stage.** It sat at the destination's `bk2_frame_offset` for all
4673 special-stage iterations and every gap iteration. It cannot be used to time a gap, and a
quantity that looks like a frame counter is worth checking against a second source before you
build on it.

## Forty-seventh rule: measure the quantity, do not reconstruct it

One round produced both halves of this lesson within an hour.

**Reconstructing when you could measure.** Investigating a one-pixel touch-box miss, the round
derived the player's box edge as `centreX − 8` instead of reading the value its own probe had
already captured. The derived number produced an overlap where there was none, and the correct
explanation was written off as non-causal. The probe's actual box restored it.

**Trusting a derived number that nearly fits.** The round before, a rider placement looked like a
character-radius mixup: the two radii differ by 4 and the observed step was 5. Taking the 4 as
confirmation would have produced a plausible fix changing the wrong constant. The 5 turned out to
be the ROM's own roll adjustment, so the step was evidence a code path *ran*, not evidence of a
wrong value — and the real fix changed no constant at all.

The two look opposite and are the same rule. In both, a reconstructed quantity was allowed to
compete with a measured one, and in both the reconstruction was wrong in a way that fitted the
story. **If the instrument already carries the number, read it. If it does not, get it before
building on a derivation** — especially when your derived value is close to the observation
without matching it, which is the case rule 3 already warns absorbs an error somewhere else.

## Forty-eighth rule: object execution order is observable behaviour

Three separate rounds in one day, across two games and three subsystems, traced a divergence to
**which slot an object occupies relative to another**:

- An S1 capsule burst: the ROM's first animal landed in a slot *below* its spawner, so it executed
  the following frame and took the last RNG draw instead of the first. The species assignment
  rotated by one, an animal already among the last released became the slowest in the game, and
  twelve frames later an art arm was late — ultimately ~74,000 comparator errors.
- An S3K battleship: the ROM spawner uses plain `AllocateObject` rather than the after-current
  form, so the new slot need not be reached by the current pass. The engine executed it one frame
  earlier, leading a fractional accumulator by one update.
- An S1 monitor and fan: the fan writes position **directly** rather than through velocity, so
  whichever runs first decides whether the monitor ever sees a one-pixel penetration. The engine
  runs them in the opposite order to the ROM.

The general statement: **an object's slot determines whether it runs before or after its
neighbours in the same frame, and that ordering is part of observable behaviour** — it decides RNG
draw order among siblings, whether one object sees another's position write, and whether a
spawned object gets an update on its creation frame. It is not an implementation detail, and a
slot permutation is not cosmetic even when the same objects are present.

Practical consequences:

- When a divergence involves two objects that interact, **check execution order before checking
  either object's logic**. In all three cases the individual routines were faithful.
- `FindFreeObj`-style allocation makes slot assignment depend on the whole run's spawn and despawn
  history, so an ordering defect can originate thousands of frames earlier than it surfaces.
- **Do not reorder a pair by anything measured off a fixture.** The ordering must follow from the
  ROM's own allocation, or it is a fitted model that will desync the first different recording.

**Sharpened by two later rounds.** The allocator names the guarantee, and the init's shape names
how much happens on the creation frame:

- `AllocateObjectAfterCurrent` returns a slot *ahead* of the current pass, so the object runs on
  its creation frame — and if its init falls through into its first routine rather than returning,
  that frame includes a routine step as well.
- plain `AllocateObject` scans from the bottom and **may or may not** return a slot ahead of the
  pass. This is the caveat that matters: the allocator tells you the placement is not guaranteed,
  and only the recording tells you which way it went in a given run. One round settled it by
  finding the object's code pointer still on its init routine at the end of its creation frame.

Two engine defects at one seam cancelled because of this pair — an object started a frame early
because its slot was assumed ahead of the pass, and a second started a frame late because its
init's fall-through was not modelled. Fixing either alone moved every downstream event by a row.

**The fixtures can answer these directly.** `aux_state.jsonl` carries `slot_dump`,
`object_appeared` and `object_removed` events — the ROM's own slot table and allocation history.
Slot-ordering questions are measurable against the recording rather than inferable, and a
frame-by-frame diff of the engine's dynamic slot table against that stream is the instrument these
questions want.

## Forty-ninth rule: prove your edit is on the path before believing a null

A round retracted a hypothesis on two null results — the change made no difference, twice, on two
different fixtures. Both nulls were real measurements of **dead code**. The expression under test
existed in two places: a private helper in one class, and the same arithmetic written out at seven
call sites in the harness that actually runs. The edits went to the helper. The harness never
called it.

The retraction was accepted, briefed onward, and cost two further rounds before someone
instrumented the live path and found the original hypothesis correct.

This is the instrument-not-installed rule (forty-fifth) in its most deceptive form: the build
succeeds, the tests run, the numbers are real, and the code you changed was never executed. A null
from an edit is a claim about **reachability** first and behaviour second.

**Before reporting that a change made no difference, prove the change ran.** Add a throwaway
assertion, a log line, or a deliberate breakage to the edited path and confirm the run notices. If
the same expression appears in more than one place — a helper and its inlined copies, a base class
and an override, a production path and a test harness's own arithmetic — establish which one the
failing scenario uses before touching either.

Corollary: duplicated logic makes every null result ambiguous. Where a round finds two copies of
the same expression, that is worth reporting even when it is not the defect.
