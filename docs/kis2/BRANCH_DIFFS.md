# Knuckles in Sonic 2 — branch diff catalogue

Every KiS2 implementation item in `com.openggf.game.sonic2.kis2` cites an
entry in this file. Where the branch differs from S&K-native Knuckles
behaviour, the branch wins.

## Provenance

| Item | Value |
|---|---|
| Reference repository | `docs/s2disasm` submodule (sonicretro s2disasm) |
| Branch point | `24f8782` (stock Sonic 2, `gameRevision = 1`) |
| KiS2 head | `c336fed` (`knuckles-in-sonic-2`, `gameRevision = 3`) |
| Diff command | `git diff 24f8782 c336fed -- s2.asm s2.constants.asm s2.lockon.asm` |
| Size | 77 files; `s2.asm` +4,255 lines; 388 `gameRevision=3` blocks; 412 `; KiS2 (...)` tags |
| Lock-on address map | `s2.lockon.asm` (new): `phase $300000` for KiS2 code; S&K labels at `$000000-$1FFFFF`; S2 cart labels at `$200000-$2FFFFF` (S2 REV01 address + `$200000`) |
| Images verified on this machine | `s3k.gen` (S&K half at `0x000000-0x1FFFFF`), `s2.gen` (REV01), and the user-supplied S&K + Sonic 2 lock-on dump (3,407,872 bytes, MD5 `3E5E4B18D035775B916A06F2B3DC5031`; S&K at `0x000000`, S2 at `0x200000`, the 256 KiB chip at `0x300000`), served as logical ROMs `KIS2` / `KIS2_CHIP`. |

The lock-on program targets S2 REV01: every S2 cart label in
`s2.lockon.asm` equals the REV01 address plus `$200000` (for example
`Off_Rings = $2E4300`, `ArtUnc_Sonic = $250000`, `SoundDriverLoad = $2EC000`).

## Tag classification

Counts of `; KiS2 (<tag>)` markers in the `s2.asm` diff. They match the plan
amendment exactly.

| Tag | Count | Meaning | Tier |
|---|---|---|---|
| Knuckles | 120 | Addition of Knuckles (player object, glide/climb, art conversion, layouts, Super Knuckles, HUD/results/continue swaps) | one (gameplay), two (chip assets) |
| no 2P | 102 | Two-player mode removed | n/a (engine has no 2P) |
| title | 54 | New title-screen intro | two |
| bugfix | 32 | Shipped KiS2 behaviour changes (see §Bugfix blocks) | one where the engine has a rule seam; rest catalogued |
| no Tails | 29 | Tails removed | one (faithful roster) |
| ending | 15 | Ending/credits changes | two (ending image is S&K-side, banner is chip) |
| lock-on | 10 | Boot, checksum, sound-driver bank patches for the `$200000` S2 cart | n/a (not emulated) |
| branch | 10 | Branch range extensions | n/a |
| unused | 9 | Dead code removed to fit 256 KiB | n/a |
| mappings format | 9 | S3K 6-byte sprite-piece mappings replace S2's 8-byte format engine-wide | one (Knuckles art is S3K-format already) |
| results | 8 | Special-stage results screen layout | two |
| no options | 1 | Options menu removed | n/a |

## Which image holds what

Source key: **S&K** = S&K half of the S3K image (`0x000000-0x1FFFFF`),
**S2** = Sonic 2 cart (lock-on `$200000-$2FFFFF`, REV01 offset + `$200000`),
**CHIP** = 256 KiB chip at `$300000-$33FFFF` (present only in the
user-supplied lock-on dump; see §Chip addresses). Addresses are the physical
address in the named image; chip addresses are lock-on addresses.

