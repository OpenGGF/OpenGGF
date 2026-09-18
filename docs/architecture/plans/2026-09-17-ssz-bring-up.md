# Sky Sanctuary Zone: methodology v2 bring-up plan

Date: 2026-09-17. Planned branch `feature/ai-ssz-bring-up` in `.worktrees/ai-ssz-bring-up`;
execution base develop `035e48a58` (pin this SHA for the combined change-based validation; the
branch was cut one commit after the `9cba6dbb6` the first draft named — the difference is the docs
merge that added this plan). Applies
[methodology v2](../designs/2026-09-15-zone-methodology-v2.md) with the refinements from the
[SOZ](2026-09-15-soz-methodology-v2.md), [HPZ](2026-09-16-hpz-bring-up.md) and
[DDZ](2026-09-17-ddz-bring-up.md) campaigns to SSZ (`$A00` Sonic/Tails, `$A01` Knuckles). Design
and plan by Fable 5.1; implementation and execution by Opus, as for HPZ and DDZ. Entry skill:
[s3k-zone-bring-up](../../../.agents/skills/s3k-zone-bring-up/SKILL.md). Starting inventory:
[ssz-analysis.md](../research/s3k-zones/ssz-analysis.md) (corrected 2026-09-17; reverify per slice) and the
placement inventory [ssz-object-inventory.md](../research/s3k-zones/ssz-object-inventory.md). Hardened
2026-09-17 for Opus/Sonnet execution: every slice below names its inventory rows, skills, files, first
failing test and done-condition; read the [rules box](#rules-for-the-implementer) first.

## Goal and delivery rule

Deliver `$A00` from the teleporter arrival through the GHZ and MTZ recreations, Mecha Sonic, the
results tally, the Death Egg launch and spiral-ramp exit to the `$B00` request; and `$A01` from
Knuckles' arrival through the crane cutscene, Mecha Sonic and Super Mecha Sonic to the save and
ending handoff. Every slice is demonstrated on video, with a final act-ordered reel.

- Every feature or fix gets a short `GameplayCaptureTool` demo with at least 30 frames of lead-in
  and lead-out. Media live outside the repository in `~/Videos/OGGF/ssz-bring-up/`: raw captures
  `raw-NN-*` (never overwritten), clips numbered by slice, `inputs/`, `native/`, `reel/`. Copy
  `make_clip.sh`, `side_by_side.sh` and the reel scripts from `~/Videos/OGGF/ddz-bring-up/`.
- A demo is not parity evidence. A "before" build disables only the demonstrated registration in
  an uncommitted edit, reverted and recompiled immediately (`git status` clean).
- Track five claims separately per matrix row: implemented, cold-reachable, rewind-verified,
  native behaviour matched, visually matched. No aggregate green label.
- Work stays on the local branch until the campaign is complete; one develop merge at the end. Gaps go to `docs/status/s3k-known-bugs.md`, not the discrepancies file.

## Scope decisions (HPZ/DDZ precedents)

| Question | Decision | Reason |
| --- | --- | --- |
| Cold entry | Level-select/direct `$A00`/`$A01` load per slice; the HPZ → SSZ handoff is closed by this campaign | `HpzTeleporterRouteHelperObjectInstance` already requests `$A00` (altar ending) and `$A01` (Knuckles pad). HPZ evidence: engine loads SSZ1 at movie frame 448777 vs native 448755. Add one chained HPZ-tail → SSZ arrival check per character; never position past the beam intro |
| Act 1 exit | Verify fade-out + `StartNewLevel $B00` (`loc_581D2`) and the load attempt | `S3K_DEATH_EGG_1` loads but DEZ has no events/scroll: DEZ presentation and route are the DEZ campaign. Record what the engine does after the request |
| Act 2 exit | **Cold stop line: `loc_7BCFC`** (120 frames after `loc_7BCB0`: defeat, `object_control $83`, `SaveGame`, `Events_fg_4+1` → `SSZ2_ScreenEvent` stage 4 floor patch + `Ending_running_flag`; then `Player_mode = 3` and the spawn of `loc_5E6C0`/`loc_85EE6`). The SSZ2-owned later stages (`loc_59078` routines 4/8, stage 8 = `$66666666` fill of tiles `$7F0-$7FF` + the ending island sprite mask `loc_591D6` + delayed redraw, stage `$C`) are ending presentation, **implemented and tested from a declared seeded write `Events_fg_4 = $FF00`** with `loc_59078` alive and `Special_V_int_routine != 0`, because their only cold trigger is the ending object `loc_5E6C0` (`loc_5E98A`: `st Events_fg_4`). `loc_591D6` is **not** a floor collapse (the first draft was wrong; SSZ2 has none), so nothing gameplay-relevant lies past the stop line and the stop line is unchanged. `loc_5E6C0`, `sub_5B18E`, `Obj_Ending`, credits are the ending campaign (user decision 2026-09-17: stopping at the `sub_5B18E` boundary is accepted) | No S3K `EndingProvider`; the ending code is shared with `$D01` (`Ending_ScreenEvent` reuses `sub_5928C`/`sub_592EE`). Record engine behaviour after `loc_7BCFC`; mark the seeded stages cold-blocked in the act-2 matrix |
| Roster | `$A00`: Sonic, Sonic + Tails, Tails. `$A01`: Knuckles. Cross entries are ROM-denied (`LevelSelect_CheckKnuckles`/`CheckSonicTails`), except when `Debug_cheat_flag != 0`, which skips both checks | Derive from the production launch contract and assert the live roster and ROM-backed renderers. No raw debug override for Knuckles in `$A00` or Sonic in `$A01` (no art/route) |
| Tails CPU | `loc_13AB4`: `$A00` runs `sub_13ECA`, sets `Tails_CPU_routine $A`, `object_control $83` (no-starpost path only). **`Obj_57DCC`** (spawned by `loc_57CD2` when `Player_mode == 0`) is the Player 2 arrival helper and ends with `Tails_CPU_routine = 6`, `Tails_CPU_flight_timer = 0` | Player-dispatch hook outside every SSZ table; first cold frames depend on it. Engine owner: `Sonic3kSidekickCpuInitializationPolicy` (+ `game/internal/SidekickCpuInitializationPolicy`), which today models only SOZ1/`$17` |
| Cutscene Knuckles | In scope, slice 1b: `Obj_57E34` (`subtype $60` delay) beams `Obj_CutsceneKnuckles` subtype `$2C` = `CutsceneKnux_SSZ` into act 1 for **every** player mode; he lands on terrain by `ObjCheckFloorDist` (`$AF` is an inert sprite) and sets `Events_bg+$08` (`loc_658F2`), which `$77` reads (`Obj_SSZCutsceneBridge`, sonic3k.asm:90428) before clearing `Events_bg+$05` | The arrival route is blocked without it: `Events_bg+$05` stays set, so `sub_575EA` never runs and `Camera_max_X` stays `$200`. Missed by the first plan and mis-described in the analysis |
| Widths and donors | 320 plus one wide viewport on every mandatory mechanic from slice 1; S1 donor and an extra-follower team per the level test standard | Arena locks compare `Camera_X == $160/$1660/$19A0`; cloud sprites use `& $1FF` screen maths; the Death Egg is drawn on Plane A from the FG layout (X 1:1, Y from `_unkEEEE`). All width-sensitive |
| Traces | Strict replay late; movies supply cold input and native states from slice 1 | v2 |
| Forms | Movie saves hold Super Emeralds; Hyper is the native form. Normal-form rows are authored | Same trap as DDZ: read `Super_emerald_count` from the native state first |

## Findings that change the plan

- **The `ssz` trace segments are Death Egg; SSZ is filed under `hpz`.** Metadata `zone_id` is the
  ROM zone: `hpz*` = `zone_id 10` = SSZ, `ssz*` = `zone_id 11` = DEZ, `hpz22*` = `$16` HPZ. SSZ
  fixtures (rows / `bk2_frame_offset` / start):

  | Fixture | Rows | Offset | Start | Content |
  | --- | --- | --- | --- | --- |
  | `runs/s3k-sonic-tails-complete-emeralds/hpz` | 7638 | 448920 | `$100,$FAE` | Arrival, GHZ arena (cam `$160,$7C0`, 1142 rows), MTZ arena (cam `$1660,$380`, 1004 rows), death near `$14E3,$A8` |
  | `…/hpz_2` | 4352 | 460334 | `$14C0,$E8` | Checkpoint restart, Y-wrap crossing (cam Y `$8C` → `$E4D`), second death |
  | `…/hpz_3` | 3937 | 465044 | `$1880,$968` | Checkpoint, Mecha Sonic arena (cam `$19A0,$5C0`, 1843 rows), launch, handover to `$B00` (`$30,$9AC`) |
  | `runs/s3k-tails-full-chain-all-emeralds/hpz`, `hpz_2` | 6023, 10582 | 423903, 433476 | `$100,$FAE`, `$640,$5EC` | Tails alone: GHZ in `hpz` (1117 rows); MTZ (1409) and Mecha Sonic (1566) in `hpz_2` |
  | `runs/s3k-knuckles-complete-superemeralds/hpz` | 21441 | 412501 | `$80,$6AE`, act 2 | Whole `$A01` incl. ending camera rise to `$1CA0` |
  | `hpz_completerun` | 18641 | 396720 | `$100,$FAE` | Older Sonic run |

  Replay classes: `TestS3kSonicTailsHpz{,2,3}SegmentTraceReplay`,
  `TestS3kTailsFullChainHpz{,2}SegmentTraceReplay`; **the Knuckles run has no replay package at all**
  (`tests/trace/s3k/` holds only `sonictails` and `tailsfullchain`): add the `$A01` class in slice
  10. `TestS3k*Ssz*` belong to the DEZ campaign. Two native deaths give free death/checkpoint
  evidence, and all three act-1 bosses have native rows for both Sonic + Tails and Tails alone.
- **Almost nothing exists.** Present: zone/level registry and music (`Sonic3kZoneRegistry`),
  title card without act number, `addSszEntries` (EggRobo art only, `Sonic3kPlcArtRegistry`),
  animals, `SSZHPZTeleporterObjectInstance` + `TeleporterBeamObjectInstance` (HPZ branch only),
  save progression rows, the negative falling-intro gate (`TestS3kLrzFallingIntroBootstrap`).
  Absent by name and by role: events (`Sonic3kLevelEventManager` has no SSZ case), scroll (SSZ
  silently gets `SwScrlS3kDefault`), AniPLC (`Sonic3kPatternAnimator` address switch has no `$0A`),
  runtime state, every object `$74-$7F`, `$A0-$A3`, `$AF`, `$B2` (registry has names only),
  `PLC_32_33_34_35` art (`ArtNem_SSZMisc`, `GrayButton`), all four bosses, matrix and coverage row.
  The S2 `Sonic2MechaSonicInstance` and S1/S2 boss classes are not reusable owners: the SSZ bosses
  are S&K code with their own tables (`Obj_SSZGHZBoss` borrows S2 ship code inside S&K).
- **Analysis corrections.** (1) Act-1 Mecha Sonic is allocated by the `$79` teleporter's SSZ branch
  (`loc_45A84`: `Camera_Y == Camera_max_Y`, writes `_unkFAA4`), not "the HPZ exit"; act 2 by the
  crane cutscene `loc_7CB64` with `mus_FinalBoss`. (2) `Obj_SSZEndBoss` → `Obj_SSZ2_Boss` in place
  (`loc_7BBE0`) is the **act-2** Super phase; act 1 ends at `End_of_level_flag`. Act 1 init branches
  on `Current_act` (`loc_7B308` vs `$220,$4A0`). (3) `$B2` is `Obj_KnuxFinalBossCrane`; the
  engine's `Sonic3kObjectIds.ICZ_FREEZER = 0xB2` is the S3-half ID: use the SKL table.
  (4) `Obj_57C1E` sets `Events_bg+$04`, player Y = `Camera_Y + $65`, `object_control 3`.
- **Placement totals (new, [inventory](../research/s3k-zones/ssz-object-inventory.md)).** Act 1: 213
  objects, 179 rings (+ a leading `(0,0)` ring record). Act 2: 5 objects (3 placed rings, `$79`, `$B2`),
  no ring-list rings. 68 (ID, subtype) rows: 22 shared concrete (47 + 3 placements), 6 `$79` rows on a
  concrete class whose SSZ branch is missing (10 + 1), 40 placeholder rows (156 + 1), 0 unregistered.
  Largest families: `$7B` 35, `$7D` 27, `$A0` 26, `$7E` 25. No boss is placed. Bytes match the ROM at
  `$1F90EE`/`$1F95F2`/`$1F9616`/`$1F98E8`.
- **Hardening corrections to the first draft of this plan (verified in `sonic3k.asm`).**
  (a) Boss flags: the **spawn** writes `Events_bg+$00/$02 = $7F00` (`loc_576E8`/`loc_5775C`); the
  **defeat** does `st` on the high byte (negative; `Obj_SSZGHZBoss` :162738, `Obj_SSZMTZBoss` :163573).
  `sub_575EA` reads zero / positive / negative as idle / fighting / beaten. (b) `LevelSetup` clears
  `Events_bg+$00..$0F` on every load, so a respawn forgets beaten bosses; nothing skips them, the
  starposts simply sit past each arena. Testable: after respawn at `$34:$03` the MTZ branch sets
  `Camera_min_X = $160` (GHZ word is zero), not 0. (c) `$79` gated pads (`$AA`, `$F6`) start sunk
  `$20` px and inert until their flag is negative, then rise 1 px per 4 `Level_frame_counter` ticks.
  (d) `SSZ1_ScreenInit` forces camera/bounds only on the no-starpost path. (e) Act 2
  `AnimateTiles_NULL` is a bare `rts`: act 2 has no animated tiles. (f) `SSZ2_ScreenEvent` stage 4
  (floor patch, `Ending_running_flag`) runs **after** the defeat, so pause/HUD are normal during the
  fight. (g) EggRobo (`$A0`, 26) is three behaviours paired through `_unkFA82`: low nibble 0 is a scaled
  fly-by (`Perform_Art_Scaling`) that sets bit `subtype >> 4` and re-queues `ArtKosM_EggRoboBadnik`
  when it leaves (`loc_91570`); low nibble 2 is the fighter, which `sub_91914` deletes (`loc_85088`)
  unless that bit is set; low nibble 4 is the shooter (`loc_915F6`, gate `V_int_run_count+3 & $F`).
  `Obj_SSZMTZBoss` (`loc_7A7C4`) and Mecha Sonic (`loc_7BB20`) overwrite the same RAM: model
  `_unkFA82.._unkFA87` as shared bytes in `SszZoneRuntimeState`, not as an EggRobo field.
  (i) **Pseudo-starpost.** Bridge `$77` (`loc_44FBA`) and cutscene Knuckles on leaving the screen
  (`loc_65976`) write `Last_star_post_hit = 1`, `Saved_X/Y = $140,$C6C`, `Save_Level_Data`; the bridge
  then clears `Saved_timer`. A death after the bridge respawns there, skips the arrival
  (`SSZ1_ScreenInit`, `loc_13A10`) and spawns the bridge extended (`loc_4501A`).
  (j) `loc_591D6` is the `Map_KnuxEndingIslandMask` sprite mask, not a floor collapse (found by the
  independent verifier, re-read here).
  (h) Placement Y words: bit 15 is "ignore the Y window", not a respawn bit (engine Javadoc in
  `Sonic3kObjectPlacement` says otherwise; no SSZ record sets it, so do not touch it here); two `$7D`
  clouds store Y `$103C/$104C` and rely on the `& $FFF` mask across the wrap seam.
- **`Obj_SSZEndBoss` seeds `RNG_seed` from `V_int_run_count`** (`loc_7B2DC`). As with DDZ turrets, a
  cold entry legitimately differs from the movie; full-route matching needs a declared clock seed.
- **`$79` SSZ branch is unimplemented** and also owns receiving pads gated on `Events_bg+$00/$02`
  sign (boss-defeated flags). Extending the HPZ class must not disturb HPZ (37+ green tests).
- **Results run inside SSZ1** (`loc_2DCA0` saves for `$A00`), then `SSZ1_ScreenEvent` stage 0 sees
  `End_of_level_flag` and starts the launch. Results/signpost-free tally is a route dependency.

## Design: who owns what

| State | ROM | Engine owner |
| --- | --- | --- |
| `Events_bg+$00..$06,$0C..$10`, `Events_fg_4`, `Events_routine_fg/bg`, `_unkEE98/_unkEE9C`, cloud cache `HScroll+$1F6`, crumble tables `HScroll+$80/$E0/$100/$140`, `_unkFAA4`, `_unkFAB0..B8`, `_unkFA8A`, `_unkFAA2` | Screen/BG events, bosses, teleporter | New `SszZoneRuntimeState` in `game/sonic3k/runtime/` (pattern `DdzZoneRuntimeState`), registered in `S3kRuntimeStates`, `RewindSnapshottable`; add SSZ to `currentRuntimeStateUsesThisEventInstance` (the DDZ restore bug). Objects read via `services()` |
| Camera bounds, arena locks, Y-wrap `-$100..$1000`, scroll lock, shake | `SSZ1/2_ScreenInit`, `sub_575EA`, `word_5778A/9A`, `loc_59078` | New `Sonic3kSSZEvents` (+ act split as `SozAct1Events` if size demands); `Camera.setVerticalWrapEnabled(true, $1000)`; gradual bounds via `S3kCameraGradualObjectInstance`/`S3kCameraStoredBounds`. Bosses spawn from the event, not placement |
| BG modes (sky / clouds / Death Egg), cloud drift, deform tables, FG VScroll bands, act-2 column waves | `SSZ1/2_BackgroundInit/Event`, `sub_579F0`, `sub_57A60`, `sub_574DC` (Plane A Death Egg), `sub_58D3E`, `sub_58FBC`, `SSZ1_BGDeformArray`, `word_577B2` (FG column VScroll), `word_58C80` (**FG** per-line HScroll via `ApplyFGDeformation`), `loc_5904A` (act-2 column waves: `HScroll_table+$170` → `Vscroll_buffer`, 20 columns), `SSZ2_*DeformArray` | New `SwScrlSsz` registered in `Sonic3kScrollHandlerProvider`; mode transitions use the existing staged plane redraw path (a full tilemap rebuild is a ~25 ms hitch and reverts direct Plane A writes). Per-column VScroll: reuse the DDZ/FBZ2 render-mode mechanism, no `@ModApi` change |
| Death Egg hot-swap: chunks `+$180`, blocks `+$B8`, tiles `$073`, spiral-ramp art, chunk byte patches, `Pal_SSZDeathEgg` line 2 | `SSZ1_ScreenEvent` stage 4 | `ZoneLayoutMutationPipeline`/`LevelMutationSurface` for layout and chunk patches; art through the S3K PLC/Kos-module queue with `invalidatePatternLookup` for pattern-only changes; palette via `S3kPaletteOwners`/`S3kPaletteWriteSupport` |
| Act-2 post-defeat floor patch, tiles `$7F0-$7FF` fill, ending island sprite mask | `SSZ2_ScreenEvent` stages 4-C, `loc_591D6` (`Map_KnuxEndingIslandMask`) | Same mutation and redraw owners |
| Roaming clouds, solid cloud platforms (swing on `_unkEE9C`), crumbling columns | `loc_57BB2` (draws `Random_Number` at init), `loc_57B8E` + `SolidObjectTopSloped2`, `sub_5750C` | Event-owned object instances. `sub_5750C` carries the **`_unkFAA4` object** (the Mecha Sonic slot), not the player: Y = `$660 −` column offset, deleted with `Events_fg_4+1` set when all ten columns clamp at `$580`. Players are scripted by `sub_57FE2`/`sub_58048` |
| Arrival, Tails helper (`Obj_57DCC`), cutscene Knuckles spawner (`Obj_57E34` → `CutsceneKnux_SSZ`), launch script and spiral ramp, control locks | `Obj_57C1E`, `Obj_57DCC`, `Obj_57E34`, `Obj_57E96`, `loc_58192`, `byte_587A8`, `loc_13AB4` | Controller objects using the generic object-control path; Tails CPU routine `$A` in the existing sidekick CPU owner. No zone check in shared player code: use providers/`GameRules` |
| AniPLC (6 scripts, act 1 only runs `DoAniPLC`; act 2 `AnimateTiles_NULL`) | `AniPLC_SSZ` | `Sonic3kPatternAnimator` address + per-act gate (verify act 2 truly never animates) |
| Ending palette cycles | `sub_5928C`, `sub_592EE` (event-called, gate `Palette_cycle_counters+0`) | Event-owned cycle in `Sonic3kPaletteCycler` ownership model; shared later with the ending campaign |
| Boss art/palettes: PLC `$7B`, `ArtKosM_SSZGHZMisc`, `SSZMTZOrbs`, `ObjSlot_MechaSonic` + `DPLCPtr_MechaSonic`, `MechaSonicExtra`, `PLC_KnuxFinalBossCrane`, Master Emerald | Boss inits | `Sonic3kPlcArtRegistry.addSszEntries` + boss-time queue; slotted DPLC per existing slotted-boss ports |

Binding rules as DDZ: semantic providers not zone-name carve-outs; `services()`; every gate names its
ROM clock (`Level_frame_counter & $F` for `sfx_BigRumble`, `V_int_run_count` for the RNG seed);
`FixBugs = 0` branches commented; constants cite routines; nothing keys on a fixture or frame;
trace rows never hydrate gameplay. `GameLoop`/`Engine.draw` are size-ratcheted. Check both
`@ModApi` spellings before adding public members; keep helpers in non-API classes.

## Dependency-ordered slices

Each slice: reverify inventory rows in the disassembly → discriminating failing test with
ROM-derived expectations → implementation → cold-route extension with preserved inputs → short
native comparison on a named question → 320 + wide + donor check → rewind spot → demo clip →
boundary review → evidence entry. Coupled boundaries (1, 2, 5, 7, 8, 9) get an independent review;
routine families (3, 4) share one.

| Slice | Scope and ROM owners | Early check and principal risk |
| --- | --- | --- |
| 0. Baseline and identity | Matrices `validation/levels/s3k-ssz-act1.md`, `s3k-ssz-act2.md`, coverage-backlog row, fixture identity table above, native form/emerald read, media root, `raw-00` broken-state captures of both acts, resources (`Pal_SSZ1/2` `$32/$34`, collision `$1E/$1F`, PLC, title card, music) | "Before" footage first; confirm SKL object table resolves `$74-$B2` for zone `$0A` |
| 1. Runtime state, arrival, act-1 bounds | `SszZoneRuntimeState`, `Sonic3kSSZEvents`, `SSZ1_ScreenInit`, `Obj_57C1E` + beam, `Obj_57D64`/`loc_57DA2`, `Obj_57DCC`, `loc_13AB4`, Y-wrap, `word_5778A/9A`; **1b:** `Obj_57E34` → `CutsceneKnux_SSZ`, `$AF`, `$77`, `Events_bg+$08/$05` release | First cold rows of `hpz` (`$100,$FAE`, cam `$60,$F41`). `LevelData` start `$100,$C00` vs ROM-forced camera: explain from ROM before coding. Beam class reuse vs `Obj_TeleporterBeamExpand` entry state |
| 2. Act-1 background and animation | `SwScrlSsz` sky/cloud modes and the four-state BG machine, `sub_57A60` layer maths, cloud drift `$500`, `_unkEE9C` oscillator `loc_57B6A`, roaming clouds `sub_5758A`, solid clouds `word_5853E`, AniPLC, `PLC_32_33_34_35` | Only the wrapped-Y `$800/$F00` switch is a staged plane redraw; the X `$1800` switch just changes BG offsets and re-rounds BG X (`sub_579F0`); fractions from the tables; wide-viewport seams on `& $1FF` cloud maths (record the presentation choice) |
| 3. Traversal objects | `$74 $75 $76 $7A $7B $7C $7D $7E $7F`, `$79` SSZ-branch pads (all subtypes except the `($1A40,$670)` spawner, slice 7), EggRobo `$A0` (+ `ChildObjDat_919D0/DE/E6`, `_unkFA82`), verify-only shared rows | One family review; EggRobo reviewed separately. Bouncy cloud and carrier are player-state hooks; collapsing families share debris tables; slot/allocator order decides same-frame children. Subtype census from decoded placements first |
| 4. Death, checkpoint, wrap lifecycle | Restart at starposts, `Events_bg` flags after respawn (skips a beaten boss?), objects across the Y seam | Native `hpz_2`/`hpz_3` starts give the expectations; level variables must clear on reload; timeline isolation |
| 5. GHZ recreation | `sub_575EA` lower arena, `Obj_SSZGHZBoss`, PLC `$7B`, `Pal_SSZGHZMisc` line 2, fade → `mus_EndBoss`; spawn writes `Events_bg+$00 = $7F00`, defeat `st Events_bg+$00` (negative), pad `$79:$AA` rises | `s3k-implement-boss`. Lock requires `Camera_X == $160` and grounded player: wide-viewport reachability. Sprite-composition audit (ball chain vs player vs terrain) |
| 6. MTZ recreation | Upper arena `$1660/$380`, `Obj_SSZMTZBoss`, `SSZ_MTZ_boss_*` RAM, orbs, laser timer, `PalLoad_Line1` | Orb slot order and RNG; palette line ownership vs level line 1 |
| 7. Mecha Sonic (act 1) and results | Final arena `$19A0/$5C0`, `$79` spawner `loc_45A84`, `Obj_SSZEndBoss` 21 routines from `loc_7B308`, children `ChildObjDat_7D47A`, DPLC, `sub_7D312/7D2D8/7D35A`, defeat → results → `End_of_level_flag` | RNG seed from `V_int_run_count`; Hyper flash/insta-shield interactions from the native movie; rewind across the boss graph |
| 8. Death Egg launch and exit | Stage 0→4→8, `Obj_57E96`, `sub_5750C` crumble + carry, hot-swap, `sub_574DC`, debris `loc_58234/58360/582AC/581F2`, `Special_V_int_routine 4/12`, scripted ramp run `loc_58192`, `$B00` | Most coupled slice: layout mutation + queued art + palette + independent BG V-scroll + shake in one window; renderer invalidation; rewind mid-crumble; player DPLC frames from `byte_587A8` |
| 9. Act 2 | `SSZ2_ScreenInit/Event`, `loc_59078`, `sub_58D3E`/`sub_58FBC`, `word_58C80`, `$B2` crane cutscene (`Obj_KnuxFinalBossCrane`, spawn at `loc_7CB64`; `mus_EndBoss` then `mus_FinalBoss`), `Obj_SSZEndBoss` act-2 init, forced run-right `loc_7BBE0`, `Obj_SSZ2_Boss` 36 routines, Master Emerald, `Run_PalRotationScript`, defeat/save `loc_7BCB0`, stage-4 floor patch, `loc_7BCFC`; first defeat → `mus_DDZ` + Master Emerald (`loc_7B996`); seeded: `loc_59078` 4/8, stage 8/`$C`, island mask `loc_591D6` | Column VScroll waves under widescreen; `_unkFAB8` bit 6 flicker; Knuckles glide/climb vs arena walls; cold stop line `loc_7BCFC`, later stages seeded (scope table) |
| 10. Routes and acceptance | Cold routes from movie input: Sonic + Tails (`--input-start` 448920 family; capture path runs one frame behind the headless fixture), Tails alone, Knuckles (412501); authored normal-form and Sonic-solo routes; chained HPZ → SSZ arrival; matrix breadth; rewind spots; strict replay frontiers for all six fixtures; moving inspection at 320 and wide | Positioned boss success does not advance the cold frontier. Native lag frames desync cold BK2 replay: skip movie input on repeated `lfc`. Route programs skip the recorded pre-level prefix |
| 11. Media and delivery | Reel, archive index, change-based validation against the pinned base, docs, integration | Below |

### Slice execution sheet

Inventory rows are `ID:subtype×count` from the [inventory](../research/s3k-zones/ssz-object-inventory.md);
every placed ID appears exactly once below. Paths are under `src/main/java/com/openggf/game/sonic3k/`
(tests under `src/test/java/com/openggf/tests/`), following the HPZ/DDZ layout: objects in `objects/`
prefixed `Ssz`, events in `events/`, runtime state in `runtime/`, scroll in `scroll/`. Matrices live in
`docs/architecture/validation/levels/`. The **first failing test** must be seen red before the
implementation and takes its expected values from the named ROM label or table, never from the Java.
A slice is **done** when its matrix rows carry all five claims with command + commit, or an explicit
blocked/open entry; "implemented" alone never closes a slice.

| Slice | Inventory rows | Skills to load | Files (new unless marked *edit*) | First failing test → expectation source | Done-condition beyond the five claims |
| --- | --- | --- | --- | --- | --- |
| 0 | all 68 rows: census only | `s3k-zone-bring-up`, `gameplay-capture`, `bizhawk-native-reference-capture` | `docs/architecture/validation/levels/s3k-ssz-act1.md`, `s3k-ssz-act2.md`; *edit* `docs/status/level-test-coverage.md`, `docs/status/trace-frontier-log.md` (fixture identity table) | `TestS3kSszPlacementCensus`: decoded ROM placements equal the inventory (213/5, per-ID counts, 180/1 ring records) → inventory doc, ROM offsets `$1F90EE…` | `raw-00` captures of both acts; native pass 1 savestates; `Super_emerald_count` read from each movie |
| 1 | `$79:$00` at `($100,$C70)` (arrival pad, verify art only) | `s3k-zone-events`, `s3k-disasm-guide` | `runtime/SszZoneRuntimeState`, `events/Sonic3kSSZEvents`, `objects/SszArrivalControllerObjectInstance` (`Obj_57C1E`/`Obj_57D64`/`loc_57DA2`), `objects/SszTailsArrivalHelperObjectInstance` (`Obj_57DCC`); *edit* `Sonic3kLevelEventManager` (SSZ case + `currentRuntimeStateUsesThisEventInstance`), `runtime/S3kRuntimeStates`, `sidekick/Sonic3kSidekickCpuInitializationPolicy`, `objects/TeleporterBeamObjectInstance` (`Obj_TeleporterBeamExpand` entry state) | `TestS3kSszArrivalHeadless`: frame-0 camera `($60,$F49)`, `Camera_max_X $200`, `max_Y $BC0`, `Scroll_lock`, P1 Y = `Camera_Y + $65`, `object_control 3`, `Events_bg+$04/$05` set → `SSZ1_ScreenInit`, `Obj_57C1E`/`loc_57CAC`. Second case with a starpost: none of these forced → `tst.b Last_star_post_hit` | First cold rows of fixture `hpz` match (`$100,$FAE`, cam `$60,$F41`); S+T, Sonic, Tails rosters asserted live; HPZ suites green |
| 1b | `$77:$00×1`, `$AF:$00×1` | `s3k-implement-object`, `s3k-plc-system` | `objects/SszCutsceneKnucklesSpawnerObjectInstance` (`Obj_57E34`), `objects/CutsceneKnucklesSszInstance` (pattern `CutsceneKnucklesHpzInstance`), `objects/SszCutsceneButtonObjectInstance`, `objects/SszCutsceneBridgeObjectInstance`; *edit* `Sonic3kObjectRegistry` (SKL `$77`, `$AF`) | `TestS3kSszKnucklesBridgeHeadless`: Knuckles allocated `$60` frames after the controller at X `$100`, base Y `$C4E`; `_unkFAB8` bit 0 on landing; `Events_bg+$08` + `sfx_Switch` at `loc_658F2`; bridge reads (never clears) `+$08`, slides `$C0` px at 2 px/frame, then clears `+$05` and itself writes the bounds and pseudo-starpost in the Verified ROM values table → `loc_44FA2`/`loc_44FBA` | Cold route walks off the arrival ledge over the bridge; rewind mid-cutscene; **pseudo-starpost lifecycle test**: die after the bridge → respawn `($140,$C6C)`, no arrival, bridge already extended |
| 2 | none placed (event-spawned clouds) | `s3k-parallax`, `s3k-animated-tiles`, `s3k-plc-system` | `scroll/SwScrlSsz`, `objects/SszRoamingCloudObjectInstance` (`loc_57BB2`), `objects/SszCloudOscillatorObjectInstance` (`loc_57B6A`), `objects/SszSolidCloudObjectInstance` (`loc_57B8E`); *edit* `scroll/Sonic3kScrollHandlerProvider`, `Sonic3kPatternAnimator` (`AniPLC_SSZ`, act 1 only), `Sonic3kPlcArtRegistry.addSszEntries` | `TestS3kSszScrollBands`: band scroll words for two cameras per BG mode → `sub_579F0`, `sub_57A60`, `SSZ1_BGDeformArray`; `TestS3kSszPatternAnimation`: 6 scripts' destinations/durations, **and act 2 animates nothing** → `AniPLC_SSZ`, `Offs_AniFunc` | 5 roaming + 10 solid clouds (`word_58758`, `word_5853E` = `$A-1`); moving capture at 320 and wide with no seam; mode switch without a full tilemap rebuild |
| 3 | `$74:$00×5`, `$75:$00×5,$80×1,$82×2`, `$76:$00×3,$01×4`, `$7A:$00×5`, `$7B:$00×31,$80×4`, `$7C:$00×7,$80×1`, `$7D:$00×27`, `$7E:$00×25`, `$7F:$00×8`, `$79:$00` at `($200,$5B0)`,`($1500,$CF0)`,`($1700,$B0)`; `$79:$15,$1E,$32,$AA,$F6` ×1 each; `$A0` 24 subtype rows ×26. **Verify only (already concrete):** `$01:$01×3,$03×8,$05×1,$06×1,$07×1,$08×2`, `$02:$45×1`, `$07:$01×4,$03×1,$10×1,$12×3`, `$08:$00×7,$10×3,$11×1`, `$14:$01×4`, `$28:$11×1,$30×1,$81×1` | `s3k-implement-object`, `s3k-plc-system` | one `objects/Ssz<Name>ObjectInstance` per ID (`SszRetractingSpring`, `SszSwingingCarrier`, `SszRotatingPlatform`, `SszElevatorBar`, `SszCollapsingBridgeDiagonal`, `SszCollapsingBridge`, `SszBouncyCloud`, `SszCollapsingColumn`, `SszFloatingPlatform`), `objects/EggRoboBadnikObjectInstance` + shot/child classes; *edit* `objects/SSZHPZTeleporterObjectInstance` (SSZ branch `loc_455BA`-`loc_4581C`, `sub_45866` line-3 palette), `Sonic3kObjectRegistry` (register each with `registerStockRomZoneBound(id, S3kZoneSet.SKL, ZONE_SSZ, …)`; never edit the S3KL `FBZ_*` factories), `constants/Sonic3kObjectIds` (SKL names) | One per family, e.g. `TestS3kSszTeleporterPads`: lift = `(subtype & $3F) * $10`; gated pad starts `$20` low and inert, rises 1 px / 4 ticks after the flag goes negative; launch sets `min_Y -$100`, `max_Y $1000` → `loc_45744`, `loc_4556A`, `loc_45640`, `loc_4577E`. `TestS3kSszEggRobo`: `_unkFA82` bit = subtype high nibble on exit; shot gate `V_int_run_count & $F` → `loc_91570`, `loc_915F6` | Zero SSZ placeholders left in act 1 except `$79` spawner (slice 7); `TestS3kSszPlacementCensus` extended to assert concrete classes; two `$7D` at Y `$103C/$104C` load at `$03C/$04C`; HPZ teleporter tests (37+) still green |
| 4 | `$34:$02×1,$03×1,$04×1` (verify only) | `s3k-zone-events` | `tests/TestS3kSszLifecycleProduction` | Respawn at `$34:$03`: `Events_bg+$00..$0F` zero, arrival not re-run, `Camera_min_X = $160` from the MTZ branch, objects and rings correct across the Y seam → `LevelSetup` :102201-102204, `SSZ1_ScreenInit`, `loc_5770C` | Matches native `hpz_2`/`hpz_3` first rows; timeline isolation across the reload |
| 5 | none placed (`Obj_SSZGHZBoss` from `loc_576E8`) | `s3k-implement-boss`, `s3k-palette-cycling` | `objects/SszGhzBoss*` (boss + `ChildObjDat_7A684/7A69E`, tree list, mecha head, explosions); *edit* `Sonic3kSSZEvents` (`sub_575EA` lower branch), `Sonic3kPlcArtRegistry` | `TestS3kSszGhzArenaHeadless`: lock needs P1 Y ≥ `$7C0`, `Camera_X == $160`, grounded; bounds `max_X $160`, `min_Y = target_max_Y = $7C0`; spawn when `Camera_Y == $7C0` with `+$00 = $7F00`, `+$05` set; defeat → `+$00` negative → `loc_57686`-`loc_576E8`, :162738 | Pad `$79:$AA` rises and lifts `$2A0`; sprite-composition audit recorded; 320 + wide lock reachability |
| 6 | none placed (`Obj_SSZMTZBoss` from `loc_5775C`) | `s3k-implement-boss` | `objects/SszMtzBoss*` (boss, orbs `ChildObjDat_7AB80`, mecha head) | `TestS3kSszMtzArenaHeadless`: P1 Y < `$440`, Y ≥ `$420`, `Camera_X == $1660`, grounded; `min_X $1660`, `min_Y = target_max_Y = $380`; spawn at `Camera_Y == $380`, `+$02 = $7F00`; `min_X` before the lock is `0` if GHZ word non-zero else `$160` → `loc_5770C`-`loc_5775C` | Pad `$79:$F6` rises and lifts `$360`; `PalLoad_Line1` ownership vs level line 1 recorded |
| 7 | `$79:$00` at `($1A40,$670)` (spawner), `$02:$45` below it (verify) | `s3k-implement-boss`, `s3k-plc-system` | `objects/SszMechaSonic*` (boss, `ChildObjDat_7D474…7D492`, `7D4CA`, `7D4D0`, slotted DPLC); *edit* `SSZHPZTeleporterObjectInstance` (`loc_45A66`/`loc_45A84`/`loc_45AB0`), `Sonic3kSSZEvents` (final arena `$19A0/$5C0`, `Events_bg+$06`) | `TestS3kSszMechaSpawnHeadless`: arena lock at `Camera_X ≥ $19A0 && P1 Y < $680`; pad gets high priority, allocates only when `Camera_Y == Camera_max_Y`, writes `_unkFAA4`; boss init `collision_property 8`, `RNG_seed = V_int_run_count`, act-1 branch routine 4, `x_vel -$800`, X = `Camera_X + $160`; pad explodes when boss X ≤ pad X → `sub_575EA` head, `loc_45A84`, `loc_7B2DC`, `loc_7B308` | 21 routines traced to labels in the test names; results + save reached cold; declared clock seed for movie matching, labelled seeded |
| 8 | none placed | `s3k-zone-events`, `s3k-parallax`, `s3k-plc-system`, `s3k-palette-cycling` | `objects/SszLaunchControllerObjectInstance` (`Obj_57E96`), `objects/SszLaunchDebris*`; *edit* `Sonic3kSSZEvents` (stages 0/4/8, `sub_5750C`, `sub_574DC`), `SwScrlSsz` (Death Egg mode, `word_577B2`) | `TestS3kSszLaunchSequenceHeadless`: stage order on `End_of_level_flag`; `sfx_BigRumble` on `Level_frame_counter & $F == 0` while routine ≤ 4; script jump at counter `$910` (`x_vel $400`, `y_vel -$680`, `ground_vel $800`); Player 2 handled only when `Player_mode == 0`; `$B00` request `3*60` frames after the scripted arc's `y_vel` turns non-negative (`loc_58192`), issued by Player 1 only → `Obj_57E96`, `sub_5806E`, `loc_58192`, `loc_581D2` | Hot-swap uses the mutation pipeline + `invalidatePatternLookup`; rewind mid-crumble; `$B00` load attempt recorded |
| 9 | act 2: `$00:$00×3`, `$79:$00×1` (verify), `$B2:$00×1` | `s3k-implement-boss`, `s3k-zone-events`, `s3k-parallax`, `s3k-palette-cycling` | `objects/SszFinalBossCrane*` (`$B2` + `ChildObjDat_7D4BC/7D4C4`, robo head, ship flame, `loc_7D11C`), `objects/SszSuperMechaSonic*` (`Obj_SSZ2_Boss`, `ChildObjDat_7D49A…7D4AE`, Master Emerald), `objects/Ssz2CameraControllerObjectInstance` (`loc_59078`), `objects/Ssz2EndingIslandMaskObjectInstance` (`loc_591D6`, sprite mask: check it reaches the production SAT mask post-pass); *edit* `Sonic3kSSZEvents` (act 2), `SwScrlSsz` (act 2), `Sonic3kObjectRegistry` (SKL `$B2`) | `TestS3kSsz2EventsHeadless`: init camera `(0,$649)`, arrival `$2D = $44`, controller `$30 = 1`; stage 4 fires only after `Events_fg_4+1` from the defeat, writes `$17,$18` ×4 then `$17` into row `$24(a3)` and `$19` ×9 into row `$28(a3)`, sets `Ending_running_flag`; boss swap only after P1 X ≥ `_unkFA8A + $10` → `SSZ2_ScreenInit`, `loc_58AE0`, `loc_7BBE0`, `loc_7BCB0` | Cold Knuckles route to `loc_7BCFC`; seeded stage 8/`$C` test with the write declared; ending rows marked blocked |
| 10-11 | — | `bk2-input-authoring`, `gameplay-capture`, `trace-replay-bug-fixing`, `gameplay-highlights` | `tests/TestS3kSszColdRoutes`, `tests/TestS3kSszCompatibilityMatrix`; optional `tests/trace/s3k/knuckles/…` (Knuckles trace testing is out of scope) | `TestS3kSszColdRoutes`: movie-input route reaches each boundary frame within the native fixture's tolerance → fixtures `hpz`, `hpz_2`, `hpz_3` | Frontiers for the five Sonic/Tails fixtures logged; reel built; combined validation acknowledged |

### Rules for the implementer

Read before the first slice; they are the failure modes previous campaigns actually hit.

| Rule | Detail |
| --- | --- |
| No tuning to fixtures | Expected values come from a ROM label/table or an independent native probe. Never fit a constant, frame index, route or fixture name. If a test disagrees with the ROM, the test is wrong |
| Disassembly first | Cite labels, not line numbers (the analysis doc's lines run ~5 early; `loc_XXXXX` is a ROM address). Re-read the owning routine in each slice even where this plan summarises it |
| `services()` | Objects use injected `services()`, never `getInstance()`. Shared state lives in `SszZoneRuntimeState`; objects do not cache camera deltas |
| No zone-name carve-outs | Shared code takes semantic rules (`GameRules`, providers, policies such as `SidekickCpuInitializationPolicy`). A `zone == SSZ` test belongs only in SSZ-owned classes and registries |
| Mod API pin | Do not add public members to any `@ModApi` / `@com.openggf.game.ModApi` type (grep both spellings first). Helpers go in non-API classes. Hook edits need explicit user OK |
| Size ratchets | `GameLoop` (3072 effective lines) and `Engine.draw` (3 lines) are guarded: put logic in managers; run `-Pguards` before committing near them |
| Rewind | Every new object: `recreateForRewind`/probe constructor + captured state; every new manager/state: a registered `RewindSnapshottable`. Add SSZ to `currentRuntimeStateUsesThisEventInstance`. Test before/active/after with forward replay at each slice's spot |
| ROM-only assets | Every art/mapping/palette byte through the ROM pipeline; disassembly files are research only |
| `FixBugs = 0` | Model the shipped branch; comment which branch and what the fixed one changes |
| Maven | Always `python3 tools/testing/maven_queue.py -Dmse=off … "-Ds3k.rom.path=<absolute>"`; never bare `mvn`; under fish pass each `-D` separately. **Inspect skips**: a wrong ROM path skips `@RequiresRom` tests and reports green. `rm -rf target/test-tmp` on `UnsatisfiedLinkError`. Clean build after a merge before measuring |
| Git | Never `git stash`. Never `--no-verify`. Start every git chain with an explicit `cd`; check `git log -1` and `git status` after each commit; do not hide hook output. All seven trailers on every commit. Work only in `.worktrees/ai-ssz-bring-up`; never build in another lane's worktree |
| Evidence | Five claims per matrix row, each with command, commit, configuration, setup, result, skips. Break every new comparison on purpose once. Native probes: `OGGF_OUT` only, no `print()`, savestate on pass 1, check the probe's own error status (exit 0 proves nothing) |
| Demo clip | One `GameplayCaptureTool` clip per feature/fix, ≥ 30 frames lead-in and lead-out, under `~/Videos/OGGF/ssz-bring-up/`; raw captures never overwritten |
| Gaps | Unfixed gaps go to `docs/status/s3k-known-bugs.md`; the discrepancies file is intentional-only. No develop merge until the campaign is complete |

### Verified ROM values

Re-read in `sonic3k.asm` on 2026-09-17 by the planner and an independent verifier; write tests from
these, citing the label. Anything not listed here is still a claim to reverify in its slice.

| Owner | Exact behaviour |
| --- | --- |
| `SSZ1_ScreenInit` (no starpost only) | `Obj_57C1E` at X `$100`, `$2D = $6C`; `Events_bg+$05` set; `Camera_max_X $200`; `max_Y = target_max_Y = $BC0`; camera `($60,$F49)`; `Scroll_lock` set. Always: clear `_unkEE98/_unkEE9C`, 5 roaming clouds from `word_58758` |
| `Obj_57C1E` | P1 Y = `Camera_Y + $65`, `object_control 3`, `Events_bg+$04` set; act 1 always spawns `Obj_57E34` (`subtype $60`); `loc_57CD2` rise: P1 Y and `Camera_Y` −8 per frame for `$2D` frames; then `object_control 1`, roll anim 2, `y_vel −1`, **`Scroll_lock` cleared here** (`loc_57D3C`), `Obj_57DCC` (`subtype $C` delay) if act 1 and `Player_mode == 0`, act 2 sets P1 `art_tile` bit 7; `Obj_57D64` releases control and clears `Events_bg+$04` when the `$20000/$800` swing completes |
| `loc_13AB4` | `$A00`, no starpost (`Tails_CPU_star_post_flag` gate at `loc_13A10`): `sub_13ECA`, `Tails_CPU_routine = $A`, `object_control = $83`. `Obj_57DCC` ends with routine 6, flight timer 0 |
| `Obj_57E34` → `CutsceneKnux_SSZ` | After `$60` frames: `Obj_CutsceneKnuckles` subtype `$2C`, X `$100`, base Y `$C4E`, `_unkFAB8` bit 0 when the swing completes. 11 routines (0..`$14`). Routine 0 clears `_unkFAB8`, copies line 2 to `Target_palette_line_2`, loads `Pal_CutsceneKnux`; routine 2 queues `ArtKosM_SSZDeathEggSmall`. Art is DPLC'd from `ArtUnc_Knux` to `ArtTile_CutsceneKnux` (`$4DA`, inside monitor art `$4C4+`), so no PLC wait; the exit `loc_65976` restores line 2 and reloads `PLC_Monitors` |
| `Obj_SSZCutsceneBridge` (`$77`) | Waits for `Events_bg+$08 != 0`; offset `$C0` → 0 at 2 px/frame, `sfx_DoorOpen` at `$68`; on 0: clear `Events_bg+$05`, `min_X 0`, `max_X $19A0`, `min_Y −$100`, `max_Y = target = $1000`, `Saved_X/Y = $140,$C6C`, `Last_star_post_hit = 1`, `Save_Level_Data`, `Saved_timer = 0`. With a starpost at init it starts extended (`loc_4501A`). Never touches `Scroll_lock` |
| `sub_575EA` | Skipped while `Events_bg+$05` or `+$06` set. Final arena: `Camera_X ≥ $19A0 && P1 Y < $680` → `min_X $19A0`, `min_Y = target_max_Y = $5C0`, `+$06`. GHZ (P1 Y in `[$440,$880)`, flag byte 0): pre-lock `min_X $160`, `max_X $19A0`; lock when P1 Y ≥ `$7C0`, `Camera_X == $160`, not in air → `max_X $160`, `min_Y = target = $7C0`, `+$01`; spawn at `Camera_Y == $7C0` with `+$00 = $7F00` (also zeroes `+$01`), `+$05`. MTZ (P1 Y < `$440`): pre-lock `max_X $1660`, `min_X` = 0 if word `+$00` ≠ 0 else `$160`; lock at P1 Y ≥ `$420`, `Camera_X == $1660`, not in air → `min_X $1660`, `min_Y = target = $380`, `+$03`; spawn at `Camera_Y == $380`, `+$02 = $7F00`, `+$05`. Beaten (flag negative): `min_X 0`, `max_X $19A0`. **`+$05` stays set from the spawn until a `$79` launch clears it (`loc_45790`)**, freezing the bounds logic through the fight |
| GHZ / MTZ bosses | Both: PLC `$7B`, line 2 saved to `Target_palette_line_2` then `PalLoad_Line1`; defeat `st Events_bg+$00` / `+$02`, line 2 restored, PLC `$32` reloaded. MTZ `loc_7A7C4` writes `$10,0,3,0,1,0` to `_unkFA82.._unkFA87` |
| `$79` SSZ pads | See the inventory semantics table (lifts `(subtype & $3F) * $10`; gated pads sunk `$20`, rise 1 px per 4 `Level_frame_counter` ticks; launch bounds `−$100/$1000`, `Scroll_lock`, clears `+$05`; `sub_45866` writes line 3 `+$18`) |
| `loc_45A84` / `loc_7B2DC` / `loc_7B308` | Spawn when `Camera_Y == Camera_max_Y`; `_unkFAA4` = boss slot; `collision_property 8`; `RNG_seed = V_int_run_count`; act 1: routine 4, frame 2, `x_vel −$800`, `_unkFAB4 = Cam_X + $20`, `_unkFAB6 = Cam_X + $120`, X = `Cam_X + $160`, `_unkFAB0 = Cam_Y + $30`, Y = `Cam_Y + $A0`, children `ChildObjDat_7D47A`; act 2: `($220,$4A0)` |
| EggRobo | Low nibble 0 fly-by sets `_unkFA82` bit `subtype >> 4` and re-queues art on exit; low nibble 2 deleted by `sub_91914` unless the bit is set; low nibble 4 shoots on `V_int_run_count+3 & $F == 0` while on screen, `$39` shots |
| BG (`sub_579F0`, `sub_57A60`) | Plain mode below `Camera_X $1800`: BG = (`Cam_X + $28`, `Cam_Y + $160 + _unkEE9C`); at/above: (`Cam_X`, `Cam_Y + $180`); the toggle re-rounds `Camera_X_pos_BG_rounded` only. Cloud mode for wrapped `Cam_Y` in `[$800,$F00)`: staged `Draw_PlaneVert*` redraw; drift `+$500`/frame on `HScroll_table+0` |
| Launch (`Obj_57E96`, `sub_5750C`, `sub_574DC`, `sub_5806E`) | `sfx_BigRumble` when `Level_frame_counter & $F == 0` and routine ≤ 4. Ten columns: gravity `+$800`, clamp when offset + `Camera_Y_pos_copy` < `$580`; carried object = `_unkFAA4`, Y = `$660 −` column offset; all clamped → `Events_fg_4+1`, object deleted. Stage 4 swap: `Chunk_table+$180`, `Block_table+$B8`, tiles `$073`, `ArtKosM_SSZSpiralRamp`, `Pal_SSZDeathEgg` → line 2. Death Egg: `_unkEEEA = Cam_X`, `_unkEEEE = (Cam_Y − $110) >> 2` plus `$6000`/frame once `Cam_Y == $110`. Ramp script: at counter `$910` jump `x_vel $400`, `y_vel −$680`, `ground_vel $800`, gravity `+$38`; when `y_vel ≥ 0`: `object_control 3`, timer `3*60`; P1 only → `cmd_FadeOut`, `StartNewLevel $B00`. Tails (`d3 ≠ 0`): Y `+4`, priority `$100`, or `$80` when `d2 ≥ 0`; same frame table `byte_587A8` and DPLC |
| Act 2 | `SSZ2_ScreenInit`: `Palette_cycle_counters+0` set, `Obj_57C1E` X `$A0` `$2D = $44`, camera `(0,$649)`, `Scroll_lock`, `loc_59078` with `$30 = 1`. Crane: `mus_EndBoss`, later `mus_FinalBoss`, `Scroll_lock`, spawns `loc_7D11C` + `Obj_SSZEndBoss` (`loc_7CB90` writes `_unkFAA4` even if allocation failed). First defeat `loc_7B996`: routine `$A`, `mus_DDZ`, `ChildObjDat_7D492`, `ArtKosM_EndingMasterEmerald`. `loc_7BBE0`: forced right until P1 X ≥ `_unkFA8A + $10`, then pointer swap, `collision_property 8`, `collision_flags $23`. `loc_7BCB0`: `_unkFAA2`, `Events_fg_4+1`, `object_control $83`, `SaveGame`; `loc_7BCFC` after `(2*60)−1`: `Player_mode 3`, spawns `loc_5E6C0`, `loc_85EE6` |
| Act 2 seeded chain | `Events_fg_4 = $FF00` with `loc_59078` alive and `Special_V_int_routine ≠ 0` → routine 0 consumes it on the second swing zero-crossing, sets `Special_V_int_routine = $C` → routine 4 moves the camera 1 px/frame and sets `Events_fg_4+1` at `Camera_Y == $2A0` (7 Chaos or Super emeralds) or `$600` (fewer) → stage 8 (`bmi` ignores negative values): fill tiles `$7F0-$7FF` with `$66666666`, spawn mask `loc_591D6`, `Draw_delayed_position $1F0`, 15 rows, clear `Palette_cycle_counters+0` |

## Native probes

Question-led, disassembly first; BizHawk 2.11 via the shared capture host with a new
`tools/bizhawk/capture_ssz_route_reference.lua` (model on `capture_ddz_route_reference.lua`,
including `plan.slots`/`plan.boss_code`; output to `OGGF_OUT` only, no `print()`). Pass 1 saves a
state near every window from both movies; later probes load them. Record ROM SHA-1, movie SHA-256,
host exit code and the probe's own error status (a failing Lua probe exits 0; verify a process is
stray before killing it). Choose fields and intervals before looking at engine output; break each
new comparison on purpose once.

| Window (fixture rows) | Question |
| --- | --- |
| Arrival (`hpz` 0-200; Knuckles `hpz` 0-200) | Beam phases, player Y/`object_control` release frame, Tails helper, first-frame art and palette, inherited camera fraction from HPZ |
| Cloud band (`hpz` ~`$5DC`-`$BB8`) | BG mode switch frames, layer scroll values per band, `_unkEE9C` phase, solid-cloud Y |
| GHZ arena (`hpz` ~`$1100`-`$1500`) | Lock frame and condition, music fade, ball swing cadence, flag writes on defeat |
| Wrap crossing (`hpz_2` ~`$BB8`) | Camera/object/ring behaviour across Y `0 ↔ $1000` |
| MTZ arena (`hpz` rows with cam `$1660,$380`) | Orb cadence, laser timer, palette lines |
| Mecha Sonic + launch (`hpz_3` `$5DC`-end) | Spawn frame, RNG seed, routine sequence, results, crumble timing, hot-swap frame, BG V-scroll, exit request frame |
| Act 2 (Knuckles `hpz` 0-`$2904`) | Camera settle, crane cutscene, phase change, palette rotation, defeat/save frame, first `sub_5B18E` frame |

## Demo and reel plan

Clips (renumber as needed): `00a/b` broken baselines; `01` arrival beam (+ Tails); `02a` sky
parallax, `02b` cloud band and mode switch, `02c` animated tiles, `02d` roaming and solid clouds;
`03a-k` one per traversal object, `03l` EggRobo; `04` death/checkpoint and Y-wrap; `05a-c` GHZ
arrival, fight, defeat + pad; `06a-c` MTZ; `07a-c` Mecha Sonic arrival, attacks, defeat + results;
`08a` crumble and shake, `08b` Death Egg reveal, `08c` ramp run and `$B00`; `09a` Knuckles arrival,
`09b` crane cutscene, `09c` Mecha phase, `09d` Super Mecha + Master Emerald, `09e` defeat and
collapse; `20-23` uncut cold routes (S+T, Tails, Sonic, Knuckles); one wide route per act; `24`
HPZ → SSZ chained handoff. Captures have no audio: SFX/music claims are test-backed, not shown.

Reel (`gameplay-highlights`, scripts copied from DDZ): act 1 in route order — arrival → clouds and
objects → GHZ → climb/wrap → MTZ → Mecha Sonic → launch and exit — then act 2 arrival → cutscene →
both phases → collapse. Delivered revision only, one example per feature, recorded speed,
nearest-neighbour integer scaling, labels state positioned vs cold. Re-read state CSVs after every
route change. Uncut cold runs stay in the archive as traversal evidence.

## Acceptance matrix (seed for `s3k-ssz-act1.md` / `s3k-ssz-act2.md`)

Act 1 rows: load/identity, title card (no act number), arrival (Sonic, S+T, Tails), HPZ handoff,
cutscene Knuckles + button `$AF` + bridge `$77`, placement census,
Y-wrap, dynamic bounds, BG sky, BG clouds, BG mode transitions, cloud sprites, solid clouds, AniPLC
(per script), each object `$74-$7F/$AF/$79`, EggRobo, starpost/respawn, GHZ lock/fight/defeat/pad,
MTZ lock/fight/defeat/pad, Mecha spawn/fight/defeat, results + save, crumble, hot-swap, Death Egg
BG, debris, ramp script, `$B00` request, DEZ presentation (blocked). Act 2 rows: load/identity,
arrival, camera controller, BG parallax, column waves, arena floor patch, crane cutscene, Mecha
phase, forced run, Super phase, Master Emerald, palette rotation, defeat/save, post-defeat floor patch, ending island mask (seeded),
emerald/water cycles (ending-gated), ending (blocked). Columns: the five claims with command,
commit, configuration, setup, result, skips, limits; products: character/team × form × width ×
donor. Rewind spots (before/active/after, restore equality + forward replay): beam intro, BG mode
switch, carrier/cloud ride, each collapse, Y-wrap frame, each arena lock, each boss mid-attack and
defeat, results, mid-crumble, hot-swap frame, ramp script, crane grab, phase change, final hit;
timeline isolation for death reloads and the `$B00`/ending loads.

## Validation and delivery

Focused tests and `run_categories.py --category NAME --run` during slices via `maven_queue.py`
with `-Dmse=off` and absolute ROM paths (wrong paths skip silently: inspect skips; under fish pass
`-D` flags individually). Mandatory S3K checks stay green: `TestS3kAiz1SkipHeadless`,
`TestSonic3kLevelLoading`, `TestSonic3kBootstrapResolver`, `TestSonic3kDecodingUtils`; plus the
HPZ and DDZ suites after any teleporter, beam, camera-gradual, runtime-state or render-mode change.
One combined `run_categories.py --base <pinned base> --run` at delivery after focused fixes and
docs; state class count, cost (~24 + 10 min) and stopping rule first; launch detached, no tree
edits during the run; acknowledge the run. Shared-owner changes (camera wrap, sidekick CPU, mutation
pipeline, pattern animator, scroll provider) make this normal change-based validation. Attribute
red to a matched baseline in this tree. Strict replay: record each fixture's frontier (command,
commit, errors, first error frame/field) in `docs/status/trace-frontier-log.md`; clean build after
merges before measuring; never build in another lane's worktree.

