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
        // Second keyframe at audioFrame=4 (2 frames x 2 samples).
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
