# S3K Lava Reef boss act coverage matrix

Game / canonical zone / act: S3K `S3K_LRZ_BOSS`, engine zone `$16` act index 0,
ROM `Current_zone_and_act = $1600`, SKL object set. The act shows the Lava Reef title card
on a level-select load and none on the Act 2 handover (`Act3_flag`, `loc_62B6`).
Character route: Sonic + Tails and Tails alone only — Knuckles never enters `$1600`.
Flash sequence, autoscroll, end boss, capsule and `Obj_StartNewLevel $2D` at `($FE8,$5E0)` to
Hidden Palace `$1601`. Owning plan: [LRZ bring-up](../../plans/2026-09-17-lrz-bring-up.md).
Status: fresh-load carry/title suppression, screen stages and autoscroll implemented;
platform generation and lava presentation are implemented; the flash/controller
graph remains open; the background stage owner and end-boss candidate are under
validation (see the in-progress evidence below).

Incoming: LRZ2 `loc_63C14` with the Act 3 carry (`Act3_flag`, `Act3_ring_count`, `Act3_timer`,
`Saved2_status_secondary`), level select `$1600`, star-post respawn (`LRZ3_ScreenInit` P1 X >=
`$480`). Outgoing: `$1601`, which closes the Hidden Palace entry dependency recorded in the
[HPZ matrix](s3k-hpz-act.md).

Widths / donors / characters / teams (support authority `LaunchProfile.sanitizedFor`,
same roster as HPZ and DDZ): widths 320/352/400/528/800; supported character/donor pairs
off x {Sonic, Tails, Knuckles}, S1 x Sonic, S2 x {Sonic, Tails}; teams: solo, Sonic+Tails,
S1 Sonic+Sonic duplicate, S2 Sonic+Tails, Sonic+Tails+Knuckles at 800.

Five claims are tracked separately and never aggregated: **implemented**, **cold-reachable**,
**rewind-verified**, **native behaviour matched**, **visually matched**. Nothing below certifies
the act.

Placement baseline (`TestS3kLrzPlacementCensus`): 35 placed objects, of which **1 still builds a
`PlaceholderObjectInstance`** (`$9E`; 8 before the September 22 platform work); 52 live rings (53 records minus the
leading `(0,0)` sentinel). The end boss, capsule, `StartNewLevel`, dome platform and Death Egg sprite are
event-spawned and are not in the placement list.

## Obligations

| Obligation + spot | Contract / oracle | Config cases | Test binding | Implementation | Result (revision) | Gap / action |
| --- | --- | --- | --- | --- | --- | --- |
| BASELINE: placed object and ring census | `LRZ3_Sprites` `$1FCBA2` (35), `LRZ3_Rings` `$1FCD82` (53 records, 52 live) | native | `TestS3kLrzPlacementCensus` | implemented | pass, `3418eba6e` | Ratchet target 0 placeholders |
| ENTRY: `$1600` resources, title card, Act 3 carry | `Sonic3kLevelResourceProfile`; `Act3_flag` skips the title card and the `loc_62CC` Kos/Nem drain loop; `LRZ3_ScreenEvent` stage 0 (`loc_59B1C`) restores rings and timer | native | `TestS3kLevelContinuationHeadless`, `TestLevelContinuationCarry`, `TestS3kLrzBoulderCutsceneHeadless` | implemented | focused pass, 2026-09-22 | DEZ adoption, native entry-loop timing and remaining screen stages still open |
| ENTRY: star-post respawn branch | `LRZ3_ScreenInit` P1 X >= `$480`: camera `($920,$2F0)`, `Special_events_routine = $14`, `Events_bg+$00 = $10`, `Events_bg+$02 = $2D`, `Events_routine_fg = $C`, `Pal_LRZBossFire` -> `Target_palette_line_2`, player `($9C0,$36C)` | native + wide/donor | `TestS3kLrzBossCameraHeadless` | implemented | 17 checkpoint cases pass, 2026-09-22 | Full respawn route still needs the platform/boss graph |
| PRESENT: scroll handler registration | `$1600` must not use `SwScrlHpz`; `$1601` must keep it | native | `SwScrlLrzTest`, `TestS3kLrzScrollRegistrationHeadless` | implemented (`$1600` selects `SwScrlLrz3`; `$1601` retains HPZ) | focused pass, 2026-09-22 | stage reachability remains open |
| PRESENT: `SwScrlLrz3`, shimmer and per-column VScroll | `LRZ3_BackgroundEvent` five stages `0,4,8,$C,$10`; `word_5A106` = `$310` then 18 x `$10`; `sub_59D82/59DA2/59DBC`, `sub_59DDE` | native + wide | `SwScrlLrz3Test` | scroll/shimmer/column rendering implemented | 581 native frames matched, 2026-09-22 | background stage owner and boss allocation still open |
| PRESENT: animated tiles and palette | `AnimateTiles_LRZ3` channel 0 only at tile `$170` (`loc_2833C` returns for `Current_zone $16`); `$1600` AniPLC entry is `AniPLC_NULL`; `AnPal_LRZ3` gate `Palette_cycle_counters+$00` in {0, `$80`, 1} | native | `TestS3kLrzPatternAnimation`, `TestS3kLrzBossPaletteCycling` | implemented | ROM pixel/color oracles pass, 2026-09-22 | flash graph must publish palette modes |
| EVENT: Death Egg flash sequence | `LRZ3_BackgroundEvent` stages, `Obj_CollapsingBridge` spawn at `($60,$4D0)` | native | — | not implemented | open | Slice 9 |
| EVENT: autoscroll | `Special_events_routine $14` (`loc_59E46`), seven stages; thresholds X `$410`, Y <= `$330`, X `$650`, Y <= `$2F0`, X `$910`, Y >= `$320`, X `$BBF` with P1 X >= `$C50`; `sub_59F82` push at `Camera_X + $10`, kill on `Status_Push`, right cap `Camera_X + $120` | native + wide/donor | `TestLrzBossAutoscroll`, `TestS3kLrzBossCameraHeadless` | implemented | focused pass, 2026-09-22; 1635 native moving dispatches match arithmetic | Preserve 32px right margin at wide widths; flash trigger graph and cold route still open |
| OBJECT: `$9E` autoscroll controller, `$AD` platforms (7), `$6E` lava blocks (6), `$8B` sprite masks (2) | `Obj_LRZ3Autoscroll`, `Obj_LRZ3Platform`, `Obj_InvisibleLavaBlock`, `Obj_SpriteMask` | native | `TestS3kLrzPlacementCensus`, `TestSonic3kInvisibleHurtBlockHObjectInstance` | `$6E` and `$AD` implemented; `$8B` reads ROM mapping frame and enables SAT masking | platform graph/rewind and 900-frame checkpoint capture, 2026-09-22 | `$9E` and boss-driven platform stream remain open |
| OBJECT: `$0F` collapsing bridges (8) use `Map_HPZCollapsingBridge` | `Obj_CollapsingBridge` picks the HPZ mappings for `Current_zone $16` by ROM design | native | — | implemented (shared switch already matches) | classification pass | Art under it unverified: open question, slice 9 |
| BOSS: end boss and lava surface | `Obj_LRZEndBoss` `collision_property $E` (14 hits), `off_79812` six routines; `Obj_59FC4` `SolidObjectTopSloped2`, push `Events_bg+$14`; shared `HScroll_table+$110` table | native | `TestLrzBossLavaSurface`, `TestS3kLrzBossCameraHeadless` | lava surface implemented; boss still absent | slope/current, real landing and removed-object restore/replay pass, 2026-09-22 | boss graph and background arena entry still open |
| LOAD: defeat -> capsule -> `$1601` handoff | `loc_79998`/`loc_79A30`, `mus_LRZ2` fade, `$EC0` gradual, `StartNewLevel $2D` | native | — | not implemented | open | Slice 10; closes the HPZ entry dependency |
| ORACLE: strict segment replay | `TestS3kSonicTailsHpz22SegmentTraceReplay` (LRZ3 autoscroll), `TestS3kSonicTailsHpz222SegmentTraceReplay` (boss -> `$1601`), Tails equivalents | `-Ptrace-segments` | — | — | see [trace frontier log](../../../status/trace-frontier-log.md) | Slice 11 |