| Data | Owning label | Source | Address |
|---|---|---|---|
| KiS2 object-layout pointer table (34 longwords, `zone*8 + act*4`) | `Off_Objects_KiS2` (S&K disasm: `S2K_Sprite_Lists`) | S&K | `0xDF370` |
| EHZ1, EHZ2, MTZ1, MTZ2, MTZ3, WFZ1, WFZ2 (stub), HTZ1, HTZ2, OOZ1, OOZ2, MCZ1, MCZ2, CPZ1, CPZ2, ARZ1, ARZ2 layouts | `S2KSprite_*` | S&K | see §Object placements |
| HPZ1/2, DEZ1/2, SCZ1/2 layouts | stock `Objects_HPZ_1`… (unchanged) | S2 | `0x2E8C80/0x2E8D94`, `0x2EB230/0x2EB254`, `0x2EBBDE/0x2EBD4C` |
| CNZ1, CNZ2 layouts | `Objects_CNZ_1`, `Objects_CNZ_2` | CHIP | `0x33F06E`, `0x33F74C` |
| Empty layout for unused zone slots | `S2KSprite_NULL` | S&K | `0xE40AE` |
| Knuckles art (4092 tiles, uncompressed, S3K palette indices) | `ArtUnc_Knuckles` | S&K | `0x1200E0` (size `0x1FF80`) |
| Knuckles sprite mappings (S3K format) | `MapUnc_Knuckles` | S&K | `0x14A8D6` |
| Knuckles DPLCs | `MapRUnc_Knuckles` | S&K | `0x14BD0A` |
| Knuckles animation scripts (37) | `SonicAniData` in KiS2 = S&K `AniKnuckles` table | S&K copy at `0x017EF4`; KiS2's own copy is CHIP | see §Animation |
| S2-layout Knuckles palette line | S&K `Pal_KnuxEndPose` | S&K | `0x060BEA` |
| Ending Knuckles image | `ArtNem_EndingKnuckles` (S&K `ArtNem_KnuxEndPose`) | S&K | `0xDEA00` |
| S3K Knuckles life icon (tier-one stand-in) | S&K `ArtNem_KnucklesLifeIcon` | S&K | `0x190E4C` |
| Knuckles lives counter (`ArtNem_Sonic_life_counter`), continue icon (`ArtNem_MiniSonic`), monitor patch (`ArtNem_PowerupsKnucklesPatch`), signpost patch (`ArtNem_SignpostKnucklesPatch`), merged shield+stars (`ArtNem_Shield_and_invincible_stars`) | listed under `PlrList_ResultsTails_Dup_End` "Knuckles in Sonic 2 Assets" | CHIP | see §Chip addresses |
| CNZ slot pictures (`ArtUnc_CNZSlotPicsKnucklesPatch`), special-stage Knuckles frames (`ArtNem_SpecialSonicAndTails`), title-screen art, title-card font K (`ArtUnc_FontK`) | as named | CHIP | unresolved (presentation tier) |
| `Pal_BGND` (`SonicAndTails.bin` with Knuckles colours), `Pal_CPZ_U`, `Pal_ARZ_U` | as named | CHIP | see §Chip addresses |
| `Pal_SS`, `Pal_133EC` (title), `Pal_AC7E`/`Pal_AC9E` (ending), `Pal_KiS2_Ending`, `CyclingPal_SKTransformation`, `CyclingPal_SKRevert` | as named | CHIP | unresolved (presentation tier) |
| `hud_a`, `obj09`, `obj5E`, `obj6F`, `objCF`, `obj0E_*` mappings and all KiS2 code | `mappings/sprite/*` | CHIP | unresolved; `hud_a` is modelled (see §Chip addresses) |

**Palette verification (2026-09-13, `s3k.gen`; 2026-09-14, lock-on dump).**
`Pal_KnuxEndPose` at `0x060BEA` is the S2-layout Knuckles line: indices 0-1
and 6-15 equal S2's `Pal_SonicTails` (`0x29E2`) byte for byte, and indices
2-5 hold Knuckles' `$206/$20C/$080/$64E` exactly where `ArtConvTable` places
S3K colours 4, 3, 5 and 2. The chip's `SonicAndTails.bin` (`Pal_BGND` line 0
at `0x30253E`) **is byte-identical** to it (`TestKis2ChipArt`), so tier one
and tier two draw the same line 0; tier two reads it from the chip because
that is the data the lock-on program loads.

## Chip addresses

Located on the user-supplied lock-on dump on 2026-09-14 by matching each
branch binary (`art/nemesis/*.nem`, `art/palettes/*.bin` at `c336fed`) byte
for byte against the chip window; every hit was unique inside the chip.
Addresses are lock-on addresses (`$300000` + chip offset); the engine
resolves them through `LockOnAddressSpace` from the `KIS2` logical ROM.

| KiS2 label | Branch file | Lock-on address | Size | Loaded by |
|---|---|---|---|---|
| `Objects_CNZ_1` | `level/objects/CNZ_1.bin` | `0x33F06E` | 292 records | `Off_Objects_KiS2[24]` |
| `Objects_CNZ_2` | `level/objects/CNZ_2.bin` | `0x33F74C` | 257 records | `Off_Objects_KiS2[25]` |
| `Pal_BGND` | `SonicAndTails.bin` + `SonicAndTails2.bin` | `0x30253E` | 64 bytes | `PalPtr_BGND` (level lines 0-1); line 1 is stock |
| `Pal_CPZ_U` | `CPZ underwater.bin` | `0x3029BE` | 128 bytes | `PalPtr_CPZ_U` via `PalLoad_Water`; only line 0 differs from stock |
| `Pal_ARZ_U` | `ARZ underwater.bin` | `0x302AFE` | 128 bytes | `PalPtr_ARZ_U`; only line 0 differs from stock |
| `ArtNem_MiniSonic` | `Knuckles continue.nem` | `0x33AAF2` | 12 tiles | continue screen (`loc_10744`), `PlrList_Results` at `ArtTile_ArtNem_MiniCharacter` |
| `ArtNem_Sonic_life_counter` | `Knuckles lives counter.nem` | `0x33AC46` | 12 tiles | `PlrList_Std1` at `ArtTile_ArtNem_life_counter` (HUD and the 1-up monitor face at monitor tile `$154`) |
| `ArtNem_Shield_and_invincible_stars` | `Shield and invincibility stars.nem` | `0x33AD40` | 66 tiles | `PlrList_Std2` at `ArtTile_ArtNem_Shield` (`$4BE`): tiles 0-31 replace `ArtNem_Shield`, 32-65 replace `ArtNem_Invincible_stars` (`$4DE`) |
| `ArtNem_SignpostKnucklesPatch` | `Signpost (Knuckles patch).nem` | `0x33AF4C` | 24 tiles | `PlrList_Signpost` at `ArtTile_ArtNem_Signpost+34` |
| `ArtNem_PowerupsKnucklesPatch` | `Monitor and contents (Knuckles patch).nem` | `0x33B15E` | 8 tiles | `PlrList_Std2` at `ArtTile_ArtNem_Powerups+44` (grey shield and invincibility icons) |

