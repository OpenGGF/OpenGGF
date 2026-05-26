# Reverse Audio Re-Synthesis Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend held-rewind audio past the current 10-second silence wall by re-synthesizing historical PCM from audio keyframes when the reverse cursor approaches the bottom of its ring window.

**Architecture:** A `ReverseResynthesizer` watches the runtime's `PcmHistoryRing.ReverseCursor` during reverse drain. When headroom drops below 0.5 s, it restores the live `SmpsDriver` + standalone SFX driver from the latest audio keyframe whose clock snapshot precedes the burst target, replays SFX timeline entries under a new `REVERSE_RESYNTH` scope, forward-steps the chips game-frame-by-game-frame to produce 1 s of PCM, and prepends it into the ring below `oldestReadableFrame`. A rewind-bracket capture/restore of the full audio snapshot at `beginReverseAudioPresentation` / `endReverseAudioPresentation` keeps the live drivers honest across the held-rewind session.

**Tech Stack:** Java 21, JUnit Jupiter (5.x), LWJGL OpenAL backend, Maven with `-Dmse=relaxed` default via `.mvn/maven.config`. PowerShell when invoking `mvn -Dtest=...`.

**Reference spec:** `docs/superpowers/specs/2026-05-26-reverse-resynth-design.md`.

**Prerequisites confirmed on develop:**
- LWJGL runtime-presentation migration (`158e4cab3`).
- `consumeCommands` past-frame drain fix (`367612eb4`).
- `beginGameplayAudioFrame` monotonic clamp (`9b37ff0ef`).

---

## File Structure

| File | Responsibility |
|------|----------------|
| `runtime/PcmHistoryRing.java` (modify) | Forward writes + reverse reads of stereo PCM. **New:** `prependBackward` writes older samples and `ReverseCursor.extendOldestTo` lowers the readable floor. |
| `runtime/DeterministicAudioRuntime.java` (modify) | Interface gains `captureClockSnapshot` / `restoreClockSnapshot` / `hasActivePresentation` defaults; runtime variant gains a `ReverseResynthesizer` hook. |
| `runtime/StreamBackedDeterministicAudioRuntime.java` (modify) | Implements clock snapshot. Accepts optional `ReverseResynthesizer`. In `drainPcm`, when `reverseCursor != null`, calls `resynth.ensureHeadroom(cursor)` before `readPrevious`. |
| `runtime/ReverseResynthesizer.java` (new) | Owns the burst loop. References `PcmHistoryRing`, `AudioKeyframeStore`, `AudioManager`, the runtime, and a list-backed `commandTimeline` accessor. |
| `rewind/AudioBackendLogicalSnapshot.java` (modify) | Record gains nullable `AudioFrameClock.Snapshot clockSnapshot` field. |
| `rewind/AudioKeyframeStore.java` (modify) | New `keyframeAtOrBeforeAudioFrame(long)` lookup + new `replayLiveTo(audioManager, targetAudioFrame, commandTimeline)` reverse-resynth replay path. |
| `rewind/AudioReplayReason.java` (modify) | New enum value `REVERSE_RESYNTH`. |
| `audio/AudioManager.java` (modify) | `captureLogicalSnapshot` records clock snapshot. `replayTimelineCommand` routes `PlaySfx` directly to the chip path under `REVERSE_RESYNTH`. `beginReverseAudioPresentation` brackets a `restoreLogicalSnapshot` against a captured pre-rewind state at `endReverseAudioPresentation`. |
| `audio/LWJGLAudioBackend.java` (modify) | Wires `ReverseResynthesizer` into the runtime construction. |
| Test files (new + extend) | Per "Testing" section. |

---

## Task 1: Round-trip test for AudioFrameClock.Snapshot

`AudioFrameClock.Snapshot` already exists and `captureSnapshot()` / `restoreSnapshot()` are present. We just lock the round-trip invariant we'll lean on later.

**Files:**
- Create: `src/test/java/com/openggf/audio/runtime/TestAudioFrameClockSnapshotRoundTrip.java`

- [ ] **Step 1: Write the test**

```java
package com.openggf.audio.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestAudioFrameClockSnapshotRoundTrip {

    @Test
    void captureRestoreReproducesSamplesForNextFrameSequence() {
        AudioFrameClock clock = new AudioFrameClock(48000, 60);
        // Advance to a non-trivial state with a non-zero remainder.
        for (int i = 0; i < 17; i++) {
            clock.samplesForNextFrame();
        }
        AudioFrameClock.Snapshot snapshot = clock.captureSnapshot();
        long totalAtSnapshot = clock.totalSamplesProduced();
        int remainderAtSnapshot = clock.remainder();

        // Walk forward and capture the sequence.
        int[] forwardSequence = new int[10];
        for (int i = 0; i < 10; i++) {
            forwardSequence[i] = clock.samplesForNextFrame();
        }

        // Restore back to the snapshot and re-run.
        clock.restoreSnapshot(snapshot);
        assertEquals(totalAtSnapshot, clock.totalSamplesProduced());
        assertEquals(remainderAtSnapshot, clock.remainder());

        for (int i = 0; i < 10; i++) {
            assertEquals(forwardSequence[i], clock.samplesForNextFrame(),
                    "sample count for frame " + i + " must match across restore");
        }
    }

    @Test
    void restoreRejectsMismatchedRate() {
        AudioFrameClock clock = new AudioFrameClock(48000, 60);
        AudioFrameClock.Snapshot mismatched =
                new AudioFrameClock.Snapshot(44100, 60, 0, 0);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> clock.restoreSnapshot(mismatched));
    }
}
```

- [ ] **Step 2: Run**

`mvn "-Dtest=TestAudioFrameClockSnapshotRoundTrip" test`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/openggf/audio/runtime/TestAudioFrameClockSnapshotRoundTrip.java
git commit -m "$(cat <<'EOF'
test: lock AudioFrameClock snapshot round-trip invariant

Establishes that capture + restore reproduces the exact
samplesForNextFrame() sequence including remainder state. The reverse
resynth burst loop relies on this for audio-frame indexing across
keyframe restores.

Changelog: n/a
Guide: n/a
Known-Discrepancies: n/a
S3K-Known-Discrepancies: n/a
Agent-Docs: n/a
Configuration-Docs: n/a
Skills: n/a

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: Add clockSnapshot to AudioBackendLogicalSnapshot

**Files:**
- Modify: `src/main/java/com/openggf/audio/rewind/AudioBackendLogicalSnapshot.java`
- Create: `src/test/java/com/openggf/audio/rewind/TestAudioBackendLogicalSnapshotClockField.java`

- [ ] **Step 1: Add field + compatibility constructor to the record**

Replace the contents of `AudioBackendLogicalSnapshot.java` with:

```java
package com.openggf.audio.rewind;

import com.openggf.audio.runtime.AudioFrameClock;

import java.util.List;
import java.util.Objects;

public record AudioBackendLogicalSnapshot(
        AudioSourceDescriptor currentMusic,
        boolean sfxBlocked,
        boolean pendingRestore,
        boolean speedShoesEnabled,
        int speedMultiplier,
        List<AudioSourceDescriptor> overrideStack,
        SmpsDriverSnapshot musicDriver,
        SmpsDriverSnapshot standaloneSfxDriver,
        AudioFrameClock.Snapshot clockSnapshot) {

    private static final AudioBackendLogicalSnapshot EMPTY =
            new AudioBackendLogicalSnapshot(null, false, false, false, 1, List.of(), null, null, null);

    public AudioBackendLogicalSnapshot {
        overrideStack = List.copyOf(Objects.requireNonNull(overrideStack, "overrideStack"));
    }

    public AudioBackendLogicalSnapshot(
            AudioSourceDescriptor currentMusic,
            boolean sfxBlocked,
            boolean pendingRestore,
            boolean speedShoesEnabled,
            int speedMultiplier,
            List<AudioSourceDescriptor> overrideStack) {
        this(currentMusic, sfxBlocked, pendingRestore, speedShoesEnabled, speedMultiplier,
                overrideStack, null, null, null);
    }

    public AudioBackendLogicalSnapshot(
            AudioSourceDescriptor currentMusic,
            boolean sfxBlocked,
            boolean pendingRestore,
            boolean speedShoesEnabled,
            int speedMultiplier,
            List<AudioSourceDescriptor> overrideStack,
            SmpsDriverSnapshot musicDriver,
            SmpsDriverSnapshot standaloneSfxDriver) {
        this(currentMusic, sfxBlocked, pendingRestore, speedShoesEnabled, speedMultiplier,
                overrideStack, musicDriver, standaloneSfxDriver, null);
    }

    public static AudioBackendLogicalSnapshot empty() {
        return EMPTY;
    }
}
```

- [ ] **Step 2: Add a unit test pinning the new field's accessor**

Create `src/test/java/com/openggf/audio/rewind/TestAudioBackendLogicalSnapshotClockField.java`:

```java
package com.openggf.audio.rewind;

import com.openggf.audio.runtime.AudioFrameClock;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class TestAudioBackendLogicalSnapshotClockField {

    @Test
    void compatibilityConstructorsLeaveClockSnapshotNull() {
        AudioBackendLogicalSnapshot oldShape = new AudioBackendLogicalSnapshot(
                null, false, false, false, 1, List.of());
        assertNull(oldShape.clockSnapshot());

        AudioBackendLogicalSnapshot drivers = new AudioBackendLogicalSnapshot(
                null, false, false, false, 1, List.of(), null, null);
        assertNull(drivers.clockSnapshot());

        assertNull(AudioBackendLogicalSnapshot.empty().clockSnapshot());
    }

    @Test
    void canonicalConstructorRetainsClockSnapshot() {
        AudioFrameClock.Snapshot clock = new AudioFrameClock.Snapshot(48000, 60, 480000L, 13);
        AudioBackendLogicalSnapshot snapshot = new AudioBackendLogicalSnapshot(
                null, false, false, false, 1, List.of(), null, null, clock);

        assertNotNull(snapshot.clockSnapshot());
        assertEquals(480000L, snapshot.clockSnapshot().totalSamplesProduced());
        assertEquals(13, snapshot.clockSnapshot().remainder());
    }
}
```

- [ ] **Step 3: Build**

`mvn test-compile`
Expected: clean compile; existing callers of the two-arg constructor are unaffected because we kept the compatibility constructor.

- [ ] **Step 4: Run the new test**

`mvn "-Dtest=TestAudioBackendLogicalSnapshotClockField" test`
Expected: PASS.

- [ ] **Step 5: Run the full audio package**

`mvn "-Dtest=com.openggf.audio.**" test`
Expected: PASS. Any existing test that constructed `AudioBackendLogicalSnapshot` via the old shape continues to compile and pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/openggf/audio/rewind/AudioBackendLogicalSnapshot.java \
        src/test/java/com/openggf/audio/rewind/TestAudioBackendLogicalSnapshotClockField.java
git commit -m "$(cat <<'EOF'
feat(audio): add nullable clockSnapshot to AudioBackendLogicalSnapshot

Records the runtime's AudioFrameClock state at capture time so reverse
resynth can map game-frame keyframes to audio-frame indices when
forward-stepping chips from a restored keyframe. Compatibility
constructors keep the old (8-arg and 6-arg) call sites working.

Spec: docs/superpowers/specs/2026-05-26-reverse-resynth-design.md
(Architecture, AudioBackendLogicalSnapshot row).

Changelog: n/a
Guide: n/a
Known-Discrepancies: n/a
S3K-Known-Discrepancies: n/a
Agent-Docs: n/a
Configuration-Docs: n/a
Skills: n/a

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: Add captureClockSnapshot / restoreClockSnapshot to runtime interface

**Files:**
- Modify: `src/main/java/com/openggf/audio/runtime/DeterministicAudioRuntime.java`
- Modify: `src/main/java/com/openggf/audio/runtime/StreamBackedDeterministicAudioRuntime.java`
- Modify: `src/test/java/com/openggf/audio/runtime/TestStreamBackedDeterministicAudioRuntime.java`

- [ ] **Step 1: Write failing tests**

Append to `TestStreamBackedDeterministicAudioRuntime.java` (use the existing `import` block; `AudioFrameClock` is already accessible):

```java
    @Test
    void captureClockSnapshotReturnsCurrentClockState() {
        AudioFrameClock clock = new AudioFrameClock(120, 60);
        AudioOutputFifo fifo = new AudioOutputFifo(120);
        StreamBackedDeterministicAudioRuntime runtime =
                new StreamBackedDeterministicAudioRuntime(clock, fifo);
        runtime.setMusicStream(new SequenceStream(0, 0));
        runtime.advanceFrame(1, FrameAudioMode.NORMAL);
        runtime.advanceFrame(2, FrameAudioMode.NORMAL);

        AudioFrameClock.Snapshot snap = runtime.captureClockSnapshot();
        assertEquals(clock.totalSamplesProduced(), snap.totalSamplesProduced());
        assertEquals(clock.remainder(), snap.remainder());
    }

    @Test
    void restoreClockSnapshotRewindsTheClock() {
        AudioFrameClock clock = new AudioFrameClock(120, 60);
        AudioOutputFifo fifo = new AudioOutputFifo(120);
        StreamBackedDeterministicAudioRuntime runtime =
                new StreamBackedDeterministicAudioRuntime(clock, fifo);
        runtime.setMusicStream(new SequenceStream(0, 0));
        runtime.advanceFrame(1, FrameAudioMode.NORMAL);
        AudioFrameClock.Snapshot atOne = runtime.captureClockSnapshot();
        runtime.advanceFrame(2, FrameAudioMode.NORMAL);
        runtime.advanceFrame(3, FrameAudioMode.NORMAL);

        runtime.restoreClockSnapshot(atOne);
        assertEquals(atOne.totalSamplesProduced(), runtime.captureClockSnapshot().totalSamplesProduced());
    }

    @Test
    void noOpRuntimeClockSnapshotIsNull() {
        assertNull(NoOpDeterministicAudioRuntime.INSTANCE.captureClockSnapshot());
    }
```

