package com.openggf.audio;

import com.openggf.audio.rewind.AudioKeyframeStore;
import com.openggf.audio.runtime.AudioFrameClock;
import com.openggf.audio.runtime.AudioOutputFifo;
import com.openggf.audio.runtime.PcmHistoryRing;
import com.openggf.audio.runtime.ReverseResynthesizer;
import com.openggf.audio.runtime.StreamBackedDeterministicAudioRuntime;
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
