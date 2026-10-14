# Master title redesign: cartridge shelf, Game Setup and tabbed Settings

Branch `feature/ai-master-title-redesign`, based on `develop` at `f02bdbfd57`.

## Problem

The two-pane hub listed per-game and engine-wide actions together, packed the
carousel, profile status and mods ticker into one corner, and showed only "3
changes" for the selected game's profile. The same settings lived in up to three
places: global keys, per-game Launch Options, and the raw `launch.*` keys under
Settings > Launch. A master-title launch always applied the per-game rewind, debug,
cross-game and character values, so the global Rewind and Debug switches had no
effect on hub launches. Launch Options saved on Enter/Tab while Settings and Mods
used a draft; `*` meant "not stock" in one screen and "unsaved" in another. Settings
showed four rows per page and spread hotkeys over five sections.

## Decisions

- **Layout:** a cartridge shelf (selected game centred, neighbours receding), with
  per-game actions under the card and engine-wide actions as header tabs.
- **Setup chips** state what Start will launch. Amber means "set for this game only".
  Settings does not list which games override a value; that is visible at launch
  instead.
- **Default/On/Off:** Rewind and Debug tools gained a `global` value
  (`LaunchToggle`); aspect already had one. `LaunchProfileApplier` applies no
  session override for `global`. Legacy booleans migrate in
  `ConfigMigrationService.migrateLaunchProfileToggles` (`true` to `on`, the old
  stock `false` to `global`).
- **Borrow from** names cross-game donation in the UI. Cross-game and Players stay
  per-game only; the global `crossGame.*` and `characters.*` keys only matter for
  direct launches and moved to Advanced settings.
- **One draft flow:** Settings, Game Setup and Mods ask Save / Discard / Keep
  editing on Back with unsaved changes, Save first.
- **Footers** carry only screen-wide controls as key caps; row-specific keys sit in
  the description box; status (unsaved count) sits in its own box on the left;
  Up/Down is never labelled.
- **Feedback polish** (follow-up to `2596fbc40`): the reported harsh menu cues
  retain their sine pitches and note durations, but use 8 ms cosine attacks,
  18 ms cosine releases to exact silence at each note boundary, and 18% peak
  amplitude instead of 28%. This softens their transients and leaves more mixing
  headroom. Start uses white text with the existing font shadow on its gold fill.

## Rejected

- Sidebar hub, level-select grid and attract-screen layouts (round 1 mockups): the
  shelf read best as a game picker and scales to many standalone mod games. The
  level-select grid lost the ROM logos; the attract screen hid Mods and Settings a
  level deeper.
- Listing the games that override a setting on Settings rows (round 2 mockup):
  rejected as cluttered; overrides are shown on the hub chips and in Game Setup.
- "Sprite Donation" as the cross-game label: too narrow, since donation also covers
  spindash, sounds, Super Sonic handling and the S3K save screen.
- A per-frame cache of the hub chips: tests (and any caller that changes ROM
  availability without advancing a frame) saw stale chips; reading the saved
  profile on demand is cheap.

## Verification aids

`MasterTitleCaptureTool` renders every hub, Game Setup and Settings scene offscreen;
see [agent workflow tools](../../agent-workflow/README.md).

Initial feedback follow-up validation in the current checkout, based on `2596fbc40`:
`JAVA_HOME=$(/usr/libexec/java_home -v 25) python3 tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestHostUiSfx,TestMasterTitleScreenAudio,TestChildMenuFeedback,TestAudioManagerRuntimeInstallation,TestAudioPresentationMixer,TestSampleBackedVoice' test`
passed 40 tests with no skips. The change-based plan selected all 3,157 classes
because the title screen is unclassified shared code; focused validation is
proportionate to generated cue PCM and a single label colour, with no mixer,
timing or gameplay changes. `MasterTitleCaptureTool` completed all 18 scenes at
320 logical pixels; inspection of the hub confirmed white Start lettering on
gold. PCM checks cover note boundaries and amplitude, not subjective listening
or device underruns on the user's audio setup.

## Sequential playback breakup follow-up

The envelope/volume change alone did not fix the reported popping. A 600-frame
offline probe starting a different overlapping host cue every three frames
matched independently mixed sample voices exactly (zero mismatching samples).
The actual OpenAL sink repeatedly stopped during hidden-window menu drawing:
render submissions exceeded the 16.7 ms budget while audio generation remained
below 0.4 ms. Increasing the device queue would only postpone starvation while
the menu continually produced fewer samples than the speaker consumed.

