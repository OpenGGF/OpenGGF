# Timing-constant provenance sweep

Point-in-time audit, 2026-08-21, against `origin/develop` `3efd63568`. **Read-only — nothing
was changed.** Scope: engine constants in animation and timer code whose provenance is not
traceable to a disassembly line.

Every figure below is marked **verified** (checked against the disassembly this round),
**confessed** (the code's own comment admits approximation), or **unverified** (flagged by
shape, not yet checked).

## Method, and the filter that does not work

2381 declarations across `src/main/java/com/openggf` carry a timing or animation name; 1891 have
no disassembly citation nearby. That is far too many to act on, and most of it is noise.

**Round decimals and `N * 60` are NOT a tell in this codebase.** The ROM uses second-scaled
values itself:

| engine constant | ROM |
|---|---|
| `Sonic3kSpecialStageConstants.TAILS_CPU_IDLE_TIMEOUT = 600` | `move.w #600,(Tails_CPU_idle_timer).w` (`sonic3k.asm:11721`) — **verified** |
| `Sonic2Constants.SUPER_SONIC_RING_DRAIN_INTERVAL = 60` | `move.w #60,(Super_Sonic_frame_count).w` (`s2.asm:37512`) — **verified** |
| `IczEndBossInstance.DEFEAT_CAPSULE_HANDOFF_WAIT = (2*60)-1` | the `#(2*60)-1` idiom, **21 occurrences** in `sonic3k.asm` — **verified** |

A sweep keyed on "looks like seconds" flags ~37 constants of which at least three are
ROM-exact. Those three are **documentation gaps, not defects**. Anyone repeating this sweep
should not re-flag them.

**S1/S2 scalar animation delays are also not a tell.** Their scripts are
`dc.b duration, frame, frame, …` — one duration for the whole script — so a scalar
`ANIM_SPEED` is the ROM's own shape. Only **S3K** raw scripts use per-entry `(frame, delay)`
pairs, so only there does a scalar stand in for a script.

## Ranked findings

> **Correction, same day, after checking rank 1.** `FLAME_DURATION = 40` is **ROM-exact** —
> see "Rank 1 resolved" below. A comment admitting approximation is evidence about the
> author's confidence, **not about the value**. Rank 1 should be read as "worth checking
> first", not "likely wrong", and `MESSAGE_FLYOUT_FRAMES` must not be assumed wrong either.

### 1. Confessed approximations — highest confidence

| where | constant | note |
|---|---|---|
| `AizEndBossFlameChild.java:52` | `FLAME_DURATION = 40` | comment: *"Approximate flame animation duration"* |
| `Sonic2SpecialStageConstants.java:257` | `MESSAGE_FLYOUT_FRAMES = 15` | comment: *"(approximate)"* |

`FLAME_DURATION` is the clearest case in the sweep and its ROM structure is **verified**:
`AIZEndBossFlame_Init` (`sonic3k.asm:138579-138591`) selects
`AniRaw_AIZEndBossFlame_Diagonal` / `_Vertical` by angle into `$30(a0)` and sets
`$34 = AIZEndBossFlame_SpawnBomb`; `AIZEndBossFlame_Main` runs `Animate_Raw`, and the script's
terminator invokes `$34`. **The flame's duration is script-terminated by a callback, exactly
like the emerge** — there is no scalar duration in the ROM to be approximate about. The
engine's 40 is a stand-in for a script.

### 2. Presentation durations expressed in seconds, no ROM value — unverified

| where | constant |
|---|---|
| `Sonic3kSpecialStageConstants.java:63` | `RATE_TIMER_NORMAL = 30 * 60` |
| `Sonic3kSpecialStageConstants.java:65` | `RATE_TIMER_BLUE_SPHERES = 45 * 60` |
| `Sonic3kSpecialStageConstants.java:196` | `BANNER_DISPLAY_FRAMES = 3 * 60` |
| `Sonic3kTitleScreenManager.java:133` | `SEGA_HOLD_DURATION = 180` (*"~3 seconds"*) |
| `S3kResultsScreenObjectInstance.java:64-65` | `S3K_PRE_TALLY_DELAY = 360`, `S3K_WAIT_DURATION = 90` |

