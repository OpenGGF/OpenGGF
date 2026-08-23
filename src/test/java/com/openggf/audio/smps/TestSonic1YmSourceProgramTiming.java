package com.openggf.audio.smps;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.game.sonic1.audio.Sonic1YmServiceTimingProfile;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Executable authority for the two authenticated S1 FM5 first-attack paths. */
class TestSonic1YmSourceProgramTiming {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Path RESEARCH = Path.of("docs/architecture/research/audio");
    private static final Path PROGRAM = RESEARCH.resolve(
            "s1-fm5-ym-busy-write-program-v1.json");

    @Test
    void checkedProgramConsumesBothAuthenticatedInstructionLedgers() throws IOException {
        JsonNode root = JSON.readTree(Files.readAllBytes(PROGRAM));
        assertEquals("openggf.s1-ym-busy-program.v1", root.path("schema").asText());
        assertEquals(7, root.path("clock").path("master_cycles_per_m68k_cycle").asInt());
        assertEquals(42, root.path("clock").path("master_cycles_per_ym_clock").asInt());
        assertEquals(32, root.path("clock").path("busy_ym_clocks_after_data_write").asInt());
        assertEquals(14, root.path("clock").path("m68k_refresh_delay_master_cycles").asInt());

        assertEquals(2, root.path("programs").size());
        assertProgram(root.path("programs").get(0), "VOICE_NOTE", 30, 0);
        assertProgram(root.path("programs").get(1), "VOICE_PAN_NOTE", 31, 1);
    }

    @Test
    void productionProfileIsByteForByteTheCheckedSourceProgram() throws IOException {
        JsonNode programs = JSON.readTree(Files.readAllBytes(PROGRAM)).path("programs");
        for (JsonNode checked : programs) {
            YmSourceProgramTiming.FirstPathShape shape =
                    YmSourceProgramTiming.FirstPathShape.valueOf(
                            checked.path("shape").asText());
            YmSourceProgramTiming.SourceProgram production =
                    Sonic1YmServiceTimingProfile.PROFILE.requireProgram(shape, 0b1010);
            assertEquals(checked.path("writes").size(), production.writes().size());
            for (int index = 0; index < production.writes().size(); index++) {
                JsonNode expected = checked.path("writes").get(index);
                YmSourceProgramTiming.ProgramWrite actual = production.writes().get(index);
                assertEquals(YmServiceTimingProfile.SegmentKind.valueOf(
                        expected.path("section").asText()), actual.section());
                assertEquals(expected.path("port").asInt(), actual.expectedPort());
                assertEquals(expected.path("register").asInt(), actual.expectedRegister());
                assertEquals(expected.path("fixed_cycles_before_first_status_read").asLong(),
                        actual.fixedCyclesBeforeFirstStatusRead());
                assertEquals(expected.path("status_read_cycles").asLong(),
                        actual.statusReadCycles());
                assertEquals(expected.path("taken_busy_loop_cycles").asLong(),
                        actual.takenBusyLoopCycles());
                assertEquals(expected.path("cycles_after_ready_status_to_data_write").asLong(),
                        actual.cyclesAfterReadyStatusToDataWrite());
            }
            assertEquals(Integer.valueOf(0x05), production.writes().stream()
                    .filter(write -> write.section()
                            == YmServiceTimingProfile.SegmentKind.KEY_OFF)
                    .findFirst().orElseThrow().expectedFixedValue());
            assertEquals(Integer.valueOf(0xF5), production.writes().get(
                    production.writes().size() - 1).expectedFixedValue());
        }
    }