You will also need `import static org.junit.jupiter.api.Assertions.assertNull;` in the file's static imports if not already present.

- [ ] **Step 2: Run, expect failures**

`mvn "-Dtest=TestStreamBackedDeterministicAudioRuntime" test`
Expected: three new tests fail (methods not defined).

- [ ] **Step 3: Add interface defaults**

In `src/main/java/com/openggf/audio/runtime/DeterministicAudioRuntime.java`, add (top of file: `import com.openggf.audio.runtime.AudioFrameClock;` is unnecessary because the type is in the same package; add the two defaults next to the other defaults):

```java
    default AudioFrameClock.Snapshot captureClockSnapshot() {
        return null;
    }

    default void restoreClockSnapshot(AudioFrameClock.Snapshot snapshot) {
    }
```

- [ ] **Step 4: Override in `StreamBackedDeterministicAudioRuntime`**

Add inside `StreamBackedDeterministicAudioRuntime.java`, alongside the other `@Override` methods (after `hasActivePresentation`):

```java
    @Override
    public AudioFrameClock.Snapshot captureClockSnapshot() {
        return frameClock.captureSnapshot();
    }

    @Override
    public void restoreClockSnapshot(AudioFrameClock.Snapshot snapshot) {
        if (snapshot != null) {
            frameClock.restoreSnapshot(snapshot);
        }
    }
```

- [ ] **Step 5: Run all audio tests**

`mvn "-Dtest=com.openggf.audio.**" test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/openggf/audio/runtime/DeterministicAudioRuntime.java \
        src/main/java/com/openggf/audio/runtime/StreamBackedDeterministicAudioRuntime.java \
        src/test/java/com/openggf/audio/runtime/TestStreamBackedDeterministicAudioRuntime.java
git commit -m "$(cat <<'EOF'
feat(runtime): capture/restore AudioFrameClock state on the runtime

Adds DeterministicAudioRuntime.captureClockSnapshot / restoreClockSnapshot.
NoOp keeps the defaults (null / no-op). StreamBacked round-trips
through its private AudioFrameClock so the reverse resynth burst loop
can rewind the clock to a keyframe's audio-frame index before
forward-stepping.

Spec: docs/superpowers/specs/2026-05-26-reverse-resynth-design.md
(Architecture, DeterministicAudioRuntime row).

Changelog: n/a
Guide: n/a
Known-Discrepancies: n/a
S3K-Known-Discrepancies: n/a
Agent-Docs: n/a
Configuration-Docs: n/a
Skills: n/a

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: AudioManager.captureLogicalSnapshot records the clock snapshot

**Files:**
- Modify: `src/main/java/com/openggf/audio/AudioManager.java`
- Create: `src/test/java/com/openggf/audio/TestAudioManagerCapturesClockSnapshot.java`

- [ ] **Step 1: Write failing test**

```java
package com.openggf.audio;

import com.openggf.audio.rewind.AudioLogicalSnapshot;
import com.openggf.audio.runtime.AudioFrameClock;
import com.openggf.audio.runtime.AudioOutputFifo;
import com.openggf.audio.runtime.DeterministicAudioRuntime;
import com.openggf.audio.runtime.NoOpDeterministicAudioRuntime;
import com.openggf.audio.runtime.StreamBackedDeterministicAudioRuntime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class TestAudioManagerCapturesClockSnapshot {

    @AfterEach
    void tearDown() {
        AudioManager.getInstance().resetState();
        AudioManager.getInstance().setBackend(new NullAudioBackend());
    }

    @Test
    void streamBackedRuntimePopulatesClockSnapshot() {
        AudioManager audio = AudioManager.getInstance();
        audio.resetState();
        DeterministicAudioRuntime runtime = new StreamBackedDeterministicAudioRuntime(
                new AudioFrameClock(120, 60), new AudioOutputFifo(120));
        audio.setDeterministicAudioRuntime(runtime);
        audio.setBackend(new NullAudioBackend());

        AudioLogicalSnapshot snap = audio.captureLogicalSnapshot();
        assertNotNull(snap.backend().clockSnapshot(),
                "StreamBacked runtime must expose its clock snapshot via the backend snapshot");
    }

    @Test
    void noOpRuntimeLeavesClockSnapshotNull() {
        AudioManager audio = AudioManager.getInstance();
        audio.resetState();
        audio.setDeterministicAudioRuntime(NoOpDeterministicAudioRuntime.INSTANCE);
        audio.setBackend(new NullAudioBackend());

        AudioLogicalSnapshot snap = audio.captureLogicalSnapshot();
        assertNull(snap.backend().clockSnapshot(),
                "NoOp runtime returns null clock snapshot, AudioManager must propagate that");
    }
}
```

- [ ] **Step 2: Run, expect failure**

`mvn "-Dtest=TestAudioManagerCapturesClockSnapshot" test`
Expected: FAIL (`clockSnapshot()` returns null even with StreamBacked, because AudioManager isn't populating it).

- [ ] **Step 3: Update `AudioManager.captureLogicalSnapshot`**

Locate `captureLogicalSnapshot()` in `AudioManager.java`. The current code reads `backend.captureLogicalSnapshot()` directly. Replace that call with a wrapper that injects the clock snapshot:

Find the body that constructs the `AudioBackendLogicalSnapshot` (look for either `backend.captureLogicalSnapshot()` or the direct `new AudioBackendLogicalSnapshot(...)` call). Wrap the returned snapshot so it carries the clock:

Add a helper method to `AudioManager`:

```java
    private com.openggf.audio.rewind.AudioBackendLogicalSnapshot captureBackendSnapshotWithClock() {
        com.openggf.audio.rewind.AudioBackendLogicalSnapshot base = backend != null
                ? backend.captureLogicalSnapshot()
                : com.openggf.audio.rewind.AudioBackendLogicalSnapshot.empty();
        com.openggf.audio.runtime.AudioFrameClock.Snapshot clock =
                deterministicAudioRuntime.captureClockSnapshot();
        if (clock == null) {
            return base;
        }
        return new com.openggf.audio.rewind.AudioBackendLogicalSnapshot(
                base.currentMusic(),
                base.sfxBlocked(),
                base.pendingRestore(),
                base.speedShoesEnabled(),
                base.speedMultiplier(),
                base.overrideStack(),
                base.musicDriver(),
                base.standaloneSfxDriver(),
                clock);
    }
```

Then replace the existing `backend.captureLogicalSnapshot()` (or `backend != null ? backend.captureLogicalSnapshot() : AudioBackendLogicalSnapshot.empty()`) call inside `captureLogicalSnapshot()` with `captureBackendSnapshotWithClock()`. Be careful: there is also a `currentBackendLogicalSnapshot()` private helper around line 475 — DO NOT change that one; it's used by `restoreLogicalSnapshot` paths that must not promote a stale clock back into runtime state.

- [ ] **Step 4: `restoreLogicalSnapshot` deliberately does NOT restore the clock**

Inspect `AudioManager.restoreLogicalSnapshot`. Do NOT call `deterministicAudioRuntime.restoreClockSnapshot` from it. The spec calls this out: the clock snapshot is read by `ReverseResynthesizer` for per-burst lookups only; restoring it from `restoreLogicalSnapshot` would leave the clock pointing at an old keyframe's audio-frame count at the end of a normal rewind seek, breaking subsequent forward audio-frame indexing.

Add a comment near the `backend.restoreLogicalSnapshot(...)` call in `restoreLogicalSnapshot` explaining this:

```java
// Deliberately do NOT call deterministicAudioRuntime.restoreClockSnapshot here.
// The clock snapshot rides on AudioBackendLogicalSnapshot purely so the
// ReverseResynthesizer can look up audio-frame indices for each keyframe
// during a burst — restoring it here would corrupt forward audio-frame
// indexing for any subsequent normal-rewind seek.
```

- [ ] **Step 5: Run**

`mvn "-Dtest=TestAudioManagerCapturesClockSnapshot,TestAudioManagerRewindSuppression,TestAudioManagerResetState,TestAudioManagerRuntimeInstallation,TestAudioManagerFrameMonotonic" test`
Expected: all PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/openggf/audio/AudioManager.java \
        src/test/java/com/openggf/audio/TestAudioManagerCapturesClockSnapshot.java
git commit -m "$(cat <<'EOF'
feat(audio): AudioManager captures runtime clock into backend snapshot

AudioManager.captureLogicalSnapshot now reads
deterministicAudioRuntime.captureClockSnapshot() and stores it on the
AudioBackendLogicalSnapshot the caller sees. restoreLogicalSnapshot
intentionally does NOT restore the clock; ReverseResynthesizer pulls
the clock snapshot off the keyframe directly when stepping chips
forward inside a burst.

Spec: docs/superpowers/specs/2026-05-26-reverse-resynth-design.md
(Architecture, AudioManager.captureLogicalSnapshot row).

Changelog: n/a
Guide: n/a
Known-Discrepancies: n/a
S3K-Known-Discrepancies: n/a
Agent-Docs: n/a
Configuration-Docs: n/a
Skills: n/a

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: PcmHistoryRing.prependBackward + ReverseCursor.extendOldestTo

**Files:**
- Modify: `src/main/java/com/openggf/audio/runtime/PcmHistoryRing.java`
- Modify: `src/test/java/com/openggf/audio/runtime/TestPcmHistoryRing.java`

- [ ] **Step 1: Write failing tests**

Append to `TestPcmHistoryRing.java`:

```java
    @Test
    void prependBackwardExtendsReadableWindow() {
        PcmHistoryRing ring = new PcmHistoryRing(16);
        // Write 4 forward frames: samples 0..7.
        short[] forward = {0, 1, 2, 3, 4, 5, 6, 7};
        ring.write(forward, 4);

        PcmHistoryRing.ReverseCursor cursor = ring.createReverseCursor();
        // Read 2 frames to advance the cursor past those slots.
        short[] readBack = new short[4];
        cursor.readPrevious(readBack, 2);
        // Now cursor.nextReadableFrame=1, oldestReadableFrame=0. Two slots consumed.

        // Prepend 4 older frames at logical indices [-4, -3, -2, -1] (adjacency
        // requirement: startAudioFrame + frames == cursor.oldestReadableFrame).
        short[] older = {-1, -10, -2, -20, -3, -30, -4, -40};
        ring.prependBackward(-4L, cursor, older, 4);

        // Now reverse-read the remaining 6 frames: [1 right after the read],
        // [0], then [-1], [-2], [-3], [-4].
        short[] tail = new short[12];
        int read = cursor.readPrevious(tail, 6);
        assertEquals(6, read);
        assertArrayEquals(new short[] {
                2, 3,        // forward frame 1
                0, 1,        // forward frame 0
                -1, -10,     // prepended frame -1
                -2, -20,     // prepended frame -2
                -3, -30,     // prepended frame -3
                -4, -40      // prepended frame -4
        }, tail);
    }

    @Test
    void prependBackwardRejectsNonAdjacentStart() {
        PcmHistoryRing ring = new PcmHistoryRing(16);
        ring.write(new short[]{0, 0, 0, 0}, 2);
        PcmHistoryRing.ReverseCursor cursor = ring.createReverseCursor();
        short[] payload = new short[4];
        // oldestReadableFrame is 0. Adjacency requires startAudioFrame + frames == 0.
        // Passing startAudioFrame=-3, frames=2 -> -3+2 = -1 != 0.
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> ring.prependBackward(-3L, cursor, payload, 2));
    }

    @Test
    void prependBackwardRejectsSpanExceedingCapacity() {
        PcmHistoryRing ring = new PcmHistoryRing(8);
        ring.write(new short[]{0, 0, 0, 0, 0, 0, 0, 0}, 4);
        PcmHistoryRing.ReverseCursor cursor = ring.createReverseCursor();
        // unread = nextReadableFrame - oldestReadableFrame + 1 = 4. Capacity = 8.
        // Adding 5 more frames at the front makes total span 4 + 5 = 9 > 8.
        short[] payload = new short[10];
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> ring.prependBackward(cursor.oldestReadableFrame() - 5L, cursor, payload, 5));
    }
```

- [ ] **Step 2: Run, expect failure**

`mvn "-Dtest=TestPcmHistoryRing" test`
Expected: FAIL on the three new tests (method missing).

- [ ] **Step 3: Make ReverseCursor.oldestReadableFrame mutable + add `extendOldestTo`, `oldestReadableFrame()`, `nextReadableFrame()`, `maxPrependableFrames()` accessors**

Edit `PcmHistoryRing.java`:

- Change `private final long oldestReadableFrame;` to `private long oldestReadableFrame;` inside the `ReverseCursor` class.
- Add (inside `ReverseCursor` — note it's a non-static inner class so it can read the outer `capacityFrames` directly):

```java
        public long oldestReadableFrame() {
            return oldestReadableFrame;
        }

        public long nextReadableFrame() {
            return nextReadableFrame;
        }

        /**
         * Returns the maximum number of frames a caller can safely
         * {@link PcmHistoryRing#prependBackward} given the current cursor
         * state. The ring is bounded; once the unread window equals
         * {@code capacityFrames}, no more frames can be prepended until the
         * cursor consumes some via {@link #readPrevious}. Returns 0 in that
         * case so the caller (typically {@code ReverseResynthesizer}) can
         * back off and let {@code drainPcm} make progress before retrying.
         */
        public int maxPrependableFrames() {
            long unread = nextReadableFrame - oldestReadableFrame + 1;
            long room = capacityFrames - unread;
            if (room <= 0) {
                return 0;
            }
            return (int) Math.min(room, (long) Integer.MAX_VALUE);
        }

        void extendOldestTo(long newOldest) {
            if (newOldest > oldestReadableFrame) {
                throw new IllegalArgumentException(
                        "extendOldestTo must lower oldestReadableFrame; got "
                                + newOldest + " > current " + oldestReadableFrame);
            }
            oldestReadableFrame = newOldest;
        }
