package com.openggf.tools.audio.completerun.s3k;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestS3kCompleteRunReferenceRawAdapter {
    @TempDir Path temporary;

    @Test
    void streamsPinnedRawRowsWithoutBufferingOrLosingUnsignedPayload() throws Exception {
        Path raw = temporary.resolve("raw.jsonl");
        String state = "00".repeat(1024);
        Files.writeString(raw, metadata()
                + boundary("baseline", "\"row\":810,", state)
                + "{\"type\":\"frame\",\"row\":810,\"lag\":true,\"state_hex\":\"" + state
                + "\",\"events\":[{" + event() + "}]}\n"
                + boundary("cutoff", "\"exclusive_end\":811,", state));
        var sink = new RecordingSink();

        S3kCompleteRunReferenceRawAdapter.scanPrefixForTesting(raw, sink);

        assertEquals(810, sink.header.firstRow());
        assertEquals(1, sink.frames.size());
        assertEquals(true, sink.frames.getFirst().lag());
        assertArrayEquals(new byte[1024], sink.frames.getFirst().driverState());
        assertEquals(new BigInteger("18446744073709551615"),
                sink.frames.getFirst().events().getFirst().payload());
        assertEquals(811, sink.cutoff.exclusiveEnd());
    }

    @Test
    void rejectsAnyRowGapBeforeCallingTheCutoffSink() throws Exception {
        Path raw = temporary.resolve("gap.jsonl");
        String state = "00".repeat(1024);
        Files.writeString(raw, metadata()
                + boundary("baseline", "\"row\":810,", state)
                + "{\"type\":\"frame\",\"row\":811,\"lag\":false,\"state_hex\":\"" + state
                + "\",\"events\":[]}\n"
                + boundary("cutoff", "\"exclusive_end\":812,", state));
        var sink = new RecordingSink();

        assertThrows(IllegalArgumentException.class,
                () -> S3kCompleteRunReferenceRawAdapter.scanPrefixForTesting(raw, sink));
        assertEquals(null, sink.cutoff);
    }

    @Test
    void rejectsDuplicateJsonFields() throws Exception {
        Path raw = temporary.resolve("duplicate.jsonl");
        String state = "00".repeat(1024);
        Files.writeString(raw, metadata().replaceFirst(
                "\\{\"type\":\"metadata\",", "{\"type\":\"metadata\",\"type\":\"metadata\",")
                + boundary("baseline", "\"row\":810,", state)
                + boundary("cutoff", "\"exclusive_end\":810,", state));

        assertThrows(IllegalArgumentException.class,
                () -> S3kCompleteRunReferenceRawAdapter.scanPrefixForTesting(raw, new RecordingSink()));
    }

    private static String metadata() {
        return "{\"type\":\"metadata\",\"schema\":\"openggf.s3k-complete-run-audio-raw.v1\","
                + "\"rom_sha1\":\"cfbf98c36c776677290a872547ac47c53d2761d6\","
                + "\"bk2_sha256\":\"aa892856df22b7bb1fe5accb48db10b90dc26845d1dccee90352da30349f53cc\","
                + "\"service_manifest_sha256\":\"ef8f8103c38d70e41cb09cb29751f56815a0401709dc509071aa514d614813a0\","
                + "\"first_row\":810,\"exclusive_end\":434417,\"state_start\":7168,"
                + "\"state_exclusive_end\":8192}\n";
    }

    private static String boundary(String type, String coordinate, String state) {
        return "{\"type\":\"" + type + "\"," + coordinate + "\"state_hex\":\"" + state
                + "\",\"ym_port0_latch\":40,\"ym_port1_latch\":161,"
                + "\"native_arm_epoch\":1,\"native_armed\":true,"
                + "\"active_services\":[],\"pending_descendants\":[]}\n";
    }

    private static String event() {
        return "\"ordinal\":0,\"service_token\":2,\"parent_token\":0,\"pc\":4660,"
                + "\"subject\":1,\"offset\":0,\"kind\":3,\"service_kind\":2,"
                + "\"depth\":1,\"source_cpu\":1,\"payload_length\":0,\"value\":42,"
                + "\"flags\":0,\"reserved\":0,\"payload\":\"18446744073709551615\"";
    }

    private static final class RecordingSink implements S3kCompleteRunReferenceRawAdapter.Sink {
        private S3kCompleteRunReferenceRawAdapter.Header header;
        private final List<S3kCompleteRunReferenceRawAdapter.RawFrame> frames = new ArrayList<>();
        private S3kCompleteRunReferenceRawAdapter.RawBoundary cutoff;
        @Override public void header(S3kCompleteRunReferenceRawAdapter.Header value) { header = value; }
        @Override public void baseline(S3kCompleteRunReferenceRawAdapter.RawBoundary value) { }
        @Override public void frame(S3kCompleteRunReferenceRawAdapter.RawFrame value) { frames.add(value); }
        @Override public void cutoff(S3kCompleteRunReferenceRawAdapter.RawBoundary value) { cutoff = value; }
    }
}