Docs at delivery: both matrices and the coverage backlog, this plan's status/evidence,
`CHANGELOG.0.7.md`, `s3k-known-bugs.md` for gaps, agent-workflow README for promoted probes,
lessons into existing catalogues, ssz-analysis corrections. Seven trailers per commit; no
`--no-verify`; never `git stash`; start git chains with an explicit `cd`; check `git log -1` after
each commit. Integrate to develop once when the campaign is complete (both acts), then push and remove the
worktree.

## Cross-campaign coordination

Written together with the [LRZ](2026-09-17-lrz-bring-up.md), [SSZ](2026-09-17-ssz-bring-up.md) and
[S3K DEZ](2026-09-17-s3k-dez-bring-up.md) plans; the three campaigns can run in parallel worktrees.

- **Trace directories in `s3k-sonic-tails-complete-emeralds` are named one zone off.** `lrz` = LRZ1/2
  (`$1600` handover at row 38817), `hpz22` = LRZ3 autoscroll, `hpz22_2` = LRZ3 boss → `$1601` → SSZ,
  `hpz*` = SSZ (`zone_id 10`), `ssz*` = DEZ (`zone_id 11`), `dez23_8` = `$1700`, `zone0c` = DDZ,
  `ddz` = ending. Always select by `zone_id`/`zone_act_state`. Whichever campaign starts first records
  this table once in `docs/status/trace-frontier-log.md`; renaming fixtures is a separate task.