```

- [ ] **Step 4: Add `prependBackward` to `PcmHistoryRing`**

Add (after `write`):

```java
    /**
     * Writes {@code frames} stereo frames into the ring at logical indices
     * {@code [startAudioFrame, startAudioFrame + frames)}, where
     * {@code startAudioFrame + frames == cursor.oldestReadableFrame}. Lowers
     * the cursor's {@code oldestReadableFrame} so subsequent
     * {@link ReverseCursor#readPrevious} calls can drain the prepended range.
     *
     * <p>Invariants enforced:
     * <ol>
     *   <li>Adjacency: {@code startAudioFrame + frames == cursor.oldestReadableFrame}
     *       so the new range is contiguous below the current readable window.</li>
     *   <li>Physical-slot safety: the total span after prepend
     *       ({@code cursor.nextReadableFrame - startAudioFrame + 1}) must not
     *       exceed {@link #capacityFrames}, or the new writes would land in
     *       slots whose contents the cursor has not yet read.</li>
     * </ol>
     */
    public void prependBackward(long startAudioFrame, ReverseCursor cursor, short[] source, int frames) {
        if (cursor == null) {
            throw new IllegalArgumentException("cursor must not be null");
        }
        validateBuffer(source, frames);
        if (startAudioFrame + frames != cursor.oldestReadableFrame) {
            throw new IllegalArgumentException(
                    "prependBackward range must be adjacent to cursor.oldestReadableFrame; got start="
                            + startAudioFrame + " frames=" + frames
                            + " cursor.oldestReadableFrame=" + cursor.oldestReadableFrame);
        }
        long span = cursor.nextReadableFrame - startAudioFrame + 1;
        if (span > capacityFrames) {
            throw new IllegalArgumentException(
                    "prependBackward span would exceed ring capacity (would overwrite unread slots): span="
                            + span + " capacity=" + capacityFrames);
        }
        for (int i = 0; i < frames; i++) {
            long logicalIndex = startAudioFrame + i;
            int slot = ringSlot(logicalIndex) * CHANNELS;
            samples[slot] = source[i * CHANNELS];
            samples[slot + 1] = source[i * CHANNELS + 1];
        }
        cursor.extendOldestTo(startAudioFrame);
    }
```

- [ ] **Step 5: Run, expect PASS**

`mvn "-Dtest=TestPcmHistoryRing" test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/openggf/audio/runtime/PcmHistoryRing.java \
        src/test/java/com/openggf/audio/runtime/TestPcmHistoryRing.java
git commit -m "$(cat <<'EOF'
feat(runtime): PcmHistoryRing.prependBackward extends reverse window

prependBackward writes older PCM into the ring below the cursor's
oldestReadableFrame and lowers the cursor floor so subsequent
readPrevious calls drain the prepended range. Enforces adjacency
(start + frames == cursor.oldestReadableFrame) and physical-slot
safety (cursor.nextReadableFrame - start + 1 <= capacity) so the
new writes can never land on slots the cursor has not yet read.

ReverseCursor exposes oldestReadableFrame(), nextReadableFrame(), and
maxPrependableFrames() accessors plus a package-private extendOldestTo
so the prepend path can lower the floor. maxPrependableFrames returns
how many frames can be safely prepended given the cursor's current
unread span — used by ReverseResynthesizer.runOneBurst to cap each
burst by the ring's physical-slot budget so a full ring is a clean
no-op instead of an IllegalArgumentException.

Spec: docs/superpowers/specs/2026-05-26-reverse-resynth-design.md
(Architecture rows for PcmHistoryRing and ReverseCursor; Edge case 8).

Changelog: n/a
Guide: n/a
Known-Discrepancies: n/a
S3K-Known-Discrepancies: n/a
Agent-Docs: n/a
Configuration-Docs: n/a
Skills: n/a

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: AudioReplayReason.REVERSE_RESYNTH

**Files:**
- Modify: `src/main/java/com/openggf/audio/rewind/AudioReplayReason.java`

- [ ] **Step 1: Add the value**

Replace the enum body with:

```java
package com.openggf.audio.rewind;

public enum AudioReplayReason {
    SEEK,
    STEP_BACKWARD,
    SEGMENT_EXPANSION,
    REVERSE_RESYNTH
}
```

- [ ] **Step 2: Compile**

`mvn test-compile`
Expected: clean.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/openggf/audio/rewind/AudioReplayReason.java
git commit -m "$(cat <<'EOF'
feat(rewind): add REVERSE_RESYNTH replay reason

Reserved enum value used by ReverseResynthesizer when calling
audioManager.beginRewindReplay and by replayTimelineCommand to route
PlaySfx directly to the chip path instead of the live backend
playSfx* method.

Spec: docs/superpowers/specs/2026-05-26-reverse-resynth-design.md
(Architecture, AudioReplayReason row).

Changelog: n/a
Guide: n/a
Known-Discrepancies: n/a
S3K-Known-Discrepancies: n/a
Agent-Docs: n/a
Configuration-Docs: n/a
Skills: n/a

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: AudioKeyframeStore — audio-frame lookup + replayLiveTo

**Files:**
- Modify: `src/main/java/com/openggf/audio/rewind/AudioKeyframeStore.java`
- Modify (or extend) tests in `src/test/java/com/openggf/audio/rewind/` (look for existing `TestAudioKeyframeStore*` style file; if none exists, create `TestAudioKeyframeStoreReverseResynth.java`).

- [ ] **Step 1: Inspect AudioKeyframeStore**

Open `src/main/java/com/openggf/audio/rewind/AudioKeyframeStore.java`. Note the existing `capture`, `keyframeAtOrBefore(long)`, `replayTo`, `replayToLogicalState` methods. We will add two new public methods that key off audio-frame index.

- [ ] **Step 2: Add `keyframeAtOrBeforeAudioFrame` and `replayCommandsAtGameFrame`**

Add inside the class (after the existing `replayToLogicalState` method):

```java
    /**
     * Returns the latest keyframe whose {@code backend().clockSnapshot()}
     * places its captured audio-frame index at or before {@code audioFrame}.
     * Returns null if no keyframe in the store has a clock snapshot, or if
     * the earliest captured keyframe sits at a higher audio-frame index than
     * the requested one. Audio-frame indices are read from each keyframe's
     * {@code AudioFrameClock.Snapshot.totalSamplesProduced()} value.
     */
    public AudioLogicalSnapshot keyframeAtOrBeforeAudioFrame(long audioFrame) {
        AudioLogicalSnapshot best = null;
        long bestAudio = Long.MIN_VALUE;
        for (AudioLogicalSnapshot snapshot : keyframes.values()) {
            if (snapshot == null || snapshot.backend() == null
                    || snapshot.backend().clockSnapshot() == null) {
                continue;
            }
            long candidate = snapshot.backend().clockSnapshot().totalSamplesProduced();
            if (candidate <= audioFrame && candidate >= bestAudio) {
                best = snapshot;
                bestAudio = candidate;
            }
        }
        return best;
    }

    /**
     * Dispatches any timeline entries whose game-frame equals
     * {@code atGameFrame}, in submission order, via
     * {@link AudioManager#replayTimelineCommand} under the
     * {@link AudioReplayReason#REVERSE_RESYNTH} scope. Returns the number of
     * commands replayed.
     *
     * <p>Caller contract: this method is invoked once per game-frame inside
     * the burst loop with monotonically increasing {@code atGameFrame}
     * values. It must not re-dispatch entries at earlier frames — the
     * burst loop relies on this single-frame semantic to avoid duplicating
     * SFX commands that already mutated the chip on a prior iteration. The
     * keyframe state is restored once at burst start; from there, this
     * method walks forward command-by-command exactly as the live game did.
     *
     * <p>Entries are stored in {@code AudioCommandTimeline} sorted by frame
     * (then by order within a frame). The early-exit on
     * {@code entry.frame() > atGameFrame} relies on that invariant.
     */
    public int replayCommandsAtGameFrame(AudioManager audio,
                                          AudioLogicalSnapshot keyframe,
                                          long atGameFrame) {
        Objects.requireNonNull(audio, "audio");
        Objects.requireNonNull(keyframe, "keyframe");
        if (keyframe.commandTimelineFrame() > atGameFrame) {
            return 0;
        }
        int replayed = 0;
        try (AudioReplayScope ignored = audio.beginRewindReplay(
                Math.toIntExact(keyframe.commandTimelineFrame()),
                Math.toIntExact(atGameFrame),
                AudioReplayReason.REVERSE_RESYNTH)) {
            List<AudioTimelineEntry> entries = audio.commandTimeline().entries();
            for (int i = keyframe.commandEntryCount(); i < entries.size(); i++) {
                AudioTimelineEntry entry = entries.get(i);
                if (entry.frame() < atGameFrame) {
                    continue;
                }
                if (entry.frame() > atGameFrame) {
                    break;
                }
                audio.replayTimelineCommand(entry.command());
                replayed++;
            }
        }
        return replayed;
    }
```

- [ ] **Step 3: Add a focused test**

Create `src/test/java/com/openggf/audio/rewind/TestAudioKeyframeStoreReverseResynth.java`:

```java
package com.openggf.audio.rewind;

import com.openggf.audio.AudioManager;
import com.openggf.audio.NullAudioBackend;
import com.openggf.audio.runtime.AudioFrameClock;
import com.openggf.audio.runtime.AudioOutputFifo;
import com.openggf.audio.runtime.StreamBackedDeterministicAudioRuntime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class TestAudioKeyframeStoreReverseResynth {

    @AfterEach
    void tearDown() {
        AudioManager.getInstance().resetState();
        AudioManager.getInstance().setBackend(new NullAudioBackend());
    }

    @Test
    void keyframeAtOrBeforeAudioFrameReturnsNullWhenNoSnapshotsHaveClock() {
        AudioKeyframeStore store = new AudioKeyframeStore();
        // No captures: store is empty. Should return null.
        assertNull(store.keyframeAtOrBeforeAudioFrame(0L));
    }

    @Test
    void keyframeAtOrBeforeAudioFramePicksLargestNotExceedingTarget() {
        AudioManager audio = AudioManager.getInstance();
        audio.resetState();
        audio.setDeterministicAudioRuntime(new StreamBackedDeterministicAudioRuntime(
                new AudioFrameClock(120, 60), new AudioOutputFifo(120)));
        audio.setBackend(new NullAudioBackend());

        AudioKeyframeStore store = new AudioKeyframeStore();
        // First keyframe at audioFrame=0.
        store.capture(0L, audio);
        // Advance the runtime clock by stepping a few frames (2 samples per
        // frame at 120Hz / 60fps).
        audio.advanceGameplayFrameAudio();
        audio.advanceGameplayFrameAudio();
        // Second keyframe at audioFrame=4 (2 frames × 2 samples).
        store.capture(2L, audio);
        audio.advanceGameplayFrameAudio();
        audio.advanceGameplayFrameAudio();
        // Third keyframe at audioFrame=8.
        store.capture(4L, audio);

        AudioLogicalSnapshot ten = store.keyframeAtOrBeforeAudioFrame(10L);
        assertNotNull(ten);
        assertEquals(8L, ten.backend().clockSnapshot().totalSamplesProduced());

        AudioLogicalSnapshot six = store.keyframeAtOrBeforeAudioFrame(6L);
        assertNotNull(six);
        assertEquals(4L, six.backend().clockSnapshot().totalSamplesProduced());

        AudioLogicalSnapshot zero = store.keyframeAtOrBeforeAudioFrame(0L);
        assertNotNull(zero);
        assertEquals(0L, zero.backend().clockSnapshot().totalSamplesProduced());

        AudioLogicalSnapshot negative = store.keyframeAtOrBeforeAudioFrame(-1L);
        assertNull(negative,
                "Requesting an audio frame earlier than the earliest keyframe must return null");
    }
}
```

- [ ] **Step 4: Run**

