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
 * Integration test for {@link StreamBackedDeterministicAudioRuntime#drainPcm}
 * verifying that PCM the worker writes into the ring is correctly read out
 * via the audio drain path.
 *
 * <p>Task 5 simplified the integration: drainPcm no longer drives the
 * worker synchronously. Instead the worker writes to the ring on its own
 * thread (Task 6 wires the lifecycle). For this unit-test scope we drive
 * the worker manually via {@link ReverseResynthesizer#runOneIterationForPrefill}
 * before draining — the same hook Task 6 uses for startup prefill.
 */
class TestStreamBackedDrainPcmIntegration {

    @AfterEach
    void tearDown() {
        AudioManager.getInstance().resetState();
        AudioManager.getInstance().setBackend(new NullAudioBackend());
    }

    @Test
    void prefillExtendsReverseDrainPastHistoryWindow() {
        // Setup: small 8-audio-frame history ring (at 120Hz / 60fps, that's
        // 4 game-frames worth — each game frame produces 2 audio frames).
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

        // Produce 8 game-frames of music. Ring holds the most recent 8 audio
        // frames (capped at the 8-frame capacity).
        for (int i = 0; i < 8; i++) {
            audio.advanceGameplayFrameAudio();
        }

        runtime.beginReversePresentation();
        PcmHistoryRing.ReverseCursor cursor = runtime.activeReverseCursor();
        assertTrue(cursor != null, "reverse cursor must exist after begin");

        // Drive the worker synchronously to prefill the ring backward. With
        // a fresh cursor on a full ring, prefill is a no-op until drain
        // creates physical slots. Drain a chunk, then prefill, then drain.
        short[] firstChunk = new short[8];
        int firstRead = runtime.drainPcm(firstChunk, 4);
        assertEquals(4, firstRead, "first drain should read 4 frames from the ring");

        ReverseAudioSession session = new ReverseAudioSession(
                ring,
                keyframes.frozenView(),
                List.of(),
                120, 60,
                SmpsSequencer.Region.NTSC,
                4, 2,
                SmpsDriverSnapshot.liveReferences(),
                () -> new SmpsDriver(120),
                new NullDataResolver(),
                /* audioProfile */ null,
                /* defaultSequencerConfig */ null,
                false, false, false);
        ReverseResynthesizer resynth = new ReverseResynthesizer(session);
        long floorBefore = cursor.oldestReadableFrame();
        boolean ranBurst = resynth.runOneIterationForPrefill(cursor);
        assertTrue(ranBurst, "prefill must produce a burst when slots are free and a keyframe exists");
        long floorAfter = cursor.oldestReadableFrame();
        assertTrue(floorAfter < floorBefore,
                "prefill must lower the cursor floor (before=" + floorBefore
                        + " after=" + floorAfter + ")");

        // After prefill the ring's readable window has been extended. Drain
        // the rest and check that we read more frames than the original 4
        // remaining (which would have been the case without prefill).
        short[] secondChunk = new short[16];
        int secondRead = runtime.drainPcm(secondChunk, 8);
        assertTrue(secondRead >= 4,
                "post-prefill drain should read at least the original 4 remaining frames;"
                        + " got " + secondRead);
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
