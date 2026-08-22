package com.openggf.audio.synth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Executable source/native proof for the S1/S2 ring timing audit. */
class TestS1S2YmWriteTimingAudit {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Path S1_ORACLE = Path.of(
            "docs/architecture/research/audio/s1-ring-ym-write-audit-v2.json");
    private static final Path S2_ORACLE = Path.of(
            "docs/architecture/research/audio/s2-ringright-ym-write-audit-v2.json");
    private static final Path S1_CALCULATION = Path.of(
            "docs/architecture/research/audio/s1-ring-ym-write-timing-calculation-v1.json");
    private static final Path S2_CALCULATION = Path.of(
            "docs/architecture/research/audio/s2-ringright-ym-write-timing-calculation-v1.json");

    @Test
    void retainedNativeCounterfactualUsesEveryCapturedPreGroupContext() throws IOException {
        assertNativeCounterfactual(S1_ORACLE, "s1");
        assertNativeCounterfactual(S2_ORACLE, "s2");
    }

    @Test
    void everySourceGapDerivesFromPrimitiveInstructionRows() throws IOException {
        assertPrimitiveCalculation(S1_CALCULATION, S1_ORACLE, "m68k");
        assertPrimitiveCalculation(S2_CALCULATION, S2_ORACLE, "z80");
    }

    private static void assertNativeCounterfactual(Path path, String game)
            throws IOException {
        JsonNode root = JSON.readTree(Files.readAllBytes(path));
        assertEquals("openggf.s1-s2-ym-write-timing-audit.v2",
                root.path("schema").asText());
        assertEquals(game, root.path("game").asText());
        boolean isolated = false;
        boolean overlap = false;
        boolean materialIsolated = false;
        for (JsonNode group : root.path("groups")) {
            String classification = group.path("classification").asText();
            isolated |= classification.equals("isolated");
            overlap |= classification.equals("overlap");
            JsonNode counterfactual = group.path("native_counterfactual");
            byte[] context = Base64.getDecoder().decode(counterfactual
                    .path("pre_group_context_base64").asText());
            assertEquals(counterfactual.path("pre_group_context_size").asInt(),
                    context.length);
            assertEquals(counterfactual.path("pre_group_context_sha256").asText(),
                    sha256(context));
            assertEquals(4, counterfactual.path("atomic_key_on_attenuation").size());
            assertEquals(4, counterfactual.path("timed_key_on_attenuation").size());
            assertFalse(counterfactual.path("pre_group_context_sha256").asText()
                    .matches("0+") , "native context hash must not be a placeholder");
            if (classification.equals("isolated")
                    && group.path("relative_last_master_cycle").asLong() >= 4_032
                    && counterfactual.path("maximum_attenuation_difference").asInt() >= 8) {
                materialIsolated = true;
            }
        }
        assertTrue(isolated, game + " capture must retain isolated admissions");
        assertTrue(overlap, game + " capture must retain overlap admissions separately");
        assertEquals(materialIsolated,
                root.path("ruling").path("isolated_material").asBoolean(),
                "ruling must be derived from native isolated contexts");
    }

