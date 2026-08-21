# LBZ frame 411: the player touch pass runs a frame late

Attribution round for `TestS3kLbzZoneSliceTraceReplay`, whose first error is

```
Totals: 4592 errors, 0 warnings.
First error: frame 411 -- y_speed mismatch (expected=0x0000, actual=-0100)
```

Measured at `f7a3ba7101f4de7882f349c592381096091710cf`. Attributed, not fixed:
the remedy is a shared-path ordering change and its cause is not yet named.

## Read this first: the physics CSV frame column is HEXADECIMAL

`StoredPhysicsFrameDomain.scan` defaults to `FrameEncoding.HEXADECIMAL`
(`src/main/java/com/openggf/trace/StoredPhysicsFrameDomain.java:38`), so the
`frame` column of `physics.csv` is base 16 and the domain is contiguous from 0.

* The row labelled `0411` is frame **1041**.
* Frame **411** is the row labelled `019B`.

The failure mode is vicious rather than merely annoying. Row `0411` of this
fixture already holds `player_y_speed=FF00` -- the engine's value. A lane that
reads that row sees expected and actual agreeing while the comparator reports a
mismatch, concludes the comparator is inverted, and goes hunting a comparator
defect that does not exist. Anyone reading a fixture by hand hits this.

The right row, `019B`, holds `player_y_speed=0000` and matches the divergence
report's ROM context exactly (`gfc=019C`, `cam=02A9,05CC`, `rings=3`, `anim=09`,
`map=86`). `FF00` first appears one row later, at `019C` = frame 412.

Aux JSONL `frame` fields are decimal and share this domain, so an aux row
`"frame":412` lines up with physics row `019C`.

## What frame 411 is

The player is idle on the ground at `(0346,062C)` while an LBZ Flybot767 dives
into him. `-0x100` is the `Touch_KillEnemy` bounce, enemy-above arm: `subi.w
#$100,y_vel(a0)`.

Both sides agree the badnik dies. They disagree by one frame about when:

| | ROM | engine |
|---|---|---|
| badnik killed, `y_vel -= $100` | frame 412 | frame 411 |

The ROM side is read out of the recording, not inferred. Aux `object_appeared`
places the Explosion (slot 7), Animal (slot 10) and Points (slot 11) all at
frame **412**, all at `0x0359,0x0619`, the same frame `player_y_speed` becomes
`FF00`.

## The badnik's trajectory is in phase

Recorded `object_state` for slot 7 (`object_code 0x0008C96C` = `Obj_Flybot767`)
matches the engine's positions frame for frame across the whole approach:
`0365,060D` @405 ... `035B,0617` @410 ... `0359,0619` @411.

So this is not a creation-frame or allocator phase error, despite having the
shape of one. The badnik reaches `y=0619` at the *end* of frame 411 on both
sides.

## What the ROM does, and why 412 is the only answer it can give

* `TouchResponse` is called from the tail of `Obj_Sonic`
  (`docs/skdisasm/sonic3k.asm:22018-22022`, `loc_10C7E`).
* `Process_Sprites` walks `Object_RAM` upward from its first slot
  (`docs/skdisasm/sonic3k.asm:35963-35995`), and that first slot is `Player_1`
  (`docs/skdisasm/sonic3k.constants.asm:303-304`).
* `Touch_Loop` stores object RAM **pointers**, not snapshots, and dereferences
  `x_pos(a1)` / `y_pos(a1)` live at Sonic's execution time
  (`docs/skdisasm/sonic3k.asm:20655-20681`).

Sonic therefore scans before any badnik's slot has run that frame, and reads
every object's **end-of-previous-frame** position. The flybot only holds `0619`
at end of 411, so the ROM's scan can first see it on 412. That is the whole
explanation of the ROM's timing, and it holds for every object, layout-placed
or dynamically allocated, with no exceptions.

## What the engine does, measured

