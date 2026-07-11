package com.openggf.audio;

import com.openggf.audio.runtime.PcmHistoryRing;
import com.openggf.audio.runtime.StreamBackedDeterministicAudioRuntime;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.debug.PerformanceProfiler;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("performance-measurement")
class TestAudioHistoryAllocationMeasurement {

    private static final int SAMPLE_RATE = 48_000;
    private static final int FRAME_RATE = 60;
    private static final int MEASUREMENT_ITERATIONS = 5;

    @Test
    void reportsLazyAndCaptureHistoryOwnership() throws Exception {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        AudioManager audio = AudioManager.getInstance();
        try {
            cleanupAudio(audio);
            config.resetToDefaults();

            AudioBenchmarkMemoryProbe probe = AudioBenchmarkMemoryProbe.create();

            // Discard equivalent first-touch work (class loading, MXBean setup,
            // JIT compilation) before collecting fresh-instance samples.
            measureArmAllocation(config, probe);
            measureCaptureRuntimeAllocation(config, audio, probe);

            AllocationRun[] armRuns = new AllocationRun[MEASUREMENT_ITERATIONS];
            AllocationRun[] captureRuns = new AllocationRun[MEASUREMENT_ITERATIONS];
            for (int i = 0; i < MEASUREMENT_ITERATIONS; i++) {
                armRuns[i] = measureArmAllocation(config, probe);
                captureRuns[i] = measureCaptureRuntimeAllocation(config, audio, probe);
            }

            long[] armBytes = allocatedBytes(armRuns);
            long[] captureRuntimeBytes = allocatedBytes(captureRuns);
            boolean allocatedSupported = allocationSupported(armRuns)
                    && allocationSupported(captureRuns);
            long armMedianBytes = allocatedSupported ? median(armBytes.clone()) : -1L;
            long captureRuntimeMedianBytes = allocatedSupported
                    ? median(captureRuntimeBytes.clone())
                    : -1L;
            if (allocatedSupported) {
                assertTrue(armMedianBytes >= 0,
                        "arm allocation median must be non-negative when supported");
                assertTrue(captureRuntimeMedianBytes >= 0,
                        "capture-runtime allocation median must be non-negative when supported");
            }

            int historyCapacityFrames = armRuns[0].historyCapacityFrames();
            long structuralPcmBytes = armRuns[0].structuralPcmBytes();
            for (AllocationRun run : armRuns) {
                assertEquals(historyCapacityFrames, run.historyCapacityFrames(),
                        "fresh armed backends must use the same effective history capacity");
                assertEquals(structuralPcmBytes, run.structuralPcmBytes(),
                        "fresh armed backends must allocate the same structural PCM storage");
            }
            for (AllocationRun run : captureRuns) {
                assertEquals(historyCapacityFrames, run.historyCapacityFrames(),
                        "capture runtime must own the same effective history capacity");
                assertEquals(structuralPcmBytes, run.structuralPcmBytes(),
                        "capture runtime must own exactly one equivalent PCM history ring");
            }

            System.out.printf(Locale.ROOT,
                    "AUDIO_HISTORY_ALLOCATION armMedianBytes=%d captureRuntimeMedianBytes=%d "
                            + "allocatedSupported=%s iterations=%d armBytes=%s captureRuntimeBytes=%s "
                            + "historyLimitType=%s historySeconds=%d historySizeMb=%d sampleRate=%d "
                            + "historyCapacityFrames=%d structuralPcmBytes=%d%n",
                    armMedianBytes, captureRuntimeMedianBytes, allocatedSupported,
                    MEASUREMENT_ITERATIONS, formatList(armBytes), formatList(captureRuntimeBytes),
                    config.getString(SonicConfiguration.REWIND_AUDIO_HISTORY_LIMIT_TYPE),
                    config.getInt(SonicConfiguration.REWIND_AUDIO_HISTORY_SECONDS),
                    config.getInt(SonicConfiguration.REWIND_AUDIO_HISTORY_SIZE_MB),
                    SAMPLE_RATE, historyCapacityFrames, structuralPcmBytes);
        } finally {
            try {
                cleanupAudio(audio);
            } finally {
                config.resetToDefaults();
            }
        }
    }

    private static AllocationRun measureArmAllocation(
            SonicConfigurationService config,
            AudioBenchmarkMemoryProbe probe) throws Exception {
        HeadlessSmpsAudioBackend backend =
                new HeadlessSmpsAudioBackend(config, PerformanceProfiler.getInstance());
        try {
            backend.init();
            assertNull(history(backend),
                    "headless backend initialization must leave rewind history unallocated");

            AudioBenchmarkMemoryProbe.RunResult measurement =
                    probe.measureTimedRun(() -> backend.setRewindHistoryArmed(true));
            PcmHistoryRing history = history(backend);
            assertNotNull(history, "arming must allocate backend PCM history");
            return allocationRun(measurement, history);
        } finally {
            try {
                backend.setRewindHistoryArmed(false);
            } finally {
                backend.destroy();
            }
        }
    }

