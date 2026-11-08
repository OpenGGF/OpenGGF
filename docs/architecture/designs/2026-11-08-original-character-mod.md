# Original playable character mod (2026-11-08)

An example and tutorial mod that adds two brand-new, original playable characters to
Sonic 1, Sonic 2 and Sonic 3 & Knuckles: MICA, a pangolin who stands in for Sonic, and
TALON, a falcon courier who stands in for Tails. Art is generated with GPT Image through Codex
from a supplied base design, then reduced to native Mega Drive constraints. The mod is
a framework demonstration: where the Mod API lacks a seam, this work adds the safe
facade rather than reaching into engine internals.

Status: phase 1 in progress (see [Delivery plan and status](#delivery-plan-and-status)). Mod API line: 0.7 candidate (`mod-api-release-policy.properties`).

## Agreed decisions

The user settled these on 2026-11-08:

1. **Full art is the target**, including every ending and credits sequence. Every
   character presentation surface gets a custom slot. Surfaces may ship on the stock
   fallback first and gain art later.
2. **Route archetypes:** MICA uses `SONIC_AND_TAILS` (with `SONIC_ALONE` when launched
   without a sidekick). TALON uses `TAILS_ALONE` when leading and is the default
   sidekick. No Knuckles-only route, so no glide requirement.
3. **MICA's ability:** the Mutator Lab homing attack is her own move. Instagib is not a
   menu option; it is what the move becomes in Super and Hyper form.
4. **Name text** ("SONIC" on the HUD, results and special-stage messages) is composed
   from each game's own ROM letter set, not baked per game. Letters a game's ROM lacks
   are generated in that game's style and shipped by the mod.
5. **Super form is implemented**, including Hyper where the host game has it. Sonic 1,
   which has no native super form, gets one when all six emeralds are collected; the
   requirement is configurable (see W6).

6. **Two characters, two phases.** Phase 1 delivers MICA with all shared engine work
   and every presentation surface; a stock sidekick may follow her. Phase 2 adds TALON,
   after which MICA & TALON is the default team and each is playable solo.
7. **TALON's ability:** native Tails flight with flapping. Holding down while airborne
   shows the homing target reticle; jump with down held starts a divebomb onto the
   target. Without down held, jump flaps as normal.
8. **Super TALON:** the divebomb becomes the Instagib dive, and each flight flap gives
   twice the native lift. No Super Flickies.

Both characters must be original designs, not existing Sega characters. The approved
concept sheets are `examples/mica-and-talon/concept/mica.png` and `talon.png`.

## What already exists

`docs/modding/characters.md` and `src/test/resources/mods/sample-character-src` cover
owner-tagged identity, factory, physics profile, `.ggfp` playable art and palette, the
airborne `onAbilityActivate` hook, saves, rewind (`captureSubclassRewindState`) and
sidekick creation. The homing attack, its target classification and the Instagib
effects are implemented and audited in all three games
(`2026-10-31-homing-attack-mutator.md`, `game/mutators/HomingAttackRuntime`).

## Workstreams

Sizes are relative (S, M, L, XL). Engine workstreams W1–W6 and the art pipeline W7 can
run in parallel; W8 integrates them.

### W1 — One mod, three games (S)

The sample is `baseGame: s2`. Prove `baseGame: any` registration (the Mutator Lab
model) publishes the character in the S1, S2 and S3K launch rosters and data select,
saves and reloads the canonical key, and falls back cleanly when the mod is disabled.
Deliverable: the skeleton example with a headless acceptance test per game.

### W2 — Canonical animation sheets (L)

`PlayableArtConverter` emits one `idle` animation, and `PlayableSheetMaterializer`
indexes the sheet by the raw native `getAnimationId()`. Native animation numbering
differs per game, and the trivial profile has no speed-driven walk/run timing and no
slope-angle frame selection.

Change: playable sheets name animations by `CanonicalAnimation`. The materializer builds
the host game's `ScriptedVelocityAnimationProfile` through the existing
`AnimationTranslator` and `DonorCapabilities` fallback map, so one sheet serves all
three games with native timing, slope frame strides and fallbacks for missing poses.
The sheet YAML gains an animation table (canonical name, frame list, durations or
speed-driven marker, loop/hold). This is a GGFP format change; the format is
unpublished, so no migration is required.

### W3 — Underwater palette (S–M)

`LevelPlayableArtInitializer.createCharacterPaletteContext` sets only the dry palette
for mod characters. Add an underwater palette to the character contract, either
authored or derived per zone by applying the host's underwater transform to the
character's palette line, and set it on the render context. Covers LZ, CPZ/ARZ/HPZ and
the S3K water zones.

### W4 — Character presentation contract (L)

Status: API foundation delivered; group 1 follow-up in progress on
`feature/ai-mica-presentation2`. ROM readers now inventory all three small HUD
fonts and S3K per-zone title/results-name art. S2 stock Tails-alone results select
Miles/Tails using the native graphics-region flag. Surface wiring, sign spin/backing
contract extensions, packaged MICA presentation assets and gameplay captures remain
pending; group 1 is not complete. The existing owner-bounded supplier, immutable
indexed images, pixel composer and S1/S2 title readers remain the foundation.

Add a `CharacterPresentation` record carried by `CharacterDefinition` (and stored as
GGFP v3 sections), modelled on KiS2's internal seams (`Sonic2ArtOverlays`, the S2
continue `PlayerPresentation`, the S2 ending `Presentation`). Every stock surface asks
the presentation first and keeps its current Sonic/Tails/Knuckles logic as the
fallback. Slots:

| Slot | Consumers |
|---|---|
| Display name (for ROM-font composition) | HUD name tiles, level results, special-stage results, continue screen |
| HUD life icon (2×2 tiles) | S1/S2/S3K HUD |
| 1-up monitor face | S1/S2/S3K monitors |
| Signpost faces | S1 signpost, S2 signpost, S3K end-of-act sign |
| Special-stage sprite sets | S2 half-pipe, S3K Blue Sphere; S1 reuses playable art |
| Bonus-stage art | S3K slot machine (currently swaps in ROM art by class check) |
| Continue poses | S1/S2/S3K continue screens |
| Data select portrait | S3K data select (and engine-added S1/S2 data select) |
| Cutscene sets | S3K ICZ snowboard, ending and credits sequences per game |

**Cutscenes and endings** replace art frame-for-frame against the native frames of the
archetype's own sequence, so ROM choreography and timing stay native. The S1, S2 and S3K
endings and credits are all in scope.

**Name composition.** A per-game composer lays the display name out from that game's ROM
letters. Each composer reports glyph coverage; a letter the ROM lacks comes from a
supplementary glyph the mod supplies in that game's style. The engine extracts ROM
glyphs at pixel level, so a glyph need not be tile-aligned. Letter inventories found so
far:

| Letter set | S1 | S2 | S3K |
|---|---|---|---|
| Large title-card/results font | 21 letters: A–I, K–P, R–U, Y, Z. K and U are in the art but no card uses them (checked by decoding `artnem/Title Cards.nem`). No J, Q, V, W, X. | Full A–Z and `.`: `ArtNem_TitleCard2` is decoded to RAM by `LoadTitleCard0` and letters are picked from it. | Per-zone title-card sets plus the results names SONIC, KNUCKLES, MILES and TAILS. Zone names suggest no J, Q, W or X; to be confirmed by decoding. |
| Small HUD name (fixed 4×2-tile cell) | SONIC | SONIC, TAILS and MILES life counters | SONIC, TAILS, MILES and Knuckles' abbreviated K·T·E life icons; no U (checked by decoding the `HUD Icon` art) |
| Save-screen font | — | — | A–Z (`S3kSaveTextCodec`) |

The HUD cell fits about five letters. A name of five letters or fewer avoids a separate
short HUD form.

**Selection audit.** 44 source files compare character codes to `"sonic"`, `"tails"` or
`"knuckles"`. Classify each as presentation (moves behind the contract) or route/gameplay
gating (keeps using the `behavesLike` archetype). Today S3K mixes the two: a mod
character behaving like Knuckles would show "SONIC" on the HUD but "KNUCKLES GOT
THROUGH" on the results screen.

**Parity side effect.** S2's results object always draws "SONIC GOT THROUGH"
(`ResultsScreenObjectInstance`, frame 0), but the ROM advances the name frame for
Tails alone and again by the region flag, `loc_14102 (s2.asm:27836-27843)`. Routing stock characters through the
same name path should reproduce the ROM, with a test.

### W5 — Homing attack as a character ability (M)

`MutatorPolicy.HomingCharacter` is `{SONIC, TAILS, KNUCKLES}` and the runtime is owned
by `MutatorWorldState`. Promote the runtime to a capability the world owns whether or
not the mutator is enabled, with a `@ModApi` facade a character calls from
`onAbilityActivate`. The facade takes a mode (`HOMING`, `INSTAGIB`) and keeps native hit
resolution, target precedence, the forward-dash fallback and the rewind snapshot. Mutator
Lab becomes one client of the same facade. Super/Hyper form selects `INSTAGIB`
(see W6). Regenerate the Mod API signature pin.

### W6 — Super and Hyper form for mod characters (L)

`CharacterDefinition` rejects `supportsSuperForm` for mod keys, and
`SuperStateController.transformationSupported()` gates on it. Removing the rejection is
not enough: `Sonic3kSuperStateController` picks the form tier by `instanceof Tails` /
`instanceof Knuckles`, so a mod sprite would become Hyper Sonic with Sonic's palettes
and stars.

Add a `SuperFormProfile` to the character contract:

- form tiers the character supports (Super; Hyper in S3K with the Super Emeralds);
- super art: canonical animation sheet for the super poses, including the transform
  animation;
- palette cycles per tier, dry and underwater, as frame tables;
- super physics profile;
- Hyper extras: after-image and star effects, and the Hyper double-jump. For this
  character the Super/Hyper ability is the Instagib homing strike.

Tier selection moves from `instanceof` to the profile. Per game:

- **S2:** Super with seven Chaos Emeralds and 50 rings, native ring drain, music and
  palette timing.
- **S3K:** Super with seven Chaos Emeralds; Hyper with seven Super Emeralds. Any route
  or ending branch that requires Super Sonic must be checked for the character
  (for example the Doomsday sequence on the Sonic route).
- **S1:** has no native super form; today the engine offers one only through
  `CrossGameFeatureProvider`, which needs a donor ROM and supplies Sonic's art. The
  character instead gets a host-side S1 controller driven entirely by its
  `SuperFormProfile`, so no donor ROM is needed. The default gate is all six emeralds
  (`GameStateManager.hasAllEmeralds()` already counts against the game's emerald total)
  plus 50 rings, with S2-style triggering and ring drain. S1 has no super music, so the
  default cue is S1's invincibility music. This is a mod-defined rule, not ROM behaviour,
  and is documented as such.

**Configuration.** Super-form options are declared with the existing `MutatorOption`
model (choices and checkboxes with LIVE/LOAD/LAUNCH scope) and shown on the host
settings screen, so no new UI is needed. Proposed options: super form on/off per game,
the S1 emerald requirement (default 6), the ring requirement (default 50) and the S1
music cue. S2 and S3K defaults reproduce their native rules.

### W7 — Art pipeline: GPT Image to playable sheets (XL, dominant cost)

Native frame counts for scale: S1 Sonic has 88 mapping frames, S2 214, S3K 251
(S3K includes super frames); S3K's Blue Sphere set has 24 and the ICZ snowboard set 13.
With canonical animations (W2) one sheet of about 120–150 normal frames plus the super
set serves all three games.

Pipeline, as a maintained `ggfmod` preprocessing step:

1. **Model sheet.** Generate a turnaround and key poses from the base design with GPT
   Image via Codex. Every later generation is image-to-image against this sheet.
2. **Pose list.** Generate from a manifest that names each canonical animation and its
   frames, including the slope-angle sets for walk and run. Animated output is for
   previewing cycles only; timing comes from the native animation scripts.
3. **Reduce.** Downscale to native size (about 32–48 px tall), quantize to Mega Drive
   colour (3 bits per channel) and one 15-colour palette shared by every frame, the
   icon, signpost faces, portrait and special-stage sets. Super palette cycles are
   authored against this base palette.
4. **Clean up.** Manual pixel cleanup on every frame; consistency across frames is the
   main risk with generated art.
5. **Cut.** Align hotspots (feet on baseline, roll centred), split into mapping pieces
   of at most 4×4 tiles, and stay within the per-frame DPLC tile budget the converter
   reports.
6. **Bake.** `ggfmod convert art --playable` into the v3 sheet with presentation
   sections.

Art is produced in fallback order: core playable sheet, then HUD/1-up/signpost, then
special stages, then super set, then continue/portrait, then cutscenes and endings.

W7 art-lane handoff (2026-11-08, `feature/ai-mica-art`): 96 indexed core frames
and a [repeatable Python/Pillow pipeline](../../../tools/modding/playable_art/README.md).
Source prompts, native pixel patches and `frames.yaml` are under
`examples/mica-and-talon/art/mica/`. The first walk atlas was rejected for weak
strides; the first dive for an exposed leading muzzle. Replacement atlas cells
and pixel patches are recorded in `generated/prompts.json` and `recipe.json`.
Shared-palette, alpha, outline, geometry and deterministic rebuild checks pass.
Slope frames are rigid native-pixel rotations; W2 still owns slope-stride and
runtime contact validation. `HOMING_DIVE` is an explicit custom handoff key,
not a new canonical enum member. No final GGFP or runtime certification yet.

W7 art-lane round 2 handoff (2026-11-08, `feature/ai-mica-art2`):
`art/mica/presentation/presentation.yaml` now describes 18 native-size indexed
images: three 16×16 HUD faces, three 16×16 monitor faces, S1 supplementary M/A
HUD glyphs (8×8 / 6×8), three 48×32 sign faces, six 32×32 angled spin faces,
and the optional 48×32 S2 alternate face. Native mapping/art labels, piece layouts
and palette lines are recorded per surface. Sign images are character overlays;
retain native border/backing/pole and the edge-on frame. The S1 font keeps native
yellow/shadow indices; all character images retain the unchanged shared palette.
The [presentation pipeline](../../../tools/modding/playable_art/README.md#mica-presentation-round-2)
rebuilds them without Java; optional review decodes pinned native art using the
Nemesis oracle under isolated Python. `gilded.yaml` supplies full index tables,
six fade frames, and dry Super loops: S2 nine frames at 8 ticks, S3K three at
7 ticks, following the shipped final-frame-skip branches. Only jade slots 2–6
change. Palette/alpha/dimensions and deterministic rebuild checks pass, and the
presentation comparisons and gold WAIT cycle were inspected at 1× and 4×.
No GGFP or runtime binding is claimed. Underwater/Hyper and transform poses remain.
This lane takes the permitted presentation-first stop: **all 36 slope frames are
unchanged**, still needing hand-drawn stride/contact correction rather than rigid
rotation. Temporary review material is under `/tmp/mica-art2-review-20261108/`,
including a slope before/after sheet explicitly marked unchanged. The 16px face
is simplified and spin dithering still needs native-background gameplay review.

W7 art-lane round 3 handoff (2026-11-08, `feature/ai-mica-art3`):
six head portraits now fill a 14×15 silhouette with cream facial planes, large
white/ink eyes, pink ears and light-over-dark crest rows. Four signs are large
three-quarter busts with grins and peace gloves; six spin frames project the
same face without dither. Separate original framed backing planes supply native
S1 yellow, S2/S3K orange and alternate S2 blue. Their native palette line is 0
(indices 15, 15, 9 and 2 respectively). MICA's palette is unchanged; backing
composition requires a separate available runtime palette line, recorded in
`presentation.yaml`. No GGFP/runtime binding is supplied. Native-side-by-side
1×/4× reviews are in `/tmp/mica-art3-review-20261108/`. Python generation and
palette/dimension checks pass without a Java build. All 36 WALK/RUN `up45`, `steep` and `inverted` frames now use explicit native pixel rows in
`recipe.json`, with redrawn broad-scale heads, eyes, bent legs and coral boots;
flat-cycle stride order is retained. The final pass repairs cut-off gloves, inverted eye placement and sole contact.
The torso/tail seeds are retained; the new crest/eyes, gloves, bent limbs and
boots use native contours. Six side-by-side slope GIFs accompany 1×/4× sheets.
Python palette/alpha/outline/geometry/coverage and byte-for-byte frame rebuild
checks pass. The broader crest scales are simpler than the flat art, arm swing
is restrained, and runtime foot contact remains unverified. The 16px far eye is necessarily
narrow; the small crest has fewer scale separations than the playable head.

W7 art-lane round 4 handoff (2026-11-08, `feature/ai-mica-art4`):
`art/mica/wave3/wave3.yaml` and its hand-authored `build.py` now supply the
18 S2 half-pipe frames, with native per-frame piece layouts and DPLC budgets.
Owning references: `Obj09_MapUnc_34212`, `Obj09_MapRUnc_345FA`, `off_341E4`,
`SSHurt_Animation` and `dword_33AA2` in `docs/s2disasm/s2.asm`.
The sequence is 4 upright, 8 diagonal, 4 horizontal, 2 ball; hurt reuses run
frames with native flips. Run entries last 4 ticks, ball entries 2 ticks.
Character originals retain every shared palette index; optional `-fixed.png`
files quantize to native `Pal_SS` line 1. That fixed line has no jade and cannot
preserve MICA's colours. Binding a character palette remains integration work.
The 1×/4× native comparisons and five sequence GIFs are disposable files in
`/tmp/mica-art4-review-20261108/`. Pixel/palette/layout/aliased-tile/budget and
byte-for-byte rebuild checks pass without Java. Leaning heads were repositioned
after the first review clipped their crests; balls were redrawn inside their
native oval. Scales on the ball are simplified; torso motion is restrained.
No GGFP, stage palette binding or gameplay certification is claimed.
Blue Sphere now supplies every native `Map_SStageSonic_` mapping entry and
`PLC_SStageSonic_` layout from `General/Sprites/Sonic/Map - SStage Sonic.asm`:
12 entries, including transparent frame 0, standing frame 1, seven running
frames 2–8 and three curl frames 9–11. The earlier 24-frame estimate counts
sequence steps, not distinct mapping entries: `byte_91E8` and `byte_91F6` each
select twelve steps with reuse. Turning rotates the stage in `sub_9580`, with
no extra player turning set. Native `Pal_SStage_Main` line 0 quantizations
accompany shared-palette originals. `loc_907E` derives phase from velocity;
GIF timings are illustrative. Three native-side-by-side GIFs and both-size
sheets join the S2 reviews. Lower limbs were redrawn for the narrow mapping
pieces; stride remains restrained and boots are blockier than the core sheet.
The S1 reuse audit confirms `SonicSS_Main` installs `Map_Sonic` and `id_Roll`,
then uses `Sonic_Animate` and `Sonic_LoadGfx`. No dedicated maze set is missing.
`SonAni_Roll` / `SonAni_Roll2` select five normal roll mappings; existing MICA
canonical ROLL has four images. The S1 comparison repeats phase 0 for the fifth
native mapping, explicitly a size/readability comparison rather than certified
native-phase remapping. Two GIFs show the slow/fast native sequence selections.
No new S1 pixels are committed. Native source labels and reuse paths are in the
wave-3 manifest. Runtime W2 binding remains responsible for canonical phases.
Fixed-palette reviews were also added for both special stages. RGB-nearest
quantization was rejected after it turned red boots tan and erased scale values;
the final explicit semantic LUTs retain red boots/white gloves and map jade to
native blues. Original shared-palette files remain unchanged in colour.
**Partial stopping point after item 3:** slot-machine bonus, S1/S2/S3K continue
sets and data-select/team portrait are not delivered. No stage integration,
Java build, merge or push was performed.

W7 art-lane round 5 handoff (2026-11-08, `feature/ai-mica-art5`):
slot-machine replacements now cover all five normal Sonic mappings $96–$9A,
with their native piece layouts, hotspots and PLC budgets in `wave3.yaml`.
`Obj_Sonic_RotatingSlotBonus`, `loc_4B97C`, `sub_4B99E` and `off_4B9C2`
select ordinary host art, not a separate slot-stage sheet. `AniSonic02`/03
repeat $96 between the other four phases; `loc_12A2A` owns speed-driven timing.
Original jade scale rows, tucked cream faces, gloves and coral boots retain the
unchanged shared palette. No fixed-palette variants are generated this round.
Engine lane must load MICA's palette into the player line and bind these native
frame IDs when replacing the host-art swap. Native side-by-side 1×/4× sheets
and a roll GIF are under `/tmp/mica-art5-review-20261108/`. Python layout,
palette, tile-alias and DPLC budget checks pass; no Java build or runtime claim.
Scale motion is restrained and the tuck silhouettes deliberately remain rounded.
Continue sets, data-select portrait/team composition, snowboard and ending inventory
remain pending at this item-1 checkpoint. Byte-identical regeneration of all five
PNGs and the manifest passes. The design-document disassembly citation check
passes. Rebuild instructions are in the playable-art README, round 5 section.

### W8 — Example, tutorial and evidence (M)

- `examples/<character>/` with a build script in the Mutator Lab pattern.
- A `docs/modding` walkthrough from base image to playable character, written as a
  tutorial, plus updates to `characters.md` and the format docs.
- Short demo clips per game (normal play, homing, Super/Hyper Instagib, special stage,
  results).
- Changelog in `CHANGELOG.0.8.prerelease.md`; mark the delivered backlog items in
  `docs/modding/BACKLOG.md` (GGFP v3 HUD icon, data-select portrait, mod super form).

## Fallback rule

Until a slot has art, the surface uses today's behaviour. Name text is never left as
"SONIC" once W4 lands; it always composes from the display name, falling back to the
baked name only when the ROM font lacks a glyph.

## Validation

- Per game: headless acceptance covering registration, launch, save/reload, disabled-mod
  fallback, rewind across the ability and across a super transformation.
- Animation: a test that one sheet resolves walk/run/roll/spring/hurt/death in all three
  games with native speed timing.
- Presentation: pixel tests (shared offscreen context) for the HUD name, results name
  and signpost face in each game, with a negative control showing the stock fallback.
- Stock parity: Sonic, Tails and Knuckles presentation unchanged in every game, apart
  from the S2 Tails results correction.
- Homing facade: the existing `TestHomingAttackMutator` stays green through the
  refactor; add a character-driven case and a Super Instagib case.
- Mod API pin and package tests; S3K guard tests from `AGENTS.md`; change-based smoke
  run against the pre-task develop SHA.

## Characters

| | MICA | TALON |
|---|---|---|
| Species | Pangolin | Peregrine falcon courier |
| Stands in for | Sonic | Tails |
| Normal colours | Jade scales, sand-cream face and belly, coral-red boots, white gloves | Slate blue-grey, cream barred chest, amber goggles, red-and-white shoes, courier satchel |
| Super form | Gilded: gold scales | Solar: white-gold plumage, flame wingtips |
| Generated name letters | S1 HUD: M, A | S1 HUD: T, A, L |

TALON's wings are drawn into his body frames, so he needs no separate appendage sprite
(the engine's Tails tail renderer stays Tails-only).

### TALON engine notes (phase 2)

- 49 source files test `instanceof Tails`, some in gameplay (sidekick CPU, touch
  response, launchers, vacuum tubes, several S3K objects and bosses). Each must move to
  the archetype or a declared capability so a Tails stand-in behaves natively. MICA
  needs no such audit: a non-Tails, non-Knuckles sprite already takes the Sonic branch.
- Tails-only animations: flight, flight carrying, flight tired, swim variants, carry.
- Player-controlled flight is native only in S3K. In S1 and S2 it is a mod-defined
  capability and is documented as such.
- Tails-only presentation: Tails results variants, Tails special-stage sets, the data
  select team portrait, and Tails' ending roles (for example piloting the S2 Tornado).

## Open questions

1. Confirm the S3K large-font inventory by decoding the per-zone title-card art.

## Delivery plan and status

Integration branch: `feature/ai-original-character`, from develop `33c3b8e56`. Lanes
branch from it as `feature/ai-mica-<lane>` and are merged into it from their reports.
Per the user's rule, develop receives the work only at a major milestone.

Phase 1 waves:

1. W2 canonical animation sheets; W5 homing ability facade (designed for TALON's
   divebomb too); W1 three-game skeleton plus W3 underwater palette; W7 art pipeline and
   MICA's core movement set.
2. W4 presentation contract (name composer, HUD, results, signpost, 1-up), then W6
   super form.
3. Remaining W4 surfaces: special and bonus stages, continue, data select, cutscenes,
   endings and credits.

Status log (times UTC):

- 2026-11-08: design agreed; wave 1 launched.
- 2026-11-08: merged W7 core art (`391684f4f`), W1+W3 skeleton and water palettes
  (`5e21ef547`), W2 canonical animation sheets (`59998a446`); MICA placeholder sheet
  given its required WAIT (`838c9743d`). Lead check of the merged tree: the MICA,
  sample-character, sheet, converter and signature tests pass;
  `TestLevelPlayableArtInitializerModArt` has 4 errors (NPE `crossGameFeatures` under
  `mockStatic(CrossGameFeatureProvider)`) that reproduce identically on develop
  `33c3b8e56` when the class runs alone, so they predate this work. Art round 2's
  presentation art was rejected on review (blob-like 16x16 faces, sign faces without
  native composition, noise spin frames, slopes unchanged); round 3 is redoing it.
- 2026-11-08: merged art rounds 2–3 (`8a2351954`: redrawn faces, native-composition
  sign busts, spin frames, all 36 slope frames, Gilded palette), the W4a presentation
  foundation (`62ff3951d`: contract, ROM name composer, S1/S2 title fonts; on-screen
  surfaces still pending in W4b) and the W5 homing facade (`13515a986`). Lead check of
  the merged tree passed: homing (15), name composer, signature pin, Mutator Lab
  package, character registry and MICA integration tests, all ROMs found.
- 2026-11-08: merged W6 Super/Hyper forms (`e4639065d`; conflict with W4a on
  `CharacterDefinition` resolved by carrying both suppliers; 174 focused tests
  passed) and W7c sheet assembly (`e9c8350d8`; MICA plays with her art in S1 GHZ1,
  S2 EHZ1 and S3K AIZ1). Sheet rebuilt from the round-3 slope frames; 39 focused tests
  and the assembler test passed. Also merged art round 4 (S2 half-pipe, Blue Sphere,
  S1 maze audit) and round 5 item 1 (S3K slot stage).
- 2026-11-08: Sol (`gpt-6.1-sol`) reached its usage limit until 2026-11-14 10:38.
  Lanes stopped mid-work: W4c0 signs/assets, W4c1 S1 surfaces (one unverified commit
  `290c96d7c`, not merged), W4c2 S2 surfaces, W4c3 S3K surfaces. Their uncommitted
  work is saved as patches under `~/scratch/agent-tmp/mica-wip-20261108/<lane>/`
  with base SHA and worktree path. Not started: continue/portrait/snowboard art,
  endings inventory and art, MICA ability wiring (homing, Instagib when Super,
  HOMING_DIVE binding, Sonic-equivalent physics), phase 2 (TALON), broad smoke run.

