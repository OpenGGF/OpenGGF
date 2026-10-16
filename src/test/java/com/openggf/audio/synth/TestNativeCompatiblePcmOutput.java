package com.openggf.audio.synth;

import com.openggf.audio.session.LegacyCompatibilitySmpsPhysicalPolicy;
import com.openggf.audio.session.OwnedSmpsAudioStream;
import com.openggf.audio.session.SmpsPhysicalDevice;
import com.openggf.audio.synth.NativeCompatiblePcmOutput.Config;
import com.openggf.audio.synth.NativeCompatiblePcmOutput.Filter;
import com.openggf.audio.synth.NativeCompatiblePcmOutput.Snapshot;
import com.openggf.audio.synth.blip.BlipBuf;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import static com.openggf.audio.synth.NativeCompatiblePcmVectors.CHIP_FRAMES;
import static com.openggf.audio.synth.NativeCompatiblePcmVectors.NTSC_FRAMES;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Component tier of the opt-in native-compatible PCM output stage: the
 * production {@link Ym2612Chip}'s own pin sums through the blip_buf port, the
 * Nuked x11 scale and the movie filters, compared byte-for-byte with
 * native-source vectors from Genesis Plus GX's own {@code ym3438.c},
 * {@code blip_buf.c} and {@code eq.c} (generator and hashes in
 * {@code src/test/resources/audio/native-compatible-pcm/v1/README.md}).
 * The PSG half is compared with GPGX's own {@code psg.c} on the same
 * stream's PSG bytes. Every equality has a perturbation that must break it.
 * Passing proves the output stage on an identical write schedule only: not
 * the MAME core, not production write scheduling, and not any game window.
 */
class TestNativeCompatiblePcmOutput {

    private static final Config NONE = Config.withFilter(Filter.NONE);
    private static final Config THREE_BAND_UNITY = Config.withFilter(Filter.THREE_BAND);
    private static final Config LOW_PASS = Config.withFilter(Filter.LOW_PASS);
    private static final Config THREE_BAND_GAINS = new Config(Filter.THREE_BAND, 0x6666, 880, 5000, 150, 50, 200);

    static Stream<Arguments> vectors() {
        return Stream.of(
                Arguments.of("native-none.s16", NONE),
                Arguments.of("native-threeband-unity.s16", THREE_BAND_UNITY),
                Arguments.of("native-lowpass-6666.s16", LOW_PASS),
                Arguments.of("native-threeband-150-50-200.s16", THREE_BAND_GAINS));
    }