Adjacent solid rectangles now share an ordered `MenuPixelFont` submission,
flushing at text/texture transitions and batch boundaries. Hub card bodies,
borders, sparkle and header rectangles use that same batch. This leaves the
shared renderer, audio clocks, mixer and device buffering unchanged.
The retained `MenuAudioProbeTool` validates its menu destination and physical
speaker cursor, drives real menu feedback, and separates warm-up from measurement.

Matched measurements used the pre-batching `MenuPixelFont` and `MasterTitleScreen`
sources from `2596fbc40`, compiled into a temporary class directory ahead of the
current checkout's classes. White Start text and the softened cues were held
constant. Command: Java 25 with `-XstartOnFirstThread`, the production dependency
classpath and `com.openggf.tools.MenuAudioProbeTool <width> <hub|settings>`.
Each case used 120 warm-up frames then 240 measured frames with sequential
navigation every six frames; `glFinish` included GPU completion.

| Rendering | Menu | Width | Mean draw submission | Measured speaker underruns |
|---|---|---|---|---|
| Baseline | Hub | 320 | 27.745 ms | 46 |
| Batched | Hub | 320 | 13.308 ms | 0 |
| Baseline | Settings | 320 | 20.782 ms | 26 |
| Batched | Settings | 320 | 12.777 ms | 0 |
| Batched | Hub | 400 | 13.339 ms | 0 |
| Batched | Settings | 400 | 10.873 ms | 0 |

The batched cases also had zero warm-up underruns and nonzero cue PCM. These are
measurements of owned hidden-window rendering and real speaker playback on this
Mac, not an interactive Engine.loop or subjective listening claim.
All 18 `MasterTitleCaptureTool` scene PNGs were byte-identical between baseline
and batched rendering after removing random clouds in both temporary capture
drivers. No random-cloud change was made to production.

`JAVA_HOME=$(/usr/libexec/java_home -v 25) python3 tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestMenuPixelFont,TestMenuRenderAlignment,TestPixelFont,TestPixelFontTextRenderer,TestTexturedQuadRenderer,TestMasterTitleScreenLayout,TestMasterTitleCarousel,TestMasterTitleMouse' test`
passed 50 tests with no skips. Regression coverage includes rectangle growth,
alpha/colour and overlap order, text transitions, repeated flushes and immediate
unbatched fills. The combined change-based plan still selected 3,157 classes;
focused validation remains proportionate to menu-only geometry submission,
with direct native playback and complete capture-scene comparisons covering its
consumers. This is focused validation, not a full engine-suite pass.


## Menu cost and white-flash follow-up

Compact glyphs, rectangles and checkerboards now share the unused final atlas
cell's white texel, preserving draw order and colours while eliminating texture
switches. The launch flash joins the final footer batch. All three textured-quad
submission paths now upload with `glBufferData(..., GL_STREAM_DRAW)` so the driver
can replace storage while queued draws retain the previous storage, instead of
blocking each `glBufferSubData` overwrite behind the GPU. Native measurements
confirmed this was the remaining large submission cost; geometry and shader
contracts are unchanged.

A tone-bank preload was tried and removed: first-command update overhead remained
variable (approximately 6–17 ms), so cue synthesis alone was not established as
its cause. The buffer change creates more frame headroom for that cold work.
Do not infer the source of a launch hitch from a whole-frame maximum alone; the
probe now prints update, audio, draw and finish separately for every flash frame.

Before buffer streaming, the shared-atlas pass reduced Settings draw submission
from 12.883 to 2.806 ms at 320 and measured 2.511 ms at 400. Hub submission still
averaged about 10 ms, and first flash frames reached 34–40 ms. With buffer streaming,
the 320 launch run measured 0.946 ms mean draw submission over 240 frames, no frames
above 16.7 ms, and zero warm-up/measured speaker underruns. Its 14 flash frames
averaged 3.550 ms including GPU completion, with a 15.340 ms maximum; individual
flash submissions were approximately 0.3–0.6 ms. These device measurements vary with
host load; they cover menu confirmation, not subsequent game bootstrap.

