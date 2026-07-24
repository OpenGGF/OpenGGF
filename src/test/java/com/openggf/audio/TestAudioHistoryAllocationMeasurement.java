package com.openggf.audio;

import com.openggf.audio.runtime.PcmHistoryRing;
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
                        "offline capture must reuse the producer history capacity");
                assertEquals(structuralPcmBytes, run.structuralPcmBytes(),
                        "offline capture must reuse exactly one PCM history ring");
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
            assertNull(history(backend),
                    "retired backend must never own presentation history");
            assertTrue(audio.releaseStateForTesting().producer().historyArmed());

            AudioBenchmarkMemoryProbe.RunResult measurement =
                    probe.measureTimedRun(() -> audio.beginCaptureMode(SAMPLE_RATE, FRAME_RATE));
            assertNull(history(backend),
                    "offline capture must not reactivate backend history");
            PcmHistoryRing captureHistory = captureLeaseHistory(audio);
            assertNotNull(captureHistory,
                    "the presentation producer must own a PCM history ring");
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

    /**
     * Offline capture no longer installs its own streaming runtime: it attaches
     * one non-consuming lease to the authoritative presentation producer, which
     * already owns the single PCM history ring. Measure that ring.
     */
    private static PcmHistoryRing captureLeaseHistory(AudioManager audio) throws Exception {
        Field producerField = AudioManager.class.getDeclaredField("shadowProducer");
        producerField.setAccessible(true);
        Object producer = producerField.get(audio);
        assertNotNull(producer, "the presentation producer must own capture history");

        Field historyField = producer.getClass().getDeclaredField("history");
        historyField.setAccessible(true);
        return (PcmHistoryRing) historyField.get(producer);
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
