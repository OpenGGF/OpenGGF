# Lava Reef Zone: methodology v2 bring-up plan

Date: 2026-09-17. Planned branch `feature/ai-lrz-bring-up` in `.worktrees/ai-lrz-bring-up`;
execution base develop `9cba6dbb6` (pin this SHA for the combined change-based validation). Applies
[methodology v2](../designs/2026-09-15-zone-methodology-v2.md) with the refinements from the
[SOZ](2026-09-15-soz-methodology-v2.md), [HPZ](2026-09-16-hpz-bring-up.md) and
[DDZ](2026-09-17-ddz-bring-up.md) campaigns to LRZ1 (`$900`), LRZ2 (`$901`) and the boss act LRZ3
(`$1600`) with its handoff to Hidden Palace (`$1601`). Design and plan by Fable 5.1; implementation
and execution by Opus, as for HPZ and DDZ. Entry skill:
[s3k-zone-bring-up](../../../.agents/skills/s3k-zone-bring-up/SKILL.md). Starting inventory:
[lrz-analysis.md](../research/s3k-zones/lrz-analysis.md) (its `sonic3k.asm` line citations have
drifted by +5; reverify each by label before use, do not repoint them blind).

## Goal and delivery rule

Deliver three cold routes: Sonic + Tails `$900` → seamless `$901` → boulder cutscene → `$1600` →
autoscroll → end boss → capsule → `$1601`; Tails alone on the same route; Knuckles `$900` → `$901` →
`Obj_StartNewLevel` → `$1601` (no LRZ3). Every slice is demonstrated on video, with a final
act-ordered highlights reel like the HPZ and DDZ ones.

- Every feature or fix gets a short `GameplayCaptureTool` demo with at least 30 frames of lead-in
  and lead-out. Media live outside the repository in `~/Videos/OGGF/lrz-bring-up/`: raw captures
  `raw-NN-*` (never overwritten), clips numbered by slice, `inputs/`, `native/`, `reel/`. Copy
  `make_clip.sh` and `side_by_side.sh` from `~/Videos/OGGF/ddz-bring-up/`.
- A demo is not parity evidence. A "before" build disables only the demonstrated registration in an
  uncommitted edit, reverted and recompiled immediately (`git status` clean).
- Track five claims separately per matrix row: implemented, cold-reachable, rewind-verified, native
  behaviour matched, visually matched. No aggregate green label.
- LRZ is three times the size of HPZ or DDZ (609 + 455 + 35 placements, about 30 new object
  classes, three bosses). Work stays on the local branch until the whole campaign is complete (user decision
  2026-09-17): one develop merge at the end, none per act. Act 1, act 2 and LRZ3 → `$1601`
  cold-complete are internal milestones only.

## Scope decisions (HPZ/DDZ precedents)

| Question | Decision | Reason |
| --- | --- | --- |
| Cold entry | Level-select/direct `$900` for every route; `$901` and `$1600` direct loads only for short independent checks. SOZ2 → `$900` is verified as a request/load at the end, not used as the route entry | SOZ end boss → LRZ already has bounded coverage (`level-test-coverage.md`); routes must not sit behind another zone |
| Exit | LRZ3 `Obj_StartNewLevel` `$2D` at `($FE8,$5E0)` → `$1601` and Knuckles LRZ2 `$B3`/`$2D` → `$1601` (with `SaveGame`) are **closed by this campaign**: HPZ exists. Assert the HPZ entry state (rings/timer carry, intro run) | Unblocks `TestS3kSonicTailsHpz222SegmentTraceReplay` rows ≥ `$1E46` and the HPZ plan's recorded dependency |
| Roster | Sonic + Tails, Sonic, Tails, Knuckles. Knuckles has a different start (`$10,$7AD`, intro run), route, BG chunk and no LRZ3; LRZ3 is Sonic/Tails only | Derive from the production launch contract and assert the live roster; no raw debug override to put Knuckles in `$1600` |
| Widths and donors | 320 plus one wide viewport on every mandatory mechanic from slice 1; S1 donor (Sonic) and S2 donor (Sonic/Tails) per the level test standard; final breadth per the standard | Width-sensitive owners: rock-sprite window (`Camera_X − 8 … + $150`), LRZ3 autoscroll push/kill box (`+$10 … +$120`), boss VScroll columns (19 × `$10` from `$310`), locked-BG dome regions, BG Death Egg sprite |
| Bonus/special entries | Star posts and `$85` SS entry rings (8 placed) use existing owners; verify return-to-LRZ state only. Native movies enter Pachinko/Slots/Gumball and the `$1701` chamber mid-zone | Existing subsystems; the segment splits below are caused by these detours |
| Traces | Strict replay stays late; movies supply cold-route input and native states from slice 1 | v2: short native sequences per slice, full-route replay late |

