package com.openggf.audio.synth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Executable source/native proof for the S1/S2 ring timing audit. */
class TestS1S2YmWriteTimingAudit {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Path RESEARCH = Path.of("docs/architecture/research/audio");
    private static final Path SOURCE_MAP = RESEARCH.resolve("s1-s2-ym-write-source-map-v1.tsv");
    private static final String SOURCE_MAP_SHA256 =
            "96f514aa28a41038e6622f0237726cdbd0692301946ce974f58c9e789dfddd3c";
    private static final List<SourceRange> SOURCE_RANGES = readSourceMap();

    @Test
    void retainedNativeCounterfactualUsesEveryCapturedPreGroupContext() throws IOException {
        assertNativeCounterfactual(RESEARCH.resolve("s1-ring-ym-write-audit-v2.json"), "s1");
        assertNativeCounterfactual(RESEARCH.resolve("s2-ringright-ym-write-audit-v2.json"), "s2");
    }

    @Test
    void s1ProductionSourceModelImprovesEveryYmResidueAcrossAllNativeStates()
            throws IOException {
        JsonNode root = JSON.readTree(Files.readAllBytes(
                RESEARCH.resolve("s1-ring-ym-write-audit-v2.json")));
        assertEquals(sha256(Files.readAllBytes(RESEARCH.resolve(
                        "s1-fm5-ym-busy-write-program-v1.json"))),
                root.path("provenance").path("source_program_sha256").asText());
        assertEquals(38, root.path("groups").size());
        int atomicAggregate = 0;
        for (JsonNode group : root.path("groups")) {
            atomicAggregate += attenuationError(
                    ints(group.path("native_counterfactual")
                            .path("atomic_key_on_attenuation")),
                    ints(group.path("key_on_attenuation")));
        }
        assertEquals(11_764, atomicAggregate);

        List<Integer> residueAggregates = new ArrayList<>(42);
        List<String> regressions = new ArrayList<>();
        for (int residue = 0; residue < 42; residue++) {
            int sourceAggregate = 0;
            for (JsonNode group : root.path("groups")) {
                JsonNode matrix = group.path("native_counterfactual")
                        .path("source_model_by_cursor_residue");
                assertEquals(42, matrix.size(), "group "
                        + group.path("group_ordinal").asInt());
                sourceAggregate += attenuationError(ints(matrix.get(residue)),
                        ints(group.path("key_on_attenuation")));
            }
            residueAggregates.add(sourceAggregate);
            if (sourceAggregate > atomicAggregate) {
                regressions.add("residue " + residue + ": atomic="
                        + atomicAggregate + ", source=" + sourceAggregate);
            }
        }
        assertEquals(List.of(), regressions,
                "no runtime-reachable YM clock residue may regress the full "
                        + "38-state native attenuation aggregate");
        assertEquals(List.of(
                9221, 9221, 9221, 9221, 9221, 9221, 9221,
                9221, 9221, 9221, 9221, 9221, 9221, 9221,
                9188, 9188, 9188, 9188, 9188, 9188, 9188, 9188,
                9221, 9221, 9221, 9221, 9221, 9221, 9221,
                9221, 9221, 9221, 9221, 9221, 9221, 9221,
                9221, 9221, 9221, 9221, 9221, 9221), residueAggregates);
        int retainedAtomicAggregate = atomicAggregate;
        assertTrue(residueAggregates.stream().allMatch(
                value -> value < retainedAtomicAggregate));
        assertTrue(residueAggregates.stream().mapToLong(Integer::longValue).sum()
                        < (long) atomicAggregate * 42,
                "the complete 42-by-38 matrix must strictly improve");
    }

    @Test
    void everyGapIsAnExactOrderedJoinOfCapturedInstructionOccurrences() throws IOException {
        assertEquals(SOURCE_MAP_SHA256, sha256(Files.readAllBytes(SOURCE_MAP)));
        assertSourceMapIntegrity();
        assertInstructionJoin(
                "s1", "m68k", 2, 7,
                "s1-ring-ym-write-audit-v2.json",
                "s1-ring-ym-write-timing-calculation-v2.json",
                "a6d385bc17a9efb79ee687897c3577fdc9f3225bd8f4212022cc09fe7a5ccf7a",
                "6c519b99ae89803993233bd85f22c1b942021556ce2dbefab7c2eddb6e2a8751");
        assertInstructionJoin(
                "s2", "z80", 1, 15,
                "s2-ringright-ym-write-audit-v2.json",
                "s2-ringright-ym-write-timing-calculation-v2.json",
                "b8f632aab340f07e2ed863944f2cfd3d39badbe0410a23979ebb10dd81e86372",
                "598cad4a897bcb46d764d4bf334639c5f5f1007b923aa68e866a269402d4ffcb");
    }