- **One shared edit:** `Sonic3kScrollHandlerProvider` maps zones `$16` and `$17` to `hpzHandler` without
  the act. LRZ (`$1600`) and DEZ (`$1700`) both need it act-keyed; the first campaign to land makes the
  provider act-aware for both zones and keeps `$1601` on `SwScrlHpz`; the second rebases onto it.
- **Handoffs:** LRZ owns `$1600` → `$1601` (closes HPZ's entry dependency). SSZ owns the HPZ teleporter
  arrival and the `$A00` → `$B00` request. DEZ owns the `$B00` arrival presentation, `$1700` and the
  `loc_803D6` → `$C00`/`$D01` branch (closes DDZ's seeded-entry caveat). `$D01` and the Knuckles ending
  stay with the ending campaign. A handoff is verified by the requesting side as a request plus load
  attempt, and by the receiving side from a cold chain once both exist.
- **Act3 carry (informational for SSZ).** LRZ2 → `$1600` and DEZ2 → `$1700` (`loc_7F310`) both use the
  `Act3_flag`/`Act3_ring_count`/`Act3_timer` carry: one shared engine owner, built by whichever of LRZ or
  DEZ lands first. SSZ does not use it.
- **Clock-seeded RNG/aim** (`V_int_run_count`: Mecha Sonic, DEZ turrets as in DDZ) needs a declared
  seed for movie-route matching until the full cold chain supplies it; label such evidence seeded.
- **Shared ring-sentinel fix, owned by LRZ (`3418eba6e`).** Every S3K ring list begins with a
  `(0,0)` record that `Load_Rings` (`loc_E8BE`) always steps over, because its scan starts at
  `max(Camera_X - 8, 1)`; the engine's window floor is `max(cameraX - 8, 0)` and so spawned it as a
  real ring wherever the camera reaches X 0 — which SSZ1 does once the cutscene bridge retracts.
  The LRZ branch fixes it in shared `Sonic3kRingPlacement` and it reaches this branch at merge
  time, dropping one ring from every SSZ act's live set. **SSZ does not make this edit** (it would
  duplicate the fix). The SSZ obligation is that no test pins the sentinel-inclusive number
  silently: `TestS3kSszPlacementCensus#ringRecordsMatchTheRomIncludingTheLeadingZeroRecord` now
  decodes `SSZ1_Rings`/`SSZ2_Rings` from the ROM itself, asserts the collectible totals the ROM
  allows (**179** act 1, **0** act 2), and accepts the loader either matching those already or
  carrying exactly one leading `(0,0)` sentinel, which it asserts *as* a sentinel with the message
  naming `3418eba6e`. The test is correct on both sides of the merge and turns red if the extra
  record is ever anything but that sentinel. The slice 0 gap entry in
  [s3k-known-bugs](../../status/s3k-known-bugs.md) closes when the merge lands.
- **Knuckles trace testing is out of scope (user decision 2026-09-17).** One Knuckles replay class
  exists (`TestS3kKnucklesLbz2BigArmTraceReplay`); the `s3k-knuckles-complete-superemeralds` run has no
  segment classes. A campaign may add one where cheap, but owes no Knuckles replay frontier; Knuckles
  rows rest on authored routes and native probes from that movie.

## Open questions (with kill conditions)

Resolved while hardening (2026-09-17, read in `sonic3k.asm`):

1. MTZ fight location: fixture `hpz`, 1004 locked rows (closed while planning).
2. Respawn after a beaten boss: **not skipped by state.** `LevelSetup` clears `Events_bg+$00..$0F`
   on every load; the starposts sit past each arena. Slice 4 asserts it and checks `hpz_2` row 0.
3. `Ending_running_flag`: set at `SSZ2_ScreenEvent` stage 4, whose only trigger is the defeat
   routine's `st Events_fg_4+1` (`loc_7BCB0`). It is **after** the fight; pause is normal during it.
4. Act 2 AniPLC: **none.** `Offs_AniFunc` → `AnimateTiles_NULL` = `rts`.
5. Knuckles replay classes: out of scope by user decision; optional in slice 10.
6. Tails-specific launch branch: `sub_57FE2`/`sub_58048` process Player 2 only when
   `Player_mode == 0`; `Player_mode == 2` passes `d3 = -1` for Player 1; only Player 1 issues `$B00`.

7. `d3` in `sub_5806E`: Tails gets Y `+4` and priority `$100` (`$80` when `d2 ≥ 0`); same frames/DPLC.
11. Super Mecha music is `mus_DDZ` (`loc_7B996`, act-2 first defeat, with the Master Emerald child).
13. `Scroll_lock` is cleared by the arrival controller (`loc_57D3C`), not the bridge; the bridge
    writes the bounds itself (Verified ROM values).
10. (mostly) EggRobo nibbles and `_unkFA82`: a fly-by → fighter pairing gate (`sub_91914`); still open:
    `Perform_Art_Scaling` inputs.

Still open — the named slice must resolve each **before** building on it:

| # | Question | Kill condition | Slice |
| --- | --- | --- | --- |
| 8 | Does the ring manager treat the leading `(0,0)` ring record as a ring? | Read `Load_Rings` init and `Sonic3kRingPlacement`; compare ring counts with native `Ring_count` reachable total | 0 |
| 9 | `$75`, `$76`, `$7B`, `$7C` bit-7 / low-bit subtype meaning | Read each object's init; add to the inventory's semantics table | 3 |
| 10 | EggRobo `Perform_Art_Scaling` inputs (`$40/$41`, `loc_91526`) and fly-by path | Read `loc_91526` and the nibble-0 init | 3 |
| 15 | What is in the `_unkFAA4` slot during the launch (the beaten Mecha Sonic object's state) and how it renders while `sub_5750C` carries it | Read the `Obj_SSZEndBoss` act-1 defeat tail to `End_of_level_flag` | 8 |
| 12 | `CutsceneKnux_SSZ` routine list, art/DPLC readiness at beam time, and what clears `_unkFAA4` before Mecha Sonic writes it | Read `loc_65730`…`loc_6594A`; check `PLC_32_33_34_35`/Knuckles cutscene art queue | 1b |
| 14 | Wide-viewport behaviour of exact-equality locks (`Camera_X == $160/$1660`) | Decide from the camera clamp maths, record the presentation choice; gameplay geometry stays native | 5 |

## Status

Last updated 2026-09-18 after slice 5, at `0d66b1434` on `feature/ai-ssz-bring-up`.

| Claim | State |
| --- | --- |
| Implemented | Slices 0, 1, 1b, 2, **3**, **4** and **5** delivered. All 154 act-1 slice-3 placements resolve to concrete classes (`$74 $75 $76 $79 $7A $7B $7C $7D $7E $7F $A0`); the census ratchets act 1 to zero unimplemented families and act 2 still owes `$B2` (slice 9). On top of that: the placement census, runtime state, screen init, the whole `sub_575EA` bounds machine **including** the Green Hill allocation at `loc_576E8`, the arrival controller and beam, the Tails helper, the Knuckles/Death Egg/button/bridge cutscene with its pseudo-starpost, the act-1 background, the death and checkpoint lifecycle, and `Obj_SSZGHZBoss` with its six-link chain, emitter and Mecha Sonic head. Slices 6-11 are not started |
| Cold-reachable | Act 1's arrival and cutscene run from a cold load and open the route: the bridge clears `Events_bg+$05` and the camera limits become `0 … $19A0`. `TestS3kSszColdRoutes` carries the recorded movie input to X `$6EB`, past the bridge and the arrival ledge, short of the `$7B` cluster at `$740`. Everything past that — the traversal families, both arenas, the lifecycle — is exercised from **declared** star-post entries, not from a route. `SSZ1_ScreenInit` is why: with no star post it drags the leader back to the arrival column, so a walked approach to anything past the bridge does not exist yet. Act 2 still loads with no events |
| Rewind-verified | Spots: the cloud band (slice 2), six slice-3 families, the `$7E` debris **deletion** (the first SSZ spot where an `ObjectRefId` sidecar is load-bearing, because the children really are gone at the restore), the death reload's timeline isolation, and the Green Hill fight mid-swing with all six chain links out. Each was broken on purpose once. Still owed: a spot mid-arrival or mid-cutscene, and any spot at a wide viewport |
| Native behaviour matched | Three places. The act-1 background layout is decoded from the ROM and matched column for column against the engine's layer. Fixture `hpz` row 0's arrival values match `SSZ1_ScreenInit`/`loc_57D50` exactly, with a one-frame phase difference recorded as open. Fixture `hpz_2` is itself a `$34:$03` restart and its row 0's player `($14C0,$EC)` and camera `($1420,$8C)` are matched exactly. **Nothing else is.** In particular no boss, no traversal family and no EggRobo is compared against a native row, and the `hpz` fixture's Green Hill arena window (camera `$160,$7C0`, 1142 rows) is unread. There is still no SSZ native probe |
| Visually matched | Seventeen clips, `01`-`17`, each cut from a raw capture whose frames were read before cutting; the frames looked at are in `~/Videos/OGGF/ssz-bring-up/INDEX.md`. Every slice-3 family, the death and restart, and the Green Hill fight are filmed. Not filmed: the EggRobo's nibble-0/nibble-2 pairing in one shot (the obstacle is measured in INDEX.md), a wide-viewport row for anything past slice 2, and known bug #41's before/after — which was taken and shows **no pixel difference**, reopening the rendered half of that fix |

Out of scope, recorded as dependencies: DEZ presentation/route after `$B00` (DEZ campaign, which
also owns the mislabelled `ssz*` fixtures); `sub_5B18E`, `Obj_Ending`, credits and the Knuckles
good/bad ending (ending campaign); Sonic's post-credits Mecha/EggRobo scenes.

## Evidence log

### 2026-09-17 — slice 0: baseline and identity

Worktree `.worktrees/ai-ssz-bring-up`, branch `feature/ai-ssz-bring-up`, base develop `035e48a58`.
ROM by absolute path: `.worktrees/ai-ssz-bring-up/s3k.gen` (symlink, SHA-1
`CFBF98C36C776677290A872547AC47C53D2761D6`). All Maven through
`python3 tools/testing/maven_queue.py -Dmse=off …`.

**Placement census.** New `src/test/java/com/openggf/tests/TestS3kSszPlacementCensus.java`.
Expectations were derived by decoding the ROM directly (a throwaway Python walk of
`SpriteLocPtrs`/`RingLocPtrs` index `zone * 2 + act`, zone `$0A`) before writing the Java, and they
reproduce the [inventory](../research/s3k-zones/ssz-object-inventory.md) exactly: act 1 213 records
in 66 (ID, subtype) rows, act 2 5 records in 3 rows, union 68 rows (`$79:$00` is the shared row);
pointer targets `$1F90EE`, `$1F95F2`, `$1F9616`, `$1F98E8`; 180 ring records in act 1 (first
`(0,0)`, 179 positioned) and the `(0,0)` record alone in act 2; no record sets Y-word bit 15; the
two wrap-seam `$7D` records store `$103C`/`$104C` and mask to `$03C`/`$04C` at X `$C70`/`$C94`.
All 22 placed IDs resolve to their SKL (SK Set 2) names, not the S3KL `FBZ_*`/`ICZ_*` names the same
numeric IDs carry for zones 0-6 — so the early check "SKL object table resolves `$74-$B2` for zone
`$0A`" passes.

- **Broken on purpose first.** With the act-1 count set to 214:
  `-Dtest=TestS3kSszPlacementCensus` → `Tests run: 7, Failures: 1, Errors: 0, Skipped: 0`,
  `SSZ1 live object records ==> expected: <214> but was: <213>`. Restored:
  `Tests run: 7, Failures: 0, Errors: 0, Skipped: 0`. Zero skips both times, so the ROM path
  resolved and the comparison is live rather than silently absent.

**Media root.** `~/Videos/OGGF/ssz-bring-up/` created with `inputs/`, `native/`, `reel/` and the
DDZ `make_clip.sh`, `make_clips.sh`, `side_by_side.sh`. `raw-00` baselines captured with
`GameplayCaptureTool` on a neutral/right script (`90 -; 180 R; 90 -`), 320 px, 360 frames each,
never to be overwritten:

| Capture | Configuration | Start | Observed |
| --- | --- | --- | --- |
| `raw-00-ssz1-before` | `$A00`, Sonic | `LevelData` `(256,3072)` = `$100,$C00`, camera `(96,2976)` | Sanctuary terrain against a flat blue sky; no cloud background, no arrival, no events (frame 200 inspected) |
| `raw-00-ssz2-before` | `$A01`, Knuckles | `Knux_Start_Locations` `(128,32)` = `$80,$20`, camera `(0,0)` | Static cloud layout; no arrival controller, camera controller or crane (frame 200 inspected) |

**Fixture identity.** The plan's fixture table is confirmed from the committed metadata:
`zone_id 10` with acts 1,1,1 for `s3k-sonic-tails-complete-emeralds/hpz{,_2,_3}` (offsets 448920 /
460334 / 465044, starts `$100,$FAE`, `$14C0,$E8`, `$1880,$968`), acts 1,1 for
`s3k-tails-full-chain-all-emeralds/hpz{,_2}` (423903 / 433476), act 2 for
`s3k-knuckles-complete-superemeralds/hpz` (412501, `$80,$6AE`). The one-time trace-directory
identity table in [trace frontier log](../../status/trace-frontier-log.md) is the **LRZ campaign's**
edit; it is referenced here, not duplicated.

`$100,$FAE` is not a start-location table value: `SSZ1_ScreenInit` forces camera `($60,$F49)` and
`Obj_57C1E` sets Player 1 Y to `Camera_Y + $65` = `$FAE`. Row 0 of the fixture reads camera
`$60,$F41` and player `$100,$FA6` — both exactly 8 px lower, i.e. one `loc_57D50` rise step, which
matches the recorder sampling rows after the frame. The same explains the Knuckles `$80,$6AE`
(`SSZ2_ScreenInit` camera Y `$649` + `$65`). This answers slice 1's "explain the `$100,$C00` vs
ROM-forced camera difference from the ROM before coding": the level-start table is never used on
the no-starpost path, because `SSZ1_ScreenInit` overwrites the camera and the controller overwrites
the player.

**Open question 8 (leading `(0,0)` ring record) — resolved on the ROM side, gap on the engine side.**
`Load_Rings` `loc_E8BE` sets `d4 = Camera_X - 8` and forces `d4 = 1` when that is not above zero,
then advances the cursor while `d4 > recordX`. With a floor of 1 the `X = 0` record is always
stepped over, so the ROM never makes it collectible; it is a list head, not a ring. The engine's
`RingManager.RingPlacement#ringWindowStart` floors at 0, so at `Camera_X <= 8` the record enters
the window — and SSZ1 does reach `Camera_min_X = 0` once the cutscene bridge retracts
(`loc_44FBA`). The margin is shared with the S2 raw window, so this is filed as a shared-owner gap
in [s3k-known-bugs](../../status/s3k-known-bugs.md) rather than patched here. The census test
records the decode either way.

**Deferred from slice 0, with kill conditions.**

| Item | Why deferred | Kill condition |
| --- | --- | --- |
| Native pass-1 savestates and `tools/bizhawk/capture_ssz_route_reference.lua` | The arrival window's native question is already answered by the committed fixture rows above, which are cheaper and stronger than a fresh probe. The probe is built by the first slice that asks it a question the traces cannot answer | A slice needs a field the physics/aux rows do not carry (palette lines, object slots, `_unkFAxx`); then the probe is written and pass 1 saves a state near every window |
| Chaos vs Super emeralds in each movie | The committed manifests carry `emeralds_after` (7 before every SSZ segment in all three runs) but no Chaos/Super distinction and no `Super_emerald_count`; the run *names* claim Super Emeralds for the Knuckles run and Chaos for the others, which is a label, not a measurement. Slices 0, 1 and 1b are form-independent: the arrival and the cutscene run under `object_control 3`/`$83` scripts | Read `Collected_emeralds_array` / `Super_emerald_count` at the SSZ entry frame from a native probe or savestate. Required before any route row claims a form |

**Docs.** Both matrices created
([act 1](../validation/levels/s3k-ssz-act1.md), [act 2](../validation/levels/s3k-ssz-act2.md)) with
the five claims kept in separate columns, and both backlog rows updated in
[level test coverage](../../status/level-test-coverage.md).

**Correction to this plan.** The execution base is `035e48a58`, not `9cba6dbb6` (updated above).
`loc_13A10`'s starpost gate reads `Tails_CPU_star_post_flag`, not `Last_star_post_hit`.

### 2026-09-17 — slices 1 and 1b: runtime state, arrival, act-1 bounds, cutscene

Commit `686824e73` on `feature/ai-ssz-bring-up`. Same worktree, ROM and Maven wrapper as slice 0.

**What landed.** `runtime/SszZoneRuntimeState` (the sixteen `Events_bg` bytes with byte and word
accessors, `Events_fg_4`, both routine words, `_unkEE98`/`_unkEE9C`, `_unkFA84`, `_unkFAA4`,
`_unkFAB8`, and the screen-init latch), registered in `S3kRuntimeStates`, installed by
`Sonic3kLevelEventManager` and added to `currentRuntimeStateUsesThisEventInstance` so a reinstall
cannot zero the bytes or replay the init. `events/Sonic3kSSZEvents` ports `SSZ1_ScreenInit`,
`SSZ2_ScreenInit` and the whole of `sub_575EA`, including the `word_5778A`/`word_5779A` bands and
both arena branches up to (not including) boss allocation. Objects: `SszArrivalControllerObjectInstance`
(`Obj_57C1E`…`loc_57DA2`), `SszTailsArrivalHelperObjectInstance` (`Obj_57DCC`),
`SszCutsceneKnucklesSpawnerObjectInstance` (`Obj_57E34`), `CutsceneKnucklesSszInstance`
(`CutsceneKnux_SSZ`, all eleven routines), `SszDeathEggSmallObjectInstance` (`loc_659CC`…`loc_65A4A`),
`SszCutsceneButtonObjectInstance` (`$AF`) and `SszCutsceneBridgeObjectInstance` (`$77`), plus the
shared `S3kGradualSwing` (`Gradual_SwingOffset`) and `SszCheckpointOps` (the pseudo-starpost).
Art: an `ArtNem_SSZMisc` level entry for the beam and the bridge, `ArtNem_GrayButton` for the
button, `ArtKosM_SSZDeathEggSmall` and the two shared cutscene-Knuckles body sheets.

**Where the screen init runs, and why.** `SSZ1_ScreenInit` is a load-time routine that precedes the
first `Load_Sprites`/`Process_Sprites` pass. Calling it from `installZoneRuntimeState` (the
Doomsday hook) was tried first and **rejected**: the engine's level load positions the camera
*after* the runtime state is installed, so the forced `($60,$F49)` was overwritten and `Obj_57C1E`
placed Player 1 at `Camera_Y + $65` off the level-start camera `$BA0` — the capture read
`(256,3077)` instead of `$100,$FAE`. It now runs from `updatePrePhysics` of the first frame, which
is the earliest hook after the camera is settled and still before the object pass. Consequence,
recorded rather than fitted: frame 1 is `Obj_57C1E`'s init pass (player `$FAE`, camera `$F49`,
`Events_bg+$04`/`+$05` set, no rise step) and frame 2 is the first `loc_57D50` step. The native
fixture's row 0 reads camera `$60,$F41` / player `$100,$FA6`, i.e. one rise step already taken, so
the engine's arrival is **one frame later** than the recorded segment's phase. That is a cold-route
phase question for slice 10, not a bounds or arithmetic difference: every value matches.

**Tests.** `src/test/java/com/openggf/tests/TestS3kSszArrivalHeadless.java` (6 cases, 320 and 800)
and `TestS3kSszKnucklesBridgeHeadless.java` (3 cases). Both were seen red before the code was
right, on real defects rather than on a placeholder: the arrival test caught the missing
`Events_bg+$04`, the un-cleared `Scroll_lock`, a two-frame rise offset and the load-time camera
problem above; the cutscene test caught the spawner's off-by-one allocation frame and the fact that
the bridge never loads while the player stands on the arrival column (`Load_Sprites` only reaches
X `$320` once the camera has moved). Final runs, zero skips throughout:

| Command | Result |
| --- | --- |
| `-Dtest=TestS3kSszArrivalHeadless` | 6 tests, 0 failures, 0 skips |
| `-Dtest=TestS3kSszKnucklesBridgeHeadless` | 3 tests, 0 failures, 0 skips |
| `-Dtest=TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestS3kHpz*,TestS3kDdz*,TestEveryObjectRewindRoundTrip,TestS3kSsz*` | 1391 tests, 0 failures, 0 skips |
| `-Pguards test -B` | 669 tests, 0 failures, 0 skips |

This is focused validation, not a suite pass.

**One real regression, caught and fixed.** Giving `Obj_TeleporterBeamExpand`'s `parent2` word a
`TeleporterBeamOwner` interface so the arrival controller could own a beam broke 35 HPZ tests with
`Missing required object reference` on restore. The rewind identity table only registers
`ObjectInstance`s; making the interface extend `ObjectInstance` restored it. A beam owner is an
object, so this is the right shape as well as the working one — but the failure mode (a typed field
narrowing to an interface silently leaving the rewind graph) is worth remembering.

**Guards.** `Sonic3kObjectProfile` needed `SSZ_ONLY_IDS` (`$77`, `$AF`) for zone 10 once the SKL
registrations existed. Two guard baselines were extended with triage: the new per-object
capture/restore overrides (each exists for a cross-object SST link or a `RewindStateful` holder the
generic schema cannot reach) and the `@RewindTransient` spawn decodes. `S3kGradualSwing` was made
`RewindStateful` rather than baselined, because the helper-state baseline is deliberately empty.
`ObjectLifetimeOps.deleteNoRespawn` replaced six raw `setDestroyed` calls and the sidekick lookups
went through `playerQuery().nativeP2OrNull()`.

**Media.** `~/Videos/OGGF/ssz-bring-up/`:
`raw-01-arrival-sonic-tails` → `01-ssz-arrival-beam-sonic-tails.mp4` (frames 0-300);
`raw-03-knuckles-cutscene-bridge-walk` → `02a-ssz-knuckles-beam-and-death-egg.mp4` (60-460) and
`02b-ssz-bridge-releases-the-route.mp4` (1330-1560). `raw-02-knuckles-cutscene-bridge` is the first
attempt, kept: its input stopped holding right, so the player never walked to the bridge.
Frames inspected: arrival 60 (the beam columns), 175 (Knuckles beamed in over the release point),
250 (both on the ledge); cutscene 350 (the Death Egg rising behind the player), 1200 (the camera
pinned at `Camera_max_X $200` with Player 1 held at X `$328`), 1400 and 1450 (the bridge retracts
and the route opens). `state.csv` confirms the bound: the camera sits at `512` from frame ~600 to
1406 and releases at 1407, which is the bridge's `clr.b (Events_bg+$05)`.

**Gaps filed** in [s3k-known-bugs](../../status/s3k-known-bugs.md): the Death Egg's `Pal_KnuxSSZEnd`
patch, its missile and cloud children, and cutscene Knuckles' unverified resting position.

**Corrections to this plan.**

| Plan said | Actual |
| --- | --- |
| Slice 1 edits `Sonic3kSidekickCpuInitializationPolicy` | Wrong owner. `loc_13AB4`'s `$A00` branch is the one AIZ1 (`$0000`) takes: `sub_13ECA`, routine `$A`, `object_control $83`. The engine models that as `SidekickCpuController.State.DORMANT_MARKER` behind `LevelEventProvider.shouldEnterSidekickDormantMarker`, so SSZ adds a predicate there. `preservesSpawnState` is the *other* branch (`loc_13B18`, SOZ1 and zone `$17`) and SSZ does not use it |
| `Obj_57DCC` ends with `Tails_CPU_routine = 6` | Confirmed, but the existing `releaseDormantMarkerForLevelEvent()` writes routine 2 (catch-up flight). Routine 6 needed a new package-private `releaseDormantMarkerToNormalFollow()` reached through the non-API `SidekickLevelEventRelease`, because `SidekickCpuController` is a `@ModApi` type |
| `loc_13A10` gates on `Last_star_post_hit` | It reads `Tails_CPU_star_post_flag`, which `Tails_Init` copies from it |
| `Obj_57E34` spawns after `$60` frames | Its counter starts on the frame `Obj_57C1E` allocates it (`CreateNewSprite4` runs it in the same pass), so Knuckles appears on frame `$60`, not `$60 + 1` |
| Slice 1b's done-condition "cold route walks off the arrival ledge over the bridge" | Reached, with a caveat: while `Events_bg+$05` is set the camera is capped at `Camera_max_X $200`, so Player 1 runs ahead of the camera and waits at X `$328` for ~800 frames of Death Egg rise before the bridge releases him. That is what the routines say; whether the native movie spends the same time there is unmeasured |

**Still open.** The one-frame arrival phase against fixture `hpz` row 0; the Death Egg's rise rate
(`$40 = -$40` added to the `y_vel` longword is 0.25 px/frame, which is ~900 frames from `$C68` to
above the camera — long, and unconfirmed against native); cutscene Knuckles' resting X; and
`Super_emerald_count` per movie, carried from slice 0.

### 2026-09-17 — slice 2: act-1 background, cloud objects and animated tiles

Same worktree, ROM and Maven wrapper as slices 0-1b.

**What landed.** `scroll/SwScrlSsz` ports `SSZ1_BackgroundInit`, `SSZ1_BackgroundEvent`'s four
routines and both parameter subroutines, registered for zone `$0A` in
`Sonic3kScrollHandlerProvider` (act 2 still falls back to the default handler, slice 9).
`SszZoneRuntimeState` gained the words the machine needs — `Events_bg+$10`,
`Camera_X/Y_pos_BG_copy`, `Camera_X_pos_BG_rounded`, the `HScroll_table+$000` drift longword and
the frame the event last ran for — so a rewind captures the whole background. Objects:
`SszCloudOscillatorObjectInstance` (`loc_57B6A`, the `_unkEE9C` driver),
`SszRoamingCloudObjectInstance` (`loc_57BB2` + `sub_5758A`) and `SszSolidCloudObjectInstance`
(`loc_57B8E`, ten invisible sloped platforms), all allocated by `Sonic3kSSZEvents` in the ROM's
order. `Sonic3kPatternAnimator` resolves `AniPLC_SSZ` (`$28AA4`) for act 1 only.

**Things the ROM decided that the first reading did not.**

| Question | Answer, from the ROM |
| --- | --- |
| Is `Apply_FGVScroll`/`word_577B2` part of ordinary act-1 play? | No. It fills `Vscroll_buffer`, which only reaches VSRAM through `SpecialVInt_VScrollCopy`, and `Special_V_int_routine` is non-zero in Sky Sanctuary only during the Death Egg launch (`SSZ1_ScreenEvent`, sonic3k.asm:115961, 116060). The per-column path is slice 8's, not slice 2's |
| What are the ten `word_5853E` clouds? | Invisible collision only: the rows carry no `mappings` and `loc_57B8E` never draws. The clouds the player sees are the background plane |
| Does each solid cloud's slope table fit its half-width? | No. `SolidObjSloped2` indexes `(dx + $2E) >> 1`, so it reads `$2E + 1` bytes; eight of the ten rows ask for more than their own table holds and run on into the next (address arithmetic: only the two `$40` rows fit, and row 1 reaches into `byte_58658`'s ramp). The engine reads those bytes from the ROM rather than clamping |
| Which clock drives the cloud drift? | `sub_57A60`'s own `addi.l #$500,-4(a1)` on `HScroll_table+$000`, once per `SSZ1_BackgroundEvent`. At `Camera_X $800` the top band's 16.16 value is `$200000`, so it takes 53 calls to carry one pixel — the test pins that arithmetic rather than a frame count |
| Does the `$1800` switch redraw the plane? | No. `sub_579F0` flips `Events_bg+$10`, re-runs itself and re-rounds `Camera_X_pos_BG_rounded`. Only the wrapped-Y `$800`/`$F00` crossing stages a redraw |

