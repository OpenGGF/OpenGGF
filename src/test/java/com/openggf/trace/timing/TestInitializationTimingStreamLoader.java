package com.openggf.trace.timing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.trace.TraceMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class TestInitializationTimingStreamLoader {
    @TempDir Path temporaryDirectory;
    private static final String FP = "sha256:" + "a".repeat(64);

    @Test
    void absenceAndRecordedEmptyRemainDistinct() throws Exception {
        assertFalse(load(null).hasRecordedInput());
        assertTrue(load("").hasRecordedInput());
        assertTrue(load("").events().isEmpty());
    }

    @Test
    void orderedServicesHoldAndMultiplePhasesCanShareOnePhysicalRow() throws Exception {
        String content = event(0, 0, 7, 4, "level_data_decode", "hold")
                + event(1, 0, 7, 4, "level_data_decode", "vint_0")
                + event(1, 1, 7, 4, "level_data_decode", "vint_0")
                + event(1, 2, 7, 4, "level_data_decode", "resume")
                + event(1, 3, 7, 6, "level_setup", "resume")
                + event(1, 4, 8, 2, "display_clear", "hold");
        InitializationTimingSchedule schedule = load(content);
        assertEquals(6, schedule.events().size());
        assertEquals(5, schedule.eventsAt(1).size());
        assertTrue(schedule.eventsAt(2).isEmpty());
        assertEquals(InitializationTimingEvent.Operation.VINT_0, schedule.eventsAt(1).getFirst().operation());
        assertThrows(UnsupportedOperationException.class, () -> schedule.eventsAt(1).clear());
        assertThrows(UnsupportedOperationException.class, () -> schedule.events().clear());
        List<InitializationTimingEvent> input = new ArrayList<>(schedule.events());
        InitializationTimingSchedule copied = new InitializationTimingSchedule(input);
        input.clear();
        assertEquals(6, copied.events().size());
    }

    @Test
    void unfinishedRepresentedPrefixAndResumeWithoutAnInventedPredecessorAreValid() throws Exception {
        assertEquals(1, load(event(0, 0, 9, 8, "initial_objects", "hold")).events().size());
        assertEquals(1, load(event(0, 0, 9, 8, "initial_objects", "resume")).events().size());
    }

    @Test
    void rejectsSequenceAndPhysicalRowReordering() {
        String row = event(1, 0, 1, 1, "display_clear", "hold");
        reject(event(0, 1, 1, 1, "display_clear", "hold"));
        reject(row + event(1, 0, 1, 1, "display_clear", "resume"));
        reject(row + event(1, 2, 1, 1, "display_clear", "resume"));
        reject(row + event(0, 1, 1, 1, "display_clear", "resume"));
        reject(row + event(2, 1, 1, 1, "display_clear", "resume"));
    }

    @Test
    void rejectsIdentityMutationPrematureTransitionAndReentryAfterResume() {
        String hold = event(0, 0, 3, 4, "level_setup", "hold");
        reject(hold + event(0, 1, 3, 4, "initial_objects", "vint_0"));
        reject(hold + event(0, 1, 3, 4, "level_setup", "resume").replace(FP, "sha256:" + "b".repeat(64)));
        reject(hold + event(0, 1, 3, 5, "initial_objects", "resume"));
        String resumed = event(0, 0, 3, 4, "level_setup", "resume");
        reject(resumed + event(0, 1, 3, 4, "level_setup", "vint_0"));
        reject(resumed + event(0, 1, 3, 3, "initial_objects", "resume"));
        reject(resumed + event(0, 1, 2, 5, "initial_objects", "resume"));
        reject(resumed + event(0, 1, 4, 1, "display_clear", "resume")
                + event(0, 2, 3, 5, "initial_objects", "resume"));
    }

    @Test
    void rejectsUnknownDuplicateMissingFieldsAndArbitraryCounters() {
        String valid = event(0, 0, 1, 1, "display_clear", "resume");
        reject(valid.replace("{", "{\"sequence\":0,"));
        reject(valid.replace("{", "{\"v_int_run_count\":42,"));
        reject(valid.replace("\"sequence\":0,", ""));
        reject(valid.replace("load_phase_timing", "hardware_work_completed"));
        reject(valid.replace("display_clear", "unknown"));
        reject(valid.replace("resume", "set_counter"));
        reject(valid.replace(FP, FP.toUpperCase()));
        reject(valid.replace(FP, "sha256:a"));
    }

    @Test
    void rejectsNonIntegralOverflowAndOutOfRangeIdentities() {
        String valid = event(0, 0, 1, 1, "display_clear", "resume");
        for (String value : List.of("0", "-1", "1.0", "\"1\"", "9223372036854775808")) {
            reject(valid.replace("\"load_ordinal\":1", "\"load_ordinal\":" + value));
        }
        for (String field : List.of("raw_frame", "sequence", "phase_ordinal")) {
            String original = "\"" + field + "\":" + (field.equals("phase_ordinal") ? "1" : "0");
            for (String value : List.of("-1", "1.0", "2147483648", "null")) {
                reject(valid.replace(original, "\"" + field + "\":" + value));
            }
        }
        reject(event(4, 0, 1, 1, "display_clear", "resume"));
        reject(event(0, 0, 1, 0, "display_clear", "resume"));
    }

    @Test
    void rejectsMalformedFramingAndMultipleJsonValues() {
        String valid = event(0, 0, 1, 1, "display_clear", "resume");
        for (String invalid : List.of("\n", " " + valid, valid.stripTrailing(),
                valid.replace("\n", "\r\n"), valid.replace("\n", " {}\n"), "[]\n", "{\n")) reject(invalid);
    }

    @Test
    void gzipHasIdenticalSemanticsAndStrictDecodingWithPlainSiblingPrecedence() throws Exception {
        String valid = event(0, 0, 1, 1, "display_clear", "resume");
        Path directory = Files.createTempDirectory(temporaryDirectory, "gzip-");
        Path gzipPath = directory.resolve("initialization_timing.jsonl.gz");
        gzip(gzipPath, valid.getBytes(StandardCharsets.UTF_8));
        assertEquals(load(valid).events(), InitializationTimingStreamLoader.load(directory, metadata(5)).events());
        gzip(gzipPath, new byte[0]);
        assertTrue(InitializationTimingStreamLoader.load(directory, metadata(5)).hasRecordedInput());
        gzip(gzipPath, new byte[]{(byte) 0xc3, 0x28});
        assertThrows(IOException.class, () -> InitializationTimingStreamLoader.load(directory, metadata(5)));
        Files.write(gzipPath, new byte[0]);
        assertThrows(IOException.class, () -> InitializationTimingStreamLoader.load(directory, metadata(5)));
        Files.writeString(directory.resolve("initialization_timing.jsonl"), valid);
        assertEquals(1, InitializationTimingStreamLoader.load(directory, metadata(5)).events().size());
    }

    @Test
    void presentStreamRequiresV5EvenWhenEmpty() throws Exception {
        Path directory = Files.createTempDirectory(temporaryDirectory, "schema-");
        Files.writeString(directory.resolve("initialization_timing.jsonl"), "");
        assertThrows(IOException.class, () -> InitializationTimingStreamLoader.load(directory, metadata(4)));
    }

    @Test
    void postObjectPresentationHasDistinctOrderedIdentity() throws Exception {
        var events = load(event(0, 0, 1, 4, "initial_objects", "resume")
                + event(0, 1, 1, 5, "initial_presentation", "vint_0")
                + event(0, 2, 1, 5, "initial_presentation", "resume")).events();
        assertEquals(InitializationTimingEvent.Phase.INITIAL_PRESENTATION, events.getLast().phase());
    }

    @Test
    void runRootUsesAbsoluteMovieBoundsAcrossSegmentGaps() throws Exception {
        var run = new com.openggf.trace.TraceRunManifest("s3k", "run", "movie.bk2", "checksum",
                List.of(), List.of());
        Path directory = Files.createTempDirectory(temporaryDirectory, "run-");
        Files.writeString(directory.resolve("initialization_timing.jsonl"),
                event(900, 0, 1, 1, "display_clear", "resume"));
        assertEquals(900, InitializationTimingStreamLoader.loadRun(directory, run, 901)
                .events().getFirst().rawFrame());
        assertThrows(IOException.class, () -> InitializationTimingStreamLoader.loadRun(directory, run, 900));
        assertThrows(IOException.class, () -> InitializationTimingStreamLoader.loadRun(directory, run, -1));
    }

    @Test
    void holdEndsRowAndEveryServiceHasAnExplicitOutcome() {
        reject(event(0, 0, 1, 1, "display_clear", "hold")
                + event(0, 1, 1, 1, "display_clear", "resume"));
        reject(event(0, 0, 1, 1, "display_clear", "vint_0"));
        reject(event(0, 0, 1, 1, "display_clear", "vint_0")
                + event(1, 0, 1, 1, "display_clear", "resume"));
    }

    private InitializationTimingSchedule load(String content) throws Exception {
        Path directory = Files.createTempDirectory(temporaryDirectory, "fixture-");
        if (content != null) Files.writeString(directory.resolve("initialization_timing.jsonl"), content);
        return InitializationTimingStreamLoader.load(directory, metadata(5));
    }

    private static TraceMetadata metadata(int schema) throws IOException {
        return new ObjectMapper().readValue("{\"game\":\"s3k\",\"zone\":\"test\",\"zone_id\":0,\"act\":1,"
                + "\"bk2_frame_offset\":0,\"trace_frame_count\":4,\"start_x\":\"0x0000\","
                + "\"start_y\":\"0x0000\",\"trace_schema\":" + schema + "}", TraceMetadata.class);
    }

    private void reject(String content) {
        IOException failure = assertThrows(IOException.class, () -> load(content));
        assertTrue(failure.getMessage().startsWith("initialization_timing.jsonl:"), failure::getMessage);
    }

    private static void gzip(Path path, byte[] content) throws IOException {
        try (GZIPOutputStream stream = new GZIPOutputStream(Files.newOutputStream(path))) { stream.write(content); }
    }

    private static String event(int row, int sequence, long load, int ordinal, String phase, String operation) {
        return "{\"event\":\"load_phase_timing\",\"raw_frame\":" + row + ",\"sequence\":" + sequence
                + ",\"load_ordinal\":" + load + ",\"phase_ordinal\":" + ordinal + ",\"phase\":\"" + phase
                + "\",\"submission_fingerprint\":\"" + FP + "\",\"operation\":\"" + operation + "\"}\n";
    }
}
