# Reverse-Resynth Worker Thread Implementation Plan

> Successor to `2026-05-26-reverse-resynth.md`. Addresses the synchronous-burst frame-hitch surfaced during smoke testing.

**Status:** Approved 2026-05-27, ready to implement after a Task 2 spike.

**Goal:** Move `ReverseResynthesizer` chip emulation off the game thread so held-rewind playback past the PCM history ring no longer causes per-keyframe frame hitches.

## Architecture

**Frozen session model.** At `beginReverseAudioPresentation`, snapshot every input the worker needs into an immutable `ReverseAudioSession`. Worker operates entirely on this snapshot + its own private SMPS presentation state. Worker never calls `AudioManager` or `AudioBackend`.

```java
public record ReverseAudioSession(
    PcmHistoryRing ring,
    AudioKeyframeStore.FrozenView keyframes,       // immutable copy at begin
    List<AudioTimelineEntry> frozenTimeline,       // List.copyOf at begin
    int sampleRate, int frameRate,
    int burstAudioFrames, int headroomThresholdFrames,
    SmpsDriverSnapshot.DependencyResolver replayDependencies,
    SmpsPresentationFactory presentationFactory
) {}
```

**Worker thread.** Spawned at `beginReverseAudioPresentation`, joined at `endReverseAudioPresentation` with a 200ms timeout. Cooperative shutdown via `volatile boolean stopping`. Per iteration:

1. Acquire ring monitor briefly → snapshot cursor state → release.
2. Acquire keyframe-store monitor briefly → pick keyframe → release. (Keyframes are pre-frozen, so this is constant-time copy.)
3. Restore worker's private `SmpsPresentationState` from keyframe (no locks).
4. Replay frozen-timeline entries onto private state via `SmpsPresentationReplay.applyTo(state, cmd, deps)`.
5. Forward-step chip emulation on private drivers, produce mixed PCM (no locks).
6. If `stopping` or `session == null` after step 5: discard burst and exit.
7. Acquire ring monitor briefly → `prependBackward` → release.
8. If headroom OK: `Thread.sleep(2ms)`. Otherwise loop immediately.

**Consumer (audio drain on game thread).** `drainPcm` reads ring under brief lock. `readPrevious` pads with silence if cursor is exhausted (existing behaviour). The synchronous `ensureHeadroom` call is REMOVED from `StreamBackedDeterministicAudioRuntime.drainPcm`.

**Synchronization.** Encapsulated inside `PcmHistoryRing` and `AudioKeyframeStore`. `PcmHistoryRing.ReverseCursor` is a non-static inner class; its methods synchronize on `PcmHistoryRing.this` (outer monitor), so prepend and read are mutually exclusive. Lock scope is cursor + ring slot mutation only — no snapshot work under the lock. Migration to a lock-free SPSC ring later is a contained refactor.

**Startup prefill.** Initial cursor on `beginReverseAudioPresentation` points at the most recent recorded frame; the ring is full of recently-recorded forward PCM (up to `REWIND_AUDIO_HISTORY_SECONDS`). The worker spawns immediately and pre-fills ahead of the consumer. If the user holds rewind through the entire ring before the worker can produce, the consumer hears silence — acceptable trade vs frame hitches.

## Tasks

### Task 1 — Lock `PcmHistoryRing`

- All `PcmHistoryRing` public methods: `synchronized (this)`.
- `ReverseCursor` (non-static inner class) methods synchronize on `PcmHistoryRing.this` — not the cursor.
- Lock scope: cursor state mutation + slot reads/writes only. No snapshot copies under the lock.
- Document SPSC contract in Javadoc.
- Concurrent smoke test: thread A prepends 1000 frames, thread B drains 1000 frames; assert correctness + no exceptions.

### Task 2 — `SmpsPresentationState` + `SmpsPresentationReplay` (highest risk, spike first)

**Spike first** with `playSfxSmps` only — the most complex path. Validate parity (output identical to current path) before expanding.

`SmpsPresentationState`: per-session SMPS logical state. Fields mirror what `LWJGLAudioBackend` carries today:
- `SmpsDriver musicDriver`
- `SmpsDriver standaloneSfxDriver`
- `boolean sfxBlocked`
- `boolean speedShoesEnabled`, `int speedMultiplier`
- override stack (`Deque<AudioSourceDescriptor>` or similar)
- SFX priority slot
- continuous-SFX tracking
- fallback voice binding

`SmpsPresentationReplay`: stateless helper that applies an `AudioCommand` to a `SmpsPresentationState` given immutable `ReplayDependencies` (smpsLoader, dacData, donor loaders, audio profile, sequencer config).

**Strict scope: SMPS logical state only.** This abstraction does NOT touch OpenAL sources, runtime stream binding, or the presentation FIFO. Those remain backend concerns.

LWJGL backend wrappers (live path):
```java
public void playSfxSmps(...) {
    synchronized (streamLock) {
        SmpsPresentationReplay.applyTo(livePresentationState, command, replayDeps);
        bindRuntimePresentationStreams();  // OpenAL/runtime side effects layered ABOVE
    }
}
```

Worker (offline path):
```java
SmpsPresentationReplay.applyTo(workerPresentationState, command, replayDeps);
// no bindRuntimePresentationStreams, no OpenAL
```

Single source of truth for SMPS replay logic; backend keeps its OpenAL/runtime concerns separate.