    @Test
    void productionChipStrobeScheduleIsTheVectorInput() {
        NativeCompatiblePcmVectors.StrobeRecorder recorder = new NativeCompatiblePcmVectors.StrobeRecorder();
        Ym2612Chip chip = NativeCompatiblePcmVectors.newChip(NONE, recorder);
        List<NativeCompatiblePcmVectors.PsgWrite> psgWrites = new ArrayList<>();
        NativeCompatiblePcmVectors.psgRecorder = psgWrites;
        try {
            NativeCompatiblePcmVectors.drive(chip, 0, CHIP_FRAMES, null, null);
        } finally {
            NativeCompatiblePcmVectors.psgRecorder = null;
        }
        assertEquals(NativeCompatiblePcmVectors.resourceText("strobes.csv"),
                NativeCompatiblePcmVectors.strobesCsv(recorder.strobes),
                "the vectors were generated from this exact production strobe schedule");
        assertEquals(NativeCompatiblePcmVectors.resourceText("psg_writes.csv"),
                NativeCompatiblePcmVectors.psgWritesCsv(psgWrites),
                "and from these PSG bytes at these master clocks");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("vectors")
    void stageOutputEqualsNativeSourceVector(String vector, Config config) {
        short[] expected = NativeCompatiblePcmVectors.s16(NativeCompatiblePcmVectors.resource(vector));
        Ym2612Chip chip = NativeCompatiblePcmVectors.newChip(config, null);
        NativeCompatiblePcmVectors.drive(chip, 0, CHIP_FRAMES, null, null);
        assertEquals(NTSC_FRAMES, chip.nativeCompatibleOutput().framesEnded());
        short[] actual = drainAll(chip.nativeCompatibleOutput());
        assertEquals(expected.length, actual.length, "sample count of " + NTSC_FRAMES + " NTSC frames");
        assertArrayEquals(expected, actual, vector);
    }

    @Test
    void perturbedPsgByteBreaksTheNoneVector() {
        short[] expected = NativeCompatiblePcmVectors.s16(NativeCompatiblePcmVectors.resource("native-none.s16"));
        int[][] sums = productionPinSums();
        List<long[]> psg = psgWrites();
        List<long[]> changed = new ArrayList<>(psg);
        long[] tone = changed.get(5);
        changed.set(5, new long[] {tone[0], tone[1] ^ 0x01});
        assertFalse(Arrays.equals(expected, feedStandalone(NONE, sums, changed, 0)), "one PSG data bit must be visible");
        // Write 6 is tone 0's first attenuation (0x92). Small moves can be invisible: GPGX applies PSG effects on
        // 240-master-clock steps and a level change on a low channel waits for the next flip. A quarter-frame
        // move spans several flips of the new tone and must change the output.
        List<long[]> late = new ArrayList<>(psg);
        long[] write = late.get(6);
        assertEquals(0x92, write[1]);
        late.set(6, new long[] {write[0] + NativeCompatiblePcmOutput.FRAME_MCYCLES / 4, write[1]});
        assertFalse(Arrays.equals(expected, feedStandalone(NONE, sums, late, 0)), "a PSG level write a quarter frame later");
    }

    @Test
    void perturbedPinSumOrClockOriginBreaksTheNoneVector() {
        short[] expected = NativeCompatiblePcmVectors.s16(NativeCompatiblePcmVectors.resource("native-none.s16"));
        int[][] sums = productionPinSums();
        List<long[]> psg = psgWrites();
        assertArrayEquals(expected, feedStandalone(NONE, sums, psg, 0), "standalone feed reproduces the chip tap");

        int[][] perturbed = {sums[0].clone(), sums[1].clone()};
        perturbed[0][2000] += 1;
        assertFalse(Arrays.equals(expected, feedStandalone(NONE, perturbed, psg, 0)), "one pin-sum unit must be visible");
        assertFalse(Arrays.equals(expected, feedStandalone(NONE, sums, psg, 1)), "one YM clock (42 M-cycles) must be visible");
    }

    @Test
    void perturbedFilterSettingsBreakTheirVectors() {
        short[] threeBand = NativeCompatiblePcmVectors.s16(
                NativeCompatiblePcmVectors.resource("native-threeband-unity.s16"));
        short[] lowPass = NativeCompatiblePcmVectors.s16(NativeCompatiblePcmVectors.resource("native-lowpass-6666.s16"));
        assertFalse(Arrays.equals(threeBand, NativeCompatiblePcmVectors.renderStage(NONE)), "the EQ is not identity");
        assertFalse(Arrays.equals(threeBand, NativeCompatiblePcmVectors.renderStage(
                new Config(Filter.THREE_BAND, 0x6666, 880, 5000, 100, 101, 100))), "mid gain 1.01");
        assertFalse(Arrays.equals(threeBand, NativeCompatiblePcmVectors.renderStage(
                new Config(Filter.THREE_BAND, 0x6666, 881, 5000, 100, 100, 100))), "low cutoff 881 Hz");
        assertFalse(Arrays.equals(lowPass, NativeCompatiblePcmVectors.renderStage(
                new Config(Filter.LOW_PASS, 0x6667, 880, 5000, 100, 100, 100))), "lp_range + 1");
    }

    /**
     * SHA-256 of little-endian {@code (x, sin(x))} double pairs for every 16-bit
     * cutoff {@code f} at 44.1 kHz, {@code x = (f / 44100.0) * pi}, produced by
     * executing the stock BizHawk 2.11 core's own {@code sin} machine code
     * (README: {@code stock_core_sin}).
     */
    private static final String STOCK_CORE_SIN_ALL_SHORTS_SHA256 =
            "c2cc584475fb26fc9945ea45b116ff992fa98ce43c8608daaf5e766e38cceeb8";

    @Test
    void equaliserCutoffSinEqualsTheStockCoreForEverySixteenBitFrequency() throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        java.nio.ByteBuffer pair = java.nio.ByteBuffer.allocate(16).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        for (int f = Short.MIN_VALUE; f <= Short.MAX_VALUE; f++) {
            double x = ((double) f / (double) 44100) * Math.PI;
            pair.clear();
            pair.putDouble(x).putDouble(MuslSin.sin(x));
            digest.update(pair.array());
        }
        assertEquals(STOCK_CORE_SIN_ALL_SHORTS_SHA256, java.util.HexFormat.of().formatHex(digest.digest()));
        // The 880 Hz cutoff the movie uses; glibc's sin gives ...3581 here.
        assertEquals(0x3fc009b8777f3582L, Double.doubleToRawLongBits(
                2 * NativeCompatiblePcmOutput.ThreeBandEq.nativeSin(Math.PI * (880.0 / 44100.0))));
    }