`Ending Knuckles Banner.bin` (`0x3085F6`, also `0x3106E8`), the title, special
stage, results and ending assets and the Super Knuckles cycles are on the chip
too but belong to the presentation tier; their addresses are not resolved here.

**Mappings-format entries.** The branch's `hud_a.asm` diff changes one
thing besides the 6-byte piece format: the lives-name piece (`$10E`, 4x2)
moves from palette line 1 to line 0, so "KNUCKLES" draws with the icon's
line. The engine models that through `Sonic2HudStaticArtFactory`'s
icon-palette layout whenever a patch supplies the life icon
(`TestKis2ChipArt`). `obj09` (special-stage player), `obj5E` (special-stage
HUD, Tails pieces removed), `obj6F` (results) and `objCF` (ending helixes)
are presentation-tier objects and stay catalogued.

Engine mapping: `Kis2Constants` declares every address above; `Kis2ChipArt`
decodes the Nemesis art and palettes through `LockOnAddressSpace.tierTwo`;
`Kis2GameModule` supplies them as `Sonic2ArtOverlays` (life icon plus
`SheetPatch`es mirroring the extra `plreq` entries), a
`Sonic2WaterDataProvider` underwater-palette source and the continue-screen
icon supplier. The chip art is already in Sonic 2's palette layout and is
not passed through `ArtConvTable`.

## Physics constants (`Obj01`)

| Routine | Stock S2 | KiS2 | Engine mapping |
|---|---|---|---|
| `Sonic_Jump` | `#$680`; Super `#$780`; underwater `#$380` (Super `#$480`) | `#$600`; underwater `#$300`; **+`$80` in `Demo_mode_flag` only**; no Super boost | `Kis2Physics.KNUCKLES.jump() = 0x600`; `PhysicsModifiers.KNUCKLES` (`waterJump = 0x300`). Demo offset not modelled (engine has no ROM demo mode). |
| `Obj01_ChkShoes`, `Obj01_InWater`, `Obj01_OutWater`, `Sonic_CheckGoSuper` | Super `$A00/$30/$100`; Super underwater `$500/$18/$80` | Super `$800/$18/$C0`; Super underwater `$400/$C/$60` | tier two (Super Knuckles needs chip palettes); documented |
| Normal speeds | `$600/$C/$80` | unchanged | `SONIC_2_SONIC` values retained in `Kis2Physics.KNUCKLES` |
| `Obj01_Init` radii | `$13/$9` | unchanged | unchanged |
| `Sonic_BalanceOnObjRight/Left`, `Sonic_Balance`, `Sonic_BalanceLeft` | four-state balance (`Balance`..`Balance4`) | single `AniIDSonAni_Balance`; when facing away the code flips `x_flip` toward the edge and writes `anim`/`prev_anim = Balance` with `anim_frame = 4`, `anim_frame_duration = 0` | `Kis2Physics.KNUCKLES.singleFacingBalance() = true` (Tails-style). The frame-4 restart is not modelled (see known-discrepancies). |
| `SuperSonic_Balance*` | present | removed (Super Knuckles has no unique animations) | n/a |
| `Obj01_MdNormal_Checks` | impatient blink / lie-down interrupt | removed | KiS2 profile sets no blink/get-up ids |
| `Sonic_ResetOnFloor_Part2` | rolling: hard-coded `subq.w #5,y_pos` | `y_pos += y_radius - 19` after resetting radii to `19/9` (S3K `Player_TouchFloor` form); also always resets radii | `Kis2Rules.RULES.playerMovement().landing().landingRollClearUsesCurrentYRadiusDelta() = true` |
| `Sonic_ResetOnFloor_Part3` | — | `double_jump_flag = 0`; any `anim >= AniIDKnuxAni_Glide` reset to Walk | engine glide states clear on landing (`PlayableSpriteMovement` glide code) |

