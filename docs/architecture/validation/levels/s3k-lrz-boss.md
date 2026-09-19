# S3K Lava Reef boss act coverage matrix

Game / canonical zone / act: S3K `S3K_LRZ_BOSS`, engine zone `$16` act index 0,
ROM `Current_zone_and_act = $1600`, SKL object set. The act shows the Lava Reef title card
on a level-select load and none on the Act 2 handover (`Act3_flag`, `loc_62B6`).
Character route: Sonic + Tails and Tails alone only — Knuckles never enters `$1600`.
Flash sequence, autoscroll, end boss, capsule and `Obj_StartNewLevel $2D` at `($FE8,$5E0)` to
Hidden Palace `$1601`. Owning plan: [LRZ bring-up](../../plans/2026-09-17-lrz-bring-up.md).
Status: slice 0 baseline only.

Incoming: LRZ2 `loc_63C14` with the Act 3 carry (`Act3_flag`, `Act3_ring_count`, `Act3_timer`,
`Saved2_status_secondary`), level select `$1600`, star-post respawn (`LRZ3_ScreenInit` P1 X >=
`$480`). Outgoing: `$1601`, which closes the Hidden Palace entry dependency recorded in the
[HPZ matrix](s3k-hpz-act.md).

Widths / donors / characters / teams (support authority `LaunchProfile.sanitizedFor`,
same roster as HPZ and DDZ): widths 320/400/512/640/800; supported character/donor pairs
off x {Sonic, Tails, Knuckles}, S1 x Sonic, S2 x {Sonic, Tails}; teams: solo, Sonic+Tails,
S1 Sonic+Sonic duplicate, S2 Sonic+Tails, Sonic+Tails+Knuckles at 800.

Five claims are tracked separately and never aggregated: **implemented**, **cold-reachable**,
**rewind-verified**, **native behaviour matched**, **visually matched**. Nothing below certifies
the act.

Placement baseline (`TestS3kLrzPlacementCensus`): 35 placed objects, of which **8 still build a
`PlaceholderObjectInstance`** after slice 1 (14 at `035e48a58`); 52 live rings (53 records minus the
leading `(0,0)` sentinel). The end boss, capsule, `StartNewLevel`, dome platform and Death Egg sprite are
event-spawned and are not in the placement list.

## Obligations

