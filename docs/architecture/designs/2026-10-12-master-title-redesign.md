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