## Player object code (glide/climb, dispatch)

KiS2 adds `double_jump_property = $1F` and `double_jump_flag = $21` (SSTs),
`Gliding_collision_flags` and `Disable_wall_grab` (RAM), and moves
`Ending_Routine`. Dispatch:

- `Obj01_Control`: when `obj_control` bit 0 is set, `double_jump_flag` is cleared before skipping control.
- `Obj01_MdAir`: `tst.b double_jump_flag; bne Obj01_MdAir_Gliding` (new) →
  `Knuckles_GlideSpeedControl`, `Sonic_LevelBound`, `ObjectMove`, `Knuckles_GlideControl`.
- `Sonic_JumpHeight`: the "release jump early" cap is followed by
  `Sonic_CheckGoSuper` unconditionally (`ble.w Sonic_CheckGoSuper`), which
  now also owns glide entry: a fresh A/B/C press while `double_jump_flag = 0`
  (and not in a demo) either transforms (7 emeralds, ≥ 50 rings, timer
  running) or enters `Knuckles_BeginGlide` (`double_jump_flag = 1`, radii
  `10/10`, `y_vel += $200` clamped at 0, `inertia = $400`, angle 0,
  `double_jump_property = 0` or `-$80` when facing left).

**Answer to plan question 2:** `Sonic_JumpHeight` behaves like stock S2
except that Super transformation no longer requires `y_vel = 0` at the apex;
it and gliding are both triggered by a second jump press. The existing
`PhysicsFeatureSet.SONIC_2` is reused unchanged; `SecondaryAbility.GLIDE`
supplies glide/climb.

### Glide/climb block versus S3K

`Knuckles_GlideControl`, `Knuckles_GlideSpeedControl`,
`Knuckles_DoLevelCollision2`, `Knuckles_DoGlidingAnimation`,
`Knuckles_DoLedgeClimbingAnimation`, `GetDistanceFromWall` and the
`*_WithRadius` collision probes are a near-verbatim port of S3K's
`Knuckles_Glide`/`Knux_Climb`/`Knux_DoLevelCollision` block. Deviations:

| Site | KiS2 | S3K original |
|---|---|---|
| `Disable_wall_grab` | tested in `Knuckles_BeginClimb` and `Knuckles_Climbing_Wall` but **never written** (leftover; always 0) | written by boss/cutscene code |
| Radii during glide | `Knuckles_GlideControl` (normal glide), `.continueSliding` and `Knuckles_Climbing_Wall` write `y_radius/x_radius = 10/10` before their collision call and restore `19/9` after ("These two lines are not here in S3K") | radii set once at glide entry |
| Sounds | `sfx_Grab`, `sfx_GlideLand`, `sfx_GroundSlide` calls removed ("This sound does not exist in Sonic 2") | played |
| Hyper wall impact | `Super_Sonic_flag` + `inertia >= $480` path is a `nop` (no `Glide_screen_shake`, no `HyperAttackTouchResponse`, no `sfx_Thump`) | quake + attack |
| `Knuckles_Climbing_Wall .finishMoving` | the S3K "detach if floor directly below" probe (`CheckFloorDist_WithRadius`) is absent (`if 0` block in the branch) | present (and buggy: clobbers `d1`) |
| Slide dust | `Obj08_CheckSkid` spawns dust when `double_jump_flag = 3` with y offset 6 (16 for the stop skid) | S3K `DustPuff`/`Obj_Knuckles_Slide` equivalent |
| Monitor/enemy contact | `Touch_Monitor` and `Touch_Enemy` accept `double_jump_flag` 1 or 3 as attacking; `Touch_Enemy_Part2` knocks an active glide (flag 1) into fall-from-glide (flag 2), facing by `x_vel` sign, radii `19/9` | same shape (`Touch_Enemy` in S3K) |
| Exit points | `loc_270DC` (springs), `loc_2A990` (CNZ cages/bumpers), `ObjB5_CheckPlayer` (WFZ launch) clear `double_jump_flag`; `ObjB5_CheckPlayer` also clears `Status_RollJump` | equivalent per-object clears |
| WFZ Tornado cutscene (`ObjB2`) | re-timed (`$20` instead of `$30` wait), held inputs changed to press-only so Knuckles cannot glide, control locked above `y = $540` | n/a |
| Speed control | identical to S3K including Super glide bonus and `Camera_Y_pos_bias` drift to `$60` | — |

Engine note: `PlayableSpriteMovement`'s glide is the S3K model (grab/land
sounds through `GameSound.GRAB`/`GLIDE_LAND`, which the S2 sound map does
not contain so they are silent on the S2 host; the S3K floor-below detach
probe is implemented). The radius toggling and the missing detach probe are
recorded in `docs/status/known-discrepancies.md`, not modelled.