**Two engine-shaped problems, both real.**

- *A once-per-frame ROM routine in a render-time hook.* `SSZ1_BackgroundEvent` has persistent
  state, but `ParallaxManager` can compose the same frame twice — a rewind restore re-renders the
  frame it restored. The first version advanced `$500` of drift that no frame paid for, and a
  capture/restore/capture comparison caught it (`zone-runtime.stateBytes[45]: A=38 B=43`, exactly
  one frame of drift). The frame the event last ran for now lives in `SszZoneRuntimeState`, so a
  restore rewinds it too. Worth remembering for any future zone whose scroll handler keeps state.
- *Load-time allocations that outlive the camera window.* All sixteen sky objects were created and
  then unloaded on their first frame: `SSZ1_ScreenInit`/`BackgroundInit` are load-time routines
  whose objects have no `Sprite_OnScreen_Test` at all, but the engine's `MarkObjGone` equivalent
  unloads any non-persistent dynamic object whose position leaves the window. `isPersistent()`
  is the correct opt-out and each class now carries it with the routine that justifies it. The
  diagnostic that settled it printed the constructor results, which showed all ten being created —
  so the fault was deletion, not allocation.
- A third, smaller one: the generic rewind schema cannot restore a `final byte[]` whose length
  differs between instances of the same class. The solid clouds' slope tables are `161` and `385`
  bytes; the field is `@RewindTransient` and `recreateForRewind` re-reads the row.

