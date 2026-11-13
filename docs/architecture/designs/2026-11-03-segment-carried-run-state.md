# Segment carried run state (2026-11-03)

**Status:** implemented and qualified in wave 8; final composition is recorded below.
**Owner:** `com.openggf.trace.replay.TraceSegmentCarriedStateImport`.
**Supersedes:** the "Run-Level Progression Is Absent" consequence and
known-discrepancies §30 (Super Emerald special-stage identity).

## Ruling

User ruling, 2026-11-03, verbatim: "State can be carried from a previous
segment into a new one. It's basically required for sync. State must not be
invented mid-run, and this is invalid according to our invariants."

A standalone run segment therefore starts with the run state the ROM carried in
from the previous segment. That state is imported once, at segment entry,
through one named mechanism, from recorder observations. Nothing is written into
engine gameplay after entry, and no value is hand-authored for a fixture.

## Mechanism

`TraceSegmentCarriedStateImport` has two halves:

- `resolve(...)` turns recorder observations into an immutable
  `TraceSegmentCarriedState`. Unestablished variables stay `null` and are listed
  in `unresolved()` with the reason; nothing is defaulted.
- `importAtSegmentEntry(trace, player)` writes it into the engine owners the ROM's
  own restore writes. It is called once, from
  `TraceReplaySessionBootstrap.applyStartPositionAndGroundSnap`, the cold
  declared-position entry. That is the analogue of `Load_Starpost_Settings`
  (loc_2D2C2, sonic3k.asm:61835-61856) and the `SpawnLevelMainSprites` power-up
  restore (loc_69E0, sonic3k.asm:8311-8355), before camera, level events and the
  first row. `importAtSpecialStageEntry(carried)` is the special-stage entry. It
  imports the emerald state and returns the stage that `sub_85B0` selects.

Run-chain walks never call it. A continuation segment's state lives in the
engine that played the previous segment.

The selected restore block's camera maximum must reach camera initialization
before its first snap. A special-stage return can use `Saved2_*` while the
imported death checkpoint retains different `Saved_*` bounds; the latter must
not clamp the current return. `initializeCameraAtEntry` supplies that bound
through the same entry mechanism. The checkpoint remains available unchanged
for a later death, and the post-initialization import still restores the live
bounds, event routine and water after event setup.

The run is found from the segment's own metadata (`run_id`, `segment_index`):
`run_manifest.json` beside the segment directory, or `runs/<run_id>/` for a
standalone copy. The manifest segment must agree on `bk2_frame_offset` and
`trace_frame_count`.

## Imported variables and their recorded source

| ROM variable | Engine owner | Recorded source |
|---|---|---|
| `Ring_count` | `LevelGamestate.setRings` | inbound transition: `rings_after` on a `stage_exit` return, `rings_before` into a bonus stage. A fresh zone has no inbound record; the ROM clears rings there (loc_63B4) and nothing is imported |
| `Chaos_emerald_count` | `GameStateManager` emerald states | `emeralds_after`/`emeralds_before` of the latest transition at or before the segment (level play cannot change it) |
| `Collected_emeralds_array` (0-3 per emerald), `Super_emerald_count` | `GameStateManager.restoreS3kEmeraldProgress` | folded by `S3kRunEmeraldLedger` over the manifest's earlier special stages (see below) |
| `Collected_special_ring_array` | `GameStateManager.markSpecialRingCollected` | giant-ring touches recorded anywhere in earlier segments of this zone visit, confirmed against the loaded level's giant-ring placements |
| Player 1 shield (`status_secondary` fire/lightning/bubble) | `AbstractPlayableSprite.giveShield` | this segment's recorded entry row (`interact_state`, row 0) |
| `top_solid_bit`/`lrb_solid_bit` | `setTopSolidBit`/`setLrbSolidBit` | this segment's recorded entry row (`state_snapshot`, row 0); moved here from `seedSegmentEntrySolidBits` |
| `Current_special_stage`, `SK_special_stage_flag` (special-stage segments) | stage selection | recorded `special_stage_index` (sampled at the arm, before the search); the set from the ledger |

