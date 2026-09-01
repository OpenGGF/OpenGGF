package com.openggf.tools.audio.completerun.s3k;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.openggf.tests.RomTestUtils;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestS3kSubmissionReferenceV2 {
    @TempDir Path temporary;

    @Test
    void retainsAuthenticatedIntraAdvanceFeBeforeFollowingMusicService() throws Exception {
        Path raw = Files.writeString(temporary.resolve("submission-v2.jsonl"), rawV2(false, false));

        var frames = new RecordingSink();
        S3kCompleteRunReferenceRawAdapter.scanSubmissionV2PrefixForTesting(raw, frames);
        var submission = frames.frames.getFirst().submissions().getFirst();
        assertEquals(0xfe, submission.request());
        assertEquals(1, submission.beginOrdinal());
        assertEquals(5, submission.endOrdinal());
        assertEquals(0x1358, submission.beginPc());
        assertEquals(0x1374, submission.endPc());
        assertEquals(List.of(0xfe), submission.mailboxBytes());

        var records = new S3kCompleteRunReferenceProjector()
                .projectSubmissionV2PrefixForTesting(raw, rom()).records();
        var frame = (CompleteRunAudioTrace.Frame) records.get(1);
        assertEquals(1, frame.requests().size());
        assertEquals(0xfe, frame.requests().getFirst().nativeId());
        assertEquals("s3k.sk.command.stop_sega_pcm",
                frame.requests().getFirst().contentKey());
        assertEquals("zMusicNumber", frame.requests().getFirst().queueSource());
        assertNull(frame.requests().getFirst().queueSlot());
        var services = frame.nativeDiagnostics().services();
        long submissionBegin = services.stream()
                .filter(value -> value.kind().equals("MusicMailboxSubmission"))
                .findFirst().orElseThrow().beginOrdinal();
        long updateMusicBegin = services.stream()
                .filter(value -> value.kind().equals("UpdateMusic"))
                .findFirst().orElseThrow().beginOrdinal();
        assertEquals(true, submissionBegin < updateMusicBegin);
    }

    @Test
    void rejectsDuplicateAndReversedSubmissionEvidence() throws Exception {
        String valid = rawV2(false, false);
        String one = submission(2, 1, 5, "fe", 0xfe);
        for (String malformed : List.of(
                valid.replace("\"submissions\":[" + one + "]",
                        "\"submissions\":[" + one + "," + one + "]"),
                rawV2(true, true))) {
            Path raw = Files.writeString(temporary.resolve(
                    "malformed-" + Math.abs(malformed.hashCode()) + ".jsonl"), malformed);
            assertThrows(IllegalArgumentException.class, () ->
                    S3kCompleteRunReferenceRawAdapter.scanSubmissionV2PrefixForTesting(
                            raw, new RecordingSink()));
        }
    }

    @Test
    void v1CannotClaimSubmissionCapabilityAndFailsBeforeSinkBegin() throws Exception {
        String state = "00".repeat(1024);
        Path raw = Files.writeString(temporary.resolve("raw-v1.jsonl"),
                "{\"type\":\"metadata\",\"schema\":\"openggf.s3k-complete-run-audio-raw.v1\","
                + "\"rom_sha1\":\"cfbf98c36c776677290a872547ac47c53d2761d6\","
                + "\"bk2_sha256\":\"aa892856df22b7bb1fe5accb48db10b90dc26845d1dccee90352da30349f53cc\","
                + "\"service_manifest_sha256\":\"ef8f8103c38d70e41cb09cb29751f56815a0401709dc509071aa514d614813a0\","
                + "\"first_row\":810,\"exclusive_end\":434417,\"state_start\":7168,"
                + "\"state_exclusive_end\":8192}\n"
                + boundary("baseline", "\"row\":810,", state)
                + boundary("cutoff", "\"exclusive_end\":810,", state));
        var sink = new RecordingSink();

        assertThrows(IllegalArgumentException.class, () ->
                S3kCompleteRunReferenceRawAdapter.scanSubmissionV2PrefixForTesting(raw, sink));
        assertEquals(0, sink.beginCalls);
    }

    @Test
    void doesNotInferSubmissionFromStateChipOrOutputEvidence() throws Exception {
        String state = "fe" + "00".repeat(1023);
        String events = event(0, 1, 1, 0, 8, 0, 1, 4432, 14, 0, 0, 0, "0")
                + "," + event(1, 4, 1, 0, 8, 0, 1, 4433, 0, 0, 0, 0xfe, "0")
                + "," + event(2, 5, 1, 0, 8, 0, 1, 4453, 1, 0, 0, 0, "0")
                + "," + event(3, 6, 1, 0, 8, 0, 1, 4453, 1, 0, 1, 0, "0")
                + "," + event(4, 7, 1, 0, 8, 0, 1, 4453, 1, 1, 0, 0, "0")
                + "," + event(5, 2, 1, 0, 8, 0, 1, 4453, 16, 0, 0, 0, "0");
        String rawText = metadata() + boundary("baseline", "\"row\":0,", state)
                + "{\"type\":\"frame\",\"row\":0,\"lag\":false,\"state_hex\":\""
                + state + "\",\"events\":[" + events + "],\"submissions\":[]}\n"
                + boundary("cutoff", "\"exclusive_end\":1,", state);
        Path raw = Files.writeString(temporary.resolve("no-inference.jsonl"), rawText);
        var sink = new RecordingSink();

        S3kCompleteRunReferenceRawAdapter.scanSubmissionV2PrefixForTesting(raw, sink);

        assertEquals(List.of(), sink.frames.getFirst().submissions());
        var frame = (CompleteRunAudioTrace.Frame) new S3kCompleteRunReferenceProjector()
                .projectSubmissionV2PrefixForTesting(raw, rom()).records().get(1);
        assertEquals(List.of(), frame.requests());
        assertEquals(List.of(new CompleteRunAudioTrace.PsgWrite(0, 0xfe)), frame.chipEvents());
    }

    private static String rawV2(boolean twoSubmissions, boolean reverse) {
        String state = "00".repeat(1024);
        List<String> events = new ArrayList<>();
        events.add(event(0, 1, 1, 0, 8, 0, 1, 0x1150, 14, 0, 0, 0, "0"));
        addSubmission(events, 1, 2, 0xfe);
        int at = 6;
        if (twoSubmissions) {
            addSubmission(events, at, 3, 0xff);
            at += 5;
        }
        int vintToken = twoSubmissions ? 4 : 3;
        int updateToken = vintToken + 1;
        int musicToken = vintToken + 2;
        events.add(event(at++, 5, 1, 0, 8, 0, 1, 0x1165, 1, 0, 0, 0, "0"));
        events.add(event(at++, 6, 1, 0, 8, 0, 1, 0x1165, 1, 0, 1, 0, "0"));
        events.add(event(at++, 7, 1, 0, 8, 0, 1, 0x1165, 1, 1, 0, 0, "0"));
        events.add(event(at++, 2, 1, 0, 8, 0, 1, 0x1165, 16, 0, 0, 0, "0"));
        events.add(event(at++, 1, vintToken, 0, 3, 0, 1, 56, 2, 0, 0, 0, "0"));
        events.add(event(at++, 1, updateToken, vintToken, 11, 1, 1, 283, 23, 0, 0, 0, "0"));
        events.add(event(at++, 5, updateToken, vintToken, 11, 1, 1, 289, 1, 0, 0, 0, "0"));
        events.add(event(at++, 6, updateToken, vintToken, 11, 1, 1, 289, 1, 0, 1, 0, "0"));
        events.add(event(at++, 7, updateToken, vintToken, 11, 1, 1, 289, 1, 1, 0, 0, "0"));
        events.add(event(at++, 2, updateToken, vintToken, 11, 1, 1, 289, 24, 0, 0, 0, "0"));
        events.add(event(at++, 1, musicToken, vintToken, 12, 1, 1, 289, 24, 0, 0, 0, "0"));
        events.add(event(at++, 5, musicToken, vintToken, 12, 1, 1, 69, 1, 0, 0, 0, "0"));
        events.add(event(at++, 6, musicToken, vintToken, 12, 1, 1, 69, 1, 0, 1, 0, "0"));
        events.add(event(at++, 7, musicToken, vintToken, 12, 1, 1, 69, 1, 1, 0, 0, "0"));
        events.add(event(at++, 2, musicToken, vintToken, 12, 1, 1, 69, 25, 0, 0, 0, "0"));
        events.add(event(at++, 5, vintToken, 0, 3, 0, 1, 132, 1, 0, 0, 0, "0"));
        events.add(event(at++, 6, vintToken, 0, 3, 0, 1, 132, 1, 0, 1, 0, "0"));
        events.add(event(at++, 7, vintToken, 0, 3, 0, 1, 132, 1, 1, 0, 0, "0"));
        events.add(event(at, 2, vintToken, 0, 3, 0, 1, 132, 3, 0, 0, 0, "0"));
        String first = submission(2, 1, 5, "fe", 0xfe);
        String second = submission(3, 6, 10, "ff", 0xff);
        String submissions = twoSubmissions
                ? reverse ? second + "," + first : first + "," + second : first;
        return metadata() + boundary("baseline", "\"row\":0,", state)
                + "{\"type\":\"frame\",\"row\":0,\"lag\":false,\"state_hex\":\""
                + state + "\",\"events\":[" + String.join(",", events)
                + "],\"submissions\":[" + submissions + "]}\n"
                + boundary("cutoff", "\"exclusive_end\":1,", state);
    }

    private static void addSubmission(List<String> events, int ordinal, int token, int request) {
        events.add(event(ordinal, 1, token, 1, 13, 1, 2, 0x1358, 27, 0, 0, 0, "0"));
        events.add(event(ordinal + 1, 5, token, 1, 13, 1, 2, 0x1374, 2, 0, 0, 0, "0"));
        events.add(event(ordinal + 2, 6, token, 1, 13, 1, 2, 0x1374, 2, 0, 1, 0,
                Integer.toString(request)));
        events.add(event(ordinal + 3, 7, token, 1, 13, 1, 2, 0x1374, 2, 1, 0, 0, "0"));
        events.add(event(ordinal + 4, 2, token, 1, 13, 1, 2, 0x1374, 28, 0, 0, 0, "0"));
    }

    private static String metadata() {
        return "{\"type\":\"metadata\",\"schema\":\"openggf.s3k-complete-run-audio-raw.v2\","
                + "\"rom_sha1\":\"cfbf98c36c776677290a872547ac47c53d2761d6\","
                + "\"service_manifest_sha256\":\"a1736a1ec5e279299f15177192eefc737efbbe4d046d3260a942f7cb3074a16c\","
                + "\"first_row\":0,\"exclusive_end\":1,\"state_start\":7168,"
                + "\"state_exclusive_end\":8192,\"authority\":\"UNBOUND_TEST_ONLY\"}\n";
    }

    private static String boundary(String type, String coordinate, String state) {
        return "{\"type\":\"" + type + "\"," + coordinate + "\"state_hex\":\"" + state
                + "\",\"ym_port0_latch\":0,\"ym_port1_latch\":0,"
                + "\"native_arm_epoch\":0,\"native_armed\":true,"
                + "\"active_services\":[],\"pending_descendants\":[]}\n";
    }

    private static String submission(int token, int begin, int end, String mailbox, int request) {
        return "{\"service_token\":" + token + ",\"parent_token\":1,"
                + "\"begin_ordinal\":" + begin + ",\"end_ordinal\":" + end + ","
                + "\"begin_pc\":4952,\"end_pc\":4980,\"begin_hook_token\":27,"
                + "\"end_hook_token\":28,\"mailbox_hex\":\"" + mailbox + "\","
                + "\"request\":" + request + "}";
    }

    private static String event(int ordinal, int kind, int token, int parent, int serviceKind,
            int depth, int cpu, int pc, int subject, int offset, int length, int value,
            String payload) {
        return "{\"ordinal\":" + ordinal + ",\"service_token\":" + token
                + ",\"parent_token\":" + parent + ",\"pc\":" + pc
                + ",\"subject\":" + subject + ",\"offset\":" + offset
                + ",\"kind\":" + kind + ",\"service_kind\":" + serviceKind
                + ",\"depth\":" + depth + ",\"source_cpu\":" + cpu
                + ",\"payload_length\":" + length + ",\"value\":" + value
                + ",\"flags\":0,\"reserved\":0,\"payload\":\"" + payload + "\"}";
    }

    private static Path rom() {
        File value = RomTestUtils.ensureSonic3kRomAvailable();
        assumeTrue(value != null, "Sonic 3&K locked-on ROM not available — skipping test");
        try { return value.toPath().toRealPath(); }
        catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
    }

    private static final class RecordingSink implements S3kCompleteRunReferenceRawAdapter.Sink {
        private final List<S3kCompleteRunReferenceRawAdapter.RawFrame> frames = new ArrayList<>();
        private int beginCalls;
        @Override public void begin() { beginCalls++; }
        @Override public void header(S3kCompleteRunReferenceRawAdapter.Header value) { }
        @Override public void baseline(S3kCompleteRunReferenceRawAdapter.RawBoundary value) { }
        @Override public void frame(S3kCompleteRunReferenceRawAdapter.RawFrame value) { frames.add(value); }
        @Override public void cutoff(S3kCompleteRunReferenceRawAdapter.RawBoundary value) { }
        @Override public void commit() { }
        @Override public void abort() { frames.clear(); }
    }
}
