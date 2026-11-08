# Original playable character mod (2026-11-08)

An example and tutorial mod that adds two brand-new, original playable characters to
Sonic 1, Sonic 2 and Sonic 3 & Knuckles: MICA, a pangolin who stands in for Sonic, and
TALON, a falcon courier who stands in for Tails. Art is generated with GPT Image through Codex
from a supplied base design, then reduced to native Mega Drive constraints. The mod is
a framework demonstration: where the Mod API lacks a seam, this work adds the safe
facade rather than reaching into engine internals.

Status: phase 1 code-complete; documentation consolidated. Evidence gaps and
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
