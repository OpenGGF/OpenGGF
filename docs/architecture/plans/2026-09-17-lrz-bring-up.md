# Lava Reef Zone: methodology v2 bring-up plan

Date: 2026-09-17. Planned branch `feature/ai-lrz-bring-up` in `.worktrees/ai-lrz-bring-up`;
execution base develop `035e48a58` (pin this SHA for the combined change-based validation; it is
`9cba6dbb6` plus the merge of this plan and its SSZ/DEZ siblings). Applies
[methodology v2](../designs/2026-09-15-zone-methodology-v2.md) with the refinements from the
[SOZ](2026-09-15-soz-methodology-v2.md), [HPZ](2026-09-16-hpz-bring-up.md) and
[DDZ](2026-09-17-ddz-bring-up.md) campaigns to LRZ1 (`$900`), LRZ2 (`$901`) and the boss act LRZ3
(`$1600`) with its handoff to Hidden Palace (`$1601`). Design and plan by Fable 5.1; implementation
and execution by Opus, as for HPZ and DDZ. Entry skill:
[s3k-zone-bring-up](../../../.agents/skills/s3k-zone-bring-up/SKILL.md). Starting inventory:
[lrz-analysis.md](../research/s3k-zones/lrz-analysis.md) (line citations re-resolved by label and four
content errors corrected on 2026-09-17; labels stay authoritative) and the per-subtype
[placement inventory](../research/s3k-zones/lrz-object-inventory.md) (1099 objects, 251 rows,
byte-matched to the ROM; the `$1D`/`$A0` row was restored on 2026-09-17, see the evidence log). `loc_` labels are ROM addresses, never line numbers.

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
| Widths and donors | 320 plus one wide viewport on every mandatory mechanic from slice 1; S1 donor (Sonic) and S2 donor (Sonic/Tails) per the level test standard; final breadth per the standard | Width-sensitive owners: rock-sprite window (X front `Camera_X − 8`, back `Camera_X + $148`; see Verified ROM values), LRZ3 autoscroll push/kill box (`+$10 … +$120`), boss VScroll columns (`word_5A106`: one `$310` band then 18 × `$10`), locked-BG dome regions, BG Death Egg sprite |
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
  (`unsupported-held-row-POST`, raw frame 38719). Re-measured at `3418eba6e` in slice 0: the
  `lrz` frontier is unchanged (11909 errors; first error frame 208 `tails_y_speed`
  expected `0x07BD`, actual `0x0000`).
- **LRZ has no events, no scroll handler and no zone objects.** No `Sonic3kLRZEvents`; zone 9 gets
  `SwScrlS3kDefault`; every `Obj_LRZ*` id is a name-only `PlaceholderObjectInstance`. Present:
  `AnPal_LRZ1/2` (`Sonic3kPaletteCycler`), the falling intro (`TestS3kLrzFallingIntroBootstrap`),
  `AizLrzRockObjectInstance`, `LrzCollapsingBridgeInstance`, LRZ branches of collapsing bridge,
  button, tension bridge, still/animated still sprites, breakable wall, automatic tunnel, art keys
  for the three badniks (no badnik classes).
- **Wrong registrations to fix first.** (1) `Sonic3kPatternAnimator` gives both acts
  `AniPLC_LRZ1`; `Offs_AniFunc` pairs `$901` with `AniPLC_LRZ2`, and both acts run the custom
  `AnimateTiles_LRZ1/2` split-DMA first (absent). (2) The animator comment says `$1600` has no
  animation; the table entry is `AnimateTiles_LRZ3` (channel 0 **only**, at tile `$170`: `loc_2833C` returns for `Current_zone $16`, so no channel 1, and the `$1600` AniPLC entry is `AniPLC_NULL`).
  (3) `$1600` receives `SwScrlHpz` (provider keys on zone only) and no `AnPal_LRZ3`.
- **Cheap wide win.** `Obj_InvisibleLavaBlock` (`$6E`, 44 placements) is
  `Obj_InvisibleHurtBlockHorizontal` with `shield_reaction` bit 4 set; the engine already has the
  hurt blocks but the SKL `$6E` factory returns a placeholder. All lava floor damage hangs on it.
- **`Obj_LRZRockCrusher` (`$9C`) is an act 1 object** (subtype 0 at `($FA0,$71C)`, subtype 2 at
  `($5A0,$81C)`) and writes **both** signs of `Events_bg+$0C` for `LRZ1_ScreenEvent`: its timer child
  `loc_90512` runs `st (Events_bg+$0C)` for subtype 0 (high byte `$FF` → negative → the
  `$44/$00/$4A` + `$3E/$00/$4B` edits) and `st (Events_bg+$0D)` otherwise (low byte → `$00FF`
  positive → the `$9C` edit). Each branch also allocates `Obj_LRZCollapsingBridge` with `$32 = 1`.
- **Placement baseline (inventory).** 534 of 1099 placements are placeholders: 239 act 1, 281
  act 2, 14 boss act; 23 SKL-branch ids plus four unregistered ids (`$1A $1C $1D $25`). The miniboss
  `$9D` is **placed** at `($2CA0,$880)`; the end boss, capsule, `$1600` `StartNewLevel`, dome
  platform and Death Egg sprite are event-spawned. Ring files start with a `(0,0)` sentinel record
  the ROM skips (`loc_EB52`): live rings 331/281/52.
- **`$0F` in `$1600` is an HPZ bridge by ROM design.** `Obj_CollapsingBridge` picks
  `Map_HPZCollapsingBridge` for `Current_zone $16` (8 placements plus the flash sequence's spawn at
  `($60,$4D0)`); the engine switch already does the same. Verify the art under it, do not "fix" it.
- **Dome region table corrected.** `word_56F88` is three 5-word rows (X min, X max, Y min, Y max,
  threshold): `$1AC0,$1B40,$840,$8C0,X≥$1B00`; `$2240,$2340,$840,$880,X<$22C0`;
  `$20C0,$2180,$740,$800,Y≥$7A0`. X max inclusive, Y max exclusive; tested only inside the box.
- **Rock sprites are not objects.** `Draw_LRZ_Special_Rock_Sprites` runs from the level loop and
  `sub_1CB68` runs at `Render_Sprites_NextLevel` while `a5 == Sprite_table_input`: **after** the HUD,
  rings and every bucket-0 object, **before** bucket 1; zone 9 only (never `$1600`). Each rock does
  `subq.w #1,d7` with no exhaustion test, so rocks consume the 80-sprite budget ahead of buckets 1-7. The seamless transition clears `LRZ_rocks_routine`.
- **Act 3 continuity (corrected from the ROM).** `loc_63C14` (Player 1 Y ≥ `$4C0`) sets `Act3_flag`,
  copies `Ring_count` → `Act3_ring_count`, `Timer` → `Act3_timer`, `status_secondary` →
  `Saved2_status_secondary`, then `StartNewLevel $1600`. Level init **does** zero rings and timer
  (`loc_63B4`, unless a star post was hit); `$1600` only skips the `Saved2_status_secondary` clear
  (shield carry). `LRZ3_ScreenEvent` stage 0 (`loc_59B1C`) restores rings/timer and flags the HUD.
  `Act3_flag` makes `loc_62B6` **skip the title card** on that load and is cleared at `loc_62FE`; a
  level-select `$1600` load shows the Lava Reef card (`sonic3k.asm:62147`). `GameLoop` only cites
  `Act3_flag` in a comment (bonus return); **no engine owner exists**. DEZ2 → `$1700` (`loc_7F310`)
  is the identical sequence: build one owner (design table) and tell the DEZ campaign.

## Design: who owns what

Resolve these owners before any consumer (v2 step 3; `s3k-zone-bring-up` deliver step 1).

| State | ROM | Engine owner |
| --- | --- | --- |
| `Events_routine_bg/fg`, `Events_bg+$00..$14`, `_unkEE9C`, `Events_fg_4/5`, `_unkFAB8`, `_unkFAA8`, `LRZ_rocks_*`, locked-BG region index | `LRZ1/2/3_*Event`, `sub_56DCA`, cutscenes, bosses | New `LrzZoneRuntimeState` in `game/sonic3k/runtime/` (pattern: `HpzZoneRuntimeState`, `DdzZoneRuntimeState`) with a registered `RewindSnapshottable` adapter and an entry in `currentRuntimeStateUsesThisEventInstance` (the DDZ restore bug). One state class for `$900/$901/$1600`; objects read it through `services()` |
| Camera bounds, locks, LRZ3 autoscroll, boss arena, `Obj_IncLevEndXGradual` | `LRZ3_ScreenInit`, `Special_events_routine $14` (`loc_59E46`, `SpecialEvents_Index` entry 5; `loc_59F3C` is only its camera-apply tail), `LRZ3_BackgroundEvent` stage 4, `loc_799E0` | New `Sonic3kLRZEvents` (both acts and, keyed on `$1600`, the boss act) registered in `Sonic3kLevelEventManager`; autoscroll runs in the special-events phase, not an object slot; reuse `S3kCameraGradualObjectInstance`/`S3kCameraStoredBounds` |
| Seamless `$900` → `$901` | `LRZ1_BackgroundEvent` stage `$C`: `−$2C00` on players, objects, camera and bounds; KosM queue, PLC `$30`, rocks reset | `S3kSeamlessMutationExecutor` + `S3kTransitionEventBridge` (pattern: `SozActTransitionHandoff`, ICZ handoff); level variables explicitly cleared at the load |
| Layout edits | `LRZ1_ScreenEvent` (`Events_bg+$0C` ±), `LRZ1_BackgroundInit` Knuckles `$F6`, `LRZ3_ScreenEvent` stages 4/`$C` | `ZoneLayoutMutationPipeline`/`LevelMutationSurface` only; pattern-only changes use `invalidatePatternLookup` (a full tilemap rebuild is a ~25 ms hitch and reverts direct Plane A writes) |
| Parallax, locked dome BG, shimmer, per-column VScroll | `LRZ1_Deform`, `sub_57082`, `sub_56DAC`, `sub_59D82/59DA2/59DBC`, `sub_59DDE`, `word_5A106` | New `SwScrlLrz` (acts 1-2) and `SwScrlLrz3`; provider keyed on zone **and act** so `$1601` keeps `SwScrlHpz`. Shimmer table already ported in `SwScrlAiz`/`SwScrlSoz` (share, do not copy); per-column VScroll through the existing AIZ/Gumball path in `LevelScrollPresentation`. No `@ModApi` surface change (check both annotation spellings first) |
| Animated tiles | `loc_282D0` channels 0/1 keyed on `Events_bg+$12/$10 − Camera_X_pos_BG_copy`, split tables `word_2834C`/`word_283D2`; `AniPLC_LRZ1/2`; `Anim_Counters+1/+3` seeded `−1` by `Animate_Init` for `$900` and `$1600`, **not** `$901` (see Verified ROM values) | `Sonic3kPatternAnimator` graph channels (pattern: ICZ/MHZ/CNZ direct-DMA). The phase inputs come from `SwScrlLrz`, so scroll ownership lands first |
| Palette | `AnPal_LRZ1/2` (present), `AnPal_LRZ3` gate `Palette_cycle_counters+$00` ∈ {0, `$80`, 1}, `Pal_LRZBossFire`, miniboss/end-boss/rock-crusher palettes, flash fades | `Sonic3kPaletteCycler` + `S3kPaletteOwners` (new LRZ3 and boss owners); deferred palette-ownership writes must resolve before a fade copies Normal → Target (DDZ line-3 bug). `AnPal_LRZ2` channel D keeps the `FixBugs = 0` duplicated pair, with the branch comment |
| Rock sprites | `Draw_LRZ_Special_Rock_Sprites`, `sub_1CB68`, placement and attribute bins | New ROM-backed `LrzRockSpriteRenderer` behind `Sonic3kZoneFeatureProvider`, drawn between bucket 0 and bucket 1 (after HUD, rings and bucket-0 objects), windowing state in the runtime state. Decide from the ROM window constants what a wide viewport shows and record it as a presentation choice |
| Act 3 carry (`Act3_flag`, `Act3_ring_count`, `Act3_timer`, `Saved2_status_secondary`) | `loc_63C14`, `loc_62B6`/`loc_62FE`, `loc_63B4`, `loc_59B1C` | One engine-internal carry record on the level-transition path (`LevelTransitionCoordinator`/`LevelManager.requestZoneAndAct` family; pattern: the bonus-return saved state in `GameLoop`), consumed by `Sonic3kLRZEvents` stage 0: suppress the title card for that load only, restore rings/timer, keep the shield. Not a `GameLoop` body edit (size ratchet) and not a public `@ModApi` member. Cleared on consume so a level-select `$1600` load is unaffected; snapshot it for rewind or prove the load isolates the timeline |
| Shield-gated hurt blocks | `sub_1F58C`: skip when `shield_reaction(a0) & $73 & shield_reaction(a1)` ≠ 0; `$6E` sets bit 4, `$6D` bit 5 | Add the mask to the existing `Sonic3kInvisibleHurtBlockHObjectInstance` as a constructor parameter (semantic: "reaction bits"), register `$6E` under SKL. The DEZ campaign's `$6D` reuses it. CPU sidekick path keeps its existing branch |
| Lava hurt and push | `Obj_56EA0`, `Obj_59FC4`/`Obj_LRZ3Platform`, `sub_24280`, `$6E` | Object classes; fire-shield test is `Status_FireShield` on Player 1, **no shield check for Player 2** in `Obj_56EA0` (verified at `loc_56F54`; keep as ROM behaviour, comment it) |

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
| 1. Runtime state, scroll, registration fixes | `LrzZoneRuntimeState`, `Sonic3kLRZEvents` shell with `LRZ1/2_ScreenEvent` (shake offset), `SwScrlLrz` (`LRZ1_Deform`, `sub_57082`, draw/deform arrays), act-keyed scroll provider, `AniPLC_LRZ2` for `$901`, `$6E` lava block | Band values exactly as in Verified ROM values (b = X/8, s = X/32 in 16.16), not measurement; wide seams; `$1601` must still get `SwScrlHpz` (HPZ regression check) |
| 2. Animated tiles and rock sprites | `loc_282D0` both channels and split tables, `AniPLC_LRZ1/2` order after them, `Anim_Counters` seed; rock renderer | Phase depends on slice 1 outputs `Events_bg+$10/$12`; two DMA writes per update and write order; sprite-budget interaction of rocks with objects; rewind of window pointers |
| 3. Act 1 traversal objects | `$15` corkscrew, `$16` wall ride, `$17` sinking rock, `$18` falling spike, `$19`/`$1A` doors, `$1B` fireball launcher, `$1C` button, `$1D` shooting trigger, `$1E` dash elevator, `$1F` lava fall, `$20` swinging spike ball, `$21` smashing spike platform, `$22` spike ball, `$9C` rock crusher (+ chunk edit), LRZ subtypes of `$05` rock (89 placed) | Follow `s3k-implement-object`. Corkscrew/wall ride/dash elevator take player control: check S1-donor spindash absence on the dash elevator and team capture/release. Slot order decides sibling draw/RNG order |
| 4. Badniks | `Obj_Fireworm` (`$99`, segments), `Obj_Iwamodoki` (`$9A`, 66 placed), `Obj_Toxomister` (`$9B`, mist slows the player), `PLCKosM_LRZ` | Art readiness via `LoadEnemyArt`; Toxomister's player effect is a player-state hook, inventory it as such |
| 5. Act 1 dome regions | `sub_56DCA`/`word_56F88` (corrected rows above), `sub_56DAC`, `Obj_56EA0`, BG stages 4/8, Knuckles `$F6` BG chunk | BG bottom-up refresh on exit (`Draw_delayed_rowcount $F`); each region entered from its ROM side and left again; rewind inside a region; `_unkEE9C` platform phase restarts at 0 on every entry |
| 6. Miniboss, results and seamless act change | `Obj_LRZMiniboss` (placed `$9D`; count routines and children from the code before coding, three palettes loaded at init/phase, `word_78EAA` **post-defeat** rotation only), `Obj_EndSignControl`, results, `Events_fg_5` → stage `$C` → `$901`, `LRZ2_BackgroundEvent` stages 0/4 | `s3k-implement-boss`; sprite-composition audit against lava/rocks. The transition frame moves players, objects, camera, bounds and rock window together; timeline isolation across it |
| 7. Act 2 traversal objects | `$25` chained platforms, `$29` flame thrower (52), `$2B`/`$2C` orbiting spike balls (52), `$2D` solid moving platforms (52), `$32` turbine sprites, `$37` spike ball launcher, `$24` tunnel (LRZ subtypes), `$0D`, `$0F`, Death Egg BG sprite `loc_5711E` (absent for Knuckles) | The Death Egg sprite is positioned by the scroll routine, art queued on first visibility; visibility gate at `−$7E0` (not a clamp; see Verified ROM values) |
| 8. Act 2 exits | Sonic/Tails: `Obj_LRZ2CutsceneKnuckles` (`$AE`), `CutsceneKnux_LRZ2`, boulder, `Act3_flag`, `StartNewLevel $1600`. Knuckles: `Obj_StartNewLevel` (`$B3`) as a real shared object (`Check_InMyRange word_86426`, `SaveGame` gate) | Which character reaches which exit is geometry, not a flag: prove it on the cold routes. Reuse the HPZ `CutsceneKnuckles` art/palette owners |
| 9. LRZ3 entry, flash and autoscroll | `LRZ3_ScreenInit` (incl. respawn branch X ≥ `$480`), screen stages 0-`$C`, BG stages 0-`$10` (five), Death Egg flash sequence, `Obj_LRZ3Autoscroll` (`$9E`), `$14` autoscroll (7 stages, push and crush-kill), `Obj_LRZ3Platform` (`$AD`), chunk `$17` writes, `AnimateTiles_LRZ3`, `AnPal_LRZ3`, `SwScrlLrz3` shimmer, `loc_68A6` falling intro for `$1600` | Star-post respawn enters mid-machine (`Events_bg+$00 = $10`, delay `$2D`): test it, the native movie does exactly this. Kill needs `Status_Push` at the left edge; wide viewport vs `+$120` right cap |
| 10. LRZ3 end boss and handoff | BG stage 4 lock (needs `Camera_Y ≥ $500`, `Camera_X == $A00` exactly and `Camera_Y == Camera_max_Y_pos`; then `+8` → stage `$C`), `Obj_LRZEndBoss` (14 hits, six routines), `Obj_59FC4` sloped lava surface (`SolidObjectTopSloped2`, push `Events_bg+$14`), per-column VScroll (`Special_V_int_routine 4/$C`), defeat → capsule → `mus_LRZ2` fade → `$EC0` gradual → `StartNewLevel $2D` → `$1601` | The surface slope, BG columns and solid share one table (`HScroll_table+$110`): one owner. Cold arrival in HPZ with correct carry-over |
| 11. Routes and acceptance | Cold routes from the movies (Sonic + Tails 389982, Tails 370581, Knuckles 387121; the capture path runs one frame behind the headless fixture; skip movie input on repeated `lfc`), authored inputs where a movie detours into a bonus stage, matrix breadth, rewind spots, strict replay frontiers, moving inspection at 320 and wide | A positioned boss success does not advance the cold frontier. Wide full routes likely need independent input (SOZ precedent) |
| 12. Media and delivery | Reel, archive index, change-based validation against `035e48a58`, docs, integration | See below |

### Slice work orders

A row belongs to the slice that **implements its class**; placements of that class in a later act are
verified by that act's traversal slice. `V` rows (already concrete) are pinned in slice 0 by
`TestS3kLrzPlacementCensus`: a ROM-backed production census that loads each act, builds every placed
object through the registry, and asserts per `(id, subtype)` either the expected concrete class or
membership in the declared placeholder baseline (239 / 281 / 14). Its expectations come from the
inventory (ROM bytes), so it goes red whenever a slice forgets a subtype, and the baseline only
ratchets down. Paths: objects `src/main/java/com/openggf/game/sonic3k/objects/Lrz*.java`, badniks
`…/objects/badniks/`, bosses `…/objects/bosses/`, events `…/sonic3k/events/Sonic3kLRZEvents.java`,
state `…/sonic3k/runtime/LrzZoneRuntimeState.java`, scroll `…/sonic3k/scroll/SwScrlLrz*.java`; route
and lifecycle tests `src/test/java/com/openggf/tests/TestS3kLrz*.java`, unit tests beside the HPZ
ones (`…/game/sonic3k/{scroll,runtime,objects}/`); matrices `docs/architecture/validation/levels/`.
Every slice also loads `s3k-disasm-guide`; capture slices load `gameplay-capture` and
`bk2-input-authoring`; native questions load `bizhawk-native-reference-capture`.

| Slice | Inventory rows (placements) | Skills | Create / modify | First failing test → oracle | Done when |
| --- | --- | --- | --- | --- | --- |
| 0 | All 251 rows (census); 105 `V` rows (533) plus `$31` (27) and `$8B` (5) | `s3k-zone-bring-up` | `TestS3kLrzPlacementCensus`, three matrices, coverage-backlog rows, `raw-00-*` | Census red on a deliberately wrong expectation first (break it on purpose), then green at the baseline → inventory table. Also assert 331/281/52 rings and no ring at `(0,0)` → `loc_EB52` | Baseline numbers recorded in the evidence log with commit; trace frontiers re-measured |
| 1 | `$6E` all four subtypes (34/4/6 = 44) | `s3k-zone-events`, `s3k-parallax`, `s3k-animated-tiles` | New `LrzZoneRuntimeState` (+ rewind adapter, `currentRuntimeStateUsesThisEventInstance`), `Sonic3kLRZEvents`, `SwScrlLrz`; modify `Sonic3kLevelEventManager`, `Sonic3kScrollHandlerProvider` (act key), `Sonic3kPatternAnimator` (`$901` → `AniPLC_LRZ2`), hurt block H + registry `$6E` | `SwScrlLrzTest`: band heights from `LRZ1_BGDeformArray` (`$40,$20,$10×5,$100,$10×3,$20`) and fractions from `LRZ1_Deform`/`sub_57082`; provider test `$1600` ≠ `SwScrlHpz`, `$1601` = `SwScrlHpz`; lava block: fire shield immune, other shields and none hurt → `sub_1F58C` | Implemented + rewind on state/scroll; cold route enters `$900` at 320 and wide with correct BG; clip `01`, `05`-lava |
| 2 | none (no SST objects) | `s3k-animated-tiles`, `s3k-plc-system` | `Sonic3kPatternAnimator` LRZ channels, new `LrzRockSpriteRenderer` behind `Sonic3kZoneFeatureProvider` | Pattern test: channel 0/1 source offsets for three `Events_bg+$10/$12 − Camera_X_pos_BG_copy` phases → `loc_282D0`, `word_2834C`, `word_283D2`; rock window test → `loc_1CAF4`/`loc_1CB84` values in Verified ROM values | Renderer receives the DMA (not just CPU patterns); rocks drawn after bucket 0 and before bucket 1, test with a bucket-0 and a bucket-1 object overlapping a rock; rewind of window pointers; clip `02` |
| 3a control | `$15` (1), `$16` (1+1), `$1E` six subtypes (6) | `s3k-implement-object` | `LrzCorkscrewObjectInstance`, `LrzWallRideObjectInstance`, `LrzDashElevatorObjectInstance`; registry SKL branches | Capture/release positions and `object_control` values per routine → `Obj_LRZCorkscrew`, `Obj_LRZWallRide`, `Obj_LRZDashElevator` | + team capture/release, S1-donor row on `$1E`, rewind mid-capture, clips `03a` |
| 3b doors | `$19` (15+11), `$1A` (1), `$1C` (10+11), `$1D` `$A0,$C2` (2); verify `$33` (3+1) | `s3k-implement-object` | `LrzDoorObjectInstance`, `LrzBigDoorObjectInstance`, `LrzButtonHorizontalObjectInstance`, `LrzShootingTriggerObjectInstance`; registry (four are unregistered ids) | Door opens only for its `Level_trigger_array[subtype & $F]` writer; table of act 1 pairs from the inventory note → `Obj_LRZDoor`, `Obj_LRZButtonHorizontal`, `Obj_LRZShootingTrigger` | Trigger array cleared at level load **and** by `Clear_Switches` in `loc_56CAA` (seamless act change), and snapshotted; clips `03b` |
| 3c hazards | `$17` (11), `$18` five subtypes (15), `$1B` eleven subtypes (27), `$1F` (7), `$20` (11+14), `$21` ten subtypes (15), `$22` (6); verify `$05` `$40,$44,$50,$68` (89) | `s3k-implement-object` | One class per id, `Lrz…ObjectInstance`; children with probe constructors | Per class: subtype → timing/amplitude table rows → the owning `Obj_LRZ*` routine and its data tables | One family review; clips `03c…` |
| 3d crusher | `$9C` subtypes 0 and 2 (2) | `s3k-implement-object`, `s3k-zone-events` | `LrzRockCrusherObjectInstance` (+ timer child), `Sonic3kLRZEvents` screen-event chunk edits through `ZoneLayoutMutationPipeline` | Subtype 0 → negative branch bytes, subtype 2 → `$9C` branch, 180-frame shake, bridge spawns at the three ROM positions → `loc_90512`, `loc_9056E`, `LRZ1_ScreenEvent` | Camera bounds stored/restored (`Camera_stored_*`); rewind before/during/after the edit; clip `03d` |
| 4 | `$99` (20+9), `$9A` (32+34), `$9B` (22+9) = 126 | `s3k-implement-object`, `s3k-plc-system` | `FirewormBadnikInstance`, `IwamodokiBadnikInstance`, `ToxomisterBadnikInstance` in `objects/badniks/`; Toxomister's player slow-down as a player-state hook | Movement/attack cadence per routine → `Obj_Fireworm`, `Obj_Iwamodoki`, `Obj_Toxomister`; art present after `PLCKosM_LRZ` | Clip `04`; sidekick and S1-donor interaction row for the mist |
| 5 | none placed (`Obj_56EA0` dynamic) | `s3k-zone-events`, `s3k-parallax` | `Sonic3kLRZEvents` BG stages, `SwScrlLrz` locked mode, `LrzDomeLavaPlatformObjectInstance` | Enter/exit per corrected region row incl. boundary values (`$1AFF/$1B00`, `$22BF/$22C0`, `$79F/$7A0`, Y max exclusive) → `sub_56DCA`; P1 fire-shield immunity and P2 none → `loc_56F54` | Clip `05`; native probe on the switch frame |
| 6 | `$9D` (1) | `s3k-implement-boss`, `s3k-zone-events`, `s3k-palette-cycling` | `objects/bosses/LrzMiniboss*.java`, seamless handoff class beside `SozActTransitionHandoff` | Boss routine/hit table → `Obj_LRZMiniboss`; transition: every live object, both players, camera and bounds shift exactly `−$2C00` on one frame → `loc_56CAA` | Independent review; timeline isolation across the change; clips `06a/06b` |
| 7 | `$25` (3), `$29` eleven subtypes (52), `$2B` (12), `$2C` sixteen subtypes (40), `$2D` ten subtypes (52), `$32` (18), `$37` (9) = 186; verify act 2 rows of `$05/$F4` (21), `$0D` (4), `$0F` (25), `$16`, `$19`, `$1C`, `$20`, `$24` (10) | `s3k-implement-object`, `s3k-parallax` | One class per id; Death Egg BG sprite owned by `SwScrlLrz` act 2 | Subtype decodes as listed in Verified ROM values → `Obj_LRZFlameThrower`, `Obj_LRZOrbitingSpikeBall*`, `Obj_LRZSolidMovingPlatforms` (`off_258BC`, `byte_25826`) | One family review; clips `07a…07d` |
| 8 | `$AE` (1), `$B3/$2D` (1) | `s3k-zone-events`, `s3k-implement-object` | `Lrz2CutsceneKnucklesObjectInstance` (+ boulder), shared `S3kStartNewLevelObjectInstance`, Act 3 carry owner | `$AE`: deletes for `character_id 2`, triggers inside `word_63B94`, requests `$1600` at Y ≥ `$4C0` with the carry set → `loc_63C14`; `$B3`: range `word_86426`, `SaveGame` only for `Player_mode 3` in zone 9, target `$1601` from subtype `$2D` | Both exits cold-reached; `$1600` loads with no title card and carried rings/timer/shield; clips `08a/08b` |
| 9 | `$9E` (1), `$AD` subtypes 0,1,2,4 (7); verify boss-act `$01` (2), `$0F` (8), `$28` (8), `$34` (1), `$6E` (6), `$8B` (2) | `s3k-zone-events`, `s3k-parallax`, `s3k-animated-tiles`, `s3k-palette-cycling` | `Sonic3kLRZEvents` `$1600` branch, `SwScrlLrz3`, `Lrz3AutoscrollObjectInstance`, `Lrz3PlatformObjectInstance`, `AnimateTiles_LRZ3`, `AnPal_LRZ3` | Autoscroll stage thresholds and velocities → `loc_59E46` stage table; respawn branch X ≥ `$480` → `LRZ3_ScreenInit`; `AnPal_LRZ3` gate: `$80` written at `loc_79416`, `1` at `loc_79486`; negative = off, 0 = lava cycle only, positive = lava + `AnPal_PalLRZ3` | Crush-kill and star-post respawn rows; wide-viewport decision recorded; clips `09a-c` |
| 10 | none placed (`Obj_LRZEndBoss`, `Obj_59FC4`, capsule, `$2D` at `($FE8,$5E0)` dynamic) | `s3k-implement-boss`, `s3k-zone-events` | `objects/bosses/LrzEndBoss*.java`, `Lrz3LavaSurfaceObjectInstance`, VScroll columns in `SwScrlLrz3` | Hit count, routine order, defeat → capsule → `StartNewLevel` sequence → `Obj_LRZEndBoss`, `loc_79998`, `loc_79A30` | Independent review; cold arrival in `$1601`; HPZ `Hpz222` frontier re-measured; clips `10a/10b` |
| 11-12 | whole matrix | `gameplay-capture`, `bk2-input-authoring`, `trace-replay-bug-fixing`, `gameplay-highlights` | Route/compatibility/lifecycle tests, reel | — | Five claims filled per row; census baseline 0 / 0 / 0 |

### Rules for the implementer