    @Test
    void enablingTheStageLeavesOrdinaryChipOutputUnchanged() {
        int[] plainLeft = new int[CHIP_FRAMES];
        int[] plainRight = new int[CHIP_FRAMES];
        NativeCompatiblePcmVectors.drive(NativeCompatiblePcmVectors.newChip(null, null), 0, CHIP_FRAMES,
                plainLeft, plainRight);
        int[] tappedLeft = new int[CHIP_FRAMES];
        int[] tappedRight = new int[CHIP_FRAMES];
        NativeCompatiblePcmVectors.drive(NativeCompatiblePcmVectors.newChip(THREE_BAND_UNITY, null), 0,
                CHIP_FRAMES, tappedLeft, tappedRight);
        assertArrayEquals(plainLeft, tappedLeft);
        assertArrayEquals(plainRight, tappedRight);
    }

    // ------------------------------------------------------------------ rewind

    /** Capture point: inside the DAC passage, mid slot sweep, with queued samples and live buffers. */
    private static final int CAPTURE_FRAME = 3707;

    private static Ym2612Chip chipAtCapturePoint(Config config) {
        Ym2612Chip chip = NativeCompatiblePcmVectors.newChip(config, null);
        NativeCompatiblePcmVectors.drive(chip, 0, CAPTURE_FRAME, null, null);
        chip.write(0, 0xB4, 0xC0); // queued write; the status read drains it with bus pacing, ending mid-sweep
        chip.readStatus();
        return chip;
    }

    /** Remaining stream, then every queued sample and the final stage state. */
    private record Replay(short[] pcm, long framesEnded) {
    }

    private static Replay replay(Ym2612Chip chip) {
        NativeCompatiblePcmVectors.drive(chip, CAPTURE_FRAME, CHIP_FRAMES, null, null);
        NativeCompatiblePcmOutput stage = chip.nativeCompatibleOutput();
        return new Replay(drainAll(stage), stage.framesEnded());
    }

    static Stream<Config> rewindConfigs() {
        return Stream.of(THREE_BAND_UNITY, LOW_PASS);
    }

    @ParameterizedTest
    @MethodSource("rewindConfigs")
    void snapshotRestoreReplaysByteIdenticallyAndEveryStageFieldMatters(Config config) {
        Ym2612Chip uninterrupted = chipAtCapturePoint(config);
        Ym2612Chip.Snapshot snapshot = uninterrupted.captureSnapshot();
        Snapshot stageAtCapture = snapshot.nativeOutput();
        assertTrue(stageAtCapture.pending().length > 0, "capture with queued samples");
        Replay expected = replay(uninterrupted);

        Ym2612Chip restored = NativeCompatiblePcmVectors.newChip(config, null);
        Snapshot fresh = restored.nativeCompatibleOutput().captureSnapshot();
        restored.restoreSnapshot(snapshot);
        Replay actual = replay(restored);
        assertArrayEquals(expected.pcm(), actual.pcm());
        assertEquals(expected.framesEnded(), actual.framesEnded());

        // Negative: restoring everything except one field (left at the restoring chip's value) must fail.
        for (StageField field : fieldsFor(config)) {
            Ym2612Chip omitted = NativeCompatiblePcmVectors.newChip(config, null);
            omitted.restoreSnapshot(snapshot);
            omitted.nativeCompatibleOutput().restoreSnapshot(field.mix(stageAtCapture, fresh));
            Replay broken = replay(omitted);
            assertFalse(Arrays.equals(expected.pcm(), broken.pcm())
                            && expected.framesEnded() == broken.framesEnded(),
                    "omitting " + field.name() + " from the snapshot must change the replay");
        }
    }

