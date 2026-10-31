# Live frame pacing: display-locked cadence, tick-gated pictures, regulated speaker

Date: 2026-10-31
Status: synthesized design; implementation on `feature/ai-live-frame-pacing`.
Base: `develop` at `599d46762`. Follows
[independent rendering rate](2026-09-14-independent-render-frame-rate-design.md),
whose live region scheduler this design keeps.

## Problem

The live scheduler admits region services at exactly 50 or 60 Hz of `System.nanoTime`, draws on a
second rational phase at `display.fps`, and swaps under `glfwSwapInterval(1)`. The display follows its
own vblank. Three free clocks cause the remaining downsides:

- On a 59.94 or 60.02 Hz display, 60.000 Hz services beat against vblank. Every few seconds a
  picture repeats or two services share one picture.
- At a cap above the region rate, unchanged state is redrawn. S1/S2 level pictures re-run every
  object's `appendRenderCommands`, so any draw-side mutation runs at picture rate.
- While recording at a cap above the region rate, window-only redraws are added on top of the
  per-service capture pictures.
- A finished service waits for the next render deadline before it is shown.
- The speaker has no rate control. Production (800/960 frames per service) and the OpenAL crystal
  drift apart in every mode; a display-paced service rate would drain or flood the 2 s FIFO.

Constraints honoured: GameLoop keeps native steps, lag admission, recording rows and rewind
snapshots. Trace replay keeps its own tick driver. Pacing state never enters `RewindRegistry` or
trace data. All GL calls stay on the window thread. New types are engine-internal. Thresholds are
named host policy, not fitted gameplay constants.

## Usage (caller's view)

```java
// Engine.loop: poll, iterate, wait. The runtime owns its clock, anchor and cadence.
liveFrames = new LiveFrameRuntime(createLiveOwner(), System::nanoTime);
while (!glfwWindowShouldClose(window)) {
    pollHostAndApplyWindowChanges();
    waitForFrameDeadline(liveFrames.iterate().wakeAtNanos());   // slices poll input + service audio
}
```

```java
// Engine owner: observations in one record, effects as methods.
public LiveFrameRuntime.HostState hostState() {
    return new LiveFrameRuntime.HostState(FrameRateResolver.simulation(config),
            FrameRateResolver.presentation(config), !paused, gameLoop.isPaused(),
            liveSchedulingBoundary(), captureActive(), forcedPicture(),
            FrameRateResolver.pacing(config), displayWindowController.output(),
            audioManager.isSpeakerRateRegulated());
}
public boolean tick() { /* unchanged: mod-abort, render tasks, advanceFrame() */ }
public boolean draw(boolean capture) { /* unchanged */ }
public void swap() { /* unchanged swapPrepared(glfwSwapBuffers) */ }
public void swapInterval(int interval) { glfwSwapInterval(interval); }   // sole live writer
public void serviceDevice() { AudioManagerInternalAccess.serviceDevice(audioManager); }
```

```java
// Fake-time production-path test: a blocking 59.94 Hz swap paces the loop.
var clock = new FakeClock();
var vsync = new FakeVsync(clock, 1_000_000_000L * 1001 / 60_000);
var runtime = new LiveFrameRuntime(owner(vsync::swap), clock::now);
for (int i = 0; i < 3_600; i++) runtime.iterate();
assertEquals("LOCKED", runtime.statistics().cadence());
```

Audio callers do not change. `AudioManager.presentFrame` still pushes nominal packets; capture and
history taps are upstream of the speaker. `OpenAlPcmSink.updateDevice()` regulates its own
playback rate.

## Shape

### Cadence: one owner of service time

`LiveFrameRuntime` holds a private sealed cadence:

| Cadence | Service source | Pictures | Wake |
|---|---|---|---|
| `Exact` | `FixedSimulationScheduler` on host time (unchanged arithmetic, ≤4 per batch, 250 ms debt) | Only when a service is unpresented, thinned by the cap gate | Next service deadline |
| `Probing` | `Exact` services | One picture per swap; swap returns are measured | Immediately after the swap |
| `Locked` | One service per paced swap, at most one per swap | One picture per service | Immediately after the swap |

- **Nomination.** `AUTO` preference, a known advertised refresh, a regulated (or silent) speaker,
  a cap at or above the region rate, and refresh ≈ `n × regionHz` for integer `n` (GLFW reports
  integers, so allow ±1 Hz) nominate swap interval `n`. Anything else stays `Exact` with a reason.