**Tests for the spike:**
- For each existing path through `playSfxSmps` (standalone SFX, continuous SFX extension, priority gate, fallback voice, sfxBlocked-gated dispatch): construct an input, run through the new helper + verify state equivalence with what the old direct path produced.

### Task 3 — `ReverseAudioSession` record

Built once at `beginReverseAudioPresentation`. Immutable. Contains everything in the architecture diagram above.

### Task 4 — `AudioKeyframeStore.FrozenView` + lock

- Add `synchronized` to all `AudioKeyframeStore` public methods.
- `frozenView()` returns an immutable wrapper holding `Map.copyOf(keyframes)`. Same lookup API surface as the mutable store, no synchronization needed by callers of the frozen view.

### Task 5 — `ReverseResynthesizer` refactor

- Implements `Runnable`.
- Fields: `volatile ReverseAudioSession session`, `volatile boolean stopping`, `SmpsPresentationState workerState`.
- Constructor: takes initial session, allocates `workerState` from `session.presentationFactory()`.
- `run()` loop: see architecture section above.
- `requestStop()` sets `stopping = true`.
- `detachSession()` sets `session = null; stopping = true` — atomic-feeling detach.
- Worker re-reads `session` at every burst boundary; null → return.
- Test counter: `int detachedSinceStart` — observable for shutdown-timeout tests.

### Task 6 — Lifecycle wiring + startup prefill

`AudioManager.beginReverseAudioPresentation`:
1. Build `ReverseAudioSession` from live state.
2. Construct `ReverseResynthesizer`.
3. Run one synchronous burst inline to prefill (best-effort; if no keyframes/ring isn't ready, skip).
4. Spawn daemon thread `new Thread(resynth, "reverse-resynth"); setDaemon(true); start()`.
5. Existing `reverseAudioPresentationActive = true` etc.

`AudioManager.endReverseAudioPresentation`:
1. `resynth.requestStop()`.
2. `thread.join(200ms)`.
3. If timed out:
   - `LOGGER.fine("reverse-resynth worker timed out, detaching session")` (FINE, not WARNING).
   - `resynth.detachSession()` — atomically makes ring and keyframes unreachable from worker.
4. Continue to existing snapshot restore.
5. `runtime.endReversePresentation()` last (it calls `commitReverseCursor`).

Remove: `setReverseResynthesizer` setter on `StreamBackedDeterministicAudioRuntime`, `rebuildReverseResynthesizerForCurrentBackend` in `AudioManager`, the `ensureHeadroom` call inside `StreamBackedDeterministicAudioRuntime.drainPcm`.

### Task 7 — Shutdown race guard

Already covered in Task 5 + 6. Specifically:
- Worker checks `stopping` AFTER chip emulation, BEFORE the prepend call — skip prepend if stopping.
- Worker re-reads `session` reference (volatile) at every burst boundary — null → exit.
- Game thread joins for 200ms, then detaches. After detach, worker cannot touch the ring or keyframes even if it's still running.

### Task 8 — Tests

1. **Concurrent ring access** (Task 1) — prepend + read threads, sanity assertions.
2. **Frozen keyframe view** (Task 4) — mutate underlying store after `frozenView()`; verify view is unaffected.
3. **SMPS replay parity** (Task 2 spike) — old-path output vs new-path output, byte-identical.
4. **Worker lifecycle** (Task 5+6) — begin → end, no thread leaks, no exceptions.
5. **Shutdown timeout** (Task 7) — simulate slow worker (e.g. a mocked `SmpsPresentationReplay` that sleeps); verify `endReverseAudioPresentation` returns within ~400ms total and `detachSession` was called.
6. **Output parity** — end-to-end held-rewind through the worker; PCM matches the current synchronous resynth for the same keyframe + timeline window.
7. **Frozen session isolation** — start a session; mutate the live AudioKeyframeStore / AudioCommandTimeline mid-session; verify worker output is unaffected.

### Task 9 — Manual smoke

Boot engine, play 30s, hold rewind for >10s.
- Confirm no frame hitches during held-rewind.
- Confirm audio plays continuously past the 10s ring (assuming `REWIND_AUDIO_HISTORY_SECONDS=10`) or 2s ring if user has the config tuned.
- Confirm releasing rewind resumes forward play correctly.

## Sequencing & risk

**Order**: 1, 4 (independent foundation, parallelizable) → **2 spike** → 3 → 5 → 6 → 7 → 8 → 9.

**Highest risk: Task 2.** Spike `playSfxSmps` first. If extraction reveals more entanglement than expected (e.g. side effects on OpenAL source state that can't be cleanly factored out), STOP and re-plan. Do not proceed to Task 3+ until the spike is parity-clean with focused tests.

**Open question (confirm before Task 5)**: Verify that game-thread audio paths during held-rewind don't depend on state the worker mutates. Specifically inspect:
- `LiveRewindManager.handleRealtimeRewindInput`
- `RewindController.stepBackward` → `restoreAudioLogicalState` → `audioKeyframes.replayToLogicalState` → `audio.restoreLogicalSnapshot`
- `audio.replayTimelineCommandLogically`

These run on the live backend state, which the worker doesn't touch (worker has private `SmpsPresentationState`). Confirm by code inspection.

## Migration to lock-free SPSC ring (future)

Once parity is established, `PcmHistoryRing`'s `synchronized` blocks can be replaced with atomic cursor fields + appropriate memory barriers. Caller API is unchanged. Out of scope for this plan.