    @ParameterizedTest
    @MethodSource("rewindConfigs")
    void mutationRollbackRestoresIdenticalSubsequentOutputAndEveryStageFieldMatters(Config config) {
        Replay expected = replay(chipAtCapturePoint(config));

        Ym2612Chip rolled = chipAtCapturePoint(config);
        FmChip.MutationBackup backup = rolled.createMutationBackup();
        rolled.captureMutation(backup);
        NativeCompatiblePcmVectors.drive(rolled, CAPTURE_FRAME, CAPTURE_FRAME + 1200, null, null);
        rolled.write(0, 0x28, 0x00);
        rolled.readStatus();
        Snapshot mutated = rolled.nativeCompatibleOutput().captureSnapshot();
        rolled.restoreMutation(backup);
        Snapshot rolledBack = rolled.nativeCompatibleOutput().captureSnapshot();
        assertEquals(chipAtCapturePoint(config).nativeCompatibleOutput().captureSnapshot(), rolledBack);
        assertNotEquals(mutated, rolledBack);
        Replay actual = replay(rolled);
        assertArrayEquals(expected.pcm(), actual.pcm());
        assertEquals(expected.framesEnded(), actual.framesEnded());

        // Negative: a rollback that left one field at its mutated value must fail.
        for (StageField field : fieldsFor(config)) {
            Ym2612Chip partial = chipAtCapturePoint(config);
            FmChip.MutationBackup partialBackup = partial.createMutationBackup();
            partial.captureMutation(partialBackup);
            NativeCompatiblePcmVectors.drive(partial, CAPTURE_FRAME, CAPTURE_FRAME + 1200, null, null);
            partial.write(0, 0x28, 0x00);
            partial.readStatus();
            partial.restoreMutation(partialBackup);
            partial.nativeCompatibleOutput().restoreSnapshot(field.mix(rolledBack, mutated));
            Replay broken = replay(partial);
            assertFalse(Arrays.equals(expected.pcm(), broken.pcm())
                            && expected.framesEnded() == broken.framesEnded(),
                    "leaving " + field.name() + " out of the rollback must change the replay");
        }
    }

    @Test
    void stateWithoutTheStageCannotRestoreIntoAChipWithIt() {
        Ym2612Chip withStage = NativeCompatiblePcmVectors.newChip(NONE, null);
        Ym2612Chip without = NativeCompatiblePcmVectors.newChip(null, null);
        assertThrows(IllegalArgumentException.class, () -> withStage.restoreSnapshot(without.captureSnapshot()));
        assertThrows(IllegalArgumentException.class, () -> without.restoreSnapshot(withStage.captureSnapshot()));
        Ym2612Chip otherFilter = NativeCompatiblePcmVectors.newChip(LOW_PASS, null);
        assertThrows(IllegalArgumentException.class, () -> otherFilter.restoreSnapshot(withStage.captureSnapshot()));
    }

    /** One stage field, mixed from {@code donor} into {@code base}. */
    private record StageField(String name, BiFunction<Snapshot, Snapshot, Snapshot> mixer) {
        Snapshot mix(Snapshot base, Snapshot donor) {
            Snapshot mixed = mixer.apply(base, donor);
            assertNotEquals(base, mixed, name + " differs between the two states");
            return mixed;
        }
    }

