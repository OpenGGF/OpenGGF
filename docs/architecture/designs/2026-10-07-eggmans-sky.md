# Eggman's Sky (2026-10-07)

An S3K mod scene that plays like No Man's Sky with Dr. Eggman: procedurally remixed planets
from every supplied ROM's zones, a software-rendered space cockpit, survival, scanning, crafting,
Sonic as wanted-level sentinels, stations, a galaxy map and a core ending. Player-facing details
are in [the example's README](../../../examples/eggmans-sky/README.md).

## Engine additions (commit 26708902c5)

- `SceneRomArt.levelKit(zone, act)` / `SceneLevelKit`: an act's foreground layout grid, per-block
  pictures with transparent sky, per-block primary-path collision masks, playable area,
  background and palette. Implemented by `DetachedLevelKit` over the existing detached level
  builders: S1/S2 through per-act `DetachedLevelPictures` kit loaders from the zone registry,
  S3K through `Sonic3kZoneArt` with a generic profile (AniPLC first frames) for unpictured acts.
- `SceneImage.streaming(w, h)` + `update(int[])`: the texture cache re-uploads into the same GL
  texture when the image's revision changes, so a scene can software-render every frame without
  allocating textures.

## Terrain remixing: what was tried

1. Seams only where every row's block pair occurs side by side in the stock layout. Kill
   evidence: on Emerald Hill 1 only 14 of 107 columns had any such jump (histogram of best-jump
   row agreement 4/8: 3, 5/8: 6, 6/8: 32, 7/8: 31, 8/8: 14), so planets were long verbatim copies.
2. Adding facing-edge matching (pixel shape, colour distance and collision edge agreement)
   barely changed the sequence: the disagreeing rows were usually solid interiors.
3. **Landed:** allow up to `rows/4` disagreeing rows at a seam, then repair each disagreeing cell
   by searching the act's blocks for one that fits its left, right (horizontal pairs or edge
   match) and upper/lower (vertical pairs) neighbours. Runs of 2–6 columns, 85% jump chance,
   loop closed where the last column joins the first. Seams are effectively invisible in
   captures of all 55 biomes.

## Biome survey

Every act with a kit was landed on and captured. Excluded or adjusted, from captures:
Hydrocity 2 foreground shows undecoded digit tiles (excluded); Sandopolis 1–2, Mushroom Hill
1–2, IceCap 2, Chemical Plant 1–2, Oil Ocean 1–2 and Metropolis 1–3 backgrounds contain tiles
their events load later, so they use a procedural climate sky. S2 Hill Top and Wing Fortress
have no kit (resource-plan art); S1 Final Zone, S2 Sky Chase/Death Egg and S3K Doomsday are too
small or have no ground.

## Balance notes from scripted playtests

- First version: the crash site could be a toxic world with aggressive heroes; holding the laser
  while flying hit passing animals, the wanted level reached 5 and Eggman died in ~25 s. Now the
  home planet is a non-hazardous lush/temperate world with no sentinels or storms, and the laser
  passes harmless wildlife unless aimed at it with the mouse.
- Pirates first orbited behind the player (only edge markers visible); they now jockey for
  position ahead with occasional attack runs, with aim assist, a lock-on reticle and a radar.
- Measured cost through the scene host: surface ≈0.05 ms, space ≈0.5 ms per update+draw.

## First-build bug reports (2026-10-08)

Investigated from `2df2ad46d6` in the current `feature/ai-eggmans-sky` checkout:

- Silence was not intentional. `ModSceneLauncher` bypassed title/level initialization,
  leaving native IDs without their active ROM audio source. Install that source before
  scene entry. Testing after `SharedLevel.load` would hide the bug; the new launcher
  regression checks final music PCM, stops music, then checks an isolated ring effect.
- `Map_RobotnikShip` faces left by default. The head, pod and exhaust now flip when
  travelling right; the muzzle and exhaust positions already followed travel direction.
  Direct draws in the title, intro, ending and station also face right.
- Di-hydrogen existed in the random table (6% crystal selection, 60% of those blue,
  reduced further when choosing cave floors), but no nearby supply was guaranteed.
  Add three blue crystals on available exposed ground per 512-pixel sector, yielding
  at least 42 units where all three fit. Separate `#fuel` IDs and no additional random
  draws preserve the original placement IDs/sequence. Existing saves gain these deposits.
  Tests inspect populated, visible, mineable crystals near several fresh landing sites.

Validation uses focused scene, startup and audio regressions: the change-based plan
selects all ordinary classes due to unclassified example/launcher paths (and an unrelated
untracked trace movie), but these changes do not alter the audio driver, frame clock,
shared physics or public API. No engine-wide suite claim is made.

Completed checks on the working changes above `2df2ad46d6` (Java 21, real S3K ROM
passed by absolute `-Ds3k.rom.path`; all three supplied ROMs available to the scene):