`mvn "-Dtest=TestAudioKeyframeStoreReverseResynth" test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/openggf/audio/rewind/AudioKeyframeStore.java \
        src/test/java/com/openggf/audio/rewind/TestAudioKeyframeStoreReverseResynth.java
git commit -m "$(cat <<'EOF'
feat(rewind): AudioKeyframeStore audio-frame lookup + per-frame replay

keyframeAtOrBeforeAudioFrame scans recorded keyframes for the largest
audio-frame index not exceeding a requested target, using each
keyframe's AudioBackendLogicalSnapshot.clockSnapshot.
replayCommandsAtGameFrame dispatches only the timeline entries whose
game-frame equals a specific value, in submission order, via
replayTimelineCommand under the REVERSE_RESYNTH scope. The single-
frame semantic is critical: ReverseResynthesizer calls this once per
game-frame as it walks forward through a burst, and re-replaying
entries from keyframe.commandEntryCount() on every iteration would
duplicate SFX commands and corrupt chip state. Both methods are
inputs to ReverseResynthesizer.

Spec: docs/superpowers/specs/2026-05-26-reverse-resynth-design.md
(Architecture, AudioKeyframeStore row).

Changelog: n/a
Guide: n/a
Known-Discrepancies: n/a
S3K-Known-Discrepancies: n/a
Agent-Docs: n/a
Configuration-Docs: n/a
Skills: n/a

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 8: REVERSE_RESYNTH dispatch in AudioManager.replayTimelineCommand

**Files:**
- Modify: `src/main/java/com/openggf/audio/AudioManager.java`
- Create: `src/test/java/com/openggf/audio/TestAudioManagerReverseResynthDispatch.java`

The spec requires that SFX timeline entries between a burst's restored keyframe and its target game-frame **mutate the live SMPS chip state** so the synthesized PCM reflects SFX that began after the keyframe was captured. A no-op would drop all SFX inside the synth window — the keyframe's `SmpsDriverSnapshot` only encodes state as of the keyframe.

Re-read of `LWJGLAudioBackend.playSfxSmps` confirms the SMPS-route side effects are exactly what we want during a burst:
- Creates an `SmpsSequencer` for the SFX and adds it to the active `SmpsDriver` (music) or creates / re-uses the standalone SFX driver.
- Updates continuous-SFX tracking (extend vs. restart).
- May call `deterministicAudioRuntime.setSfxStream(sfxDriver)` if a fresh standalone driver was created.

All of those mutations get rolled back at rewind end by the Task 9 rewind-bracket `restoreLogicalSnapshot`, so they are safe to perform inside a burst. The only sites that need explicit gating under `REVERSE_RESYNTH` are the **WAV-fallback** routes (`AudioCommand.SfxRoute.FALLBACK_NAME` and `RING_RESOLVED`), which would allocate new persistent OpenAL sources via `alGenSources` and play a `.wav` from disk — neither is reproducible inside a held-rewind synth window. Those are silently skipped per spec edge case 9.

`PlayMusic` commands inside the burst go through `backend.playSmps` unchanged — that's the live behavior the burst is reproducing, and the bracket cleans up. The REVERSE_RESYNTH scope mainly serves as a marker so the WAV-fallback branch can branch off.

- [ ] **Step 1: Read `AudioManager.replayTimelineCommand` and `replaySfx`**

Find the `replayTimelineCommand` method. Inspect each `case` arm. Locate `replaySfx` and `replayMusic`. Note that `replaySfx` routes via `backend.playSfxSmps(...)` and that `audioManager.beginRewindReplay` already increments a `rewindReplaySuppressionDepth`. Existing logic gates `recordTimelineCommand` and `sendLiveBackendCommands()` based on suppression depth.

Inspect `beginRewindReplay` to confirm it accepts an `AudioReplayReason` and tracks the active reason on a stack/field.

- [ ] **Step 2: Track the active replay reason**

Add a field on `AudioManager`:

```java
    private AudioReplayReason currentReplayReason;
```

Update `beginRewindReplay` to set this field on enter and clear it on close:

```java
    public AudioReplayScope beginRewindReplay(int fromFrame, int targetFrame, AudioReplayReason reason) {
        AudioReplayReason previous = currentReplayReason;
        currentReplayReason = reason;
        rewindReplaySuppressionDepth++;
        return () -> {
            if (rewindReplaySuppressionDepth > 0) {
                rewindReplaySuppressionDepth--;
            }
            currentReplayReason = previous;
        };
    }
```

(Adjust to match the existing return-shape — preserve any AutoCloseable contract already used; the closure body must restore `currentReplayReason = previous` and decrement the depth.)

- [ ] **Step 3: Branch `replaySfx` for REVERSE_RESYNTH WAV-fallback only**

In `replaySfx` (find the `case PlaySfx` arm in `replayTimelineCommand`), add an early-skip for WAV-fallback routes under REVERSE_RESYNTH. The SMPS routes (`BASE_SMPS_ID`, `BASE_SMPS_NAME`, `DONOR_SMPS`) keep their existing `backend.playSfxSmps` dispatch.

Locate the `switch (command.route())` block in `replaySfx`. Add a guard at the top:

```java
    private void replaySfx(AudioCommand.PlaySfx command) {
        if (currentReplayReason == AudioReplayReason.REVERSE_RESYNTH
                && (command.route() == AudioCommand.SfxRoute.FALLBACK_NAME
                    || command.route() == AudioCommand.SfxRoute.RING_RESOLVED)) {
            // WAV-fallback SFX would allocate new persistent OpenAL sources
            // and play a .wav from disk. Neither is reproducible inside a
            // held-rewind synth window. Spec edge case 9: explicitly out of
            // scope for the faithful tape effect.
            return;
        }
        switch (command.route()) {
            // ...existing switch body unchanged: SMPS routes flow through
            // backend.playSfxSmps so the chip state evolves to match the
            // SFX that fired between this game-frame and the keyframe...
        }
    }
```

No other branching is required — the SMPS routes' `backend.playSfxSmps` call correctly mutates the live `SmpsDriver` / standalone SFX driver chip state, which is what the burst loop needs. The rewind-bracket save/restore added in Task 9 cleans up any driver-allocation side effects when the rewind session ends.

- [ ] **Step 4: Write a regression test**

```java
package com.openggf.audio;

import com.openggf.audio.rewind.AudioCommand;
import com.openggf.audio.rewind.AudioReplayReason;
import com.openggf.audio.rewind.AudioReplayScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestAudioManagerReverseResynthDispatch {

    private AudioManager audio;
    private AudioTestFixtures.RecordingAudioBackend backend;

    @BeforeEach
    void setUp() {
        audio = AudioManager.getInstance();
        audio.resetState();
        backend = new AudioTestFixtures.RecordingAudioBackend();
        audio.setBackend(backend);
        AudioTestFixtures.StubSmpsLoader loader = new AudioTestFixtures.StubSmpsLoader();
        loader.namedSfxResults.put("JUMP", new AudioTestFixtures.StubSmpsData("jump"));
        audio.setAudioProfile(new AudioTestFixtures.StubAudioProfile(
                loader, 0xF0, 0xF1, GameAudioProfile.SpeedMode.FRAME_MULTIPLY));
        audio.setRom(null);
        audio.setSoundMap(new EnumMap<>(GameSound.class));
    }

    @AfterEach
    void tearDown() {
        audio.resetState();
    }

    @Test
    void smpsSfxRouteFiresBackendUnderBothScopes() {
        // SEEK baseline: SMPS-route SFX dispatches to backend.playSfxSmps.
        try (AudioReplayScope ignored = audio.beginRewindReplay(10, 4, AudioReplayReason.SEEK)) {
            audio.replayTimelineCommand(new AudioCommand.PlaySfx(
                    -1, "JUMP", AudioCommand.SfxRoute.BASE_SMPS_NAME, 1.0f, null));
        }
        int seekCalls = backend.playSfxSmpsCalls;

        // REVERSE_RESYNTH: SMPS-route SFX must also dispatch to backend.playSfxSmps
        // so the chip state evolves to reflect SFX that fired after the keyframe.
        try (AudioReplayScope ignored = audio.beginRewindReplay(10, 4, AudioReplayReason.REVERSE_RESYNTH)) {
            audio.replayTimelineCommand(new AudioCommand.PlaySfx(
                    -1, "JUMP", AudioCommand.SfxRoute.BASE_SMPS_NAME, 1.0f, null));
        }
        assertEquals(seekCalls + 1, backend.playSfxSmpsCalls,
                "REVERSE_RESYNTH SMPS SFX must mutate the chip via backend.playSfxSmps");
    }

    @Test
    void wavFallbackSfxIsSilentNoOpUnderReverseResynth() {
        int playSfxCallsBefore = backend.playSfxCalls;
        try (AudioReplayScope ignored = audio.beginRewindReplay(10, 4, AudioReplayReason.REVERSE_RESYNTH)) {
            audio.replayTimelineCommand(new AudioCommand.PlaySfx(
                    -1, "JUMP", AudioCommand.SfxRoute.FALLBACK_NAME, 1.0f, null));
        }
        assertEquals(playSfxCallsBefore, backend.playSfxCalls,
                "WAV-fallback SFX must be skipped under REVERSE_RESYNTH (spec edge case 9)");
    }

    @Test
    void wavFallbackSfxFiresNormallyUnderSeek() {
        int playSfxCallsBefore = backend.playSfxCalls;
        try (AudioReplayScope ignored = audio.beginRewindReplay(10, 4, AudioReplayReason.SEEK)) {
            audio.replayTimelineCommand(new AudioCommand.PlaySfx(
                    -1, "JUMP", AudioCommand.SfxRoute.FALLBACK_NAME, 1.0f, null));
        }
        assertEquals(playSfxCallsBefore + 1, backend.playSfxCalls,
                "WAV-fallback SFX continues to fire normally under non-resynth replay scopes");
    }
}
```

(If `RecordingAudioBackend` doesn't already track `playSfxSmpsCalls` / `playSfxCalls`, add those counters in the fixture or use the closest existing counter and adjust the assertions.)

- [ ] **Step 5: Run**

`mvn "-Dtest=TestAudioManagerReverseResynthDispatch,TestAudioManagerRewindSuppression" test`
Expected: PASS. Existing rewind suppression behavior must remain green.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/openggf/audio/AudioManager.java \
        src/test/java/com/openggf/audio/TestAudioManagerReverseResynthDispatch.java
git commit -m "$(cat <<'EOF'
feat(audio): REVERSE_RESYNTH skips WAV-fallback SFX, mutates SMPS chip

AudioManager tracks the active replay reason on its rewind-replay
stack. Under REVERSE_RESYNTH:
- SMPS-route PlaySfx commands flow through backend.playSfxSmps as
  normal, so the live SmpsDriver / standalone SFX driver chip state
  evolves to match SFX that fired after the keyframe — the keyframe
  snapshot only encodes state up to that moment, and the burst loop
  needs the chips to reflect later SFX as it forward-steps them.
- WAV-fallback routes (FALLBACK_NAME, RING_RESOLVED) are silent
  no-ops: replaying them would allocate persistent OpenAL sources and
  play a .wav from disk, neither reproducible inside a held-rewind
  synth window. Spec edge case 9.

The rewind-bracket save/restore in Task 9 rolls back the SmpsDriver
mutations at endReverseAudioPresentation, so the SMPS dispatch is
safe to perform without leaking state past the held-rewind session.

Spec: docs/superpowers/specs/2026-05-26-reverse-resynth-design.md
(Architecture, AudioManager.replayTimelineCommand row; Edge case 9;
Known Open Points #1).

Changelog: n/a
Guide: n/a
Known-Discrepancies: n/a
S3K-Known-Discrepancies: n/a
Agent-Docs: n/a
Configuration-Docs: n/a
Skills: n/a

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 9: Rewind-bracket save/restore for SFX driver state

**Files:**
- Modify: `src/main/java/com/openggf/audio/AudioManager.java`

The spec's P1.2 fix says: at `beginReverseAudioPresentation`, snapshot the full audio state (music + SFX driver); at `endReverseAudioPresentation`, restore it before the existing `afterRewindRestore` music resync runs. This keeps the live drivers honest when the resynthesizer mutates them across bursts.

- [ ] **Step 1: Add a snapshot-stash field on `AudioManager`**

Near the other private fields:

```java
    private com.openggf.audio.rewind.AudioLogicalSnapshot preReverseSnapshot;
```

- [ ] **Step 2: Capture at `beginReverseAudioPresentation`**

In `beginReverseAudioPresentation`, before invoking the runtime's reverse begin call (and before the backend's), capture:

```java
    public void beginReverseAudioPresentation() {
        preReverseSnapshot = captureLogicalSnapshot();
        reverseAudioPresentationActive = true;
        deterministicAudioRuntime.beginReversePresentation();
        if (backend != null) {
            backend.beginReversePresentation();
        }
    }
```

- [ ] **Step 3: Restore at `endReverseAudioPresentation`**

```java
    public void endReverseAudioPresentation() {
        reverseAudioPresentationActive = false;
        deterministicAudioRuntime.endReversePresentation();
        if (backend != null) {
            backend.endReversePresentation();
        }
        if (preReverseSnapshot != null) {
            // 1. Restore driver state (music + standalone SFX) via the normal
            //    restoreLogicalSnapshot path. This deliberately does NOT touch
            //    the runtime clock — see the comment on restoreLogicalSnapshot.
            restoreLogicalSnapshot(preReverseSnapshot);
            // 2. Separately restore the runtime clock to where it was BEFORE
            //    held-rewind started. ReverseResynthesizer mutates the clock
            //    on every burst (runtime.restoreClockSnapshot to a keyframe
            //    audio-frame index, then forward-step), so at endReverse the
            //    clock is parked at the last synthesized historical audio
            //    frame. Without this explicit restore, forward audio after
            //    held-rewind would resume from that historical position,
            //    breaking audio-frame indexing.
            //
            //    We do this OUTSIDE restoreLogicalSnapshot so normal rewind
            //    seeks (RewindController.seekTo, stepBackward) continue to
            //    leave the runtime clock at its current live position — they
            //    don't touch the clock and shouldn't be made to.
            com.openggf.audio.runtime.AudioFrameClock.Snapshot clockSnap =
                    preReverseSnapshot.backend() != null
                            ? preReverseSnapshot.backend().clockSnapshot()
                            : null;
            if (clockSnap != null) {
                deterministicAudioRuntime.restoreClockSnapshot(clockSnap);
            }
            preReverseSnapshot = null;
        }
    }
