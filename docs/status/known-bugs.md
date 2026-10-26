# Known Bugs and Unfinished Work (Engine-Wide)

> **Canonical ledger.** This is the only open-bug list; trace frontiers live in
> [trace-frontier-log.md](trace-frontier-log.md) and S3K bug write-ups in
> [s3k-known-bugs.md](s3k-known-bugs.md). The former `bug-list.md` (S2) and `s3k-bug-list.md`
> (AIZ, 2026-03-25) working lists were folded in here on 2026-08-28 and deleted.

This document tracks **bugs**, incomplete implementations, and known parity gaps that we intend to fix but haven't addressed yet. Entries here are *not* intentional — they're acknowledged problems with a plan (or hope) of eventual resolution.

For **intentional** deviations from the original ROMs (architectural choices, feature extensions, deliberate bug-fixes of ROM data), see [known-discrepancies.md](known-discrepancies.md).

Entries should include:
- **Location** — the file(s) where the bug lives, if known
- **Symptom** — what goes wrong and where you can observe it (test name, trace frame, manual repro)
- **Suspected cause** — best current theory, with ROM/disasm references when relevant
- **Removal condition** — what needs to be true for this entry to be deleted

---

## TAS pause clock does not advance during native Start pause — 2026-10-26

**Location:** `GameLoop.prepareAdmittedIteration`,
`LevelFrameStep.serviceVBlankOnly`, and the `VIntRunCounter` binding to
`ObjectManager`.

**Symptom:** representative S1, S2 and S3K Start-pause checks at `c69447d77`
reach paused iterations with no advance in the bound V-int counter. All reach
ordinary gameplay and confirm that resume advances the counter.
Native V-int continues while the level frame and player wait; the independent
S1 GPGX control observes counter 629 through 633
while level frame 181 holds. Pause-based capsule and RNG phase manipulation
therefore cannot rely on the native clock behavior in these reached scenarios.
The S2 capsule test reaches the consequence: resume still hits the original
modulo-eight spawn phase, creates the late animal and delays results. Its
ordinary off-mask control omits that animal and reaches results.
Prepared S1 PLC work can still finish during pause, a separate supported action.

**Cause identified in source:** the global carrier reads the bound object clock
and deliberately avoids incrementing it. The paused frame services art and
hardware boundaries, then returns without the object-clock increment performed
by ordinary object execution.

