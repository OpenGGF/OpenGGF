package com.openggf.trace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestLevelEntryRamMetadata {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Path KIS2 = Path.of("src/test/resources/traces/kis2/ehz1/metadata.json");

    @Test
    void committedCameraLongsRetainTheirUnsignedBits() throws Exception {
        for (Path path : List.of(KIS2, Path.of(
                "src/test/resources/traces/s2/runs/s2-ehz-halfpipe-roundtrip/seg1_ehz1/metadata.json"))) {
            long wire = JSON.readTree(path.toFile()).path("level_entry_ram").path("camera_x_pos").longValue();
            assertEquals(wire, Integer.toUnsignedLong(TraceMetadata.load(path).levelEntryRam().get("camera_x_pos")));
        }
    }

    @Test
    void signedAndUnsignedLimitsAreBitPatterns(@TempDir Path dir) throws Exception {
        ObjectNode root = (ObjectNode) JSON.readTree(KIS2.toFile());
        for (long value : new long[] {Integer.MIN_VALUE, -1, 0, Integer.MAX_VALUE, 0x80000000L, 0xFFFFFFFFL}) {
            ((ObjectNode) root.get("level_entry_ram")).put("camera_x_pos", value);
            Path metadata = dir.resolve("metadata.json");
            JSON.writeValue(metadata.toFile(), root);
            assertEquals((int) value, TraceMetadata.load(metadata).levelEntryRam().get("camera_x_pos"));
        }
    }

    @Test
    void continuationBankUsesV5ObservationRatherThanRecorderProvenance(@TempDir Path dir) throws Exception {
        Path segment = Path.of("src/test/resources/traces/s3k/runs/"
                + "s3k-sonic-tails-complete-emeralds/hpz22");
        ObjectNode root = (ObjectNode) JSON.readTree(segment.resolve("metadata.json").toFile());
        root.put("recorder", "opaque-alternate-producer");
        root.put("recorder_version", "not-a-behavior-selector");
        Path metadata = dir.resolve("metadata.json");
        JSON.writeValue(metadata.toFile(), root);
        var observed = com.openggf.trace.replay.TraceSegmentCarriedStateImport.resolve(
                segment, TraceMetadata.load(metadata));
        assertEquals(new com.openggf.trace.replay.TraceSegmentCarriedState.NativeContinuation(233, 12180),
                observed.nativeContinuation());
        ((ObjectNode) root.get("level_entry_ram"))
                .remove(List.of("act3_flag", "act3_ring_count", "act3_timer"));
        JSON.writeValue(metadata.toFile(), root);
        var absent = com.openggf.trace.replay.TraceSegmentCarriedStateImport.resolve(
                segment, TraceMetadata.load(metadata));
        org.junit.jupiter.api.Assertions.assertNull(absent.nativeContinuation());
        org.junit.jupiter.api.Assertions.assertNull(absent.ringCount(), "no fallback to live rings233");
        org.junit.jupiter.api.Assertions.assertTrue(absent.unresolved().stream()
                .anyMatch(note -> note.startsWith("Act3 continuation:")));
    }

    @Test
    void rejectsFractionsAndOutOfRangeIntegers(@TempDir Path dir) throws Exception {
        ObjectNode root = (ObjectNode) JSON.readTree(KIS2.toFile());
        for (Number value : List.of(1.5, -2147483649L, 0x100000000L, new java.math.BigInteger("18446744073709551616"))) {
            ((ObjectNode) root.get("level_entry_ram")).set("camera_x_pos", JSON.valueToTree(value));
            Path metadata = dir.resolve("metadata.json");
            JSON.writeValue(metadata.toFile(), root);
            assertThrows(IllegalArgumentException.class, () -> TraceMetadata.load(metadata));
        }
    }
}