## Animation (`Sonic_Animate`, `SonicAniData`)

- `SonicAniData` is replaced by the 37-entry Knuckles table
  (`KnucklesAni_*`): entries 0-`$1F` occupy S2's `SonAni_*` slots and
  `$20`-`$24` add Glide, FallAfterGlide, ClimbLedge, LandAfterGlide, ShadowBox.
  Scripts are byte-identical to S&K `AniKnuckles` (S&K `0x017EF4`, 37
  scripts), so tier one loads them from the S&K half.
- `SAnim_Do2`: end-of-script flag test is `cmpi.b #$FC` (was `$F0`) because
  Knuckles uses frames above `$F0`.
- `SAnim_Tumble`/`loc_1B572`: tumble base frame `$31` (was `$5F`).
- `SAnim_Push`: delay `lsr.w #8` (was `#6`).
- `SAnim_WalkRun`: run threshold `$600`, sliding doubles the animation speed
  (stock S2 code retained); Super walk/run branches removed.
- `Sonic_Animate`: no `SuperSonicAniData` swap.
- `Touch_Rings`, `Check_CNZ_bumpers`, `TouchResponse`: ducking touch-box
  frame is `$9C` (Knuckles duck frame); `Touch_Boss` still tests `$4D`
  ("looks like they forgot to update this one").
- `LoadTitleCard0`: title-card art tiles `+$5A..+$5B` are overwritten with
  colour 4 (green background); `loc_140AC` uploads `ArtUnc_FontK` (chip).

Engine mapping: `Kis2PlayerArt` builds the profile from S2's host flags
(`anglePreAdjust`, sliding double speed, push walk handler) with Knuckles'
values (push shift 8, tumble base `$31`, no blink/get-up/Super ids) and the
S&K animation set. `Kis2Rules.RULES.objectInteraction().duckTouchBoxMappingFrame() = 0x9C`
(the engine has one field, so `Touch_Boss` also uses `$9C`; discrepancy).

## Art, mappings, DPLC and the boot-time conversion

- `Obj01_Init`, `ObjDB_Sonic_Init`, `Debug_ExitDebugMode` point `mappings`
  at `MapUnc_Knuckles` (S&K). `art_tile` stays `ArtTile_ArtUnc_Sonic`
  (`$780`) so Knuckles occupies Sonic's VRAM slot.
- `LoadSonicDynPLC_Part2` (KiS2, lock-on build): walks `MapRUnc_Knuckles`
  (S&K) and copies each tile of `ArtUnc_Knuckles` (S&K) through
  `ArtConvTable` into `Knuckles_Art_Conversion_Buffer` (RAM), then DMAs the
  buffer to `ArtTile_ArtUnc_Sonic`. This is the boot/DPLC-time conversion
  from S3K palette indices to S2 layout. The conversion table
  (`KPLC_ConvertArtFromS3K`) is:
  `$0→$0 $1→$6 $2→$5 $3→$3 $4→$2 $5→$4 $6→$C $7→$D $8→$E $9→$F $A→$A $B→$B $C→$7 $D→$8 $E→$9 $F→$1`.
  Engine: `Kis2Constants.ART_CONV_TABLE`, applied once to the loaded tile set
  in `Kis2PlayerArt`.
- `SonicMappingsVer := 3`: every sprite mapping in the game is S3K-format
  (6-byte pieces). `DrawSprite_*`, `BuildRings_Loop`, `Obj1A_CreateFragments`,
  `BreakObjectToPieces_Loop`, `loc_25C1C` skip the 2P art-tile word.
  Engine: no change; the engine's S2 object mappings are parsed from the S2
  cart (stock format), the Knuckles art from S&K (S3K format).
- `PlrList_Std2`: `ArtNem_Shield`/`ArtNem_Invincible_stars` replaced by the
  merged grey `ArtNem_Shield_and_invincible_stars` and a monitor-icon patch
  (`ArtNem_PowerupsKnucklesPatch` at `ArtTile_ArtNem_Powerups+44`) — CHIP.
- `PlrList_Signpost`: `ArtNem_SignpostKnucklesPatch` at
  `ArtTile_ArtNem_Signpost+34` — CHIP.
- `ArtNem_Sonic_life_counter` → "Knuckles lives counter.nem"; `ArtNem_MiniSonic`
  → "Knuckles continue.nem" — CHIP. Tier one substitutes the S&K
  `ArtNem_KnucklesLifeIcon` (`0x190E4C`) through the same index conversion,
  as donation already does (`Sonic2ObjectArtProvider.loadS3kKnucklesLivesPatterns`).
- `SlotMachine_GetPixelRow`: slot picture id 0 reads
  `ArtUnc_CNZSlotPicsKnucklesPatch` — CHIP (tier two, CNZ).
