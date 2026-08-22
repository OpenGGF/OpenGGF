package com.openggf.audio.synth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openggf.audio.rewind.SmpsSourceDescriptor;
import com.openggf.audio.smps.DacData;
import com.openggf.audio.smps.YmServiceTimingProfile;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestVirtualSynthesizerSnapshot {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final SmpsSourceDescriptor SOURCE =
            new SmpsSourceDescriptor(
                    SmpsSourceDescriptor.Kind.BASE_SFX_ID,
                    0x5A, null, null, 0x1234, 16, 0x5678,
                    false, 0);

    @Test
    void restoreSnapshotProducesBitExactMixedFutureFrames() {
        VirtualSynthesizer uninterrupted = configuredSynth();
        VirtualSynthesizer restored = configuredSynth();

        prime(uninterrupted);
        prime(restored);

        uninterrupted.renderFrames(new short[82], 0, 41);
        restored.renderFrames(new short[82], 0, 41);

        VirtualSynthesizer.Snapshot snapshot = uninterrupted.captureSynthSnapshot();
        perturb(uninterrupted);
        short[] expected = new short[256];
        uninterrupted.renderFrames(expected, 0, expected.length / 2);

        perturb(restored);
        restored.restoreSynthSnapshot(snapshot);
        assertEquals(snapshot, restored.captureSynthSnapshot(),
                "chip snapshots compare their state arrays by value");
        assertEquals(snapshot.hashCode(),
                restored.captureSynthSnapshot().hashCode());
        perturb(restored);
        short[] actual = new short[256];
        restored.renderFrames(actual, 0, actual.length / 2);

        assertArrayEquals(expected, actual);
    }

    @Test
    void pendingTimelineSnapshotRestoresPcmCallbacksFrontierAndOrdinalsTwice() {
        VirtualSynthesizer uninterrupted = configuredSynth();
        prime(uninterrupted);
        uninterrupted.renderFrames(new short[82], 0, 41);

        VirtualSynthesizer.Snapshot beforeWrites =
                uninterrupted.captureSynthSnapshot();
        long frontier = beforeWrites.renderedYmMasterCycle();
        long generation = beforeWrites.ymTimelineGeneration();
        uninterrupted.ymWriteTimelineForTesting().commit(List.of(
                entry(frontier, 0, 0x28, 0x00, generation),
                entry(frontier + 3_150, 1, 0xA0, 0x40, generation),
                entry(frontier + 6_300, 2, 0x28, 0xF0, generation)));

        VirtualSynthesizer.Snapshot snapshot =
                uninterrupted.captureSynthSnapshot();
        assertEquals(3, snapshot.ymWriteTimeline().pending().size());
        assertEquals(frontier, snapshot.renderedYmMasterCycle());
        assertEquals(generation, snapshot.ymTimelineGeneration());

        RecordingObserver expectedObserver = new RecordingObserver();
        uninterrupted.setChipWriteObserver(expectedObserver);
        short[] expectedPcm = renderPendingWrites(uninterrupted);
        VirtualSynthesizer.Snapshot expectedFinal =
                uninterrupted.captureSynthSnapshot();

        RestoredDrain first = restoreAndDrain(snapshot);
        RestoredDrain second = restoreAndDrain(snapshot);

        assertArrayEquals(expectedPcm, first.pcm());
        assertArrayEquals(expectedPcm, second.pcm());
        assertEquals(expectedObserver.events, first.callbacks());
        assertEquals(expectedObserver.events, second.callbacks());
        assertEquals(List.of(
                "YM:0:28:00", "YM:0:A0:40", "YM:0:28:F0"),
                first.callbacks());
        assertEquals(expectedFinal, first.finalSnapshot());
        assertEquals(expectedFinal, second.finalSnapshot());
        assertEquals(List.of(), first.finalSnapshot()
                .ymWriteTimeline().pending());
        assertEquals(3, first.finalSnapshot()
                .ymWriteTimeline().nextOrdinal());
    }

    @Test
    void synthSnapshotDefensivelyCopiesPendingEntriesAndChipArrays() {
        VirtualSynthesizer synth = configuredSynth();
        long generation = synth.captureSynthSnapshot()
                .ymTimelineGeneration();
        synth.ymWriteTimelineForTesting().commit(List.of(
                entry(3_150, 0, 0x40, 0x21, generation)));
        VirtualSynthesizer.Snapshot expected = synth.captureSynthSnapshot();

        assertThrows(UnsupportedOperationException.class,
                () -> expected.ymWriteTimeline().pending().clear());
        ArrayList<YmWriteTimeline.Entry> callerCopy = new ArrayList<>(
                expected.ymWriteTimeline().pending());
        callerCopy.clear();
        Ym2612Chip.ChannelSnapshot[] channels = expected.ym().channels();
        channels[0] = null;
        boolean[] ymMutes = expected.ym().mutes();
        ymMutes[0] = !ymMutes[0];
        int[] psgRegisters = expected.psg().regs();
        psgRegisters[0] ^= 0x0F;

        assertEquals(expected, synth.captureSynthSnapshot());
        assertEquals(1, synth.captureSynthSnapshot()
                .ymWriteTimeline().pending().size());
    }

    @Test
    void hardResetBarrierDiscardsPendingWritesWithoutAChipCallback() {
        TestableSynth synth = synthWithOnePendingWrite();
        RecordingObserver observer = observe(synth);
        long oldGeneration = synth.currentGeneration();

        synth.crossBarrier(
                VirtualSynthesizer.YmTimelineGenerationBarrier.HARD_RESET);
        renderPendingWrites(synth);

        assertEquals(oldGeneration + 1, synth.currentGeneration());
        assertEquals(List.of(), observer.events);
        assertEquals(List.of(), synth.captureSynthSnapshot()
                .ymWriteTimeline().pending());
        assertEquals(2, synth.captureSynthSnapshot()
                .ymWriteTimeline().nextOrdinal());
    }

    @Test
    void synthReplacementBarrierDiscardsPendingWritesWithoutAChipCallback() {
        TestableSynth synth = synthWithOnePendingWrite();
        RecordingObserver observer = observe(synth);
        long oldGeneration = synth.currentGeneration();

        synth.crossBarrier(
                VirtualSynthesizer.YmTimelineGenerationBarrier.SYNTH_REPLACEMENT);
        renderPendingWrites(synth);

        assertEquals(oldGeneration + 1, synth.currentGeneration());
        assertEquals(List.of(), observer.events);
        assertEquals(List.of(), synth.captureSynthSnapshot()
                .ymWriteTimeline().pending());
    }

    @Test
    void fullSilenceBarrierDiscardsPendingWritesBeforeItsImmediateWrites() {
        TestableSynth synth = synthWithOnePendingWrite();
        long oldGeneration = synth.currentGeneration();
        GenerationObserver observer = new GenerationObserver(synth);
        synth.setChipWriteObserver(observer);

        synth.silenceAll();
        assertEquals(oldGeneration + 1, synth.currentGeneration());
        assertEquals(202, observer.generations.size());
        assertTrue(observer.generations.stream().allMatch(
                generation -> generation == oldGeneration + 1));
        observer.generations.clear();
        renderPendingWrites(synth);

        assertEquals(List.of(), observer.generations);
        assertEquals(List.of(), synth.captureSynthSnapshot()
                .ymWriteTimeline().pending());
    }

    @Test
    void publicationRejectsStaleFutureAndMixedGenerationsAtomically() {
        TestableSynth stale = configuredTestableSynth();
        long staleGeneration = stale.currentGeneration();
        stale.crossBarrier(
                VirtualSynthesizer.YmTimelineGenerationBarrier.HARD_RESET);
        assertJournalRejectedWithoutMutation(stale, List.of(
                entry(3_150, 0, 0x40, 0x11, staleGeneration)));

        TestableSynth future = configuredTestableSynth();
        long currentGeneration = future.currentGeneration();
        assertJournalRejectedWithoutMutation(future, List.of(
                entry(3_150, 0, 0x40, 0x11,
                        currentGeneration + 1)));

        TestableSynth mixed = configuredTestableSynth();
        currentGeneration = mixed.currentGeneration();
        assertJournalRejectedWithoutMutation(mixed, List.of(
                entry(3_150, 0, 0x40, 0x11, currentGeneration),
                entry(6_300, 1, 0x41, 0x22,
                        currentGeneration + 1)));
    }

    @Test
    void restoreRejectsMismatchedPendingGenerationWithoutMutation() {
        TestableSynth target = poisonRestoreTarget();
        VirtualSynthesizer.Snapshot valid = target.captureSynthSnapshot();
        YmWriteTimeline.Entry pending = valid.ymWriteTimeline()
                .pending().getFirst();
        YmWriteTimeline.Snapshot poisonedTimeline =
                new YmWriteTimeline.Snapshot(
                        valid.ymWriteTimeline().capacity(),
                        valid.ymWriteTimeline().nextOrdinal(),
                        List.of(entry(
                                pending.dueMasterCycle(),
                                pending.sourceOrdinal(),
                                pending.register(),
                                pending.value(),
                                valid.ymTimelineGeneration() + 1)));
        VirtualSynthesizer.Snapshot poisoned = new VirtualSynthesizer.Snapshot(
                valid.outputSampleRate(), valid.ym(), valid.psg(),
                poisonedTimeline, valid.renderedYmMasterCycle(),
                valid.ymTimelineGeneration());

        assertRestoreRejectedWithoutMutation(target, poisoned);
    }

    @Test
    void restorePrevalidatesYmArraysBeforeMutatingLiveSynth() {
        TestableSynth target = poisonRestoreTarget();
        ObjectNode json = JSON.valueToTree(target.captureSynthSnapshot());
        ArrayNode channels = (ArrayNode) json.path("ym").path("channels");
        channels.remove(channels.size() - 1);

        assertRestoreRejectedWithoutMutation(
                target, snapshotFromJson(json));
    }

    @Test
    void restorePrevalidatesPsgArraysBeforeMutatingLiveSynth() {
        TestableSynth target = poisonRestoreTarget();
        ObjectNode json = JSON.valueToTree(target.captureSynthSnapshot());
        ArrayNode registers = (ArrayNode) json.path("psg").path("regs");
        registers.remove(registers.size() - 1);

        assertRestoreRejectedWithoutMutation(
                target, snapshotFromJson(json));
    }

    @Test
    void restorePrevalidatesNestedResamplerBeforeMutatingLiveSynth() {
        TestableSynth target = poisonRestoreTarget();
        ObjectNode json = JSON.valueToTree(target.captureSynthSnapshot());
        ObjectNode resampler = (ObjectNode) json.path("ym")
                .path("blipResampler");
        resampler.put("head", -1);

        assertRestoreRejectedWithoutMutation(
                target, snapshotFromJson(json));
    }

    @Test
    void outerSnapshotRejectsNonFiniteOutputRates() {
        VirtualSynthesizer.Snapshot valid = configuredSynth()
                .captureSynthSnapshot();

        assertThrows(IllegalArgumentException.class,
                () -> copyWithOutputRate(valid, Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> copyWithOutputRate(
                        valid, Double.POSITIVE_INFINITY));
    }

    @Test
    void restoreAndDrainEmitsOnlyTheRetainedGenerationExactlyOnce() {
        TestableSynth synth = configuredTestableSynth();
        VirtualSynthesizer.Snapshot initial = synth.captureSynthSnapshot();
        long frontier = initial.renderedYmMasterCycle();
        long generation = initial.ymTimelineGeneration();
        synth.ymWriteTimelineForTesting().commit(List.of(
                entry(frontier + 3_150, 0, 0x40, 0x11, generation),
                entry(frontier + 6_300, 1, 0x41, 0x22,
                        generation + 1)));
        synth.crossBarrier(
                VirtualSynthesizer.YmTimelineGenerationBarrier.SYNTH_REPLACEMENT);
        VirtualSynthesizer.Snapshot retained = synth.captureSynthSnapshot();

        RestoredDrain first = restoreAndDrain(retained);
        RestoredDrain second = restoreAndDrain(retained);

        assertEquals(List.of("YM:0:41:22"), first.callbacks());
        assertEquals(first.callbacks(), second.callbacks());
        assertArrayEquals(first.pcm(), second.pcm());
        assertEquals(first.finalSnapshot(), second.finalSnapshot());
        assertEquals(2, first.finalSnapshot()
                .ymWriteTimeline().nextOrdinal());
    }

    private static VirtualSynthesizer configuredSynth() {
        VirtualSynthesizer synth = new VirtualSynthesizer(44100.0);
        synth.setDacData(dacData());
        synth.setDacInterpolate(true);
        synth.setPsgNoiseShiftOnEveryToggle(false);
        return synth;
    }

    private static TestableSynth configuredTestableSynth() {
        TestableSynth synth = new TestableSynth(44100.0);
        synth.setDacData(dacData());
        synth.setDacInterpolate(true);
        synth.setPsgNoiseShiftOnEveryToggle(false);
        return synth;
    }

    private static TestableSynth synthWithOnePendingWrite() {
        TestableSynth synth = configuredTestableSynth();
        VirtualSynthesizer.Snapshot snapshot = synth.captureSynthSnapshot();
        synth.ymWriteTimelineForTesting().commit(List.of(
                entry(snapshot.renderedYmMasterCycle() + 3_150,
                        0, 0x2B, 0x80,
                        snapshot.ymTimelineGeneration())));
        synth.ymWriteTimelineForTesting().commit(List.of(
                entry(snapshot.renderedYmMasterCycle() + 6_300,
                        1, 0x2A, 0x5A,
                        snapshot.ymTimelineGeneration())));
        return synth;
    }

    private static TestableSynth poisonRestoreTarget() {
        TestableSynth synth = configuredTestableSynth();
        prime(synth);
        synth.renderFrames(new short[20], 0, 10);
        VirtualSynthesizer.Snapshot snapshot = synth.captureSynthSnapshot();
        synth.publish(List.of(entry(
                snapshot.renderedYmMasterCycle() + 100_800,
                0, 0x40, 0x21, snapshot.ymTimelineGeneration())));
        return synth;
    }

    private static void assertJournalRejectedWithoutMutation(
            TestableSynth synth, List<YmWriteTimeline.Entry> journal) {
        VirtualSynthesizer.Snapshot before = synth.captureSynthSnapshot();

        assertThrows(IllegalArgumentException.class,
                () -> synth.publish(journal));
        assertEquals(before, synth.captureSynthSnapshot());
    }

    private static void assertRestoreRejectedWithoutMutation(
            VirtualSynthesizer synth,
            VirtualSynthesizer.Snapshot poisoned) {
        VirtualSynthesizer.Snapshot before = synth.captureSynthSnapshot();

        assertThrows(IllegalArgumentException.class,
                () -> synth.restoreSynthSnapshot(poisoned));
        assertEquals(before, synth.captureSynthSnapshot());
    }

    private static VirtualSynthesizer.Snapshot snapshotFromJson(
            ObjectNode json) {
        return JSON.convertValue(json, VirtualSynthesizer.Snapshot.class);
    }

    private static VirtualSynthesizer.Snapshot copyWithOutputRate(
            VirtualSynthesizer.Snapshot snapshot, double outputSampleRate) {
        return new VirtualSynthesizer.Snapshot(
                outputSampleRate, snapshot.ym(), snapshot.psg(),
                snapshot.ymWriteTimeline(),
                snapshot.renderedYmMasterCycle(),
                snapshot.ymTimelineGeneration());
    }

    private static RecordingObserver observe(VirtualSynthesizer synth) {
        RecordingObserver observer = new RecordingObserver();
        synth.setChipWriteObserver(observer);
        return observer;
    }

    private static short[] renderPendingWrites(VirtualSynthesizer synth) {
        short[] pcm = new short[256];
        synth.renderFrames(pcm, 0, pcm.length / 2);
        return pcm;
    }

    private static RestoredDrain restoreAndDrain(
            VirtualSynthesizer.Snapshot snapshot) {
        VirtualSynthesizer restored = configuredSynth();
        restored.restoreSynthSnapshot(snapshot);
        assertEquals(snapshot, restored.captureSynthSnapshot());
        RecordingObserver observer = observe(restored);
        short[] pcm = renderPendingWrites(restored);
        return new RestoredDrain(pcm, List.copyOf(observer.events),
                restored.captureSynthSnapshot());
    }

    private static YmWriteTimeline.Entry entry(
            long dueMasterCycle, long sourceOrdinal, int register,
            int value, long generation) {
        return new YmWriteTimeline.Entry(
                dueMasterCycle, sourceOrdinal, 0, register, value,
                generation, 4, SOURCE,
                YmServiceTimingProfile.SegmentKind.FM_VOICE_UPLOAD);
    }

    private static DacData dacData() {
        return new DacData(
                Map.of(1, new byte[] { 0, 24, 64, 127, (byte) 255, (byte) 196, 96, 32, 8, 0 }),
                Map.of(0x81, new DacData.DacEntry(1, 4)),
                295);
    }

    private static void prime(VirtualSynthesizer synth) {
        synth.writeFm(synth, 0, 0x22, 0x0B);
        synth.writeFm(synth, 0, 0x2B, 0x80);
        synth.setInstrument(synth, 0, new byte[] {
                0x32,
                0x71, 0x0D, 0x33, 0x01,
                0x5F, 0x5F, 0x5F, 0x5F,
                0x14, 0x0E, 0x0E, 0x0E,
                0x08, 0x08, 0x08, 0x08,
                0x0F, 0x0F, 0x0F, 0x0F,
                0x1B, 0x16, 0x1F, 0x00
        });
        synth.writeFm(synth, 0, 0xA4, 0x22);
        synth.writeFm(synth, 0, 0xA0, 0x69);
        synth.writeFm(synth, 0, 0xB4, 0xC7);
        synth.writeFm(synth, 0, 0x28, 0xF0);
        synth.playDac(synth, 0x81);
        synth.writePsg(synth, 0x80 | 0x04);
        synth.writePsg(synth, 0x12);
        synth.writePsg(synth, 0x90 | 0x02);
        synth.writePsg(synth, 0xE4);
        synth.writePsg(synth, 0xF0 | 0x04);
    }

    private static void perturb(VirtualSynthesizer synth) {
        synth.writeFm(synth, 0, 0x2A, 0x5A);
        synth.writeFm(synth, 0, 0x40, 0x23);
        synth.writePsg(synth, 0xE7);
        synth.writePsg(synth, 0xF2);
        synth.playDac(synth, 0x81);
    }

    private record RestoredDrain(
            short[] pcm,
            List<String> callbacks,
            VirtualSynthesizer.Snapshot finalSnapshot) {
    }

    private static final class RecordingObserver implements ChipWriteObserver {
        private final List<String> events = new ArrayList<>();

        @Override
        public void onYm2612Write(int port, int register, int value) {
            events.add("YM:%d:%02X:%02X".formatted(
                    port, register, value));
        }

        @Override
        public void onPsgWrite(int value) {
            events.add("PSG:%02X".formatted(value));
        }
    }

    private static final class GenerationObserver implements ChipWriteObserver {
        private final TestableSynth synth;
        private final List<Long> generations = new ArrayList<>();

        private GenerationObserver(TestableSynth synth) {
            this.synth = synth;
        }

        @Override
        public void onYm2612Write(int port, int register, int value) {
            generations.add(synth.currentGeneration());
        }

        @Override
        public void onPsgWrite(int value) {
            generations.add(synth.currentGeneration());
        }
    }

    private static final class TestableSynth extends VirtualSynthesizer {
        private TestableSynth(double outputSampleRate) {
            super(outputSampleRate);
        }

        private void crossBarrier(YmTimelineGenerationBarrier barrier) {
            crossYmTimelineGenerationBarrier(barrier);
        }

        private long currentGeneration() {
            return ymTimelineGeneration();
        }

        private void publish(List<YmWriteTimeline.Entry> journal) {
            commitYmWriteJournal(journal);
        }
    }
}
