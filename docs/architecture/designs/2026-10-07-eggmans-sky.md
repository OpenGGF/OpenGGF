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