**Tests.** `src/test/java/com/openggf/game/sonic3k/scroll/TestS3kSszScrollBands.java` (11 cases),
`src/test/java/com/openggf/game/sonic3k/TestS3kSszPatternAnimation.java` (3) and
`src/test/java/com/openggf/tests/TestS3kSszBackgroundClouds.java` (6). Every expectation comes from
the ROM: the scroll test's band walk was worked out by hand from `SSZ1_BGDeformArray` before the
code ran, and the cloud test decodes `word_5853E` from the ROM inside the test rather than trusting
the engine's copy of the table.

- **Broken on purpose.** Four constants were perturbed at once in `SwScrlSsz` — the `$160` near-framing
  Y offset, the second `SSZ1_BGDeformArray` band, the half-step in `sub_57A60`'s last three bands and
  the drift increment. Result: `Tests run: 11, Failures: 5, Errors: 0, Skipped: 0`, naming the framing
  offsets, `HScroll_table word 21` and the accumulator. Restored: 11/11. The band-array perturbation
  alone did **not** fail the original "the background varies down the screen" assertion, which is why
  that assertion was replaced with the seven exact per-line bands the walk predicts.
- The drift test was wrong the first two times and the ROM was right both times: the fan reads the
  accumulator *before* advancing it, and `$500 * 52 = $10400` is the first value to carry, not
  `$500 * 32`. Arithmetic errors in a test expectation look exactly like implementation bugs.

| Command (all `-Dmse=off`, absolute `-Ds3k.rom.path`) | Result |
| --- | --- |
| `-Dtest=TestS3kSszScrollBands` | 11 tests, 0 failures, 0 skips |
| `-Dtest=TestS3kSszPatternAnimation` | 3 tests, 0 failures, 0 skips |
| `-Dtest=TestS3kSszBackgroundClouds` | 6 tests, 0 failures, 0 skips |
| `-Dtest=…,TestEveryObjectRewindRoundTrip,TestRewindHarnessCoverageRatchet` with the four SSZ classes | 1158 tests, 0 failures, 0 skips (1121 of them the per-object rewind round trip) |

Zero skips throughout, so the ROM path resolved and the `@RequiresRom` cases really ran.
This is focused validation, not a suite pass.

**Rewind spot.** `TestS3kSszBackgroundClouds#theSkySurvivesACaptureRestoreAndForwardReplay`
captures at frame 120 — the arrival rise has already carried the camera into the cloud band and
the oscillator is mid-swing — steps one frame, restores, compares the whole composite snapshot,
then replays the same frame forward and compares again. It found both of the engine-shaped
problems above; neither was visible from the object tests.

**Cold route.** Unchanged from slice 1b: the cold frontier is still the bridge release, because
slice 2 adds no traversal. What slice 2 does change is that the route now runs under the real
background — the arrival opens in plain sky, crosses `$F00` into the cloud band during the rise,
and the ten solid cloud platforms and five roaming clouds are alive for the whole of it.

**What `-Pguards` changed about the design.** The first version of the three cloud classes read
their ROM tables through `GameServices` in the constructor and carried the decoded row as `final`
fields with hand-written `captureRewindState`/`restoreRewindState` overrides. Guards rejected all
of it — object classes must use `services()`, and both new rewind overrides and new `final` scalars
are baselined growth — and the rework is better shaped anyway: each cloud now reads its own
`word_5853E`/`word_58758` row lazily on its first frame through `services().rom()`, keyed only by
the row index the spawn's subtype carries, so a spawn-based rewind recreation rebuilds it with no
extra state. The overrides are gone: `S3kGradualSwing` is `RewindStateful` and the remaining fields
are ordinary non-final scalars, so the generic schema captures everything. The lesson worth keeping
is that "read a ROM table in the constructor" is the wrong shape for an object in this engine:
the services and the spawn identity are what the constructor has, and the table is what the first
frame has.

**Media and what the frames actually show.** `~/Videos/OGGF/ssz-bring-up/raw-04-ssz1-sky-320` and
`raw-05-ssz1-sky-800` (Sonic, 420 neutral frames from a cold `$A00` load), cut to
`04a-ssz-sky-and-roaming-clouds-320.mp4` and `04b-ssz-sky-and-roaming-clouds-wide.mp4`. Frames
inspected: 5 (the five roaming clouds are up before the camera has left the plain-sky band), 200
(they have drifted left and bobbed, the sanctuary terrain and the rising Death Egg are in frame).
Against slice 0's `raw-00-ssz1-before` frame 5, which is flat blue with no cloud at all, the
difference is unmistakable.

Two honest limits on that comparison. First, `raw-00-ssz1-before` predates slices 1 and 1b as well,
so it is the campaign baseline rather than the slice-2-only "before" build the method asks for;
a build with only the scroll registration and `spawnBackgroundClouds` disabled is still owed.
Second — and this is an **open question, not a finding** — the background *plane* renders flat sky
in both builds, at both camera framings. The scroll words, the band expansion and the background Y
are all asserted against the ROM and the roaming cloud sprites are plainly there, but nothing in
these frames proves the background layout is being sampled at the new rows at all: it may be that
Sky Sanctuary's background layer really is plain sky over this stretch, or that the layer is not
reaching the screen. Kill condition: read the SSZ background layout rows that
`Camera_Y_pos_BG_copy` selects in each mode straight out of the ROM and compare them with what the
renderer draws, or capture the same camera in BizHawk. Until that is done, "BG: cloud band" is
scroll-verified and not visually verified.

At 800 px the roaming clouds occupy only the left ~460 px, because `sub_5758A`'s `& $1FF` puts every
cloud within 512 screen pixels of the camera regardless of viewport. That is the recorded
presentation consequence of a screen-space ROM constant on a wide viewport; the geometry is native.

**Ring-sentinel expectation, checked on both sides of the LRZ merge (2026-09-17).** The census
ring test now decodes `SSZ1_Rings`/`SSZ2_Rings` from the ROM and asserts the totals `loc_E8BE` can
reach — 179 and 0 — then compares the loader against them, accepting exactly one leading `(0,0)`
sentinel while LRZ `3418eba6e` is in flight. Both branches were exercised: as written it is
`7 tests, 0 failures, 0 skips` against today's loader (sentinel present), and with the loader's
first record temporarily trimmed away in the test to stand in for the post-merge shape it is again
`7 tests, 0 failures, 0 skips`. The simulation was reverted immediately. SSZ makes no edit to
`Sonic3kRingPlacement`.

### 2026-09-17 — slice 3, part 1: the `$79` pads, `$7F` and `$7E`; and two slice-2 carry-overs

Same worktree, ROM and Maven wrapper as slices 0-2. This entry covers the two carry-over items and
the first three inventory families of slice 3; the rest of slice 3 is **not** done and is listed at
the end.

**Carry-over (i): s3k-known-bugs #41, the flat background plane — answered from the ROM, and the
answer is split.** The `$A00` level layout (`LevelPtrs` index `$0A * 2`, ROM `$A458E`) is uncompressed: a
four-word header (`FG cols`, `BG cols`, `FG rows`, `BG rows`) then interleaved per-row pointers
based at `$8000`. Act 1's background is **60 columns by 22 rows** of 128-pixel chunks, so it covers
`Y $000`-`$AFF`. Decoded:

| BG rows | Y range | Distinct chunk ids per row |
| --- | --- | --- |
| 0-2 | `$000`-`$17F` | 1 (a single repeated chunk across all sixty columns) |
| 3-17 | `$180`-`$8FF` | 3 to 15 |
| 18-21 | `$900`-`$AFF` | 2 |

`sub_57A60`'s plain mode frames the background at `Camera_Y + $160`; `SSZ1_ScreenInit` forces
`Camera_Y = $F49`; the `$1000` Y wrap makes that `$10A9 mod $1000 = $A9`, i.e. **background row 1**.
Flat sky over the arrival stretch is the shipped layout, not a sampling fault, and the same holds
for the bottom of the level. `TestS3kSszBackgroundLayout` decodes all of this from the ROM and, in
the case that could have disagreed, compares the engine's background layer with the ROM rows column
for column — 22 × 60 cells. It matches, so the layer is loaded and addressed correctly.