| Rule | Check |
| --- | --- |
| Expectations come from a cited ROM label or an independent native capture, never from the Java being written or a fixture | Test header names the label; break each new comparison once on purpose |
| Never tune to a fixture, frame index, route or fitted constant; "1 second"-style invented durations are a defect | Grep new code for unexplained literals |
| Objects use injected `services()`, never `getInstance()`; layout edits only through `ZoneLayoutMutationPipeline` / `LevelMutationSurface` | `-Pguards` |
| No zone-name carve-outs in shared code: `GameRules`, providers, profiles, constructor parameters | Review every shared-file diff |
| No new public member on a `@ModApi` type without the pin/version update; grep both `@ModApi` and `@com.openggf.game.ModApi` first; keep helpers in non-API classes | Signature-pin hook |
| `GameLoop` (3072 effective lines) and `Engine.draw` (3 lines) are size-ratcheted: logic goes in managers | `-Pguards` before committing there |
| Every new object: recreation + captured state, children with a probe constructor; every new global state: a registered `RewindSnapshottable` adapter; level variables cleared at level load | `TestEveryObjectRewindRoundTrip`, `TestRewindHarnessCoverageRatchet`, a before/active/after rewind spot per slice |
| Name the clock each gate reads (`V_int_run_count`, `Level_frame_counter`, object timer); `FixBugs = 0` branch commented at each conditional | Review |
| `AllocateObject` vs `AllocateObjectAfterCurrent` and slot order are behaviour: copy the ROM's allocator | Native slot log where siblings draw RNG |
| Maven only through `python3 tools/testing/maven_queue.py -Dmse=off … -Ds3k.rom.path=<absolute>`; wrong paths skip silently, so **read the skip count** before reporting; `rm -rf target/test-tmp` on `UnsatisfiedLinkError` | Report tests run / skipped / failed |
| No tree edits during a category run; never `git stash`; never `--no-verify`; start git chains with `cd`; check `git log -1` after each commit | — |
| Every non-merge commit carries all seven trailers, mapped files and trailers agreeing | Hook |
| One demo clip per feature/fix, ≥ 30 frames lead-in and lead-out, moving output inspected at 320 and wide; a clip is not parity evidence | Evidence-log entry names the clip |
| Gaps go to `docs/status/s3k-known-bugs.md`; the discrepancies file is intentional-only | — |
| Stop and record, do not guess, when a ROM reading is unclear: add an open question with a kill condition | — |

### Verified ROM values (independent pass 2026-09-17, re-read against `sonic3k.asm`)

Write tests from these, citing the label. Line numbers are for disassembly `1a454a0e`.

| Topic | Exact values |
| --- | --- |
| Act 1 scroll (`LRZ1_Deform`, 115389) | BG Y = `((Camera_Y_copy − shake) asr 3) + shake` (word). X in 16.16: `b = X/8`, `s = b/4 = X/32`. `Camera_X_pos_BG_copy = b`, `HScroll_table+$004 = b`, `Events_bg+$10 = b − s`, `Events_bg+$12 = b − 2s`. `+$01A` down to `+$00C` = `b, b+s … b+7s`; `+$01C` up to `+$024` = `b+s, b+3s, b+5s, b+7s, b+9s`. Bands `LRZ1_BGDeformArray` = `$40,$20,$10×5,$100,$10×3,$20`; draw array `$B0,$100` |
| Act 2 scroll (`sub_57082`, 115739) | BG Y = `(Y/8 − Y/32) + shake` with `Y = Camera_Y_copy − shake` in 16.16 (= 3Y/32). Same `b`, `s`, `Events_bg+$10/$12`. `+$00E` down to `+$000` = `b … b+7s` (so `+$004 = b+5s`); `+$010` up to `+$018` = `b+s … b+9s`. Bands `LRZ2_BGDeformArray` = `$20,$20,$20,$10×4,$F0,$10×3,$20` |
| Death Egg BG sprite (end of `sub_57082`) | Only when `Events_bg+$06` holds the object address: `x = $678 − HScroll_table+$004`; kept when `x ≤ −$7E0` (signed), else `x = 0`; `y = $C0 − Camera_Y_pos_BG_copy`. A gate, not a clamp. How `x = 0` suppresses drawing and the `$1FF` mask in `Render_Sprites` are **not traced**: slice 7 owner traces `loc_5711E` before coding |
| Animated tiles (`loc_282D0`) | Ch0 phase = `(Events_bg+$12 − BG_copy − 1) mod $30` by `divu.w` on the zero-extended 16-bit difference: a negative difference wraps unsigned and `$10000 mod $30 ≠ 0`, so model the wrap, not a signed modulo. Ch1 phase = `(Events_bg+$10 − BG_copy) & $1F`; skipped for zone `$16`. `word_2834C` six size pairs, `word_283D2` four. Update only when the phase differs from `Anim_Counters+1` / `+3`. A direct or star-post `$901` load has cleared counters, so phase 0 skips the first upload: test it |
| Rock window | `loc_1CAF4`: front = `Camera_X − 8`, forced to 1 when `Camera_X ≤ 8`; back = front `+ $150`. `loc_1CB84`: drawn when `0 ≤ y − Camera_Y + 8 < 240` (unsigned compare against `d5`) |
| LRZ3 BG (`LRZ3_BackgroundEvent`) | Five stages `0,4,8,$C,$10`. `word_5A106` = `$310` then 18 × `$10` |
| LRZ3 respawn (`LRZ3_ScreenInit`, 119313) | P1 X ≥ `$480`: camera `($920,$2F0)`, `Special_events_routine = $14`, `Events_bg+$00 = $10`, `Events_bg+$02 = $2D`, `Events_routine_fg = $C`, `Pal_LRZBossFire` → `Target_palette_line_2` (`$60` bytes), player `($9C0,$36C)` |
| Autoscroll (`loc_59E46`) | Seven stages; thresholds X `$410`, Y ≤ `$330`, X `$650`, Y ≤ `$2F0`, X `$910`, Y ≥ `$320`, X `$BBF` with P1 X ≥ `$C50`; velocities `$20000`, `±$16A00`, `$1D900`/`$C400`. `sub_59F82`: left of `Camera_X + $10` pushed, killed if `Status_Push`; right cap `Camera_X + $120` (verifier's reading; owner rereads 119717-119818 for the stage/velocity pairing) |
| Miniboss (`Obj_LRZMiniboss`) | `off_7854C` 11 slots, 9 distinct handlers (`loc_785E4` ×3); `collision_property` 6; children 12 (`ChildObjDat_78D84` → `loc_7880A`) + 12 (`78D8A` → `loc_787FE`) at init, later 1, 1, 11 (`78D90`, `78D98`, `78D9E`). `word_78EAA` is **not** the fight palette: `loc_78AA8` starts it only once `End_of_level_flag` is set; the end boss reuses it at `loc_7A100` |
| End boss (`Obj_LRZEndBoss`) | `collision_property $E` (14 hits); `off_79812` six routines |
| Slice 7 decodes | `$29`: bit 7 selects the variant (frame 7 + `loc_43F12` vs frame 6 + `loc_43DDC`); both set `$32 = (subtype & $7F) × 4` and `$30 = 2*60`. `$2B/$2C`: bit 0 = large ball (`bclr` after read); the rest of the byte is added to `Level_frame_counter × 2` as the angle; placed low nibbles are all 0. `$2D`: `subtype & $F` indexes `off_258BC` (9 routines); `(subtype >> 2) & $1C` indexes `byte_25826`, rows 0 and 1 only |
| `$1D` | Sets bit 0 of `Level_trigger_array[subtype & $F]` in `sub_42EC0` only when the touching player has `anim == 2`, then becomes `Obj_Explosion`; high nibble × 4 = shot period; projectile via `AllocateObjectAfterCurrent` |
| `Obj_StartNewLevel` | Decode reads a **word** at `subtype`: SST `$2D` must be 0 for `$2D` → `$1601` |
| Title-card skip | `Act3_flag` also skips the `loc_62CC` Kos/Nem drain loop: art readiness on the `$1600` load needs its own check |
| Rock crusher | Subtype 0 clears `Screen_shake_flag` in `loc_90512`; the `loc_9056E` branch does not (it hands to `Child7_ChangeLevSize`) |

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
Knuckles). One combined `run_categories.py --base 035e48a58 --run` at the final delivery, after focused
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
- **Act 3 carry is one shared engine owner.** LRZ2 → `$1600` (`loc_63C14`) and DEZ2 → `$1700`
  (`loc_7F310`) run the identical sequence: `Act3_flag`, `Act3_ring_count`, `Act3_timer`,
  `Saved2_status_secondary`, then `StartNewLevel`; the load skips the title card (and its Kos/Nem drain
  loop) and the destination's screen-event stage 0 restores rings and timer. Whichever campaign lands
  first builds the owner (LRZ design table); the second reuses it and adds no parallel mechanism.
- **Clock-seeded RNG/aim** (`V_int_run_count`: Mecha Sonic, DEZ turrets as in DDZ) needs a declared
  seed for movie-route matching until the full cold chain supplies it; label such evidence seeded.
- **Knuckles trace testing is out of scope (user decision 2026-09-17).** One Knuckles replay class
  exists (`TestS3kKnucklesLbz2BigArmTraceReplay`); the `s3k-knuckles-complete-superemeralds` run has no
  segment classes. A campaign may add one where cheap, but owes no Knuckles replay frontier; Knuckles
  rows rest on authored routes and native probes from that movie.

## Open questions (each with its kill condition)

Resolved on 2026-09-17 from the disassembly (details in Findings):

| Question | Answer |
| --- | --- |
| Who writes the positive `Events_bg+$0C`? | The rock crusher, subtype ≠ 0, through the low byte (`st (Events_bg+$0D)`, `loc_9056E`) |
| Does `GameLoop` cover the LRZ2 → `$1600` carry? | No: comment only. New carry owner in the design table, shared with DEZ `$1700` |
| Region table row 1 (min > max)? | Transcription error: 5-word rows, corrected |
| Miniboss/end-boss counts, autoscroll stages, `$480` respawn, `AnPal_LRZ3` gate, slice 7 decodes | Verified independently: see Verified ROM values |
| Which exit gates on character? | Only `$AE` (deletes for `character_id 2`; Tails alone gets the cutscene). `$B3` has no gate; `SaveGame` needs `Player_mode 3` |
| Does `Sonic3kRingPlacement` spawn the `(0,0)` sentinel as a ring? | Yes, it did, in every S3K act. Fixed in `3418eba6e`; the ROM never makes record 0 live (`loc_EB52` counts from `+4`, and `loc_E8BE`/`loc_E904` clamp the window key to 1) |
| `TestS3kSonicTailsLrzSegmentTraceReplay` frame 208 still the first error? | Yes, unchanged at `3418eba6e`: 11909 errors, first error frame 208 `tails_y_speed` (expected `0x07BD`, actual `0x0000`) |

Still open:

| Question | Kill condition | Must resolve before |
| --- | --- | --- |
| Can Sonic/Tails pass X `$38B0` outside `$AE`'s Y window (`y − $240 … y`, i.e. `0 … $240`) and reach `$B3` at `($3FE0,$E0)`; can Knuckles's route miss `$B3`? | Cold routes for all three characters plus the act 2 collision map around X `$38A0-$3FF0`; if Sonic can reach `$B3`, record the ROM result (`$1601` without LRZ3), do not block it | Slice 8 done-condition |
| `$8B` sprite mask under SKL is `SozSpriteMaskObjectInstance`: any SOZ-only assumption? | Read the class against `Obj_SpriteMask`; test the three LRZ subtypes `$44 $84 $F1` | Slice 9 (boss act uses two) |
| Does `$1600` art at the `$0F` bridge tile match `Map_HPZCollapsingBridge` frames? | Moving capture of the `($60,$4D0)` bridge vs native | Slice 9 |
| Why does a direct `$901` load draw HUD font tiles in the upper background rows, when the layout is a clean four-chunk repeat and the plane is refilled from column 0? | Native `$901` reference capture of the same rows, or identifying the art the seamless entry's `loc_56BD2` queue supplies | Slice 6/7; recorded in `s3k-known-bugs.md` |
| Death Egg sprite draw path (`x = 0` suppression, `$1FF` wrap); autoscroll stage ↔ velocity pairing; which of `$29`'s `$30/$32` is the on and which the off timer | Slice owner traces `loc_5711E`, `loc_59E5E`-`loc_59F3C`, `loc_43DDC` before writing the test | Slices 7, 9, 7 |
| Do the miniboss's two post-defeat camera releases belong to act 2? `loc_78AE6` sets `Camera_min_X_pos = $940` once `Camera_X_pos` reaches `$940`, and `loc_78B08` sets `$2C0` at `$2C0` (sonic3k.asm:160523-160545). Both thresholds are far *behind* the act 1 arena, whose camera sits past `$2C00`, so in act 1 both would fire on the frame they are created and drag the bound backwards. The reading is that they survive the seamless change, after which the `−$2C00` rebase puts the arena near `$0` and the player walks forward through `$2C0` and then `$940` | A native capture of `Camera_min_X_pos` across the act change, or a `$901` entry probe reading the bound on the first act 2 frames. If the act 1 reading is right instead, the object must be gated on something this pass has not found | Slice 6 done-condition. **Not implemented for this reason**: wiring them before the act change exists would break the act 1 camera |
| Does `word_78EAA`'s rotation script need the S3K palette-rotation subsystem, or can `loc_78AA8`'s `Palette_rotation_data` copy plus `Palette_cycle_counter1 = $7FFF` be modelled as a plain per-frame palette write? | Decode the `palscriptptr` header/data pair at sonic3k.asm:160887 and check whether `Run_PalRotationScript` has an engine owner; if it does not, this is subsystem work, not boss work | Slice 6 done-condition, after the act change |

## Status

| Claim | State |
| --- | --- |
| Implemented | **Slices 0-5 complete, slice 6 started** (placeholder baseline **0 / 188 / 8** of 609 / 455 / 35): scroll for both playable acts, the shared runtime state and its rewind capture, the events shell, act-keyed scroll registration, `AniPLC_LRZ2`, the `$6E` lava blocks, both `loc_282D0` animated-tile channels with their `Anim_Counters` seed, the `Draw_LRZ_Special_Rock_Sprites` renderer, every slice 3 class (`$15` corkscrew, `$16` wall ride, `$17` sinking rock, `$18` falling spike, `$19` door, `$1A` big door, `$1B` fireball launcher, `$1C` horizontal button, `$1D` shooting trigger, `$1E` dash elevator, `$1F` lava fall, `$20` swinging spike ball, `$21` smashing spike platform, `$22` spike ball, `$9C` rock crusher with its timer child, eight hit pieces and the `LRZ1_ScreenEvent` chunk edits), and **all three badniks**: `$9A` Iwamodoki, `$9B` Toxomister with its cloud and seven puffs, and `$99` Fireworm as its four ROM objects (spawner, DPLC head, four staggered segments, a flame on each). Slice 5's state half landed: `LrzDomeRegions` (`sub_56DCA` + `word_56F88`), `sub_56DAC`'s locked-background arithmetic, and `Obj_56EA0` the dome lava surface with its `_unkEE9C` phase and the P1/P2 fire-shield asymmetry. **Slice 5 is now complete**: the `Events_routine_bg` 0/4/8 stage machine (`LrzBackgroundStageMachine`), `SwScrlLrz`'s locked and pinned modes, the `Draw_delayed_rowcount $F` bottom-up refresh, and the Knuckles `$F6` background chunk. **Slice 6 part one**: `LrzMinibossInstance` (SKL `$9D`) with the whole `off_7854C` routine table, the `$34` continuation chain, `sub_7867C`'s re-aim, `Swing_Setup1`/`Swing_UpAndDown`, and the three `loc_7880A` child shapes plus the hand's projectile -- registered, art-wired and unit-tested, which takes the act 1 census to **0**. **The seven-item independent review is applied** (`d48420e24`): the arms unroll on `sub_78BD6`'s `$2E` stagger, the hand rides `parent3` through `MoveSprite_AtAngleLookup`, `Animate_RawMultiDelay` starts at index 2, `loc_788F4` stores the stepped angle unconditionally, link offsets use the ROM sine table and `asr.l #4` in 16.16, the projectile culls on `Sprite_CheckDeleteTouchXY` literally, and `SolidObjectFull`/`Displace_PlayerOffObject` are real. With them the **hit path**: `collision_flags` is the ROM byte and `sub_78C14` owns the `$20`-frame flash with the `FixBugs = 0` `word_78CB2` window. A restore after `ROUTINE_INIT` rebuilds any missing child. **The review's remaining items are closed too**: the hand's render gating at both routine-switch frames plus its `$20` blink, its own hit path (`sub_78CF4`, the `loc_78A28` hit ring, `loc_78D2C`), the touch pass's `$1C` attacker marker and `status` bit 7, the `parent3` anchor reading (and standing still rather than substituting the drill when the anchor is gone), `sub_78B46`'s per-child retirement -- so killing a hand now peels its arm away from the hand end first -- and nineteen corrected `sonic3k.asm` citations. Re-reading for those found a seventh defect the review did not: `FLASH_PALETTE_LINE` was 2, but the ROM's palette-line names are one-based (`Normal_palette_line_2 = Normal_palette+$20`), so every hit flash was on the wrong line. **The fight can now end**: `loc_78C60` -> `Wait_FadeToLevelMusic` (no `$2E` reseed) -> `loc_787E0`'s eleven `ChildObjDat_78D9E` pieces on their own `Obj_VelocityIndex` arcs and `Obj_FlickerMove` blink -> `S3kBossDefeatSignpostFlow`, the engine's `Obj_EndSignControl`. Twenty-two tests, most watching a value across frames; nine deliberate breaks across three runs produced nine failures with the right diagnosis and no masking. Still owed: the two post-defeat camera releases and `word_78EAA`'s rotation (both open questions -- their thresholds are act-2 coordinates), results consuming `Events_fg_5` for LRZ, and the seamless `$900` -> `$901` change. **Slice 6 part two**: `Obj_LRZMiniboss`'s own first dispatch -- `Check_CameraInRange` on `word_784E0`, `sub_85D6A` and the `loc_85CA4` ramp on the shared `S3kSharedBossCameraGate` -- so the fight starts only once the camera has locked on `word_784E8` (`$710,$710,$2C00,$2C00`), the measured lock being `($2C00,$710)`, native's pair exactly. It now has **clip `29`** (arrival and arms unrolling) from a declared `($2C00,$600)` setup; still **no route rewind spot, no native frame comparison and no clip of a hit, a hand kill or the defeat**. Absent: the `LRZ1_BackgroundEvent` seamless `$901` handover (stage `$C`), the two remaining bosses, cutscenes, `StartNewLevel` |
| Cold-reachable | Act 1 started from the level start with no teleport: the `$05` push-break rock, the `$1C` button and the `$19` door are cold-reached and the door is opened on the route (clip `13`). On the fixture's own recorded native input the engine matches Player 1 `(x, y)` **exactly for native rows 0-2322** (0-856 at the fourth handover, 0-636 before the frame-637 fix). **The reach is not a progress measure** and will keep falling as the zone fills in: the input is the fixture's own, which native survives, so the reach only measures how long an off-phase replay lives among real hazards. Quote the exact-match row and the first divergence. Re-measured at `13a7c8fe8`, row 857 was **already closed** by the Fireworm landing (the fourth handover's number predates `cad4a2e07`). The real divergence was row 863, a hurt the engine took and native did not: a killed worm left its flames alive. Fixed, and the route now matches **exactly for native rows 0-2322**, with the new first divergence at row 2323, unattributed. Evidence in the [trace frontier log](../../status/trace-frontier-log.md). The hand-authored `lrz1-cold-route-v7` frontier of x 1909 is untouched. The **act 1 miniboss arena is reachable from a declared positioned setup** -- `--x 0x2C00 --y 0x600` lands on the arena floor at `y $7AD`, the recorded run's own standing height -- and a walk right locks the camera and starts the fight; this is a **seeded** reach, not a cold one. Nothing cold-reached in act 2 or the boss act |
| Rewind-verified | Before/active/after spots with forward replay for the `$19` door, the `$1C` button latch, the `$15` corkscrew ride, the `$17` sinking rock, the `$16` wall ride, the `$18` falling spike, the `$21` smashing spike platform, `$1B`, `$1F`, `$20` and the `$1E` dash elevator's mid-ride (`TestLrzHazardRewindSpots`, `TestLrzDashElevatorRewindSpot`), each broken on purpose once. Plus `LrzZoneRuntimeState` capture/restore round trips and the animator's counter blob. New in `TestS3kLrzRouteRewindSpots`, on real terrain at fixture route positions: `$18` mid-fall AND landed (whole composite), `$1A` opening (whole composite), `$9C` rumbling and `$9A` fuse-lit (both now whole-composite again, with the dropped-children restore gap closed), and a child-count spot for the crusher's four `S3kCameraGradualObjectInstance` children and the Fireworm's eight segments. `TestS3kLrzDomeBackgroundHeadless` adds a whole-composite spot inside a locked dome region. Still owed: `$1D`/`sub_42EC0` on a route, and cold-route (rather than route-position) spots |
| Native behaviour matched | Not started (Sonic + Tails `lrz` frontier frame 208, inherited, re-measured `3418eba6e`) |
| Visually matched | Act 1 parallax, the act-1 lava block, the act-1 rock sprites (320 and 400), the act-1 animated background lava, and clips `08`-`28` covering every slice 3a/3b/3c/3d class plus all three badniks. Clips `21`-`28` are after-only: the "before" is a placeholder that draws nothing. Clip `27` is the Fireworm swimming with its tail; clip `28` is the Toxomister mist reaching Sonic and pinning him -- his ground speed is held at zero for ~100 frames while Left is held, then the shake frees him. Act 2's background is still blocked by the direct-`$901` art gap, which slice 2 proved is not the animated-tile DMA. **Slice 5 has no clip and cannot have one**: Lava Reef act 1's *foreground* plane is opaque across the whole dome, so no background pixel can reach the screen there whatever the lock computes -- measured from ROM data with `PlaneOpacityProbe` (0 of 71 680 see-through pixels at each of six dome viewports), pinned by `TestS3kLrzForegroundOpacity` with an Angel Island control, and recorded in [s3k-known-bugs](../../status/s3k-known-bugs.md), whose removal condition is now a native plane-B-toggled capture |

Out of scope, recorded as dependencies: SSZ after HPZ (SSZ campaign); Knuckles replay classes and
fixtures' harness work beyond recording frontiers; the `lrz_completerun` hardware-timing compile
blocker unless it blocks a named slice.

## Evidence log

### 2026-09-17 - Slice 0: baseline and identity (commit `3418eba6e`)

Worktree `.worktrees/ai-lrz-bring-up`, branch `feature/ai-lrz-bring-up`, base `035e48a58`.
All Maven through `maven_queue.py -Dmse=off` with
`-Ds3k.rom.path=<worktree>/s3k.gen`
(symlink resolves to SHA-1 `cfbf98c3...d2761d6`; every run below reports 0 skips).

**Census.** `TestS3kLrzPlacementCensus` decodes `LRZ1/2/3_Sprites` and `LRZ1/2/3_Rings`
straight from the ROM pointer tables and builds every placed record through the production
`Sonic3kObjectRegistry`. Baseline pinned: 609 / 455 / 35 placements, **239 / 281 / 14**
placeholders by exact `(id, subtype)` map, 331 / 281 / 52 live rings.
Broken on purpose once (`$6E`/`$31` expected 8 instead of 7): the run reported exactly that
one row, `expected: <... $6E/$31=8 ...> but was: <... $6E/$31=7 ...>`, and nothing else.
Reverted; the baseline is green.

**Two defects found by the census.**

1. *Ring sentinel (engine).* Every S3K ring list but the Pachinko one opens with a `(0,0)`
   record. The ROM never makes it live: `loc_EB52` counts `Perfect_rings_left` from
   `Ring_start_addr_ROM + 4`, and both window searches (`loc_E8BE` X-ordered, `loc_E904`
   Pachinko Y-ordered) build their key as `camera - 8` clamped up to 1 (`moveq #1,d4`) and
   then step past every record below it. `Sonic3kRingPlacement` was spawning it as a real
   ring in the top-left corner of **every S3K act**. Fixed in `3418eba6e`; surviving rings
   keep their record index as `placementId`, which is the index the ROM's `Ring_status_table`
   walks in lockstep. Re-checked `TestRingManager`, `TestS3kAiz2BigRingCollision`,
   `TestS3kAiz2BigRingFormation`, `TestS3kCnzLateSSEntryRingPlacement`, `TestRingSparkleDelay`,
   `TestSonic3kRingPlacement` and the four mandatory S3K classes: 59 tests, 0 failures, 0 skips.
2. *Inventory row (document).* The placement inventory's main table was missing `$1D`/`$A0`
   (1 act-1 placement), so it summed to 250 rows / 608 act-1 placements against its own stated
   251 / 609. Row restored; the table now reconciles exactly, and the unregistered class is
   20 rows / 27 placements / 13 in act 1 as the summary already claimed.

**Trace identity.** Measured by walking every `zone_act_state` aux row of the LRZ-adjacent
segments in all three runs and recorded once in
[trace-frontier-log.md](../../status/trace-frontier-log.md). Confirms the plan's reading for
Sonic + Tails (`lrz` act 2 at 25557, apparent at 26272, `$1600` at 38817; `hpz22` autoscroll to
1981; `hpz22_2` `$1601` at 7558, SSZ at 14685) and Knuckles (`lrz_2` act 2 at 5985, `lrz_3`
`$1601` at 6870). **Two plan corrections:** Tails `hpz22` is 12956 rows covering LRZ3 *and* the
Hidden Palace arrival (8619 is the `$1601` entry row, not the segment length), and Tails
`hpz22_2` starts already inside `$1601` and leaves for SSZ at 4439 - it is not a second LRZ3
segment. Also: each segment's `metadata.json` `zone_id` is correct (22 = `$16`); only the
directory names are one zone off.

**Frontier.** `-Ptrace-segments -Dtest=TestS3kSonicTailsLrzSegmentTraceReplay` at `3418eba6e`:
red, 11909 errors, 0 warnings, first error frame 208 `tails_y_speed` (expected `0x07BD`, actual
`0x0000`). Unchanged from the 2026-08-15 record. No fixture changed or consumed.

**Matrices.** [Act 1](../validation/levels/s3k-lrz-act1.md),
[Act 2](../validation/levels/s3k-lrz-act2.md), [boss act](../validation/levels/s3k-lrz-boss.md),
with the three coverage-backlog rows repointed. The five claims are tracked separately in each.

**Media.** `~/Videos/OGGF/lrz-bring-up/` laid out as HPZ's (`inputs/`, `native/`, `reel/`,
`make_clip.sh`, `make_clips.sh`, `side_by_side.sh` copied from the DDZ and HPZ roots).
Baseline captures at 320, Sonic + Tails, title cards on, 360 frames each
(`--input target/capture/lrz-idle-right.txt`, 120 idle + 180 right + 60 idle):
`raw-00-lrz1-before/`, `raw-00-lrz2-before/`, `raw-00-lrz3-before/`. Inspected frames 40/120/350
of act 1 (falling intro, "LAVA REEF ZONE ACT 1" card, rocks and both players on the ledge), frame
300 of act 2 (crystal wall, tube floor) and frame 300 of the boss act (crystal arena over lava).
All three render; none is blank. These are the "before" state with no scroll handler, no events
and no LRZ objects.

**Open issues from this slice.** None blocking. The `lrz_completerun` hardware-timing compile
blocker was not re-measured (no named slice depends on it yet).

### 2026-09-17 - Slice 1: runtime state, scroll, registration fixes (commit `bbd156d37` + follow-up)

Worktree `.worktrees/ai-lrz-bring-up`. All Maven through `maven_queue.py -Dmse=off` with
`-Ds3k.rom.path=<worktree>/s3k.gen`; every run below reports 0 skips.