- `Obj61_Init` (special-stage bombs) uses palette line 2; `LoadSSSonicDynPLC`
  rewritten to standard DPLCs over `Obj09_MapRUnc_345FA` — CHIP (tier two).

**Answer to plan question 3:** the S&K-side addresses agree with
`Sonic3kConstants`: `ART_UNC_KNUCKLES_ADDR = 0x1200E0`,
`MAP_KNUCKLES_ADDR = 0x14A8D6`, `DPLC_KNUCKLES_ADDR = 0x14BD0A`,
`KNUCKLES_ANIM_DATA_ADDR = 0x017EF4` (the KiS2 copy of the scripts lives on
the chip but is byte-identical). `Kis2Constants` declares KiS2's own set.

## Object placements

`ObjectsManager_Init` (KiS2, lock-on build): `d0 = (Current_ZoneAndAct ror.b 1) >> 5`
= `zone*8 + act*4`; `lea (Off_Objects_KiS2).l,a0; movea.l (a0,d0.w),a0`.
The pointers are absolute lock-on addresses. The 2P CNZ branch is removed.

Counts are 6-byte records up to the `$FFFF` terminator, measured on
`s3k.gen`/`s2.gen` on 2026-09-13. The S2 cart pointers equal the stock
`Off_Objects` (`0xE6800`) targets plus `$200000`, so those acts are
byte-identical to stock.

| Index | Act | KiS2 pointer | Source | KiS2 records | Stock records |
|---|---|---|---|---|---|
| 0 | EHZ1 | `0xDF3FE` | S&K | 157 | 135 |
| 1 | EHZ2 | `0xDF7B2` | S&K | 175 | 158 |
| 2-7 | unused zones | `0xE40AE` | S&K | 0 | 0 |
| 8 | MTZ1 | `0xDFBD2` | S&K | 193 | 193 (content differs) |
| 9 | MTZ2 | `0xE005E` | S&K | 224 | 220 |
| 10, 11 | MTZ3 (zone 5, both acts) | `0xE05A4` | S&K | 273 | 270 |
| 12 | WFZ1 | `0xE0C10` | S&K | 170 | 157 |
| 13 | WFZ2 (stub) | `0xE1012` | S&K | 0 | 0 |
| 14 | HTZ1 | `0xE1018` | S&K | 148 | 144 |
| 15 | HTZ2 | `0xE1396` | S&K | 285 | 259 |
| 16, 17 | HPZ1, HPZ2 | `0x2E8C80`, `0x2E8D94` | S2 | 45, 0 | 45, 0 |
| 18, 19 | unused | `0xE40AE` | S&K | 0 | 0 |
| 20 | OOZ1 | `0xE1A4A` | S&K | 204 | 189 |
| 21 | OOZ2 | `0xE1F18` | S&K | 202 | 190 |
| 22 | MCZ1 | `0xE23DA` | S&K | 131 | 130 |
| 23 | MCZ2 | `0xE26F2` | S&K | 152 | 148 |
| 24 | CNZ1 | `0x33F06E` | CHIP | 292 (measured on the dump, 2026-09-14) | 286 |
| 25 | CNZ2 | `0x33F74C` | CHIP | 257 (measured on the dump, 2026-09-14) | 254 |
| 26 | CPZ1 | `0xE2A88` | S&K | 189 | 153 |
| 27 | CPZ2 | `0xE2EFC` | S&K | 249 | 202 |
| 28, 29 | DEZ1, DEZ2 | `0x2EB230`, `0x2EB254` | S2 | 5, 0 | 5, 0 |
| 30 | ARZ1 | `0xE34D8` | S&K | 200 | 182 |
| 31 | ARZ2 | `0xE398E` | S&K | 303 | 222 |
| 32, 33 | SCZ1, SCZ2 | `0x2EBBDE`, `0x2EBD4C` | S2 | 60, 0 | 60, 0 |

Start positions (`StartLocations`) are unchanged: the diff touches no
`StartLoc` data. Ring layouts (`Off_Rings = $2E4300`) are the stock S2 cart
data.

Engine mapping: `LockOnAddressSpace` resolves a pointer by window
(`[0,0x200000)` → logical S&K reader, `[0x200000,0x300000)` → S2 reader at
`address - 0x200000`, `[0x300000,0x340000)` → the chip window of the `KIS2`
logical ROM when the dump is available, otherwise unavailable);
`Kis2ObjectPlacement` reads the table through it and, without the chip,
falls back to the stock S2 list for the CNZ pointers with one logged warning.

## Monitor and life-icon swaps

- HUD life counter: `ArtNem_Sonic_life_counter` now names the chip's
  "Knuckles lives counter.nem" (loaded by `PlrList_Std1` at
  `ArtTile_ArtNem_life_counter`); `hud_a.asm` mapping puts "K.T.E" on palette
  line 0.