Java 25, Lua 5.4 and PowerShell tool preflight passed. The pinned base remains
`2596fbc40`; the change-based plan selects 3,157 ordinary classes plus structural
guards. A broad run was started, then interrupted after reviewing proportionality
with James; no broad-suite pass is claimed. Its diagnostics were inspected and
acknowledged. The storage upload change is bounded to three renderer paths, with
unchanged geometry, shaders, public contracts and gameplay timing; focused
regressions plus real GPU completion and complete scene pixel comparisons directly
exercise the changed behavior. This is an OpenGL upload mechanism change, not a
shared gameplay/timing algorithm change. Before the streaming change, focused atlas/menu
regressions passed 67 tests without skips, and the audio/menu follow-up passed 49
without skips. Final focused results and visual comparisons follow below.


Final native checks used the built production classes with an absolute classpath,
Java 25 `-XstartOnFirstThread`, 120 warm-up/240 measured frames and `glFinish`.

| Menu | Width | Mean draw submission | Mean GPU finish/swap | Slow frames | Measured speaker underruns |
|---|---|---|---|---|---|
| Hub | 320 | 1.059 ms | 6.012 ms | 1 | 1 |
| Settings | 320 | 0.585 ms | 5.799 ms | 0 | 0 |
| Launch | 320 | 0.943 ms | 2.654 ms | 1 | 1 |
| Hub | 400 | 1.140 ms | 3.458 ms | 1 | 1 |
| Settings | 400 | 0.556 ms | 5.937 ms | 0 | 0 |
| Launch | 400 | 1.194 ms | 2.704 ms | 0 | 0 |

Warm-up underruns were zero throughout. The final cold-launch flash averaged
6.589 ms (320) / 4.133 ms (400); maxima were 32.843 / 15.038 ms. The remaining
320 peak was in first-button update (26.526 ms), while that frame's GPU submission
was 0.560 ms. A temporary JFR recording sampled cold physical-input class loading
and lambda linkage in command submission on the first press. These are distinct
from the repeated GPU overwrite stall; first-use/runtime scheduling overhead is
not eliminated or certified by this patch. Do not report all runs as zero
underruns or infer game-bootstrap timing from this tool.

Final focused command:
`JAVA_HOME=$(/usr/libexec/java_home -v 25) python3 tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestMenuPixelFont,TestMenuRenderAlignment,TestPixelFont,TestPixelFontTextRenderer,TestTexturedQuadRenderer,TestMasterTitleScreenLayout,TestMasterTitleCarousel,TestMasterTitleMouse,TestMasterTitleScreenAudio,TestChildMenuFeedback,TestHostUiSfx,TestAudioManagerRuntimeInstallation' test`
passed **73 tests, zero failures/errors/skips** against the final production sources.
The deterministic capture driver cleared random clouds identically in both
variants; all **18 scene PNGs at both 320 and 400** were byte-identical between
pre-atlas/pre-streaming and final rendering, including confirmation-flash alpha
and order. Temporary comparators, screenshots and JFR data were removed after
inspection. No full ordinary or structural-guard pass is claimed.


## Develop merge on 2026-10-14

Merged develop `7277cef404` into the title branch at `e8c1712297`. The title
conflict combines develop's dynamic Editor Projects and Import Content callbacks
with the redesigned Advanced settings and help rows. All five rows use the same
30-pixel spacing for drawing and pointer selection. The no-argument mod-manager
entry remains package-visible for content import and delegates to the redesigned
game-filtered entry. The changelog retains the redesigned hub and imported editor
capabilities; taking either side wholesale would drop one set of actions.

Java 25 tool preflight passed. The plan against the pre-merge head selects 3,289
ordinary classes plus guards across the imported develop history. Focused
validation covers the merge's bounded title/menu integration rather than
revalidating all imported subsystems; this is not full-suite or graphics-capture
validation. With Java 25,
`python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=TestMasterTitle*,TestTitleHubNavigation,TestTitleContentImportAccess,TestModManagerScreen' test`
passed 149 tests with three ROM-preview skips caused by unconfigured ROM paths.
The content-import navigation regression follows the redesigned header tabs and
checks single activation under held confirmation, settings/drop ownership, and
the existing mod-manager neutral-input boundary.

The follow-up
`python3 tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestMasterTitleRomPreview' test`
with `sonic1.rom.path`, `sonic2.rom.path` and `s3k.rom.path` set to the existing
absolute root ROM paths passed all 31 preview tests with no skips. Both completed
runs measured the resolved merge tree before its merge commit; no production
changes followed those runs.


## Playtest feedback follow-up on 2026-10-14

