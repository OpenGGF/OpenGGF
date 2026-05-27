package com.openggf.audio;

import com.openggf.audio.rewind.AudioKeyframeStore;
import com.openggf.audio.runtime.AudioFrameClock;
import com.openggf.audio.runtime.AudioOutputFifo;
import com.openggf.audio.runtime.PcmHistoryRing;
import com.openggf.audio.runtime.ReverseResynthesizer;
import com.openggf.audio.runtime.StreamBackedDeterministicAudioRuntime;
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