Probes in `Flybot767BadnikInstance.updateMovement`,
`AbstractObjectInstance.snapshotTouchResponseState`, the touch-scan coordinate
selection in `ObjectTouchResponseController`, and `applyEnemyBounce`. The probe
was proved to have fired before its output was read: the build reported zero
`COMPILATION ERROR` and the run emitted 2400+ probe lines.

```
MOVE flybot -> (035B,0617)   SNAP live=(035B,0617)   PROBE used=(035B,0617) x2
MOVE flybot -> (0359,0619)   SNAP live=(0359,0619)   PROBE used=(0359,0619)
BOUNCE player=(838,1580) enemyY=0619 ySpeedBefore=0000
```

The object moves, **then** the snapshot is taken, **then** the player scan runs
against it. `getPreUpdateX/Y` equalled `getX/getY()` on every frame -- `preU ==
live` in all 2491 probe lines -- so the pre-update cache carries no information
at all on this path.

Two documented statements are therefore not met in practice:

* `ObjectManager.java:614-630` documents `refreshTouchResponseSnapshot` as
  capturing each object's end-of-previous-frame position "before any object
  updates this frame".
* `LevelFrameStep.java:299` orders `prepareTouchResponseSnapshots` ahead of
  player physics and ahead of object execution.

Both describe the ROM's phase correctly. Neither matches the observed order for
this object. **This is shared-path, not Flybot-specific**, which is what makes
it larger than one fixture.

## Eliminated

* **`Flybot767BadnikInstance.usesCurrentTouchResponseState()`.** Forced to
  `false`, rebuilt, re-run: byte-identical `4592 errors, first error frame 411`.
  Not the lever -- this Flybot is LBZAlarm-allocated (`layoutIndex < 0`) and
  already took the snapshot branch.

  **This is rule 110 in the wild.** Without checking the recompiled class
  timestamp the result would have been reported as "changed nothing, therefore
  ruled out" when the truth was "the branch never ran". A negative control that
  is never exercised is indistinguishable from a negative control that is
  exercised and inert.
* **Creation-frame / allocator phase drift on the badnik** -- engine and ROM
  positions agree exactly, frame for frame, over the whole approach.
* **Sidekick involvement** -- `sidekick_y_speed` is `0000` throughout the
  window, and the failing field is the unprefixed player one
  (`TraceBinder.java:217`).
* **Comparator inversion** -- fully explained by the hexadecimal frame domain
  above.

## Not established

* **Why the touch pass runs after the object pass for this object.**
  `LevelFrameStep.java:299` declares the opposite order under
  `inlineSolidResolution`. Either this S3K path takes a different branch, or the
  Flybot executes outside the main exec loop (LBZAlarm-driven, or via
  `flushPostExecDynamicSpawns`). Establishing which is the next round, and it
  must be answered before any remedy: a shared-path ordering change has a very
  large blast radius.
* **Whether the `usesCurrentTouchResponseState()` opt-in is wrong for the other
  ~20 S3K objects that override it.** Its justifying comment says a retained
  slot exposes a live SST coordinate to the touch list; per the ROM reading
  above that looks wrong, because `Touch_Loop` dereferences the pointer at
  Sonic's slot and no object can legitimately expose a same-frame post-move
  position. Held as a hypothesis and deliberately not swept: one inert test is
  not evidence about twenty.

## A reporting gap that hides a data gap

`metadata.json` for `src/test/resources/traces/s3k/lbz_completerun` advertises
`collision_response_list_per_frame`, `collision_response_list_end_of_frame` and
`velocity_write_per_frame` in `aux_schema_extras`. The fixture contains **zero**
rows for all three.

Worse, the divergence report's own "Missing advertised aux schemas" line does
not list `collision_response_list` at all -- it names cage, velocity_write,
position_write, sonic_record_pos, tails_cpu_normal_step and cnz. So that
particular gap is silently unreported, and a reader trusting the line concludes
the stream is present.

A `velocity_write` stream would have named this defect in one grep, by pointing
at the PC that wrote `-0x100`. Both the missing streams and the incomplete
missing-schema report are worth someone's round.