```

This re-runs the SMPS driver state that existed before held-rewind started, then explicitly puts the runtime clock back where it was before the rewind. `afterRewindRestore` runs separately (called from `LiveRewindManager` after the bracket closes) and applies its presentation policy on top of the restored state.

- [ ] **Step 4: Write a regression test**

Create `src/test/java/com/openggf/audio/TestAudioManagerReverseBracket.java`:

```java
package com.openggf.audio;

import com.openggf.audio.rewind.AudioLogicalSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class TestAudioManagerReverseBracket {

    @AfterEach
    void tearDown() {
        AudioManager.getInstance().resetState();
        AudioManager.getInstance().setBackend(new NullAudioBackend());
    }

    @Test
    void endReverseAudioPresentationRestoresThePreReverseSnapshot() {
        AudioManager audio = AudioManager.getInstance();
        audio.resetState();
        AudioTestFixtures.RecordingAudioBackend backend = new AudioTestFixtures.RecordingAudioBackend();
        audio.setBackend(backend);

        // Establish a non-trivial snapshot. RecordingAudioBackend tracks
        // restoreLogicalSnapshot calls; we want exactly one at endReverse.
        AudioLogicalSnapshot before = audio.captureLogicalSnapshot();
        int restoreCallsBefore = backend.restoreLogicalSnapshotCalls;

        audio.beginReverseAudioPresentation();
        audio.endReverseAudioPresentation();

        assertNotNull(before);
        org.junit.jupiter.api.Assertions.assertEquals(restoreCallsBefore + 1,
                backend.restoreLogicalSnapshotCalls,
                "endReverseAudioPresentation must invoke exactly one backend.restoreLogicalSnapshot");
    }

    @Test
    void endReverseAudioPresentationRestoresTheRuntimeClock() {
        AudioManager audio = AudioManager.getInstance();
        audio.resetState();
        com.openggf.audio.runtime.StreamBackedDeterministicAudioRuntime runtime =
                new com.openggf.audio.runtime.StreamBackedDeterministicAudioRuntime(
                        new com.openggf.audio.runtime.AudioFrameClock(120, 60),
                        new com.openggf.audio.runtime.AudioOutputFifo(120));
        audio.setDeterministicAudioRuntime(runtime);
        audio.setBackend(new NullAudioBackend());

        // Advance a few audio frames to seed a non-zero pre-rewind clock.
        runtime.setMusicStream(new ScriptedAudioStream((short) 1, (short) 0));
        for (int i = 0; i < 5; i++) {
            audio.advanceGameplayFrameAudio();
        }
        long preRewindAudioFrame = runtime.captureClockSnapshot().totalSamplesProduced();

        audio.beginReverseAudioPresentation();
        // Simulate a burst pulling the clock backward — this is what the
        // ReverseResynthesizer would do in production.
        runtime.restoreClockSnapshot(new com.openggf.audio.runtime.AudioFrameClock.Snapshot(
                120, 60, 0L, 0));
        org.junit.jupiter.api.Assertions.assertEquals(0L,
                runtime.captureClockSnapshot().totalSamplesProduced(),
                "Sanity: burst left the clock at the historical audio-frame");

        audio.endReverseAudioPresentation();

        org.junit.jupiter.api.Assertions.assertEquals(preRewindAudioFrame,
                runtime.captureClockSnapshot().totalSamplesProduced(),
                "endReverseAudioPresentation must restore the runtime clock to the pre-rewind position");
    }
}
```

(If `RecordingAudioBackend` doesn't expose a `restoreLogicalSnapshotCalls` counter, add it; this is standard test-double extension.)

- [ ] **Step 5: Run**

`mvn "-Dtest=TestAudioManagerReverseBracket,TestAudioManagerRewindSuppression" test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/openggf/audio/AudioManager.java \
        src/test/java/com/openggf/audio/TestAudioManagerReverseBracket.java
git commit -m "$(cat <<'EOF'
feat(audio): rewind bracket captures/restores full audio state

beginReverseAudioPresentation now snapshots the full
AudioLogicalSnapshot (music + standalone SFX driver state, plus the
runtime clock) into a private stash before delegating to the runtime
and backend reverse-start hooks. endReverseAudioPresentation pops the
stash via restoreLogicalSnapshot (driver state) and a separate
deterministicAudioRuntime.restoreClockSnapshot (runtime clock),
because restoreLogicalSnapshot deliberately doesn't restore the clock
on normal seek paths. Without the explicit clock restore here, the
ReverseResynthesizer's per-burst clock-rewinds would leave the runtime
clock parked at the last synthesized historical audio-frame index at
endReverse, breaking forward audio-frame indexing for subsequent live
play.

Addresses spec finding P1.2: without this bracket, SFX driver state
leaked across bursts and bled into post-rewind audio.

Spec: docs/superpowers/specs/2026-05-26-reverse-resynth-design.md
(Decision Summary, chip emulator strategy row).

Changelog: n/a
Guide: n/a
Known-Discrepancies: n/a
S3K-Known-Discrepancies: n/a
Agent-Docs: n/a
Configuration-Docs: n/a
Skills: n/a

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 10: ReverseResynthesizer

**Files:**
- Create: `src/main/java/com/openggf/audio/runtime/ReverseResynthesizer.java`
- Create: `src/test/java/com/openggf/audio/runtime/TestReverseResynthesizer.java`

- [ ] **Step 1: Write the class**

```java
package com.openggf.audio.runtime;

import com.openggf.audio.AudioManager;
import com.openggf.audio.AudioStream;
import com.openggf.audio.rewind.AudioKeyframeStore;
import com.openggf.audio.rewind.AudioLogicalSnapshot;

import java.util.Arrays;
import java.util.Objects;

/**
 * Synthesizes historical PCM into the {@link PcmHistoryRing} when a reverse
 * cursor approaches the bottom of its readable window. Mutates the live
 * music + standalone SFX drivers across bursts; callers wrap the held-rewind
 * session in a {@link AudioManager#beginReverseAudioPresentation} /
 * {@link AudioManager#endReverseAudioPresentation} bracket so those
 * mutations are erased when the rewind session ends.
 *
 * <p>The {@link #ensureHeadroom} entry point must be called before each
 * {@link PcmHistoryRing.ReverseCursor#readPrevious}: it tops up the ring's
 * floor (lowering {@code cursor.oldestReadableFrame}) until the cursor has
 * at least {@link #HEADROOM_THRESHOLD_FRAMES} of unread frames or until the
 * keyframe store runs out of usable history.
 *
 * <p>Spec: {@code docs/superpowers/specs/2026-05-26-reverse-resynth-design.md}.
 */
public final class ReverseResynthesizer {

    /** Drains added per burst. One game-second-equivalent of audio frames. */
    private final int burstAudioFrames;

    /** Trigger burst when unread frames in the cursor window fall below this. */
    private final int headroomThresholdFrames;

    private final PcmHistoryRing pcmHistory;
    private final AudioKeyframeStore keyframes;
    private final AudioManager audioManager;
    private final DeterministicAudioRuntime runtime;

    public ReverseResynthesizer(PcmHistoryRing pcmHistory,
                                AudioKeyframeStore keyframes,
                                AudioManager audioManager,
                                DeterministicAudioRuntime runtime,
                                int burstAudioFrames,
                                int headroomThresholdFrames) {
        this.pcmHistory = Objects.requireNonNull(pcmHistory, "pcmHistory");
        this.keyframes = Objects.requireNonNull(keyframes, "keyframes");
        this.audioManager = Objects.requireNonNull(audioManager, "audioManager");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        if (burstAudioFrames <= 0) {
            throw new IllegalArgumentException("burstAudioFrames must be positive");
        }
        if (headroomThresholdFrames < 0) {
            throw new IllegalArgumentException("headroomThresholdFrames must be non-negative");
        }
        this.burstAudioFrames = burstAudioFrames;
        this.headroomThresholdFrames = headroomThresholdFrames;
    }

    /**
     * Tops up the cursor's readable window so it can satisfy a read of
     * {@code framesNeeded} stereo frames with at least
     * {@link #headroomThresholdFrames} of slack remaining afterwards (when
     * possible). Returns early if a burst attempt fails because no usable
     * keyframe is available — the caller's {@code readPrevious} will then
     * naturally drop to silence past the existing history floor.
     *
     * <p>{@code framesNeeded} must reflect the size of the upcoming
     * {@code readPrevious} so the resynth produces enough PCM to cover the
     * caller's actual request. Using a constant target independent of the
     * request would let a large drain underrun the ring when the headroom
     * threshold alone wasn't enough headroom.
     */
    public void ensureHeadroom(PcmHistoryRing.ReverseCursor cursor, int framesNeeded) {
        if (cursor == null) {
            return;
        }
        long target = (long) framesNeeded + headroomThresholdFrames;
        while (headroomFor(cursor) < target) {
            if (!runOneBurst(cursor)) {
                break;
            }
        }
    }

    private static long headroomFor(PcmHistoryRing.ReverseCursor cursor) {
        return cursor.nextReadableFrame() - cursor.oldestReadableFrame() + 1;
    }

    private boolean runOneBurst(PcmHistoryRing.ReverseCursor cursor) {
        // Cap the burst by the cursor's physical-slot availability. The ring
        // has a fixed capacity; once the unread window fills it, no more
        // frames can be prepended without corrupting unread slots. drainPcm
        // is expected to drain a chunk between calls, freeing slots; until
        // it does, we return false so ensureHeadroom's loop yields.
        int maxPrependable = cursor.maxPrependableFrames();
        if (maxPrependable <= 0) {
            return false;
        }
        int requestedBurst = Math.min(burstAudioFrames, maxPrependable);
        // Cap by the start-of-history floor: we can't synthesize earlier than
        // audio frame 0.
        long burstFloor = Math.max(0L, cursor.oldestReadableFrame() - requestedBurst);
        int actualBurstFrames = (int) (cursor.oldestReadableFrame() - burstFloor);
        if (actualBurstFrames <= 0) {
            return false;
        }
        long targetOldestAudioFrame = burstFloor;
        AudioLogicalSnapshot keyframe = keyframes.keyframeAtOrBeforeAudioFrame(targetOldestAudioFrame);
        if (keyframe == null
                || keyframe.backend() == null
                || keyframe.backend().clockSnapshot() == null) {
            return false;
        }

        // Restore audio state to the keyframe (drivers + clock).
        audioManager.restoreLogicalSnapshot(keyframe);
        runtime.restoreClockSnapshot(keyframe.backend().clockSnapshot());

        long gameFrame = keyframe.commandTimelineFrame();
        long audioFrame = keyframe.backend().clockSnapshot().totalSamplesProduced();
        long burstEnd = cursor.oldestReadableFrame(); // exclusive
        // burstFrames == actualBurstFrames (already capped above by both ring
        // capacity and the start-of-history floor).
        int burstFrames = actualBurstFrames;
        if (burstFrames <= 0) {
            return false;
        }

        short[] mixed = new short[burstFrames * 2];
        int mixedOffset = 0;

        while (audioFrame < burstEnd) {
            keyframes.replayCommandsAtGameFrame(audioManager, keyframe, gameFrame);
            // Re-read the runtime's music and sfx streams every iteration.
            // replayCommandsAtGameFrame can flow into backend.playSfxSmps or
            // backend.playSmps, which install fresh SmpsDriver instances via
            // runtime.setMusicStream / runtime.setSfxStream. A stream captured
            // once before the loop would go stale immediately after the first
            // such command, and a freshly-started SFX would silently drop out
            // of the mix until the next burst.
            AudioStream music = runtime.musicStreamForReverseResynth();
            AudioStream sfx = runtime.sfxStreamForReverseResynth();
            int samplesThisFrame = runtime.samplesForNextFrameForReverseResynth();
            short[] musicScratch = new short[samplesThisFrame * 2];
            short[] sfxScratch = new short[samplesThisFrame * 2];
            if (music != null) {
                music.read(musicScratch);
            }
            if (sfx != null) {
                sfx.read(sfxScratch);
                mixSfxIntoMusic(musicScratch, sfxScratch);
            }

            long thisFrameStart = audioFrame;
            long thisFrameEnd = audioFrame + samplesThisFrame;
            long copyStart = Math.max(thisFrameStart, targetOldestAudioFrame);
            long copyEnd = Math.min(thisFrameEnd, burstEnd);
            if (copyStart < copyEnd) {
                int srcOff = (int) (copyStart - thisFrameStart);
                int len = (int) (copyEnd - copyStart);
                System.arraycopy(musicScratch, srcOff * 2,
                        mixed, mixedOffset * 2, len * 2);
                mixedOffset += len;
            }
            audioFrame = thisFrameEnd;
            gameFrame++;
        }

        pcmHistory.prependBackward(targetOldestAudioFrame, cursor, mixed, mixedOffset);
        return true;
    }

    private static void mixSfxIntoMusic(short[] music, short[] sfx) {
        int len = Math.min(music.length, sfx.length);
        for (int i = 0; i < len; i++) {
            int mixedVal = music[i] + sfx[i];
            if (mixedVal > Short.MAX_VALUE) {
                mixedVal = Short.MAX_VALUE;
            } else if (mixedVal < Short.MIN_VALUE) {
                mixedVal = Short.MIN_VALUE;
            }
            music[i] = (short) mixedVal;
        }
    }
}
```

- [ ] **Step 2: Extend `DeterministicAudioRuntime` with the reverse-resynth accessors**

