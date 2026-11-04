package com.openggf.trace.timing;

import com.openggf.trace.TraceRunManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class TestInitializationHardwareTimingStream {
    @TempDir Path directory;
    private static final String FP = "sha256:" + "a".repeat(64);
    private static final TraceRunManifest RUN = new TraceRunManifest("s3k", "run", "movie.bk2", "checksum", List.of(), List.of());
    private static final String EVENT = "{\"event\":\"hardware_work_completed\",\"raw_frame\":11,\"load_ordinal\":7,\"boundary\":\"post_objects\",\"kind\":\"kos_module_queue\",\"ordinal\":9,\"submission_fingerprint\":\"" + FP + "\"}\n";

    @Test void onlyDedicatedInputProvidesAbsoluteQueueRows() throws Exception {
        Files.writeString(directory.resolve("hardware_timing_interstitial.jsonl"), "provenance is not readiness input\n");
        assertFalse(load().hasRecordedInput());
        try (var gzip = new GZIPOutputStream(Files.newOutputStream(directory.resolve("initialization_hardware_timing.jsonl.gz")))) {
            gzip.write(EVENT.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        var input = load();
        assertEquals(11, input.forLoad(7).edges().getFirst().rawFrame());
        assertEquals(9, input.forLoad(7).edges().getFirst().ordinal());
        assertTrue(input.hasLoad(7));
        assertFalse(input.hasLoad(8));
        assertTrue(input.forLoad(8).edges().isEmpty());
        assertThrows(IllegalStateException.class, () -> input.verifyObservedLoads(java.util.Set.of()));
        input.verifyObservedLoads(java.util.Set.of(7L));
        assertThrows(IllegalArgumentException.class, () -> input.requireMatchingPhases(InitializationTimingSchedule.empty()));
        input.requireMatchingPhases(new InitializationTimingSchedule(List.of(
                phase(10, InitializationTimingEvent.Operation.HOLD),
                phase(11, InitializationTimingEvent.Operation.HOLD),
                phase(12, InitializationTimingEvent.Operation.RESUME))));
    }

    @Test void rejectsGameplayFieldsMalformedIdentitiesAndOutOfOrderRows() throws Exception {
        for (String invalid : List.of(
                EVENT.replace("{", "{\"v_int_run_count\":50,"),
                EVENT.replace("\"raw_frame\":11", "\"raw_frame\":11,\"raw_frame\":11"),
                EVENT.replace("\"load_ordinal\":7", "\"load_ordinal\":0"),
                EVENT.replace("\"load_ordinal\":7", "\"load_ordinal\":7.0"),
                EVENT.replace("\"raw_frame\":11", "\"raw_frame\":20"),
                EVENT.replace("kos_module_queue", "kos_decompression_queue"),
                EVENT + EVENT,
                EVENT + EVENT.replace("\"raw_frame\":11", "\"raw_frame\":10").replace("\"ordinal\":9", "\"ordinal\":10"),
                EVENT + EVENT.replace("\"raw_frame\":11", "\"raw_frame\":12").replace("\"ordinal\":9", "\"ordinal\":8"))) {
            Files.writeString(directory.resolve("initialization_hardware_timing.jsonl"), invalid);
            assertThrows(IOException.class, this::load, invalid);
        }
    }

    @Test void presentEmptyIsDistinctFromMissingAndBoundsAreStrict() throws Exception {
        assertFalse(load().hasRecordedInput());
        Files.writeString(directory.resolve("initialization_hardware_timing.jsonl"), "");
        assertTrue(load().hasRecordedInput());
        assertThrows(IOException.class, () -> InitializationHardwareTimingStream.loadRun(directory, RUN, -1));
    }

    private InitializationHardwareTimingStream load() throws IOException {
        return InitializationHardwareTimingStream.loadRun(directory, RUN, 20);
    }
    private static InitializationTimingEvent phase(int frame, InitializationTimingEvent.Operation operation) {
        return new InitializationTimingEvent(frame, 0, 7, 1, InitializationTimingEvent.Phase.DISPLAY_CLEAR, FP, operation);
    }
}