- 1-up monitor face: `ArtNem_PowerupsKnucklesPatch` overwrites
  `ArtTile_ArtNem_Powerups+44` (`PlrList_Std2`). `tails_1up` is aliased onto
  `sonic_1up` (`Obj26`), so both monitor subtypes give the main character a
  life.
- Continue screen: `ArtNem_MiniSonic` → "Knuckles continue.nem";
  `ObjDB_Sonic_Init` uses `MapUnc_Knuckles` and `AniIDKnuxAni_ShadowBox`;
  `ObjDB_Sonic_StartRunning` uses Walk; no Tails object.
- Signpost: `ArtNem_SignpostKnucklesPatch` at `ArtTile_ArtNem_Signpost+34`.
- Debug lists: monitor default subtype 4 (super ring) instead of 8 (teleport);
  all per-zone debug lists blanked to `DbgObjList_Def`.

**Answer to plan question 4:** all four swap assets are CHIP-resident
(§Chip addresses). Tier two reads them from the dump; tier one uses the S&K
`ArtNem_KnucklesLifeIcon` (`0x190E4C`) converted through `ArtConvTable` for
the HUD and the 1-up monitor face and keeps the stock signpost, continue
icon and shield/stars.

## Bugfix blocks (32, shipped KiS2 behaviour)

Modelled as KiS2 rules or catalogued; never `fixBugs` toggles.

| Owning label | Change | Tier-one status |
|---|---|---|
| `WindTunnel` (2) | clears `Status_RollJump` and `double_jump_flag` on entry; holding Up cannot raise `y_pos` above `windtunnel_min_y_pos` | catalogued (S2 wind-tunnel code is engine-shared; discrepancy) |
| `WindTunnelsCoordinates` | first tunnel `$1510,$420,$1AF0,$580` (was `$400`) | catalogued (table lives on chip) |
| `MenuScreen_LevelSelect`, `LevelSelect_Main` | loads `PLCID_Std1` and runs `RunPLC_RAM` | tier two (level select) |
| `CheckCheats` | continue-code jingle plays (REV02 form) | tier two |
| `loc_A53A`, `loc_3AB18`, `ObjB2_Waiting_animation` | write `anim = Wait, prev_anim = Walk` instead of `mapping_frame/anim_frame/anim` longword | catalogued (cutscenes) |
| `SwScrl_EHZ` | bottom two H-scroll lines written | catalogued |
| `Obj26_Init` | debug-spawned monitors get a respawn entry | catalogued (debug placement) |
| `Obj34_MoveTowardsTargetPosition` (2) | signed compare (`bgt`) and off-screen display gate | catalogued (title card) |
| `SpecialCNZBumpers_Act1` | leading boundary marker present | tier two (CNZ) |
| `SolidObject_ChkBounds` | the `cmpi.w #4,d1 / bls` test moved before `SolidObject_LeftRight` so near-top/bottom contacts branch to `SolidObject_TopBottom` | catalogued (shared solid-object code; discrepancy) |
| `SolidObject_InsideBottom` (2) | airborne inside-bottom contact zeroes `inertia`; `ObjID_FallingPillar` squashes when grounded | catalogued |
| `Obj01_CheckWallsOnGround`, `loc_1A6A8` | pushing flag only set when facing the wall | catalogued (discrepancy) |
| `Sonic_TurnLeft`, `Sonic_TurnRight` | `fixBugs` angle-band check enabled (no skid on steep slopes) | catalogued (discrepancy) |
| `Sonic_ChgJumpDir` (2), `Tails_ChgJumpDir` (2) | S1 air speed cap removed outside demos: a speed already above `Sonic_top_speed` is kept, not clamped | catalogued (discrepancy) |
| `Sonic_CheckGoSuper` | `Super_Sonic_frame_count = 60` on transform | tier two (Super Knuckles) |
| `Obj03` | plane switchers drawn in debug mode | catalogued (debug) |
| `Obj74_Main` | REV00 debug-visible invisible block restored | catalogued (debug) |
| `Obj7F_Action` (2) | player pinned to the object every frame while held | catalogued |
| `Obj57_Main_SubA` | MCZ boss writes `Boss_AnimationArray+0` via `a1` | catalogued |
| `ObjB5_CheckPlayer` | clears `Status_RollJump` on launch | catalogued |

## Miscellaneous (`KiS2:` untagged-category blocks)

- `Obj79_LoadData`: `Ring_count`/`Extra_life_flags` are **not** cleared on
  checkpoint respawn ("responsible for making the player respawn with
  rings"). Catalogued as a discrepancy (engine `CheckpointState` clears rings
  per stock).
- `SpecialStage_RingReq_Alone`: requirements lowered
  (`30,70,130,110 / 50,90,130,130 / 50,100,140,160 / 40,90,140,150 / 40,80,130,130 / 70,130,170,170 / 50,100,140,140`);
  `SpecialStage_RingReq_Team` removed. Tier two (table is on the chip).