    @Test
    void captureDigestRejectsDeletionPcOpcodeCountOrderAndFakePrimitive() throws IOException {
        Path ledger = RESEARCH.resolve("s2-ringright-ym-write-instruction-ledger-v1.tsv");
        String expected = "b8f632aab340f07e2ed863944f2cfd3d39badbe0410a23979ebb10dd81e86372";
        List<String> original = Files.readAllLines(ledger);
        List<List<String>> mutations = new ArrayList<>();
        mutations.add(without(original, 59));
        mutations.add(replaced(original, 59, "0xE46", "0xE47"));
        mutations.add(replaced(original, 59, "0x4E", "0x46"));
        mutations.add(replaced(original, 59, "58\t", "408\t"));
        List<String> reordered = new ArrayList<>(original);
        String row = reordered.get(59);
        reordered.set(59, reordered.get(60));
        reordered.set(60, row);
        mutations.add(reordered);
        mutations.add(replaced(original, 59, "ordinary", "fake_primitive"));
        mutations.add(replaced(original, 59, "@s2.sounddriver.asm:", "@wrong.asm:"));
        for (List<String> mutation : mutations) {
            byte[] bytes = (String.join("\n", mutation) + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
            assertThrows(AssertionError.class, () -> assertEquals(expected, sha256(bytes)));
        }

        List<String> s1 = Files.readAllLines(RESEARCH.resolve(
                "s1-ring-ym-write-instruction-ledger-v1.tsv"));
        int indexedJump = java.util.stream.IntStream.range(1, s1.size())
                .filter(i -> s1.get(i).contains("\t0x4EFB\t"))
                .findFirst().orElseThrow();
        List<String> wrongJump = replaced(s1, indexedJump, "\tjump\t", "\tlinear\t");
        assertThrows(AssertionError.class, () -> assertEquals(
                "a6d385bc17a9efb79ee687897c3577fdc9f3225bd8f4212022cc09fe7a5ccf7a",
                sha256((String.join("\n", wrongJump) + "\n").getBytes(
                        java.nio.charset.StandardCharsets.UTF_8))));
        LedgerRow authenticJump = readLedger(RESEARCH.resolve(
                "s1-ring-ym-write-instruction-ledger-v1.tsv")).stream()
                .filter(candidate -> candidate.opcode() == 0x4efb).findFirst().orElseThrow();
        assertThrows(AssertionError.class, () -> assertClassification("s1", "m68k",
                authenticJump.pc(), authenticJump.opcode(), "linear",
                authenticJump.roles(), authenticJump.source()));
        assertThrows(AssertionError.class, () -> assertClassification("s1", "m68k",
                authenticJump.pc(), authenticJump.opcode(), authenticJump.flow(),
                "ordinary", authenticJump.source()));
        assertThrows(AssertionError.class, () -> assertClassification("s1", "m68k",
                authenticJump.pc(), authenticJump.opcode(), authenticJump.flow(),
                authenticJump.roles(), "CoordFlag@wrong.asm:1-2"));
        assertThrows(AssertionError.class, () -> sourceFor("s1", 0x71ceb));
        assertThrows(AssertionError.class, () -> assertEquals(
                "coordflagLookup@s1.sounddriver.asm:2074-2076",
                sourceFor("s1", 0x72a60)));
    }

    @Test
    void canonicalSourceMapRejectsShiftedBoundaryWrongLabelAndWrongLine() throws IOException {
        List<String> original = Files.readAllLines(SOURCE_MAP);
        List<List<String>> mutations = List.of(
                replaced(original, 1, "0x71CEA", "0x71CEC"),
                replaced(original, 1, "FMUpdateTrack", "FinishTrackUpdate"),
                replaced(original, 1, "\t348\t362", "\t349\t362"));
        for (List<String> mutation : mutations) {
            byte[] bytes = (String.join("\n", mutation) + "\n").getBytes(
                    java.nio.charset.StandardCharsets.UTF_8);
            assertThrows(AssertionError.class,
                    () -> assertEquals(SOURCE_MAP_SHA256, sha256(bytes)));
        }
    }

    private static void assertNativeCounterfactual(Path path, String game)
            throws IOException {
        JsonNode root = JSON.readTree(Files.readAllBytes(path));
        assertEquals("openggf.s1-s2-ym-write-timing-audit.v2", root.path("schema").asText());
        assertEquals(game, root.path("game").asText());
        boolean isolated = false;
        boolean overlap = false;
        boolean materialIsolated = false;
        for (JsonNode group : root.path("groups")) {
            String classification = group.path("classification").asText();
            isolated |= classification.equals("isolated");
            overlap |= classification.equals("overlap");
            JsonNode counterfactual = group.path("native_counterfactual");
            byte[] context = Base64.getDecoder().decode(
                    counterfactual.path("pre_group_context_base64").asText());
            assertEquals(counterfactual.path("pre_group_context_size").asInt(), context.length);
            assertEquals(counterfactual.path("pre_group_context_sha256").asText(), sha256(context));
            assertEquals(4, counterfactual.path("atomic_key_on_attenuation").size());
            assertEquals(4, counterfactual.path("timed_key_on_attenuation").size());
            assertFalse(counterfactual.path("pre_group_context_sha256").asText().matches("0+"));
            if (classification.equals("isolated")
                    && group.path("relative_last_master_cycle").asLong() >= 4_032
                    && counterfactual.path("maximum_attenuation_difference").asInt() >= 8) {
                materialIsolated = true;
            }
        }
        assertTrue(isolated);
        assertTrue(overlap);
        assertEquals(materialIsolated, root.path("ruling").path("isolated_material").asBoolean());
    }

    private static void assertInstructionJoin(String game, String architecture,
                                               int cpu, long masterCyclesPerCpuCycle,
                                               String oracleName, String calculationName,
                                               String expectedLedgerSha,
                                               String expectedFullCaptureSha) throws IOException {
        JsonNode oracle = JSON.readTree(Files.readAllBytes(RESEARCH.resolve(oracleName)));
        JsonNode calculation = JSON.readTree(Files.readAllBytes(RESEARCH.resolve(calculationName)));
        assertEquals("openggf.s1-s2-ym-write-calculation.v2",
                calculation.path("schema").asText());
        assertEquals(game, calculation.path("game").asText());
        assertEquals(architecture, calculation.path("architecture").asText());
        assertEquals(SOURCE_MAP.getFileName().toString(),
                calculation.path("source").path("map_path").asText());
        assertEquals(SOURCE_MAP_SHA256,
                calculation.path("source").path("map_sha256").asText());
        assertEquals(masterCyclesPerCpuCycle,
                calculation.path("clock").path("master_cycles_per_cpu_cycle").asLong());
        assertEquals(expectedFullCaptureSha,
                oracle.path("provenance").path("native_instructions_sha256").asText());
        assertEquals(expectedFullCaptureSha,
                calculation.path("ledger").path("full_capture_sha256").asText());
        if (game.equals("s2")) {
            String scriptSha = sha256(Files.readAllBytes(Path.of(
                    "tools/bizhawk-headless/native/gpgx-audio-lab/capture-ym-write-timing.sh")));
            assertEquals("e187e2ca34f0c46a6213094d7a8059adad38136f6bca00e388e476c5eaa93f17",
                    scriptSha);
            assertEquals(scriptSha,
                    oracle.path("provenance").path("capture_script_sha256").asText());
            assertEquals("0x1D90",
                    oracle.path("source_authentication").path("owner_ix").asText());
        }

        Path ledgerPath = RESEARCH.resolve(calculation.path("ledger").path("path").asText());
        byte[] ledgerBytes = Files.readAllBytes(ledgerPath);
        assertEquals(expectedLedgerSha, sha256(ledgerBytes));
        assertEquals(expectedLedgerSha, calculation.path("ledger").path("sha256").asText());
        List<LedgerRow> rows = readLedger(ledgerPath);
        assertEquals(calculation.path("ledger").path("row_count").asInt(), rows.size());
        assertTrue(rows.stream().anyMatch(row -> row.afterSourceOrdinal() == -1));
        if (architecture.equals("m68k")) {
            assertTrue(rows.stream().anyMatch(row -> (row.opcode() & 0xffc0) == 0x4e80));
            assertTrue(rows.stream().anyMatch(row -> (row.opcode() & 0xffc0) == 0x4ec0));
            assertTrue(rows.stream().anyMatch(row -> row.opcode() == 0x4e75));
            assertTrue(rows.stream().anyMatch(row -> (row.opcode() >>> 8) >= 0x60
                    && (row.opcode() >>> 8) <= 0x6f));
            assertTrue(rows.stream().anyMatch(row -> (row.opcode() & 0xf0f8) == 0x50c8));
        }

        Map<Integer, List<LedgerRow>> byGap = new HashMap<>();
        for (int index = 0; index < rows.size(); index++) {
            LedgerRow row = rows.get(index);
            assertEquals(index, row.occurrenceOrdinal());
            assertEquals(cpu, row.cpu());
            assertClassification(game, architecture, row.pc(), row.opcode(), row.flow(),
                    row.roles(), row.source());
            if (index + 1 < rows.size()) {
                LedgerRow next = rows.get(index + 1);
                assertEquals(next.pcText(), row.nextPc());
                assertEquals(next.startMasterCycle() - row.startMasterCycle(), row.deltaToNextStart());
                assertTrue(row.deltaToNextStart() > 0);
                assertEquals(0, row.deltaToNextStart() % masterCyclesPerCpuCycle);
                if (isConditional(architecture, row.opcode())) {
                    assertEquals(next.pc() == sequentialPc(architecture, row.pc(), row.opcode())
                                    ? "not_taken" : "taken",
                            row.branchOutcome());
                } else if (!row.flow().equals("linear")) {
                    assertEquals("target=" + next.pcText(), row.branchOutcome());
                } else {
                    assertEquals("n/a", row.branchOutcome());
                }
            }
            if (row.roles().contains("bank_wait_3t")) {
                assertEquals("s2", game);
                assertEquals(10, row.deltaToNextStart() / masterCyclesPerCpuCycle,
                        "7T LD r,(HL) plus the exact GPGX 3T bank wait");
            }
            if (row.afterSourceOrdinal() >= 0) {
                byGap.computeIfAbsent(row.afterSourceOrdinal(), ignored -> new ArrayList<>()).add(row);
            }
        }

        JsonNode representative = oracle.path("groups").get(0);
        assertEquals("isolated", representative.path("classification").asText());
        JsonNode writes = representative.path("writes");
        JsonNode gaps = calculation.path("gaps");
        assertEquals(writes.size() - 1, gaps.size());
        assertEquals(gaps.size(), byGap.size());
        long terminalMasterCycles = calculation.path("terminal_write")
                .path("master_cycles_from_instruction_start").asLong();
        for (int gapIndex = 0; gapIndex < gaps.size(); gapIndex++) {
            JsonNode gap = gaps.get(gapIndex);
            List<LedgerRow> occurrences = byGap.get(gapIndex);
            assertTrue(occurrences != null && !occurrences.isEmpty());
            LedgerRow first = occurrences.get(0);
            LedgerRow last = occurrences.get(occurrences.size() - 1);
            assertEquals(gapIndex, gap.path("after_source_ordinal").asInt());
            assertEquals(gapIndex + 1, gap.path("before_source_ordinal").asInt());
            assertEquals(occurrences.size(), gap.path("occurrence_count").asInt());
            assertEquals(first.occurrenceOrdinal(), gap.path("first_occurrence_ordinal").asInt());
            assertEquals(last.occurrenceOrdinal(), gap.path("last_occurrence_ordinal").asInt());
            assertEquals(first.pcText(), gap.path("first_pc").asText());
            assertEquals(first.opcodeText(), gap.path("first_opcode").asText());
            assertEquals(last.pcText(), gap.path("last_pc").asText());
            assertEquals(last.opcodeText(), gap.path("last_opcode").asText());
            assertEquals("per-occurrence ledger source mapping", gap.path("source").asText());
            assertRoleCount(gap, occurrences, "branch_occurrences", "branch", false);
            long callReturns = occurrences.stream().filter(row ->
                    row.flow().equals("call") || row.flow().equals("return")).count();
            assertEquals(callReturns, gap.path("call_return_occurrences").asLong());
            assertRoleCount(gap, occurrences, "busy_poll_occurrences", "busy_poll", true);
            assertRoleCount(gap, occurrences, "bank_wait_occurrences", "bank_wait_3t", true);
            assertTrue(gap.path("branch_occurrences").asInt() > 0);
            assertTrue(gap.path("call_return_occurrences").asInt() > 0);
            assertTrue(gap.path("busy_poll_occurrences").asInt() > 0);

            boolean permittedTerminalPc = false;
            for (JsonNode terminalPc : calculation.path("terminal_write").path("pc")) {
                permittedTerminalPc |= terminalPc.asText().equals(last.pcText());
            }
            assertTrue(permittedTerminalPc);
            assertEquals(calculation.path("terminal_write").path("opcode").asText(),
                    last.opcodeText());
            long derivedGap = last.startMasterCycle() - first.startMasterCycle()
                    + terminalMasterCycles;
            long capturedGap = writes.get(gapIndex + 1).path("relative_master_cycle").asLong()
                    - writes.get(gapIndex).path("relative_master_cycle").asLong();
            assertEquals(capturedGap, derivedGap, "gap " + gapIndex);
        }
    }

    private static void assertRoleCount(JsonNode gap, List<LedgerRow> rows,
                                        String field, String token, boolean roles) {
        long count = rows.stream().filter(row -> roles
                ? row.roles().contains(token) : row.flow().equals(token)).count();
        assertEquals(count, gap.path(field).asLong());
    }

    private static int[] ints(JsonNode node) {
        int[] result = new int[node.size()];
        for (int index = 0; index < result.length; index++) {
            result[index] = node.get(index).asInt();
        }
        return result;
    }

    private static int attenuationError(int[] actual, int[] expected) {
        assertEquals(expected.length, actual.length);
        int error = 0;
        for (int index = 0; index < expected.length; index++) {
            error += Math.abs(actual[index] - expected[index]);
        }
        return error;
    }

    private static List<LedgerRow> readLedger(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path);
        boolean refreshAware = lines.get(0).contains(
                "\trefresh_delay_total_master_cycles\t");
        assertEquals("occurrence_ordinal\tframe\tafter_source_ordinal\tcpu\tpc\topcode\t"
                        + "start_master_cycle\t"
                        + (refreshAware ? "refresh_delay_total_master_cycles\t" : "")
                        + "next_pc\tdelta_to_next_start\tflow\t"
                        + "branch_outcome\troles\tsource", lines.get(0));
        List<LedgerRow> rows = new ArrayList<>();
        for (int index = 1; index < lines.size(); index++) {
            String[] part = lines.get(index).split("\t", -1);
            assertEquals(refreshAware ? 14 : 13, part.length);
            int shift = refreshAware ? 1 : 0;
            rows.add(new LedgerRow(
                    Integer.parseInt(part[0]), Integer.parseInt(part[2]), Integer.parseInt(part[3]),
                    part[4], parseHex(part[4]), part[5], parseHex(part[5]),
                    Long.parseLong(part[6]), part[7 + shift], part[8 + shift].equals("key_on")
                    ? -1 : Long.parseLong(part[8 + shift]), part[9 + shift],
                    part[10 + shift], part[11 + shift], part[12 + shift]));
        }
        return List.copyOf(rows);
    }