**Delivered.** `SwScrlLrz` (both acts), `LrzZoneRuntimeState` with its rewind capture,
`Sonic3kLRZEvents` registered in `Sonic3kLevelEventManager` (including
`currentRuntimeStateUsesThisEventInstance`, keyed so zone `$16` act 0 takes the Lava Reef state and
act 1 Hidden Palace's), an act-keyed `Sonic3kScrollHandlerProvider` for zones `$16` and `$17`,
`AniPLC_LRZ2` (`$28A84`) for `$901`, and `$6E` `Obj_InvisibleLavaBlock` on the shared H hurt block
with the `sub_1F58C` reaction mask.

**ROM re-read for this slice** (independently of the plan's verified-values table, which it
confirms): `LRZ1_Deform` 115389-115435, `sub_57082` 115739-115820, the deform arrays at 115643 and
115845, `LRZ1_ScreenEvent` 115199, `LRZ2_ScreenEvent` 115670, `Offs_AniFunc` 53842-53881 (the table
is `(AnimateTiles, AniPLC)` pairs; counting pairs from its head, entry 18 is LRZ1 and 19 LRZ2),
`AniPLC_LRZ1`/`AniPLC_LRZ2` 56007/56022, `Obj_InvisibleLavaBlock` 43270, `sub_1F58C` 43427,
`sub_24280` 49205. `shield_reaction` and `status_secondary` are the same SST byte (`$2B`), which is
why the object's bit 4 answers `Status_FireShield`.

**Tests.** `SwScrlLrzTest` (10) asserts both scatter runs word by word, both vertical ratios, and
the 224 background words as hand-walked band runs for camera `($800,$320)`, plus the provider act
keys. `TestS3kLrzScrollRegistrationHeadless` (7) checks what a real load resolves - both playable
acts get `SwScrlLrz` and an `LrzZoneRuntimeState`, `$1600` gets neither `SwScrlHpz` nor a Hidden
Palace state, `$1601` keeps both - plus 320 against 640 and a capture/restore round trip.
`TestS3kLrzPatternAnimation` (2) pins the two AniPLC lists by tiles-per-frame and destination tile.
`TestSonic3kInvisibleHurtBlockHObjectInstance` gains four lava-block cases, including that the
immunity runs before the ring spawn and that the plain `$6A` block ignores a fire shield.
Batch at `bbd156d37`: 1263 tests, 0 failures, 0 skips (the four mandatory S3K classes, the LRZ and
HPZ suites, `TestEveryObjectRewindRoundTrip`, `TestRewindHarnessCoverageRatchet`). `-Pguards`: 669
tests, 0 failures, after updating `Sonic3kObjectProfile` for `$6E` and making `shieldReactionBits`
non-final for the rewind coverage guard.

**Census ratchet.** 239 / 281 / 14 placeholders to **205 / 277 / 8**; the census went red on exactly
the `$6E` rows first.

**Rejected: widening the background plane period.** The act-2 capture showed HUD font tiles in the
background, and the first hypothesis was the Hidden Palace fix - `getBgPeriodWidth()` widened to the
rightmost visible column. It was implemented, measured and removed: it changed **zero** pixels, and
a dump of both background layouts killed the premise. Act 1 is `E9 E8 E9 E8 ...` and act 2
`D5 D6 D7 D8 D5 D6 D7 D8 ...` - both repeat every four 128 px chunks, so 512 px already is the
period, and act 2 refills its plane with `moveq #0,d1` (always layout column 0). The real cause is
art readiness on a direct `$901` load, recorded in
[s3k-known-bugs.md](../../status/s3k-known-bugs.md).

**Clips** (`~/Videos/OGGF/lrz-bring-up/`, 3x nearest-neighbour, 60 fps):

| Clip | Shows | Raw |
| --- | --- | --- |
| `00a/00b/00c-lrz{1,2,3}-baseline-before-work.mp4` | The three acts as they were at `035e48a58` | `raw-00-lrz{1,2,3}-before` |
| `02-lrz1-parallax-before-after.mp4` | Act 1 running right at `($2385,$160)`, 420 frames, generic fallback left and `LRZ1_Deform` right; the cave-ceiling background moves at a visibly different rate, no seams or artefacts | `raw-02-lrz1-parallax-{before,after}` |
| `05-lrz1-lava-block-before-after.mp4` | Standing on the act-1 lava slab at `($F40,$3E3)`; left the placeholder leaves the player unharmed, right `Obj_InvisibleLavaBlock` kills him and the act reloads | `raw-03-lrz1-lavablock-{before,after}` |

Each "before" build disabled only the demonstrated registration in an uncommitted edit, reverted and
recompiled immediately (`git status` clean, verified after each). Frames were extracted and looked
at, not just encoded: act-1 side-by-side frame 260, the lava side-by-side frame 70, and frames
40/120/350 of each baseline.

**Not delivered, with reasons.**
- *Act-2 parallax clip.* The act-2 background is only visible at two of eight sampled positions and
  both show the missing-art defect above, so there is no honest "after" frame to show yet. The act-2
  scroll is numerically asserted in `SwScrlLrzTest` and the clip belongs to slice 6/7, after the
  seamless entry supplies the art.
- *Fire-shield lava clip.* Getting a fire shield into a capture needs a route from a `$05` monitor to
  a `$6E` placement; the three act-1 fire monitors are each walled off from the nearest lava block on
  a teleport-and-walk input (verified at `($B98,$472)` and `($1B0F,$AB0)`; the second does break the
  monitor and the shield renders). Deferred to slice 3, which owns act-1 traversal. The immunity
  itself is asserted over all five shield states in the unit test.

**Open issue raised.** Lava Reef act 2 background art on a direct `$901` load (known-bugs entry).

### 2026-09-17 - Slice 2: animated tiles and rock sprites (commits `1ef1256ca`, `fbbb793f7`)

Worktree `.worktrees/ai-lrz-bring-up`. All Maven through `maven_queue.py -Dmse=off` with
`-Ds3k.rom.path=<worktree>/s3k.gen`; every run below reports 0 skips.

**Delivered.** `loc_282D0`'s two custom background channels for both playable acts, installed in
`Sonic3kPatternAnimator` ahead of the AniPLC scripts, with the `Anim_Counters+1/+3` seed and both
counters in the animator's rewind blob; and `LrzRockSpriteRenderer`, a ROM-backed renderer for
`Draw_LRZ_Special_Rock_Sprites` / `sub_1CB68` spliced into the end of priority level 0 through a new
engine-internal `PriorityBucketSpriteSource`, with the two placement pointers in
`LrzZoneRuntimeState`.

**ROM re-read for this slice** (independently of the Verified ROM values table, which it confirms
in full): `AnimateTiles_LRZ1/2/3` 55045-55054, `loc_282D0` 55055-55095, `word_2834C` 55107-55119,
`loc_2833C` 55096-55099, `loc_28364` 55121-55167, `word_283D2` 55177-55184, `Animate_Tiles`
53825-53834, `Animate_Init` 56411-56414 and 56458-56461, `Draw_LRZ_Special_Rock_Sprites`
39556-39647, `sub_1CB68` 39656-39694, `Render_Sprites` 36318-36324 and
`Render_Sprites_NextLevel` 36386-36390, `LevelLoop` 7899-7903. Addresses from `sonic3k.lst`:
`ArtUnc_AniLRZ__BG` `$C0300` ($2400 bytes, eight $480 frames), `ArtUnc_AniLRZ__BG2` `$C2700` ($C00,
eight $180 frames), `LRZ1_Rock_Placement` `$CAD00`, `LRZ2_Rock_Placement` `$CB81A`,
`LRZ_Rock_SpriteData` `$1CBBE` (240 bytes, 30 entries).

Four details worth carrying forward:

1. Each placement list is a leading `dc.w 0,0,0` record, then the binary include (472 records in
   act 1, 10 in act 2), then a four-byte `$FFFF,$FFFF` terminator - which is what stops both scans,
   since its X compares above every camera key. The leading record is never drawn: the front key is
   at least 1 and the sentinel's X is 0.
2. `sub_1CB68` reads `Camera_X_pos_copy`/`Camera_Y_pos_copy` (`a3` in `Render_Sprites`), while the
   window scan reads `Camera_X_pos`. The engine keeps that split.
3. Priority level 0 is the **front** of the sprite table, and the players sit in a later level, so
   the rocks are drawn over Sonic and Tails. That is the ROM's composition, not a bug, and it is
   the most visible thing in clip `06`.
4. `loc_2833C` skips channel 1 for `Current_zone` `$16`, i.e. the boss act. Zone 9 always runs
   both. `AnimateTiles_LRZ3`'s own destinations (`$170`/`$194`) are slice 9 and are not registered.

**Tests.** `TestS3kLrzPatternAnimation` grows to 11: both channels compared pixel by pixel against
the ROM art bytes at the ROM-derived split offsets for **all** 48 channel-0 and 32 channel-1 phases,
the unsigned wrap (`$FFFF mod $30 = 15`, not the 47 a signed modulo gives), the `-1`/cleared counter
seeds per act, the channel order before the scripts, the skipped phase-0 first upload on `$901`,
and - added after the first capture showed nothing - a **production-pass** test for both acts that
drives the real scroll handler and `animator.update()` and requires both destinations to change.
`TestLrzRockSpriteRenderer` (7) covers the list identity against the ROM bytes, the attribute-table
decode, the window keys including the `Camera_X <= 8` forcing, the incremental walk agreeing with a
full scan at every step in both directions, the capture/restore round trip of the two pointers, and
the visible set at 320x224 against an independently computed one (with the widened back key at 424).

Broken on purpose twice, both reverted immediately: swapping the last `word_2834C` pair turned
`channel0UploadsTheRotatedFrameForEveryPhase` red at `phase=40 pixel=392`, and narrowing the rock
window to `$140` turned `routineZeroScanMatchesTheWindowKeys` red at `back at 0 ==> expected: <11>
but was: <9>`.

Batch at `fbbb793f7` plus the test additions: 1400 tests, 0 failures, 0 skips (the four mandatory
S3K classes, the LRZ, HPZ and SOZ pattern suites, `TestEveryObjectRewindRoundTrip`,
`TestRewindHarnessCoverageRatchet`). `-Pguards`: 669 tests, 0 failures. The first guards run was red
on one row - the audio architecture guard reads `sink.accept(` as a speaker-packet hand-off - so the
rock visitor interface was renamed to `RockRecordVisitor.visitRock`.

**Census.** Unchanged at 205 / 277 / 8: slice 2 adds no placed object classes.

**Two findings that change later slices.**

- *Act 1's background lava was HUD font tiles.* With the channels disabled, a direct `$900` load
  draws the HUD font and digit art in the background rows that use VRAM `$320-$34F`; the channels
  replace it with the ROM lava art. This is the act-1 twin of the recorded act-2 background defect
  and it is now closed for act 1.
- *...but it is not the act-2 defect.* At the act-2 known bug's own reproduction
  `(0x2438,0x0629)`, a 240-frame capture with the LRZ channels disabled and one with them enabled
  are **byte-identical in every frame**, while the production-pass test proves both channels do
  animate in act 2. The animated-tile DMA is therefore ruled out as that bug's cause; the
  known-bugs entry records the kill.

**Where the animated tiles actually are.** A temporary probe over the decoded level (deleted after
use) found 19 chunks per act referencing `$320-$34F`, and they appear only in the background layer:
act 1 in the background rows at `y = $100-$17F` (visible around camera Y `$860`), act 2 at
`y = $80-$FF`. Three earlier sample positions showed no difference at all because those rows were
off screen - a reminder that "the capture looks the same" is a fact about the capture.

**Clips** (`~/Videos/OGGF/lrz-bring-up/`, 3x nearest-neighbour, 60 fps, 360 frames each, before on
the left):

| Clip | Shows | Raw |
| --- | --- | --- |
| `06-lrz1-rock-sprites-before-after.mp4` | Act 1 at `($1560,$520)`, camera `($14C5,$539)` running right through the densest rock screen; right, eight to thirteen `Draw_LRZ_Special_Rock_Sprites` rocks fill the gaps between terrain chunks and pass in front of Sonic | `raw-06-lrz1-rocks-before`, `raw-06-lrz1-slice2-after` |
| `07-lrz1-animated-lava-before-after.mp4` | Act 1 at `($1800,$8C0)`, camera Y `$86C`, where the background uses the animated rows; left the HUD font tiles, right the flowing lava art | `raw-07-lrz1-animated-tiles-{before,after}` |

Each "before" build disabled only the demonstrated registration in an uncommitted edit, reverted and
recompiled immediately (`git status` clean, verified after each). Frames were extracted and looked
at, not just encoded: the rock side-by-side at frame 209 (the largest diff, 455 pixels) and the
animated-tile side-by-side at frame 150 (up to 8876 pixels over 358 of 360 frames).

**Width.** A 400-wide capture of the rock screen (`raw-06-lrz1-rocks-after-400`, frame 209 inspected)
shows rocks to the right edge with no cut-off. `GameplayCaptureTool` rejects `--width 640`; the
supported set is the named aspect ratios, so the wide row used 400. The widened back key itself is
asserted in the unit test, not on video.

**Not delivered, with reasons.**
- *Route rewind spot.* Slice 2 adds no route; the rock window's capture/restore and the animator's
  counter blob are covered by unit round trips. A before/active/after spot belongs to the first
  slice that has a cold route (slice 3).
- *Native comparison.* No LRZ native probe was run for this slice; the oracle is the ROM art bytes
  and the routine constants, which is stronger for a DMA than a screenshot would be.
- *Donor row.* Both features are presentation and donor-independent; no S1/S2 donor capture was made.

**Plan corrections.** None needed in the Verified ROM values for slice 2 - the channel-0 unsigned
wrap, the channel-1 mask and zone-`$16` skip, the `-1` seed for `$900`/`$1600` only, the rock window
keys and the vertical test were all confirmed as written. The demo numbering in the plan is already
superseded: slice 1 delivered the parallax clip as `02`, so slice 2 used `06` and `07`.

### 2026-09-17 - Slice 3a (partial): the dash elevator (commit `1e01edaa0`)

Worktree `.worktrees/ai-lrz-bring-up`. All Maven through `maven_queue.py -Dmse=off` with
`-Ds3k.rom.path=<worktree>/s3k.gen`; every run below reports 0 skips.

**Delivered.** `LrzDashElevatorObjectInstance` for `$1E` (six act-1 placements), registered under
`S3kZoneSet.SKL` + `ZONE_LRZ` on the id the S3KL set spends on `Obj_LBZSpinLauncher`, with its
`Map_LRZDashElevator` level art (`$43096` over `ArtTile_LRZMisc`, palette 0) and a
`Sonic3kObjectProfile` `LRZ_ONLY_IDS` entry.

**ROM read for this object**: `Obj_LRZDashElevator` 88381-88467, `sub_4301C` 88469-88494,
`Map_LRZDashElevator` `$43096` (sonic3k.lst). Four things worth carrying to the rest of slice 3a:

1. `$30(a0)` and `$34(a0)` are longwords written as words by Init, so they are 16.16 accumulators
   whose high word is the pixel offset. The per-frame delta is a **word** accumulator swapped into
   the high half and shifted right by three - eight units of push is one pixel a frame.
2. The latch is `cmpi.b #9,anim(a1)`, the spindash animation. A character with no spindash can
   never start a ride, which is the plan's S1-donor row: it needs no donor-specific code.
3. `add.b spin_dash_counter(a1),d0` after `moveq #8,d0` is a byte add into a cleared register, so
   the push is `(8 + counter) & $FF`.
4. `addi.w`/`subi.w #$40` followed by `bcc` is what clamps `ground_vel` at zero: the carry out of
   the 16-bit operation, not a sign test.

**Tests.** `TestLrzDashElevatorObjectInstance` (7): the Init decode for all six placed subtypes plus
both bit-7 and flip combinations, the `ground_vel` drain at every clamp edge, that only `anim 9`
starts a ride, the one-pixel-per-eight-units push with a charged counter, both clamps, the release
paths, and the `SolidObjectFull` arguments. Broken on purpose once: `RIDER_BASE_PUSH` 8 to 4 turned
`aLatchedRiderDrivesThePlatformOnePixelPerEightUnitsOfPush` red at `expected: <65536> but was:
<32768>`; reverted.

The census went red on exactly the six `$1E` rows before the baseline was ratcheted, and act 1's
placeholder total moved 205 to **199**. Batch: 1185 tests, 0 failures, 0 skips (census, the object
test, `TestEveryObjectRewindRoundTrip`, `TestRewindHarnessCoverageRatchet` and the four mandatory
S3K classes). `-Pguards`: 669 tests, 0 failures, after adding the profile entry and making
`maxPosition`/`baseY` non-final for the rewind coverage guard.

**Independent confirmation.** A capture of the `($8A0,$50C)` placement (subtype `$B6`, unflipped)
with a spindash charged facing left descends from y `$510` to `$6A0`: exactly 400 pixels, which is
its `$1B0` range less the `$20` the high subtype bit starts it at. That number was not used to build
the class.

**Clip** `08-lrz1-dash-elevator-before-after.mp4` (`raw-08-lrz1-dash-elevator-{before,after}`, 351
frames, before on the left): left the player falls straight through the placeholder to the floor
below, right he lands on the elevator, charges a spindash and rides it down the shaft. The "before"
build disabled only this registration in an uncommitted edit, reverted and recompiled immediately.
Input preserved as `target/capture/lrz-dash-elevator.txt`
(`40 -; 3 L; 34 -; 8 D; 1 D+C; 20 D; 1 D+C; 20 D; 1 D+C; 200 D; 30 -`); the first attempt gave the
player left-speed before Down and produced a roll rather than a spindash, so it has to be
stationary first.

**Clip 10** `10-lrz1-button-opens-door-before-after.mp4` (`raw-12-lrz1-drop-on-button-{before,after}`,
260 frames, before on the left): Sonic is dropped at `($DF0,$710)` and lands on the shared
`$33 Obj_Button` subtype `$03` at `($DF0,$736)` on frame 58, which writes trigger index 3; the
`$19` door subtype `$03` at `($E10,$719)` then rises out of the way over its 64 frames. On the left
the same door is still a pink placeholder box. 259 of the 260 frames differ, checked before
publishing. The "before" build disabled only the four slice 3b registrations in an uncommitted
edit, reverted and recompiled immediately. Input preserved as
`target/capture/lrz-drop-on-button.txt` (260 neutral frames: the drop and the door are the whole
demo).

**Clip 11** `11-lrz1-big-door-before-after.mp4` (`raw-13-lrz1-big-door-{before,after}`, frames
0-160 of 260, before on the left): Sonic is dropped at `($AF4,$28A)`, inside the big door's
proximity box at `($A8C,$208)` from the first frame (`dx` 104 >= `$50`, `dy` 130 inside
`[$40,$C0)`), and the 96x128 slab grinds down out of the ceiling while `Screen_shake_flag` holds
the camera shaking - which is why the whole frame differs, not just the door's own box. 122 of the
161 frames differ; the last difference is frame 123, so the cut carries 37 frames of quiet
lead-out. On the left the door is not there at all.

**Clip 12** `12-lrz1-shooting-trigger-door-before-after.mp4`
(`raw-14-lrz1-shooting-trigger-{before,after}`, 260 frames): Sonic is dropped at `($8FC,$496)` and
runs right. On the right the `$19` door subtype `$00` at `($920,$4DE)` stops him dead at x 2309 -
exactly its left solid edge, `$2336 - $1B` - while the `$1D` trigger above at `($94B,$4A7)` keeps
firing its diagonal shots. On the left, with the four registrations disabled, there is no door and
he runs straight past to x 2493. 259 of the 260 frames differ. This is the clearest proof that the
doors are load-bearing terrain rather than decoration, and the shots are the first end-to-end sight
of the `$1D` gun half running on the production path (frames 150-174 cropped around
`($94B,$4A7)` show one shot travelling down and to the right, two pixels per frame on each axis).
**It does not exercise `sub_42EC0`**: this door stops Sonic at its left edge, 70 pixels short of
the trigger, so nothing rolls into it and the `Touch_Special` -> `collision_property` ->
explosion path is still only covered by the unit test. A capture that reaches the trigger has to
come from the other side of the door, or from the `$C2` trigger at `($19C8,$6D9)`.

Two earlier attempts at this clip are kept as the record of what does not work:
`raw-10-lrz1-button-door-after` walks Sonic right along the floor past the `$1C`/`$19` pair at
`($445,$4D4)`/`($490,$500)`, but the floor there is 57 px below the horizontal button, so he walks
under it and is simply blocked by the closed door; `raw-11-lrz1-button33-door-after` starts level
with the `$33` button and is blocked by its own solid box before ever standing on it. A horizontal
button has to be approached at its own height and a stand-on button from above.

**Hazard worth carrying.** The first `feat` commit of this slice was made while a detached capture
script had temporarily disabled the four registrations for its "before" build, and `git add`
raced that edit: the commit contained four `// DEMO-DISABLED` comments instead of the
registrations. `git show <sha>:<file>` caught it. Never stage while a before/after capture script
owns the tree; the branch was reset with `git reset --mixed` and re-committed from the restored
tree as `d2c58f148`, with the registry content verified inside the commit.

**Not delivered.** `$15` corkscrew and `$16` wall ride, the other two classes of sub-slice 3a, and
all of 3b, 3c and 3d. No wide-viewport or donor row for the elevator, and no mid-ride rewind spot:
both belong with the rest of the sub-slice's review.

### 2026-09-18 - Slice 3b: doors, buttons and shooting triggers (commit `d2c58f148`)

Worktree `.worktrees/ai-lrz-bring-up`. All Maven through `maven_queue.py -Dmse=off` with
`-Ds3k.rom.path=<worktree>/s3k.gen`; every run below reports 0 skips.

**Delivered.** `LrzDoorObjectInstance` (`$19`), `LrzBigDoorObjectInstance` (`$1A`),
`LrzButtonHorizontalObjectInstance` (`$1C`), `LrzShootingTriggerObjectInstance` (`$1D`) and its
`LrzShootingTriggerProjectileInstance` child, all four registered under `S3kZoneSet.SKL` +
`ZONE_LRZ` with `Sonic3kObjectProfile` `LRZ_ONLY_IDS` entries and act-keyed `LevelArtEntry` rows
(`Map_LRZDoor` `$429DA`, `Map_LRZBigDoor` `$42B24`, `Map_LRZButtonHorizontal` `$42D7C`,
`Map_LRZButtonHorizontal2` `$42D9E`, `Map_LRZShootingTrigger` `$42F06`). `$19` reuses the id the
S3KL set spends on `Obj_LBZCupElevatorPole`; `$1A`, `$1C` and `$1D` were unregistered ids.

**ROM read** (`Obj_LRZDoor` 88015-88063, `Obj_LRZBigDoor` 88070-88145,
`Obj_LRZButtonHorizontal` 88221-88277, `Obj_LRZShootingTrigger` 88279-88348, `sub_42EC0`
88349-88361, `Touch_Special` 21162-21194, `SolidObject_cont` 41486-41512). Six things worth
carrying forward:

1. The door's gate is `tst.b (Level_trigger_array,d0.w)` - the **whole byte**, not bit 0. Any
   writer of that index opens it, which is why `$1C`, the shared `$33 Obj_Button` and `$1D` can all
   feed the same doors.
2. Both doors are one-way. `loc_42974`/`loc_42A68` replace the routine pointer and **fall straight
   into** the next handler, so the trigger frame is also the first frame of travel, and once
   `$2E(a0)` hits `$40` the routine becomes a solid-box-only tail. Clearing the trigger afterwards
   cannot shut a door.
3. The horizontal button is not a stand-on button. Its `swap d6 / andi.w #3,d6` reads
   `SolidObjectFull`'s own return: `SolidObject_cont` does `move.w d6,d4 / addi.b #$D,d4 /
   bset d4,d6` on every horizontal push, and `d6` still holds the standing-bit number (3 for P1, 4
   for P2), so the tested bits are 16 and 17 - "either player is against my side this frame".
4. The big door's two proximity tests differ in signedness on purpose: `cmpi.w #$80,d0 / bhs` after
   `addi.w #-$40` is **unsigned**, so the band is `[y+$40, y+$C0)` and a player above the door wraps
   out of it; `cmpi.w #$50,d0 / blt` is **signed**, so the door only opens from the right. Its sine
   term is *added* (`asr #1`, no `neg`), which is why the already-open branch is `addi.w #$80`.
5. `$1D`'s `collision_flags` is `$C6`: `Touch_Special`'s size list includes 6, and `loc_103FA` adds
   1 for the main character and 2 for the sidekick, so `collision_property` is a per-player bitmask
   that `loc_42E84` consumes with `bclr`. `sub_42EC0` then does nothing unless that player's `anim`
   is 2, so only a rolling player arms the trigger.
6. `$1D`'s subtype is split both ways from one byte: low nibble is the trigger index, high nibble
   times four is the shot period. The two act 1 placements `$A0` and `$C2` are therefore trigger 0
   at 40 frames and trigger 2 at 48 frames. `subq.w #1,$2E / bpl` fires on the frame the word first
   goes **negative**, so the real gap between shots is `$30 + 1` frames.

**Tests.** `TestLrzDoorsButtonsAndTriggers` (18): the door's trigger-index decode over all sixteen
subtypes, that a zero byte holds it shut for 120 frames, the same-frame start, the full
`GetSineCosine` ramp frame by frame against the ROM `SineTable`
(`docs/skdisasm/Levels/Misc/sine.bin`, `sin($40) = $100`), the one-way latch, and the solid box; the
horizontal button's three subtype fields, side-contact-only pressing (a standing contact is
explicitly asserted **not** to press it), the release/latch split and its solid box; the shooting
trigger's index/period split, the `$30 + 1` reload period, a non-rolling touch doing nothing at all,
and a rolling touch negating both velocities, setting bit 0 and self-destructing; the shot's
velocities and flip; and the big door's proximity box over eight boundary cases plus its sine ramp.
Broken on purpose once: `TRAVEL_SHIFT` 2 to 1 turned three door comparisons red
(`doorStartsMovingOnTheSameFrameItsTriggerIsWritten` `expected: <1535> but was: <1533>`,
`doorRisesOneSineStepAFrameAndStopsExactlySixtyFourPixelsUp`, `doorNeverClosesOnceItHasStartedOpening`);
reverted and re-verified.

The census went red on exactly the 28 act-1 and 22 act-2 rows before the baseline was ratcheted
(`$19` 15/11, `$1A` 1/0, `$1C` 10/11, `$1D` 2/0), taking the placeholder totals from 199 / 277 to
**171 / 255**. Batch: 1273 tests, 0 failures, 0 skips (the object test, the census, the dash
elevator, the rock renderer, scroll registration, pattern animation, the falling intro, palette
cycling, `Obj_Button`, the lava block, `TestEveryObjectRewindRoundTrip`,
`TestRewindHarnessCoverageRatchet` and the four mandatory S3K classes); re-run after the guard fixes
as 1201 tests, 0 failures, 0 skips. `-Pguards`: 669 tests, 0 failures.

**Guards that bit, and what they wanted.** The first `-Pguards` run was red seven ways and all seven
are worth recording: `TestRewindCoverageGuard` rejects `private final` scalars decoded from the
spawn (seven keys across the three classes) and wants them non-final, exactly as the dash elevator
found; `TestObjectPhysicsStandardizationGuard` caps raw `setDestroyed(true)` calls in object
packages (582) and wants `ObjectLifetimeOps.destroyLatched`; the same guard rejects
`usesS3kTouchSpecialPropertyResponse()` / `requiresContinuousTouchCallbacks()` without an explicit
`getTouchResponseProfile()`; and `TestArchUnitRules` plus `TestObjectServicesMigrationGuard`
(three assertions) reject `GameServices` in an object package - the zone runtime state has to come
through `services().zoneRuntimeRegistry()`.

**Gap recorded.** The big door's "already opened" bit lives in its placement's
`Object_respawn_table` byte in ROM; the engine models only bit 7 of that table, so
`LrzZoneRuntimeState` keeps the opened placement's X word instead. Entered in
[s3k-known-bugs.md](../../status/s3k-known-bugs.md) with its removal condition.

**Clip 06 re-cut as `09-lrz1-rock-sprites-in-front-of-player-before-after.mp4`.** The complaint was
right and the cause was clip selection, not the renderer. A frame-by-frame diff of the two existing
raw captures (`raw-06-lrz1-rocks-before` vs `raw-06-lrz1-slice2-after`, both 320x224, 360 frames)
shows 271 differing frames, but the largest difference anywhere on that route is 455 pixels in a
single 16x40 box around frame 209 - every other rock in range sits behind opaque foreground tiles.
Cropping that box and looking at it shows what the old clip buried: in "after" a rock sprite is
drawn **in front of Sonic**, hiding most of him, and that is ROM-correct.
`Render_Sprites_NextLevel` (sonic3k.asm:36392-36396) emits the rocks at the end of priority level 0,
and on the Genesis an earlier sprite-list entry is in front, so the rocks sit in front of everything
from level 1 on - and `Obj_Sonic` is `move.w #$100,priority(a0)`, level 2. The new clip is frames
150-280 of the same two captures (123 of those 131 frames differ, checked before publishing), which
is 45 frames of lead-in before the overlap and 50 after. The two earlier raw directories
`raw-06-lrz1-rocks-after-400` (400 px) and `raw-06-lrz1-rocks-after-640` (empty) are not comparable
with the 320 px "before" and were not used. A capture aimed at the twelve-rock column at
`($15C8,$598-$668)` died in lava at frame 43 (`raw-09-lrz1-rocks-column-after`) and is kept only as
the record of that attempt.

**Not delivered.** `$15` corkscrew and `$16` wall ride (the rest of 3a), all of 3c and 3d, and the
dash elevator's owed wide/donor row and mid-ride rewind spot. No cold controller-driven act 1 route
was started. Act 2's door and button skins are registered but have no act-2 unit case and no route
spot. No rewind spot for any slice 3b object beyond the generic
`TestEveryObjectRewindRoundTrip` coverage.

**Independent cross-check of the 3b read (2026-09-18).** A research subagent's full disassembly
pass on the same five routines was recovered after the fact and compared line by line against what
had shipped. It agreed on every behavioural point above and turned up four things worth keeping:

1. **A real defect, fixed.** The shot copies its parent's `mappings` but *not* its `art_tile`: the
   parent writes `make_art_tile(ArtTile_LRZMisc,0,0)` into the child (:88307), so the same
   `Map_LRZShootingTrigger` data is drawn on **palette line 0**, not the parent's line 3. The first
   implementation reused the parent's art key and would have drawn the shot in the wrong palette.
   It now has its own `LRZ_SHOOTING_TRIGGER_SHOT` key and `LevelArtEntry`.
2. **`Clear_Switches` clears `$20` bytes, not `$10`** (sonic3k.asm:104284-104290: `moveq
   #bytesToLcnt($20),d0` then eight `clr.l`), so it wipes `Level_trigger_array` **and** the
   `Anim_Counters` block that immediately follows it at `$FFFFF7F0`. The LRZ call site is
   `loc_56CAA` line 115356, right before `Load_Level`. Slice 6 must clear both, and
   `Sonic3kLevelTriggerManager.reset()` covers only the 16 trigger bytes.
3. **The big door is not deletable while it is moving.** `loc_42AEC` ends with
   `jmp (Draw_Sprite).l`, while the waiting and settled states end with `Sprite_OnScreen_Test`
   (:88136 vs :88145). Scrolling away mid-descent cannot unload it. Not modelled: the engine's
   off-screen handling is generic, and no route reaches the door and leaves inside 64 frames.
4. **`d6` is a documented out-parameter of the whole solid family**, not just the side-push pair:
   `addi.b #$D` gives the side-push bits (16/17), `addi.b #$F` the ceiling bits (18/19) and
   `addi.b #$11` the standing bits (20/21) (:41501-41512, :41583-41606, :41633-41635). Only the
   side-push pair is needed here, but a later object wanting a ceiling hit reads bits 2 and 3 of the
   same swapped word.

Also confirmed rather than assumed: `$33 Obj_Button` already carries its own zone-9 branch
(`Map_LRZButton`, `ArtTile_LRZMisc` palette 3 in act 1, `ArtTile_LRZ2Misc+$1C` palette 1 in act 2,
:60748-60754) and both solidity modes, which is why the census classifies it `V`; and the two
object-pointer listings both map `$33` to `Obj_Button`, so the set choice does not matter for it.

**Gap not closed.** The shot sets `bset #3,$2B(a1)` - the shield-reaction bit that makes a shield
bounce it (:88312). The engine can only express that through a canonical `TouchResponseProfile`
whose remaining fields were not traced, so the shot is a plain harmful `$98` object and a shielded
player absorbs it instead of deflecting it. Recorded here rather than guessed at.

### 2026-09-18 - Slice 3b follow-up: rewind spots and the cold act 1 route

**Rewind spots** (`TestLrzDoorButtonRewindSpots`, 5 tests). Before, during and after the door
opening, plus the button latch and the two together, each followed by a forward replay: the same
number of updates after a restore has to land on exactly the state the uninterrupted timeline
reached. The door cases pin the routine stage as well as `$2E`, because a restore that brought back
the counter but not the stage would replay from the wrong branch. The harness registers
`Sonic3kLevelTriggerStaticAdapter` alongside the object manager, as
`Sonic3kLevelEventManager#extraRewindAdapters` does in production. Broken on purpose once by
dropping that adapter: two tests went red on the shared-array assertion in opposite directions
(`buttonRewindSpot... expected: <true> but was: <false>`, `doorRewindSpotBefore... expected:
<false> but was: <true>`), which is the discriminator that the array and the objects restore
together rather than either alone. Reverted and re-verified 5/5.

**Cold act 1 route, started from the level start with no teleport.** Five hand-authored attempts
and one derived from the native input; inputs preserved in `~/Videos/OGGF/lrz-bring-up/inputs/`.

| Attempt | Reaches | Blocker | What it was |
| --- | ---: | --- | --- |
| v1 hold right + jump every 45 frames | x 326 | the `$05` rock at `($161,$51E)` | input, not engine |
| v2 hold right, no jumps | x 326 | same | input |
| v3 spindash from a standing start | x 1141 | the `$19` door subtype `$04` at `($490,$500)`, correctly shut | **the slice 3b object, working** |
| v4 back off and jump right | x 1141 | no ledge to the left; he falls | input |
| v5 native door manoeuvre | **x 1573** | the `$08` spikes at `($640,$538)` | input |
| v6 v5 + a jump over the spikes | **x 1845** | terrain at x 1845; native is airborne over it | input |
| v7 v6 + native's second jump | **x 1909** | the vertical climb from x 1877 | input, and see the conclusion |
| native input column, 12000 rows | **x 2357** at frame 2318 | the two located divergences below | measurement |