- `maven_queue.py --lean -Dmse=off -Dtest=TestEggmansSkyScene,TestGameLoopAudioPresentationModes,TestModSceneHost,TestEngineConfiguredHeadlessStartup,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils test`:
  114 tests passed, zero failures/errors/skips. Includes all supplied biomes and the
  four fresh expedition seeds; the two identically named level-loading classes both ran.
- `maven_queue.py --lean -Dmse=off -Dtest=TestModSceneLauncherAudio test`: one test
  passed with zero skips, checking within-channel PCM variation for music and SFX.
- `maven_queue.py --lean -Dmse=off -Dtest=TestEggmansSkyScene test`: all five
  passed again after the direct-screen facing fixes, zero skips. The direction test
  now also checks the title, intro, ending and station pod draws.
- `python3 examples/build_example.py eggmans-sky`: engine compilation and mod packaging/
  validation succeeded; final mod-only rebuild with `--skip-engine` also passed; output `target/examples/eggmans-sky/eggmans-sky.jar`.

The audio fix is in the engine launcher, so the rebuilt mod needs the updated engine;
`python3 examples/build_example.py eggmans-sky --run` uses both from this checkout.
Speaker-device listening and a full ordinary/guard run were not performed.


## Space rendering performance (2026-10-08)

Investigated from `69cbe6b836` in the current checkout after a report of 20–30 fps.
The earlier ≈0.5 ms scene-host figure excluded native draw submission. The
software planet renderer was not the measured bottleneck, so reducing its
resolution or approximation accuracy was rejected.

`SceneRenderer.flush` repeatedly uploaded each texture/clip batch at offset zero
with `glBufferSubData`, forcing the driver to wait for the preceding draw to stop
reading that storage. Replace the complete store with `glBufferData` for each
batch; the driver can retain the previous storage until its draw completes.
Vertex contents, draw ordering, batch limits, shaders, texture updates and the
public Mod API are unchanged. No Eggman's Sky assets or gameplay need alteration.

Matched native macOS/Java 21 probe: `HeadlessGameBoot(800,448,400,224)`, real S3K
ROM, `ExampleModHarness` built from source, `new:42`, 330 update ticks, `space:0`,
then 600 frames. Every frame ticks the scene, clears, calls `ModSceneHost.draw`
and `glFinish`; exclude the first 120 frames from timings. Audio, swap/vsync and
capture encoding are outside the measured interval. Baseline engine classes
versus only the changed renderer compiled into a temporary override directory:

| Mean ms/frame (480 frames) | Baseline | Replacement storage |
|---|---:|---:|
| Update | 0.483 | 0.486 |
| Native draw submission | 5.318 | 0.167 |
| GPU completion wait | 0.479 | 0.390 |
| Total | 6.280 | 1.043 |

Both runs caught no mod faults; final 800×448 PNGs are byte-for-byte identical.
This identifies a substantial driver stall, not a claim that the user's live
20–30 fps session or every GPU has been reproduced. It requires an updated
engine; rebuilding only the mod jar does not replace `SceneRenderer`.

Validation scope: the change-based plan selected 3,010 ordinary classes plus
guards, including an unrelated untracked BK2. Proportionate validation uses
scene-host, canvas, texture-cache and expedition tests, plus a new native
pixel test of small→full→small batches, the 2,048-quad boundary, texture/clip
switches and repeated streaming updates. This is a local buffer upload fix;
no batching algorithm, public contract, gameplay timing or physics changed.

Completed on the working changes above `69cbe6b836`:

- `python3 tools/testing/run_categories.py --base 69cbe6b836 --preflight`: Java 21,
  Lua 5.4 and PowerShell prerequisites passed.
- `python3 tools/testing/maven_queue.py --lean -Dmse=off
  -Dopenggf.test.gl.native=true
  -Dtest=TestSceneRenderer,TestSceneTextureCache,TestRecordingCanvas,TestModSceneHost,TestEggmansSkyScene
  "-Ds3k.rom.path=${PWD}/Sonic 3 & Knuckles (W) [!].gen"
  test`: 28 passed, zero failures/errors/skips, including the native GL regression.
- Matched native probe above and exact final PNG comparison passed.
- `python3 tools/testing/maven_queue.py -Dmse=off -DskipTests package`: succeeded;
  rebuilt the engine and executable dependency jar without repeating tests.

No full ordinary/guard suite or live-session FPS claim is made.


## Menu and warp confirmation (2026-10-08)

Follow-up on the same checkout above `69cbe6b836`: `Controls` intentionally maps
Enter/keypad Enter and pad Start to both opening a menu and confirming a selection.
`MenuMode` and `GalaxyMode` checked menu-close first, so these inputs dismissed the
screen before its confirm branch. Close on a menu action only when it is not also
a confirm; explicit back retains priority. Tab/I still close, and Backspace/X/pad B
still go back. Flight's menu-opening controls are unchanged.

