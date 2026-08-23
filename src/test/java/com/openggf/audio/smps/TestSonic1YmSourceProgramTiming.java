package com.openggf.audio.smps;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertEquals(1_008, root.path("clock").path("master_cycles_per_ym_sample").asInt());
        assertEquals(47, root.path("clock").path("busy_ym_cycles_after_data_write").asInt());
        assertEquals(24, root.path("clock").path("busy_ym_cycles_per_sample").asInt());

        assertEquals(2, root.path("programs").size());
        assertProgram(root.path("programs").get(0), "VOICE_NOTE", 30, 0);
        assertProgram(root.path("programs").get(1), "VOICE_PAN_NOTE", 31, 1);
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

        int zeroAnchors = 0;
        Set<Integer> consumed = new HashSet<>();
        for (int index = 0; index < program.path("writes").size(); index++) {
            JsonNode write = program.path("writes").get(index);
            assertEquals(index, write.path("write_ordinal").asInt());
            if (write.path("row_zero_anchor").asBoolean()) {
                zeroAnchors++;
                assertEquals(0, index);
            }
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
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }
}