## Findings that change the plan

- **Trace identity (from `zone_act_state`, not directory names).** Sonic + Tails: `lrz` (38,885
  rows, `bk2_frame_offset` 389982) is LRZ1 → act 2 at row 25557 (apparent act at 26272) → `$1600`
  handover at 38817. **`hpz22` is LRZ3, not Hidden Palace**: 1981 rows of the autoscroll, then a
  bonus stage; `hpz22_2` (offset 434069) resumes at the post-autoscroll checkpoint `($9C0,$36C)`,
  fights the end boss, enters `$1601` at row 7558 and leaves for SSZ at 14685. Tails full chain:
  `lrz`, `lrz_2`, `lrz_3` (act 2 at 2661, `$1600` at 15165), `hpz22` (all of LRZ3, 8619 rows, then
  HPZ), `hpz22_2`. Knuckles: `lrz`, `lrz_2` (act 2 at 5985), `lrz_3` (`$901` → `$1601` directly at
  6870), `hpz22`. `lrz_completerun` (38,755 rows) duplicates the Sonic + Tails shape. Replay classes
  exist for Sonic + Tails (`…SonicTailsLrz…`, `…Hpz22…`, `…Hpz222…`) and Tails (`…Lrz…`, `…Lrz2…`,
  `…Lrz3…`, `…Hpz22…`, `…Hpz222…`); no Knuckles segment classes exist (Knuckles trace testing is out of scope).
- **Known frontiers.** `TestS3kSonicTailsLrzSegmentTraceReplay`: first error frame 208
  `tails_y_speed` (S3K `SolidObjectTop` zero-distance boundary, found not landed, frontier log
  2026-08-15). `lrz_completerun` stops compiling its hardware-timing rows
  (`unsupported-held-row-POST`, raw frame 38719). Re-measure both at `9cba6dbb6` before briefing.
- **LRZ has no events, no scroll handler and no zone objects.** No `Sonic3kLRZEvents`; zone 9 gets
  `SwScrlS3kDefault`; every `Obj_LRZ*` id is a name-only `PlaceholderObjectInstance`. Present:
  `AnPal_LRZ1/2` (`Sonic3kPaletteCycler`), the falling intro (`TestS3kLrzFallingIntroBootstrap`),
  `AizLrzRockObjectInstance`, `LrzCollapsingBridgeInstance`, LRZ branches of collapsing bridge,
  button, tension bridge, still/animated still sprites, breakable wall, automatic tunnel, art keys
  for the three badniks (no badnik classes).
- **Wrong registrations to fix first.** (1) `Sonic3kPatternAnimator` gives both acts
  `AniPLC_LRZ1`; `Offs_AniFunc` pairs `$901` with `AniPLC_LRZ2`, and both acts run the custom
  `AnimateTiles_LRZ1/2` split-DMA first (absent). (2) The animator comment says `$1600` has no
  animation; the table entry is `AnimateTiles_LRZ3` (channel 0 at `$170`, then `DoAniPLC`).
  (3) `$1600` receives `SwScrlHpz` (provider keys on zone only) and no `AnPal_LRZ3`.
- **Cheap wide win.** `Obj_InvisibleLavaBlock` (`$6E`, 44 placements) is
  `Obj_InvisibleHurtBlockHorizontal` with `shield_reaction` bit 4 set; the engine already has the
  hurt blocks but the SKL `$6E` factory returns a placeholder. All lava floor damage hangs on it.
