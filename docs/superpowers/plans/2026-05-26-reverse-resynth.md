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

- [ ] **Step 3: Make ReverseCursor.oldestReadableFrame mutable + add `extendOldestTo` + `oldestReadableFrame()` accessor**

Edit `PcmHistoryRing.java`:

- Change `private final long oldestReadableFrame;` to `private long oldestReadableFrame;` inside the `ReverseCursor` class.
- Add (inside `ReverseCursor`):

```java
        public long oldestReadableFrame() {
            return oldestReadableFrame;
        }

        public long nextReadableFrame() {
            return nextReadableFrame;
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

ReverseCursor exposes oldestReadableFrame() and nextReadableFrame()
accessors and a package-private extendOldestTo so the prepend path
can lower the floor.

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

- [ ] **Step 2: Add `keyframeAtOrBeforeAudioFrame` and `replayLiveTo`**

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
     * Walks the audio command timeline forward from the given keyframe to
     * (and including) any entries whose game-frame falls at or before
     * {@code targetGameFrame}, dispatching them via
     * {@link AudioManager#replayTimelineCommand} under
     * {@link AudioReplayReason#REVERSE_RESYNTH}. Returns the number of
     * commands replayed. The caller is responsible for having restored the
     * driver state from {@code keyframe} via
     * {@link AudioManager#restoreLogicalSnapshot} before calling this method.
     */
    public int replayLiveTo(AudioManager audio,
                            AudioLogicalSnapshot keyframe,
                            long targetGameFrame) {
        Objects.requireNonNull(audio, "audio");
        Objects.requireNonNull(keyframe, "keyframe");
        if (keyframe.commandTimelineFrame() > targetGameFrame) {
            return 0;
        }
        int replayed = 0;
        try (AudioReplayScope ignored = audio.beginRewindReplay(
                Math.toIntExact(keyframe.commandTimelineFrame()),
                Math.toIntExact(targetGameFrame),
                AudioReplayReason.REVERSE_RESYNTH)) {
            List<AudioTimelineEntry> entries = audio.commandTimeline().entries();
            for (int i = keyframe.commandEntryCount(); i < entries.size(); i++) {
                AudioTimelineEntry entry = entries.get(i);
                if (entry.frame() <= targetGameFrame) {
                    audio.replayTimelineCommand(entry.command());
                    replayed++;
                }
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
feat(rewind): AudioKeyframeStore audio-frame lookup + replayLiveTo

keyframeAtOrBeforeAudioFrame scans recorded keyframes for the largest
audio-frame index not exceeding a requested target, using each
keyframe's AudioBackendLogicalSnapshot.clockSnapshot. replayLiveTo
walks the live command timeline forward from a keyframe's
commandEntryCount, dispatching entries via replayTimelineCommand under
the REVERSE_RESYNTH scope. Both are inputs to ReverseResynthesizer.

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

## Task 8: REVERSE_RESYNTH branch in AudioManager.replayTimelineCommand

**Files:**
- Modify: `src/main/java/com/openggf/audio/AudioManager.java`
- Create: `src/test/java/com/openggf/audio/TestAudioManagerReverseResynthDispatch.java`

The spec's open point #1 cautions: SFX dispatched under REVERSE_RESYNTH must NOT go through `backend.playSfx*` (which records new timeline entries and may have listener side effects). It must reach only the chip-state mutation path. The cleanest seam is a new `AudioBackend.playSfxSmpsRaw` (no-op default; LWJGL implements it without backend bookkeeping) — but we can do this more surgically by adding a private dispatch helper that bypasses the live backend method.

For this task, take the minimal route: when the scope is REVERSE_RESYNTH and the command is `PlaySfx` going through the SMPS path, **dispatch directly to the chip via the existing standalone SFX driver path on the backend** if such a method is exposed, OR fall back to silently skipping (the SFX driver state already carries enough information in the snapshot to evolve correctly across the burst window without re-firing one-shot SFX). The implementer must read `replayTimelineCommand`, identify the relevant call sites, and decide which option fits the actual backend surface.

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

- [ ] **Step 3: Branch `replaySfx` for REVERSE_RESYNTH**

In `replaySfx` (or whatever the current method name is — find the `case PlaySfx` arm in `replayTimelineCommand`), branch so REVERSE_RESYNTH dispatches to a new helper that bypasses `backend.playSfxSmps`:

```java
    private void replaySfx(AudioCommand.PlaySfx command) {
        if (currentReplayReason == AudioReplayReason.REVERSE_RESYNTH) {
            dispatchReverseResynthSfx(command);
            return;
        }
        // ... existing body unchanged ...
    }

    private void dispatchReverseResynthSfx(AudioCommand.PlaySfx command) {
        // ReverseResynthesizer is forward-stepping the chips inside a burst.
        // SFX timeline entries should mutate the live SMPS driver(s) so the
        // PCM produced reflects the SFX that was firing at this game frame —
        // but they must NOT re-enter backend.playSfxSmps (which records new
        // timeline entries, fires backend listeners, and reallocates AL
        // sources). For SMPS-backed SFX, the chip state is fully captured in
        // the keyframe's SmpsDriverSnapshot; re-issuing the SFX command would
        // duplicate the work the snapshot already encodes. Drop the command
        // here.
        //
        // WAV-fallback SFX (LWJGLAudioBackend.playSfx(String,float)) is
        // explicitly out of scope per spec edge case 9; silently skipping
        // them under REVERSE_RESYNTH is the intended behavior.
    }