The existing expedition test accepted either SpaceMode or GalaxyMode after pressing
Enter on the map, masking a return without a warp. The new independent regression
opens System → Galaxy Map through Enter, checks current-system and missing-fuel
refusals remain on the map, then confirms with keypad Enter and requires WarpMode,
one cell consumed, the selected destination reached and exactly one recorded warp.
It also verifies Tab/I/Backspace/X dismissal in both menus. Drive upgrades and fuel
are explicit test setup; UI navigation and the warp use the production input path.

The combined selection remains the full ordinary suite because of the example and
unrelated untracked movie paths. This bounded mod-only input-priority correction
uses focused expedition coverage; the preceding 28-test renderer validation remains
applicable because the engine renderer has not changed again.

Completed checks for the control follow-up:

- `python3 tools/testing/maven_queue.py --lean -Dmse=off -Dtest=TestEggmansSkyScene
  "-Ds3k.rom.path=${PWD}/Sonic 3 & Knuckles (W) [!].gen"
  test`: six tests passed, zero failures/errors/skips, including the new warp regression.
- `python3 examples/build_example.py eggmans-sky --skip-engine`: compiled, validated
  and packaged `target/examples/eggmans-sky/eggmans-sky.jar` against the engine
  already built and tested above. This follow-up needs the rebuilt mod jar.

## Background continuity and flora variety (2026-10-08)

Investigated above `b14cdb9454c3274a96e343f87ac690f5ce403cb5` in the current
`feature/ai-eggmans-sky` checkout. The mod split every single-band kit backdrop,
including its generated skies, into eight-pixel strips with different horizontal
speeds. That invented depth boundaries through connected scenery. Retain the
source bands verbatim; an intact single plane still has parallax relative to the
foreground. Deriving band cuts from arbitrary row heights was rejected because
neither the ROM art nor the generated skyline has those boundaries.

The camera also wraps X at the planet width, which jumps any background whose
fractional scroll period does not divide that width. Accumulate unwrapped camera
travel for the sky, including the small draw-time shake offset. Align background Y
to pixels and clip the last band to the content height before the lower fill.
No shared engine renderer, ROM scroll handler or public API changed.

Six additional seeded flora silhouettes: fern fronds, succulent rosettes, reed
clumps, pleated fans, nodding bell flowers and tiered shelf fungi. Biomes select
from appropriate shape pools, rotated deterministically by planet seed, with six
specimens per species instead of three. Shape selection does not consume the
species RNG; existing species IDs, seed sequence, resource types, yields and
placement sequence are preserved. Only visual geometry and its existing sprite
hit bounds change. No materials, recipes or inventory entries were added.

Validation on the working changes above that commit:

- The unchanged category planner selected 3,010 ordinary classes plus guards due
  to unclassified example paths and the pre-existing untracked S2 movie. Focused
  validation is proportionate for mod-local drawing and procedural sprites; no
  shared physics, timing, asset loading or API behavior changed.
- `python3 tools/testing/maven_queue.py --lean -Dmse=off
  -Dtest=TestEggmansSkyScene
  "-Ds3k.rom.path=${PWD}/Sonic 3 & Knuckles (W) [!].gen" test`:
  eight passed, zero failures/errors/skips, Java 21. The first attempt failed to
  compile an ambiguous JUnit assertion; the corrected invocation completed.
  Coverage includes all 55 supplied biomes retaining source bands, six specimens
  per species, both seam directions through camera update and sky draw, and 72
  repeatable distinct new silhouettes across six shapes and twelve seeds.
- `python3 examples/build_example.py eggmans-sky --skip-engine`: compiled,
  validated and packaged `target/examples/eggmans-sky/eggmans-sky.jar`.
- Native `ExampleModCapture` with `java -XstartOnFirstThread`, real S3K ROM,
  400×224 at 2× scale, 1,200 ticks and PNGs every 200 ticks. Started with
  `biome:s3k:2:0:7`; scripted rightward flight, then jumped to
  `biome:s3k:0:0:42` at tick 400 and `biome:s3k:7:0:7` at tick 800, flying right
  for 180 ticks after each 200-tick settling interval. Inspected native frames
  for intact ruins, source jungle bands and generated hills, plus a separate
  six-by-six flora contact sheet. Visual artifacts: `/tmp/eggmans-sky-visuals/`.
  The initial native launch omitted the required first-thread option and failed
  before capturing; the corrected launch completed.

This is focused mod validation, not a full ordinary/guard suite pass. The rebuilt
mod jar works with the existing engine from this checkout.

## Expedition quality of life and discovery journal (2026-10-08)

Implemented in the current `feature/ai-eggmans-sky` checkout above `86ec1047a88c`.
All changes are in the example mod and its focused tests; no engine or public API change.

- Recipe/next-tier technology pins persist in the existing expedition save. The HUD expands
  missing crafting/refining intermediates and scan markers highlight needed materials.
  Basic refining routes are suggestions, not forced conversions; alternative star metals
  remain available in the refiner. Existing tutorial objectives remain visible.
