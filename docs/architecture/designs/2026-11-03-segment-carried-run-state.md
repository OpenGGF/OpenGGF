# Segment carried run state (2026-11-03)

**Status:** implemented on `bugfix/ai-trace-carried-segment-state`.
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
| `Collected_special_ring_array` | `GameStateManager.markSpecialRingCollected` | giant-ring touches recorded in the last 256 rows of earlier segments of this zone visit, confirmed against the loaded level's giant-ring placements |
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

`S3kRunEmeraldLedger.fold` applies only ROM rules to recorder observations, in
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
ring's position into `Obj_SSEntryFlash`, so two object routines occupy the same
position and subtype until the flash deletes the ring. The flash's subtype is the
bit loc_618D0 sets. Measured over every S3K run segment, the signature occurs on
exactly the giant-ring-ended segments, including the S&K giant rings that lead to
the shrine (subtype `$81`/`$82`) and have no manifest transition of their own.
The zone visit runs back through special and bonus stages and the shrine
excursions. A level of another zone that the run left by advancing to the next
level ends the visit: the act-2 results' full `SaveGame` clears the array
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

## Not recorded: TraceChaser export needed

These carried words are not in any recorder output. The export would add them to
each manifest transition at the mode change (`run_state`), or to each segment's
metadata at arm time:

- `Saved2_mean_water_level` / `Saved_mean_water_level`,
  `Saved2_water_full_screen_flag`. These are the water-start fixtures
  (`aiz_5`, `hcz_2`).
- `Saved2_camera_max_Y_pos` and `Saved2_dynamic_resize_routine` (the AIZ1 return
  below the default max Y).
- `Collected_special_ring_array` (`$FF92`, long) at each boundary. This would
  replace the touch-signature read, and it would also cover rings spent for 50
  rings with seven emeralds (loc_61794) earlier in the segment.
- `Extra_life_flags`, `Life_count` and `Timer` (not compared today).
- Optionally `Collected_emeralds_array` (`$FFB2-$FFB8`), `Super_emerald_count`
  (`$FFB1`) and `SK_special_stage_flag` (`$FFBB`), to cross-check the ledger. The
  ledger is exact for recorded gameplay and needs none of them.

## Measured interaction: `Last_star_post_hit` (needs policy B)

A segment that returns from a star-post bonus stage re-enters standing on the
star post it used. The ROM carries `Last_star_post_hit` across the return: the
bonus exit restores it from `Saved_last_star_post_hit` (loc_4A024, sonic3k.asm:96716-96726). So
that star post is already hit and `sub_2D028` (sonic3k.asm:61646-61651) skips
it. The engine's cold entry has no star-post state, so with the carried ring
count (20 or more) the post activates on frame 0 and spawns bonus stars and
their KosM art. The ROM spawns neither. The variable is entry provenance. The
provenance lane plans to export it (`Last_star_post_hit` `$FFFE2A` at
`loc_1BE46`), so it is not imported here. Measured consequences on this branch:
Knuckles `lbz_2` (Big Arm) adds row-0 `queue.s3k_kos_*` errors and aborts at 1086
on an LBZ boss rewind-closure defect. Tails `lrz_3` aborts at row 2445 with the
KosM FIFO full. Both were red before, and both keep their first error at row 0.

## Entry provenance (policy B)

The entry hook takes no position on whether a segment is a fresh start or a
return. Policy B can establish the engine's return state before
`importAtSegmentEntry` runs. The import then writes the same values the engine's
return path would otherwise restore from its saved block.
