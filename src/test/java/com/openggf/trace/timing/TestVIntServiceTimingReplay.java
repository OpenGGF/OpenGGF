package com.openggf.trace.timing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.trace.TraceMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The lost-V-INT admission's stream and port (hardware-timing contract, 2026-11-13 extension): strict grammar,
 * absence distinct from an empty stream, loading fails for an undeclaring game or an event on a main-loop row, and
 * the port answers rows only in order.
 */
class TestVIntServiceTimingReplay {
    @TempDir Path temporaryDirectory;

    /** Rows 1-3 VBlank-only (a levelFrames plateau), rows 0 and 4-7 run a main loop. */
    private static final Set<Integer> VBLANK_ONLY = Set.of(1, 2, 3);

    @Test
    void absenceIsNotObservedAndAnEmptyStreamIsObservedNone() throws Exception {
        VIntServiceTimingSchedule absent = load(null);
        assertFalse(absent.observed());
        assertEquals(0, absent.unservicedRows().length);
        VIntServiceTimingSchedule empty = load("");
        assertTrue(empty.observed());
        assertEquals(0, empty.unservicedRows().length);
        assertArrayEquals(new int[]{2, 5}, load(event(2) + event(5)).unservicedRows());
    }

    @Test
    void rejectsMalformedFieldsOrderAndRange() {
        String valid = event(2);
        reject(valid.replace("{", "{\"counter\":3,"));
        reject(valid.replace("\"raw_frame\":2", "\"level_vint_counter\":2"));
        reject(valid.replace("vint_not_serviced", "vint_serviced"));
        reject(valid.replace("2", "-1"));
        reject(valid.replace("2", "2.0"));
        reject(valid.replace("2", "\"2\""));
        reject(event(8));
        reject(event(3) + event(3));
        reject(event(3) + event(2));
        reject(valid.replace("{", "{\"raw_frame\":2,"));
        for (String framing : List.of("\n", " " + valid, valid.stripTrailing(), valid.replace("\n", "\r\n"),
                valid.replace("\n", " {}\n"), "[]\n")) {
            reject(framing);
        }
    }

    @Test
    void gzipHasTheSameSemanticsAndAPresentStreamRequiresV5() throws Exception {
        Path directory = Files.createTempDirectory(temporaryDirectory, "gzip-");
        try (GZIPOutputStream out = new GZIPOutputStream(
                Files.newOutputStream(directory.resolve("vint_service_timing.jsonl.gz")))) {
            out.write(event(1).getBytes(StandardCharsets.UTF_8));
        }
        assertArrayEquals(new int[]{1}, VIntServiceTimingStreamLoader.load(directory, metadata(5)).unservicedRows());
        assertThrows(IOException.class, () -> VIntServiceTimingStreamLoader.load(directory, metadata(4)));
    }

    @Test
    void anEventOnARowThatRunsAMainLoopFailsLoading() throws Exception {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> VIntServiceTimingReplayPort.install(load(event(2) + event(4)), true, VBLANK_ONLY::contains));
        assertEquals("vint_not_serviced on raw_frame 4, which runs a main loop; only a VBlank-only row can lose its "
                + "V-INT", failure.getMessage());
        assertThrows(IllegalStateException.class,
                () -> VIntServiceTimingReplayPort.install(load(event(0)), true, VBLANK_ONLY::contains));
    }

    @Test
    void aStreamOnAGameThatDoesNotDeclareTheAdmissionFailsLoading() throws Exception {
        assertThrows(IllegalStateException.class,
                () -> VIntServiceTimingReplayPort.install(load(event(2)), false, VBLANK_ONLY::contains));
        assertThrows(IllegalStateException.class,
                () -> VIntServiceTimingReplayPort.install(load(""), false, VBLANK_ONLY::contains));
        VIntServiceTimingReplayPort unrecorded =
                VIntServiceTimingReplayPort.install(load(null), false, VBLANK_ONLY::contains);
        assertFalse(unrecorded.recorded());
        for (int row = 0; row < 8; row++) assertTrue(unrecorded.vBlankServiced(row), "row " + row);
    }

    @Test
    void theEventRowIsTheOnlyUnservicedRowAndRowsAreAskedInOrder() throws Exception {
        VIntServiceTimingReplayPort port = VIntServiceTimingReplayPort.install(load(event(2)), true,
                VBLANK_ONLY::contains);
        assertTrue(port.recorded());
        assertTrue(port.vBlankServiced(0));
        assertTrue(port.vBlankServiced(1));
        assertFalse(port.vBlankServiced(2));
        assertTrue(port.vBlankServiced(3));
        assertThrows(IllegalStateException.class, () -> port.vBlankServiced(3));
        assertThrows(IllegalStateException.class, () -> port.vBlankServiced(2));

        VIntServiceTimingReplayPort skipping = VIntServiceTimingReplayPort.install(load(event(2)), true,
                VBLANK_ONLY::contains);
        assertTrue(skipping.vBlankServiced(1));
        assertThrows(IllegalStateException.class, () -> skipping.vBlankServiced(3),
                "skipping past an event row must not silently service it");
    }

    private VIntServiceTimingSchedule load(String content) throws IOException {
        Path directory = Files.createTempDirectory(temporaryDirectory, "vint-");
        if (content != null) Files.writeString(directory.resolve("vint_service_timing.jsonl"), content);
        return VIntServiceTimingStreamLoader.load(directory, metadata(5));
    }

    private void reject(String content) {
        IOException failure = assertThrows(IOException.class, () -> load(content), content);
        assertTrue(failure.getMessage().startsWith("vint_service_timing.jsonl:"), failure::getMessage);
    }

    private static String event(int row) {
        return "{\"event\":\"vint_not_serviced\",\"raw_frame\":" + row + "}\n";
    }

    private static TraceMetadata metadata(int schema) throws IOException {
        return new ObjectMapper().readValue("{\"game\":\"scd\",\"zone\":\"test\",\"zone_id\":0,\"act\":1,"
                + "\"bk2_frame_offset\":0,\"trace_frame_count\":8,\"start_x\":\"0x0000\","
                + "\"start_y\":\"0x0000\",\"trace_schema\":" + schema + "}", TraceMetadata.class);
    }
}
