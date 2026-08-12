package com.openggf.tools.audio.completerun.s2;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestS2CompleteRunReferenceRawAdapter {
    @TempDir Path temporary;

    @Test
    void streamsPinnedRawRowsWithoutBufferingOrLosingUnsignedPayload() throws Exception {
        Path raw = temporary.resolve("raw.jsonl");
        String state = "00".repeat(8192);
        Files.writeString(raw, metadata()
                + boundary("baseline", "\"row\":769,", state)
                + "{\"type\":\"frame\",\"row\":769,\"lag\":true,\"state_hex\":\"" + state
                + "\",\"events\":[{" + event() + "}]}\n"
                + boundary("cutoff", "\"exclusive_end\":770,", state));
        var sink = new RecordingSink();

        S2CompleteRunReferenceRawAdapter.scanPrefixForTesting(raw, sink);

        assertEquals(769, sink.header.firstRow());
        assertEquals(1, sink.frames.size());
        assertEquals(true, sink.frames.getFirst().lag());
        assertArrayEquals(new byte[8192], sink.frames.getFirst().driverState());
        assertEquals(new BigInteger("18446744073709551615"),
                sink.frames.getFirst().events().getFirst().payload());
        assertEquals(770, sink.cutoff.exclusiveEnd());
    }

    @Test
    void rejectsAnyRowGapBeforeCallingTheCutoffSink() throws Exception {
        Path raw = temporary.resolve("gap.jsonl");
        String state = "00".repeat(8192);
        Files.writeString(raw, metadata()
                + boundary("baseline", "\"row\":769,", state)
                + "{\"type\":\"frame\",\"row\":770,\"lag\":false,\"state_hex\":\"" + state
                + "\",\"events\":[]}\n"
                + boundary("cutoff", "\"exclusive_end\":812,", state));
        var sink = new RecordingSink();

        assertThrows(IllegalArgumentException.class,
                () -> S2CompleteRunReferenceRawAdapter.scanPrefixForTesting(raw, sink));
        assertEquals(null, sink.cutoff);
    }

    @Test
    void rejectsDuplicateJsonFields() throws Exception {
        Path raw = temporary.resolve("duplicate.jsonl");
        String state = "00".repeat(8192);
        Files.writeString(raw, metadata().replaceFirst(
                "\\{\"type\":\"metadata\",", "{\"type\":\"metadata\",\"type\":\"metadata\",")
                + boundary("baseline", "\"row\":769,", state)
                + boundary("cutoff", "\"exclusive_end\":769,", state));

        assertThrows(IllegalArgumentException.class,
                () -> S2CompleteRunReferenceRawAdapter.scanPrefixForTesting(raw, new RecordingSink()));
    }

    @Test
    void rejectsMalformedPinnedBoundaryBeforeStartingTheTransaction() throws Exception {
        Path raw = temporary.resolve("bad-boundary.jsonl");
        String state = "00".repeat(8192);
        Files.writeString(raw, metadata()
                + boundary("baseline", "\"row\":769,", state)
                    .replace("\"ym_port0_latch\":42", "\"ym_port0_latch\":42.5")
                + boundary("cutoff", "\"exclusive_end\":769,", state));
        var sink = new RecordingSink();

        assertThrows(IllegalArgumentException.class,
                () -> S2CompleteRunReferenceRawAdapter.scanPrefixForTesting(raw, sink));
        assertEquals(0, sink.beginCalls);
        assertEquals(0, sink.abortCalls);
    }

    @Test
    void rejectsNonCanonicalPayloadAndAbiInvalidEventFields() throws Exception {
        String state = "00".repeat(8192);
        for (String changed : List.of(
                event().replace("\"payload\":\"18446744073709551615\"", "\"payload\":\"+1\""),
                event().replace("\"payload_length\":8", "\"payload_length\":9"),
                event().replace("\"reserved\":0", "\"reserved\":1"),
                event().replace("\"source_cpu\":1", "\"source_cpu\":4"),
                event().replace("\"pc\":4660", "\"pc\":18446744073709551616"),
                event().replace("\"kind\":6", "\"kind\":12"))) {
            Path raw = temporary.resolve("bad-event-" + Math.abs(changed.hashCode()) + ".jsonl");
            Files.writeString(raw, metadata()
                    + boundary("baseline", "\"row\":769,", state)
                    + "{\"type\":\"frame\",\"row\":769,\"lag\":false,\"state_hex\":\"" + state
                    + "\",\"events\":[{" + changed + "]}\n"
                    + boundary("cutoff", "\"exclusive_end\":770,", state));
            assertThrows(IllegalArgumentException.class,
                    () -> S2CompleteRunReferenceRawAdapter.scanPrefixForTesting(raw, new RecordingSink()));
        }
    }

    @Test
    void rejectsMalformedFrontierServiceAndAbortsAfterLateFailure() throws Exception {
        String state = "00".repeat(8192);
        String invalidService = boundary("cutoff", "\"exclusive_end\":770,", state)
                .replace("\"active_services\":[]", "\"active_services\":[{}]");
        Path raw = temporary.resolve("late-frontier.jsonl");
        Files.writeString(raw, metadata()
                + boundary("baseline", "\"row\":769,", state)
                + "{\"type\":\"frame\",\"row\":769,\"lag\":false,\"state_hex\":\"" + state
                + "\",\"events\":[]}\n" + invalidService);
        var sink = new RecordingSink();

        assertThrows(IllegalArgumentException.class,
                () -> S2CompleteRunReferenceRawAdapter.scanPrefixForTesting(raw, sink));
        assertEquals(1, sink.beginCalls);
        assertEquals(1, sink.abortCalls);
        assertEquals(0, sink.commitCalls);
        assertEquals(0, sink.frames.size());
        assertEquals(null, sink.cutoff);
    }

    @Test
    void commitsOnlyAfterTheValidatedCutoffAndTrailingEof() throws Exception {
        Path raw = temporary.resolve("transaction.jsonl");
        String state = "00".repeat(8192);
        Files.writeString(raw, metadata()
                + boundary("baseline", "\"row\":769,", state)
                + boundary("cutoff", "\"exclusive_end\":769,", state));
        var sink = new RecordingSink();

        S2CompleteRunReferenceRawAdapter.scanPrefixForTesting(raw, sink);

        assertEquals(1, sink.beginCalls);
        assertEquals(1, sink.commitCalls);
        assertEquals(0, sink.abortCalls);
        assertTrue(sink.cutoff != null);
    }

    @Test
    void parsesEveryStrictFrontierServiceFieldIntoTypedLosslessValues() throws Exception {
        Path raw = temporary.resolve("frontier.jsonl");
        String state = "00".repeat(8192);
        Files.writeString(raw, metadata()
                + boundary("baseline", "\"row\":769,", state)
                + boundary("cutoff", "\"exclusive_end\":769,", state)
                    .replace("\"active_services\":[]",
                            "\"active_services\":[" + service() + "]"));
        var sink = new RecordingSink();

        S2CompleteRunReferenceRawAdapter.scanPrefixForTesting(raw, sink);

        var service = sink.cutoff.activeServices().getFirst();
        assertEquals(1, service.token());
        assertEquals(3, service.kind());
        assertEquals(56, service.beginPc());
        assertEquals(0, service.chips().size());
        assertEquals(0, service.snapshots().size());
        assertEquals(0, service.ancestryTransitions().size());
    }

    @Test
    void parsesTypedChipSnapshotAndAncestryEvidenceLosslessly() throws Exception {
        Path raw = temporary.resolve("typed-frontier.jsonl");
        String state = "00".repeat(8192);
        String typed = service()
                .replace("\"chips\":[]", "\"chips\":[{\"coordinate\":7,"
                        + "\"native_ordinal\":8,\"event_kind\":3,\"subject\":1,"
                        + "\"value\":42,\"pc\":56,\"source_cpu\":1,\"data\":true,"
                        + "\"port\":0,\"register\":42}]")
                .replace("\"snapshots\":[]", "\"snapshots\":[{\"range_id\":2,"
                        + "\"source_cpu\":1,\"pc\":56,\"bytes_hex\":\"ab\"}]")
                .replace("\"ancestry_transitions\":[]", "\"ancestry_transitions\":[{"
                        + "\"coordinate\":9,\"native_ordinal\":10,"
                        + "\"previous_parent_token\":0,\"previous_depth\":0,"
                        + "\"current_parent_token\":2,\"current_depth\":1,"
                        + "\"hook_token\":20,\"source_cpu\":1,\"pc\":378}]");
        Files.writeString(raw, metadata()
                + boundary("baseline", "\"row\":769,", state)
                + boundary("cutoff", "\"exclusive_end\":769,", state)
                    .replace("\"active_services\":[]", "\"active_services\":[" + typed + "]"));
        var sink = new RecordingSink();

        S2CompleteRunReferenceRawAdapter.scanPrefixForTesting(raw, sink);

        var parsed = sink.cutoff.activeServices().getFirst();
        assertEquals(42, parsed.chips().getFirst().register());
        assertArrayEquals(new byte[] {(byte) 0xab}, parsed.snapshots().getFirst().bytes());
        assertEquals(20, parsed.ancestryTransitions().getFirst().hookToken());
    }

    @Test
    void acceptsOnlyTheNativeResetServicesExactAbsentBeginSourceShape() throws Exception {
        Path raw = temporary.resolve("reset-frontier.jsonl");
        String state = "00".repeat(8192);
        Files.writeString(raw, metadata()
                + boundary("baseline", "\"row\":769,", state)
                + boundary("cutoff", "\"exclusive_end\":769,", state)
                    .replace("\"pending_descendants\":[]",
                            "\"pending_descendants\":[" + resetService() + "]"));
        var sink = new RecordingSink();

        S2CompleteRunReferenceRawAdapter.scanPrefixForTesting(raw, sink);

        var reset = sink.cutoff.pendingDescendants().getFirst();
        assertEquals(1, reset.kind());
        assertEquals(0, reset.beginSourceCpu());
        assertEquals(0, reset.beginPc());
        assertEquals(0, reset.beginHookToken());
    }

    private static String metadata() {
        return "{\"type\":\"metadata\",\"schema\":\"openggf.s2-complete-run-audio-raw.v1\","
                + "\"rom_sha1\":\"8bca5dcef1af3e00098666fd892dc1c2a76333f9\","
                + "\"bk2_sha256\":\"e850798f882b8c580aad148bc97cb50f260cae1d336dd649fe2f4dfae6796aa5\","
                + "\"service_manifest_sha256\":\"ef8f8103c38d70e41cb09cb29751f56815a0401709dc509071aa514d614813a0\","
                + "\"first_row\":769,\"exclusive_end\":259590,\"state_start\":0,"
                + "\"state_exclusive_end\":8192}\n";
    }

    private static String boundary(String type, String coordinate, String state) {
        String active = type.equals("baseline")
                ? "[" + service().replace("\"kind\":3", "\"kind\":4") + "]"
                : "[]";
        return "{\"type\":\"" + type + "\"," + coordinate + "\"state_hex\":\"" + state
                + "\",\"ym_port0_latch\":42,\"ym_port1_latch\":161,"
                + "\"native_arm_epoch\":1,\"native_armed\":true,"
                + "\"active_services\":" + active + ",\"pending_descendants\":[]}\n";
    }

    private static String event() {
        return "\"ordinal\":0,\"service_token\":2,\"parent_token\":0,\"pc\":4660,"
                + "\"subject\":1,\"offset\":0,\"kind\":6,\"service_kind\":2,"
                + "\"depth\":1,\"source_cpu\":1,\"payload_length\":8,\"value\":0,"
                + "\"flags\":0,\"reserved\":0,\"payload\":\"18446744073709551615\"";
    }

    private static String service() {
        return "{\"token\":1,\"parent_token\":0,\"kind\":3,\"depth\":0,"
                + "\"current_parent_token\":0,\"current_depth\":0,"
                + "\"begin_coordinate\":1,\"end_coordinate\":0,"
                + "\"begin_pc\":56,\"end_pc\":0,\"begin_hook_token\":1,"
                + "\"end_hook_token\":0,\"begin_source_cpu\":1,"
                + "\"cancelled\":false,\"complete\":false,\"chips\":[],"
                + "\"snapshots\":[],\"ancestry_transitions\":[]}";
    }

    private static String resetService() {
        return "{\"token\":9,\"parent_token\":0,\"kind\":1,\"depth\":0,"
                + "\"current_parent_token\":0,\"current_depth\":0,"
                + "\"begin_coordinate\":1,\"end_coordinate\":2,"
                + "\"begin_pc\":0,\"end_pc\":0,\"begin_hook_token\":0,"
                + "\"end_hook_token\":0,\"begin_source_cpu\":0,"
                + "\"cancelled\":false,\"complete\":true,\"chips\":[],"
                + "\"snapshots\":[],\"ancestry_transitions\":[]}";
    }

    private static final class RecordingSink implements S2CompleteRunReferenceRawAdapter.Sink {
        private S2CompleteRunReferenceRawAdapter.Header header;
        private final List<S2CompleteRunReferenceRawAdapter.RawFrame> frames = new ArrayList<>();
        private S2CompleteRunReferenceRawAdapter.RawBoundary cutoff;
        private int beginCalls;
        private int commitCalls;
        private int abortCalls;
        @Override public void begin() { beginCalls++; }
        @Override public void header(S2CompleteRunReferenceRawAdapter.Header value) { header = value; }
        @Override public void baseline(S2CompleteRunReferenceRawAdapter.RawBoundary value) { }
        @Override public void frame(S2CompleteRunReferenceRawAdapter.RawFrame value) { frames.add(value); }
        @Override public void cutoff(S2CompleteRunReferenceRawAdapter.RawBoundary value) { cutoff = value; }
        @Override public void commit() { commitCalls++; }
        @Override public void abort() {
            abortCalls++;
            header = null;
            frames.clear();
            cutoff = null;
        }
    }
}