    @Test
    void sourceProgramsAreImmutableDenseAndBounded() {
        var variant = new YmSourceProgramTiming.ProgramVariant(
                1, 0b1110, YmSourceProgramTiming.FirstPathShape.VOICE_NOTE);
        var source = new YmSourceProgramTiming.SourcePath("SetVoice@source:1-2");
        var writes = new java.util.ArrayList<>(List.of(
                new YmSourceProgramTiming.ProgramWrite(
                        YmServiceTimingProfile.SegmentKind.FM_VOICE_UPLOAD,
                        1, 0xB1, null, 0, 0, 0, 0, source),
                new YmSourceProgramTiming.ProgramWrite(
                        YmServiceTimingProfile.SegmentKind.KEY_OFF,
                        0, 0x28, 0x05, 100, 119, 259, 500, source)));
        var sections = new java.util.ArrayList<>(List.of(
                new YmSourceProgramTiming.ProgramSection(
                        YmServiceTimingProfile.SegmentKind.FM_VOICE_UPLOAD, 0, 1),
                new YmSourceProgramTiming.ProgramSection(
                        YmServiceTimingProfile.SegmentKind.KEY_OFF, 1, 1)));
        var program = new YmSourceProgramTiming.SourceProgram(
                YmSourceProgramTiming.ProgramKind.S1_FM5_FIRST_VOICE_ATTACK,
                variant, writes, sections);
        writes.clear();
        sections.clear();
        assertEquals(2, program.writes().size());
        assertEquals(2, program.sections().size());
        assertNotSame(writes, program.writes());
        assertThrows(UnsupportedOperationException.class,
                () -> program.writes().clear());

        assertThrows(IllegalArgumentException.class, () ->
                new YmSourceProgramTiming.ProgramSection(
                        YmServiceTimingProfile.SegmentKind.KEY_OFF, -1, 1));
        assertThrows(IllegalArgumentException.class, () ->
                new YmSourceProgramTiming.ProgramWrite(
                        YmServiceTimingProfile.SegmentKind.KEY_OFF,
                        0, 0x28, 0x05, -1, 1, 1, 1, source));
        assertThrows(IllegalArgumentException.class, () ->
                new YmSourceProgramTiming.ProgramVariant(
                        2, 0, YmSourceProgramTiming.FirstPathShape.VOICE_NOTE));
    }

    @Test
    void resolverAnchorsOnlyRowZeroAndCarriesBusyAcrossSections() {
        var source = new YmSourceProgramTiming.SourcePath("WriteFM@s1:1-2");
        var variant = new YmSourceProgramTiming.ProgramVariant(
                1, 0b1110, YmSourceProgramTiming.FirstPathShape.VOICE_PAN_NOTE);
        var program = new YmSourceProgramTiming.SourceProgram(
                YmSourceProgramTiming.ProgramKind.S1_FM5_FIRST_VOICE_ATTACK,
                variant,
                List.of(
                        new YmSourceProgramTiming.ProgramWrite(
                                YmServiceTimingProfile.SegmentKind.FM_VOICE_UPLOAD,
                                1, 0xB1, null, 0, 0, 0, 0, source),
                        new YmSourceProgramTiming.ProgramWrite(
                                YmServiceTimingProfile.SegmentKind.TRACK_PAN_WRITE,
                                1, 0xB1, null, 100, 119, 259, 500, source),
                        new YmSourceProgramTiming.ProgramWrite(
                                YmServiceTimingProfile.SegmentKind.KEY_OFF,
                                0, 0x28, 0x05, 100, 119, 259, 500, source)),
                List.of(
                        new YmSourceProgramTiming.ProgramSection(
                                YmServiceTimingProfile.SegmentKind.FM_VOICE_UPLOAD, 0, 1),
                        new YmSourceProgramTiming.ProgramSection(
                                YmServiceTimingProfile.SegmentKind.TRACK_PAN_WRITE, 1, 1),
                        new YmSourceProgramTiming.ProgramSection(
                                YmServiceTimingProfile.SegmentKind.KEY_OFF, 2, 1)));
        var state = YmSourceProgramTiming.ProgramState.initial();
        var first = YmSourceProgramTiming.YmSourceProgramResolver.resolveNext(
                program, state, YmServiceTimingProfile.SegmentKind.FM_VOICE_UPLOAD,
                1, 0xB1, 0x29, 7_000, 6_500);
        assertEquals(7_000, first.dueMasterCycle());
        assertEquals(7_000, first.nextState().lastDueMasterCycle());
        assertEquals(8_358, first.nextState().busyUntilMasterCycle());
        var pan = YmSourceProgramTiming.YmSourceProgramResolver.resolveNext(
                program, first.nextState(),
                YmServiceTimingProfile.SegmentKind.TRACK_PAN_WRITE,
                1, 0xB1, 0xC0, 7_000, 6_500);
        assertEquals(8_895, pan.dueMasterCycle());
        var keyOff = YmSourceProgramTiming.YmSourceProgramResolver.resolveNext(
                program, pan.nextState(), YmServiceTimingProfile.SegmentKind.KEY_OFF,
                0, 0x28, 0x05, 7_000, 6_500);
        assertEquals(10_790, keyOff.dueMasterCycle());
        assertTrue(keyOff.nextState().complete(program));

        assertThrows(IllegalArgumentException.class, () ->
                YmSourceProgramTiming.YmSourceProgramResolver.resolveNext(
                        program, first.nextState(), YmServiceTimingProfile.SegmentKind.KEY_OFF,
                        0, 0x28, 0x05, 7_000, 6_500));
        assertThrows(IllegalArgumentException.class, () ->
                YmSourceProgramTiming.YmSourceProgramResolver.resolveNext(
                        program, first.nextState(),
                        YmServiceTimingProfile.SegmentKind.TRACK_PAN_WRITE,
                        0, 0xB1, 0xC0, 7_000, 6_500));
        assertThrows(IllegalArgumentException.class, () ->
                YmSourceProgramTiming.YmSourceProgramResolver.resolveNext(
                        program, pan.nextState(), YmServiceTimingProfile.SegmentKind.KEY_OFF,
                        0, 0x28, 0x04, 7_000, 6_500));
    }

