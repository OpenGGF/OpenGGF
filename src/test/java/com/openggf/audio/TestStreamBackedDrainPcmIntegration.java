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

/**
 * Integration test for {@link StreamBackedDeterministicAudioRuntime#drainPcm}
 * verifying that the reverse-cursor branch invokes
 * {@link ReverseResynthesizer#ensureHeadroom} when a resynth is attached and
 * lowers the cursor floor accordingly.
 *
 * <p>Lives in {@code com.openggf.audio} (rather than alongside the other
 * StreamBacked tests in {@code com.openggf.audio.runtime}) because the test
 * needs the package-private {@link AudioManager#setDeterministicAudioRuntime}
 * to install the test's runtime so {@link AudioManager#advanceGameplayFrameAudio}
 * advances THIS runtime's clock.
 */
class TestStreamBackedDrainPcmIntegration {

    @AfterEach
    void tearDown() {
        AudioManager.getInstance().resetState();
        AudioManager.getInstance().setBackend(new NullAudioBackend());
    }

    @Test
    void drainPcmInvokesReverseResynthesizerEnsureHeadroom() {
        // Small ring so that producing more audio than its capacity wraps the
        // floor up off zero. Without wrap, beginReversePresentation creates a
        // cursor with oldestReadableFrame=0 and ensureHeadroom has no room
        // to lower it further (start-of-history floor).
        PcmHistoryRing ring = new PcmHistoryRing(8);
        AudioFrameClock clock = new AudioFrameClock(120, 60);
        AudioOutputFifo fifo = new AudioOutputFifo(120);
        StreamBackedDeterministicAudioRuntime runtime =
                new StreamBackedDeterministicAudioRuntime(clock, fifo, ring);
        runtime.setMusicStream(new ScriptedAudioStream((short) 1, (short) 1));

        AudioManager audio = AudioManager.getInstance();
        audio.resetState();
        audio.setDeterministicAudioRuntime(runtime);
        audio.setBackend(new NullAudioBackend());

        AudioKeyframeStore keyframes = new AudioKeyframeStore();
        keyframes.capture(0L, audio);

        // Produce 12 game-frames of audio (24 audio-frames at 120/60). The
        // 8-frame ring keeps the last 8 (audio frames 16..23), so the
        // reverse cursor starts at oldestReadable=16.
        for (int i = 0; i < 12; i++) {
            audio.advanceGameplayFrameAudio();
        }

        runtime.beginReversePresentation();
        // Attach a real resynthesizer with a small burst so a single
        // drainPcm call should trigger ensureHeadroom and lower the floor.
        ReverseResynthesizer resynth = new ReverseResynthesizer(
                ring, keyframes, audio, runtime, /* burst */ 2, /* threshold */ 4);
        runtime.setReverseResynthesizer(resynth);

        // Capture floor before draining.
        long floorBefore = runtime.getActiveReverseCursorForTest().oldestReadableFrame();
        assertTrue(floorBefore > 0,
                "Ring must have wrapped so floor>0 for the assertion below to be meaningful;"
                        + " floorBefore=" + floorBefore);

        // Drain a request larger than the ring's capacity (8). The loop in
        // drainPcm must iterate, calling ensureHeadroom between
        // readPrevious calls so the second iteration can extend the floor
        // backward into newly-resynthesized history.
        short[] sink = new short[24];
        runtime.drainPcm(sink, 12);

        long floorAfter = runtime.getActiveReverseCursorForTest().oldestReadableFrame();
        assertTrue(floorAfter < floorBefore,
                "drainPcm with attached resynth should call ensureHeadroom and lower"
                        + " the cursor floor; floorBefore=" + floorBefore
                        + " floorAfter=" + floorAfter);
    }

    @Test
    void reverseDrainExtendsPastHistoryWindowWithResynthesizer() {
        // Setup: small 8-audio-frame history ring (at 120Hz / 60fps, that's
        // 4 game-frames worth — each game frame produces 2 audio frames).
        PcmHistoryRing ring = new PcmHistoryRing(8);
        AudioFrameClock clock = new AudioFrameClock(120, 60);
        AudioOutputFifo fifo = new AudioOutputFifo(120);
        StreamBackedDeterministicAudioRuntime runtime =
                new StreamBackedDeterministicAudioRuntime(clock, fifo, ring);
        runtime.setMusicStream(new ScriptedAudioStream(
                (short) 1, (short) 1));

        // Drive AudioManager so it captures the runtime as its current.
        AudioManager audio = AudioManager.getInstance();
        audio.resetState();
        audio.setDeterministicAudioRuntime(runtime);
        audio.setBackend(new NullAudioBackend());

        AudioKeyframeStore keyframes = new AudioKeyframeStore();
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
        ReverseResynthesizer resynth =
                new ReverseResynthesizer(
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
}