- An explicit batch screen previews 1, 5 or maximum batches. Production simulates ingredient
  removal before testing output capacity and applies a successful result atomically. The
  former remove/add/refund approach could round away ingredients for non-unit conversions;
  regression cases cover full cargo, freed slots, 2:3 refining, failed batches and reserves.
- Cargo reserves protect a chosen stock quantity from crafting, refining, discard and sales.
  Recharging and technology installation can still consume those emergency materials.
- Expedition and station menus remember tabs/rows within the session, tracking material IDs
  in changing refining/trading lists. Station Backspace handling returns after cancel because
  the stock mapping also exposes Start; a new input regression caught the previous same-edge
  leave confirmation. The failing test expected SpaceMode but reached MenuMode on its next
  Enter; prioritising cancel fixes the actual input conflict rather than altering the test.
- Resource pickup notices accumulate by item identity on surfaces and in space. Red warnings
  use a separate panel and defer banners. Native screenshots exposed banners obscuring the
  batch preview and tutorial hints crossing the pinned checklist: banners now wait during
  paused menus and the redundant early control hint hides while a pin is displayed.
- The journal browses visited planets and all three species categories, with completion
  counts, discovered portraits and descriptions/yields. Undiscovered entries stay anonymous.
  It reconstructs a selected planet from its existing seed on demand, reuses the currently
  loaded planet when possible, and retains only the selected survey/portrait. Browsing does
  not teleport, award discoveries or mutate expedition state. This avoids a second persisted
  species catalogue and makes existing save discovery IDs usable immediately. Planet opening
  may incur the existing planet-generation cost; scrolling species does not rebuild terrain.

Validation (Java 21, real S3K ROM at the existing absolute root path; both optional ROMs
available), all against the working changes above `86ec1047a88c`:

- Inspected `run_categories.py --base 86ec1047a88cf315bd4fd7ac49485af45e63bceb`:
  fallback selects 3,010 ordinary classes plus guards for unclassified example paths and an
  unrelated untracked movie. Proportionate validation applies: mod-local menu/inventory/save
  behavior is exercised directly; there is no shared engine, timing or physics change.
- `maven_queue.py --lean -Dmse=off
  -Dtest=TestEggmansSkyQualityOfLife,TestEggmansSkyScene,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils
  -Ds3k.rom.path=<absolute S3K ROM> test`: 75 passed, zero failures/errors/skips. This includes
  60 S3K regression checks, six new non-ROM production/save/notification checks and nine scene
  checks at that stage. The scene test traverses all 55 supplied biomes.
- After extending station memory and correcting visual overlaps, the two mod classes ran:
  six non-ROM tests passed; nine of ten scene tests passed and the station binding regression
  above failed. After the cancel fix, `-Dtest=TestEggmansSkyScene` completed with all ten
  passing, zero failures/errors/skips. The unaffected S3K and pure-production checks were
  not repeated. These are focused checks, not a full ordinary/guard-suite pass.
- `python3 examples/build_example.py eggmans-sky --skip-engine`: Java compilation, mod
  validation and packaging passed against the existing engine build.
- Native `ExampleModCapture` with `java -XstartOnFirstThread`, 400x224 at 2x scale, 850 ticks
  and PNGs every 50 ticks: inspected batch previews, pinned HUD, planet list, unknown species,
  and known fauna/flora/mineral portraits. A separate temporary save supplied known discovery
  IDs for the portrait survey; the user's expedition was not changed. Temporary captures:
  `/tmp/eggsky-qol-visuals/`. A repeat initially rejected the existing capture-build jar;
  removing that generated jar allowed the corrected capture to finish.

## Creature sprite layouts (2026-10-08)

Working changes above `587c705fa4a0efcb9f67a3b46a6a94cd8aa45ddf` in the current
`feature/ai-eggmans-sky` checkout correct eleven animal mapping assignments.
The reported fragmented penguin appearance is reproduced by decoding Penguin.nem
through Map_Animals4 (seal): its tile dimensions and second-frame tile start are
wrong. It needs Map_Animals5. This is a catalogue error, not corrupted ROM art or
a recolouring/rasterizer defect; changing shared rendering was unnecessary.

ROM species tables provide the oracle: S1 `Anml_Variables` at `0x95E4`, S2
`Obj28_Properties` at `0x118F0`, and S3K `word_2C7EA`. Each entry contains two
velocity words followed by the species' mapping pointer. S1's chicken, seal, pig,
flicky and squirrel previously all used the rabbit layout. S2's rabbit and turtle
used Flicky's. S3K's seal and penguin used the wrong dimensions/strides; pig and
squirrel mapping pointers were swapped too (those two mapping layouts currently
have identical pieces). Explicit per-species mappings preserve creature IDs and saves.

Validation against these working changes, Java 21:

- Verified all three root ROM SHA-1s against the repository's expected revisions.
- `TestEggmansSkyFauna` checks all 17 animal mapping pointers against their owning
  ROM tables and decodes every selected frame of all 83 catalogue bodies, checking
  valid frame indices and nonempty output. Temporary before/after contact sheets
  were inspected across the full catalogue; the affected animal frames are intact.
  These are CPU sprite rasterizations, not native GPU gameplay captures.
- Inspected `run_categories.py --base 587c705fa4a0efcb9f67a3b46a6a94cd8aa45ddf`:
  3,012 ordinary classes plus guards, due to unclassified example paths and an
  unrelated untracked movie. Proportionate validation applies to this mod-local
  mapping-data fix; no shared engine, physics, timing or API behavior changed.
- `maven_queue.py --lean -Dmse=off
  -Dtest=TestEggmansSkyFauna,TestEggmansSkyScene,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils
  -Dsonic1.rom.path=<absolute root S1 ROM>
  -Dsonic2.rom.path=<absolute root S2 ROM>
  -Ds3k.rom.path=<absolute root S3K ROM> test`: 73 tests passed, zero failures,
  errors or skips, including the scene's all-biome traversal. The first sandboxed
  attempt could not create the queue's `.git` lock; the permitted rerun completed.
  Focused validation only, not a full ordinary/guard suite pass.
- `python3 examples/build_example.py eggmans-sky --skip-engine`: compiled,
  validated and rebuilt `target/examples/eggmans-sky/eggmans-sky.jar`.

## Animal travel direction (2026-10-08)

Working changes above `9a546f64d7f9c52042865b467cd43c6f6ae7ad34` correct
`Creature.draw`'s assumption that every body faces left before mirroring. All
17 freed-animal bodies face right in their ROM mappings, as also visible in the
previous task's contact sheets. S1 `Anml_FromEnemy`, S2 `Obj28_InitRandom`, and
S3K `loc_2C940` set render X-flip for the negative horizontal velocities from
the animal tables. Their later reversal routines negate velocity and toggle
that same bit. Animals therefore need the opposite flip rule to the catalogue's
badniks. Changing movement or reversing every creature would be incorrect.

The fix chooses the flip convention using the existing animal classification.
It leaves movement, steering, frame selection, IDs and saves intact. The fauna
regression now exercises actual `Creature.draw` calls for all 83 bodies, checking
both left/right directions while moving and at rest (332 draw checks). It uses
real ROM sprites with a mocked surface art lookup and recording canvas; this
checks draw submission, not GPU output.

Validation selection: `run_categories.py --base
9a546f64d7f9c52042865b467cd43c6f6ae7ad34` selected 3,012 ordinary classes plus
guards because of unclassified example paths and the unrelated untracked movie.
Proportionate focused validation is appropriate for this local presentation
branch: no shared renderer, physics, timing, public API or save format changed.

- Java 21 `maven_queue.py --lean -Dmse=off
  -Dtest=TestEggmansSkyFauna,TestEggmansSkyScene,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils`
  with `test` and all three absolute root ROM properties: 73 passed, zero
  failures/errors/skips against these working changes. This includes the 332
  facing assertions and all-biome scene traversal; it is focused validation,
  not a full ordinary/guard suite pass.
- `python3 examples/build_example.py eggmans-sky --skip-engine`: compilation,
  mod validation and packaging passed. Installed the rebuilt jar into `mods/`
  and refreshed its existing trusted SHA-256 in `mods/modstate.json`.

## Original system voice (2026-10-08)

The voice task started from `c4124257094e` in the existing isolated Eggman's Sky
checkout, on local `feature/ai-eggmans-sky-voice`. The user approved Alice's
matter-of-fact performance and the metallic/DAC audition, then requested the
complete bank in the mod. `4229554eafcf` brings published develop `913c5a3516b1`
into the private branch: signature conflicts retain both level-kit and current
scene-music additions, and both measurement notes are preserved. Main integration
is held for the separately coordinated parity qualification; this section does
not claim publication.

`c83f87386403` privately imports the frozen parity main source at
`863683b092f7`. The only merge conflict was the measurement-hazard catalogue:
both the Eggman's Sky draw-cost note and all upstream parity/environment/input
hazards are retained. A worktree metadata lease prevented queued compilation
from observing the temporary merge/stash state. No main inputs or publication
were changed.

The bank contains 122 literal announcements (274 seconds), covering vitals,
climates/weather, travel, heroes/security, discoveries, inventory/production,
station services/missions, saves/recovery and the core/new-galaxy progression.
Procedural names and quantities remain visual. Capsule salvage and wreck salvage,
destruction and rebuild, and partial recharge and complete restoration have
separate lines so speech describes the actual state. Save success is announced
only after storage succeeds. The shipped WAVs are original ElevenLabs Alice
performances directed through OpenRouter, not samples/clones of No Man's Sky.
ROM music, effects and visual assets continue through their existing pipelines.
No player credential or network is used at runtime.

