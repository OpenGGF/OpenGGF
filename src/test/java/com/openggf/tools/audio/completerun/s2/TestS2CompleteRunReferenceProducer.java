package com.openggf.tools.audio.completerun.s2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.openggf.tests.RomTestUtils;
import com.openggf.tools.audio.completerun.CompleteRunAudioProducer;
import com.openggf.tools.audio.completerun.CompleteRunAudioCaptureStore;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestS2CompleteRunReferenceProducer {
    @TempDir Path temporary;

    @Test
    void projectsValidatedStateAndCutoffChipsWithoutInventingRequestsOrDecisions() throws Exception {
        Path raw = Files.writeString(temporary.resolve("s2-prefix.jsonl"), rawPrefix(false));
        Path output = temporary.resolve("s2-capture").toAbsolutePath();
        new S2CompleteRunReferenceProjector().projectPrefixForTesting(raw.toAbsolutePath(), rom(), output);
        List<CompleteRunAudioTrace.Record> records = records(output);
        CompleteRunAudioTrace.Baseline baseline = (CompleteRunAudioTrace.Baseline) records.getFirst();
        CompleteRunAudioTrace.Frame frame = (CompleteRunAudioTrace.Frame) records.get(1);
        CompleteRunAudioTrace.CutoffFrontier cutoff =
                (CompleteRunAudioTrace.CutoffFrontier) records.get(2);
        var catalog = S2CompleteRunAssetCatalog.load(rom());
        assertEquals(S2CompleteRunStateNormalizer.normalizeReference(
                S2CompleteRunStateDecoder.decode(new byte[8192], catalog), catalog.assets()), baseline.state());
        assertEquals(baseline.state(), frame.services().getFirst().state());
        assertEquals(baseline.state(), cutoff.terminalState());
        assertEquals(List.of(), frame.requests());
        assertEquals(1, frame.services().size());
        assertEquals(List.of(), frame.services().getFirst().decisions());
        assertEquals(List.of(new CompleteRunAudioTrace.PsgWrite(0, 0x44)), frame.rawChipEvents());
        assertEquals(List.of(), cutoff.rawChipEvents());
    }

    @Test
    void assignsGloballyContiguousCanonicalOrdinalsAcrossFrames() throws Exception {
        Path raw = Files.writeString(temporary.resolve("s2-two-frames.jsonl"), twoFramePrefix());

        List<CompleteRunAudioTrace.Record> records = new S2CompleteRunReferenceProjector()
                .projectPrefixForTesting(raw.toAbsolutePath(), rom()).records();
        CompleteRunAudioTrace.Frame first = (CompleteRunAudioTrace.Frame) records.get(1);
        CompleteRunAudioTrace.Frame second = (CompleteRunAudioTrace.Frame) records.get(2);

        assertEquals(0, first.services().getFirst().ordinal());
        assertEquals(1, second.services().getFirst().ordinal());
        assertEquals(0, first.rawChipEvents().getFirst().ordinal());
        assertEquals(1, second.rawChipEvents().getFirst().ordinal());
    }

    @Test
    void lateRawFailureAbortsThePrivateProjectionTransaction() throws Exception {
        Path raw = Files.writeString(temporary.resolve("invalid.jsonl"), rawPrefix(true));
        Path output = temporary.resolve("invalid-capture").toAbsolutePath();
        assertThrows(IllegalArgumentException.class, () ->
                new S2CompleteRunReferenceProjector().projectPrefixForTesting(raw.toAbsolutePath(), rom(), output));
        assertFalse(Files.exists(output));
    }

    @Test
    void fixedProducerRejectsEveryMismatchedTypedRequestBeforePublication() throws Exception {
        S2CompleteRunReferenceProducer producer = new S2CompleteRunReferenceProducer();
        CompleteRunAudioProducer.Request valid = validRequest(temporary.resolve("output").toAbsolutePath());

        assertThrows(IllegalArgumentException.class, () -> producer.capture(new CompleteRunAudioProducer.Request(
                CompleteRunAudioTrace.ProducerKind.OPENGGF, valid.profileId(), valid.rom(), valid.bk2(),
                valid.runManifest(), valid.referenceHome(), valid.output())));
        assertThrows(IllegalArgumentException.class, () -> producer.capture(new CompleteRunAudioProducer.Request(
                valid.producerKind(), "wrong", valid.rom(), valid.bk2(), valid.runManifest(),
                valid.referenceHome(), valid.output())));
        assertThrows(IllegalArgumentException.class, () -> producer.capture(new CompleteRunAudioProducer.Request(
                valid.producerKind(), valid.profileId(), valid.runManifest(), valid.bk2(), valid.runManifest(),
                valid.referenceHome(), valid.output())));
        assertThrows(IllegalArgumentException.class, () -> producer.capture(new CompleteRunAudioProducer.Request(
                valid.producerKind(), valid.profileId(), valid.rom(), valid.runManifest(), valid.runManifest(),
                valid.referenceHome(), valid.output())));
        assertThrows(IllegalArgumentException.class, () -> producer.capture(new CompleteRunAudioProducer.Request(
                valid.producerKind(), valid.profileId(), valid.rom(), valid.bk2(), valid.bk2(),
                valid.referenceHome(), valid.output())));
        assertThrows(IllegalArgumentException.class, () -> producer.capture(new CompleteRunAudioProducer.Request(
                valid.producerKind(), valid.profileId(), valid.rom(), valid.bk2(), valid.runManifest(),
                valid.rom(), valid.output())));
        Files.createDirectory(valid.output());
        assertThrows(java.nio.file.FileAlreadyExistsException.class, () -> producer.capture(valid));
        Files.delete(valid.output());
        assertThrows(IllegalStateException.class, () -> producer.capture(valid));
        assertFalse(Files.exists(valid.output()));
    }

    @Test
    void testOnlyAuthenticatedPipelinePropagatesSubprocessAndAdapterFailuresWithoutPublication()
            throws Exception {
        S2CompleteRunReferenceProducer producer = new S2CompleteRunReferenceProducer();
        Path failedOutput = temporary.resolve("subprocess-output").toAbsolutePath();
        CompleteRunAudioProducer.Request failed = withReferenceHome(validRequest(failedOutput),
                fakeTraceChaser("exit 23\n"));
        assertThrows(java.io.IOException.class, () -> producer.capturePipelineForTesting(
                failed, S2CompleteRunReferenceProjector.fullSyntheticMetadataForTesting()));
        assertFalse(Files.exists(failedOutput));

        Path invalidOutput = temporary.resolve("adapter-output").toAbsolutePath();
        String invalidRaw = "output=\nwhile [ \"$#\" -gt 0 ]; do "
                + "if [ \"$1\" = --output ]; then output=$2; shift 2; else shift; fi; done\n"
                + "printf '{}\\n' > \"$output\"\n";
        CompleteRunAudioProducer.Request invalid = withReferenceHome(validRequest(invalidOutput),
                fakeTraceChaser(invalidRaw));
        IllegalArgumentException adapterFailure = assertThrows(IllegalArgumentException.class,
                () -> producer.capturePipelineForTesting(
                        invalid, S2CompleteRunReferenceProjector.fullSyntheticMetadataForTesting()));
        assertTrue(adapterFailure.getMessage().startsWith("S2 raw"));
        assertFalse(Files.exists(invalidOutput));
    }

    private CompleteRunAudioProducer.Request validRequest(Path output) {
        Path root = Path.of("").toAbsolutePath();
        return new CompleteRunAudioProducer.Request(CompleteRunAudioTrace.ProducerKind.REFERENCE,
                S2CompleteRunAudioProfile.ID, rom(),
                root.resolve("src/test/resources/traces/s2/runs/s2-sonic-tails-complete-emeralds/sonic-2-sonic-tails-complete-emeralds.bk2"),
                root.resolve("src/test/resources/traces/s2/runs/s2-sonic-tails-complete-emeralds/run_manifest.json"),
                root.resolve("tools/tracechaser"), output);
    }

    private static CompleteRunAudioProducer.Request withReferenceHome(
            CompleteRunAudioProducer.Request request, Path referenceHome) {
        return new CompleteRunAudioProducer.Request(request.producerKind(), request.profileId(), request.rom(),
                request.bk2(), request.runManifest(), referenceHome, request.output());
    }

    private Path fakeTraceChaser(String body) throws Exception {
        Path root = Files.createTempDirectory(temporary, "tracechaser-").toAbsolutePath();
        Path tool = Files.createDirectories(root.resolve("bizhawk-headless"));
        Path fixtures = Files.createDirectories(tool.resolve("fixtures"));
        Path launcher = Files.writeString(tool.resolve("run-complete-audio.sh"), "#!/bin/sh\n" + body);
        launcher.toFile().setExecutable(true, true);
        Files.writeString(fixtures.resolve("gpgx-audio-service-manifests-v1.json"), "manifest");
        Files.writeString(fixtures.resolve("gpgx-audio-capability-v1.json"), "capability");
        return root;
    }

    private static Path rom() {
        File value = RomTestUtils.ensureSonic2RomAvailable();
        assumeTrue(value != null, "Sonic 2 REV01 ROM not available — skipping test");
        try { return value.toPath().toRealPath(); }
        catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
    }

    private static String rawPrefix(boolean trailing) {
        String state = "00".repeat(8192);
        String metadata = "{\"type\":\"metadata\",\"schema\":\"openggf.s2-complete-run-audio-raw.v1\","
                + "\"rom_sha1\":\"8bca5dcef1af3e00098666fd892dc1c2a76333f9\","
                + "\"bk2_sha256\":\"e850798f882b8c580aad148bc97cb50f260cae1d336dd649fe2f4dfae6796aa5\","
                + "\"service_manifest_sha256\":\"ef8f8103c38d70e41cb09cb29751f56815a0401709dc509071aa514d614813a0\","
                + "\"first_row\":769,\"exclusive_end\":259590,\"state_start\":0,\"state_exclusive_end\":8192}\n";
        String baseline = boundary("baseline", "\"row\":769,", state, "[" + dpcm() + "]");
        String frame = "{\"type\":\"frame\",\"row\":769,\"lag\":false,\"state_hex\":\""
                + state + "\",\"events\":[" + psgEvent() + "]}\n";
        String cutoff = boundary("cutoff", "\"exclusive_end\":770,", state, "[]");
        return metadata + baseline + frame + cutoff + (trailing ? "{}\n" : "");
    }

    private static String twoFramePrefix() {
        String first = rawPrefix(false);
        String cutoff = boundary("cutoff", "\"exclusive_end\":770,", "00".repeat(8192), "[]");
        String second = "{\"type\":\"frame\",\"row\":770,\"lag\":false,\"state_hex\":\""
                + "00".repeat(8192) + "\",\"events\":[" + psgEvent() + "]}\n";
        return first.replace(cutoff, second
                + boundary("cutoff", "\"exclusive_end\":771,", "00".repeat(8192), "[]"));
    }

    private static List<CompleteRunAudioTrace.Record> records(Path output) throws Exception {
        List<CompleteRunAudioTrace.Record> records = new java.util.ArrayList<>();
        try (var reader = new CompleteRunAudioCaptureStore().read(output)) {
            while (reader.hasNext()) records.add(reader.next());
        }
        return records;
    }

    private static String boundary(String type, String coordinate, String state, String active) {
        return "{\"type\":\"" + type + "\"," + coordinate + "\"state_hex\":\"" + state
                + "\",\"ym_port0_latch\":42,\"ym_port1_latch\":161,"
                + "\"native_arm_epoch\":1,\"native_armed\":true,\"active_services\":"
                + active + ",\"pending_descendants\":[]}\n";
    }

    private static String dpcm() {
        return service(1, 4, 378, 5, "[]");
    }

    private static String psgEvent() {
        return "{\"ordinal\":0,\"service_token\":1,\"parent_token\":0,\"pc\":378,"
                + "\"subject\":0,\"offset\":0,\"kind\":4,\"service_kind\":4,\"depth\":0,"
                + "\"source_cpu\":1,\"payload_length\":0,\"value\":68,\"flags\":0,"
                + "\"reserved\":0,\"payload\":\"0\"}";
    }

    private static String service(int token, int kind, int pc, int hook, String chips) {
        return "{\"token\":" + token + ",\"parent_token\":0,\"kind\":" + kind
                + ",\"depth\":0,\"current_parent_token\":0,\"current_depth\":0,"
                + "\"begin_coordinate\":1,\"end_coordinate\":0,\"begin_pc\":" + pc
                + ",\"end_pc\":0,\"begin_hook_token\":" + hook + ",\"end_hook_token\":0,"
                + "\"begin_source_cpu\":1,\"cancelled\":false,\"complete\":false,"
                + "\"chips\":" + chips + ",\"snapshots\":[],\"ancestry_transitions\":[]}";
    }
}
