package com.openggf.tools.audio.completerun.s1;

import static com.openggf.tools.audio.completerun.s1.S1RawContractVectors.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestS1CompleteRunReferenceRawAdapter {
    @TempDir Path temporary;

    @Test void parsesActualSeparatedS1RecordsAndCarriesServiceAcrossRows() throws Exception {
        RecordingSink sink = scan(prefix());
        assertTrue(sink.committed);
        assertEquals(List.of(860, 861), sink.frames.stream().map(S1CompleteRunReferenceRawAdapter.Frame::row).toList());
        assertEquals(1472, sink.baseline.driverState().length);
        byte[] state = sink.baseline.driverState();
        state[0] = 7;
        assertEquals(0, sink.baseline.driverState()[0]);
        assertEquals(4, sink.frames.getFirst().events().size());
        assertEquals(0, sink.frames.getLast().events().getFirst().ordinal());
    }

    @Test void preservesNumericUnsignedPayloadAndS1M68kA7Observation() throws Exception {
        StringBuilder source = new StringBuilder(event(860, 0, 0, 0, 10, 0, 0, 2, 5006, 100,
                0, 4, 3, 0, "16776626"));
        source.append(event(860, 1, 1, 0, 1, 5, 0, 2, 0x134a, 7, 0, 0, 0, 0, "0"));
        source.append(event(860, 2, 1, 0, 5, 5, 0, 2, 0x138c, 30, 0, 0, 0, 0, "0"));
        for (int offset = 0; offset < 8192; offset += 8)
            source.append(event(860, 3 + offset / 8, 1, 0, 6, 5, 0, 2, 0x138c, 30,
                    offset, 8, 0, 0, offset == 0 ? "18446744073709551615" : "0"));
        source.append(event(860, 1027, 1, 0, 7, 5, 0, 2, 0x138c, 30, 8192, 0, 0, 0, "0"));
        source.append(event(860, 1028, 1, 0, 2, 5, 0, 2, 0x138c, 8, 0, 0, 0, 0, "0"));
        String body = source.toString();
        var sink = scan(metadata() + baseline() + frame(860, body) + terminal(861));
        assertEquals("18446744073709551615", sink.frames.getFirst().events().get(3).payload().toString());
        assertEquals(4, sink.frames.getFirst().events().getFirst().payloadLength());
        for (String invalid : List.of(body.replace("18446744073709551615", "\"18446744073709551615\""),
                body.replace("18446744073709551615", "18446744073709551616"),
                body.replace("\"offset\":8192", "\"offset\":8191"),
                body.replace("\"payload_length\":4", "\"payload_length\":0"))) {
            rejectsAfterBegin(metadata() + baseline() + frame(860, invalid) + terminal(861));
        }
    }

    @Test void acceptsDeferredEvidenceWithoutMistakingFutureConsumeForCurrentOrdinal() throws Exception {
        String deferred = "{\"type\":\"dispatch\",\"row\":860,\"sound_id\":129,\"request_id\":null,"
                + "\"managed_correlation_ordinal\":0,\"native_correlation_events\":[],"
                + "\"native_ordinal\":42,\"native_hook_token\":104,\"native_service_token\":1,"
                + "\"native_parent_token\":0,\"native_marker_value\":4,\"deferred_a7\":16776626,"
                + "\"deferred_return_pc\":2916,\"consume_hook_token\":108,\"consume_pc\":465794,"
                + "\"consumed_service_token\":2,\"consume_begin_ordinal\":42}\n";
        var sink = scan(metadata() + baseline() + frame(860, deferred) + terminal(861));
        assertEquals(1, sink.frames.getFirst().managedEvidenceCount());
        assertTrue(sink.frames.getFirst().events().isEmpty());
    }

    @Test void acceptsDirectParentPromotionWithSurvivingZ80Child() throws Exception {
        String body = music(860, 0, 1, 112, 0)
                + event(860, 1, 2, 1, 1, 2, 1, 1, 0x77, 5, 0, 0, 0, 0, "0")
                + snapshot(860, 2, 1, 0, 4, 0, 1, 0xac, 10)
                + event(860, 5, 1, 0, 2, 4, 0, 1, 0xac, 13, 0, 0, 0, 0, "0")
                + event(860, 6, 2, 0, 11, 2, 0, 1, 0xac, 13, 0, 0, 0, 0, "0")
                + event(860, 7, 2, 0, 3, 2, 0, 1, 0x88, 1, 0, 0, 64, 0, "0")
                + snapshot(860, 8, 2, 0, 2, 0, 1, 0xac, 2)
                + event(860, 11, 2, 0, 2, 2, 0, 1, 0xac, 2, 0, 0, 0, 0, "0");
        assertTrue(scan(metadata() + baseline() + frame(860, body) + terminal(861)).committed);
        rejectsAfterBegin(metadata() + baseline() + frame(860, body.replace("\"parent_token\":0,\"pc\":172,\"subject\":13,\"offset\":0,\"kind\":11",
                "\"parent_token\":1,\"pc\":172,\"subject\":13,\"offset\":0,\"kind\":11")) + terminal(861));
    }

    @Test void doesNotInventEmptyNativeTerminalFrontierForOpenAsyncService() throws Exception {
        String begin = event(860, 0, 1, 0, 1, 2, 0, 1, 0x77, 1, 0, 0, 0, 0, "0");
        assertTrue(scan(metadata() + baseline() + frame(860, begin) + terminal(861)).committed);
    }

    @Test void refusesLegacyMovieSchemaAndWrongAbiBeforeOpeningTransaction() throws Exception {
        for (String bad : List.of(metadata().replace("f2e81793", "f744c814"),
                metadata().replace("raw.v1", "raw.v2"), metadata().replace("\"native_abi\":5", "\"native_abi\":4"))) {
            var sink = new RecordingSink();
            assertThrows(Exception.class, () -> read(bad + baseline() + terminal(860), sink, false));
            assertEquals(0, sink.begins);
            assertFalse(sink.aborted);
        }
    }

    @Test void rejectsRowsUnknownRecordsNativeOwnershipAndLateFailuresTransactionally() throws Exception {
        for (String bad : List.of(prefix().replace("\"row\":861", "\"row\":862"),
                prefix().replace("\"ordinal\":2", "\"ordinal\":7"),
                prefix().replace("\"kind\":4", "\"kind\":12"),
                prefix().replace("\"type\":\"native_event\"", "\"type\":\"unknown\""),
                prefix().replace("\"service_token\":1", "\"service_token\":2").replace("\"kind\":1", "\"kind\":3"),
                prefix().replace("\"overflows\":0", "\"overflows\":1"),
                prefix() + "{}\n", prefix().stripTrailing(),
                prefix().replace("\"payload\":0", "\"payload\":0,\"payload\":0"))) {
            rejectsAfterBegin(bad);
        }
    }

    @Test void fullScanRejectsPrefixAndSinkFailurePreservesAbortFailure() throws Exception {
        var sink = new RecordingSink();
        assertThrows(IllegalArgumentException.class, () -> read(prefix(), sink, true));
        assertTrue(sink.aborted);
        var broken = new RecordingSink() {
            @Override public void frame(S1CompleteRunReferenceRawAdapter.Frame frame) throws IOException {
                throw new IOException("primary");
            }
            @Override public void abort() throws IOException { throw new IOException("cleanup"); }
        };
        IOException failure = assertThrows(IOException.class, () -> read(prefix(), broken, false));
        assertEquals("primary", failure.getMessage());
        assertEquals("cleanup", failure.getSuppressed()[0].getMessage());
    }

    @Test void fullScannerConsumesEveryPinnedRowWithoutBufferingTheRun() throws Exception {
        Path raw = temporary.resolve("full-contract-vector.jsonl");
        try (var writer = Files.newBufferedWriter(raw)) {
            writer.write(metadata());
            writer.write(baseline());
            for (int row = 860; row < 225_101; row++) writer.write(frame(row, ""));
            writer.write(terminal(225_101));
        }
        int[] count = {0};
        var sink = new RecordingSink() {
            @Override public void frame(S1CompleteRunReferenceRawAdapter.Frame value) {
                assertEquals(860 + count[0]++, value.row());
            }
        };
        S1CompleteRunReferenceRawAdapter.scan(raw, sink);
        assertEquals(224_241, count[0]);
        assertTrue(sink.committed);
    }

    @Test void rejectsAncestorChipWritesMissingSnapshotsAndMissingPromotion() throws Exception {
        String parentChild = music(860, 0, 1, 112, 0)
                + event(860, 1, 2, 1, 1, 2, 1, 1, 0x77, 5, 0, 0, 0, 0, "0");
        rejectsAfterBegin(metadata() + baseline() + frame(860, parentChild
                + music(860, 2, 3, 1, 64)) + terminal(861));
        rejectsAfterBegin(metadata() + baseline() + frame(860,
                music(860, 0, 1, 112, 0) + music(860, 1, 2, 131, 0)) + terminal(861));
        String parentEnd = parentChild + snapshot(860, 2, 1, 0, 4, 0, 1, 0xac, 10)
                + event(860, 5, 1, 0, 2, 4, 0, 1, 0xac, 13, 0, 0, 0, 0, "0");
        rejectsAfterBegin(metadata() + baseline() + frame(860, parentEnd) + terminal(861));
    }

    private RecordingSink scan(String content) throws Exception {
        var sink = new RecordingSink();
        read(content, sink, false);
        return sink;
    }

    private void rejectsAfterBegin(String content) throws Exception {
        var sink = new RecordingSink();
        assertThrows(Exception.class, () -> read(content, sink, false));
        assertEquals(1, sink.begins);
        assertTrue(sink.aborted);
        assertFalse(sink.committed);
        assertTrue(sink.frames.isEmpty());
    }

    private void read(String content, RecordingSink sink, boolean full) throws Exception {
        Path raw = Files.createTempFile(temporary, "raw-", ".jsonl");
        Files.writeString(raw, content);
        if (full) S1CompleteRunReferenceRawAdapter.scan(raw, sink);
        else S1CompleteRunReferenceRawAdapter.scanPrefixForTesting(raw, sink);
    }

    private static class RecordingSink implements S1CompleteRunReferenceRawAdapter.Sink {
        int begins;
        boolean committed, aborted;
        S1CompleteRunReferenceRawAdapter.Baseline baseline;
        List<S1CompleteRunReferenceRawAdapter.Frame> frames = new ArrayList<>();
        @Override public void begin() { begins++; }
        @Override public void baseline(S1CompleteRunReferenceRawAdapter.Baseline value) { baseline = value; }
        @Override public void frame(S1CompleteRunReferenceRawAdapter.Frame value) throws IOException { frames.add(value); }
        @Override public void terminal(int end) { }
        @Override public void commit() { committed = true; }
        @Override public void abort() throws IOException { aborted = true; frames.clear(); }
    }
}