    private static List<StageField> fieldsFor(Config config) {
        List<StageField> fields = new ArrayList<>(List.of(
                blipField("blip offset", (b, d) -> new BlipBuf.State(b.size(), b.factor(), d.offset(),
                        b.integratorLeft(), b.integratorRight(), b.left(), b.right())),
                blipField("blip integrator left", (b, d) -> new BlipBuf.State(b.size(), b.factor(), b.offset(),
                        d.integratorLeft(), b.integratorRight(), b.left(), b.right())),
                blipField("blip integrator right", (b, d) -> new BlipBuf.State(b.size(), b.factor(), b.offset(),
                        b.integratorLeft(), d.integratorRight(), b.left(), b.right())),
                blipField("blip buffer left", (b, d) -> new BlipBuf.State(b.size(), b.factor(), b.offset(),
                        b.integratorLeft(), b.integratorRight(), d.left(), b.right())),
                blipField("blip buffer right", (b, d) -> new BlipBuf.State(b.size(), b.factor(), b.offset(),
                        b.integratorLeft(), b.integratorRight(), b.left(), d.right())),
                new StageField("fm master-clock position", (b, d) -> copy(b, d.fmTime(), b.heldLeft(),
                        b.heldRight(), b.lastLeft(), b.lastRight(), b.framesEnded(), b.lowPassLeft(),
                        b.lowPassRight(), b.eqLeft(), b.eqRight(), b.pending())),
                new StageField("held fm left", (b, d) -> copy(b, b.fmTime(), d.heldLeft(), b.heldRight(),
                        b.lastLeft(), b.lastRight(), b.framesEnded(), b.lowPassLeft(), b.lowPassRight(),
                        b.eqLeft(), b.eqRight(), b.pending())),
                new StageField("held fm right", (b, d) -> copy(b, b.fmTime(), b.heldLeft(), d.heldRight(),
                        b.lastLeft(), b.lastRight(), b.framesEnded(), b.lowPassLeft(), b.lowPassRight(),
                        b.eqLeft(), b.eqRight(), b.pending())),
                new StageField("previous delta level left", (b, d) -> copy(b, b.fmTime(), b.heldLeft(),
                        b.heldRight(), d.lastLeft(), b.lastRight(), b.framesEnded(), b.lowPassLeft(),
                        b.lowPassRight(), b.eqLeft(), b.eqRight(), b.pending())),
                new StageField("previous delta level right", (b, d) -> copy(b, b.fmTime(), b.heldLeft(),
                        b.heldRight(), b.lastLeft(), d.lastRight(), b.framesEnded(), b.lowPassLeft(),
                        b.lowPassRight(), b.eqLeft(), b.eqRight(), b.pending())),
                new StageField("frames ended", (b, d) -> copy(b, b.fmTime(), b.heldLeft(), b.heldRight(),
                        b.lastLeft(), b.lastRight(), d.framesEnded(), b.lowPassLeft(), b.lowPassRight(),
                        b.eqLeft(), b.eqRight(), b.pending())),
                new StageField("pending samples", (b, d) -> copy(b, b.fmTime(), b.heldLeft(), b.heldRight(),
                        b.lastLeft(), b.lastRight(), b.framesEnded(), b.lowPassLeft(), b.lowPassRight(),
                        b.eqLeft(), b.eqRight(), d.pending())),
                new StageField("psg generator state", (b, d) -> new Snapshot(b.config(), b.blip(), b.fmTime(),
                        b.heldLeft(), b.heldRight(), b.lastLeft(), b.lastRight(), b.framesEnded(), b.lowPassLeft(),
                        b.lowPassRight(), b.eqLeft(), b.eqRight(), b.pending(), d.psg()))));
        if (config.filter() == Filter.THREE_BAND) {
            fields.add(new StageField("eq history left", (b, d) -> copy(b, b.fmTime(), b.heldLeft(),
                    b.heldRight(), b.lastLeft(), b.lastRight(), b.framesEnded(), b.lowPassLeft(),
                    b.lowPassRight(), d.eqLeft(), b.eqRight(), b.pending())));
            fields.add(new StageField("eq history right", (b, d) -> copy(b, b.fmTime(), b.heldLeft(),
                    b.heldRight(), b.lastLeft(), b.lastRight(), b.framesEnded(), b.lowPassLeft(),
                    b.lowPassRight(), b.eqLeft(), d.eqRight(), b.pending())));
        }
        if (config.filter() == Filter.LOW_PASS) {
            fields.add(new StageField("low-pass carry left", (b, d) -> copy(b, b.fmTime(), b.heldLeft(),
                    b.heldRight(), b.lastLeft(), b.lastRight(), b.framesEnded(), d.lowPassLeft(),
                    b.lowPassRight(), b.eqLeft(), b.eqRight(), b.pending())));
            fields.add(new StageField("low-pass carry right", (b, d) -> copy(b, b.fmTime(), b.heldLeft(),
                    b.heldRight(), b.lastLeft(), b.lastRight(), b.framesEnded(), b.lowPassLeft(),
                    d.lowPassRight(), b.eqLeft(), b.eqRight(), b.pending())));
        }
        return fields;
    }