- **`Obj_LRZRockCrusher` (`$9C`) is an act 1 object** (2 placements), not act 2 as the analysis
  says; it is the writer of the negative `Events_bg+$0C` chunk edit in `LRZ1_ScreenEvent`.
- **Rock sprites are not objects.** `Draw_LRZ_Special_Rock_Sprites` runs from the level loop and
  `sub_1CB68` emits them inside `Render_Sprites` before the first priority bucket, consuming the
  80-sprite budget. The seamless transition clears `LRZ_rocks_routine`.
- **Act 3 continuity.** `Obj_LRZ2CutsceneKnuckles` sets `Act3_flag`, saves rings/timer, then
  `StartNewLevel $1600`; level init skips the ring/timer clear for `$1600`, `LRZ3_ScreenEvent`
  stage 0 restores them, and the title card shows Lava Reef. `GameLoop` already models `Act3_flag`
  for bonus exits; check that owner before adding another.

## Design: who owns what

Resolve these owners before any consumer (v2 step 3; `s3k-zone-bring-up` deliver step 1).

| State | ROM | Engine owner |
| --- | --- | --- |
| `Events_routine_bg/fg`, `Events_bg+$00..$14`, `_unkEE9C`, `Events_fg_4/5`, `_unkFAB8`, `_unkFAA8`, `LRZ_rocks_*`, locked-BG region index | `LRZ1/2/3_*Event`, `sub_56DCA`, cutscenes, bosses | New `LrzZoneRuntimeState` in `game/sonic3k/runtime/` (pattern: `HpzZoneRuntimeState`, `DdzZoneRuntimeState`) with a registered `RewindSnapshottable` adapter and an entry in `currentRuntimeStateUsesThisEventInstance` (the DDZ restore bug). One state class for `$900/$901/$1600`; objects read it through `services()` |
| Camera bounds, locks, LRZ3 autoscroll, boss arena, `Obj_IncLevEndXGradual` | `LRZ3_ScreenInit`, `Special_events_routine $14` (`loc_59F3C`), `LRZ3_BackgroundEvent` stage 4, `loc_799E0` | New `Sonic3kLRZEvents` (both acts and, keyed on `$1600`, the boss act) registered in `Sonic3kLevelEventManager`; autoscroll runs in the special-events phase, not an object slot; reuse `S3kCameraGradualObjectInstance`/`S3kCameraStoredBounds` |
| Seamless `$900` → `$901` | `LRZ1_BackgroundEvent` stage `$C`: `−$2C00` on players, objects, camera and bounds; KosM queue, PLC `$30`, rocks reset | `S3kSeamlessMutationExecutor` + `S3kTransitionEventBridge` (pattern: `SozActTransitionHandoff`, ICZ handoff); level variables explicitly cleared at the load |
| Layout edits | `LRZ1_ScreenEvent` (`Events_bg+$0C` ±), `LRZ1_BackgroundInit` Knuckles `$F6`, `LRZ3_ScreenEvent` stages 4/`$C` | `ZoneLayoutMutationPipeline`/`LevelMutationSurface` only; pattern-only changes use `invalidatePatternLookup` (a full tilemap rebuild is a ~25 ms hitch and reverts direct Plane A writes) |
| Parallax, locked dome BG, shimmer, per-column VScroll | `LRZ1_Deform`, `sub_57082`, `sub_56DAC`, `sub_59D82/59DA2/59DBC`, `sub_59DDE`, `word_5A106` | New `SwScrlLrz` (acts 1-2) and `SwScrlLrz3`; provider keyed on zone **and act** so `$1601` keeps `SwScrlHpz`. Shimmer table already ported in `SwScrlAiz`/`SwScrlSoz` (share, do not copy); per-column VScroll through the existing AIZ/Gumball path in `LevelScrollPresentation`. No `@ModApi` surface change (check both annotation spellings first) |
| Animated tiles | `loc_282D0` channels 0/1 keyed on `Events_bg+$12/$10 − Camera_X_pos_BG_copy`, split tables `word_2834C`/`word_283D2`; `AniPLC_LRZ1/2`; `Anim_Counters` seeded `−1` for `$900` | `Sonic3kPatternAnimator` graph channels (pattern: ICZ/MHZ/CNZ direct-DMA). The phase inputs come from `SwScrlLrz`, so scroll ownership lands first |
| Palette | `AnPal_LRZ1/2` (present), `AnPal_LRZ3` gate `Palette_cycle_counters+$00` ∈ {0, `$80`, 1}, `Pal_LRZBossFire`, miniboss/end-boss/rock-crusher palettes, flash fades | `Sonic3kPaletteCycler` + `S3kPaletteOwners` (new LRZ3 and boss owners); deferred palette-ownership writes must resolve before a fade copies Normal → Target (DDZ line-3 bug). `AnPal_LRZ2` channel D keeps the `FixBugs = 0` duplicated pair, with the branch comment |
| Rock sprites | `Draw_LRZ_Special_Rock_Sprites`, `sub_1CB68`, placement and attribute bins | New ROM-backed `LrzRockSpriteRenderer` behind `Sonic3kZoneFeatureProvider`, drawn ahead of bucket 0, windowing state in the runtime state. Decide from the ROM window constants what a wide viewport shows and record it as a presentation choice |
| Lava hurt and push | `Obj_56EA0`, `Obj_59FC4`/`Obj_LRZ3Platform`, `sub_24280`, `$6E` | Object classes; fire-shield test is `Status_FireShield` on Player 1, **no shield check for Player 2** in `Obj_56EA0` (verify, keep as ROM behaviour) |

