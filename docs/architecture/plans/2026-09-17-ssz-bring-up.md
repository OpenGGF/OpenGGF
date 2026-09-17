# Sky Sanctuary Zone: methodology v2 bring-up plan

Date: 2026-09-17. Planned branch `feature/ai-ssz-bring-up` in `.worktrees/ai-ssz-bring-up`;
execution base develop `9cba6dbb6` (pin this SHA for the combined change-based validation; re-pin
and re-measure if the branch is cut later). Applies
[methodology v2](../designs/2026-09-15-zone-methodology-v2.md) with the refinements from the
[SOZ](2026-09-15-soz-methodology-v2.md), [HPZ](2026-09-16-hpz-bring-up.md) and
[DDZ](2026-09-17-ddz-bring-up.md) campaigns to SSZ (`$A00` Sonic/Tails, `$A01` Knuckles). Design
and plan by Fable 5.1; implementation and execution by Opus, as for HPZ and DDZ. Entry skill:
[s3k-zone-bring-up](../../../.agents/skills/s3k-zone-bring-up/SKILL.md). Starting inventory:
[ssz-analysis.md](../research/s3k-zones/ssz-analysis.md) (reverify per slice; corrections below).

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
| Act 2 exit | Deliver up to `loc_7BCB0` (defeat, `object_control $83`, `SaveGame`, `Events_fg_4+1`) plus the SSZ2-owned stages that visibly follow (floor collapse `loc_591D6`, camera controller `loc_59078`). `sub_5B18E`/`Obj_Ending`/credits are the ending campaign | No S3K `EndingProvider`; the ending code is shared with `$D01` (`Ending_ScreenEvent` reuses `sub_5928C`/`sub_592EE`). Draw the line where `sub_5B18E` phase 0 first runs and record engine behaviour after it |
| Roster | `$A00`: Sonic, Sonic + Tails, Tails. `$A01`: Knuckles. Cross entries are ROM-denied (level select `sonic3k.asm` ~10179/10198) | Derive from the production launch contract and assert the live roster and ROM-backed renderers. No raw debug override for Knuckles in `$A00` or Sonic in `$A01` (no art/route) |
| Tails CPU | `loc_13AB4`: `$A00` sets `Tails_CPU_routine $A`, `object_control $83`, `sub_13ECA`; `Obj_57E34` (subtype `$60`) is the act-1 Tails arrival helper | Player-dispatch hook outside every SSZ table; first cold frames depend on it |
| Widths and donors | 320 plus one wide viewport on every mandatory mechanic from slice 1; S1 donor and an extra-follower team per the level test standard | Arena locks compare `Camera_X == $160/$1660/$19A0`; cloud sprites use `& $1FF` screen maths; the Death Egg plane is 1:1 BG. All width-sensitive |
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
| BG modes (sky / clouds / Death Egg), cloud drift, deform tables, FG VScroll bands, act-2 column waves | `SSZ1/2_BackgroundInit/Event`, `sub_579F0`, `sub_57A60`, `sub_574DC`, `sub_58D3E`, `sub_58FBC`, `SSZ1_BGDeformArray`, `word_577B2`, `word_58C80`, `SSZ2_*DeformArray` | New `SwScrlSsz` registered in `Sonic3kScrollHandlerProvider`; mode transitions use the existing staged plane redraw path (a full tilemap rebuild is a ~25 ms hitch and reverts direct Plane A writes). Per-column VScroll: reuse the DDZ/FBZ2 render-mode mechanism, no `@ModApi` change |
| Death Egg hot-swap: chunks `+$180`, blocks `+$B8`, tiles `$073`, spiral-ramp art, chunk byte patches, `Pal_SSZDeathEgg` line 2 | `SSZ1_ScreenEvent` stage 4 | `ZoneLayoutMutationPipeline`/`LevelMutationSurface` for layout and chunk patches; art through the S3K PLC/Kos-module queue with `invalidatePatternLookup` for pattern-only changes; palette via `S3kPaletteOwners`/`S3kPaletteWriteSupport` |
| Act-2 arena floor patch, VRAM `$7F0-$800` fill, floor collapse | `SSZ2_ScreenEvent` stages 4-C, `loc_591D6` | Same mutation and redraw owners |
| Roaming clouds, solid cloud platforms (swing on `_unkEE9C`), crumble platforms and player carry | `loc_57BB2`, `loc_57B8E` + `SolidObjectTopSloped2`, `sub_5750C` | Event-owned object instances; carry through the normal solid-object contact path, native writes via `NativePositionOps` |
| Arrival, Tails helper, launch script and spiral ramp, control locks | `Obj_57C1E`, `Obj_57E34`, `Obj_57E96`, `loc_58192`, `byte_587A8`, `loc_13AB4` | Controller objects using the generic object-control path; Tails CPU routine `$A` in the existing sidekick CPU owner. No zone check in shared player code: use providers/`GameRules` |
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
| 1. Runtime state, arrival, act-1 bounds | `SszZoneRuntimeState`, `Sonic3kSSZEvents`, `SSZ1_ScreenInit`, `Obj_57C1E` + beam, `Obj_57E34`, `loc_13AB4`, Y-wrap, `word_5778A/9A` | First cold rows of `hpz` (`$100,$FAE`, cam `$60,$F41`). `LevelData` start `$100,$C00` vs ROM-forced camera: explain from ROM before coding. Beam class reuse vs `Obj_TeleporterBeamExpand` entry state |
| 2. Act-1 background and animation | `SwScrlSsz` sky/cloud modes and the four-state BG machine, `sub_57A60` layer maths, cloud drift `$500`, `_unkEE9C` oscillator `loc_57B6A`, roaming clouds `sub_5758A`, solid clouds `word_5853E`, AniPLC, `PLC_32_33_34_35` | Mode switch at wrapped Y `$800/$F00` and X `$1800` needs staged plane draws; fractions from the tables; wide-viewport seams on `& $1FF` cloud maths (record the presentation choice) |
| 3. Traversal objects | `$74 $75 $76 $77 $7A $7B $7C $7D $7E $7F $AF`, `$79` SSZ branch pads, EggRobo `$A0` (+ shots), starposts across the wrap | One family review. Bouncy cloud and carrier are player-state hooks; collapsing families share debris tables; slot/allocator order decides same-frame children. Subtype census from decoded placements first |
| 4. Death, checkpoint, wrap lifecycle | Restart at starposts, `Events_bg` flags after respawn (skips a beaten boss?), objects across the Y seam | Native `hpz_2`/`hpz_3` starts give the expectations; level variables must clear on reload; timeline isolation |
| 5. GHZ recreation | `sub_575EA` lower arena, `Obj_SSZGHZBoss`, PLC `$7B`, `Pal_SSZGHZMisc` line 2, fade → `mus_EndBoss`, defeat sets `Events_bg+$00 = $7F00`, pads enable | `s3k-implement-boss`. Lock requires `Camera_X == $160` and grounded player: wide-viewport reachability. Sprite-composition audit (ball chain vs player vs terrain) |
| 6. MTZ recreation | Upper arena `$1660/$380`, `Obj_SSZMTZBoss`, `SSZ_MTZ_boss_*` RAM, orbs, laser timer, `PalLoad_Line1` | Orb slot order and RNG; palette line ownership vs level line 1 |
| 7. Mecha Sonic (act 1) and results | Final arena `$19A0/$5C0`, `$79` spawner `loc_45A84`, `Obj_SSZEndBoss` 21 routines from `loc_7B308`, children `ChildObjDat_7D47A`, DPLC, `sub_7D312/7D2D8/7D35A`, defeat → results → `End_of_level_flag` | RNG seed from `V_int_run_count`; Hyper flash/insta-shield interactions from the native movie; rewind across the boss graph |
| 8. Death Egg launch and exit | Stage 0→4→8, `Obj_57E96`, `sub_5750C` crumble + carry, hot-swap, `sub_574DC`, debris `loc_58234/58360/582AC/581F2`, `Special_V_int_routine 4/12`, scripted ramp run `loc_58192`, `$B00` | Most coupled slice: layout mutation + queued art + palette + independent BG V-scroll + shake in one window; renderer invalidation; rewind mid-crumble; player DPLC frames from `byte_587A8` |
| 9. Act 2 | `SSZ2_ScreenInit/Event`, `loc_59078`, `sub_58D3E`/`sub_58FBC`, `word_58C80`, `$B2` crane cutscene (`loc_7CA3A`-`loc_7CBA4`), `Obj_SSZEndBoss` act-2 init, forced run-right `loc_7BBE0`, `Obj_SSZ2_Boss` 36 routines, Master Emerald, `Run_PalRotationScript`, defeat/save `loc_7BCB0`, floor collapse | Column VScroll waves under widescreen; `_unkFAB8` bit 6 flicker; Knuckles glide/climb vs arena walls; stop line at `sub_5B18E` |
| 10. Routes and acceptance | Cold routes from movie input: Sonic + Tails (`--input-start` 448920 family; capture path runs one frame behind the headless fixture), Tails alone, Knuckles (412501); authored normal-form and Sonic-solo routes; chained HPZ → SSZ arrival; matrix breadth; rewind spots; strict replay frontiers for all six fixtures; moving inspection at 320 and wide | Positioned boss success does not advance the cold frontier. Native lag frames desync cold BK2 replay: skip movie input on repeated `lfc`. Route programs skip the recorded pre-level prefix |
| 11. Media and delivery | Reel, archive index, change-based validation against the pinned base, docs, integration | Below |

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
Y-wrap, dynamic bounds, BG sky, BG clouds, BG mode transitions, cloud sprites, solid clouds, AniPLC
(per script), each object `$74-$7F/$AF/$79`, EggRobo, starpost/respawn, GHZ lock/fight/defeat/pad,
MTZ lock/fight/defeat/pad, Mecha spawn/fight/defeat, results + save, crumble, hot-swap, Death Egg
BG, debris, ramp script, `$B00` request, DEZ presentation (blocked). Act 2 rows: load/identity,
arrival, camera controller, BG parallax, column waves, arena floor patch, crane cutscene, Mecha
phase, forced run, Super phase, Master Emerald, palette rotation, defeat/save, floor collapse,
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
- **Clock-seeded RNG/aim** (`V_int_run_count`: Mecha Sonic, DEZ turrets as in DDZ) needs a declared
  seed for movie-route matching until the full cold chain supplies it; label such evidence seeded.