    private static String expectedFlow(String architecture, int opcode) {
        if (architecture.equals("z80")) {
            if (contains(opcode, 0xcd, 0xc4, 0xcc, 0xd4, 0xdc, 0xe4, 0xec, 0xf4, 0xfc,
                    0xc7, 0xcf, 0xd7, 0xdf, 0xe7, 0xef, 0xf7, 0xff)) return "call";
            if (contains(opcode, 0xc9, 0xc0, 0xc8, 0xd0, 0xd8, 0xe0, 0xe8, 0xf0, 0xf8)) return "return";
            if (contains(opcode, 0x10, 0x18, 0x20, 0x28, 0x30, 0x38,
                    0xc2, 0xca, 0xd2, 0xda, 0xe2, 0xea, 0xf2, 0xfa, 0xc3)) return "branch";
            return "linear";
        }
        int high = opcode >>> 8;
        if (high == 0x61 || (opcode & 0xffc0) == 0x4e80) return "call";
        if ((opcode & 0xffc0) == 0x4ec0) return "jump";
        if (contains(opcode, 0x4e75, 0x4e73, 0x4e77)) return "return";
        if ((high >= 0x60 && high <= 0x6f) || (opcode & 0xf0f8) == 0x50c8) return "branch";
        return "linear";
    }