Rules that bind every slice: `GameRules`/providers, never zone-name carve-outs in shared code;
`services()`; each gate names the ROM clock it reads (shimmer reads `Level_frame_counter`, the
`StartNewLevel` rumble reads `V_int_run_count`); `FixBugs = 0` branches commented; constants cite
their routine; nothing keys on a fixture, frame index or fitted measurement; trace rows never
hydrate gameplay. `GameLoop`/`Engine.draw` are size-ratcheted: run `-Pguards` before committing
there. New objects need recreation and captured state; children need a probe constructor.

## Dependency-ordered slices

Each slice: reverify its inventory rows by label → discriminating failing test with ROM-derived
expectations → implementation → cold-route extension with preserved inputs → short native comparison
on a named question → 320 + wide + donor check → rewind spot → demo clip → boundary review → plan
evidence entry. Coupled boundaries (1, 2, 6, 8, 9, 10) get an independent review; routine object
families (3, 4, 7) share one each.

| Slice | Scope and ROM owners | Early check and principal risk |
| --- | --- | --- |
| 0. Baseline and identity | Matrices `validation/levels/s3k-lrz-act1.md`, `-act2.md`, `-boss.md`; coverage-backlog rows (`S3K_LAVA_REEF_1/2`, `S3K_LRZ_BOSS`); decoded per-act object/subtype census; trace identities re-measured; media root; `raw-00` captures of all three acts as they are | "Before" footage exists first. Record the placeholder count per act as the baseline number |
| 1. Runtime state, scroll, registration fixes | `LrzZoneRuntimeState`, `Sonic3kLRZEvents` shell with `LRZ1/2_ScreenEvent` (shake offset), `SwScrlLrz` (`LRZ1_Deform`, `sub_57082`, draw/deform arrays), act-keyed scroll provider, `AniPLC_LRZ2` for `$901`, `$6E` lava block | Band fractions from the routines (16.16 `/8`, `/4`, the act 2 Y `−¼`), not measurement; wide seams; `$1601` must still get `SwScrlHpz` (HPZ regression check) |
| 2. Animated tiles and rock sprites | `loc_282D0` both channels and split tables, `AniPLC_LRZ1/2` order after them, `Anim_Counters` seed; rock renderer | Phase depends on slice 1 outputs `Events_bg+$10/$12`; two DMA writes per update and write order; sprite-budget interaction of rocks with objects; rewind of window pointers |
| 3. Act 1 traversal objects | `$15` corkscrew, `$16` wall ride, `$17` sinking rock, `$18` falling spike, `$19`/`$1A` doors, `$1B` fireball launcher, `$1C` button, `$1D` shooting trigger, `$1E` dash elevator, `$1F` lava fall, `$20` swinging spike ball, `$21` smashing spike platform, `$22` spike ball, `$9C` rock crusher (+ chunk edit), LRZ subtypes of `$05` rock (89 placed) | Follow `s3k-implement-object`. Corkscrew/wall ride/dash elevator take player control: check S1-donor spindash absence on the dash elevator and team capture/release. Slot order decides sibling draw/RNG order |
| 4. Badniks | `Obj_Fireworm` (`$99`, segments), `Obj_Iwamodoki` (`$9A`, 66 placed), `Obj_Toxomister` (`$9B`, mist slows the player), `PLCKosM_LRZ` | Art readiness via `LoadEnemyArt`; Toxomister's player effect is a player-state hook, inventory it as such |
| 5. Act 1 dome regions | `sub_56DCA`/`word_56F88`, `sub_56DAC`, `Obj_56EA0`, BG stages 4/8, Knuckles `$F6` BG chunk | Region 1's table row has min > max as transcribed: reread `word_56F88` before coding. BG bottom-up refresh on exit; entry from both directions; rewind inside a region |
| 6. Miniboss, results and seamless act change | `Obj_LRZMiniboss` (all 11 routines, 24 children, `word_78EAA` palette script, three palettes), `Obj_EndSignControl`, results, `Events_fg_5` → stage `$C` → `$901`, `LRZ2_BackgroundEvent` stages 0/4 | `s3k-implement-boss`; sprite-composition audit against lava/rocks. The transition frame moves players, objects, camera, bounds and rock window together; timeline isolation across it |
| 7. Act 2 traversal objects | `$25` chained platforms, `$29` flame thrower (52), `$2B`/`$2C` orbiting spike balls (52), `$2D` solid moving platforms (52), `$32` turbine sprites, `$37` spike ball launcher, `$24` tunnel (LRZ subtypes), `$0D`, `$0F`, Death Egg BG sprite `loc_5711E` (absent for Knuckles) | The Death Egg sprite is positioned by the scroll routine, art queued on first visibility; wide-viewport clamp `−$7E0` |
| 8. Act 2 exits | Sonic/Tails: `Obj_LRZ2CutsceneKnuckles` (`$AE`), `CutsceneKnux_LRZ2`, boulder, `Act3_flag`, `StartNewLevel $1600`. Knuckles: `Obj_StartNewLevel` (`$B3`) as a real shared object (`Check_InMyRange word_86426`, `SaveGame` gate) | Which character reaches which exit is geometry, not a flag: prove it on the cold routes. Reuse the HPZ `CutsceneKnuckles` art/palette owners |
| 9. LRZ3 entry, flash and autoscroll | `LRZ3_ScreenInit` (incl. respawn branch X ≥ `$480`), screen stages 0-`$C`, BG stages 0-8, Death Egg flash sequence, `Obj_LRZ3Autoscroll` (`$9E`), `$14` autoscroll (7 stages, push and crush-kill), `Obj_LRZ3Platform` (`$AD`), chunk `$17` writes, `AnimateTiles_LRZ3`, `AnPal_LRZ3`, `SwScrlLrz3` shimmer, `loc_68A6` falling intro for `$1600` | Star-post respawn enters mid-machine (`Events_bg+$00 = $10`, delay `$2D`): test it, the native movie does exactly this. Kill needs `Status_Push` at the left edge; wide viewport vs `+$120` right cap |
| 10. LRZ3 end boss and handoff | BG stage 4 lock at `($A00, max Y)`, `Obj_LRZEndBoss` (14 hits, six routines), `Obj_59FC4` sloped lava surface (`SolidObjectTopSloped2`, push `Events_bg+$14`), per-column VScroll (`Special_V_int_routine 4/$C`), defeat → capsule → `mus_LRZ2` fade → `$EC0` gradual → `StartNewLevel $2D` → `$1601` | The surface slope, BG columns and solid share one table (`HScroll_table+$110`): one owner. Cold arrival in HPZ with correct carry-over |
| 11. Routes and acceptance | Cold routes from the movies (Sonic + Tails 389982, Tails 370581, Knuckles 387121; the capture path runs one frame behind the headless fixture; skip movie input on repeated `lfc`), authored inputs where a movie detours into a bonus stage, matrix breadth, rewind spots, strict replay frontiers, moving inspection at 320 and wide | A positioned boss success does not advance the cold frontier. Wide full routes likely need independent input (SOZ precedent) |
| 12. Media and delivery | Reel, archive index, change-based validation against `9cba6dbb6`, docs, integration | See below |