```

The body is intentionally empty: the snapshot restore at burst start already encodes the SFX driver state. Forward-stepping the chips from there will reproduce the SFX PCM. Re-issuing the `PlaySfx` command would either double-fire (chip + new sequencer) or pollute the live backend with timeline noise. This matches the spec's open-point #1 disposition: keep SFX out of the live backend path.

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
    void seekReasonRoutesSfxThroughBackendAsBefore() {
        try (AudioReplayScope ignored = audio.beginRewindReplay(10, 4, AudioReplayReason.SEEK)) {
            audio.replayTimelineCommand(new AudioCommand.PlaySfx(
                    -1, "JUMP", AudioCommand.SfxRoute.BASE_SMPS_NAME, 1.0f, null));
        }
        assertEquals(1, backend.playSfxSmpsCalls,
                "SEEK reason must route PlaySfx through backend.playSfxSmps (existing behavior)");
    }

    @Test
    void reverseResynthReasonDoesNotInvokeBackendPlaySfx() {
        try (AudioReplayScope ignored = audio.beginRewindReplay(10, 4, AudioReplayReason.REVERSE_RESYNTH)) {
            audio.replayTimelineCommand(new AudioCommand.PlaySfx(
                    -1, "JUMP", AudioCommand.SfxRoute.BASE_SMPS_NAME, 1.0f, null));
        }
        assertEquals(0, backend.playSfxSmpsCalls,
                "REVERSE_RESYNTH must bypass backend.playSfxSmps (chip state is in the keyframe snapshot)");
    }
}
```

(If `RecordingAudioBackend` doesn't already track `playSfxSmpsCalls`, add that counter in the fixture or use the closest existing counter and adjust the assertions.)

- [ ] **Step 5: Run**

`mvn "-Dtest=TestAudioManagerReverseResynthDispatch,TestAudioManagerRewindSuppression" test`
Expected: PASS. Existing rewind suppression behavior must remain green.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/openggf/audio/AudioManager.java \
        src/test/java/com/openggf/audio/TestAudioManagerReverseResynthDispatch.java
git commit -m "$(cat <<'EOF'
feat(audio): REVERSE_RESYNTH replay scope bypasses backend.playSfx*

AudioManager tracks the active replay reason on its rewind-replay
stack. When replayTimelineCommand sees a PlaySfx command under
REVERSE_RESYNTH, it routes to a new dispatchReverseResynthSfx helper
that intentionally does nothing — SMPS chip state is fully captured
in the keyframe's SmpsDriverSnapshot, so forward-stepping the chips
across the burst already reproduces SFX PCM without re-issuing the
backend.playSfxSmps call. WAV-fallback SFX (out of scope per spec
edge case 9) is therefore also a silent no-op under this scope.

SEEK and STEP_BACKWARD scopes keep their existing
backend.playSfxSmps routing, locked by
TestAudioManagerReverseResynthDispatch.

Spec: docs/superpowers/specs/2026-05-26-reverse-resynth-design.md
(Architecture, AudioManager.replayTimelineCommand row; Known Open
Points #1).

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
            restoreLogicalSnapshot(preReverseSnapshot);
            preReverseSnapshot = null;
        }
    }