Three things worth keeping from this:

1. **No blocker so far selects a new object class.** All three real stops are navigation: the `$05`
   rock is subtype `$44`, whose low nibble sets bit 2, so `AIZLRZEMZRock_CheckPushBreak` sends it to
   `AIZLRZEMZRock_PushBreakMain` - it breaks to a *rolling* player, and a walking or jumping route
   can never pass it. The spikes want a jump. The door wants its button.
2. **The door is opened on a cold route.** v5 reproduces the native manoeuvre at `($475,$50D)`:
   turn left one frame, jump up-left for eleven, drift left for seventeen, then steer right. The
   engine reaches `(1116,1235)` at frame 400 - level with the `$1C` button at `($445,$4D4)`, 57 px
   above the floor - and is past the door's x at 1169 on the ground by frame 460. That is the
   button, the `Level_trigger_array` write and the door's 64-frame rise exercised end to end through
   production, not a unit test.
3. **How the route was found.** Not by guessing: the segment fixture
   `traces/s3k/runs/s3k-sonic-tails-complete-emeralds/lrz/physics.csv.gz` is a plain gzipped CSV
   whose `input` column is the recorded controller state. Its bit order is recoverable from the
   trace's own behaviour - 0 Up, 1 Down, 2 Left, 3 Right, 4 A, and no other bit occurs in any of the
   38,885 rows - and those five bits convert straight into a `GameplayCaptureTool` input log. This
   is controller input, not gameplay state: nothing physics or aux is read, so it stays inside the
   comparison-only rule, and it is the same shape as the DDZ seeded-route captures.

**Clip 13** `13-lrz1-cold-route-door-opened.mp4` (`raw-21-lrz1-cold-route-v5-full`, frames 340-520
of a 620-frame full-rate re-capture; the route captures themselves use `--every 4` or `--every 8`,
which is why the clip needed its own pass). No before/after halves: this one is the cold route
itself, from the level start with no teleport. Sonic arrives at the shut door at frame 380, turns
and jumps up-left onto the `$1C` button at frame 400, lands at 430, and is through the doorway at
frame 460. Frames 385 and 450 were extracted and compared before publishing: in the first the
sandstone column is down and blocking him, in the second it has risen into the ceiling with only
its bottom edge showing.

**Why the hand-authored route stops at x 1909, and what that costs.** Each extension so far has
been a navigation fix copied from the native input: a spindash for the `$05` push-break rock, the
turn-and-jump-left onto the `$1C` button, a jump over the `$08` spikes at `($640,$538)`, then
native's second jump at its row 896. Past x 1877 the act turns into a vertical climb - native jumps
at rows 940, 988 and 1042, gaining about 60 px each time - and copying that sequence stops working,
because each jump has to *land where native lands*. It does not: at x 1845 native is airborne at
y 1292 while the engine is grounded at 1328, and at x 1909 native is near y 1198 while the engine
sits at 1260. There is no placed object anywhere in x 1830-2150 between y 1100 and 1500, so nothing
here is a missing class - **the route is stalled by phase, not by content**, and the phase is the
two divergences measured below. Extending it further by hand would mean re-timing every jump against
an engine that is one frame out from the act's third frame; the economical order is to close those
two first and then replay the native input, which already reaches x 2357 unaided.

**Two located divergences, not "drift" (measured 2026-09-18 at `f75a47ae5`).** Replaying the
recorded input frame for frame and comparing against the same fixture's own rows turns the vague
"open-loop limit" into two specific defects, both inside the first 165 frames, both with
**identical input**, and neither caused by anything this campaign has implemented.

1. **The falling intro starts gravity one frame late.** First position difference greater than
   1 px is **frame 7** (engine `y` 36, native `y` 38, input `0000`, both airborne, both
   `ground_vel` 0), but the shape is not drift: over frames **3 to 155** the engine's `(x,y)` equals
   the native row `n-1` **exactly, every frame** - 153 consecutive frames with zero mismatches at
   shift 1, against 323 mismatches at shift 0 and 319 at shift 2. The engine is one whole frame
   behind from the very first moving frame of the act, before any object is involved. The
   air-to-ground transitions carry the same offset: native rows 62, 108, 181; engine frames 63, 109,
   190.
2. **The `$31` collapsing bridge gives way eight frames late.** The shift-1 match breaks at
   **frame 156**. At native row 152 the player standing at `($17D,$371)` leaves the ground and
   `y_speed` climbs 56, 112, 168, 224 as the platform under him drops; the engine keeps
   `air = 0` and `y` pinned at `881` until **frame 161**, eight frames later. The only object within
   96 px is `$31 Obj_LRZCollapsingBridge` at `($13E,$3A0)`, 63 px left and 47 below - the anchor end
   of a bridge the player is standing along. `$31` is a shared, already-implemented class
   (`CollapsingBridgeObjectInstance`), inherited rather than introduced here.

Both belong to slice 11, and the second is the more tractable: a collapse-timing comparison against
these rows needs no new harness. The accumulated figures the earlier entry quoted (44 px by frame
400, 1833 by 2200, death at 4659) are the downstream consequence of these two, not separate
defects, and should not be read as a list.

### 2026-09-18 - Slice 3a continued: the corkscrew (commit `9b0608d96`)

**Delivered.** `LrzCorkscrewObjectInstance` for `$15` (one act 1 placement at `($1240,$3D8)`),
registered on the id the S3KL set spends on `Obj_LBZPlayerLauncher`, with a `LRZ_ONLY_IDS` entry and
no art: Init writes only `width_pixels` and the routine pointer, so the object is invisible and the
level art draws the shape it sweeps the player along.

**Four ROM readings worth carrying.**

1. **The accumulator is read two ways.** `$30(a0)`/`$34(a0)` are longs; `add.l` accumulates
   `ground_vel << 8`, but every `cmpi.w`/`move.w` on `(a2)` reads the **high** word
   (sonic3k.asm:87613-87616). So the high word is the ride parameter and the exits are "the long
   went negative" and "the high word reached `$700`". Reading those word accesses as the low half
   would put the rider at the wrong point of the turn while still looking plausible frame to frame.
2. **The two capture tests are deliberately different kinds.** Horizontal is an unsigned borrow
   (`bcs`) plus a signed `bge #$20`, so half-open; vertical is a plain signed `bgt #$20`, so it
   includes its far edge.
3. **The Y add is a byte add into a masked word.** `andi.w #$FF80,d0 / add.b (a3,d1.w),d0`
   (:87640-87643): the table value lands in the low byte and the `$80` step stays in bit 7 of that
   byte rather than carrying into the high byte.
4. **Both exits reverse the rider.** `neg.w ground_vel(a1)` at :87591 and :87607 on a speed that was
   positive for the whole ride. And `move.w #1,anim(a1)` is a word write over `anim` and
   `prev_anim`, so the rider leaves with `anim` 0 - the walk - not `anim` 1.

The three tables (`RawAni_4247E` `$4247E`, `byte_4248A` `$4248A`, `byte_4250A` `$4250A`) were read
out of the user-supplied ROM image and are byte-identical to the disassembly's `dc.b` listings.

**Independent confirmation from the native trace, not used to build the class.** In
`s3k-sonic-tails-complete-emeralds/lrz`, row 3393 has the player at `(4654,981)` with
`ground_vel` 1076; row 3394 is at `(4658,983)` - `dx` 2 inside the half-open `$20` box, `dy` 15
inside the inclusive one - and `ground_vel` jumps to **1536**, exactly the `$600` floor. It then
climbs 1552, 1568, 1584, 1600, 1616, 1632: exactly `$10` a frame. Both the capture floor and the
acceleration are therefore confirmed against recorded hardware.

**Tests.** `TestLrzCorkscrewObjectInstance` (10) and `TestLrzCorkscrewRewindSpot` (3, before /
mid-ride / after with forward replay, pinning the whole long rather than its high word). Broken on
purpose once: `X_AMPLITUDE` `$4800` to `$4000` turned
`rideXOffsetIsTheSineTimesFortyEightHundredHighWord` red at `expected: <27> but was: <24>`;
reverted. Census red on exactly the one `$15` row, then act 1 ratcheted 171 to **170**. Green batch
1220 tests, 0 failures, 0 skips; `-Pguards` 669, 0 failures.

**Clip 14** `14-lrz1-corkscrew-before-after.mp4` (`raw-22-lrz1-corkscrew-{before,after}`, 300
frames, 299 of which differ). With the corkscrew the player's `ground_vel` is floored to `$600` at
frame 68 and climbs to 3984 while he is swept 416 pixels down and around the turn; without it he
runs past at his own speed and stops on terrain at x 4797.

**Not modelled.** `scroll_delay_counter`, which the capture zeroes and the engine has no setter
for; it affects camera lag, not the ride.

**Read ahead for 3a's remainder, so the next agent does not re-derive it.** `Obj_LRZCorkscrew`
(sonic3k.asm:87494-87513, ROM `$4224E`) and `Obj_LRZWallRide` (:87693-87712, ROM `$4254A`) are both
much larger than anything in 3b: each takes full control of the player through
`object_control = $43`, drives `x_pos` from `GetSineCosine` times `$4800` and `y_pos` from a
128-byte offset table (`byte_4248A`, with `byte_4250A` swapped in above `(a2) = $600`), picks
`mapping_frame` through `divu.w #$16` into the 12-byte `RawAni_4247E`, flips `art_tile` bit 7 from
the ride angle, and tail-calls `Perform_Player_DPLC`. Capture needs `ground_vel >= 0`, not airborne,
no existing `object_control`, and a `$20`-wide box; the corkscrew floors `ground_vel` at `$600` and
accelerates by `$10` a frame to `$1000`, the wall ride floors it at `+-$400` and reads `status`
bit 0 to pick the direction. Both carry a live `FixBugs = 0` branch: `addq.b
#p2_standing_bit-p1_standing_bit,d6` leaves `d6` dirty after Player 1's `Perform_Player_DPLC`, so
Player 2 behaves erratically on a shared ride, and the shipped behaviour is the dirty one.


### 2026-09-18 - Both located divergences withdrawn, and `$17` found by the route

**Neither divergence was an engine defect.** The full measurement is in the
[trace frontier log](../../status/trace-frontier-log.md); the short form:

1. The **falling intro** is not one frame late. `TestS3kSonicTailsLrzSegmentTraceReplay` at
   `9744c58de` has its first error at frame 208 (`tails_y_speed`, expected `0x07BD`, actual
   `0x0000`, 7191 errors total), and `TraceBinder` compares Player 1 `y` and `y_speed` on every row
   (`TraceBinder.java:202`, `:217`), so frames 0-207 are an exact Player 1 position match - frame 7
   included. What was one frame late was the **capture**: `GameplayCaptureTool` writes its state row
   after `loop.step()` but its `boot()` leaves one pre-gameplay frame for the first step to consume,
   so capture frame `n+1` is native row `n`.
2. The **collapsing bridge** is not eight frames late. The capture ran with the tool's default
   `--sidekick none` against a Sonic + Tails fixture, and `$31` arms on *either* player's standing
   bit (`loc_39CBC`). Tails is standing on that bridge from row 101, seven frames before Sonic lands
   at 108. With subtype `$00` the counter starts at `8`, the collapse fires one frame after it
   expires and the rider is released `$2A` frames later: `101 + 8 + 1 + 42 = 152`, the native release
   row exactly. `LrzCollapsingBridgeInstance` was already correct and was not touched.

**A third alignment fact, found while checking the first two.** The input log has to start one
capture frame late too (`--settle 1`). Applied from frame 0 it reached the engine one gameplay frame
early, which released a spindash at native row 364 instead of 365. With `--sidekick tails --settle 1`
the cold route on the recorded input matched the fixture's Player 1 `(x, y)` **exactly for frames
0-628**. The lesson is recorded as a hazard: a cold-route capture compared against a fixture needs
the fixture's own team and the capture's own one-frame boot offset, on both the comparison and the
input, or it manufactures divergences that look like engine defects.

**What the corrected comparison then found is a real missing class**, not phase. At native row 629
the player lands on an object (`player_stand_on_obj` goes to `0D`) at `($4F3,$51F)` and the engine
fell through it. The layout has `$17 Obj_LRZSinkingRock` subtype 0 at `($4F8,$543)` with
`d3 = $11`, so its top is `1347 - 17 = 1330` and a standing player sits at `1330 - 19 = 1311` -
the native row exactly. Implemented (commit `d38a4aa34`); the exact match now runs to frame 636.

**The new first divergence is one pixel at frame 637** and belongs to the shared solid/riding path:
the ROM runs Player_1 before the block, so a jump off it uses the previous frame's seat, while the
engine sinks the block and re-seats the rider first and applies both the sink's `+1` and the jump's
`+5`. Recorded with its kill condition in the frontier log. It is what ends the route: by frame 800
the engine is 1 px low, and at row 856 the fixture negates `player_y_speed` exactly (`208` to
`-208`) on a margin the engine misses. Open-loop the route still reaches **x 2779** (was 2357).

**Clip 15** `15-lrz1-sinking-rock-before-after.mp4` (`raw-26-lrz1-sinking-rock-{before,after}`, 160
frames, 159 of which differ). Teleported onto the block at `(1280,1290)` with two jumps: in "after"
he lands on it, it sinks under him, he jumps off, it rises back, and it sinks again; in "before" he
falls straight through and dies in the lava below. The block sinks a standing player into that lava
in about 51 frames, which is why the demo jumps.

**A hazard that cost two captures.** `exec:java` does not recompile, and the earlier
`raw-25-lrz1-sinking-rock-*` pair was taken against stale classes: the "after" half showed the
player supported at `y 1311` but never sinking, which reads exactly like a missing rider carry. It
is kept as the record of that. Always queue `compile exec:java` after touching engine code.

### 2026-09-18 - Slice 3a's remainder and most of 3c

Six classes landed after the divergence work, each with a ROM-cited unit test broken on purpose
once, a rewind spot where the object has restorable state, a before/after clip whose halves were
frame-compared before publishing, and a census ratchet. Commits: `d38a4aa34` `$17`, `94f72378e`
`$16`, `c01b86106` `$18`, `366589663` `$1B`, `99688145b` `$1F`, `21fbec7e6` `$20`.

**Readings worth keeping.**

1. **`$18`'s trigger distance is the subtype in pixels.** `move.b subtype(a0),$2F(a0)` writes into
   the *low byte* of the word `cmp.w $2E(a0),d0` reads, and a freshly allocated slot is zeroed
   (`Delete_Referenced_Sprite` clears the whole SST; `AllocateObject` only looks for a slot whose
   first long is zero). Lava Reef's subtypes are 1 to 5, so a spike releases only while a player is
   within one to five pixels of its X. Both instructions were byte-verified in the ROM at
   `$4288C` and `$428BE` because a value that narrow reads like a transcription slip.
2. **`$16` carries a live X-velocity defect.** `loc_42718` loads the rider's previous `x_pos` into
   `d2` and then overwrites `d2` with the ride amplitude before `sub.w d2,d0` makes `x_vel`, so
   `x_vel` is `(newX - amplitude) << 8` and not a displacement; the Y path re-loads `y_pos(a1)`
   after using `d2` and does compute a real delta. Byte-verified at `$42718` and `$42748`.
3. **`MoveSprite2` scaling was wrong in landed work.** The routine is `ext.l / lsl.l #8 / add.l`
   per axis (sonic3k.asm:36054-36061), so an 8.8 velocity must be shifted left eight to line up
   with a 16.16 position. `LrzShootingTriggerProjectileInstance` added the raw word, so its shots
   crept at 1/256 of the right speed, and its test asserted only the velocity *fields* and never
   the resulting motion. Fixed in `366589663`, with a motion assertion added. **The lesson is the
   general one:** a test that asserts the inputs to a computation and not its output cannot catch
   the computation being wrong.
4. **`render_flags` bit 7 is "was drawn last frame", not "is on screen".** `$1B` gates its shot on
   it, and a launcher created as the camera reaches it must not fire on its creation frame. The
   class latches the previous frame's on-screen answer rather than asking the camera directly.
5. **`$20`'s chain geometry has a dirty-register question**, recorded on the class with a kill
   condition: `GetSineCosine` writes only the low word of `d0`/`d1`, so after the `swap` the long's
   low word is caller junk. It cannot reach the first link, but the `add.l` loop accumulates it and
   with four links could carry one pixel into the ball. Modelled as clean; kill it with a trace row
   that disagrees.

**Clips 15-20** in `~/Videos/OGGF/lrz-bring-up/`: `15` sinking rock, `16` wall ride, `17` falling
spike, `18` fireball launcher, `19` lava fall, `20` swinging spike ball, with `raw-26` to `raw-31`
kept. `raw-25` is kept only as the record of a stale-classes capture (see the hazard above).

**Not delivered.** 3c's `$21` (15 placements, ten subtypes) and `$22` (6, two subtypes), and 3d's
`$9C`. Read-ahead for all three is in the handover below.

### 2026-09-18 - Slice 3 completed, and slice 4's Iwamodoki

Base develop `035e48a58`, branch head `72920cabc`. Commits: `f0b7a6eff` `$21` + `$22`,
`cfa443e13` `$9C` and the screen-event chunk edits, `98a8c7261` the shake-table fix,
`6f6a48bab` the owed rewind spots, `72920cabc` `$9A`.

**Census: 43 / 206 / 8** placeholders of 609 / 455 / 35 (98 / 240 / 8 at the second handover;
239 / 281 / 14 at `035e48a58`). Slice 3 has no rows left.

**Readings worth keeping.**

1. **`$21`'s fall is an accumulator, not a speed.** `loc_43128` reads the CURRENT `y_vel` and adds
   `$40` afterwards, and the displacement lands on the 16.16 long at `$34(a0)`, so after `n`
   frames the whole-pixel drop is exactly `floor(n(n-1)/8)`. The landing write
   `move.w $38(a0),$34(a0)` replaces only the HIGH word, so the fraction survives into the next
   fall - a class that kept `$34` as a plain pixel count would drift.
2. **`$22` subtype 0 is not static.** The handover read it as "the static big spike"; it is not.
   `loc_4397E` takes the same `$44(a0) + (cos(angle) asr 2)` X as the swinging shape and
   `subq.b #2,angle(a0)` at `:89016` runs it, so the boulder grinds 64 pixels either side of its
   placed X on a 128-frame cycle while `ObjCheckFloorDist` keeps it on the terrain. Five of Lava
   Reef's six placements are this shape.
3. **`Events_bg+$0C`'s two shapes are the same word written two ways.** `st (Events_bg+$0C)` sets
   the whole word, so the request reads NEGATIVE; `st (Events_bg+$0D)` sets only the low byte, so
   it reads POSITIVE. That is the entire difference between `LRZ1_ScreenEvent`'s two branches.
4. **`LRZ1_ScreenEvent`'s `a3` is `Level_layout_main`, and its entries are longs.** The
   `ScreenEvents` preamble loads it (`:102237`) and `Layout_row_index_mask` is `$7C` (`:102207`),
   so `movea.w $38(a3)` / `$3C(a3)` / `$40(a3)` are the FOREGROUND row pointers of layout rows
   14, 15 and 16 and `lea $1D(a1)` is column 29. The crusher's own bridge coordinates confirm it
   independently: subtype 0 drops slabs at `($F00,$760)` and `($F80,$760)`, columns 30 and 31 of
   row 14, and the other subtype at `($540,$860)`, column 10 of row 16. The engine calls the ROM's
   `$80 x $80` unit a **block**, so each ROM byte write is one `setBlockInMap(0, column, row, id)`.
5. **The crusher rumbles; it does not creep.** `bchg #0,$38(a0)` alternates `+1` and `-1`
   (`:197097-197102`), so there is no net descent. What ends the rumble is the floor test below it,
   and that turns non-negative only once the timer child has rewritten the layout out from under
   it. The set piece reads as the crusher smashing through, not descending.
6. **`byte_904AC` is indexed by a BYTE offset.** `loc_9046E` builds
   `d3 = ((subtype & 8) >> 1) + (0 or 2)` and then `lea byte_904AC(pc,d3.w)`, and the table's
   entries are `(frames, delta)` PAIRS. Reading `d3` as a pair index walks off the end for the
   upper row: a real act 1 load threw `Index 4 out of bounds for length 4` inside
   `GameplayCaptureTool`. The unit test could not have caught it because it never built a piece.
   **The lesson is the general one:** a class with children needs a test that builds the children.
7. **`Animate_RawMultiDelay` skips its script's first pair.** `addq.w #2,d0` runs BEFORE the read
   (`:177563-177566`) and `anim_frame_timer` starts at zero, so the first step lands on pair 1.
   Every S3K object driven by that routine inherits it; `$9A`'s fuse opens on `byte_8FC30`'s
   second pair.
8. **`Obj_Iwamodoki` has no touch collision at all.** `ObjDat_Iwamodoki` leaves `collision_flags`
   at 0, so it is not an attackable badnik - it is a solid block with a fuse. Its solid is
   `d1 = $17`, `d2 = $C`, `d3 = $B`, with `d3` one LESS than `d2`, not one more as most solids
   have it.

**Recorded, not modelled.** `Check_CameraInRange`'s second tail comparison reads `4(a1)` after
four `(a1)+` reads, i.e. index 6 of a six-word table - for `word_901B8` that is `word_901C4`'s
first word. It sets `$27(a0)` bit 6, which no routine here reads, so the over-read is inert and
`LrzRockCrusherObjectInstance` reproduces it by not modelling bit 6. `loc_90368`'s requeue of
`ArtKosM_FirewormSegments` and `ArtKosM_Iwamodoki` waits for slice 4's remaining consumers; the
palette restore is implemented.

**Clips 21-25** in `~/Videos/OGGF/lrz-bring-up/`: `21` smashing spike platform, `22` grinding
spike ball with its rock chips, `23` the swinging boulder arming and breaking loose, `24` the rock
crusher set piece, `25` the Iwamodoki fuse and detonation, with `raw-32` to `raw-36` kept. These
are after-only clips, not before/after pairs: the "before" for every one of them is a
`PlaceholderObjectInstance` that draws nothing, which the census records exactly. Clip `24`'s
frames 200 and 330 differ in exactly the edited layout rows.

**Slice 4's second class, `$9B Obj_Toxomister`** (commit `c45f35e74`), landed after those
measurements: the body, its cloud and the seven puffs. Two readings worth keeping.

9. **`Check_LRControllerShake` frees on the SIXTH reversal, not the fifth.** `$3C(a0)` is loaded
   with 5, but the escape is `subq.b #1,$3C(a0) / bmi`, so it is the reversal that takes the
   counter below zero that frees the player. Reading the loaded constant as the count is off by
   one; the test says so by name.
10. **The Toxomister's slow-down is an arithmetic shift.** `sub_8FFE0` takes an eighth off
    `x_vel` and an eighth off `ground_vel` -- or `y_vel` when airborne -- every frame with
    `asr.w #3`, so a negative speed decays toward zero rather than reversing. A logical shift
    happens to give the same answer for a 16-bit value after truncation, so that perturbation is
    NOT a usable "break it on purpose" for this routine; changing the shift distance is.

**The four object-graph links this round needed sidecars.** `LrzRockCrusherPieceInstance#parent`,
`ToxomisterBadnikInstance#cloud`, `ToxomisterCloudInstance#body` and `ToxomisterPuffInstance#parent`
are all read every frame and cannot be rebuilt from scalars. Java `transient` does NOT satisfy
`TestRewindCoverageGuard`: it wants `@RewindTransient` plus a real restore, and
`TestRewindArchitectureGuard` then ratchets both the annotation and the capture/restore override,
so each addition is triaged in its two baselines with the reason. The crusher piece had the same
latent defect from the earlier commit -- a restored piece would have frozen in place -- and is
fixed here.

**Measurements at `72920cabc`.**

| What | Command | Result |
| --- | --- | --- |
| `TestS3kSonicTailsLrzSegmentTraceReplay` | `maven_queue.py -Dmse=off -Ptrace-segments -Dtest=... test` | **7708 errors, first error frame 208 `tails_y_speed`** (7174 at `21fbec7e6`). The first error frame and field are unchanged, so the frontier has NOT moved; the count rose because the newly implemented hazards now act on later frames that previously ran empty. A count is not a frontier. |
| Mandatory S3K + LRZ/HPZ/SOZ + rewind batch | `-Dtest=TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestLrz*,TestS3kLrz*,TestS3kHpz*,TestS3kSoz*,TestEveryObjectRewindRoundTrip,TestRewindHarnessCoverageRatchet` | 1556 tests, 0 failures, 0 skips |
| Structural guards | `-Pguards test -B` | 669 tests, 0 failures |
| Cold act 1 route on the recorded input | `GameplayCaptureTool --main sonic --sidekick tails --settle 1 --frames 12000` | exact `(x, y)` to frame 636 unchanged; open-loop reach **x 4029**, then **hurt at frame 1102** and **dead at frame 3649** |

**The route got shorter, and the reason is not a stale input.** It reached x 4301 at `21fbec7e6`
and reaches x 4029 now, on `lrz1-native-input-route.txt` - the FIXTURE'S OWN recorded input, which
the native game plays through these same hazards without dying. What that number measures is how
long the engine's open-loop replay survives its accumulated phase error, and the error has been
there since frame 637. Adding faithfully implemented hazards that native also has can only lower
an open-loop reach when the engine is already off-phase: the first hurt is at frame 1102,
`(1993,1157)`, beside `$1B Obj_LRZFireballLauncher` subtype `$1C` at `(1982,1008)` and
`(1789,1168)`, and the run then limps to x 4029 with no rings and dies at frame 3649 near
`(3790,959)`.

**So the reach is not a regression and the classes are not implicated** - but it is also no longer
a useful progress number on its own. It will keep falling as the zone fills in, until the frame-637
shared solid/riding ordering divergence is closed. Quote the exact-match frame (636) and the first
divergence, not the open-loop reach, until then.

This is focused validation, not a suite pass: the change-based runner has not been run against
`035e48a58` for this branch.

### 2026-09-18 - The frame-637 divergence closed (it was not an ordering defect)

The second handover recorded the frame-637 `player_y` 1322/1321 divergence as a shared
solid/riding **ordering** question and put it outside the campaign. That framing was wrong, and
one read of the engine kills it: S3K already runs the player pass before the object pass.
`ObjectInteractionRules.objectsExecuteAfterPlayerPhysics()` is true for S3K, `LevelFrameStep`
(:325, :339-:364) wraps `physics` and then `objects` with inline solid checkpoints in that order,
and `SpriteManager.tickPlayablePhysics` (:1836-:1842) deliberately skips the legacy pre-movement
batched solid pass for exactly that reason. Nothing needed reordering.