## Native probes

Question-led, disassembly first, BizHawk 2.11 through the shared capture host with an LRZ exporter
modelled on `tools/bizhawk/capture_ddz_route_reference.lua` (keep `plan.slots`), output to `OGGF_OUT`
only, no `print()`. Pass 1 saves states near each window from all three movies; later probes load
them. Record ROM SHA-1, movie SHA-256, host exit code and the probe's own error status (a failing Lua
probe exits 0). Verify a process is stray before killing it. Planned windows (Sonic + Tails rows):

| State | Segment/row (approx.) | Question |
| --- | --- | --- |
| Entry | `lrz` 0 | Falling intro, first-frame BG bands, animated-tile phase, rock sprites on frame 0 |
| Dome region | act 1, first entry | Locked-BG switch frame, `_unkEE9C` platform phase, refresh rows on exit |
| Miniboss | before 25557 | Rise, track cadence, palette script steps, defeat palette |
| Seamless change | 25557-26272 | One-frame `−$2C00` shift, KosM/PLC readiness, BG bottom-up refresh, title-card/apparent-act lag |
| Act 2 Death Egg | act 2 | Sprite position vs BG bands, art queue frame |
| Cutscene | before 38817 | Control lock frame, boulder physics, `$1600` request frame |
| Autoscroll | `hpz22` 0-1981 | Flash palette gate, stage thresholds, push at left edge |
| Respawn + boss | `hpz22_2` 0-7558 | `ScreenInit` respawn state, VScroll columns, slope push, hit cadence, handoff frame |
| Knuckles | `lrz_3` end | `$F6` BG, no Death Egg, `SaveGame` + `$1601` request |