- **Setup chips are status only.** Moving Down from the card had to step through
  the chip row before reaching Start, Game Setup and Recordings. The hub no longer
  has a chip focus zone: the keyboard, controller and pointer all pass over the
  chips, a click on one does nothing, and their per-chip descriptions and footer
  hints are gone. An intermediate version, `c19cbcb70b`, kept mouse hover and click
  on chips; it was rejected because the chips still looked and acted like
  controls. Rejected:
  making Left/Right cycle a chip's value in place. That would edit a per-game
  profile from the hub without Game Setup's draft and Save / Discard prompt, and
  it would keep the extra row on the path to the buttons.
- **Borrow from kept its focus by index.** Choosing or leaving the S3K donor swaps
  Character and Sidekick for the read-only Team row, which shifts the focusable
  rows above Borrow from by one. The focus now follows the focused item rather
  than its index.
- **Mod Manager mouse support.** The title passes its pointer, already moved into
  the centred 320-pixel page, to `ModManagerView.pointer` before each update. The
  neutral `ModManagerScreen.MenuInput` carries it as default methods, so the
  `mods.ui` package still has no engine dependency. Hover focuses list rows,
  buttons and prompt choices, and a click runs them. The wheel moves the list,
  scrolls reading pages and reorders on the order page. A click closes a details
  or notices page, because their Enter actions (disable without trust, retry
  Apply) stay keyboard-only to avoid accidental activation. On an armed
  confirmation, a second click confirms, as a second Enter does. Right-click goes
  back, skipping the keyboard's intermediate step from the buttons to the list.

Validation: with Java 25,
`python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=TestMasterTitle*,TestTitleHubNavigation,TestTitleContentImportAccess,TestModManagerScreen,TestModManagerScreenHost,TestModManagerScreenNativeGuard,TestLaunchConfigPanel,TestHubSetupChips,TestChildMenuFeedback' test`
passed 183 tests. Three ROM-preview tests were skipped because no ROM paths were
configured. This is focused validation of menu-only input changes. It is not a
smoke-suite or full-suite pass.


## Suspend to the hub on held Escape (2026-10-14)

Holding Escape in an ordinary Sonic 1, 2 or 3&K session now parks the game behind
the master title instead of tearing it down. Requested by James as a "minimise"
that sits beside the stock games on the shelf, with settings changes deferred to
the next launch.

- **One paused slot, first on the shelf.** `MasterTitleEntry.Suspended` is its own
  card (last frame, a PAUSED badge, "Paused in Emerald Hill, Act 1") with Resume and
  End game, rather than a fourth stock-styled card that would make "Sonic 2" ambiguous.
  The catalog never contains it; `MasterTitleScreen` prepends it, so catalog refreshes
  keep their duplicate-id check.
- **Keep the session, unbind it.** `GameLoopSuspension` (outside the line-budgeted
  GameLoop) makes `resolveGameplayModeContext` return null while parked. The hub then
  runs exactly as with no game: bootstrap camera and fade, Engine-ticked fades, no
  gameplay frames. The session stays published in `SessionManager`; mode changes use
  `changeGameModeWithoutRewindBoundary`, so rewind history survives the round trip.
  `park()` returns the mode it left, because outside levels the game can change mode
  during the fade-out.
- **What the hub would otherwise change** is held by `SuspendedGame`: the music (an
  `AudioLogicalSnapshot` captured before `fadeOutMusic`, restored on resume, since the
  shadow presentation is the only speaker path), the camera position (the hub's
  screen-space draw zeroes the gameplay camera), and the settings.
- **Settings freeze** (`SuspendedSettings`) uses only the public configuration
  surface, so the `@ModApi` `SonicConfigurationService` signature pins are unchanged.
  Suspend records overrides and effective values and clears overrides, so the hub
  shows saved preferences as after a normal return. Resume restores the overrides and
  pins each value saved since. Window size and derived pixel sizes are not pinned.
- **Ending it** goes through the existing `resetForGameplayFromMasterTitle`. That
  method also drops a parked game first, so every launch route (Start, recordings,
  editor projects, traces, mod reload) ends it even without the UI prompt.
- **Eligibility.** Traces, hosted runs, user recordings and playback, mod scenes and
  scene acts, editor playtests, standalone games and test mode keep the full return:
  each owns its own ending, and standalone games were out of scope.