    private static AllocationRun measureCaptureRuntimeAllocation(
            SonicConfigurationService config,
            AudioManager audio,
            AudioBenchmarkMemoryProbe probe) throws Exception {
        HeadlessSmpsAudioBackend backend =
                new HeadlessSmpsAudioBackend(config, PerformanceProfiler.getInstance());
        try {
            audio.setBackend(backend);
            assertNull(history(backend),
                    "freshly initialized capture backend must leave history unallocated");
            audio.setRewindHistoryArmed(true);
            assertNotNull(history(backend),
                    "pre-capture backend must own history while rewind is armed");

            AudioBenchmarkMemoryProbe.RunResult measurement =
                    probe.measureTimedRun(() -> audio.beginCaptureMode(SAMPLE_RATE, FRAME_RATE));
            assertNull(history(backend),
                    "capture runtime must become the only PCM history owner");
            PcmHistoryRing captureHistory = captureRuntimeHistory(audio);
            assertNotNull(captureHistory, "capture runtime must own a PCM history ring");
            return allocationRun(measurement, captureHistory);
        } finally {
            cleanupAudio(audio);
        }
    }

    private static AllocationRun allocationRun(
            AudioBenchmarkMemoryProbe.RunResult measurement,
            PcmHistoryRing history) throws Exception {
        return new AllocationRun(
                measurement.allocatedBytes(),
                measurement.allocatedBytesSupported(),
                capacityFrames(history),
                structuralPcmBytes(history));
    }

    private static void cleanupAudio(AudioManager audio) {
        try {
            // Disarm while capture still provides presentation PCM. Reversing
            // these calls would recreate a throwaway backend history ring.
            audio.setRewindHistoryArmed(false);
        } finally {
            try {
                audio.endCaptureMode();
            } finally {
                try {
                    audio.setBackend(new NullAudioBackend());
                } finally {
                    audio.resetState();
                }
            }
        }
    }

    private static PcmHistoryRing history(AbstractSmpsAudioBackend backend) throws Exception {
        Field field = AbstractSmpsAudioBackend.class.getDeclaredField("pcmHistory");
        field.setAccessible(true);
        return (PcmHistoryRing) field.get(backend);
    }

    private static PcmHistoryRing captureRuntimeHistory(AudioManager audio) throws Exception {
        Field runtimeField = AudioManager.class.getDeclaredField("captureRuntime");
        runtimeField.setAccessible(true);
        StreamBackedDeterministicAudioRuntime runtime =
                (StreamBackedDeterministicAudioRuntime) runtimeField.get(audio);
        assertNotNull(runtime, "capture runtime must be installed during capture measurement");

        Field historyField = StreamBackedDeterministicAudioRuntime.class.getDeclaredField("pcmHistory");
        historyField.setAccessible(true);
        return (PcmHistoryRing) historyField.get(runtime);
    }

    private static int capacityFrames(PcmHistoryRing history) throws Exception {
        Field field = PcmHistoryRing.class.getDeclaredField("capacityFrames");
        field.setAccessible(true);
        return field.getInt(history);
    }

    private static long structuralPcmBytes(PcmHistoryRing history) throws Exception {
        Field field = PcmHistoryRing.class.getDeclaredField("samples");
        field.setAccessible(true);
        return (long) ((short[]) field.get(history)).length * Short.BYTES;
    }

    private static long[] allocatedBytes(AllocationRun[] runs) {
        long[] values = new long[runs.length];
        for (int i = 0; i < runs.length; i++) {
            values[i] = runs[i].allocatedBytes();
        }
        return values;
    }

    private static boolean allocationSupported(AllocationRun[] runs) {
        for (AllocationRun run : runs) {
            if (!run.allocatedBytesSupported()) {
                return false;
            }
        }
        return true;
    }

    private static long median(long[] values) {
        Arrays.sort(values);
        int mid = values.length / 2;
        return values.length % 2 == 1
                ? values[mid]
                : Math.round((values[mid - 1] + values[mid]) / 2.0);
    }

    private static String formatList(long[] values) {
        StringBuilder result = new StringBuilder("[");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                result.append(',');
            }
            result.append(values[i]);
        }
        return result.append(']').toString();
    }

    private record AllocationRun(
            long allocatedBytes,
            boolean allocatedBytesSupported,
            int historyCapacityFrames,
            long structuralPcmBytes) {
    }
}