## Execution evidence

Worktree `.worktrees/ai-lrz-bring-up`. Slice 0 evidence is shared with the
[Act 1 matrix](s3k-lrz-act1.md). Baseline media: `~/Videos/OGGF/lrz-bring-up/raw-00-lrz3-before/`.

## September 22 camera/event slice

Worktree `.worktrees/ai-sk-zone-completion`, parent `59baeab3c`. Queued
`TestLrzBossAutoscroll,TestS3kLevelContinuationHeadless,TestS3kLrzBoulderCutsceneHeadless`
passed 39 tests at 16:30 BST, no failures/errors/skips. Expanded real-world
`TestS3kLrzBossCameraHeadless` passed 18 at 16:34 BST, no failures/errors/skips:
17 checkpoint viewport/donor/team cases plus foreground signals, terrain edits,
and full-world restore/forward comparison. Initial fixture failures came from
post-load ground snap (Tails) and capturing an unstepped sprite graph; the tests
now use the fresh-entry lifecycle and advance the complete world before capture.
No runtime adjustment was made for those fixture failures.

The native original movie observer recorded 20201 consecutive frames. It includes
the initial autoscroll, bonus-stage detour/checkpoint re-entry and the full boss
completion. All 1635 moving special-event dispatches agree with the ported fixed
point arithmetic; unchanged emulator frames were excluded from that motion check.
This does not certify frame-clock admission, the unimplemented flash/boss graphs
or visual parity. See the campaign audit for the native encounter observations.

## September 22 end-boss candidate (not certified)

On parent `3fbb61e7c`, the background arena owner and 14-hit boss/mine graph are
implemented in the working tree. Short startup lifetime and removed-parent/child
restore/forward checks pass (`TestS3kLrzBossPlatformsHeadless`, 19 tests, 18:10 BST).
The original checkpoint movie reaches defeat but hurts the player at capture
frame 2777 and dies at 5903 before results. Clip 43 shows only the first cycle.
A native stalled frame precedes a consistent one-frame boss offset; diagnostic
alignment matches 4584 root state rows but is not a strict replay pass.

Startup/launch allocation prefixes, negative player/fire-shield attacks, mine
publication and explosion failure/RNG order now pass. A real authored checkpoint
fight reaches capsule/results and playable `$1601`; full-world restoration and
forward replay pass at peak graph (13), defeat, capsule opening and results.
The 96-test focused selection passed at 18:41 BST without failures/errors/skips.
Clip 44 records the completion with explicit initial fire shield and 37 rings,
native Sonic + Tails, width 320; no hurt/death in 7300 frames.
At 18:46 BST, 25 complete checkpoint fights pass across five widths and native
Sonic solo/team, native Tails solo, S1 Sonic and S2 Sonic + Tails; every case
includes the four restore/forward checks. Stream allocation failure/retry and
reused-slot reads also pass. Palette disable/pause/resume is covered at 18:49 BST.
Capsule child-slot fidelity, native palette row timing, hardware admission and
cold entry remain open. See the [campaign audit](../../audits/2026-09-22-sk-zone-bring-up.md)
for failures, rejected approaches and provenance. Existing obligation rows above
retain their delivered status until the candidate passes its encounter gate.