    private static boolean isConditional(String architecture, int opcode) {
        if (architecture.equals("z80")) {
            return contains(opcode, 0x10, 0x20, 0x28, 0x30, 0x38,
                    0xc0, 0xc8, 0xd0, 0xd8, 0xe0, 0xe8, 0xf0, 0xf8,
                    0xc2, 0xca, 0xd2, 0xda, 0xe2, 0xea, 0xf2, 0xfa,
                    0xc4, 0xcc, 0xd4, 0xdc, 0xe4, 0xec, 0xf4, 0xfc);
        }
        int high = opcode >>> 8;
        return (high >= 0x62 && high <= 0x6f) || (opcode & 0xf0f8) == 0x50c8;
    }

    private static int sequentialPc(String architecture, int pc, int opcode) {
        if (architecture.equals("z80")) {
            if (contains(opcode, 0x10, 0x18, 0x20, 0x28, 0x30, 0x38)) return pc + 2;
            if (contains(opcode, 0xc2, 0xca, 0xd2, 0xda, 0xe2, 0xea, 0xf2, 0xfa, 0xc3,
                    0xc4, 0xcc, 0xd4, 0xdc, 0xe4, 0xec, 0xf4, 0xfc, 0xcd)) return pc + 3;
            return pc + 1;
        }
        int high = opcode >>> 8;
        int low = opcode & 0xff;
        if (high >= 0x60 && high <= 0x6f) return pc + (low == 0 ? 4 : (low == 0xff ? 6 : 2));
        if ((opcode & 0xf0f8) == 0x50c8) return pc + 4;
        return pc + 2;
    }

