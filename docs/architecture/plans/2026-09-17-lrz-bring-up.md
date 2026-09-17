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

## Status

| Claim | State |
| --- | --- |
| Implemented | Slices 0-1 (placeholder baseline now 205 / 277 / 8 of 609 / 455 / 35 placements): scroll for both playable acts, the shared runtime state and its rewind capture, the events shell, act-keyed scroll registration, `AniPLC_LRZ2` and the `$6E` lava blocks. Present at `035e48a58`: `AnPal_LRZ1/2`, falling intro, breakable rock, `$31` collapsing bridge, shared-object LRZ branches. Absent: events, scroll, custom animated tiles, rock sprites, all other `Obj_LRZ*`, badniks, three bosses, cutscenes, `StartNewLevel` |
| Cold-reachable | Not started |
| Rewind-verified | `LrzZoneRuntimeState` capture/restore round trip only (`TestS3kLrzScrollRegistrationHeadless`); no route spots yet |
| Native behaviour matched | Not started (Sonic + Tails `lrz` frontier frame 208, inherited, re-measured `3418eba6e`) |
| Visually matched | Act 1 parallax and the act-1 lava block inspected as before/after clips; act 2's background is blocked by the direct-`$901` art gap |

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


