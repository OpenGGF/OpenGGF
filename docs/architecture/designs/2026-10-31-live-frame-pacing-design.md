# Live frame pacing: display-locked cadence, tick-gated pictures, regulated speaker

Date: 2026-10-31
Status: implemented on `feature/ai-live-frame-pacing`; real-display qualification open.
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

   **Implementation notes (unit 2).** The private queue-depth controller measures FIFO frames plus
   remaining OpenAL source frames before refill. Priming retains 4096 combined frames. A one-second
   exponential low-pass feeds PI gains derived from a ten-second unfiltered critically damped
   response. Conditional integration accounts for both the ±0.5% clamp and the 500 ppm/s slew
   limit. Unprimed/stopped output, flush, reverse, pause/resume and retirement reset the controller;
   saturation excursions remain diagnostic history. Queue telemetry subtracts `AL_SAMPLE_OFFSET`
   from unreclaimed source-buffer lengths. The loopback test checks source-frame offsets and
   retirement at both pitch limits with deterministic native mixing. At 48 kHz and 100 ms latency,
   the running slew's worst-case current-ratio latency approximation error is 0.12 frames, so no
   history ring is needed. The slew traverses the full trim in one response time; a slower
   100 ppm/s policy could not correct a startup skew before exhausting the three-buffer runway.
   A single monotone cursor observation also prevents the pause-time unity reset from moving
   the latency estimate backward; flush still starts a new source coordinate. Tests independently
   schedule packets, device consumption and pumps with ±20% interval jitter for two simulated hours
   per skew.
   At 48 kHz, three queued buffers guarantee just over two buffers of runway (42.7 ms);
   the 30 Hz witness stays within it at 40 ms. ±30% jitter exceeded that budget (43.3 ms)
   and correctly produced stopped-source transitions, so it does not qualify as drift-only load.
   Ratio, last serviced combined backlog, saturation and underrun counts are available on the sink. `AudioManager.isSpeakerRateRegulated()` admits only
   a rate-capable live sink or silent/no-device output. Its additive query is pinned in the mutable
   unpublished 0.7 API surface; candidate version and status stay unchanged.

3. **Display cadence.** `DisplayCadencePolicy`, `Probing`/`Locked`, `display.pacing`, `DisplayOutput`.
   Proof: synthetic swap sequences (59.94/60.02/50/119.88 lock; 75/144 refuse; immediate returns
   demote before the next service; jitter stays locked; one missed vblank is one slow interval; stalls
   reset), and production-path fake-vsync cases with identical per-service state.
4. **Draw purity.** Mutator fixes, bytecode guard, behavioural redraw check, with negative controls.

   **Implementation notes (unit 4).** `Obj_FlickerMove` toggles its visibility bit after
   movement/culling (sonic3k.asm:179086-179101). `LevelLoop` executes objects
   (sonic3k.asm:7916-7926); the lag-only `VInt_0_Level` path does not
   (sonic3k.asm:606-671). MGZ defeat fragments therefore retain their flicker in
   rewind state and advance it only in `update`, with the first executed phase hidden.
   Pachinko's render count and ICZ's tile count have no ROM temporal counterpart.
   The former is removed, including its debug overlay field; the latter is returned
   per draw instead of being stored. `Obj_ICZ1BigSnowPile` supplies collision without
   a sprite submission (sonic3k.asm:110484-110530), while `loc_49FD6` submits the
   current Pachinko sprite without a draw clock (sonic3k.asm:96684-96688).
   FBZ cloud projection and MGZ capsule mapping selection now use draw-local values.
   S3K preparation/publication phases are unchanged.

   `TestDrawPathFieldWriteGuard` scans compiled draw roots and transitive same-class
   private helpers, including lambda handles. It rejects direct, helper, static and
   enclosing-instance writes in negative controls. The reviewed cache inventory
   records 48 fields / 53 write sites, each with a reason. The wider scan also found
   31 logical debt fields / 43 sites, frozen separately in
   `src/test/resources/architecture/draw-purity/logical-debt.txt`. These include
   MCZ/LRZ lazy initialization, HCZ rocket and OOZ child storage initialization,
   SSZ screen-event position refresh, S2 results-message timeline initialization,
   and HCZ water-column spray animation. Moving the latter needs the independently
   executed spray child's init/release phases, rather than an unconditional parent
   update tick. Neither debt nor render caches may gain silent extra write sites;
   retired entries must be removed.

   `TestRepeatedPictureDrawPurity` belongs to routine rendering smoke and uses
   production offscreen boots for GHZ1, EHZ1 and AIZ1. At a fixed service boundary
   it compares every registered rewind key before/after one and three extra draws,
   including the first picture. A test-only draw mutator registered in the same
   live registry proves the detector rejects state changes. This is bounded
   three-level evidence; it does not certify the debt paths or unregistered state.

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
- A plain per-window mean telescopes to its two endpoints, so ±2 ms return jitter is ±0.4% per
  one-second window, twice the band. The first lane commit worked around that only while locked
  (an eight-second span plus a jitter allowance) and left the probe able to refuse a jittered
  59.94 Hz panel. Lead integration replaced both with one rule: each window fits its vblank period
  by least squares over every swap return, and decisions use the mean of the recent window fits
  (the two probe windows; the last eight while locked). A single one-second fit was still not
  enough, because a 59.94 Hz panel already spends 0.1% of the 0.2% band and one fit at ±2 ms
  uniform jitter has σ ≈ 0.05% (inferred; an hour of locked windows demoted at swap 1207).
  `jitteredProbeLocksOnANearRatePanelAndRefusesAnOffRatePanel` probes 50 seeded 59.94 Hz and
  59.7 Hz panels under ±2 ms jitter; all lock and refuse respectively. A 5% shift still demotes at
  the next window close.
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