**But that only covers plain mode, and cloud mode has a real defect.** This was corrected after the
entry was first written, from a disassembly reading that was then verified here against
`sonic3k.asm` and the ROM. `SSZ1_BackgroundInit`'s cloud branch (`loc_5786A`), `loc_57946` and
`loc_5799A` each load a literal `move.w #$1C00,d1` before `Refresh_PlaneFull` / `Draw_TileRow`, and
`loc_5799A` passes `moveq #$20,d6` — 32 cells, the full 512-pixel plane B width. `$1C00 >> 7 = 56`
and four chunks tile the plane, so **cloud mode reads layout columns 56-59 every frame regardless of
the camera**; `sub_57A60` never writes `Camera_X_pos_BG_copy`, so that word is stale and all
horizontal motion comes from the `HScroll_table` fan. Decoding those four columns confirms it: rows
3-7 carry chunks `$7A $7B $7C $7D $7E $7F $80 $81 $82 $83 $84 $86`, and every other row of that
window is the sky chunk `$02` or blank `$00`. The engine derives its columns from the camera
(SSZ1's camera X range `0`-`$19A0` is columns 0-52), and at background rows 1-8 columns 0-22 and
43-55 are entirely chunk `$02` — so it draws the right pixels from the wrong columns, and the whole
ascent from about `Camera_Y $E80` down to `Camera_max_Y_pos $BC0` is flat when it should be cloud.

**Fixed.** The renderer site is `LevelManager.applyBackgroundTilemapWindowSelection`, which takes
its window base from `ParallaxManager.getBgCameraX()` — i.e. from the active scroll handler. `SwScrlSsz`
returned the `Integer.MIN_VALUE` "no override", so the window stayed at base 0. It now returns
`$1C00` while the cloud mode is active (both `BG_ENTERING_CLOUDS` and `BG_CLOUDS`, because
`loc_57946` loads the literal too, not just `loc_5799A`) and reports a 512-pixel period there; and
`Sonic3kZoneFeatureProvider.bgWrapsHorizontally()` gained `isSszCloudBackgroundWindowActive`, which
the branch requires before it will relocate the window at all. This is the mechanism that class's
own Javadoc already describes for `SwScrlMgz` state 8, and it names ICZ1's `d1 = $1880` as the other
zone that pins its plane this way — SSZ's `$1C00` is the third. Plain mode keeps `MIN_VALUE`: there
the ROM is camera-derived too (`Reset_TileOffsetPositionEff`).

The red test was seen red on the real defect — `expected: <7168> but was: <-2147483648>` — and the
first attempt was still red because it only matched `BG_CLOUDS`; the entering routine draws with the
same literal, which is what the ROM says and what the second attempt models.

s3k-known-bugs #41 is now narrowed to what genuinely remains: whether plain mode below wrapped
`Camera_Y $800` reaches the separate sanctuary structures the layout holds at columns ~9-52, rows
4-17. No capture has had a camera there, and it may well be correct already.

**Method note.** The first version of this entry closed #41 outright as "correct behaviour plus a
missing observation". That was wrong, and it was wrong in the direction that retires a defect. The
decode that produced it was real and is unchanged; what it could not see is that the two BG modes
index the layout by different rules, so evidence about the row the arrival selects says nothing
about the columns the cloud path reads. A decode that explains the symptom is not yet a verdict on
the routine that produces it.

**Carry-over (ii): the overlapping Sonic and Knuckles at ~3 s of
`04a-ssz-sky-and-roaming-clouds-320.mp4` — ROM behaviour; the box below them was the real defect.**
Read from the routines rather than guessed:

- `Obj_57C1E`'s rise ends with Player 1 at `$FAE - 8 * $6C = $C4E` and stores that in `$3E(a0)`;
  `Obj_57D64` then swings the player around it with `Gradual_SwingOffset(#$20000,#$800)`.
- `Obj_57E34` allocates cutscene Knuckles at X `$100` and writes `$3E(a0) = $C4E`, then swings *him*
  around the same base with the same parameters (`loc_57E64`).
- Both are therefore at X `$100` and within a few pixels of `Y $C4E`, differing only by the phase of
  two swings that start about fourteen frames apart. `loc_65730` gives Knuckles `priority $80`
  against the player's `$100`, so he is drawn in front — which is what frames 160-240 of
  `raw-04-ssz1-sky-320` show. Knuckles then falls, waits, and leaves to the right from routine `$E`
  onward (`x_vel $200`, then `$300` until X `$2A8`), so the overlap is brief and intended.

The placeholder box below them was **not** ROM behaviour: it is the `$79:$00` arrival pad at
`($100,$C70)`, whose factory was bound to `S3KL` or to SKL zone `$16` only, so Sky Sanctuary got a
placeholder. That is fixed below and the box is gone.

**What landed.** `SSZHPZTeleporterObjectInstance` gains the Sky Sanctuary branch of its own ROM
routine, and the `$79` factory predicate now admits SKL zone `$0A` as well as `$16`:

- Init `loc_4554E`-`loc_4556A`: the placement Y moves to `$16(a0)` and the idle routine rebuilds
  `y_pos` from it. A negative subtype is a pad gated on a boss-defeat flag — `add.b d0,d0` puts
  subtype bit 6 into the sign, selecting `Events_bg+$02` (MTZ) over `+$00` (GHZ) — and starts `$20`
  px sunk. Decoding `SSZ1_Sprites` shows only Sky Sanctuary places a negative `$79` subtype (HPZ act
  2 has `$00` and `$4A`, the `$1701` arena none), so this branch never reads another zone's flags.
- `loc_455BA`: `x_pos >= $1A00 && y_pos < $680` is the Mecha Sonic spawner, not a teleporter. It is
  never solid and never launches; the boss allocation is slice 7's and is filed as s3k-known-bugs
  #42 rather than faked.
- `loc_455F8`/`loc_45616`: a sunk pad is not solid at all, and every subtype-0 receiving pad is
  intangible while `Events_bg+$04` is set (the arrival script).
- `loc_45640`: once the gate flag is negative the pad rises one pixel on every fourth
  `Level_frame_counter` tick, and only behaves as a launch pad when it is flush.
- `loc_45744`-`loc_45790`: the SSZ lift is `(subtype & $3F) * $10`, not HPZ's whole subtype. The
  launch writes `Camera_min_Y = -$100`, `Camera_max_Y = Camera_target_max_Y = $1000`, clears
  `Events_bg+$05` and sets `Scroll_lock`; `loc_457BE` clears `Scroll_lock` again at the top.
- `sub_45866`'s zone `$A` branch: one `word_4670C` longword — two entries in table order — into
  palette line 2 colours `$C`/`$D`. HPZ's branch writes the same table to line 3 colours 1-2 in the
  **opposite** order, which is why the two cannot share a write.
- Rendering: `make_art_tile(ArtTile_SSZMisc+$88,0,0)` over `Map_SSZHPZTeleporter`, mapping frame 0.
  SSZ never reaches `loc_455B2`, so the frame stays at the SST's zero rather than HPZ's `$A`.

New `SszFloatingPlatformObjectInstance` (`$7F`, eight placements): `loc_44AA0`'s dip counter in
`$2E(a0)` rising to 4 and falling back one pixel a frame, `y_pos = y_vel + $2E`, `SolidObjectTop`
with `d1 = $2B` — wider to stand on than the `$20` `width_pixels` the init writes.

New `SszCollapsingColumnObjectInstance` + `SszCollapsingColumnDebrisObjectInstance` (`$7E`,
twenty-five placements): the `Gradual_SwingOffset(#$2800,#$80)` bob whose phase comes from an
init-time `Random_Number` draw into `$30(a0)` (the *low* word of the speed longword), the collapse
that allocates eight `word_46618` pieces and plays `sfx_Collapse`, each piece hanging on the column
for its own delay and then falling with a `$3800` accumulator, and `loc_44B98`'s `x_pos = $7FFF`
park once `routine(a0)` is back to zero.

**Something the ROM decided that the first reading did not.** `word_46618` row 6 hangs for a single
frame, and the pieces are allocated after the current slot, so that piece runs in the same object
pass as the collapse: its delay reaches zero immediately and it reports back on the collapse frame.
The column's count at the end of that frame is **seven**, not eight. The test asserted eight first
and was wrong; the ROM was right. The expectation now derives the number from the table.

**Tests.** `TestS3kSszTeleporterPads` (7 cases), `TestS3kSszBackgroundLayout` (3) and
`TestS3kSszTraversalPlatforms` (3). Placements, `byte_466E8`, `word_4670C`, `word_46618` and the
whole background layout are decoded from the ROM inside the tests.

Two pad cases make a **declared seeded** write: `st (Events_bg+$00)`, which is what
`Obj_SSZGHZBoss`'s defeat does, because slice 5's boss does not exist yet. One also seeds
`Events_bg+$06` — `sub_575EA` returns immediately while the final-arena flag is set — to isolate
`loc_4577E`'s own camera writes. Without it, clearing `Events_bg+$05` releases the bounds machine,
which re-derives `Camera_max_Y` from `word_5779A` in the same frame's `ScreenEvents` tail and
replaces the launch's `$1000` with `$C60`. That is the ROM's behaviour too and is recorded, not
worked around.

**Broken on purpose, one perturbation per new comparison.** Four production constants were changed
at once — the `$79` lift mask `$3F` → `$7F`, `GATED_SINK` `$20` → `$18`, the floating platform's
`MAX_DIP` 4 → 6 and `DEBRIS_TABLE_ADDR` `$46618` → `$46620` — and the run went
`Tests run: 10, Failures: 4`, naming the sink position, the rise duration, the dip saturation and
the piece count. All four were restored immediately and `git status` is clean of them.

One honest limitation that check exposed: **the lift mask produced no red on its own.** The three
plain launch pads carry `$15`, `$1E` and `$32`, all below `$40`, so `& $3F` passes them through
unchanged and the launch case could not see the change. A case was added for exactly that —
`theGatedPadLiftUsesOnlyTheLowSixSubtypeBits` launches from the `$AA` pad with its GHZ flag seeded
before the pad's init (so `loc_4556A` skips the sink and it is solid from the first frame) and
asserts `$2A0` of lift, not `$AA0`.

**Media.** `raw-06-ssz1-cloud-window-after` (Sonic, 420 neutral frames from a cold `$A00` load, 320
px), cut to `05b-ssz-cloud-band-after.mp4`, with slice 2's `raw-04-ssz1-sky-320` — the same cold
load at the same width, before the fix — as `05a-ssz-cloud-band-before.mp4` and the labelled
`05-ssz-cloud-band-before-after.mp4`. Frames inspected: 250 and 400 (the layered cloud band fills
the plane, and the `$79` arrival pad draws as a pad where slice 2's capture had the magenta
placeholder box), 60 and 120 (the white column is `Obj_TeleporterBeamExpand` drawn over the cloud
band, not a wrap seam — checked precisely because it looked like one). The honest limit: `raw-04` is
the campaign's slice-2 capture, so it is "before" for the background *and* for the pad, not a
build with only the scroll override disabled.

**Earlier media, and what is still owed.** The evidence
for carry-over (ii) is `~/Videos/OGGF/ssz-bring-up/raw-04-ssz1-sky-320/frames`, re-read frame by
frame against `state.csv`: frame 160 (Player 1 rolling at `(256,3097)`, one red ball on screen —
cutscene Knuckles at priority `$80` covering the player's own ball), frame 180 (`(256,3085)`, the
two separated by the phase difference between their swings, blue quills above the red body), frame
240 (Knuckles has landed and is standing). The magenta placeholder box below them in every one of
those frames sits at world `($100,$C67)` — the `$79:$00` arrival pad — and is what this commit
removes. A capture showing the pad drawn instead of the box, and one with the camera inside
background rows 3-17, are both still owed and are the reason the two visual claims stay open.

**Rows remaining in slice 3.** Implemented: `$79` (all 10 act-1 placements), `$7F` (8), `$7E` (25) —
**43 of the 154** slice-3 placements. Still placeholders, with the lead's notes file
`~/Videos/OGGF/ssz-bring-up/notes/ssz-objects-74-7C-disasm-spec-summary.md` as a secondhand
starting point that must be re-read against the ASM before coding:

| ID | Routine | Placements |
| --- | --- | --- |
| `$74` | `Obj_SSZRetractingSpring` `$46426` | 5 |
| `$75` | `Obj_SSZSwingingCarrier` `$460D8` | 8 (`$00`×5, `$80`×1, `$82`×2) |
| `$76` | `Obj_SSZRotatingPlatform` `$45DAE` | 7 (`$00`×3, `$01`×4) |
| `$7A` | `Obj_SSZElevatorBar` `$4536C` | 5 |
| `$7B` | `Obj_SSZCollapsingBridgeDiagonal` `$44D60` | 35 (`$00`×31, `$80`×4) |
| `$7C` | `Obj_SSZCollapsingBridge` `$44C44` | 8 (`$00`×7, `$80`×1) |
| `$7D` | `Obj_SSZBouncyCloud` `$450C8` | 27 |
| `$A0` | `Obj_EggRobo` | 26 across 24 subtype rows |

Also owed for this slice: the census test's concrete-class assertions (the file was being edited by
another lane while this work ran, so it was left alone); the rewind spot; the 320-plus-wide and
donor breadth rows; a cold route that actually reaches a pad past the bridge; and the demo clips.
**Open questions 9 and 10 are answered** by two disassembly readings delivered after this entry's
implementation work, relayed through the lead. They are secondhand: re-read each cited line before
writing a test from them. They have not been used to write any code in this entry.

- **9, subtype bits.** `$7C` and `$7B` read **only bit 7** (`tst.b subtype / bmi`), and set means
  *never collapses*; bits 0-6 are unread. `$75` reads bit 7 (clear = a `$30`-wide pendulum bar on
  `Gradual_SwingOffset(#$20000,#$821)` + `$41`; set = a width-8 hub rotating ±1 per frame by status
  bit 0, which also forces the player's facing on grab and enables a `SonicOnObjHitFloor` release at
  `x_vel ±$800`) **and bits 0-1** as the arc's chain length, `6 + (subtype & 3)`. `$76` reads
  **only bit 0**, and only inside its invisible carrier child, selecting `width_pixels $60` or
  `$A0` (solid half-width `$6B` or `$AB`). `$74` and `$7A` read no subtype at all. The only ROM
  clock in that whole set is `(Level_frame_counter+1)` bit 0, gating `$74`'s proximity scan to
  alternate frames.
- **10, `Perform_Art_Scaling`.** Inputs are `$40(a0)` (the scale byte, clamped to `$1C` by
  `sub_2468A` and written into `mapping_frame`), `anim(a0)` (source `+= anim * $1000` in
  `sub_246DA`), `$42(a0)` (the scaled-art base, `ArtScaled_EggRoboFly` `$17B6E0`), `art_tile(a0)`
  (offset by the running VRAM slot `_unkF740`) and `$3A(a0)` (the DMA destination). `word_2464A`
  (`$2464A`, 32 words) is the per-level VRAM cell budget; over `$80` the object is skipped for the
  frame. The fly-by's scale falls `$7F, $7C, $79, …` three per frame and sticks at 4.

**Two corrections to this plan's EggRobo summary, from the same reading.** The plan's rules box and
slice-3 row say low nibble 4 is "the shooter, gate `V_int_run_count+3 & $F`". Both halves are wrong:
low nibble **4 is the animal releaser** (`loc_918FC` → `loc_915F6` → `loc_917C0` → `Obj_Animal`),
which releases one animal every sixteen frames — *that* is what the `V_int_run_count+3 & $F` gate
times — and converts into an ordinary fighter after five releases. The laser belongs to the
fighter's gun child and is gated on `|y_pos(EggRobo) - y_pos(Player_1)| <= 8` (`cmpi.w #8,d3 /
bhi`), with a `$5F`-frame cooldown and a `$38` bit-1 handshake; there is no shot object id, the shot
is the bare code pointer `loc_91756`. `_unkFA82` is confirmed: a word bitmask at `$FFFFFA82`, bit
index `subtype >> 4`, set by the fly-by at `loc_91570` and read only by the fighter's `sub_91914`,
never cleared by EggRobo code — so the engine must clear it at level load. The `$79` spawner
condition `x_pos >= $1A00 && y_pos < $680` is confirmed as this entry implemented it.

`src/test/java/com/openggf/tests/TestS3kSszPlacementCensus.java` carries the same wrong "4 shooter"
comment on its `$A0` rows; it is corrected here, without touching the assertions.

| Command (all `-Dmse=off`, absolute `-Ds3k.rom.path`) | Result |
| --- | --- |
| `-Dtest=TestS3kSszTeleporterPads` | 8 tests, 0 failures, 0 skips |
| `-Dtest=TestS3kSszBackgroundLayout` | 3 tests, 0 failures, 0 skips |
| `-Dtest=TestS3kSszTraversalPlatforms` | 3 tests, 0 failures, 0 skips |
| `-Dtest=TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestS3kHpz*,TestS3kDdz*,TestS3kSsz*,TestEveryObjectRewindRoundTrip,TestRewindHarnessCoverageRatchet` | 1435 tests, 0 failures, 0 skips |
| `-Pguards test -B` | 669 tests, 0 failures, 0 skips (after two baseline updates, below) |

Zero skips throughout, so the ROM path resolved and the `@RequiresRom` cases really ran.
`TestEveryObjectRewindRoundTrip` went from 1121 cases to 1124 — the three new object classes — so
each of them has a probe construction and a captured-state round trip. This is focused validation,
not a suite pass: no change-based category run is attributed to this entry.

**Two guard baselines moved, both for the reason the guard asks for.** `-Pguards` first failed on
exactly two things. `TestSonic3kObjectProfileRegistryGuard` wanted zone 10's implemented-id list to
gain `79 7E 7F`, which is the transcription the guard exists to force; `SSZ_ONLY_IDS` now carries
them with `$79` commented as the shared-with-HPZ class. `TestRewindCoverageGuard` reported
`SszCollapsingColumnDebrisObjectInstance#objectRef#column` as a *new* gap — and the coverage
baseline file is empty, so baselining it would have been wrong. The debris does capture its column
as an `ObjectRefId`; what was missing was the `DefaultObjectRewindPolicies` entry that tells the
audit so, exactly as `SSZHPZTeleporterObjectInstance#beam` and `TeleporterBeamObjectInstance#parent`
already do. The rewind architecture guard's override baseline gained the debris pair with the same
"cross-object SST link" triage as the slice-1 classes.

### 2026-09-18 — slice 3, part 2: `$7C`, the shared bridge debris, and a capture-tool flag

**`$7C` `Obj_SSZCollapsingBridge`** (8 placements) and the `loc_45052` debris both families use.
Re-read in `sonic3k.asm` rather than taken from the relayed spec: init `bset #2,render_flags`,
`height_pixels $10`, `width_pixels $20`, `priority $180`,
`make_art_tile(ArtTile_SSZMisc+$20,2,1)` over `Map_SSZCollapsingBridge` — the same sheet, tile base
and palette line `Obj_SSZCutsceneBridge` already uses, so no new art entry was needed.

**Subtype, now verified rather than assumed.** `tst.b subtype(a0)` / `bmi.s loc_44C96` is the only
read: bit 7 set means the section never collapses, bits 0-6 are never read. The ROM places seven
`$00` and one `$80`, and the test asserts both the split and that no placement sets a bit the
routine ignores — so the two rows really are the whole behaviour space.

`loc_44C9C` records which side the player is on in `$2E(a0)` (`scc` after `cmp.w x_pos(a1),d4`) and
lays four pieces from that side inward: first at `$18` px toward the player, stepping `$10` back,
mapping frames alternating through the `$102` word, hang delays 6/12/18/24. `loc_44D22` then
shrinks the solid half-width from `$20` by 8 every sixth frame, walks the object 8 px away from the
player each step so the near edge retreats, and parks it at `x_pos $7FFF` — plus that same 8 px
step, so the parked X is `$7FF7` or `$8007`, which the test asserts as a range rather than the bare
literal. The section stays solid at the shrinking width the whole time.

**A capture-tool flag, because the clips could not be taken without it.** `GameplayCaptureTool`'s
`--x/--y` teleport is overridden outright in `$A00`: `SSZ1_ScreenInit` forces the arrival camera and
`Obj_57C1E` writes Player 1 to `Camera_Y + $65`, so every positioned SSZ capture snapped back to the
arrival column by frame 58 — visible in the first attempt's `state.csv`. The ROM's own answer is the
star-post branch, so the tool gained `--star-post`: declared capture setup, in the same block as
`--emeralds`, `--vint-run-count` and `--camera-x-sub`, writing `Last_star_post_hit` with `Saved_X/Y`
at the requested start. It is generic — any act with a star-post-gated intro needs it — and it is
what makes slice 4's respawn captures possible too.

**Clips** (each cut with at least 30 frames of lead-in and lead-out):

| Clip | Raw | What the frames show |
| --- | --- | --- |
| `05-ssz-cloud-band-before-after.mp4` (+ `05a`/`05b`) | `raw-06-ssz1-cloud-window-after` vs slice 2's `raw-04-ssz1-sky-320` | The cloud band fills the plane where the before is flat blue |
| `06-ssz-floating-platform-dips.mp4` | `raw-07-ssz-floating-platform` | `$7F` at `($600,$C20)` carrying the player; frame 150 inspected |
| `07-ssz-column-breaks-into-eight.mp4` | `raw-08-ssz-collapsing-column` | `$7E` at `($600,$960)` breaking; frame 90 shows the pieces falling under the player |
| `08-ssz-teleporter-pad-launch.mp4` | `raw-09-ssz-teleporter-pad-launch` | `$79:$15` at `($1000,$7B0)` launching; frame 200 shows the rolled player mid-lift |

The `$79` pad also shows drawn rather than as a placeholder box in `raw-06` frames 250 and 400.
One placeholder box remains visible in `raw-07` and `raw-08`: the families this entry has not
reached yet.

**Tests.** `TestS3kSszTraversalPlatforms` grew to 5 cases, `TestS3kSszTeleporterPads` 8,
`TestS3kSszBackgroundLayout` 5 — 18, 0 failures, 0 skips.

**HUD legibility over the fixed cloud band — checked, not a defect.** Reviewing clip `05`, the
"after" half's bottom-left lives counter reads washed out and the SCORE/TIME text has cloud pixels
between its glyphs, where the "before" HUD looks solid. Two candidate causes were separated by
measurement rather than by eye:

- *Does the engine let the background occlude the HUD?* No. Comparing the raw PNGs at frame 250,
  **zero** glyph-coloured pixels differ between before and after — 815 unchanged in the
  SCORE/TIME/RINGS block and 364 in the lives counter, with the dark outline colour
  `(36,36,36)` and the yellow `(255,255,0)` at identical counts in both. Every pixel that changed
  was background. The HUD is drawn intact.
- *Could the cloud chunks legitimately occlude it?* Also no, and this is the part worth pinning.
  On the hardware the order is low planes, low sprites, high planes, high sprites, so a Plane B
  tile with bit 15 set really can cover a low-priority sprite.
  `TestS3kSszBackgroundLayout#noCloudWindowChunkCarriesAHighPriorityTile` walks every pattern
  descriptor of every distinct chunk in the cloud window's rows 3-7 through the engine's decoded
  tables and asserts none sets the priority bit. They are all low priority.

What actually changed is what sits *behind* the HUD's transparent gaps: flat blue before, white
cloud after. White text on white cloud reads as low contrast. That is the cartridge's own
composition — the background is correct now, and the HUD is unchanged — so nothing is filed as a
gap. It is recorded here because "the HUD looks wrong after a background fix" is exactly the shape
of report that invites a renderer change that would be wrong.