- **Knuckles trace testing is out of scope (user decision 2026-09-17).** One Knuckles replay class
  exists (`TestS3kKnucklesLbz2BigArmTraceReplay`); the `s3k-knuckles-complete-superemeralds` run has no
  segment classes. A campaign may add one where cheap, but owes no Knuckles replay frontier; Knuckles
  rows rest on authored routes and native probes from that movie.

## Open questions (with kill conditions)

1. (Closed while planning: the MTZ fight is in `hpz`, 1004 locked rows.)
2. Does a respawn after a beaten boss skip it (`Events_bg+$00/$02` survive reload or not)? Kill:
   read the level-load clear range vs `Events_bg`, confirm in `hpz_2` rows.
3. Is `Ending_running_flag` really set at `SSZ2_ScreenEvent` stage 4 (before the fight)? Kill: read
   `sonic3k.asm` ~117760-117800; it decides pause/HUD behaviour during the act-2 fight.
4. Does act 2 run AniPLC (`AnimateTiles_NULL` claim)? Kill: `Offs_AniFunc` entries for `$A01`.
5. Knuckles-run replay classes are absent for every zone: omission or deliberate? Kill: grep the
   segment-class generator/manifest; add the `$A01` class in slice 10 either way.
6. Tails-alone vs Mecha Sonic and the ramp script (`loc_58192` iterates both players): any
   Tails-specific branch? Kill: read `Obj_57E96`-`loc_581F0` for `Player_mode` tests.

## Status

| Claim | State |
| --- | --- |
| Implemented | Not started. Exists: level load, music, title card, EggRobo art entry, HPZ-branch teleporter/beam, HPZ exit requests |
| Cold-reachable | Not started. SSZ1 loads from the HPZ route with default scroll, no events, no objects |
| Rewind-verified | Not started |
| Native behaviour matched | Not started. Fixtures identified (table above); no SSZ native probes yet |
| Visually matched | Not started |

Out of scope, recorded as dependencies: DEZ presentation/route after `$B00` (DEZ campaign, which
also owns the mislabelled `ssz*` fixtures); `sub_5B18E`, `Obj_Ending`, credits and the Knuckles
good/bad ending (ending campaign); Sonic's post-credits Mecha/EggRobo scenes.

## Evidence log

(empty — first entry is slice 0)
