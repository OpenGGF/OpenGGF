package com.openggf.game.sonic3k.audio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.audio.smps.YmServiceTimingProfile;
import com.openggf.audio.smps.YmServiceTimingProfile.PathKind;
import com.openggf.audio.smps.YmServiceTimingProfile.Segment;
import com.openggf.audio.smps.YmServiceTimingProfile.SegmentKind;
import com.openggf.audio.smps.YmServiceTimingProfile.Variant;
import com.openggf.game.sonic1.audio.Sonic1SmpsSequencerConfig;
import com.openggf.game.sonic1.audio.Sonic1YmServiceTimingProfile;
import com.openggf.game.sonic2.audio.Sonic2SmpsSequencerConfig;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private static final Pattern SOURCE_ROW = Pattern.compile(
            "Z80 Sound Driver\\.asm:(\\d+)(?:-(\\d+))?");
    private static final String SOURCE_ROW_DIGEST =
            "e345a0148cb5e284b36a0572facc2b8bfd7f5e4de5b1a2e58c3f07cf038a7e4f";
    private static final Map<String, Integer> AUTHORITATIVE_T_STATES = Map.ofEntries(
            Map.entry("NOP", 4),
            Map.entry("RET", 10),
            Map.entry("RET cc taken", 11),
            Map.entry("RET cc not taken", 5),
            Map.entry("CALL nn", 17),
            Map.entry("JP nn", 10),
            Map.entry("JP cc taken", 10),
            Map.entry("JR", 12),
            Map.entry("JR cc taken", 12),
            Map.entry("JR cc not taken", 7),
            Map.entry("DJNZ taken", 13),
            Map.entry("DJNZ not taken", 8),
            Map.entry("RST p", 11),
            Map.entry("PUSH AF", 11),
            Map.entry("PUSH BC", 11),
            Map.entry("PUSH DE", 11),
            Map.entry("PUSH HL", 11),
            Map.entry("PUSH IX", 15),
            Map.entry("POP AF", 10),
            Map.entry("POP BC", 10),
            Map.entry("POP DE", 10),
            Map.entry("POP HL", 10),
            Map.entry("POP IX", 14),
            Map.entry("EX DE,HL", 4),
            Map.entry("LD r,r", 4),
            Map.entry("LD r,n", 7),
            Map.entry("LD rr,nn", 10),
            Map.entry("LD r,(HL)", 7),
            Map.entry("LD r,(DE)", 7),
            Map.entry("LD r,(nn)", 13),
            Map.entry("LD rr,(nn)", 16),
            Map.entry("LD r,(IX+d)", 19),
            Map.entry("LD (HL),r", 7),
            Map.entry("LD (nn),r", 13),
            Map.entry("LD (IX+d),r", 19),
            Map.entry("INC r", 4),
            Map.entry("DEC r", 4),
            Map.entry("INC rr", 6),
            Map.entry("DEC rr", 6),
            Map.entry("INC (IX+d)", 23),
            Map.entry("DEC (IX+d)", 23),
            Map.entry("ADD A,r", 4),
            Map.entry("ADD A,n", 7),
            Map.entry("ADD A,(IX+d)", 19),
            Map.entry("ADD HL,rr", 11),
            Map.entry("SUB n", 7),
            Map.entry("AND n", 7),
            Map.entry("OR r", 4),
            Map.entry("OR n", 7),
            Map.entry("OR (IX+d)", 19),
            Map.entry("XOR A", 4),
            Map.entry("CP r", 4),
            Map.entry("CP n", 7),
            Map.entry("CP (IX+d)", 19),
            Map.entry("BIT b,r", 8),
            Map.entry("BIT b,(IX+d)", 20),
            Map.entry("RES b,(IX+d)", 23),
            Map.entry("RRA", 4),
            Map.entry("RRCA", 4));

    private static final Variant ADMISSION = new Variant(
            1, 4, false, true, 0, PathKind.FIRST_ADMISSION);
    private static final Variant BLUE_SPHERE = firstAttack(0b1110);

    @Test
    void productionConfigsEnableOnlyTheirAuditedProfiles() {
        YmServiceTimingProfile none = YmServiceTimingProfile.none();
        assertSame(Sonic1YmServiceTimingProfile.PROFILE,
                Sonic1SmpsSequencerConfig.CONFIG.getYmServiceTimingProfile());
        assertSame(none,
                Sonic2SmpsSequencerConfig.CONFIG.getYmServiceTimingProfile());
        assertSame(Sonic3kYmServiceTimingProfile.PROFILE,
                Sonic3kSmpsSequencerConfig.CONFIG.getYmServiceTimingProfile());
        assertSame(Sonic3kYmServiceTimingProfile.PROFILE,
                Sonic3kSmpsSequencerConfig.create(null)
                        .getYmServiceTimingProfile());
        assertEquals(4 * 34, Sonic3kYmServiceTimingProfile.PROFILE
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
                6_435, 3_225, 3_765,
                3_570, 3_570, 3_570, 3_570, 3_570, 3_570, 3_570,
                3_570, 3_570, 3_570, 3_570, 3_570, 3_570, 3_570,
                3_570, 3_570, 3_570, 3_570, 3_570,
                5_145, 3_825, 3_825, 3_825 },
                advances(profile, SegmentKind.FM_VOICE_UPLOAD, BLUE_SPHERE));
        assertArrayEquals(new long[] { 8_055 },
                advances(profile, SegmentKind.KEY_OFF, BLUE_SPHERE));
        assertArrayEquals(new long[] { 30_630, 2_700, 2_880 },
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
        Clock clock = checkedClock(root);

        for (JsonNode path : root.path("executed_paths")) {
            assertEquals(false, path.path("id").asText().isBlank());
            assertEquals(false, path.path("owner").asText().isBlank());
            assertEquals(false, path.path("source").asText().isBlank());
            assertEquals(false, path.path("rows").isEmpty());
            sumPrimitiveRows(path.path("rows"));
        }

        for (JsonNode segmentNode : root.path("segments")) {
            SegmentKind kind = SegmentKind.valueOf(segmentNode.path("kind").asText());
            Variant variant = variant(segmentNode.path("variant"));
            sumPath(root, segmentNode.path("source_prefix_path").asText());
            long[] derived = derivedAdvances(root, clock,
                    segmentNode.path("writes"));
            derived[0] = Math.addExact(derived[0],
                    crossSegmentAdvance(root, clock, segmentNode));
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
        assertEquals(10_106L,
                relative[33] / clock.masterCyclesPerZ80TState());
    }

    @Test
    void calculationFreezesEveryLocalCumulativeVectorAndCrossAdvance()
            throws IOException {
        JsonNode root = MAPPER.readTree(CALCULATION.toFile());
        Clock clock = checkedClock(root);

        assertArrayEquals(new long[] { 0, 3570, 6720, 9870, 13020 },
                cumulative(derivedAdvances(root, clock,
                        findSegment(root, "admission-prep").path("writes"))));
        assertArrayEquals(new long[] { 0, 3150, 6300, 9450 },
                cumulative(derivedAdvances(root, clock,
                        findSegment(root, "blue-sphere-max-release")
                                .path("writes"))));
        assertArrayEquals(new long[] {
                0, 3225, 6990, 10560, 14130, 17700, 21270, 24840,
                28410, 31980, 35550, 39120, 42690, 46260, 49830,
                53400, 56970, 60540, 64110, 67680, 71250, 74820,
                79965, 83790, 87615, 91440 },
                cumulative(derivedAdvances(root, clock,
                        findSegment(root, "blue-sphere-voice-upload")
                                .path("writes"))));
        assertArrayEquals(new long[] { 0 }, cumulative(derivedAdvances(
                root, clock, findSegment(root, "blue-sphere-key-off")
                        .path("writes"))));
        assertArrayEquals(new long[] { 0, 2700, 5580 }, cumulative(
                derivedAdvances(root, clock,
                        findSegment(root, "blue-sphere-frequency-key-on")
                                .path("writes"))));
        assertArrayEquals(new long[] {
                0, 16170, 19395, 23160, 26730, 30300, 33870, 37440,
                41010, 44580, 48150, 51720, 55290, 58860, 62430,
                66000, 69570, 73140, 76710, 80280, 83850, 87420,
                90990, 96135, 99675, 103215, 107040 }, cumulative(
                derivedAdvances(root, clock,
                        findSegment(root, "blue-sphere-completion-restore")
                                .path("writes"))));

        assertEquals(0, crossSegmentAdvance(root, clock,
                findSegment(root, "blue-sphere-max-release")));
        assertEquals(6435, crossSegmentAdvance(root, clock,
                findSegment(root, "blue-sphere-voice-upload")));
        assertEquals(8055, crossSegmentAdvance(root, clock,
                findSegment(root, "blue-sphere-key-off")));
        assertEquals(30630, crossSegmentAdvance(root, clock,
                findSegment(root, "blue-sphere-frequency-key-on")));
        assertEquals(0, crossSegmentAdvance(root, clock,
                findSegment(root, "blue-sphere-completion-restore")));
    }

    @Test
    void calculationRejectsHeaderDriftAggregateRowsAndArbitraryWaitTotals()
            throws IOException {
        JsonNode root = MAPPER.readTree(CALCULATION.toFile());
        JsonNode clock = root.path("clock");
        assertEquals(15, clock.path("master_cycles_per_z80_t_state").asInt());
        assertEquals(1008,
                clock.path("master_cycles_per_internal_sample").asInt());
        assertEquals(3,
                clock.path("gpgx_average_banked_read_wait_t_states").asInt());

        JsonNode drifted = root.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) drifted.path("clock"))
                .put("master_cycles_per_z80_t_state", 16);
        assertThrows(AssertionError.class, () -> checkedClock(drifted));

        JsonNode aggregate = MAPPER.readTree("""
                [{"opcode":"combined path subtotal","count":1,"t_states":210}]
                """);
        assertThrows(AssertionError.class,
                () -> sumPrimitiveRows(aggregate));

        JsonNode poisonedOpcodeTime = MAPPER.readTree("""
                [{"opcode":"NOP","count":1,"t_states":210}]
                """);
        assertThrows(AssertionError.class,
                () -> sumPrimitiveRows(poisonedOpcodeTime));

        for (JsonNode segment : root.path("segments")) {
            for (JsonNode write : segment.path("writes")) {
                for (JsonNode wait : write.path("bank_waits")) {
                    assertEquals(false, wait.has("average_t_states"));
                }
            }
        }
    }

    @Test
    void sourceRowsAreSelfContainedForCleanCheckoutAndRejectEqualCostSwap()
            throws IOException, NoSuchAlgorithmException {
        JsonNode root = MAPPER.readTree(CALCULATION.toFile());
        JsonNode voicePath = findExecutedPath(root,
                "voice.max-release-to-panning-data");

        assertEquals(3, opcodeCount(voicePath, "CALL nn"));
        assertEquals(2, opcodeCount(voicePath, "JR cc taken"));
        assertEquals(3, opcodeCount(voicePath, "RET cc not taken"));

        JsonNode citationSchema = root.path("source").path("citation_schema");
        assertEquals("Z80 Sound Driver.asm:first[-last]",
                citationSchema.path("format").asText());
        int minimumLine = citationSchema.path("minimum_line").asInt();
        int maximumLine = citationSchema.path("maximum_line").asInt();
        assertEquals(1, minimumLine);
        assertEquals(3506, maximumLine);

        for (JsonNode path : root.path("executed_paths")) {
            for (JsonNode row : path.path("rows")) {
                Matcher citation = SOURCE_ROW.matcher(
                        row.path("source").asText());
                assertEquals(true, citation.matches(),
                        path.path("id").asText() + " source row");
                int firstLine = Integer.parseInt(citation.group(1));
                int lastLine = citation.group(2) == null
                        ? firstLine : Integer.parseInt(citation.group(2));
                assertEquals(true, firstLine >= minimumLine
                                && lastLine >= firstLine
                                && lastLine <= maximumLine,
                        row.path("source").asText());
            }
        }
        assertEquals(SOURCE_ROW_DIGEST, sourceRowDigest(root));

        JsonNode poisoned = root.deepCopy();
        JsonNode poisonedVoice = findExecutedPath(poisoned,
                "voice.max-release-to-panning-data");
        long expectedTStates = sumPrimitiveRows(poisonedVoice.path("rows"));
        setOpcodeCount(poisonedVoice, "CALL nn", 4);
        setOpcodeCount(poisonedVoice, "JR cc taken", 1);
        setOpcodeCount(poisonedVoice, "RET cc not taken", 2);
        assertEquals(expectedTStates,
                sumPrimitiveRows(poisonedVoice.path("rows")),
                "poison must preserve the arithmetic subtotal");
        assertThrows(AssertionError.class,
                () -> checkedSourceRows(poisoned));
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
        assertArrayEquals(new long[] { 15 },
                new Segment(SegmentKind.KEY_OFF, variant,
                        new long[] { 15 })
                        .advanceBeforeWriteMasterCycles());
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

    private static long[] derivedAdvances(
            JsonNode root, Clock clock, JsonNode writes) {
        long[] advances = new long[writes.size()];
        for (int index = 0; index < advances.length; index++) {
            JsonNode write = writes.get(index);
            assertEquals(index, write.path("slot").asInt());
            String pathId = write.path("advance_before_write_path").asText();
            if (index == 0) {
                assertEquals("", pathId, "slot-zero path");
                assertEquals(0, write.path("bank_waits").size(),
                        "slot-zero bank waits");
            } else {
                assertEquals(false, pathId.isBlank(),
                        "later write requires an executed path");
            }
            long tStates = pathId.isBlank() ? 0 : sumPath(root, pathId);
            tStates = Math.addExact(tStates,
                    sumBankWaits(clock, write.path("bank_waits")));
            advances[index] = Math.multiplyExact(tStates,
                    clock.masterCyclesPerZ80TState());
            assertEquals(write.path("expected_delta_master_cycles").asLong(),
                    advances[index]);
        }
        return advances;
    }

    private static long sumPrimitiveRows(JsonNode steps) {
        long tStates = 0;
        for (JsonNode step : steps) {
            String opcode = step.path("opcode").asText();
            Integer authoritative = AUTHORITATIVE_T_STATES.get(opcode);
            if (authoritative == null) {
                throw new AssertionError("Unrecognized aggregate/opcode label: "
                        + opcode);
            }
            assertEquals(authoritative.intValue(),
                    step.path("t_states").asInt(), opcode);
            if (step.path("count").asLong() <= 0) {
                throw new AssertionError("Instruction count must be positive");
            }
            tStates = Math.addExact(tStates, Math.multiplyExact(
                    step.path("count").asLong(),
                    authoritative.longValue()));
        }
        return tStates;
    }

    private static long sumPath(JsonNode root, String pathId) {
        return sumPrimitiveRows(findExecutedPath(root, pathId).path("rows"));
    }

    private static JsonNode findExecutedPath(JsonNode root, String pathId) {
        for (JsonNode path : root.path("executed_paths")) {
            if (pathId.equals(path.path("id").asText())) {
                return path;
            }
        }
        throw new AssertionError("Missing executed path " + pathId);
    }

    private static int opcodeCount(JsonNode path, String opcode) {
        for (JsonNode row : path.path("rows")) {
            if (opcode.equals(row.path("opcode").asText())) {
                return row.path("count").asInt();
            }
        }
        throw new AssertionError("Missing opcode " + opcode + " in "
                + path.path("id").asText());
    }

    private static void setOpcodeCount(
            JsonNode path, String opcode, int count) {
        for (JsonNode row : path.path("rows")) {
            if (opcode.equals(row.path("opcode").asText())) {
                ((com.fasterxml.jackson.databind.node.ObjectNode) row)
                        .put("count", count);
                return;
            }
        }
        throw new AssertionError("Missing opcode " + opcode + " in "
                + path.path("id").asText());
    }

    private static void checkedSourceRows(JsonNode root)
            throws NoSuchAlgorithmException {
        assertEquals(SOURCE_ROW_DIGEST, sourceRowDigest(root));
    }

    private static String sourceRowDigest(JsonNode root)
            throws NoSuchAlgorithmException {
        StringBuilder canonical = new StringBuilder();
        for (JsonNode path : root.path("executed_paths")) {
            canonical.append(path.path("id").asText()).append('\n');
            for (JsonNode row : path.path("rows")) {
                canonical.append(row.path("source").asText()).append('|')
                        .append(row.path("opcode").asText()).append('|')
                        .append(row.path("count").asLong()).append('|')
                        .append(row.path("t_states").asLong()).append('\n');
            }
        }
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                canonical.toString().getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }

    private static Clock checkedClock(JsonNode root) {
        JsonNode clock = root.path("clock");
        assertEquals(15,
                clock.path("master_cycles_per_z80_t_state").asInt());
        assertEquals(1008,
                clock.path("master_cycles_per_internal_sample").asInt());
        assertEquals(3,
                clock.path("gpgx_average_banked_read_wait_t_states").asInt());
        return new Clock(
                clock.path("master_cycles_per_z80_t_state").asLong(),
                clock.path("master_cycles_per_internal_sample").asLong(),
                clock.path("gpgx_average_banked_read_wait_t_states").asLong());
    }

    private static long[] cumulative(long[] advances) {
        long[] result = new long[advances.length];
        long cursor = 0;
        for (int index = 0; index < advances.length; index++) {
            cursor = Math.addExact(cursor, advances[index]);
            result[index] = cursor;
        }
        return result;
    }

    private static long crossSegmentAdvance(
            JsonNode root, Clock clock, JsonNode segment) {
        String pathId = segment.path("cross_segment_advance_path").asText();
        long tStates = pathId.isBlank() ? 0 : sumPath(root, pathId);
        tStates = Math.addExact(tStates, sumBankWaits(clock,
                segment.path("cross_segment_bank_waits")));
        return Math.multiplyExact(tStates,
                clock.masterCyclesPerZ80TState());
    }

    private static long sumBankWaits(Clock clock, JsonNode waits) {
        long tStates = 0;
        for (JsonNode wait : waits) {
            assertEquals("GPGX z80_request_68k_bus_access average wait",
                    wait.path("owner").asText());
            assertEquals(false, wait.has("average_t_states"));
            if (wait.path("accesses").asLong() <= 0) {
                throw new AssertionError("Bank-wait access count must be positive");
            }
            tStates = Math.addExact(tStates, Math.multiplyExact(
                    wait.path("accesses").asLong(),
                    clock.averageBankWaitTStates()));
        }
        return tStates;
    }

    private static long[] composeAuditedFirstAttack(JsonNode root) {
        List<Long> relative = new ArrayList<>();
        long cursor = 0;
        Clock clock = checkedClock(root);
        String[] keys = {
                "blue-sphere-max-release", "blue-sphere-voice-upload",
                "blue-sphere-key-off", "blue-sphere-frequency-key-on" };
        for (String key : keys) {
            JsonNode segment = findSegment(root, key);
            cursor = Math.addExact(cursor,
                    crossSegmentAdvance(root, clock, segment));
            long[] advances = derivedAdvances(root, clock,
                    segment.path("writes"));
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

    private record Clock(long masterCyclesPerZ80TState,
                         long masterCyclesPerInternalSample,
                         long averageBankWaitTStates) {
    }
}