- **Mod Escape.** Mod scenes, their acts and standalone games read a short Escape
  press themselves. While one is running the toast stays hidden for a tap and appears
  after 15 frames of holding; the 60-frame hold is unchanged. The mod still receives
  the tap, as `SceneKeys` documents.

Rejected:

- Snapshotting every runtime singleton, or keeping several games in memory. The
  engine runs one game at a time, and parking needs no copy.
- Pausing the audio sink: hub cues share it, so the menu would go silent.
- Keeping GameLoop bound to the parked session. Engine skips its own fade tick while
  a gameplay runtime owns fading, so the hub's fade-in would never progress.
- Freezing settings with new public methods on `SonicConfigurationService`: that
  would change the pinned Mod API surface.
- Suspending from the Mutators overlay's *Return to game hub*. That is an explicit
  quit and still tears down.

Validation, Java 25, with the root S1, S2 and S3&K ROM paths:
`python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=com.openggf.TestGameLoop*,com.openggf.TestEngine*,com.openggf.TestMasterTitle*,com.openggf.TestEscape*,com.openggf.TestModScene*,com.openggf.TestTraceSessionLauncher*,com.openggf.TestTitle*,com.openggf.TestSuspended*,com.openggf.game.TestMasterTitle*,com.openggf.game.TestTitle*,com.openggf.game.TestHub*,com.openggf.game.TestLaunch*,com.openggf.game.launch.*' test`
passed 554 tests with no skips. `TestGameLoopSuspension` holds Escape in a real Sonic 2
Emerald Hill run. It parks, runs 300 hub frames with input held, then resumes with
the player, camera, rings and timer unchanged, and the level runs again. A second
case stops the zone music and plays a hub cue, and the restore returns the same music
slot. `-Pguards` `TestArchitecturalSourceGuard`, `TestArchitecturalReviewGuard` and
`TestArchUnitRules` passed 108 tests (GameLoop at 3,303 of 3,313 effective lines).
`MasterTitleCaptureTool` with an Emerald Hill frame rendered the paused-card scenes,
and the stock-card scenes were inspected for regressions. The change-based plan selects all 3,293 smoke classes because
GameLoop and Engine are shared, so this is focused consumer validation, not a smoke
or full-suite pass. Engine's GL-side suspend, thumbnail and resume path has no
automated test; it needs an interactive playtest.


## In-flight settings for a suspended game (2026-10-15)

James asked for the settings that are easy to apply mid-game (aspect, debug, rewind and
others that make sense) to reach a suspended game on resume, without a reload.

