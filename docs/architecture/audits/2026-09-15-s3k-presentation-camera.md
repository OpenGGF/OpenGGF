# S3K retained presentation and moving-camera alignment

## Report and cause

The user observed MHZ objects trailing the camera after the FBZ sprite-table
publication work. Investigation starts from develop `316788395`; the candidate
is isolated on `bugfix/ai-s3k-presentation-camera`.

The preceding implementation (`7653029ab`, finalized by `892292047`) retained
screen-relative CPU sprite geometry until the next SAT-serving VBlank. Terrain
and its sprite-occlusion mask still consumed live camera/parallax values after
the next gameplay loop. A stationary world object and the terrain beneath it
therefore used different camera generations. Stationary FBZ comparison pairs
could not expose that camera-delta error.

In the locked-on disassembly, `VInt` writes `V_scroll_value` to VSRAM;
`VInt_8_Cont` uploads `H_scroll_buffer` and the sprite table. The single-player
`VInt_0` lag path retains this published state. The candidate pairs immutable
scroll buffers/registers and resolved plane routing with each prepared SAT,
publishes them together, and includes both pending and published states in the
existing rewind adapter. Visible foreground, high-priority mask and background
sampling consume that generation. Live gameplay camera/physics remain owned by
the gameplay loop. The separately owned ending background keeps live scrolling.

Reprojecting old sprites through the new camera was rejected: that hides the
symptom by replacing the ROM's published screen coordinates. A zone-specific
MHZ exception would leave the same shared defect elsewhere.

## Verification protocol

`TestS3kMovingCameraPresentation` boots MHZ Act 1 through
`GameplayCaptureSession`, drives `240 R; 60 -`, and executes real GL terrain and
mask commands on moving-camera frames. Their origins and horizontal buffer must
match the CPU generation used by the published SAT. The same test is installed
on the pinned baseline to establish the regression. No production baseline
sources are changed.

Lifecycle tests cover immutable buffer ownership, signed/shaken coordinates,
vertical wrap values, per-line/per-column scrolling, plane reversal, skipped
publication on lag, pending/published rewind restoration and load reset. These
local checks do not certify complete MHZ routes or whole-zone visual parity.
Existing FBZ route/character/viewport and native comparison gaps remain open;
the documented five-frame S1 elevator challenge is unchanged.

Before/after gameplay captures use the same input under
`<external-task-directory>/s3k-presentation-camera-20260915/`.
Review trajectory CSVs and actual images; final comparison videos have one-second
introductory and trailing holds. Engine captures demonstrate the regression and
correction, not pixel parity with a native emulator.

## Execution status

On `316788395` plus the candidate, queued Maven
`-Dmse=off -Dtest=TestS3kMovingCameraPresentation,TestLevelSpritePresentation,TestLevelSpritePresentationLifecycle,TestLevelRendererBackgroundViewport,TestSpritePresentation -Ds3k.rom.path=<absolute verified S3K ROM> test`
passed **27 tests, zero failures/errors/skips**, in 45.784 seconds including
compilation. The identical new moving-camera test on unmodified `316788395`
failed: expected terrain X **206**, actual **192**, while SAT retained the
preceding CPU camera. One failure, no errors/skips, 18.253 seconds including test
compilation. The corrected test checks at least 20 moving-camera frames.

Both 300-frame captures completed; all CSV rows are identical before/after,
including position, speed, camera, animation, mode and input. Subsequent camera
movement reaches seven pixels per frame. Frames 80 and 120 were visually
inspected: terrain shifts into the same presentation generation as the sprites.
The comparison is `mhz-camera-before-after.mp4` in the external task directory,
with one-second endpoint holds. These captures do not establish native pixel
parity. The broad run and delivery are still pending.
The change-based plan selects all 2,589 classes and separate guards because this
changes shared render timing. Java 21, Lua 5.4 and PowerShell preflight passed.
Broad validation follows focused fixes; no source/HEAD changes during the run.

The queued domain run used `-Dmse=off -Ptrace-replay-r7
-Dtest=TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestFbzRetainedPlaneNativeRows,TestS3kFbzCompleteRunTraceReplay test -B`
with all three absolute ROM properties. It passed **61 tests, no failures/errors/
skips**, in 36.804 seconds. The full FBZ strict replay passed (16.42 seconds),
so this presentation correction preserves that gameplay frontier.