Choose fields and intervals before looking at engine output. Palette lines are `$20` bytes. Break
each new comparison on purpose once before trusting it. Seeded evidence is labelled seeded.

## Demo and reel plan

Clips (renumber as the work dictates): `00` baselines; `01` parallax before/after per act; `02`
animated lava and rock sprites; `03a…` one per act 1 object family (corkscrew, wall ride, doors and
buttons, dash elevator, crushers, spike balls, lava fall, rock crusher); `04` the three badniks;
`05` dome region and lava platform with and without fire shield; `06a` miniboss, `06b` results and
seamless change; `07a…` act 2 families (flame throwers, orbiting balls, moving/chained platforms,
turbines, launcher), `07d` Death Egg in the BG; `08a` Knuckles boulder cutscene, `08b` Knuckles
route exit; `09a` flash and autoscroll, `09b` crush death, `09c` star-post respawn; `10a` end boss
and lava surface, `10b` capsule and walk into HPZ; `20-22` uncut cold routes per character; one wide
route. Captures have no audio: SFX/music claims are test-backed, not shown.

Reel (`gameplay-highlights`, scripts copied from DDZ): act order — entry → act 1 mechanics →
miniboss → seamless change → act 2 mechanics → cutscene → autoscroll → end boss → HPZ arrival, with
the Knuckles exit as the character variant. Delivered revision only, one example per feature,
recorded speed, nearest-neighbour integer scaling, labels state positioned vs cold. Re-read state
CSVs after every route change. Uncut runs stay in the archive as the traversal evidence.

## Acceptance matrix (seed)

