package com.openggf.audio;

import com.openggf.audio.rewind.AudioKeyframeStore;
import com.openggf.audio.rewind.AudioLogicalSnapshot;
import com.openggf.audio.runtime.AudioFrameClock;
import com.openggf.audio.runtime.AudioOutputFifo;
import com.openggf.audio.runtime.StreamBackedDeterministicAudioRuntime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link AudioKeyframeStore.FrozenView} is genuinely
 * disconnected from the live store: post-snapshot mutations to the store
 * (capture, discardAfter, clear) must not change what the frozen view
 * sees. This is the foundation for the worker thread reading keyframes
 * without synchronising against live game-thread activity.
 */
class TestAudioKeyframeStoreFrozenView {

    @AfterEach
    void tearDown() {
        AudioManager.getInstance().resetState();
        AudioManager.getInstance().setBackend(new NullAudioBackend());
    }

    @Test
    void frozenViewSurvivesSubsequentCapturesAndDiscards() {
        AudioManager audio = AudioManager.getInstance();
        audio.resetState();
        audio.setDeterministicAudioRuntime(new StreamBackedDeterministicAudioRuntime(
                new AudioFrameClock(120, 60), new AudioOutputFifo(120)));
        audio.setBackend(new NullAudioBackend());

        AudioKeyframeStore store = new AudioKeyframeStore();
        store.capture(0L, audio);
        audio.advanceGameplayFrameAudio();
        audio.advanceGameplayFrameAudio();
        store.capture(2L, audio);
        audio.advanceGameplayFrameAudio();
        audio.advanceGameplayFrameAudio();
        store.capture(4L, audio);

        // Snapshot at this point — 3 keyframes.
        AudioKeyframeStore.FrozenView frozen = store.frozenView();
        assertEquals(3, frozen.size());

        // Live store mutates. Frozen view must stay at 3 entries with the
        // exact same content.
        store.discardAfter(2L); // drops keyframe at game-frame 4
        audio.advanceGameplayFrameAudio();
        audio.advanceGameplayFrameAudio();
        store.capture(6L, audio); // adds a new keyframe
        store.clear();            // wipe the live store entirely

        assertEquals(3, frozen.size(),
                "frozen view size must not change after live mutations");

        AudioLogicalSnapshot four = frozen.keyframeAtOrBeforeAudioFrame(10L);
        assertNotNull(four);
        assertEquals(8L, four.backend().clockSnapshot().totalSamplesProduced(),
                "frozen view's audio-frame 8 keyframe must still be reachable");

        AudioLogicalSnapshot two = frozen.keyframeAtOrBeforeAudioFrame(5L);
        assertNotNull(two);
        assertEquals(4L, two.backend().clockSnapshot().totalSamplesProduced());

        AudioLogicalSnapshot negative = frozen.keyframeAtOrBeforeAudioFrame(-1L);
        assertNull(negative);
    }

    @Test
    void frozenViewIsEmptyWhenStoreIsEmpty() {
        AudioKeyframeStore store = new AudioKeyframeStore();
        AudioKeyframeStore.FrozenView frozen = store.frozenView();
        assertTrue(frozen.isEmpty());
        assertEquals(0, frozen.size());
        assertNull(frozen.keyframeAtOrBeforeAudioFrame(0L));
    }
}
