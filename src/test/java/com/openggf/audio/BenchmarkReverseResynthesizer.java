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

import java.util.Arrays;
import java.util.List;

/**
 * Performance benchmark for {@link ReverseResynthesizer}. Not a parity test —
 * measures p50/p95/p99 burst duration for synthesized historical PCM windows
 * of representative sizes.
 *
 * <p>Post-Task-5 the worker is no longer driven from {@code drainPcm}; this
 * benchmark instead drives {@link ReverseResynthesizer#runOneIterationForPrefill}
 * directly so the timed work is a single chip-emulation burst against a
 * full ring + a frame-0 keyframe.
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

            runtime.beginReversePresentation();
            // Drain a chunk so the cursor has physical-slot budget for the
            // burst we're about to time.
            short[] sink = new short[sampleRate * 2];
            runtime.drainPcm(sink, sampleRate / 2);
            PcmHistoryRing.ReverseCursor cursor = runtime.activeReverseCursor();

            ReverseAudioSession session = new ReverseAudioSession(
                    ring,
                    keyframes.frozenView(),
                    List.of(),
                    sampleRate, 60,
                    SmpsSequencer.Region.NTSC,
                    burstAudioFrames, sampleRate / 2,
                    SmpsDriverSnapshot.liveReferences(),
                    () -> new SmpsDriver(sampleRate),
                    new NullDataResolver(),
                    /* audioProfile */ null,
                    /* defaultSequencerConfig */ null,
                    false, false, false);
            ReverseResynthesizer resynth = new ReverseResynthesizer(session);

            long start = System.nanoTime();
            resynth.runOneIterationForPrefill(cursor);
            durations[i] = System.nanoTime() - start;

            runtime.endReversePresentation();
        }
        return durations;
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