    @Test
    void resolverUsesSourceCostsAndDiscreteBusyForEveryYmClockResidue() {
        var source = new YmSourceProgramTiming.SourcePath("WriteFMII@s1:1-2");
        var program = new YmSourceProgramTiming.SourceProgram(
                YmSourceProgramTiming.ProgramKind.S1_FM5_FIRST_VOICE_ATTACK,
                new YmSourceProgramTiming.ProgramVariant(
                        1, 0b1010, YmSourceProgramTiming.FirstPathShape.VOICE_NOTE),
                List.of(
                        new YmSourceProgramTiming.ProgramWrite(
                                YmServiceTimingProfile.SegmentKind.FM_VOICE_UPLOAD,
                                1, 0xB1, null, 0, 0, 0, 0, source),
                        new YmSourceProgramTiming.ProgramWrite(
                                YmServiceTimingProfile.SegmentKind.FM_VOICE_UPLOAD,
                                1, 0x31, null, 924, 119, 259, 700, source)),
                List.of(new YmSourceProgramTiming.ProgramSection(
                        YmServiceTimingProfile.SegmentKind.FM_VOICE_UPLOAD, 0, 2)));
        for (long residue = 0; residue < 42; residue++) {
            long cursor = 84_000 + residue;
            var row0 = YmSourceProgramTiming.YmSourceProgramResolver.resolveNext(
                    program, YmSourceProgramTiming.ProgramState.initial(),
                    YmServiceTimingProfile.SegmentKind.FM_VOICE_UPLOAD,
                    1, 0xB1, 0x29, cursor, cursor);
            long busyUntil = Math.multiplyExact(
                    Math.addExact(Math.floorDiv(cursor, 42)
                            + (cursor % 42 == 0 ? 0 : 1), 32), 42);
            long status = cursor + 924;
            while (status < busyUntil) status += 259;
            long expected = status + 700;
            var row1 = YmSourceProgramTiming.YmSourceProgramResolver.resolveNext(
                    program, row0.nextState(),
                    YmServiceTimingProfile.SegmentKind.FM_VOICE_UPLOAD,
                    1, 0x31, 0x71, cursor, cursor);
            assertEquals(expected, row1.dueMasterCycle(), "residue=" + residue);
        }
    }