- **Probe.** Present every swap at interval `n` while services stay on the exact clock. Accept after
  two consecutive one-second windows whose mean swap period, measured as Δ/round(Δ/P), is within
  ±0.2% of `n/regionHz`, with no immediate returns. Windows containing a boundary change, focus
  change or stall restart the probe.
- **Locked.** Each iteration: tick once, draw, swap, timestamp. A missed vblank is one slow interval,
  never a catch-up burst.
- **Demotion.** Immediate return (Δ < P/2) demotes before the next service. A rolling window out of
  band demotes. More than `MAX_MISSES_PER_SECOND` slow intervals demote. Stalls over the debt bound
  and boundary changes reset the window without demoting. Demotion rebases `Exact` at the demoting
  swap. A refusal for not-blocking or out-of-band waits for a changed output/config epoch; a
  refusal for misses may re-probe after the next boundary change.
- The game runs at display speed while locked (59.94 Hz is 0.1% slow). `display.pacing=EXACT`
  keeps nominal elapsed speed on any display.

### Types (engine-internal)

```java
final class LiveFrameRuntime {
    record HostState(int regionHz, int pictureCap, boolean focused, boolean hostPaused,
                     Object boundary, boolean captureActive, boolean forcedPicture,
                     DisplayPacing pacing, DisplayOutput output, boolean speakerRegulated) { }
    interface Owner {
        HostState hostState();
        boolean tick();
        boolean draw(boolean capture);
        void swap();
        void swapInterval(int interval);
        void serviceDevice();
    }
    LiveFrameRuntime(Owner owner, LongSupplier monotonicNanos);
    Iteration iterate();
    Statistics statistics();
    record Iteration(int services, boolean presented, long wakeAtNanos) { }
    record Statistics(int regionHz, int pictureCap, String cadence, String reason,
                      double measuredPresentHz, long services, long pictures, long swaps,
                      long pendingTicks, long discardedWallNanos) { }
}

/** Pure swap-timestamp policy: nominate, probe, lock, demote. No GL, no clock reads. */
final class DisplayCadencePolicy { /* see unit 3 */ }

// com.openggf.configuration
public enum DisplayPacing { AUTO, EXACT }      // display.pacing, default AUTO

// com.openggf (DisplayWindowController)
record DisplayOutput(long epoch, int advertisedRefreshHz) { }   // 0 = unknown; no VRR claim
```

`FixedSimulationScheduler.Batch` splits `nanosUntilNextIteration` into `nanosUntilTick` and
`nanosUntilRender`. The runtime wakes on ticks and uses `render` only as a thinning gate. The render
phase and `tickRemainderUnits` stay as the dormant interpolation inputs.

### Speaker regulation

Inside `OpenAlPcmSink`, a private servo sets `AL_PITCH` from the smoothed source backlog (FIFO frames
plus queued AL source frames). Target backlog: three AL buffers plus one FIFO buffer (4096 frames,
about 85 ms at 48 kHz). Ratio clamp ±0.5%, slew-limited. It runs in every cadence, which also removes
the existing crystal creep. It resets to unity on flush, pause and reverse boundaries and never
integrates while unprimed or starved. `Device.setPlaybackRate(double)` is the one new backend
operation; a backend without it reports `isSpeakerRateRegulated() == false`, which keeps the runtime
`Exact`. No-device/silent output counts as regulated (nothing can drift).

The audible cursor (`audibleSampleFrame`) scales its mixer-latency subtraction by the current pitch.
The pitch moves by at most the slew limit across the reported latency, so the error is a fraction of
a frame; no rate-history ring is kept. `ManagedSceneMusic.samplePositionAt` extrapolates at nominal
rate but clamps to the latest observation, so it is unaffected within a sample.

Capture and history audio are unchanged: a locked 59.94 Hz recording still holds 800 frames per
service and region-rate CFR timestamps.

### Draw purity

- Tick-gated pictures make ordinary window draws at most one per service at caps at or above the
  region rate. Capture draws per service and forced pictures remain explicit exceptions.
- Known logical mutators in draw paths move to their update owner, with ROM-phase evidence.
- A ROM-free bytecode guard rejects field writes from `appendRenderCommands` and its same-class
  helpers, apart from an exact, shrink-only list of reviewed render caches. Negative fixtures prove it
  fails on direct, helper and static writes.
