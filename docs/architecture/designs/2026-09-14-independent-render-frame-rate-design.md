# Independent rendering rate and fixed simulation timing

Date: 2026-09-14
Status: proposed for 0.7; isolated scheduler proof only, no live integration.
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