Instrumenting a bounded headless reproduction (ride the `($4F8,$543)` block, jump while `$2E`
climbs) showed the block's own `update` seeing Player 1 already at the ROM's 1322 with
`air = true`, and the player leaving the block's checkpoint at 1323. The pixel is
`resolveContactInternal`'s `loc_1E154` upward-velocity lift, reached because an earlier slot's
checkpoint (`LrzButtonHorizontalObjectInstance`, in this act) had already consumed the block's
riding record and standing bit; the block's own checkpoint then read `riding=null
standingBit=false` and took the fresh-contact path.

The ROM's `a0.d6` is per object (`SolidObjectFull_1P`, sonic3k.asm:41021-41034): `loc_1DC98`'s
`bclr d6,status(a0)` names *that* object's status byte, so another solid's `SolidObjectFull`
cannot clear this block's. With the bit set and `Status_InAir` set, the block's own call returns
`d4 = 0` - no `MvSonicOnPtfm`, no `loc_1E154`.

**Fix:** `LrzSinkingRockObjectInstance` declares `airborneRiderUnseatRequiresOwnCheckpoint` (the
`Obj_MGZMovingSpikePlatform` precedent) and `airborneStaleStandingBitReturnsNoContact`. Both are
existing per-object opt-ins; **no shared file was edited**, so the change cannot reach another zone
or game and no matched cross-zone baseline run was needed. `TestS3kLrzSinkingRockJumpOffHeadless`
is the RED-first test, cited from `Sonic_Jump` (:23288-23349, the `loc_118AE` +5) and
`loc_1DC98`; it failed with the route's own `expected 1322, was 1323` before the fix.

**Measurements at this head.** Mandatory S3K + `TestLrz*`/`TestS3kLrz*`/`TestS3kHpz*`/`TestS3kSoz*`
+ `TestEveryObjectRewindRoundTrip` + `TestRewindHarnessCoverageRatchet`: 1554 tests, 0 failures, 0
skips. `TestS3kSonicTailsLrzSegmentTraceReplay`: 7703 errors, first error still frame 208
`tails_y_speed` - frontier unchanged, five fewer errors than `72920cabc`'s 7708. Cold act 1 route
on the fixture's recorded input: exact Player 1 `(x, y)` for **native rows 0-856** (was 0-636),
open-loop reach still x 4029.

**New first divergence: native row 857, `player_y` 1228 against 1230** (`player_x` 1635 in both).
This is the sign negation the previous entry predicted at row 856, near `($663,$4CC)`. Its owner is
not a `$17`-family placement and has not been identified; it is the next route blocker.

### 2026-09-18 - Slice 4 completed, the breadth matrix, and slice 5's state half

**`$99 Obj_Fireworm` (`cad4a2e07`)** closes slice 4. Four ROM objects, four classes: the placement
is a spawner with `collision_flags 0` and no `Draw_Sprite` anywhere, waiting for `Find_SonicTails`
to report a horizontal distance under `$80`; the head is the only attackable part and the only one
with dynamic art (`ArtUnc_Fireworm` + `DPLC_Fireworm`, registered on the `MGZ_BUBBLES_BADNIK`
precedent); four segments seed `$2E` from `word_8F940` (`$B,$16,$21,$2C`) indexed by their own
`CreateChild1_Normal` subtype and do nothing at all while they wait, which is where the trailing
shape comes from; each segment then grows a flame with `shield_reaction` bit 4 and a
`Random_Number & $3F` hold between flicker loops. The swim-and-turn pair is shared because the ROM
shares it literally (`off_8F906` points routines 6 and 8 at the head's own `loc_8F862`/`loc_8F89A`).
One oddity worth keeping: `loc_8F8C6` tests the candidate x velocity before storing it, so after a
turn the worm keeps `$F0` and never reaches `$100`. `S3kRawAnimation` gained
`animateNoSstMultiDelayFlipX` for `byte_8FA4D`'s bit-6 flip entry.

**Census 21 / 197 / 8 -> 1 / 188 / 8.** The one remaining act 1 placeholder is the `$9D` miniboss.

**Breadth (`728aee1c9`).** `TestS3kLrzCompatibilityMatrix`, ten rows: four rosters (Sonic,
Sonic + Tails, Tails, Knuckles), 320 and 400, donor off and `s1`, plus two act 2 rows for act 2's
own skins. Each asserts the live roster, the viewport reaching the camera, the donor's own
capability rules reaching the playable (`spindashEnabled() == false` for S1), and a ready
ROM-backed renderer for every art key a slice 3/4 class draws from. It deliberately does not
re-assert registry id resolution: which placements a frame materializes depends on the camera, so
an object sweep would measure the camera, and the census already pins the ids exactly.

**Route rewind spots (`749bb7c08`).** `TestS3kLrzRouteRewindSpots`, four spots that need real
terrain and a real object graph. Writing them found two things. The Fireworm head resolved its
segment `ObjectRefId` sidecars as REQUIRED and threw when one was missing -- fixed, a missing link
must shorten the list. And restoring a composite snapshot drops dynamically created children
entirely: the crusher's four `S3kCameraGradualObjectInstance` children and the Fireworm's four
segments both vanish from `object-manager.usedSlotsBits` after a restore although both classes
implement `RewindRecreatable`. That is engine-level and is now in
[s3k-known-bugs](../../status/s3k-known-bugs.md) with its reproduction; those two spots assert the
parent's own fields until it is fixed.

**Slice 5's state half (`01fb501f4`).** `LrzDomeRegions` carries `sub_56DCA` and `word_56F88` with
every branch named -- X max inclusive, Y max exclusive, row 1 the only low-side threshold, and the
fact that outside every box the routine returns without touching `Events_bg+$00`, so a locked
background survives leaving the box. `Obj_56EA0` is the dome lava surface: a 32-bit position and
velocity turning round at zero with `±$C000` and a `$100` step, publishing the position's high word
as `_unkEE9C`, which is both its own `y_pos` (`$988` minus it) and the term `sub_56DAC` adds to the
background Y. `$34(a0) = -$4000` is written and never read; recorded rather than dropped. The burn
is asymmetric by design: a fire shield saves Player 1 and not Player 2.

**Media.** Clips `27` (the Fireworm) and `28` (the mist catching and pinning Sonic), with
`raw-27-lrz1-fireworm` and `raw-28-lrz1-toxomister-catch` and both inputs preserved.

### 2026-09-18 - The rewind restore gap was not one, slice 5 completed, and row 857 identified

**The dropped children were a missing probe constructor (`73f78efb2`).** The recorded engine-level
restore gap does not exist. `ObjectRewindDynamicCodecs.genericRecreate` has to *build* an instance
of a `RewindRecreatable` class before it can call `recreateForRewind`, and it only knows a fixed
list of constructor signatures (`(ObjectSpawn)`, `(ObjectSpawn, int)`, `(ParentType)`,
`(int, int, int)` and so on). `S3kCameraGradualObjectInstance(int)` and
`FirewormSegmentInstance(int,int,int,int,boolean,boolean)` match none of them, so probe
construction returned `null` and each child was dropped in silence. Both classes gained a private
`(ObjectSpawn)` probe constructor; the fix reaches Hidden Palace and Doomsday too, since they own
the other `S3kCameraGradualObjectInstance` users.

Stating the gap as a child count first is what made it cheap: `TestS3kLrzRouteRewindSpots` went red
at 0 of 4 crusher children and 0 of 8 Fireworm segments, and green after. Restoring the segments
then exposed a **second, real** defect one level down. The flame runs
`Refresh_ChildPositionAdjusted` (`loc_8F95C`) and never moves on its own, so the lost `flame`
reference left a restored flame standing still while its segment swam away -- one pixel a frame,
which is exactly how the whole-composite forward replay reported it (`spawn.x: A=2546 B=2545`).
The link now travels as an `ObjectRefId` sidecar like the head's segment list. Both of the
crusher's and the Iwamodoki's spots are whole-composite again.

**Slice 5's background half (`75d607383`).** `LrzBackgroundStageMachine` owns three of
`LRZ1_BackgroundEvent_Index`'s four entries. `loc_56E40` sets `Events_bg+$00`, allocates
`Obj_56EA0` and steps to stage 4. `loc_56E66` clears the lock and saves `Camera_X/Y_pos_BG_copy`
into `Events_bg+$02/$04` **before** re-running `LRZ1_Deform` -- the one ordering detail the whole
effect rests on, because what gets pinned is the *locked* dome view while the real background is
redrawn underneath it -- seeds `Draw_delayed_rowcount` to `$F`, steps to stage 8 and drains its
first two rows on that same frame (`addq.w #4,sp / jmp loc_56C8C`). Stage 8 never calls
`sub_56DCA`, so a threshold crossed during those eight frames is not seen. The pass ends on the
call that takes the counter negative: `Draw_PlaneVertBottomUpComplex` calls `sub_4F03E` once and
again while the `subq.w #1` left it non-negative, so `$F` is eight frames, not fifteen.

`SwScrlLrz` gained the two matching modes -- `sub_56DAC` plus `PlainDeformation` while locked, and
the same plain fill from the saved copies while pinned -- and `Sonic3kLRZEvents` runs
`LRZ1_BackgroundInit`'s Knuckles-only `$F6` chunk once per act 1 load, on background row 1 column 4
through the mutation pipeline. The locked mode deliberately rebuilds no `HScroll_table` word and no
`Events_bg+$10/$12`, so the animated-tile channels hold their phase while the dome is up.

**Slice 5 has no clip, and the reason is worth more than the clip.** The lock demonstrably runs and
writes very different scroll words, and the rendered frame does not change by one pixel -- before
and after builds are byte-identical at every sampled frame of the same 420-frame route, including
deep inside the dome where the crystal wall fills the screen. Forcing the locked background camera
to an absurd `($123,$45)` changes nothing either. The engine's act 1 background plane contributes
no visible pixels there; what looks like the crystal wall is foreground art. Recorded in
[s3k-known-bugs](../../status/s3k-known-bugs.md) with the native capture that would settle it.

**Row 857 was already closed, and the real defect was eight rows later.** Read straight out of the
fixture, not guessed:
rows 850-870 have Player 1 rolling and airborne with `player_y_speed` climbing `$38` a frame, row
855 at `+$98` and row 856 at `-$D0`, so the frame applied gravity to reach `+$D0` and then negated
it -- the ordinary rolling-kill bounce. The `aux_state` rows name the victim: at row 856 slot 24
(`Obj_Fireworm`'s head, `$8F7A4`) becomes `loc_1E66E` inside `Obj_Explosion`, slot 25 a segment
becomes `Obj_FlickerMove` and slot 29 the flame becomes `Delete_Current_Sprite`, all in one frame.
**The engine matches that whole bounce.** The fourth handover's row 857 was measured before
`cad4a2e07` and was stale; re-measuring at `13a7c8fe8` put the exact match at rows 0-862. What the
engine got wrong was the *aftermath*: `Child_DrawTouch_Sprite_FlickerMove` -> `loc_849D8`
(sonic3k.asm:178135-178140, :178120-178125) sets each segment's own `status` bit 7, clears its
collision flags and replaces its routine with `Obj_FlickerMove`, and each flame's
`Child_DrawTouch_Sprite` (:178053-178058) then deletes it. The engine answered only the collision
flags, so four `collision_flags $98` flames outlived the worm and one hurt Player 1 on row 863.
Deleting the flames alone moved it only to row 871, because a segment still inside its
`word_8F940` wait went on to grow a *new* flame; the routine has to stop as well. With both halves
the route matches **exactly for native rows 0-2322** and the new first divergence is row 2323,
unattributed.

### 2026-09-18 - The dome background is occluded, not mis-windowed

The slice 5 handover left the dome lock with no clip and an open question: the engine's act 1
background plane draws no visible pixels, proved only by an absurd-offset ablation. The working
hypothesis was SSZ's cloud-window defect (`7eae9918d`: `SwScrlSsz.getBgCameraX()` reporting a fixed
`$1C00` window plus `Sonic3kZoneFeatureProvider.bgWrapsHorizontally()`), where the right pixels were
sampled from the wrong layout columns. **It is not that.** The evidence:

- Lava Reef is not in `bgWrapsHorizontally()`, so `applyBackgroundTilemapWindowSelection` pins
  `bgTilemapBaseX` to 0 and the shader wraps at the 512px period. The act 1 background layout
  (decoded, Sonic) is a clean periodic tiling: row 0 `E9 E8` repeating, rows 1-3 four-chunk repeats
  `F6/EC/ED/EA/EB`, `EF/F0/F1/EE`, `F3/F5/F4/F2`, all in columns 0-13, row 4 back to row 0's pair.
  Both the locked (`$561,$C5`) and unlocked (`$34C,$109`) background cameras land on populated
  columns, and the absurd ablation `($123,$45)` shifts by 62px horizontally and 128px vertically --
  neither a multiple of 512 -- so a visible plane B would have moved. The window is not the problem.
- The foreground is. `com.openggf.tools.PlaneOpacityProbe` walks the decoded layout, blocks, chunks
  and patterns (ROM data; no renderer, no camera, no frame) and counts pixels through which plane B
  could show. At six 320x224 camera positions spanning the dome -- `($1900,$800)`, `($1B00,$800)`,
  `($1D00,$880)`, `($1E00,$900)`, `($2200,$900)`, `($22C0,$780)` -- **0 of 71 680 pixels** are
  see-through. Act-wide: 48 234 496 layout pixels, of which only 116 645 (0.24%) are transparent
  inside populated chunks, in 2 299 16x16 cells bounded by `($900,$30)`-`($2850,$950)`, all thin
  ceiling and floor seams. The longest run near the dome is one 16px row at `y=$630`, ~500px above
  region 0's floor and outside a 224px viewport there. The crystal wall seen inside the dome is
  foreground art.
- Pinned by `TestS3kLrzForegroundOpacity`: the dome assertion (`seeThroughPixels == 0`) ships beside
  an Angel Island act 1 control asserting `> 0` through the same code, so a probe that could not
  disagree fails instead of passing silently. 2 tests, 0 failures, 0 skips.

What this does **not** establish is the ROM's own plane B at those coordinates. The engine decodes
the same ROM data and an index-0 plane A pixel is the only way plane B shows on hardware, so the
arithmetic carries over, but no native capture has been taken. The removal condition in
[s3k-known-bugs](../../status/s3k-known-bugs.md) is now a native plane-B-toggled capture at
`($1E00,$900)`: agreement closes the entry as correct behaviour, disagreement reopens it as a
chunk/pattern decode defect. Slice 5 still has no clip, and that is the reason.

A separate, unobservable fidelity gap found while reading: `LRZ1_BackgroundInit` (:115231-115241)
copies background row 0's pointer over rows 7 to 31 (`move.w #$1C,d1` / `moveq #$19-1,d2`, 4-byte
rows). The engine implements only the Knuckles `$F6` write from that routine; its decoded rows 5-31
keep whatever the layout held (zeros plus stray `FF` at row 7 column 27 and `78`/`1F` at rows 18-19
columns 56-63). Because act 1's background camera Y is `Camera_Y_pos_copy / 8` it never reaches row
7, so nothing observable depends on it; `Sonic3kSOZEvents.initializeScreen` shows the one-loop shape
if a later slice needs it.

### 2026-09-18 - Slice 6, part one: the miniboss object

**The load-bearing ROM correction.** `CreateChild8_TreeListRepeated` (sonic3k.asm:177181-177203)
steps its child-subtype counter with `addq.w #2,d2` -- by **two**. `loc_78562` makes two rings of
twelve, so each ring runs subtypes `0, 2, ... $16`, and `loc_7880A`'s dispatch (`0` -> arm segment,
`$16` -> firing hand, anything else -> arm link) yields one segment, ten links and one hand per
ring. Read the counter as stepping by one and the same code gives two segments, twenty-two links
and **no hands at all** -- and the hands are the only children that shoot and the only ones with
their own `collision_property`, so the whole fight would be inert. That is what
`TestLrzMinibossInstance.initCreatesTwoRingsOfOneSegmentTenLinksAndOneHand` exists to hold. It was
red (24 expected, 0 observed) before the children registered, and three of its four assertions were
red before the fix.

Two further readings worth keeping:

- The links are **not** independent orbiters. `MoveSprite_CircularSimple` (sonic3k.asm:177668-177684)
  anchors on `parent3(a0)`, which the create loop sets to the *previous child*, so the ten links are
  an articulated chain. Its `asr.l d2` with `d2 = 4` makes each link a 16-pixel step. The engine
  resolves that predecessor positionally from the parent's child list rather than storing an object
  reference, so nothing here needs an `ObjectRefId` sidecar across a rewind capture.
- The arm segment does not hang from the boss at all: `sub_78BEE` puts it at `Camera_X + $20`
  (`+ $120` mirrored) and `Camera_Y + $1B8`, i.e. pinned to the screen edges while the drill moves.

**Landed.** `LrzMinibossInstance` with the full `off_7854C` routine table and the `$34(a0)`
continuation chain (`loc_785EA` -> `78606` -> `786A6` -> `786BC` -> `786EA` -> `7873A` -> `787AE`,
including `loc_787D8`'s collapse of the hover from `$15F` to `$1F` once both hands are dead),
`sub_7867C`'s sixteen-frame re-aim with its 8px deadband, `Swing_Setup1`/`Swing_UpAndDown`, and the
three child shapes plus the hand's projectile. Art wired through `Sonic3kObjectArtKeys.LRZ_MINIBOSS`
/ `Sonic3kPlcArtRegistry` on `Map_LRZMiniboss` (`$186EC8`) over `ArtTile_LRZMiniboss` (`$3FB`),
palette line 1, high priority. Registered on SKL `$9D` bound to `ZONE_LRZ`.

**Census: 0 / 188 / 8.** Every act 1 placement now builds a concrete class. `PLACEHOLDER_BASELINE`
takes an empty spec for LRZ1 and `parseBaseline` handles it; the ratchet still only goes down.

**Not done, and not to be mistaken for done.** This is the object, unit-tested. It has had **no
motion verification, no clip, no rewind spot on a route and no native comparison**, and the fight
cannot yet end:

| Owed | ROM |
| --- | --- |
| The hit path: the shared touch response does not yet reach `sub_78C14` (parent) or `sub_78CF4` (hand), so `takeHit()` is only driven by the test | `sub_78C14`, `sub_78CF4` |
| The `FixBugs = 0` flash. Both hit paths take `2*2` where the fixed branch takes `2*6`, so the shipped ROM flashes from the wrong half of `word_78CB2`. Model the shipped branch and comment it | `sub_78C98`, `word_78CA6`, `word_78CB2` |
| `SolidObjectFull d1=$33 d2=4 d3=0` during the slam, and `Displace_PlayerOffObject` on recovery | `loc_7871A`, `loc_7873A` |
| The defeat chain: `Wait_FadeToLevelMusic`, `Child6_CreateBossExplosion`, `BossDefeated_StopTimer`, then `loc_787E0` -> eleven debris + `Obj_EndSignControl` | `loc_78C60`, `loc_787E0`, `ChildObjDat_78D9E` |
| Per-child retirement on a `$2C - 2*subtype` delay | `sub_78B46`, `loc_78B86` |
| The post-defeat palette rotation and the two camera-release waiters | `loc_78AA8`, `loc_78B00`, `loc_78B08`, `loc_78AE6`, `word_78EAA` |
| Results, `Events_fg_5` -> stage `$C` -> the seamless `$901` change, `LRZ2_BackgroundEvent` stages 0/4 | `loc_56BD2`, `loc_56CAA`, `loc_5700C`, `loc_57040` |

The debris data is fully decoded and needs no further ROM reading: `ChildObjDat_78D9E`'s eleven
offsets are `(0,-$C) (-$19,-$C) ($19,-$C) (-$C,$22) ($C,$22) (-8,$36) (8,$36) (-$12,4) ($12,4)
(-$12,$C) ($12,$C)`, frames come from `RawAni_78A9C` = `$C $C $C $11 $11 $12 $13 $D $E $F $10`, and
`Set_IndexedVelocity` with `d0 = $5C` selects `Obj_VelocityIndex` entries 23-33 (sonic3k.asm:179204-179214),
i.e. `(0,-$100) (-$100,-$100) ($100,-$100) (-$200,-$100) ($200,-$100) (-$200,-$200) ($200,-$200)
(-$300,-$200) ($300,-$200) (-$300,-$300) ($300,-$300)`. `word_78D7E` gives them priority `$80`,
size `$18 $14`, mapping frame `$C`. `GravityDebrisChild` and `CnzMinibossDebrisChild` are the shape
to copy.

Palettes: `Pal_LRZMiniboss1` `$78E0A` (line 1 at setup), `Pal_LRZMiniboss2` `$78E2A`
(`sub_78B38` copies `$40` bytes to line 3), `Pal_LRZMiniboss3` `$78E6A` and `Pal_LRZ2` `$A96DC`
(`loc_78B08`, `$20` bytes over line 2). All in `Sonic3kConstants`.

### 2026-09-18 - The miniboss travels the other way, and the first test that said so was worthless

Re-reading `Obj_LRZMiniboss` after committing it found a real inversion in the commit itself.
`loc_78562` sets `_unkFAB0 = $7A8` and `_unkFAB2 = y_pos`, and the natural reading is "floor" and
"ceiling". It is the other way round. `loc_78606` starts that leg with `y_vel = -$400`, so the
drill **climbs**, and `loc_78628`'s `cmp.w y_pos(a0),d0 / blo.w` (sonic3k.asm:160108-160110)
computes `d0 - y_pos` and returns while `$7A8` is the lower of the two -- that is, while the drill
is still below the top of its travel. `_unkFAB2`, the spawn height, is the **bottom** it falls back
to after each slam, which `loc_78768`'s `bhi.s` (sonic3k.asm:160219-160222) confirms in the
opposite direction. The shipped code had the compare reversed; with a spawn above `$7A8` the boss
snapped on its first frame, and below it, it never arrived. Fixed, with `FLOOR_Y`/`ceilingY`
renamed to `TRAVEL_TOP_Y`/`travelBottomY` so the names stop arguing with the ROM.

**The first regression test for it passed against the broken code.** `theDrillClimbsToTheTravelTop`
asserted the endpoint (`y == $7A8`), that motion was upward, and that the boss had moved -- and an
inverted compare satisfies all three, because snapping from `$900` to `$7A8` in one frame *is* an
upward move ending at `$7A8`. Endpoint assertions cannot distinguish a climb from a teleport. The
replacement, `theDrillClimbsToTheTravelTopOneStepAtATime`, asserts that the first moved frame lands
*strictly between* the spawn height and `$7A8`, and that more than fifty intermediate frames occur
(`-$400` is four pixels a frame, so `$158` takes about 86). Re-broken on purpose, it now fails with
`it did land on 7a8`.

Worth carrying forward as a habit, not just a fix: a test written from the same misreading as the
code will agree with it. Breaking the code on purpose is the only step that catches that, and it
has to be done *after* the test is written, not instead of writing one.

## Handover, 2026-09-18

**Committed on `feature/ai-lrz-bring-up`** (base develop `035e48a58`): `d2c58f148` slice 3b,
`b789c0006` + `42b016940` + `7e2ea0572` its evidence and clips, `b79e84b00` the shot palette fix,
`cf31acb88` rewind spots and the cold route, `9b0608d96` + `f75a47ae5` the corkscrew, `6ab6aef8d`
the located divergences. Tree clean; nothing pushed or merged.

**Census: 170 / 255 / 8** placeholders of 609 / 455 / 35 (was 239 / 281 / 14 at `035e48a58`).

**Rows that remain in slice 3**, all act 1 unless noted:

| Sub-slice | Rows | Placements |
| --- | --- | ---: |
| 3a remainder | `$16` wall ride | 1 + 1 act 2 |
| 3c | `$17` (11), `$18` five subtypes (15), `$1B` eleven subtypes (27), `$1F` three subtypes (7), `$20` three subtypes (11 act 1, 14 act 2), `$21` ten subtypes (15), `$22` two subtypes (6) | 92 act 1, 14 act 2 |
| 3d | `$9C` subtypes 0 and 2 | 2 |

`$16 Obj_LRZWallRide` is the cheapest next class: it shares `sub_42636` and the whole
capture/ride/eject shape with `$15`, which is now implemented and tested, and its only real
difference is that `status` bit 0 selects a leftward ride with the speed floor at `-$400`
(sonic3k.asm:87693-87786). The read-ahead for both is in the slice 3b entry above.

**The route's first blocker is not a missing class.** It is phase: at x 1909 the engine is grounded
at y 1260 where native is near 1198, there is no placed object anywhere in x 1830-2150 between
y 1100 and 1500, and the vertical climb past x 1877 needs jumps that land where native lands. The
two divergences in the [trace frontier log](../../status/trace-frontier-log.md) - the falling intro
one frame late from frame 3, and `$31 Obj_LRZCollapsingBridge` eight frames late at frame 156 - are
the cause, and closing them is worth more to the route than any further hand-authored input.

**Media.** Clips `09` (rock sprites re-cut), `10` (button opens door), `11` (big door), `12`
(shooting trigger and door), `13` (cold route opening the door), `14` (corkscrew) in
`~/Videos/OGGF/lrz-bring-up/`, with every `raw-NN-*` kept and all route and demo inputs under
`inputs/`.

**Owed on what has landed:** wide-viewport and donor rows for every slice 3a/3b class, a cold-route
spot for `$1A` and `$1D`, `sub_42EC0` exercised on a route rather than only in a unit test, act 2's
door and button skins exercised at all, and the dash elevator's mid-ride rewind spot.

## Handover, 2026-09-18 (second)

**Committed on `feature/ai-lrz-bring-up`** (base develop `035e48a58`), on top of the first
handover's commits: `d38a4aa34` `$17` sinking rock, `40c8fbee1` the withdrawn divergences,
`94f72378e` `$16` wall ride, `c01b86106` `$18` falling spike, `366589663` `$1B` fireball launcher
plus the `MoveSprite2` fix, `99688145b` `$1F` lava fall, `21fbec7e6` `$20` swinging spike ball.
Tree clean; nothing pushed or merged.

**Census: 98 / 240 / 8** placeholders of 609 / 455 / 35 (was 170 / 255 / 8 at the first handover,
239 / 281 / 14 at `035e48a58`).

**Measurements at this head.**

| What | Command | Result |
| --- | --- | --- |
| `TestS3kSonicTailsLrzSegmentTraceReplay` | `maven_queue.py -Dmse=off -Ptrace-segments -Dtest=… -Ds3k.rom.path=<worktree>/s3k.gen test` | 7174 errors, first error frame 208 `tails_y_speed` (7191 at `9744c58de`; first error unchanged) |
| Mandatory S3K + LRZ/HPZ/SOZ + rewind batch | `-Dtest=TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestLrz*,TestS3kLrz*,TestS3kHpz*,TestS3kSoz*,TestEveryObjectRewindRoundTrip,TestRewindHarnessCoverageRatchet` | 1499 tests, 0 failures, 0 skips |
| Structural guards | `-Pguards test -B` | 669 tests, 0 failures |
| Cold act 1 route on the recorded input | `GameplayCaptureTool --main sonic --sidekick tails --settle 1 --frames 12000` | exact `(x, y)` match for frames 0-636; open-loop reach **x 4301** (was 2779 before the hazards, 2357 before the alignment fix) |

This is focused validation, not a suite pass: the change-based runner has not been run against
`035e48a58` for this branch.

**Rows that remain in slice 3**, all act 1 unless noted:

| Sub-slice | Rows | Placements |
| --- | --- | ---: |
| 3c | `$21` ten subtypes, `$22` two subtypes | 15 + 6 |
| 3d | `$9C` subtypes 0 and 2 | 2 |

**Read-ahead, so the next agent does not re-derive it.**

- **`$21 Obj_LRZSmashingSpikePlatform`** (sonic3k.asm:88538-88651, ROM `$433E8`). Not read in
  detail this round.
- **`$22 Obj_LRZSpikeBall`** (sonic3k.asm:88838-…, ROM `$436E8`). Two shapes. Subtype 0 goes
  straight to `loc_4397E` with `collision_flags $8F` and the priority bit set: the static big
  spike. Subtype `$C0` runs `loc_437FE`, a swinging boulder whose X is
  `$44(a0) + (cos(angle) >> 2)` and whose collision and priority are switched off for the half of
  the circle where `angle` is non-negative (`andi.w #drawing_mask,art_tile` at :88868). Standing on
  it with `$30(a0)` set and the angle equal to the subtype starts a roll: routine `loc_4389E`,
  `x_vel -$400`, then `MoveSprite2` with `ObjCheckLeftWallDist_Part2` and `ObjCheckFloorDist` in
  the loop. The rolling half needs terrain queries the engine has (`ObjectTerrainUtils`), so the
  work is size rather than novelty.
- **`$9C Obj_LRZRockCrusher`** (sonic3k.asm:196988-197400, ROM `$900E4`) is **a slice on its own**,
  not a 3c-sized object. Its parts, all enumerated and located:
  - Init picks `word_901B8` or `word_901C4` by subtype for the new camera limits, stores the four
    current ones in `Camera_stored_*`, runs `SetUp_ObjAttributes` from `ObjDat_LRZRockCrusher`
    (`Map_LRZRockCrusher`, `make_art_tile(ArtTile_LRZRockCrusher,1,0)`, priority `$180`,
    `$80/$40/0/$10`), sets `y_radius $40` and `collision_property -1`, creates an
    `Obj_SpriteMask` child positioned per subtype, queues `ArtKosM_LRZRockCrusher`, loads
    `PLC_BossExplosion`, loads `Pal_LRZRockCrusher` on line 1, and creates the eight-piece
    `ChildObjDat_90626` hit child ring.
  - Five parent routines (`off_901EA`): wait for the camera to reach the stored limits and then
    allocate the `loc_90502` timer child; rumble and creep downward one pixel a frame with
    `ObjCheckFloorDist`; a `$27` countdown; `MoveSprite` until `y_pos` passes `word_902EC`; and the
    explosion tail that restores `ArtKosM_FirewormSegments`, `ArtKosM_Iwamodoki` and `Pal_LRZ1`.
  - `sub_905A8` is the boss-style hit flash (`CopyWordData_3` over three palette entries).
  - `loc_90512`, the timer child, is the layout edit: subtype 0 does `st (Events_bg+$0C)`, clears
    `Screen_shake_flag` and allocates two `Obj_LRZCollapsingBridge` with `$32 = 1` at `($F00,$760)`
    and `($F80,$760)`; the other subtype does `st (Events_bg+$0D)`, allocates one at `($540,$860)`,
    restores `Camera_target_max_Y_pos` and creates `Child7_ChangeLevSize` (the four
    `Obj_*LevStart/End*Gradual` objects).
  - Everything it needs has engine precedent except `Child7_ChangeLevSize`; the `Events_bg+$0C`
    consumer in `Sonic3kLRZEvents` is written but has no writer yet, and `LrzZoneRuntimeState`
    already carries the request word so rewind captures it.

**The route's first divergence is one pixel, and it is not an LRZ object.** Frame 637, `player_y`
1322 against 1321: the ROM runs Player_1 before the sinking rock, so the jump off it uses the seat
the block gave on frame 636, while the engine sinks the block and re-seats the rider first and
applies both the sink's `+1` and the jump's `+5`. Full evidence and a kill condition are in the
[trace frontier log](../../status/trace-frontier-log.md). It belongs to the shared solid/riding
path and is out of this campaign's scope to reorder.

**Owed on what has landed:** wide-viewport and donor rows for every slice 3a/3b/3c class; a
cold-route spot for `$1A` and `$1D`; `sub_42EC0` exercised on a route rather than only in a unit
test; act 2's door, button and swinging-spike-ball skins exercised at all; the dash elevator's
mid-ride rewind spot; a rewind spot on `$18`'s landing boundary (it needs real terrain for
`ObjCheckFloorDist`); and rewind spots for `$1B`, `$1F` and `$20`, whose state is a small counter
and is covered today only by `TestEveryObjectRewindRoundTrip`.

**Media.** Clips `09`-`20` in `~/Videos/OGGF/lrz-bring-up/`, every `raw-NN-*` kept, all demo and
route inputs under `inputs/`.

## Handover, 2026-09-18 (third)

**Committed on `feature/ai-lrz-bring-up`** (base develop `035e48a58`), on top of the second
handover's commits: `f0b7a6eff` `$21` smashing spike platform and `$22` spike ball,
`cfa443e13` `$9C` rock crusher and the `LRZ1_ScreenEvent` chunk edits, `98a8c7261` the
shake-table fix, `6f6a48bab` the owed rewind spots, `72920cabc` `$9A` Iwamodoki, `07fe184d7` and
`7aece7b45` the docs and the route-attribution correction, `c45f35e74` `$9B` Toxomister. Tree
clean; nothing pushed or merged.

**Census: 21 / 197 / 8** placeholders of 609 / 455 / 35. **Slice 3 has no rows left, and slice 4
has only `$99 Obj_Fireworm` (20 act 1, 9 act 2).**

**The one row that remains in slice 4**, and nothing of slice 3 (the `$9B` and `PLCKosM_LRZ`
rows below are kept for the record of what was read):

| Row | Placements | Read-ahead |
| --- | ---: | --- |
| `$99 Obj_Fireworm` | 20 act 1, 9 act 2 | sonic3k.asm:188000 area, ROM `$8F760`. A head (`loc_8F7A4`) plus a four-segment tail (`ChildObjDat_8FA16` -> `loc_8F8F0`) and a tip (`ChildObjDat_8FA30` -> `loc_8F95C`). `loc_8F77A` waits for `Find_SonicTails` under `$80`, then `ChildObjDat_8FA0E` makes the head at `(0,-8)`. The head's routine 2 sets `$2E = 3`, `$34 = loc_8F81E` and `Set_VelocityXTrackSonic` with `d4 = -$100`; routine 6 is `Swing_UpAndDown_Count` with `$3E = $80`, `y_vel = $80`, `$40 = 8` and `$39 = 8` bursts; routine 8 turns around, flipping `$42(a0)` and re-reading `byte_8FA4D`. Segment `$2E` seeds come from `word_8F940` = `$B, $16, $21, $2C`, i.e. each segment trails the one before by `$B` frames. `DPLCPtr_Fireworm` at ROM `$8FA38` means the head needs `Perform_DPLC`, which the segments do not. |
| ~~`$9B Obj_Toxomister`~~ **done** (`c45f35e74`) | 22 act 1, 9 act 2 | sonic3k.asm from `Obj_Toxomister`, ROM `$8FD48`. The body is a main sprite with one child sprite at `y +- $18`; `sub_8FF72` plays `sfx_EnemyBreath` and creates the cloud (`ChildObjDat_90040` -> `loc_8FDBA`), whose address it keeps in `$44(a0)`. The cloud has seven puffs (`ChildObjDat_90048` -> `loc_8FE8E`), `collision_flags $D8`, `y_radius $18`, a `$6F`-frame life and then `$40` of `y_vel` until `ObjHitFloor_DoRoutine` lands it. **The player hook is `sub_8FF8C` + `sub_8FFE0`**: on a touch that is not `anim == 2` and not a bubble shield, the cloud latches the player in `$44(a0)`, its controller port in `$3E(a0)`, and routine 8 with `$2E = 59`. From then `loc_8FE50` runs `Check_LRControllerShake` (five left/right reversals inside 60 frames frees the player), pins the cloud to the player, calls `sub_8FFE0` - which removes an eighth of `x_vel` and an eighth of `ground_vel`, or of `y_vel` when airborne, every frame - and `sub_881FE`, which takes one ring a second and kills the player at zero. That is the slow-down, and it belongs in a player-state hook with its own rewind adapter. |
| `PLCKosM_LRZ` readiness | - | Already done, and worth knowing before re-deriving it: `Sonic3kPlcArtRegistry` already registers `FIREWORM_SEGMENTS`, `IWAMODOKI` and `TOXOMISTER` as `StandaloneArtEntry` rows, which is what `LoadEnemyArt`'s `PLCKosM_LRZ` (sonic3k.asm:64423-64426) queues. `ArtTile_Iwamodoki` is `$530` and `ArtTile_Toxomister` `$562`. |

