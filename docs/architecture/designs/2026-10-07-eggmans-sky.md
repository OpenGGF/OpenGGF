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