- A ROM-backed behavioural check draws the current picture repeatedly on representative S1/S2/S3K
  levels and compares `RewindRegistry.capture()`; an injected mutating object proves it can fail.

### Interface depth

`LiveFrameRuntime` exposes a constructor, `iterate()` and `statistics()`, plus a six-method owner
(down from eleven). Behind it are three cadences, probe/lock/demotion policy, picture gating, capture
pictures, swap-interval control, rebasing and abort handling. `Engine.loop` only polls, iterates and
waits. The speaker's public surface gains one query.

## Synthesis decision

Two independent candidates (Opus, Sol) converged on exclusive wall/display cadences inside
`LiveFrameRuntime` and speaker-side drift control. The Sol cross-judge scored Sol 22/30 and Opus
17/30; the lead agreed. Base: Sol's candidate.

- From Sol: exact-first probe, at most one service per locked swap, `AL_PITCH` servo over a custom
  resampler, ±0.2% lock band, one `HostState` observation record, single swap-interval writer.
- From Opus: tick-gated pictures (cap as a thinning gate), a pure timestamp-policy class for
  synthetic tests, the missed-vblank-aware period estimate, `display.pacing=AUTO|EXACT`.
- Rejected from Sol: the `PresentationPhase.Between` API (unused until interpolation), the rate
  history ring (bounded error makes it unnecessary), broad S1/S2 publication now, a transitive
  call-graph effect analysis.
- Rejected from Opus: catch-up services from rounded swap durations, `glFinish`, the Hermite
  resampler and replacement span ledger, a lock band derived from audio constants, S1/S2
  per-tick capture now.

## Tradeoffs accepted

- We accept up to ±0.2% game-speed deviation while locked, in exchange for even motion on the most
  common displays. `EXACT` restores nominal speed. Tick identity, recordings and traces are unaffected.
- We accept judder on fixed 75/144/165 Hz panels without VRR, in exchange for not building
  presentation history now. On a VRR panel, tick-gated `Exact` swaps once per service and the panel
  follows; this is expected, not claimed as qualified.
- We accept a missed vblank slowing one interval while locked, in exchange for no catch-up bursts at
  the moment a frame is already late. Repeated misses fall back to `Exact`, which owns catch-up.
- We accept a pitch trim of at most ±0.5% (about 9 cents) and about 21 ms of extra speaker backlog, in
  exchange for bounded latency and no periodic FIFO drops or underruns.
- We accept per-service capture draws below the region-rate cap, in exchange for recordings that hold
  every completed service picture.

## Alternatives considered

- **Interpolation first.** Wall ticks plus blended pictures at every vblank. It hides the beat at any
  refresh, but needs stable render identity across about 1056 object renderers, camera/scroll
  contracts and a tick of added latency. It is the next phase, not this fix.
- **Always display-paced (the old coupled loop).** One tick per swap always. Wrong at non-multiple
  refresh rates and under VRR; it reopens the problem the decoupling solved.
- **Rate-matched wall clock (PLL).** Retune the exact scheduler to the measured display period. No
  phase lock, so estimator error still beats over minutes, and rational rates must thread through the
  scheduler, audio clock and capture lease.
- **Audio as master clock.** Removes speaker drift but leaves the display beat and makes the audio
  device the video scheduler.

## Open questions and risks

- Do compositors (DWM windowed, GNOME, KDE) return swaps periodically but out of phase with scan-out?
  The probe checks period, not phase. Device qualification records cadence, reason and measured Hz.
- Does `glfwSwapInterval(n ≥ 2)` block correctly on Wayland and macOS? If not, the probe rejects it
  and the runtime stays `Exact`.
- Does `AL_PITCH` keep `AL_SAMPLE_OFFSET` in source frames on every supported OpenAL implementation?
  The sink tests pin the expected contract; a backend that fails stays unregulated.
- Is `AUTO` the right default before device qualification? The probe fails safe to `Exact`.

## Deferred (seams kept)

- Interpolation: reactivate the render deadline as a wake source, pass `tickRemainderUnits / 1e9` to
  draw, and publish S1/S2 level pictures through `LevelSpritePresentation` (S3K tables already exist).
- VRR qualification and the device matrix in the 2026-09-14 design.
- Engine screen-space camera writes during draw (idempotent, non-gameplay modes).

## Implementation units

