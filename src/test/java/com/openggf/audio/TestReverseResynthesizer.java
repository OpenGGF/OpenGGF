package com.openggf.audio;

import com.openggf.audio.driver.SmpsDriver;
import com.openggf.audio.rewind.AudioCommand;
import com.openggf.audio.rewind.AudioKeyframeStore;
import com.openggf.audio.rewind.SmpsDriverSnapshot;
import com.openggf.audio.runtime.AudioCommandDataResolver;
import com.openggf.audio.runtime.AudioFrameClock;
import com.openggf.audio.runtime.AudioOutputFifo;
import com.openggf.audio.runtime.PcmHistoryRing;
import com.openggf.audio.runtime.ReverseAudioSession;
import com.openggf.audio.runtime.ReverseResynthesizer;
import com.openggf.audio.runtime.StreamBackedDeterministicAudioRuntime;
import com.openggf.audio.smps.SmpsSequencer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit coverage for the worker-shape {@link ReverseResynthesizer}: drive
 * {@code runOneIterationForPrefill} synchronously against a frozen
 * {@link ReverseAudioSession} and assert the cursor floor moves (or
 * doesn't) as expected. The Runnable {@code run()} loop is left to
 * Task 6's lifecycle wiring + the end-to-end smoke.
 */
class TestReverseResynthesizer {

    @AfterEach
    void tearDown() {
        AudioManager.getInstance().resetState();
        AudioManager.getInstance().setBackend(new NullAudioBackend());
    }

    @Test
    void prefillReturnsFalseWhenNoKeyframes() {
        PcmHistoryRing ring = new PcmHistoryRing(32);
        ring.write(new short[]{0, 0, 0, 0}, 2);
        PcmHistoryRing.ReverseCursor cursor = ring.createReverseCursor();

        ReverseAudioSession session = buildEmptySession(ring, new AudioKeyframeStore(), 8, 4);
        ReverseResynthesizer resynth = new ReverseResynthesizer(session);

        long beforeOldest = cursor.oldestReadableFrame();
        assertEquals(false, resynth.runOneIterationForPrefill(cursor),
                "with no keyframes the worker has nothing to restore from");
        assertEquals(beforeOldest, cursor.oldestReadableFrame(),
                "no prepend should have happened");
    }

    @Test
    void prefillExtendsWindowWhenKeyframeAvailable() {
        PcmHistoryRing ring = new PcmHistoryRing(8);
        AudioManager audio = AudioManager.getInstance();
        audio.resetState();
        StreamBackedDeterministicAudioRuntime runtime =
                new StreamBackedDeterministicAudioRuntime(
                        new AudioFrameClock(120, 60), new AudioOutputFifo(120), ring);
        audio.setDeterministicAudioRuntime(runtime);
        audio.setBackend(new NullAudioBackend());
        runtime.setMusicStream(new ScriptedAudioStream((short) 1, (short) 1));

        AudioKeyframeStore store = new AudioKeyframeStore();
        store.capture(0L, audio);

        // Produce 12 game-frames of audio (24 audio frames) so the ring is
        // full and the cursor's oldestReadableFrame is well above 0.
        for (int i = 0; i < 12; i++) {
            audio.advanceGameplayFrameAudio();
        }
        PcmHistoryRing.ReverseCursor cursor = ring.createReverseCursor();
        // Consume 4 frames so the ring has physical-slot budget for the prepend.
        short[] drain = new short[8];
        cursor.readPrevious(drain, 4);
        long beforeOldest = cursor.oldestReadableFrame();

        ReverseAudioSession session = buildEmptySession(ring, store, 4, 2);
        ReverseResynthesizer resynth = new ReverseResynthesizer(session);

        assertEquals(true, resynth.runOneIterationForPrefill(cursor));
        assertTrue(cursor.oldestReadableFrame() < beforeOldest,
                "prefill must lower oldestReadableFrame after a successful burst (before="
                        + beforeOldest + " after=" + cursor.oldestReadableFrame() + ")");
    }

    @Test
    void prefillIsNoOpWhenRingIsFull() {
        PcmHistoryRing ring = new PcmHistoryRing(8);
        AudioManager audio = AudioManager.getInstance();
        audio.resetState();
        StreamBackedDeterministicAudioRuntime runtime =
                new StreamBackedDeterministicAudioRuntime(
                        new AudioFrameClock(120, 60), new AudioOutputFifo(120), ring);
        audio.setDeterministicAudioRuntime(runtime);
        audio.setBackend(new NullAudioBackend());
        runtime.setMusicStream(new ScriptedAudioStream((short) 1, (short) 1));

        AudioKeyframeStore store = new AudioKeyframeStore();
        store.capture(0L, audio);
        for (int i = 0; i < 12; i++) {
            audio.advanceGameplayFrameAudio();
        }
        // Fresh cursor — entire ring is unread. maxPrependableFrames is 0.
        PcmHistoryRing.ReverseCursor cursor = ring.createReverseCursor();
        long beforeOldest = cursor.oldestReadableFrame();

        ReverseAudioSession session = buildEmptySession(ring, store, 4, 2);
        ReverseResynthesizer resynth = new ReverseResynthesizer(session);

        assertEquals(false, resynth.runOneIterationForPrefill(cursor),
                "ring at capacity must refuse prefill so drain can free slots first");
        assertEquals(beforeOldest, cursor.oldestReadableFrame(),
                "cursor floor must be unchanged");
    }

    @Test
    void detachSessionStopsFurtherBursts() {
        PcmHistoryRing ring = new PcmHistoryRing(8);
        AudioManager audio = AudioManager.getInstance();
        audio.resetState();
        StreamBackedDeterministicAudioRuntime runtime =
                new StreamBackedDeterministicAudioRuntime(
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
        short[] drain = new short[8];
        cursor.readPrevious(drain, 4);

        ReverseAudioSession session = buildEmptySession(ring, store, 4, 2);
        ReverseResynthesizer resynth = new ReverseResynthesizer(session);
        resynth.detachSession();

        long beforeOldest = cursor.oldestReadableFrame();
        assertEquals(false, resynth.runOneIterationForPrefill(cursor),
                "detached worker must not attempt a burst");
        assertEquals(beforeOldest, cursor.oldestReadableFrame());
    }

    /** Builds a session with no audio profile, no command data, an empty
     *  timeline, and a NoOp resolver. Sufficient for cursor-floor tests
     *  where the timeline is empty so no PlayMusic/PlaySfx commands fire. */
    private static ReverseAudioSession buildEmptySession(
            PcmHistoryRing ring, AudioKeyframeStore store,
            int burstFrames, int headroomThreshold) {
        return new ReverseAudioSession(
                ring,
                store.frozenView(),
                /* audioFloor */ 0L,
                List.of(),
                120, 60,
                SmpsSequencer.Region.NTSC,
                burstFrames, headroomThreshold,
                SmpsDriverSnapshot.liveReferences(),
                () -> new SmpsDriver(120),
                new NullDataResolver(),
                /* audioProfile */ null,
                /* defaultSequencerConfig */ null,
                /* dacInterpolate */ false,
                /* fm6DacOff */ false,
                /* psgNoiseShiftOnEveryToggle */ false);
    }

    private static final class NullDataResolver implements AudioCommandDataResolver {
        @Override
        public MusicData resolveMusic(AudioCommand.PlayMusic command) {
            return null;
        }

        @Override
        public SfxData resolveSfx(AudioCommand.PlaySfx command) {
            return null;
        }
    }
}