Rows per act: load/identity, title card, intro (falling / Knuckles run), parallax, locked dome BG,
animated tiles (custom ch0, ch1, AniPLC), palette cycles, rock sprites, each object family, each
badnik, lava block/fire shield, dome lava platform, miniboss, results, seamless change, Death Egg BG,
boulder cutscene, Knuckles exit, act 3 carry-over, flash, autoscroll (push, kill, respawn), LRZ3
platforms and chunk writes, shimmer, VScroll arena, end boss, lava surface, capsule, `$1601`
handoff, SOZ2 entry, bonus/special return. Columns: the five claims, each with command, commit,
configuration, setup, result, skips and limits; products: character × width × donor × team shape.
Rewind spots (before / active / after, restore equality plus forward replay): corkscrew capture,
dash elevator, dome region entry, miniboss attack, the seamless-change frame, cutscene lock,
autoscroll stage change, boss lock, slope at amplitude, defeat, handoff fade; timeline isolation for
death/star-post reloads, `$1600` and `$1601` loads.

## Validation and delivery

Focused tests and `run_categories.py --category NAME --run` during slices, through
`maven_queue.py` with `-Dmse=off` and absolute ROM paths (wrong paths skip silently: inspect skips).
Mandatory S3K checks stay green: `TestS3kAiz1SkipHeadless`, `TestSonic3kLevelLoading`,
`TestSonic3kBootstrapResolver`, `TestSonic3kDecodingUtils`; plus `TestS3kLrzFallingIntroBootstrap`,
`TestS3kLrzPaletteCycling`, the HPZ and SOZ suites (shared scroll provider, shimmer table, cutscene
Knuckles). One combined `run_categories.py --base 9cba6dbb6 --run` at the final delivery, after focused
fixes and docs, with class count, cost and stopping rule stated first; no tree edits during the run;
acknowledge the run. Shared-owner changes (scroll provider, pattern animator, seamless executor,
hurt block, `StartNewLevel`) make this normal change-based validation. Attribute any red to a matched
baseline check in a separate lead-verify worktree; clean build after merging before measuring.
Strict replay: record each LRZ/`hpz22` frontier (command, commit, first error frame/field) in
`docs/status/trace-frontier-log.md`.

Docs at delivery: matrices and coverage backlog, this plan's status/evidence, `CHANGELOG.0.7.md`,
`docs/status/s3k-known-bugs.md` for gaps (the discrepancies file is intentional-only), corrections to
`lrz-analysis.md`, the HPZ plan's dependency note, agent-workflow README for promoted probes, lessons
into existing catalogues. Commits carry all seven trailers; no `--no-verify`; never `git stash`;
start git chains with an explicit `cd`; check `git worktree list` before merging.

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

## Open questions (each with its kill condition)

- Who writes the **positive** `Events_bg+$0C` in LRZ1 (chunk `$9C`)? Only the rock crusher's `st`
  (negative) was found. Kill: a label-scoped search of act 1 objects finds the writer, or none
  exists and the branch is recorded as unreachable.
- Does Sonic's act 2 geometry ever reach the `$B3` exit, or Knuckles's the `$AE` cutscene (which
  self-skips on `character_id 2`)? Kill: decoded positions against both cold routes.
- Does `GameLoop`'s existing `Act3_flag` model cover the LRZ2 → `$1600` carry? Kill: read it.
- Analysis region table row 1 and the LRZ3 boss routine summary are unverified; slice owners reread.

## Status

| Claim | State |
| --- | --- |
| Implemented | Not started. Present at `9cba6dbb6`: `AnPal_LRZ1/2`, falling intro, breakable rock, `$31` collapsing bridge, shared-object LRZ branches. Absent: events, scroll, custom animated tiles, rock sprites, all other `Obj_LRZ*`, badniks, three bosses, cutscenes, `StartNewLevel` |
| Cold-reachable | Not started |
| Rewind-verified | Not started |
| Native behaviour matched | Not started (Sonic + Tails `lrz` frontier frame 208, inherited) |
| Visually matched | Not started |

Out of scope, recorded as dependencies: SSZ after HPZ (SSZ campaign); Knuckles replay classes and
fixtures' harness work beyond recording frontiers; the `lrz_completerun` hardware-timing compile
blocker unless it blocks a named slice.

## Evidence log

(empty)