**Removal condition:** the real Start/release/resume paths advance the native
counter once per represented V-int while preserving the paused level/player and
existing art-service boundaries. Convert the explicit limitation regressions
to native expectations and retain their running/resume controls. The
[TAS verdict record](../architecture/validation/2026-10-26-tas-trick-verdicts.md#observed-limitations)
records the tests and their scope. This is a parity defect, not an accepted
deviation; the verdict pass does not repair production behavior.

## TAS unsigned horizontal underflow — 2026-10-26

**Location:** playable level-boundary prediction/clamping in `PlayableSpriteMovement`.

**Symptom:** reached airborne left-underflow scenarios in S1, stock S2, KiS2
and S3K clamp to the left edge (`$10`) and clear horizontal speed. Native word
prediction wraps to a large unsigned X and takes the right-boundary branch:
S1 GHZ1 expects 9767, S2/KiS2 expects `$2168`, and the S3K fixture expects `$1128`.
Non-underflow controls exercise ordinary boundary behavior. These are local
boundary checks, not claims about every published level-wrap route.

**Suspected cause:** the engine's signed position/prediction reaches the left
clamp before reproducing the native unsigned word comparison.

**Removal condition:** pass the native right-clamp expectations and existing
ordinary-boundary controls, preserving word/subpixel behavior. The
[S1](../architecture/validation/2026-10-26-tas-verdicts-s1.md#s1_level_wrap),
[S2](../architecture/validation/2026-10-26-tas-verdicts-s2.md), and
[S3K](../architecture/validation/2026-10-26-tas-verdicts-s3k.md#s3k-hwrap--unsupported)
matrices give the reached entries and retained limitation selectors.

## S1 offscreen floating-platform standing retention — 2026-10-26

**Location:** S1 floating-block solid dispatch and standing-release ownership.

**Symptom:** `TestTasS1SlopeVerdict` lands on the real SBZ3 floating block,
moves the camera offscreen and walks beyond its edge. The engine releases
standing; native `FBlock_Action` omits `SolidObject` while offscreen, leaving the
standing latch used by the documented slope-lock trigger. The object remains
alive. The onscreen control correctly releases standing.

**Suspected cause:** standing maintenance still runs when native render gating
would omit the complete solid-object call.

**Removal condition:** reproduce offscreen latch retention through the real
object/collision seam while keeping the onscreen release control. See the
[S1 mechanism evidence](../architecture/validation/2026-10-26-tas-verdicts-s1.md#s1_slope_glitch).
The full SBZ3 shortcut is outside this local trigger check.

## S2 TAS launcher, boss, art and input limitations — 2026-10-26

The [S2 verdict matrix](../architecture/validation/2026-10-26-tas-verdicts-s2.md)
records native expectations, reached controls and exact commands at unchanged
production `c69447d77`. Its first local test commit is `4f5eb1ee2de0b6128c39728289516c079d71064e`.

| Owner / requirement | Observed limitation | Removal condition |
|---|---|---|
| OOZ sphere launcher | Actual hurt routine 4 at the sphere centre is rejected in stock S2 and KiS2; native `Obj48 loc_252F0` accepts routines below 6. Normal entry works. `TestTasS2CurrentLimitations`. | Accept the native hurt-state entry and qualify the downstream speed effect without a route-specific exception. |
| ARZ final-hit endpoint | Real final-hit contact reaches the travel endpoint, but engine routine becomes 4 instead of native `$A`; ordinary defeat reaches 8. `TestTasS2ArzBossEndpoint`. | Preserve the native assignment/addition ordering and execute the resulting explosion-skip transition. |
| DEZ shared art address | Explosion and animal PLCs both target tile `$580`, but the dedicated explosion sheet cannot display the animal pixels after the animals-last load; reverse-order control stays fiery. `TestTasS2DezArtAlias`. | Render the native last-writer art result for both load orders through the ROM art pipeline. |
| Halfpipe live pacing | Actual jumps occur, but their rendering-cost dependency is absent from the fixed live pacing owner. Running/jumping keep the same measured schedule. `TestTasS2JumpLag`. | Model and exercise the relevant jump-dependent native service cost. The test does not measure a native delta in the same 240-update window. |
| Intro P2 input | Actual P2 Start at the engine's final modeled visible text-fade step does not affect title admission; both controls take 17 updates. `TestTasS2IntroP2Start`. | Exercise the native P2-sensitive transition with a synchronized entry and verify its title-loading consequence. The engine's timer 21 is not asserted to be a synchronized native frame. |

The pause/capsule clock gap is tracked separately above. These entries describe
local mechanisms or missing capabilities, not full publication synchronization.
The DEZ author associates animal explosions with quick boss defeats; the test
proves the independently identified PLC alias prerequisite, not that causal route.

## S3K TAS memory, mode and entry-input limitations — 2026-10-26

The [S3K bug record](s3k-known-bugs.md#tas-mechanism-limitations--2026-10-26)
links reached slot-lifetime, allocation recovery, mode capability and
special-stage input mismatches to their executable controls. These tests
record compatibility gaps; they do not designate new intentional deviations.

## Current stock parity frontiers — 2026-10-08

The [continued stock audit](../architecture/audits/2026-10-07-stock-parity-gap-verification.md)
and [frontier log](trace-frontier-log.md) retain the exact commands and limits.
Round 3 is privately composed at `84f0c20f11` against published `d740b7a0`;
275 focused cases pass with no failures/errors/skips. Combined canonical verification completes17/4/0/0, preserving the qualified
frontiers below. Ordinary and fresh-guard qualification remain pending.

- S1: native death/floor reset closes all 192 MZ1 differences. The next early
  frontier remains MZ2_3 row101's three PLC prepared-state comparisons; a fresh
  native timing capture closes them locally but fails later matching/closure,
  so it is withheld. Segment33 remains an incomplete inherited premature-death
  route; the radius correction changes its propagation to 3203 errors and
  cursor210396, with exact attribution in the S1 audit.
- S2: native seventh-emerald leave/return/wait handling clears the SS7 results
  walk and reaches ARZ1/ARZ2. ARZ1 first differs at row4213 mapping frame
  `61/65`; ARZ2 first differs at row2175 X fraction `8800/5800`, then loses
  ownership in TITLE_CARD at cursor110617. Twelve return-gap clock axes remain.
- S3K: fan movement, retirement, clock and saturated-pool RNG behavior are
  corrected locally. Returned HCZ remains 82067 errors, first mapping row1507,
  first primary Y row1510, and LEVEL ownership loss at cursor68801. Earlier
  AIZ sidekick frontiers remain owned separately and unchanged.

These are trace parity and lifecycle frontiers, not complete-game certification.
Supported act/character, viewport, donor, load/respawn, whole-route rewind and
native presentation obligations remain in the [coverage backlog](level-test-coverage.md).

## Table of Contents

1. [Game Over and Continue Parity Details](#game-over-and-continue-parity-details)
2. [Persisted Editor Saves Disabled for S3K Gameplay Loads](#persisted-editor-saves-disabled-for-s3k-gameplay-loads)
3. [Trace Replay Recorder Coverage Follow-Up](#trace-replay-recorder-coverage-follow-up)
4. [S3K AIZ Items Carried From The 2026-03-25 Working List](#s3k-aiz-items-carried-from-the-2026-03-25-working-list)
5. [S2 EHZ Boss Animation Tables Contain Three Extra Frames](#s2-ehz-boss-animation-tables-contain-three-extra-frames)
6. [S2 Sideways Platform Allocation and Strict Rewind Gap](#s2-sideways-platform-allocation-and-strict-rewind-gap)

---

## S2 Sideways Platform Allocation and Strict Rewind Gap

**Status:** Engine ownership defect fixed under the user-authorized
[Sideways safe-admission and owned-child-retirement exception](known-discrepancies.md#s2-sideways-platform-safe-admission-and-owned-child-retirement).
Native corruption compatibility is deliberately omitted.

**Former symptom:** Exhausted anchored child allocation constructed a rejected,
unregistered Java child, published reciprocal links and broke strict rewind at
`SidewaysPformObjectInstance#linkedPlatform`. The earlier retained qualification
expected that failure; those historical passes were not a repair.

**Closure evidence:** The [repair qualification](../architecture/audits/2026-10-24-rewind-disposition-review.md#sideways-safe-admission-and-owned-child-retirement-2026-10-25)
records nine actual old-red pressure controls, followed by positive strict
capture/reconstruction/forward checks. The final focused selection passes 51
cases with zero failures/errors/skips, including 35 Sideways qualification cases,
malformed-link rejection and unchanged lifecycle/rewind guards. Exhaustion now
leaves a normally initialized solo parent without retry, rejected link, consumed
slot or Tails write. Admitted pairs preserve anchored dispatch and normal endpoint
remember/slot cleanup. Either-member retirement, marked-but-unswept capture,
Stageplay rejection, identity changes during admission and genuine slot reuse
are covered by the bounded ownership contract.

Closure requires those engine invariants and strict identity checks to remain
intact; it no longer requires emulation of native failed-pointer writes, fixed
Tails storage or cleared/reused-slot cross-writes. Those effects belong to the
named accepted discrepancy. Natural route frequency, complete CPZ/MCZ and native
two-player/corruption-dependent trace outcomes remain unqualified. Reopen an
engine ownership regression if these controls fail; ordinary-route dependency on
the omitted behavior calls for revisiting the explicit compatibility decision,
not silently restoring RAM emulation or weakening comparison.

---

## S2 EHZ Boss Animation Tables Contain Three Extra Frames

**Location:** `EHZBossAnimations`.

An audit against S2 World REV01 (`8bca5dcef1af3e00098666fd892dc1c2a76333f9`)
found one extra zero/frame in each of propeller animations 1 and 2, and vehicle-top
animation 4. Eight other scripts match. Owning tables are `Ani_obj56_a`
(`docs/s2disasm/s2.asm:64164-64177`) and `Ani_obj56_c` (`:64212-64227`).
This is inherited native animation timing debt, not an accepted ROM deviation.
Stageplay retains ordinary Free rendering and uses a cited held/authored fallback
for these directed appearances. Correct the native tables with matched animation
timing checks before removing this entry. The comparison and rejected aggregate
animation approach are recorded in the
[Stageplay plan](../architecture/plans/2026-10-21-stageplay-live-world-plan.md).

---

## Game Over and Continue Parity Details

**Location:** `src/main/java/com/openggf/GameLoop.java`,
`src/main/java/com/openggf/level/objects/AbstractGameOverCardObjectInstance.java`,
`src/main/java/com/openggf/game/sonic3k/objects/S3kGameOverCardObjectInstance.java`

### Symptom

The GAME OVER / TIME OVER cards and ROM-backed Continue screens now run in all
three games. Continue acceptance spends one continue, restores three lives and
clears score/rings/time before the native level reload. S1/S2 clear their
checkpoint; S3K retains it (so the checkpoint load may reinstate its saved timer)
and requests `SaveGame_LivesContinues`. Emerald inventory survives.

### Current State

Landed: the death routine's zero-life / time-over branch loads the two card objects at the ROM's fixed slots
(`v_gameovertext1/2`, `GameOver_GameText/OverText`, `Reserved_object_3` + `Dynamic_object_RAM`), plays the game
over music, queues the S1/S2 PLC and waits on it, slides the words in, waits 12 s (S1/S2) or 8 s (S3K) or A/B/C
(S3K also Start; S2/S3K poll both controllers), then either restarts the level with the saved star-post time cleared
(time over) or ends the level (game over). Zero-life gameplay is no longer pausable (`PauseGame` `Life_count` gate).

S1 title/level-select new-game startup also clears the emerald inventory and
special-stage cursor through the module lifecycle owner, matching `PlayLevel`
(`docs/s1disasm/sonic.asm:2278-2282`). Configured progress dimensions survive
module decoration. Continue and ordinary level reload retain progress; the
six-case `TestTitleNewGameSpecialStageProgress` covers these boundaries.

Remaining gaps:

- S2's shipped `fixBugs = 0` Continue path does not clear stale HTZ DMA, so the
  ROM can corrupt Tails' Continue art with a queued cloud upload. The standalone
  Continue art bank does not reproduce this VRAM alias. The retained Super flag
  and its animation-table selection are preserved separately.
- S3K `Obj_GameOver` holds on `tst.l (Nem_decomp_queue).w` until `Load_PLC_2 #3` has decompressed `ArtNem_GameOver`
  (`docs/skdisasm/sonic3k.asm:62061-62063`). The engine has no per-frame S3K Nemesis drain (the same gap
  `Sonic3kTitleCardManager` notes), so the S3K card starts sliding on its first frame rather than a few frames later.
- S3K `loc_2D638` zeroes `Collision_response_list` on every wait frame (`:62065`); the engine's per-frame list is
  rebuilt by later slots anyway and the clear is not modelled.

### Removal Condition

Remove this entry when the remaining art-queue, stale-DMA and wait-loop gaps are
resolved or explicitly accepted as intentional discrepancies.

---

## Persisted Editor Saves Disabled for S3K Gameplay Loads

**Location:** `src/main/java/com/openggf/level/LevelManager.java`,
`src/main/java/com/openggf/level/MutableLevel.java`,
`src/main/java/com/openggf/game/sonic3k/events/*`

### Symptom

Sonic 3&K levels currently skip automatic persisted editor-save application during normal gameplay level loads.
S1/S2 editor saves still apply normally. S3K editor sessions can still mutate the live editor/playtest level, but
those persisted edits are not re-applied the next time the S3K level is loaded from disk.

### Current State

This is a release safety guard. Several S3K runtime event paths still require the concrete `Sonic3kLevel` overlay
surface for PLC, pattern, chunk, and battleship/AIZ terrain swaps. Applying a persisted editor save wraps the loaded
level in `MutableLevel`; until `MutableLevel` can execute those S3K runtime overlays directly, that wrapper can disable
route-critical S3K event logic.

### Removal Condition

Remove this entry once S3K runtime overlay operations are expressed through a `MutableLevel`-safe capability or mutation
surface, and persisted S3K editor saves can be applied without disabling AIZ/CNZ/MGZ event handlers or terrain swaps.

---

## Trace Replay Recorder Coverage Follow-Up

**Location:** `src/test/java/com/openggf/tests/trace/*`, `tools/tracechaser/*`

### Symptom

BK2-derived fixture coverage now exists across Sonic 1, Sonic 2, and Sonic 3&K, but the suite is
mixed between green guard traces, known-red frontier traces and synthetic fixtures.
The full trace suite still depends on explicit frontier documentation and on
choosing the correct ordinary/deferred replay profile. Fixture presence alone
does not demonstrate a passing level slice or continuous campaign.

### Current State

V5 (`trace_schema: 5`) is the sole live contract. `TraceMetadata.load`,
`TraceFrame` and `TraceRunManifest` validate it; historical schema files are
not an accepted loading fallback. `TraceExecutionModel` still contains a
counter-absent heuristic for programmatically constructed rows, which is
distinct from supporting old on-disk fixtures. TraceChaser owns native
production. Committed per-zone suites and continuous-run chains measure
different obligations; their current results belong in
`docs/status/trace-frontier-log.md`.

Current stock verification (2026-10-08 continued swarm, canonical composition at
`45a9414233553b0fed9dd0b764c06e3dadafd44f` plus the shared-input correction):
canonical request 71207 completes 16 cases with three continuous-chain assertion
failures, zero errors/skips and all thirteen other cases passing. S1 now reaches
segment 33 instead of stopping at the MZ2 segment-12 giant ring; its earliest
remaining movement difference is MZ1 segment 7 row 3261. S2 ARZ and following
SS7 art differences close, but gap clocks and the SS7 results walk remain red.
S3K HCZ closes its remaining 488 differences and reaches the giant-ring handoff
at segment 11; AIZ differences remain unchanged. The shared-input focus passes
82 cases with zero failures/errors/skips. These are local focused/canonical
results, not a passing full campaign. Integrated `5d1ff9b` completes full ordinary
validation with 27 unchanged inherited failures, zero errors and 62 unchanged
skips; all 672 fresh guards pass. FBZ→SOZ is the sole removed failure. See the
[actual-main qualification](../architecture/audits/2026-10-07-stock-parity-gap-verification.md#actual-main-delivery-qualification)
and current [coverage follow-ups](level-test-coverage.md).

### Removal Condition

Remove this entry once documented release-required campaigns and route breadth
have completed replay evidence, and the release trace gate distinguishes green
guards from open frontiers without hidden warning-only failures. The former
pre-v3 recorder/loading description was stale at the 2026-10-07 source audit.

---

## S3K AIZ Items Carried From The 2026-03-25 Working List

**Location:** `src/main/java/com/openggf/game/sonic3k/objects/AizVineHandleLogic.java`,
`src/main/java/com/openggf/game/sonic3k/Sonic3kWaterDataProvider.java`, AIZ1 launcher object (unidentified)

### Symptom

Three observations from the retired `s3k-bug-list.md` remain **unverified since 2026-03-25**; nobody has
reproduced or refuted them against the current engine:

- **AIZ rope swing:** jumping off the rope swing for the first time was reported to activate the insta-shield
  immediately. The ROM release path sets `Status_InAir` and `Status_Roll` on the player and writes nothing to
  `double_jump_flag` inside `Obj_AIZRideVineHandle` (`docs/skdisasm/sonic3k.asm:46489+`); the engine release
  in `AizVineHandleLogic` sets air and rolling the same way. Whether the ROM also arms the insta-shield on the
  next jump press has not been checked frame-for-frame.
- **AIZ water launcher:** the object that launches the player over the water was reported to launch too short.
  The report did not name the object id.
- **AIZ1 water effect before the fire sequence:** Act 1 showed no water effect before the fire while the
  post-fire state showed the wavy effect. `Sonic3kWaterDataProvider` now carries explicit AIZ act 0 / act 1
  branches, but which state is ROM-accurate has not been confirmed visually.

Two further items from that list are resolved and are not carried: water surface sprites are rendered by
`Sonic3kWaterSurfaceManager` (`TestSonic3kInitialWaveSplashSstOwner`), and invincibility stars are
`Sonic3kInvincibilityStarsObjectInstance`. The one open item from the retired S2 `bug-list.md` — ARZ rising
pillars resetting when they leave the screen — is ROM behaviour, not a bug: `Obj2B` exits through
`MarkObjGone`, which clears the respawn bit so the pillar reappears in its initial state
(`docs/s2disasm/s2.asm:51297-51524`; `RisingPillarObjectInstance` deliberately does not mark itself remembered).

### Suspected cause

Unknown; the items predate the trace-replay suite and no trace exercises them.

### Removal condition

Remove each bullet once it is reproduced against the current engine and either fixed with a ROM citation or
shown to match the ROM. Remove the entry when no bullet remains.
