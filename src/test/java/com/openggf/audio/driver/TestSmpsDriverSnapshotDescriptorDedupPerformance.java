package com.openggf.audio.driver;

import com.openggf.audio.AudioManager;
import com.openggf.audio.AudioTestFixtures;
import com.openggf.audio.rewind.SmpsDriverSnapshot;
import com.openggf.audio.rewind.SmpsSourceDescriptor;
import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.audio.smps.SmpsSequencerConfig;
import com.sun.management.ThreadMXBean;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSmpsDriverSnapshotDescriptorDedupPerformance {

    private static final int SFX_COUNT = 32;
    private static final int CAPTURES_PER_REPETITION = 20;
    private static volatile SmpsDriverSnapshot escapedSnapshot;

    @Test
    void sharedLargeFallbackIsHashedOncePerOptimizedCapture() {
        ThreadMXBean bean = allocationBeanOrSkip();
        Fixture fixture = fixture();
        SmpsSourceDescriptor expectedFallback = SmpsSourceDescriptor.from(fixture.fallback);
        fixture.fallback.resetDataReads();

        for (int i = 0; i < 10; i++) {
            escapedSnapshot = frozenLegacyCapture(fixture);
            escapedSnapshot = fixture.driver.captureSnapshot();
        }

        long[] legacyBytes = new long[5];
        long[] optimizedBytes = new long[5];
        long[] legacyNanos = new long[5];
        long[] optimizedNanos = new long[5];
        for (int repetition = 0; repetition < legacyBytes.length; repetition++) {
            CaptureRun legacy;
            CaptureRun optimized;
            if ((repetition & 1) == 0) {
                legacy = measure(bean, fixture.fallback, () -> frozenLegacyCapture(fixture));
                validateRun(legacy, fixture, expectedFallback, SFX_COUNT, "legacy " + repetition);
                optimized = measure(bean, fixture.fallback, fixture.driver::captureSnapshot);
                validateRun(optimized, fixture, expectedFallback, 1, "optimized " + repetition);
            } else {
                optimized = measure(bean, fixture.fallback, fixture.driver::captureSnapshot);
                validateRun(optimized, fixture, expectedFallback, 1, "optimized " + repetition);
                legacy = measure(bean, fixture.fallback, () -> frozenLegacyCapture(fixture));
                validateRun(legacy, fixture, expectedFallback, SFX_COUNT, "legacy " + repetition);
            }
            legacyBytes[repetition] = legacy.bytesPerCapture;
            optimizedBytes[repetition] = optimized.bytesPerCapture;
            legacyNanos[repetition] = legacy.nanosPerCapture;
            optimizedNanos[repetition] = optimized.nanosPerCapture;
        }

        long legacyAllocationMedian = median(legacyBytes);
        long optimizedAllocationMedian = median(optimizedBytes);
        long legacyTimingMedian = median(legacyNanos);
        long optimizedTimingMedian = median(optimizedNanos);
        System.out.printf("smps fallback descriptor capture legacyBytes=%s optimizedBytes=%s "
                        + "legacyNanos=%s optimizedNanos=%s medians=%d/%d bytes %d/%d ns%n",
                Arrays.toString(legacyBytes), Arrays.toString(optimizedBytes),
                Arrays.toString(legacyNanos), Arrays.toString(optimizedNanos),
                legacyAllocationMedian, optimizedAllocationMedian,
                legacyTimingMedian, optimizedTimingMedian);
        assertTrue(optimizedAllocationMedian + 256 < legacyAllocationMedian,
                "capture-local descriptor dedup should remove repeated descriptor allocation");
        assertTrue(optimizedTimingMedian * 2 < legacyTimingMedian,
                "hashing one 256 KiB fallback should take less than half of hashing it 32 times");
    }

    private static CaptureRun measure(ThreadMXBean bean,
                                      CountingSmpsData fallback,
                                      Supplier<SmpsDriverSnapshot> capture) {
        fallback.resetDataReads();
        long threadId = Thread.currentThread().threadId();
        long allocatedBefore = bean.getThreadAllocatedBytes(threadId);
        long nanosBefore = System.nanoTime();
        long workCount = 0;
        for (int i = 0; i < CAPTURES_PER_REPETITION; i++) {
            SmpsDriverSnapshot snapshot = capture.get();
            workCount += snapshot.sequencers().size();
            escapedSnapshot = snapshot;
        }
        long elapsedNanos = System.nanoTime() - nanosBefore;
        long allocatedAfter = bean.getThreadAllocatedBytes(threadId);
        return new CaptureRun(
                (allocatedAfter - allocatedBefore) / CAPTURES_PER_REPETITION,
                elapsedNanos / CAPTURES_PER_REPETITION,
                fallback.dataReads(),
                workCount,
                escapedSnapshot);
    }

    private static void validateRun(CaptureRun run,
                                    Fixture fixture,
                                    SmpsSourceDescriptor expectedFallback,
                                    int hashesPerCapture,
                                    String label) {
        assertEquals((long) hashesPerCapture * CAPTURES_PER_REPETITION,
                run.hashReads, label + " hash work");
        assertEquals(33L * CAPTURES_PER_REPETITION, run.workCount, label + " snapshot entry work");
        List<SmpsDriverSnapshot.SequencerEntry> entries = run.snapshot.sequencers();
        assertEquals(33, entries.size(), label + " entry count");
        assertNull(entries.getFirst().fallbackVoiceSource(), label + " music fallback");
        assertEquals(fixture.sequencers.getFirst().getSourceDescriptor(), entries.getFirst().source(),
                label + " music descriptor");
        SmpsSourceDescriptor firstFallback = entries.get(1).fallbackVoiceSource();
        assertEquals(expectedFallback, firstFallback, label + " fallback descriptor");
        for (int index = 1; index < entries.size(); index++) {
            assertEquals(fixture.sequencers.get(index).getSourceDescriptor(), entries.get(index).source(),
                    label + " source descriptor " + index);
            assertEquals(expectedFallback, entries.get(index).fallbackVoiceSource(),
                    label + " fallback descriptor " + index);
        }
        if (hashesPerCapture == 1) {
            for (int index = 2; index < entries.size(); index++) {
                assertSame(firstFallback, entries.get(index).fallbackVoiceSource(),
                        label + " should reuse one capture-local descriptor identity");
            }
        }
    }

    /** Exact pre-fix capture algorithm for this default, unlocked driver fixture. */
    private static SmpsDriverSnapshot frozenLegacyCapture(Fixture fixture) {
        IdentityHashMap<SmpsSequencer, Integer> sequencerIds = new IdentityHashMap<>();
        IdentityHashMap<AbstractSmpsData, SmpsSourceDescriptor> sourceDescriptors =
                new IdentityHashMap<>();
        for (SmpsSequencer sequencer : fixture.sequencers) {
            sourceDescriptors.put(sequencer.getSmpsData(), sequencer.getSourceDescriptor());
        }
        List<SmpsDriverSnapshot.SequencerEntry> entries = new ArrayList<>(fixture.sequencers.size());
        for (int index = 0; index < fixture.sequencers.size(); index++) {
            SmpsSequencer sequencer = fixture.sequencers.get(index);
            sequencerIds.put(sequencer, index);
            AbstractSmpsData fallback = sequencer.getFallbackVoiceData();
            SmpsSourceDescriptor fallbackDescriptor = fallback != null
                    ? sourceDescriptors.getOrDefault(fallback, SmpsSourceDescriptor.from(fallback))
                    : null;
            entries.add(new SmpsDriverSnapshot.SequencerEntry(
                    sequencer.isSfx(),
                    sequencer.getSourceDescriptor(),
                    fallbackDescriptor,
                    sequencer.getSmpsData(),
                    sequencer.getDacData(),
                    sequencer.getAudioManager(),
                    sequencer.getConfig(),
                    sequencer.captureSnapshot()));
        }
        int[] fmLocks = new int[6];
        int[] psgLocks = new int[4];
        Arrays.fill(fmLocks, -1);
        Arrays.fill(psgLocks, -1);
        // The production legacy path queried this capture-local identity table
        // for every unlocked FM/PSG slot even though all values remain -1.
        for (int index = 0; index < fmLocks.length + psgLocks.length; index++) {
            sequencerIds.get(null);
        }
        return new SmpsDriverSnapshot(
                SmpsSequencer.Region.NTSC,
                SmpsDriver.ReadMode.HYBRID,
                0,
                false,
                0,
                entries,
                fmLocks,
                psgLocks,
                fixture.driver.captureSynthSnapshot());
    }

    private static Fixture fixture() {
        SmpsDriver driver = new SmpsDriver();
        List<SmpsSequencer> sequencers = new ArrayList<>(SFX_COUNT + 1);
        SmpsSequencer music = sequencer(new CountingSmpsData(new byte[0], 0x81), driver);
        driver.addSequencer(music, false);
        sequencers.add(music);
        CountingSmpsData fallback = new CountingSmpsData(new byte[256 * 1024], 0x90);
        for (int index = 0; index < SFX_COUNT; index++) {
            SmpsSequencer sfx = sequencer(
                    new CountingSmpsData(new byte[0], 0xB0 + index), driver);
            sfx.setFallbackVoiceData(fallback);
            driver.addSequencer(sfx, true);
            sequencers.add(sfx);
        }
        fallback.resetDataReads();
        return new Fixture(driver, List.copyOf(sequencers), fallback);
    }

    private static SmpsSequencer sequencer(AbstractSmpsData data, SmpsDriver driver) {
        return new SmpsSequencer(
                data,
                AudioTestFixtures.EMPTY_DAC,
                driver,
                AudioManager.getInstance(),
                new SmpsSequencerConfig.Builder().build());
    }

    private static long median(long[] values) {
        long[] sorted = values.clone();
        Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }

    private static ThreadMXBean allocationBeanOrSkip() {
        java.lang.management.ThreadMXBean raw = ManagementFactory.getThreadMXBean();
        Assumptions.assumeTrue(raw instanceof ThreadMXBean,
                "ThreadMXBean allocation accounting unavailable");
        ThreadMXBean bean = (ThreadMXBean) raw;
        Assumptions.assumeTrue(bean.isThreadAllocatedMemorySupported(),
                "thread allocation accounting unsupported");
        if (!bean.isThreadAllocatedMemoryEnabled()) {
            bean.setThreadAllocatedMemoryEnabled(true);
        }
        Assumptions.assumeTrue(bean.getThreadAllocatedBytes(Thread.currentThread().threadId()) >= 0,
                "current-thread allocation reads unavailable");
        return bean;
    }

    private static final class CountingSmpsData extends AbstractSmpsData {
        private int dataReads;

        private CountingSmpsData(byte[] data, int id) {
            super(data, 0);
            setId(id);
        }

        @Override public byte[] getData() { dataReads++; return super.getData(); }
        private int dataReads() { return dataReads; }
        private void resetDataReads() { dataReads = 0; }
        @Override protected void parseHeader() { }
        @Override public byte[] getVoice(int voiceId) { return new byte[0]; }
        @Override public byte[] getPsgEnvelope(int id) { return new byte[0]; }
        @Override public int read16(int offset) { return 0; }
        @Override public int getBaseNoteOffset() { return 0; }
    }

    private record Fixture(
            SmpsDriver driver,
            List<SmpsSequencer> sequencers,
            CountingSmpsData fallback) {
    }

    private record CaptureRun(
            long bytesPerCapture,
            long nanosPerCapture,
            long hashReads,
            long workCount,
            SmpsDriverSnapshot snapshot) {
    }
}