### 2026-09-18 — slice 3, part 3: `$7B`, the largest family

**`$7B` `Obj_SSZCollapsingBridgeDiagonal`** (35 placements: 31 `$00`, 4 `$80`). Same bit-7-only
subtype as `$7C`, same `Map_SSZCollapsingBridge` art, same `loc_45052` debris — eight pieces this
time, from `$38` px toward the player stepping `$10` back, frames alternating through the `$405`
word, delays 6 to 48, with a vertical stagger that flips every other piece (`btst #0,d5`).

Three things the routine does that a summary would lose:

- The solid is `SolidObjectTopSloped2` over `byte_46658` with `d1 = $40`, one signed byte per two
  pixels. The index reaches `$40`, so it reads `$41` bytes and the last one is past the table's end
  — `byte_46698[0]`. The engine reads them from the ROM at the live pointer rather than holding a
  clamped copy, so the shipped over-read is what the geometry is. Same hazard as the solid clouds.
- `loc_44D98` snapshots `status(a0)` *before* the solid call and then re-grounds each player who was
  standing and is now airborne and not jumping (`bclr #Status_InAir`). Without it the slope drops
  the player it just carried.
- `loc_44EBA` advances the slope pointer four bytes as well as shrinking the half-width by eight,
  so the visible surface slides along the diagonal while it retreats, and the object steps two
  pixels vertically to keep its centre on the slope.

`mainspr_childsprites` is never set despite the multi-sprite render flag, so the object draws no
sub-sprites. Faithful, and recorded so nobody goes looking for a missing chain.

**Clip** `09-ssz-diagonal-walkway-collapses.mp4` from `raw-10-ssz-diagonal-walkway`; frame 75 shows
the player standing on the sloped walkway before it goes.

**That capture also settled the open plain-mode question**, from the opposite direction to the one
hoped for. It sits at camera `($6A0,$550)` — wrapped `Camera_Y` far below `$800`, so plain mode
throughout and the cloud window never engaged — and the background is flat blue, while the layout's
row 13 carries thirteen distinct chunk ids across columns ~9-52. So s3k-known-bugs #41 moves from
"unverified" to an observed defect with a frame. The cause is *not* established and is deliberately
not guessed: `plainParameters` does produce `Camera_X + $28` (layout column 13 here, inside the
cluster), and `getBgCameraX()` returns `MIN_VALUE` by design so the column is meant to come from
the scroll word as it does in every non-wrapping zone. Which of the two is not lining up is the open
question, and the cloud-window change is not implicated because this capture never enters cloud mode.

**Tests.** `TestS3kSszTraversalPlatforms` 7 cases, `TestS3kSszTeleporterPads` 8,
`TestS3kSszBackgroundLayout` 6 — 21, 0 failures, 0 skips. `-Pguards` 669, 0 failures, 0 skips.

### 2026-09-18 — slice 3, part 4: the five remaining traversal families, the EggRobo, and #41's cause

Commits `2bdca80eb` (traversal) and `ac93480de` (EggRobo + census ratchet), on
`feature/ai-ssz-bring-up` over `cbb66adca`.

**`$7D` `Obj_SSZBouncyCloud`** (27 placements, all subtype 0). Three things a summary loses:

- `routine(a0)` is not a routine. It is a shared bounce counter: the first player to land sets it
  to 7, it decrements once a frame in the draw path, and a second player who lands while it runs
  takes the current value, so two riders leave together rather than on their own clocks.
- The Y term at `loc_450EC` is a two-branch pick. If either player's sag byte is non-zero it takes
  the **unsigned** larger of the two and only then sign-extends it — so `$F8` beats `$12` and then
  applies as `-8`. Otherwise it takes the recoil byte belonging to whichever player has the larger
  *countdown index*, not the larger recoil.
- The post-launch angle spin-down at `loc_4527A` adds or subtracts 6 while the angle byte reads on
  one side of the sign boundary and 2 after it crosses, in both directions, and both paths take
  exactly 80 frames to reach zero. `byte_466A0` only covers 38 of them, sticking at index 0.

The four `word_466C8` puffs are allocated at the player's X, and `loc_452DA` is a cull, not an
init: a puff outside `cloud X ± $18` deletes itself on the pass it was allocated for, so a bounce
off the end of a cloud sheds fewer than four.

**`$7A` `Obj_SSZElevatorBar`** (5, subtype 0). The throw's Y velocity is the bar's own
`Gradual_SwingOffset` speed longword shifted down eight plus `-$400`, so a bar caught on the way up
throws harder than one caught at the bottom; holding down drops the `-$400` and the player simply
falls with the bar. Left leaves the bar's priority at `$80`, in front of the player; right and the
neutral release put it back to `$180`. The hang offset is `$14` except for `Player_mode == 2`
(Tails alone) and Player 2, which get `$11`.

**`$76` `Obj_SSZRotatingPlatform`** (7: three `$00`, four `$01`). Bit 0 is the only subtype bit any
part of it reads, and it only widens the invisible `loc_45F10` carrier from `$60` to `$A0`.
`sub_45E6E` is a two-step latch: the frame a player first stands, it writes `$0100` and does
nothing else, so the spin starts a frame late. The carrier's priority for a rider is a four-way
pick on the pose angle's sign *and* on whether the other player is riding at a smaller radius.