**Slice 3's remaining owed rows**, carried forward because they are coverage, not content:

- Wide-viewport and S1-donor / extra-follower rows for every slice 3 class. None exists yet; the
  campaign has only 320 solo and Sonic + Tails evidence. This wants one headless production class
  in the shape of `TestFbzCompatibilityMatrix`, not per-class unit cases.
- A cold-route spot for `$1A` and `$1D`, and `sub_42EC0` exercised on a route rather than only in
  a unit test.
- Act 2's door, button and swinging-spike-ball skins exercised at all.
- A rewind spot on `$18`'s landing boundary; it needs real terrain for `ObjCheckFloorDist`, so it
  belongs in a headless level test rather than the unit harness.
- A route spot for `$9C` and `$9A`.

**Stop quoting the open-loop reach as progress.** `lrz1-native-input-route.txt` is the fixture's
own recorded input, which the native game plays through these hazards without dying, so the reach
measures how long the engine's replay survives its accumulated phase error and nothing else. At
`72920cabc` it is first hurt at frame 1102 beside the `$1B` launchers at `(1982,1008)` and
`(1789,1168)`, reaches x 4029 and dies at frame 3649 - down from 4301, purely because there are now
hazards for an off-phase run to hit. The frames 0-636 exact match is unchanged and the frame-637
shared solid/riding ordering divergence still ends the parity comparison. **Expect the reach to
keep falling as the zone fills in**, and quote the exact-match frame and the first divergence
instead. Raising it means closing frame 637, which belongs to the shared solid path and is outside
this campaign.

**Media.** Clips `09`-`26` in `~/Videos/OGGF/lrz-bring-up/`, every `raw-NN-*` kept, all demo and
route inputs under `inputs/`. `21`-`26` are after-only clips; the census is the "before". Clip
`26` shows the Toxomister present and breathing at 320, not the mist catching a player -- that
beat needs an authored approach and is owed.

## Handover, 2026-09-18 (fourth)

**Committed on `feature/ai-lrz-bring-up`** (base develop `035e48a58`), on top of the third
handover's `8129bb243`: `398c34991` the frame-637 fix, `cad4a2e07` the Fireworm, `728aee1c9` the
breadth matrix, `749bb7c08` the route rewind spots and the Fireworm restore fix, `01fb501f4`
slice 5's dome regions and lava surface. Tree clean; nothing pushed or merged.

**Census: 1 / 188 / 8** of 609 / 455 / 35. Slices 3 and 4 have no rows left. The single act 1
placeholder is `$9D`, the miniboss (slice 6).

**Measurements at this head.**

| What | Result |
| --- | --- |
| Mandatory S3K + `TestLrz*`/`TestS3kLrz*`/`TestS3kHpz*`/`TestS3kSoz*`/`TestFireworm*` + both rewind guards | 1592 tests, 0 failures, **0 skips** |
| `-Pguards test -B` | 669 tests, 0 failures |
| `TestS3kSonicTailsLrzSegmentTraceReplay` (`-Ptrace-segments`, at `398c34991`) | 7703 errors, first error frame 208 `tails_y_speed` -- frontier unchanged |
| Cold act 1 route on the fixture's own recorded input | exact Player 1 `(x, y)` for **native rows 0-856**; open-loop reach x 4029 |

Not re-measured after `398c34991`: the trace-segment total and the route (the three later commits
add objects and tests, not shared physics). Re-measure before quoting them with a newer commit.

**The route's first divergence is now native row 857**, `player_y` 1228 against the engine's 1230,
`player_x` 1635 in both. This is the exact `player_y_speed` sign negation the earlier entries
predicted near `($663,$4CC)`: the fixture's speed flips from `208` to `-208` in one row while the
engine keeps falling. No `$17`-family placement lies there, so the owner is terrain or a
dynamically spawned object, and it has **not** been identified. That is the next thing worth
measuring for the route.

**What slice 5 still needs** (the background half; the state half is done and tested):

| Owed | ROM |
| --- | --- |
| `Events_routine_bg` stage machine 0 -> 4 -> 8 -> 0 | `LRZ1_BackgroundEvent_Index` (:115264-115271), `loc_56C28`, `loc_56C6E`, `loc_56C88` |
| The lock's background tail | `loc_56E40`: `Reset_TileOffsetPositionEff`, `addq.w #4,(Events_routine_bg)`, then `loc_56C76` = `DrawBGAsYouMove` + `PlainDeformation` + `ShakeScreen_Setup` |
| `SwScrlLrz`'s locked mode | while stage 4, `sub_56DAC` replaces `LRZ1_Deform` and `PlainDeformation` replaces `ApplyDeformation`. `LrzDomeRegions.lockedBackgroundX/Y` already carry the arithmetic |
| The release's bottom-up refresh | `loc_56E66`: save `Camera_X/Y_pos_BG_copy` into `Events_bg+$02/$04`, run `LRZ1_Deform`, `HScroll_table+$006 = d2`, `Draw_delayed_position = (d0 + $E0) & Camera_Y_pos_mask`, `Draw_delayed_rowcount = $F`, then stage 8's `Draw_PlaneVertBottomUpComplex`, which clears `Events_bg+$02` and the routine word when it returns negative |
| Knuckles `$F6` background chunk | `LRZ1_BackgroundInit` |
| Native probe on the switch frame | question-led, `bizhawk-native-reference-capture`, savestate on pass 1, no `print()` |
| Rewind spot inside a region, and a clip | numbered on from `29` |

**Owed elsewhere, carried forward.**

- `$1D` / `sub_42EC0` exercised on a route. A spindash into the `($94B,$4A7)` placement from
  `($9B8,$4B4)` did not latch it; the other placement is `($19C8,$6D9)` with a route row at
  `($19B4,$723)`.
- Cold-route (rather than route-position) rewind spots, and a route spot for `$99`.
- Act 2 placements exercised on an act 2 route: the matrix asserts their art is ready, nothing more.
- The engine-level rewind restore gap in [s3k-known-bugs](../../status/s3k-known-bugs.md); closing
  it restores the whole-composite assertions in `TestS3kLrzRouteRewindSpots`.
- No change-based `run_categories.py --base 035e48a58 --run` has been run for this branch. Every
  number above is focused validation.

**Media.** Clips `09`-`28` in `~/Videos/OGGF/lrz-bring-up/`, every `raw-NN-*` kept, all demo and
route inputs under `inputs/`.

## Handover, 2026-09-18 (fifth)

**Committed on `feature/ai-lrz-bring-up`** (base develop `035e48a58`), on top of the fourth
handover's `b575a5f13`: `73f78efb2` the rewind probe constructors and the Fireworm flame sidecar,
`75d607383` slice 5's background half. Tree clean; nothing pushed or merged.

**Census: 1 / 188 / 8** of 609 / 455 / 35, unchanged. The single act 1 placeholder is `$9D`, the
miniboss. **Slices 0-5 are complete.**

**Measurements at this head.**

| What | Result |
| --- | --- |
| Mandatory S3K + `TestLrz*`/`TestS3kLrz*`/`TestS3kHpz*`/`TestS3kSoz*`/`TestS3kDdz*`/`SwScrlLrzTest`/`TestFireworm*` + both rewind guards | 1654 tests, 0 failures, **0 skips** |
| `-Pguards test -B` | 669 tests, 0 failures, re-run at `82a790ba4` |
| Cold act 1 route, `GameplayCaptureTool --main sonic --sidekick tails --settle 1` on the fixture's own recorded input | exact Player 1 `(x, y)` for **native rows 0-2322**; first divergence row 2323, `player_y` 390 against 399 |

Not re-measured at this head: `TestS3kSonicTailsLrzSegmentTraceReplay`. This is focused validation; no `run_categories.py --base 035e48a58 --run` has been run for
this branch.

**Slice 6 read-ahead, from a full ROM pass this round, so the next agent does not re-derive it.**

`Obj_LRZMiniboss` (sonic3k.asm:160001-160900, ROM `$78500`). Roughly 900 lines and six distinct
classes; treat it as a slice, not an object.

- **Parent.** `Check_CameraInRange` on `word_784E0`, `sub_85D6A` with `word_784E8`,
  `mus_Miniboss` into `boss_saved_mus`, `Pal_LRZMiniboss1` on line 1, then the standard boss
  wrapper at `loc_78522`/`loc_78528` (`Pal_LRZMiniboss2` through `sub_78B38`, which copies `$40`
  bytes to `Normal_palette_line_3`). `off_7854C` is 11 slots with 9 distinct handlers
  (`loc_785E4` = `Obj_Wait` three times). `loc_78562` sets `collision_property 6`,
  `_unkFAB0 = $7A8` (the floor it descends to) and `_unkFAB2 = y_pos` (the ceiling it returns to),
  then two `CreateChild8_TreeListRepeated` rings of 12: `ChildObjDat_78D84` -> `loc_7880A` and
  `ChildObjDat_78D8A` -> `loc_787FE`.
- **Cycle.** `loc_78592` waits on `Nem_decomp_queue`, queues `ArtKosM_LRZMiniboss` and
  `PLC_BossExplosion`, `$2E = $2F`. Then a descend/hover/slam/rise loop driven by `$34(a0)`
  continuations: `$2E = $15F` hovering (`bset #3,$38`), `-$400` rise, `MoveSprite2` down to
  `_unkFAB0`, `Swing_Setup1` + `Swing_UpAndDown` for `$BF`, `collision_flags $B5` then `$06` with
  `Screen_shake_flag = $14` and `sfx_ThumpBoss`, `SolidObjectFull` with `d1=$33 d2=4 d3=0`, and
  `loc_7873A` returning with `y_vel $400` until `_unkFAB2`. `sub_7867C` re-aims once every 16
  frames on `V_int_run_count+3` low nibble, `Find_OtherObject` against Player 1, `x_vel` from
  `word_786A2` = `-$200/$200`, and it clears `x_vel` whenever the distance is `<= 8`.
