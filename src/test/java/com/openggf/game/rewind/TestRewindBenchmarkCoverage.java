package com.openggf.game.rewind;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestRewindBenchmarkCoverage {
    private final Map<String, String> originalProperties = new LinkedHashMap<>();

    @BeforeEach
    void rememberProperties() {
        remember("openggf.rewind.benchmark.tailBudgets");
        remember("openggf.rewind.benchmark.phase2.capture.maxP99Ns");
        remember("openggf.rewind.benchmark.audioBudgets");
        remember("openggf.rewind.benchmark.audio.capture.maxP95Ns");
    }

    @AfterEach
    void restoreProperties() {
        for (var e : originalProperties.entrySet()) {
            if (e.getValue() == null) {
                System.clearProperty(e.getKey());
            } else {
                System.setProperty(e.getKey(), e.getValue());
            }
        }
    }

    @Test
    void captureAttributionReportsEveryRegisteredSubsystem() {
        RewindRegistry registry = new RewindRegistry();
        registry.register(new IntegerSubsystem("camera", 10));
        registry.register(new IntegerSubsystem("rings", 20));
        Map<String, BenchmarkTiming> timings = new LinkedHashMap<>();

        RewindBenchmark.TimedCompositeSnapshot first =
                RewindBenchmark.captureWithAttributionForTests(registry, timings);
        RewindBenchmark.captureWithAttributionForTests(registry, timings);
        Map<String, BenchmarkResults.PhaseStats> stats =
                RewindBenchmark.summarizeSubsystemTimingsForTests(timings);

        assertEquals(10, first.snapshot().get("camera"));
        assertEquals(20, first.snapshot().get("rings"));
        assertEquals(2, stats.get("camera").sampleCount());
        assertEquals(2, stats.get("rings").sampleCount());
        assertTrue(stats.get("camera").maxNs() >= 0);
    }

    @Test
    void restoreAttributionReportsRegisteredSubsystemsAndCallbacks() {
        RewindRegistry registry = new RewindRegistry();
        IntegerSubsystem camera = new IntegerSubsystem("camera", 0);
        IntegerSubsystem rings = new IntegerSubsystem("rings", 0);
        AtomicInteger callbacks = new AtomicInteger();
        registry.register(camera);
        registry.register(rings);
        registry.registerPostRestoreCallback("rebinding", callbacks::incrementAndGet);
        CompositeSnapshot snapshot = new CompositeSnapshot(Map.of("camera", 12, "rings", 34));
        Map<String, BenchmarkTiming> timings = new LinkedHashMap<>();

        RewindBenchmark.restoreWithAttributionForTests(registry, snapshot, timings);
        RewindBenchmark.restoreWithAttributionForTests(registry, snapshot, timings);
        Map<String, BenchmarkResults.PhaseStats> stats =
                RewindBenchmark.summarizeSubsystemTimingsForTests(timings);

        assertEquals(12, camera.value);
        assertEquals(34, rings.value);
        assertEquals(2, callbacks.get());
        assertEquals(2, stats.get("camera").sampleCount());
        assertEquals(2, stats.get("rings").sampleCount());
        assertEquals(2, stats.get("post-restore-callbacks").sampleCount());
    }

    @Test
    void tailBudgetGatesCheckP95P99AndMaxWhenOptedIn() {
        System.setProperty("openggf.rewind.benchmark.tailBudgets", "true");
        System.setProperty("openggf.rewind.benchmark.phase2.capture.maxP99Ns", "10");
        Map<String, BenchmarkResults> results = corePhaseResults(
                "phase2.capture",
                new BenchmarkResults.PhaseStats(1, 1, 1, 1, 11, 11));

        AssertionError error = assertThrows(AssertionError.class,
                () -> RewindBenchmark.assertBenchmarkTailBoundsForTests(results));

        assertTrue(error.getMessage().contains("phase2.capture p99"));
    }

    @Test
    void audioOptInBudgetsCheckTailPercentiles() {
        System.setProperty("openggf.rewind.benchmark.audioBudgets", "true");
        System.setProperty("openggf.rewind.benchmark.audio.capture.maxP95Ns", "10");
        Map<String, BenchmarkResults> results = audioPhaseResults(
                "phase7.audio.capture-logical",
                new BenchmarkResults.PhaseStats(1, 1, 1, 11, 11, 11));

        AssertionError error = assertThrows(AssertionError.class,
                () -> RewindBenchmark.assertAudioBenchmarkBoundsForTests(results));

        assertTrue(error.getMessage().contains("phase7.audio.capture-logical p95"));
    }

    @Test
    void phaseMarkerCarriesJfrFriendlyPhaseAndDetail() {
        RewindBenchmark.BenchmarkPhaseMarker marker =
                RewindBenchmark.beginPhaseMarkerForTests("phase5.hot-seek", "cached");

        assertEquals("phase5.hot-seek", marker.phaseNameForTests());
        assertEquals("cached", marker.detailForTests());

        marker.close();
    }

    private void remember(String key) {
        originalProperties.put(key, System.getProperty(key));
        System.clearProperty(key);
    }

    private static Map<String, BenchmarkResults> corePhaseResults(
            String overridePhase,
            BenchmarkResults.PhaseStats overrideStats) {
        Map<String, BenchmarkResults> results = new LinkedHashMap<>();
        putCore(results, "phase2.capture", overridePhase, overrideStats);
        putCore(results, "phase3.restore", overridePhase, overrideStats);
        putCore(results, "phase4.cold-seek", overridePhase, overrideStats);
        putCore(results, "phase5.hot-seek.first-segment-expansion", overridePhase, overrideStats);
        putCore(results, "phase5.hot-seek.cached-same-segment", overridePhase, overrideStats);
        putCore(results, "phase5.hot-seek.segment-crossing-scrub", overridePhase, overrideStats);
        return results;
    }

    private static void putCore(
            Map<String, BenchmarkResults> results,
            String phase,
            String overridePhase,
            BenchmarkResults.PhaseStats overrideStats) {
        BenchmarkResults.PhaseStats stats = phase.equals(overridePhase)
                ? overrideStats
                : new BenchmarkResults.PhaseStats(1, 1, 1, 1, 1, 1);
        results.put(phase, new BenchmarkResults(phase, stats, 0, Map.of()));
    }

    private static Map<String, BenchmarkResults> audioPhaseResults(
            String overridePhase,
            BenchmarkResults.PhaseStats overrideStats) {
        Map<String, BenchmarkResults> results = new LinkedHashMap<>();
        putAudio(results, "phase7.audio.capture-logical", overridePhase, overrideStats);
        putAudio(results, "phase7.audio.restore-logical", overridePhase, overrideStats);
        putAudio(results, "phase7.audio.replay-logical", overridePhase, overrideStats);
        putAudio(results, "phase8.audio.presentation-forward-pcm", overridePhase, overrideStats);
        putAudio(results, "phase8.audio.presentation-reverse-pcm", overridePhase, overrideStats);
        return results;
    }

    private static void putAudio(
            Map<String, BenchmarkResults> results,
            String phase,
            String overridePhase,
            BenchmarkResults.PhaseStats overrideStats) {
        BenchmarkResults.PhaseStats stats = phase.equals(overridePhase)
                ? overrideStats
                : new BenchmarkResults.PhaseStats(1, 1, 1, 1, 1, 1);
        Map<String, Long> counters = phase.equals("phase7.audio.replay-logical")
                ? Map.of("allocatedBytesSupported", 0L, "allocatedBytes", -1L)
                : Map.of();
        results.put(phase, new BenchmarkResults(phase, stats, 0, Map.of(), counters));
    }

    private static final class IntegerSubsystem implements RewindSnapshottable<Integer> {
        private final String key;
        private int value;

        private IntegerSubsystem(String key, int value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public String key() {
            return key;
        }

        @Override
        public Integer capture() {
            return value;
        }

        @Override
        public void restore(Integer snapshot) {
            value = snapshot;
        }
    }
}