**`$75` `Obj_SSZSwingingCarrier`** (8: five `$00`, one `$80`, two `$82`). Two subtype fields, both
read: bit 7 picks pendulum (`Gradual_SwingOffset($20000,$821)` biased `$41`) or rotator (one step a
frame, reversed by the placement's X-flip bit); bits 0-1 give the arc `(subtype & 3) + 6` segments.
The arc's step is `GetSineCosine` swapped, low-word-cleared and `asr.l #4` — cosine into X, sine
into Y — so a full-scale `$100` entry is a sixteen-pixel step. Only the rider bar is solid, and it
is a `SolidObjectFull`: a side or bottom touch runs `sub_24280`, which rewinds the player's Y by one
frame of `y_vel` before `HurtCharacter` so the knockback starts where they were.

**`$74` `Obj_SSZRetractingSpring`** (5, subtype 0). The proximity test is gated on
`btst #0,(Level_frame_counter+1).w` — the counter's low byte — so the extension steps every other
frame. `loc_464C6` hands the solid routine `$7FFF` while the spring is below mapping frame 2 and
restores the real `x_pos` immediately. **Exposing that parked X through `getX()` despawned the
object**: the placement window saw it at `$7FFF` and never loaded it. The class now reports the real
X always and simply does not call the solid below frame 2; `solidXForTest()` keeps the ROM's value
visible to the test.

**`$A0` `Obj_EggRobo`** (26). `sub_9185E` masks with `$F` and indexes `off_9186E`, which has three
entries; the ROM places only 0, 2 and 4, and that is asserted rather than assumed. Every high
nibble 0-`$A` carries both a fly-by and a fighter, which is what makes `_unkFA82` a pairing gate:
`loc_91570` sets bit `subtype >> 4` as the fly-by leaves the screen and `sub_91914` drops any
fighter whose bit is clear before its init runs. `word_919CA` gives the shot **no collision at all**
for its first `$1F` frames, which is why a player in the line of fire can still walk out of it, and
the arm holds the parent for `$5F` frames after each shot, so the cadence is one per 96 frames
rather than one per alignment.

**Census.** `TestS3kSszPlacementCensus` now ratchets against `Sonic3kObjectProfile`'s zone-10 list,
which `TestSonic3kObjectProfileRegistryGuard` pins to the registry in a fresh JVM. Act 1: **zero**
placements without an implemented owner. Act 2: one, `$B2:$00`, the crane slice 9 owns.

**Known-bug #41 — cause established, fix not taken.** Reading
`LevelManager.applyBackgroundTilemapWindowSelection` settles it. Its middle branch, the one that
moves the BG cache window to follow the background camera, needs both `bgCameraX != MIN_VALUE` and
`bgWrapsHorizontally()`. In plain mode `SwScrlSsz.getBgCameraX()` returns `MIN_VALUE` and
`isSszCloudBackgroundWindowActive` is false, so control reaches the third branch, which pins
`bgTilemapBaseX` to 0, and `getBgPeriodWidth()` outside cloud mode is the scroll fan's width. The
plane is therefore built from background layout X 0 for that many pixels and the `Camera_X + $28`
word wraps inside it. Neither of the two things previously suspected is wrong on its own; the defect
is that "camera-derived" is expressed as base 0, which only equals the cartridge when the cached
window spans the whole background. The candidate fix and its risk are in the known-bugs entry; it
was not taken here because it changes the plane source for every plain-mode frame in the act and
needs its own RED layout test and before/after capture.

**Tests.** `TestS3kSszCarriersAndSprings` 11, `TestS3kSszEggRobo` 5, `TestS3kSszPlacementCensus` 9 —
all 0 failures, 0 skips. Four comparisons were broken on purpose and each was caught: the cloud's
`byte_46698` index (`+1` -> "6(a2) is byte_46698 at the countdown index"), the spring's
`Level_frame_counter` gate (ungated -> "the extension stepped on an even Level_frame_counter"), the
EggRobo pairing gate (always-pass -> "a fighter survived with its _unkFA82 bit clear") and its
animal release (suppressed -> "CreateChild6_Simple let animals go on the way"). The four mandatory
S3K classes plus the SSZ, HPZ and DDZ suites and `TestEveryObjectRewindRoundTrip` /
`TestRewindHarnessCoverageRatchet`: 1476, 0 failures, 0 skips. `-Pguards`: 669, 0 failures, 0 skips.
`TestEveryObjectRewindRoundTrip` caught one real defect — the arc child forced `barSpawned` true on
restore, so a rewind past the allocation re-spawned nothing.

**Not delivered, and owed by whoever continues slice 3:** no capture for any of the six families
(the clip numbering is still at `09`, so the next is `10`); no rewind spot for the carrier chain or
the EggRobo pairing; no breadth matrix class; the cold act-1 route is unchanged, still stopping
where slice 1b left it, because every class here sits past the bridge and is exercised from
star-post entries.

### 2026-09-18 — slice 3, part 5: the owed items

Commits `955489d30` (#41), `c0dd69a4e` (rewind spots + breadth matrix), `3cd068e69` (cold route).

**Known bug #41 — fixed, and its evidence corrected.** `SwScrlSsz.getBgCameraX()` now publishes
`state.backgroundCameraX()` in plain mode, `backgroundWindowActive()` puts act 1 on the 512-pixel
wrap model in both modes, and `getBgPeriodWidth()` returns the plane width for act 1. Without that
`LevelManager.applyBackgroundTilemapWindowSelection` took its base-0 branch and the
`Camera_X + $28` word wrapped inside layout columns 0-3.

The correction matters more than the fix. Decoding the layout the way `TestS3kSszBackgroundLayout`
does — 60 columns by 22 rows — background row 13 is the sky chunk `$02` from column 13 through 16.
The `raw-10` frame-75 camera `($6A0,$550)` selects exactly those columns, so **the cartridge shows
flat sky there too** and the capture never demonstrated the defect. The earlier entry read "row 13
carries thirteen distinct chunk ids across columns ~9-52" as "structure should be on screen here";
the row does carry structure, at columns 9, 12 and 17-22, but none of it is in front of that
camera. The real defect was that a base-0 window ignores the camera at every camera, which is what
the new case asserts.

**Rewind spots.** Six, one per family, each mid-action. They caught nothing new, and one thing they
cannot catch is written into the helper rather than glossed: at every spot the object instances
survive the restore in place, so an `ObjectRefId` sidecar restore is never exercised. Disabling
`SszRotatingPlatformObjectInstance`'s carrier restore outright leaves both these spots and
`TestEveryObjectRewindRoundTrip` green. An `assertSame` on the resolved reference was written,
found unable to disagree, and deleted rather than shipped.

**Breadth.** `TestS3kSszCompatibilityMatrix`: ten scenarios over 320/800, no donor and the S1
donor, three rosters and two team shapes, each walking nine checkpoints. It asserts every slice
1b-3 class loads and that all eight ROM art keys have a ready renderer — the half that would
otherwise be silent, because a class whose PLC entry is missing loads fine and draws nothing.
Suppressing the retracting spring's entry fails ten of the twenty cases.

**Cold route.** The fixture that carries SSZ act 1 is `hpz_completerun` (`zone_id 10`); the
`ssz`-named ones are Death Egg. Bridge open at route frame 1390, furthest X `$6EB`, nobody dead
across 6000 frames — past the bridge and the ledge, stopping short of the `$7B` cluster at `$740`.

**Media.** Clips `10`-`13`: the bouncy cloud throwing the player with its puffs, the elevator bar
hanging him at mapping frame `$E5`, the rotating post walking him through `byte_468C4`'s poses, and
the swinging carrier's jointed arc. Each frame was looked at before the clip was cut.

**Still owed.** The `$74` spring clip: the authored walk-in completes and nobody dies, but no
spring is drawn at frames 180 or 270 as Sonic passes `($A60,$A30)`, while the matrix asserts its
renderer is ready — an open question about the draw, not the capture position, and the first thing
the next agent should chase. The `$A0` EggRobo clip: a fighter only exists once its paired fly-by
has left the screen, so a still capture near one shows nothing; an authored input that walks the
camera across the fly-by's placement first is the way. The #41 before/after, at a camera where a
background column actually carries structure (row 13, columns 17-22). And a rewind spot that forces
object recreation, which is the only way to exercise the sidecar restores.

### 2026-09-18 — slice 3's tail: the spring's draw, the EggRobo's box, and what the clips settled

**The `$74` spring is not a draw defect.** `Map_SSZRetractingSpring` (`$046D20`) is a six-frame
table whose **frame 0 has a zero piece count** — the ROM's own way of saying "not out yet" — and
`loc_46452` only leaves frame 0 while a player is inside the `$60`-wide approach box. Re-reading
`raw-15-ssz-retracting-spring/state.csv` against the placement settles it arithmetically: the
spring is at `($A60,$A30)`, so its box is X `[$A00,$A60]`, and Sonic is inside it only between
capture frames ~70 and ~105. The two frames the previous entry inspected, 180 and 270, have him at
X `$BB8` and `$B21` — well past the box, with the spring correctly retracted and correctly
invisible. Frames 78, 85 and 95 show it extended, yellow and grey, exactly where it belongs.

`TestS3kSszCarriersAndSprings#theRetractedSpringHasNoSpritePiecesAndTheExtendedOneDoes` pins both
halves: the ROM's six piece counts (0, 1, 5, 4, 3, 4) and the engine's decoded sheet, whose frame-0
`FrameBounds` are all-zero while frame 3's are not. Broken on purpose by pointing the empty-frame
assertion at frame 1: `expected FrameBounds[0,0,0,0] but was FrameBounds[-8,8,23,15]`.

**A real defect the EggRobo capture found: every mode wore the fighter's hitbox.**
`EggRoboBadnikInstance` passed `FIGHTER_COLLISION_SIZE` to its superclass unconditionally, so
`getCollisionFlags()` returned the size-6 enemy box in all three modes. The ROM disagrees, and the
disagreement is one byte per `ObjDat3` row (`dc.b width, height, frame, collision`), verified
against the cartridge at `$9199A`/`$919A6`/`$919B2`:

| Row | Bytes | Collision |
| --- | --- | --- |
| `ObjDat3_9199A` (fly-by) | `20 20 00 00` | none |
| `ObjDat3_919A6` (fighter) | `14 18 01 06` | size 6 |
| `ObjDat3_919B2` (animal releaser) | `04 04 00 00` | none |

The releaser is a 4x4 **invisible marker** and takes `ObjDat3_919A6` only at `loc_915F6`, on the
frame its fourth animal goes. `getCollisionFlags()` now returns 0 unless the loaded attribute row
is the fighter's, which is true for `FIGHTER` mode from its init and for a releaser once it has
converted. RED first: `onlyTheFighterRowCarriesACollisionBox` failed with
`expected: <0> but was: <6>` on the releaser.

**The fix exposed a test that had been resting on the bug.** `theAnimalReleaserLetsFourAnimalsGoBeforeItFliesAway`
and the EggRobo rewind spot both booted at the `$1330,$620` releaser and went red the moment the
hitbox went away. The reason is worth recording: **that releaser hangs over a chasm**. A capture at
`($1330,$5E0)` has the player fall straight past it and off the level; what had been keeping him near
enough for the object to stay on screen was the invented hitbox knocking him upward. Both tests and
the new one now use `$A0:$04` at `($1720,$E20)`, the one releaser of the three with ground under it —
confirmed by capture, the player standing at `($172A,$E2C)` with the object on screen throughout.

**Clips.**

| Clip | Raw | What the frames show |
| --- | --- | --- |
| `14-ssz-retracting-spring-extends.mp4` | `raw-15-ssz-retracting-spring` 40-150 | The spring folded flat, then extending through frames 78-95 as Sonic enters its box, then folding away again as he leaves |
| `15-ssz-eggrobo-releases-and-converts.mp4` | `raw-23-ssz-eggrobo-releaser` 0-137 | `$A0:$04` letting its animals go while a player stands in it unharmed, then taking `ObjDat3_919A6` at ~frame 70 and, now that it is a robot, hurting him |

**The nibble-0/nibble-2 pairing is still unfilmed, and here is the measurement that says why.**
The pairing itself is proven in tests — `theFlyByArcsOffTheScreenAndOnlyThenDoesItsFighterSurvive`
drives group 6's fly-by to `loc_91570`, watches bit 6 of `_unkFA82` go up, and
`aFighterWithNoReleasedFlyByNeverRuns` holds the other side. What cannot be filmed at any camera
tried is the two of them in one shot, for a reason that is arithmetic rather than bad luck:

- The fly-by's arc is tiny. `y_vel` starts `$180` and `$3C(a0)` subtracts one more each frame, so
  it turns negative after 27 frames having descended about `$1A` pixels, and the perspective term
  `$100 / (scale + 4)` pulls the drawn position up and left by as much as 32 px as the scale falls.
  It therefore never gets more than a few pixels below its placement row before leaving upward.
- Every nibble-2 partner hovers at its own placement Y, and at the camera the player can actually
  occupy, that Y is off the top of the screen. Group 6 measured: fly-by `($1520,$C80)`, fighter
  `($1660,$C80)`, player's standing Y on that walkway `$CEC`, camera Y `$C8C` — both objects sit 12
  px *above* the visible area. Walking right to load the fighter (`raw-20`) runs the player off the
  walkway at `$15C0` into the shaft below, where the camera is `$14C` too low to see it.
- Group 1 (`raw-18`) reaches the fly-by at `($C20,$9E0)` but the return leg strands the player on a
  carrier oscillating between X `$B1A` and `$C65`, never back inside the fighter's load window.

Owed, with the next thing to try named: a capture that stands the player on the `$7B` walkway at
`($15D0,$CEC)` **without** stepping off it — a one-frame right tap rather than a burst — so the
fighter's load window opens while the camera is still on the walkway; or a group whose fighter Y is
at least `$60` below its own ledge.

### 2026-09-18 — slice 3's tail, part 2: #41 has no picture, and a rewind spot that can disagree

**The #41 before/after was taken and it is blank.** `raw-24-ssz-bg41-before` and
`raw-25-ssz-bg41-after`: 240 frames each, `--star-post --x 0x900 --y 0x580`, one recompile apart.
The before build put `getBgCameraX()` back to `MIN_VALUE` in plain mode and made
`backgroundWindowActive()` cloud-only; it was confirmed effective rather than assumed, by
`TestS3kSszBackgroundLayout#plainModeSourcesThePlaneFromTheCameraDerivedWindow` going red on it
(`expected: <1792> but was: <-2147483648>`). The player settles at `($938,$5EC)`, camera
`($898,$58C)` — background column 17, row 13, the place the entry asked for — and **48 sampled
frames are pixel-identical between the builds**. `Sonic3kZoneFeatureProvider.useFullWidthBackgroundTilemapWindow`
is MGZ-only, so SSZ does take the branch the fix moves it to; the window base really changes from
0 to `$890` and the period from the fan width to 512, and nothing on screen does. Either the SSZ
act-1 plane is not built from `bgTilemapBaseX` at all, or `($898,$58C)` is the wrong camera. Filed
as an open question with both kill conditions in `docs/status/s3k-known-bugs.md` #41, and the
plain-mode half is downgraded there from "fixed" to **asserted, not demonstrated**. This is
recorded rather than quietly dropped because a fix whose only evidence is the assertion it was
written against is exactly the shape of thing a later agent would re-derive.

**A rewind spot that can disagree.** The slice-3 spots all captured and restored one frame apart
with every object alive on both sides, so a broken `ObjectRefId` restore could not have been seen
— the previous entry proved that by disabling the rotating platform's carrier restore and finding
everything still green.
`TestS3kSszTraversalPlatforms#restoringPastTheDebrisDeletionRecreatesThePiecesAndTheirColumnLink`
captures while the `$7E` column's eight `word_46618` pieces are in the air, runs on until every
piece has been deleted (`countActive == 0`, asserted), and only then restores. The pieces have to
be recreated — the test holds the pre-deletion instances and asserts none of the restored ones is
the same object — and each piece's `parent3(a0)` has to resolve through the identity table to the
live column. Broken on purpose by replacing the debris' `resolveObject` with `column = null`:
`expected: <SszCollapsingColumnObjectInstance@…> but was: <null>`. That is the first SSZ spot where
the sidecar is load-bearing.

### 2026-09-18 — slice 4: death, checkpoint and what a load takes with it

`TestS3kSszLifecycleProduction`, three cases, driven through a real `GameLoop` reload rather than
by constructing a state.

**Two ROM clears, in two different routines, and neither is in the SSZ code.** `LevelSetup`
(sonic3k.asm:102185) runs `clr.l (Events_bg+$00/$04/$08/$0C).w` on every load, so a beaten boss is
not remembered; the star posts simply sit past each arena. `Level:` runs `clearRAM _unkFA80,$80`
(sonic3k.asm:7623), which covers `_unkFA82`, `_unkFA8A`, `_unkFAA2`, `_unkFAA4` and
`_unkFAB0..B8` — the question the plan left open ("`_unkFA82` after respawn: no ROM clear") has an
answer, and it is that block clear. `_unkFA82` is a shared scratch word used by a dozen unrelated
routines across the ROM (grep finds writes in ICZ, MGZ and both SSZ bosses), which is exactly why
no zone clears it individually. The engine's per-load rebuild of `SszZoneRuntimeState` is therefore
faithful, and the test proves the load really replaces the instance: asserting `assertSame` instead
of `assertNotSame` fails with two different objects.

The lifecycle case seeds the two things a naive carry-over would keep — `Events_bg+$00` negative (a
beaten Green Hill boss, `st (Events_bg+$00).w`) and `_unkFA82` bit 6 (a passed EggRobo fly-by) —
calls `applyPitDeath`, and then requires all sixteen `Events_bg` bytes, the pairing word and
`_unkFAA4` to be zero, the arrival not to run, and the rewind timeline to reset rather than
continue the pre-death history.

**`sub_575EA` after the restart, which is the observable consequence.** `$34:$03` is at
`($14C0,$E8)`, and `$E8 < $440`, so the restart lands in the Metropolis branch. With the Green Hill
word zeroed by the load the branch publishes `Camera_min_X = $160`; if the boss were remembered it
would publish 0. The test reads `$160` and `Camera_max_X = $1660`, plus `Levels_1000_High`'s wrap
still enabled over `$1000`.

**Native corroboration, and a correction to this plan's own fixture table.**
`s3k-sonic-tails-complete-emeralds/hpz_2` *is* a `$34:$03` restart: its metadata declares
`start_x 0x14C0 / start_y 0x00E8`, the placement's own coordinates. Its row 0 reads player
`($14C0,$EC)` and camera `($1420,$8C)`, and the engine's first frame from the same declared restart
reads exactly those four values (broken on purpose with `nativeCameraY + 1`: `expected <141> but
was <140>`). The table above describes `hpz_2` as carrying a "second death"; scanning
`player_routine` across all 4352 rows finds **one** `00` span (rows 4224-4350), not two, and the
test asserts that count so the correction cannot drift back.

**Declared setup, not a route.** `SSZ1_ScreenInit` reads `Last_star_post_hit` on the load's first
pass and, with it clear, hands Player 1 to `Obj_57C1E` under `object_control 3` — a leader placed
near `$34:$03` with no checkpoint is dragged back to the arrival column before he can touch
anything. The restart is therefore written the way the ROM writes it and the way
`GameplayCaptureTool --star-post` does. A capture that walks into the post is not available at this
placement and is not claimed.

**Clip** `16-ssz-death-restarts-at-the-star-post.mp4` (`raw-27`, frames 40-280): death at 77,
restart at the declared star post around 150, alive again by 220.

**Two things the captures taught on the way.** (1) Falling in act 1 does not kill: the act wraps at
`$1000`, so `raw-26` walks off the same ledge that "killed" in `raw-15`-era captures and comes back
round the top. The death in `raw-27` is the converted EggRobo, which is why the clip is filmed
there. (2) The same input that died at frame 404 in `raw-17` survives after the EggRobo collision
fix — the pre-fix hitbox was killing players the cartridge would not have.

**Owed by slice 4:** a `$34:$02`/`$34:$04` restart case (only `$34:$03` is driven), a wide-viewport
row, and a capture of a star post being physically touched, which needs a route rather than a
declared setup.

### 2026-09-18 — slice 5: the Green Hill recreation

`SszGhzBossObjectInstance` plus three child classes, the `loc_576E8` allocation in
`Sonic3kSSZEvents`, and `TestS3kSszGhzArenaHeadless` (5 cases).

**The arena is two gates, not one, and the second one is a camera.** `loc_57686` locks when Player
1 is at or below `$7C0` with `Camera_X_pos == $160` and not in the air, and all it publishes is
`Camera_max_X = $160` and `Camera_min_Y = Camera_target_max_Y = $7C0`. `loc_576E8` then waits for
`Camera_Y_pos == $7C0` **exactly** — and because only the *target* moved, the boundary eases down
from `$1000` at two pixels a frame, so about 1050 frames pass between the lock and the boss. The
first version of the test gave that 240 frames and read the failure as a missing allocation.

**A width bug the wide-viewport row found.** Two of `sub_575EA`'s three camera-X gates are equality
tests against literals, and `Camera_X_pos` is the leader's X minus half the screen. At 800 the same
world position gives `$70`, not `$160`, so the Green Hill lock could never fire and the arena was
unreachable on every wide viewport. `Sonic3kSSZEvents.nativeFramedCameraX` reframes the value the
gates read into the ROM's 320-wide framing — the same treatment, for the same reason, as
`HczMinibossInstance.nativeFramedCameraX` — and leaves every bounds *write* in world coordinates,
because moving the arena would move its terrain. Both viewport rows lock and spawn now.

**A word that is two flags.** `move.w #$7F00,(Events_bg+$00).w` writes the fighting byte and clears
the lock byte beside it in one instruction: `Events_bg+$01` is the low half of that word, which is
why nothing ever clears it separately. The first version of the test polled `eventsBgWord(0)` for
the spawn and saw `$00FF` — the lock byte alone — so it "detected" a spawn that had not happened.
The test reads the byte now, and says why in a comment.

**`SetUp_ObjAttributes` is the routine counter.** It ends `addq.b #2,routine(a0)`, which is the
only thing that advances `routine` through `off_7A2B4`; the routine bodies never write it
themselves except where they jump the sequence. Reading the table without that fact makes routine 0
look like an infinite loop.

**Persistence, found by a test that stalled.** The escape stopped 104 frames in and never wrote its
flag: the ship leaves the locked arena's right edge and the engine's out-of-range unload stops
updating it. The ROM has no `Obj_WaitOffscreen` anywhere in `loc_7A29C`'s chain, so the object is
simply never unloaded; the boss and all three child classes now answer `isPersistent()` true. The
diagnosis was a measurement — the escape's own `$2E` frozen at 15 with the ship at X `$380` — not a
guess.

**The defeat is three routines deep and none of them is the killing hit.** `sub_7A5A0`'s zero
branch installs `Wait_FadeToLevelMusic` with `$34 = loc_7A3CE` and leaves `$2E` holding whatever
the last routine left, so the ship hangs where it died; `loc_85674` then arms `(2*60)-1`, allocates
`Obj_Song_Fade_ToLevelMusic` and hands to `loc_7A3CE`, which turns the ship right at `x_vel $400`;
only `loc_7A3F8`, at the end of those 119 frames, writes `st (_unkFA89).w` (which deletes the head)
and `st (Events_bg+$00).w` (which `sub_575EA` reads as beaten and the `$79:$AA` pad reads as its
release), restores palette line 2 and reloads PLC `$32`. The test asserts the flag is still
positive immediately after the eighth hit, which is the half that would otherwise be invisible.

**The chain is a tree, not a list.** `CreateChild9_TreeList` sets each link's `parent3` to the link
before it and only the first link's to the ship; `$44` is the ship for all six. The links pay out
at two pixels a frame for `word_7A642`'s per-subtype counts, and the last one to finish — subtype
`$A`, the ball — is what sets the ship's `$38` bit 2. The root then owns the angle: `$3C` steps by
`$3A` and reverses whenever `($3C - $40)` is below `$80` unsigned, a pendulum between `$40` and
`$BF` the long way round through zero, and at the end of the sweep that lies in the ship's
direction of travel it sets bit 3, which is what turns the ship around. Which end that is comes
from `sls`'s `$3C <= $40` answer combined with the ship's X-flip through a `not.b`.

**Rewind.** Four cross-object SST links that no spawn can rebuild — each link's `parent3`, the
ship's list, its emitter and the head's parent — so all four classes capture typed `ObjectRefId`
sidecars and relink on restore, with `DefaultObjectRewindPolicies` entries and the architecture
guard's override baseline triaged the same way slice 3's debris was. The spot is taken mid-swing
with all six links out; nulling the links' `chainParent` restore fails it on five of the six.

**Art.** `ArtKosM_SSZGHZMisc` (`$17EDB4`) over `Map_SSZGHZMisc` (`$186E7C`) on palette line 1 —
`ObjDat3_7A660` and `_7A66C` carry a `FixBugs` comment calling that line wrong and the shipped ROM
uses it — plus `ArtKosM_MechaSonicHead` (`$17C6E0`) over `Map_MechaSonicHead` (`$681FC`). Both are
registered in `addSszEntries`: the engine has no runtime decompression queue for object art, so the
ROM's `Load_PLC $7B` is served by the zone's art plan. `Pal_SSZGHZMisc` is `$07D850`.

**Tests.** `TestS3kSszGhzArenaHeadless` 5 (two viewport rows plus the routine walk, the defeat and
the rewind spot). Broken on purpose three times, each caught: suppressing `dropChain()`
("dropped the chain when Camera_X + $A0 reached the ship"), suppressing the beaten flag
("st (Events_bg+$00).w after the escape") and nulling the chain link restore (five links'
compact state differing). The four mandatory S3K classes with the SSZ, HPZ and DDZ suites and the
rewind harness: 1520 tests, 0 failures, 0 skips. `-Pguards`: 669, 0 failures, 0 skips, after two
baseline moves the guards exist to force (the four `ObjectRefId` policy entries and the eight
override rows).

**Not delivered by slice 5, and owed.** `sub_7A5A0`'s hit flash writes three specific
`Normal_palette` entries from `word_7A628` rather than a whole line; this uses the shared boss
flasher instead, and the exact three-colour flash is unported. The `$79:$AA` gated pad's rise after
the defeat is implemented but not driven end to end in a test. No capture yet. No native row
comparison for the fight: the `hpz` fixture's Green Hill arena window (camera `$160,$7C0`, 1142
rows) is the obvious next evidence and nothing here is compared against it, so "native behaviour
matched" is not claimed for any of these rows.

### 2026-09-18 — handover after slice 5

Branch `feature/ai-ssz-bring-up`, head `0d66b1434`, base develop `035e48a58`. Nothing pushed,
nothing merged. Commits this entry covers, oldest first: `5d746a8f2` (the `$74` finding and the
EggRobo collision fix), `965ea8c1f` (the recreation rewind spot and #41's blank before/after),
`664b15e76` (slice 4), `9e38c808c` (slice 5), `df1a3b85e` and `0d66b1434` (matrix and backlog).

**Where slice 6 starts.** `Obj_SSZMTZBoss` (`loc_7A6B6`) and `sub_575EA`'s `loc_5775C`. Four things
this entry learned that slice 6 will hit immediately and should not re-derive:

1. **`SetUp_ObjAttributes` is the routine counter** — it ends `addq.b #2,routine(a0)`. Without that
   fact every S&K boss's routine-0 entry reads as an infinite loop.
2. **The arena gate is a camera, not the lock.** `loc_5775C` waits for `Camera_Y_pos == $380`
   exactly, and the lock only publishes `Camera_target_max_Y_pos`; the boundary then eases at two
   pixels a frame. From `$1000` that is roughly 1650 frames. Give the test the budget.
3. **`Camera_X_pos` comparisons are width-sensitive.** `loc_5770C` tests `== $1660`. Use
   `Sonic3kSSZEvents.nativeFramedCameraX`, which already exists, or the Metropolis arena is
   unreachable at every wide viewport exactly as Green Hill was.
4. **The boss and its children must be persistent.** Nothing in these dispatch chains runs
   `Obj_WaitOffscreen`, and the escape leaves the locked arena; without `isPersistent()` the engine
   stops updating the object part-way through and the defeat flag is never written.

`Obj_SSZMTZBoss` differs from Green Hill in ways worth reading before writing anything: it has two
routines rather than six, it writes its own `SSZ_MTZ_boss_X/Y_pos` and `_vel` words rather than
using `MoveSprite2`'s SST fields, `loc_7A7C4` writes `$10,0,3,0,1,0` into `_unkFA82.._unkFA87` —
the same word the EggRobo pairing uses, so an EggRobo fly-by released before the fight will look
released afterwards unless the boss's write is modelled — and it allocates a second object at
`loc_7AD8A` with `$34 = a0`.

**The four things slice 5 did not deliver**, in the order they are worth doing: the `word_7A628`
three-colour hit flash (`sub_7A5A0` patches `Normal_palette+$0E/$1C/$1E` from a two-row table
indexed by `$20(a0)` bit 0, not a whole line, and the shared boss flasher is standing in); the
`$79:$AA` gated pad driven end to end from the defeat flag to the `$2A0` lift; a native comparison
against the `hpz` fixture's Green Hill window; and a wide-viewport capture of the fight.

**Two open questions with kill conditions.**

- *Known bug #41's rendered half.* The fix changes the published window (the layout test proves it)
  and changes no pixel at camera `($898,$58C)` over 48 sampled frames. Either `LevelTilemapManager`
  does not consume `bgTilemapBaseX` for SSZ act 1's plane, or that is the wrong camera. Killed by a
  before/after pair at any camera whose frames differ, or by a read of the act-1 path through
  `ensureBackgroundTilemapData` showing where the base is dropped. Recorded in
  `docs/status/s3k-known-bugs.md` #41, where the plain-mode half is now marked asserted rather than
  demonstrated.
- *The EggRobo pairing shot.* Measured obstacle in `~/Videos/OGGF/ssz-bring-up/INDEX.md`: the fly-by
  descends about `$1A` px before turning upward, and every nibble-2 partner hovers at a Y that is
  off the top of the screen from the ledge the player must stand on to load it. Killed by a capture
  that puts both on screen, or by establishing that none of the eleven pairs can.

**One correction to make sure survives.** This plan's fixture table describes
`s3k-sonic-tails-complete-emeralds/hpz_2` as carrying a "second death". It carries one
`player_routine 00` span, not two, and `TestS3kSszLifecycleProduction` asserts that count so the
claim cannot drift back in.

### 2026-09-18 — slice 5 follow-up: `Boss_flag`, and a no-op that nearly shipped

Reviewing the port's own citations turned up one thing slice 5 had left out and one thing it nearly
got wrong in a way that would have read as done.

`Obj_SSZGHZBoss`'s init is `move.b #1,(Boss_flag).w` and `loc_7A3F8`'s first instruction is
`clr.b (Boss_flag).w`, before the `_unkFA89` and `Events_bg+$00` writes. Neither was ported.

The obvious fix — call `Sonic3kLevelEventManager.setBossFlag` the way `MhzEndBossInstance` does —
would have been a **no-op dressed as a port**: that method routes only to `aizEvents` and
`cnzEvents`, so an SSZ caller writes nothing and every test that asserted it would have had to
assert something else. The write goes to `SszZoneRuntimeState.bossFlag` instead, where it is real
state, the rewind capture carries it, and a consumer can be added when one exists. **No SSZ
consumer reads it yet**, which is recorded on the accessor itself rather than left to be
rediscovered.

The test asserts it true after the entry wait and false after the escape. The first version
asserted it on the frame the allocation happens and failed — correctly: the init block is the
object's first *execution* in the engine exactly as it is in the ROM, where the allocation only
writes the routine pointer. That failure is left described here because "assert the init's effects
on the allocation frame" is an easy thing to write and an easy thing to then weaken.

Re-run after the change: the four mandatory S3K classes with the SSZ, HPZ and DDZ suites and the
rewind harness, 1520 tests, 0 failures, 0 skips; `-Pguards` 669, 0 failures, 0 skips.

**One precision deviation, recorded not fixed.** `MoveSprite_CircularSimple` reads and writes the
chain links' positions as longwords — `move.l x_pos(a1),d2` / `move.l d2,x_pos(a0)` — so the ROM
carries each link's sub-pixel fraction. `SszGhzBossChainLinkChild` keeps whole-pixel X and Y and
re-derives the orbit from its parent every frame, so the fraction is dropped. The error cannot
accumulate, because each frame's position is computed absolutely from the parent rather than
integrated, but a link can sit one pixel from where the cartridge puts it. Worth knowing before
anyone compares the chain against a native row.

### 2026-09-18 — the slice-5 reviewer did not deliver, and what was checked instead

An independent reviewer was spawned against commit `9e38c808c` with a specific brief: the routine
dispatch and what advances `routine`, `Swing_UpAndDown`'s sign conventions, `MoveSprite2` versus
`MoveSprite`, the `loc_7A496` angle machine's `sls`/`not.b`/`btst #0,render_flags` combination,
`MoveSprite_CircularSimple`'s shift semantics, `sub_7A5A0`'s hit gate and `$25(a0)` restore,
`loc_7A3F8`'s write order, drifted citations, and assertions that could not disagree. It ran for
about ten minutes and twenty-four tool calls and then ended **without returning a report**; three
requests to deliver it, including one asking only for partial findings, produced nothing. **No
reviewer findings exist for slice 5.** This is recorded rather than quietly dropped, because "an
independent reviewer looked at it" is exactly the kind of claim that later reads as coverage.

What was checked directly instead, against the disassembly text, with the results:

| Checked | Result |
| --- | --- |
| `SetUp_ObjAttributes` ends `addq.b #2,routine(a0)` | Correct as modelled; it is the only thing advancing `routine` through `off_7A2B4` |
| `loc_7A496`'s reversal window and which end sets `$38` bit 3 | Traced frame by frame from angle 0. `sls` is C-or-Z on `subi.b #$40,d0`, so it means `angle <= $40`; `bhs #-$80` skips when the biased value is `>= $80`, so the reversal fires once at `$40` and once at `$BF` and the sweep runs the long way round through zero. With the ship unflipped bit 3 is set at the `$BF` end and flipped at the `$40` end. Matches the port |
| `MoveSprite_CircularSimple`'s `asr.l d2` | `(±$100 << 16) >> 4` is 16.0 in 16.16, so `$3A = 4` really is a 16-pixel arm and `3` a 32-pixel one. Matches |
| The same routine's longword position read/write | **Deviation found**: the ROM carries each link's sub-pixel fraction, the port keeps whole pixels. Recorded above; cannot accumulate |
| `Obj_SSZGHZBoss`'s `Boss_flag` writes | **Gap found and fixed**; see the entry above |
| `loc_7A32C`'s `Camera_X + $A0` trigger | Width-independent in practice, because the lock pins `Camera_max_X` to `$160` so the camera sits at `$160` at every viewport |
| The defeat case's hit delivery | **Hole found and closed**: the eight hits are delivered by calling `onPlayerAttack` directly, so the test passed without the ship being touchable at all. It now asserts the box (`$C0 | $F`), the hit counter and the zero after defeat |

Not checked by anyone: `sub_7A5A0`'s `$25(a0)` collision restore against the shared hit handler's
equivalent, `Swing_UpAndDown`'s shared helper against the ROM routine text, and every citation in
the four classes' Javadoc. Those remain open for slice 6's reviewer, who should be given the same
brief and actually waited on.