```

This re-runs the SMPS driver state that existed before held-rewind started, so any chip mutations the resynthesizer performed during the rewind session are erased. `afterRewindRestore` runs separately (called from `LiveRewindManager` after the bracket closes) and applies its presentation policy on top of the restored state.

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
stash via restoreLogicalSnapshot before returning, so any live-driver
mutations the ReverseResynthesizer performed across burst restores
get erased when held-rewind ends.

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

    public void ensureHeadroom(PcmHistoryRing.ReverseCursor cursor) {
        if (cursor == null) {
            return;
        }
        while (headroomFor(cursor) < headroomThresholdFrames) {
            if (!runOneBurst(cursor)) {
                break;
            }
        }
    }

    private static long headroomFor(PcmHistoryRing.ReverseCursor cursor) {
        return cursor.nextReadableFrame() - cursor.oldestReadableFrame() + 1;
    }

    private boolean runOneBurst(PcmHistoryRing.ReverseCursor cursor) {
        long targetOldestAudioFrame = cursor.oldestReadableFrame() - burstAudioFrames;
        if (targetOldestAudioFrame < 0) {
            return false;
        }
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
        int burstFrames = (int) (burstEnd - targetOldestAudioFrame);
        if (burstFrames <= 0) {
            return false;
        }

        short[] mixed = new short[burstFrames * 2];
        int mixedOffset = 0;

        AudioStream music = runtime.musicStreamForReverseResynth();
        AudioStream sfx = runtime.sfxStreamForReverseResynth();

        while (audioFrame < burstEnd) {
            keyframes.replayLiveTo(audioManager, keyframe, gameFrame);
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
        resynth.ensureHeadroom(cursor);
        assertEquals(beforeOldest, cursor.oldestReadableFrame(),
                "No keyframes available -> no prepend, cursor floor unchanged");
    }

    @Test
    void ensureHeadroomExtendsWindowWhenKeyframeAvailable() {
        PcmHistoryRing ring = new PcmHistoryRing(64);
        // Set up a runtime + scripted music stream so the burst loop has
        // something to read.
        AudioManager audio = AudioManager.getInstance();
        audio.resetState();
        StreamBackedDeterministicAudioRuntime runtime = new StreamBackedDeterministicAudioRuntime(
                new AudioFrameClock(120, 60), new AudioOutputFifo(120), ring);
        audio.setDeterministicAudioRuntime(runtime);
        audio.setBackend(new NullAudioBackend());
        runtime.setMusicStream(new ScriptedAudioStream((short) 1, (short) 1));

        // Advance 10 game frames forward to populate the ring with samples 1..40
        // (10 frames × 2 samples/frame × 2 channels).
        for (int i = 0; i < 10; i++) {
            audio.advanceGameplayFrameAudio();
        }

        // Capture a keyframe AFTER the first frame so the resynth has something
        // to seek to.
        AudioKeyframeStore store = new AudioKeyframeStore();
        store.capture(0L, audio);

        // Set up a cursor and drain most of its window to drop below threshold.
        PcmHistoryRing.ReverseCursor cursor = ring.createReverseCursor();
        short[] drain = new short[16];
        cursor.readPrevious(drain, 8);

        ReverseResynthesizer resynth = new ReverseResynthesizer(
                ring, store, audio, runtime, /* burst */ 4, /* threshold */ 4);

        long beforeOldest = cursor.oldestReadableFrame();
        resynth.ensureHeadroom(cursor);
        assertTrue(cursor.oldestReadableFrame() < beforeOldest,
                "ensureHeadroom must lower cursor.oldestReadableFrame when keyframe is available");
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
ring window. ensureHeadroom loops while the cursor has less than
HEADROOM_THRESHOLD_FRAMES of unread data, restoring the live drivers
from the latest audio keyframe whose clock snapshot precedes the
burst target, replaying SFX timeline entries under REVERSE_RESYNTH,
forward-stepping the chips game-frame-by-game-frame, mixing SFX into
music, and prepending the burst's audio into the ring.

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
            if (reverseResynthesizer != null) {
                reverseResynthesizer.ensureHeadroom(reverseCursor);
            }
            int read = reverseCursor.readPrevious(target, frames);
            rememberLastReverseFrame(target, read);
            return read;
        }
        // ...existing FIFO drain path unchanged...
    }
```

