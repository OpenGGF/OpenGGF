package com.openggf.game.sonic3k.audio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.audio.smps.YmServiceTimingProfile;
import com.openggf.audio.smps.YmServiceTimingProfile.PathKind;
import com.openggf.audio.smps.YmServiceTimingProfile.Segment;
import com.openggf.audio.smps.YmServiceTimingProfile.SegmentKind;
import com.openggf.audio.smps.YmServiceTimingProfile.Variant;
import com.openggf.game.sonic1.audio.Sonic1SmpsSequencerConfig;
import com.openggf.game.sonic2.audio.Sonic2SmpsSequencerConfig;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestSonic3kYmServiceTimingProfile {
    private static final Path CALCULATION = Path.of(
            "docs/architecture/research/audio/s3k-ym-write-timing-calculation-v1.json");
    private static final Path ORACLE = Path.of(
            "docs/architecture/research/audio/s3k-blue-sphere-ym-write-oracle-v1.json");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Variant ADMISSION = new Variant(
            1, 4, false, true, 0, PathKind.FIRST_ADMISSION);
    private static final Variant BLUE_SPHERE = firstAttack(0b1110);

    @Test
    void productionConfigsEnableOnlyTheAuditedLockedOnProfile() {
        YmServiceTimingProfile none = YmServiceTimingProfile.none();
        assertSame(none,
                Sonic1SmpsSequencerConfig.CONFIG.getYmServiceTimingProfile());
        assertSame(none,
                Sonic2SmpsSequencerConfig.CONFIG.getYmServiceTimingProfile());
        assertSame(Sonic3kYmServiceTimingProfile.PROFILE,
                Sonic3kSmpsSequencerConfig.CONFIG.getYmServiceTimingProfile());
        assertSame(Sonic3kYmServiceTimingProfile.PROFILE,
                Sonic3kSmpsSequencerConfig.create(null)
                        .getYmServiceTimingProfile());
        assertEquals(34, Sonic3kYmServiceTimingProfile.PROFILE
                .maximumWritesPerDriverService());
    }

    @Test
    void auditedFirstAttackSegmentsHaveExactNormalizedShape() {
        YmServiceTimingProfile profile = Sonic3kYmServiceTimingProfile.PROFILE;
        assertArrayEquals(new long[] { 0, 3_570, 3_150, 3_150, 3_150 },
                advances(profile, SegmentKind.SFX_ADMISSION_PREP, ADMISSION));
        assertArrayEquals(new long[] { 0, 3_150, 3_150, 3_150 },
                advances(profile, SegmentKind.SFX_MAX_RELEASE, BLUE_SPHERE));
        assertArrayEquals(new long[] {
                0, 3_225, 3_765,
                3_570, 3_570, 3_570, 3_570, 3_570, 3_570, 3_570,
                3_570, 3_570, 3_570, 3_570, 3_570, 3_570, 3_570,
                3_570, 3_570, 3_570, 3_570, 3_570,
                5_145, 3_825, 3_825, 3_825 },
                advances(profile, SegmentKind.FM_VOICE_UPLOAD, BLUE_SPHERE));
        assertArrayEquals(new long[] { 0 },
                advances(profile, SegmentKind.KEY_OFF, BLUE_SPHERE));
        assertArrayEquals(new long[] { 0, 2_700, 2_880 },
                advances(profile, SegmentKind.FREQUENCY_AND_KEY_ON, BLUE_SPHERE));
    }

    @Test
    void carrierMaskSelectsEveryStoredOperatorVolumeBranch() {
        long[] noCarriers = advances(Sonic3kYmServiceTimingProfile.PROFILE,
                SegmentKind.FM_VOICE_UPLOAD, firstAttack(0));
        long[] mixed = advances(Sonic3kYmServiceTimingProfile.PROFILE,
                SegmentKind.FM_VOICE_UPLOAD, firstAttack(0b1110));
        long[] allCarriers = advances(Sonic3kYmServiceTimingProfile.PROFILE,
                SegmentKind.FM_VOICE_UPLOAD, firstAttack(0b1111));

        assertArrayEquals(new long[] { 5_145, 3_540, 3_540, 3_540 },
                Arrays.copyOfRange(noCarriers, 22, 26));
        assertArrayEquals(new long[] { 5_145, 3_825, 3_825, 3_825 },
                Arrays.copyOfRange(mixed, 22, 26));
        assertArrayEquals(new long[] { 5_430, 3_825, 3_825, 3_825 },
                Arrays.copyOfRange(allCarriers, 22, 26));

        assertThrows(IllegalArgumentException.class,
                () -> firstAttack(0b1_0000));
        assertThrows(IllegalArgumentException.class,
                () -> firstAttack(-1));
    }

    @Test
    void calculationRowsDeriveEveryAdvanceAndTheAuditedComposite()
            throws IOException {
        JsonNode root = MAPPER.readTree(CALCULATION.toFile());
        assertEquals("openggf.s3k-ym-write-calculation.v1",
                root.path("schema").asText());

        for (JsonNode segmentNode : root.path("segments")) {
            SegmentKind kind = SegmentKind.valueOf(segmentNode.path("kind").asText());
            Variant variant = variant(segmentNode.path("variant"));
            long[] derived = derivedAdvances(segmentNode.path("writes"));
            assertEquals(0, derived[0], kind + " slot-zero anchor");
            assertArrayEquals(derived, advances(
                    Sonic3kYmServiceTimingProfile.PROFILE, kind, variant),
                    kind + " " + variant);
        }

        long[] relative = composeAuditedFirstAttack(root);
        assertArrayEquals(new long[] {
                0, 3150, 6300, 9450, 15885, 19110, 22875, 26445,
                30015, 33585, 37155, 40725, 44295, 47865, 51435,
                55005, 58575, 62145, 65715, 69285, 72855, 76425,
                79995, 83565, 87135, 90705, 95850, 99675, 103500,
                107325, 115380, 146010, 148710, 151590 }, relative);
        assertEquals(10_106L, relative[33] / 15L);
    }

    @Test
    void sourceCalculationAgreesIndependentlyWithNativeOracleAndNoDmaStalls()
            throws IOException {
        JsonNode calculation = MAPPER.readTree(CALCULATION.toFile());
        long[] sourceDerived = composeAuditedFirstAttack(calculation);
        JsonNode oracle = MAPPER.readTree(ORACLE.toFile());

        int comparedGroups = 0;
        for (JsonNode group : oracle.path("groups")) {
            if (group.path("writes").size() != sourceDerived.length) {
                continue;
            }
            long[] nativeRelative = new long[sourceDerived.length];
            for (int index = 0; index < nativeRelative.length; index++) {
                JsonNode write = group.path("writes").get(index);
                nativeRelative[index] = write.path("relative_master_cycle").asLong();
                assertEquals(0, write.path("dma_stall_count").asInt(),
                        "group " + group.path("group_ordinal").asInt()
                                + " write " + index);
            }
            assertArrayEquals(sourceDerived, nativeRelative,
                    "group " + group.path("group_ordinal").asInt());
            comparedGroups++;
        }
        assertEquals(12, comparedGroups);
    }

    @Test
    void profileRejectsInvalidShapeAndDefensivelyCopiesArrays() {
        Variant variant = firstAttack(0);
        long[] source = { 0, 15 };
        Segment segment = new Segment(SegmentKind.KEY_OFF, variant, source);
        source[1] = 999;
        assertArrayEquals(new long[] { 0, 15 },
                segment.advanceBeforeWriteMasterCycles());
        long[] obtained = segment.advanceBeforeWriteMasterCycles();
        obtained[1] = 888;
        assertArrayEquals(new long[] { 0, 15 },
                segment.advanceBeforeWriteMasterCycles());
        assertNotSame(obtained, segment.advanceBeforeWriteMasterCycles());

        assertThrows(IllegalArgumentException.class,
                () -> new Segment(SegmentKind.KEY_OFF, variant, new long[0]));
        assertThrows(IllegalArgumentException.class,
                () -> new Segment(SegmentKind.KEY_OFF, variant,
                        new long[] { 0, -1 }));
        assertThrows(IllegalArgumentException.class,
                () -> YmServiceTimingProfile.of(1,
                        new Segment(SegmentKind.KEY_OFF, variant,
                                new long[] { 0, 15 })));
        Segment duplicate = new Segment(SegmentKind.KEY_OFF, variant,
                new long[] { 0 });
        assertThrows(IllegalArgumentException.class,
                () -> YmServiceTimingProfile.of(2, duplicate, duplicate));
        assertThrows(ArithmeticException.class,
                () -> YmServiceTimingProfile.of(3,
                        new Segment(SegmentKind.KEY_OFF, variant,
                                new long[] { 0, Long.MAX_VALUE, 1 })));
        assertThrows(IllegalArgumentException.class,
                () -> YmServiceTimingProfile.of(-1));
    }

    @Test
    void noneIsCanonicalEmptyAndRequiresNoAuditedSegments() {
        assertSame(YmServiceTimingProfile.none(), YmServiceTimingProfile.none());
        assertEquals(0, YmServiceTimingProfile.none()
                .maximumWritesPerDriverService());
        assertThrows(IllegalArgumentException.class,
                () -> YmServiceTimingProfile.none().requireSegment(
                        SegmentKind.KEY_OFF, firstAttack(0)));
    }

    private static Variant firstAttack(int carrierMask) {
        return new Variant(1, 4, true, false, carrierMask,
                PathKind.FIRST_VOICE_ATTACK);
    }

    private static Variant variant(JsonNode node) {
        return new Variant(node.path("port").asInt(),
                node.path("operators").asInt(),
                node.path("banked_voice").asBoolean(),
                node.path("ssg_eg").asBoolean(),
                node.path("carrier_mask").asInt(),
                PathKind.valueOf(node.path("path").asText()));
    }

    private static long[] advances(
            YmServiceTimingProfile profile, SegmentKind kind, Variant variant) {
        return profile.requireSegment(kind, variant)
                .advanceBeforeWriteMasterCycles();
    }

    private static long[] derivedAdvances(JsonNode writes) {
        long[] advances = new long[writes.size()];
        for (int index = 0; index < advances.length; index++) {
            JsonNode write = writes.get(index);
            assertEquals(index, write.path("slot").asInt());
            long tStates = sumSteps(write.path("advance_before_write_steps"));
            for (JsonNode wait : write.path("bank_waits")) {
                tStates = Math.addExact(tStates, Math.multiplyExact(
                        wait.path("accesses").asLong(),
                        wait.path("average_t_states").asLong()));
            }
            advances[index] = Math.multiplyExact(tStates, 15L);
            assertEquals(write.path("expected_delta_master_cycles").asLong(),
                    advances[index]);
        }
        return advances;
    }

    private static long sumSteps(JsonNode steps) {
        long tStates = 0;
        for (JsonNode step : steps) {
            assertEquals(false, step.path("opcode").asText().isBlank());
            tStates = Math.addExact(tStates, Math.multiplyExact(
                    step.path("count").asLong(),
                    step.path("t_states").asLong()));
        }
        return tStates;
    }

    private static long[] composeAuditedFirstAttack(JsonNode root) {
        List<Long> relative = new ArrayList<>();
        long cursor = 0;
        String[] keys = {
                "blue-sphere-max-release", "blue-sphere-voice-upload",
                "blue-sphere-key-off", "blue-sphere-frequency-key-on" };
        for (String key : keys) {
            JsonNode segment = findSegment(root, key);
            cursor = Math.addExact(cursor,
                    Math.multiplyExact(sumSteps(
                            segment.path("cross_segment_advance_steps")), 15L));
            long[] advances = derivedAdvances(segment.path("writes"));
            for (long advance : advances) {
                cursor = Math.addExact(cursor, advance);
                relative.add(cursor);
            }
        }
        return relative.stream().mapToLong(Long::longValue).toArray();
    }

    private static JsonNode findSegment(JsonNode root, String key) {
        for (JsonNode segment : root.path("segments")) {
            if (key.equals(segment.path("key").asText())) {
                return segment;
            }
        }
        throw new AssertionError("Missing calculation segment " + key);
    }
}