- **`LiveSettings` is the single list.** A setting is listed only when its readers take
  a new value without a reload. Rewind (`LiveRewindManager` reads it per frame and
  installs the recorder lazily), debug tools (read per frame; Engine's cached copy is
  refreshed on resume), the editor flag, rewind history length and tear bands, all key
  bindings (looked up when pressed through `InputBindingFactory`'s supplier),
  controller settings, wide camera movement (re-read by `Camera.refreshViewportDimensions`),
  automatic window size and capture settings (read when a recording starts). Aspect is
  listed only when the game was paused in a level.
- **Not listed**, so pinned until the next launch: characters and Borrow from (the
  player objects and donor content are built at launch), ROMs, audio backend settings
  and region, frame rate (Engine reads it once), Discord (PresenceManager reads it once),
  the rewind VHS effect (its pass exists only if enabled at boot) and tape-coast tuning.
- **Resume = a fresh launch for live keys.** `SuspendedSettings.resume` asks
  `LaunchProfileApplier` what a launch of this game would set now (saved preferences
  plus its Game Setup profile), applies those values to live keys and pins the rest.
  The paused card gained a Game Setup button. Its rows, and every Settings row, say
  whether the paused game takes the change.
- **Mid-level aspect.** Width was cached at level load in `LevelManager`
  (screen size and debug overlay), `ParallaxManager` (final per-column VScroll arrays)
  and `BackgroundRenderer` (column buffer, which outlives sessions). `Engine.applyResolvedDisplayDimensions`
  now also calls `LevelManager.refreshDisplayDimensions`, which resizes parallax,
  rebuilds the debug context and resizes the shared background column buffer. Level
  loads resize that buffer too, which fixes a pre-existing bug: a later game at a
  wider aspect lost background column scroll past the first game's width. The resize
  sits in `LevelManager` because ArchUnit's frozen layering rule forbids graphics
  calling the level render package (a first attempt in `GraphicsManager` failed
  `low_level_layers_do_not_depend_on_runtime_layers`). `LevelManager`, `ParallaxManager`
  and `BackgroundRenderer` are `@ModApi` classes, so the new methods are package-private.
  The engine reaches them through unannotated `LevelDisplayWidth` and
  `BackgroundRendererWidth`, and the candidate Mod API surface and its pin are unchanged.
  Creators never resize the display.
  `rebuildLevelDerivedState` was rejected for this: it also rebuilds the tilemap
  manager, which does not depend on width.
  Title, special-stage and ending screens size themselves on entry, so a new aspect
  waits for a level there.

Evidence. `AspectSwitchProbeTool` loads a level at 4:3, holds right for 60 frames,
switches to 16:9 through those hooks, runs 60 more frames and renders. For S1 GHZ1 and
SBZ1, S2 EHZ1, HTZ1 and CPZ1, and S3K AIZ1, HCZ1 and MGZ1, the frame is pixel-identical
to a native 16:9 start with the same input (0 differing pixels). The `stale` negative
control, refreshing only the camera, differs by 18,665 to 62,071 pixels per zone (AIZ
draws the level against the old width and loses the waterfall), so the comparison
does detect missing hooks. In `TestGameLoopSuspension`, rewind switched on mid-level
in S2 EHZ rewinds, and stops at the frame it was enabled. A mid-level 16:9 change gives
a 400-pixel camera and 25 parallax columns, and the level keeps running.
`TestSuspendedSettings` covers settings saved in the hub, Game Setup for the paused
game, aspect in and out of a level, and characters staying as launched. The capture
tool's paused scenes 24–28 show the Game Setup button and the row status lines.
Validation: the consumer selection above plus Game Setup, Settings, configuration,
camera, parallax, background and level-manager tests passed 635 tests with no skips;
`run_categories.py --category rendering` (with common, 471 classes) ran 3,003 tests with
0 failures, 4 errors and 19 skips. All 4 errors are `UnsatisfiedLinkError` for the Linux
surfaceless-EGL library on macOS, in palette-presentation classes this change does not
touch; they were not checked against a baseline. The skips need an offscreen GL context
or opt-in properties. `-Pguards` passed 108 tests after the layering fix. These
results are not a full smoke pass.


## Restart-needed indicator (2026-10-15)

James asked for a clear, user-friendly way to see which settings need a restart.

- **`SettingTiming`** (`NOW`, `NEXT_GAME`, `RESTART`) classifies every
  `SonicConfiguration` key from its readers. The switch has no default, so a new key
  does not compile until it is classified. The audit found:
  - **Restart:** frame rate (Engine constructor) and window size; display shaders,
    their keys and phase (display initialisation; the shader picker changes them
    live, Settings does not); the colour profile and its toggle key; the VHS rewind
    pass; Discord (GameLoop constructor); start-up screens and default ROM; test
    mode; and turning sound off. The title's audio backend survives launches, while
    turning sound on works at the next launch.
  - **`SCALE` has no runtime reader.** It is classed as restart with the window keys.
  - **Next game:** audio driver settings, region, characters, Borrow from, rewind
    tuning, aspect and load-time simulation.
  - **Now:** everything read at use.
- **`LiveSettings` now derives from it.** `NOW` settings always reach a paused game;
  restart settings never do. Of the next-game settings, only the four a resume
  refreshes reach it (rewind, debug, deadzone, autosize).
- **UI.** An amber RESTART tag on restart rows, drawn only where it fits beside the
  label. Those rows' second help line reads *Needs a restart: close and reopen
  OpenGGF.* A *RESTART NEEDED* footer pill appears on Settings and the game shelf,
  and the Settings tab row names the waiting settings, or counts them when the names
  overflow. Other rows keep both description lines, a deliberate earlier design
  pinned by `TestEngineSettingsScreen`; their timing is in Details. Always spending
  the second line on timing was tried and rejected because it broke that design.
- **`PendingRestart`** counts only Settings saves whose values differ from what
  OpenGGF started with (captured in `Engine.init`). Engine writes such as
  `DEFAULT_ROM` on every launch do not count.

Validation: `TestSettingTiming` (classification, consistency with `LiveSettings`,
pending rules) and the Settings screen test. The consumer selection passed 520 tests
and `-Pguards` with the Mod API checks passed 143. `MasterTitleCaptureTool` scenes
29–31 were inspected.