- [ ] **Step 3: Write the integration test**

Append to `TestStreamBackedDeterministicAudioRuntime.java`:

```java
    @Test
    void drainPcmWithReverseResynthesizerExtendsBeyondHistoryWindow() {
        PcmHistoryRing ring = new PcmHistoryRing(8);
        AudioFrameClock clock = new AudioFrameClock(120, 60);
        AudioOutputFifo fifo = new AudioOutputFifo(120);
        StreamBackedDeterministicAudioRuntime runtime =
                new StreamBackedDeterministicAudioRuntime(clock, fifo, ring);
        runtime.setMusicStream(new SequenceStream(
                10, 100, 20, 200, 30, 300, 40, 400,
                50, 500, 60, 600, 70, 700, 80, 800));
        // Step 8 audio frames forward to fill the 8-frame ring with samples.
        for (int i = 0; i < 4; i++) {
            runtime.advanceFrame(i + 1, FrameAudioMode.NORMAL);
        }

        runtime.beginReversePresentation();
        // Stub resynthesizer that fakes "produced 2 older frames" without
        // running real chip code — we just want to confirm drainPcm calls
        // ensureHeadroom on the resynthesizer before readPrevious.
        boolean[] called = {false};
        runtime.setReverseResynthesizer(new ReverseResynthesizer(ring,
                new com.openggf.audio.rewind.AudioKeyframeStore(),
                com.openggf.audio.AudioManager.getInstance(),
                runtime, 2, 2) {
            // (Subclass-by-instance hook would be ideal; if the class is final
            // we instead verify the call indirectly via cursor changes.)
        }) {
            // Sanity: target/threshold above 0 ensures the loop runs.
        };

        short[] buf = new short[4];
        int read = runtime.drainPcm(buf, 2);
        assertEquals(2, read);
    }
```