`voice-bank.json` records the text and performance direction. The promoted
`tools/audio/eggmans_sky_voice.py` preserves paid-request state, raw MP3 sources,
clean masters, edits and blind transcription checks in an external task cache.
The catalogue's synthesis estimate is about $0.77 before transcription and the
one regenerated stuttering take; provider usage reporting lagged, so this is not
an exact billed total. At 21:25:26Z the key reports $0.921038196 total usage,
including the earlier auditions; provider billing can still lag. The generator
checks a $5 reported-key-usage safety ceiling
and refuses automatic retries of uncertain requests. Source and output hashes,
whole-utterance edits, final word checks and the recipe are in the shipped
`audio/voice/provenance.json`. Publishing derives manifest ids and speech queue
leases from actual 48 kHz mono 16-bit PCM frames.

The accepted processing has 73 Hz metallic modulation and short 7.6/11.3/17.9 ms
reflections, a 6% 24-band vocoder blend with a 185 Hz harmonic carrier, loudness
normalization, a strong 7-bit/8 kHz zero-order-hold DAC approximation, and 10%
upper-band consonant recovery. There is no room reverb. The earlier irritated
performance was rejected despite accepted effects; six-bit DAC processing lost
consonants. The final performance is neutral, evenly paced and literal. Eleven v4
sometimes rehearsed/repeated words, even in one-line requests: whole utterances
were retained at quiet gaps and rechecked after processing. Direct cuts at ASR word
timestamps proved unreliable (loose/overlapping times cut consonants or retained
a previous ending); the final bank passes all 122 literal word checks. This is
an artistic retro effect, not YM2612 hardware emulation or an official account
of No Man's Sky's production method.

`SceneAudio.playSfx(String)` provides the missing narrow bridge to existing
namespaced audio. The host validates the local name and supplies the trusted
scene owner, then records the existing `PlayNamespacedSfx` command. Unknown clips,
suppressed playback, closed contexts and absent headless audio return false.
Patch SFX declarations now use the same bounded owner-atomic validation/decode
pipeline as standalone SFX. The first focused run exposed remaining
standalone-only filters in both scanned and packed validation: accepted patch
manifests still yielded an empty registry. Both registry paths now retain valid
patch SFX; the eligibility check exercises both paths. The launch factory also
admits SFX-only stock-game patches: its previous no-track early return would
silently omit the entire bank. The packaged-bank test traverses this production
factory to reproduce that failure; no numeric mod IDs, cross-owner requests or
base-game SFX override map are introduced. The unpublished 0.7 version prose
and mutable candidate signature pin are updated together; the release descriptor
retains candidate 0.7.0. The commit hook rejected an unnecessary descriptor
comment during ordinary pin regeneration, so that comment was removed to retain
the existing publication/version contract.

The mod's eight-entry queue uses priorities, per-line cooldowns, severity
replacement, resolved-vital cancellation, 15-second pending expiry and measured
PCM-duration reservations at the scene's 60 Hz clock. Pulse/hull/shield threshold
warnings have hysteresis. A separate SYSTEM voice toggle preserves other settings
and persists in `settings.txt`; an active one-shot finishes when muted. Tests
exercise the real packaged mod's queue and settings, host ownership/lifetime, and
all assets through production validation, preparation and stereo PCM cursors.
This proves content and nominal queue timing, not physical speaker latency.

Validation and qualification:

- Final offline publication: all 122 current hashes and blind word checks pass;
  finite non-silent mono WAVs have no clipped samples. Every catalogue line has
  an owning runtime hook. Python syntax and `git diff --check` pass.
- Direct Java 21 example compilation against the current private engine classes
  passes. The first queued focused Maven attempt reached test compilation and
  failed on two new scene-test helper calls; those factory lambdas are corrected.
- Focused request: `python3 tools/testing/maven_queue.py --lean -Dmse=off
  -Dtest=TestModSceneHost,TestModCatalogValidator,TestEggmansSkyVoice,TestEggmansSkyVoiceAssets,TestModApiSignatureSurface test`.
  The first completed run had 46 cases: 44 passed, two failed, zero errors/skips.
  Both failures identify the accepted-but-empty patch SFX registry described
  above; the eight queue/settings tests, 18 scene-host tests and nine API surface
  tests passed. After fixing both registry paths, the narrow rerun is
  `maven_queue.py --lean -Dmse=off
  -Dtest=TestModCatalogValidator,TestEggmansSkyVoiceAssets test` on the updated
  private base `c83f87386403`. It completed at 21:15:28Z, exit 0: all 11 cases
  passed with zero failures/errors/skips, including validation and production
  PCM playback of all 122 packaged clips.