    @Test
    void firstPathClassifierIsSideEffectFreeAndRejectsUnauditedControlFlow() {
        assertEquals(YmSourceProgramTiming.FirstPathShape.VOICE_NOTE,
                SmpsSequencer.classifyFirstFmPath(new BytesView(0x90, 0x04), 0));
        assertEquals(YmSourceProgramTiming.FirstPathShape.VOICE_PAN_NOTE,
                SmpsSequencer.classifyFirstFmPath(
                        new BytesView(0xE0, 0x40, 0x90, 0x04), 0));
        assertEquals(null, SmpsSequencer.classifyFirstFmPath(
                new BytesView(0x80, 0x04, 0x90, 0x04), 0));
        for (int command : new int[] {0xE3, 0xE6, 0xE7, 0xE8, 0xE9,
                0xEF, 0xF2, 0xF6, 0xF7, 0xF8}) {
            assertEquals(null, SmpsSequencer.classifyFirstFmPath(
                    new BytesView(command, 0x00, 0x90, 0x04), 0));
        }
        assertEquals(null, SmpsSequencer.classifyFirstFmPath(
                new BytesView(0xE0), 0));
        assertEquals(null, SmpsSequencer.classifyFirstFmPath(
                new BytesView(0xE0, 0x40, 0xE0, 0x40, 0x90), 0));
    }

    private static void assertProgram(JsonNode program, String shape, int writes,
                                      int panWrites) throws IOException {
        assertEquals(shape, program.path("shape").asText());
        assertEquals(writes, program.path("writes").size());
        assertEquals(26, program.path("sections").path("FM_VOICE_UPLOAD").asInt());
        assertEquals(panWrites, program.path("sections").path("TRACK_PAN_WRITE").asInt());
        assertEquals(1, program.path("sections").path("KEY_OFF").asInt());
        assertEquals(3, program.path("sections").path("FREQUENCY_AND_KEY_ON").asInt());

        JsonNode authority = program.path("authority");
        Path ledger = RESEARCH.resolve(authority.path("ledger_path").asText());
        assertEquals(authority.path("ledger_sha256").asText(), sha256(Files.readAllBytes(ledger)));
        assertEquals(authority.path("ledger_row_count").asInt(),
                Files.readAllLines(ledger).stream()
                        .filter(line -> !line.isBlank() && !line.startsWith("#"))
                        .skip(1).count());
        if (shape.equals("VOICE_NOTE")) {
            assertEquals(1, authority.path("group_ordinal").asInt());
            assertTrue(authority.path("selection").asText().contains("lowest"));
        } else {
            assertEquals(0, authority.path("group_ordinal").asInt());
        }
        assertNativeGroupProjection(authority);

        int zeroAnchors = 0;
        long previousCaptured = 0;
        Set<Integer> consumed = new HashSet<>();
        for (int index = 0; index < program.path("writes").size(); index++) {
            JsonNode write = program.path("writes").get(index);
            assertEquals(index, write.path("write_ordinal").asInt());
            if (write.path("row_zero_anchor").asBoolean()) {
                zeroAnchors++;
                assertEquals(0, index);
            }
            long captured = write.path("captured_relative_master_cycle").asLong();
            assertFalse(write.has("advance_before_write_master_cycles"));
            assertTrue(write.path("taken_busy_loop_cycles").isInt());
            if (index > 0) assertEquals(259,
                    write.path("taken_busy_loop_cycles").asInt());
            previousCaptured = captured;
            assertTrue(write.path("register").asInt() >= 0);
            assertTrue(write.path("register").asInt() <= 0xff);
            assertTrue(write.path("source_occurrence_first").asInt() >= 0 || index == 0);
            for (JsonNode occurrence : write.path("source_occurrences")) {
                assertTrue(consumed.add(occurrence.asInt()), "instruction occurrence reused");
            }
            assertFalse(write.path("source").asText().isBlank());
        }
        assertEquals(1, zeroAnchors);
        assertEquals(authority.path("ledger_row_count").asInt(), consumed.size());
        assertSourceCosts(program, ledger);
    }