## Integration validation — 2026-10-31

Lanes: draw purity `5187fd24a`, speaker regulation `36f3438de` (merged `2411b9272`, query moved
off the Mod API surface in `0e296306d`), runtime cadence `545d23b96` + `76dd927c3` (merged
`e9f2e2b6d`). Integration base `599d46762`.

- `run_categories.py --base 599d46762 --workers 2 --run` on `e9f2e2b6d` (surfaceless EGL, Java 25,
  Lua 5.4, pwsh): change-based selection was the whole smoke inventory (4,369 classes, 10
  categories). Ordinary lane 36,310 tests in 1,490 s: 8 failures, 0 errors, 49 skips. Guards lane
  816 tests in 135 s: all passed.
- The 8 failures (`TestMasterTitleScreenLayout` ×2, `TestSonic2InterlacePixels`,
  `TestNativeCompetitionViews`, `TestNativeSpriteSatGameplay` ×3, `TestPrivateLobbyScreenRendering`)
  reproduce with identical test identities on a detached `599d46762` checkout with the same
  five-class selection and ROMs. They predate this work and are not attributed to it.
- The 49 skips are opt-in capture, benchmark and performance probes plus
  `TestOpenAlIsolatedContext` (no OpenAL device in the headless environment; the speaker lane ran
  it with `ALSOFT_DRIVERS=null`).
- After the smoke run, the cadence fit changed (least squares, mean of window fits).
  `TestDisplayCadencePolicy` and `TestLiveFrameRuntime` were rerun: 56 passed, 0 skipped.
- Not run: any real window, real OpenAL device, VRR panel, Wayland/macOS swap interval 2, or
  trace replay sweep. `AUTO` locking on real hardware is unqualified; the probe falls back to
  `Exact` when a display does not qualify.


### Implementation notes: VRR measurement and even presents

`SwapPacingPolicy` owns a rolling two-second interval window, with at least one
second of samples before a verdict. It receives timestamps and measured work only.
The performance panel and Ctrl+P report the mean swap-return interval, population
standard deviation, and `ACTIVE`, `NOT_DETECTED`, or `UNKNOWN`.

For service period P and advertised vblank period T, let q = floor(P/T) and
f = P/T - q. Tick-gated fixed-refresh swaps alternate q*T and (q+1)*T, with
weights 1-f and f. Both models have mean P. Fixed refresh predicts variance
Q = f*(1-f)*T*T, while service-following swaps predict zero. The nearest-variance
model changes at Q/2. The mean error must also fit within that half-separation
budget. `NOT_DETECTED` additionally requires a fixed-lattice residual below Q/2.
Variable work can cross extra vblanks and increase the fixed-refresh variance above Q,
so the lattice fit remains valid without an upper variance bound. Unknown refresh, refresh at or below the service rate, integer ratios, insufficient
data, stalls, and ambiguous noisy intervals report `UNKNOWN`. The rule has no device-fitted
threshold. Measurement restarts on host boundary, focus, configuration, cadence,
and picture-mode changes. Only ordinary `Exact` pictures with one completed
service and no pending debt contribute. Paused, thinned, captured, forced,
screenshot, probing, locked, and blended pictures do not contribute.

When that evidence reports `ACTIVE`, a prepared tick-gated window picture may
wait until its rational service deadline plus the largest tick-and-draw work
sample from the last second. This empirical 100th percentile covers every recently
observed cost. The runtime returns the hold deadline to the existing polling loop.
Input polling and device service continue. The next iteration swaps without
reserving another service. A deadline at or beyond the next tick is never held,
and a late wake services due input and audio before replacing the stale picture.
Focus, capture, interpolation, screenshots, configuration, and boundary changes
cancel a pending hold. Capture draws and recording are never held. Interpolation
keeps its existing behavior and is never disabled automatically.

Rate windows restart at focus loss, regain, and boundary changes, including
changes observed during a service. Unfocused time contributes no rate samples.

These verdicts infer a swap-return model, not physical scan-out engagement.
A compositor that returns at service cadence can look like VRR without the panel
following it. Advertised refresh can also be stale or identify the wrong Wayland
output. Missed deadlines, irregular work, and low-framerate compensation can make
the models ambiguous. Real-panel qualification still requires the independent
render-rate design's device matrix and an external engagement indicator. No real
display, driver, compositor policy, or desktop setting was changed or qualified.
ROM-free seeded tests cover 50 and 60 Hz services on 75, 120, 144, and 165 Hz
swap models. This does not qualify the user's KDE Wayland displays.
