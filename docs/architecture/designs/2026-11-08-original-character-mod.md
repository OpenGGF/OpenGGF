# Original playable character mod (2026-11-08)

An example and tutorial mod that adds two brand-new, original playable characters to
Sonic 1, Sonic 2 and Sonic 3 & Knuckles: MICA, a pangolin who stands in for Sonic, and
TALON, a falcon courier who stands in for Tails. Art is generated with GPT Image through Codex
from a supplied base design, then reduced to native Mega Drive constraints. The mod is
a framework demonstration: where the Mod API lacks a seam, this work adds the safe
facade rather than reaching into engine internals.

Status: phase 1 code-complete; phase 2 TALON playable art, Solar palettes and
down-held reticle/divebomb wiring implemented. Evidence gaps and
follow-ons are listed under [Open items](#open-items); see also
[Delivery plan and status](#delivery-plan-and-status). Mod API line: 0.7 candidate
(`mod-api-release-policy.properties`).

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
concept sheets are `examples/mica-and-talon/concept/mica.png` and `talon.png`; the
[character reference](../../../examples/mica-and-talon/concept/README.md) describes them
and keeps the four-candidate selection sheet and brief.

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

Current status: delivered — `baseGame: any` registers MICA in all three hosts; the example
acceptance covers identity, launch, save retention and disabled-owner fallback.

Original starting point: the sample was `baseGame: s2`. Prove `baseGame: any` registration (the Mutator Lab
model) publishes the character in the S1, S2 and S3K launch rosters and data select,
saves and reloads the canonical key, and falls back cleanly when the mod is disabled.
Deliverable: the skeleton example with a headless acceptance test per game.

### W2 — Canonical animation sheets (L)

Current status: delivered — GGFP v2 canonical animation tables bind to host timing, slopes and
fallbacks. WAIT is mandatory; HOMING_DIVE is now a canonical enum member.

Original gap: `PlayableArtConverter` emitted one `idle` animation, and `PlayableSheetMaterializer`
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

Current status: delivered — optional authored water palettes and live host-zone derivation,
with dry/water palette isolation and real LZ/CPZ/HCZ acceptance inputs.

Original gap: `LevelPlayableArtInitializer.createCharacterPaletteContext` set only the dry palette
for mod characters. Add an underwater palette to the character contract, either
authored or derived per zone by applying the host's underwater transform to the
character's palette line, and set it on the render context. Covers LZ, CPZ/ARZ/HPZ and
the S3K water zones.

### W4 — Character presentation contract (L)

Current status: delivered through code-supplied `CharacterPresentation` and
bounded indexed PNG assets. All three hosts consume HUD/name, results, 1-up,
signs, continue and ending sets. S1 reuses the canonical roll sheet; S2 half-pipe,
S3K Blue Sphere and slot-machine player sets use native-frame slots. Data select
uses the portrait, life icon and miniature continue figure, including NO SAVE
solo/team refresh. ICZ uses thirteen rider frames. Native choreography, poles,
vehicles, companions and palette ownership remain with their host. Full route
captures and the excluded surfaces are listed under Open items.

**Stage palette ownership audit.** Preserve all four native stage palette lines;
only the custom player resolves to a private render-context palette row. This is
an engine presentation extension, not an attempt to simulate extra CRAM.
S1 loads the whole special-stage palette, not just Sonic's colours, and its maze
and background continue to use those native lines. S2 `SSInitPalAndData`
(s2.asm:10232-10250) loads `PalID_SS`; the stage's sprite assembly also shares
player lines with messages/hand/wing art. S3K copies `Pal_SStage_Main` over the
whole target palette at `loc_8284 (sonic3k.asm:10650-10665)` and only overwrites
Knuckles' subset. Blue Sphere's red spheres use player line 0, so replacing it
would recolour the field. Slots retains native player line 0 and stage palette
cycling (`AnPal_Slots (sonic3k.asm:3876-3892)` updates line 3). Private player rows
leave every native colour, background, object and stock companion untouched;
no fixed-native recolouring or quantization is used.


`CharacterPresentation` is carried by `CharacterDefinition` (GGFP presentation
sections remain pending), modelled on KiS2's internal seams (`Sonic2ArtOverlays`, the S2
continue `PlayerPresentation`, the S2 ending `Presentation`). Every stock surface asks
the presentation first and keeps its current Sonic/Tails/Knuckles logic as the
fallback. Slots:

| Slot | Consumers |
|---|---|
| Display name (for ROM-font composition) | HUD name tiles, level results, special-stage results |
| HUD life icon (2×2 tiles) | S1/S2/S3K HUD |
| 1-up monitor face | S1/S2/S3K monitors |
| Signpost faces | S1 signpost, S2 signpost, S3K end-of-act sign |
| Special-stage sprite sets | S2 half-pipe, S3K Blue Sphere; S1 reuses playable art |
| Bonus-stage art | S3K slot-machine player (reel symbols remain separate) |
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
| Large title-card/results font | 21 letters: A–I, K–P, R–U, Y, Z. K and U are in the art but no card uses them (checked by decoding `artnem/Title Cards.nem`). No J, Q, V, W, X. | Full A–Z and `.`: `ArtNem_TitleCard2` is decoded to RAM by `LoadTitleCard0` and letters are picked from it. | Per-zone title-card sets plus the results names SONIC, KNUCKLES, MILES and TAILS. Decoded per-zone inventories and results banks determine coverage; supplement missing letters by host/style. |
| Small HUD name (fixed 4×2-tile cell) | SONIC | SONIC, TAILS and MILES life counters | SONIC, TAILS, MILES and Knuckles' abbreviated K·T·E life icons; no U (checked by decoding the `HUD Icon` art) |
| Save-screen font | — | — | A–Z (`S3kSaveTextCodec`) |

The HUD cell fits about five letters. A name of five letters or fewer avoids a separate
short HUD form.

**Selection audit (original design).** 44 source files compare character codes to `"sonic"`, `"tails"` or
`"knuckles"`. Classify each as presentation (moves behind the contract) or route/gameplay
gating (keeps using the `behavesLike` archetype). The original S3K presentation mixed the two: a mod
character behaving like Knuckles would show "SONIC" on the HUD but "KNUCKLES GOT
THROUGH" on the results screen.

**Parity side effect (delivered).** S2's results object previously always drew "SONIC GOT THROUGH"
(`ResultsScreenObjectInstance`, frame 0), but the ROM advances the name frame for
Tails alone and again by the region flag, `loc_14102 (s2.asm:27836-27843)`. The shared name path now selects the region-correct Miles/Tails name for Tails alone, with regression coverage.

### W5 — Homing attack as a character ability (M)

Current status: delivered — the world-owned `HomingAttack` facade supports normal/Instagib
strikes, untargeted dash, target-only admission, requested reticle and flight
divebomb. MICA uses it directly, with world-owned rewind state.

Original design context: `MutatorPolicy.HomingCharacter` is `{SONIC, TAILS, KNUCKLES}` and the runtime was owned
by `MutatorWorldState`. Promote the runtime to a capability the world owns whether or
not the mutator is enabled, with a `@ModApi` facade a character calls from
`onAbilityActivate`. The facade takes a mode (`HOMING`, `INSTAGIB`) and keeps native hit
resolution, target precedence, the forward-dash fallback and the rewind snapshot. Mutator
Lab becomes one client of the same facade. Super/Hyper form selects `INSTAGIB`
(see W6). Regenerate the Mod API signature pin.

### W6 — Super and Hyper form for mod characters (L)

Current status: delivered — owner-bound `SuperFormProfile`, host controllers, authored palette
cycles/physics, optional animation/effects, rewind and LOAD-scoped Gilded settings.
MICA keeps her own sheet, uses WAIT to transform, and opts out of trails/stars.

Original gap: `CharacterDefinition` rejected `supportsSuperForm` for mod keys, and
`SuperStateController.transformationSupported()` gated on it. Removing the rejection
was insufficient: `Sonic3kSuperStateController` picked the form tier by `instanceof Tails` /
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
- **S1:** has no native super form; the original engine path was
  `CrossGameFeatureProvider`, which needed a donor ROM and supplied Sonic's art. The
  character instead gets a host-side S1 controller driven entirely by its
  `SuperFormProfile`, so no donor ROM is needed. The default gate is all six emeralds
  (`GameStateManager.hasAllEmeralds()` already counts against the game's emerald total)
  plus 50 rings, with S2-style triggering and ring drain. S1 has no super music, so the
  default cue is S1's invincibility music. This is a mod-defined rule, not ROM behaviour,
  and is documented as such.

**Configuration.** Super-form options are declared with the existing `MutatorOption`
model (choices and checkboxes with LIVE/LOAD/LAUNCH scope) and shown on the host
settings screen, so no new UI is needed. Delivered options: super form on/off per game,
the S1 emerald requirement (default 6), the ring requirement (default 50) and the S1
music cue. S2 and S3K defaults reproduce their native rules.

### W7 — Art pipeline: GPT Image to playable sheets (XL, dominant cost)

Native frame counts for scale: S1 Sonic has 88 mapping frames, S2 214, S3K 251
(S3K includes super frames); the ICZ snowboard set has 13.
The original estimate was 120–150 normal frames plus a Super set. The delivered
sheet uses 98 frames; Gilded reuses its mappings. Blue Sphere has twelve distinct
mapping entries (the 24-frame estimate counted reused sequence steps).

Pipeline: maintained Python preprocessing followed by the `ggfmod` converter:

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
6. **Bake.** `ggfmod convert art --playable` into the v2 gameplay sheet; package presentation PNGs separately until
   container presentation sections are implemented.

Art is produced in fallback order: core playable sheet, then HUD/1-up/signpost, then
special stages, then super set, then continue/portrait, then cutscenes and endings.

Current status: delivered. The assembled core sheet has 98 frames and 18
canonical animations, including HOMING_DIVE; presentation and ending PNGs are
packaged separately. Redrawn slope contours and the motion feedback pass replace
the early rigid rotations. Historical art checkpoints are retained in the status
log below; their pending statements describe those checkpoints only.

### W8 — Example, tutorial and evidence (M)

Current status: delivered — maintained example and [creator tutorial](../../modding/guides/original-character.md),
consolidated reference, changelog and backlog. Focused acceptance and surface
captures exist; full title-to-ending route evidence remains open.

- `examples/<character>/` with a build script in the Mutator Lab pattern.
- A `docs/modding` walkthrough from base image to playable character, written as a
  tutorial, plus updates to `characters.md` and the format docs.
- Short demo clips per game (normal play, homing, Super/Hyper Instagib, special stage,
  results).
- Changelog in `CHANGELOG.0.8.prerelease.md`; mark the delivered backlog items in
  `docs/modding/BACKLOG.md` (GGFP v3 HUD icon, data-select portrait, mod super form).

## Fallback rule

Until a slot has art, the surface uses today's behaviour. Names compose from the display/short name when glyph coverage and layout fit.
Missing glyphs without supplements, unsupported dimensions and omitted slots
retain the native presentation. This fallback is intentional and tested.

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

S2 ending photos belong to the sidekick's presentation (`S2_PHOTOS`), using the
first active sidekick, independently of the main character. Phase 1 MICA with
stock Tails or alone leaves all four native panels unchanged. Phase 2 TALON:
photo 1 never changes; photos 2 and 3 replace Tails with TALON; photo 4 changes
only if the back of TALON's head reads differently from Tails'. Selector 0 is
always native, even if a supplier includes it. Missing selectors fall back to
the native panel and its native palette. No main-character photo override.
The shipped photo palette is monochrome (often described as sepia); preserve
ROM values rather than adding a brown tint.

## Open items

- S2 competitive 2P presentation.
- Title/branding art.
- Slot-reel prize symbols (the bonus-stage player frames are delivered).
- Custom Tornado pilots (MICA rider/photos retain native Tails/vehicle roles).
- GGFP presentation sections; current presentation uses bounded sidecar PNGs.
- Full title-to-ending route captures across all three games; existing positioned,
  programmatic-entry and short title-first captures are bounded evidence.
- Phase 2: TALON, including flight/carry, team roles, Solar form and presentation.

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

Entries below are historical checkpoints, not current support statements. Pending
art, binding and certification notes may be superseded by later entries and the
current workstream summaries above.

- 2026-11-08: documentation lane reconciled API and example sources, consolidated
  creator reference/changelog/backlog, and added the MICA build-along. This lane
  changes documentation only; it does not re-certify gameplay or full routes.
  Owner: documentation lane, `feature/ai-mica-docs`, based on `76fb498b4`.
  Checked the final documentation tree with:

  ```bash
  python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=TestModdingDocumentationLinks,TestModApiReleasePolicy,TestModApiPinPolicy' test
  python3 tools/disasm/disasm_citations.py check --strict docs/modding/characters.md docs/modding/guides/original-character.md examples/mica-and-talon/README.md CHANGELOG.0.8.prerelease.md docs/architecture/designs/2026-11-08-original-character-mod.md docs/modding/BACKLOG.md docs/modding/index.md docs/guide/index.md
  git diff --cached --check
  ```

  Maven passed 20 tests across three classes, zero failures/errors/skips (all
  ROM paths found, though this selection requires none). Queue wait 194s; Maven
  execution 1m27s; user wait zero; other work time unmeasured. Citation counts
  match the base: 16 exact, 3 near, 3 unverifiable, no stale/missing/out-of-range
  findings. A separate touched-file link/heading check resolved 180 local links;
  fenced-block and docs-only scope checks passed. Policy pre-commit/commit-message
  validation passed; skills were untouched. No category run or category VERDICT
  applies. All jobs finished, no blocker. Next action: lead reviews/integrates
  this local documentation commit; this lane does not merge or push.

- 2026-11-08: W4 stage lane on `feature/ai-mica-stages`: S1 canonical roll and
  private player palette committed in `dda7b29aa`; S2 half-pipe now installs the
  indexed behind-view set in the shared native SAT assembly, retaining stage
  animation, flips, shadows, admission and painter order. Complete working-tree
  stage/integration/S3K guard selection: 173 passed, no skips. S1 manager 17/17,
  S2 manager 16/16, compiler 3/3 and MICA integration 8/8. All four production-session
  stage captures were inspected; final API/native regression results follow below.
  S2 is committed in `e6a5ac02e`. Blue Sphere now uses MICA's SStage frames with
  unchanged native projection/animation. Its isolated palette participates in the
  native channel-ordered white fade and palette rewind snapshot; snapshot tests
  passed 11/11 (including private-palette fade/restore), renderer 6/6.
- 2026-11-08: W4 stage lane complete. Blue Sphere committed in `1c8ce9035`.
  Slot-machine art now uses MICA's five native-ID roll replacements, with low tile
  priority so native foreground glass still occludes her. Its private palette
  follows the native black fade and resident-art rewind state. Stock fallback
  branches remain unchanged. API/native-rendering selection passed 35/35, no
  skips; corrected slot capture recheck passed 1/1, no skips. Original attempt
  failures were implementation-time example/engine compilation errors; both
  were repaired before the passing checks. No baseline failure is being waived.
  Four local stage commits; no merge or push. Production `GameplayCaptureSession`
  boots the packaged mod, then uses `GameLoop.enterSpecialStage()` or
  `LevelManager.requestBonusStageEntry(SLOT_MACHINE)` and neutral input. This
  proves stage presentation, not traversal of a physical entry trigger.
  Inspected captures: `/tmp/mica-stages-20261108/{s1,s2,s3k,s3k-slots}/frame-239.png`,
  with frame 120/180 stills and per-frame `state.csv` alongside them. Slot frame
  120 is still the native entry title card; frames 180/239 are the bonus stage.
  Native stage palette rows stay unchanged by custom binding; private rows take
  character-supplier colours without native recolouring. The new indexed frame
  sets reconstruct byte-for-byte from their tile/mapping representation.

  Verification commands (Java 25, verified S1/S2/S3K ROMs found; no skips):

  ```bash
  python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=Sonic1SpecialStageManagerTest,Sonic2SpecialStageManagerTest,TestSonic3kSpecialStageRenderer,TestSonic3kSpecialStageManagerSnapshot,TestMicaCharacterIntegration,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestStageSpriteCompiler,TestS3kSlotBonusStageRuntime,TestS3kSlotPlayerRuntime,TestS3kSlotBonusPlayer' test
  # 173 passed; two matching LevelLoading classes and two SlotBonusStageRuntime classes selected.
  python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestModApiSignatureSurface,TestSonic2SpecialStageSpriteAssembly,TestSonic2SpecialStagePaletteNativeChannels,TestS3kSpecialStageSpritePixels,TestS3kSlotGlassPriority,TestS3kSlotMachineRenderer' test
  # 35 passed.
  python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestMicaCharacterIntegration#micaSlotsCapture' -Dmica.stage.captureRoot=/tmp/mica-stages-20261108 test
  # 1 passed after the custom slot tile-priority correction.
  python3 tools/disasm/disasm_citations.py check docs/architecture/designs/2026-11-08-original-character-mod.md src/main/java/com/openggf/game/sonic3k/bonusstage/slots/S3kSlotPlayerArt.java
  # 6 checked, 2 existing unverifiable citations; no stale citations.
  ```

  These are bounded checks, not a full-suite run. Queue wait and execution are
  separate: the 173-test run last reported 361s waiting, then executed in 1m45s;
  the 35-test run last reported 180s waiting, then executed in 1m41s. The slot
  recheck executed in 43s. Admission timestamps between queue reports are unknown;
  there was no user-approval wait. No category run was launched, so no category
  `VERDICT` is applicable.



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

- 2026-11-08: S3K surface lane resumed: HUD life icon/name adapter restored;
  results, Blue Sphere and independent 1-up face wiring still pending.

- 2026-11-08: sign lane `feature/ai-mica-signs`: additive semantic sign poses,
  bounded indexed PNG loader, packaged MICA HUD/monitor/sign/glyph assets and S1
  sign consumer implemented; S2/S3K wiring and capture verification in progress.
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

- 2026-11-08: W4c1 S1 resumed on `feature/ai-mica-s1-surfaces-r2`: recovered
  `290c96d7c`, committed act-results checkpoint `ffbad7a27`, merged integration
  `d2b8d5fe7` as `7e6935887` without conflicts, then added special-stage results
  (`ca78ad1f6`) and 1-up monitor art (`6301b46af`). No contract or other-game edits.
  Name replacement retains the suffix pieces from `M_Got_SonicHas` (docs/s1disasm/_maps/Title Cards.asm:185-195) and
  `M_SSR_GotAll` (docs/s1disasm/_maps/Title Cards.asm:322-341); the face replaces the four $1C
  tiles of `.sonic` (docs/s1disasm/_maps/Monitor.asm:37-40).
  Verification at `6301b46af`: queued Maven `-Dmse=off` compile passed before the
  merge; final focused run passed 58 tests in 11 classes, no failures/errors/skips:
  character presentation (5), HUD donation (4), results mappings (4), monitor
  objects (8+7), results PLC readiness (3), special-stage screen (6), results
  numbers (2), special-stage PLC readiness (3), API signatures (12), MICA integration
  (4). Tile/mapping tests use test presentations; no GL pixel capture or broad smoke
  run is claimed. Final queue wait: 8 seconds; Maven execution: 1m40s; user wait: 0.
  Earlier attempts stopped at compilation because an edit during compilation left
  stale main classes; refreshing the two source timestamps forced the successful
  rebuild. No unresolved S1 surface work; lead owns cross-lane delivery and smoke.

- 2026-11-08: S2 surface lane resumed from saved WIP, compiled and checkpointed
  (`a41c16e3e`), then merged the character integration branch (`41277b704`).
  Merge resolution retained both the S2 and Gilded changelog entries; no contract
  changes. HUD uses the native small letters, act/results use title-card letters,
  and the monitor uses its independent 16×16 face slot. Special-stage headings
  include GOT A, HAS ALL THE, NOW … CAN and SUPER …; native suffixes and timing
  remain with their existing owners. Initial five-class check: four pass, one
  Mockito unfinished-stubbing error in the new provider fixture (registry creation
  nested inside a stub); corrected before expanded verification. Owner: S2 lane.

  Validation selection (all queued with Java 25 and verified ROMs; tile/mapping
  assertions, no GL fixture):

  ```bash
  python3 tools/testing/maven_queue.py -Dmse=off -DskipTests compile
  python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=TestSonic2CharacterPresentation,TestSonic2OneUpPresentation,TestSonic2PresentationProvider,TestSonic2ResultsPresentation,TestSonic2SpecialStageNamePresentation,TestSonic2LivesHudDonation,TestSonic2ResultsCharacterName,TestSonic2ResultsPlcReadiness,TestSonic2ResultsFixedZeroArt,TestSonic2SpecialStageResultsPlcReadiness,TestSonic2SpecialStageSuperResults,TestSonic2SpecialStageResultsWidescreenCommands,TestSonic2SpecialStageResultsTallyCadence,TestSonic2MonitorIconArt,TestMonitorObjectInstance,TestSonic2MonitorMusicAdmission,TestSonic2RetailRingMonitor,TestSonic2CompetitionMonitorConsumer,TestMicaCharacterIntegration,TestModApiSignatureSurface' test
  python3 tools/testing/maven_queue.py -Dmse=off -Dtest=TestSonic2PresentationProvider test
  ```

  Expanded run: 79 tests, one new-fixture assertion failure, zero skips; all
  existing tests and the custom-surface assertions passed. The new stock HUD
  assertion incorrectly expected Tails tiles in the provider's initial view;
  `Sonic2ObjectArt.loadForZone` initializes that view from the Sonic bank. Corrected
  the test to compare with the unchanged native loader output and retained the
  independent native Tails monitor assertion. No production change followed this
  run. Expanded queue wait: 378 seconds; Maven execution: 102 seconds. No user wait;
  other elapsed work time was not separately measured. This is focused evidence,
  not full-suite or gameplay-capture certification. Packaged presentation assets
  and the other game/signpost lanes remain outside this lane.

  Final provider rerun: 2 passed, zero failures/errors/skips (35.163 seconds Maven
  execution). All 79 selected tests are covered by the expanded run plus this
  fixture-only rerun. Citation validation: 2 citations valid; whitespace check
  clean. The tested production source is the `41277b704` merged tree plus the
  S2 snapshot reuse and special-stage name additions committed by this lane.
  No live jobs or blockers. Next action: lead integrates the lane branch; no
  destination merge or push was performed here.


- 2026-11-08: W4 S3K surface lane complete on
  `feature/ai-mica-s3k-surfaces-r2`: HUD life icon/name, act-results name,
  Blue Sphere results name and independent 1-up face. This consolidates the
  three intermediate adapter reports. Results derive tiles/mappings after native
  art readiness, including rewind rebuilds; Blue Sphere retains native tile
  compensation/palette selection. Monitors use their existing life-icon offset.
  Native names, GOT THROUGH ACT mappings and missing-slot faces retain their
  stock fallback. No contract, other-host or signpost changes in this lane.
  Tested source `a67bbed5f` with queued Maven (`-Dmse=off`, surfaceless EGL):
  310 tests passed, zero failures/errors/skips, including five new tile/mapping
  surface cases, S3K HUD/results/monitor regressions, MICA integration, signature
  surface and all four required S3K loading checks. Checkpoint compilation passed.
  The initial focused attempt failed test compilation because a newly added test
  ran against main classes compiled before the 1-up method was added; the fresh
  fixed-tree run above passed. Final validation queued about 5.5 minutes, executed
  1m50s; user wait zero. No gameplay captures, broad smoke or release suite run.
  The lead owns remaining asset packaging, other surfaces and integration; this
  lane has made no push and keeps its worktree/branch for handoff.


- 2026-11-08: resumed sign lane on `feature/ai-mica-signs-r2` from saved WIP;
  checkpoint `5b2be03ac`, integrated character branch at `d2b8d5fe7` in
  `4e7ed3101`, retaining presentation and Gilded suppliers and the tool-built sheet.
  All three native sign consumers now support optional face/spin/native-backing
  poses. MICA packages presentation PNGs through bounded `ModAssetRoot` loading.
  API signature, packaged-asset/native-sheet tile checks, existing sign tests and
  the four required S3K bootstrap/loading guards passed: 116 tests, zero skips.
  S1/S2 coverage checkpoints: `8223e0d62`, `4897515a8`. Inspected EGL captures in
  `/tmp/mica-signs-20261108/`: S1 GHZ1 (`s1-approach`, face 500/spin 200), S2 EHZ1
  (`s2-approach`, face 450/spin 300), S3K HCZ1 (`s3k-hcz-arena`, face 2400).
  These are positioned native-route witnesses, not full title-to-sign certification;
  HCZ starts at (14080,1110) with declared 500 rings/seven emeralds, uses Gilded to
  defeat the native miniboss, and reaches the sign/results before later act-2 death.
  Native reference input replays through the titles did not qualify MICA's routes.
  Full title-route sign captures remain outstanding; unrelated W4 consumers remain
  owned by their parallel lanes. No engine physics or native route rules were changed.
- 2026-11-08 (lead review): the S2 special-stage HUD still prints the native
  "SONIC" label above the ring count (seen in the W4 stages capture). Not yet in the
  surface inventory; add it to the next presentation lane.

- 2026-11-08: abilities lane outcome 1 wires MICA’s air hook to `HomingAttack.start`,
  selecting Instagib when powered. No-target presses dash because the strike is her
  own move, rather than preserving Sonic’s unrelated native air fallback.

- 2026-11-08: abilities lane outcome 2 adds optional `HOMING_DIVE` canonical
  sheet binding outside native animation IDs; the world strike phase selects its
  four two-tick frames. No gameplay or character-local animation latch was added.
  Validation: 42 focused Java tests passed without skips (MICA, homing,
  materializer, canonical mapping, API signatures), plus the assembler test.
  Rejected: publishing the synthetic dive ID as gameplay state failed the real
  Buzzer kill because native touch requires anim=Roll. The visual script now runs
  with a temporary ID restored before returning; native contact retains Roll.

- 2026-11-08: abilities lane outcome 3 replaces placeholder tuning with the shared
  `PhysicsProfile.SONIC_2_SONIC` used by all three stock single-player providers.
  `CharacterPhysicsSpec` remains instance-owned; host rules/modifiers still apply.
  Acceptance compares the entire physics record against each host Sonic provider.
  Validation: the requested queued Maven selection passed 96 tests, zero skips,
  including all three MICA hosts, homing, API pin, Super-form gate and S3K guards.
  Capture packaging validates with zero findings. Outcome 4 remains in progress.

- 2026-11-08: abilities capture audit found that the character hook precedes the
  S3K Super gate. MICA now tries the supported transformation controller first;
  an input-driven S3K acceptance guards against homing consuming that press.
  Input-driven transformation regression and all requested checks pass again:
  96 tests, no failures/errors/skips. Queue wait 400s; execution 45s; user wait 0.

- 2026-11-08: abilities lane outcome 4 records production gameplay evidence under
  `/tmp/mica-abilities-20261108/`, using `gameplay-capture` and surfaceless EGL.
  The code is `db3781859` (homing `9d606cda4`, dive `320f6a1d5`, physics
  `0ae6e7d5b`, S3K input gate `db3781859`). No merge or push by this lane.
  Title-first clips retain the real launch/input path and owned save roots:
  `s1-verified-route/capture.mp4` (idle, full-speed run, spring at frame 2392),
  `s1-verified-chain/capture.mp4` (normal strikes 1364–1367 and 1397–1408, with no
  intervening grounded row; second native kill awards 200 points),
  `s2-verified-kill/capture.mp4` (idle, full-speed run, normal Coconuts homing
  kill at frames 1553–1555; native 100-point defeat at 1560), and
  `s3k-verified-route/capture.mp4` (idle/run, monkey kill at 1137–1142, native spring
  launch at 1562 with y velocity -0xA00). `s2-verified-powered/capture.mp4`
  demonstrates transformation, gold-palette Instagib strikes and the authored
  spring pose (frame 845). That last clip deliberately uses direct EHZ1 boot
  with supported capture debug setup `--emeralds 1111111 --rings 80`, not a
  title-first saved route. No positioned starts or mod gameplay hacks are used.
  All clips are 640×448, 60 fps; ffprobe frame counts match their state CSVs.
  Reviewed selected before/strike/defeat/spring pixels as well as input/state rows.
  Inputs, state CSVs, selected review stills and `evidence.json` remain beside the
  videos; regenerable PNG frame directories were removed after review because
  shared `/tmp` filled. Earlier probes are not acceptance clips. The requested
  actions are demonstrated across the set; an airborne chain is filmed in GHZ1,
  not separately certified in every host. No broad smoke or full-suite claim:
  cross-lane validation/integration remain with the delivery lead.


- 2026-11-08: W4 screen lane on `feature/ai-mica-screens`: optional indexed
  continue banks preserve the native frame/timing owners in S1/S2/S3K. MICA
  packages every authored continue pose; native text and stock Tails retain their
  original palettes (custom line 2 in S1/S2, line 1 in S3K). Focused tests and
  captures in progress. Portrait and snowboard consumers remain pending.
  Continue palette evidence: S1 `.cont_text` and `.cont_Sonic1` share line 0,
  while the oval uses line 1: Map_ContScr_internal (docs/s1disasm/_maps/Continue Screen.asm:14-56).
  S2 `ContinueScreen` retains the player lines and loads `PalID_SS1` for its
  backdrop: ContinueScreen (s2.asm:10350-10368). S3K loads all four
  native lines and renders text on line 0: loc_5C3BC (sonic3k.asm:122909-122919);
  the actor/icon mapping bytes use line 0; Knuckles' actor adds line 3.
  Native screen text/partners therefore keep their ROM palette while MICA
  binds to an unused screen line. Initial capture images were rejected because
  GameplayCaptureSession's generic render path draws a level in continue mode;
  the component fixture now explicitly draws the production continue provider.
- 2026-11-08: W4 portrait adapter resolves character-owned portrait poses by
  canonical key before the native Sonic/Tails/Knuckles fallback. Team layouts
  retain the native Tails frame. A renderer-owned scoped palette bank isolates
  MICA from every menu palette line; these are derived graphics resources,
  released on reset and recreated when their context generation retires.
  Menu/background and character lines load separately:
  loc_C710 (sonic3k.asm:16118-16132). Portrait and continue pixel checks are
  in progress; the continue custom bank was moved clear of native allocations.
  Rejected captures also exposed the fixture retaining a level camera during
  intermediate frontend batch flushes; frontend capture must set camera (0,0).
- 2026-11-08: W4 ICZ adapter uses the 13-frame `snowboard` bank while retaining
  `IczSnowboardIntroInstance`'s native animation, movement and handoff. Both the
  custom rider and native board/spray have renderer-owned palette banks; deferred
  sprite commands carry the palette replay, so no live level palette is replaced.
  Palette-sharing evidence: the rider selects player line 0 at
  loc_393EE (sonic3k.asm:76793-76804); the board selects line 0 in
  Obj_LevelIntroICZ1 (sonic3k.asm:77047-77055); spray selects line 0 in
  sub_39924 (sonic3k.asm:77196-77205). Rider-frame DPLC selection belongs to
  sub_3968E (sonic3k.asm:76991-77007). Big snow pile terrain/priority and constants remain
  native owners; neither needs a character-code special case. Capture/checks pending.
  Capture diagnosis: resetting the camera alone did not restore S3K's missing
  rows. Instrumentation showed `DisplayedFade[mode=FROM_BLACK, steps=0,
  lineMask=14, pending=false]`, with no GL errors. The fixture had requested
  game over immediately after boot, before the initial level reveal. Continue
  captures now step that reveal first; direct data-select captures explicitly
  release its predecessor's palette publication. Debug-only probes are removed.
  ICZ runtime captures at ticks 60/120/180/240/300 show the authored rider;
  tick 0 was rejected because the native level fade is still black.

- 2026-11-08: W4 screens lane complete, owned by `feature/ai-mica-screens` (adapter details above).
  Checkpoints: continue `e7d2fb27a`, portrait `9ab76f309`; final snowboard
  adapter and capture fixture verified together before the final surface commit.
  All three continues, S3K solo/team portraits and ICZ1 rider art are wired.
  Native animation/movement/timing and stock fallback remain with their owners.
  No changes were needed in ICZ events, BigSnowPile or the existing 13-frame constant.
  Capture witnesses were inspected at 320×224 under surfaceless EGL in
  `/tmp/mica-screens-20261108/`: `s1-continue-{wait,depart}.png`,
  `s2-continue-{wait,depart}.png`, `s3k-continue-{wait,depart}.png`,
  `s3k-data-select-solo-and-tails.png` (both saved-slot variants in one image),
  and `s3k-icz1-snowboard-{60,120,180,240,300}.png`. The data-select zone-preview
  images retain the existing native/frontend path; this is portrait certification,
  not certification of every menu preview. No full-route or release-suite claim.

  Final focused check: 213 tests passed, zero failures/errors/skips; all ROMs found.
  Production sources are the portrait checkpoint plus the final snowboard adapter;
  only this status entry follows the successful run. Final queue wait 16s,
  Maven execution 1m45s, user wait zero; other elapsed work was not separately timed.
  Earlier focused runs passed 42 continue/API/integration checks and 96 portrait
  regressions; rejected fixture captures are explained above. The prior expanded
  181-test run had only the incomplete-fade capture assertion failure, now repaired.

  ```bash
  python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl -Dmica.captureDir=/tmp/mica-screens-20261108/ '-Dtest=TestSonic1ContinueScreen,TestSonic2ContinueScreen,TestSonic3kContinueScreenProvider,TestSonic3kContinueScreenArt,TestContinueScreenRoundTrip,TestMicaCharacterIntegration,TestIczSnowboardArtLoader,TestRewindFixS3KIczBigSnowPileCodec,TestS3kCustomTeamPortraitComposer,TestS3kDataSelectPresentation,TestModApiSignatureSurface,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils' test
  python3 tools/disasm/disasm_citations.py check docs/architecture/designs/2026-11-08-original-character-mod.md
  git diff --check
  ```

  Citation check: 8 exact, 3 within owning routines, 1 inherited unanchored citation;
  no stale/out-of-range/missing findings. Whitespace check clean. All validation
  jobs finished. No merge or push; lead owns integration, cross-lane contract
  reconciliation and broad validation. The `Art.screenFrames` component is additive
  and retains the previous four/five-argument constructors; the other presentation
  lane may need to combine its added components at integration.
- 2026-11-08 (lead): merged W5/W6 abilities (`632861a37`), special/bonus stages
  (`bcdbe781e`) and continue/portrait/snowboard (`c6fa45fdf`). Combined focused run
  on `4f467117f`: 1,663 tests, 14 failures + 1 error, 2 skipped, all in KiS2 and S3K
  special/bonus-stage trace replays. The same 15 classes on develop `33c3b8e56` and on
  pre-stages integration `f9729bc43` fail identically (same identities, first
  divergence and error totals), so they predate this work. Remaining presentation
  surfaces seen in captures: S3K data-select save-slot life icons and NO SAVE slot,
  S2 special-stage HUD "SONIC" label.

- 2026-11-08: W4 S1/S2 ending lane `feature/ai-mica-endings-s1s2` complete.
  S1 commit `6f5d7a49f` supplies eight native-timed ending poses. S2 supplies all
  native ROM photos, five Float2 frames, eight normal and six powered selectors;
  native vehicles and separate Tails roles remain ROM-owned. Palette rows are
  private, with native white fade steps and W6 Gilded cycling. Captures inspected
  at `/tmp/mica-endings-s1s2-20261108/{s1,s2-normal,s2-super}/` include per-frame CSVs.
  Validation: queued Maven with `-Dmse=off` and surfaceless EGL; S1 existing ending,
  rewind and API checks (29 passing plus corrected capture test, no skips), S2
  ending/KiS2/native-entry/full MICA integration/API checks (48 passing, no skips),
  Engine draw-boundary checks plus two captures (9 passing), final denser captures
  (2 passing). The initial generic capture draw showed DEZ rather than the S2
  ending: rejected as visual evidence. The retained capture uses the real Engine
  binding/reshape/ending draw and `flushScreenSpace`, following
  `TestEndingBackgroundDrawBoundary`; blank native camera-pan intervals are not
  visibility failures. No full-suite or route-completion claim. No live jobs remain.

- 2026-11-08: W4/W6 S3K ending lane `feature/ai-mica-endings-s3k` adds
  optional native-ID ending sets through the owner-bounded presentation supplier,
  packaged MICA S3K PNGs and private palette contexts. Rescue/reunion callers use
  semantic scene-form operations for both native and profile controllers; the
  profile retains authored palette cycles and no scene ring drain. Native ROM
  Float2 and transformation scripts drive the added frames. The implementation and verification reports are consolidated here.
  This remains declared-entry scene verification rather than Doomsday route
  certification, covering normal/Gilded rescue and reunion, ride, tiny passengers
  and the Hyper-progress trio child. All three production capture sessions return to the
  title (15,088 / 15,798 / 15,538 recorded level frames), with screenshots and
  CSVs under `/tmp/mica-endings-s3k-20261108/`. The final visual review caught and
  corrected the native hidden-frame-zero / authored WAIT collision; focused
  regressions preserve ordinary idle outside scene ownership. Long credits also
  exposed fresh native input callback identities exhausting the owner wrapper
  cache; stable native identities fix it without relaxing the bound.
  The original ending/API/S3K run had 402 passes and three capture aborts from
  that cache issue; the capture rerun passed 4/0/0/0, the remaining MICA/DDZ/API
  and required S3K checks passed 100/0/0/0, and the corrected final captures plus
  hidden-frame/input regressions passed 6/0/0/0. No full-suite or campaign claim.
  New citations pass the focused strict checker; Python build syntax and diff
  whitespace checks pass. This lane neither merges nor pushes.

  The strengthened production-render assertions also pass 3/0/0/0: every required
  ending slot reaches the renderer, fixed-palette slots match their packaged
  colours, and private palette rows stay outside native lines 0–3. Across the
  five focused test jobs, queue wait was 15m12s and Maven execution was 8m42s;
  user wait was zero. Other investigation/build wall time was not fully timed.

- 2026-11-08: W4 presentation sweep, surface 1: save-slot life counters now
  resolve the selected canonical character's HUD icon and palette. The native
  cross, digits, continues and stock character tilemaps remain owned by the menu.
  The icon uses the existing renderer-owned palette bank; no public presentation
  contract changes. Focused verification and inspected captures recorded below.
  Verification: queued Java 25 Maven `-Dmse=off` with surfaceless EGL, selection
  `TestS3kDataSelectPresentation,TestS3kCustomTeamPortraitComposer,TestMicaCharacterIntegration#continuePixelsS3k`: 91 passed, zero skips.
  Inspected `/tmp/mica-sweep-20261108/s3k-data-select-solo-and-tails.png`: both
  saved MICA slots show the jade HUD face beside lives; native continues icons
  remain a separate surface. Queue wait 393s, execution 95s, user wait zero.
  Initial compile raced a newly added S2 helper; the fresh source build passed.

- 2026-11-08: W4 sweep surface 2: NO SAVE already used the portrait composer,
  but team selection was absent from the object-state cache key. Team changes
  now rebuild its custom frame immediately, including within an animation tick.
  The integration fixture selects MICA solo and MICA/Tails through the real
  controller roster; stock NO SAVE retains its native mapping. Same 91-test
  selection passed without skips (queue 196s; execution 94s). Inspected
  `/tmp/mica-sweep-20261108/s3k-no-save-mica.png` and
  `s3k-no-save-mica-tails.png`: both NO SAVE variants show MICA; Tails remains native.

- 2026-11-08: W4 sweep surface 3 composes the S2 half-pipe HUD name from its
  own five SONIC tiles, five MILES tiles and overseas TAILS bank. Native M/I/C/A
  cover MICA without supplements. Only the five name tiles are derived; native
  ring text, counts, stock partner, palettes and SAT mappings are retained.
  The inventory is owned by `Obj5E` (s2.asm:9503-9515), its mapping include
  `Obj5E_MapUnc_7070` (s2.asm:9615) and `ArtNem_SpecialHUD` (s2.asm:91131).

  Sweep search: reviewed canonical `"sonic"` comparisons and SONIC literals,
  native frame/art selections in HUD, menu/data select, results, continue,
  sign/monitor, special/bonus stage and title owners across all three hosts.
  Endings/credits remain owned by other lanes. Native fallback banks and route/
  physics/trace identifiers remain intentional. No additional unadapted
  single-player gameplay HUD/results name or face consumer was found.
  Follow-ups outside this sweep's presentation slots:
  - S1/S2/S3K title-screen logos and mascots (including S2 Sega giant Sonic)
    are fixed branding/front-end art, with no title-art slot.
  - S2 title-card `SONIC THE HEDGEHOG` stripe is native game branding, not the
    results/HUD name cell; no replacement stripe asset is supplied.
  - S2 native competition owns fixed Sonic/Tails participants and results/HUD
    banks; custom 2P characters are outside this single-player lane.
  - S3K slot reels retain native prize symbols (including Sonic); these are
    machine props, separate from the already adapted playable slot sprite.
  - S2 Tornado's native partner/pilot art remains route-owned; MICA's Sonic route
    uses stock Tails there. Custom companion/pilot content needs its own slot.
  S2 results' SONIC RINGS debug fallback is diagnostic text; the native mapped
  row is ring/bonus art, not a further composed SONIC name.

  Expanded check: queued Maven `-Dmse=off -Dopenggf.headless.gl=surfaceless-egl`
  passed 143 tests, no failures/errors/skips, all ROMs found (queue 229s; execution
  108s). Selection: `TestSonic2SpecialStageHudPresentation`,
  `Sonic2SpecialStageHudLayoutTest`, `Sonic2SpecialStageManagerTest`,
  `Sonic2SpecialStageDataLoaderTest`, `TestSonic2SpecialStageSpriteAssembly`,
  `TestSonic2SpecialStageNativePresentation`,
  `TestSonic2SpecialStageNativePlayerPresentation`, `TestMicaCharacterIntegration`,
  `TestModApiSignatureSurface`, `TestS3kAiz1SkipHeadless`,
  `TestSonic3kLevelLoading`, `TestSonic3kBootstrapResolver`,
  `TestSonic3kDecodingUtils`. Capture properties:
  `-Dmica.captureDir=/tmp/mica-sweep-20261108` and
  `-Dmica.stage.captureRoot=/tmp/mica-sweep-20261108`. Inspected
  `s2/frame-180.png`: MICA above RINGS, with native stage palette/track retained.
  Frames 120 and 239 plus state.csv are adjacent component-stage witnesses,
  not title-to-special-stage route certification.

- 2026-11-08: W4 sweep surface 4: the capture audit exposed native Sonic in
  saved-slot miniature continue counters. These now reuse `continue/icon` frame
  0 and its isolated palette. Only transparent padding may extend beyond the
  16×24 figure cell; otherwise the consumer keeps native art, preserving the
  adjacent cross/digits. No public API additions. The expanded 143-test run above
  includes this production source and its MICA capture assertion. Final menu
  regression command:

  ```bash
  python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestS3kDataSelectPresentation,TestS3kCustomTeamPortraitComposer' test
  ```

  91 passed (76 presentation + 15 composer), zero failures/errors/skips; queue
  49s, execution 36.677s. This includes the new figure-cell/cross preservation
  test and existing stock menu regressions. Inspected final saved-slot and both
  NO SAVE captures: MICA figures/faces, native Tails, crosses and digits visible.
  S2 frame 120 is the native white transition and is rejected as art evidence;
  frames 180/239 visibly show MICA's label. Zone preview noise follows the
  inherited frontend path and remains uncertified, as in the screens lane.
  The tested source is `18f363717` plus the final continue-counter adapter/test
  changes committed by this lane; subsequent edits are documentation only.
  All five validation jobs finished (one initial compilation failure plus four
  passing runs). Total queue wait 935s; Maven execution about 418s; user wait zero;
  other work time unmeasured. Citation check: 13 exact, 3 near, 2 unverifiable
  (include/inherited references), no stale/missing/out-of-range findings;
  whitespace clean. No merge or push. Lead owns integration and broader smoke;
  this is focused presentation evidence, not full-suite or route certification.

### Historical W7 art checkpoints (2026-11-08)

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

W7 motion feedback handoff (2026-11-08, `feature/ai-mica-art-motion`):
RUN replaces all sixteen frames with a four-phase coral boot blur, forward lean,
swept arms/crest and streaming tail. `art/mica/motion.py` is the editable original
contour source; slope contours are rasterized in their 0/45/90/135-degree bases
about the shared engine hotspot, without bitmap rotation. Flat support remains
centre+18. Palette indices are unchanged. The flat RUN/SPRING checkpoint was
compared at 1×/4× beside decoded S1 art before expanding slopes. Review sheets
and GIFs live only in `/tmp/mica-art-motion-20261108/`; fastest native RUN is one
tick (GIF rounds to 20ms). The first closed tyre read was rejected and replaced
with changing cutouts/crossing strokes. Runtime slope contact remains unverified;
the hood/scale clusters are simpler than the generated core artwork. Python
assembly roundtrip passes (96 frames, 17 scripts, at most 36 tiles/frame); no
Java build, GGFP bake or gameplay certification is claimed.
SPRING now has two stretched launch poses with an identical head contour, open
raised gloves, paired downward boots and hanging tail. Glove reach and boot
extension change in the second beat; the existing 24+24-tick exit to WALK stays.
WAIT now uses six unique poses: rest, 1px inhale/crest rise, blink/tail fall,
camera glance, raised boot and toe tap. Its 15-step loop animates the initial
186 ticks, then fidgets and returns to breathing. Only two mappings were added:
the reassembled sheet has 98 frames, 17 scripts, unchanged palette order and
maximum 36 tiles/frame. `scripts.WAIT` in the handoff carries symbolic IDs and
canonical durations/end actions; no new runtime animation state is needed.
`mica-custom-frames.json` was regenerated after the mapping shifts. Python
palette/geometry/deterministic rebuild and assembler roundtrip/script checks pass.
Review GIFs cover each RUN set, both SPRING phases and the full WAIT script;
small mouth/eye clusters and runtime presentation remain integration limitations.

W7 motion round 2 checkpoint (2026-11-08, `feature/ai-mica-art-motion2`):
The lead rejected the previous horizontal RUN and front-facing SPRING. Flat RUN
now retains the approved WALK profile/head at native scale above an upright,
slightly leaning torso. Four hip-attached coral wheel phases exchange bent arms
and bob the short diagonal tail. SPRING faces right with the same WALK head in
both phases, an open grin, staggered overhead gloves, hanging tail and long boots.
SPRING metadata now agrees with assembly's native 24+24 ticks and WALK exit.
WAIT's approved six frames/script are unchanged. `art/mica/motion.py` remains the
editable pixel source; no generated/native Sonic pixels enter new runtime art.
Reviews at 1×/4× beside decoded S1 RUN/SPRING and timing GIFs are disposable in
`/tmp/mica-art-motion2-20261108/`. RUN previews use fastest native one-tick timing
(rounded to 20ms by GIF); actual RUN timing is speed-driven. SPRING uses 400ms
per authored phase. Palette/outline/geometry/deterministic rebuild verification
and the isolated Python assembler unittest pass (98 frames, 17 animations,
maximum 36 tiles/frame). No Java build or gameplay certification. The head is
prominent at 1×; open RUN wheel phases remain more alike than crossed phases,
and small grin/lace details simplify.
The twelve RUN slope replacements are now complete: authored contours in
45°/90°/135° bases, redrawn hood/face clusters, continuous ink outlines and the
matching flat wheel/arm/tail phases. Each RUN frame has one connected silhouette.
Support measured along the rotated ground normal is centre+18 for flat/steep
and centre+18.38 for diagonals (native pixel rounding); all keep the common engine
hotspot. The diagonal crops reach 43px; flat crops are 39×38 and SPRING 25×48.
Slope heads/scales are simpler than the detailed flat WALK-derived head; runtime
slope contact and orientation remain unverified. No changes outside RUN/SPRING
frames were made, and the approved WAIT script is byte-for-byte unchanged.

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


## Regression lane verification (2026-11-08)

Local branch `feature/ai-mica-smoke-fixes`, based on `ce401f1f2`; no merge or
push. The supplied develop comparison at `33c3b8e56` is the baseline evidence;
this lane did not rerun or change unrelated baseline failures. Final executable
tree: `e74ed87801` (the runs preceded the commits but used the same code and
final fixture contents). All 15 reported failures pass. The 57-class expanded
selection has 666 unique cases, all passing across the combined run and the
three repaired fixture classes, with zero skips. This is focused verification,
not a full-suite or route certification.

| Failure | Cause and repair |
| --- | --- |
| SOZ connected production graph | `59998a446d` copied canonical-sheet settings into the translator also used for native donation. Reverting those additions restored all 12 SOZ configurations. Keep the settings in `PlayableSheetAnimationBinding`; native donation retains its prior dispatch. Push-special dispatch can clear pushing on animation changes. Canonical host-flag retention is checked in all three hosts. No SOZ assertion changed. |
| Mod-main archetype resolver | The design explicitly specifies Sonic-alone semantics without followers, matching native Sonic's actual-roster distinction. Correct the solo expectation while preserving the paired-team assertions. |
| Two registry cardinality assertions | WorldSession now owns homing independently of policy admission. Deliberately expect its `homing-attack` key and eighteen adapters. A nontrivial registry round trip checks phase, aim, clocks, shards, ability latches and owner identity through reattachment. |
| In-place restore classification | Remove the sign helper's retained character/presentation fields, then classify the stateless final helper as a construction constant in the audit pin. |
| Transient engine references | Mark the three stateless sign helper references Java transient; no gameplay state is excluded. |
| Production AWT blacklist | Reuse pure-Java PngCodec, preserving packed palette indices and duplicate-colour slots. Keep bounded input bytes and maximum 256-by-256 dimensions. |
| Four architecture assertions | Move the asset facade to `com.openggf.mods.assets.PresentationImages`; keep I/O free of game dependencies. Use shared active-character presentation instead of an S3K adapter. Resolve Super options through game-owned value contracts rather than mod-host internals. Neither cycle baseline nor the explicit-mod-contract allowlist grows. |
| Bootstrap access audit | S2 zone art uses the active session module, without an added bootstrap-capable access. |
| Broad catch inventory | Propagate preparation failures in ICZ art, S2 ending character palettes and S3K special-stage result art. Remove their three retired catch rows. |
| Static session state | The existing S3K event manager owns/retire ICZ's custom renderer and two palette banks, published through the module service. The pre-existing native cache debt remains unchanged. |
| Draw writes | Prepare S1/S2 result art and name mappings during forward update. Draw only uploads/uses prepared data. Retire eleven cache rows and three S2 timeline-debt rows. |

The facade package move updates example imports, creator reference and the
normalized candidate API signature pin together. The descriptor and
`ModApiVersion` still agree on unpublished candidate 0.7.0; no published API
baseline or API version changes. The only growing classification pins are the
new world rewind key/cardinality and the now-stateless sign helper type; debt
inventories only shrink.

### Commands and observed outcomes

Every test invocation used `python3 tools/testing/maven_queue.py`, `-Dmse=off`
and `-Dopenggf.headless.gl=surfaceless-egl`; none used `-Pguards`. The wrapper
found all configured ROMs, with no missing properties. One worktree build tree
was used throughout.

The combined acceptance command was:

```bash
python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=TestSozConnectedMechanismsProduction,TestActiveGameplayTeamResolver,TestGameplayModeContextRewindRegistry,TestRewindInPlaceObjectRestore,TestRewindTransientGuard,TestProductionAwtBlacklistGuard,TestArchUnitRules,TestBootstrapModuleProviderCachingGuard,TestBroadCatchSwallowingGuard,TestBuildToolingGuard,TestDrawPathFieldWriteGuard,TestMicaCharacterIntegration,TestModApiSignatureSurface,TestHomingAttackMutator,TestSonic3kModSuperFormGate,*SuperState*,TestCharacterNameComposer,TestSonic1CharacterPresentation,TestSonic2CharacterPresentation,TestSonic3kCharacterSurfaces,TestCharacterSignRenderer,*Signpost*,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,*SpecialStageResults*,TestAnimationTranslator,TestCanonicalPlayableSheet,TestPngTransparency,TestIndexedPngCodec,TestS3kIcz1SnowboardIntroHeadless,TestIczSnowboardArtLoader' test
```

It ran 666 cases: 660 passed, one failed and five errored, zero skips. All 15
original failures passed, as did MICA integration, signatures, homing, Super,
presentation, signposts, ICZ and the four required S3K checks. The six newly
exposed fixture failures came from moving CPU preparation into update: two S2
fixtures omitted ROM services and the S1 mock renderer was marked uploaded but
not CPU-prepared. Supply the actual ROM services and mark the prepared mock
consistently; retain all assertions. Recheck:

```bash
python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=Sonic1SpecialStageResultsScreenTest,TestSonic2SpecialStageResultsPlcReadiness,TestSonic2SpecialStageResultsTallyCadence' test
```

Result: 12 passed, zero failures/errors/skips, covering all six previously red
cases. No production code changed after the combined acceptance run.

Earlier diagnostic selections used the same command prefix:

- The eleven original failing classes: 238 cases, 15 reproduced failures, zero
  errors/skips. After the first corrections plus new codec/registry tests:
  241 cases, two remaining failures (SOZ and the I/O layer edge), zero skips.
- SOZ alone with the former policy-only homing accessor: first blocked by an
  introduced reset-time world lookup, then the corrected reset owner still left
  the original SOZ failure. That experiment was rejected and fully reverted.
- SOZ alone with pre-task playable-art initialization: 12 cases, original SOZ
  failure remained. That experiment was rejected and fully reverted.
- `-Dtest=TestSozConnectedMechanismsProduction,TestArchUnitRules,TestModApiSignatureSurface`
  with pre-task translation: 58 cases, SOZ and signatures passed, one remaining
  I/O layer edge failed; moving the facade into the mod asset layer resolved it
  in the combined run.

Queue wait and execution are distinct: the first diagnostic execution took
2m23s; the combined acceptance execution took 3m03s; fixture recheck execution
took 31.5s. Intermediate jobs also incurred shared queue waits (up to about two
minutes in the captured queue logs). No user-input wait occurred. Total
investigation/edit wall time was not separately instrumented. Raw regenerable
logs remain only under this worktree's ignored `target/`.

`git diff --check` and parsing both documented shell selectors passed. An
additional whole-repository `python3 tools/disasm/disasm_citations.py check`
failed with existing reference debt: 2,051 stale, 32 out-of-range and 122
missing-file findings (plus non-failing classification categories). A diff
check confirms this lane adds no disassembly line citations; no reference pin
or unrelated citation was changed. That broad reference audit is not claimed
green.

W7 native-pose pilot handoff (2026-11-08,
`feature/ai-mica-art-native-pose`): owner-directed enlarge → GPT Image transform
→ shared-palette reduction. The isolated
`examples/mica-and-talon/art/mica/native-pose-pilot/` supplies 26 choices: six
WAIT, eight flat WALK, four flat RUN, two SPRING, four ROLL, S3K HUD and S2 sign.
Existing frames, assembled sheet and Java stay untouched. No merge or push.

`tools/modding/playable_art/native_pose.py` reassembles stock S2 mapped player
frames from the pinned uncompressed bank/DPLC, and decodes the S3K HUD/S2 sign
with the existing Nemesis oracle under isolated Python. It emits transparent
8× nearest-neighbour references, tight native bounds and engine-centre hotspots
only into `/tmp`. Pins are recorded beside the exact transformation prompts in
`provenance.json`; no decoded native art is committed. Each built-in GPT Image
call receives its individual enlarged native pose plus the approved concept.
Model identifier, token usage and billed cost are not exposed by this tool.

`native_pose_pilot.py` uses the existing pipeline's connected-alpha cleanup,
Lanczos reduction, fixed 15-colour assignment and inside silhouette ink. Local
pixel corrections are recorded in `patches.json`; final native rows in
`recipe.json` rebuild the indexed PNGs without temporary generation files.
Review originals and four-column native/raw/reduced/current sheets at 1×/4×,
plus WAIT/WALK/RUN paired GIFs, live in `/tmp/mica-native-pose-20261108/`.
Raw review columns are normalized to native bounds but retain unquantized colour.
WAIT uses MICA's actual authored script; WALK/RUN use native fastest one-tick
steps with cumulative GIF-centisecond rounding. They do not certify all speeds.
Both SPRING phases use the same native mapping; S2 has one launch drawing.

Rollout estimate (planning assumptions, not a price quotation): the current
inventory contains 62 nonslope core PNGs, 36 slopes, 28 presentation planes,
65 stage variants, 53 continue images, one portrait, 13 snowboard images and
64 ending PNGs: 322 existing files, including backing/fixed-palette/shared
variants that should be deduplicated before transformation. Budget 250–330
unique transformations plus 25–75% retries: roughly 315–580 generation calls.
Allow 2–4 minutes of service time per call (10–39 serial service-hours),
parallelizable in bounded batches; 35–70 hours of art/inspection/cleanup and
10–20 hours for reference extraction, reduction and handoff: 45–90 active hours,
roughly 6–12 working days. Broken down: core 8–14h; slopes 7–13h;
presentation 3–6h; stages 5–9h; continue 6–11h; portrait 1–2h;
snowboard 3–6h; endings 12–29h, including composite reference work.
At a hypothetical effective $0.10–$0.25 per generation, 315–580 calls would
cost $31.50–$145; actual built-in billing may differ and was not measured.
This excludes runtime integration and route/background/contact certification.
Per-frame generation improves shapes but needs cycle-level consistency review;
independent calls must not automatically replace a coherent existing set.

Visual assessment: the pilot WALK opens the stride and RUN follows the native
boot arc more closely; the sign gains a softer, larger eye and layered hood.
WAIT has richer scales but head size and tail angle wobble between independent
calls. SPRING follows the low-glove native silhouette rather than the current
raised-arm interpretation. ROLL exposes face/gloves/boots instead of the
current closed armour ball, but some generated phases lose the exact native
rotation. The sign glove and very small HUD eye still simplify at native size.
The first HUD attempt was rejected for drawing a full body; the stricter
head-only retry and original prompt are recorded. This pilot supports the new
method as a source of better shapes and motion, with consistent head/scale
clusters still requiring attention before a whole-set replacement. Light
cleanup darkens selected pupils, restores eye whites and inks the blink; the
rest remains generated and reduced rather than hand-redrawn.

Verification for this checkpoint: isolated Python reference/containment tests and
all 26 PNG palette/index/alpha/outline/native-size/hotspot checks pass (three
unittest cases). Ordinary pipeline regeneration is byte-identical for the frame
manifest and all 26 PNGs. The paired GIFs contain 15/8/4 frames, with total
WAIT/WALK/RUN durations 4630/130/70ms after format rounding. Python compilation
and whitespace checks pass. No Java build, bake, gameplay or route tests were
run. The whole-tree citation diagnostic reports inherited stale/missing references;
this handoff adds no numeric disassembly citations. Commit hooks check the delta.

W7 native-pose core lane (2026-11-08, `feature/ai-mica-np-core`): adopts the
approved six WAIT, eight flat WALK, four flat RUN and two SPRING pilot redraws.
Existing frame IDs, manifest order, numeric hotspots and palette indices stay
unchanged; compatibility reduction fits the generated art to the existing frame
boxes. WAIT reuses the approved first head/hood cluster across the cycle, with
a closed-eye blink, to remove independent-generation head-size wobble. The
native fidget bodies remain distinct. Flat cycles only; slope artwork is untouched.
`art/mica/native-pose-core.json` records exact source prompts, raw hashes and
native mapping references; `recipe.json` retains final original MICA pixel rows.
Disposable references, raw generation, per-frame four-column 1x/4x sheets and
paired animation GIFs live in `/tmp/mica-np-core-20261108/`. Speed-driven GIFs
use the fastest one-tick native interval; WAIT uses the existing authored script.

Checkpoint verification: palette/ink/binary-alpha/geometry checks and exact
recipe regeneration pass for all 98 frames. Assembly remains 98 frames, 18
animations, maximum 36 tiles. The requested Python discovery baseline has 43
tests: 42 pass and the creator-kit export test errors because the inherited
`examples/mica-and-talon/LICENSE` is absent. No Java build, bake or gameplay
certification. Remaining groups and rotation/scale consistency review continue
in this lane; native timing across all speeds and background/contact remain
integration checks.

W7 core native-anchor correction and grounded actions (same lane): the owner
explicitly superseded the earlier compatibility-box choice. Native dimensions
and numeric native hotspots now govern every adopted frame. This rechecks the
pilot WAIT/WALK/RUN/SPRING and supplies corrected quarter-turn ROLL plus new
SPINDASH, PUSH, DUCK, LOOK_UP, SKID/STOP, BALANCE and HURT. STOP remains an exact
SKID alias; all 36 slope frames remain unchanged. The native exporter now has
an optional `--core` inventory; its default 26-frame pilot contract is unchanged.
The retained `native_pose_core.py` method is indexed in the playable-art README.

An initial extra-reference export accidentally used Pillow's default resize
filter. Those new generations were rejected and repeated against explicit 8x
nearest-neighbour exports; temporary rejected originals are preserved in the
review directory, never in Git. A custom dive attempt that stayed upright was
also rejected. Provenance records accepted raw hashes and exact prompts only.
`verify.py` checks recorded native geometry for adopted frames, retaining the
older geometry rules for historical handoffs. The final airborne/water/custom
pose group and its checks follow below; this checkpoint is not runtime contact
or animation certification.

W7 core completion (same lane): all 62 assigned nonslope PNGs are replaced,
including the four STOP aliases; 36 WALK/RUN slope PNGs remain byte-identical to
the starting tree. The final group supplies DEATH (2), DROWN (3), GET_AIR (2),
HANG (3) and HOMING_DIVE (4). WAIT and SPRING reuse generated head/hood clusters
to stabilize adjacent phases; ROLL uses expanded-bounds quarter turns of the
accepted face-in-ball base. Custom dive-00 tucks its small leading muzzle cluster
behind jade scales. Dive-02 uses the regenerated inverted spring pose, reduced
into ROLL-02's native envelope/anchor to avoid a tall-frame pop; its separate
pose and geometry references are explicit. HOMING_DIVE has no native animation.

Native box changes require in-engine capture review, particularly HANG: old
23x44 / 32x44 / 29x44 become 48x25 / 47x26 / 48x25, native hotspots [24,18].
Both SPRING boxes change 25x48 -> 24x45, hotspot [13,24] -> [12,22]. PUSH changes
42x37 / 41x37 -> native boxes; all old/new sizes and anchors are recorded per
frame in `art/mica/native-pose-core.json`, with material-change flags. Disposable
`/tmp/mica-np-core-20261108/geometry-changes.json` also lists per-frame tile counts
and expands STOP aliases. Native centres supersede every earlier MICA anchor.

The review directory has each frame's native 8x source, raw GPT original,
engine-centred four-column 1x/4x comparisons, and 18 paired animation GIFs.
WAIT follows its retained authored script; fixed animations retain the assembler's
native timer intervals and original adaptation phase counts. WALK/RUN/ROLL/PUSH
use fastest one-tick previews, not all-speed certification. Tiny eye/glove/lace
clusters still merge at 1x; the pointed custom dive phase remains stylistically
more angular than its round neighbours. HANG grip, PUSH wall alignment, spring
contact and all changed native anchors need the integrating lane's gameplay
captures; no in-engine capture is claimed here.

Final bounded verification: `verify.py` passes all 98 palette/alpha/outline/
geometry and byte-identical recipe checks; assembly remains 98 frames, 18
animations, maximum 36 tiles per frame. A per-frame audit checks all 62 native
anchors, accepted raw hashes, IDs/order/scripts, unchanged slopes, review/GIF
coverage and tile counts <=36. The three isolated native-pose tests pass.
`python3 -I -m unittest discover -s tools/modding -p 'test_*.py'` runs 43 tests:
42 pass; the same inherited creator-kit missing-LICENSE error remains, compared
by test identity and exception with the pre-edit baseline. Python compilation
and whitespace checks pass. No Java build, GGFP bake, merge or push.

### S2 photo correction and native-reference palette audit (2026-11-08)

The original reference export treated palette attributes in the photo maps as
indices into the ending startup banks. ObjCA_Init (s2.asm:13357-13374) submits
palette changer subtype 4; off_133A4 (s2.asm:26988-26989) selects `Pal_AD1E`, and
ObjC9_Init (s2.asm:26923-26946) copies it into target line 0. Thus all four photos
use `Ending Photos.bin`, not blue `Ending Sonic.bin`. Its words are grayscale,
not brown sepia. The bad reference plus the incorrect solo-MICA brief led to
invalid panels; deleting those panels restores the native cast and composition.

`palette_audit.py` in `tools/modding/playable_art/` freshly decodes representative
references beside the exact archived generation inputs. Its index is in that
directory's README. Outputs for this lane are under
`$HOME/scratch/agent-tmp/mica-s2-photos-20261108/audit/` (PNG sheets and
`results.json`); decoded native pixels remain outside Git. No art was regenerated.
The table records palette risk, not certification of anatomy or mapping fidelity.

| Set | Palette used by generation reference | Correct in-game palette | Plausibly misled generation; evidence |
| --- | --- | --- | --- |
| Core | S2 SonicAndTails line 0 | Same | No; four freshly decoded core samples match. |
| Slopes | S2 SonicAndTails line 0 | Same (these references used S2 slope mappings) | No; four native slope samples match. |
| Presentation signs/HUD | Per-game Sonic player line | Same for character faces; backing separately native | No; S1 HUD, S2/S3K signs match. |
| Portrait | Save Menu Chars line 0 | Same | No; fresh portrait matches. |
| S2 half-pipe | Special Stage Main line 1 | Same | No; four samples match. |
| S3K blue sphere | Special Stage Main line 0 | Same | No; four samples match. |
| S3K slot bonus | SonicAndTails line 0 | Same | No; four samples match. |
| Snowboard | SonicAndTails line 0 | Same | No; four samples match. |
| S1 continue | Sonic line flattened over every piece | Continue Bonus lines 0/1, selected by mapping bits | Yes, possible on the three prone floor poses: glove/shadow clusters change. Two floor samples differ; player fall samples match. Continue line 0 equals Sonic except scene background semantics. |
| S2 continue | SonicAndTails line 0 | Same normal inherited character bank; SS1 loads line 3 | No; icon and player samples match. Retained-Super FixBugs=0 artifacts are outside this normal reference set. |
| S3K continue | Continue Palette line 0 | Same | No; team idle and player samples match. |
| S1 ending | Sonic line 0 | Ending line 0 | No; only transparent index 0 differs; four visible references match. |
| S2 ending photos | Ending Sonic line 0 | Ending Photos copied to line 0 | Yes; all four blue/white references differ, and the invalid MICA scene prompts confirm misinterpretation. Removed overrides. |
| S2 normal/Super/Float2 ending | Ending Sonic/Tails, Ending Super Sonic; Float2 ordinary player | Same scene palettes; Float2 Ending Sonic | No; representative normal, powered and Float2 pixels match (Float2 only index 0 changes). |
| S3K reunion | Sonic/Super Sonic End Pose | Same | No palette issue; samples match. Existing scrambled Super selectors 0/1 are a separate art/mapping caveat. |
| S3K passengers/trio/Float2/ride | SonicAndTails | Same character line (vehicles/logos use separate scene banks) | No; samples match. |
| S3K transformation | Blue SonicAndTails | Sonic base plus ROM Super cycle entries 2..4 | Yes, possible: d2/d3/d5 change from blue to gold in the first steady phase; d4 uses none of those entries. Owner decides whether this warrants redoing art already reduced to Gilded colours. |

The S3K reference exporter now accepts an explicit cycle phase, defaulting to 6,
the first steady Super row. SuperHyper_PalCycle_SonicApply
(sonic3k.asm:4674-4678) writes entries 2..4 from PalCycle_SuperSonic
(sonic3k.asm:4865-4875). This is a representative native phase, not a claim that
all transition frames or Hyper colour phases share one static palette.

Lane verification: queued Java 25 command
`python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl -Dmica.s2.captureRoot=$HOME/scratch/agent-tmp/mica-s2-photos-20261108/captures '-Dtest=TestMicaCharacterIntegration,TestSonic2*Ending*,TestSonic2*Credits*,TestSonic2SidekickPhotos,TestModApiSignatureSurface' test`
passed 78 tests, zero failures/errors/skips (all ROMs found). Final queue wait was
313 seconds, Maven execution 133 seconds; no user wait. This is focused coverage,
not a full-suite claim. Python discovery passed 44 modding plus 7 native-reference
tests. The signature pin is unchanged and verified. Modified guide/tool links,
JSON manifests, staged whitespace, and new disassembly citations were checked.
The repository-wide citation scan reports historical findings; the design's
scoped JSON scan has no findings. The first Java attempt failed due to an overly
broad test edit, corrected before successful reruns; no baseline regression was
attributed to that authoring error.

Headless full-ending captures are in `captures/mica-alone/` and
`captures/mica-tails/` beneath the lane scratch root above: `photo-1.png` through
`photo-4.png`, plus frame CSVs. `captures/photos-contact.png` shows all eight
inspected frames. Corresponding full-frame images are byte-identical between
teams, retain the native cast, and show the exact ROM monochrome palette. Tests
also assert the native photo palette on line 0 and no packaged MICA photo PNGs.

### W2 follow-up — object-owned mapping vocabulary (2026-11-08)

The canonical animation table cannot translate an object's mapping-byte writes.
GGFP v2 now has an optional bounded `NFRM` table, authored as YAML `nativeFrames`.
Hosts publish native-byte → semantic-pose descriptors through
`DonorCapabilities.getNativeFramePoses`; descriptors carry a canonical fallback.
Binding reserves byte slots 0–255 and relocates authored animation/slope frames
and their DPLCs by 256. Objects still publish their native bytes. Native zero is
empty (hide/cutscene); stock art and sheets without aliases retain their layout.
MICA binds `hang/0`, `hang/1`, `hang/2` to its three original native-pose redraws
of S3K $90/$91/$92. Exact prompts, hashes and native anchor metadata are in
`art/mica/native-pose-core.json`; only original MICA indexed rows enter Git.
Native references and raw generation remain in disk-backed scratch.

Source inventory below concerns **playable** mappings, not similarly named
object-local mapping setters. `setAnimationId`, forced animation and script
cursor resets already go through canonical binding. This includes S1 poles,
S2 moving vines/flippers/CPZ tubes, and S3K breakable bars: forcing a native
animation is not an unaliased native mapping write. Movement's native tumble
and super alternate-frame selectors are already disabled for canonical sheets;
their authored slope/roll scripts remain the owner. Continue, snowboard,
special/bonus stages and ending scene renderers use their separate existing
presentation banks, not this normal-playable table.

| Host | Native mapping writer / object | Native IDs or owner expression | MICA binding |
| --- | --- | --- | --- |
| S1 | Sonic1EndingEvents | $03 (post-BuildSprites wait) | authored WAIT-03 (`wait/fidget`) |
| S2 | TornadoObjectInstance ride capture | $01 | authored WAIT-00 (`wait/stand`); separate vehicle/pilot art retained |
| S2 | Sonic2CompetitionSession reset | $00 | invisible; competitive presentation remains outside MICA coverage |
| S3K | AIZ plane intro actor (independent renderer) | normal $BA, $96–$9A; Super bank $21/$22 | existing MICA ride/roll art; powered ROLL fallback with owned Gilded palette |
| S3K | AIZ/MHZ ride vines; LBZ grapple; MHZ pulley | $90–$92 | authored `hang/0`–`hang/2` |
| S3K | AIZ/MHZ vine swing | $78–$7F | authored batch-3 `swing/0`–`swing/7` |
| S3K | AIZ hollow tree; CNZ wire cage; MGZ twisting loop; FBZ wire cages; DEZ gravity tube | $69–$77 / $6C–$77 | authored batch-3 `tumble/*` |
| S3K | CNZ cylinder; HCZ spinning column; LBZ cup/tube elevators; DEZ teleporter | $55–$5B | existing MICA continue-player poses at $55–$57/$59–$5A (`twist/*`); $58/$5B authored batch-3 front/back views |
| S3K | CNZ cage release; FBZ spinning pole; MHZ vertical bar | $5C–$61; vertical initial $62, release $24 | authored batch-3 `pole/*`, existing MICA continue-player $24 on release |
| S3K | MHZ horizontal bar; HCZ conveyor; LRZ turbine | $63–$68, $94/$95 | authored batch-3 `bar/*`, `bar-end/*` |
| S3K | FBZ chain link | $80–$85, $91; release $96 | HANG fallback (`chain/*`), authored hang/1, authored ROLL-00 |
| S3K | FBZ stationary cage entry | $49, $52–$54 | RUN fallback (`cage-entry/*`) |
| S3K | DEZ tunnel launcher | $96 | authored ROLL-00 (`roll/0`) |
| S3K | DEZ gravity hub | $31–$3C, $6C–$77 | ROLL fallback (`gravity-loop/*`, `tumble/*`) |
| S3K | LRZ corkscrew / wall ride | $EF–$FA; $DF–$E3 | ROLL / RUN fallback (`corkscrew/*`, `wall-run/*`) |
| S3K | MHZ mushroom parachute; SSZ elevator bar | $E4–$E7; $E5/$E9 | HANG fallback (`parachute/*`) |
| S3K | LBZ final boss 2; LRZ cutscene boulder | $8A–$8D | ROLL fallback (`carry/*`); native art is a carried curl |
| S3K | MHZ Knuckles cutscene player | $B4/$B5 (Sonic archetype) | HURT fallback (`cutscene-hurt/*`) |
| S3K | HPZ entry/ceremony/theft; LBZ Robotnik ship | $BA / $C4 (Sonic archetype) | existing native MICA ride pose (`ride/stand`) / LOOK_UP fallback (`emerald/look`) |
| S3K | SSZ crane claw | $CB | existing native MICA $CB art (`hang/crane`) |
| S3K | SOZ rapel wire | $90–$93 HANG/SWING, $E4–$EA FLIP | authored $90–$92; $93 HANG fallback and `parachute/*` HANG fallback |
| S3K | DEZ miniboss transport | $55–$5B (verified ROM table $7EF2E, 12 entries) | existing $55–$57/$59–$5A MICA turn art; $58/$5B authored batch-3 front/back views (`twist/*`) |
| S3K | SSZ launch / carried-player / crane release | $EF–$FA (table $587A8); $55–$5B (12 pairs at $468C4); $C0/$CA/$CB release | ROLL fallback (`corkscrew/*`), existing turn art except $58/$5B WAIT; release $C0 WAIT fallback; existing native MICA $CA/$CB art |
| S3K | LBZ1 ground launch / cup initial capture | first frame of installed script | already relocated authored script frame |
| S3K | LBZ final boss 1 player presentation | installed presentation frame | existing presentation owner; no object-local art copied |
| S3K | AIZ plane, CNZ teleporter, HPZ route/entry, ICZ snowboard intro, SSZ arrival/launch/HPZ teleporter, SS entry ring, Mecha Sonic release | $00 | invisible native frame |
| S3K | EndingRescueDirector / EndingKnucklesFlight | $BA, $58, $00; ending-bank selection | existing ending presentation bank; zero invisible |
| S3K | slot runtime / competition miniature | copies current player's mapping | copies the already selected namespace; no second translation |

Fallback art debt is explicit: every semantic key above except `hang/0`–`hang/2`, `wait/stand`,
`wait/fidget`, `roll/0`–`roll/4`, `ride/stand`, `crane/fall`, `hang/crane`,
`bar/release`, and the five authored `twist/*` keys
currently uses its named canonical fallback's first pose. No MICA drawing exists
for native $58/$5B twists, tumble, pole, bar, chain, cage-entry, gravity-loop, carry,
wall-run, corkscrew, parachute, crane and cutscene-specific phases. The SSZ/DEZ table IDs were read from the verified locked-on ROM
(SHA-1 CFBF98C36C776677290A872547AC47C53D2761D6). SSZ crane release $C0 still uses WAIT fallback; $CA/$CB now reuse the existing
normal-mapping MICA art. Unknown byte slots
intentionally use WAIT rather than arbitrary sheet frames. Object route captures
remain coverage gaps, not certified replacements for those presentations. The source-call scan is in the task's scratch folder.

Acceptance: the production AIZ ride vine transitions from native $91 to
$78–$7F; its aliased DPLC selects MICA's HANG source rather than an inverted WALK
frame. The capture is positioned at a spawned production handle; it is not a
cold route certification. The initial 108-test checkpoint passed without skips;
subsequent FBZ/MHZ/Tornado probes and the ROM-table audit are recorded below.
Uncaptured full object routes remain coverage gaps.

W2 contour follow-up: twelve steep WALK/RUN frames have their hood/back contours
completed in native indexed rows. Original reference metadata remains intact;
authored geometry expands above/behind the sprite with the corresponding
hotspot adjustment. Native sole support is unchanged (Python regression).
The scratch `steep-before.png` / `steep-after.png` sheets were visually inspected.

Final focused verification after contour adoption: queued Java selection
`TestMicaCharacterIntegration,TestCanonicalPlayableSheet,TestPlayableSheetMaterializer,TestModApiSignatureSurface,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils`
passes 108 tests, zero failures/errors/skips, with verified ROMs and surfaceless
EGL. Python modding discovery passes 44 tests; indexed-art verification passes.
Broad smoke preflight requires `LUA_BIN=/usr/bin/lua5.4` on this host (the default
Lua version fails); the explicit 5.4 preflight passes. Broad smoke is additional
qualification and does not replace the outstanding object-capture obligations.

Smoke attempt on 2293eb371f was interrupted for the capture-root and crane-pose
corrections. VERDICT INCOMPLETE: 7/91 batches, 3031 tests, zero failures/errors,
one unexplained GL skip (`TestDisplayShaderPickerPresentation` required explicit
surfaceless EGL); guards did not run. The replacement run must select
`--headless-gl surfaceless-egl` as well as explicit Lua 5.4.

The added FBZ horizontal chain and MHZ pulley component captures sustain $91
and $90–$92 respectively and assert their DPLCs against MICA's HANG source.
Both encounters are positioned above native terrain in their owning zones, not
claimed cold routes. Their PNGs were inspected. The two ending tests now honor
`mica.ending.captureRoot`; their old hard-coded `/tmp` paths were removed.

Native normal-Sonic table ownership: `Map_Sonic_`
(`docs/skdisasm/General/Sprites/Sonic/Map - Sonic.asm:1-252`), including the
$90–$92 entries at lines 146–148. Canonical fallbacks are creator presentation
choices, not alternate ROM behavior. The initial host vocabulary covers Sonic
archetypes; separate Tails/Knuckles raw vocabularies are future work.

MICA now declares those aliases as symbolic frame IDs in recipe/frames.yaml
`nativeFrames`; the reducer preserves them and the assembler resolves packed
indices. Existing WAIT and ROLL art is explicitly bound, with roll/0–roll/4 now binding
the five existing normal-Sonic MICA slot poses. The normal-Sonic S2 Tornado waiting helper
was executed as a component pose probe in WFZ; native frame $01 selects MICA's
standing art. This is not a completed WFZ exit/landing route.

Final follow-up verification: the full requested focused selection passed 111
tests without skips after adding FBZ/MHZ/Tornado captures. After moving alias
declarations into recipe metadata, the directly affected 15 Java tests passed
without skips, Python modding discovery passed 44 tests, and palette/geometry/
regeneration verification passed. The final broad smoke run remains separate.

Native-asset reuse audit: accepted ending art already covers normal-Sonic
$BA/$CA/$CB, and the slot bank covers $96–$9A. These eight images are now
packed as nonanimated normal-sheet frames, using semantic aliases instead of
canonical fallbacks. A reproducible `art/mica/reuse_native_art.py` imports only
original creator pixels and records hashes; no rescale or visible pixel changes
were needed. Core coverage is now 106 frames / 18 canonical animations / max
36 tiles per frame. Exact native art wins over fallbacks in an integration test.

Rejected approach: inferring poses from carrier/column object names produced
an inappropriate HANG fallback for $8A–$8D and a SPRING fallback for $C0 in
250b7b9f51. The native atlas shows a curl and standing pose respectively;
the final vocabulary uses ROLL and WAIT. Source image references remain in
scratch `native/raw-pose-audit.png`; native pixels are not committed.


The AIZ intro's independent actor renderer now binds creator art and its private
palette. Its normal bank uses the same semantic aliases; its separate Super
$21/$22 bank uses canonical ROLL with the creator's authored Super dry cycle.
Intro eligibility and title suppression use the registered Sonic archetype,
so MICA is admitted while stock Tails/Knuckles keep their native skip path.
Native routine flow, motion and normal/Super source bank IDs are unchanged.


Baseline attribution for the smoke run used the same build tree with all tracked
source restored temporarily to `ed3f6b39be847ae89b0362431e17024eda7aec7a`,
then restored to the task HEAD without switching branches or stashing. On that
exact baseline, `TestModZoneRuntimeProfile` has 9 tests / 1 error / no skips:
`customS3kZoneInstallsExplicitEmptyRuntimeContracts` throws the same
`CharacterIconPalette` NPE through a null `GameModule.getGameId()`. Likewise,
`TestThreeIslandsScene` has 12 tests / 8 failures / no skips. The matching
failure identities are `tailsJoinsInTheFieldAndTheTravellingShopReturnsToTheSameSpot`,
`aSonic1ZonePlaysItsOwnRomSongWithoutPlaceholderSwitches`,
`startupIsPlayableAndArrivalDialogueAndRestStayInTheField`,
`greenHillMysteriesUseRealInteractionsAndPersistThroughContinue`,
`physicalTrailsConnectFieldsAndBattleUsesTheEncounterField`,
`allStoryLandmarksHavePlayableInteriorsWithIndependentGatesSavesAndRewards`,
`travellerDialogueWaitsForTheDiscoveryOrRescueItDescribes`, and
`explorationUsesTwoAxesAndStoryDiscoveriesPersistAcrossContinue`.
Assertions match the task-tree reports, including the scene mismatch categories;
object identity suffixes vary normally. Baseline XML/text are in scratch.

The earlier 250b7b9f51 smoke invocation inherited `DISPLAY=:0`; eight extra
assertions in native competition/interlace/halfpipe tests required no display.
Those are environment failures. The final run removes `DISPLAY` and
`WAYLAND_DISPLAY`, uses Lua 5.4 and explicit surfaceless EGL, and redirects all
MICA capture properties into `$MICA_POSES_CAPTURE_ROOT/`.
That interrupted invocation completed 64 batches; the subsequent interrupted
retry completed 12/91 batches, 5435 tests, zero failures/errors, 12 allowed skips
and no unexplained skips, VERDICT INCOMPLETE. Guards had not run. Both failed/
incomplete diagnostics were inspected before acknowledgement; no pass is inferred.


A final close-up review found four remaining lower-tail crop edges after the
hood correction. `walk-steep-00/04` and `run-steep-01/02` now end in tapered
jade tips on taller canvases, with original anchors and steep X support intact.
The final `steep-after-tail.png` contact sheet was inspected. All 44 Python
modding tests and palette/ink/geometry/deterministic rebuild verification pass
on the final 106-frame sheet (maximum still 36 tiles). The new AIZ actor test
also runs through plane retirement and native Knuckles control release; its
final focused assertion passes without skips.


The complete presentation-manifest cross-check also found exact normal-Sonic
$24/$55–$57/$59–$5A drawings in the S3K continue player bank. These six original
assets now precede canonical fallbacks through `bar/release` and five `twist/*`
aliases. Normal $58/$5B remain WAIT fallbacks. The final normal sheet contains
112 frames, 18 canonical animations and fourteen reused native-pose assets,
with the same maximum 36 tiles per frame. Separate stage/screen/ending mapping
banks with coincident IDs were excluded from this reuse inventory.


Frozen 112-frame verification: the nine-class queued selection (the eight
requested classes plus `TestS3kAizIntroCoverage`) passes **115 tests, zero
failures/errors/skips** with verified ROM inputs and surfaceless EGL. It also
checks complete native AIZ actor/Knuckles control handoff. Execution was 69s,
queue wait 335s, user wait zero; other elapsed investigation time is not folded
into those figures. The preceding 106/112 mixed-source run is invalid: the
queued compiler had read the prior frame-count assertion before atlas update.
The frozen rerun is the authoritative result. Python discovery passes 44 tests,
and indexed-art/deterministic rebuild verification passes for 112 frames.

Inspected captures live under the task-supplied external scratch directory
`$MICA_POSES_CAPTURE_ROOT/`: `s2-spindash/`,
`s3k-spindash/`, `aiz-vine/`, `fbz-chain/`, `mhz-pulley/`,
`s2-tornado-wait/`, `aiz-plane-intro/`, and `endings/s1/`; state CSVs accompany
the gameplay probes. Source-pose contact sheets are `reused-original-poses.png`,
`reused-continue-poses.png` and `steep-after-tail.png`. Native references remain
only in scratch. Vine/chain/pulley encounters are positioned production-object
probes, Tornado is its production waiting helper, and intro is a natural native
sequence. These do not certify unrelated cold routes. S1 has one implemented
normal-playable raw writer; S2 has one plus the competitive invisible reset,
so there are not two or three distinct gameplay raw setters to capture there.


#### Implementation-lane handoff

Code/art commit: `4c17838e8f15ccca1f3cc7ca95bc15d01c6ad593` on
`feature/ai-mica-poses`. The requested focused checks are complete. The broader
delivery qualification belongs to the delivery lead; this worker unnecessarily
started additional smoke qualification and stopped it before handoff rather
than continuing duplicate delivery work.

The final additional invocation was:

```sh
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py \
  --base ed3f6b39be847ae89b0362431e17024eda7aec7a --workers 2 \
  --headless-gl surfaceless-egl --max-minutes 60 --run
```

Its environment removed `DISPLAY` and `WAYLAND_DISPLAY` and supplied the six
MICA capture properties below through `JAVA_TOOL_OPTIONS`. Preparation passed
in 33.5s. VERDICT INCOMPLETE: 15/91 ordinary batches, 6447 tests, zero
failures/errors, 12 allowed skips, zero unexplained skips; guards did not run.
The twelve allowed skips are explicit audio benchmark/OpenAL-isolation opt-ins
and editor/Stageplay picture refresh opt-ins. Test execution was 155.16s;
observed admission wait was at least 1835s (1745s acquired waits plus the final
90s queued checkpoint), user wait zero, residual overhead unmeasured. The owned
runner and Maven children stopped cleanly. Diagnostics were read before
acknowledgement. This is not a broad-smoke or full-suite pass. The earlier
baseline failures above remain relevant to the lead's eventual qualification.

Reproduce the authoritative **115-test, zero-skip** focused result with
`MICA_POSES_CAPTURE_ROOT` set to the task's external scratch directory:

```sh
env -u DISPLAY -u WAYLAND_DISPLAY python3 tools/testing/maven_queue.py \
  -Dmse=off -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=TestMicaCharacterIntegration,TestCanonicalPlayableSheet,TestPlayableSheetMaterializer,TestModApiSignatureSurface,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestS3kAizIntroCoverage' \
  "-Dmica.poses.captureRoot=$MICA_POSES_CAPTURE_ROOT" \
  "-Dmica.ending.captureRoot=$MICA_POSES_CAPTURE_ROOT/endings" \
  "-Dmica.hud.captureRoot=$MICA_POSES_CAPTURE_ROOT/hud" \
  "-Dmica.icon.captureRoot=$MICA_POSES_CAPTURE_ROOT/icons" \
  "-Dmica.stage.captureRoot=$MICA_POSES_CAPTURE_ROOT/stages" \
  "-Dmica.captureDir=$MICA_POSES_CAPTURE_ROOT/screens" test
python3 -m unittest discover -s tools/modding -p 'test_*.py'
python3 tools/modding/playable_art/verify.py examples/mica-and-talon/art/mica
```

The initial AIZ actor PNGs have a black scene background. They verify MICA's
normal/powered actor and palette, while full intro scene visual parity remains
unverified. The complete native control handoff is tested. Other unrun object
routes and the documented canonical pose fallbacks remain coverage/art debt.

## Dust and target preview follow-up (2026-11-09)

Canonical sheets omitted attached dust in `drawStandardArt`; S2 also rejected the
mod's persisted character key in its native dust loader. Mod playables now select
the common Sonic dust bank independently of their body art, and standard drawing
submits the existing dust controller. Skid puffs borrow that same ROM renderer.
The ROM creates a separate dust object in InitPlayers (s2.asm:5177-5182) and
loc_6AB8 (sonic3k.asm:8384-8392); its charge paths are
Obj08_MdSpindashDust (s2.asm:42768-42790) and loc_18C20
(sonic3k.asm:34066-34096). No body-sheet pixels supply effect art.

The documented tick hook did not exist. The candidate API now provides protected
`onMovementTick()`, invoked through the character's construction fault boundary
before input/homing selection. MICA requests a preview while airborne; the world
runtime owns availability and target selection, and locks the reticle immediately
when launching. The 0.7 signature pin includes the hook. No new mutable mod state
or rewind payload is needed.

Focused evidence covers S2/S3K charge pixels with and without the dust controller,
skid-puff construction, and MICA's airborne preview, launch lock and landing hide.
The initial dust negative control removed the live controller without rebuilding
S3K's already published SAT, so both pictures retained dust. The maintained check
explicitly prepares/publishes both diagnostic sprite tables without stepping
gameplay, and samples a visible puff. The first AIZ homing filming route
fell into a pit before reaching an enemy; the replacement starts explicitly at
(7100,900), approaches the placed Rhinobot, and records native defeat. These are
bounded presentation/ability witnesses, not whole-route certification; inherited
route and pose gaps above remain.

The landing visibility assertion runs after the existing strike/replay checks:
a mid-sequence world restore replaces object identities, while temporarily
landing before launch clears the native jumping latch. Neither is a neutral
way to pause the original encounter. No production workaround was added for
these fixture interactions.

Validation for this follow-up (Java 25, `DISPLAY`/`WAYLAND_DISPLAY` unset):

```sh
env -u DISPLAY -u WAYLAND_DISPLAY python3 tools/testing/maven_queue.py -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=TestMicaCharacterIntegration,TestHomingAttackMutator,TestHomingStrikePose,TestModApiSignatureSurface' test
env -u DISPLAY -u WAYLAND_DISPLAY python3 tools/testing/maven_queue.py -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestMicaCharacterIntegration' test
env -u DISPLAY -u WAYLAND_DISPLAY python3 tools/testing/maven_queue.py -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils' test
```

The combined selection passed all 32 homing/pose/signature tests; the integration
fixture corrections above required its separate final rerun: 34 passed. The S3K
selection passed 60 tests. All selected tests ran with zero skips. No full-suite
or whole-route claim is made. The final integration run waited 125 seconds for
the queue and executed for 73 seconds; the S3K selection waited 299 seconds and
executed for 42 seconds. No user wait was required; unmeasured editing/capture
intervals are not attributed to test execution.

Five headless `GameplayCaptureTool --mod` MP4s and an inspected contact sheet live
under the external `$MICA_DUST_RETICLE_CAPTURE_ROOT` evidence directory. The short clips
are `s2-dust.mp4`, `s3k-dust.mp4`, `s1-homing.mp4`, `s2-homing.mp4`, and
`s3k-approach.mp4`; `README.txt` records source frame ranges, the positioned AIZ
start and retained input/CSV sources. `ffprobe` confirms H.264, 960×672, 60 fps.
The source ROM aliases were verified against all three required SHA-1 identities.
The mod was built by `python3 examples/mica-and-talon/build.py`. Scoped citation
checks and staged policy checks pass. Delivery owner: implementation agent;
local branch `feature/ai-mica-dust-reticle`, based on `c88c34cd61`. No live jobs
or blockers remain; the lead owns subsequent integration. No merge or push.

## Coverage audit (2026-11-09)

**Object-pose closure, 2026-11-10:** the maintained sheets now contain MICA
274 frames / 52 keys and TALON 171 / 47. All 63 outstanding MICA S3K direct-pose
rows and all 16 requested rare animation rows bind authored art or explicit
aliases. Fallback totals move MICA S2 **38 → 34**, S3K **102 → 36**; TALON S2
**44 → 39**, S3K **71 → 67**. S1 is unchanged. Remaining totals include powered
and presentation obligations and are not normal-play pose counts. The raw tumble
selector is admitted only when every phase of its native bank is explicitly
authored. Normal transformation aliases retain ROM script timing; separate powered
banks remain distinct. TALON S2 tail-only dummy frames intentionally bind blank.
Sources and independent size checks are under each character's art directory.
Positioned native-object encounters use production headless EGL rendering; they
are review evidence, not route certification. The historical audit below remains
as the earlier baseline.

The [2026-11-10 powered sheet](#powered-sheet-design-2026-11-10-implemented) is
implemented: `SuperFormProfile.Form` takes an optional `PoweredPlayableSheet`
(a second GGFP bank plus explicit normal-animation and phase aliases), combined
with the normal bank at art initialization. MICA's S2/S3K Gilded banks add 34
native-pose drawings with derived slopes; the 70 powered rows move to 16
authored, 50 alias and 4 justified N/A, with zero fallback. Hyper shares Super
geometry; characters without a bank are unchanged.

Research branch `feature/ai-character-art-audit` starts at `24bfe483c7` and merges
approved TALON art `71fefca894`, independently of the TALON engine lane.
Current sources: **MICA 132 frames / 22 canonical keys; TALON 82 / 13**.
TALON's approved round-2 WALK/RUN use Sonic references and derived slopes;
these are not missing art merely because native Tails has different poses.

The maintained [ROM-gated probe](../../../src/test/java/com/openggf/level/objects/TestCharacterArtCoverageAudit.java)
uses production conversion/materialization/binding and native ROM loaders,
recording scripts, all mapping indices, raw targets and 512-clock publication
samples at four slopes/two speeds. The [report generator](../../../tools/modding/character_art_audit.py)
joins creator provenance and native labels. Neither writes decoded native art.
Run the queued test with `-Dmse=off`, surfaceless EGL and
`-Dart.audit.output=<external-dir>`, then run the report generator on that directory.

External outputs: `$CHARACTER_ART_AUDIT_ROOT/` (task-supplied external scratch directory)
contains `matrix.csv`, `index.html`, `native-inventory.csv`, numeric probe JSONs,
`totals.json` and `backlog-native-frames.csv` (exact missing-cycle native unions).
The HTML lists original thumbnails/provenance; native reference images are omitted.

| Character / host | Authored | Native alias | Fallback | N/A |
| --- | ---: | ---: | ---: | ---: |
| MICA/S1 | 49 | 3 | 16 | 2 |
| MICA/S2 | 76 | 2 | 53 | 2 |
| MICA/S3K | 91 | 18 | 160 | 4 |
| TALON/S1 | 0 | 0 | 2 | 2 |
| TALON/S2 | 11 | 0 | 61 | 51 |
| TALON/S3K | 13 | 0 | 96 | 77 |

Counts above are **resolution obligations**, combining cycles, object byte slots
and separate presentation selectors, not unique drawings or completion percentages.
The loader inventories cover Sonic 88/214/251 mappings (S1/S2/S3K) and Tails
139/251 (S2/S3K); S1 has no native Tails. Authored canonical cycles can compress
native phases. TALON binding is diagnostic against Sonic host capabilities.

Generation priority; each family uses its own native reference bank:

| Priority | Native reference / target | Estimated drawings or slots |
| --- | --- | --- |
| 1 | TALON S2 then S3K Tails: PUSH, ROLL2, balance/hang/float/bubble/slide, carry/swim/flight variants | 20–35 poses after union/reuse; exact missing-cycle lists in CSV |
| 2 | MICA S1/S2/S3K canonical variants: float/leap/warp/slide/burn/shrink, balance variants, finite blink/get-up, carried/victory | 20–40 poses before reuse |
| 3 | MICA early S3K objects: swing $78–$7F; tumble $69–$77; twist $58/$5B; pole $5C–$62; bar $63–$68/$94/$95 | 8+15+2+7+8 = 40 byte slots; AIZ/HCZ first |
| 4 | TALON identity; S2 half-pipe 0–17; S3K Blue Sphere 0–11 plus result pose | about 5–6 identity images/host; 18+12+1 stage slots, including blank |
| 5 | MICA later S3K: gravity $31–$3C, cage $49–$54, chain $80–$85, carry $8A–$8D, hang $93, hurt $B4/$B5, wall $DF–$E3, parachute $E4–$EA, corkscrew $EF–$FA, emerald $C4, crane $C0 | 63 byte slots; filter unselected descriptors using owning-object table |
| 6 | TALON screens/endings: per-host continue/data-select; S2 ending $09–$0B/$0F–$11/$18/$19, pilot $01–$04/$10, photos 1–3; S3K reunion 3/4 | 8 ending + 5 reusable pilot + 3 photos + 2 reunion; other role counts still open |
| 7 | MICA powered gameplay: separate S2/S3K Super reference banks | roughly 40–80 each before reuse; Hyper shares geometry |
| 8 | Competition/2P miniature banks | S3K complete: MICA 44 / TALON 46 selectors; S2 remains separate |

Prerequisites: archetype-specific animation/raw-pose vocabulary and TALON NFRM
relocation; S2 FLY/TAILS_FLY bridge; S3K Tails transformation $29 versus
high-speed run $1F; finite BLINK/GET_UP; WAIT partial-loop restart; powered creator
bank/alternate-slope selection; sidekick pilot art/palette; role-specific
screen/ending/competition seams. Both creator WAIT cycles use LOOP while ROM
WAIT uses LOOP_BACK: Sonic parameters 2/6/53 (S1/S2/S3K), Tails 28 (S2/S3K).
This timing debt needs no new drawing.

Ownership evidence: Tails_pilot_frames (s2.asm:79690-79718) has 24 steps/five
unique mappings. ObjCE_Init (s2.asm:13928-13941) selects Tails final leap $0F.
Tails_Transform (sonic3k.asm:28712-28719) selects $29, whose AniTails29
(General/Sprites/Tails/Anim - Tails.asm:89) uses $EB/$EC/$ED. Tails reunion starts
at 3 in loc_5E334 (sonic3k.asm:124519-124533), then increments to 4 in
loc_5E3EE (sonic3k.asm:124575-124587). S2 photos are first-sidekick-owned
(`ModEndingPresentation.sidekickPhotos`). TALON has integrated wings, so separate
tail appendage art is N/A; its Tails route skips Sonic's ICZ snowboard bank.
Gilded/Solar currently reuse normal gameplay geometry; no third Hyper body set
or Solar Flickies is required.

**Remaining research:** this is a reproducible binding inventory and bounded
backlog, not definitive all-scene native-pose closure. TALON's remaining
ending/continue/data-select roles, full direct-writer reachability and exact
native-to-authored phase correspondence still require investigation. Do not
budget complete production art from obligation totals alone. No art was generated.

Validation: final queued `TestCharacterArtCoverageAudit` run passed 3 tests,
zero failures/errors/skips (all verified ROMs found, Java 25, display unset,
`-Dmse=off -Dopenggf.headless.gl=surfaceless-egl`). Queue wait 325s, Maven
35.6s, test execution 0.98s; user wait zero. Report closure checks cover all
loaded normal/powered scripts and mapping indices: 789 matrix obligations,
11,945 evidence rows. Python compilation, output/schema checks and scoped
citation check pass (existing design citations include near/unverifiable
references; no stale finding). No broad smoke/full-suite or route claim.

### MICA priority-3 art checkpoint (2026-11-09)

Local batch-3 art adds 40 semantic object-byte aliases and drawings (172 total
core frames; canonical key count remains 22). Native full-palette references
were enlarged 8× and redrawn by built-in GPT Image, two attempts per base pose.
The 33 chosen generated poses use uniform reduction and the shared indexed
palette; seven exact native quarter-turn relationships use the deterministic
rotation tool. No decoded native art enters Git. Prompts, source hashes,
selections and scale rectangles are under the MICA `object-poses/` directory.

The external review at
`$MICA_BATCH3_REVIEW_ROOT/review.html` compares
lossless native/MICA frames at 60Hz, including observed mapping streams from
packaged-mod AIZ vine and hollow-tree captures and an HCZ conveyor capture.
These positioned interactions prove the new aliases are selected; they do not
certify the intervening route or later-zone owners. Manual head/torso panels
retain the native hotspot and expose screen-axis foreshortening. Rear tumble
scale clusters simplify at 1×; new twist front/back views join older side art.

The production audit moves exactly those 40 MICA/S3K obligations from fallback
to native alias: **91 authored / 58 native alias / 120 fallback / 4 N/A**,
compared with the historical 91 / 18 / 160 / 4 above. Other host/character
counts are unchanged. The six-class Java run exercised 97 tests with zero skips;
the old vine fallback expectation required updating for the new swing aliases.
The focused MICA rerun passes all 34 tests without skips after that correction;
together with the unchanged five other classes, all 97 selected tests pass.
Python assembly and native-method discovery each pass seven tests; indexed
palette/geometry/regeneration verification and the example jar build pass.


### Phase 2 TALON engine audit

At base `c88c34cd61` the Tails body-type inventory (including fully qualified names) has 29 files and
53 checks (the earlier 49-file estimate was historical). The additional
`instanceof TailsRespawnStrategy` in SidekickCpuController remains a strategy
capability check: TALON registers that exact engine strategy. 21 gameplay checks
move to `PlayableArchetype.isTails`, resolving the registered `TAILS_ALONE`
archetype. 32 checks remain native identity/presentation decisions.

| File | Checks | Decision |
|---|---:|---|
| `src/main/java/com/openggf/level/LevelPlayableArtInitializer.java` | 2 | kept |
| `src/main/java/com/openggf/stageplay/runtime/StageplayNativeCast.java` | 1 | kept |
| `src/main/java/com/openggf/sprites/managers/NativePlayerArtBindings.java` | 1 | kept |
| `src/main/java/com/openggf/sprites/managers/SpindashDustController.java` | 1 | moved |
| `src/main/java/com/openggf/sprites/playable/PlayableCharacterIdentity.java` | 1 | kept |
| `src/main/java/com/openggf/sprites/playable/PlayableHurtRadiusTransition.java` | 1 | moved |
| `src/main/java/com/openggf/sprites/playable/PlayablePuppet.java` | 1 | kept |
| `src/main/java/com/openggf/level/objects/ObjectTouchResponseController.java` | 1 | moved |
| `src/main/java/com/openggf/level/objects/SkidDustObjectInstance.java` | 1 | moved |
| `src/main/java/com/openggf/game/sonic3k/Sonic3kSuperStateController.java` | 22 | kept |
| `src/main/java/com/openggf/game/sonic3k/events/Sonic3kMGZEvents.java` | 2 | moved |
| `src/main/java/com/openggf/game/sonic3k/objects/HpzEmeraldTheftPlayerControlObjectInstance.java` | 1 | kept |
| `src/main/java/com/openggf/game/sonic3k/objects/Mgz2EndEggCapsuleInstance.java` | 1 | moved |
| `src/main/java/com/openggf/game/sonic3k/objects/S3kDezTeleporterObjectInstance.java` | 1 | moved |
| `src/main/java/com/openggf/game/sonic3k/objects/CnzVacuumTubeInstance.java` | 1 | moved |
| `src/main/java/com/openggf/game/sonic3k/objects/SozFallingIntroInstance.java` | 1 | moved |
| `src/main/java/com/openggf/game/sonic3k/objects/SozHyudoroBodyObjectInstance.java` | 1 | moved |
| `src/main/java/com/openggf/game/sonic3k/objects/SozMinibossChild.java` | 1 | moved |
| `src/main/java/com/openggf/game/sonic3k/objects/FbzMagneticPendulumObjectInstance.java` | 2 | moved |
| `src/main/java/com/openggf/game/sonic3k/objects/HCZHandLauncherObjectInstance.java` | 1 | moved |
| `src/main/java/com/openggf/game/sonic3k/objects/badniks/BlastoidBadnikInstance.java` | 1 | moved |
| `src/main/java/com/openggf/game/sonic3k/objects/badniks/JawzBadnikInstance.java` | 1 | moved |
| `src/main/java/com/openggf/game/sonic3k/objects/badniks/RocknBadnikInstance.java` | 1 | moved |
| `src/main/java/com/openggf/game/sonic3k/objects/bosses/SszMtzBossOrbChild.java` | 1 | moved |
| `src/main/java/com/openggf/game/sonic2/objects/LauncherSpringObjectInstance.java` | 1 | moved |
| `src/main/java/com/openggf/game/sonic2/Sonic2SuperStateController.java` | 1 | moved |
| `src/main/java/com/openggf/game/sonic3k/bonusstage/slots/S3kSlotPlayerArt.java` | 1 | kept |
| `src/main/java/com/openggf/game/sonic3k/resources/S3kRuntimeArtCoordinator.java` | 1 | kept |
| `src/main/java/com/openggf/game/sonic3k/ending/EndingNativePlayerBinding.java` | 1 | kept |

Kept: native body identity fallback; native-only Stageplay cast/puppet rig;
separate appendage art; HPZ's raw native mapping-frame write; and the stock
super controller's palette, art, Hyper effects and Flicky branches; native slot
body art, ending appendages and body-PLC bookkeeping. Authored
super profiles use the separate `ProfileSuperStateController`, which owns their
physics and gates; the retained stock-only emerald/shield gates and physics
selectors do not dispatch TALON. TALON declares
only Solar (Super), with neither stars nor after-images nor Super Flickies.

Moved: launcher/radius geometry, hurt radii, dust origin, flight attacks, CNZ
flight cancellation, SOZ intro alignment, DEZ transport, MGZ transition/capsule
participation; and S2 normal-form Tails geometry restoration. Existing native tests exercise the same code after dispatch;
Tails' builtin key still selects every former Tails branch. The shared rolling
routine also dispatches by archetype instead of requiring a subclass override.

Flight reuses `Tails_Move_FlySwim (sonic3k.asm:27632-27674)`: the native flap
subtracts $20 until the native upward-speed gate resets its state. Solar's
explicit `SUPER_FLIGHT_LIFT` declaration subtracts $40 instead; thresholds,
duration and fatigue stay native. `CROSS_GAME_FLIGHT` opts into this model in
S1/S2, where player-controlled flight is a mod rule, not ROM behaviour. Native
Tails declares neither extension. The shared controller stores all flight state
in the existing rewind-captured double-jump fields.

MICA declares TALON as her default sidekick. Selecting MICA in the launch panel
selects that team; the sidekick row remains editable for solo play. TALON is a
separate selectable solo leader with `TAILS_ALONE`. S1 sidekick support is
provided by the MICA patch, leaving the stock S1 roster unchanged.

**Playable wiring (2026-11-09):** TALON now loads his independently baked
`art/talon.ggfp` (82 frames, 13 canonical animations) and palette through bounded
mod assets. `TalonSolar` is generated from `art/talon/presentation/solar.yaml`;
`solar_java.py --check` and its Python sync test guard the palette tables.
The profile keeps Tails' Super physics, double flap lift and no Flickies/Hyper.
The mod aliases ascent/carry wing variants to its own flight cycle. S1/S2, which
have no native flight IDs, select private mod scripts from the existing tick
hook. S3K's shared glide/flight IDs are rebound to TALON's wing cycles before
native flight selects its animation. The art initializer uses the host's default
motion vocabulary when a creator's route archetype is absent from the native
roster; this allows S1 to install TALON's own art without nonexistent native
Tails assets.
`onMovementTick` requests the reticle only for airborne held down using the
existing pinned `isDownPressed()` API. The air ability gate tries native Super
transformation first, then down+jump divebomb (Instagib in Solar); ordinary
jump still enters/flaps native flight. Active manual flight offers its jump edge
to the character hook before native flap handling; native Tails declines it.
Ground duck and flight descent are native.
The backward-compatible `PresentationSupplier.load(code, host)` overload lets
the HUD adapters supply their host without an internal engine lookup in mod code.
The owner wrapper preserves its fault boundary and the candidate signature pin
adds that one method; the API remains the unpublished 0.7 candidate.

S2's 32-pixel native glyph composition spells TALON. S3K retains TAL because
TALON's native glyphs exceed 32 pixels. S1 retains its native fallback pending
supplementary T/A/L art; no letters were painted. Other TALON presentation
surfaces and exact WAIT partial-loop playback remain separate obligations.


Validation ownership: `TestPlayableArchetype` checks that the replacement boolean
is identical to `instanceof Tails` for all native bodies in all three hosts,
covering the dispatch substitution at every moved site. Focused object coverage
includes LauncherSpring, HCZHandLauncher, FbzSpiderCraneAndPendulum,
S3kBadnikTouchResponseProfiles, S3kBossTouchResponseProfiles, S3kJawzBadnik,
SozFallingIntro, SozMiniboss, S3kDezTeleporterHeadless and
Sonic3kMgz2EndBossEvents. Shared native motion/CPU and flight tests cover the
remaining geometry and flight-state consumers. TALON acceptance exercises real
host boot, solo flight activation, flap acceleration, Solar doubling, team
selection and a moving CPU follower in S1/S2/S3K. These are bounded encounters,
not end-to-end zone route certification.


### Phase 2 verification (2026-11-09)

Implementation source was based on `c88c34cd619c62c56d185c163aa38e7551207c54`.
The complete audit includes fully qualified body checks: 53 original checks in
29 files, 21 moved and 32 kept. The strategy-type check remains separately.

All commands use Java 25, verified ROM inputs and the queued Maven wrapper.
The expanded focused selection ran 298 tests: the 37 MICA integration tests
(including TALON in all three hosts), signature surface, both native flight
classes, the four required S3K selectors, launchers, pendulum, badnik/boss touch,
Jawz, SOZ intro/miniboss, DEZ teleporter, MGZ events, manual CPU flight and dust.
One newly exposed unbound Mockito body was corrected in the archetype helper;
the final pendulum/archetype rerun passed 8 tests, no skips. Earlier iterations
also corrected the temporary short HUD name and a removed native rescue-actor
import. No final source changes followed that correction run.

```sh
python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=TestMicaCharacterIntegration,TestModApiSignatureSurface,TestPlayableArchetype,TestTailsFlightController,TestPlayableSpriteMovementTailsFlight,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestLauncherSpringObjectInstance,TestHCZHandLauncherObjectInstance,TestFbzSpiderCraneAndPendulum,TestS3kBadnikTouchResponseProfiles,TestS3kBossTouchResponseProfiles,TestS3kJawzBadnik,TestSozFallingIntro,TestSozMiniboss,TestS3kDezTeleporterHeadless,TestSonic3kMgz2EndBossEvents,TestSidekickCpuManualFlight,TestSpindashDustControllerSplash' test
python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=TestFbzSpiderCraneAndPendulum,TestPlayableArchetype' test
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py \
  --base c88c34cd61 --workers 2 --headless-gl surfaceless-egl --run
```

The smoke selection ran all 4,507 candidate classes, 91 ordinary batches and one
guard batch: 38,006 tests, 38 failures, one error and 50 skips. It spent approximately 8,684
seconds waiting for admission and 1,838.64 seconds executing test batches,
plus 34.2 seconds of preparation. These are runner timings, not total task time;
there was no user-approval wait. Its unchanged source fingerprint was
`c59f9005bc82`.

```text
VERDICT FAILED: head=c88c34cd619c source=c59f9005bc82 change-based 4507/4507 candidate classes; ordinary 91/91 batches; guards 1/1 batches; tests=38006 failures=38 errors=1 skipped=50 (allowed=47 unexplained=3); missing ROM properties=none; skip inputs audio_reference_files=false,gl=true,s1_bizhawk_reference=false,s2_bk2=false,s3k_observations=false; bounded smoke selection, not a full-suite pass
```

Baseline attribution uses an isolated cow checkout at the exact base, the same
ROM properties, EGL backend and ordinary two-fork setting (one fork for guards).
It compares case identities, exception types and assertion messages; normalization
is limited to one Three Islands object identity and the three MHZ assertions
whose source explicitly compares unordered sets. The CNZ five cases match full
messages, including input frames and native state, exactly. The initial baseline
runs covered 118 tests, reproducing all 38 ordinary failed/error cases: 34 exact
messages and four normalized matches, with no unmatched case. This establishes
inherited gaps, not green route certification.

| Failure class (package shortened) | Cases | Baseline obligation |
|---|---:|---|
| Sonic2NativeCompetitionHost | 2 | inherited DISPLAY precondition |
| Sonic2InterlacePixels | 2 | inherited DISPLAY precondition |
| CompetitiveHalfpipeEgl | 4 | inherited DISPLAY precondition |
| ModZoneRuntimeProfile | 1 | same null game-id HUD palette exception |
| ThreeIslandsScene | 8 | same screen/story assertions |
| PrivateLobbyScreenRendering | 1 | same full-text pixels assertion |
| LrzSszCheckpointRoutes | 1 | same death at input 6499 |
| S3kCnz1MinibossCompletionEdge | 5 | identical solo route frames/state |
| S3kFreshLevelRngState | 1 | same D01 title-owner assertion |
| S3kIcz1PhysicalLowerPlatformReplay | 1 | same final-neutral turn assertion |
| S3kMhzAct2AuthoredRoute | 3 | same absent events/deaths |
| S3kMhzAct2EntryHeadless | 1 | same camera minimum 872 versus 896 |
| S3kMhzAuthoredRoute | 1 | same missing event set |
| S3kMhzTailsAuthoredRoute | 1 | same missing event set |
| S3kMhzTeamAuthoredRoute | 1 | same death at input 26152 |
| S3kMhzWideAuthoredRoute | 1 | same missing event set |
| DezIncomingFinalRouteCapture | 2 | same deaths at 14633/14638 |
| Icz2ColdCubeBridgeRoute | 1 | same expected six versus five objects |
| S3kCompleteRunReferenceProducer | 1 | same manifest digest exception mismatch |
| DrawPathFieldWriteGuard | 1 | AIZ plane creator caches; baseline check recorded below |

The eight DISPLAY precondition failures and three unclassified reel skips came
from inherited `DISPLAY=:0`, despite the explicit EGL backend. The other 47
skips are classified optional inputs/opt-ins. The correction command removes
both display variables and runs every affected class; its result is recorded
below. No skip-policy allowance was added to hide the environment problem.

```sh
env -u DISPLAY -u WAYLAND_DISPLAY python3 tools/testing/maven_queue.py \
  -Dmse=off -Dopenggf.headless.gl=surfaceless-egl -DforkCount=2 \
  '-Dtest=TestSonic2NativeCompetitionHost,TestSonic2InterlacePixels,TestCompetitiveHalfpipeEgl,TestTestReelExtension' test
```


The baseline draw-write guard ran 3 tests (one failure, no skips) and reproduced
all seven AIZ creator-cache/write-count differences exactly. Including that
check, 121 baseline tests reproduce every smoke failed/error case: 35 exact
assertion messages and four normalized matches; no unmatched identity or
assertion. Baseline commands used `-DforkCount=2 -Dtest=<failed classes> test`,
with `-Pguards -DforkCount=1 -Dtest=TestDrawPathFieldWriteGuard test` for the
single guard. No inherited failure was fixed or admitted as new expected debt.


With both display variables removed, the four-class rerun executed 14 tests:
12 passed, two errored, no failures and no skips. The reel's three formerly
unclassified cases now execute successfully. The two interlace cases progress
past their environment assertions and expose a reservation collision:
`[0x1d8000-0x1d9000]` conflicts with the permanent Character special-stage bank
`[0x1d8000-0x1e8000]`. The exact two errors are checked separately at the baseline
with the corrected environment; no pattern-bank contract is changed in this lane.


The corrected-environment baseline ran the two interlace tests and reproduced
both error types and full reservation-collision messages exactly. The corrected
four-class current-tree run therefore has 12 passes and two inherited errors,
with no skips. All original smoke failures/errors, plus both errors newly exposed
by fixing display isolation, are attributed to the base. There is no newly
observed regression, but the raw broad run remains **FAILED**, not green and not
a full-suite pass. Inherited route, fixture and AIZ draw-cache obligations remain
open; interlace pixels remain blocked by the pre-existing bank collision.

The candidate API pin adds six normalized lines: the expanded character
constructor and `defaultSidekick` component/accessor, two movement declarations,
and `SidekickRespawnStrategy.tails(...)`. Existing constructors retain their
source/signature forms. The compiled normalized snapshot matches the pin.
Disassembly citation and whitespace checks pass. No art assets, API version,
skip-policy debt or native algorithm constants were substituted to obtain these
results. At that historical checkpoint, down-held reticle and divebomb remained
the `TalonCharacter` TODO; the playable wiring section above supersedes it.

### TALON playable wiring verification (2026-11-09)

Base: `fb617c82dd82ff6bc06d108ea08f6b0a2644e845`, isolated local
`feature/ai-talon-wiring`; no push or integration requested. No MICA art/sheet
changes. Final bounded selection: **214 tests, zero failures/errors/skips**,
including 40 MICA/TALON integration cases. All required ROMs were found.
The final run waited 99 seconds for admission and Maven reported 2:21 execution;
these are separate from earlier iteration/queue time, which is not totalled.
There was no user-approval wait. This is focused verification, not smoke or a
full-suite result.

```sh
env -u DISPLAY -u WAYLAND_DISPLAY python3 tools/testing/maven_queue.py \
  -Dmse=off -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=TestMicaCharacterIntegration,TestHomingAttackMutator,TestHomingStrikePose,TestModApiSignatureSurface,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestTailsFlightController,TestPlayableSpriteMovementTailsFlight,TestSonic1CharacterPresentation,TestSonic2CharacterPresentation,TestLevelPlayableArtInitializerModArt' test
python3 -m unittest discover -s tools/modding
python3 tools/modding/playable_art/solar_java.py --check
python3 examples/mica-and-talon/build.py
git diff --check
```

Python discovery passed **52 tests**; Solar sync passed. Packaging reports zero
compatibility findings. The signature test confirms the single added host-aware
presentation overload against the updated candidate pin; the release descriptor
retains unpublished candidate 0.7.0 (the commit hook forbids descriptor edits
for ordinary candidate signature regeneration). ModApiVersion documents the addition. No new disassembly citations
were introduced. The repository-wide citation audit reports existing stale
references outside the edits; the targeted new API/wrapper files pass.

Rendered normal/tired-flight assertions run against production capture sessions
in S1/S2/S3K. Logic acceptance exercises reticle release/landing, actual down+jump
input from flight, native badnik touch defeat in normal/Solar modes, Instagib mode
selection, and deterministic registry rewind across the strike. Earlier assertions
exposed S1's missing native Tails art and S3K's glide/flight ID collision; neither
was accepted as a passing visual result. Direct facade-only activation would miss
the native active-flight input branch, so acceptance uses the frame driver.

Media and reproducible scripts are under
`$HOME/scratch/agent-tmp/talon-wiring-20261109/`:
`s2-flight/capture.mp4`, `s2-movement/capture.mp4`, `s2-dive/capture.mp4`,
`s3k-flight/capture.mp4`, `s3k-movement/capture.mp4`, `s3k-dive/capture.mp4`,
`s3k-team/capture.mp4`, and `contact-sheet.png`. Each clip retains `state.csv`
and native frames; `capture.py`, controller scripts and `contact.py` reproduce
selection and labelling. Down-held targeting precedes the dive in the encounter
clips; both show native defeat explosions/100-point awards. Videos are bounded
positioned encounters and native team movement, not complete-route certification.
S1 supplementary T/A/L glyphs, a full-width S3K HUD name, other TALON presentation
surfaces and exact WAIT partial-loop playback remain outstanding.

### Shared animation-family implementation (2026-11-09)

Branch `feature/ai-animation-families` starts at `4349476aa1`. The single shared
vocabulary remains `CanonicalAnimation`; module-owned phase profiles under
`src/main/resources/animation-families/` map native script slots into those
families. `AnimationFamilies.Binding` contains animation/family identity and
ordered phase indices, not art, timer bytes or animation bytecode. Every loaded
normal Sonic script has an entry; a null family explicitly marks N/A; unnamed native scripts may retain a known family.
The maintained review generator inventories **101 Sonic scripts and 75 reference
Tails scripts**, including native frame count/order, timing, terminator and
script-label citation. Tails metadata is reference-only and does not change
archetypes, flight or CPU code.

Creator sheets author `family-<name>/<phase>` aliases in the existing NFRM table.
Complete phase sets bind against actual host ROM scripts: repeated frames,
LOOP_BACK counts, SWITCH and delay all remain native. Canonical movement/slope
scripts and sparse-sheet compatibility continue through the existing path.
There is no format version change, migration or canonical enum renumbering.
The candidate API adds `DonorCapabilities.getAnimationFamilies()`, the binding
record, and a default semantic `AnimationId.toCanonical()`; normalized signature
pins move together. Typed and canonical cross-game animation requests can use
host-timed scripts in a private namespace; raw integer writes keep their existing
meaning. Cross-game balance/skid/slide profile selectors use these scripts too.

| Drawing family | Host script vocabulary | Authored phase set |
| --- | --- | --- |
| ROLL | ROLL, ROLL2 | Five curl/blur phases; host repetition/order retained |
| SKID | STOP, SKID | Brake phases; S1 loops, later games switch to WALK |
| FLOAT2 | FLOAT, FLOAT2, FLOAT3, FLOAT4 | Eight directional tumble phases; explicit host subsets |
| BALANCE | BALANCE | Toward-edge wobble; S1 two-phase subset |
| BALANCE2 | BALANCE2 | Toward-edge near-limit wobble; S2 repeats an intermediate phase |
| BALANCE3 | BALANCE3 | Away-edge wobble |
| BALANCE4 | BALANCE4 | Away-edge entry and four-phase tail; native LOOP_BACK 4 |
| LEAP1 | LEAP1, LEAP2 | Ending leap and transition, native LOOP_BACK 1 |
| BUBBLE | GET_AIR, BUBBLE | Air pickup plus return through walk poses |
| HURT_FALL | WATER_SLIDE, SLIDE, HURT_FALL | Two recoil/slide phases, host-specific order |
| HURT | HURT, HURT2 | Shared recoil drawing |
| BLANK | NULL_ANIM, BLANK | Transparent phase; native loop or finite switch |
| HANG / HANG2 / TAILS_CARRIED | Same keys | Separate hanging/carry families and hand positions |
| BLINK / GET_UP | Same keys | Shared blink phases; distinct recovery phases when needed |
| VICTORY | VICTORY | Entry and partial-loop celebration phases |
| WARP1–4 / SURF / BURNT / SHRINK | Same keys | Distinct native silhouette/scale phases retained |

The full inventory and **94 lossless full-frame GIF comparisons** are generated
at the task's external review path `index.html` (reported at handoff), beside
`family-inventory.csv`. GIF timing uses cumulative 60 Hz-to-centisecond rounding;
speed-driven roll is shown at an illustrative one-tick interval. Native decoded
pixels remain external. Source art, indexed palette and hotspot dimensions are
unchanged. MICA drops **226 → 208 frames**, removing 18 redundant drawings and
adding 106 semantic phase aliases; representative script count remains 52.
Both `recipe.json` and `frames.yaml` carry the change. The recipe's previously
stale full-loop WAIT now matches the already-correct handoff's six-step tail.

Retained source drawings whose filenames name a host:

| Sources | Reason |
| --- | --- |
| `variant-s1-33`, `-rot52`, `-rot53`, `-rot54` | Four warp orientations; later games supply no equivalent family |
| `variant-s1-43`, `variant-s1-44` | Ending leap/transition silhouette, distinct from spring |
| `variant-s1-49` | Surf stance, distinct from idle and balance |
| `variant-s1-4b` | Burnt family source; reused where the same family is available |
| `variant-s1-4e`, `-scale79` through `-scale82` | Shrinking body scales, not another host's death pose |
| `variant-s2-0a` | S2 crouched recovery; S3K recovery has a different pose |
| `variant-s2-6c` | Second overhead hand phase absent from S3K's single HANG2 pose |

Host prefixes in source filenames are provenance, not runtime selection. These
are phases within the shared families, not parallel complete per-host sheets.

New donation equivalences resolve STOP→SKID, FLOAT3/FLOAT4→FLOAT2,
GET_AIR→BUBBLE, WATER_SLIDE/SLIDE→HURT_FALL, NULL_ANIM→BLANK and HURT2→HURT;
BURNT uses the native burned-death slot. Same-named balance/float families can
also retain the earlier host's step order and timing. A donor without a required
phase keeps its existing compatibility script. S1 warp/leap/surf/shrink have no
complete later-game family; their approximate fallbacks remain explicit.

Rejected approach: treating unused legacy dash script numbers as S1 leap poses.
SonAni_Dash2 (s2.asm:38740) and AniSonic12
(General/Sprites/Sonic/Anim - Sonic.asm:63) retain old slot numbers but do not
establish matching modern mapping geometry. They are N/A in the Sonic profile.
Also, finite GET_UP is not universal: SonAni_GetUp (s2.asm:38726) switches to
WALK, whereas AniSonic0B (General/Sprites/Sonic/Anim - Sonic.asm:56) loops. The
shared table preserves that shipped difference; forcing a finite S3K ending
would violate native timing/control flow.

TALON's remaining character art should be budgeted by phases, not per-host
script count: one set each for PUSH, roll/blur, toward/away edge wobble and
near-edge entry, tumble directions, hanging hand positions, air pickup and
slide/recoil. Keep Tails' carry/ascend/tired/swim phases as separate functional
families; integrated wings still make detached tail art N/A. Reuse common phases
across S2/S3K rather than commission two complete sets. The previous 20–35-pose
estimate remains an upper planning range; the actual decrement depends on his
approved poses, not Sonic phase counts. TALON's measured coverage is unchanged
until the archetype lane installs his own phase correspondence and aliases.

Production audit fallback obligations, compared with a baseline report generated
using `4349476aa1` sources: MICA S1 **0 → 0**, S2 **40 → 38**, S3K
**106 → 102**. TALON S1/S2/S3K remains **2 / 61 / 96**. These are binding
obligations, not drawing counts or route certification. Family coverage is
recognized only when every required phase alias is supplied; incidental native
script-frame equality is insufficient.

For the existing Sonic-host diagnostic specifically, TALON's 23 missing canonical
slot names reduce to **18 new drawing families**: ROLL2 and HURT2 reuse his
existing ROLL/HURT; FLOAT/FLOAT2, SLIDE/HURT_FALL and NULL_ANIM/BLANK each
collapse to one new set. This removes five independently budgeted script sets,
without pretending Sonic phase counts define Tails' remaining flight/swim art.
The reference Tails rows in the CSV enumerate the actual ordered phases for
that lane to adopt. Required sets are PUSH; four edge-balance families;
FLOAT2 directional tumble; HANG/HANG2; BUBBLE pickup/return; HURT_FALL recoil;
BLINK and recovery; blank; drown/burnt; victory; and powered transformation.
Existing flight/tired-flight and movement art remains approved; carry, ascend,
swim and tired-swim need their distinct Tails reference phases.

Validation checkpoint: the requested 14-class focused selection passes 163 tests
with zero failures/errors/skips, including all four required S3K test names.
The Python assembly selection passes five tests; palette/geometry and deterministic
208-frame recipe regeneration pass. Initial local failures exposed invalid alias
key syntax, old 226-frame assertions and the stale recipe WAIT terminator; those
were corrected. Scoped citation validation reports no stale citations (three near
and eight unverifiable pre-existing references remain). Donation changes after
this checkpoint receive a focused rerun before the final smoke run.

Final correspondence audit corrects two legacy-index traps: S3K's roll blur is
its first phase ($96), while S1/S2 place the blur last in their unique phase set;
Sonic's $20 script is the lying idle pose despite the legacy FLY enum spelling.
The Sonic profile assigns that slot to WAIT phases 7/8, leaving Tails' flight
meaning untouched. S3K carried scripts $22/$23 share the same phases; $23 is
an unnamed native exit script with SWITCH rather than a second drawn set.
The initial smoke attempt was stopped after two passing batches (762 tests,
zero skips) to correct these metadata errors; its incomplete verdict is not a
validation pass. The final run uses the corrected frozen source.

The final stationary/movement pass supplies 106 phase aliases. WALK/RUN keep
materialized contiguous slope-bank addresses while their phase selection follows
the host ROM script (S1's six-step walk uses a subset of the common eight-phase
set). MICA's bound WAIT tails are now exactly **2 / 6 / 53** for S1/S2/S3K;
DUCK/LOOK_UP retain native one-step partial loops rather than flattened HOLD.
The corrected focused selection passes **163 tests, zero failures/errors/skips**.
The stale recipe WAIT issue is independently reproduced on `4349476aa1`:
`verify.py` passes but rewrites WAIT to LOOP, after which the existing assembly
selection fails `test_authored_finite_scripts_and_invalid_rewind` and
`test_roundtrip_pixels_hotspots_slopes_and_custom_hook`. The baseline handoff was
restored after this probe. Both recipe/handoff and the five assembly tests now
agree on this branch.

### Final donation, anchor and validation evidence

Built-in Sonic's canonical and typed native requests resolve against the loaded
donor namespace: prefer a host-timed family projection, then donor-native or
compatibility resolution. Creator sheets, Tails and Knuckles retain their owners.
Explicit donation checks cover STOP, FLOAT3/4, GET_AIR, BURNT, WATER_SLIDE/SLIDE,
NULL_ANIM, HURT2, TAILS_CARRIED, HANG2, BLINK, GET_UP and WAIT in S1/S2.
Host WAIT timing and frame indices travel with the projection. Only a host whose
native profile owns the finite idle interrupt enables BLINK/GET_UP selectors;
Obj01_MdNormal_Checks (s2.asm:36444-36468) retains its native gate. S1/S3K do not
gain the S2 gate. Movement speed handlers retain their existing owners.

The expanded tests rejected exact-name FLOAT lookup: S3K's one-frame FLOAT slot
cannot supply a whole float family. Projection selects the complete FLOAT2 bank
before applying the host's ordered phases. A missing donor drawing phase repeats
the first compatible family pose while preserving the host's timing and finite
terminator; absent families retain their declared compatibility fallback. This
keeps S2 HANG2 and GET_UP control flow valid without generating new art. MICA
retains the genuinely distinct S2 recovery/grip drawings.

The live, formerly unclassified S3K `$19` script also belongs to HURT_FALL.
Its `$D7/$D8` RGBA pixels equal `$8C/$8D`, but hotspots differ by `(4,-4)`.
loc_726E (sonic3k.asm:9120-9124) selects it for ice slides, and loc_7402 (sonic3k.asm:9278-9283) selects the same variant.
The binding records `offsetX=-4, offsetY=4`; other bindings default to zero.
word_147A2C (General/Sprites/Sonic/Map - Sonic.asm:1334-1337) and word_147A40 (General/Sprites/Sonic/Map - Sonic.asm:1338-1341)
corroborate the shifted mappings, compared with word_147484 (General/Sprites/Sonic/Map - Sonic.asm:1046-1049) and word_147498 (General/Sprites/Sonic/Map - Sonic.asm:1050-1053).
The binder creates two mapping views sharing art and DPLC, preserving every
piece's palette, flip, priority and tile fields. The candidate binding record
adds integer offsets, and the normalized API pin updates with it. Audit output
separates the fixed 256-slot native prefix from generated mapping views.

Final focused verification passes **192 tests, zero failures/errors/skips**,
including all requested names, the four required S3K names, Super profile refresh
and all 21 HPZ sanctuary cases. Python assembly
passes five tests; palette/geometry and deterministic regeneration pass. The
final audit has MICA fallback obligations **0 / 38 / 102**, against baseline
**0 / 40 / 106**; TALON remains **2 / 61 / 96**. There are **208 source drawings,
106 family aliases, two generated mapping views and 94 lossless review GIFs**.
The inventory contains 101 Sonic and 75 reference Tails scripts. No new art or
Tails archetype/CPU code is introduced.

Two exploratory smoke runs were interrupted to correct metadata and donation
caller omissions, rather than reported as passing validation. A later attempt
stopped before Maven because the shared filesystem was full (zero tests run).
The truncated design and handoff were recovered from the prior staged Git blob
and the authoritative recipe. This task's disposable baseline build output was
removed; the baseline source and recorded 154-test pass were retained. Final
smoke verification runs only after source recovery and regeneration checks.

### Smoke failure attribution and corrected headless environment

The first broad diagnostic completed 37 batches (14,862 tests) before stopping:
two failures were Mockito assertions expecting the old integer NULL overload,
and eight were native EGL cases rejecting inherited `DISPLAY=:0`. On the exact
baseline, `TestSonic1PlcProducerCoverage` passes 19 tests and
`TestSonic1GiantRingObjectInstance` passes five; their assertions now verify the
new typed NULL request without changing forced-blank behavior. The baseline
reproduces all eight display failures. Clearing only DISPLAY reveals the same
WAYLAND_DISPLAY requirement, so final commands unset both desktop variables.

With both unset, `TestSonic2NativeCompetitionHost` and `TestCompetitiveHalfpipeEgl`
pass on both trees (six tests). `TestSonic2InterlacePixels` has two identical
pre-existing errors on both trees: private windows `[0x1d8000-0x1d9000]` overlap
permanently reserved character special-stage art `[0x1d8000-0x1e8000]`. This task
does not alter that unrelated reservation or turn the errors into skips. The
corrected focused selection exercises 195 tests: the original 163 and the 32
attribution cases; the eight display-sensitive cases are also rerun separately
with the fully headless environment. Baseline identity, assertion and skip
comparison—not totals alone—governs final smoke attribution.

### Broad-smoke native borrowing regression

The corrected smoke attempt reached 79 ordinary batches / 33,317 tests before
stopping for two new HPZ sanctuary rewind failures. It recorded 27 assertion
failures, three errors and 41 allowed skips. Same-class runs at `4349476aa1`
reproduce the other 28 failure/error cases: interlace reservation collisions,
the mock mod-zone game ID, Three Islands/lobby assertions, SSZ/CNZ/ICZ routes,
fresh-level RNG/title ownership and MHZ entry/authored routes. Compare unordered
expected-set diagnostics as sets; their display order varies between JVMs.
The baseline HPZ class passes all 21 cases; this tree reproduced two failures
both in broad smoke and a focused diagnostic run.

The first HPZ rewind occurred while literal host art was borrowed. The Super
controller lazily cached that temporary native profile as its normal profile.
After ordinary donation returned, subsequent rewind restored native WAIT ID 5
instead of the host-timed family ID 517. Boundary instrumentation identified the
profile change outside the native-art binding owner. Ordinary art initialization
now explicitly binds the Super controller's normal profile on creation and art
refresh, before scenes can borrow resources. The existing native borrowing and
post-restore owners retain their ordering. A focused regression covers borrowed
art followed by ordinary release and a second ordinary-art refresh. Temporary
instrumentation is removed; no Tails archetype or CPU code is changed.

The correction passes all 21 HPZ sanctuary cases and 54 focused tests in total
(`TestSuperStateController`, `TestS3kHpzSanctuaryLifecycleProduction`,
`TestCrossGameFeatureProviderRefactor`, `TestModApiSignatureSurface`), with zero
failures/errors/skips. The same-class baseline comparisons preserve the exact
assertions and input/frame boundaries; no route fixture or skip policy changes.

The review now groups playback by family and shows a compact S1/S2/S3K table
with explicit absent-host cells. Tails references use local phase indices,
classify their blink and reserved slots independently of Sonic, and do not
inherit Sonic mapping offsets. All 94 GIF hashes remain byte-identical after
this inventory-only refinement; all 176 rows retain native script citations.


### Final delivery verification (animation families)

The corrected Java/art/script tree passes the requested focused selection plus
Super-profile/HPZ coverage: **192 tests, zero failures/errors/skips**. Assembly
passes five Python tests and deterministic 208-frame regeneration. The refreshed
audit still reports MICA fallback obligations **0 / 38 / 102**, against baseline
**0 / 40 / 106**; TALON remains **2 / 61 / 96**.

The required change-based command completed all 4,508 candidate classes,
91 ordinary batches and one guard batch:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py --base 4349476aa1 --workers 2 --headless-gl surfaceless-egl --run
```

```text
VERDICT FAILED: head=4349476aa187 source=6f21149b677f change-based 4508/4508 candidate classes; ordinary 91/91 batches; guards 1/1 batches; tests=38012 failures=31 errors=3 skipped=49 (allowed=49 unexplained=0); missing ROM properties=none; skip inputs audio_reference_files=false,gl=false,s1_bizhawk_reference=false,s2_bk2=false,s3k_observations=false; bounded smoke selection, not a full-suite pass
```

All **33 inherited failure/error cases** were rerun by class at `4349476aa1`
and compared by test identity, assertion/exception and skips. In addition to the
previously recorded cases, these include DEZ incoming-final deaths at inputs
14633/14638, ICZ2 cube-bridge expected 6/actual 5, the audio reference producer's
exception mismatch, and the unchanged AIZ draw-cache inventory mismatch. No
fixture, draw-debt inventory or skip-policy rule was relaxed.

One new packaging failure in that full run identified missing native-image
resource includes for the family profiles. The only subsequent production edits
are native-image JSON metadata: include `animation-families/*.json`, and register
the Binding record and canonical enum for Jackson reflection. No gameplay Java,
ROM correspondence, art or script input changed after the ordinary lane. That
37,176-test lane remains applicable. The complete guard lane was rerun on the
packaging correction:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl -Pguards test -B
```

The rerun executes **836 tests, one failure, zero errors/skips**: the sole failure
is the exact inherited AIZ draw-cache inventory assertion. Native-image resource
reachability passes; ArchUnit's 34 cases pass. JSON syntax, all three include
matches and reflected class names also pass. No native-image binary build or
full-suite pass is claimed. All 49 smoke skips are allowed; required/unclassified
skips and missing ROM properties are zero.

The full smoke measured 1,789.44 seconds of Maven execution plus 39.92 seconds
preparation, and approximately 1,893 seconds of queue wait (rounded admissions).
User-input wait is zero; other investigation/baseline wall time is unmeasured.
The review directory retains `verification.json`, exact logs and same-class
baseline attribution. Failed category diagnostics were inspected and acknowledged;
only this task's owned detached baseline checkout is removed after preserving
its results. The implementation branch and review assets remain for integration.

### TALON shared-family art and archetype binding (2026-11-09)

Local `feature/ai-talon-families`, based on `c3789d8d05`; no push or integration.
The source bank grows **82 → 167** drawings and **13 → 47** canonical keys.
The 85 additions cover the 18 shared-family obligations (BURNT reuses DROWN,
BLANK is literal transparency), plus the seven missing flight/swim variants.
All 82 approved PNGs remain byte-identical. There are 27 built-in GPT Image
requests: three alternative rows per family, plus rejected/replaced PUSH,
HANG and reverse-edge strips. Final reductions and per-phase selections are
recorded under TALON's art directory; native pixels remain in scratch only.

The owner authorized the initially excluded tooling/binding changes after the
Sonic/Tails phase-count conflict was demonstrated. Per-game capabilities now
provide `forPlayableCharacter(TAILS_ALONE)` views with Tails correspondence and
raw-pose vocabularies. The original provider and Sonic donation path are unchanged.
Native timing and terminators come from loaded scripts, including Tails WAIT and
BLINK LOOP_BACK tails. The assembler accepts flight/swim variants and canonical
ROLL2's required speed flag. Both new candidate API methods are signature-pinned;
the unpublished candidate version is unchanged.

TALON's prior movement hook overwrote every flight variant with normal FLY and
used unrelocated indices. It now uses the native controller's pure pose selector,
preserves source terminators, reserves private IDs and applies the mapping-bank
relocation. Native flight writes its pose after the movement hook; TALON
redirects only that selected native ID through its own setters into the private
wing cycle. The optional forced slot follows the same local presentation path.
A diagnostic captured native ID 33, cursor 1 and a 32-tick timer before this
correction; membership alone had passed while S3K ascent wings were static. The ascent/fatigue integration checks verify the actual relocated
variant drawings in all three hosts. Native single-body-phase flight bindings retain their native body phase count
and timer. TALON's private presentation scripts play the selected six integrated
wing phases at the approved FLY cadence (two ticks each), preserving native loop
terminators. Original FLY/tired-FLY and all 82 approved PNGs remain unchanged.
The exact body and authored wing clocks are shown side by side in review.

Final contact registration uses the native PUSH palm plane, carrier boot baseline
and carried hand-grip top. No contours were painted for those hotspot corrections.
S2 float correspondence is chosen by native view, not local dedup ordinals:
TailsAni_Float (s2.asm:41588) uses phases `[0,1]`, and TailsAni_Float2
(s2.asm:41590) uses `[0,2,4,5,7]` in the eight-view union from AniTails0E
(General/Sprites/Tails/Anim - Tails.asm:62) and AniTails0F
(General/Sprites/Tails/Anim - Tails.asm:63). Local unique-frame ordinals are
inventory labels; they do not establish cross-host pose correspondence.
Jackson's new native-pose record receives native-image reflection metadata and
an extension to the existing packaging guard. No native-image binary is claimed.

Validation checkpoint: Python discovery **52 passed**; focused family, donation,
canonical-sheet, MICA/TALON integration, signature and four required S3K checks
**142 passed, zero failures/errors/skips**. Initial failures were an invalid fixed
ROLL2 timing flag and a test-only File/Rom type mismatch, both corrected.
The earlier baseline integration/audit selection passed **43 tests** on the base.
Coverage obligations: TALON S1 **2 → 2**, S2 **61 → 44**, S3K **96 → 71**.
Presentation/special-stage/end-scene gaps remain outside this animation pass.
Scoped citations have no stale entries (three near and fifteen unverifiable references across the two checked documents, including native source paths with spaces).

Lead owns final validation. Additional native flight/carry checks passed
**73 tests with no failures/errors/skips**. The change-based smoke command was:

```bash
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py --base c3789d8d05 --workers 2 --headless-gl surfaceless-egl --run
```

```text
VERDICT FAILED: head=c3789d8d0583 source=f6289e4adad7 change-based 4509/4509 candidate classes; ordinary 91/91 batches; guards 1/1 batches; tests=38022 failures=38 errors=1 skipped=50 (allowed=47 unexplained=3); missing ROM properties=none; skip inputs audio_reference_files=false,gl=true,s1_bizhawk_reference=false,s2_bk2=false,s3k_observations=false; bounded smoke selection, not a full-suite pass
```

This invocation inherited DISPLAY/WAYLAND_DISPLAY despite selecting EGL. Eight
explicit display-environment assertions and three reel skips required repair.
With both variables unset, all 21 affected classes (including the failed guard)
ran **127 tests** on `c3789d8d05` and this tree: **30 failures, three errors,
zero skips** on each. All test identities, exception types and assertions match;
only JVM object addresses and printed Set ordering differ. No skip-policy or
unrelated gameplay changes were made. The inherited failures include the known
AIZ draw-cache inventory mismatch. No new/worse ordinary or guard failure remains.
This is attributed smoke evidence, not a green full-suite claim.

Smoke execution was 1,855.95 seconds plus 40.74 seconds preparation; rounded
admission records total 4,782 seconds queue wait. User-input wait is zero; other
investigation and generation time is unmeasured. Exact rollup, per-case base/task
snapshots and normalization are retained in the review scratch directory.

After the frozen smoke snapshot, bounded corrections affect TALON art/contact
metadata, S2 float correspondence and native-image reflection metadata. Native
Sonic/donation Java paths remain the tested snapshot. Final focused validation
also checks six distinct ascent wing frames in every host, catching a later
native animation write that frame membership alone cannot detect. The final selection
passes **144 tests, zero failures/errors/skips**, including ordinary and forced
six-phase ascent in all three hosts. Both Python selections pass: **52 modding
tests plus seven native-pose tests**. All 82 original PNG hashes remain unchanged.
The review checks every encoded GIF tick against source RGB pixels and duration.
Native body bindings and native stock behavior are distinct from TALON's authored
wing cadence; full carrying-route contact is not certified.

Review and exact logs:
`$HOME/scratch/agent-tmp/talon-families-20261109/review.html`.

### Reel portraits and title ownership (2026-11-10)

The reel lane adds optional `CharacterPresentation.reelFaces` (native 32×32
normal/powered portraits with independent sixteen-colour palettes), retaining
all earlier constructor signatures. The release-policy guard requires the descriptor to remain unchanged for
ordinary unpublished candidate signature regeneration; `ModApiVersion` notes
and the normalized pin are refreshed on unpublished 0.7.0. Generated MICA/TALON head portraits replace the
selected main's Sonic symbol and first sidekick's Tails symbol. TALON solo owns
both and supplies Solar for the powered symbol; Gilded uses separately generated
native-Super-pose art. Knuckles, Eggman, Ring, BAR, jackpot, prize IDs, RNG and
reel clocks stay native. Transparent portrait pixels retain the native cell's
flat backing colour. Shader sampling no longer adds a second half pixel to
fragment-centred coordinates. Native white-entry fades modulate portrait RGB.

Native evidence: SlotMachine_GetPixelRow (s2.asm:59778-59790) addresses fixed
ArtUnc_CNZSlotPics (s2.asm:90461), with column-major 4×4 tiles. Contrary to the
brief's native-rule assumption, that routine has **no Player_mode/current-player
portrait substitution**: Sonic and Tails are fixed symbols. The new semantic
main/sidekick substitution is an official creator presentation rule, not a claim
about ROM behavior. S3K's sub_4C77C (sonic3k.asm:100060-100075) selects fixed
32×32 symbols from ArtUnc_SlotOptions (sonic3k.asm:201630): jackpot 0, Sonic 1,
Tails 2, Knuckles 3, Eggman 4, Ring 5, BAR 6 and Super Sonic 7. Production
atlas upload and both native slot shaders consume the authored portraits.

Size measurement excludes the native edge-connected white backing while
retaining the dark outline. Raw cell opaque area would incorrectly count all
1024 backing pixels as character. Normal heads reduce to 91% of their native
cell box, then quantize to the approved character palette; Gilded reduces to 96%.

| Portrait | Native head area | Authored head area | sqrt(area ratio) |
| --- | ---: | ---: | ---: |
| MICA / S2 Sonic reference | 696 | 678 | 0.987 |
| TALON / S2 Tails reference | 607 | 602 | 0.996 |
| MICA / S3K Sonic reference | 684 | 678 | 0.996 |
| TALON / S3K Tails reference | 592 | 602 | 1.008 |
| Gilded / S3K Super Sonic reference | 730 | 727 | 0.998 |
| Solar / TALON normal geometry alias | 607 | 602 | 0.996 |

S3K normal symbols alias the same original head within the same native cell.
Solar retains TALON head geometry rather than acquiring Super Sonic's spikes;
its ratio against native Super Sonic's different silhouette is 0.908, an explicit
species/pose difference, not a claim of equal Super Sonic geometry. Each normal
request and Gilded request generated two alternatives; the selected left image
and rejected right image, exact prompts, reference roles, hashes, reduction and
palette provenance live under each character's `reels/`. No native art or raw
generation is committed. Review/captures are in the lane scratch directory.

New audit obligations: six reel rows are authored (MICA/TALON S2 normal and S3K
normal/powered); six title mascot rows explicitly remain fallback. Earlier audit
omitted both families, so these are new tracked obligations, not removals from
its old fallback totals. Authored totals MICA S1/S2/S3K **65/91/109 → 65/92/111**,
TALON **0/28/38 → 0/29/40**. Title fallback adds one per character/host; other
fallback/alias/N/A rows are unchanged. Against the expanded inventory before
reel authoring, six reel fallback obligations move to authored.

**Title surfaces remain unimplemented in this lane; no title captures or title
animation certification are delivered.** Selection order is not a blanket
technical blocker: `LaunchProfileApplier.apply` sets `MAIN_CHARACTER_CODE` from
the launch profile, and the hub supports choosing the default before entering a
native title. A later Data Select choice cannot retroactively change an already
shown title; it should take effect on a subsequent title entry. A future title
seam should resolve the configured default/explicit title option, not stale
level-session selection, and must leave every logo tile untouched.

- S1: TSon_Main (0E, 0F Title Screen - Sonic, Press Start, TM.asm:18-30) owns
  the sprite mapping/palette and delay; TSon_Move
  (0E, 0F Title Screen - Sonic, Press Start, TM.asm:44-51) owns the rise.
  Ani_TSon (_anim/Title Screen Sonic.asm:5-11) supplies six entrance mappings plus a
  two-frame finger loop. A character can sensibly own those eight mascot
  frames with native hotspots and screen-plane masking; the emblem remains
  host-owned. This is feasible, but those eight frames were not authored here.
- S2: Obj0E_Sonic (s2.asm:26337-26358), Obj0E_Tails (s2.asm:26586-26600),
  Obj0E_SonicHand (s2.asm:26778-26792), and Obj0E_TailsHand
  (s2.asm:26822-26836) separate bodies/hands, with logo-top and curved
  occlusion passes in `TitleScreenManager`. A single pasted portrait would
  leave native hands or break entry composition. A proper lead/sidekick title
  bank needs all reachable body/hand frames, exact native timing and independent
  palette contexts. Those banks/seams were not implemented here.
- S3K: TitleSonic_LoadFrame (sonic3k.asm:5850-5878) page-flips Kosinski art;
  initial ArtKos_S3TitleSonic1 and MapEni_S3TitleSonic1 are uploaded to plane A
  in TitleScreen (sonic3k.asm:5480-5508). The later static plane composition
  also has separate Obj_TitleSonicFinger (sonic3k.asm:6239-6259) and
  Obj_TitleSonicWink (sonic3k.asm:6261-6278). Replacing only those small sprites
  would leave Sonic's native head/body. A mascot-only plane mask/frame bank,
  independent palettes and matching finger/wink sprites are required before
  this can honestly count as authored. No such plane-art title seam is delivered.

These are remaining implementation/art obligations, not evidence that titles
cannot be customized. No option was added that promises unavailable title art.


Reel-lane validation on `feature/ai-mt-reels-title`, based on `3aac15eb45`:
52 Python tests passed with
`python3 -m unittest discover -s tools/modding -p 'test_*.py'`.
All 127 focused Java cases passed, zero failures/errors/skips, with verified S1,
S2 and S3K ROMs, Java 25, DISPLAY/WAYLAND_DISPLAY unset and surfaceless EGL:

```sh
env -u DISPLAY -u WAYLAND_DISPLAY python3 tools/testing/maven_queue.py -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=TestReelCharacterPresentation,TestReelPortraitRendering,TestCNZSlotMachineViewport,TestS3kSlotMachineRenderer,TestKis2SlotArt,TestMicaCharacterIntegration,TestCharacterArtCoverageAudit,TestModApiSignatureSurface,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils' test
```

The first attempt exposed an extra half-pixel offset in both slot shaders and
a copied empty signature resource after a snapshot classpath error. Corrected
exact-pixel and pin rechecks passed; the final 127-case run passed on the final
implementation. Pixel tests compare native ROM symbols and every opaque MICA
pixel through the actual GPU shaders, and emit native/team/solo/half-scroll
component captures. They do not certify in-level routes, title composition or
all fade states. Four original PNGs pass 32×32/4-bit/binary-alpha/16-colour native
ladder checks. Staged disassembly citation and whitespace checks passed.

The final change-based smoke planner selects 4,511 classes because shared API,
shader and audit changes select all smoke categories. That broad integration run is left to the coordinating lead;
this lane reports focused evidence, with no category-run VERDICT or full-suite
claim. Title art and title captures remain outstanding. No push/merge is performed.

The additional 11-case `TestPaletteTextureCapacity` check passed without skips with the same queued
EGL command prefix, preserving legacy single-channel slot callers through an
explicit atlas flag. Final focused-run queue wait was 226s and Maven execution
124s; user wait zero. The palette check waited 108s and Maven ran 33.5s.

### S3K Competition miniatures (2026-11-10)

The official indexed-frame presentation contract adds `S3K_COMPETITION` and
`S3K_COMPETITION_TAIL`. Missing banks/IDs retain the native archetype mapping;
authored transparent frames explicitly suppress appendages. Per-frame hotspots
survive static DPLC compilation. Each custom bank uses a private palette row, so
partial fallback and arena art retain native colours. Presentation callbacks run
through the existing character-owner fault boundary. The unpublished descriptor
remains candidate 0.7.0; runtime description and mutable signature pins move with it.

The configured lead/sidekick team supplies the first registered creator per native
archetype to the three-slot Competition wheel. Selecting the Sonic/Tails slots
with MICA/TALON configured draws their miniature banks. Native teams retain stock
art. Wheel portraits, record identities and all miniature movement/animation,
collision, item and race logic remain native; this is an archetype-slot seam,
not arbitrary campaign sprite factories in Competition. `TalonCharacter.java` is
untouched; the example's separate `CompetitionPresentation` adapter composes with
the concurrently maintained TALON presentation supplier.

The audit now includes MICA's independent Sonic 2P bank. Before authorship the
new inventory has 44 MICA and 46 TALON fallback rows. After: MICA 42 authored,
1 native alias, 1 N/A; TALON 44 authored, 1 native alias, 1 N/A. $00 is blank;
$27 aliases $26 in both banks. TALON's 16 separate tail rows remain N/A.
Native-pose GPT Image transformations use the real Competition body palette.
The independent opaque-area ratios are MICA 1.009 and TALON 1.045; source hashes,
prompts, attempts, native dimensions and hotspots live in each competition art
directory. The full-frame 60Hz review and paired Azure Lake race rendering witness
are in `$HOME/scratch/agent-tmp/talon-competition-20261110/review.html`.
This does not certify S2 competition, all S3K arenas or native menu portrait art.

Verification on the task tree based at `3aac15eb45`: queued
`TestS3kCompetitionCharacterArt,TestS3kCompetitionArtProvider,TestS3kCompetitionSpritePages,TestModApiSignatureSurface`
passed 33 tests with zero skips. The queued integration/audit plus four required
S3K classes ran 104 tests: 100 passed, and four existing stage-capture assertions
incorrectly required every enum surface, including MICA's intentionally absent
Tails appendage bank. They now explicitly enumerate MICA's four supported
surfaces; all four passed on focused rerun, with zero skips. Python modding
discovery passed 52 tests. All required ROMs were found. The 120-frame, 60Hz Azure
Lake captures rendered both humans; GIF readback verifies every full frame and
exact RGB palette. This is bounded verification, not a full-suite claim.

Overall audit with the newly inventoried MICA miniature rows included:
MICA/S3K authored/alias/fallback/N/A **109/58/146/4 → 151/59/102/5**;
TALON/S3K **38/0/71/77 → 82/1/25/78**. Other hosts retain their inherited gaps.

A final focused `TestS3kCompetitionArtProvider` run passed 9 tests, zero skips,
including explicit per-ID fallback versus transparent appendage override.
Integration queue wait was at least 121 seconds; execution was 74 seconds.
The four-test correction queued at least 30 seconds and executed in 41 seconds.
No user wait occurred; image-generation and review time was not separately metered.

### Powered sheet design (2026-11-10; implemented)

### Powered sheet implementation (2026-11-10)

Gilded now supplies S2/S3K powered GGFP banks through `PoweredPlayableSheet`
on `SuperFormProfile.Form`; its old constructor still supplies no bank.
`PoweredAnimationProvider` keeps native script and movement selection in each
host. `PoweredPlayableArtBinding` relocates tiles and mapping/script namespaces
once at initialization, retains whole-animation fallback, and shares the combined
storage between normal, Super and Hyper views. Explicit normal phase aliases
handle mixed shared scripts without duplicating normal drawings. The existing
profile controller restores the right view during transformation, active form,
revert and renderer refresh. Existing Form animation overrides retain precedence.

The example has 34 generated base drawings plus deterministic movement slopes.
S3K shared pointers require special treatment for secondary balance, bubble
recovery and lying poses; those combine existing powered geometry with explicit
normal-phase aliases. Blink is N/A for single-player powered selection: its
interrupt is competition-owned and its C7 isolated reference additionally lacks
retained tile-bank context. C7 is not recorded as a verified normal-pose alias. Transformation reuses the existing authored family.
The coverage audit inspects the production combiner, script clocks and frame
bounds. Reserved Dash2/Dash3 entries are N/A with their selector limitation.
Review/captures are positioned presentation evidence, not route certification.
Observed powered audit coverage is S2 6 authored / 26 alias / 2 N/A and S3K
10 authored / 24 alias / 2 N/A, with zero fallback across all 70 rows. The N/A
entries retain Dash2/Dash3 table obligations and the S3K competition-only Blink
limitation. Normal and Super/Hyper art share one validated namespace; the
existing transformation family supplies the transformation drawings.


**Native evidence.** `SuperSonicAniData (s2.asm:38773-38819)` has 32 entries;
six replace the normal script pointer (walk, run, push, stand, balance, duck).
Transformation is a seventh dedicated powered script, already referenced by
the normal table. The engine's merged set retains normal `$20/$21` after the
32-entry powered table; the audit correctly counts 34 obligations rather than
32. `SupSonAni_Stand (s2.asm:38813)` uses `$72,$73,$74,$73` with delay 7.
`SupSonAni_Transform (s2.asm:38819)` repeats `$6D` and `$6E`, then `$6F`, then
alternates `$70/$71`, and switches to WALK; the duplicate timer entries matter.
`SAnim_Super (s2.asm:38517-38527)` selects the powered walk/run scripts at
speed `$800`; `SAnim_SuperWalk (s2.asm:38529-38551)` adds the angle displacement
and adds `$20` to walk frames on every fourth level frame. The inventory includes
those implicit frames rather than counting script literals alone.

S3K `AniSuperSonic` in `General/Sprites/Sonic/Anim - Sonic.asm:81-119`
also supplies 32 entries over the 36-entry normal set. Its six table overrides
use different geometry/order from S2: WALK `$01..$08`, RUN `$21/$22`, PUSH
`$B6..$B9`, WAIT `$BA/$BB`, BALANCE `$A1..$A3`, DUCK `$9B`. The shared
`AniSonic1F` transformation is `$D2..$D6` with repeated timing entries.
`Map_SuperSonic` in `General/Sprites/Sonic/Map - Sonic.asm:253-505` and
`PLC_SuperSonic` in `General/Sprites/Sonic/DPLC - Sonic.asm:253-505`
are distinct pointer tables even where scripts share normal pointers.
`loc_12766 (sonic3k.asm:24925-24937)` and
`loc_12780 (sonic3k.asm:24939-24963)` own powered slope displacement and
speed-derived timer reloads. Script-pointer sharing alone is not an art alias.

The first inventory expands to 140 S2 and 106 S3K reachable frame indices,
including 86 and 50 in the six overridden scripts respectively. These are
bounded script/movement inventories, not every object-owned direct mapping.
The native render pass produces 140 distinct complete S2 RGBA/hotspot references
and 102 distinct complete S3K references after deduplication; one S3K reference
requires previous-bank state. These counts must not be presented as the number
of finished creator drawings. Compare per-frame mapping/DPLC identity and
existing creator art before deciding which frames need new generations.

**Implemented seam.** Optional powered playable art is attached to each
`SuperFormProfile.Form`, loaded through bounded mod assets by the existing
owner-bound profile supplier. Use a second ordinary GGFP sheet, with canonical
animation/family phase keys, rather than adding an unrelated format or putting
Super-specific conditions in MICA's sprite update. Keep the existing constructor
as an overload that supplies no powered bank. A Form-level bank lets Hyper
share Super geometry and allows another creator to use different tiers without
adding global or character-name gates. The descriptor, ModApiVersion documentation and normalized 0.7 signature pin
change together; this remains the unpublished 0.7 candidate.

Reuse the GGFP tile/mapping/DPLC validation and native-phase family binding.
The host must supply its powered animation scripts/profile through a semantic
provider, beside its existing normal art provider. Shared binding must not switch
on a host name to choose Sonic constants. Bind complete powered animations to
that host's exact script entries, delays, repeats and terminators, including
speed/slope selection and S2's alternate walk bank. Native frame numbers stay
inside the host provider; creators supply canonical family phases. Extend the
family vocabulary where a current native animation is unmapped; do not silently
map an unrecognised powered script to WAIT and call it authored.

Merge normal and powered tile sources/mapping namespaces once during art
initialization, relocating both DPLC source tiles and script mapping indices.
Use the larger required pattern-bank capacity. This avoids runtime atlas swaps
or a frame where the active renderer and scripts disagree. Keep normal and
powered animation/profile views over that same immutable combined art. Missing
powered animations select the entire already-bound normal animation and its
normal profile settings; do not fill missing phases with unrelated poses. A
normal-frame reuse is an explicit alias to existing mapping/DPLC data and adds
no duplicate PNG. Existing `Form.animations` overrides retain their documented
addressing and precedence; validate bounds against the combined bank.

The existing profile controller owns transition and rewind: install the powered
view before the transformation script, retain it for Super/Hyper, restore the
normal view on revert/reset, and reconstruct the view from restored form tier
and phase after renderer refresh. Test restore **during transformation** as well
as active Super/Hyper: current code's `isSuper()` check alone does not establish
that the transforming phase is reconciled. No-bank forms retain exactly their
current scripts, rendering, palette and transform behavior. S1 has no native
powered animation bank: it uses authored canonical timings, the mod's existing
six-emerald/ring rule and no donor-ROM dependency.

Update the release-policy descriptor, `ModApiVersion` documentation and normalized
0.7 candidate signature pin together with the actual implementation. Preserve
all old constructors. The candidate API version remains governed by the policy;
this design alone does not authorize publication or a version bump.

**Art and acceptance.** Use native 8× references, the approved model sheet and
`wait-00` head/torso scale for each new powered pose. Record attempts, selection,
raw hashes, reduction and final indexed rows. Gold scales and pale-gold face/
belly retain stable semantic palette slots so the existing Gilded cycle can
animate on top; it must update the pale-gold slots in the powered view as well
as scale slots. Do not recolour the normal cream slots globally. Review every
native contact phase and implicit movement variant at 60 Hz and compute
`sqrt(mean authored opaque area / mean native opaque area)` per pose family.
Values below about 0.95 require correction, alongside landmark inspection.

Acceptance needs all 70 audit rows resolved by observed production binding,
including explicit aliases and justified empty/native-unreachable N/A cases.
Required focused tests: optional bank/no-bank compatibility, each powered host
script's order/delay/end action and frame bounds, mixed per-animation fallback,
S1 authored timing without a second ROM, transform/Super/Hyper/revert rewind and
renderer refresh, MICA normal and powered palette/physics/ability integration,
API signature surface, and the four standard S3K bootstrap/loading tests.
Capture real headless S2/S3K stand/run/jump and S3K Hyper beside native Super
Sonic; annotate positioned versus route-driven captures. Neither a static pilot
nor an inventory JSON is in-game acceptance.

## Ending ownership (2026-11-10)

`TalonEndings` supplies eight S2 ending object poses, three monochrome search
photos, five 24×16 goggles-down cockpit crops, and S3K reunion selectors 3/4.
Built-in GPT Image transformed real-palette native references against the approved
model and WAIT. Native decoded pixels and raw candidates remain in external
scratch; provenance and indexed authored images live in `art/talon/endings/`.
The first configured sidekick owns photos and the new optional
`S2_TORNADO_PILOT` set. The ending and SCZ/WFZ renderers replace only the cockpit
piece, keeping native sequence clocks, vehicle pieces and DPLC decisions.
Missing sidekick/slot retains native art. The unpublished API remains 0.7.0.

Ending object/reunion sets apply to TALON's solo Tails route; MICA's native S3K
reunion does not include a sidekick portrait. S1 credits demos use the configured
team's ordinary playable sheet. S2 credits are text slides, and S3K scrolls ROM
text (`S3kCreditsState`); neither has a credits gameplay bank to redraw.
Presentation-lane merge must add `TalonEndings.load(assets)` to its endings map;
this lane leaves the character definition and TalonPresentation untouched.

The S3K reunion plane also has a separate sidekick pilot (native selector 12).
`S3K_TORNADO_PILOT` aliases the generated S2 side-view cockpit with hotspot
(12,8). Native alpha geometry is identical; native palettes differ. This keeps
TALON visible in MICA's team reunion without adding a reunion portrait actor.

Ending-lane verification: 181 distinct bounded Java cases covered (180 passing
in the combined run; corrected positioned S3K capture passing on rerun), zero
skips; Python modding discovery 52 passing. Final TALON audit authored / alias /
fallback / N/A: S1 0/1/2/2, S2 45/1/27/52, S3K 42/1/69/78. The extra S3K pilot
obligation was discovered in the actual team capture. See the ending art README
for independent size measurements and scope. Main presentation wiring remains
with the parallel lane; no definition or TalonPresentation file was edited.