- Cheat codes terminated with `$FF` and changed (`TailsNameCheat` enables
  level select; debug code `1,9,9,4,1,0,1,8`; Super code `1,6,7,7,7,2,1,6`).
- `Level_SetPlayerMode`: `Player_mode` forced to 1 (Sonic alone) — the
  no-Tails enforcement, with `ObjPtr_Tails`/`ObjPtr_TailsTails = ObjNull`,
  `Sidekick` never spawned (`Obj02`, `Obj05`, continue-screen Tails, results
  Tails, `Tails_ResetOnFloor_Part2` dispatch all removed).
  **Answer to plan question 6.** Engine: faithful roster default = Knuckles
  alone through the launch profile's standard pair; a configured sidekick is
  honoured (documented divergence).
- `Demo_EHZ` inputs re-timed for 1P; `MoveDemo_On_P1` keeps the S1 held-input
  bug on purpose.
- `DebugObjectLists` blanked; `V_Int` gains a `nop`; checksum dummied out;
  padding byte `$FF`.

## Deferred (presentation tier)

Tier two (2026-09-14) delivered the in-level chip data: CNZ layouts, the
lives counter, monitor, signpost and shield/stars patches, the continue icon,
`Pal_BGND` line 0 and the CPZ/ARZ underwater palettes. Still catalogued:
title screen (`Obj0E_*`, `Obj0F`, `TitleScreen*`), level select/menu
removal, special stage (`Obj09` DPLCs, `Obj61`, `Obj63`, `SSHUD`, `obj5E`,
ring requirements), results (`Obj6F_Knuckles`, `EOL_Sonic` "KNUCKLES GOT"),
ending (`EndgameCredits` banner, `objCF`, `Pal_KiS2_Ending`, flicky
selection by emerald count), Super Knuckles (`PalCycle_SuperSonic`, speeds),
the continue screen's Knuckles player object (`ObjDB_Sonic_Init` with
`MapUnc_Knuckles` and `AniIDKnuxAni_ShadowBox`), CNZ slot pictures
(`SlotMachine_GetPixelRow`), and KiS2 trace fixtures (require the
user-supplied lock-on dump in BizHawk).

## Presentation and Super implementation (2026-09-14)

The chip-backed providers now consume the title, special-stage/results and
ending data catalogued above. Source addresses were verified by assembling
`c336fed` with `gameRevision=3`, `fixBugs=0`: its chip output matches the
user-supplied dump byte for byte. Runtime code reads only logical ROM data.
`Kis2SpriteMappings` decodes the chip's six-byte pieces; reusing stock S2's
eight-byte parser was rejected after the new art-boundary tests exposed it.

| Owner | ROM bytes / behavior | Implementation |
| --- | --- | --- |
| `PalCycle_SuperSonic` | `CyclingPal_SKTransformation` $301EE0 (60 bytes), revert $301F1C (6 bytes); colours 2/3/5 | `Kis2SuperStateController` |
| `Knuckles_TurnSuper` | $800/$18/$C0; seed `Super_Sonic_frame_count=60`; retains jump and animation set | `Kis2Physics.SUPER_KNUCKLES`, controller |
| `EOL_Sonic`, `loc_140AC` | eleven-piece KNUCKLES GOT at $311BF6; four FontK tiles at $312096 written to VRAM $5C6 | `Kis2ResultsArt` |
| `Obj09`, `Pal_SS` | art $33B3F0; mappings $32D410; DPLC $32D728; palette $302CBE | `Kis2SpecialStageDataLoader` |
| `SpecialStage_RingReq_Alone`, `Obj6F` | targets $3071D2; mappings $311D22; results letters $307284 | KiS2 special-stage loader/provider |
| `ObjDB_Sonic_Init` | Knuckles mappings and shadow-box $24, then walk; no Tails | `Kis2ContinuePresentation` |
| `EndgameCredits`, `ObjCF` | SK art $0DEA00; chip palette $3090BC; mappings $3091E0; postcredits banner data | `Kis2EndingPresentation` |
| `TitleScreen`, `Obj0E` | chip artwork, palettes, mappings and Knuckles/hand/emblem/banner sequence | `Kis2TitleData`, `Kis2TitleAnimation`, `Kis2TitleScreen` |

`PalCycle_SuperSonic` runs once per pass. A transformation-completion pass must
not also decrement the active-cycle timer through the shared controller's
ring-work call; the controller explicitly keeps those two responsibilities
separate. Sparkles remain object-owned across rewind; revert resolves the live
objects by owning playable rather than retaining a stale recreated reference.

Remaining scope and validation limits are in the linked completion plan and
known-discrepancies entry, which supersede the earlier deferred-status prose.