    private static void assertNativeGroupProjection(JsonNode authority) throws IOException {
        JsonNode oracle = JSON.readTree(Files.readAllBytes(RESEARCH.resolve(
                "s1-ring-ym-write-audit-v2.json")));
        JsonNode group = oracle.path("groups").get(authority.path("group_ordinal").asInt());
        Map<String, Object> projection = new TreeMap<>();
        projection.put("classification", group.path("classification").asText());
        projection.put("frame", group.path("frame").asInt());
        projection.put("group_ordinal", group.path("group_ordinal").asInt());
        List<Map<String, Object>> writes = new ArrayList<>();
        for (JsonNode write : group.path("writes")) {
            Map<String, Object> projected = new TreeMap<>();
            for (String field : List.of("dma_stall_count", "internal_ordinal",
                    "master_cycle", "port", "register", "relative_master_cycle",
                    "source_ordinal", "value")) {
                projected.put(field, write.path(field).asLong());
            }
            writes.add(projected);
        }
        projection.put("writes", writes);
        String digest = sha256(JSON.writeValueAsBytes(projection));
        assertEquals(authority.path("native_group_projection_sha256").asText(), digest);
    }

    private static void assertSourceCosts(JsonNode program, Path ledger) throws IOException {
        List<SourceRow> rows = readSourceRows(ledger);
        JsonNode writes = program.path("writes");
        JsonNode oracle = JSON.readTree(Files.readAllBytes(RESEARCH.resolve(
                "s1-ring-ym-write-audit-v2.json")));
        JsonNode nativeWrites = oracle.path("groups")
                .get(program.path("authority").path("group_ordinal").asInt())
                .path("writes");
        for (int ordinal = 0; ordinal < writes.size(); ordinal++) {
            JsonNode expected = writes.get(ordinal);
            int after = ordinal - 1;
            List<SourceRow> gap = rows.stream()
                    .filter(row -> row.afterSourceOrdinal() == after).toList();
            assertFalse(gap.isEmpty());
            long virtual = 0;
            if (ordinal > 0) {
                SourceRow priorWrite = rows.stream()
                        .filter(row -> row.afterSourceOrdinal() == after - 1
                                && row.roles().contains("ym_write"))
                        .findFirst().orElseThrow();
                long refresh = gap.get(0).refreshDelay() - priorWrite.refreshDelay();
                assertTrue(refresh >= 0 && refresh % 14 == 0);
                virtual = gap.get(0).startMasterCycle()
                        - nativeWrites.get(ordinal - 1).path("master_cycle").asLong()
                        - refresh;
            }
            List<Long> starts = new ArrayList<>();
            for (int index = 0; index < gap.size(); index++) {
                starts.add(virtual);
                if (index + 1 < gap.size()) {
                    long refresh = gap.get(index + 1).refreshDelay()
                            - gap.get(index).refreshDelay();
                    assertTrue(refresh >= 0 && refresh % 14 == 0);
                    virtual += gap.get(index).deltaToNextStart() - refresh;
                }
            }
            int writeIndex = onlyIndex(gap, "ym_write");
            int busyPc = expected.path("port").asInt() == 0 ? 0x7272e : 0x72764;
            List<Integer> busy = new ArrayList<>();
            for (int index = 0; index < gap.size(); index++) {
                if (gap.get(index).pc() == busyPc) busy.add(index);
            }
            long fixed = busy.isEmpty() ? 0 : starts.get(busy.get(0));
            long status = busy.isEmpty() ? 0
                    : sourceDelta(gap.get(busy.get(0)), gap.get(busy.get(0) + 1));
            long loop = busy.size() < 2 ? 259
                    : starts.get(busy.get(1)) - starts.get(busy.get(0));
            for (int index = 1; index < busy.size(); index++) {
                assertEquals(loop, starts.get(busy.get(index))
                        - starts.get(busy.get(index - 1)));
            }
            long afterReady = busy.isEmpty() ? 0
                    : starts.get(writeIndex) + 7 - starts.get(busy.get(busy.size() - 1));
            if (ordinal == 0) fixed = status = loop = afterReady = 0;
            assertEquals(fixed, expected.path("fixed_cycles_before_first_status_read").asLong());
            assertEquals(status, expected.path("status_read_cycles").asLong());
            assertEquals(loop, expected.path("taken_busy_loop_cycles").asLong());
            assertEquals(afterReady,
                    expected.path("cycles_after_ready_status_to_data_write").asLong());
        }
    }