    private static void assertPrimitiveCalculation(Path calculationPath,
                                                   Path oraclePath,
                                                   String architecture)
            throws IOException {
        JsonNode calculation = JSON.readTree(Files.readAllBytes(calculationPath));
        JsonNode oracle = JSON.readTree(Files.readAllBytes(oraclePath));
        assertEquals("openggf.s1-s2-ym-write-calculation.v1",
                calculation.path("schema").asText());
        assertEquals(architecture, calculation.path("architecture").asText());
        if (architecture.equals("z80")) {
            assertEquals("zSFX_FM5", calculation.path("source")
                    .path("owner").path("label").asText());
            assertEquals("0x1D90", calculation.path("source")
                    .path("owner").path("ix").asText());
        }
        long masterCyclesPerCpuCycle = calculation.path("clock")
                .path("master_cycles_per_cpu_cycle").asLong();
        Map<String, Long> timing = primitiveTimings(architecture);
        JsonNode representative = null;
        for (JsonNode group : oracle.path("groups")) {
            if (group.path("classification").asText().equals("isolated")) {
                representative = group;
                break;
            }
        }
        assertTrue(representative != null);
        JsonNode writes = representative.path("writes");
        JsonNode gaps = calculation.path("gaps");
        assertEquals(writes.size() - 1, gaps.size());
        for (int gapIndex = 0; gapIndex < gaps.size(); gapIndex++) {
            JsonNode gap = gaps.get(gapIndex);
            assertEquals(gapIndex, gap.path("after_source_ordinal").asInt());
            assertEquals(gapIndex + 1, gap.path("before_source_ordinal").asInt());
            assertTrue(gap.path("source").asText().startsWith(
                    architecture.equals("m68k")
                            ? "s1.sounddriver.asm:" : "s2.sounddriver.asm:"));
            long cpuCycles = 0;
            assertTrue(gap.path("rows").size() > 0);
            boolean busyPoll = false;
            boolean callReturn = false;
            boolean bankWait = architecture.equals("m68k");
            for (JsonNode row : gap.path("rows")) {
                String primitive = row.path("primitive").asText();
                assertTrue(timing.containsKey(primitive),
                        "unknown primitive " + primitive);
                assertEquals(timing.get(primitive).longValue(),
                        row.path("cycles_each").asLong(),
                        "artifact cannot redefine primitive timing");
                assertTrue(row.path("count").asLong() > 0);
                busyPoll |= row.path("role").asText().equals("busy_poll");
                callReturn |= row.path("role").asText().equals("call_return");
                bankWait |= row.path("role").asText().equals("bank_wait");
                if (primitive.equals("M68K_BNE_SHORT")
                        || primitive.equals("M68K_DBF")
                        || primitive.startsWith("Z80_JR")
                        || primitive.equals("Z80_DJNZ")) {
                    assertTrue(row.hasNonNull("branch_outcome"));
                }
                cpuCycles = Math.addExact(cpuCycles, Math.multiplyExact(
                        timing.get(primitive), row.path("count").asLong()));
            }
            assertTrue(busyPoll, "gap " + gapIndex + " must expose busy polling");
            assertTrue(callReturn, "gap " + gapIndex + " must expose call/return");
            assertTrue(bankWait, "gap " + gapIndex + " must expose bank wait rules");
            cpuCycles = Math.addExact(cpuCycles,
                    gap.path("wait_cpu_cycles").asLong());
            long actual = writes.get(gapIndex + 1)
                    .path("relative_master_cycle").asLong()
                    - writes.get(gapIndex).path("relative_master_cycle").asLong();
            assertEquals(actual, Math.multiplyExact(cpuCycles,
                    masterCyclesPerCpuCycle), "gap " + gapIndex);
        }
    }

    private static Map<String, Long> primitiveTimings(String architecture) {
        Map<String, Long> timing = new HashMap<>();
        if (architecture.equals("m68k")) {
            timing.put("M68K_RTS", 16L);
            timing.put("M68K_JSR_PC", 18L);
            timing.put("M68K_MOVE_B_ABS_READ", 16L);
            timing.put("M68K_BTST_IMMEDIATE", 4L);
            timing.put("M68K_BNE_SHORT", 8L);
            timing.put("M68K_MOVE_B_ABS_WRITE", 20L);
            timing.put("M68K_DBF", 10L);
            timing.put("M68K_MOVE_B_AN_POSTINC", 8L);
            timing.put("M68K_NOP", 4L);
            timing.put("M68K_BUS_ARBITRATION", 1L);
        } else {
            timing.put("Z80_RET", 10L);
            timing.put("Z80_RST", 11L);
            timing.put("Z80_LD_ABS_A", 13L);
            timing.put("Z80_LD_A_ABS", 13L);
            timing.put("Z80_ADD_A_A", 4L);
            timing.put("Z80_JR_C", 7L);
            timing.put("Z80_BIT_IX_D", 20L);
            timing.put("Z80_JR_Z", 7L);
            timing.put("Z80_JR", 12L);
            timing.put("GPGX_BANK_READ_WAIT", 3L);
            timing.put("Z80_LD_R_IX_D", 19L);
            timing.put("Z80_DJNZ", 13L);
            timing.put("Z80_PUSH_AF", 11L);
            timing.put("Z80_POP_AF", 10L);
            timing.put("Z80_LD_R_HL", 7L);
            timing.put("Z80_INC_HL", 6L);
            timing.put("Z80_LD_R_R", 4L);
        }
        return Map.copyOf(timing);
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }
}