    private static String expectedRoles(String game, int pc, int opcode, String flow) {
        StringBuilder roles = new StringBuilder(flow.equals("linear") ? "ordinary" : "control_flow");
        if (game.equals("s1")) {
            if (((contains(pc, 0x7272e, 0x72746, 0x72764, 0x7277c) && opcode == 0x1439)
                    || (contains(pc, 0x72734, 0x7274c, 0x7276a, 0x72782) && opcode == 0x0802)
                    || (contains(pc, 0x7273a, 0x72750, 0x72770, 0x72786)
                    && (opcode & 0xff00) == 0x6600))) roles.append(",busy_poll");
            if ((pc == 0x72788 || pc == 0x72752) && opcode == 0x13c1) roles.append(",ym_write");
        } else {
            if ((pc == 0x8 && opcode == 0x3a) || (pc == 0xb && opcode == 0x87)
                    || (pc == 0xc && opcode == 0x38)) roles.append(",busy_poll");
            if ((pc == 0xe34 && opcode == 0x7e)
                    || ((pc == 0xe46 || pc == 0xe52 || pc == 0xe79) && opcode == 0x4e)) {
                roles.append(",bank_wait_3t");
            }
            if ((pc == 0x31 || pc == 0x21) && opcode == 0x32) roles.append(",ym_write");
        }
        return roles.toString();
    }