- **Children.** `loc_7880A` dispatches on subtype into three shapes through `off_7882C`: the arm
  segments (`loc_78838`, five routines, riding the parent's `$38` bits 2 and 3), the orbiters
  (`loc_788DE`, `MoveSprite_CircularSimple` with `$3C` stepped by `$40` and reflected outside
  `[$70,$90]`), and the firing hand (`loc_78922`, `collision_property 4`, `byte_78E05`, fires
  `ChildObjDat_78D90` -> `loc_78A02` projectiles with `sub_78BAA`'s `word_78BCA` velocity pairs
  and a `$39` shot counter that stretches the reload to `$FFF` after three).
- **Hits.** `sub_78C14` (parent) and `sub_78CF4` (hand) are the same shape: `$20(a0)` is the
  flash counter seeded to `$20`, `sfx_BossHit`, and `sub_78C98` copies three palette words through
  `CopyWordData_6` from `word_78CB2`. **`FixBugs = 0` matters here**: both take the `2*2` offset
  rather than `2*6`, so the flash uses the wrong half of the table. Model the shipped branch and
  comment it. The hand's kill sets bit 6 or 7 of the parent's `$38` by facing, and both set gives
  the parent `$2E = $1F`.
- **Defeat.** `loc_78C60`: `Wait_FadeToLevelMusic`, `priority $80`, `$38` bits 6 and 7,
  `Child6_CreateBossExplosion`, `Displace_PlayerOffObject`, `BossDefeated_StopTimer`, continuation
  `loc_787E0` -> `ChildObjDat_78D9E` (11 debris, `loc_78A70`, `Obj_FlickerMove`,
  `Set_IndexedVelocity $5C`, frames from `RawAni_78A9C`) and `Obj_EndSignControl`. Each surviving
  child retires through `sub_78B46` on a per-subtype delay `$2C - 2*subtype`.
- **`word_78EAA` is the post-`End_of_level_flag` palette rotation**, exactly as the verified table
  says: `loc_78AA8` only starts once `End_of_level_flag` is set, installs `Palette_rotation_custom`
  = `loc_78B00`, `Palette_cycle_counter1 = $7FFF`, and allocates `loc_78B08`. Those two are the
  camera-release pair: `loc_78B08` waits for `Camera_X_pos >= $2C0`, then sets
  `Camera_min_X_pos = $2C0`, copies `Pal_LRZ2` over `Normal_palette_line_2` and
  `Pal_LRZMiniboss3` through `sub_78B38`; `loc_78AE6` waits for `$940`, sets
  `Camera_min_X_pos = $940`, clears the cycle counter and deletes itself.

`loc_56CAA`, the seamless `$901` change (sonic3k.asm:115347-115373), read in full:

- Gated on `Kos_modules_left` being zero; until then it falls to `loc_56D16`, the ordinary act 1
  draw tail, so the act keeps rendering while the queue drains.
- Then, on one frame: `Current_zone_and_act = $901`; `Dynamic_resize_routine`,
  `Object_load_routine`, `Rings_manager_routine`, `Boss_flag` and `Respawn_table_keep` cleared;
  `Clear_Switches` (sonic3k.asm:104284-104291) clears exactly `$20` bytes from
  `Level_trigger_array` -- `bytesToLcnt($20)` is `$20/4-1 = 7`, and a `dbf` from 7 runs eight
  times over longs -- which is the trigger array **and** the `Anim_Counters` that share those 32
  bytes;
  `LRZ_rocks_routine` cleared; `Load_Level`; `LoadSolids`.
- The `-$2C00` rebase: both players' `x_pos`, then `Offset_ObjectsDuringTransition`
  (:104166-104181), which walks `Dynamic_object_RAM+object_size` to `Breathing_bubbles` and shifts
  **only** objects whose `render_flags` bit 2 is set, then `Camera_X_pos`, `Camera_X_pos_copy`,
  `Camera_min_X_pos` and `Camera_max_X_pos`. `d1` is zero, so nothing moves vertically.
- Finally `Reset_TileOffsetPositionActual` and `clr.w (Events_routine_bg)` -- the background stage
  machine is reset to 0 by the act change, which is why stage `$C` is not this campaign's dome
  stage and why `LrzBackgroundStageMachine` deliberately does not own it.
- `loc_56BD2` is the frame that *requests* it: `Events_fg_5` non-zero queues
  `LRZ2_128x128_Secondary_Kos`, `LRZ2_16x16_Secondary_Kos`, `ArtKosM_LRZ2_Secondary` at
  `tiles_to_bytes($090)` and PLC `$30`, then sets `Events_routine_bg = $C`.

**Owed, carried forward.**

- Slice 6 in full: the miniboss, `Obj_EndSignControl`, results, the seamless change beside
  `SozActTransitionHandoff`, `LRZ2_BackgroundEvent` stages 0/4, the independent review of the
  coupled boundary, and timeline isolation across the act change.
- `$1D` / `sub_42EC0` exercised on a route. A spindash into the `($94B,$4A7)` placement from
  `($9B8,$4B4)` did not latch it; the other placement is `($19C8,$6D9)` with a route row at
  `($19B4,$723)`.
- Cold-route (rather than route-position) rewind spots, and a route spot for `$99`.
- Act 2 placements exercised on an act 2 route.
- Native row 2323, the new first route divergence, unattributed ([trace frontier log](../../status/trace-frontier-log.md)).
- `loc_849D8`'s `Set_IndexedVelocity` `d0` for a retired Fireworm segment, so its debris flies.
- The dome background's invisibility, in [s3k-known-bugs](../../status/s3k-known-bugs.md); until it
  is settled, slice 5 has no clip.
- No change-based `run_categories.py --base 035e48a58 --run` for this branch.

**Media.** Clips `09`-`28` unchanged. New raw captures kept as evidence for the dome finding, not
as clips: `raw-38-lrz1-dome-lock`, `raw-38-lrz1-dome-lock-before`, `raw-39-lrz1-dome-lock-after`,
`raw-39-lrz1-dome-lock-before`, with their inputs.

### 2026-09-18 - The arm chain anchors by identity, and the first test for that was tautological too

`MoveSprite_CircularSimple` anchors each arm link on `parent3(a0)`, which the create loop sets to
the previously created child. The first implementation resolved that as "my immediate neighbour in
the parent's child list", which is wrong in a way that only bites later: the engine prunes
destroyed children from that list, and `sub_78B46` retires every child one at a time at defeat. The
ROM's `parent3` is a stored pointer and never re-aims.

The hazard is narrower than it first looks, and getting that right mattered. Removing an *unrelated*
child shifts a link and its neighbour together, so the positional lookup survives. It does **not**
survive removing the anchor itself: ring two's first link then takes whatever slid into that slot,
which is ring one's hand, wiring the two arms into one chain. Resolving by `(ring, subtype - 2)`
instead resolves to nothing and falls back to the boss, which is the safe reading. `LrzMinibossRingChild`
carries that identity.

**The first test for this passed against the positional code**, for two separate reasons stacked on
each other: it removed an unrelated child (which, per the above, is harmless either way). Only after
working out *which* removal actually leaks did the test fail on the broken code -- with exactly the
right diagnosis, `LrzMinibossHandChild`. That is the second test in this slice to need a deliberate
break before it was worth anything, and in both cases the break did not merely confirm the test: it
corrected my understanding of the defect.


## Handover, 2026-09-18 (sixth)

**Committed on `feature/ai-lrz-bring-up`** (base develop `035e48a58`), on top of the fifth
handover's `a7b9de1ef`: `fd0d769bf` the dome-background finding and `PlaneOpacityProbe`,
`9020c502f` the miniboss object, `17ccb4e72` its travel-direction fix. Tree clean; nothing pushed
or merged.

**Census: 0 / 188 / 8** of 609 / 455 / 35. **Act 1 has no placeholders left** -- every placement in
the act builds a concrete class.

**Measurements at this head (`17ccb4e72`).**

| What | Result |
| --- | --- |
| Four mandatory S3K classes + `TestLrz*`/`TestS3kLrz*`/`TestS3kHpz*`/`TestS3kSoz*`/`TestS3kDdz*`/`SwScrlLrzTest`/`TestFireworm*` + both rewind guards | 1666 tests, 0 failures, **0 skips** |
| `-Pguards test -B` (at `9020c502f`; `17ccb4e72` touched no registry or annotation surface) | 669 tests, 0 failures |
| `TestS3kLrzForegroundOpacity` | 2 tests, 0 failures, 0 skips |

Not re-measured at this head: the cold act 1 route and
`TestS3kSonicTailsLrzSegmentTraceReplay`. The miniboss sits far past the route's frontier, but the
route number in the fifth handover is now stamped to an older commit -- **re-measure before quoting
it**. This is focused validation; no `run_categories.py --base 035e48a58 --run` has been run for
this branch.

**(A) is answered, and the answer was not the expected one.** The dome background lock is not an
SSZ-style window defect. Lava Reef act 1's *foreground* plane is opaque wherever the camera sits in
or around the dome, so no plane B pixel can reach the screen there whatever `SwScrlLrz` computes:
0 of 71 680 see-through pixels at each of six dome viewports, and act-wide only 116 645 of
48 234 496 layout pixels (0.24%) transparent inside populated chunks. Measured from ROM data with
the new `com.openggf.tools.PlaneOpacityProbe`, pinned by `TestS3kLrzForegroundOpacity` with an
Angel Island control. The [s3k-known-bugs](../../status/s3k-known-bugs.md) entry is rewritten and
its removal condition is now a native plane-B-toggled capture at `($1E00,$900)`: agreement closes
it as correct behaviour, disagreement reopens it as a chunk/pattern decode defect. **Slice 5 still
has no clip, and now the reason is understood rather than merely observed.**

**Slice 6 is part done.** The miniboss object is in, registered, art-wired and unit-tested, and the
fight's *shape* is right. It cannot yet end. What is owed, with the ROM label for each, is the
table in the slice 6 evidence-log entry above; the debris offsets, frames and `Obj_VelocityIndex`
entries and all four palette addresses are already decoded there, so none of that needs re-reading.
The largest remaining pieces in order: the hit path into `sub_78C14`/`sub_78CF4` (without it
nothing can damage the boss), the `loc_78C60` defeat chain, then results and the `loc_56CAA`
seamless `$901` change, then `LRZ2_BackgroundEvent` stages 0/4.

**Two method notes worth more than the code.**

- A test written from the same misreading as the code will agree with it. The first travel-direction
  test passed against an inverted comparison because it asserted only the endpoint, and an
  inverted compare reaches the same endpoint in one frame. Breaking the code on purpose *after*
  writing the test is what caught it. The same pattern is why `TestS3kLrzForegroundOpacity` ships
  with an Angel Island control rather than the dome assertion alone.
- A byte-identical before/after capture cannot separate "wrong pixels drawn" from "no pixels
  reachable". When a background change produces one, reach for `PlaneOpacityProbe` before reaching
  for another capture.

**Owed elsewhere, carried forward unchanged.**

- `$1D` / `sub_42EC0` exercised on a route. A spindash into the `($94B,$4A7)` placement from
  `($9B8,$4B4)` did not latch it; the other placement is `($19C8,$6D9)` with a route row at
  `($19B4,$723)`.
- Cold-route (rather than route-position) rewind spots, and a route spot for `$99`.
- Act 2 placements exercised on an act 2 route.
- Native row 2323, the route's first divergence, unattributed, and measured before this head.
- `loc_849D8`'s `Set_IndexedVelocity` `d0` for a retired Fireworm segment.
- The direct-`$901` act 2 background art gap in s3k-known-bugs; the seamless path is the one
  expected to make act 2 correct, so it stays open until the transition lands.
- No matrix rows for the miniboss: it has no motion verification, no clip and no route rewind spot,
  so nothing may be recorded as covered for it yet.

**The independent review did not deliver a report.** A reviewer subagent was spawned for the
coupled boundary and resumed three times; it never returned findings. Its five areas were covered
by hand instead, and two of them produced real fixes (`17ccb4e72`, `8121b3dee`). Verified directly
against the disassembly and found correct: `Animate_RawMultiDelay`'s command dispatch -- `$FC`
reaches `loc_845F2`, which emits the script's base frame and reloads the timer in the same call, so
the engine's same-call restart is right, and `$7F` has bit 7 clear so it is a delay and not a
command (sonic3k.asm:177563-177613). Still unreviewed by a second pair of eyes: the routine-table
and continuation-chain constants, and oracle independence beyond the three deliberate breaks done
here. A later slice should re-spawn the review before the transition lands.

**Media.** Clips `09`-`28` unchanged; no new clip. `raw-38`/`raw-39` dome captures retained as
evidence for the foreground-opacity finding.

### 2026-09-18 - The independent review applied: seven shape defects, and ten deliberate breaks

**Items 1 and the chain-predecessor identity re-verified, not taken on trust.** `loc_78628`'s
`cmp.w y_pos(a0),d0 / blo.w` (sonic3k.asm:160109-160110) and `loc_7878C`'s `cmp.w y_pos(a0),d0 /
bhi.s` (sonic3k.asm:160220-160222) both match what `17ccb4e72` landed, and
`CreateChild8_TreeListRepeated`'s `move.w a3,parent3(a1)` with `movea.l a1,a3` at the tail of each
iteration (sonic3k.asm:177188-177191) confirms `parent3` is the previously created child, which is
what `8121b3dee` resolves by `(ring, subtype - 2)`.

**The other seven were all real, and all of one kind: the fight had the right endpoints and the
wrong shape.** Nothing was mis-timed by a whole phase; everything was collapsed, phase-shifted or
anchored to the wrong object -- which is exactly the class of defect an endpoint assertion cannot
see, and why every test added here watches a value across frames.

| Item | ROM | What was wrong |
| --- | --- | --- |
| 2 hand motion | `loc_7897A` (sonic3k.asm:160422-160446) positions the hand with `MoveSprite_AtAngleLookup` over `AngleLookup_2` anchored on `parent3` = the subtype-`$14` link; `$3C` stays `$80` so `AtAngle_80_BF` at `lo = 0` gives `(0, -$18)` | The hand inherited the shared sync-to-parent, so it rode the drill body and its shots left the drill instead of the arm |
| 3 child stagger | `sub_78BD6` (sonic3k.asm:160642-160648) parks each link and hand on `Wait_Draw` for the `$2E` `loc_7880A` gave it: `(mirrored ? $10 : 0) + subtype * 2` (sonic3k.asm:160258-160261, 160276-160279) | Absent. Both arms snapped out fully formed on one frame instead of unrolling |
| 4 raw animation phase | `Animate_RawMultiDelay`'s `addq.w #2,d0` runs *before* the read, on an `anim_frame` `Set_Raw_Animation` cleared (sonic3k.asm:177563-177579), so the first pair played is index 2 and index 0/1 is only `loc_845F2`'s restart target | The walk started at index 0, stretching every script by one pair: `byte_78DF1`'s drop was four frames and 16 px instead of three and 12 |
| 5 orbiter angle | `loc_788F4`'s out-of-window branch negates `$40` and then *falls through* to the same `move.b d0,$3C(a0)` at `loc_7890C` (sonic3k.asm:160361-160377) | The angle was recomputed after the flip, so it never reached `$6F`/`$91` and the sway was two units short at each end |
| 6 link offsets | `MoveSprite_CircularSimple` (sonic3k.asm:178424-178441): ROM sine table, `asr.l #4`, anchor read and written as longwords | `Math.sin` and a rounded pixel. The table disagrees with `Math.sin` by up to 1/16 px per joint, and truncating to pixels shortened a ten-joint chain |
| 7 projectile cull | `Sprite_CheckDeleteTouchXY` (sonic3k.asm:179032-179043): `(x & $FF80) - Camera_X_pos_coarse_back > $280` and `y - Camera_Y_pos + $80 > $200`, both `bhi` | An eyeballed `cameraX + $180` / `cameraY + $140` box, which retires shots the ROM keeps -- by up to `$7F` px in X and `$40` in Y |
| 8/9 dead state | `collision_flags` `$B5`/`6`, `SolidObjectFull d1=$33 d2=4 d3=0` at `loc_7871A`, `Displace_PlayerOffObject` at `loc_7873A` | The `$B5`/`6` transitions had no reader, the solid pass and the displace were stubs, and the hand rendered a constant frame 6 |

**The hit path, which is what the fight was missing.** `sub_78C14` (sonic3k.asm:160641-160670) is
gated on `collision_flags(a0)` being *zero*: the shared touch pass zeroes it (stowing the old value
in `$25`) and decrements `collision_property`, and only then does the object do the sound, the
`$20`-frame flash and the `$25` restore. `loc_78768` splits on `cmpi.b #6,anim_frame(a0)`, so only
the first three pairs of `byte_78DF8` keep the boss hittable on the way back down. The boss now
overrides `getCollisionFlags()` with the ROM byte and `usesBaseHitHandler()` with `false`; the
base class's unconditional `$C0 | size` had made the drill hittable for its whole cycle.

**`FixBugs = 0`, and this one is visible.** Both flash routines take `addi.w #2*2,d0` where the
`FixBugs` branch takes `addi.w #2*6,d0` (sonic3k.asm:160659-160664, 160707-160712), so
`sub_78C98` copies `word_78CB2` from word **2** -- `2, $644, $422, 0, $888, $AAA`, a window
straddling the boss's own colours and the white flash -- rather than the six white words at word 6.
The shipped ROM does not flash the miniboss white at all. Modelled as shipped and commented.

**Census fallback.** `loc_78562` creates the 24 children once, in `ROUTINE_INIT`, behind a
`childrenCreated` latch that a rewind capture carries as true. `afterRewindRestoreSettled()` now
rebuilds any child the restore did not return, from the same deterministic (ring, subtype) loop.

**Ten deliberate breaks, ten failures, each with the right diagnosis.** Nine breaks were applied
at once (stagger to zero, animation index to 0, angle recomputed after the flip, `Math.sin` for the
table, hand synced to parent, the eyeballed cull box, the base-class collision flags, the census
fallback short-circuited) and produced exactly nine failures, one per test. The tenth --
`FLASH_WORD_OFFSET_SHIPPED` set to the `FixBugs` value 6 -- needed its own run, because under the
combined break the flash test failed on the collision-flags break first and never reached its own
assertion. **A break that another break masks is not evidence.** That is worth more than the
result: a combined break run proves a test can fail, not that it fails *for its own reason*, and
the only way to tell is to check that each failure's message is the one that test exists to
produce.

**Two of the first eight tests failed against correct code, and the reason is a property of these
objects.** The stagger and the hand tests both sampled the frame on which `Obj_Wait` runs `$34(a0)`
-- and `$34` for both only *installs* the live routine, so nothing moves until the frame after.
Objects built as a `(a0)`/`$34(a0)` continuation chain are one frame later than their state flag
suggests, every time. A third failure was arithmetic in the test (the two rings' start frames
overlap on six of fourteen distinct frames, not zero), and only the fourth -- the hand sitting at
`(0,0)` because `syncPositionWithParent()` had been made a no-op without replacing the create
loop's position copy -- was a defect in the code under test.

**The idle hand is not drawn.** `loc_78946` does no positional work and ends `bra.w sub_78B46`
with no `Draw_Sprite` after it, so the hand is invisible between volleys and simply reappears
where the arm's end has reached. Easy to read as a missing draw call and "fix".

**The seamless transition's trigger chain, decoded but not implemented.** `Obj_Results`
(sonic3k.asm:62621) sets `Events_fg_5` at the tail of its routine 2, but only when
`Apparent_act == 0` and the zone is neither Angel Island nor Ice Cap. `LRZ1_BackgroundEvent`
stage 0 (`loc_56BD2`, sonic3k.asm:115274-115289) sees it, clears it, queues
`LRZ2_128x128_Secondary_Kos`, `LRZ2_16x16_Secondary_Kos`, `ArtKosM_LRZ2_Secondary` at tile `$090`
and PLC `$30`, and sets `Events_routine_bg = $C`. Stage `$C` (`loc_56CAA`,
sonic3k.asm:115347-115375) waits on `Kos_modules_left`, then does the act change in one frame.
`Clear_Switches` (sonic3k.asm:104284-104291) clears **`$20` bytes** from `Level_trigger_array`, and
the constants file puts `Anim_Counters ds.b $10` immediately after `Level_trigger_array ds.b $10`
(sonic3k.constants.asm:699-700) -- so the act change wipes the animated-tile phase counters too,
which is the same cleared-counter state the Verified ROM values row for `loc_282D0` already
describes for a direct `$901` load. `Sonic3kLevelEventManager`'s existing CNZ1 -> CNZ2 retained
`Obj_EndSignControl` and results path is the closest precedent in the engine.

## Handover, 2026-09-18 (seventh)

**Committed on `feature/ai-lrz-bring-up`** (base develop `035e48a58`), on top of `590d7ef4d`:
`d48420e24`, the review fixes plus the hit path. Tree clean; nothing pushed or merged.
**Census unchanged at 0 / 188 / 8.**

**Measurements at `d48420e24`.**

| What | Command | Result |
| --- | --- | --- |
| Four mandatory S3K classes + `TestLrz*`/`TestS3kLrz*`/`TestS3kHpz*`/`TestS3kSoz*`/`TestS3kDdz*`/`SwScrlLrzTest` + both rewind guards | `maven_queue.py -Dmse=off -Dtest=<list> -Ds3k.rom.path=... test` | **1666 tests, 0 failures, 0 skips** |
| Structural guards | `maven_queue.py -Dmse=off -Pguards test -B` | **669 tests, 0 failures, 0 skips** |
| `TestLrzMinibossInstance` alone | focused | 15 tests, 0 failures, 0 skips |
| Same, with nine deliberate breaks | focused | 9 failures, one per break |
| Same, with the `FixBugs` break alone | focused | 1 failure, the flash-window assertion |

This is focused validation. **No `run_categories.py --base 035e48a58 --run` has been run for this
branch.** `TestS3kSonicTailsLrzSegmentTraceReplay` **was** re-measured, at `51474c172`: the
frontier is unchanged at frame 208 `tails_y_speed` (expected `0x07BD`, actual `0x0000`) with 6835
errors, recorded in the [trace frontier log](../../status/trace-frontier-log.md) with the note that
the drop from 7703 belongs to the whole span since the frame-637 fix and was not attributed
further. The **cold act 1 route was not re-measured**; the sixth handover's row-2322 figure is
stamped to `17ccb4e72`, so re-measure before quoting it.

**What slice 6 still owes, in the order it has to be built.** The ROM is decoded for all of it;
the debris offsets, frames, `Obj_VelocityIndex` entries and the four palette addresses are in the
slice 6 evidence entry above and need no further reading.

1. **The defeat chain.** `loc_78C60` currently stops at "defeated": it sets `$38` bits 6 and 7,
   displaces the player and stops the timer, but nothing downstream consumes that.
   `Wait_FadeToLevelMusic` (sonic3k.asm:179659-179676) counts `$2E` out, hides the sprite, sets
   `$2E = 2*60-1`, allocates `Obj_Song_Fade_ToLevelMusic` and jumps `$34` = `loc_787E0`, which
   allocates the `loc_78AA8` palette waiter, creates the eleven `ChildObjDat_78D9E` debris and
   jumps `Obj_EndSignControl`.
2. **Per-child retirement, `sub_78B46` (sonic3k.asm:160547-160585).** Every ring child tests one
   bit of the parent's `$38` -- 6 unmirrored, 7 mirrored -- at the end of its own update. Two
   things set it: `loc_78D2C` when that ring's **hand** dies, so killing a hand retires the whole
   arm behind it, and `loc_78C60` on defeat. The child is then parked on `Wait_Draw` with
   `priority = $80` and `$2E = $2C - subtype * 2`, so the arm peels away from the hand end first;
   `loc_78B86` spawns a `Child6_CreateBossExplosion` (`S3kBossExplosionChild` exists) and deletes
   `$F` frames later. **This is currently inert**: the flag is set and nothing reads it, so today
   killing a hand does nothing visible beyond shortening the hover. A first attempt at a shared
   `LrzMinibossRetirement` holder was written and **deleted unused**: a new object-typed field on
   a boss child is a `TestRewindHarnessCoverageRatchet` risk that was not worth taking blind. Two
   `int` scalars per child, matching the existing non-final-scalar pattern in these classes, is
   the low-risk shape.
3. **The two camera-release waiters**, `loc_78AA8`/`loc_78AE6` (`Camera_min_X_pos = $940` once
   `Camera_X_pos` reaches it, `word_78EAA` rotation, `Palette_cycle_counter1 = $7FFF`) and
   `loc_78B08` (`Camera_min_X_pos = $2C0`, `Pal_LRZ2` over line 2, `Pal_LRZMiniboss3` over line 3).
   `loc_78AA8` only starts once `End_of_level_flag` is set, so it belongs after results.
4. **Results, then the seamless `$901` change.** The trigger chain is decoded in the evidence
   entry above and is the load-bearing piece: `Obj_Results` sets `Events_fg_5` at
   sonic3k.asm:62621 **only** when `Apparent_act == 0` and the zone is neither Angel Island nor
   Ice Cap; `LRZ1_BackgroundEvent` stage 0 (`loc_56BD2`, sonic3k.asm:115274-115289) consumes it,
   queues the LRZ2 secondary chunk/block/art Kos plus PLC `$30`, and sets `Events_routine_bg = $C`;
   stage `$C` (`loc_56CAA`, sonic3k.asm:115347-115375) waits on `Kos_modules_left` and then does
   the whole act change on one frame. `Clear_Switches` (sonic3k.asm:104284-104291) clears `$20`
   **bytes** from `Level_trigger_array`, and `sonic3k.constants.asm:699-700` puts
   `Anim_Counters ds.b $10` directly after `Level_trigger_array ds.b $10`, so the act change wipes
   the animated-tile phase counters as well -- the same cleared-counter state the Verified ROM
   values row for `loc_282D0` describes for a direct `$901` load, and the reason the first upload
   after the transition is skipped. `Sonic3kLevelEventManager`'s CNZ1 -> CNZ2 retained
   `Obj_EndSignControl` and results path (around lines 1860-2015) is the engine's closest
   precedent and should be read before `SozActTransitionHandoff`.
5. **`LRZ2_BackgroundEvent` stages 0 and 4** (`loc_5700C`/`loc_57040`, sonic3k.asm:115693-115722).
   Stage 0 allocates `loc_5711E` into `Events_bg+$06` -- which is the Death Egg background sprite
   the Verified ROM values row says slice 7's owner must trace before coding -- then runs
   `sub_57082`, `Reset_TileOffsetPositionEff`, `Draw_delayed_position = (d0 + $E0) & Camera_Y_pos_mask`,
   `Draw_delayed_rowcount = $F`, and advances by 4.

**Independent review.** One was spawned for the seven applied items and its findings are in the
final report for this round; it is **not** a review of the transition boundary, because the
transition is not implemented. The transition reviewer the work order asks for should be spawned
once item 4 above exists to review.

**Carried forward unchanged from the sixth handover**: the `$1D`/`sub_42EC0` route exercise, cold-
route rewind spots and a route spot for `$99`, act 2 placements on an act 2 route, native row 2323,
`loc_849D8`'s `Set_IndexedVelocity` `d0` for a retired Fireworm segment, the direct-`$901` act 2
background art gap, and slice 5's clip. **No matrix row may be recorded for the miniboss yet**: it
still has no clip, no route rewind spot and no native comparison.

**Media.** Clips `09`-`28` unchanged; no new clip this round.

### 2026-09-18 - The independent review still does not deliver, and that is now a measured fact

Three reviewer subagents have now been spawned for this campaign across two rounds -- one in the
sixth handover's round, two in this one -- and **none of them delivered a report**. All three did
real work before stopping: this round's two burned 193k and 81k tokens over 41 and 13 tool calls
respectively. Each was resumed by message (three times, three times and once) with progressively
shorter and more explicit instructions, including "no more tool calls, just write the text" and an
explicit 15-call budget with "an incomplete report delivered is worth far more than a complete one
you never send". None produced output.

**Treat an independent review as a step that may silently not happen**, and plan the round so the
work is not gated on it. The failure is invisible from inside: the spawn returns, the agent works,
and the absence only shows up as a missing report -- which looks exactly like a review that found
nothing.

**What was done instead, and what it is not.** The four highest-risk claims in `d48420e24` were
re-read against the disassembly a second time by hand and all four hold: `sub_78C14`'s gate is
`collision_flags` being **non**-zero (sonic3k.asm:160641-160670), and since `$20` seeds to `$20`,
an even number, bit 0 is clear on the first flash frame, so the shipped `2*2` really does put the
**bugged** `word_78CB2` window first; `word_78CA6`'s six `Normal_palette_line_2` byte offsets
`$06 $08 $10 $18 $1A $1C` are colour indices 3, 4, 8, 12, 13, 14 (sonic3k.asm:160685-160687);
`loc_78768`'s `cmpi.b #6,anim_frame(a0) / bhs.s` puts `sub_78C14` on the `< 6` side and
`sub_78CCA` plus `clr.b collision_flags(a0)` on the `>= 6` side (sonic3k.asm:160218-160226); and
`MoveSprite_AtAngleLookup` with `$3C = $80` selects `AtAngle_80_BF` through `lsr.w #5 / andi.w #6`
and reads `AngleLookup_2[0] = 0` and, via `not.w d0` against `a3 = a2 + $40`,
`AngleLookup_2[$3F] = $18`, both negated, giving `(0, -$18)` (sonic3k.asm:178504-178562,
201856-201859) -- and the Java switch's four sign patterns match the four `AtAngle_*` routines.

**This is a second reading, not a second pair of eyes.** It catches a transcription slip; it cannot
catch a misreading the same author would make twice, which is the specific failure the deliberate
breaks exist for and the reason the work order asks for a reviewer at all. The routine-table and
continuation-chain constants remain unreviewed by anyone but their author, as the sixth handover
already recorded.

### 2026-09-18 - The review's remaining items closed, and the fight can now end

**Six review items, and one the review did not find.** The two incomplete items from the
`9020c502f` review note and the A-D verifier's follow-ups are closed, and re-reading the
disassembly for them turned up a seventh defect nobody had flagged.

| Item | ROM | What was wrong, and what it looks like |
| --- | --- | --- |
| Hand render gating | `loc_78946` (sonic3k.asm:160388-160405) ends `bra.w sub_78B46` with no `Draw_Sprite`; `loc_7897A` (160407-160432) rewrites `(a0)` for the *next* frame and still falls through `loc_7898C` to `loc_789C4` | The draw was derived from the routine byte, which gets **both** switch frames wrong in opposite directions: the arming frame drew the hand at the stale create-loop position, and the final volley frame -- the one the ROM does draw -- was suppressed. The draw is now a per-frame decision, and it also honours `loc_7897A`'s `$20` blink (`move.b $20(a0),d0 / beq -> collision list + draw; btst #0,d0 / bne -> rts`) |
| The hand's own hit path | `sub_78CF4` (160756-160776) and `loc_78D2C` (160778-160791) | Unmodelled. The hand now implements `TouchResponseProvider`/`TouchResponseAttackable` with `word_78D6C`'s collision byte `6` and `collision_property` 4, opens the `$20` window, plays `sfx_BossHit`, and creates `ChildObjDat_78D98` -> `loc_78A28`, the hit ring that rides the hand and deletes itself when the hand's `status` bit 7 is set. `takeHit()`, which had no production caller, is gone |
| Touch bookkeeping | sonic3k.asm:20916-20923 | `onPlayerAttack` stowed `$25` and decremented `collision_property` but dropped `move.b d0,$1C(a1)` (the attacker's object address, `$00`/`$4A`) and `bset #7,status(a1)` on the killing blow. Both are modelled on the drill and on the hand; the hand's bit 7 is the one the hit ring reads |
| Hand anchor | `MoveSprite_AtAngleLookup` (178504-178523) reads `move.w x_pos(a1)` / `move.w y_pos(a1)` off `parent3` | It read `getX()/getY()`. For an `AbstractBossChild` those *are* the ROM position words -- `currentX` is what `drawFrameIndex` receives -- so the value was right, but the reading was not: it now goes through `ringXFixed() >> 16`, which names the field. The fallback is the real fix: when the anchor link is gone the hand now **stands still**, where it used to substitute the boss body and teleport across the arena |
| `sub_78B46` retirement | 160568-160590 and `loc_78B86` 160592-160605 | The `$38` bits were set and nothing read them, so killing a hand only shortened the hover. Every ring child now parks on `Wait_Draw` for `$2C - subtype * 2`, explodes, and deletes `$F` frames later. The subtype rises towards the hand, so the arm peels away from the hand end first |
| Javadoc citations | -- | Nineteen `sonic3k.asm:` ranges were 14-45 lines out. Re-read against the label lines and corrected in all six classes. `sub_78BEE` was the worst, cited 24 lines early |
| **Flash palette line** (not in the review) | `word_78CA6`'s offsets are into `Normal_palette_line_2`, and `sonic3k.constants.asm:767-770` defines `Normal_palette ds.b $80` with `Normal_palette_line_2 = Normal_palette+$20` | The ROM's palette-line names are **one-based**. `FLASH_PALETTE_LINE` was `2`, so every hit flash was writing engine palette line 2 instead of line 1 -- and because the shipped window is the boss's own colours rather than white, the symptom is unrelated sprites tinting for `$20` frames, not a missing flash. The rest of the codebase already knew this (`AizEndBossInstance`:129, `LbzEndBossInstance`:85, `TunnelbotBadnikInstance`:136 all say so in comments); this class did not |

**The shared retirement is shared code, not three copies.** `LrzMinibossRingChildBase` holds
`sub_78B46` and the two-step park/explode/delete, because in the ROM it is literally one routine
that `loc_78838`, `loc_788F4`, `loc_78946` and `loc_7897A` all branch to. The seventh handover
suggested two `int` scalars per child instead, on the grounds that an object-typed field on a
boss child is a `TestRewindHarnessCoverageRatchet` risk -- the two scalars are what landed, they
just live on a shared abstract base rather than being written out three times.

**The defeat chain.** `loc_78C60` now installs `Wait_FadeToLevelMusic` (179656-179669) without
reseeding `$2E`, so the pause before the drill breaks up is whatever the interrupted phase had
left -- the ROM's behaviour, and the reason the pause is not a fixed length. When it expires,
`loc_85674` clears `render_flags` bit 7 (the drill stops drawing *before* the pieces appear),
spawns the song fade, and runs `loc_787E0` (160247-160255): eleven `ChildObjDat_78D9E` pieces at
their signed byte offsets, each with its own `RawAni_78A9C` frame and its own `Obj_VelocityIndex`
entry (`Set_IndexedVelocity` with `d0 = $5C` is a **byte** offset, so the pieces take entries 23
to 33), flickering on alternate frames through `Obj_FlickerMove`'s `bchg #6,$38(a0) / beq` -- which
reads the bit *before* the change, so the first frame after creation is a skipped one. Then the
drill becomes `Obj_EndSignControl`, which in this engine is `S3kBossDefeatSignpostFlow`, the same
object every other S3K miniboss hands over to.

**What `loc_787E0` still does not do, and why.** It allocates the `loc_78AA8` palette waiter. That
object and the one it allocates in turn release the camera at `Camera_min_X_pos = $940` and
`$2C0` -- both far *behind* the act 1 arena. Building them now would fire both on the frame the
drill dies. They are recorded as an open question with a native kill condition, and are owed
together with the act change that makes their thresholds mean anything.

**Verification.** Nine deliberate breaks across three runs, each producing exactly the failure
its own test exists to produce, none masked by another. Six together for the review items: the
draw derived from the routine byte, the `$20` window restoring `collision_flags` immediately, the
`$1C` marker hard-coded wrong, `status` bit 7 never set, the boss body substituted for a retired
anchor, and `$2C - subtype * 2` flattened to `$2C`. Two together for the defeat chain: the pieces
created at `loc_78C60` instead of after the fade (caught as "loc_787E0 has not run yet: expected 0
but was 11"), and `bchg` read as setting the flag from the *new* bit (caught as the flicker
pattern inverted, `[true, false, ...]` for `[false, true, ...]`). And one alone, because the test
that holds it also holds the first two: `MoveSprite`'s gravity applied *before* the position step
rather than after.

**Two of the first defeat-chain tests passed against broken code, for two different reasons, and
both are worth recording.** The debris are allocated into their own object slots
(`AllocateObjectAfterCurrent`), so the main object loop runs them and the parent's child pass does
not -- and this fixture drives only the boss, so the pieces never updated at all and the flicker
list read `[false × 8]` against code that was correct. The second is worse: the first draft
asserted "at least seven distinct X positions after four frames", which the *creation offsets*
already satisfy. It would have passed with the velocities deleted. It now asserts each piece's
displacement against its own `Obj_VelocityIndex` entry, which is the only thing that can tell a
velocity from a starting position.

**A third passed against broken code for a third reason.** `AbstractBossInstance.update` skips
`updateBossLogic` entirely once `state.defeated` is set, unless the boss overrides
`usesDefeatSequencer()` to false. Without that override the whole chain was unreachable: the fade
never counted out and the eleven pieces never appeared. The test caught it by hanging, not by
failing, because its wait for the pieces was an unbounded loop -- which is its own lesson about
bounding every wait in a test.

The palette-line assertion is a **constant** check, not a rendered-output one; it is stated as
such because the risk it guards (one-based ROM naming) is a transcription risk, not a behavioural
one.

### 2026-09-18 - The arena was always reachable; what was missing was the gate

**The ninth handover's ten probes were read wrong.** Its own table records three teleports that
**land safely** -- `($2A80,$600)`, `($2B40,$600)`, `($2C00,$600)`, at `y` `$7B3`, `$66C`, `$7AD` --
and dismisses them as "ledges above and left of the drill, not in the arena". They are the arena.
Read the fixture instead of the screen: from native row 22985 to the end of the fight Player 1
stands at `y` `$7AD`-`$7B1` and the camera is pinned at `($2C00,$710)`, and the probe that landed
at `($2C00,$600)` came to rest at `y $7AD` -- **the native standing height, to the pixel**. The
drill is at `($2CA0,$880)`, 210 px *below* that floor, because this miniboss hangs under it and
sends its arms up through it; that is why a capture framed on the drill's own coordinates shows it
"off the bottom-right of the screen". Nothing about the arena floor is event-built. **Do not
re-run the probes.**

**What actually kept the fight from starting: `Obj_LRZMiniboss`'s first dispatch was not
implemented.** sonic3k.asm:160001-160010 never enters `off_7854C`. It runs
`Check_CameraInRange` against `word_784E0` (`$610,$810,$2B00,$2D00`), then `sub_85D6A` --
`Boss_flag`, a music fade, the four `Camera_stored_*` saves and `word_784E8`
(`$710,$710,$2C00,$2C00`) into `_unkFAB0..6` -- and installs `loc_78522`, which is a bare
`jmp loc_85CA4`. Only when that ramp's three `$27` bits are all set does it jump through
`$34(a0)` to `loc_78528`, which installs `loc_78538`, the routine table. So `loc_78562` and its
two child rings do not run until the camera has locked. The engine went straight to `loc_78562` on
activation, so the drill built its arms wherever the player happened to be, the camera never
locked, and a capture could walk out of the arena in either direction.

Implemented on the shared `S3kSharedBossCameraGate` that the Ice Cap, Hydrocity and Lava Bed
bosses already use, with three tests and two deliberate breaks:
`ARENA_LOCK_Y` `$710` -> `$700` failed only `theGateLocksTheArenaToWord784E8` with
`expected: <1808> but was: <1792>`, and `BOSS_GATE_FADE_FRAMES` `2*60` -> `0` failed only
`theGateWaitsTwoSecondsBeforeTheMinibossMusic` with `completed at 1`. A third break -- disabling
the gate call itself -- failed all 25, through `setUp`, which is the assertion that the gate is
load-bearing for the whole fight.

**The measured result, at `--x 0x2C00 --y 0x600 --rings 355`:** the camera locks at
**`(11264,1808)` = `($2C00,$710)`, the native pair exactly**, and holds there for the rest of the
capture; the player runs right to **`x 11560` = `$2D28`, the same wall native stops at**
(native row 23100), and the two arms unroll as vertical link chains either side of the drill. The
native arm is a vertical chain too -- aux rows 23160-23300 put its twelve links between
`x $2D08` and `$2D3A` with `y` sweeping `$846` up to `$734`, straight past the player's `$7B0` --
so the shape the engine draws is the shape the ROM makes.

**`mus_Miniboss` is `$2E`, not `$18`.** `sonic3k.constants.asm:1461,1483` puts `mus_MinibossK`
at `$18` and `mus_Miniboss` at `$2E`, and `Obj_LRZMiniboss` writes the latter. In the S&K driver
table both ids play the same track, so this is the ROM's byte rather than an audible change; four
sibling minibosses in the engine use `$18` and were left alone.

**A measurement hazard that cost two captures.** `maven_queue.py ... exec:java` **does not
compile**. Two arena captures were run against `target/classes` left behind by an earlier
`-Dtest=` run -- which at that moment held a deliberately *broken* build -- and their camera
numbers were read as engine behaviour. Every capture command in this campaign now runs
`compile exec:java`. The tell was a camera that locked at `$700` instead of `$710`: the broken
constant, not a defect.

**`--rings` was added to `GameplayCaptureTool`.** A boss filmed from a positioned start begins on
zero rings, where the first touch is fatal; the recorded run arrives at this arena with 355. With
the ring count declared the fight runs for over 2000 frames instead of ending on frame 256.

**Open question with a kill condition.** Standing still at `$2D28` -- native's own spot -- the
engine takes a hit at about frame 270 of the arena capture, and native's ring count does not move
between rows 22985 and 24200. It is not yet established that this is a defect: native is jumping
through that window (its Player 1 `y` moves `$7B4` -> `$77B` -> `$788` over rows 23160-23300)
while the capture stands still, and the two fights are not in phase. **Kill condition:** drive the
engine through native's rows 23150-23310 input from a position and boss phase matched to native's,
and compare `rings`. If the engine still loses rings there, the arm link's collision box or its
`MoveSprite_AtAngleLookup` radius is wrong; if not, the hit is the capture's own phase and nothing
is owed.

### 2026-09-18 - The measured fight cycle, so the next round does not author blind

Printed from `LrzMinibossInstance.updateBossLogic` against `V_int_run_count` during a capture from
`($2C00,$600)`. **The gate completes at `v 172`**; the drill's routine byte then runs:

| `V_int_run_count` | routine | what it is | `collision_flags` | drill `y` |
| --- | --- | --- | --- | --- |
| 173 / 174 / 175 | `00` / `02` / `04` | `loc_78562` children, `loc_78592` art, `loc_785C2` delay | `00` | 2176 |
| 223 | `06` | hover wait `$15F`; **`bset #3` here is the arms unrolling** | `00` | 2176 |
| 575 | `08` | pre-descent wait `$4F` | `00` | 2176 |
| 655 | `0A` | rise to `_unkFAB0 $7A8` | `00` | 2176 -> 1960 |
| 709 | `0C` | swing `$BF`, **tracking Player 1's X** | `00` | 1960 |
| 901 | `0E` | pre-drop wait `$1F`; **X is locked from here** | `00` | 1963 |
| 933 | `10` | drop | **`B5` (hurts)** | 1963 |
| 936 | `12` | slam `$5F` | **`06` (hittable)** | 1975 |
| 1032 | `14` | return to bottom | `06` | 1975 -> 2176 |
| 1083 | `06` | the cycle repeats, 874 frames long | `00` | 2176 |

So the **window to damage the drill is `v 936`-`1082`, 146 frames**, with the drill resting at
`y 1975` -- five pixels below the player's standing height -- at whatever X it tracked to by
`v 901`. A capture frame is `V_int_run_count − 1`, and an input-log frame is the capture frame
minus `--settle`.

**Three things this settles.** The drop hurts for three frames and the slam is an ordinary enemy
box for ninety-six, so the play is to be clear of the drill's X at `v 933` and to arrive on it
airborne after `v 936`. A 32-frame walk away is not enough: from a standstill it moves about 17 px
and the drill's own box is `$33` half-width. A 90-frame walk left starting at `v ~860` does clear
it -- that capture took no damage at all through the whole first cycle (`lrz-arena-fight-timed-v3`)
-- but the jumps that followed passed over the drill without a recorded hit, and **whether a hit
landed was not measured**: the instrumented re-run of that input was still waiting on the Maven
queue when this round ended. That measurement is the next round's first step, and it is one
capture.
### 2026-09-18 - Measured: the slamming drill takes no hit, and that is the next round's first bug

The instrumented re-run the previous entry left owed has now been made
(`inputs`/`lrz-arena-fight-timed-v3`, drill state printed against `V_int_run_count`). **The hit
count never moves: `hits` is `6` on every one of the 1947 dispatches.** And the player was
demonstrably inside the box:

- Window 1 is `v 936`-`1082` with the drill resting at **`x 11392`**, `y 1975`, `cf 06`.
- Over capture frames 990-1050 Player 1 goes `x 11341 -> 11419` at `y 1923`-`1959`, airborne the
  whole way (`state.csv`). The drill's own solid half-width is `$33` = 51, so its box is
  `11341`-`11443`, and `SolidObjectFull`'s `d2 = 4` puts its top at `1971`. The player crosses
  that box, in the air, for about sixty frames.
- **Nothing happens either way**: no hit dealt, and no ring lost, for the whole first cycle.

So this is not a timing miss and not an input-authoring problem -- the two earlier hypotheses. The
drill is `isSolidFor` during `ROUTINE_SLAM` *and* publishes `collision_flags 06` in the same frames,
and the shared touch pass appears to be resolving the overlap as the solid and never reaching the
enemy box. `loc_7871A` calls `SolidObjectFull` with `d1=$33 / d2=4 / d3=0` and `loc_786EA` sets
`collision_flags` to `6` (sonic3k.asm, `loc_786EA`/`loc_7871A`); in the ROM both are live at once
and an airborne player still damages it.

**Next round's first step, before any clip work:** a headless test that puts an airborne Player 1
inside `SolidObjectParams.of($33,4,0)` while `state.routine == ROUTINE_SLAM` and asserts
`collision_property` decrements -- then fix whichever of the solid path or the touch path is
swallowing it. That test is also the RED test for clip `30`, because a fight where the boss cannot
be hit cannot be filmed. **Clips `30`, `31` and `32` are blocked on it**, not on input authoring.
### 2026-09-18 - The drill was always hittable: the box is 32 px, not 102

**The hit defect was a measurement error, and the ROM settles it.** `loc_7871A` calls
`SolidObjectFull` with `d1=$33`, a pixel half-width for the push-out box; `loc_786EA` writes
`collision_flags 6`, whose low six bits select `Touch_Sizes` entry 6 (sonic3k.asm:20713-20720,
`dc.b $10,$10`) -- a **32x32** box on `x_pos`/`y_pos`. The tenth round read `$33` as the touch
size and concluded the player "crosses that box, in the air, for about sixty frames". It crossed
the 102 px box the player stands on. Against the real 32 px box the recorded crossing is a near
miss: over capture frames 990-1050 the player is left of it while still too high (`y` 1923-1945,
box top 1959) and low enough only once it is past on the right (`x` 11419, box right edge 11408).
No hit **and** no ring loss is what a miss looks like; a swallowed hit would have hurt him.

**Native, at its own relative geometry** (`s3k-sonic-tails-complete-emeralds/lrz`, slot 22 =
`loc_78538`): the drill slams at `(11432,1975)` over frames 23832-23927. Player 1 lands on its
solid top at `y 1956` (frames 23870-23876, `air 0`), jumps, comes down rolling, is inside the
touch box at 23927-23930 at `(11429,1956)` -- 3 px left, 19 px above -- and its `y` reverses there
on the boss rebound. Rings are 355 across the whole window.

**The engine already does all of that**, and `TestLrzMinibossHitPath` now holds it: a hit at
native's own offset, a hit at the 16 px edge and none at 40 px, and the drill solid across its
full `$33` width throughout. Two deliberate breaks: publishing size index 7 failed both geometry
tests on their own assertions while the solid test stayed green; making the drill non-solid failed
only the solid test. `COLLISION_SIZE` was `$33` sitting in the size-index slot -- dead, because
`getCollisionFlags()` is overridden, but it is the misreading itself in the code, and it is now 6.

**Confirmed in the fight, not only in a fixture.** With
`inputs/lrz1-miniboss-fight-v7.txt` (`--x 0x2C00 --y 0x600 --rings 355 --settle 1`) and a
temporary `lrz.drill.probe` print in `updateBossLogic` (reverted before the delivered capture),
the drill goes `hits 6 -> 5` at `v 972` with `collision_flags 6 -> 0`, and `sub_78C14` restores
the byte at `v 1004` -- the `$20` window, exactly. The hit is landed by the **insta-shield**: the
script presses A every other frame, and the 48x48 pass reaches a drill 33 px away that the
ordinary 16 px player box does not.

**Three things the arena forces on any input script, all measured.**

1. **`Check_CameraInRange` must see the arena before the fight starts at all.** A capture that
   walks left first completes the gate at `v 1987` instead of `v 173`, and no slam happens inside
   1000 frames. A capture that reaches the arena promptly gets the measured `v 173`.
2. **Once the gate locks the camera at `($2C00,$710)` the player is confined to about
   `x 11272`-`11560`.** The left bound is the locked camera edge. The one safe standing spot found
   earlier (`x 11163`, 900 frames without a scratch) is *outside* that band and only reachable
   before the lock -- which is why it is safe, and why it cannot be used.
3. **Standing still inside the band costs every ring.** At `x 11280`, `11470` and `11560` the
   player is hit about every 180 frames (capture frames 256, 443, 619 in `raw-41`; 261 in the v5
   probe), the first hit scatters all 355 rings and the second kills. Native never loses a ring in
   the same band because it never stands still. Pacing and jumping through the wait survives it.

**Open question, with a kill condition.** Is the ~180-frame hit while standing in the band the
hand's shot behaving as the ROM's does, or an engine box? **Kill condition:** drive native's rows
23150-23310 input from a position and boss phase matched to native's and compare `rings`; if the
engine still loses them there, the hand or its shot is wrong. Not answered this round.

**A capture-fidelity question this round did not chase.** In `raw-44-lrz1-miniboss-hit`, frames
963-967 and 968-971 are byte-for-byte identical although `state.csv` has the player moving 2 px a
frame and the slam's screen shake running. Adjacent frames elsewhere in the same capture differ
normally. Either `GameplayCaptureTool` repeated a presented frame there or the render skipped one;
`state.csv` is the authority for what happened, and the hit is evidenced by the probe, not by the
PNGs.

### 2026-09-18 - The seamless act change, read out of the ROM in full

Not implemented this round. Read and written down so the next round does not re-derive it, and
because the two halves **must land together**: stage 0's advance to `$C` without `loc_56CAA`
behind it leaves `Events_routine_bg` pointing at an unimplemented stage and the background stops
being drawn.

**The trigger (item 1).** `Obj_Results` (sonic3k.asm:62615-62622) ends its sprite-creation routine
with `addq.b #2,routine(a0)` and then, **only** when `Apparent_act` is zero (act 1),
`Apparent_zone` is neither `0` (Angel Island) nor `5` (Ice Cap), `st (Events_fg_5).w`. Note
`Apparent_act`/`Apparent_zone`, not the loaded pair: across a seamless change those differ, which
is why the engine's results owner has to read the apparent values.

**Stage 0's branch (`loc_56BD2`, 115273-115292).** `tst.w (Events_fg_5)`; when zero it falls to
`loc_56C28`, the ordinary dome-region path. When set: `clr.w (Events_fg_5)`, then queue
`LRZ2_128x128_Secondary_Kos` into `Chunk_table+$180` and `LRZ2_16x16_Secondary_Kos` into
`Block_table+$128` through `Queue_Kos`; queue `ArtKosM_LRZ2_Secondary` at
`tiles_to_bytes($090)` through `Queue_Kos_Module`; `Load_PLC $30`; then
`move.w #$C,(Events_routine_bg)` and `bra loc_56D16` -- so the frame that arms the change still
draws the background normally.

**Stage `$C` (`loc_56CAA`, 115347-115380).** `tst.b (Kos_modules_left)`; while non-zero it does
nothing but `loc_56D16`, so the change waits for the queued art. When the queue is empty, on
**one** frame:

| Step | ROM |
| --- | --- |
| The act | `move.w #$901,(Current_zone_and_act)` |
| Level variables | `clr.b` on `Dynamic_resize_routine`, `Object_load_routine`, `Rings_manager_routine`, `Boss_flag`, `Respawn_table_keep` |
| Switches | `jsr Clear_Switches` -- `$20` bytes, which is the trigger array **and** `Anim_Counters` |
| Rocks | `clr.b (LRZ_rocks_routine)` |
| Reload | `jsr Load_Level`, `jsr LoadSolids` (both inside a `movem.l d7-a0/a2-a3` save) |
| The rebase | `d0 = $2C00`, `d1 = 0`; `sub.w d0` from `Player_1+x_pos`, `Player_2+x_pos`, then `jsr Offset_ObjectsDuringTransition`, then `Camera_X_pos`, `Camera_X_pos_copy`, `Camera_min_X_pos`, `Camera_max_X_pos` |
| Tiles | `jsr Reset_TileOffsetPositionActual` |
| The stage | `clr.w (Events_routine_bg)` -- back to stage 0, which is now LRZ2's |

Then it falls into `loc_56D16` like every other stage: `LRZ1_Deform`, `Draw_BG` with
`LRZ1_BGDrawArray` (`d6 = $20`, `d5 = 3`), `ApplyDeformation` with `LRZ1_BGDeformArray` at
`HScroll_table+$00C`, `ShakeScreen_Setup`.

**What that means for the engine.** The owner is a transition class beside
`SozActTransitionHandoff`, driven from `LrzBackgroundStageMachine`; the rebase goes through the
existing transition offset path, not per-object edits; `Clear_Switches` has to clear the animation
counters as well as the triggers, which is the part an engine is most likely to miss; and
`Respawn_table_keep` being cleared is what makes the new act's objects load rather than being
suppressed as already-visited. The `$901` art readiness closed by the KosM queue here is the same
gap a direct `$901` load has.

## Handover, 2026-09-18 (eighth)

**Where the work is.** Branch `feature/ai-lrz-bring-up`, base develop `035e48a58`. This round
closed every outstanding item from the `9020c502f` review and its A-D follow-ups, found and fixed
a seventh defect the review did not (the one-based palette-line naming), and built the defeat
chain as far as `Obj_EndSignControl`. **Census unchanged at 0 / 188 / 8.**

**What slice 6 still owes, in the order it has to be built.**

1. **Results consuming `Events_fg_5` for Lava Reef.** The engine already has the whole chain:
   the shared results path calls `S3kTransitionEventBridge.signalActTransition()`, which
   `Sonic3kLevelEventManager.setEventsFg5ForActTransition()` (around line 1809) fans out to the
   zones that have implemented it -- CNZ, HCZ, FBZ, MGZ, MHZ, LBZ. LRZ is simply not in that list
   yet, and `LrzZoneRuntimeState` has no `eventsFg5` word. The ROM gate is `Obj_Results`
   (sonic3k.asm:62615-62622): `Apparent_act == 0` and the zone is neither Angel Island nor Ice
   Cap, which the shared path already enforces for the others.
2. **`LRZ1_BackgroundEvent` stage 0's consumer** (`loc_56BD2`, sonic3k.asm:115274-115293): clear
   `Events_fg_5`, queue `LRZ2_128x128_Secondary_Kos` into `Chunk_table+$180`,
   `LRZ2_16x16_Secondary_Kos` into `Block_table+$128`, `ArtKosM_LRZ2_Secondary` at tile `$090`,
   PLC `$30`, and set `Events_routine_bg = $C`. `LrzBackgroundStageMachine` already owns stages
   0/4/8 and deliberately leaves `$C` alone; this is where stage `$C` joins it.
3. **Stage `$C`, the act change itself** (`loc_56CAA`, sonic3k.asm:115347-115374). Waits on
   `Kos_modules_left`, then on **one** frame: `Current_zone_and_act = $901`; clear
   `Dynamic_resize_routine`, `Object_load_routine`, `Rings_manager_routine`, `Boss_flag`,
   `Respawn_table_keep`; `Clear_Switches` (sonic3k.asm:104284-104291) clears **`$20` bytes** from
   `Level_trigger_array`, and `sonic3k.constants.asm:699-700` puts `Anim_Counters ds.b $10`
   directly after `Level_trigger_array ds.b $10`, so the animated-tile phase counters go with it;
   `clr.b LRZ_rocks_routine`; `Load_Level`; `LoadSolids`; then `−$2C00` off both players'
   `x_pos`, `Offset_ObjectsDuringTransition`, `Camera_X_pos`, `Camera_X_pos_copy`,
   `Camera_min_X_pos` and `Camera_max_X_pos`; `Reset_TileOffsetPositionActual`; and
   `Events_routine_bg = 0`.
   **The engine shape for this is settled, not open.** `SozAct1Events.requestAct2Reload()` is the
   closest precedent and should be copied almost literally: a `SeamlessLevelTransitionRequest`
   of type `RELOAD_TARGET_LEVEL` with `targetZoneAct(9, 1)`, `deactivateLevelNow(false)`,
   `preserveMusic(true)`, `preserveLevelGamestate(true)`, `showInLevelTitleCard(false)`,
   `objectSurvivalPolicy(ALL_LIVE_SST)`, `preserveOffsetCameraPosition(true)`, a
   `cameraOffset(-0x2C00, 0)` and the four `postTransition*` bounds offset by the same, applied
   through `levelManager().applySynchronousScreenEventTransition(...)` -- *synchronous*, because
   `loc_56CAA` does the reload inside this background-event dispatch and deferring it leaves one
   unreloaded frame. `Sonic3kCNZEvents.handleSeamlessReloadStage()` (around line 820) is the
   second precedent and uses `PERSISTENT_EXACT_SST` instead; read both before choosing.
   A `LrzActTransitionHandoff` beside `SozActTransitionHandoff` carries the act-2 side.
4. **The two camera releases and `word_78EAA`**, both now open questions with kill conditions.
   They belong after the act change, and the reason is in the question: their thresholds are act-2
   coordinates.
5. **`LRZ2_BackgroundEvent` stages 0 and 4** (`loc_5700C`/`loc_57040`, sonic3k.asm:115693-115722),
   unchanged from the seventh handover.

**What a player now sees at the end of the fight, and what they do not.** The drill dies, fades,
breaks apart and the end-of-act sign and results appear -- and then the act does not change. The
results owner calls `signalActTransition()`, `setEventsFg5ForActTransition()` has no Lava Reef
branch, so nothing is set and nothing consumes it: the player is left standing in act 1 after the
tally. Nothing stalls and nothing regresses (the background event never advances to a stage it
cannot leave), but the ending is visibly unfinished, and that is the state item 1 above closes.
**Do not build stage `$C` half-way**: a stage 0 that advances `Events_routine_bg` to `$C` with no
stage `$C` behind it would freeze the act 1 background from the first frame after results.

**What has not been done and is not owed to a later slice.** The miniboss still has **no clip, no
route rewind spot and no native comparison**, so **no matrix row may be recorded for it**. The
cold act 1 route was **not re-measured this round**; the last figure (native rows 0-2322 exact,
first divergence row 2323) is stamped to `17ccb4e72` and predates three commits, so re-measure
before quoting it. `TestS3kSonicTailsLrzSegmentTraceReplay` was last measured at `51474c172`
(frame 208 `tails_y_speed`, 6835 errors) and was not re-run.

**Carried forward unchanged from the seventh handover**: the `$1D`/`sub_42EC0` route exercise,
cold-route rewind spots and a route spot for `$99`, act 2 placements on an act 2 route, native row
2323, `loc_849D8`'s `Set_IndexedVelocity` `d0` for a retired Fireworm segment, the direct-`$901`
act 2 background art gap, and slice 5's clip.

**Media.** Clips `09`-`28` unchanged; no new clip this round either. The next round's first
numbered clip is `29`.

**On reviews.** No reviewer subagent was spawned this round. The three previous attempts across
two rounds all returned nothing (recorded above), and the work order's own instruction -- write
the report to a file *and* send it, then wait -- is the mitigation, not a guarantee. What was done
instead is the six-break run: each new comparison was broken on purpose, once, in a single
combined run, and each failure was checked against the message that test exists to produce. That
catches a test that cannot fail. It does not catch a misreading its author would make twice,
which is what a second pair of eyes is for, and the routine-table and continuation-chain constants
remain unreviewed by anyone but their author.

### 2026-09-18 - The miniboss cannot be filmed from a teleport, and the reason is the arena floor

**Nine capture probes, no clip.** Every one is recorded here because the negative results are the
expensive part and re-running them costs a queue cycle each.

`$9D` is placed at **`($2CA0,$880)`** and is live on a teleported load: a teleport onto it dies on
frame 1 (the drill's own box, with 0 rings). But **there is no floor anywhere under it.**

| Teleport | Result |
| --- | --- |
| `($2C40,$8A0)` | falls to `y=$B9A`, dies frame 118 |
| `($2B80,$8A0)` | falls to `y=$C05`, dies frame 91 |
| `($2DC0,$8A0)` | falls to `y=$C05`, dies frame 91 |
| `($2CA0,$8A0)` | dies frame 1 -- inside the drill |
| `($2CA0,$7E0)` | falls onto the drill, dies frame 95 at `y=$841` |
| `($2CA0,$940)` | falls to `y=$BEA`, dies frame 142 |
| `($2C60,$820)`, `($2CE0,$820)` | both fall to `y=$BFE`, die frame 97 |
| `($2A80,$600)`, `($2B40,$600)`, `($2C00,$600)` | **land safely** at `y=$7B3`, `$66C`, `$7AD` |

The three that land are on ledges *above and left of* the drill, not in the arena: a 640-frame
capture at `($2C00,$600)` shows a lava shaft and a rock ledge with the drill off the bottom-right
of the screen (camera `(11114,1869)`, drill centre `(11424,2176)`), and the player stands there
untouched for the whole capture. That capture was deleted rather than numbered, because a clip
that does not show the boss is not a clip of the boss.

**Why there is no floor: the teleport skips the events that build it.** The `gameplay-capture`
skill's own warning ("teleporting skips plane switchers and level events between the act start and
that point") is the whole story here. The arena floor in Lava Reef act 1 is not static layout; the
only floor-producing mechanisms in this act are the `LRZ1_ScreenEvent` chunk edits and the rock
crusher's `Child7_ChangeLevSize` bridge, and neither runs for a player set down at the arena.

**And the walk-in is blocked.** From the furthest-right position any previous LRZ capture reached
alive (`($2857,$6D9)`, from `raw-28`), holding Right for 900 frames walks to **`x=$2995`,
`y=$68F`** and stops dead against a wall; adding a jump every 45 frames for 1200 frames does not
pass it either (`x=$2995` at frame 213 and unchanged to frame 1199). The frame shows Sonic on a
ledge against a rock column with a structure above right. That is **780 px short of the drill**.

**What the next round should do instead of repeating this.** Do not scan for a teleport spot;
there is not one. Extend an authored route: the `$1C` button / `$19` door / `$1A` big door clips
(`10`, `11`, `12`) and `lrz1-cold-route-v7` already drive the triggers in this part of the act, so
start from one of those input logs and carry it right through the wall at `$2995` with the door
open, then into the arena. The capture that films the fight is the same capture that proves the
cold route reaches the boss, so doing the route work once serves both the clips and the frontier
re-measurement the lead asked for.

**The wall is not a wall: it yields to speed.** A tenth probe added a spindash at it
(`30 -; 220 R; 20 D; 1 D+A; 12 D; ... ; 20 D; 700 R` from `($2857,$6D0)`) and the player passed
`$2995` to **`x=$29F5`, `y=$692`**, still alive and still moving right at `gspeed 44` when the
input ran out. So it is a slope or a rise that walking cannot climb, not a closed door, and the
route continues -- it just needs a longer tail and probably more than one spindash. That is the
thread to pull: the input log that got there is saved in the campaign capture directory as
`inputs/lrz1-arena-approach-spindash-v1.txt`.

**Clips `29`-`32` are therefore still owed, and so is the route re-measurement.** Nothing was
numbered this round; the next clip number is still `29`.

## Handover, 2026-09-18 (ninth)

**Where the work is.** Branch `feature/ai-lrz-bring-up`, base develop `035e48a58`, one commit this
round: `b35f59d33` the arena gate and `--rings`. Census unchanged at **0 / 188 / 8**.

**What this round settled, and what it did not.**

1. **The arena is reachable, and the eighth handover's "there is no floor" conclusion was wrong.**
   `--x 0x2C00 --y 0x600` lands on the arena floor at `y $7AD`; the recorded run stands at
   `$7AD`-`$7B1` for the whole fight. Three of that handover's own probes landed there and were
   dismissed. Evidence and the full reasoning are in the entry above. **This is a seeded reach.**
   The cold route was **not** re-measured this round and its last figure (native rows 0-2322 exact,
   first divergence 2323) is still stamped to `17ccb4e72` and now predates four commits.
2. **The fight now starts the way the ROM starts it** (`b35f59d33`): `Check_CameraInRange`,
   `sub_85D6A`, the `loc_85CA4` ramp, and only then `loc_78528` and the routine table. Measured
   lock `($2C00,$710)`; measured right wall `x $2D28`, native's own.
3. **Clip `29`** `29-lrz1-miniboss-arrival-and-arms.mp4`, with `INDEX.md` created for the campaign.
   **Clips `30`, `31` and `32` are still owed**, and so is the `33` transition clip.
4. **Nothing of work order 6 item 1 onwards was built**: no `Events_fg_5` for Lava Reef, no stage
   `$C`, no camera releases, no `LRZ2_BackgroundEvent` stages. The eighth handover's build order
   stands unchanged and is still the right order. An `eventsFg5` word was written for
   `LrzZoneRuntimeState` and then **deliberately reverted**: a flag nothing reads is not a step,
   and the plan's own rule is that stage `$C` is built whole or not at all.

**Why clips `30`-`32` did not land, so the next round does not repeat it.** Three authored fight
inputs were captured (arena centre, native's own recorded fight input replayed from the arena, and
two continuous-jump patterns of 2900 and 4200 frames). All three reach the fight; none landed a
single hit on the drill. What the captures show:

- The drill tracks Player 1 and rises to `_unkFAB0 = $7A8`, which **is** the player's standing
  height, so it ends up on top of a player who holds still, and a player pinned against the
  `$2D28` wall is under it with nowhere to go. Frame 1600 of the jump capture shows the drill
  filling the right quarter of the screen with the player inside it.
- `collision_flags` is `$B5` (hurts) through `loc_786BC`'s drop and `6` (hittable) only from
  `loc_786EA`, so a jump into the descending drill is a hit **taken**, not dealt. The `$5F`-frame
  slam is the window, and it is about 950 frames after the gate completes.
- Blind input authoring is the wrong tool for this. The next round should either drive the
  fixture's own rows with the boss phase matched (see the kill condition in the entry above) or
  step the fight in a headless test and read the routine byte, then author the jump against the
  measured slam frames rather than against a guess.

**A hazard worth more than the captures.** `maven_queue.py ... exec:java` **does not compile**.
Two captures ran against classes left by an earlier `-Dtest=` run and their camera numbers were
read as engine behaviour. Every capture command in this campaign now runs `compile exec:java`.

**Correction to this handover, measured after it was written:** clips `30`-`32` are not blocked on
input authoring. The drill takes no hit at all while it slams, with the player measurably inside
its box; see the entry above. That is the next round's first item.

**Carried forward unchanged from the eighth handover**: the `$1D`/`sub_42EC0` route exercise,
cold-route rewind spots and a route spot for `$99`, act 2 placements on an act 2 route, native row
2323, `loc_849D8`'s `Set_IndexedVelocity` `d0` for a retired Fireworm segment, the direct-`$901`
act 2 background art gap, slice 5's clip, and
`TestS3kSonicTailsLrzSegmentTraceReplay` (last measured at `51474c172`, frame 208
`tails_y_speed`, 6835 errors, **not re-run**).

**Media.** Clips `09`-`28` unchanged; `29` added; `INDEX.md` created. The next clip number is `30`.

**Tests run this round.** `TestLrzMinibossInstance` 25/25 (three new, three deliberate breaks, each
failing only its own assertion); a focused run of the four mandatory S3K classes plus every
`TestLrz*`/`TestS3kLrz*`/`TestS3kHpz*`/`TestS3kSoz*` class, `TestEveryObjectRewindRoundTrip`
(1150) and `TestRewindHarnessCoverageRatchet`, all green with no skips; `-Pguards` 669/669 green.
**This is focused validation, not a suite pass**: no trace fixtures, no `run_categories.py`
selection, and no act 2 or LRZ3 coverage were run.

### 2026-09-18 - The row-2323 divergence: the toxomister body was not a badnik at all

**What it was.** `ObjDat_Toxomister` (sonic3k.asm:196950-196954) ends `dc.b 8,8,1,$18`, and that
last byte is `collision_flags`. `Touch_ChkValue` (sonic3k.asm:20774-20776) takes only bits 6-7 as
the type, and `$18 & $C0` is zero: the body is a plain `Touch_Enemy`. The low six bits index
`Touch_Sizes` (sonic3k.asm:20713+), and entry `$18` is `dc.b 4,4` -- an 8x8 box, not the `8,8`
`width_pixels`/`height_pixels` in the same record, which are the sprite.

`ToxomisterBadnikInstance` implemented `TouchResponseProvider` and nothing else. The shared owner
therefore classified the touch as `ENEMY`, found the player attacking, called nobody, saw the
instance still alive and applied no bounce: `ObjectTouchResponseController` only bounces when
`onPlayerAttack` destroyed the instance. The body could not be destroyed by any means.

**How it was found, so the next round does not re-derive it.** A probe printed the touch list and
every overlap on the route's own recorded input: the body is live at `(4292,408)` with `cf=$18`,
`skip=false`, `onScreen=true`, and `LRZTP-HIT ToxomisterBadnikInstance@(4292,408) cat=ENEMY
flags=18 w=4 h=4` fires on four consecutive frames with no effect at all. The engine's cluster
matches native's aux rows exactly -- body `(4292,408)`, cloud `(4280,416)` `cf=$D8`, seven puffs
across `(4268-4292, 412-420)` -- so this was never a placement or activation defect.

**The fix.** The body implements `TouchResponseAttackable` and `PoweredScreenAttackable` and
destroys through the shared `S3K_DESTRUCTION_CONFIG`, which is now package-visible for it. The
cloud follows `Child_AddToTouchList` (sonic3k.asm:84962-84966): when the body has raised `status`
bit 7 the cloud takes `Go_Delete_Sprite` instead of joining the collision response list, and
`Go_Delete_Sprite` itself does `bset #7,status(a0)` -- the bit the puffs read at `loc_8FEDC` -- so
the puffs disperse through `loc_8FF12` exactly as on the cloud's other endings. Nothing else in
the chain changed.

**Two measurement hazards worth carrying forward.**

1. A headless fixture started in Lava Reef act 1 carries the act's **forced** intro animation
   (`HURT_FALL $1B`), and `withSkippedZoneIntro()` does **not** clear it. `setRolling(true)` and
   `setAnimationId(ROLL)` both look like they took -- `getRolling()` is true -- but
   `getAnimationId()` still reads `$1B`, `isSpinAttackAnimation` fails, and the player is hurt or
   killed by the very badnik the case is about. Three runs were spent on this. The case needs
   `setForcedAnimationId(-1)`.
2. The first synthetic setup put the player somewhere the act kills them, and the failure looked
   like the touch not firing. The route's own state (rolling, airborne, `y_vel $448`, at
   `(4293,394)`) is what the case must reproduce, and the route capture is what settles whether a
   synthetic case is measuring the engine or its own scaffolding.

**Verified.** `TestS3kLrzToxomisterReboundHeadless` green with the fix and red on its own
assertion with `onPlayerAttack` broken once ("Touch_EnemyNormal rewrites the badnik to
Obj_Explosion"). The focused set -- `TestToxomisterBadnikInstance`,
`TestLrz*`/`TestS3kLrz*`, the four mandatory S3K classes, `TestEveryObjectRewindRoundTrip`,
`TestRewindHarnessCoverageRatchet`, `SwScrlLrzTest` -- 1482 tests, 0 failures, 0 skips.

**The route.** Re-measured on the fixture's own recorded input at this commit: exact for **3154**
compared frames, first divergence engine frame **3155** = native row **3154**. The frontier moved
831 rows. The new divergence is a landing the engine does not make: native puts the player on a
floor at `(4274,770)` (`air 0`, `rolling 0`, `y_vel 0`, `g_speed` taking the `x_vel`) while the
engine falls on through. Recorded in the frontier log with its kill condition.

### 2026-09-18 - The seamless act change built, and what the independent review found in it

**It runs in production.** `Obj_Results` -> `signalActTransition()` ->
`setEventsFg5ForActTransition()` (now with a Lava Reef branch) -> `loc_56BD2`'s stage 0 ->
`loc_56CAA`'s stage `$C`. `TestS3kLrzSeamlessActChangeHeadless` drives exactly that chain and
asserts the act word, the stage the change parks on, the trigger-array clear, the player, the
camera position and a carried object.

**The review found a blocker the first cut had.** Report:
`~/Videos/OGGF/lrz-bring-up/notes/lrz-seamless-act-change-review.md` (13 findings, read-only, no
build). The blocker: `jsr (Offset_ObjectsDuringTransition)` (sonic3k.asm:115365) was not modelled
at all. The request carried `cameraOffset` but no `playerOffset` and no
`romWorldObjectOffsetRange`, and `LevelActTransitionExecutor.offsetCarriedObjectsForTransition`
reads **`playerOffsetX()`**, not `cameraOffsetX()` -- so under the default `CARRIED_OBJECTS`
policy every surviving act-1 object was left `$2C00` to the right of the player while the commit
message claimed they moved. Fixed with `.playerOffset(-$2C00, 0)` and
`.romWorldObjectOffsetRange(4, 94)`, the FBZ precedent's own slot span
(`Dynamic_object_RAM+object_size` to `Breathing_bubbles`, sonic3k.constants.asm:303-311).

**That fix has a cost the review did not predict, and it is worth knowing before the next zone
does this.** `ROM_WORLD_OFFSET_RANGE` **throws** for any carried object that reports
`participatesInRomWorldTransitionOffset()` without implementing `RomWorldPositionedObject`:
"SST slot N reports render_flags bit 2 without a native ROM position contract". Three classes had
to be given the contract before the change would run at all -- `Sonic3kPathSwapObjectInstance`,
`SozSpriteMaskObjectInstance` (both shared) and `LrzMinibossInstance`. The failure is loud rather
than silent, which is the right trade, but **the live set at the change is what has to be
compliant**, and it is only known by running the change. A zone adopting this policy should
expect the same enumeration.

**Three review findings were applied as given**: `Clear_Switches` runs *before* the reload
(:115355 precedes :115359), not in the handoff after it, matching FBZ; the arm branch is gated on
stage 0, because only `loc_56BD2` reads `Events_fg_5` and stages 4 and 8 never do; and
`setEventsFg5` is gated on zone and act, because the Lava Reef runtime state is shared with the
boss act and an ungated write latches a word there that nothing consumes. The `ordinal < 0`
fallback that would have changed the act without the art now throws instead.

**Two review findings were rejected, with the ROM.**

1. *"No act-2 title card is requested, unlike every precedent."* `loc_56CAA` allocates no
   `Obj_TitleCard`: it has no `AllocateObject` at all. That is what makes the Lava Reef change
   seamless, and it is why `showInLevelTitleCard(false)` is right. SOZ and FBZ pass `TITLE_OWNER`
   for their art lease because their own paths *do* allocate a card; with no title owner here a
   `TITLE_OWNER` lease would never be consumed, so `IMMEDIATE` stands. Recorded so the next round
   does not "fix" it.
2. *"The post-transition camera Y bounds are not carried; the arena Y lock is released."* Half
   right and the wrong conclusion. `loc_56CAA` does subtract from no Y word -- and it also does
   `clr.b (Dynamic_resize_routine)` at :115350, which puts act 2's resize owner back at its first
   entry so it installs act 2's own bounds immediately. Measured on the change frame: `minY` goes
   `0` to `$710` and `maxX` ends at `0`, not the `$128` the subtract alone would leave. The Y and
   the bounds are therefore **not** asserted, and the comment in the test says why. What is
   attributable to the subtract is the camera *position*, which the resize owner does not rewrite,
   and that is asserted exactly.

**A measurement the review asked for and got.** The player does not land on
`playerXBefore - $2C00`. `Player_LevelBound` (sonic3k.asm:23179-23181, 23211-23212) pins them to
`Camera_min_X_pos + $10` on the same frame, and with the rebased min at 0 that is exactly `$10`.
The earlier range check was hiding this; the test now asserts `$10` with the citation. The review
was right that the weakening hid something, and wrong that the hidden thing was a defect.

**Verified.** `TestS3kLrzSeamlessActChangeHeadless` green, and red on the carried-object assertion
alone when `romWorldObjectOffsetRange` is removed once (`expected: <65440> but was: <11168>` --
the object sitting in act 1 coordinates, which is the blocker reproduced). Focused set
`TestLrz*`/`TestS3kLrz*`/`TestSoz*`/`TestS3kSoz*`/`TestS3kHpz*`/`TestS3kDdz*`/`TestFbz*`/
`TestS3kFbz*` plus the four mandatory S3K classes, both rewind guards, `SwScrlLrzTest` and the
path-swap/sprite-mask tests: **3719 tests, 0 failures, 8 skips**, all eight
`@EnabledIfSystemProperty` SOZ capture harnesses. `-Pguards test -B`: **669, 0 failures**.
This is focused validation, not a suite pass: no trace fixtures beyond the SOZ complete run that
the pattern pulled in, and no `run_categories.py` selection.

**Still owed on slice 6**: the two camera releases and `word_78EAA` (`loc_78AA8`/`loc_78B00`/
`loc_78B08`, sonic3k.asm:160509-160528: `Camera_X_pos >= $2C0` sets `Camera_min_X_pos = $2C0` and
swaps `Pal_LRZ2` into line 2 with `Pal_LRZMiniboss3` into line 3; `Camera_X_pos >= $940` sets
`Camera_min_X_pos = $940` and clears `Palette_cycle_counter1`), `LRZ2_BackgroundEvent` stages 0
and 4 (`loc_5700C`/`loc_57040`, :115693-115722), timeline isolation across the change, rewind
spots either side of it, clip `33`, and the matrix rows. Clips `31` and `32` are untouched.

## Handover, 2026-09-18 (eleventh)

**Head `4509d4f25`**, branch `feature/ai-lrz-bring-up`, base develop `035e48a58`. Tree clean;
nothing pushed or merged.

**What landed.** `c83854dc7` the hit finding and `TestLrzMinibossHitPath` (4 tests, two deliberate
breaks); `37c45d1ae`, `805625add`, `4509d4f25` the evidence, the route re-measurement and the
seamless-change spec.

**The hit defect does not exist.** The drill's touch box is `Touch_Sizes` entry 6, 32x32; the
tenth round read the `$33` solid half-width as a size index. The engine lands a hit at native's
own relative offset, and lands one in a real fight capture (`hits 6 -> 5` at `v 972`, the `$20`
window restoring the byte at `v 1004`). Clip `30` is delivered.

**Owed, in the order the next round should take it.**

1. **Clips `31` and `32`.** Neither is delivered. They need an input script that lands six hits;
   the best so far (`inputs/lrz1-miniboss-fight-v7.txt`) lands one, because the arena confines the
   player to `x 11272`-`11560` and the drill tracks to wherever the player stood at `v 901`. Start
   from the three measured facts in the entry above, and note the hit that did land was an
   insta-shield reach, not the ordinary 16 px box. Clip `31` also needs a hand killed
   (`sub_78CF4`, `collision_property 4`).
2. **The standing-still ring loss** (~180 frames, all three tested spots inside the band) with its
   native kill condition. It is what makes a six-hit script hard: two hits and the capture is over.
3. **The act change**, both halves together, from the spec above.
4. **The route.** First divergence re-measured at `37c45d1ae`: engine frame 2324 = native row 2323,
   cause identified as the `Obj_Toxomister` rebound, kill condition in the frontier log.
5. **Slice 6 items 2-5** (stage `$C`, the two camera releases and `word_78EAA`, `LRZ2_BackgroundEvent`
   stages 0/4, the transition class, timeline isolation, rewind spots, clip `33`, matrix rows) are
   **not started**. No reviewer was run.

**Verification at this head.** `-Dtest=TestLrzMinibossHitPath,TestLrzMinibossInstance,
TestEveryObjectRewindRoundTrip,TestRewindHarnessCoverageRatchet,TestS3kLrzPlacementCensus,
TestS3kLrzRouteRewindSpots`: 1191 tests, 0 failures, 0 errors, 0 skips. The four mandatory S3K
classes plus `TestSozMiniboss` were run at `c83854dc7`: 73 tests, 0 failures, 0 skips. This is
focused validation, not a suite pass; no `-Pguards` run was needed (no registry, profile or
rewind-annotation change) and none was made.
