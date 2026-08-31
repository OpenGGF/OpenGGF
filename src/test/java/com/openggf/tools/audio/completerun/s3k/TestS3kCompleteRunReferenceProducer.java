package com.openggf.tools.audio.completerun.s3k;

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
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestS3kCompleteRunReferenceProducer {
    @TempDir Path temporary;

    @Test
    void projectsValidatedStateAndCutoffChipsWithoutInventingRequestsOrDecisions() throws Exception {
        Path raw = Files.writeString(temporary.resolve("s3k-prefix.jsonl"), rawPrefix(false));
        Path output = temporary.resolve("s3k-capture").toAbsolutePath();
        new S3kCompleteRunReferenceProjector().projectPrefixForTesting(raw.toAbsolutePath(), rom(), output);
        List<CompleteRunAudioTrace.Record> records = records(output);
        CompleteRunAudioTrace.Baseline baseline = (CompleteRunAudioTrace.Baseline) records.getFirst();
        CompleteRunAudioTrace.Frame frame = (CompleteRunAudioTrace.Frame) records.get(1);
        CompleteRunAudioTrace.CutoffFrontier cutoff =
                (CompleteRunAudioTrace.CutoffFrontier) records.get(2);
        var catalog = S3kCompleteRunAssetCatalog.load(rom());
        assertEquals(S3kCompleteRunStateNormalizer.normalizeReference(
                S3kCompleteRunStateDecoder.decode(new byte[1024], catalog), catalog.assets()), baseline.state());
        assertEquals(baseline.state(), frame.services().getFirst().state());
        assertEquals(baseline.state(), cutoff.terminalState());
        assertEquals(List.of(), frame.requests());
        assertEquals(1, frame.services().size());
        assertEquals(List.of(), frame.services().getFirst().decisions());
        assertEquals(List.of(new CompleteRunAudioTrace.PsgWrite(0, 0x44)), frame.rawChipEvents());
        assertEquals(List.of(), cutoff.rawChipEvents());
    }

    @Test
    void lateRawFailureAbortsThePrivateProjectionTransaction() throws Exception {
        Path raw = Files.writeString(temporary.resolve("invalid.jsonl"), rawPrefix(true));
        Path output = temporary.resolve("invalid-capture").toAbsolutePath();
        assertThrows(IllegalArgumentException.class, () ->
                new S3kCompleteRunReferenceProjector().projectPrefixForTesting(raw.toAbsolutePath(), rom(), output));
        assertFalse(Files.exists(output));
    }

    @Test
    void preservesTheFullNativeOneActiveFourPendingCutoffThroughCanonicalProjection() throws Exception {
        Path raw = Files.writeString(temporary.resolve("s3k-frontier.jsonl"), fullFrontierPrefix());

        var records = new S3kCompleteRunReferenceProjector()
                .projectPrefixForTesting(raw.toAbsolutePath(), rom()).records();
        CompleteRunAudioTrace.CutoffFrontier cutoff =
                (CompleteRunAudioTrace.CutoffFrontier) records.get(2);

        assertEquals(1, cutoff.activeStack().size());
        assertEquals(4, cutoff.pendingDescendants().size());
        assertEquals("DigitalAudioDispatch", cutoff.activeStack().getFirst().kind());
        assertEquals(List.of("DpcmIteration", "DpcmIteration", "DpcmIteration", "DpcmIteration"),
                cutoff.pendingDescendants().stream().map(CompleteRunAudioTrace.CutoffService::kind).toList());
        assertEquals(List.of(new CompleteRunAudioTrace.PsgWrite(0, 0x44)), cutoff.rawChipEvents());

        var chip = new CompleteRunAudioTrace.FrontierChipEvent(
                10, 10, "Z80", 4256, 4, 0, 68, true, null, null);
        var snapshot = new CompleteRunAudioTrace.FrontierSnapshot(2, "Z80", 4256, List.of(0x7f));
        var active = new CompleteRunAudioTrace.FrontierService(1, 0, 0, "DigitalAudioDispatch",
                CompleteRunAudioTrace.FrontierServiceState.OPEN, 810, 0, 4256, 9, "Z80",
                null, null, null, null, List.of(snapshot), List.of(chip), 0, 0, List.of());
        var transition = new CompleteRunAudioTrace.NativeAncestryTransition(
                4, 810, 4, 2, 2, 1, 1, 13, "Z80", 4360);
        List<CompleteRunAudioTrace.FrontierService> pending = List.of(
                completedService(2, 1, 1, 1, 3, 1, 1, List.of()),
                completedService(3, 2, 2, 2, 5, 1, 1, List.of(transition)),
                completedService(4, 1, 1, 6, 7, 1, 1, List.of()),
                completedService(5, 1, 1, 8, 9, 1, 1, List.of()));
        var nativeCutoff = cutoff.nativeDiagnostics();
        assertEquals(List.of(active), nativeCutoff.activeStack());
        assertEquals(pending, nativeCutoff.pendingDescendants());
        assertEquals(List.of(new CompleteRunAudioTrace.FrontierOwnedChip(1, chip)),
                nativeCutoff.rawChipInventory());
        assertEquals(List.of(new CompleteRunAudioTrace.FrontierOwnedSnapshot(1, 0, snapshot)),
                nativeCutoff.rawSnapshotInventory());
        assertEquals(null, nativeCutoff.pendingDeferredServiceBegin());
        assertEquals(1, nativeCutoff.armEpoch());
        assertTrue(nativeCutoff.armed());
        assertEquals(sha256(new byte[1024]), nativeCutoff.terminalZ80Digest());
    }

    @Test
    void bindsCutoffCoordinatesToTheFinalFrameWhenNativeTokensAreReused() throws Exception {
        Path raw = Files.writeString(temporary.resolve("s3k-token-reuse.jsonl"), tokenReusePrefix());

        var records = new S3kCompleteRunReferenceProjector()
                .projectPrefixForTesting(raw.toAbsolutePath(), rom()).records();
        CompleteRunAudioTrace.CutoffFrontier cutoff =
                (CompleteRunAudioTrace.CutoffFrontier) records.get(3);

        assertEquals(811, cutoff.nativeDiagnostics().activeStack().getFirst().beginFrame());
        assertEquals(811, cutoff.nativeDiagnostics().pendingDescendants().get(1)
                .ancestryTransitions().getFirst().frame());
    }

    @Test
    void rejectsCutoffAncestryThatDiffersFromItsObservedPromotionEvent() throws Exception {
        String mismatched = fullFrontierPrefix().replace(
                "\"coordinate\":4,\"native_ordinal\":4,\"previous_parent_token\":2",
                "\"coordinate\":4,\"native_ordinal\":3,\"previous_parent_token\":2");
        Path raw = Files.writeString(temporary.resolve("s3k-ancestry-mismatch.jsonl"), mismatched);

        assertThrows(IllegalArgumentException.class, () -> new S3kCompleteRunReferenceProjector()
                .projectPrefixForTesting(raw.toAbsolutePath(), rom()));

        String mismatchedBoundary = fullFrontierPrefix().replaceFirst(
                "\\\"begin_pc\\\":4300", "\\\"begin_pc\\\":4301");
        Path boundaryRaw = Files.writeString(
                temporary.resolve("s3k-boundary-mismatch.jsonl"), mismatchedBoundary);
        assertThrows(IllegalArgumentException.class, () -> new S3kCompleteRunReferenceProjector()
                .projectPrefixForTesting(boundaryRaw.toAbsolutePath(), rom()));

        String mismatchedBeginCoordinate = fullFrontierPrefix().replaceFirst(
                "\\\"begin_coordinate\\\":1", "\\\"begin_coordinate\\\":41");
        Path beginCoordinateRaw = Files.writeString(
                temporary.resolve("s3k-begin-coordinate-mismatch.jsonl"), mismatchedBeginCoordinate);
        assertThrows(IllegalArgumentException.class, () -> new S3kCompleteRunReferenceProjector()
                .projectPrefixForTesting(beginCoordinateRaw.toAbsolutePath(), rom()));

        String mismatchedEndCoordinate = fullFrontierPrefix().replaceFirst(
                "\\\"end_coordinate\\\":3", "\\\"end_coordinate\\\":43");
        Path endCoordinateRaw = Files.writeString(
                temporary.resolve("s3k-end-coordinate-mismatch.jsonl"), mismatchedEndCoordinate);
        assertThrows(IllegalArgumentException.class, () -> new S3kCompleteRunReferenceProjector()
                .projectPrefixForTesting(endCoordinateRaw.toAbsolutePath(), rom()));

        String mismatchedPromotionCoordinate = fullFrontierPrefix().replaceFirst(
                "\\\"coordinate\\\":4,\\\"native_ordinal\\\":4,\\\"previous_parent_token\\\":2",
                "\\\"coordinate\\\":44,\\\"native_ordinal\\\":4,\\\"previous_parent_token\\\":2");
        Path promotionCoordinateRaw = Files.writeString(
                temporary.resolve("s3k-promotion-coordinate-mismatch.jsonl"), mismatchedPromotionCoordinate);
        assertThrows(IllegalArgumentException.class, () -> new S3kCompleteRunReferenceProjector()
                .projectPrefixForTesting(promotionCoordinateRaw.toAbsolutePath(), rom()));
    }

    @Test
    void fixedProducerRejectsEveryMismatchedTypedRequestBeforePublication() throws Exception {
        S3kCompleteRunReferenceProducer producer = new S3kCompleteRunReferenceProducer();
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

    private CompleteRunAudioProducer.Request validRequest(Path output) {
        Path root = Path.of("").toAbsolutePath();
        return new CompleteRunAudioProducer.Request(CompleteRunAudioTrace.ProducerKind.REFERENCE,
                S3kCompleteRunAudioProfile.ID, rom(),
                root.resolve("src/test/resources/traces/s3k/_movies/s3k-knuckles-complete-superemeralds.bk2"),
                root.resolve("src/test/resources/traces/s3k/runs/s3k-knuckles-complete-superemeralds/run_manifest.json"),
                root.resolve("tools/tracechaser"), output);
    }

    private static Path rom() {
        File value = RomTestUtils.ensureSonic3kRomAvailable();
        assumeTrue(value != null, "Sonic 3&K locked-on ROM not available — skipping test");
        try { return value.toPath().toRealPath(); }
        catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
    }

    private static String rawPrefix(boolean trailing) {
        String state = "00".repeat(1024);
        String metadata = "{\"type\":\"metadata\",\"schema\":\"openggf.s3k-complete-run-audio-raw.v1\","
                + "\"rom_sha1\":\"cfbf98c36c776677290a872547ac47c53d2761d6\","
                + "\"bk2_sha256\":\"aa892856df22b7bb1fe5accb48db10b90dc26845d1dccee90352da30349f53cc\","
                + "\"service_manifest_sha256\":\"ef8f8103c38d70e41cb09cb29751f56815a0401709dc509071aa514d614813a0\","
                + "\"first_row\":810,\"exclusive_end\":434417,\"state_start\":7168,\"state_exclusive_end\":8192}\n";
        String baseline = boundary("baseline", "\"row\":810,", state, "[]");
        String frame = "{\"type\":\"frame\",\"row\":810,\"lag\":true,\"state_hex\":\""
                + state + "\",\"events\":[" + psgEvent() + "]}\n";
        String cutoff = boundary("cutoff", "\"exclusive_end\":811,", state, "[]");
        return metadata + baseline + frame + cutoff + (trailing ? "{}\n" : "");
    }

    private static List<CompleteRunAudioTrace.Record> records(Path output) throws Exception {
        List<CompleteRunAudioTrace.Record> records = new java.util.ArrayList<>();
        try (var reader = new CompleteRunAudioCaptureStore().read(output)) {
            while (reader.hasNext()) records.add(reader.next());
        }
        return records;
    }

    private static String fullFrontierPrefix() {
        return fullFrontierPrefix(0);
    }

    private static String fullFrontierPrefix(int coordinateOffset) {
        String state = "00".repeat(1024);
        String metadata = "{\"type\":\"metadata\",\"schema\":\"openggf.s3k-complete-run-audio-raw.v1\","
                + "\"rom_sha1\":\"cfbf98c36c776677290a872547ac47c53d2761d6\","
                + "\"bk2_sha256\":\"aa892856df22b7bb1fe5accb48db10b90dc26845d1dccee90352da30349f53cc\","
                + "\"service_manifest_sha256\":\"ef8f8103c38d70e41cb09cb29751f56815a0401709dc509071aa514d614813a0\","
                + "\"first_row\":810,\"exclusive_end\":434417,\"state_start\":7168,\"state_exclusive_end\":8192}\n";
        String baseline = boundary("baseline", "\"row\":810,", state, "[]");
        String events = beginEvent(0, 1, 0, 9, 0, 4256, 9)
                + "," + beginEvent(1, 2, 1, 7, 1, 4300, 10)
                + "," + beginEvent(2, 3, 2, 7, 2, 4300, 10)
                + "," + endEvent(3, 2, 1, 7, 4357, 12)
                + "," + transitionEvent(4, 3, 1, 7, 1, 4360, 13)
                + "," + endEvent(5, 3, 1, 7, 4357, 12)
                + "," + beginEvent(6, 4, 1, 7, 1, 4300, 10)
                + "," + endEvent(7, 4, 1, 7, 4357, 12)
                + "," + beginEvent(8, 5, 1, 7, 1, 4300, 10)
                + "," + endEvent(9, 5, 1, 7, 4357, 12)
                + "," + psgEvent(10, 1, 9, 4256);
        String frame = "{\"type\":\"frame\",\"row\":810,\"lag\":false,\"state_hex\":\""
                + state + "\",\"events\":[" + events + "]}\n";
        String active = frontierService(1, 0, 9, 0, coordinateOffset, 0, 4256, 9,
                false, 0, 0, 0, 0,
                "[{\"coordinate\":" + (10 + coordinateOffset)
                        + ",\"native_ordinal\":10,\"event_kind\":4,\"subject\":0,"
                        + "\"value\":68,\"pc\":4256,\"source_cpu\":1,\"data\":true,"
                        + "\"port\":0,\"register\":0}]",
                "[{\"range_id\":2,\"source_cpu\":1,\"pc\":4256,\"bytes_hex\":\"7f\"}]", "[]");
        List<String> pending = List.of(
                frontierService(2, 1, 7, 1, 1 + coordinateOffset, 3 + coordinateOffset, 4300, 10,
                        true, 4357, 12, 1, 1, "[]", "[]", "[]"),
                frontierService(3, 2, 7, 2, 2 + coordinateOffset, 5 + coordinateOffset, 4300, 10,
                        true, 4357, 12, 1, 1, "[]", "[]", ancestry(coordinateOffset)),
                frontierService(4, 1, 7, 1, 6 + coordinateOffset, 7 + coordinateOffset, 4300, 10,
                        true, 4357, 12, 1, 1, "[]", "[]", "[]"),
                frontierService(5, 1, 7, 1, 8 + coordinateOffset, 9 + coordinateOffset, 4300, 10,
                        true, 4357, 12, 1, 1, "[]", "[]", "[]"));
        String cutoff = boundaryWithPending(state, active, String.join(",", pending));
        return metadata + baseline + frame + cutoff;
    }

    private static String tokenReusePrefix() {
        String original = fullFrontierPrefix(1);
        int frameStart = original.indexOf("{\"type\":\"frame\"");
        String prefix = original.substring(0, frameStart);
        String reused = "{\"type\":\"frame\",\"row\":810,\"lag\":false,\"state_hex\":\""
                + "00".repeat(1024) + "\",\"events\":["
                + transitionEvent(0, 3, 2, 7, 2, 4360, 13) + "]}\n";
        String finalFrame = original.substring(frameStart)
                .replaceFirst("\\\"row\\\":810", "\\\"row\\\":811")
                .replaceFirst("\\\"exclusive_end\\\":811", "\\\"exclusive_end\\\":812");
        return prefix + reused + finalFrame;
    }

    private static String beginEvent(int ordinal, int token, int parent, int serviceKind,
            int depth, int pc, int hook) {
        return rawEvent(ordinal, token, parent, pc, hook, 1, serviceKind, depth, 0);
    }

    private static String endEvent(int ordinal, int token, int parent, int serviceKind, int pc, int hook) {
        return rawEvent(ordinal, token, parent, pc, hook, 2, serviceKind, parent == 0 ? 0 : 1, 0);
    }

    private static String transitionEvent(int ordinal, int token, int parent, int serviceKind,
            int depth, int pc, int hook) {
        return rawEvent(ordinal, token, parent, pc, hook, 11, serviceKind, depth, 0);
    }

    private static String psgEvent(int ordinal, int token, int serviceKind, int pc) {
        return rawEvent(ordinal, token, 0, pc, 0, 4, serviceKind, 0, 68);
    }

    private static String rawEvent(int ordinal, int token, int parent, int pc, int subject,
            int kind, int serviceKind, int depth, int value) {
        return "{\"ordinal\":" + ordinal + ",\"service_token\":" + token
                + ",\"parent_token\":" + parent + ",\"pc\":" + pc + ",\"subject\":" + subject
                + ",\"offset\":0,\"kind\":" + kind + ",\"service_kind\":" + serviceKind
                + ",\"depth\":" + depth + ",\"source_cpu\":1,\"payload_length\":0,"
                + "\"value\":" + value + ",\"flags\":0,\"reserved\":0,\"payload\":\"0\"}";
    }

    private static String frontierService(int token, int parent, int kind, int depth,
            int beginCoordinate, int endCoordinate, int beginPc, int beginHook, boolean complete,
            int endPc, int endHook, int currentParent, int currentDepth, String chips,
            String snapshots, String ancestry) {
        return "{\"token\":" + token + ",\"parent_token\":" + parent + ",\"kind\":" + kind
                + ",\"depth\":" + depth + ",\"current_parent_token\":" + currentParent
                + ",\"current_depth\":" + currentDepth + ",\"begin_coordinate\":" + beginCoordinate
                + ",\"end_coordinate\":" + endCoordinate + ",\"begin_pc\":" + beginPc
                + ",\"end_pc\":" + endPc + ",\"begin_hook_token\":" + beginHook
                + ",\"end_hook_token\":" + endHook + ",\"begin_source_cpu\":1,"
                + "\"cancelled\":false,\"complete\":" + complete + ",\"chips\":" + chips
                + ",\"snapshots\":" + snapshots + ",\"ancestry_transitions\":" + ancestry + "}";
    }

    private static String ancestry(int coordinateOffset) {
        return "[{\"coordinate\":" + (4 + coordinateOffset)
                + ",\"native_ordinal\":4,\"previous_parent_token\":2,"
                + "\"previous_depth\":2,\"current_parent_token\":1,\"current_depth\":1,"
                + "\"hook_token\":13,\"source_cpu\":1,\"pc\":4360}]";
    }

    private static CompleteRunAudioTrace.FrontierService completedService(int token, int parent,
            int depth, int beginOrdinal, int endOrdinal, int currentParent, int currentDepth,
            List<CompleteRunAudioTrace.NativeAncestryTransition> transitions) {
        return new CompleteRunAudioTrace.FrontierService(token, parent, depth, "DpcmIteration",
                CompleteRunAudioTrace.FrontierServiceState.COMPLETED, 810, beginOrdinal, 4300, 10,
                "Z80", 810, (long) endOrdinal, 4357, 12, List.of(), List.of(), currentParent,
                currentDepth, transitions);
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static String boundaryWithPending(String state, String active, String pending) {
        return "{\"type\":\"cutoff\",\"exclusive_end\":811,\"state_hex\":\"" + state
                + "\",\"ym_port0_latch\":40,\"ym_port1_latch\":161,\"native_arm_epoch\":1,"
                + "\"native_armed\":true,\"active_services\":[" + active
                + "],\"pending_descendants\":[" + pending + "]}\n";
    }

    private static String boundary(String type, String coordinate, String state, String active) {
        return "{\"type\":\"" + type + "\"," + coordinate + "\"state_hex\":\"" + state
                + "\",\"ym_port0_latch\":40,\"ym_port1_latch\":161,"
                + "\"native_arm_epoch\":1,\"native_armed\":true,\"active_services\":"
                + active + ",\"pending_descendants\":[]}\n";
    }

    private static String psgEvent() {
        return "{\"ordinal\":0,\"service_token\":1,\"parent_token\":0,\"pc\":56,"
                + "\"subject\":0,\"offset\":0,\"kind\":4,\"service_kind\":3,\"depth\":0,"
                + "\"source_cpu\":1,\"payload_length\":0,\"value\":68,\"flags\":0,"
                + "\"reserved\":0,\"payload\":\"0\"}";
    }
}
