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

    @Test
    void retainedNativeCounterfactualUsesEveryCapturedPreGroupContext() throws IOException {
        assertNativeCounterfactual(RESEARCH.resolve("s1-ring-ym-write-audit-v2.json"), "s1");
        assertNativeCounterfactual(RESEARCH.resolve("s2-ringright-ym-write-audit-v2.json"), "s2");
    }

    @Test
    void everyGapIsAnExactOrderedJoinOfCapturedInstructionOccurrences() throws IOException {
        assertInstructionJoin(
                "s1", "m68k", 2, 7,
                "s1-ring-ym-write-audit-v2.json",
                "s1-ring-ym-write-timing-calculation-v2.json",
                "790fe245386f77d09309bb7eeb7f653bfdbd6312d4600eba04520d143ec95643",
                "b860dccea2be3c3bae9788fd4621e7fd57311e6c2d9e57ef34a5617222ce23aa");
        assertInstructionJoin(
                "s2", "z80", 1, 15,
                "s2-ringright-ym-write-audit-v2.json",
                "s2-ringright-ym-write-timing-calculation-v2.json",
                "b3326be2fd908d2914f55828f2c7734627fa319da9aa496f75d862d74d60c6b4",
                "d03eed2d2679b2287c626c5098b96140c22e3746e425a23901ef023998826c3c");
    }

    @Test
    void captureDigestRejectsDeletionPcOpcodeCountOrderAndFakePrimitive() throws IOException {
        Path ledger = RESEARCH.resolve("s2-ringright-ym-write-instruction-ledger-v1.tsv");
        String expected = "b3326be2fd908d2914f55828f2c7734627fa319da9aa496f75d862d74d60c6b4";
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
                "790fe245386f77d09309bb7eeb7f653bfdbd6312d4600eba04520d143ec95643",
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
        assertEquals(masterCyclesPerCpuCycle,
                calculation.path("clock").path("master_cycles_per_cpu_cycle").asLong());
        assertEquals(expectedFullCaptureSha,
                oracle.path("provenance").path("native_instructions_sha256").asText());
        assertEquals(expectedFullCaptureSha,
                calculation.path("ledger").path("full_capture_sha256").asText());
        if (game.equals("s2")) {
            String scriptSha = sha256(Files.readAllBytes(Path.of(
                    "tools/bizhawk-headless/native/gpgx-audio-lab/capture-ym-write-timing.sh")));
            assertEquals("a21fbd6ee44173fd8bbe20b0af747bc703f0f1c7f9c521a1866f89e579cd7388",
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

    private static List<LedgerRow> readLedger(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path);
        assertEquals("occurrence_ordinal\tframe\tafter_source_ordinal\tcpu\tpc\topcode\t"
                        + "start_master_cycle\tnext_pc\tdelta_to_next_start\tflow\t"
                        + "branch_outcome\troles\tsource", lines.get(0));
        List<LedgerRow> rows = new ArrayList<>();
        for (int index = 1; index < lines.size(); index++) {
            String[] part = lines.get(index).split("\t", -1);
            assertEquals(13, part.length);
            rows.add(new LedgerRow(
                    Integer.parseInt(part[0]), Integer.parseInt(part[2]), Integer.parseInt(part[3]),
                    part[4], parseHex(part[4]), part[5], parseHex(part[5]),
                    Long.parseLong(part[6]), part[7], part[8].equals("key_on")
                    ? -1 : Long.parseLong(part[8]), part[9], part[10], part[11], part[12]));
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
        if (game.equals("s1")) {
            if (pc >= 0x71cd8 && pc <= 0x71e48) return "FinishTrackUpdate@s1.sounddriver.asm:436-544";
            if (pc >= 0x726e2 && pc <= 0x72714) return "FMNoteOn_FMNoteOff@s1.sounddriver.asm:1670-1704";
            if (pc >= 0x72716 && pc <= 0x72720) return "WriteFMIorIIMain@s1.sounddriver.asm:1707-1717";
            if (pc >= 0x72722 && pc <= 0x72728) return "WriteFMIorII@s1.sounddriver.asm:1720-1726";
            if (pc >= 0x7272e && pc <= 0x72758) return "WriteFMI@s1.sounddriver.asm:1737-1755";
            if (pc >= 0x7275a && pc <= 0x72762) return "WriteFMIIPart@s1.sounddriver.asm:1759-1763";
            if (pc >= 0x72764 && pc <= 0x7278e) return "WriteFMII@s1.sounddriver.asm:1766-1784";
            if (pc >= 0x72a5a && pc <= 0x72a64) return "CoordFlag@s1.sounddriver.asm:2066-2072";
            if (pc >= 0x72acc && pc <= 0x72ae6) return "cfPanningAMSFMS@s1.sounddriver.asm:2128-2140";
            if (pc >= 0x72c26 && pc <= 0x72c48) return "cfSetVoice@s1.sounddriver.asm:2313-2326";
            if (pc >= 0x72c4e && pc <= 0x72caa) return "SetVoice@s1.sounddriver.asm:2329-2375";
        } else {
            if (pc >= 0x8 && pc <= 0x35) return "zFMBusyWait_zWriteFM@s2.sounddriver.asm:343-389";
            if (pc >= 0x243 && pc <= 0x2d9) return "zFinishTrackUpdate@s2.sounddriver.asm:947-1076";
            if (pc >= 0x3e5 && pc <= 0x413) return "zTrackRun@s2.sounddriver.asm:1078-1124";
            if (pc >= 0xc46 && pc <= 0xc94) return "zBankSwitchToMusic@s2.sounddriver.asm:2796-2829";
            if (pc >= 0xcfc && pc <= 0xd19) return "zSetMaxRelRate@s2.sounddriver.asm:3005-3024";
            if (pc >= 0xe06 && pc <= 0xe11) return "cfSetVoice@s2.sounddriver.asm:3271-3282";
            if (pc >= 0xe12 && pc <= 0xe1f) return "cfSetVoiceCont@s2.sounddriver.asm:3285-3295";
            if (pc >= 0xe24 && pc <= 0xe64) return "zSetVoice@s2.sounddriver.asm:3305-3396";
            if (pc >= 0xe65 && pc <= 0xe89) return "zSetFMTLs@s2.sounddriver.asm:3399-3432";
        }
        throw new AssertionError("unmapped source PC " + Integer.toHexString(pc));
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
}