- The pre-existing base request selected 3058 ordinary classes at
  `913c5a3516b1`, with a 40-minute execution cap. Run
  `20261008T185944Z-d6436e78` terminated at that cap, outer exit 2: 2690 ordinary
  reports, 23434 cases, two failures, zero errors and 56 skips; no guard lane ran.
  Its two failures were `TestS3kMhzAct2AuthoredRoute` incoming-route cases [2]/[3],
  asserting late pulley ownership of Tails/Sonic. Main also changed during this
  incomplete run for the separately coordinated parity integration. It is not
  qualifying baseline evidence. All reported skips were inspected (no missing-ROM
  skip); consumed diagnostics were acknowledged and removed. Existing parity and
  Windows requests remain untouched. The owning actual-main qualification
  subsequently completed at `863683b092f7` as run
  `20261008T194559Z-9deb33e8`: 3058 ordinary reports/26570 cases, 26 inherited
  failures, zero errors and 62 inherited skips; separate fresh guards have
  87 reports/674 passing cases with zero skips. Its complete assertion and skip
  table is in the [owning parity audit](../audits/2026-10-07-stock-parity-gap-verification.md#actual-main-full-assertion-and-skip-summary).
  Evidence-only successor `5662ad2c2291` changes that audit alone, retaining
  every executable/test/build input of tested863.

Voice commit `d5f4a36ccd40` passed the required hooks after removing the
unnecessary descriptor comment. Private merge `46cfd4208` imports the completed
main evidence; `a5ac85a1b2dc` imports independently published develop `d4993a730`,
including Starfall and the original Eggman's Sky feature. The latter merge only
conflicts in the hazard catalogue: retain all parity hazards and one copy of the
identical Eggman's Sky draw-cost note. Voice source/assets and scene SFX routing,
registry and launch-factory code are unchanged by these imports. Main local and
remote publication holds remain in effect pending the coordinated successor.

The combined plan against actual held main `5662ad2c2291` selects all 3070 ordinary
classes plus separate fresh guards. Qualification used the normal runner with
`--max-minutes 150`, excluding queue wait, and the unchanged ten-minute no-output
rule. The announced estimate was 80–110 minutes ordinary plus about four minutes guards.
Timeout, omitted required reports, missing-ROM skips or new/worsened/unattributed
assertions block integration. The entire candidate input tree remained frozen
through both lanes; source/publication in main remains under the parity hold.

Private qualification of `d2899a86d75f` completed at 2026-10-08T23:20:45Z,
after admission at 21:49:35Z, as `20261008T214935Z-95b99174`. Both
`python3 tools/testing/run_categories.py --base 5662ad2c2291 --max-minutes 150 --preflight`
and the same command with `--run` used Java 21 and the normal launch environment
without inherited `LD_LIBRARY_PATH`, with all three verified original ROM paths.
The retained six-hour supervisor excluded queue wait from the runner's 150-minute
execution cap. No timeout occurred, and the recorded input fingerprint remained
`286a3184ee22a24b50fbef883158a2d3a400974d110f5c90c0dafa5e6bd54b55`
through both lanes and the terminal inspection.

- Ordinary: all 3070 selected classes, 3068 reports, 26609 cases, 26 failures,
  zero errors and 63 skips, in 5224.24 seconds. Exit 1 is the inherited-failure
  outcome, not a green full-suite result. No negative cases were omitted.
- Fresh guards: 87 reports, 674 cases, all passing, zero skips,
  in 245.57 seconds; exit 0.
- Every failure identity, kind/type and complete first assertion matches
  actual-main `863683b092f7` / run `20261008T194559Z-9deb33e8` in the
  [owning parity table](../audits/2026-10-07-stock-parity-gap-verification.md#actual-main-full-assertion-and-skip-summary).
  Twenty-five assertions match literally. The complete SSZ assertion matches
  at 2907 characters after only that table's exception-prefix removal and
  replacement of two verified `RewindObjectStateBlob@hex` values with `@HASH`.
  There are no added, removed or worsened failure assertions.
- All 62 inherited skip identities and literal first reasons match. The sole
  addition is
  `com.openggf.mods.scene.host.TestSceneRenderer#changingBatchSizesPreservesEveryPixelAndStreamingUpdates`:
  `org.opentest4j.TestAbortedException: Assumption failed: OpenGL 4.1 unavailable`.
  This test's source is byte-identical to incoming develop `d4993a730724`;
  GPU rendering remains explicitly unverified by this run. There are no
  missing-ROM skips. The absent CLI failure remains an inherited loader-environment
  effect, not a source fix.

`python3 examples/build_example.py eggmans-sky` also completed with zero
validation findings. Its 17285297-byte JAR contains all 122 WAVs and provenance
byte-identical to the committed resources, excludes private request/credential
files, and has SHA-256
`75d30e818a41eef5a942bf57028b586154aa4f013e1df9ed6e4e5a5b6600a8cb`.
A copy is preserved in the external voice-bank task directory while temporary
Maven output remains under this worktree's `target/`.

Consumed qualification diagnostics are acknowledged and deleted. This completed
private result does not release the main/publication hold: the parity owner is
qualifying the updated remote baseline and combined successor. Reconcile that
published successor before main integration, then complete destination validation,
push and accounted-for cleanup. No main inputs, commits or publication changed.

The parity hold was released with published develop
`019dd454b0d63b10a1d0585450bb28f34e360c04`. Its actual-main qualification belongs
to `b317e94ebdce60c6f81553113543295c75b1d826`, run
`20261009T015657Z-e417e53f`, with a verified prose-only publication successor.
The [updated complete baseline table](../audits/2026-10-07-stock-parity-gap-verification.md#updated-actual-main-full-assertion-and-skip-summary)
matches all 26 private failure assertions and all 63 skip identities/reasons.
Private merge `211b61de2999` retains the published hazard catalogue and audit;
the only conflict was the catalogue's relocation of the Eggman draw-cost note.
Compared with tested private `d2899a86d75f`, only three documentation paths
change: that catalogue, the parity audit and this task record. Every executable,
test, fixture, example asset, build and API input remains unchanged, so the
completed private qualification is reused rather than repeated.

Destination qualification will pin published `efedf9198eef`, select all 3070
ordinary classes and separate fresh guards, and use the normal runner's
150-minute execution cap excluding queue wait, unchanged ten-minute no-output
rule and a retained six-hour outer supervisor. Expected cost is 80–110 minutes
ordinary plus about four minutes guards. Freeze main tracked inputs and HEAD
through both lanes, compare the complete negative cases against the updated
source-attributed baseline, and block push on incomplete coverage, missing-ROM
skips or new/worsened/unattributed failures. Preserve all seven unrelated main
paths and the user's private `.env` during integration and cleanup.

Before main integration, independently published Windows evidence advanced develop
to `efedf9198eef5e717c8827f1aa34dc7728cb92fd`. The destination guard stopped
before changing main. Its delta from `019dd454b0d6` is exactly one research
Markdown path, with no executable/test/build/API change. Import that evidence
and use the newer published SHA as the actual destination base; the tested
`b317e94ebdce` negative-case table and completed private voice qualification
remain applicable to their unchanged inputs.

### Destination qualification

The conflict-free develop integration is
`0103b9bdc880c142301073fba9024750ccf4f1c2`, first parent and pinned published
validation base `efedf9198eef5e717c8827f1aa34dc7728cb92fd`. Its tracked index
exactly matches private composition `267ab7a478c6`; all seven unrelated main
paths retain their recorded bytes and submodule state.

Normal command `python3 tools/testing/run_categories.py --base efedf9198eef
--max-minutes 150 --run` followed the passing Java 21/Lua 5.4/PowerShell
preflight in retained unit `openggf-eggmans-sky-voice-main-0103b9bdc`.
It admitted at 2026-10-09T04:49:19Z as `20261009T044919Z-b2966220`, completing
at 06:15:16Z without timeout. Queue wait was 3864 seconds and excluded from
the execution cap. Both lanes received the three canonical original absolute
main ROM paths; the source/input fingerprint stayed
`43a48821e59f083567cf03f6f85a1c31c87417452ecf88334dabd3ff5ca2563d`
through terminal inspection.

- All 3070 selected ordinary classes: 3068 reports, 26609 cases, 26 inherited
  failures, zero errors and 63 skips; 4915.64 seconds, Maven exit 1.
- Separate fresh guards: 87 reports, 674 passing cases, zero failures/errors/skips;
  240.45 seconds, exit 0.
- All 26 identities, kinds/types and complete first assertions match the
  [published source-attributed baseline](../audits/2026-10-07-stock-parity-gap-verification.md#updated-actual-main-full-assertion-and-skip-summary).
  Twenty-five are literal matches. The complete SSZ first line is 2952 characters
  and matches at 2907 after only the documented exception-prefix removal and
  two verified `RewindObjectStateBlob@hex` normalizations. All 63 skip identities
  and literal causal reasons match. There are no omitted negative cases,
  missing-ROM skips or added/worsened/unattributed failures.

This qualifies the unchanged inherited failures; it is not a green whole-suite
or parity claim. Native/GPU coverage retains the baseline's explicit OpenGL
skip. Consumed diagnostics were acknowledged and deleted, and the bounded
supervisor unit was collected. A prose-only delivery successor records these
results; executable/test/build/API inputs remain those of tested `0103b9bdc`.

The already-validated JAR is installed locally as `mods/eggmans-sky.jar`, with
all 122 resources verified against the integrated source and the same package
SHA-256 recorded above. Existing Mod Manager state and other mods are preserved.
The user's `.env` is preserved with mode 0600 in the external voice-bank task
directory before removing the fully merged voice worktree; no credential enters
Git or the package. Push only develop, then remove the accounted-for local voice
branch/worktree and the two owned merge-backup stashes, preserving unrelated work.