1. **Runtime owns its clock and pictures follow ticks.** `LiveFrameRuntime(Owner, LongSupplier)`,
   `HostState`, single swap-interval writer, `Batch` split, tick-gated `Exact`. Proof:
   `TestLiveFrameRuntime` on a fake clock (pictures = swaps = min(cap, hz), identical per-tick state,
   48 000 PCM frames/s, a tick and its picture in one iteration), `TestFixedSimulationScheduler`,
   `TestEngineFrameAdvance`, `TestArchitecturalSourceGuard`.
2. **Speaker regulation.** Servo, `Device.setPlaybackRate`, latency-scaled cursor,
   `isSpeakerRateRegulated()`. Proof: fake-device skew at ±0.2% ±200 ppm with service jitter over two
   simulated hours (no underruns or drops, settled backlog), unity passthrough, reset cases, cursor
   monotonicity, unchanged capture bytes.
3. **Display cadence.** `DisplayCadencePolicy`, `Probing`/`Locked`, `display.pacing`, `DisplayOutput`.
   Proof: synthetic swap sequences (59.94/60.02/50/119.88 lock; 75/144 refuse; immediate returns
   demote before the next service; jitter stays locked; one missed vblank is one slow interval; stalls
   reset), and production-path fake-vsync cases with identical per-service state.
4. **Draw purity.** Mutator fixes, bytecode guard, behavioural redraw check, with negative controls.
5. **Delivery validation.** Change-based smoke plus guards and the S3K four.

### Implementation notes: unit 1

- `HostState` is sampled before the batch, after every tick and after every swap. After a
  changed or forced present, the post-swap sample becomes the reference boundary: the picture
  consumes the forced request, so the pre-draw sample would read as a second change.
- `isSpeakerRateRegulated()` is a public member of `@ModApi` `AudioManager`, so the 0.7
  candidate signature pin gains its line. Unit 2 replaces the placeholder body only.
- `waitForFrameDeadline` no longer exits while unfocused; the runtime's 33 ms unfocused
  deadline goes through the same 2 ms polling slices, replacing the separate sleep.
- Removing the init `glfwSwapInterval(1)` lowers the `Engine#init` dispatcher ratchet from
  159 to 156 lines. The source guard pins one `glfwSwapInterval` reference in `Engine`.

### Implementation notes: unit 3

- The lock band is relative to the nominated vblank `1 / (n × regionHz)`; a swap at interval
  `n` spans `n` vblanks, so the locked service period is `1 / regionHz` within ±0.2%. The
  "±0.2% of n/regionHz" wording above would halve the service rate at `n = 2`.
- An immediate return generalises to `Δ < (n − ½)` vblanks, which equals `P/2` at `n = 1` and
  also rejects an interval-2 request the driver honours as interval 1 (119.88 Hz returning every
  8.3 ms). A slow interval is `round(Δ / vblank) > n`.
- The probe uses the plain per-window mean, as designed. The locked check cannot: that mean
  telescopes to its endpoints, so ±2 ms return jitter is ±0.4% per one-second window, twice the
  band. Locked windows therefore roll over eight one-second marks (probe marks carry into the
  lock) and allow `2 × RETURN_JITTER_NANOS / span` on top of the band; a 5% rate shift still
  demotes at the next window close. With ±2 ms jitter on a 59.94 Hz panel the probe itself can
  refuse `OUT_OF_BAND`; that fails safe to `Exact` and is a device-qualification question.
- `MAX_MISSES_PER_SECOND = 2`, counted per window and checked as each miss arrives.
- `Locked` ticks on every iteration, including the first after a boundary change: it holds no
  wall debt, so it does not insert the zero-service picture `Exact` shows at a boundary. Boundary
  changes, forced pictures and stalls only restart the policy window.
- `Probing` and `Locked` record the nomination request (pacing, speaker, cap, region, output);
  any change returns to `Exact` without a refusal and re-nominates in the same iteration.
- `DisplayWindowController.apply` samples the window's monitor each loop and bumps the output
  epoch on a mode change or a changed monitor handle/refresh. Wayland reports the primary
  monitor while windowed, so a windowed move between Wayland outputs is not observed.
- `SonicConfiguration.DISPLAY_PACING` is a public enum constant, so the 0.7 candidate signature
  pin gains its line. Only the English `settings.arb` strings are added; locale catalogues fall
  back to English until the translation pass.
- The production-path tests drive `Engine.LiveEndpoints` (render, swap, swap interval, output,
  speaker regulation, clock) with a blocking fake vsync; `TestScheduledNativeGameplay` adds one
  S2 locked restore/replay case on the same seam.
