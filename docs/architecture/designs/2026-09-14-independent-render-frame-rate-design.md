# Independent rendering rate and fixed simulation timing

Date: 2026-09-14
Status: proposed for 0.7; scheduler proof, live phase extraction and ordered input admission, no independent live scheduling.
Source inspection: `develop` at `71603cbb66f0ccbf78eb96cf52da25c6997cfd57`.
Roadmap: [0.7 supporting workstream](../../project/v0.7-roadmap.md#supporting-workstream--independent-rendering-rate).

## Problem and intended behavior

Rendering performance and the FPS setting currently affect gameplay speed.
Keep simulation at the selected region's cadence while allowing lower, higher,
and irregular rendering rates. Preserve PAL behavior rather than making PAL
play like NTSC: the intended outer simulation cadence is 50 Hz for PAL and
60 Hz for NTSC. Existing ROM loop admission and hardware-timing semantics
continue to decide what happens inside each outer tick.

At 30 rendered FPS, NTSC normally executes two ticks per picture. At 144 FPS,
some pictures occur without a new tick. Over equal elapsed time, identical
per-tick inputs must produce identical authoritative state regardless of the
render schedule, provided the machine can sustain the simulation workload.
Optional presentation interpolation makes motion smoother above the tick rate.

## Observed starting point

[`Engine.loop()`](../../../src/main/java/com/openggf/Engine.java) accumulates
elapsed time but invokes one combined update/render/swap at most per iteration
and clamps outstanding time to one interval. `displayFrame()` also handles
input routing, fades, audio presentation and live capture. Separating only the
swap or changing the accumulator condition is insufficient.

[`FrameRateResolver.effective()`](../../../src/main/java/com/openggf/configuration/FrameRateResolver.java)
returns 50 for PAL and otherwise uses the FPS setting. Both Engine and
[`AudioManager`](../../../src/main/java/com/openggf/audio/AudioManager.java)
consume it. The configuration catalog explicitly describes FPS as changing
game speed. VSync is enabled in Engine. These are inspected implementation
facts, not a measured performance or visual baseline.

## Clock and ownership design

Introduce a small scheduler outside Engine that accepts monotonic elapsed time,
a region tick rate and a render deadline. It must be testable with a synthetic
clock. Engine coordinates it with existing simulation and rendering owners;
keep scheduling arithmetic and presentation history out of gameplay managers.
Split the shared frame-rate resolver into explicitly named simulation and
presentation decisions, auditing every consumer including standalone audio.

Use integer or rational accumulation with retained fractional remainder so
rounding a 60 Hz interval does not create cumulative drift. Poll window events
and collect input before executing due ticks. Execute due ticks, publish their
presentation states, then render at most once for the current presentation
deadline. Sleep against an absolute next deadline after accounting for work
and swap time; VSync may limit presentation but cannot define simulation time.
Do not render all missed pictures during catch-up.

| Owner | Clock and responsibility |
|---|---|
| Gameplay, ROM counters, object updates | Existing simulation tick and internal ROM admission rules |
| Input | Collect host events promptly; consume queued edges once at a simulation boundary |
| Audio driver/sequencer | Existing emulated service timing, independent of render calls |
| Audio device | Continuous sample consumption; presentation repeats cannot duplicate production |
| Gameplay fades, menus and timed transitions | Explicit update owner at simulation cadence; rendering reads state |
| Cosmetic overlays and shader effects | Explicit presentation time where appropriate; never advance gameplay |
| Replay, recording and rewind | Existing authoritative tick/state boundaries |
| Video capture | Explicit output timestamps/cadence; no extra simulation to obtain a picture |

Audit draw paths for state mutation before allowing repeated renders or skipped
pictures. Preserve render-thread resource installation and queued art work;
separating clocks must not move GL calls to another thread or silently defer
work that a production tick requires. A mod callback abort must terminate the
affected tick/batch and invalidate presentation history before the existing
return-to-title path; rendering cannot continue with a partially updated scene.

## Input, pause and time discontinuities

Keep held state and ordered press/release edges separate. A host event must not
be replayed for every catch-up tick or lost on a render-only iteration. A short
press released before the next tick must still reach its intended input owner.
Define event-to-tick admission explicitly; live input cannot reconstruct what
a player would have pressed during a blocked event loop. Determinism tests use
the same tick-indexed input stream, not an assumed equivalence of host polling.
Modal UI input ownership and recorded playback retain their existing contracts.

User pause and focus/minimize pause do not accrue catch-up debt. Frame-step
executes exactly the existing single-step operation and presents its current
state without blending from the prior step. Reset scheduler anchors and
presentation history on resume, region/session changes and clock discontinuity.
Rewind and restore invalidate history; they do not synthesize forward ticks.
Existing deliberate fast-forward modes need explicit rate control separate from
the FPS setting, preserving their current recording and audio policies.

## Optional interpolation

After each completed tick, retain previous/current render-only snapshots of
positions and camera state. With remainder fraction `alpha` in [0, 1), draw
`previous + alpha * (current - previous)`. Never overwrite native positions,
collision coordinates, object state or rewind snapshots with this result.
Keep stable render identities with generation information so reused object
slots cannot blend unrelated objects. Bound snapshot storage to the live scene.

Interpolate camera and world motion together, deriving screen coordinates from
the same fraction. Include players, sidekicks, moving platforms and supported
object children. Attachments need a consistent parent/local transform policy.
Interpolate continuous background offsets only where the scroll owner can
provide a valid presentation contract; handle wrap seams explicitly. Animation
frames, palette writes, tile edits, visibility and other discrete state initially
use the current tick. Do not crossfade full screen images or invent intermediate
collision/animation states.

Reset or snap on teleport, respawn, scene/act load, camera snap, object creation
or removal, restore, and unsupported discontinuities. New objects appear at their
current position; deleted objects are not retained as interpolated ghosts.
Special stages and custom renderers require individual support decisions and
visual evidence. Unsupported renderers present discrete state at an independent
rendering rate; document that limit rather than claiming universal smoothness.

Interpolation adds up to approximately one tick of presentation delay relative
to the latest simulation state (16.7 ms NTSC, 20 ms PAL). Proposed default: off,
with an explicit opt-in setting. Turning it off presents the latest state while
keeping simulation speed independent. No extrapolation is proposed because it
can visibly predict motion through collisions and needs correction afterward.

## Configuration and capture

Keep `display.fps` as the rendering limit, default 60; PAL simulation no longer
forces that rendering limit to 50. Introduce a clearly named optional interpolation
setting when implemented. Audit existing FPS uses for intentional speed control
and explain the changed meaning in settings help, CONFIGURATION.md and the 0.7
changelog. This design does not change configuration files or runtime defaults.

Keep capture output cadence distinct from region cadence and display cadence.
Live capture timestamps and audio duration must agree under skipped/repeated
pictures. Offline trace capture remains driven by authoritative replay ticks
and its explicit output settings, never host wall time. Preserve exact-state
capture paths for diagnostic comparisons; interpolation must not enter trace
comparison data or authoritative recording state.

## Overload policy

Catch up occasional slow frames by executing additional ticks and omitting
intermediate renders. Bound work per outer iteration and continue polling events
between batches. Retain short-term debt; after a documented long-stall threshold,
rebase wall-clock debt without skipping authoritative tick identities. This means
simulation can lag real time under sustained overload. Expose tick rate, render
rate, debt and discarded wall time in performance diagnostics. Final batch and
stall limits need measured responsiveness evidence during implementation; they
are scheduler settings, never fitted physics constants or fixture conditions.

No design can maintain real-time gameplay when simulation alone cannot execute
50/60 ticks per second. Interpolation does not solve that workload. Tests must
cover both successful catch-up and the explicit degraded policy.

## Desktop windowed variable refresh rate qualification

Added to the proposed 0.7 scope following the September 14 VRR discussion.
VRR (G-SYNC, FreeSync and Adaptive-Sync) lets the display follow presentation
cadence; interpolation supplies intermediate motion. Neither changes the fixed
region simulation clock. Qualify the existing GLFW/OpenGL path first, starting
with Windows, and assess Linux separately by display server and compositor.
Ordinary decorated desktop windows and borderless fullscreen are separate cases:
a pass in one does not establish support in the other.

The inspected Engine uses OpenGL 4.1 and `glfwSwapInterval(1)`. That requests
VSync, not proof of active VRR. GLFW documents that driver settings can override
swap behavior. Keep render cap, swap/VSync policy and interpolation as separate
presentation decisions. Do not label a negative swap interval (adaptive VSync)
as Adaptive-Sync/VRR or introduce a universal “enable VRR” switch that cannot
control the OS/driver/display path. See the
[GLFW buffer swap contract](https://www.glfw.org/docs/latest/window_guide.html#buffer_swap).

### Vendor evidence and unresolved coverage

The following documentation was consulted on 2026-09-14. It is guidance for
qualification, not evidence that OpenGGF has passed on any vendor.

| Vendor on Windows | Documented path | OpenGGF qualification obligation |
|---|---|---|
| NVIDIA | Control Panel exposes G-SYNC for windowed and fullscreen applications | Verify the current OpenGL decorated-window path, borderless mode, driver profile and actual display engagement |
| AMD Radeon | Adrenalin exposes global and per-application FreeSync controls | Verify the actual OpenGL windowed path; the settings documentation alone does not establish it |
| Intel | Adaptive-Sync guidance describes DXGI; the Arc guide also specifies Vulkan presentation modes | Verify OpenGL separately; documented support in another API cannot qualify this renderer |

Sources: [NVIDIA windowed G-SYNC setup](https://www.nvidia.com/content/Control-Panel-Help/vLatest/en-gb/mergedProjects/nvdspENG/To_use_variable_refresh_rates.htm),
[AMD FreeSync setup](https://www.amd.com/en/resources/support-articles/faqs/DH3-013.html),
and [Intel Arc API guide](https://www.intel.com/content/www/us/en/developer/articles/guide/arc-a-series-gaming-api-developer-optimization.html).

Windows 11's [optimizations for windowed games](https://support.microsoft.com/en-us/windows/hardware/display-graphics/optimizations-for-windowed-games-in-windows-11)
cover DirectX 10/11; do not assume this setting upgrades OpenGGF's OpenGL
presentation. If measurements show an unresolved backend limitation, evaluate
a separate presentation-backend proposal. Microsoft's
[DXGI windowed VRR contract](https://learn.microsoft.com/en-us/windows/win32/direct3ddxgi/variable-refresh-rate-displays)
provides a documented flip-model path with feature-checked tearing flags. This
is a possible investigation, not a selected renderer migration or prerequisite
for independent simulation timing.

### Experiment and acceptance matrix

Record GPU, driver, OS build, renderer/backend, GLFW version, display model,
connection, configured refresh rate, VRR range, window mode and relevant driver
settings for every result. Include JVM/native launch identity because application
profiles may differ. Linux results also name X11/Wayland, compositor/version and
any compositor VRR policy; do not transfer Windows or one compositor's results
to all Linux desktops. Other platforms remain unqualified by this workstream.

For each available NVIDIA, Radeon and Intel system:

- Test decorated windows and borderless fullscreen at 50/60 FPS, higher rates,
  irregular delivery and near/beyond both ends of the display's VRR range.
  Compare interpolation off/on and VRR disabled/enabled with explicit VSync and
  cap settings. Test a cap below the ceiling; choose headroom from pacing evidence
  rather than prescribing one vendor-independent magic offset.
- Verify focus/Alt-Tab, minimize/restore, resizing, overlays, partial occlusion,
  movement between displays and mixed-refresh multi-monitor setups. On hybrid
  systems record which GPU renders and which drives the display.
- Measure frame intervals, swap blocking and authoritative tick counts. Correlate
  presentation measurements with a vendor VRR indicator or the monitor's live
  refresh readout where available. A steady FPS counter, a configured VRR toggle,
  or a reported maximum refresh rate does not prove active panel VRR.
- Exercise low-framerate compensation where supported and record behavior below
  the minimum range. LFC may repeat frames; it does not create intermediate motion.
  See [AMD's LFC description](https://www.amd.com/en/products/graphics/technologies/freesync.html).
- Verify ordinary fixed-refresh/VSync fallback, non-VRR displays and loss of VRR
  eligibility. Gameplay speed, input, audio and recording must remain correct.

Diagnostics distinguish requested presentation policy, measured frame pacing and
verified VRR engagement. When reliable engagement telemetry is unavailable, show
unknown rather than infer “active” from GPU branding. External indicators and
manual observations belong in the qualification record with their limitations.
Do not change global driver or desktop settings automatically.

Deliver a vendor/platform/window-mode matrix with pass, fail or untested status
and evidence. Advertise only measured combinations. Missing hardware remains
untested; it is neither a pass nor proof of unsupported functionality. Scope any
backend follow-up from observed failures, retain VSync fallback, and record the
0.7 release disposition of unresolved cases. No hardware tests have been run for
this design update.

## Delivery sequence and acceptance

1. Audit timing consumers and stateful render paths. Extract the scheduler and
   update/presentation boundaries with interpolation disabled. Prove region speed,
   input edges, audio, pause, abort and capture behavior under multiple schedules.
2. Add optional presentation history and interpolation for ordinary gameplay,
   camera and supported backgrounds. Qualify object identity, discontinuities,
   attachments and special/custom renderer fallbacks before advertising support.
3. Qualify desktop windowed VRR on NVIDIA, Radeon and Intel using the matrix
   above. Preserve fixed-refresh fallback and document untested combinations;
   evaluate backend changes separately only for demonstrated limitations.
4. Update implemented configuration/help/release prose and attach the supported
   renderer/platform evidence to the 0.7 qualification record.

| Scenario | Required evidence |
|---|---|
| NTSC/PAL at 30, 50, 60, 120, 144 FPS and jitter | Synthetic-clock tick count/remainder tests; equal per-tick state for identical tick-indexed inputs |
| Slow frames and long stalls | Catch-up, bounded work, debt retention/rebase and event responsiveness tests |
| Input and modal ownership | Press/release between ticks, held input across catch-up, render-only iterations, playback and frame-step |
| Audio and transitions | No duplicated/missing audio production; duration/sequence checks through pause, fades, menus and stage changes |
| Interpolation | Pure snapshot math/identity checks plus real camera, platform, child, wrap and discontinuity captures |
| Restore and recording | Capture/restore and forward replay unchanged; render frequency does not alter hashes or tick numbering |
| Capture | Output timestamps, frame cadence and A/V duration under slower/faster display schedules |
| Platform pacing | JVM/native and VSync behavior on tested displays; actual limits recorded |
| Windowed VRR | Vendor/OS/window-mode matrix, panel engagement evidence, range boundaries, multi-monitor/focus behavior and fixed-refresh fallback |

This is a shared timing change: focused tests alone cannot qualify implementation.
Use repository change-based validation with the actual integration base, structural
guards, affected ROM trace fixtures and required S3K regression checks. Inspect
skips and compare failures by identity. Existing full campaign and route gates
remain required; interpolation screenshots do not establish gameplay parity.
The original proposal was documentation-only. The isolated proof below uses
focused checks; live integration still requires the coverage described above.

## Alternatives and decision record

The September 14 discussion selected fixed region ticks plus optional presentation
interpolation. Variable-delta physics was rejected because ROM counters, integer
movement and collision order are frame-defined. Raising simulation rate to match
the display repeats the existing speed problem. Keeping only the existing frame
limiter cannot recover missing simulation time, as shown by the inspected clamp.
Fixed ticks with discrete rendering are the first delivery stage and the fallback;
interpolation adds smoothness and a latency trade-off, not simulation accuracy.
No live timing integration, benchmarks or platform qualification have been
performed. The isolated scheduler proof below does not qualify engine behavior.

## Isolated scheduling proof — 2026-10-29

Base: `develop` at `c4cd6ae0e8cdaf00bdf013c3c740586039c10ef8`.
Package-private `FixedSimulationScheduler` has no production callers.
`TestFixedSimulationScheduler` checks real scheduling arithmetic and a stateful
integer recurrence with tick-indexed inputs. It does not establish actual gameplay
parity, host-input equivalence, audio duration, captured pixels or draw purity.

Candidate policy:

- Accumulate elapsed nanoseconds multiplied by PAL50/NTSC60 Hz; each tick consumes
  one billion units. Retain fractional phase and short-term debt. Bound each batch
  and cap outstanding debt using caller-selected limits; report discarded whole
  wall nanoseconds. Fractional debt survives clipping, with less than one
  nanosecond of unused cap headroom. Limits have no runtime defaults and need
  responsiveness measurements. Overload slows simulation without advancing
  authoritative tick identities for discarded time.
- Coalesce missed pictures into one opportunity, keeping independent rational
  presentation phase. Zero-time backlog drains request no extra picture. The
  relative next-iteration deadline starts at the elapsed-time observation;
  subtract subsequent work/swap time before sleeping against an absolute deadline.
- Batches reserve allowances, not completed gameplay ticks. Stop on abort or
  boundary change; reset phases, the caller's wall anchor and presentation history
  on pause/resume, session changes, rewind/restore or discontinuity. Reject negative
  elapsed time without mutation; recreate for region/rate changes. Exclude paused
  wall time. Frame-step and its forced discrete picture stay caller-owned.

Tests cover both regions at 30/50/60/120/144 FPS, identical per-tick state sequences,
render-only/multi-tick iterations, jitter, short-stall catch-up, long stalls,
sustained overload, reset, fractional deadlines, an hour of synthetic time and
large elapsed inputs. No sleeping, windows, ROMs or devices are involved.
Focused command:
`JAVA_HOME=/usr/lib/jvm/java-25-openjdk python3 tools/testing/maven_queue.py --normal -Dmse=off -Dtest=TestFixedSimulationScheduler test`.
Result: 23 cases passed, zero failures, errors or skips, on Java 25.
The lean attempt exhausted javac's 1 GiB heap compiling repository test sources,
before execution; it supplies no behavioral result.

### Integration dependencies and owner decisions

| Inspected owner | Remaining decision/evidence |
|---|---|
| Engine `loop`, `displayFrame`, `waitForFrameDeadline`, mod-abort boundary | Split update/draw/swap; poll between bounded batches; account for work/VSync; choose measured limits and expose debt/discard diagnostics. Abort stops the batch and invalidates history. |
| `FrameRateResolver.effective`, `GameLoop.stepPresentationFrame`, `GameLoopGameplayPacing`, `step`, `LevelIterationAdmissionController` | Separate region/display/audio rate decisions. Select the semantic operation per allowance, preserving internal ROM admission, mutator zero/multi-step pacing and trace/recording fast-forward. |
| `InputHandler.update` / `capturePhysicalInput`, `GameLoopPauseInput`, `GameLoopZeroStepPresentation` | `update` clears pending scene events and advances key baselines. Define ordered short press/release admission, held input across catch-up, modal claims and playback ownership. Service pause/step controls independently of display FPS while excluding paused simulation time. |
| Engine fades, draw dispatch, title-card `ensureArtCached`, provider/mod draws | Fade updates currently mutate state inside `displayFrame`. Audit delegated draw mutation; retain render-thread resource installation. Verify repeated/skipped draws against real state and GL output. This inspection is not a complete draw audit. |
| `OuterFramePresentation`, `AudioManager.presentFrame`, `AudioPresentationProducer.present` / `AudioFrameClock` | PCM packets/history and capture leases currently advance per outer presentation. Select an independent service/sample clock; preserve command, silence/frame-step/reverse boundaries and verify duration/sequence. Extra display pictures cannot duplicate production. |
| `LiveCapturePresentationCoordinator`, `LiveCaptureController.capturePresentedFrame` | Keep post-FINAL capture/screenshot/indicator ordering. Define output timestamps and repeat/drop cadence against audio duration; retain offline trace capture's tick driver. |
| `LevelIterationAdmissionController`, user recording controls, live rewind/restore | Keep rows/keyframes on authoritative boundaries. Restore resets wall debt/history without forward ticks; select reverse-seek service cadence. Verify actual capture/restore and forward replay. |

The arithmetic proof is a candidate for integration. Live wiring requires the
owner decisions and behavioral evidence above. GameLoop/Engine, configuration,
public Mod API and release behavior remain unchanged. Interpolation and VRR
qualification remain later work; the rejected alternatives above still apply.

## Input admission investigation — 2026-10-29

Inspected source: `c4cd6ae0e8cdaf00bdf013c3c740586039c10ef8`, isolated branch
`feature/ai-input-admission-contract`. This investigation adds characterization
tests and the required input boundary below; it changes no runtime behavior and
does not implement or qualify independent rendering. The September observations
above retain their original source identity.

### Existing boundaries and owners

- [`InputHandler`](../../../src/main/java/com/openggf/control/InputHandler.java)
  callbacks collect keyboard held state and ordered scene transitions.
  `pollPhysicalGamepads()` observes physical state without advancing logical
  button history. `capturePhysicalInput()` drains the scene event queue, while
  leaving raw keyboard edges and logical button history available. Its sequence
  numbers preserve observation order when timestamps tie. Gamepad timestamps
  remain observation times, not hardware times.
- `refreshLogicalSnapshot()` is a mutation boundary. Keyboard presses are
  derived from `keys` versus `previousKeys`; gamepad presses are derived and
  acknowledged inside
  [`GamepadInputManager.poll()`](../../../src/main/java/com/openggf/control/GamepadInputManager.java).
  Two refreshes before `update()` repeat keyboard edges but consume pad edges.
  `logical()` only reads the cached sample; `update()` does not clear that sample.
  A keyboard or observed pad tap released before any logical refresh survives in
  physical scene events but is absent from the fresh logical sample.
- `update()` acknowledges raw keyboard/mouse edges, advances menu repeat/claim
  lifetime, expires text ownership and discards unconsumed scene events and typed
  text. Moving it to every render discards input; deferring it across multiple
  UI passes repeats shortcuts and changes text-lease lifetime.
- [`GameLoopPauseInput.resolveOwnership()`](../../../src/main/java/com/openggf/GameLoopPauseInput.java)
  publishes recorded input before the native Start consumer, then short-circuits
  through the configuration overlay and hosted-run Start owner. Title Escape
  ownership and mode changes are consulted afterward. `nextUserPaused()` keeps
  physical pause available during recording while recorded Start stays with its
  native consumer. Retain this order and the existing takeover/frame-step gates.
- [`GameLoopZeroStepPresentation`](../../../src/main/java/com/openggf/GameLoopZeroStepPresentation.java)
  refreshes logical input, handles overlay/configuration, Escape, recording
  controls and pause, then retains eligible player edges and calls `update()` in
  `finally`. This is an intentional slow-motion host pass, not an empty render.
  [`GameplayMutatorPacing`](../../../src/main/java/com/openggf/game/mutators/GameplayMutatorPacing.java)
  accumulates player edges, keeps latest held state, excludes P1 Start from its
  pending carry and owns rewindable pending state. The one-refresh
  [`RetainedGameplayInput`](../../../src/main/java/com/openggf/control/RetainedGameplayInput.java)
  handoff combines only player edges with fresh held state. It leaves the fresh
  snapshot's menu/debug flags alone. It does not provide an ordered event queue
  or recover taps that no logical refresh observed.
- [`MenuInput`](../../../src/main/java/com/openggf/control/MenuInput.java) keeps
  fixed keyboard navigation, mapped pad policy, back-before-accept and repeat
  timing. Its mapped accept path also reads player action edges; preserving
  snapshot menu flags alone does not make retained input safe for arbitrary menus.
  [`ModSceneHost.Context.beginTick()`](../../../src/main/java/com/openggf/mods/scene/host/ModSceneHost.java)
  prepares text ownership, drains physical events/text and derives scene policy
  from the logical sample. Menu/scene controllers currently own their terminating
  `update()` calls. Render-only work must not invoke these update paths.
- [`TextInputSession`](../../../src/main/java/com/openggf/control/TextInputSession.java)
  filters composition keys before publication, can retract unpublished presses
  and requires native drain acknowledgment. The legacy text claim also hides
  printable/editing keys from keyboard mappings while leaving captured-key
  access to its owner. A new queue cannot copy events ahead of those filters or
  revive keys after a text owner has consumed them.
- [`PlaybackInputBridge`](../../../src/main/java/com/openggf/debug/playback/PlaybackInputBridge.java)
  owns publication and identity-checked release of its override; publishing an
  override discards retained live edges.
  [`RecordedInputOwnership`](../../../src/main/java/com/openggf/control/RecordedInputOwnership.java)
  supplies pause ownership, not input or cursor advancement.
  [`UserRecordingRuntimeControls`](../../../src/main/java/com/openggf/game/recording/UserRecordingRuntimeControls.java)
  separates host recording controls from before/after admitted level-frame
  callbacks. Observation or rendering cannot advance the movie, record a row,
  duplicate its hold counter, or replace the existing trace/native admission.

### Required host-internal input interface

The scheduler needs the following semantic boundary before it can execute zero
or multiple ticks per picture. These operations are requirements, not new public
API or uncalled runtime scaffolding:

| Operation | Required admission contract |
|---|---|
| Observe host input | Existing callbacks and physical pad polls on the window thread. Keep latest held state and ordered transitions after existing text/chrome filters. No logical acknowledgment, text-lease expiry, scene drain or movie advancement. |
| Begin an owned input pass | Freeze one stable sample for its existing host/menu/scene or simulation owner. Repeated reads return the same edges for keyboard and pad. Retain raw shortcut/modifier queries separately from logical player/menu policy; a `LogicalInputSnapshot` alone cannot supply this interface. |
| Route and acknowledge | Existing consumers decide claims in their existing order. A host-only pass may acknowledge UI edges and retain eligible unconsumed gameplay edges; a simulation admission acknowledges those gameplay edges once. A render-only pass performs neither operation. Scene capture remains a distinct single-consumer drain. |
| Finish or invalidate the pass | Finish exactly once, including current early-return paths; advance the applicable menu/text lifetime once. On pause, takeover, reset, focus/session changes or rewind, defer to the existing owner for carry/discard/rebaseline. Never introduce a second independently restored pending-input owner. |

For live catch-up, admit all currently observed eligible transitions to the next
owned boundary in arrival order; later ticks receive current held state with no
repeated acknowledged edge. Events observed between batches belong to the next
boundary after observation. Do not backdate input into already executed ticks.
This is the proposed live policy; tick-indexed recorded input keeps its existing
cursor, lag, native Start and pending-action policy. Multiple transitions for
the same key must remain distinguishable until its semantic owner reduces them;
OR-ed action masks cannot establish event order or reconstruct multiple taps.

### Blocker and rejected incremental changes

No standalone production input change is selected. Splitting `update()` alone
cannot identify which of its current early-return owners consumed an edge.
Adding refreshes for rendering consumes pads asymmetrically. Reusing
`capturePhysicalInput()` for gameplay steals scene events and ignores legacy
keyboard claims and logical replay authority. Reusing slow-motion retention as
the global queue loses event order and changes Start/menu ownership. A generic
all-key latch would publish text/modal shortcuts to gameplay without an owner
acknowledgment contract.

The integration owner must first coordinate the host input-pass boundary with
`Engine.displayFrame()`, the GameLoop early returns, menu/scene controllers and
recorded publication/admission. Select the precise UI-pass cadence and claim
acknowledgment points there; keep rendering read-only and keep audio, capture
and rewind with their existing owners. Those edits are outside this leaf's
input-only scope. Acceptance then needs the design's cross-schedule checks,
including frame-step, short taps, simultaneous keys, UI/text claims and recorded
Start; this focused investigation cannot certify those system changes.

The ROM-free
[`TestInputAdmissionBoundaries`](../../../src/test/java/com/openggf/control/TestInputAdmissionBoundaries.java)
witnesses physical observation versus acknowledgment, keyboard/pad refresh
asymmetry, released-tap loss in logical mapping, ordered scene delivery and
tick-end discard under a replay override. Assertions describing current loss or
asymmetry are migration witnesses, not desired future behavior: replace them
with the selected admission guarantee when that owner changes.

Validation on the inspected base plus the new test source, in
`/mnt/LinuxData/T3/worktrees/OpenGGF/t3-2270c04d`:

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk python3 tools/testing/maven_queue.py \
  -Dmse=off \
  '-Dtest=TestInputAdmissionBoundaries,TestRetainedGameplayInput,TestScenePhysicalInput,TestKeyboardTextCapture,TestTextInputSession,TestGameLoopPauseInputOwnership,TestRecordedInputHostPause,TestPlaybackInputBridgeOwnership,TestGameLoopMutatorPacing' \
  test -B
```

Completed successfully on 2026-10-29: nine classes, 68 tests, zero failures,
errors or skips; all six new witnesses executed. The build's mandatory JDK
check passed with `JAVA_HOME` pointing to Java 25.0.5.0.2. Queue wait was
362 seconds; Maven execution including clean compilation was 1m36s. The tests
are ROM-free; mocked pacing worlds report the existing FAST-without-manifest
fallback to NONE. They establish input-boundary behavior, not PAL/NTSC route,
audio duration, capture, rewind integration or independent-render qualification.
No broad run was launched: the inspected change-based plan selected 3,120
classes, disproportionate to documentation and these characterization tests.
Local investigation links, diff whitespace and commit-policy checks also passed.

## Scoped input-pass prerequisite — 2026-10-29

Source base: pushed `develop` at `2e35b5814`, in the T3-provisioned checkout
`/mnt/LinuxData/T3/worktrees/OpenGGF/t3-5fa74c10`, branch `fixed-pal-ntsc-input`.
The checkout fast-forwarded from `c4cd6ae0e` without conflicts. The separate live
advance extraction at `f96eb1785e355ae3d7e478621a42cf3fe52294fb` was read, including
its live-extraction decision record. This prerequisite does not depend on that
commit and does not import its older view of unrelated upstream changes. The
lead's extraction worktree was neither changed nor used for validation.

### Implemented owner boundary

`InputHandlerInternalAccess.openInputPass()` scopes each existing canonical
`GameLoop` iteration, including each deliberate recording/trace pump iteration,
and the separate `GameLoopZeroStepPresentation` host pass. It is host-internal,
not a published creator API. Event collection completes on the window thread
before these run-to-completion consumers start. No new event poll, authoritative
tick, movie-row admission or render call is inserted.

Within that scope, logical rereads reuse the first mapped gamepad sample instead
of polling and consuming another controller edge. Keyboard queries still use the
existing text/chrome-filtered state. Keyboard claims and composition suppression
are reevaluated on each logical refresh: a claim taken after the first refresh
can still hide its keyboard contribution without losing the independent pad
sample. Shortcut substitutions used by keyboard mapping retain their first
mapping baseline; host shortcuts still read the current pad sample. This avoids
creating a new mapped action on a reread when a keyboard action aliases a
frame-step/debug/rewind key. Device prompt tracking occurs once per sample.

The consumer's existing `InputHandler.update()` acknowledges the scoped pass
once, advances menu/text lifetime once, and retains its existing scene/text
retirement behavior. Scope close does not manufacture an acknowledgment on a
setup deferral or abort. An unacknowledged scope restores only its prior logical
pad baseline and returns any sampled carry to the existing one-refresh bridge.
This is transient input-consumer state, not another rewind adapter or pending
world-input owner. Physical observation, scene event sequence and prompt history
are not rolled back. The native menu controller's existing `finally` still
acknowledges input even when its menu throws.

Recorded publication still replaces the logical sample immediately and discards
live carry; identity-checked playback release still owns cleanup. Physical pause
and frame-step stay separate from recorded native Start. Slow-motion pending
edges and their rewind snapshot remain exclusively in `GameplayMutatorPacing`;
repeated reads merely retain the already selected bridge sample for that pass.
Outside an explicit scope, public/tool refresh and update semantics are unchanged.
The existing ordinary one-refresh/one-update path keeps its order and cadence.

### Deliberately unresolved admission

This is the behavior-preserving prerequisite option, not end-to-end input
admission for a fixed scheduler. The ordered short-tap gameplay requirement
remains unimplemented. `pollPhysicalGamepads()` and keyboard callbacks still
preserve observed ordered transitions for the physical scene consumer, but a
tap released before logical refresh remains absent from gameplay mapping. The
existing `TestInputAdmissionBoundaries` loss/asymmetry witnesses still describe
unscoped behavior; the new scoped tests establish only the owner boundary above.

The precise remaining dependency is a coordinated choice of host UI service and
simulation-admission cadence. In the inspected live extraction,
`Engine.advanceFrame()` routes host display controls before
`GameLoop.stepPresentationFrame()`, which may execute a zero-step host pass or
several complete native/replay passes. Menus/scenes, composition/text claims,
configuration overlays, pause and rewind can acknowledge or defer input in
those passes. There is no existing result saying which ordered transitions a
host-only pass consumed versus retained for a later simulation boundary.
Wrapping the entire Engine advance in one scope would incorrectly coalesce the
already independent pump acknowledgments. Calling it for every scheduler
allowance would select UI, pacing, audio and capture cadence without resolving
those owners.

The next implementation must choose those service boundaries and route an
ordered filtered journal through the actual consuming owners. Render-only
iterations must perform observation only; host-only service must identify its
claims; simulation admission must acknowledge only eligible remaining input.
It must invalidate or rebaseline at pause/focus, takeover/session and
rewind/restore boundaries using existing owners. Copying raw keys before text
filters, OR-ing all events into a gameplay latch, draining scene input for
simulation, or adding another rewindable pending-input store is still rejected.
No FPS meaning, region cadence, audio/capture duration, external trace timing,
interpolation, VRR or public API publication changes in this slice.

### Focused prerequisite validation

Tested source: `2e35b5814db853c592345464ef15e6bb27f0d862` plus the staged
main/test sources in this local prerequisite commit. Main/test sources did not
change after the first job was admitted; only this validation prose was finished
after execution. Both runs used Java 25.0.5.0.2 with the build's mandatory Java 25
enforcer passing; the selected Lua binary is Lua 5.4.9.

```bash
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
  LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py --normal -Dmse=off \
  '-Dtest=TestOwnedInputPass,TestGameLoopZeroStepInputPass,TestGameLoopNativeMenus,TestInputAdmissionBoundaries,TestInputHandlerLogicalSnapshot,TestInputHandlerGamepadSuppression,TestKeyboardTextCapture,TestTextInputSession,TestScenePhysicalInput,TestRetainedGameplayInput,TestGameLoopMutatorPacing,TestGameLoopPauseInputOwnership,TestRecordedInputHostPause,TestPlaybackInputBridgeOwnership,TestLiveRewindLogicalInput,TestLiveRewindInputSourceStartHeld,TestGameLoopRewindBoundaryPolicy,TestRecordedInputNativePause' \
  test -B
```

Completed at 20:56:37 UTC on 2026-10-29: 18 classes, 126 cases, 125 passed,
zero assertion failures, one startup error, zero skips. The error was
`TestRecordedInputNativePause#recordedStartPausesNativeGameplayOncePerEdgeAndUnpausesAtTheNextEdge`:
`HeadlessGameBoot.initGl()` reported `Unable to initialize GLFW` while constructing
`GameplayCaptureSession`, before boot/gameplay. This command mistakenly included
a rendered test without the EGL backend; with both display variables removed,
it correctly failed instead of using the desktop. No source fix was needed.
Queue wait was 568 seconds; Maven execution including main/test compilation was
1m35s. The other 125 cases passed, including all twelve scoped InputHandler
checks and the actual menu/zero-step witnesses.

The already queued prerequisite-corrected check used an explicit original S2
ROM and surfaceless EGL. The reproducible command below uses a neutral ROM-path
placeholder to comply with repository resource policy; the actual invocation
passed the verified absolute original file outside this checkout:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
  LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py --normal -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  -Dsonic2.rom.path=/absolute/original-s2-rev01.gen \
  '-Dtest=TestRecordedInputNativePause,TestModApiPinPolicy' test -B
```

Completed at 21:00:37 UTC: two classes, five cases passed, zero failures, errors
or skips. The native test performed production level entry, recorded Start
pause/hold/release/unpause, and the same-iteration gameplay admission on unpause.
The four API pin checks confirmed that the candidate signature surface and
version/pin policy are unchanged. Queue wait was 536 seconds; Maven execution
was 36.065 seconds. The original ROM was read in place, with CRC32 `7B905383`
and SHA-1 `8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9` verified before submission.
`GameplayCaptureSession` used its silent no-device boot. No desktop input,
window-focus automation, ROM copy or ROM link was used.

The 19 latest per-class XML summaries were inspected by identity: 130 distinct
cases passed with zero remaining failures, errors or skips across these two
runs, including the repaired native-start check. This is combined focused
evidence, not a single broad-suite result. Ordinary deprecation/JOML warnings
and the existing FAST-without-manifest fallback to NONE supplied no skip or
parity exemption. Overall task wall time was not separately measured; queue
wait intervals overlapped and must not be added as elapsed delivery time.

Diff whitespace, local design links, the repository pre-commit/commit-message
policy and the exact prepared message's parsed final Sol co-author were checked.
Consumed Maven diagnostics are removed after inspection. No broad smoke, release
full suite, trace campaign or cross-schedule qualification was launched. The
delivery lead owns combined smoke and system qualification; these focused
checks do not certify fixed PAL/NTSC simulation independently of rendering.

### Recorded-input release correction — 2026-10-29

Independent review found that a pad edge sampled under a recorded logical override
could reappear when the override released within the same scoped pass. Closing an
unacknowledged pass could also restore that hidden edge. On the original scoped
candidate `1d1a022c275930196c977cdb41a81011d21b3c5a`, two targeted cases each
failed with action-pressed `2` instead of `0` (zero errors or skips; queue wait
742s, Maven 32.868s). The correction removes those player edges from the cached
sample and preserves the observed player history on rollback, while retaining
held state and the separately owned physical and host-shortcut channels.

On that candidate plus the correction and its tests, the following Java 25
headless command completed **132 tests, zero failures, errors or skips**; Maven
execution was 1m32s. Queue wait is not retained here. The S2 path was the verified
original REV01 file in the main workspace, read in place.

```bash
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
  LUA_BIN=/usr/bin/lua5.4 ALSOFT_DRIVERS=null EGL_PLATFORM=surfaceless \
  python3 tools/testing/maven_queue.py --normal -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  "-Dsonic2.rom.path=$S2_ROM_PATH" \
  '-Dtest=TestOwnedInputPass,TestGameLoopZeroStepInputPass,TestGameLoopNativeMenus,TestInputAdmissionBoundaries,TestInputHandlerLogicalSnapshot,TestInputHandlerGamepadSuppression,TestKeyboardTextCapture,TestTextInputSession,TestScenePhysicalInput,TestRetainedGameplayInput,TestGameLoopMutatorPacing,TestGameLoopPauseInputOwnership,TestRecordedInputHostPause,TestPlaybackInputBridgeOwnership,TestLiveRewindLogicalInput,TestLiveRewindInputSourceStartHeld,TestGameLoopRewindBoundaryPolicy,TestRecordedInputNativePause,TestModApiPinPolicy' test -B
```

This is focused input evidence. It does not qualify ordered short taps or live
independent simulation scheduling, nor replace combined shared-owner smoke.


## Live advance/presentation extraction — 2026-10-29

Source base: `develop` at `ccc7b724ced5cd2b85a735089564b9c8c31674bd`.
The provisioned `fixed-simulation-timing` branch fast-forwarded from
`c4cd6ae0e8cdaf00bdf013c3c740586039c10ef8` to that base without conflicts.
This slice takes the behavior-preserving extraction option. It does not connect
`FixedSimulationScheduler` to the window loop or change the meaning of FPS.

`Engine.displayFrame()` now calls `advanceFrame()` and `renderAdvancedFrame()`
once each. The pre-update performance-overlay sample is passed to rendering.
GL preparation, suspension's between-frame boundary, profiler lifetime
and the existing mod-abort/swap boundary retain their original positions.
The advance phase owns detached UI fade progression, content import processing,
shader rescan, capture shortcut observation, display-input routing, the existing
`Engine.update()` / `GameLoop.stepPresentationFrame()` call and outer audio.
The modal branch still skips GameLoop and acknowledges input itself. All other
input acknowledgments remain with existing GameLoop/menu owners. The captured
`FrameAdvance` carries the pre-update UI pipeline reference and the modal and
frame-step decisions through rendering to live capture, after those raw edges
have been acknowledged. User pause and rewind capture state remain sampled in
the presentation phase, as before.

The extraction is on the live production path. `advanceFrame()` can be exercised
without GL with the real input, fade, GameLoop and audio owners. It is an outer
host frame, not a new authoritative tick API. The existing trace/recording pumps,
mutator pacing and internal ROM iteration admission still execute inside that
single call. No additional input refresh, tick, audio service, recording row or
rewind snapshot is inserted. The extracted render phase still calls delegated
stateful draw owners and suspension retirement. Repeating or omitting it is not
qualified by this change.

### Remaining implementation owners

- **Window scheduling:** `Engine.loop()` and `waitForFrameDeadline()` still use the
  combined target interval and clamp. Connect region/display decisions to the
  scheduler only after the following owners are ready; select measured batch/stall
  limits and diagnostics, and reset wall anchors at pause/session/restore boundaries.
- **Input admission:** Coordinate stable owned samples and ordered short taps with
  `InputHandler`, GameLoop early returns, modal/text consumers, native Start,
  recorded publication and slow-motion retention. A render-only iteration must
  neither refresh nor acknowledge input. Catch-up must not repeat acknowledged
  edges or expire text/menu leases per picture.
- **Semantic tick selection:** `GameLoop.stepPresentationFrame()` combines host
  controls, mutator pacing, policy publication and deliberate trace/recording
  pumps. It cannot simply be called once per scheduler allowance without a
  decision on each owner's cadence. Keep the ROM timing port/admission untouched.
- **Stateful presentation:** Audit provider/mod draws, title-card art installation,
  shader frame counters, UI notifications and suspension's `afterScene` capture.
  Render-thread tasks must remain ordered with the ticks that require their art.
- **Audio duration:** `OuterFramePresentation` and its producer/capture history
  still advance per outer host frame. Choose service/sample duration independently
  of picture count, including silence, reverse and deliberate fast-forward.
- **Capture and recording:** `LiveCapturePresentationCoordinator` still emits one
  capture at post-FINAL presentation, then screenshot, then the window indicator.
  Define timestamp/repeat/drop cadence together with audio; keep recording rows
  and offline trace capture on their existing authoritative boundaries.
- **Pause/restore/suspension:** Preserve actual one-step admission, focus pause,
  mod-abort retirement and rewind release/restore while rebasing scheduling debt.
  The extracted result retains presentation decisions only; it creates no
  separately restored or replay-authoritative state.

The existing integration-dependency table remains applicable. Fixed simulation
at 30/50/60/120/144 FPS, independent audio/capture duration, draw purity,
interpolation and VRR remain unqualified. Settings, region speed, public Mod API
and publication policy are unchanged. Combined change-based smoke and affected
ROM/trace qualification belong to the delivery lead; this leaf runs focused
checks only.


### Focused extraction validation

Tested source was `ccc7b724ced5cd2b85a735089564b9c8c31674bd` plus this
Engine extraction and its test source, on `fixed-simulation-timing` in
`/mnt/LinuxData/T3/worktrees/OpenGGF/t3-04dec82d`. No source/test edits followed
the successful run; only the validation prose was completed afterward.

```bash
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
  python3 tools/testing/maven_queue.py --normal -Dmse=off \
  '-Dtest=TestEngineFrameAdvance,TestEngineLiveCapturePresentation,TestEngineModCallbackAbortBoundary,TestGameLoopAudioPresentationModes,TestGameLoopPauseInputOwnership,TestInputAdmissionBoundaries,TestFixedSimulationScheduler,TestGameLoopMutatorPacing,TestGameLoopSuspensionOwnership' \
  test -B
```

Completed 2026-10-29 at 18:45:01 UTC: nine classes, 175 tests, zero failures,
errors or skips. The nine Surefire XML summaries were inspected, including all
ten cases in
[`TestEngineFrameAdvance`](../../../src/test/java/com/openggf/TestEngineFrameAdvance.java).
The build's Java 25 check passed; the selected JDK release is 25.0.5.0.2.
Queue wait was 488 seconds, and Maven execution including main/test compilation
was 1m29s. Overall delivery wall time was not separately measured.

The new witnesses drive the actual Engine advance method with real InputHandler,
GameLoop, detached FadeManager, shader-picker controller and no-device audio.
Only the GL resource owner is substituted. They cover single-use keyboard edges
and held samples over consecutive existing host frames, picker opening/closing,
text capture, retained frame-step presentation metadata, pause-dependent audio,
fade completion before the same update, reverse audio selection and typed mod
abort containment before audio/presentation/swap. The recording pump witness
performs nine real GameLoop updates with the existing fast-forward permission
and checks one six-sample outer capture packet. It tests existing packet count
and duration with silent no-device output, not ROM synthesis or cross-schedule
A/V synchronization. The frame-step witness tests metadata/acknowledgment; it
does not establish native paused gameplay admission.

The first queued attempt waited 538 seconds, then failed main compilation at
`Engine.java:3048`: the extracted render method lacked the existing
`performanceOverlayEnabled` local. Passing the original pre-update sample fixed
that error. It executed no tests. An earlier build-only `test-compile` exited 1
during javac without a diagnostic and supplies no verification result. The
version-only queue request was cancelled as redundant after the build's JDK
check; it supplies no standalone version-probe result. The successful command
above subsequently compiled both main and test sources and ran the full focused
selection. Existing deprecation/unchecked warnings, mocked FAST-without-manifest
fallbacks and adversarial suspension warnings remain diagnostic output, not
skipped cases.

Diff whitespace, design local links and the prepared commit's seven policy
trailers/final parsed co-author were checked. A one-time source comparison
confirmed the original advance/render bodies were retained verbatim apart from
the two captured flag reads; the existing pre-update overlay sample is passed
explicitly. These checks are bounded extraction evidence. No broad suite,
structural-guard campaign, ROM route/trace run, desktop automation or independent
rendering/capture qualification was performed in this leaf.


## Sequential live host-input handoff — 2026-10-29

This bounded functional increment starts from reviewed input candidate
`54af1dd768c2041146013261890d881251bcab6f` (develop `bd0fbcd72`, scoped
input `1d1a022c`, replay-release correction `b68776c9d`). It imports the
separately reviewed Engine extraction `f96eb1785e355ae3d7e478621a42cf3fe52294fb`
through local merge `505b4aea64e04105a5668ea5faef799a70cc1181`. Only the
changelog index and this design record conflicted; both prerequisite records
and the newer develop fixes were retained. The extraction worktree and its
running smoke/target remain read-only. This checkout is the T3-provisioned
`/mnt/LinuxData/T3/worktrees/OpenGGF/t3-c6ea2a08`, local branch
`feature/ai-host-simulation-admission`.

### Production behavior and owner boundary

`Engine.advanceFrame()` now samples logical input in an explicit host-control
pass before capture shortcuts, frame-step classification and display-picker
routing. Previously those controls read the preceding mapped controller sample;
a new pad step could reach GameLoop while the saved capture/audio decision missed
it, and a display-only modal could acknowledge raw keys without polling and
acknowledging its controller press. The host refresh fixes those concrete gaps.

A picker-owned frame acknowledges that host pass once and invokes no GameLoop
consumer. Otherwise `InputPass.handoff()` ends the host scope synchronously,
then runs the existing Engine update. Only the first canonical or zero-step
GameLoop pass adopts the cached pad sample and its prior mapping baseline.
The adoption does not poll again or acknowledge input. Existing `update()` at
the consuming owner still advances text/menu/scene lifetime exactly once;
subsequent deliberate fast-forward passes open and acknowledge independent
samples. No nested input scope, extra canonical tick, movie row, audio packet,
world snapshot or event poll is introduced.

Keyboard mapping is refreshed through the current text/chrome filters at the
consuming pass, including a claim taken after host sampling. Physical scene
input retains its independent drain and ordered press/release evidence. Recorded
publication/release still replaces logical gameplay input and strips hidden live
pad edges, including a publication before sample adoption. The scoped physical
menu sample remains separate from that stripped gameplay sample, so a recorded
override cannot acknowledge a physical text-menu press during rereads or handoff.
The actual input acknowledgment clears both scoped channels. The host pass never
takes or exposes the pending slow-motion player bridge; its existing pacing
owner and first eligible consumer retain that authority. An unused handoff is
retired in the synchronous call's finally block. A deferral/exception rolls back
only the existing unacknowledged logical baseline and carry, without pretending
that text or physical scene input was consumed.

### Proven scope and remaining prerequisite

The scheduler remains disconnected and FPS/region/audio/capture cadence is
unchanged. This is a live host-to-first-consumer handoff, not a new admission
schedule for arbitrary zero/multiple simulation ticks. Pause, native movie Start,
frame-step, reset and rewind keep their existing production owners. The handoff
can terminate at the current zero-step pacing host pass as well as a canonical
pass; it cannot choose a later tick for input by itself.

Ordered taps released before the first logical refresh still do not reach
ordinary gameplay. The characterization and adversarial scene-journal tests
retain that explicit limit: physical observation preserves their order, but the
scene drain is not simulation authority. The next input increment must define
claim-aware semantic transition admission at the actual host/modal/simulation
owners, with pause/focus, takeover/session and restore invalidation, rather than
copying raw keys or OR-ing another world-input latch. The existing slow-motion
carry is intentionally unchanged and does not become an ordered journal.

There are also concrete non-input blockers to live scheduling. The extracted
advance still updates non-gameplay fades and presents one outer audio packet;
`renderAdvancedFrame()` still calls delegated draws, services pending render-thread
work and produces live capture. Existing extraction tests pin one audio packet
across nine deliberate native passes, and show that the frame-step capture
decision survives input acknowledgment. They do not prove draw purity, audio
production/duration across independent schedules, capture timestamps or rewind
history invalidation. Calling advance repeatedly or omitting presentation would
choose those unresolved service cadences. No hardware-timing input, trace
hydration, fixture exception or relaxed native contract is added here.

### Focused validation

The lead owns broad shared-owner smoke. This slice queues only the affected
Engine/input/pacing/pause/replay/rewind boundaries, API pins, the architectural
source guard and the existing ROM-backed native recorded-Start witness. The S2
original REV01 is read in place with CRC32 `7B905383` and SHA-1
`8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9`. Both display variables are absent;
the rendered native witness explicitly selects surfaceless EGL and no-device
audio. No ROM copy/link, desktop automation, broad smoke or release suite is used.

The first focused command ran against merge `505b4aea6` plus this slice's source
changes before the physical/menu cache correction. It completed at
2026-10-29 21:49:14 UTC: 21 classes, 240 cases, one failure, zero errors/skips.
The sole failure was
`TestOwnedInputPass#recordedGameplayCannotAcknowledgeTheIndependentPhysicalMenuSampleDuringHandoff`:
expected physical text accept `true`, observed `false` on the adopted pass.
The other 239 cases passed, including Engine's fresh pad-step/modal/pump cases,
the 87 architectural source guards and the original S2 native-pause witness.
Queue wait was 419 seconds; Maven execution including clean compilation was
1m33s. XML identities/assertions/skips were inspected. This red run establishes
the physical-versus-gameplay cache defect; it is not green qualification.

The corrected command also selects the real S3K consecutive reset/power/Start
movie witness with and without host handoff, plus the four required S3K bootstrap
regressions. The original locked-on ROM is read in place with CRC32 `63522553`
and SHA-1 `CFBF98C36C776677290A872547AC47C53D2761D6`. It completed at
2026-10-29 22:00:08 UTC: **302 tests in 27 classes, zero failures, errors or
skips**. Queue wait was 509 seconds; Maven execution including compilation was
1m44s. All 27 XML summaries were inspected by identity, including both loading
classes, 21 owned-pass cases, 13 Engine cases, both zero-step variants, the S2
native recorded-Start case, both S3K reset/power variants, API pins and the 87
architectural source checks. Consumed Surefire diagnostics were removed after
inspection. These are focused results, not a broad-suite or independent-scheduler
qualification. Overall task elapsed time was not separately measured.

Tested source is merge `505b4aea64e04105a5668ea5faef799a70cc1181` plus the exact
main/test diff in this implementation commit. Main/test files were unchanged
after submission of the corrected request; only this evidence prose was finished
afterward. No live job remains. No source change or required check is deferred
inside this bounded increment; broad integration qualification stays with the
lead. The branch remains local and the provisioned checkout is retained.

Tool preflight passed with the same explicit Java/Lua environment:
`python3 tools/testing/run_categories.py --base 54af1dd76 --preflight` (no tests
executed). A first preflight without explicit environment stopped on the host's
default Lua before tests; setting `LUA_BIN=/usr/bin/lua5.4` corrected it. The earlier
leased version-only probe was cancelled before admission because the focused
Maven lifecycle already enforces Java 25; it supplies no validation evidence.


The exact common test selector for the first run was:

```bash
INPUT_TESTS='TestEngineFrameAdvance,TestOwnedInputPass,TestGameLoopZeroStepInputPass,TestGameLoopNativeMenus,TestInputAdmissionBoundaries,TestInputHandlerLogicalSnapshot,TestInputHandlerGamepadSuppression,TestKeyboardTextCapture,TestTextInputSession,TestScenePhysicalInput,TestRetainedGameplayInput,TestGameLoopMutatorPacing,TestGameLoopPauseInputOwnership,TestRecordedInputHostPause,TestPlaybackInputBridgeOwnership,TestLiveRewindLogicalInput,TestLiveRewindInputSourceStartHeld,TestGameLoopRewindBoundaryPolicy,TestRecordedInputNativePause,TestModApiPinPolicy,TestArchitecturalSourceGuard'
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
  LUA_BIN=/usr/bin/lua5.4 ALSOFT_DRIVERS=null EGL_PLATFORM=surfaceless \
  python3 tools/testing/maven_queue.py --normal -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl "-Dsonic2.rom.path=$S2_ROM_PATH" \
  "-Dtest=$INPUT_TESTS" test -B
```

The corrected run uses the same environment and common selector, adding the
following exact selector suffix and the original S3K ROM property:

```bash
FINAL_TESTS="$INPUT_TESTS,TestTasS3kSessionReset#consecutiveResetPowerStartRowsHaveOneCursorOwnerAndFreshControllerEdges,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils"
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
  LUA_BIN=/usr/bin/lua5.4 ALSOFT_DRIVERS=null EGL_PLATFORM=surfaceless \
  python3 tools/testing/maven_queue.py --normal -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  "-Dsonic2.rom.path=$S2_ROM_PATH" "-Ds3k.rom.path=$S3K_ROM_PATH" \
  "-Dtest=$FINAL_TESTS" test -B
```

The ROM variables denote the verified absolute original files outside the
checkout; they are not copies or generated aliases. The simple
`TestSonic3kLevelLoading` selector covers both production loading test classes.

### Replay-filtered ordinary menu input — 2026-10-29

Independent review identified that the host handoff retained a physical pad
press for text menus but also exposed it to ordinary fixed-key menu mapping after
recorded input released. The extended handoff test held Left while replay owned
a pad B press. On `b3e8cc784`, ordinary accept was true instead of false: one
failure, zero errors or skips, queue 533s, Maven 33.832s. The correction gives
ordinary fixed-key mapping the gameplay-filtered pad view and preserves the
physical view for text controls. The same test checks that text accept remains
visible and the actual owner acknowledges both channels.

The corrected main/test diff over `b3e8cc784` passed the preceding 26-class
selector, including the S2 and S3K ROM-backed checks and architectural guard:
**302 tests, zero failures, errors or skips**, Maven 1m45s. It used Java 25,
Lua 5.4, surfaceless EGL, the verified original S2 REV01 and locked-on S3K ROMs,
and the same `$S2_ROM_PATH` and `$S3K_ROM_PATH` command shape recorded above.
The queue wait was not retained. This focused result does not establish live
independent scheduling or replace shared-owner smoke.

## Ordered physical gameplay admission — 2026-10-29

This increment reuses candidate `6ba6dd081389ff818e9dbc79504fc9484cfa0b48`
and reconciles pushed develop `445feabf82ad9cd3607991702a23625fbb886f49`
without conflicts in local merge `1b39152c3368363a6b86132bd5f612001de5820b`.
Only the T3-provisioned `t3-deef1c2b` checkout is changed, on
`feature/ai-ordered-physical-input`. The extraction worktree and its separate
combined smoke remain untouched. This replaces the preceding scoped-pass
released-tap limitation; unscoped creator reads retain their existing contract.

### Admission and authority

Accepted, mapped keyboard callbacks and observed semantic controller changes
feed a bounded window-thread transition queue independent of the scene journal.
The consuming scoped pass selects the longest ordered prefix in which each
physical control changes at most once. Different controls can change together;
a control's press and release occupy successive consuming passes. Thus a fully
released short tap becomes one held/pressed sample followed by its release,
and two taps remain press/release/press/release. This defines admission order,
not original hardware timestamps or what happened during an unobserved poll.
Controller edges use the existing assignments, action mapping and deadzone.
Keyboard and controller controls keep separate identities before player merge.
A controller-assignment-table change rebaselines the controller channel while
retaining independent keyboard transitions.

Engine's host pass still reads current physical state for shortcuts. Its existing
handoff supplies one cached controller observation to the first GameLoop pass;
that consumer selects the gameplay prefix. Rereads reuse it. Only the existing
`update()` acknowledgment commits it; closing an unacknowledged pass leaves it
pending. Deliberate multiple-pass pumps consume successive prefixes and retain
their existing tick, recording and outer audio boundaries. Scene capture drains
only its own physical journal, with its original sequence and timestamps.

Zero-step host service keeps its physical pause/control sample. If eligible,
it previews the same first gameplay prefix into the existing mutator pacing
retention owner, without committing that prefix. Repeated zero-step service
cannot pull a later physical press ahead of an earlier queued release. The
first real consuming pass commits it; the existing retained-input bridge still
owns its semantic publication and rewind snapshot. Start already consumed by
the zero-step host is claimed before retention so it cannot pause twice.
No new world snapshot component or replay input source is introduced.

Text claims and composition remove only their keyboard transitions, including
a claim acquired between host observation and handoff. Pad suppression claims
only controller transitions. Physical text-menu pad samples remain distinct
from ordinary gameplay/menu samples. Fixed menu Enter/arrows retain their UI
meaning when a queued key is rebound to a different gameplay action/direction;
physical text controls do not borrow a gameplay admission sample. Recorded publication immediately retires
pending live gameplay transitions and retained input, and continues to own the
logical snapshot; release or aborted-pass rollback cannot resurrect live edges.
Previously admitted mutator carry retains its existing authority.

Whole-frame display modals, focus clear/pause/resume, mode/runtime/level changes,
native/user pause and live rewind/release service rebaseline pending host input.
They do not restore the host queue from world history. Reset/power playback
continues through its original owner. The queue is capped at 4,096 transitions;
on overflow the incomplete interval is rebaselined until acknowledgment rather
than inventing attacks from a partial history. Binding changes also rebaseline.

### Remaining live scheduling boundary

`FixedSimulationScheduler` remains disconnected. The existing FPS meaning,
region cadence, presentation draws, audio/capture duration and owners, trace
input authority and public API version/signature pins are unchanged. A zero-step
mutator host pass is not a qualified render-only Engine iteration. The next
shared boundary must assign explicit service cadences to fades and stateful
draws, `OuterFramePresentation`, live capture timestamps/packets and deliberate
recording/trace pumps, then connect window scheduling with pause/session/restore
anchor resets. Independent PAL/NTSC timing, interpolation and VRR are not claimed.

### Focused validation

Broad smoke and combined system qualification remain owned by the lead.
The final focused requests use Java 25, Lua 5.4 and the original S2 REV01 and
locked-on S3K files, verified in place against the repository CRC32/SHA-1 pins.
The menu/build source/test tree is `6b6edc9961d4619ab1be73ed32111eacfb92d50e`
over merge `1b39152c3368363a6b86132bd5f612001de5820b`. Final owner review
restricted native level-pause retirement to `LEVEL`, producing expanded-run
source/test tree `60cd7a4d9120ad29f2e51f352000df54926284e9`. A retained level
pause bit is not a special-stage/menu input claim. Documentation is completed
after execution.

The first ten callback/poll/pass cases passed with no failures, errors or skips
before subsequent identity, rebinding and menu hardening (queue 658s, Maven
34.870s). This was the first implementation check, not a baseline-red run.
The expanded request then failed main compilation because the menu correction
referenced two helpers that the whitespace-sensitive edit had not inserted:
queue 661s, Maven 1m06s, no tests executed. The helpers were added after inspecting
the exact compiler diagnostics. A build-only check then caught the new fixture's
incorrect `P1_LEFT`/`P1_RIGHT` enum names (Maven 1m34s); using the actual
`LEFT`/`RIGHT` settings repaired it. The subsequent Java 25 `test-compile` passed
in 37.368s. Those failed compilations supply no assertion or parity evidence.
The earlier build-only initial-source compile passed in 1m39s.

The separate changed-menu check completed at 23:32:12 UTC on 2026-10-29:
`TestMenuInput` (15 cases) and `TestMenuRepeat` (5 cases), **20 passed, zero
failures, errors or skips**. Queue wait was 716s and Maven execution 35.206s.
Both XML case inventories were inspected and consumed diagnostics removed.

```bash
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
  LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py --normal -Dmse=off \
  '-Dtest=TestMenuInput,TestMenuRepeat' test -B
```

The expanded request completed at 23:44:07 UTC: **367 tests in 36 classes,
365 passed, two failures, zero errors or skips**. Queue wait was 924s and Maven
execution 1m51s. The new sixteen ordered-input cases, sixteen real Engine cases,
both S2/S3K jump/release/frame-step witnesses, six native pacing/rewind cases,
recorded native pause, reset/power playback, four API pins and required S3K
bootstrap/loading checks all ran. The ROM witness XML confirms JVM
`25.0.5.0.2` and `surfaceless-egl`.

The two failures were identified, not attributed to upstream: the old scoped
scene/text characterization still asserted that released taps produce neutral
logical input; it now checks an independent gameplay press followed by release,
while scene events and committed text retain their owners. The dispatcher ratchet
reported `stepInternalBody` at 263 lines against its unchanged 262-line budget.
Its extra pause-clear call was redundant with the new pass-finally retirement;
removing that call keeps pause retirement and the existing ratchet. No guard
budget was raised. The final correction source/test tree is `cadc00545fad4815b49ea11bb9689c24313137ef`.
All 36 XML summaries, case failures and zero skips were inspected, then consumed
diagnostics removed. These red-run results are not a green full selection.

The affected correction request completed at 00:02:42 UTC on 2026-10-30:
**155 tests in ten classes passed, zero failures, errors or skips**. Queue wait
was 943s and Maven execution 1m42s. Both earlier failing identities passed,
including all 87 architectural source guards without increasing their budgets.
The sixteen Engine, twenty-one scoped-pass, four zero-step, pause, rewind and
both native S2/S3K jump/release/frame-step witnesses passed. The ten XML case
inventories were inspected against the exact correction source/test tree before
removing consumed diagnostics. Main/test sources did not change after admission;
only this evidence prose was completed afterward.

This resolves both expanded-run failures with affected checks, rather than
repeating unchanged bootstrap/loading/input coverage. The 367-case expanded
selection is not represented as one green run; the completed corrected evidence
combines its unchanged passing checks, this 155-case repair check and the separate
20-case menu check. No selected skip, unresolved assertion or unattributed failure
remains. This is bounded focused evidence, not broad smoke, release full suite,
trace campaign or independent live-scheduler qualification.

Tool preflight passed with the explicit Java/Lua environment using
`python3 tools/testing/run_categories.py --base 445feabf82ad9cd3607991702a23625fbb886f49 --preflight`;
it ran no engine tests. Diff whitespace, local design links, AGENTS/CLAUDE mirror
and commit-policy checks passed. The exact prepared message's final Sol co-author
was verified through `git interpret-trailers --parse`. No live task test job
remains. Consumed Surefire diagnostics and task scratch are removed. The local
feature branch and provisioned checkout remain for the lead; no PR, push,
release, integration or other-worktree cleanup is performed.

The exact expanded selector and environment were:

```bash
EXPANDED_INPUT_TESTS='TestEngineFrameAdvance,TestOwnedInputPass,TestGameLoopZeroStepInputPass,TestGameLoopNativeMenus,TestInputAdmissionBoundaries,TestInputHandlerLogicalSnapshot,TestInputHandlerGamepadSuppression,TestKeyboardTextCapture,TestTextInputSession,TestScenePhysicalInput,TestRetainedGameplayInput,TestGameLoopMutatorPacing,TestGameLoopPauseInputOwnership,TestRecordedInputHostPause,TestPlaybackInputBridgeOwnership,TestLiveRewindLogicalInput,TestLiveRewindInputSourceStartHeld,TestGameLoopRewindBoundaryPolicy,TestRecordedInputNativePause,TestModApiPinPolicy,TestArchitecturalSourceGuard,TestTasS3kSessionReset#consecutiveResetPowerStartRowsHaveOneCursorOwnerAndFreshControllerEdges,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestOrderedPhysicalGameplayInput,TestOrderedGameplayRomAdmission,TestInputHandler,TestKeyboardInputMapper,TestGamepadInputManager,TestRecordedInputOwnership,TestNativeStagePacingRewind,TestRewindReleaseRetryCoordinator'
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
  LUA_BIN=/usr/bin/lua5.4 ALSOFT_DRIVERS=null EGL_PLATFORM=surfaceless \
  python3 tools/testing/maven_queue.py --normal -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  "-Dsonic2.rom.path=$S2_ROM_PATH" "-Ds3k.rom.path=$S3K_ROM_PATH" \
  "-Dtest=$EXPANDED_INPUT_TESTS" test -B
```

The correction check repeats affected consumers and both native gameplay
witnesses, without repeating unchanged decoder/loading/input suites:

```bash
CORRECTION_INPUT_TESTS='TestOwnedInputPass,TestArchitecturalSourceGuard,TestEngineFrameAdvance,TestGameLoopZeroStepInputPass,TestGameLoopPauseInputOwnership,TestRecordedInputHostPause,TestOrderedGameplayRomAdmission,TestRecordedInputNativePause,TestNativeStagePacingRewind,TestGameLoopRewindBoundaryPolicy'
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
  LUA_BIN=/usr/bin/lua5.4 ALSOFT_DRIVERS=null EGL_PLATFORM=surfaceless \
  python3 tools/testing/maven_queue.py --normal -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  "-Dsonic2.rom.path=$S2_ROM_PATH" "-Ds3k.rom.path=$S3K_ROM_PATH" \
  "-Dtest=$CORRECTION_INPUT_TESTS" test -B
```

The ROM variables denote verified absolute original files read in place, not
copies or generated links. Queue intervals overlap and must not be added as
elapsed delivery time. Overall task wall time was not separately measured.


## Ordered-input pause/menu regression correction — 2026-10-30

Implementation checkout: T3-provisioned `t3-a4629043`, local
`feature/ai-ordered-input-p2`, based on the exact reviewed candidate
`a968037eb3bc5d0b609d09205375c2df8865c383` from `t3-deef1c2b`.
Fetched develop was `a3b021ec4f7bdf4ec6a860468fc730d770cc75b5`.
A trial integration conflicted only in `CHANGELOG.md` and
`CHANGELOG.0.7.prerelease.md` and was aborted under the clean-integration
constraint. The candidate ancestry is retained; develop integration remains
with the delivery lead. No push, PR, release, manual worktree or child agent
belongs to this implementation task.

Three owner boundaries require correction. A keyboard user-pause transition
must retire paused input before the resume tick maps it, including when no
paused service occurs between Jump press/release and the Pause callback.
Checking only the post-pass pause state misses this transition. Native menus
must read gameplay-bound keyboard controls from the same admitted sample as
logical controls: OR-ing a current raw Down with a delayed logical Down
consumes one physical edge twice. Conversely, fixed menu Enter/arrows under a
logical override retain their independent physical channel even when those
keys have gameplay bindings; the replay-owned gameplay sample cannot replace
that channel. Text menus retain their existing physical reads throughout.

General raw/physical key reads remain host observations. Expanding admission
to every `isKeyPressed` caller would conflate native menu controls with host
shortcuts, so that approach is rejected. Native compatibility reads instead
belong to the engine-internal input bridge, outside the creator API. Ordered
prefix acknowledgment, abort rollback, claims/composition, retained mutator
carry, replay publication, reset, recording and rewind keep their existing
owners. No fixture predicate, comparison tolerance, trace hydration or public
API pin is changed.


The first old-red request executed 22 cases with 5 assertion failures, no
errors and no skips (queue 361s, Maven 1m30s). Its staged tree was
`941f30636fdfae436bf4a5a99b5087cb68c9eca3`: unchanged production subtree
`99184606f35bf48c632cb55a6f89ebbae3c32b20`, test subtree
`59be5654d8079c7ac9c89b37117e86e1b7548985`. Both original-ROM S2 and S3K
resume ticks exposed released Jump as held/pressed action A; real S3K
Data Select observed team indices `[3, 2]` instead of `[0, 3]` across two
scoped passes. The initially-held release/repress and opposing-direction
control passed. Both Enter parameter cases failed, but the nominally unbound
case still inherited Enter/Start; the control was corrected to unbind Start
before subsequent evidence. These two first-run Enter failures therefore do
not establish a bound/unbound contrast.


The second old-red request selected only the corrected Enter parameters and
`TestGameLoopZeroStepInputPass#keyboardResumeCannotRetainAReleasedTapFromThePausedInterval`.
It executed 3 cases with 3 assertion failures, no errors/skips (queue 520s,
Maven 35.281s), against unchanged production and test subtree
`9b73a566f174b1a333c373291430e7f66b4d0224` (staged tree
`cfa8221d9740813a137ca847150aca084c45fde8`). Zero-step resume retained action
A (`1` instead of `0`). The Enter control still inherited the checkout's
`input.player2.start: ENTER`; clearing only P1 Start did not make it unbound.
The final parameter fixture resets standalone configuration to defaults and
explicitly unbinds both Starts, asserting the resolved bindings and raw
physical Enter edge before testing menu acceptance. No production fallback is
introduced to compensate for local configuration.


Reproduction commands (from this checkout; ordinary focused lane, no broad
suite). All ROM files were hashed in place: S1 REV01 CRC32 AFE05EEE/SHA-1
69E102855D4389C3FD1A8F3DC7D193F8EEE5FE5B; S2 REV01 CRC32 7B905383/SHA-1
8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9; locked-on S3K CRC32 63522553/SHA-1
CFBF98C36C776677290A872547AC47C53D2761D6. No ROM was copied or linked for these
requests. The commands below resolve existing ROM resources to the same original
absolute files; machine-local home-path literals are excluded by repository
resource policy. Exact literal invocation paths remain in the task handoff.

```bash
# Resolve existing repository ROM resources to their original absolute files.
OPENGGF_S1_ROM="$(realpath 'Sonic The Hedgehog (W) (REV01) [!].gen')"
OPENGGF_S2_ROM="$(realpath 'Sonic The Hedgehog 2 (W) (REV01) [!].gen')"
OPENGGF_S3K_ROM="$(realpath 'Sonic and Knuckles & Sonic 3 (W) [!].gen')"
JAVA_HOME=/usr/lib/jvm/java-25-openjdk python3 tools/testing/maven_queue.py -v
# First old-red request:
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
  python3 tools/testing/maven_queue.py -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  "-Dsonic2.rom.path=$OPENGGF_S2_ROM" \
  "-Ds3k.rom.path=$OPENGGF_S3K_ROM" \
  '-Dtest=TestOrderedPhysicalGameplayInput,TestS3kDataSelectManager#queuedActionThenHeldDownChangesTeamOnlyOnceAcrossTwoConsumingPasses,TestOrderedGameplayRomAdmission' test
# Second old-red request (no ROM/GL needed):
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk \\
  python3 tools/testing/maven_queue.py -Dmse=off \\
  '-Dtest=TestOrderedPhysicalGameplayInput#fixedEnterRemainsPhysicalDuringNeutralReplayWhetherGameplayBoundOrUnbound,TestGameLoopZeroStepInputPass#keyboardResumeCannotRetainAReleasedTapFromThePausedInterval' test
# Final old-red bound/unbound control (same no-ROM environment):
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk \\
  python3 tools/testing/maven_queue.py -Dmse=off \\
  '-Dtest=TestOrderedPhysicalGameplayInput#fixedEnterRemainsPhysicalDuringNeutralReplayWhetherGameplayBoundOrUnbound' test

OPENGGF_INPUT_TESTS='TestOrderedPhysicalGameplayInput,TestS3kDataSelectManager,TestOrderedGameplayRomAdmission,TestMenuInput,TestOwnedInputPass,TestGameLoopZeroStepInputPass,TestEngineFrameAdvance,TestGameLoopNativeMenus,TestInputAdmissionBoundaries,TestInputHandlerLogicalSnapshot,TestInputHandlerGamepadSuppression,TestKeyboardTextCapture,TestTextInputSession,TestScenePhysicalInput,TestRetainedGameplayInput,TestRecordedInputOwnership,TestRecordedInputHostPause,TestGameLoopPauseInputOwnership,TestPlaybackInputBridgeOwnership,TestLiveRewindLogicalInput,TestLiveRewindInputSourceStartHeld,TestGameLoopRewindBoundaryPolicy,TestRecordedInputNativePause,TestTasS3kSessionReset#consecutiveResetPowerStartRowsHaveOneCursorOwnerAndFreshControllerEdges,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestModApiSignatureSurface,TestArchitecturalSourceGuard,TestSonic3kTitleScreenIntroSkipAudio'
env -u DISPLAY -u WAYLAND_DISPLAY \\
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \\
  python3 tools/testing/maven_queue.py -Dmse=off \\
  -Dopenggf.headless.gl=surfaceless-egl \\
  "-Dsonic1.rom.path=$OPENGGF_S1_ROM" \\
  "-Dsonic2.rom.path=$OPENGGF_S2_ROM" \\
  "-Ds3k.rom.path=$OPENGGF_S3K_ROM" \\
  "-Dtest=$OPENGGF_INPUT_TESTS" test
```


The final old-red Enter request used staged tree
`15bef1079b825b0cc16ada4157d3a71d2604f493`, unchanged production subtree
`99184606f35bf48c632cb55a6f89ebbae3c32b20`, and test subtree
`53c0c7ef1e643d2dcb51dfe6add01520324b140f`. It executed 2 parameter cases:
unbound Enter passed, Enter bound to C failed at physical menu acceptance;
both had passed the resolved-binding and raw-edge assertions. There were no
errors or skips. Queue wait was 916s; Maven execution was 33.994s.

The correction adds only an engine-internal native-menu compatibility read;
creator signatures and pins are unchanged. Scoped native title/level-select/
data-select bindings share gameplay admission, including held-repeat timing;
unscoped direct controllers retain their existing raw compatibility behavior.
Fixed menus fall back to physical Enter/arrows while an override owns gameplay.
Canonical pause transitions retire and remap pending input before continuing;
zero-step transitions retire the physical journal before preview. General host reads,
scene collection, text controls and ordered-prefix commit/rollback are unchanged.

The first corrected-candidate request's staged source/test tree was
`d5cb28044a6fb26f86d81c3519bed433693515dd`: production subtree
`1e6590b9ddfc6ec30e0d39a64453fe7da8845681`, test subtree
`4739494e30bafd9c42322280055ee2987f693bf7`. Additional controls distinguish
host physical edges, consuming native presses/holds, and independent text
navigation across a real host handoff; native replay/claim suppression and
same-pass fixed Enter release remain explicit. Documentation changes after
this staging do not change these tested source/test subtrees.


The 31-selector corrected-candidate request waited 410s and executed for
1m43s: **360 cases, 1 assertion failure, 0 errors, 0 skips**. All functional
cases passed, including both original-ROM admission cases, all 13 real
Data Select manager cases, all 21 ordered-input cases, all 11 API signature
cases, and the S3K anchors: AIZ skip 8, both loading classes 36 + 7,
bootstrap resolver 6, decoding 3. The only failure was
`TestArchitecturalSourceGuard#rootDispatchMethodsDoNotGrowBeyondCurrentBudgets`:
`GameLoop.stepInternalBody` grew to 266 lines against its unchanged 262-line
budget. The pause transition is extracted into a private helper; the guard
budget/predicate remains untouched.

A final carry-preservation review rejected clearing already-admitted pacing
carry merely because zero-step host pause changes. Only physical transitions
are retired before preview; the pacing owner's original clear/retain policy
remains. This delta and its new preservation control overlapped the first
request's compilation. Bytecode inspection showed the old three
`clearPendingInput` call sites and absence of the new carry test, so that
request is **not** evidence for the final carry delta. Its unaffected functional
coverage is retained; fresh focused validation covers both changed pause
components and the dispatcher extraction. No source/test edits occur during
that correction request.

Final correction staging: tree `205fe49c03448983e687bc7cbf80e003846e82a6`,
production subtree `ac5c070cb4705518da8c58c621a936923b4a441c`, test subtree
`4015becb81c0e020b69cdcc513ef645de5cf9af0`. Repeat the ROM/EGL command above
with only this affected selection:

```bash
OPENGGF_INPUT_TESTS='TestGameLoopZeroStepInputPass,TestGameLoopMutatorPacing,TestGameLoopPauseInputOwnership,TestEngineFrameAdvance,TestOrderedGameplayRomAdmission,TestArchitecturalSourceGuard#rootDispatchMethodsDoNotGrowBeyondCurrentBudgets'
```

The Java 25.0.5.0.2 version probe passed after 378s queue wait (execution
not separately measured). An earlier version-only request with an unpinned
Java environment was canceled while waiting; it executed no tests. Queue
waits are per request and can overlap; they are not summed delivery elapsed
time. No combined smoke, release suite, trace campaign or publication is
claimed by this implementation task.


Fresh correction result: **40 cases, 1 structural assertion failure, 0 errors,
0 skips**, queue 316s, Maven 1m32s. All 39 functional cases passed on final
production subtree `ac5c070cb4705518da8c58c621a936923b4a441c`: zero-step 6
(including already-admitted carry preservation), mutator pacing 11, pause
ownership 4, real Engine advance 16, original-ROM S2/S3K admission 2.
The remaining guard failure was not growth: its exact-count predicate also
rejects a stale upper pin after extraction (`259 != 262`). Its pin was tightened
from 262 to 259 as the existing ratchet requires; the equality predicate and
all fixture admission/comparison rules are unchanged.

Final guard-only command:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
  python3 tools/testing/maven_queue.py -Dmse=off \
  '-Dtest=TestArchitecturalSourceGuard#rootDispatchMethodsDoNotGrowBeyondCurrentBudgets' test
```

It passed **1 case, 0 failures, 0 errors, 0 skips**, queue 22s, Maven 33.635s.
Final staged source/test tree `3b0a9d4268266090d18776d2aa93ead10c7facfc`
contains production subtree `ac5c070cb4705518da8c58c621a936923b4a441c` and test
subtree `ddb5e6c2ae6b2962a0863710c3c3e30d8f166cc9`. The only test delta after
the 40-case request is that stricter dispatcher pin. Earlier unaffected
native/menu/ownership/API/S3K-anchor coverage is retained; neither the
31-selector request nor the complete 40-case selection was repeated as a
ceremony. This is combined focused evidence, not a fresh all-selection pass,
a broad smoke pass or a release/full-suite qualification. No functional or
structural failure remains unresolved within this task's checks. Latest
develop integration and combined smoke remain with the lead under the
clean-only integration constraint above.


## Controlled native publication and keyboard resume correction — 2026-10-30

T3-provisioned checkout `t3-c5240fe2`, local
`feature/ai-ordered-input-regressions`, reconciles exact candidate
`607f31a756a59ecf11b878a8dba8d021bc317fac` from `t3-a4629043` with pinned
develop `6f267e8103188214ec40570248ad8ea9322cbc40`. Merge
`debc15710574b0e21759717f54fb0495f4595ebf` retains both ancestries. The only
conflicts were the two changelogs; both upstream and input themes are retained.
Engine merged automatically; inspection retains upstream quarter-scale editor
and render changes alongside candidate advance/presentation extraction. This
implementation task creates no manual worktree or child, and does not push,
open a PR, publish, integrate into the main workspace, or remove either checkout.

Two additional source-demonstrated regressions share no fixture-specific
condition. `ControlledFrameRuntime` used the replay takeover setter for a
synchronous native row and again for restoration. That setter correctly retires
live input and carry for replay, but deletes later queued physical transitions
when borrowed for `ADVANCE_NATIVE_INPUT`. Changing the setter globally is
rejected: replay must retain takeover authority. An engine-internal synchronous
publication owner instead saves/restores the exact logical snapshot and override
in `finally`, without acknowledging the physical prefix, retiring pacing carry,
or changing controller rollback history. Native rereads reuse the published row;
only the existing consuming pass acknowledges its input. Controller-supplied
native input and recorded overrides use the same temporary publication path.
No public creator signature, API baseline, or version changes.

Separately, `ControlledLevelIteration.HostPause` invokes GameLoop's pause callback,
which retires the ordered physical interval, but then supplied its previously
cached logical snapshot to the controller/native row. The host pause owner now
refreshes that snapshot immediately after its callback, within the same input
pass. Ordinary pause, zero-step pause, frame-step, hosted-run suspension and
previously admitted pacing carry retain their existing owners.

### Old-red evidence

The old production source plus the two new witnesses was staged as tree
`fb847467ee07d889804c3134bf2973cfe45156f2`, production subtree
`cfe7940d5b707c353820c450f08be46cc1221aa0`, test subtree
`88e5f39edefe365ff9547afdf312294c0a29f887`. The queued Java 25/EGL request ran
**2 cases, 2 assertion failures, 0 errors, 0 skips**, waiting 12s, with Maven
execution 1m30s. Exact failing identities:

- `TestControlledFrameRuntime#twoReleasedLiveTapsReachFourConsumingNativeRowsInOrder`:
  native press row 2 expected action A (`1`), observed `0`. Two complete physical
  taps are queued before four real controlled native consuming passes; each
  pass checks native press, held state, actual sprite frame and live authority.
- `TestOrderedGameplayRomAdmission#controlledKeyboardResumeRetiresReleasedPausedTapBeforeItsNativeRow`:
  expected one neutral controller row, observed action A held/pressed. This boots
  original S2 through the production capture/GameLoop owner with EGL, waits for
  initialized ground control, installs a controller, pauses/releases Pause,
  queues a complete released Jump tap plus Pause before the next service, then
  checks the resumed controller and native movement owners.

All existing ROM files are read in place via absolute original paths. Their
CRC32/SHA-1 identities match S1 REV01 `AFE05EEE` /
`69E102855D4389C3FD1A8F3DC7D193F8EEE5FE5B`, S2 REV01 `7B905383` /
`8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9`, locked-on S3K `63522553` /
`CFBF98C36C776677290A872547AC47C53D2761D6`. No ROM copies or links were created.

```bash
OPENGGF_S1_ROM="$(realpath 'Sonic The Hedgehog (W) (REV01) [!].gen')"
OPENGGF_S2_ROM="$(realpath 'Sonic The Hedgehog 2 (W) (REV01) [!].gen')"
OPENGGF_S3K_ROM="$(realpath 'Sonic and Knuckles & Sonic 3 (W) [!].gen')"
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
  python3 tools/testing/maven_queue.py -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl "-Dsonic2.rom.path=$OPENGGF_S2_ROM" \
  '-Dtest=TestControlledFrameRuntime#twoReleasedLiveTapsReachFourConsumingNativeRowsInOrder,TestOrderedGameplayRomAdmission#controlledKeyboardResumeRetiresReleasedPausedTapBeforeItsNativeRow' test
```

### Corrected focused validation

Corrected staged source/test tree `6a42fccf7e65a7c02a627fcd4ca1c3d711cad102`
contains production subtree `280aebbdfe914a017836af23bde0ff3c0e19cd04` and test
subtree `a21a75ae8674310cb93a0a377f285bccc4d0848b`. Additional real-input controls
check native rereads and exception restoration, keyboard/controller rollback,
previously admitted carry and prior replay override restoration. No source or
test edits occur during the corrected request. Evidence prose and folded
changelog wording are completed separately. The lead owns combined smoke; no
broad suite, release suite or trace campaign is claimed here.

```bash
OPENGGF_CONTROLLED_TESTS='TestControlledFrameRuntime,TestControlledCourseInput,TestOrderedPhysicalGameplayInput,TestOwnedInputPass,TestOrderedGameplayRomAdmission,TestGameLoopZeroStepInputPass,TestGameLoopMutatorPacing,TestGameLoopNativeStageMutatorPacing,TestGameLoopPauseInputOwnership,TestEngineFrameAdvance,TestGameLoopNativeMenus,TestInputAdmissionBoundaries,TestInputHandlerLogicalSnapshot,TestInputHandlerGamepadSuppression,TestKeyboardTextCapture,TestTextInputSession,TestScenePhysicalInput,TestRetainedGameplayInput,TestRecordedInputOwnership,TestRecordedInputHostPause,TestPlaybackInputBridgeOwnership,TestLiveRewindLogicalInput,TestLiveRewindInputSourceStartHeld,TestGameLoopRewindBoundaryPolicy,TestRecordedInputNativePause,TestTasS3kSessionReset#consecutiveResetPowerStartRowsHaveOneCursorOwnerAndFreshControllerEdges,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestModApiSignatureSurface,TestArchitecturalSourceGuard,TestSonic3kTitleScreenIntroSkipAudio,TestTraceReplayInvariantGuard,TestTraceHydrateSwitchDefault,TestRawGlfwTestSetupGuard,TestHeadlessBackendPropertyIsolation'
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
  LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  "-Dsonic1.rom.path=$OPENGGF_S1_ROM" "-Dsonic2.rom.path=$OPENGGF_S2_ROM" \
  "-Ds3k.rom.path=$OPENGGF_S3K_ROM" "-Dtest=$OPENGGF_CONTROLLED_TESTS" test
```

The leased version probe (`JAVA_HOME=/usr/lib/jvm/java-25-openjdk python3
tools/testing/maven_queue.py -v`) confirms Java `25.0.5.0.2` after 391s queue
wait; execution was not separately measured. Old-red XML properties also confirm
that JVM and explicit `surfaceless-egl`; both exact assertion identities and
zero skips were inspected before correction.

A separate non-overlapping focused selection completes the native menu/pacing
and API policy contracts; it uses the same corrected source/test tree and
ROM/EGL environment:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
  LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  "-Dsonic1.rom.path=$OPENGGF_S1_ROM" "-Dsonic2.rom.path=$OPENGGF_S2_ROM" \
  "-Ds3k.rom.path=$OPENGGF_S3K_ROM" \
  '-Dtest=TestS3kDataSelectManager,TestMenuInput,TestMenuRepeat,TestNativeStagePacingRewind,TestRewindReleaseRetryCoordinator,TestModApiPinPolicy' test
```

The six-selector request completed on the corrected source/test tree with
**44 cases, 0 failures, 0 errors, 0 skips**: Data Select 13, menu input 15,
menu repeat 5, native pacing rewind 6, rewind-release retry 1, API pin policy 4.
All six XML inventories were inspected. Queue wait was 134s and Maven execution
including recompilation was 1m34s. These selected contracts pass; the remaining
controlled-runtime/ROM/EGL selection is qualified separately below.

The 37-selector request executed **386 cases in 38 classes: 1 assertion failure,
0 errors, 0 skips**, after 408s queue wait and 54.879s Maven execution. Both
old-red regressions pass: all 7 real controlled-runtime cases and all 3 original
ROM/EGL admission cases. S1/S2/S3K native pacing passed all 9 cases; Engine advance
16, zero-step 6, ordinary pacing 11, pause ownership 4, ordered input 21,
API signatures 11, architectural source guard 87, trace authority guards 10 + 1,
and raw-GLFW guard 5 also pass. Required S3K anchors pass without skips: AIZ skip
8, both loading classes 36 + 7, bootstrap 6, decoding 3. All 38 XML inventories
were inspected, including JVM `25.0.5.0.2` and explicit EGL for the ROM witnesses.

The sole failure was the newly added
`TestOwnedInputPass#temporaryNativePublicationPreservesAbortRollbackAndAlreadyAdmittedCarry`:
expected combined action mask `7`, observed `5` on retry. Its fixture queued
keyboard press/release before first controller observation; the ordered prefix
must stop at the earlier keyboard release rather than pull the later pad press
forward. The test expectation was wrong. It now asserts action A plus retained C
on the initial and retried prefix, then the later pad B press together with the
keyboard release, then held B without another press. This strengthens explicit
ordering assertions; no production change, existing comparator, admission
predicate, fixture condition or guard pin is changed in response.

The corrected test staging is tree
`991b96d39cb795b2e4ba5bf4f854c194de78a47b`, unchanged production subtree
`280aebbdfe914a017836af23bde0ff3c0e19cd04`, test subtree
`be59c16fd67be9b2c64381c7c5e930f8d1360ccb`. Only the affected class is rerun:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
  python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=TestOwnedInputPass' test
```

The final affected-class rerun passed **23 cases, 0 failures, 0 errors, 0 skips**
after 185s queue wait and 31.571s Maven execution. Its XML includes both temporary
native-publication controls and was inspected against the corrected test subtree.
The combined latest XML inventory is 44 classes / 430 unique cases with no
remaining failures, errors or skips. This combines the unchanged passing cases
from the 386-case request, the 44-case menu/pacing/API request and the corrected
23-case input-pass rerun; it is not one fresh all-selection pass. Production
subtree `280aebbdfe914a017836af23bde0ff3c0e19cd04` is unchanged across all corrected
requests; the only subsequent test delta is the strengthened ordering control
in test subtree `be59c16fd67be9b2c64381c7c5e930f8d1360ccb`.

Whitespace, new-section Markdown fences/links, AGENTS/CLAUDE mirror and exact
prepared-message checks pass. The message carries all seven policy trailers and
the final parsed `OpenAI Codex GPT-6.1 Sol` coauthor. No live task Maven process
remains. Focused Surefire diagnostics are retained under this checkout's
`target/surefire-reports` for lead inspection; no category diagnostics, new
validation receipt or external log archive is created. The T3 checkout and
local feature branch remain for lead review/integration. No task-owned checkout,
branch, ROM link or unrelated change is removed. Queue waits are per request
and overlap; they are not summed delivery elapsed time. Overall task wall time
was not separately measured. Combined smoke and any main-workspace integration
remain with the lead; no push, PR or release was performed.

## Ending background update ownership — 2026-10-30

Source base: combined local candidate
`2481c30ec3133a64420ff4089566cf482d13fd6e`, with destination baseline
`develop` at `270fda5f4566cc404ea6f3ea2b692bc266fd2c98`. This leaf uses only
the T3-provisioned checkout; the lead owns the candidate's active combined smoke.

`LevelRenderer.renderEndingBackground()` previously incremented the rewind-visible
level frame counter and dispatched `ParallaxManager.updateForEnding()` from
`Engine.drawEndingCutscene()`. The S2 DEZ handler reads that clock for its star
rows and can also consume shake state. Another picture advanced this logical
state; omitting pictures omitted its progression. Merely memoizing a render or
renaming the draw method would not give updates ownership of that behavior.

The bounded separation moves that existing progression to `GameLoopEndingSession`,
after ending initialization or an admitted ending update has selected the scene.
The existing semantic provider query `needsLevelBackground()` and final
`ENDING_CUTSCENE` mode gate select it; no game, zone, frame or fixture key is
introduced. `LevelEndingUpdateAccess` crosses the internal package boundary without
extending the published Mod API. It requires loaded level geometry, not an active
GL renderer. Drawing still owns viewport sampling, tilemap invalidation/rebuild,
backdrop override and deferred GL submission, but reads the already advanced
scroll output. The normal one-update/one-picture ordering and star sample are
retained. A paused picture no longer advances the level clock or native handler.

The ROM supports update ownership: `EndingSequence` increments
`Level_frame_counter` before `RunObjects` and `BuildSprites`
(docs/s2disasm/s2.asm:13134-13150); the cutscene object's scroll dispatch is
`loc_A34C` (s2.asm:13482-13490). This slice preserves the engine's
existing background-active gate, post-provider sampling and clock domain. It
does not repair the wider ending's initial/reset clock or every ROM phase's
scroll-dispatch schedule.

`TestEndingBackgroundDrawBoundary` enters the actual S2 ending through its native
initializer with the original REV01 ROM, then reaches the first background by
running real `Engine.advanceFrame()` calls without drawing. Its witnesses cover
repeated `Engine.draw()` calls with a poisoned framebuffer and pixel comparison,
admitted updates without pictures, outer audio packet counts, focus pause and
one acknowledged frame-step, and restore/forward replay of the production level
and parallax adapters under different picture counts. Ending mode intentionally
rejects user-toggle pause; this change retains that policy. The restore witness
stays inside CHARACTER_APPEAR's constant BG-Y hold and certifies those two owners,
not the entire ending provider or an input movie.

### Remaining cross-owner dependencies

- **Fades:** Engine advances detached UI fades before routing display input;
  gameplay uses `GameLoopPlcLifecycle` and provider-owned palette/PLC phases.
  Callbacks can change modes or replace owners. Those gates, reverse suppression,
  restored-frame holds and their clock semantics still need coordinated fixed-tick
  admission. `UiRenderPipeline.renderFadePass()` already reads fade state.
- **Audio:** `OuterFramePresentation` produces one packet per Engine outer advance;
  headless capture calls its own outer presentation boundary. No picture-count
  independent service/sample-duration policy is established here. The existing
  modal/pause/step silence and reverse-history policies remain intact.
- **Capture/recording:** Live capture still emits after FINAL, followed by the
  screenshot and window-only recording plate. Its timestamps/repeats/drops must
  be designed together with audio. Recorded input rows, deliberate fast-forward
  pumps and authoritative rewind capture retain their current update boundaries.
- **Drawing/resources:** S2/S3K level-select background animators still update
  from draw. Display shader frame/history, shader-picker/menu presentation counters,
  provider/mod callbacks and lazy title-card art installation remain distinct
  owners to audit. Render-thread tasks, deferred commands and scene suppression
  cannot be reordered merely because this one background is repeat-safe.
- **Suspension:** `afterScene()` consumes thumbnail readiness after scene flush
  and SCENE effects; `betweenFrames()` parks/resumes only after that presentation
  opportunity. Ticks without a scene cannot satisfy it; the frozen request and
  its exact framebuffer boundary remain necessary.
- **Restore:** Live rewind deliberately excludes `ENDING_CUTSCENE`, and the S2
  ending provider has no complete rewind snapshot. The existing level/parallax
  adapters are reused; no new persistent state or snapshot schema is added.
  Full ending restore, history invalidation, session/region/pause rebasing and
  replay/capture cadence remain prerequisites for scheduler integration.

FixedSimulationScheduler remains disconnected. No FPS/configuration, input
ownership, hardware emulation, trace hydration, API version or signature baseline
changes are included. Focused validation is recorded below; independent live
timing, full ending rewind and broad engine qualification are not claimed.

### Focused separation evidence

The first fixture attempt stopped too early: its 1,200-tick bound was shorter
than the four native photo cycles (`$180 + $80` ticks per cycle plus transition
passes). All four cases failed in setup, with no errors/skips, after 341s queue
wait and approximately 85s Maven execution including the clean compilation.
That attempt is not evidence for the production defect. The corrected bound is
2,200; it then advances 16 further updates to reveal nonuniform sky/character
pixels while remaining inside the native `$40`-tick CHARACTER_APPEAR hold.

The corrected red request ran against unchanged candidate production subtree
`0b7b46c3b43611b3d99bed745ada5dccac107a4b` and the final new test
(SHA-256 `3a4219d940a0ae8e0ebc2cabe30e9b92feefffdcef8bdb23adecaec6eaa78368`).
It executed **4 cases, 4 assertion failures, 0 errors, 0 skips**, after 393s
queue wait and 33.945s Maven execution. Exact observations: two pictures changed
the level clock from 0 to 2; three focus-paused pictures changed it from 0 to 3;
an admitted update without a picture left it at 0 instead of 1; two pictures
changed a restored clock from 0 to 2. All cases reached the actual native ending
background before failing.

The changed production subtree is
`18b116f28e9c09d7900d125d50e9795e9e7c2bed`; test subtree
`887c59d19a54fc7caa2e0cd2cb76e75baccae577` includes the unchanged corrected
regression cases. `OPENGGF_S2_ROM` names the discovered absolute original
`Sonic The Hedgehog 2 (W) (REV01) [!].gen` source,
CRC32 `7B905383`, SHA-1 `8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9`.
The red XML verifies Java `25.0.5.0.2`, that absolute ROM path and explicit EGL.
No desktop display/audio device is used.

The green focused selection uses the same command shape, with the selectors
listed here (the red command selects only `TestEndingBackgroundDrawBoundary`):

```bash
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
  python3 tools/testing/maven_queue.py -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  "-Dsonic2.rom.path=$OPENGGF_S2_ROM" \
  '-Dtest=TestEndingBackgroundDrawBoundary,TestEngineFrameAdvance,TestGameLoopEndingSession,TestEngineLiveCapturePresentation,TestEngineSuspensionRendering,TestGameLoopAudioPresentationModes,TestGameLoopRewindBoundaryPolicy,TestLevelManagerRewindSnapshot,TestParallaxRewindSnapshot,TestSonic2NativeEndingEntry,TestSwScrlDez,TestModApiSignatureSurface,TestArchitecturalSourceGuard,TestRawGlfwTestSetupGuard,TestTraceReplayInvariantGuard' test
```

The change-based planner selects all 4,332 smoke classes because the engine and
its test infrastructure are shared. This leaf deliberately does not submit that
run: the user assigns combined smoke to the lead and authorizes only focused
validation here. This request checks the changed owner, adjacent production
boundaries and structural/API guards; it does not replace delivery-wide timing,
ROM/trace or public-release qualification.

The focused request completed **269 cases across 15 classes, 0 failures, 0
errors, 0 skips**, after 342s queue wait and approximately 98s Maven execution
including recompilation. All 15 XML inventories were inspected: the new boundary
4, Engine advance 16, live capture presentation 27, suspension pixels 1, audio
presentation modes 31, ending session 36, rewind boundary policy 7, DEZ native
scroll 2, level snapshot 16, parallax snapshot 14, Mod API signatures 12,
architectural source guards 87, trace authority guards 10, raw GLFW guards 5,
and native S2 ending ingress 1. The new boundary XML confirms Java `25.0.5.0.2`,
the original ROM path and `surfaceless-egl`; its four cases all executed.
The source/test subtrees above are unchanged throughout the green request. A
subsequent source-comment-only correction disambiguates the calling label for
the citation checker; executable code and tests are unchanged. This is focused
validation, not a smoke-suite or
trace/campaign pass.

Whitespace, AGENTS/CLAUDE mirror and disassembly-citation checks pass; the large
existing design contains one unverifiable citation but no checker findings,
and the corrected production accessor has no citation findings. No category run or
diagnostic archive was created. The owned Maven requests have exited; an initial
unneeded queued version probe was cancelled, and the actual test JVM identity
was verified from XML instead. Surefire reports remain in this checkout's
ordinary `target/surefire-reports` for lead inspection. No test job is retained.
The T3 checkout and local `feature/ai-draw-presentation-boundary` branch are kept
for lead integration; the original provisioned branch and unrelated work are
preserved. No push, PR, release or other checkout/target was touched. Queue waits
are per request; overall delivery wall time was not separately measured.

The final source-comment correction gives production subtree
`8b217bbc4cb31e083b4ca1e7cec9521994e5683d`; test subtree remains
`887c59d19a54fc7caa2e0cd2cb76e75baccae577`. Its affected source guard was checked
again with
`env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=TestArchitecturalSourceGuard' test`:
**87 cases, 0 failures/errors/skips**, after 149s queue wait and approximately
87s Maven execution including full recompilation. The XML confirms Java
`25.0.5.0.2`. These are repeated identities from the 269-case selection, not 87
additional unique cases. SHA-256 hashes of the three changed production class
files match the earlier behavioral build exactly after this recompilation.
The strict citation checker and both prepared-message/content policy checks pass.
The exact message was passed through `git interpret-trailers --parse`; its final
`OpenAI Codex GPT-6.1 Sol` coauthor was verified. The seven documentation policy
trailers precede that final coauthor block. No source/test edits followed these
checks; only this evidence paragraph was added.