ReverseResynthesizer needs three pieces of state from the runtime: the bound music stream, the bound SFX stream, and the per-game-frame sample count. Add package-default methods on the interface (so test fakes can override) and implement them on `StreamBackedDeterministicAudioRuntime`:

In `DeterministicAudioRuntime.java`:

```java
    default AudioStream musicStreamForReverseResynth() {
        return null;
    }

    default AudioStream sfxStreamForReverseResynth() {
        return null;
    }

    default int samplesForNextFrameForReverseResynth() {
        return 0;
    }
```

In `StreamBackedDeterministicAudioRuntime.java`:

```java
    @Override
    public AudioStream musicStreamForReverseResynth() {
        return musicStream;
    }

    @Override
    public AudioStream sfxStreamForReverseResynth() {
        return sfxStream;
    }

    @Override
    public int samplesForNextFrameForReverseResynth() {
        return frameClock.samplesForNextFrame();
    }
```

- [ ] **Step 3: Write the focused test**

Create `src/test/java/com/openggf/audio/runtime/TestReverseResynthesizer.java`:

```java
package com.openggf.audio.runtime;

import com.openggf.audio.AudioManager;
import com.openggf.audio.NullAudioBackend;
import com.openggf.audio.ScriptedAudioStream;
import com.openggf.audio.rewind.AudioKeyframeStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestReverseResynthesizer {

    @AfterEach
    void tearDown() {
        AudioManager.getInstance().resetState();
        AudioManager.getInstance().setBackend(new NullAudioBackend());
    }

    @Test
    void ensureHeadroomReturnsCleanlyWhenNoKeyframes() {
        PcmHistoryRing ring = new PcmHistoryRing(32);
        ring.write(new short[]{0, 0, 0, 0}, 2);
        PcmHistoryRing.ReverseCursor cursor = ring.createReverseCursor();

        AudioManager audio = AudioManager.getInstance();
        audio.resetState();
        StreamBackedDeterministicAudioRuntime runtime = new StreamBackedDeterministicAudioRuntime(
                new AudioFrameClock(120, 60), new AudioOutputFifo(120));
        audio.setDeterministicAudioRuntime(runtime);
        audio.setBackend(new NullAudioBackend());

        AudioKeyframeStore store = new AudioKeyframeStore();
        ReverseResynthesizer resynth = new ReverseResynthesizer(
                ring, store, audio, runtime, /* burst */ 8, /* threshold */ 4);

        long beforeOldest = cursor.oldestReadableFrame();
        resynth.ensureHeadroom(cursor, /* framesNeeded */ 10);
        assertEquals(beforeOldest, cursor.oldestReadableFrame(),
                "No keyframes available -> no prepend, cursor floor unchanged");
    }

    @Test
    void ensureHeadroomExtendsWindowToCoverRequestedFrames() {
        // Overfill an 8-frame ring so cursor.oldestReadableFrame() > 0 at the
        // start — only then is a backward burst meaningful. (If oldestRead is
        // already 0, runOneBurst hits the start-of-history floor and bails.)
        PcmHistoryRing ring = new PcmHistoryRing(8);
        AudioManager audio = AudioManager.getInstance();
        audio.resetState();
        StreamBackedDeterministicAudioRuntime runtime = new StreamBackedDeterministicAudioRuntime(
                new AudioFrameClock(120, 60), new AudioOutputFifo(120), ring);
        audio.setDeterministicAudioRuntime(runtime);
        audio.setBackend(new NullAudioBackend());
        runtime.setMusicStream(new ScriptedAudioStream((short) 1, (short) 1));

        // Capture a keyframe at audio-frame 0 BEFORE producing any audio so
        // the resynth has a frame-0 anchor to seek backward to.
        AudioKeyframeStore store = new AudioKeyframeStore();
        store.capture(0L, audio);

        // Produce 12 game-frames of audio (24 audio-frames). The 8-frame ring
        // keeps the last 8 (audio frames 16..23), so oldestReadable = 16.
        for (int i = 0; i < 12; i++) {
            audio.advanceGameplayFrameAudio();
        }
        PcmHistoryRing.ReverseCursor cursor = ring.createReverseCursor();
        // Consume 4 frames so the ring has 4 free physical slots for the
        // burst to prepend into without violating the ring's capacity
        // invariant.
        short[] drain = new short[8];
        cursor.readPrevious(drain, 4);
        long beforeOldest = cursor.oldestReadableFrame();

        ReverseResynthesizer resynth = new ReverseResynthesizer(
                ring, store, audio, runtime, /* burst */ 4, /* threshold */ 2);
        resynth.ensureHeadroom(cursor, /* framesNeeded */ 6);

        assertTrue(cursor.oldestReadableFrame() < beforeOldest,
                "ensureHeadroom must lower cursor.oldestReadableFrame after consuming"
                        + " some unread span (oldestBefore=" + beforeOldest
                        + " oldestAfter=" + cursor.oldestReadableFrame() + ")");
        long headroom = cursor.nextReadableFrame() - cursor.oldestReadableFrame() + 1;
        assertTrue(headroom >= 6 || cursor.oldestReadableFrame() == 0,
                "ensureHeadroom must satisfy the request or hit the start-of-history floor; headroom="
                        + headroom + " oldest=" + cursor.oldestReadableFrame());
    }

    @Test
    void ensureHeadroomReturnsCleanlyWhenRingIsFull() {
        // Fresh cursor on a full ring has no consumed slots — runOneBurst
        // must back off so drainPcm can drain some frames first.
        PcmHistoryRing ring = new PcmHistoryRing(8);
        AudioManager audio = AudioManager.getInstance();
        audio.resetState();
        StreamBackedDeterministicAudioRuntime runtime = new StreamBackedDeterministicAudioRuntime(
                new AudioFrameClock(120, 60), new AudioOutputFifo(120), ring);
        audio.setDeterministicAudioRuntime(runtime);
        audio.setBackend(new NullAudioBackend());
        runtime.setMusicStream(new ScriptedAudioStream((short) 1, (short) 1));

        AudioKeyframeStore store = new AudioKeyframeStore();
        store.capture(0L, audio);
        for (int i = 0; i < 12; i++) {
            audio.advanceGameplayFrameAudio();
        }
        PcmHistoryRing.ReverseCursor cursor = ring.createReverseCursor();
        long beforeOldest = cursor.oldestReadableFrame();

        ReverseResynthesizer resynth = new ReverseResynthesizer(
                ring, store, audio, runtime, /* burst */ 4, /* threshold */ 2);
        resynth.ensureHeadroom(cursor, /* framesNeeded */ 10);

        assertEquals(beforeOldest, cursor.oldestReadableFrame(),
                "Full ring -> ensureHeadroom must no-op; drainPcm's loop is responsible"
                        + " for draining a chunk before retrying");
    }
}
```

- [ ] **Step 4: Run**

`mvn "-Dtest=TestReverseResynthesizer" test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/openggf/audio/runtime/ReverseResynthesizer.java \
        src/main/java/com/openggf/audio/runtime/DeterministicAudioRuntime.java \
        src/main/java/com/openggf/audio/runtime/StreamBackedDeterministicAudioRuntime.java \
        src/test/java/com/openggf/audio/runtime/TestReverseResynthesizer.java
git commit -m "$(cat <<'EOF'
feat(runtime): ReverseResynthesizer drives historical PCM bursts

New class synthesizes PCM into PcmHistoryRing below the reverse
cursor's readable floor when held-rewind drains past the existing
ring window. ensureHeadroom(cursor, framesNeeded) loops while the
cursor has less than framesNeeded + threshold of unread data,
restoring the live drivers from the latest audio keyframe whose
clock snapshot precedes the burst target, replaying SFX timeline
entries under REVERSE_RESYNTH, forward-stepping the chips
game-frame-by-game-frame, mixing SFX into music, and prepending the
burst's audio into the ring. runOneBurst caps the burst by
cursor.maxPrependableFrames() so a full ring is a clean no-op and
drainPcm's outer loop is responsible for draining a chunk first.

DeterministicAudioRuntime gains musicStreamForReverseResynth,
sfxStreamForReverseResynth, and samplesForNextFrameForReverseResynth
default accessors so ReverseResynthesizer can reach the chips without
breaking the interface for non-StreamBacked implementations.

Spec: docs/superpowers/specs/2026-05-26-reverse-resynth-design.md
(Architecture, ReverseResynthesizer row; Control flow during reverse
playback drain).

Changelog: n/a
Guide: n/a
Known-Discrepancies: n/a
S3K-Known-Discrepancies: n/a
Agent-Docs: n/a
Configuration-Docs: n/a
Skills: n/a

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 11: Wire ReverseResynthesizer into StreamBackedDeterministicAudioRuntime.drainPcm

**Files:**
- Modify: `src/main/java/com/openggf/audio/runtime/StreamBackedDeterministicAudioRuntime.java`
- Modify: `src/test/java/com/openggf/audio/runtime/TestStreamBackedDeterministicAudioRuntime.java`

- [ ] **Step 1: Add a setter on the runtime**

```java
    private ReverseResynthesizer reverseResynthesizer;

    public void setReverseResynthesizer(ReverseResynthesizer resynthesizer) {
        this.reverseResynthesizer = resynthesizer;
    }
```

- [ ] **Step 2: Call `ensureHeadroom` before `readPrevious` in `drainPcm`**

In `drainPcm`, wrap the existing reverse-cursor branch:

```java
    @Override
    public int drainPcm(short[] target, int frames) {
        if (reverseCursor != null) {
            // Loop: extend the cursor's readable window with the resynth as
            // much as the ring's physical-slot budget allows, then read a
            // chunk, then loop. Each readPrevious frees physical slots so the
            // next ensureHeadroom call can extend further. Without the loop,
            // a single ensureHeadroom call from a full-ring cursor would
            // refuse to extend (no slots free), and readPrevious could only
            // satisfy at most one ring-capacity worth of frames in a single
            // drainPcm call — which is wrong when the caller's request
            // exceeds the ring capacity.
            int totalRead = 0;
            while (totalRead < frames) {
                int remaining = frames - totalRead;
                if (reverseResynthesizer != null) {
                    reverseResynthesizer.ensureHeadroom(reverseCursor, remaining);
                }
                long unread = reverseCursor.nextReadableFrame()
                        - reverseCursor.oldestReadableFrame() + 1;
                if (unread <= 0) {
                    break; // history exhausted past the start-of-history floor
                }
                int chunk = (int) Math.min(unread, (long) remaining);
                short[] chunkBuf = new short[chunk * 2];
                int got = reverseCursor.readPrevious(chunkBuf, chunk);
                if (got <= 0) {
                    break;
                }
                System.arraycopy(chunkBuf, 0, target, totalRead * 2, got * 2);
                totalRead += got;
                if (got < chunk) {
                    // readPrevious stopped early; nothing more available.
                    break;
                }
            }
            // Pad the unfilled tail with silence so the caller's buffer is
            // fully written even when history runs out.
            if (totalRead < frames) {
                java.util.Arrays.fill(target, totalRead * 2, frames * 2, (short) 0);
            }
            rememberLastReverseFrame(target, totalRead);
            return totalRead;
        }
        // ...existing FIFO drain path unchanged...
    }
```

- [ ] **Step 3: Write the integration test**

Append to `TestStreamBackedDeterministicAudioRuntime.java`. This wires a **real** `ReverseResynthesizer` over a real keyframe store and asserts the cursor's floor moves downward when `drainPcm` is invoked. `ReverseResynthesizer` is `final` — do not attempt to subclass it; use cursor state to verify the resynthesizer ran.

```java
    @Test
    void drainPcmInvokesReverseResynthesizerEnsureHeadroom() {
        PcmHistoryRing ring = new PcmHistoryRing(16);
        AudioFrameClock clock = new AudioFrameClock(120, 60);
        AudioOutputFifo fifo = new AudioOutputFifo(120);
        StreamBackedDeterministicAudioRuntime runtime =
                new StreamBackedDeterministicAudioRuntime(clock, fifo, ring);
        runtime.setMusicStream(new com.openggf.audio.ScriptedAudioStream(
                (short) 1, (short) 1));

        com.openggf.audio.AudioManager audio = com.openggf.audio.AudioManager.getInstance();
        audio.resetState();
        audio.setDeterministicAudioRuntime(runtime);
        audio.setBackend(new com.openggf.audio.NullAudioBackend());

        com.openggf.audio.rewind.AudioKeyframeStore keyframes =
                new com.openggf.audio.rewind.AudioKeyframeStore();
        keyframes.capture(0L, audio);

        // Produce a few game-frames of audio to seed the ring.
        for (int i = 0; i < 4; i++) {
            audio.advanceGameplayFrameAudio();
        }

        runtime.beginReversePresentation();
        // Attach a real resynthesizer with a small burst so a single
        // drainPcm call should trigger ensureHeadroom and lower the floor.
        ReverseResynthesizer resynth = new ReverseResynthesizer(
                ring, keyframes, audio, runtime, /* burst */ 2, /* threshold */ 16);
        runtime.setReverseResynthesizer(resynth);

        // Capture floor before, drain a frame, then assert floor moved down.
        // Reach the cursor via a probe drain that returns the current state.
        short[] sink = new short[4];
        long floorBefore = ringFloorViaProbeDrain(runtime, sink);

        runtime.drainPcm(sink, 2);

        long floorAfter = ringFloorViaProbeDrain(runtime, sink);
        assertTrue(floorAfter < floorBefore,
                "drainPcm with attached resynth should call ensureHeadroom and lower"
                        + " the cursor floor; floorBefore=" + floorBefore
                        + " floorAfter=" + floorAfter);
    }

    /**
     * Best-effort accessor: drain 0 frames to surface the active cursor's
     * oldestReadableFrame without consuming PCM. If the runtime exposes a
     * direct cursor accessor at implementation time, prefer that.
     */
    private static long ringFloorViaProbeDrain(StreamBackedDeterministicAudioRuntime runtime,
                                                short[] sink) {
        runtime.drainPcm(sink, 0);
        // The runtime should expose the active cursor for testing; if it
        // doesn't, add a package-private getActiveReverseCursorForTest()
        // accessor and call it here. Implementer to wire concretely.
        return runtime.getActiveReverseCursorForTest().oldestReadableFrame();
    }