    private static StageField blipField(String name, BiFunction<BlipBuf.State, BlipBuf.State, BlipBuf.State> mixer) {
        return new StageField(name, (b, d) -> new Snapshot(b.config(), mixer.apply(b.blip(), d.blip()), b.fmTime(),
                b.heldLeft(), b.heldRight(), b.lastLeft(), b.lastRight(), b.framesEnded(), b.lowPassLeft(),
                b.lowPassRight(), b.eqLeft(), b.eqRight(), b.pending(), b.psg()));
    }

    private static Snapshot copy(Snapshot b, int fmTime, int heldLeft, int heldRight, int lastLeft, int lastRight,
            long framesEnded, short lowPassLeft, short lowPassRight, NativeCompatiblePcmOutput.ThreeBandEq.State eqLeft,
            NativeCompatiblePcmOutput.ThreeBandEq.State eqRight, short[] pending) {
        return new Snapshot(b.config(), b.blip(), fmTime, heldLeft, heldRight, lastLeft, lastRight, framesEnded,
                lowPassLeft, lowPassRight, eqLeft, eqRight, pending, b.psg());
    }

    // ------------------------------------------------------------- wiring

    @Test
    void stageRequiresTheAccurateCoreAndIsNeverSubstituted() {
        assertThrows(IllegalArgumentException.class, () -> new SmpsPhysicalDevice.Settings(
                48_000.0, false, FmCoreSelection.FAST, THREE_BAND_UNITY));
        VirtualSynthesizer fast = new VirtualSynthesizer(48_000.0, ChipWriteObserver.NONE,
                VirtualSynthesizer.Initialization.DEFERRED, FmCoreSelection.FAST);
        assertThrows(IllegalStateException.class, () -> fast.enableNativeCompatibleOutput(THREE_BAND_UNITY));
        Ym2612Chip midSweep = NativeCompatiblePcmVectors.newChip(null, null);
        midSweep.write(0, 0x22, 0x00);
        midSweep.readStatus();
        assertThrows(IllegalStateException.class, () -> midSweep.enableNativeCompatibleOutput(NONE));
    }

    @Test
    void sessionSettingsWireTheStageWithoutChangingPresentationPcm() {
        short[] plain = new short[800 * 2 * 4];
        short[] tapped = new short[800 * 2 * 4];
        short[] nativePcm = new short[2 * 4 * 800];
        int drained = 0;
        long framesEnded;
        try (OwnedSmpsAudioStream withoutStage = new OwnedSmpsAudioStream("native-pcm-test", 0,
                new SmpsPhysicalDevice.Settings(48_000.0, false),
                LegacyCompatibilitySmpsPhysicalPolicy.INSTANCE, ChipWriteObserver.NONE);
             OwnedSmpsAudioStream withStage = new OwnedSmpsAudioStream("native-pcm-test", 0,
                     new SmpsPhysicalDevice.Settings(48_000.0, false, FmCoreSelection.ACCURATE, THREE_BAND_UNITY),
                     LegacyCompatibilitySmpsPhysicalPolicy.INSTANCE, ChipWriteObserver.NONE)) {
            for (int frame = 0; frame < 4; frame++) {
                short[] a = new short[800 * 2];
                short[] b = new short[800 * 2];
                withoutStage.serviceAndRenderFrame(a, 800);
                withStage.serviceAndRenderFrame(b, 800);
                System.arraycopy(a, 0, plain, frame * 1600, 1600);
                System.arraycopy(b, 0, tapped, frame * 1600, 1600);
                drained += withStage.drainNativeCompatiblePcm(nativePcm, drained * 2, 800);
                assertEquals(0, withoutStage.drainNativeCompatiblePcm(nativePcm, 0, 800));
            }
            framesEnded = withStage.nativeCompatibleFramesEnded();
            assertEquals(-1, withoutStage.nativeCompatibleFramesEnded());
        }
        assertArrayEquals(plain, tapped, "presentation PCM is unchanged by the stage");
        // Four 800-sample frames at 48 kHz cover at least 85,227 YM clocks, so at least three NTSC frames
        // (64,003 clocks) have ended; the resampler's look-ahead may clock past the fourth boundary (85,338).
        assertTrue(framesEnded >= 3 && framesEnded <= 4, "frames ended " + framesEnded);
        assertEquals(blipSamplesForFrames(framesEnded), drained, "every ended frame's samples were drained");
    }

