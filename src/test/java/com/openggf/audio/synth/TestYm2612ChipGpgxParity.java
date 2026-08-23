package com.openggf.audio.synth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestYm2612ChipGpgxParity {
    private static final Path ORACLE = Path.of(
            "docs/architecture/research/audio/"
                    + "s3k-blue-sphere-ym-write-oracle-v1.json");
    private static final Path ENVELOPE_ORACLE = Path.of(
            "docs/architecture/research/audio/"
                    + "s3k-ym-envelope-phase-oracle-v1.json");
    private static final Path ENVELOPE_HARNESS = Path.of(
            "docs/architecture/research/audio/"
                    + "s3k-ym-envelope-phase-native-harness-v1.c");
    private static final Path PINNED_GPGX = Path.of(
            "target/audio-parity/native/blue-sphere-manual-source/"
                    + "waterbox/gpgx/Genesis-Plus-GX");
    private static final Pattern HARNESS_WRITE = Pattern.compile(
            "\\{(\\d+),(\\d+),(\\d+),(\\d+)\\}");

    @Test
    void correctedOracleUsesPostUpdateCyclesAndZeroDmaStalls()
            throws Exception {
        YmNativeOracle oracle = YmNativeOracle.load(ORACLE);
        assertEquals("post_fm_update", oracle.eventPhase());
        assertEquals(33,
                oracle.groups().get(7).writes().getLast().sourceOrdinal());
        assertEquals(151_590L,
                oracle.groups().get(7).relativeLastMasterCycle());
        assertEquals(
                "9c204d55e1c7524bf94180aa930d6be6a88e332d5227f187a2ed3d048b6bd375",
                oracle.provenance().diagnosticPatchSha256());
        assertEquals(
                "3e2cddbb22c93676046f980926fd14d0689bb5bfd36ee75d0d630c2289b940a3",
                oracle.provenance().diagnosticCoreSha256());
        assertTrue(oracle.groups().stream()
                .flatMap(group -> group.writes().stream())
                .allMatch(write -> write.dmaStallCount() == 0));
    }

    @Test
    void trackedEnvelopeHarnessIsBoundToRetainedCorrectedProjection()
            throws Exception {
        JsonNode envelope = new ObjectMapper().readTree(
                ENVELOPE_ORACLE.toFile());
        assertEquals("retained corrected native internal-ordinal delta",
                envelope.path("harness_cycle_projection").asText());
        assertEquals(sha256(ENVELOPE_HARNESS), envelope.path("provenance")
                .path("harness_sha256").asText());
        assertEquals(sha256(ORACLE), envelope.path("provenance")
                .path("retained_oracle_sha256").asText());

        Matcher matcher = HARNESS_WRITE.matcher(
                Files.readString(ENVELOPE_HARNESS));
        List<String> harnessProjection = new ArrayList<>();
        while (matcher.find()) {
            harnessProjection.add("%s:%s:%s:%s".formatted(
                    matcher.group(1), matcher.group(2), matcher.group(3),
                    matcher.group(4)));
        }
        YmNativeOracle.Group retainedGroup = YmNativeOracle.load(ORACLE)
                .groups().getFirst();
        List<String> retainedProjection = retainedGroup.writes().stream()
                .map(write -> "%d:%d:%d:%d".formatted(
                        write.internalOrdinal()
                                - retainedGroup.firstInternalOrdinal(),
                        write.port(), write.register(), write.value()))
                .toList();
        assertEquals(retainedProjection, harnessProjection,
                "the native harness GROUP is mechanically derived from the "
                        + "retained corrected write projection");
    }

    @Test
    void nativeEnvelopeHarnessReproducesTrackedVectorsWhenCoreIsPresent()
            throws Exception {
        Path ymSource = PINNED_GPGX.resolve("core/sound/ym2612.c");
        Assumptions.assumeTrue(Files.isRegularFile(ymSource),
                "pinned native GPGX checkout is not materialized");
        Path executable = Path.of("target/audio-parity/"
                + "s3k-ym-envelope-phase-native-harness-v1");
        Files.createDirectories(executable.getParent());
        Process compile = new ProcessBuilder("gcc", "-std=c11", "-O2",
                "-I" + PINNED_GPGX.resolve("core"),
                "-I" + PINNED_GPGX.resolve("core/sound"),
                ENVELOPE_HARNESS.toString(), "-lm", "-o",
                executable.toString()).inheritIO().start();
        assertEquals(0, compile.waitFor(), "native harness compilation");
        Process run = new ProcessBuilder(executable.toString())
                .redirectErrorStream(true).start();
        String actual = new String(run.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8).strip();
        assertEquals(0, run.waitFor(), "native harness execution");
        assertEquals(expectedNativeHarnessOutput(), actual);
    }

    @Test
    void sourceTimedGroupImprovesFourSyntheticEnvelopeStartingStates()
            throws Exception {
        YmNativeOracle.Group group = YmNativeOracle.load(ORACLE)
                .groups().get(7);
        EnvelopeOracle oracle = loadEnvelopeOracle();

        List<String> failures = new ArrayList<>();
        for (EnvelopeSeed seed : oracle.isolated()) {
            Ym2612Chip.Snapshot startingState = syntheticFm5State(seed);
            assertEnvelopeSeed(startingState, seed);
            int[] atomic = replayGroup(startingState, group, true);
            int[] sourceTimed = replayGroup(startingState, group, false);
            assertArrayEquals(seed.nativeAtomic(), atomic,
                    seed.name() + " atomic replay must match native GPGX");
            assertWithinNativeWindow(sourceTimed, seed.nativeMinimum(),
                    seed.nativeMaximum(), seed.name());
            int atomicError = attenuationError(
                    atomic, seed.nativeTimed());
            int timedError = attenuationError(
                    sourceTimed, seed.nativeTimed());

            if (timedError >= atomicError) {
                failures.add(seed.name() + ": atomic="
                        + Arrays.toString(atomic) + " (" + atomicError
                        + "), timed=" + Arrays.toString(sourceTimed) + " ("
                        + timedError + ")");
            }
        }
        assertEquals(List.of(), failures,
                "source timing must improve the native GPGX L1 attenuation "
                        + "metric in every authenticated envelope phase");
    }

    @Test
    void overlappingGroupsDoNotRegressTheNativeGpgxMetric()
            throws Exception {
        YmNativeOracle.Group group = YmNativeOracle.load(ORACLE)
                .groups().get(7);
        OverlapSeed overlap = loadEnvelopeOracle().overlap();
        Ym2612Chip.Snapshot startingState = syntheticFm5State(
                overlap.seed());
        assertEnvelopeSeed(startingState, overlap.seed());

        int[] atomic = replayOverlappingGroups(
                startingState, group, overlap.offsetSamples(), true);
        int[] sourceTimed = replayOverlappingGroups(
                startingState, group, overlap.offsetSamples(), false);

        assertArrayEquals(overlap.seed().nativeAtomic(), atomic,
                "atomic overlap must match native GPGX");
        assertWithinNativeWindow(sourceTimed, overlap.nativeMinimum(),
                overlap.nativeMaximum(), "overlap");
        assertTrue(attenuationError(sourceTimed, overlap.nativeTimed())
                        <= attenuationError(atomic, overlap.nativeTimed()),
                "source timing may not regress the pinned native overlap "
                        + "attenuation metric");
    }

    @Test
    void dacDefaultsToUnsmoothedHardwareSamples() {
        assertFalse(new Ym2612Chip().captureSnapshot().dacInterpolate());
    }

    @Test
    void s1BombVoiceRoutesRegisterSlotsThroughAlgorithmTwoLikeGpgx() {
        Ym2612Chip chip = configuredEnhancedChip();
        writeS1BombVoice(chip);
        writeNoteAndKeyOn(chip);

        int[] left = new int[4];
        chip.renderStereo(left, new int[left.length]);

        // Hand-captured from Genesis Plus GX ym2612.c at its native clock/144 rate.
        assertArrayEquals(new int[] { 625, 7760, -6632, -7864 }, left);
    }

    @Test
    void resetEnvelopeCounterAdvancesS1BombVoiceLikeGpgx() {
        Ym2612Chip chip = configuredEnhancedChip();
        writeS1BombVoice(chip);
        writeNoteAndKeyOn(chip);

        int[] left = new int[12];
        chip.renderStereo(left, new int[left.length]);

        // Extends through multiple three-sample EG boundaries in Genesis Plus GX.
        assertArrayEquals(new int[] {
                625, 7760, -6632, -7864, 275, 125,
                8168, 1272, -7908, 2920, 1618, 1863
        }, left);
    }

    @Test
    void decayToSustainTransitionPreservesGpgxAttenuationOvershoot() {
        Ym2612Chip chip = configuredEnhancedChip();
        writeS1BombVoice(chip);
        writeNoteAndKeyOn(chip);

        int[] left = new int[140];
        chip.renderStereo(left, new int[left.length]);

        // GPGX samples 125..140; the first affected decay transition is sample 131.
        assertArrayEquals(new int[] {
                5516, -4208, -1645, 7472, -360, 3838, 695, -2246,
                6492, -7824, 1361, -3706, 7412, -7552, 3666, 7176
        }, Arrays.copyOfRange(left, 124, 140));
    }

    @Test
    void discreteAlgorithmFourQuantizesTheGpgxCarrierSlots() {
        Ym2612Chip chip = new Ym2612Chip();
        chip.setOutputSampleRate(Ym2612Chip.getInternalRate());
        writeS1BombVoice(chip, 0xFC);
        writeNoteAndKeyOn(chip);

        int[] left = new int[8];
        chip.renderStereo(left, new int[left.length]);

        assertArrayEquals(new int[] { 1280, 4640, 8959, 6624, 8959, 8959, 4480, 8959 }, left);
    }

    @Test
    void channelThreeSpecialFrequenciesMapToGpgxOperatorSlots() {
        Ym2612Chip chip = configuredEnhancedChip();
        writeS1BombVoice(chip, 0xFA, 2);
        chip.write(0, 0x27, 0x40);
        chip.write(0, 0xAC, 0x19);
        chip.write(0, 0xA8, 0x34);
        chip.write(0, 0xAD, 0x22);
        chip.write(0, 0xA9, 0x56);
        chip.write(0, 0xAE, 0x2B);
        chip.write(0, 0xAA, 0x78);
        chip.write(0, 0xA6, 0x31);
        chip.write(0, 0xA2, 0x9A);
        chip.write(0, 0xB6, 0xC0);
        chip.write(0, 0x28, 0xF2);

        int[] left = new int[12];
        chip.renderStereo(left, new int[left.length]);

        assertArrayEquals(new int[] {
                625, 7392, -7656, 6908, 5128, -6232,
                -6832, -7020, 5504, -6724, -7696, 3334
        }, left);
    }

    @Test
    void partialKeyOnUsesGpgxOperatorBitOrder() {
        Ym2612Chip chip = new Ym2612Chip();
        chip.setOutputSampleRate(Ym2612Chip.getInternalRate());
        writeS1BombVoice(chip, 0xFF);
        chip.write(0, 0xA4, 0x22);
        chip.write(0, 0xA0, 0x69);
        chip.write(0, 0xB4, 0xC0);
        chip.write(0, 0x28, 0x20); // Key SLOT2 only.

        int[] left = new int[12];
        chip.renderStereo(left, new int[left.length]);

        assertArrayEquals(new int[] {
                768, 832, 864, 960, 992, 1088,
                1120, 1184, 1280, 1312, 1376, 1440
        }, left);
    }

    @Test
    void scheduledWriteDrainsAtTheExactPreInternalSampleBoundary() {
        assertScheduledWriteBoundary(Ym2612Chip.getInternalRate(), false, 5);
    }

    @Test
    void linearResamplingCannotMoveTheScheduledWriteBoundary() {
        assertScheduledWriteBoundary(44_100.0, false, 8);
    }

    @Test
    void blipResamplingCannotMoveTheScheduledWriteBoundary() {
        assertScheduledWriteBoundary(44_100.0, true, 64);
    }

    @Test
    void hybridSizedRenderCannotSkipTheScheduledWriteBoundary() {
        assertScheduledWriteBoundary(44_100.0, true, 735);
    }

    @Test
    void scheduledChipAndKeyOnCallbacksFireOnlyAtDrainAndNotAfterDiscard() {
        Ym2612Chip drainedChip = configuredEnhancedChip();
        YmWriteTimeline drainedTimeline = new YmWriteTimeline(1);
        drainedChip.setWriteTimeline(drainedTimeline);
        RecordingObserver drainedObserver = new RecordingObserver(drainedChip);
        drainedChip.setWriteObserver(drainedObserver);
        drainedTimeline.commit(List.of(
                scheduledEntry(3_150, 0, 0x28, 0xF0, 1)));

        drainedChip.renderStereo(new int[4], new int[4]);
        assertEquals(List.of(), drainedObserver.writeFrontiers);
        assertEquals(List.of(), drainedObserver.keyOnFrontiers);
        drainedChip.renderStereo(new int[1], new int[1]);
        assertEquals(List.of(4_032L), drainedObserver.writeFrontiers);
        assertEquals(List.of(4_032L, 4_032L, 4_032L, 4_032L),
                drainedObserver.keyOnFrontiers);

        Ym2612Chip discardedChip = configuredEnhancedChip();
        YmWriteTimeline discardedTimeline = new YmWriteTimeline(1);
        discardedChip.setWriteTimeline(discardedTimeline);
        RecordingObserver discardedObserver = new RecordingObserver(discardedChip);
        discardedChip.setWriteObserver(discardedObserver);
        discardedTimeline.commit(List.of(
                scheduledEntry(3_150, 0, 0x28, 0xF0, 1)));

        discardedChip.renderStereo(new int[4], new int[4]);
        assertEquals(List.of(), discardedObserver.writeFrontiers);
        assertEquals(List.of(), discardedObserver.keyOnFrontiers);

        discardedTimeline.discardBeforeGeneration(2);
        discardedChip.renderStereo(new int[2], new int[2]);
        assertEquals(List.of(), discardedObserver.writeFrontiers);
        assertEquals(List.of(), discardedObserver.keyOnFrontiers);
    }

    private static void assertScheduledWriteBoundary(
            double outputRate, boolean blip, int outputSamples) {
        Ym2612Chip chip = new Ym2612Chip();
        chip.setOutputSampleRate(outputRate);
        chip.setUseBlipResampler(blip);
        YmWriteTimeline timeline = new YmWriteTimeline(1);
        chip.setWriteTimeline(timeline);
        RecordingObserver observer = new RecordingObserver(chip);
        chip.setWriteObserver(observer);
        timeline.commit(List.of(scheduledEntry(3_150, 0, 0x22, 0x08, 1)));

        chip.renderStereo(new int[outputSamples], new int[outputSamples]);

        assertEquals(List.of(4_032L), observer.writeFrontiers);
        assertEquals(List.of(
                "sample@0", "sample@1008", "sample@2016", "sample@3024",
                "write@4032", "sample@4032"),
                observer.boundaries.subList(0, 6));
        assertTrue(chip.renderedMasterCycleFrontier() >= 5_040L);
    }

    private static YmWriteTimeline.Entry scheduledEntry(
            long dueMasterCycle, long sourceOrdinal,
            int register, int value, long generation) {
        return new YmWriteTimeline.Entry(
                dueMasterCycle, sourceOrdinal, 0, register, value,
                generation, 0,
                new com.openggf.audio.rewind.SmpsSourceDescriptor(
                        com.openggf.audio.rewind.SmpsSourceDescriptor.Kind.UNKNOWN,
                        -1, null, null, 0, 0, 0, false, 0),
                com.openggf.audio.smps.YmServiceTimingProfile.SegmentKind.KEY_OFF);
    }

    private static Ym2612Chip.Snapshot syntheticFm5State(
            EnvelopeSeed seed) {
        Ym2612Chip chip = configuredEnhancedChip();
        int[] offsets = { 0, 4, 8, 12 };
        for (int offset : offsets) {
            chip.write(1, 0x31 + offset, 0x01);
            chip.write(1, 0x41 + offset, 0x00);
            chip.write(1, 0x51 + offset, 0x1A);
            chip.write(1, 0x61 + offset, 0x1F);
            chip.write(1, 0x71 + offset, 0x08);
            chip.write(1, 0x81 + offset, 0x4F);
        }
        chip.write(1, 0xB1, 0x07);
        chip.write(1, 0xB5, 0xC0);
        chip.write(1, 0xA5, 0x23);
        chip.write(1, 0xA1, 0x3F);
        chip.write(0, 0x28, 0xF5);
        chip.renderStereo(new int[seed.samples()],
                new int[seed.samples()]);
        if (seed.releaseSamples() > 0) {
            chip.write(0, 0x28, 0x05);
            chip.renderStereo(new int[seed.releaseSamples()],
                    new int[seed.releaseSamples()]);
        }
        return chip.captureSnapshot();
    }

    private static void assertEnvelopeSeed(
            Ym2612Chip.Snapshot snapshot, EnvelopeSeed seed) {
        Ym2612Chip.OperatorSnapshot[] operators = snapshot.channels()[4].ops();
        assertEquals(List.of(seed.phase(), seed.phase(), seed.phase(),
                        seed.phase()),
                Arrays.stream(operators)
                        .map(TestYm2612ChipGpgxParity::envelopePhase)
                        .toList(),
                seed.name() + " must exercise the named operator phase");
        assertArrayEquals(seed.seedAttenuation(),
                Arrays.stream(operators)
                        .mapToInt(Ym2612Chip.OperatorSnapshot::volume)
                        .toArray(),
                seed.name() + " seed must match the native lab vector");
    }

    private static String envelopePhase(
            Ym2612Chip.OperatorSnapshot operator) {
        try {
            return operator.getClass().getMethod("curEnv")
                    .invoke(operator).toString();
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static int[] replayGroup(
            Ym2612Chip.Snapshot startingState,
            YmNativeOracle.Group group,
            boolean atomic) {
        Ym2612Chip chip = configuredEnhancedChip();
        chip.restoreSnapshot(startingState);
        YmWriteTimeline timeline = new YmWriteTimeline(group.writes().size());
        chip.setWriteTimeline(timeline);
        List<Integer> attenuation = new ArrayList<>();
        chip.setWriteObserver(new ChipWriteObserver() {
            @Override
            public void onYm2612Write(
                    int port, int register, int value) {
            }

            @Override
            public void onYm2612KeyOn(
                    int channel, int operator, int value) {
                if (channel == 4) {
                    attenuation.add(value);
                }
            }

            @Override
            public void onPsgWrite(int value) {
            }
        });
        List<YmWriteTimeline.Entry> entries = new ArrayList<>();
        for (YmNativeOracle.Write write : group.writes()) {
            entries.add(new YmWriteTimeline.Entry(
                    atomic ? 0 : write.relativeMasterCycle(),
                    write.sourceOrdinal(), write.port(), write.register(),
                    write.value(), 0, 0,
                    new com.openggf.audio.rewind.SmpsSourceDescriptor(
                            com.openggf.audio.rewind.SmpsSourceDescriptor.Kind
                                    .UNKNOWN,
                            -1, null, null, 0, 0, 0, false, 0),
                    com.openggf.audio.smps.YmServiceTimingProfile.SegmentKind
                            .FM_VOICE_UPLOAD));
        }
        timeline.commit(entries);
        chip.renderStereo(new int[200], new int[200]);
        return attenuation.stream().mapToInt(Integer::intValue).toArray();
    }

    private static int[] replayOverlappingGroups(
            Ym2612Chip.Snapshot startingState,
            YmNativeOracle.Group group,
            int offsetSamples,
            boolean atomic) {
        Ym2612Chip chip = configuredEnhancedChip();
        chip.restoreSnapshot(startingState);
        YmWriteTimeline timeline = new YmWriteTimeline(
                group.writes().size() * 2);
        chip.setWriteTimeline(timeline);
        List<Integer> attenuation = new ArrayList<>();
        chip.setWriteObserver(new ChipWriteObserver() {
            @Override
            public void onYm2612Write(int port, int register, int value) { }

            @Override
            public void onYm2612KeyOn(
                    int channel, int operator, int value) {
                if (channel == 4) attenuation.add(value);
            }

            @Override
            public void onPsgWrite(int value) { }
        });
        List<YmWriteTimeline.Entry> entries = new ArrayList<>();
        for (int copy = 0; copy < 2; copy++) {
            for (YmNativeOracle.Write write : group.writes()) {
                long due = atomic ? 0
                        : write.relativeMasterCycle()
                                + copy * offsetSamples * 1_008L;
                entries.add(new YmWriteTimeline.Entry(
                        due, entries.size(), write.port(), write.register(),
                        write.value(), 0, 0,
                        new com.openggf.audio.rewind.SmpsSourceDescriptor(
                                com.openggf.audio.rewind.SmpsSourceDescriptor
                                        .Kind.UNKNOWN,
                                -1, null, null, 0, 0, 0, false, 0),
                        com.openggf.audio.smps.YmServiceTimingProfile
                                .SegmentKind.FM_VOICE_UPLOAD));
            }
        }
        timeline.commit(entries);
        chip.renderStereo(new int[400], new int[400]);
        int start = attenuation.size() - 4;
        return attenuation.subList(start, attenuation.size()).stream()
                .mapToInt(Integer::intValue).toArray();
    }

    private static int attenuationError(int[] actual, int[] expected) {
        assertEquals(expected.length, actual.length);
        int error = 0;
        for (int index = 0; index < expected.length; index++) {
            error += Math.abs(actual[index] - expected[index]);
        }
        return error;
    }

    private static void assertWithinNativeWindow(
            int[] actual, int[] minimum, int[] maximum, String name) {
        assertEquals(minimum.length, actual.length);
        assertEquals(maximum.length, actual.length);
        for (int operator = 0; operator < actual.length; operator++) {
            assertTrue(actual[operator] >= minimum[operator]
                            && actual[operator] <= maximum[operator],
                    name + " operator " + operator + " attenuation "
                            + actual[operator] + " outside native window ["
                            + minimum[operator] + ", "
                            + maximum[operator] + "]");
        }
    }

    private static EnvelopeOracle loadEnvelopeOracle() throws Exception {
        JsonNode root = new ObjectMapper().readTree(ENVELOPE_ORACLE.toFile());
        assertEquals("openggf.s3k-ym-envelope-phase-oracle.v1",
                root.path("schema").asText());
        assertEquals("post_fm_update", root.path("event_phase").asText());
        assertEquals(0, root.path("dma_stall_count").asInt());
        JsonNode provenance = root.path("provenance");
        assertEquals("051d430d3d1b54625f9900c8f152d7f232e06daf",
                provenance.path("gpgx_commit").asText());
        assertEquals("82d61b0c5547f45a55a2d87e337494c9a1d668cd690b858db1ceba59801fdcb1",
                provenance.path("harness_sha256").asText());
        assertEquals("5115c7e2bb5443ae7ccf1fa32d3d41dc1f77d17f086405e29bd3c258e96ee7e2",
                provenance.path("retained_oracle_sha256").asText());
        List<EnvelopeSeed> isolated = new ArrayList<>();
        for (JsonNode node : root.path("isolated")) {
            isolated.add(seed(node));
        }
        JsonNode overlap = root.path("overlap");
        EnvelopeSeed overlapSeed = new EnvelopeSeed(
                "overlap", overlap.path("seed_samples").asInt(), 0,
                overlap.path("native_phase").asText(),
                ints(overlap.path("seed_attenuation")),
                ints(overlap.path("atomic_second_key_on_attenuation")),
                ints(overlap.path("source_timed_second_key_on_attenuation")),
                ints(overlap.path("source_timed_min")),
                ints(overlap.path("source_timed_max")));
        return new EnvelopeOracle(isolated, new OverlapSeed(
                overlapSeed,
                overlap.path("second_group_offset_internal_samples").asInt(),
                ints(overlap.path("source_timed_second_key_on_attenuation")),
                ints(overlap.path("source_timed_min")),
                ints(overlap.path("source_timed_max"))));
    }

    private static String expectedNativeHarnessOutput() throws Exception {
        JsonNode root = new ObjectMapper().readTree(ENVELOPE_ORACLE.toFile());
        List<String> lines = new ArrayList<>();
        for (JsonNode node : root.path("isolated")) {
            lines.add(nativeHarnessLine(node.path("name").asText(), node,
                    "atomic_key_on_attenuation",
                    "source_timed_key_on_attenuation"));
        }
        JsonNode overlap = root.path("overlap");
        lines.add(nativeHarnessLine("overlap", overlap,
                "atomic_second_key_on_attenuation",
                "source_timed_second_key_on_attenuation"));
        return String.join(System.lineSeparator(), lines);
    }

    private static String nativeHarnessLine(
            String name, JsonNode node, String atomicField,
            String timedField) {
        String phase = (node.path("native_phase_code").asText() + " ")
                .repeat(4).stripTrailing();
        return name + " phase " + phase
                + " volume " + vector(node.path("seed_attenuation"))
                + " atomic " + vector(node.path(atomicField))
                + " timed " + vector(node.path(timedField))
                + " window-min " + vector(node.path("source_timed_min"))
                + " window-max " + vector(node.path("source_timed_max"));
    }

    private static String vector(JsonNode values) {
        List<String> result = new ArrayList<>();
        values.forEach(value -> result.add(value.asText()));
        return String.join(" ", result);
    }

    private static String sha256(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(path)));
    }

    private static EnvelopeSeed seed(JsonNode node) {
        return new EnvelopeSeed(node.path("name").asText(),
                node.path("seed_samples").asInt(),
                node.path("release_samples").asInt(),
                node.path("native_phase").asText(),
                ints(node.path("seed_attenuation")),
                ints(node.path("atomic_key_on_attenuation")),
                ints(node.path("source_timed_key_on_attenuation")),
                ints(node.path("source_timed_min")),
                ints(node.path("source_timed_max")));
    }

    private static int[] ints(JsonNode array) {
        int[] values = new int[array.size()];
        for (int index = 0; index < values.length; index++) {
            values[index] = array.get(index).asInt();
        }
        return values;
    }

    private record EnvelopeSeed(
            String name, int samples, int releaseSamples, String phase,
            int[] seedAttenuation, int[] nativeAtomic, int[] nativeTimed,
            int[] nativeMinimum, int[] nativeMaximum) {
        private EnvelopeSeed {
            seedAttenuation = Arrays.copyOf(
                    seedAttenuation, seedAttenuation.length);
            nativeAtomic = Arrays.copyOf(nativeAtomic, nativeAtomic.length);
            nativeTimed = Arrays.copyOf(nativeTimed, nativeTimed.length);
            nativeMinimum = Arrays.copyOf(
                    nativeMinimum, nativeMinimum.length);
            nativeMaximum = Arrays.copyOf(
                    nativeMaximum, nativeMaximum.length);
        }
    }

    private record EnvelopeOracle(
            List<EnvelopeSeed> isolated, OverlapSeed overlap) { }

    private record OverlapSeed(
            EnvelopeSeed seed, int offsetSamples, int[] nativeTimed,
            int[] nativeMinimum, int[] nativeMaximum) { }

    private static final class RecordingObserver implements ChipWriteObserver {
        private final Ym2612Chip chip;
        private final List<Long> writeFrontiers = new ArrayList<>();
        private final List<Long> keyOnFrontiers = new ArrayList<>();
        private final List<String> boundaries = new ArrayList<>();

        private RecordingObserver(Ym2612Chip chip) {
            this.chip = chip;
        }

        @Override
        public void onYm2612Write(int port, int register, int value) {
            writeFrontiers.add(chip.renderedMasterCycleFrontier());
            boundaries.add("write@" + chip.renderedMasterCycleFrontier());
        }

        @Override
        public void onYm2612KeyOn(int channel, int operator, int attenuation) {
            keyOnFrontiers.add(chip.renderedMasterCycleFrontier());
        }

        @Override
        public int ym2612ChannelSampleMask() {
            return 1;
        }

        @Override
        public void onYm2612ChannelSample(int channel, int output) {
            boundaries.add("sample@" + chip.renderedMasterCycleFrontier());
        }

        @Override
        public void onPsgWrite(int value) {
        }
    }

    private static Ym2612Chip configuredEnhancedChip() {
        Ym2612Chip chip = new Ym2612Chip();
        chip.setChipType(2); // GPGX YM2612_ENHANCED: no discrete ladder distortion.
        chip.setOutputSampleRate(Ym2612Chip.getInternalRate());
        return chip;
    }

    private static void writeS1BombVoice(Ym2612Chip chip) {
        writeS1BombVoice(chip, 0xFA);
    }

    private static void writeS1BombVoice(Ym2612Chip chip, int feedbackAndAlgorithm) {
        writeS1BombVoice(chip, feedbackAndAlgorithm, 0);
    }

    private static void writeS1BombVoice(Ym2612Chip chip, int feedbackAndAlgorithm, int channel) {
        int[] registers = {
                0xB0,
                0x30, 0x38, 0x34, 0x3C,
                0x50, 0x58, 0x54, 0x5C,
                0x60, 0x68, 0x64, 0x6C,
                0x70, 0x78, 0x74, 0x7C,
                0x80, 0x88, 0x84, 0x8C,
                0x40, 0x48, 0x44, 0x4C
        };
        int[] values = {
                feedbackAndAlgorithm,
                0x21, 0x30, 0x10, 0x32,
                0x1F, 0x1F, 0x1F, 0x1F,
                0x05, 0x18, 0x05, 0x10,
                0x0B, 0x1F, 0x10, 0x10,
                0x1F, 0x2F, 0x4F, 0x2F,
                0x0D, 0x07, 0x04, 0x80
        };
        for (int i = 0; i < registers.length; i++) {
            chip.write(0, registers[i] + channel, values[i]);
        }
    }

    private static void writeNoteAndKeyOn(Ym2612Chip chip) {
        chip.write(0, 0xA4, 0x22);
        chip.write(0, 0xA0, 0x69);
        chip.write(0, 0xB4, 0xC0);
        chip.write(0, 0x28, 0xF0);
    }
}