```

**Implementer note:** the test relies on a package-private getter `getActiveReverseCursorForTest()` returning the runtime's `reverseCursor` field. Add it next to the other reverse-resynth named methods on `StreamBackedDeterministicAudioRuntime`, with the same `// Test seam` comment style as `pcmHistoryRingForReverseResynth`. The integration coverage in Task 14 hits the same path end-to-end, so if surfacing the cursor for the test seems heavy-handed, replace this test with a simpler "runtime accepts a resynth without exception" smoke and let Task 14 prove the cursor-floor behavior.

- [ ] **Step 4: Run**

`mvn "-Dtest=TestStreamBackedDeterministicAudioRuntime" test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/openggf/audio/runtime/StreamBackedDeterministicAudioRuntime.java \
        src/test/java/com/openggf/audio/runtime/TestStreamBackedDeterministicAudioRuntime.java
git commit -m "$(cat <<'EOF'
feat(runtime): drainPcm loops read+ensureHeadroom for resynth

StreamBackedDeterministicAudioRuntime gains a setReverseResynthesizer
setter and rewrites drainPcm's reverse-cursor branch into a loop:
ensureHeadroom(remaining), readPrevious(chunk), update totalRead,
repeat. Each readPrevious call frees physical slots in the ring; the
next ensureHeadroom can use those slots to extend the cursor backward
further. Without the loop, drainPcm could not satisfy any request
larger than the ring's capacity, because a single ensureHeadroom call
against a full-ring cursor refuses to extend (no free slots).

This is the seam LWJGLAudioBackend uses to enable extended reverse
playback past the 10-second history wall.

Spec: docs/superpowers/specs/2026-05-26-reverse-resynth-design.md
(Architecture, StreamBackedDeterministicAudioRuntime row; Control
flow during reverse playback drain).

Changelog: n/a
Guide: n/a
Known-Discrepancies: n/a
S3K-Known-Discrepancies: n/a
Agent-Docs: n/a
Configuration-Docs: n/a
Skills: n/a

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 12: Wire ReverseResynthesizer into LWJGLAudioBackend

**Files:**
- Modify: `src/main/java/com/openggf/audio/LWJGLAudioBackend.java`

- [ ] **Step 1: Construct + attach in init**

Locate the spot in `LWJGLAudioBackend.init()` (or `applyDeterministicAudioRuntime` /`attachDeterministicAudioRuntime` — the place where the runtime is finalized for this backend) where `StreamBackedDeterministicAudioRuntime` is constructed via `AudioManager.configureDeterministicRuntimeForBackend`. Since that path runs inside `AudioManager`, the backend needs to look up the runtime AFTER `attachDeterministicAudioRuntime` is called.

In `attachDeterministicAudioRuntime`, when the assertion passes, also wire the resynth:

```java
    @Override
    public void attachDeterministicAudioRuntime(DeterministicAudioRuntime runtime) {
        synchronized (streamLock) {
            Objects.requireNonNull(runtime, "runtime");
            if (supportsDeterministicRuntimePresentation()
                    && !runtime.providesPresentationPcm()) {
                throw new IllegalStateException(
                        "LWJGLAudioBackend declares deterministic runtime presentation"
                                + " support but was attached a runtime that does not"
                                + " provide presentation PCM: "
                                + runtime.getClass().getName());
            }
            deterministicAudioRuntime = runtime;
            bindRuntimePresentationStreams();
            attachReverseResynthesizer(runtime);
        }
    }

    private void attachReverseResynthesizer(DeterministicAudioRuntime runtime) {
        if (!(runtime instanceof StreamBackedDeterministicAudioRuntime stream)) {
            return;
        }
        // Pull the ring out of the runtime so the resynth and the cursor share
        // a single source of truth. Note: the runtime owns its own ring inside
        // the AudioManager.configureDeterministicRuntimeForBackend path.
        PcmHistoryRing ring = stream.pcmHistoryRingForReverseResynth();
        AudioManager audio = AudioManager.getInstance();
        AudioKeyframeStore keyframes = audio.audioKeyframesForReverseResynth();
        if (ring == null || keyframes == null) {
            // Either we have no ring (NoOp-shaped runtime) or no keyframe store
            // yet (early boot, between live-rewind sessions, headless trace
            // mode). Explicitly clear any previously-attached resynthesizer so
            // a stale instance never lingers with a freed AudioKeyframeStore
            // after Task 13's teardown re-runs attach with keyframes == null.
            stream.setReverseResynthesizer(null);
            return;
        }
        int sampleRate = stream.sampleRateForReverseResynth();
        ReverseResynthesizer resynth = new ReverseResynthesizer(
                ring, keyframes, audio, runtime,
                /* burst */ sampleRate,
                /* headroom */ sampleRate / 2);
        stream.setReverseResynthesizer(resynth);
    }
```

This requires three small accessors that the implementer should add to `StreamBackedDeterministicAudioRuntime` (`pcmHistoryRingForReverseResynth`, `sampleRateForReverseResynth`) and one to `AudioManager` (`audioKeyframesForReverseResynth`). Each is a one-line getter; add them next to the other reverse-resynth-named methods.

If `AudioManager` doesn't currently hold an `AudioKeyframeStore` instance (it's owned by `RewindController` and lifecycle-bound to gameplay sessions), implement `audioKeyframesForReverseResynth` to return null, then call `stream.setReverseResynthesizer(null)` so the runtime keeps the legacy reverse-cursor behavior. The `LiveRewindManager` install path is the right place to push the keyframe store into the resynth at session start; do that hook-up in Task 13.

- [ ] **Step 2: Build**

`mvn test-compile`
Expected: clean.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/openggf/audio/LWJGLAudioBackend.java \
        src/main/java/com/openggf/audio/runtime/StreamBackedDeterministicAudioRuntime.java \
        src/main/java/com/openggf/audio/AudioManager.java
git commit -m "$(cat <<'EOF'
feat(backend): LWJGLAudioBackend wires ReverseResynthesizer

attachDeterministicAudioRuntime now constructs a ReverseResynthesizer
(burst = sampleRate samples, threshold = sampleRate/2 samples) and
attaches it to the StreamBackedDeterministicAudioRuntime whenever the
backend is using that runtime variant and an AudioKeyframeStore is
available. When the keyframe store is null (early boot, trace mode,
or after Task 13's teardown re-runs attach), the helper explicitly
clears any previously-attached resynth via setReverseResynthesizer(null)
so a stale instance never lingers with a freed keyframe store; the
runtime then falls back to legacy reverse cursor reads.

Spec: docs/superpowers/specs/2026-05-26-reverse-resynth-design.md
(Architecture, LWJGLAudioBackend row).

Changelog: n/a
Guide: n/a
Known-Discrepancies: n/a
S3K-Known-Discrepancies: n/a
Agent-Docs: n/a
Configuration-Docs: n/a
Skills: n/a

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 13: LiveRewindManager install pushes keyframe store into the resynth

**Files:**
- Modify: `src/main/java/com/openggf/game/rewind/LiveRewindManager.java`
- Modify: `src/main/java/com/openggf/audio/AudioManager.java`

- [ ] **Step 1: Add a setter on AudioManager for the held-rewind keyframe store**

```java
    private AudioKeyframeStore liveRewindAudioKeyframes;

    public void setLiveRewindAudioKeyframes(AudioKeyframeStore store) {
        this.liveRewindAudioKeyframes = store;
        rebuildReverseResynthesizerForCurrentBackend();
    }

    public AudioKeyframeStore audioKeyframesForReverseResynth() {
        return liveRewindAudioKeyframes;
    }

    private void rebuildReverseResynthesizerForCurrentBackend() {
        if (backend == null) {
            return;
        }
        backend.attachDeterministicAudioRuntime(deterministicAudioRuntime);
    }
```

`rebuildReverseResynthesizerForCurrentBackend` re-runs `attachDeterministicAudioRuntime`, which is the place where the resynth is constructed (Task 12). That gives the backend a fresh chance to wire it now that the keyframe store is known.

- [ ] **Step 2: Push the keyframe store into AudioManager in LiveRewindManager.ensureInstalled**

In `LiveRewindManager.ensureInstalled` (where `rewindController = gameplayMode.getRewindController()` is set up — that controller owns the `AudioKeyframeStore`), grab the audio keyframes and hand them to AudioManager:

```java
        rewindController = gameplayMode.getRewindController();
        if (rewindController != null) {
            GameServices.audio().setLiveRewindAudioKeyframes(rewindController.audioKeyframes());
        }
```

(If `RewindController` doesn't expose `audioKeyframes()`, add the trivial getter. If `audioKeyframes` is null inside the controller, also handle that — the setter should accept null to disable the resynth.)

- [ ] **Step 3: Wire teardown**

In `LiveRewindManager.clear()` (or whatever the teardown path is — look for where `rewindController` is set back to null), also clear the audio keyframes:

```java
    private void clear() {
        if (rewinding && rewindController != null) {
            cleanupPresentationAfterRealtimeRewind(AudioPresentationPolicy.STOP_ALL_PRESENTATION);
        }
        installedGameplayMode = null;
        inputSource = null;
        rewindController = null;
        speedController.reset();
        speedController = RewindSpeedController.disabled();
        rewinding = false;
        GameServices.audio().setLiveRewindAudioKeyframes(null);
    }
```

- [ ] **Step 4: Build + run live rewind tests**

`mvn test-compile`
`mvn "-Dtest=com.openggf.game.rewind.**,com.openggf.audio.**" test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/openggf/game/rewind/LiveRewindManager.java \
        src/main/java/com/openggf/audio/AudioManager.java
git commit -m "$(cat <<'EOF'
feat(rewind): plumb audio keyframe store into AudioManager on live install

LiveRewindManager.ensureInstalled hands its RewindController's
AudioKeyframeStore to AudioManager via setLiveRewindAudioKeyframes,
which re-runs the backend's attachDeterministicAudioRuntime so the
ReverseResynthesizer can pick up the now-available keyframe store.
Teardown via LiveRewindManager.clear() clears the store back to null
to disable the resynth between rewind sessions.

Spec: docs/superpowers/specs/2026-05-26-reverse-resynth-design.md
(Architecture, ReverseResynthesizer dependencies).

Changelog: n/a
Guide: n/a
Known-Discrepancies: n/a
S3K-Known-Discrepancies: n/a
Agent-Docs: n/a
Configuration-Docs: n/a
Skills: n/a

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 14: End-to-end reverse-beyond-ten-seconds test

**Files:**
- Modify: `src/test/java/com/openggf/audio/runtime/TestStreamBackedDeterministicAudioRuntime.java`

- [ ] **Step 1: Write the test**

Append:

```java
    @Test
    void reverseDrainExtendsPastHistoryWindowWithResynthesizer() {
        // Setup: small 8-audio-frame history ring (at 120Hz / 60fps, that's
        // 4 game-frames worth — each game frame produces 2 audio frames).
        PcmHistoryRing ring = new PcmHistoryRing(8);
        AudioFrameClock clock = new AudioFrameClock(120, 60);
        AudioOutputFifo fifo = new AudioOutputFifo(120);
        StreamBackedDeterministicAudioRuntime runtime =
                new StreamBackedDeterministicAudioRuntime(clock, fifo, ring);
        runtime.setMusicStream(new com.openggf.audio.ScriptedAudioStream(
                (short) 1, (short) 1));

        // Drive AudioManager so it captures the runtime as its current.
        com.openggf.audio.AudioManager audio = com.openggf.audio.AudioManager.getInstance();
        audio.resetState();
        audio.setDeterministicAudioRuntime(runtime);
        audio.setBackend(new com.openggf.audio.NullAudioBackend());

        com.openggf.audio.rewind.AudioKeyframeStore keyframes =
                new com.openggf.audio.rewind.AudioKeyframeStore();
        // Capture keyframe at game-frame 0 BEFORE producing any audio.
        keyframes.capture(0L, audio);

        // Produce 8 game-frames of music. Ring holds the most recent 8 audio
        // frames (8 game-frames × 2 audio-frames/game-frame, capped at the
        // ring's 8-frame capacity).
        for (int i = 0; i < 8; i++) {
            audio.advanceGameplayFrameAudio();
        }

        // Attach the resynthesizer. drainPcm will pass `frames=10` to
        // ensureHeadroom, so the burst loop needs to produce enough older
        // PCM to satisfy a 10-frame request even though the ring only held
        // 8 forward frames.
        ReverseResynthesizer resynth = new ReverseResynthesizer(
                ring, keyframes, audio, runtime, /* burst */ 4, /* threshold */ 2);
        runtime.setReverseResynthesizer(resynth);

        runtime.beginReversePresentation();

        // Drain past the original 8-frame window. Without the resynth this
        // would return 8 (ring exhausted, rest silent). With it, we expect
        // 10 fully populated stereo frames.
        short[] target = new short[20];
        int read = runtime.drainPcm(target, 10);
        assertEquals(10, read,
                "drainPcm must return the full requested frame count when a"
                        + " ReverseResynthesizer is attached and a keyframe is available");

        boolean allNonZero = true;
        for (int i = 0; i < 20; i++) {
            if (target[i] == 0) {
                allNonZero = false;
                break;
            }
        }
        assertTrue(allNonZero,
                "Resynthesizer should extend the reverse window past the original 8 frames;"
                        + " drain returned target=" + java.util.Arrays.toString(target));
    }
```