    /** blip_buf's sample count for whole NTSC frames from a cleared buffer: offset factor/2, + 896040 * factor per frame. */
    private static int blipSamplesForFrames(long frames) {
        double exact = (double) (1L << 52) * 44_100.0 / 53_693_175.0;
        long factor = (long) exact;
        if (factor < exact) {
            factor++;
        }
        long offset = factor / 2;
        int total = 0;
        for (long frame = 0; frame < frames; frame++) {
            offset += (long) NativeCompatiblePcmOutput.FRAME_MCYCLES * factor;
            int samples = (int) (offset >>> 52);
            total += samples;
            offset -= (long) samples << 52;
        }
        return total;
    }

    // ------------------------------------------------------------- helpers

    private static short[] drainAll(NativeCompatiblePcmOutput stage) {
        short[] out = new short[stage.pendingStereoFrames() * 2];
        stage.drain(out, 0, out.length / 2);
        return out;
    }

    /** The committed PSG vector input as {@code {masterClock, value}} pairs. */
    private static List<long[]> psgWrites() {
        List<long[]> writes = new ArrayList<>();
        for (String line : NativeCompatiblePcmVectors.resourceText("psg_writes.csv").split("\n")) {
            if (line.isBlank() || line.startsWith("master_clock")) {
                continue;
            }
            String[] parts = line.split(",");
            writes.add(new long[] {Long.parseLong(parts[0]), Long.parseLong(parts[1])});
        }
        return writes;
    }

    /** The production chip's 24-cycle pin sums for the fixed stream (frame value >> OUTPUT_SHIFT, lossless). */
    private static int[][] productionPinSums() {
        int[] left = new int[CHIP_FRAMES];
        int[] right = new int[CHIP_FRAMES];
        NativeCompatiblePcmVectors.drive(NativeCompatiblePcmVectors.newChip(null, null), 0, CHIP_FRAMES,
                left, right);
        for (int i = 0; i < CHIP_FRAMES; i++) {
            assertEquals(0, (left[i] | right[i]) & 7, "frame value is a pin sum << 3");
            left[i] >>= 3;
            right[i] >>= 3;
        }
        return new int[][] {left, right};
    }

    /**
     * Feeds a standalone stage clock by clock the way the chip tap does: the
     * sweep's sum is latched on its 24th clock. {@code leadingClocks} shifts
     * the master-clock origin.
     */
    private static short[] feedStandalone(Config config, int[][] sums, List<long[]> psg, int leadingClocks) {
        NativeCompatiblePcmOutput stage = new NativeCompatiblePcmOutput(config);
        int[] next = {0};
        Runnable clock = () -> {
            while (next[0] < psg.size() && psg.get(next[0])[0] <= stage.masterClock()) {
                stage.psgWrite((int) psg.get(next[0]++)[1]);
            }
            stage.clockFm();
        };
        for (int i = 0; i < leadingClocks; i++) {
            clock.run();
        }
        for (int frame = 0; frame < sums[0].length && stage.framesEnded() < NTSC_FRAMES; frame++) {
            for (int c = 0; c < 23; c++) {
                clock.run();
            }
            stage.latchFmFrame(sums[0][frame], sums[1][frame]);
            clock.run();
        }
        short[] all = drainAll(stage);
        short[] expectedLength = NativeCompatiblePcmVectors.s16(NativeCompatiblePcmVectors.resource("native-none.s16"));
        return Arrays.copyOf(all, Math.min(all.length, expectedLength.length));
    }
}