### Why row 0 for the shield and path bits

No transition record samples `Saved_status_secondary`, `Saved2_status_secondary`
or the saved solid bits. The previous segment's final row is not the restored
value either. Bonus-stage prizes write `Saved_status_secondary` directly
(sonic3k.asm:127993-128002). Measured: `s3k-multibonus` `aiz_2` enters with
`status_secondary 0x21` while `gumball`'s final row reads `0x00`. The level load
restores these values before the first `LevelLoop` iteration. Frame 0 changes
them only by a monitor or a hit on that frame. Row 0 is therefore this
segment's recorded entry state. Only the three elemental bits that loc_69E0 masks
are taken.

### `Collected_emeralds_array` ledger

`S3kRunEmeraldLedger.fold`, owned by `game.sonic3k.specialstage` alongside the
S3K special-stage trace reader, applies only ROM rules to recorder observations, in
manifest order:

1. **Layout set.** A giant ring writes `SK_special_stage_flag` 0 (loc_61892), and
   the Hidden Palace pedestal writes 1 (loc_90926). A giant ring never opens a
   special stage once seven Chaos Emeralds are held. On S3 levels `loc_6170A`
   pays 50 rings; on S&K levels `SSEntryFlash_GoSS` routes to the shrine
   (loc_618AC). So a stage armed with recorded `emeralds_before == 7` is the
   Super set. The demo and level-select writers (sonic3k.asm:5734-5742,
   10138-10166) are not gameplay and cannot occur inside a recorded campaign.
2. **Placement.** The first shrine visit places every Chaos Emerald (1 → 2,
   loc_90A94/loc_90ED0) before a pedestal can arm, so the first Super arm implies
   all seven are placed.
3. **Selection.** `S3kSpecialStageSelection.selectStage` is `sub_85B0` loc_85E4 to
   loc_8630 (sonic3k.asm:10861-10895), applied to the recorded cursor.
4. **Outcome.** The stage's final recorded `clear_routine` is non-zero only after
   the clear path (loc_9CE6). loc_9CCE then sets bit 0 of the played entry unless
   that set's counter is already 7. For the Chaos set the manifest's
   `emeralds_after` must agree; a disagreement marks the ledger inconsistent, and
   no emerald state is imported.

Checked by `TestTraceSegmentCarriedStateResolution`. The tests cover Tails
`ss_8`, which resolves to stage 4 because `ss_5` failed. They cover S+T
`ss_13`, armed on pedestal 6 with the array `3,3,3,3,3,2,2`, and the shrine entry
before any Super stage (`1` × 7).

### Giant-ring memory

A touch leaves a signature an idle ring cannot. `SSEntryFlash_Init` copies the
ring's position into `Obj_SSEntryFlash`, which is allocated in another SST slot.
For a few frames two slots therefore hold the ring's position and subtype on
the same frame, until the flash deletes the ring at its fourth frame. The
flash's subtype is the bit `loc_618D0` sets. Each earlier segment of the zone
visit is scanned in full; there is no row window.

The first version required only "two object routines at one position" in a
segment's last 256 rows. That window was a fitted search constant, and the
weaker test is not exact over a whole segment. `SSEntryRing_Display` deletes a
ring through `Go_Delete_SpriteSlotted` both when it was consumed and when it
went off screen (`loc_61928` → `loc_6196A`). So an off-screen ring also shows
two routines at its position, and so does the 50-ring award (`loc_61794`). The
full-segment measurement found 41 such non-touch positions (for example S+T `fbz`
×4, `lrz` ×3, `cnz` ×3, Knuckles `icz` ×3). The two-slots-on-one-frame
signature, restricted to giant-ring positions, occurs exactly once per segment
that entered a special stage or the shrine through a giant ring, 49 segments
across the five runs. Every occurrence carries the flash, at that segment's
end, and there are none elsewhere.