- [ ] **Step 2: Run**

`mvn "-Dtest=TestStreamBackedDeterministicAudioRuntime#reverseDrainExtendsPastHistoryWindowWithResynthesizer" test`
Expected: PASS.

If the test fails, debug — the most common gotcha is the keyframe's clock snapshot being null (Task 4 not capturing it) or the cursor's adjacency invariant rejecting the prepend (the math in `runOneBurst` got off by one).

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/openggf/audio/runtime/TestStreamBackedDeterministicAudioRuntime.java
git commit -m "$(cat <<'EOF'
test: e2e reverse drain past history-window with ReverseResynthesizer

Drives a small (1-second-equivalent) history ring full of a known
ramp, then reverse-drains past its capacity with a ReverseResynthesizer
attached. Asserts every sample is non-zero, proving the synth bursts
extended the readable window beyond the original ring's 10-second
floor (in production scale).

Spec: docs/superpowers/specs/2026-05-26-reverse-resynth-design.md
(Testing section, test #9).

Changelog: n/a
Guide: n/a
Known-Discrepancies: n/a
S3K-Known-Discrepancies: n/a
Agent-Docs: n/a
Configuration-Docs: n/a
Skills: n/a

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 15: Performance benchmark (gating)

**Files:**
- Create: `src/test/java/com/openggf/audio/runtime/BenchmarkReverseResynthesizer.java`

- [ ] **Step 1: Write the benchmark**

```java
package com.openggf.audio.runtime;

import com.openggf.audio.AudioManager;
import com.openggf.audio.NullAudioBackend;
import com.openggf.audio.ScriptedAudioStream;
import com.openggf.audio.rewind.AudioKeyframeStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

/**
 * Performance benchmark for {@link ReverseResynthesizer}. Not a parity test —
 * measures p50/p95/p99 burst duration for synthesized historical PCM windows
 * of representative sizes. The spec's open point #2 sets this as the gating
 * gate: if p95 exceeds OpenAL buffer slack, the synth must move off the
 * audio drain thread (current scope: synchronous bursts).
 *
 * <p>Run with: {@code mvn "-Dtest=BenchmarkReverseResynthesizer" test}.
 */
class BenchmarkReverseResynthesizer {

    @AfterEach
    void tearDown() {
        AudioManager.getInstance().resetState();
        AudioManager.getInstance().setBackend(new NullAudioBackend());
    }

    @Test
    void measureBurstDurations() {
        int sampleRate = 48000;
        int[] burstSeconds = {1, 5, 10};
        for (int seconds : burstSeconds) {
            long[] durationsNs = measureOneSize(sampleRate, seconds, 32);
            Arrays.sort(durationsNs);
            long p50 = durationsNs[durationsNs.length / 2];
            long p95 = durationsNs[(int) (durationsNs.length * 0.95)];
            long p99 = durationsNs[(int) (durationsNs.length * 0.99)];
            System.out.printf("ReverseResynth %ds burst: p50=%.2fms p95=%.2fms p99=%.2fms%n",
                    seconds, p50 / 1_000_000.0, p95 / 1_000_000.0, p99 / 1_000_000.0);
        }
    }

    private long[] measureOneSize(int sampleRate, int seconds, int iterations) {
        int burstAudioFrames = sampleRate * seconds;
        long[] durations = new long[iterations];
        for (int i = 0; i < iterations; i++) {
            AudioManager audio = AudioManager.getInstance();
            audio.resetState();
            PcmHistoryRing ring = new PcmHistoryRing(sampleRate * (seconds + 1));
            StreamBackedDeterministicAudioRuntime runtime = new StreamBackedDeterministicAudioRuntime(
                    new AudioFrameClock(sampleRate, 60),
                    new AudioOutputFifo(sampleRate),
                    ring);
            audio.setDeterministicAudioRuntime(runtime);
            audio.setBackend(new NullAudioBackend());
            runtime.setMusicStream(new ScriptedAudioStream((short) 1, (short) 1));

            // Capture a keyframe at audio-frame 0.
            AudioKeyframeStore keyframes = new AudioKeyframeStore();
            keyframes.capture(0L, audio);

            // Produce seconds+1 game-seconds of audio so the ring is full.
            int gameFramesNeeded = 60 * (seconds + 1);
            for (int g = 0; g < gameFramesNeeded; g++) {
                audio.advanceGameplayFrameAudio();
            }

            // Drain to push cursor below threshold and trigger a burst.
            runtime.beginReversePresentation();
            short[] sink = new short[sampleRate * 2];
            runtime.drainPcm(sink, sampleRate / 2);

            ReverseResynthesizer resynth = new ReverseResynthesizer(
                    ring, keyframes, audio, runtime, burstAudioFrames, sampleRate / 2);
            runtime.setReverseResynthesizer(resynth);

            long start = System.nanoTime();
            runtime.drainPcm(sink, sampleRate / 2);
            durations[i] = System.nanoTime() - start;

            runtime.endReversePresentation();
        }
        return durations;
    }
}
```

- [ ] **Step 2: Run + record results**

`mvn "-Dtest=BenchmarkReverseResynthesizer" test`
Expected: PASS (the benchmark itself just measures). Capture the printed p50/p95/p99 values for 1s/5s/10s bursts.

- [ ] **Step 3: Update the spec or frontier log with the measured values**

Append a section to `docs/superpowers/specs/2026-05-26-reverse-resynth-design.md` under "Performance benchmark":

```markdown
**Measured (synchronous, single-threaded, 48 kHz):**
- 1 s burst: p50=<x>ms p95=<y>ms p99=<z>ms
- 5 s burst: p50=<x>ms p95=<y>ms p99=<z>ms
- 10 s burst: p50=<x>ms p95=<y>ms p99=<z>ms

Decision: <synchronous OK / move to worker> based on the comparison
against the OpenAL stream buffer slack at the current STREAM_BUFFER_SIZE
× STREAM_BUFFER_COUNT.
```

If p95 exceeds the OpenAL slack budget, raise the finding with the human partner; do NOT proceed to ship — the spec's open point #2 explicitly gates on this. Discussion is required to decide whether to shrink the burst, move to a worker thread, or change scope.

- [ ] **Step 4: Commit**

```bash
git add src/test/java/com/openggf/audio/runtime/BenchmarkReverseResynthesizer.java \
        docs/superpowers/specs/2026-05-26-reverse-resynth-design.md
git commit -m "$(cat <<'EOF'
test: benchmark ReverseResynthesizer burst durations

Measures p50/p95/p99 burst times for 1s/5s/10s synth windows at
48 kHz. Spec open point #2 gates the synchronous burst model on these
numbers; results recorded inline in the spec.

Spec: docs/superpowers/specs/2026-05-26-reverse-resynth-design.md
(Testing, Performance benchmark).

Changelog: n/a
Guide: n/a
Known-Discrepancies: n/a
S3K-Known-Discrepancies: n/a
Agent-Docs: n/a
Configuration-Docs: n/a
Skills: n/a

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 16: Full mvn test sweep + manual smoke

**Files:** none for the sweep step. Optional spec-status update for the smoke note.

- [ ] **Step 1: Run the full sweep**

`mvn test`
Expected: all tests pass. 22 pre-existing S2 trace frontiers remain (documented in `docs/TRACE_FRONTIER_LOG.md`); no NEW failures.

- [ ] **Step 2: Manual smoke test (user-side)**

Boot engine, start a level, play ~30 seconds with music, hold rewind. Confirm:
- Audio plays in reverse for the full held-rewind duration (well past 10 seconds).
- Releasing the rewind key resumes forward play with the music's pre-rewind state.
- SFX during normal play continues to work after the rewind session.

If any of these fail, capture the symptom and return to Phase 1 of the systematic-debugging skill. The most likely failure modes:
- Headroom never replenishes: probably a burst-loop infinite or no-progress condition; check that `audioFrame` advances inside `runOneBurst`.
- Music skips/glitches across the 10-second boundary: keyframe clock snapshot misaligned with the ring's audio-frame indexing; verify Task 4 captures it correctly.
- Post-rewind audio is stuck or silent: rewind bracket (Task 9) didn't restore the pre-reverse snapshot — confirm `endReverseAudioPresentation` runs `restoreLogicalSnapshot`.

- [ ] **Step 3: Final status update**

If the smoke passes, update the resynth spec status from "Approved, ready to implement" to "Implemented (date and final commit SHA)". Commit the status update with `n/a` trailers.

---

## Self-Review

### Spec coverage

- **PcmHistoryRing.prependBackward + ReverseCursor.extendOldestTo** — Task 5.
- **AudioBackendLogicalSnapshot.clockSnapshot** — Task 2.
- **DeterministicAudioRuntime.captureClockSnapshot / restoreClockSnapshot defaults + StreamBacked overrides** — Task 3.
- **AudioManager.captureLogicalSnapshot reads runtime clock; restoreLogicalSnapshot deliberately does NOT** — Task 4.
- **AudioKeyframeStore.keyframeAtOrBeforeAudioFrame + replayCommandsAtGameFrame** — Task 7. (Single-frame semantic prevents the per-burst-iteration duplicate-dispatch that an unconditional "replay from keyframe.commandEntryCount to target" would cause.)
- **AudioReplayReason.REVERSE_RESYNTH** — Task 6.
- **AudioManager.replayTimelineCommand REVERSE_RESYNTH SFX bypass** — Task 8.
- **ReverseResynthesizer class** — Task 10.
- **StreamBackedDeterministicAudioRuntime accepts resynth + calls ensureHeadroom** — Task 11.
- **LWJGLAudioBackend wires resynth** — Task 12. Live-install plumbing — Task 13.
- **End-to-end test (test #9)** — Task 14.
- **Performance benchmark (test #11)** — Task 15.
- **Manual smoke (test #10)** — Task 16, step 2.
- **Rewind bracket save/restore for SFX driver state (P1.2 fix)** — Task 9. Includes a separate `restoreClockSnapshot` call at endReverse because `restoreLogicalSnapshot` deliberately doesn't touch the clock, and the burst loop's per-keyframe clock rewinds would otherwise leave the runtime parked at a historical audio-frame index.
- **WAV SFX silent under REVERSE_RESYNTH (P2.3)** — Task 8's silent-no-op behavior covers this.

### Placeholder scan

- Task 12 acknowledges that the resynth may need to be created lazily once `AudioKeyframeStore` is available. Task 13 implements that lazy hookup. Not a placeholder — the deferred construction is the intended design. The helper now also clears any previously-attached resynth when keyframes go null, preventing a stale instance from lingering after teardown.
- Task 11 step 3 uses a real `ReverseResynthesizer` (no anonymous subclass — the class is `final`). Asserts via cursor-floor state, which requires adding a small `getActiveReverseCursorForTest()` test seam to `StreamBackedDeterministicAudioRuntime` next to the existing reverse-resynth-named methods.

### Type consistency

- `burstAudioFrames` and `headroomThresholdFrames` are constructor args throughout (Tasks 10, 11, 12, 14, 15). `ensureHeadroom(cursor, framesNeeded)` takes the upcoming-drain frame count as a second parameter and produces enough PCM to satisfy `framesNeeded + headroomThresholdFrames` of unread data (or hits the start-of-history floor first, or the ring is currently full so the burst no-ops). `drainPcm` loops with the resynth: ensureHeadroom → readPrevious chunk → ensureHeadroom → ..., so requests larger than the ring's capacity get filled across multiple iterations as the cursor frees slots.
- `ReverseCursor.maxPrependableFrames()` (Task 5) provides the physical-slot budget. `ReverseResynthesizer.runOneBurst` (Task 10) reads it to cap each burst; without that cap, a full-ring prepend would throw the adjacency invariant rather than return false.
- `keyframeAtOrBeforeAudioFrame(long)` consistent across Tasks 7, 10.
- `replayCommandsAtGameFrame(AudioManager, AudioLogicalSnapshot, long)` consistent across Tasks 7, 10.
- `pcmHistoryRingForReverseResynth`, `musicStreamForReverseResynth`, `sfxStreamForReverseResynth`, `samplesForNextFrameForReverseResynth`, `sampleRateForReverseResynth` — used in Tasks 10 and 12 with the same shape.
- `setReverseResynthesizer` — Tasks 11, 12, 14, 15.
- `setLiveRewindAudioKeyframes` + `audioKeyframesForReverseResynth` — Tasks 12, 13.

### Open spec points addressed

- **#1 (REVERSE_RESYNTH ↔ backend.playSfx* seam):** Task 8 routes SMPS-route PlaySfx under REVERSE_RESYNTH through the existing `backend.playSfxSmps` path so the chip state evolves to match SFX that fired after the keyframe. WAV-fallback routes are silent no-ops to keep persistent OpenAL source allocation out of the synth window. The rewind-bracket save/restore from Task 9 rolls back the SmpsDriver mutations at rewind end.
- **#2 (sync vs worker thread):** Task 15's benchmark surfaces the decision data; gating note added.
- **#3 (WAV SFX reset at rewind end):** Out of scope for v1 (no live code path triggers WAV SFX under REVERSE_RESYNTH); covered by Task 9's bracket restore which already runs `restoreLogicalSnapshot`.