Given the verified table above, these may still be ROM-exact. They are ranked here because the
*rationale in the comment is a wall-clock duration* rather than a ROM read — the tell recorded
previously for invented presentation durations. Each needs one disassembly check.

### 3. A script-entry count used as a delay — unverified

`AizMinibossInstance.java:73`, `RESULTS_POST_CONTROL_HANDOFF_DELAY_ENTRIES = 13`. The name says
it counts script *entries* while it is used as a delay; its neighbour
`DEFEAT_WAIT_FADE_TIMER = 0x3F` is ROM-shaped. This is the same shape as the AIZ end boss's
emerge, whose 13-entry script the engine also flattened to a frame count.

### 4. S3K scalar animation delays in files that carry frame arrays — unverified, 74 of them

Listed in full in the sweep output; the population is real but unranked within itself. Highest
prior: children of bosses and cutscene actors, where the ROM consistently uses
`AniRaw_*` scripts with `$34` terminators — `AizEndBossFlameChild`, `HczEndBossBlade`,
`HczEndBossGeyserCutscene`, `MgzMinibossInstance`, `CutsceneKnuckles*`.

## Cross-reference to the parked AIZ2 capsule seam

`IczEndBossInstance` carries `DEFEAT_CAPSULE_HANDOFF_WAIT = (2*60)-1` — the exact ROM
expression that `AizEndBossInstance`'s post-defeat wait lacks, where it instead has the
uncited `0x7F`. A sibling port having the ROM idiom is independent support for that seam's
diagnosis, arrived at from a different direction.

## Not done, deliberately

Nothing was fixed. Ranks 2, 3 and 4 are shape-flagged and need one disassembly check each
before anyone acts on them; rank 1's `FLAME_DURATION` is the only entry whose ROM structure was
established this round.

## Rank 1 resolved: `FLAME_DURATION = 40` is ROM-exact

Checked in full. `AIZEndBossFlame_Main` steps the flame's script with `Animate_Raw`
(`sonic3k.asm:138606-138611`), which is the **shared-delay** form `Animate_RawNoSST`
(`:177333-177352`): the script's first byte is one delay for the whole script, the rest is a
flat frame list, and `anim_frame` advances by **one byte** per step.

Both scripts — `AniRaw_AIZEndBossFlame_Diagonal` and `_Vertical` (`:139123-139168`) — open with
a delay byte of `0`, so each entry lasts one frame, and each carries exactly **20 pairs = 40
frame bytes** before its `$F4` terminator, which invokes `$34` = `AIZEndBossFlame_SpawnBomb`.

**40 is the script's own length.** No behavioural change is warranted; the comment was
corrected to cite the script and the value left alone.

### What this changes about the sweep's method

The flagship of rank 1 was flagged on the strength of the word *"Approximate"* in its own
comment, and the value it apologises for is exact. So:

- **A confessed approximation is a claim about the author's confidence at the time, not about
  the number.** It marks a constant as unverified, which is worth ranking first — but it is not
  evidence of a defect, and a round that treats it as one will "fix" correct values.
- The remaining rank 1 entry, `Sonic2SpecialStageConstants.MESSAGE_FLYOUT_FRAMES = 15`, carries
  no verdict from this round and must not inherit one.

That is the same trap as the "looks like seconds" filter recorded above, arriving from the
opposite direction: there the shape suggested fitted and the values were ROM-exact; here the
comment suggested fitted and the value was ROM-exact.

## Cross-reference for the parked AIZ2 capsule seam

Recorded here because it was found without reference to that seam and is easy to lose:
`IczEndBossInstance.DEFEAT_CAPSULE_HANDOFF_WAIT = (2*60)-1` is the exact ROM expression
`AizEndBossInstance`'s post-defeat wait lacks, where it instead carries an uncited `0x7F`. The
`#(2*60)-1` idiom appears **21 times** in `sonic3k.asm`. A sibling port holding the ROM idiom
that the seam's own port is missing is independent support for that diagnosis.