    private static int onlyIndex(List<SourceRow> rows, String role) {
        int result = -1;
        for (int index = 0; index < rows.size(); index++) {
            if (rows.get(index).roles().contains(role)) {
                assertEquals(-1, result);
                result = index;
            }
        }
        assertTrue(result >= 0);
        return result;
    }

    private static long sourceDelta(SourceRow row, SourceRow next) {
        return row.deltaToNextStart() - (next.refreshDelay() - row.refreshDelay());
    }

    private static List<SourceRow> readSourceRows(Path ledger) throws IOException {
        List<SourceRow> result = new ArrayList<>();
        List<String> lines = Files.readAllLines(ledger);
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] part = line.split("\t", -1);
            assertEquals(14, part.length);
            result.add(new SourceRow(Integer.parseInt(part[2]),
                    Integer.parseUnsignedInt(part[4].substring(2), 16),
                    Long.parseLong(part[6]), Long.parseLong(part[7]),
                    part[9].equals("key_on") ? -1 : Long.parseLong(part[9]), part[12]));
        }
        return List.copyOf(result);
    }

    private record SourceRow(int afterSourceOrdinal, int pc, long startMasterCycle,
                             long refreshDelay, long deltaToNextStart, String roles) {
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }

    private record BytesView(byte[] bytes) implements SmpsProgramView {
        private BytesView(int... values) {
            this(toBytes(values));
        }

        @Override public int dataLength() { return bytes.length; }
        @Override public byte dataByteAt(int index) { return bytes[index]; }
        @Override public int fmPointerCount() { return 0; }
        @Override public int fmPointerAt(int index) { throw new IndexOutOfBoundsException(); }
        @Override public int fmKeyOffsetAt(int index) { throw new IndexOutOfBoundsException(); }
        @Override public int fmVolumeOffsetAt(int index) { throw new IndexOutOfBoundsException(); }
        @Override public int psgPointerCount() { return 0; }
        @Override public int psgPointerAt(int index) { throw new IndexOutOfBoundsException(); }
        @Override public int psgKeyOffsetAt(int index) { throw new IndexOutOfBoundsException(); }
        @Override public int psgVolumeOffsetAt(int index) { throw new IndexOutOfBoundsException(); }
        @Override public int psgModEnvelopeAt(int index) { throw new IndexOutOfBoundsException(); }
        @Override public int psgInstrumentCount() { return 0; }
        @Override public int psgInstrumentAt(int index) { throw new IndexOutOfBoundsException(); }
        @Override public int voiceLength(int voiceId) { return 0; }
        @Override public byte voiceByteAt(int voiceId, int index) { throw new IndexOutOfBoundsException(); }
        @Override public int psgEnvelopeLength(int envelopeId) { return 0; }
        @Override public byte psgEnvelopeByteAt(int envelopeId, int index) { throw new IndexOutOfBoundsException(); }
        @Override public int modEnvelopeLength(int envelopeId) { return 0; }
        @Override public byte modEnvelopeByteAt(int envelopeId, int index) { throw new IndexOutOfBoundsException(); }

        private static byte[] toBytes(int[] values) {
            byte[] result = new byte[values.length];
            for (int index = 0; index < values.length; index++) {
                result[index] = (byte) values[index];
            }
            return result;
        }
    }
}