Rings spent for 50 rings (`loc_61794`) also set their bit but create no flash,
so no recording establishes them. The TraceChaser export of
`Collected_special_ring_array` at each boundary (below) would cover them.

The zone visit runs back through special and bonus stages and the shrine
excursions. A level of another zone that the run left by advancing to the next
level ends it: the act-2 results' full `SaveGame` clears the array
(loc_2DCB6 → loc_C4CC).

## Special stages

`S3kSpecialStageReplayHarness` no longer defaults to the Chaos set. It calls
`importAtSpecialStageEntry(carried)` and initialises
`Sonic3kSpecialStageProvider.initializeStage(stage, FAST, reward)` with the
stage and set that `sub_85B0` chooses from the carried state. No recorder export
was added.

## Reviewed row-0 imports

| import | verdict |
|---|---|
| `applyBonusStageEntry` rings (row 0) | **Moved.** Ring_count survives the bonus reload, so it is carried. The source is now the manifest's `rings_before`, written by the import; the bonus entry only hands the imported count to the provider as its saved count. Rings import only on S3K returns and bonus entries: S2 manifests record `rings_before`/`rings_after` around `level_advance`, which the S2 reload clears. A first draft imported them, and three green S2 standalone segments went red until the import was gated to S3K. |
| `seedSegmentEntrySolidBits` (row-0 path bits) | **Moved** under the import. These are carried `Saved_solid_bits`/`Saved2_solid_bits`. |
| `seedSegmentEntryVelocity` (row-0 angle, `anim`, `prev_anim`, `mapping_frame`) | **Removed.** These are not carried state. `Level` clears `Object_RAM` on every load (loc_60DE, sonic3k.asm:7649-7651), so row 0 holds what `SpawnLevelMainSprites` and frame 0 compute. The method's comment claimed they "survive the load"; the clear contradicts it. Measured: with the seed removed, the full `-Ptrace-replay-r7` and `-Ptrace-segments` profiles were identical in every class (status, error count, first error) to the same tree with the seed kept. The engine already computes these values. |

## Recorded entry RAM (`level_entry_ram`, 2026-11-04)

TraceChaser a9f8b94 (branch `bugfix/ai-trace-saved-block-export`, `d89e30b8d`)
adds `level_entry_ram` to every S3K level and bonus segment's metadata. It is
sampled on the same first PreLevel frame as `last_star_post_hit` and holds the
full `Saved_*` and `Saved2_*` blocks plus the carried counters. Special-stage
segments do not carry it.

The live counters are sampled before the level's own initialisation. On a
fresh, start-table entry, `Ring_count` and `Timer` still hold the previous
zone's values, which loc_63B4 then clears, so they are not imported. Only
what the ROM itself carries is used:

| ROM variable | Engine owner | Source |
|---|---|---|
| `Ring_count`, `Extra_life_flags`, `Timer` on a restore entry | `LevelGamestate` | `Load_Starpost_Settings`: the `Saved2_*` block when `special_bonus_entry_flag` is set (loc_2D2C2), otherwise `Saved_*`. The `Saved_*` rings and extra-life flags are zeroed without `respawn_table_keep`; the timer is reloaded with `Timer_frame` 59 and the second stepped back one |
| `Ring_count` in a bonus stage | `LevelGamestate` | live `ring_count` (the bonus reload keeps it) |
| path bits on a restore entry | Player 1 | the restore block's `solid_bits` |
| `Camera_max_Y_pos` / `Camera_target_max_Y_pos` | `Camera.setMaxY` / `setMaxYTarget` | restore block |
| `Dynamic_resize_routine` | `restoreEventRoutineState` (the big-ring return's owner) | restore block |
| `Mean_water_level`, `Water_full_screen_flag` (water levels only) | `WaterSystem` | restore block |
| `Last_star_post_hit` checkpoint | `CheckpointState.restoreFromSaved` (index, `Saved_X/Y_pos`, `Saved_camera_X/Y_pos`), activation mark, saved rings/timer/bounds/path bits | `last_star_post_hit` & `$7F`; `Saved_*` block |
| `Life_count` | `GameStateManager` (via `NativeGameStateOps`) | live `life_count` |
| `Collected_emeralds_array`, `Chaos_emerald_count`, `Super_emerald_count` | `GameStateManager` | live counters (replace the ledger) |
| `Collected_special_ring_array`, `Current_special_stage` | `GameStateManager` | live counters (replace the touch derivation) |

Bounds, resize routine and water are written after the engine's camera and
level-event initialisation, as `Load_Starpost_Settings` follows
`Get_LevelSizeStart`. Everything else is written at the existing entry point.
Both writes are in the one declared-position entry step, before row 0.

Water restoration writes the saved mean and full-screen flag only.
`Load_Starpost_Settings` (sonic3k.asm:61800-61856) leaves
`Target_water_level` and `Water_speed` as initialized by the destination.
The imported mean must not become a synthetic target. The ROM-backed
`TestTraceSegmentCarriedWaterRestore` uses the recorded HCZ Saved2 entry:
its mean is 1664 while the fresh target is 1280. It fails on the old target
write and passes when only the mean is restored. Continuous bonus returns
use the same mean-only rule through their production owner.

Not imported: `Saved*_camera_X/Y_pos` for the entry camera itself (owned by the
policy-B placement), `Saved*_art_tile` (priority bit) and the zone/act words.

### Derived versus recorded

- **Emerald array and counts, and ring counts:** every recorded S3K level and
  bonus segment agrees with the ledger and the manifest
  (`recordedEntryRamAgreesWithEveryDerivedValue`).
- **`Collected_special_ring_array`:** the touch derivation never set a bit the
  ROM didn't. It agrees on 117 of 146 recorded level segments and misses bits
  on 29, for two reasons:
  - the zone-visit boundary was wrong for excursions. The array survives into
    the Hidden Palace shrine (`dez23*`) and the LRZ boss act (`hpz22*`, zone
    $16), because no full `SaveGame` runs on those transitions.
  - rings spent for 50 rings (loc_61794) set bits without a flash: S+T `icz_2`,
    `soz_2`; Knuckles `lbz_2`; Tails `mgz_3`, `lrz_3`.
  The recorded value is now imported. The derivation remains only for fixtures
  without `level_entry_ram`.

## `Last_star_post_hit`

A segment that returns from a star-post bonus stage re-enters standing on the
star post it used. The ROM carries `Last_star_post_hit` across the return: the
bonus exit restores it from `Saved_last_star_post_hit` (loc_4A024,
sonic3k.asm:96716-96726), and a special-stage return sets bit 7 (loc_9D02,
sonic3k.asm:12708-12712). Each star post's init compares its subtype with the
byte and starts already hit when the byte is not lower (loc_2CFC0 to loc_2D008,
sonic3k.asm:61613-61633).

Source: `metadata.last_star_post_hit`, the raw `$FFFE2A` byte TraceChaser
d98a2aa latches at the first PreLevel frame of the segment's load. The policy-B
branch's re-recording provides it. The import sets the checkpoint's activation
mark to the byte's low seven bits; the engine's star-post init already models
loc_2D008 through that mark. A zero byte (start-table entry) sets nothing. The
manifest's `starpost_bonus` `last_star_post_hit` cannot stand in. It is sampled
after loc_2D4CA zeroes the byte (sonic3k.asm:61959-61964), and it reads 0 on
every such transition.

Before this was carried, a star-post bonus return re-entered on an un-hit post.
With the carried rings, the post spawned bonus stars on frame 0 where the ROM
spawns none. That aborted Tails `lrz_3` at 2445 (KosM FIFO full) and gave
Knuckles `lbz_2` row-0 queue errors. Measured after carrying the byte: `lrz_3`
is a failure with no abort, 1152 errors from row 73. `lbz_2` has 6 errors
(ring timing) through row 1085, then aborts at 1086 on a separate defect: the
`LbzFinalBoss1Instance$MuzzleLaserChild#head` rewind reference to an
unregistered `LaserHeadChild`. The replay now reaches that boss state.

### The engine's `Last_star_post_hit` (`lastCheckpointIndex`)

Other ROM readers test the byte directly. SSZ ScreenInit (`tst.b
(Last_star_post_hit).w`) is the measured case, modelled in the engine as
`CheckpointState.lastCheckpointIndex`. When only the activation mark was
carried, SSZ1 star-post returns took the fresh-arrival path
(`SszArrivalControllerObjectInstance`), and S+T `hpz_2`/`hpz_3` fell to row-0
first errors. With `level_entry_ram` the import restores the full checkpoint:
the index, the `Saved_*` position and camera pair, and the saved rings, timer,
bounds and path bits. A death reload then restores the ROM's saved state.

## Entry provenance (policy B)

The entry hook takes no position on whether a segment is a fresh start or a
return. Policy B can establish the engine's return state before
`importAtSegmentEntry` runs. The import then writes the same values the engine's
return path would otherwise restore from its saved block.

### Native continuation bank: observed separately from live counters

`start_table` describes player placement, not whether a load carries native
continuation state. LRZ boss entry loc_63C14 (sonic3k.asm:131250-131259) and
DEZ boss entry loc_7F310 (sonic3k.asm:169871-169879) set `Act3_flag` and copy
live rings/time to a separate bank. The title loop tests the flag at loc_62B6
(sonic3k.asm:7762-7767), clears it at loc_62FE (sonic3k.asm:7782-7783), and
loc_63B4 (sonic3k.asm:7826-7833) clears live counters on a zero-star-post entry.
The first screen event consumes nonzero bank values at loc_59B1C
(sonic3k.asm:119410-119424) or loc_5A49A (sonic3k.asm:120364-120378).
These operations have no `FixBugs` conditional; the shipped `FixBugs=0` path
remains the model.

The generic first-PreLevel `level_entry_ram` export now includes the native
byte `act3_flag` at `$FFEE5E`, word `act3_ring_count` at `$FFEF5E`, and long
`act3_timer` at `$FFEF60`. Encoded stores/reads in the pinned locked-on ROM
verify the addresses and widths. This is the existing latch, before the flag
clear and bank consumption, not a sample inferred from a later row. Schema v5
still owns the contract; recorder provenance remains opaque.

Both original Sonic+Tails and Tails complete-chain BK2s were re-recorded with
the native BizHawk2.11 harness, without movie edits, state writes or bounds.
Whole-file audits establish identical logical physics, aux, timing and manifest
contents. Native metadata adds these three fields and the recording date;
deterministic gzip normalization reproduces the old compressed payload bytes.
The native observations are:

| Entry | Flag | Act3 rings | Act3 timer (raw long) |
| --- | ---: | ---: | ---: |
| Sonic+Tails `hpz22` | 255 | 233 | 202496 |
| Tails `hpz22` | 255 | 127 | 199453 |
| Sonic+Tails `dez23_8` | 255 | 163 | 335625 |
| Tails `dez23_8` | 255 | 86 | 264719 |

The later `hpz22_2` checkpoint entries observe zero flag/bank and continue to
restore Saved rings335/68. Ordinary fresh MGZ observes zero flag/bank too;
its nonzero preload live counters remain ineligible. Saved2 special-stage
returns retain their independent saved bounds, water and emerald identity.
No native same-destination fresh HPZ movie was manufactured: ROM-backed fresh
entry and zero-flag resolver tests protect that boundary separately.

`TraceSegmentCarriedState.nativeContinuation` resolves only a complete bank
with an observed nonzero byte flag and valid widths/timer bytes. Missing or
invalid observations remain unresolved; zero flag carries no bank. Neither
live counters, predecessor physics rows nor destination identity can fill a
gap. Timer conversion preserves the exact bank, without Saved's second
decrement or forced frame59.

The existing entry importer installs this bank once into
`LevelContinuationCarry` at the already-loaded standalone entry. It submits no
second load and does not write live counters. The ordinary destination screen
event remains the sole one-shot consumer, and the existing transition snapshot
captures pending and consumed states for rewind. This does not repair or certify
earlier cold-load/title scheduling; continuous production transitions retain
their existing request/load/title-bypass owner. No trace state is applied after
entry. The protected bootstrap and seven fresh-computed player fields are
unchanged. Focused tests cover complete/absent/invalid/zero banks, same-destination
Saved returns, Saved2 water, one-shot consumption, rewind and the four S3K
keepgreens. Trace comparisons remain bounded and the larger routes remain red.

Player velocity, position fractions, animation and routine fields remain
outside this bank. Level clears player object RAM at loc_60DE
(sonic3k.asm:7649-7653). For example, the Tails ICZ intro writes its own `$0480`
Y velocity at Obj_LevelIntroICZ1Tails (sonic3k.asm:77367-77388), and the ending
director writes `$0800` ground velocity at loc_5D8BC
(sonic3k.asm:123673-123692). Row-0 differences in those fields require their
production initialization/dispatch investigation, not additional carried-state
imports. The recorded `ddz` ending is zone `$0D`, act 2; zone `$0C` Doomsday is
a separate regression control.


## Wave 8 final qualification — 2026-11-04

Runtime `a98857cdb` composes the carried camera/saved-block implementation with
current develop `663b86216`. DDZ's strict recorded-entry oracle is green;
HCZ2 advances from 3422 errors at row 0 to 26 at row 6640. These are fixture
comparisons, not whole-zone or expanded-route certificates.

The still-aborted Sonic+Tails `aiz_2` trace changes 191 to 260 groups but reduces
actual ERROR tuples 22924 to 21803. Fresh baseline/candidate controls reproduce
the complete reports and row-996 closure. Identical observers capture all 996
common rows and 45816 fields per arm, with no expected-value or coverage changes.
There are 6545 added and 7666 removed tuples, with zero additions on a baseline
error-free/all-MATCH row. The baseline already has an ERROR on every captured
row; the candidate has 84 error-free rows. First added row 256 camera Y is ROM
`$042C`, baseline `$0390`, candidate `$0430`, after the candidate's row-84
frontier. Full P1/P2/camera snapshots and raw tuples are preserved outside Git.
No observation of row 996 or its suffix is invented, and the unregistered
Knuckles reference is not repaired by this entry-state work.

The temporary observer uses the existing Jackson serializer on completed
`FrameComparison` records and closes each gzip member before returning.
Unmodified and observed report/body controls match, allowing only separately
evidenced Java identity-hash/source-line differences. All instrumentation is
restored. One earlier unavailable-Gson compilation failed before tests; it is
preserved, not counted as engine evidence. Exact sources, commands, patches,
raw gzip streams and gate results are under
`/tmp/wave8-aiz2-exact-row-gate-20261104/`.

Full current-base trace profiles, inherited ordinary failures, source-qualified
skip accounting and remaining limits are in the
[wave 8 frontier record](../../status/trace-frontier-log.md).

## S2-family destination rings (2026-11-06)

The same entry import consumes `rings_after` on an S2-family `stage_exit`.
The manifest records the destination count: stock S2 returns clear it to zero,
while the lock-on run retains it. No team/fixture name chooses a value. S1
result-screen counts are excluded because its next level clears them. Missing
records stay unresolved. This changes no continuous-chain state or later row.
The resolver test covers the stock zero and lock-on retained record explicitly.