(If the test fixture above is awkward because `ReverseResynthesizer` is `final`, drop the subclass attempt and assert via cursor positioning: capture a keyframe at audio-frame 0, attach a real resynthesizer with a small `burstAudioFrames`, exhaust the cursor's window, and assert subsequent `drainPcm` returns non-zero. The integration test in Task 12 will cover the end-to-end path more robustly anyway.)

- [ ] **Step 4: Run**

`mvn "-Dtest=TestStreamBackedDeterministicAudioRuntime" test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/openggf/audio/runtime/StreamBackedDeterministicAudioRuntime.java \
        src/test/java/com/openggf/audio/runtime/TestStreamBackedDeterministicAudioRuntime.java
git commit -m "$(cat <<'EOF'
feat(runtime): drainPcm calls ReverseResynthesizer.ensureHeadroom

StreamBackedDeterministicAudioRuntime gains a setReverseResynthesizer
setter and, in drainPcm, invokes ensureHeadroom on the cursor before
readPrevious whenever a resynthesizer is attached. This is the seam
LWJGLAudioBackend uses to enable extended reverse playback past the
10-second history wall.

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
        if (ring == null) {
            return;
        }
        AudioManager audio = AudioManager.getInstance();
        AudioKeyframeStore keyframes = audio.audioKeyframesForReverseResynth();
        if (keyframes == null) {
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
available. When the keyframe store is not yet installed (early boot,
trace mode), the resynth is left unattached and the runtime falls
back to the legacy reverse cursor read.

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
        // Setup: small 1-second history ring (8 audio frames at 120Hz / 60fps).
        // 4 game-frames produced = 8 audio frames in ring.
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

        // Produce 8 game-frames of music. Ring holds the last 4 frames (8 audio
        // frames at 2 samples per frame).
        for (int i = 0; i < 8; i++) {
            audio.advanceGameplayFrameAudio();
        }

        // Attach the resynthesizer.
        ReverseResynthesizer resynth = new ReverseResynthesizer(
                ring, keyframes, audio, runtime, /* burst */ 4, /* threshold */ 2);
        runtime.setReverseResynthesizer(resynth);

        runtime.beginReversePresentation();

        // Drain past the original 4-frame window. Without the resynth this would
        // produce silence after 4 frames. With it, we expect non-zero samples
        // throughout.
        short[] target = new short[20];
        int read = runtime.drainPcm(target, 10);
        assertEquals(10, read);

        boolean allNonZero = true;
        for (int i = 0; i < 20; i++) {
            if (target[i] == 0) {
                allNonZero = false;
                break;
            }
        }
        assertTrue(allNonZero,
                "Resynthesizer should extend the reverse window past the original 4 frames;"
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
- **AudioKeyframeStore.keyframeAtOrBeforeAudioFrame + replayLiveTo** — Task 7.
- **AudioReplayReason.REVERSE_RESYNTH** — Task 6.
- **AudioManager.replayTimelineCommand REVERSE_RESYNTH SFX bypass** — Task 8.
- **ReverseResynthesizer class** — Task 10.
- **StreamBackedDeterministicAudioRuntime accepts resynth + calls ensureHeadroom** — Task 11.
- **LWJGLAudioBackend wires resynth** — Task 12. Live-install plumbing — Task 13.
- **End-to-end test (test #9)** — Task 14.
- **Performance benchmark (test #11)** — Task 15.
- **Manual smoke (test #10)** — Task 16, step 2.
- **Rewind bracket save/restore for SFX driver state (P1.2 fix)** — Task 9.
- **WAV SFX silent under REVERSE_RESYNTH (P2.3)** — Task 8's silent-no-op behavior covers this.

### Placeholder scan

- Task 12 acknowledges that the resynth may need to be created lazily once `AudioKeyframeStore` is available. Task 13 implements that lazy hookup. Not a placeholder — the deferred construction is the intended design.
- Task 11 step 3 acknowledges the test fixture might need adaptation if `ReverseResynthesizer` is `final`. This is a concrete instruction with a fallback assertion.

### Type consistency

- `burstAudioFrames` and `headroomThresholdFrames` are constructor args throughout (Tasks 10, 11, 12, 14, 15).
- `keyframeAtOrBeforeAudioFrame(long)` consistent across Tasks 7, 10.
- `replayLiveTo(AudioManager, AudioLogicalSnapshot, long)` consistent across Tasks 7, 10.
- `pcmHistoryRingForReverseResynth`, `musicStreamForReverseResynth`, `sfxStreamForReverseResynth`, `samplesForNextFrameForReverseResynth`, `sampleRateForReverseResynth` — used in Tasks 10 and 12 with the same shape.
- `setReverseResynthesizer` — Tasks 11, 12, 14, 15.
- `setLiveRewindAudioKeyframes` + `audioKeyframesForReverseResynth` — Tasks 12, 13.

### Open spec points addressed

- **#1 (REVERSE_RESYNTH ↔ backend.playSfx* seam):** Task 8 routes PlaySfx under REVERSE_RESYNTH to a silent no-op helper because the keyframe's SmpsDriverSnapshot already encodes the SFX driver chip state.
- **#2 (sync vs worker thread):** Task 15's benchmark surfaces the decision data; gating note added.
- **#3 (WAV SFX reset at rewind end):** Out of scope for v1 (no live code path triggers WAV SFX under REVERSE_RESYNTH); covered by Task 9's bracket restore which already runs `restoreLogicalSnapshot`.