| Obligation + spot | Contract / oracle | Config cases | Test binding | Implementation | Result (revision) | Gap / action |
| --- | --- | --- | --- | --- | --- | --- |
| BASELINE: placed object and ring census | `LRZ3_Sprites` `$1FCBA2` (35), `LRZ3_Rings` `$1FCD82` (53 records, 52 live) | native | `TestS3kLrzPlacementCensus` | implemented | pass, `3418eba6e` | Ratchet target 0 placeholders |
| ENTRY: `$1600` resources, title card, Act 3 carry | `Sonic3kLevelResourceProfile`; `Act3_flag` skips the title card and the `loc_62CC` Kos/Nem drain loop; `LRZ3_ScreenEvent` stage 0 (`loc_59B1C`) restores rings and timer | native | `TestSonic3kTitleCardSublevelMappings` (card only) | not implemented (no carry owner; `GameLoop` cites `Act3_flag` in a comment only) | open | Slice 8/9; shared owner with DEZ2 -> `$1700` (`loc_7F310`) |
| ENTRY: star-post respawn branch | `LRZ3_ScreenInit` P1 X >= `$480`: camera `($920,$2F0)`, `Special_events_routine = $14`, `Events_bg+$00 = $10`, `Events_bg+$02 = $2D`, `Events_routine_fg = $C`, `Pal_LRZBossFire` -> `Target_palette_line_2`, player `($9C0,$36C)` | native | `TestS3kLrzScrollRegistrationHeadless` | camera/event checkpoint implemented and rewind-covered | focused pass, pending revision | Palette and player-position restore remain in slice 9 |
| PRESENT: scroll handler registration | `$1600` must not use `SwScrlHpz`; `$1601` must keep it | native | `SwScrlLrzTest`, `TestS3kLrzScrollRegistrationHeadless` | implemented (`$1600` uses `SwScrlLrz3`, `$1601` keeps `SwScrlHpz`) | focused pass, pending revision | — |
| PRESENT: `SwScrlLrz3`, shimmer and per-column VScroll | `LRZ3_BackgroundEvent` five stages `0,4,8,$C,$10`; `word_5A106` = `$310` then 18 x `$10`; `sub_59D82/59DA2/59DBC`, `sub_59DDE` | native + wide | `TestS3kLrzScrollRegistrationHeadless` | base camera transforms and dual-plane shimmer implemented | focused pass, pending revision | BG stages `$C/$10` and per-column boss VScroll remain slice 10 |
| PRESENT: animated tiles and palette | `AnimateTiles_LRZ3` channel 0 only at tile `$170` (`loc_2833C` returns for `Current_zone $16`); `$1600` AniPLC entry is `AniPLC_NULL`; `AnPal_LRZ3` gate `Palette_cycle_counters+$00` in {0, `$80`, 1} | native | — | not implemented | open | Slice 9 |
| EVENT: Death Egg flash sequence | `LRZ3_BackgroundEvent` stages, `Obj_CollapsingBridge` spawn at `($60,$4D0)` | native | — | not implemented | open | Slice 9 |
| EVENT: autoscroll | `Special_events_routine $14` (`loc_59E46`), seven stages; thresholds X `$410`, Y <= `$330`, X `$650`, Y <= `$2F0`, X `$910`, Y >= `$320`, X `$BBF` with P1 X >= `$C50`; `sub_59F82` push at `Camera_X + $10`, kill on `Status_Push`, right cap `Camera_X + $120` | native + wide | `TestS3kLrzScrollRegistrationHeadless` (checkpoint/rewind) | seven camera stages, fixed-point deltas and both player clamps implemented | focused pass, pending revision | Crush-kill branch and full-route/wide replay remain slice 9 |
| OBJECT: `$9E` autoscroll controller, `$AD` platforms (7), `$6E` lava blocks (6), `$8B` sprite masks (2) | `Obj_LRZ3Autoscroll`, `Obj_LRZ3Platform`, `Obj_InvisibleLavaBlock`, `Obj_SpriteMask` | native | `TestS3kLrzPlacementCensus`, `TestLrz3Platform`, `TestSonic3kInvisibleHurtBlockHObjectInstance` | `$9E`, every placed `$AD` subtype and `$6E` implemented; LRZ3 placeholder baseline is zero | focused pass, pending revision | `$8B` shared implementation remains a visual-route question |
| OBJECT: `$0F` collapsing bridges (8) use `Map_HPZCollapsingBridge` | `Obj_CollapsingBridge` picks the HPZ mappings for `Current_zone $16` by ROM design | native | — | implemented (shared switch already matches) | classification pass | Art under it unverified: open question, slice 9 |
| BOSS: end boss and lava surface | `Obj_LRZEndBoss` `collision_property $E` (14 hits), `off_79812` six routines; `Obj_59FC4` `SolidObjectTopSloped2`, push `Events_bg+$14`; shared `HScroll_table+$110` table | native | — | not implemented | open | Slice 10 |
| LOAD: defeat -> capsule -> `$1601` handoff | `loc_79998`/`loc_79A30`, `mus_LRZ2` fade, `$EC0` gradual, `StartNewLevel $2D` | native | — | not implemented | open | Slice 10; closes the HPZ entry dependency |
| ORACLE: strict segment replay | `TestS3kSonicTailsHpz22SegmentTraceReplay` (LRZ3 autoscroll), `TestS3kSonicTailsHpz222SegmentTraceReplay` (boss -> `$1601`), Tails equivalents | `-Ptrace-segments` | — | — | see [trace frontier log](../../../status/trace-frontier-log.md) | Slice 11 |

## Execution evidence

Worktree `.worktrees/ai-lrz-bring-up`. Slice 0 evidence is shared with the
[Act 1 matrix](s3k-lrz-act1.md). Baseline media: `~/Videos/OGGF/lrz-bring-up/raw-00-lrz3-before/`.