    private static void assertClassification(String game, String architecture, int pc,
                                             int opcode, String flow, String roles,
                                             String source) {
        assertEquals(expectedFlow(architecture, opcode), flow);
        assertEquals(expectedRoles(game, pc, opcode, flow), roles);
        assertEquals(expectedSource(game, pc), source);
        assertFalse(source.equals("UNKNOWN"));
    }

    private static String expectedSource(String game, int pc) {
        return sourceFor(game, pc);
    }

    private static String sourceFor(String game, int pc) {
        List<SourceRange> matches = SOURCE_RANGES.stream()
                .filter(range -> range.game().equals(game)
                        && pc >= range.startPc() && pc <= range.endPc()).toList();
        assertEquals(1, matches.size(), "source-map coverage at " + game + ":" + Integer.toHexString(pc));
        SourceRange range = matches.get(0);
        return range.label() + "@" + range.source() + ":"
                + range.lineStart() + "-" + range.lineEnd();
    }

    private static List<SourceRange> readSourceMap() {
        try {
            List<String> lines = Files.readAllLines(SOURCE_MAP);
            assertEquals("game\tstart_pc\tend_pc\tlabel\tsource\tline_start\tline_end", lines.get(0));
            List<SourceRange> result = new ArrayList<>();
            for (int index = 1; index < lines.size(); index++) {
                String[] part = lines.get(index).split("\t", -1);
                assertEquals(7, part.length);
                result.add(new SourceRange(part[0], parseHex(part[1]), parseHex(part[2]),
                        part[3], part[4], Integer.parseInt(part[5]), Integer.parseInt(part[6])));
            }
            return List.copyOf(result);
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void assertSourceMapIntegrity() {
        for (int left = 0; left < SOURCE_RANGES.size(); left++) {
            SourceRange range = SOURCE_RANGES.get(left);
            assertTrue(range.startPc() <= range.endPc());
            assertTrue(range.lineStart() <= range.lineEnd());
            for (int right = left + 1; right < SOURCE_RANGES.size(); right++) {
                SourceRange other = SOURCE_RANGES.get(right);
                if (range.game().equals(other.game())) {
                    assertTrue(range.endPc() < other.startPc() || other.endPc() < range.startPc());
                }
            }
        }
        assertEquals("FMUpdateTrack@s1.sounddriver.asm:348-362", sourceFor("s1", 0x71cd8));
        assertEquals("FinishTrackUpdate@s1.sounddriver.asm:435-456", sourceFor("s1", 0x71d60));
        assertEquals("coordflagLookup@s1.sounddriver.asm:2074-2076", sourceFor("s1", 0x72a64));
        assertEquals("zFMNoteOn@s2.sounddriver.asm:2796-2807", sourceFor("s2", 0xc46));
        assertEquals("zBankSwitchToMusic@s2.sounddriver.asm:2833-2848", sourceFor("s2", 0xc63));
        assertEquals("cfPanningAMSFMS@s2.sounddriver.asm:3004-3045", sourceFor("s2", 0xcfc));
    }

    private static boolean contains(int value, int... candidates) {
        for (int candidate : candidates) if (value == candidate) return true;
        return false;
    }

    private static List<String> without(List<String> source, int index) {
        List<String> result = new ArrayList<>(source);
        result.remove(index);
        return result;
    }

    private static List<String> replaced(List<String> source, int index,
                                         String before, String after) {
        List<String> result = new ArrayList<>(source);
        String value = result.get(index);
        assertTrue(value.contains(before));
        result.set(index, value.replaceFirst(java.util.regex.Pattern.quote(before), after));
        return result;
    }

    private static int parseHex(String value) {
        return Integer.parseUnsignedInt(value.substring(2), 16);
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private record LedgerRow(int occurrenceOrdinal, int afterSourceOrdinal, int cpu,
                             String pcText, int pc, String opcodeText, int opcode,
                             long startMasterCycle, String nextPc, long deltaToNextStart,
                             String flow, String branchOutcome, String roles, String source) {
    }

    @Test
    void captureScriptPublishesInstructionLedgersOnlyForInstructionAuditGames()
            throws IOException {
        String script = Files.readString(Path.of(
                "tools/bizhawk-headless/native/gpgx-audio-lab/capture-ym-write-timing.sh"));
        assertTrue(script.contains("if [[ \"$game\" == s1 || \"$game\" == s2 ]]; then\n"
                        + "  raw_instructions_sha=$(sha256 \"$raw_instructions\")"),
                "S3K produces the compact YM oracle but no CPU instruction ledger");
        assertTrue(script.contains("  ln -- \"$raw_instructions\" \"$instructions_output\""),
                "S1/S2 must retain their native instruction ledgers");
    }

    private record SourceRange(String game, int startPc, int endPc, String label,
                               String source, int lineStart, int lineEnd) {
    }
}
