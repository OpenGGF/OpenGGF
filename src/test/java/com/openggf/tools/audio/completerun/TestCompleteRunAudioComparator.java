package com.openggf.tools.audio.completerun;

import static com.openggf.tools.audio.completerun.CompleteRunAudioTrace.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestCompleteRunAudioComparator {
    private static final AtomicInteger PROFILE_SEQUENCE = new AtomicInteger();
    private static final int FIRST_FRAME = 860;
    private static final OwnerRef NONE = new OwnerRef(OwnerClass.NONE, "none", 0, -1);

    @TempDir
    Path temp;

    @Test
    void exactMatchCarriesBothSourceIdentitiesAndDeterministicEmptyReports() throws Exception {
        TestProfile profile = registerProfile(20);
        Path reference = writeCapture("reference", profile, ProducerKind.REFERENCE, 20, this::plainFrame);
        Path engine = writeCapture("engine", profile, ProducerKind.OPENGGF, 20, this::plainFrame);

        CompleteRunAudioReport report = CompleteRunAudioComparator.compare(reference, engine);

        assertEquals(CompleteRunAudioReport.Kind.MATCH, report.kind());
        assertEquals(ProducerKind.REFERENCE, report.reference().producerKind());
        assertEquals(ProducerKind.OPENGGF, report.engine().producerKind());
        assertEquals(report.reference().rootDigest(), report.engine().rootDigest());
        CompleteRunAudioReport repeated = CompleteRunAudioComparator.compare(reference, engine);
        assertEquals(report.toJson(), repeated.toJson());
        assertEquals(report.toText(), repeated.toText());
        assertEquals(List.of(), report.referenceContext().before());
        assertEquals(List.of(), report.engineContext().after());
        assertTrue(report.toJson().contains("\"side\":\"REFERENCE\""));
        assertTrue(report.toJson().contains("\"side\":\"ENGINE\""));
        assertTrue(report.toText().contains("reference_rom_sha1=" + "0".repeat(40)));
        assertTrue(report.toText().contains("engine_bk2_sha256=" + "2".repeat(64)));
    }

    @Test
    void reportsSelfContainedExactCaptureProvenanceDeterministically() throws Exception {
        TestProfile profile = registerProfile(2);
        Path reference = writeCapture("reference", profile, ProducerKind.REFERENCE, 2, this::plainFrame);
        Path engine = writeCapture("engine", profile, ProducerKind.OPENGGF, 2, this::plainFrame);

        CompleteRunAudioReport report = CompleteRunAudioComparator.compare(reference, engine);

        CompleteRunAudioReport.SourceIdentity source = report.reference();
        assertEquals(metadata(profile, ProducerKind.REFERENCE,
                profile.producerRuntimeIdentities().get(ProducerKind.REFERENCE)), source.metadata());
        assertEquals("1".repeat(8), source.metadata().fixture().romCrc32());
        assertEquals(FIRST_FRAME + 2, source.metadata().fixture().bk2RowCount());
        assertEquals("reference.test.v1", source.metadata().observerProof().observerProfile());
        assertEquals("1".repeat(64), source.metadata().producerRuntimeIdentity().artifactSha256()
                .get(RuntimeArtifact.BIZHAWK_EXECUTABLE));
        assertEquals(1, source.chunks().size());
        assertEquals(sha256(CompleteRunAudioJson.writeMetadata(source.metadata())), source.metadataSha256());
        assertEquals(sha256(reference.resolve("manifest.json")), source.captureManifestSha256());
        assertEquals(sha256(reference.resolve("chunks").resolve(source.chunks().get(0).file())),
                source.chunks().get(0).compressedSha256());
        assertTrue(report.toJson().contains("\"metadata_sha256\":\"" + source.metadataSha256() + "\""));
        assertTrue(report.toJson().contains("\"romCrc32\":\"" + "1".repeat(8) + "\""));
        assertTrue(report.toJson().contains("\"bk2RowCount\":" + (FIRST_FRAME + 2)));
        assertTrue(report.toJson().contains("\"observerProfile\":\"reference.test.v1\""));
        assertTrue(report.toJson().contains("\"BIZHAWK_EXECUTABLE\":\"" + "1".repeat(64) + "\""));
        assertTrue(report.toJson().contains("\"segments\":[{\"id\":\"test\""));
        assertTrue(report.toJson().contains("\"firstFrame\":" + FIRST_FRAME));
        assertTrue(report.toJson().contains("\"capture_manifest_sha256\":\""
                + source.captureManifestSha256() + "\""));
        assertTrue(report.toJson().contains("\"publication_digest\":\"" + source.publicationDigest() + "\""));
        assertTrue(report.toText().contains("reference_metadata_json="));
        assertTrue(report.toText().contains("\"observerProfile\":\"reference.test.v1\""));
        assertEquals(report.toJson(), CompleteRunAudioComparator.compare(reference, engine).toJson());
        assertEquals(report.toText(), CompleteRunAudioComparator.compare(reference, engine).toText());
    }

    @Test
    void reportsValidatedMetadataIdentityMismatchBeforeRecordDifferences() throws Exception {
        TestProfile referenceProfile = registerProfile(3);
        TestProfile engineProfile = registerProfile(3);
        Path reference = writeCapture("reference", referenceProfile, ProducerKind.REFERENCE, 3,
                this::plainFrame);
        Path engine = writeCapture("engine", engineProfile, ProducerKind.OPENGGF, 3,
                row -> row == 1 ? requestFrame(row, request(0, 0xc0)) : plainFrame(row));

        CompleteRunAudioReport report = CompleteRunAudioComparator.compare(reference, engine);

        assertEquals(CompleteRunAudioReport.Kind.METADATA_IDENTITY, report.kind());
        assertEquals(-1, report.frame());
        assertEquals("metadata.profile_id", report.location());
    }

    @Test
    void classifiesMissingAndExtraFramesWithoutAttemptingRealignment() {
        Frame earlier = plainFrame(0);
        Frame later = plainFrame(1);

        assertEquals(CompleteRunAudioReport.Kind.FRAME_MISSING,
                CompleteRunAudioComparator.difference(earlier, later).kind());
        assertEquals(CompleteRunAudioReport.Kind.FRAME_EXTRA,
                CompleteRunAudioComparator.difference(later, earlier).kind());
    }

    @Test
    void classifiesMissingAndExtraRequestsServicesDecisionsAndChipEvents() {
        Request request = request(0, 0xc0);
        Decision decision = decision(0, 1, 2, owner(0, 0xc0));
        DriverService empty = service(0, List.of(), List.of(), state(1));
        DriverService rich = service(0, List.of(decision), List.of(new YmWrite(0, 0, 0x22, 0x33)), state(1));

        assertBothDirections(CompleteRunAudioReport.Kind.REQUEST_MISSING,
                CompleteRunAudioReport.Kind.REQUEST_EXTRA,
                new Frame(FIRST_FRAME, "test", false, List.of(request), List.of()),
                new Frame(FIRST_FRAME, "test", false, List.of(), List.of()));
        assertBothDirections(CompleteRunAudioReport.Kind.SERVICE_MISSING,
                CompleteRunAudioReport.Kind.SERVICE_EXTRA,
                new Frame(FIRST_FRAME, "test", false, List.of(), List.of(empty)),
                new Frame(FIRST_FRAME, "test", false, List.of(), List.of()));
        assertBothDirections(CompleteRunAudioReport.Kind.DECISION_MISSING,
                CompleteRunAudioReport.Kind.DECISION_EXTRA,
                new Frame(FIRST_FRAME, "test", false, List.of(), List.of(rich)),
                new Frame(FIRST_FRAME, "test", false, List.of(), List.of(
                        service(0, List.of(), rich.chipEvents(), state(1)))));
        assertBothDirections(CompleteRunAudioReport.Kind.CHIP_EVENT_MISSING,
                CompleteRunAudioReport.Kind.CHIP_EVENT_EXTRA,
                new Frame(FIRST_FRAME, "test", false, List.of(), List.of(rich)),
                new Frame(FIRST_FRAME, "test", false, List.of(), List.of(
                        service(0, rich.decisions(), List.of(), state(1)))));
    }

    @Test
    void classifiesOrderingStateNameStateValueOwnerPriorityLifecycleAndTerminalCounts() {
        Request a0 = request(0, 0xc0);
        Request b1 = request(1, 0xc1);
        Request b0 = request(0, 0xc1);
        Request a1 = request(1, 0xc0);
        Frame referenceOrder = new Frame(FIRST_FRAME, "test", false, List.of(a0, b1), List.of());
        Frame engineOrder = new Frame(FIRST_FRAME, "test", false, List.of(b0, a1), List.of());
        assertEquals(CompleteRunAudioReport.Kind.REQUEST_ORDER,
                CompleteRunAudioComparator.difference(referenceOrder, engineOrder).kind());

        Baseline namedTempo = new Baseline(FIRST_FRAME,
                new NormalizedState(List.of(new StateField("tempo", 1)), inactiveRoles()));
        Baseline namedSpeed = new Baseline(FIRST_FRAME,
                new NormalizedState(List.of(new StateField("speed", 1)), inactiveRoles()));
        Baseline tempoTwo = new Baseline(FIRST_FRAME,
                new NormalizedState(List.of(new StateField("tempo", 2)), inactiveRoles()));
        assertEquals(CompleteRunAudioReport.Kind.STATE_FIELD_NAME,
                CompleteRunAudioComparator.difference(namedTempo, namedSpeed).kind());
        assertEquals(CompleteRunAudioReport.Kind.STATE_FIELD_VALUE,
                CompleteRunAudioComparator.difference(namedTempo, tempoTwo).kind());

        DriverService ownerReference = service(0, List.of(decision(0, 1, 2, owner(0, 0xc0))),
                List.of(), state(1));
        DriverService ownerEngine = service(0, List.of(decision(0, 1, 2, owner(0, 0xc1))),
                List.of(), state(1));
        assertEquals(CompleteRunAudioReport.Kind.OWNER,
                CompleteRunAudioComparator.difference(frame(ownerReference), frame(ownerEngine)).kind());

        DriverService priorityEngine = service(0, List.of(decision(0, 1, 3, owner(0, 0xc0))),
                List.of(), state(1));
        assertEquals(CompleteRunAudioReport.Kind.PRIORITY,
                CompleteRunAudioComparator.difference(frame(ownerReference), frame(priorityEngine)).kind());

        Lifecycle save = new Lifecycle(0, FIRST_FRAME, "save", Map.of("slot", 1));
        Lifecycle restore = new Lifecycle(0, FIRST_FRAME, "restore", Map.of("slot", 1));
        assertEquals(CompleteRunAudioReport.Kind.LIFECYCLE_VALUE,
                CompleteRunAudioComparator.difference(save, restore).kind());

        Terminal one = new Terminal(FIRST_FRAME + 1, 1, 0, 0, 0, 0, 0, 0, "a".repeat(64));
        Terminal two = new Terminal(FIRST_FRAME + 1, 1, 1, 0, 0, 0, 0, 0, "b".repeat(64));
        assertEquals(CompleteRunAudioReport.Kind.TERMINAL_COUNT,
                CompleteRunAudioComparator.difference(one, two).kind());
    }

    @Test
    void nullableSegmentAndPriorityFieldsStillProduceTypedDifferences() {
        Frame noSegment = new Frame(FIRST_FRAME, null, false, List.of(), List.of());
        Frame segment = new Frame(FIRST_FRAME, "test", false, List.of(), List.of());
        assertEquals(CompleteRunAudioReport.Kind.FRAME_VALUE,
                CompleteRunAudioComparator.difference(noSegment, segment).kind());

        Decision noPriority = decision(0, null, null, owner(0, 0xc0));
        Decision priorityAfter = decision(0, null, 2, owner(0, 0xc0));
        assertEquals(CompleteRunAudioReport.Kind.PRIORITY,
                CompleteRunAudioComparator.difference(
                        frame(service(0, List.of(noPriority), List.of(), state(1))),
                        frame(service(0, List.of(priorityAfter), List.of(), state(1)))).kind());
    }

    @Test
    void classifiesServiceDecisionAndChipPayloadOrderingIndependentlyOfOrdinals() {
        DriverService referenceFirst = service(0, List.of(), List.of(), state(1), "music");
        DriverService referenceSecond = service(1, List.of(), List.of(), state(1), "sfx");
        DriverService engineFirst = service(0, List.of(), List.of(), state(1), "sfx");
        DriverService engineSecond = service(1, List.of(), List.of(), state(1), "music");
        assertEquals(CompleteRunAudioReport.Kind.SERVICE_ORDER,
                CompleteRunAudioComparator.difference(
                        new Frame(FIRST_FRAME, "test", false, List.of(), List.of(referenceFirst, referenceSecond)),
                        new Frame(FIRST_FRAME, "test", false, List.of(), List.of(engineFirst, engineSecond))).kind());

        Decision a = decisionForKey(0, "sfx.a");
        Decision b = decisionForKey(1, "sfx.b");
        Decision bAtZero = decisionForKey(0, "sfx.b");
        Decision aAtOne = decisionForKey(1, "sfx.a");
        assertEquals(CompleteRunAudioReport.Kind.DECISION_ORDER,
                CompleteRunAudioComparator.difference(
                        frame(service(0, List.of(a, b), List.of(), state(1))),
                        frame(service(0, List.of(bAtZero, aAtOne), List.of(), state(1)))).kind());

        List<ChipEvent> ymThenPsg = List.of(new YmWrite(0, 0, 0x22, 0x33), new PsgWrite(1, 0x44));
        List<ChipEvent> psgThenYm = List.of(new PsgWrite(0, 0x44), new YmWrite(1, 0, 0x22, 0x33));
        assertEquals(CompleteRunAudioReport.Kind.CHIP_EVENT_ORDER,
                CompleteRunAudioComparator.difference(
                        frame(service(0, List.of(), ymThenPsg, state(1))),
                        frame(service(0, List.of(), psgThenYm, state(1)))).kind());
    }

    @Test
    void doesNotRealignOneFrameAdmissionDelay() throws Exception {
        TestProfile profile = registerProfile(120);
        Decision admission = decision(0, 1, 2, owner(0, 0xc0));
        IntFunction<Frame> referenceFrames = row -> shiftedAdmissionFrame(row, admission, 99);
        IntFunction<Frame> engineFrames = row -> shiftedAdmissionFrame(row, admission, 98);
        Path reference = writeCapture("reference", profile, ProducerKind.REFERENCE, 120, referenceFrames);
        Path engine = writeCapture("engine", profile, ProducerKind.OPENGGF, 120, engineFrames);

        CompleteRunAudioReport report = CompleteRunAudioComparator.compare(reference, engine);

        assertEquals(CompleteRunAudioReport.Kind.DECISION_EXTRA, report.kind());
        assertEquals(958, report.frame());
        assertEquals("frame.services[0].decisions[0]", report.location());
    }

    @Test
    void retainsOnlyFirstMismatchAndEightCompleteRecordsBeforeAndAfterEachSide() throws Exception {
        TestProfile profile = registerProfile(30);
        IntFunction<Frame> referenceFrames = row -> {
            if (row == 12) return requestFrame(row, request(0, 0xc0));
            if (row == 24) return chipFrame(row, 0x33);
            return plainFrame(row);
        };
        IntFunction<Frame> engineFrames = row -> {
            if (row == 12) return requestFrame(row, request(0, 0xc1));
            if (row == 24) return chipFrame(row, 0x99);
            return plainFrame(row);
        };
        Path reference = writeCapture("reference", profile, ProducerKind.REFERENCE, 30, referenceFrames);
        Path engine = writeCapture("engine", profile, ProducerKind.OPENGGF, 30, engineFrames);

        CompleteRunAudioReport report = CompleteRunAudioComparator.compare(reference, engine);

        assertEquals(CompleteRunAudioReport.Kind.REQUEST_VALUE, report.kind());
        assertEquals(FIRST_FRAME + 12, report.frame());
        assertEquals(8, report.referenceContext().before().size());
        assertEquals(8, report.referenceContext().after().size());
        assertEquals(8, report.engineContext().before().size());
        assertEquals(8, report.engineContext().after().size());
        assertNotNull(report.referenceContext().current());
        assertEquals(FIRST_FRAME + 12, report.referenceContext().current().frame());
        assertTrue(report.referenceContext().before().stream()
                .allMatch(view -> view.canonicalJson().startsWith("{\"type\":")));
        assertTrue(report.referenceContext().after().stream()
                .noneMatch(view -> Integer.valueOf(FIRST_FRAME + 24).equals(view.frame())));
    }

    @Test
    void validCaptureReplacementBetweenPassesIsTypedAndAttributedToItsSource() throws Exception {
        TestProfile profile = registerProfile(4);
        Path reference = writeCapture("reference", profile, ProducerKind.REFERENCE, 4, this::plainFrame);
        Path replacement = writeCapture("replacement", profile, ProducerKind.REFERENCE, 4,
                row -> row == 2 ? requestFrame(row, request(0, 0xc0)) : plainFrame(row));
        Path engine = writeCapture("engine", profile, ProducerKind.OPENGGF, 4, this::plainFrame);

        CompleteRunAudioReport report = CompleteRunAudioComparator.compare(reference, engine, () -> {
            Files.delete(reference);
            Files.move(replacement, reference);
        });

        assertEquals(CompleteRunAudioReport.Kind.CAPTURE_FAILURE, report.kind());
        assertEquals(CompleteRunAudioReport.Side.REFERENCE, report.failureSide());
        assertEquals(CompleteRunAudioComparator.ValidationException.Kind.SOURCE_REPLACED,
                report.validationKind());
    }

    @Test
    void sameMetadataRecordRootReplacementWithDifferentPublicationBytesIsRejected() throws Exception {
        TestProfile profile = registerProfile(4);
        Path reference = writeCapture("reference", profile, ProducerKind.REFERENCE, 4, this::plainFrame);
        Path replacement = writeCapture("replacement", profile, ProducerKind.REFERENCE, 4, this::plainFrame);
        Path engine = writeCapture("engine", profile, ProducerKind.OPENGGF, 4, this::plainFrame);
        String originalManifest = sha256(reference.resolve("manifest.json"));
        String originalChunk = sha256(reference.resolve("chunks/000000.jsonl.gz"));
        alterGzipHeaderAndManifest(replacement);
        assertNotEquals(originalManifest, sha256(replacement.resolve("manifest.json")));
        assertNotEquals(originalChunk, sha256(replacement.resolve("chunks/000000.jsonl.gz")));
        assertEquals(readAll(reference), readAll(replacement));

        CompleteRunAudioReport report = CompleteRunAudioComparator.compare(reference, engine, () -> {
            Files.delete(reference);
            Files.move(replacement, reference);
        });

        assertSemanticFailure(report, CompleteRunAudioReport.Side.REFERENCE,
                CompleteRunAudioComparator.ValidationException.Kind.SOURCE_REPLACED);
    }

    @Test
    void rejectsRuntimeIdentityOutsideTheExactRegisteredProfileWithoutParsingProse() throws Exception {
        TestProfile profile = registerProfile(2);
        Metadata wrong = metadata(profile, ProducerKind.REFERENCE, new ProducerRuntimeIdentity(
                "BizHawk", "wrong", "BizHawk", "2.11", "GPGX", "1.0",
                Map.of(RuntimeArtifact.BIZHAWK_EXECUTABLE, "1".repeat(64),
                        RuntimeArtifact.BIZHAWK_CORE_DLL, "2".repeat(64),
                        RuntimeArtifact.GPGX_CORE, "3".repeat(64))));
        Path reference = writeCapture("reference", wrong, 2, this::plainFrame);
        Path engine = writeCapture("engine", profile, ProducerKind.OPENGGF, 2, this::plainFrame);

        CompleteRunAudioReport report = CompleteRunAudioComparator.compare(reference, engine);

        assertEquals(CompleteRunAudioReport.Kind.CAPTURE_FAILURE, report.kind());
        assertEquals(CompleteRunAudioReport.Side.REFERENCE, report.failureSide());
        assertEquals(CompleteRunAudioComparator.ValidationException.Kind.METADATA_PROFILE_MISMATCH,
                report.validationKind());
        assertEquals(reference.toAbsolutePath().normalize().toString(), report.failureSource());
    }

    @Test
    void rejectsRequestContentThatDoesNotMatchTheProfileResolvedNativeIdentity() throws Exception {
        TestProfile profile = registerProfile(2);
        Path reference = writeCapture("reference", profile, ProducerKind.REFERENCE, 2, this::plainFrame);
        Request forged = new Request(0, OwnerClass.SFX, "sfx.forged", 0xc0, "mailbox", 0);
        Path engine = writeCapture("engine", profile, ProducerKind.OPENGGF, 2,
                row -> row == 0 ? requestFrame(row, forged) : plainFrame(row));

        CompleteRunAudioReport report = CompleteRunAudioComparator.compare(reference, engine);

        assertEquals(CompleteRunAudioReport.Kind.CAPTURE_FAILURE, report.kind());
        assertEquals(CompleteRunAudioReport.Side.ENGINE, report.failureSide());
        assertEquals(CompleteRunAudioComparator.ValidationException.Kind.REQUEST_IDENTITY_INVALID,
                report.validationKind());
    }

    @Test
    void rejectsNoncontiguousGlobalOrdinalsWithTypedSideAttribution() throws Exception {
        TestProfile profile = registerProfile(2);
        Request skippedZero = request(1, 0xc0);
        Path reference = writeCapture("reference", profile, ProducerKind.REFERENCE, 2,
                row -> row == 0 ? requestFrame(row, skippedZero) : plainFrame(row));
        Path engine = writeCapture("engine", profile, ProducerKind.OPENGGF, 2, this::plainFrame);

        CompleteRunAudioReport report = CompleteRunAudioComparator.compare(reference, engine);

        assertEquals(CompleteRunAudioReport.Kind.CAPTURE_FAILURE, report.kind());
        assertEquals(CompleteRunAudioReport.Side.REFERENCE, report.failureSide());
        assertEquals(CompleteRunAudioComparator.ValidationException.Kind.ORDINAL_INVALID,
                report.validationKind());
    }

    @Test
    void rejectsDecisionRoleOutsideTheProfileHardwareInventory() throws Exception {
        TestProfile profile = registerProfile(2);
        Decision invalid = decisionForRole(0, 0xc0, HardwareRole.FM2, owner(0, 0xc0));
        Path reference = writeCapture("reference", profile, ProducerKind.REFERENCE, 2, this::plainFrame);
        Path engine = writeCapture("engine", profile, ProducerKind.OPENGGF, 2,
                row -> row == 0 ? requestAndDecisionFrame(row, request(0, 0xc0), invalid, 0) : plainFrame(row));

        CompleteRunAudioReport report = CompleteRunAudioComparator.compare(reference, engine);

        assertSemanticFailure(report, CompleteRunAudioReport.Side.ENGINE,
                CompleteRunAudioComparator.ValidationException.Kind.ROLE_INVALID);
    }

    @Test
    void rejectsDecisionThatReferencesNoCapturedRequest() throws Exception {
        TestProfile profile = registerProfile(2);
        Decision invalid = decision(99, 1, 2, owner(99, 0xc0));
        Path reference = writeCapture("reference", profile, ProducerKind.REFERENCE, 2, this::plainFrame);
        Path engine = writeCapture("engine", profile, ProducerKind.OPENGGF, 2,
                row -> row == 0 ? new Frame(FIRST_FRAME, "test", false, List.of(),
                        List.of(service(0, List.of(invalid), List.of(), state(1)))) : plainFrame(row));

        CompleteRunAudioReport report = CompleteRunAudioComparator.compare(reference, engine);

        assertSemanticFailure(report, CompleteRunAudioReport.Side.ENGINE,
                CompleteRunAudioComparator.ValidationException.Kind.DECISION_REFERENCE_INVALID);
    }

    @Test
    void rejectsOwnerWhoseRequestOrdinalDoesNotExist() throws Exception {
        TestProfile profile = registerProfile(2);
        Decision invalid = decision(0, 1, 2, owner(99, 0xc0));
        Path reference = writeCapture("reference", profile, ProducerKind.REFERENCE, 2, this::plainFrame);
        Path engine = writeCapture("engine", profile, ProducerKind.OPENGGF, 2,
                row -> row == 0 ? requestAndDecisionFrame(row, request(0, 0xc0), invalid, 0) : plainFrame(row));

        CompleteRunAudioReport report = CompleteRunAudioComparator.compare(reference, engine);

        assertSemanticFailure(report, CompleteRunAudioReport.Side.ENGINE,
                CompleteRunAudioComparator.ValidationException.Kind.OWNER_INVALID);
    }

    @Test
    void rejectsOwnerIdentityThatDoesNotMatchItsOriginatingAdmission() throws Exception {
        TestProfile profile = registerProfile(2);
        Decision invalid = new Decision(0, 0xc0, "sfx.c0", true, "accepted", 1, 2,
                List.of(HardwareRole.FM1), List.of(new RoleDecision(HardwareRole.FM1, NONE,
                        owner(0, 0xc1))));
        Path reference = writeCapture("reference", profile, ProducerKind.REFERENCE, 2, this::plainFrame);
        Path engine = writeCapture("engine", profile, ProducerKind.OPENGGF, 2,
                row -> row == 0 ? requestAndDecisionFrame(row, request(0, 0xc0), invalid, 0) : plainFrame(row));

        CompleteRunAudioReport report = CompleteRunAudioComparator.compare(reference, engine);

        assertSemanticFailure(report, CompleteRunAudioReport.Side.ENGINE,
                CompleteRunAudioComparator.ValidationException.Kind.OWNER_INVALID);
    }

    @Test
    void rejectsResolvedIdentityOutsideProfileOwnedTransformationContract() throws Exception {
        TestProfile profile = registerProfile(2);
        Decision invalid = decision(0, 1, 2, owner(0, 0xc1));
        Path reference = writeCapture("reference", profile, ProducerKind.REFERENCE, 2, this::plainFrame);
        Path engine = writeCapture("engine", profile, ProducerKind.OPENGGF, 2,
                row -> row == 0 ? requestAndDecisionFrame(row, request(0, 0xc0), invalid, 0) : plainFrame(row));

        CompleteRunAudioReport report = CompleteRunAudioComparator.compare(reference, engine);

        assertSemanticFailure(report, CompleteRunAudioReport.Side.ENGINE,
                CompleteRunAudioComparator.ValidationException.Kind.RESOLUTION_INVALID);
    }

    @Test
    void sameIdOwnersRemainDistinctByOriginatingRequestOrdinal() throws Exception {
        TestProfile profile = registerProfile(3);
        IntFunction<Frame> referenceFrames = row -> {
            if (row == 0) return requestAndDecisionFrame(row, request(0, 0xc0),
                    decision(0, 1, 2, owner(0, 0xc0)), 0);
            if (row == 1) return requestAndDecisionFrame(row, request(1, 0xc0),
                    decision(1, 2, 2, owner(1, 0xc0)), 1);
            return plainFrame(row);
        };
        IntFunction<Frame> engineFrames = row -> {
            if (row == 0) return requestAndDecisionFrame(row, request(0, 0xc0),
                    decision(0, 1, 2, owner(0, 0xc0)), 0);
            if (row == 1) return requestAndDecisionFrame(row, request(1, 0xc0),
                    decision(1, 2, 2, owner(0, 0xc0)), 1);
            return plainFrame(row);
        };
        Path reference = writeCapture("reference", profile, ProducerKind.REFERENCE, 3, referenceFrames);
        Path engine = writeCapture("engine", profile, ProducerKind.OPENGGF, 3, engineFrames);

        CompleteRunAudioReport report = CompleteRunAudioComparator.compare(reference, engine);

        assertEquals(CompleteRunAudioReport.Kind.OWNER, report.kind());
        assertTrue(report.referenceValue().contains("requestOrdinal=1"));
        assertTrue(report.engineValue().contains("requestOrdinal=0"));
    }

    @Test
    void rejectsSegmentLabelsThatDoNotExactlyFollowFixtureIntervalsAndGaps() throws Exception {
        int frames = 5;
        TestProfile profile = registerProfile(frames, List.of(
                new ManifestSegment("first", FIRST_FRAME, FIRST_FRAME + 1),
                new ManifestSegment("second", FIRST_FRAME + 3, FIRST_FRAME + 4)));
        IntFunction<Frame> valid = row -> new Frame(FIRST_FRAME + row,
                row == 0 ? "first" : row == 3 ? "second" : null, false, List.of(), List.of());
        Path reference = writeCapture("reference", profile, ProducerKind.REFERENCE, frames, valid);
        Path engine = writeCapture("engine", profile, ProducerKind.OPENGGF, frames,
                row -> new Frame(FIRST_FRAME + row, row == 0 || row == 1 ? "first"
                        : row == 3 ? "second" : null, false, List.of(), List.of()));

        CompleteRunAudioReport report = CompleteRunAudioComparator.compare(reference, engine);

        assertSemanticFailure(report, CompleteRunAudioReport.Side.ENGINE,
                CompleteRunAudioComparator.ValidationException.Kind.SEGMENT_INVALID);
    }

    @Test
    void rejectsLifecycleOutsideIntervalOrRegressingInSourceOrder() throws Exception {
        TestProfile profile = registerProfile(2);
        Metadata referenceMetadata = metadata(profile, ProducerKind.REFERENCE,
                profile.producerRuntimeIdentities().get(ProducerKind.REFERENCE));
        Metadata engineMetadata = metadata(profile, ProducerKind.OPENGGF,
                profile.producerRuntimeIdentities().get(ProducerKind.OPENGGF));
        Path reference = writeCapture("reference", referenceMetadata, 2, this::plainFrame);
        List<CompleteRunAudioTrace.Record> outside = new ArrayList<>();
        outside.add(new Baseline(FIRST_FRAME, state(1)));
        outside.add(plainFrame(0));
        outside.add(plainFrame(1));
        outside.add(new Lifecycle(0, FIRST_FRAME + 2, "pulse", Map.of("payload", "outside")));
        Path outsideCapture = writeRecords("outside", engineMetadata, outside);

        CompleteRunAudioReport outsideReport = CompleteRunAudioComparator.compare(reference, outsideCapture);
        assertSemanticFailure(outsideReport, CompleteRunAudioReport.Side.ENGINE,
                CompleteRunAudioComparator.ValidationException.Kind.LIFECYCLE_INVALID);

        List<CompleteRunAudioTrace.Record> regressing = new ArrayList<>();
        regressing.add(new Baseline(FIRST_FRAME, state(1)));
        regressing.add(new Lifecycle(0, FIRST_FRAME + 1, "pulse", Map.of("payload", "later")));
        regressing.add(new Lifecycle(1, FIRST_FRAME, "pulse", Map.of("payload", "earlier")));
        regressing.add(plainFrame(0));
        regressing.add(plainFrame(1));
        Path regressingCapture = writeRecords("regressing", engineMetadata, regressing);

        CompleteRunAudioReport regressingReport = CompleteRunAudioComparator.compare(reference, regressingCapture);
        assertSemanticFailure(regressingReport, CompleteRunAudioReport.Side.ENGINE,
                CompleteRunAudioComparator.ValidationException.Kind.LIFECYCLE_INVALID);

        List<CompleteRunAudioTrace.Record> futureLifecycleBeforeEarlierFrame = new ArrayList<>();
        futureLifecycleBeforeEarlierFrame.add(new Baseline(FIRST_FRAME, state(1)));
        futureLifecycleBeforeEarlierFrame.add(
                new Lifecycle(0, FIRST_FRAME + 1, "pulse", Map.of("payload", "future")));
        futureLifecycleBeforeEarlierFrame.add(plainFrame(0));
        futureLifecycleBeforeEarlierFrame.add(plainFrame(1));
        Path crossTypeRegression = writeRecords("cross-type-regression", engineMetadata,
                futureLifecycleBeforeEarlierFrame);

        CompleteRunAudioReport crossTypeReport = CompleteRunAudioComparator.compare(reference,
                crossTypeRegression);
        assertSemanticFailure(crossTypeReport, CompleteRunAudioReport.Side.ENGINE,
                CompleteRunAudioComparator.ValidationException.Kind.LIFECYCLE_INVALID);
    }

    @Test
    void rejectsLifecycleOutsideProfileKindAndExactDetailFieldRules() throws Exception {
        TestProfile profile = registerProfile(1);
        Metadata referenceMetadata = metadata(profile, ProducerKind.REFERENCE,
                profile.producerRuntimeIdentities().get(ProducerKind.REFERENCE));
        Metadata engineMetadata = metadata(profile, ProducerKind.OPENGGF,
                profile.producerRuntimeIdentities().get(ProducerKind.OPENGGF));
        Path reference = writeCapture("reference", referenceMetadata, 1, this::plainFrame);
        List<CompleteRunAudioTrace.Record> records = new ArrayList<>();
        records.add(new Baseline(FIRST_FRAME, state(1)));
        records.add(new Lifecycle(0, FIRST_FRAME, "pulse", Map.of("wrong", "field")));
        records.add(plainFrame(0));
        Path engine = writeRecords("engine", engineMetadata, records);

        CompleteRunAudioReport report = CompleteRunAudioComparator.compare(reference, engine);

        assertSemanticFailure(report, CompleteRunAudioReport.Side.ENGINE,
                CompleteRunAudioComparator.ValidationException.Kind.LIFECYCLE_INVALID);
    }

    @Test
    void streamsFiftyThousandHighEntropyFramesInSeparateThirtyTwoMiBJvm() throws Exception {
        int frames = 50_000;
        TestProfile profile = registerProfile(frames);
        Path reference = writeStreamingCapture("large-reference", profile, ProducerKind.REFERENCE, frames);
        Path engine = writeStreamingCapture("large-engine", profile, ProducerKind.OPENGGF, frames);
        long referenceCompressedBytes = compressedBytes(reference);
        long engineCompressedBytes = compressedBytes(engine);
        long compressedBytes = referenceCompressedBytes + engineCompressedBytes;
        long uncompressedBytes = uncompressedGzipBytes(reference) + uncompressedGzipBytes(engine);
        assertTrue(referenceCompressedBytes > 32L * 1024 * 1024,
                () -> "reference compressed input was only " + referenceCompressedBytes + " bytes");
        assertTrue(engineCompressedBytes > 32L * 1024 * 1024,
                () -> "engine compressed input was only " + engineCompressedBytes + " bytes");
        assertTrue(compressedBytes > 64L * 1024 * 1024,
                () -> "combined compressed inputs were only " + compressedBytes + " bytes");
        assertTrue(uncompressedBytes > 160L * 1024 * 1024,
                () -> "combined canonical inputs were only " + uncompressedBytes + " bytes");

        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process process = new ProcessBuilder(java, "-Xmx32m", "-cp", System.getProperty("java.class.path"),
                TestCompleteRunAudioComparator.class.getName(), "compare-probe", reference.toString(),
                engine.toString(), profile.id(), Integer.toString(frames))
                .redirectErrorStream(true).start();
        int status = process.waitFor();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, status, output);
        assertTrue(output.contains("MATCH frames=50000 drained=true"), output);
    }

    public static void main(String[] args) {
        if (args.length != 5 || !"compare-probe".equals(args[0])) {
            throw new IllegalArgumentException("compare-probe <reference> <engine> <profile> <frames>");
        }
        TestProfile profile = profile(args[3], Integer.parseInt(args[4]));
        CompleteRunAudioProfiles.register(profile);
        CompleteRunAudioReport report = CompleteRunAudioComparator.compare(Path.of(args[1]), Path.of(args[2]));
        if (report.kind() != CompleteRunAudioReport.Kind.MATCH) {
            throw new IllegalStateException(report.toText());
        }
        System.out.println("MATCH frames=" + args[4] + " drained=true");
    }

    private static void assertBothDirections(CompleteRunAudioReport.Kind missing,
            CompleteRunAudioReport.Kind extra, CompleteRunAudioTrace.Record reference,
            CompleteRunAudioTrace.Record engine) {
        assertEquals(missing, CompleteRunAudioComparator.difference(reference, engine).kind());
        assertEquals(extra, CompleteRunAudioComparator.difference(engine, reference).kind());
    }

    private static void assertSemanticFailure(CompleteRunAudioReport report, CompleteRunAudioReport.Side side,
            CompleteRunAudioComparator.ValidationException.Kind kind) {
        assertEquals(CompleteRunAudioReport.Kind.CAPTURE_FAILURE, report.kind());
        assertEquals(side, report.failureSide());
        assertEquals(kind, report.validationKind());
    }

    private TestProfile registerProfile(int frames) {
        TestProfile profile = profile("comparator.test." + PROFILE_SEQUENCE.incrementAndGet(), frames);
        CompleteRunAudioProfiles.register(profile);
        return profile;
    }

    private TestProfile registerProfile(int frames, List<ManifestSegment> segments) {
        TestProfile profile = profile("comparator.test." + PROFILE_SEQUENCE.incrementAndGet(), frames, segments);
        CompleteRunAudioProfiles.register(profile);
        return profile;
    }

    private static TestProfile profile(String id, int frames) {
        return profile(id, frames,
                List.of(new ManifestSegment("test", FIRST_FRAME, FIRST_FRAME + frames)));
    }

    private static TestProfile profile(String id, int frames, List<ManifestSegment> segments) {
        int end = FIRST_FRAME + frames;
        CompleteRunFixture fixture = new CompleteRunFixture("0".repeat(40), "1".repeat(8), "2".repeat(64),
                end, "3".repeat(64), segments, FIRST_FRAME, end);
        return new TestProfile(id, fixture);
    }

    private Path writeCapture(String name, TestProfile profile, ProducerKind kind, int frames,
            IntFunction<Frame> framesFactory) throws Exception {
        return writeCapture(name, metadata(profile, kind, profile.producerRuntimeIdentities().get(kind)),
                frames, framesFactory);
    }

    private Path writeCapture(String name, Metadata metadata, int frames, IntFunction<Frame> framesFactory)
            throws Exception {
        List<CompleteRunAudioTrace.Record> records = new ArrayList<>();
        records.add(new Baseline(FIRST_FRAME, state(1)));
        for (int row = 0; row < frames; row++) records.add(framesFactory.apply(row));
        records.add(terminal(metadata.fixture().exclusiveEnd(), records));
        Path output = temp.resolve(name);
        new CompleteRunAudioCaptureStore().writeNew(output, metadata, records.iterator());
        return output;
    }

    private Path writeRecords(String name, Metadata metadata, List<CompleteRunAudioTrace.Record> records)
            throws Exception {
        List<CompleteRunAudioTrace.Record> complete = new ArrayList<>(records);
        complete.add(terminal(metadata.fixture().exclusiveEnd(), complete));
        Path output = temp.resolve(name);
        new CompleteRunAudioCaptureStore().writeNew(output, metadata, complete.iterator());
        return output;
    }

    private Path writeStreamingCapture(String name, TestProfile profile, ProducerKind kind, int frames)
            throws Exception {
        String digest = streamingRoot(frames);
        Metadata metadata = metadata(profile, kind, profile.producerRuntimeIdentities().get(kind));
        Iterator<CompleteRunAudioTrace.Record> records = new Iterator<>() {
            private long cursor;
            @Override public boolean hasNext() { return cursor < 2L * frames + 2; }
            @Override public CompleteRunAudioTrace.Record next() {
                if (!hasNext()) throw new NoSuchElementException();
                long current = cursor++;
                if (current == 0) return new Baseline(FIRST_FRAME, state(1));
                if (current == 2L * frames + 1) {
                    return new Terminal(FIRST_FRAME + frames, frames, frames, frames, frames,
                            frames, frames, frames, digest);
                }
                int row = (int) ((current - 1) / 2);
                return (current & 1) == 1 ? entropyLifecycle(row, frames) : entropyFrame(row);
            }
        };
        Path output = temp.resolve(name);
        new CompleteRunAudioCaptureStore().writeNew(output, metadata, records);
        return output;
    }

    private static String streamingRoot(int frames) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, new Baseline(FIRST_FRAME, state(1)));
            for (int row = 0; row < frames; row++) {
                update(digest, entropyLifecycle(row, frames));
                update(digest, entropyFrame(row));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }

    private static Frame entropyFrame(int row) {
        Request request = request(row, 0xc0);
        OwnerRef owner = owner(row, 0xc0);
        Decision decision = new Decision(row, 0xc0, "sfx.c0", true,
                "accepted." + Integer.toUnsignedString(Integer.rotateLeft(row * 0x9e3779b9, 13), 16),
                row & 0xff, (row + 1) & 0xff, List.of(HardwareRole.FM1),
                List.of(new RoleDecision(HardwareRole.FM1, NONE, owner)));
        NormalizedState state = new NormalizedState(List.of(new StateField("tempo", entropy(row))),
                List.of(new RoleState(HardwareRole.FM1, true,
                        List.of(new StateField("cursor", Integer.toUnsignedLong(row * 0x45d9f3b))))));
        List<ChipEvent> chipEvents = List.of(
                new YmWrite(2L * row, row & 1, (row >>> 1) & 0xff, (row * 73) & 0xff),
                new PsgWrite(2L * row + 1, (row * 151) & 0xff));
        return new Frame(FIRST_FRAME + row, "test", (row & 1) != 0, List.of(request),
                List.of(service(row, List.of(decision), chipEvents, state,
                        "driver." + Integer.toUnsignedString(row * 0x9e3779b9, 16))));
    }

    private static Lifecycle entropyLifecycle(int row, int frames) {
        return new Lifecycle(row, FIRST_FRAME + row, "pulse", Map.of("payload", entropy(frames + row)));
    }

    private static String entropy(int row) {
        StringBuilder value = new StringBuilder(1_024);
        long state = 0x9e3779b97f4a7c15L ^ row;
        for (int index = 0; index < 128; index++) {
            state += 0x9e3779b97f4a7c15L;
            long mixed = state;
            mixed = (mixed ^ (mixed >>> 30)) * 0xbf58476d1ce4e5b9L;
            mixed = (mixed ^ (mixed >>> 27)) * 0x94d049bb133111ebL;
            mixed ^= mixed >>> 31;
            String hex = Long.toUnsignedString(mixed, 16);
            value.append("0".repeat(16 - hex.length())).append(hex);
        }
        return value.toString();
    }

    private static long compressedBytes(Path capture) throws IOException {
        try (var chunks = Files.list(capture.resolve("chunks"))) {
            return chunks.mapToLong(path -> {
                try { return Files.size(path); }
                catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
            }).sum();
        }
    }

    private static long uncompressedGzipBytes(Path capture) throws IOException {
        try (var chunks = Files.list(capture.resolve("chunks"))) {
            return chunks.mapToLong(path -> {
                try {
                    byte[] bytes = Files.readAllBytes(path);
                    int end = bytes.length;
                    return (bytes[end - 4] & 0xffL) | (bytes[end - 3] & 0xffL) << 8
                            | (bytes[end - 2] & 0xffL) << 16 | (bytes[end - 1] & 0xffL) << 24;
                } catch (IOException failure) {
                    throw new java.io.UncheckedIOException(failure);
                }
            }).sum();
        }
    }

    private static void alterGzipHeaderAndManifest(Path capture) throws Exception {
        Path chunk = capture.resolve("chunks/000000.jsonl.gz");
        byte[] bytes = Files.readAllBytes(chunk);
        String previous = sha256(chunk);
        bytes[9] = bytes[9] == 3 ? (byte) 0xff : (byte) 3;
        Files.write(chunk, bytes);
        String changed = sha256(chunk);
        String manifest = Files.readString(capture.resolve("manifest.json"), StandardCharsets.UTF_8);
        Files.writeString(capture.resolve("manifest.json"), manifest.replace(previous, changed),
                StandardCharsets.UTF_8);
    }

    private static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) >= 0) digest.update(buffer, 0, count);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String sha256(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest.digest());
    }

    private static List<CompleteRunAudioTrace.Record> readAll(Path capture) throws Exception {
        List<CompleteRunAudioTrace.Record> records = new ArrayList<>();
        try (CompleteRunAudioCaptureStore.Reader reader = new CompleteRunAudioCaptureStore().read(capture)) {
            while (reader.hasNext()) records.add(reader.next());
        }
        return List.copyOf(records);
    }

    private static Metadata metadata(TestProfile profile, ProducerKind kind,
            ProducerRuntimeIdentity runtime) {
        return new Metadata(SCHEMA, profile.id(), profile.fixture(), kind, runtime,
                new ObserverProof(kind == ProducerKind.REFERENCE ? "reference.test.v1" : "openggf.test.v1",
                        kind == ProducerKind.REFERENCE ? "m68k.execute" : "java.observer",
                        List.of(new CallbackProof("driver.service", 1))),
                new ChunkPolicy(CHUNK_FRAME_ROWS, "gzip", 0), profile.hardwareRoles(),
                profile.stateInventory());
    }

    private Frame shiftedAdmissionFrame(int row, Decision admission, int admissionRow) {
        List<Request> requests = row == 0 ? List.of(request(0, 0xc0)) : List.of();
        if (row == 98 || row == 99) {
            long serviceOrdinal = row - 98;
            return new Frame(FIRST_FRAME + row, "test", false, requests,
                    List.of(service(serviceOrdinal, row == admissionRow ? List.of(admission) : List.of(),
                            List.of(), state(1))));
        }
        return new Frame(FIRST_FRAME + row, "test", false, requests, List.of());
    }

    private Frame plainFrame(int row) {
        return new Frame(FIRST_FRAME + row, "test", false, List.of(), List.of());
    }

    private static Frame requestFrame(int row, Request request) {
        return new Frame(FIRST_FRAME + row, "test", false, List.of(request), List.of());
    }

    private static Frame requestAndDecisionFrame(int row, Request request, Decision decision,
            long serviceOrdinal) {
        return new Frame(FIRST_FRAME + row, "test", false, List.of(request),
                List.of(service(serviceOrdinal, List.of(decision), List.of(), state(1))));
    }

    private static Frame chipFrame(int row, int value) {
        return new Frame(FIRST_FRAME + row, "test", false, List.of(),
                List.of(service(0, List.of(), List.of(new YmWrite(0, 0, 0x22, value)), state(1))));
    }

    private static Frame frame(DriverService service) {
        return new Frame(FIRST_FRAME, "test", false, List.of(), List.of(service));
    }

    private static Request request(long ordinal, int nativeId) {
        return new Request(ordinal, OwnerClass.SFX, "sfx." + Integer.toHexString(nativeId), nativeId,
                "mailbox", 0);
    }

    private static Decision decision(long requestOrdinal, Integer before, Integer after, OwnerRef finalOwner) {
        return new Decision(requestOrdinal, finalOwner.nativeId(), finalOwner.contentKey(), true, "accepted",
                before, after, List.of(HardwareRole.FM1),
                List.of(new RoleDecision(HardwareRole.FM1, NONE, finalOwner)));
    }

    private static Decision decisionForKey(long requestOrdinal, String key) {
        int nativeId = key.endsWith("a") ? 0xc0 : 0xc1;
        OwnerRef finalOwner = new OwnerRef(OwnerClass.SFX, key, nativeId, requestOrdinal);
        return new Decision(requestOrdinal, nativeId, key, true, "accepted", 1, 2,
                List.of(HardwareRole.FM1), List.of(new RoleDecision(HardwareRole.FM1, NONE, finalOwner)));
    }

    private static Decision decisionForRole(long requestOrdinal, int nativeId, HardwareRole role,
            OwnerRef finalOwner) {
        return new Decision(requestOrdinal, nativeId, "sfx." + Integer.toHexString(nativeId), true,
                "accepted", 1, 2, List.of(role), List.of(new RoleDecision(role, NONE, finalOwner)));
    }

    private static OwnerRef owner(long requestOrdinal, int nativeId) {
        return new OwnerRef(OwnerClass.SFX, "sfx." + Integer.toHexString(nativeId), nativeId,
                requestOrdinal);
    }

    private static DriverService service(long ordinal, List<Decision> decisions, List<ChipEvent> chipEvents,
            NormalizedState state) {
        return service(ordinal, decisions, chipEvents, state, "driver");
    }

    private static DriverService service(long ordinal, List<Decision> decisions, List<ChipEvent> chipEvents,
            NormalizedState state, String kind) {
        return new DriverService(ordinal, kind, decisions, state, chipEvents);
    }

    private static NormalizedState state(int tempo) {
        return new NormalizedState(List.of(new StateField("tempo", tempo)), inactiveRoles());
    }

    private static List<RoleState> inactiveRoles() {
        return List.of(new RoleState(HardwareRole.FM1, false, List.of()));
    }

    private static Terminal terminal(int exclusiveEnd, List<CompleteRunAudioTrace.Record> records) {
        long frames = 0, requests = 0, services = 0, decisions = 0, ym = 0, psg = 0, lifecycles = 0;
        for (CompleteRunAudioTrace.Record record : records) {
            if (record instanceof Frame frame) {
                frames++;
                requests += frame.requests().size();
                for (DriverService service : frame.services()) {
                    services++;
                    decisions += service.decisions().size();
                    for (ChipEvent event : service.chipEvents()) {
                        if (event instanceof YmWrite) ym++; else psg++;
                    }
                }
            } else if (record instanceof Lifecycle) {
                lifecycles++;
            }
        }
        return new Terminal(exclusiveEnd, frames, requests, services, decisions, ym, psg, lifecycles,
                root(records));
    }

    private static String root(List<CompleteRunAudioTrace.Record> records) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (CompleteRunAudioTrace.Record record : records) update(digest, record);
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }

    private static void update(MessageDigest digest, CompleteRunAudioTrace.Record record) throws IOException {
        digest.update((CompleteRunAudioJson.writeRecord(record) + "\n").getBytes(StandardCharsets.UTF_8));
    }

    private static final class TestProfile implements CompleteRunAudioProfile {
        private final String id;
        private final CompleteRunFixture fixture;
        private final Map<ProducerKind, ProducerRuntimeIdentity> runtimes = new LinkedHashMap<>();
        private final Map<ProducerKind, ObserverProof> observers = new LinkedHashMap<>();

        private TestProfile(String id, CompleteRunFixture fixture) {
            this.id = id;
            this.fixture = fixture;
            runtimes.put(ProducerKind.REFERENCE, new ProducerRuntimeIdentity(
                    "BizHawk", "2.11", "BizHawk", "2.11", "GPGX", "1.0",
                    Map.of(RuntimeArtifact.BIZHAWK_EXECUTABLE, "1".repeat(64),
                            RuntimeArtifact.BIZHAWK_CORE_DLL, "2".repeat(64),
                            RuntimeArtifact.GPGX_CORE, "3".repeat(64))));
            runtimes.put(ProducerKind.OPENGGF, new ProducerRuntimeIdentity(
                    "OpenGGF", "test", "OpenGGF", "test", "SMPS", "test",
                    Map.of(RuntimeArtifact.OPENGGF_PRODUCER, "4".repeat(64))));
            observers.put(ProducerKind.REFERENCE,
                    new ObserverProof("reference.test.v1", "m68k.execute",
                            List.of(new CallbackProof("driver.service", 1))));
            observers.put(ProducerKind.OPENGGF,
                    new ObserverProof("openggf.test.v1", "java.observer",
                            List.of(new CallbackProof("driver.service", 1))));
        }

        @Override public String id() { return id; }
        @Override public CompleteRunFixture fixture() { return fixture; }
        @Override public List<HardwareRole> hardwareRoles() { return List.of(HardwareRole.FM1); }
        @Override public StateInventory stateInventory() {
            return new StateInventory(List.of("tempo"), List.of("cursor"));
        }
        @Override public Map<RawAudioRequest, NativeSoundIdentity> nativeSoundIdentities() {
            return Map.of(
                    new RawAudioRequest(OwnerClass.SFX, 0xc0, "mailbox", 0),
                    new NativeSoundIdentity(OwnerClass.SFX, "sfx.c0", 0xc0),
                    new RawAudioRequest(OwnerClass.SFX, 0xc1, "mailbox", 0),
                    new NativeSoundIdentity(OwnerClass.SFX, "sfx.c1", 0xc1));
        }
        @Override public Map<ProducerKind, ProducerRuntimeIdentity> producerRuntimeIdentities() {
            return Map.copyOf(runtimes);
        }
        @Override public Map<ProducerKind, ObserverProof> observerProofs() {
            return Map.copyOf(observers);
        }
        @Override public Map<NativeSoundIdentity, List<NativeSoundIdentity>> decisionResolutions() {
            NativeSoundIdentity c0 = new NativeSoundIdentity(OwnerClass.SFX, "sfx.c0", 0xc0);
            NativeSoundIdentity c1 = new NativeSoundIdentity(OwnerClass.SFX, "sfx.c1", 0xc1);
            return Map.of(c0, List.of(c0), c1, List.of(c1));
        }
        @Override public Map<Long, NativeSoundIdentity> baselineOwnerIdentities() { return Map.of(); }
        @Override public Map<String, LifecycleRule> lifecycleRules() {
            return Map.of("pulse", new LifecycleRule("pulse", List.of("payload")));
        }
    }
}
