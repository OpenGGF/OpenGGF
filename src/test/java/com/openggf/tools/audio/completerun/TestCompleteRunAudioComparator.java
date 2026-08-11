package com.openggf.tools.audio.completerun;

import static com.openggf.tools.audio.completerun.CompleteRunAudioTrace.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestCompleteRunAudioComparator {
    private static final AtomicInteger PROFILE_SEQUENCE = new AtomicInteger();
    private static final int FIRST_FRAME = 860;
    private static final OwnerRef NONE = new OwnerRef(OwnerClass.NONE, "none", 0,
            OwnerOrigin.NONE, -1);

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
    void baselineSemanticFrontierComparesAcrossProducersAndIgnoresNativeSidecar() throws Exception {
        TestProfile profile = profile("comparator.baseline-frontier."
                + PROFILE_SEQUENCE.incrementAndGet(), 1);
        profile.useBufferedReference(
                new FrontierServiceRule("UpdateMusic", FrontierServiceState.OPEN,
                        1, "M68K", 0x71b4c, null, null, List.of()),
                new FrontierServiceRule("UpdateMusic", FrontierServiceState.COMPLETED,
                        1, "M68K", 0x71b4c, 2, 0x71c4c, List.of()));
        CompleteRunAudioProfiles.register(profile);
        NormalizedState state = baselineState(profile);
        CutoffService carried = new CutoffService(null, -1, 0, "UpdateMusic",
                FrontierServiceState.CARRIED_IN_OPEN, FIRST_FRAME, 0, null, null, List.of());
        BoundaryFrontier semantic = new BoundaryFrontier(
                List.of(carried), List.of(), List.of(), null, 0, 0);
        FrontierService nativeOpen = new FrontierService(1, 0, 0, "UpdateMusic",
                FrontierServiceState.OPEN, FIRST_FRAME - 1, 0, 0x71b4c, 1, "M68K",
                null, null, null, null, List.of(), List.of());
        BoundaryFrontier reference = new BoundaryFrontier(
                semantic.activeStack(), semantic.pendingDescendants(), semantic.rawChipEvents(),
                new CutoffNativeDiagnostics(List.of(nativeOpen), List.of(), List.of(), List.of(),
                        0, false, "f".repeat(64)), 0, 0);
        List<RoleOwner> owners = profile.baselineRoleOwners();
        Baseline expected = new Baseline(FIRST_FRAME, state, owners, reference);
        Baseline actual = new Baseline(FIRST_FRAME, state, owners, semantic);

        assertEquals(null, CompleteRunAudioComparator.difference(expected, actual));
        assertNotEquals(CompleteRunAudioJson.writeRecord(expected), CompleteRunAudioJson.writeRecord(actual));
        assertEquals(CompleteRunAudioJson.writeSemanticRecord(expected),
                CompleteRunAudioJson.writeSemanticRecord(actual));

        DriverService carriedCompletion = new DriverService(0, "UpdateMusic",
                ServiceCompletion.COMPLETED, List.of(), state, List.of(), 0L);
        Frame engineFrame = new Frame(FIRST_FRAME, "test", false, List.of(),
                List.of(carriedCompletion));
        FrontierService nativeCompletion = new FrontierService(1, 0, 0, "UpdateMusic",
                FrontierServiceState.COMPLETED, FIRST_FRAME - 1, 0, 0x71b4c, 1, "M68K",
                FIRST_FRAME, 0L, 0x71c4c, 2, List.of(), List.of());
        Frame referenceFrame = new Frame(FIRST_FRAME, "test", false, List.of(),
                List.of(carriedCompletion), List.of(),
                new FrameNativeDiagnostics(List.of(nativeCompletion), List.of(), List.of()));
        Path referenceCapture = writeCapture("baseline-frontier-reference",
                metadata(profile, ProducerKind.REFERENCE,
                        profile.producerRuntimeIdentities().get(ProducerKind.REFERENCE)),
                expected, 1, ignored -> referenceFrame);
        Path engineCapture = writeCapture("baseline-frontier-engine",
                metadata(profile, ProducerKind.OPENGGF,
                        profile.producerRuntimeIdentities().get(ProducerKind.OPENGGF)),
                actual, 1, ignored -> engineFrame);
        CompleteRunAudioReport report = CompleteRunAudioComparator.compare(referenceCapture, engineCapture);
        assertEquals(CompleteRunAudioReport.Kind.MATCH, report.kind(), report.toText());
        assertNotEquals(report.reference().rootDigest(), report.engine().rootDigest());

        DriverService wrongLink = new DriverService(0, "UpdateMusic", ServiceCompletion.COMPLETED,
                List.of(), state, List.of(), 1L);
        Path wrongEngine = writeCapture("baseline-frontier-wrong-link",
                metadata(profile, ProducerKind.OPENGGF,
                        profile.producerRuntimeIdentities().get(ProducerKind.OPENGGF)),
                actual, 1, ignored -> new Frame(FIRST_FRAME, "test", false, List.of(), List.of(wrongLink)));
        assertSemanticFailure(CompleteRunAudioComparator.compare(referenceCapture, wrongEngine),
                CompleteRunAudioReport.Side.ENGINE,
                CompleteRunAudioComparator.ValidationException.Kind.STATE_INVALID);
        Path missingEngine = writeCapture("baseline-frontier-missing-closure",
                metadata(profile, ProducerKind.OPENGGF,
                        profile.producerRuntimeIdentities().get(ProducerKind.OPENGGF)),
                actual, 1, this::plainFrame);
        assertSemanticFailure(CompleteRunAudioComparator.compare(referenceCapture, missingEngine),
                CompleteRunAudioReport.Side.ENGINE,
                CompleteRunAudioComparator.ValidationException.Kind.STATE_INVALID);

        CutoffService outer = new CutoffService(null, -1, 0, "UpdateMusic",
                FrontierServiceState.CARRIED_IN_OPEN, FIRST_FRAME, 0, null, null, List.of());
        CutoffService inner = new CutoffService(FIRST_FRAME, 0, 1, "UpdateMusic",
                FrontierServiceState.CARRIED_IN_OPEN, FIRST_FRAME, 1, null, null, List.of());
        Baseline nested = new Baseline(FIRST_FRAME, state, owners,
                new BoundaryFrontier(List.of(outer, inner), List.of(), List.of(), null, 0, 0));
        DriverService outerFirst = new DriverService(0, "UpdateMusic",
                ServiceCompletion.COMPLETED, List.of(), state, List.of(), 0L);
        Path wrongReleaseOrder = writeCapture("baseline-frontier-outer-first",
                metadata(profile, ProducerKind.OPENGGF,
                        profile.producerRuntimeIdentities().get(ProducerKind.OPENGGF)),
                nested, 1, ignored -> new Frame(FIRST_FRAME, "test", false, List.of(),
                        List.of(outerFirst)));
        assertSemanticFailure(CompleteRunAudioComparator.compare(referenceCapture, wrongReleaseOrder),
                CompleteRunAudioReport.Side.ENGINE,
                CompleteRunAudioComparator.ValidationException.Kind.STATE_INVALID);
    }

    @Test
    void baselineNativeCarryProofRejectsAFirstOwnedChipBeforeItsCarryInInventory() throws Exception {
        TestProfile profile = profile("comparator.baseline-chip-order."
                + PROFILE_SEQUENCE.incrementAndGet(), 1);
        profile.useBufferedReference(
                new FrontierServiceRule("UpdateMusic", FrontierServiceState.OPEN,
                        1, "M68K", 0x71b4c, null, null, List.of()),
                new FrontierServiceRule("UpdateMusic", FrontierServiceState.COMPLETED,
                        1, "M68K", 0x71b4c, 2, 0x71c4c, List.of()));
        CompleteRunAudioProfiles.register(profile);
        NormalizedState state = baselineState(profile);
        CutoffService carried = new CutoffService(null, -1, 0, "UpdateMusic",
                FrontierServiceState.CARRIED_IN_OPEN, FIRST_FRAME, 0, null, null, List.of());
        FrontierChipEvent address = new FrontierChipEvent(100, 0, "M68K", 0x71b4c,
                3, 0, 0x22, false, 0, 0x22);
        FrontierService nativeOpen = new FrontierService(1, 0, 0, "UpdateMusic",
                FrontierServiceState.OPEN, FIRST_FRAME - 1, 0, 0x71b4c, 1, "M68K",
                null, null, null, null, List.of(), List.of(address));
        Baseline referenceBaseline = new Baseline(FIRST_FRAME, state, profile.baselineRoleOwners(),
                new BoundaryFrontier(List.of(carried), List.of(), List.of(),
                        new CutoffNativeDiagnostics(List.of(nativeOpen), List.of(),
                                List.of(new FrontierOwnedChip(1, address)), List.of(),
                                0, false, "f".repeat(64)), 0x22, 0));
        Baseline engineBaseline = new Baseline(FIRST_FRAME, state, profile.baselineRoleOwners(),
                new BoundaryFrontier(List.of(carried), List.of(), List.of(), null, 0x22, 0));
        YmWrite semanticWrite = new YmWrite(0, 0, 0x22, 7);
        DriverService completion = new DriverService(0, "UpdateMusic", ServiceCompletion.COMPLETED,
                List.of(), state, List.of(semanticWrite), 0L);
        FrontierChipEvent regressing = new FrontierChipEvent(99, 0, "M68K", 0x71c4c,
                3, 1, 7, true, 0, 0x22);
        FrontierService nativeCompletion = new FrontierService(1, 0, 0, "UpdateMusic",
                FrontierServiceState.COMPLETED, FIRST_FRAME - 1, 0, 0x71b4c, 1, "M68K",
                FIRST_FRAME, 0L, 0x71c4c, 2, List.of(), List.of(regressing));
        Frame badReferenceFrame = new Frame(FIRST_FRAME, "test", false, List.of(), List.of(completion),
                List.of(semanticWrite), new FrameNativeDiagnostics(List.of(nativeCompletion),
                        List.of(new FrontierOwnedChip(1, regressing)), List.of()));
        Frame engineFrame = new Frame(FIRST_FRAME, "test", false, List.of(), List.of(completion));

        Path reference = writeCapture("baseline-chip-order-reference",
                metadata(profile, ProducerKind.REFERENCE,
                        profile.producerRuntimeIdentities().get(ProducerKind.REFERENCE)),
                referenceBaseline, 1, ignored -> badReferenceFrame);
        Path engine = writeCapture("baseline-chip-order-engine",
                metadata(profile, ProducerKind.OPENGGF,
                        profile.producerRuntimeIdentities().get(ProducerKind.OPENGGF)),
                engineBaseline, 1, ignored -> engineFrame);

        assertSemanticFailure(CompleteRunAudioComparator.compare(reference, engine),
                CompleteRunAudioReport.Side.REFERENCE,
                CompleteRunAudioComparator.ValidationException.Kind.STATE_INVALID);
    }

    @Test
    void carriedBoundaryLinkIsForbiddenWithoutBaselineCarryIn() throws Exception {
        TestProfile profile = registerProfile(1);
        NormalizedState state = baselineState(profile);
        Path reference = writeCapture("ordinary-service-reference", profile, ProducerKind.REFERENCE,
                1, this::plainFrame);
        DriverService impossibleLink = new DriverService(0, "UpdateMusic",
                ServiceCompletion.COMPLETED, List.of(), state, List.of(), 0L);
        Path engine = writeCapture("ordinary-service-carried-link", profile, ProducerKind.OPENGGF,
                1, ignored -> new Frame(FIRST_FRAME, "test", false, List.of(),
                        List.of(impossibleLink)));

        assertSemanticFailure(CompleteRunAudioComparator.compare(reference, engine),
                CompleteRunAudioReport.Side.ENGINE,
                CompleteRunAudioComparator.ValidationException.Kind.STATE_INVALID);
    }

    @Test
    void unresolvedCarryMustContinueAsTheSameOpenCutoffService() throws Exception {
        TestProfile profile = profile("comparator.baseline-open-cutoff."
                + PROFILE_SEQUENCE.incrementAndGet(), 1);
        FrontierServiceRule openRule = new FrontierServiceRule("UpdateMusic",
                FrontierServiceState.OPEN, 1, "M68K", 0x71b4c, null, null, List.of());
        profile.useBufferedReference(openRule);
        NormalizedState state = baselineState(profile);
        CutoffService carried = new CutoffService(null, -1, 0, "UpdateMusic",
                FrontierServiceState.CARRIED_IN_OPEN, FIRST_FRAME, 0, null, null, List.of());
        CutoffService open = new CutoffService(null, -1, 0, "UpdateMusic",
                FrontierServiceState.OPEN, FIRST_FRAME, 0, null, null, List.of());
        FrontierService nativeOpen = new FrontierService(1, 0, 0, "UpdateMusic",
                FrontierServiceState.OPEN, FIRST_FRAME - 1, 0, 0x71b4c, 1, "M68K",
                null, null, null, null, List.of(), List.of());
        CutoffNativeDiagnostics nativeProof = new CutoffNativeDiagnostics(List.of(nativeOpen),
                List.of(), List.of(), List.of(), 0, false, "f".repeat(64));
        CutoffFrontier referenceCutoff = new CutoffFrontier(List.of(open), List.of(), List.of(),
                nativeProof, 0, 0, state);
        CutoffFrontier engineCutoff = new CutoffFrontier(List.of(open), List.of(), List.of(),
                null, 0, 0, state);
        profile.cutoffPolicy = new CutoffFrontierPolicy(List.of(openRule), 1, 0,
                0, 0, 0, 0, 0, 0, false, "f".repeat(64),
                CutoffFrontierPolicy.capabilityDigest(referenceCutoff),
                CutoffFrontierPolicy.nativeCapabilityDigest(nativeProof));
        CompleteRunAudioProfiles.register(profile);
        BoundaryFrontier semanticBaseline = new BoundaryFrontier(List.of(carried), List.of(),
                List.of(), null, 0, 0);
        BoundaryFrontier nativeBaseline = new BoundaryFrontier(List.of(carried), List.of(),
                List.of(), nativeProof, 0, 0);
        Baseline expected = new Baseline(FIRST_FRAME, state, profile.baselineRoleOwners(), nativeBaseline);
        Baseline actual = new Baseline(FIRST_FRAME, state, profile.baselineRoleOwners(), semanticBaseline);

        Path reference = writeCaptureWithCutoff("baseline-open-cutoff-reference", profile,
                ProducerKind.REFERENCE, expected,
                bufferedFrame(0, List.of(), List.of()), referenceCutoff);
        Path engine = writeCaptureWithCutoff("baseline-open-cutoff-engine", profile,
                ProducerKind.OPENGGF, actual, plainFrame(0), engineCutoff);

        CompleteRunAudioReport report = CompleteRunAudioComparator.compare(reference, engine);
        assertEquals(CompleteRunAudioReport.Kind.MATCH, report.kind(), report.toText());

        CutoffService wrongOpen = new CutoffService(null, -1, 0, "UpdateMusic",
                FrontierServiceState.OPEN, FIRST_FRAME + 1, 0, null, null, List.of());
        CutoffFrontier wrongCutoff = new CutoffFrontier(List.of(wrongOpen), List.of(),
                List.of(), null, 0, 0, state);
        Path wrongEngine = writeCaptureWithCutoff("baseline-open-cutoff-wrong", profile,
                ProducerKind.OPENGGF, actual, plainFrame(0), wrongCutoff);
        assertSemanticFailure(CompleteRunAudioComparator.compare(reference, wrongEngine),
                CompleteRunAudioReport.Side.ENGINE,
                CompleteRunAudioComparator.ValidationException.Kind.STATE_INVALID);
    }

    @Test
    void bufferedReferenceDiagnosticsRoundTripAndCompareSemanticallyToOpenGgf() throws Exception {
        TestProfile profile = profile("comparator.buffered." + PROFILE_SEQUENCE.incrementAndGet(), 1);
        profile.useBufferedReference(new FrontierServiceRule("driver", FrontierServiceState.COMPLETED,
                1, "Z80", 0x38, 2, 0x40, List.of()));
        CompleteRunAudioProfiles.register(profile);
        NormalizedState state = state(1);
        DriverService semantic = service(0, List.of(), List.of(new PsgWrite(0, 0x9f)), state, "driver");
        FrontierChipEvent rawWrite = new FrontierChipEvent(1, 1, "Z80", 0x39, 4, 0, 0x9f,
                true, null, null);
        FrontierService rawService = new FrontierService(1, 0, 0, "driver", FrontierServiceState.COMPLETED,
                FIRST_FRAME, 1, 0x38, 1, "Z80", FIRST_FRAME, 3L, 0x40, 2,
                List.of(), List.of(rawWrite));
        FrameNativeDiagnostics diagnostics = new FrameNativeDiagnostics(List.of(rawService),
                List.of(new FrontierOwnedChip(1, rawWrite)), List.of());
        Frame referenceFrame = new Frame(FIRST_FRAME, "test", false, List.of(), List.of(semantic),
                List.of(new PsgWrite(0, 0x9f)), diagnostics);
        Frame engineFrame = new Frame(FIRST_FRAME, "test", false, List.of(), List.of(semantic));

        Path reference = writeCapture("buffered-reference", profile, ProducerKind.REFERENCE, 1,
                ignored -> referenceFrame);
        Path engine = writeCapture("buffered-engine", profile, ProducerKind.OPENGGF, 1,
                ignored -> engineFrame);

        CompleteRunAudioReport report = CompleteRunAudioComparator.compare(reference, engine);
        assertEquals(CompleteRunAudioReport.Kind.MATCH, report.kind(), report.toText());
        assertNotEquals(report.reference().rootDigest(), report.engine().rootDigest());
        assertNotEquals(readAll(reference).get(1), readAll(engine).get(1));

        CutoffFrontier semanticOnly = CutoffFrontier.empty(state);
        Path missingReferenceDiagnostics = writeCaptureWithCutoff("missing-reference-cutoff-diagnostics",
                profile, ProducerKind.REFERENCE, referenceFrame, semanticOnly);
        CompleteRunAudioReport missing = CompleteRunAudioComparator.compare(missingReferenceDiagnostics, engine);
        assertSemanticFailure(missing, CompleteRunAudioReport.Side.REFERENCE,
                CompleteRunAudioComparator.ValidationException.Kind.STATE_INVALID);

        CutoffFrontier unexpectedNative = new CutoffFrontier(List.of(), List.of(), List.of(),
                new CutoffNativeDiagnostics(List.of(), List.of(), List.of(), List.of(),
                        0, false, "f".repeat(64)), 0, 0, state);
        Path engineWithDiagnostics = writeCaptureWithCutoff("engine-with-cutoff-diagnostics",
                profile, ProducerKind.OPENGGF, engineFrame, unexpectedNative);
        CompleteRunAudioReport unexpected = CompleteRunAudioComparator.compare(reference, engineWithDiagnostics);
        assertSemanticFailure(unexpected, CompleteRunAudioReport.Side.ENGINE,
                CompleteRunAudioComparator.ValidationException.Kind.STATE_INVALID);
    }

    @Test
    void bufferedM68kManagedBoundariesRemainBoundAcrossFramesAndAreAccountedAtCutoff() throws Exception {
        TestProfile profile = profile("comparator.buffered.crossframe."
                + PROFILE_SEQUENCE.incrementAndGet(), 2);
        profile.useBufferedReference(new FrontierServiceRule("driver", FrontierServiceState.COMPLETED,
                10, "M68K", 0x71b4c, 11, 0x71c4, List.of()));
        CompleteRunAudioProfiles.register(profile);
        long beginCoordinate = (long) FIRST_FRAME << 32 | 1;
        long endCoordinate = (long) (FIRST_FRAME + 1) << 32 | 3;
        NativeManagedCorrelation begin = new NativeManagedCorrelation(0, List.of(
                new NativeManagedEvent(beginCoordinate, 1, "M68K", 0x71b4c,
                        1, 0, 7, 0, 4, 0, 10, true)));
        NativeManagedCorrelation wrongBegin = new NativeManagedCorrelation(0, List.of(
                new NativeManagedEvent(beginCoordinate, 1, "M68K", 0x71b4e,
                        1, 0, 7, 0, 4, 0, 10, true)));
        NativeManagedCorrelation end = new NativeManagedCorrelation(0, List.of(
                new NativeManagedEvent(endCoordinate, 3, "M68K", 0x71c4,
                        2, 0, 7, 0, 4, 0, 11, true)));
        FrontierService rawService = new FrontierService(7, 0, 0, "driver",
                FrontierServiceState.COMPLETED, FIRST_FRAME, 1, 0x71b4c, 10, "M68K",
                FIRST_FRAME + 1, 3L, 0x71c4, 11, List.of(), List.of());
        DriverService semantic = service(0, List.of(), List.of(), state(1));
        Frame beginFrame = bufferedFrame(0, List.of(), List.of(), begin);
        Frame endFrame = bufferedFrame(1, List.of(semantic), List.of(rawService), end);
        Frame engineBegin = plainFrame(0);
        Frame engineEnd = new Frame(FIRST_FRAME + 1, "test", false, List.of(), List.of(semantic));

        Path reference = writeCapture("crossframe-reference", profile, ProducerKind.REFERENCE, 2,
                row -> row == 0 ? beginFrame : endFrame);
        Path engine = writeCapture("crossframe-engine", profile, ProducerKind.OPENGGF, 2,
                row -> row == 0 ? engineBegin : engineEnd);
        assertEquals(CompleteRunAudioReport.Kind.MATCH,
                CompleteRunAudioComparator.compare(reference, engine).kind());

        Path badBegin = writeCapture("crossframe-wrong-begin", profile, ProducerKind.REFERENCE, 2,
                row -> row == 0 ? bufferedFrame(0, List.of(), List.of(), wrongBegin) : endFrame);
        assertSemanticFailure(CompleteRunAudioComparator.compare(badBegin, engine),
                CompleteRunAudioReport.Side.REFERENCE,
                CompleteRunAudioComparator.ValidationException.Kind.STATE_INVALID);

        Path missingBegin = writeCapture("crossframe-missing-begin", profile, ProducerKind.REFERENCE, 2,
                row -> row == 0 ? bufferedFrame(0, List.of(), List.of()) : endFrame);
        assertSemanticFailure(CompleteRunAudioComparator.compare(missingBegin, engine),
                CompleteRunAudioReport.Side.REFERENCE,
                CompleteRunAudioComparator.ValidationException.Kind.STATE_INVALID);

        Path missingEnd = writeCapture("crossframe-missing-end", profile, ProducerKind.REFERENCE, 2,
                row -> row == 0 ? beginFrame
                        : bufferedFrame(1, List.of(semantic), List.of(rawService)));
        assertSemanticFailure(CompleteRunAudioComparator.compare(missingEnd, engine),
                CompleteRunAudioReport.Side.REFERENCE,
                CompleteRunAudioComparator.ValidationException.Kind.STATE_INVALID);

        TestProfile wrongEndProfile = profile("comparator.buffered.crossframe.wrongend."
                + PROFILE_SEQUENCE.incrementAndGet(), 3);
        wrongEndProfile.useBufferedReference(new FrontierServiceRule("driver",
                FrontierServiceState.COMPLETED, 10, "M68K", 0x71b4c, 11, 0x71c4, List.of()));
        CompleteRunAudioProfiles.register(wrongEndProfile);
        NativeManagedCorrelation earlyEnd = new NativeManagedCorrelation(0, List.of(
                new NativeManagedEvent(endCoordinate, 3, "M68K", 0x71c4,
                        2, 0, 7, 0, 4, 0, 11, true)));
        FrontierService lateService = new FrontierService(7, 0, 0, "driver",
                FrontierServiceState.COMPLETED, FIRST_FRAME, 1, 0x71b4c, 10, "M68K",
                FIRST_FRAME + 2, 5L, 0x71c4, 11, List.of(), List.of());
        Frame lateServiceFrame = bufferedFrame(2, List.of(semantic), List.of(lateService));
        Path wrongEnd = writeCapture("crossframe-wrong-end", wrongEndProfile,
                ProducerKind.REFERENCE, 3, row -> switch (row) {
                    case 0 -> beginFrame;
                    case 1 -> bufferedFrame(1, List.of(), List.of(), earlyEnd);
                    default -> lateServiceFrame;
                });
        Path lateEngine = writeCapture("crossframe-late-engine", wrongEndProfile,
                ProducerKind.OPENGGF, 3, row -> row == 2
                        ? new Frame(FIRST_FRAME + 2, "test", false, List.of(), List.of(semantic))
                        : plainFrame(row));
        assertSemanticFailure(CompleteRunAudioComparator.compare(wrongEnd, lateEngine),
                CompleteRunAudioReport.Side.REFERENCE,
                CompleteRunAudioComparator.ValidationException.Kind.STATE_INVALID);

        FrontierService openService = new FrontierService(7, 0, 0, "driver",
                FrontierServiceState.OPEN, FIRST_FRAME, 1, 0x71b4c, 10, "M68K",
                null, null, null, null, List.of(), List.of());
        CutoffFrontier nativeOpenCutoff = CutoffFrontier.fromNative(List.of(openService), List.of(),
                List.of(), List.of(), 0, 0, 0, false, state(1), "f".repeat(64));
        CutoffFrontier semanticOpenCutoff = new CutoffFrontier(nativeOpenCutoff.activeStack(),
                nativeOpenCutoff.pendingDescendants(), nativeOpenCutoff.rawChipEvents(), null,
                0, 0, state(1));
        TestProfile openProfile = profile("comparator.buffered.crossframe.open."
                + PROFILE_SEQUENCE.incrementAndGet(), 1);
        FrontierServiceRule openRule = new FrontierServiceRule("driver", FrontierServiceState.OPEN,
                10, "M68K", 0x71b4c, null, null, List.of());
        openProfile.useBufferedReference(openRule);
        openProfile.cutoffPolicy = new CutoffFrontierPolicy(List.of(openRule), 1, 0,
                0, 0, 0, 0, 0, 0, false, "f".repeat(64),
                CutoffFrontierPolicy.capabilityDigest(nativeOpenCutoff),
                CutoffFrontierPolicy.nativeCapabilityDigest(nativeOpenCutoff.nativeDiagnostics()));
        CompleteRunAudioProfiles.register(openProfile);
        Path openReference = writeCaptureWithCutoff("crossframe-open-reference", openProfile,
                ProducerKind.REFERENCE, beginFrame, nativeOpenCutoff);
        Path openEngine = writeCaptureWithCutoff("crossframe-open-engine", openProfile,
                ProducerKind.OPENGGF, plainFrame(0), semanticOpenCutoff);
        assertEquals(CompleteRunAudioReport.Kind.MATCH,
                CompleteRunAudioComparator.compare(openReference, openEngine).kind());

        TestProfile cutoffProfile = profile("comparator.buffered.crossframe.cutoff."
                + PROFILE_SEQUENCE.incrementAndGet(), 1);
        cutoffProfile.useBufferedReference(new FrontierServiceRule("driver", FrontierServiceState.COMPLETED,
                10, "M68K", 0x71b4c, 11, 0x71c4, List.of()));
        CompleteRunAudioProfiles.register(cutoffProfile);
        Path unaccounted = writeCapture("crossframe-unaccounted-cutoff", cutoffProfile,
                ProducerKind.REFERENCE, 1, ignored -> beginFrame);
        Path emptyEngine = writeCapture("crossframe-empty-engine", cutoffProfile,
                ProducerKind.OPENGGF, 1, this::plainFrame);
        assertSemanticFailure(CompleteRunAudioComparator.compare(unaccounted, emptyEngine),
                CompleteRunAudioReport.Side.REFERENCE,
                CompleteRunAudioComparator.ValidationException.Kind.STATE_INVALID);
        NativeManagedCorrelation duplicateBegin = new NativeManagedCorrelation(1, List.of(
                new NativeManagedEvent(beginCoordinate + 1, 2, "M68K", 0x71b4c,
                        1, 0, 7, 0, 4, 0, 10, true)));
        Path duplicateToken = writeCapture("crossframe-duplicate-token", cutoffProfile,
                ProducerKind.REFERENCE, 1,
                ignored -> bufferedFrame(0, List.of(), List.of(), begin, duplicateBegin));
        assertSemanticFailure(CompleteRunAudioComparator.compare(duplicateToken, emptyEngine),
                CompleteRunAudioReport.Side.REFERENCE,
                CompleteRunAudioComparator.ValidationException.Kind.STATE_INVALID);
    }

    @Test
    void promotedManagedChildConsumesTheEarlierRawTransitionAndClosesAtEffectiveRoot() throws Exception {
        TestProfile profile = profile("comparator.buffered.promotion."
                + PROFILE_SEQUENCE.incrementAndGet(), 2);
        profile.useBufferedReference(
                new FrontierServiceRule("dpcm", FrontierServiceState.COMPLETED,
                        10, "Z80", 0x77, 11, 0xac, List.of()),
                new FrontierServiceRule("driver", FrontierServiceState.COMPLETED,
                        12, "M68K", 0x71b4c, 13, 0x71c4c, List.of()));
        CompleteRunAudioProfiles.register(profile);
        NormalizedState state = state(1);
        ServiceCoordinate parentCoordinate = new ServiceCoordinate(FIRST_FRAME, 0);
        ServiceAncestry ancestry = new ServiceAncestry(parentCoordinate, 1, null, 0,
                List.of(new ServiceAncestryTransition(
                        parentCoordinate, 1, null, 0, FIRST_FRAME, 3)));
        DriverService parentSemantic = new DriverService(0, "dpcm", ServiceCompletion.COMPLETED,
                List.of(), state, List.of(), null, ServiceAncestry.root());
        DriverService childSemantic = new DriverService(1, "driver", ServiceCompletion.COMPLETED,
                List.of(), state, List.of(), null,
                new ServiceCoordinate(FIRST_FRAME, 0),
                new ServiceCoordinate(FIRST_FRAME + 1, 0), ancestry);
        FrontierService parent = new FrontierService(1, 0, 0, "dpcm",
                FrontierServiceState.COMPLETED, FIRST_FRAME, 0, 0x77, 10, "Z80",
                FIRST_FRAME, 2L, 0xac, 11, List.of(), List.of());
        NativeAncestryTransition promotion = new NativeAncestryTransition(
                3, FIRST_FRAME, 3, 1, 1, 0, 0, 11, "Z80", 0xac);
        FrontierService child = new FrontierService(2, 1, 1, "driver",
                FrontierServiceState.COMPLETED, FIRST_FRAME, 1, 0x71b4c, 12, "M68K",
                FIRST_FRAME + 1, 0L, 0x71c4c, 13, List.of(), List.of(), 0, 0,
                List.of(promotion), ancestry);
        NativeManagedCorrelation begin = new NativeManagedCorrelation(0, List.of(
                new NativeManagedEvent(1, 1, "M68K", 0x71b4c,
                        1, 0, 2, 1, 4, 1, 12, true)));
        NativeManagedCorrelation end = new NativeManagedCorrelation(0, List.of(
                new NativeManagedEvent(4, 0, "M68K", 0x71c4c,
                        2, 0, 2, 0, 4, 0, 13, true)));
        Frame referenceParent = new Frame(FIRST_FRAME, "test", false, List.of(),
                List.of(parentSemantic), List.of(), new FrameNativeDiagnostics(
                        List.of(parent), List.of(), List.of(), List.of(), List.of(begin),
                        List.of(new FrontierOwnedAncestryTransition(2, promotion))));
        Frame referenceChild = new Frame(FIRST_FRAME + 1, "test", false, List.of(),
                List.of(childSemantic), List.of(), new FrameNativeDiagnostics(
                        List.of(child), List.of(), List.of(), List.of(), List.of(end), List.of()));
        Frame engineParent = new Frame(FIRST_FRAME, "test", false, List.of(), List.of(parentSemantic));
        Frame engineChild = new Frame(FIRST_FRAME + 1, "test", false, List.of(), List.of(childSemantic));

        Path reference = writeCapture("promotion-reference", profile, ProducerKind.REFERENCE, 2,
                row -> row == 0 ? referenceParent : referenceChild);
        Path engine = writeCapture("promotion-engine", profile, ProducerKind.OPENGGF, 2,
                row -> row == 0 ? engineParent : engineChild);
        CompleteRunAudioReport report = CompleteRunAudioComparator.compare(reference, engine);
        assertEquals(CompleteRunAudioReport.Kind.MATCH, report.kind(), report.toText());

        Frame missingProofParent = new Frame(FIRST_FRAME, "test", false, List.of(),
                List.of(parentSemantic), List.of(), new FrameNativeDiagnostics(
                        List.of(parent), List.of(), List.of(), List.of(), List.of(begin), List.of()));
        Path missingProof = writeCapture("promotion-missing-proof", profile, ProducerKind.REFERENCE, 2,
                row -> row == 0 ? missingProofParent : referenceChild);
        assertSemanticFailure(CompleteRunAudioComparator.compare(missingProof, engine),
                CompleteRunAudioReport.Side.REFERENCE,
                CompleteRunAudioComparator.ValidationException.Kind.STATE_INVALID);

        Frame wrongOwnerParent = new Frame(FIRST_FRAME, "test", false, List.of(),
                List.of(parentSemantic), List.of(), new FrameNativeDiagnostics(
                        List.of(parent), List.of(), List.of(), List.of(), List.of(begin),
                        List.of(new FrontierOwnedAncestryTransition(3, promotion))));
        Path wrongOwner = writeCapture("promotion-wrong-owner", profile, ProducerKind.REFERENCE, 2,
                row -> row == 0 ? wrongOwnerParent : referenceChild);
        assertSemanticFailure(CompleteRunAudioComparator.compare(wrongOwner, engine),
                CompleteRunAudioReport.Side.REFERENCE,
                CompleteRunAudioComparator.ValidationException.Kind.STATE_INVALID);

        assertThrows(IllegalArgumentException.class, () -> new FrameNativeDiagnostics(
                List.of(parent), List.of(), List.of(), List.of(), List.of(begin),
                List.of(new FrontierOwnedAncestryTransition(2, promotion),
                        new FrontierOwnedAncestryTransition(2, promotion))));

        TestProfile cutoffProfile = profile("comparator.buffered.promotion.cutoff."
                + PROFILE_SEQUENCE.incrementAndGet(), 1);
        FrontierServiceRule parentRule = new FrontierServiceRule("dpcm",
                FrontierServiceState.COMPLETED, 10, "Z80", 0x77, 11, 0xac, List.of());
        FrontierServiceRule childRule = new FrontierServiceRule("driver",
                FrontierServiceState.OPEN, 12, "M68K", 0x71b4c, null, null, List.of());
        cutoffProfile.useBufferedReference(parentRule, childRule);
        FrontierService openChild = new FrontierService(2, 1, 1, "driver",
                FrontierServiceState.OPEN, FIRST_FRAME, 1, 0x71b4c, 12, "M68K",
                null, null, null, null, List.of(), List.of(), 0, 0,
                List.of(promotion), ancestry);
        CutoffFrontier nativeCutoff = CutoffFrontier.fromNative(List.of(openChild), List.of(),
                List.of(), List.of(), 0, 0, 0, false, state, "f".repeat(64));
        CutoffFrontier semanticCutoff = new CutoffFrontier(nativeCutoff.activeStack(),
                nativeCutoff.pendingDescendants(), nativeCutoff.rawChipEvents(), null,
                nativeCutoff.ymPort0Latch(), nativeCutoff.ymPort1Latch(), state);
        cutoffProfile.cutoffPolicy = new CutoffFrontierPolicy(List.of(parentRule, childRule),
                1, 0, 0, 0, 0, 0, 0, 0, false, "f".repeat(64),
                CutoffFrontierPolicy.capabilityDigest(nativeCutoff),
                CutoffFrontierPolicy.nativeCapabilityDigest(nativeCutoff.nativeDiagnostics()));
        CompleteRunAudioProfiles.register(cutoffProfile);
        Path cutoffReference = writeCaptureWithCutoff("promotion-cutoff-reference", cutoffProfile,
                ProducerKind.REFERENCE, referenceParent, nativeCutoff);
        Path cutoffEngine = writeCaptureWithCutoff("promotion-cutoff-engine", cutoffProfile,
                ProducerKind.OPENGGF, engineParent, semanticCutoff);
        CompleteRunAudioReport cutoffReport = CompleteRunAudioComparator.compare(
                cutoffReference, cutoffEngine);
        assertEquals(CompleteRunAudioReport.Kind.MATCH, cutoffReport.kind(), cutoffReport.toText());
    }

    @Test
    void bufferedResetDiagnosticsClearLatchesAndBindImmediateTypedLifecycleToCanonicalService() throws Exception {
        TestProfile profile = profile("comparator.buffered.reset." + PROFILE_SEQUENCE.incrementAndGet(), 1);
        profile.useBufferedReference(
                new FrontierServiceRule("driver", FrontierServiceState.COMPLETED,
                        1, "Z80", 0x38, 2, 0x40, List.of()),
                new FrontierServiceRule("reset", FrontierServiceState.COMPLETED,
                        0, "RESET", 0, 0, 0, List.of()));
        profile.lifecycleRules = Map.of(
                "power", new LifecycleRule("power", List.of("service_ordinal"),
                        LifecycleOwnershipAction.NONE),
                "pulse", new LifecycleRule("pulse", List.of("payload"), LifecycleOwnershipAction.NONE),
                "reset", new LifecycleRule("reset", List.of("service_ordinal"),
                        LifecycleOwnershipAction.NONE));
        CompleteRunAudioProfiles.register(profile);
        DriverService reset = service(1, List.of(), List.of(new YmWrite(1, 0, 0, 0x33)), state(1), "reset");
        DriverService power = service(2, List.of(), List.of(), state(1), "reset");
        long coordinateBase = (long) FIRST_FRAME << 32;
        FrontierChipEvent beforeAddress = new FrontierChipEvent(coordinateBase + 1, 1, "Z80", 0x100,
                3, 0, 0x2a, false, 0, 0);
        FrontierChipEvent beforeData = new FrontierChipEvent(coordinateBase + 2, 2, "Z80", 0x101,
                3, 1, 0x7f, true, 0, 0x2a);
        FrontierChipEvent afterData = new FrontierChipEvent(coordinateBase + 5, 5, "RESET", 0,
                3, 1, 0x33, true, 0, 0);
        FrontierService ordinary = new FrontierService(1, 0, 0, "driver",
                FrontierServiceState.COMPLETED, FIRST_FRAME, 0, 0x38, 1, "Z80",
                FIRST_FRAME, 3L, 0x40, 2, List.of(), List.of(beforeAddress, beforeData));
        FrontierService resetRoot = new FrontierService(7, 0, 0, "reset",
                FrontierServiceState.COMPLETED, FIRST_FRAME, 4, 0, 0, "RESET",
                FIRST_FRAME, 6L, 0, 0, List.of(), List.of(afterData));
        FrontierService powerRoot = new FrontierService(8, 0, 0, "reset",
                FrontierServiceState.COMPLETED, FIRST_FRAME, 7, 0, 0, "RESET",
                FIRST_FRAME, 8L, 0, 0, List.of(), List.of());
        FrameNativeDiagnostics diagnostics = new FrameNativeDiagnostics(List.of(ordinary, resetRoot, powerRoot),
                List.of(new FrontierOwnedChip(1, beforeAddress), new FrontierOwnedChip(1, beforeData),
                        new FrontierOwnedChip(7, afterData)), List.of(),
                List.of(new NativeResetDiagnostic(7, false), new NativeResetDiagnostic(8, true)));
        Frame referenceFrame = new Frame(FIRST_FRAME, "test", false, List.of(),
                List.of(service(0, List.of(), List.of(new YmWrite(0, 0, 0x2a, 0x7f)), state(1), "driver"),
                        reset, power), List.of(new YmWrite(0, 0, 0x2a, 0x7f),
                                new YmWrite(1, 0, 0, 0x33)), diagnostics);
        Frame engineFrame = new Frame(FIRST_FRAME, "test", false, List.of(),
                referenceFrame.services(), referenceFrame.rawChipEvents(), null);
        Lifecycle lifecycle = new Lifecycle(0, FIRST_FRAME, "reset", Map.of("service_ordinal", 1L), List.of());
        Lifecycle powerLifecycle = new Lifecycle(1, FIRST_FRAME, "power",
                Map.of("service_ordinal", 2L), List.of());

        Path reference = writeSequence("reset-reference", profile, ProducerKind.REFERENCE,
                List.of(referenceFrame, lifecycle, powerLifecycle));
        Path engine = writeSequence("reset-engine", profile, ProducerKind.OPENGGF,
                List.of(engineFrame, lifecycle, powerLifecycle));
        assertEquals(CompleteRunAudioReport.Kind.MATCH,
                CompleteRunAudioComparator.compare(reference, engine).kind());

        Path wrongOrdinal = writeSequence("reset-wrong-ordinal", profile, ProducerKind.REFERENCE,
                List.of(referenceFrame, new Lifecycle(0, FIRST_FRAME, "reset",
                        Map.of("service_ordinal", 0L), List.of()), powerLifecycle));
        assertSemanticFailure(CompleteRunAudioComparator.compare(wrongOrdinal, engine),
                CompleteRunAudioReport.Side.REFERENCE,
                CompleteRunAudioComparator.ValidationException.Kind.STATE_INVALID);
        Path wrongPower = writeSequence("reset-wrong-power", profile, ProducerKind.REFERENCE,
                List.of(new Frame(FIRST_FRAME, "test", false, List.of(), referenceFrame.services(),
                                referenceFrame.rawChipEvents(), new FrameNativeDiagnostics(
                                        diagnostics.services(), diagnostics.rawChipInventory(),
                                        diagnostics.rawSnapshotInventory(),
                                        List.of(new NativeResetDiagnostic(7, true),
                                                new NativeResetDiagnostic(8, true)))),
                        lifecycle, powerLifecycle));
        assertSemanticFailure(CompleteRunAudioComparator.compare(wrongPower, engine),
                CompleteRunAudioReport.Side.REFERENCE,
                CompleteRunAudioComparator.ValidationException.Kind.STATE_INVALID);
        Path intervening = writeSequence("reset-intervening", profile, ProducerKind.REFERENCE,
                List.of(referenceFrame, new Lifecycle(0, FIRST_FRAME, "pulse",
                        Map.of("payload", "x"), List.of()),
                        new Lifecycle(1, FIRST_FRAME, "reset", Map.of("service_ordinal", 1L), List.of()),
                        new Lifecycle(2, FIRST_FRAME, "power", Map.of("service_ordinal", 2L), List.of())));
        assertSemanticFailure(CompleteRunAudioComparator.compare(intervening, engine),
                CompleteRunAudioReport.Side.REFERENCE,
                CompleteRunAudioComparator.ValidationException.Kind.STATE_INVALID);
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
        Path relocated = writeCapture("relocated-reference", profile, ProducerKind.REFERENCE, 2,
                this::plainFrame);
        assertEquals(report.toJson(), CompleteRunAudioComparator.compare(relocated, engine).toJson());
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
                row -> row == 1 ? requestAndDecisionFrame(row, request(0, 0xc0),
                        decision(0, 1, 2, owner(0, 0xc0)), 0) : plainFrame(row));

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

        Baseline namedTempo = baseline(
                new NormalizedState(List.of(new StateField("tempo", 1)), inactiveRoles()));
        Baseline namedSpeed = baseline(
                new NormalizedState(List.of(new StateField("speed", 1)), inactiveRoles()));
        Baseline tempoTwo = baseline(
                new NormalizedState(List.of(new StateField("tempo", 2)), inactiveRoles()));
        assertEquals(CompleteRunAudioReport.Kind.STATE_FIELD_NAME,
                CompleteRunAudioComparator.difference(namedTempo, namedSpeed).kind());
        assertEquals(CompleteRunAudioReport.Kind.STATE_FIELD_VALUE,
                CompleteRunAudioComparator.difference(namedTempo, tempoTwo).kind());

        Baseline emptyOwner = baseline(state(1));
        Baseline liveBaselineOwner = new Baseline(FIRST_FRAME, state(1), List.of(
                new RoleOwner(HardwareRole.FM1, new OwnerRef(OwnerClass.MUSIC, "baseline", 0xc0,
                        OwnerOrigin.BASELINE, 0))));
        assertEquals(CompleteRunAudioReport.Kind.OWNER,
                CompleteRunAudioComparator.difference(emptyOwner, liveBaselineOwner).kind());

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

        Lifecycle save = new Lifecycle(0, FIRST_FRAME, "save", Map.of("slot", 1), List.of());
        Lifecycle restore = new Lifecycle(0, FIRST_FRAME, "restore", Map.of("slot", 1), List.of());
        assertEquals(CompleteRunAudioReport.Kind.LIFECYCLE_VALUE,
                CompleteRunAudioComparator.difference(save, restore).kind());

        Terminal one = new Terminal(FIRST_FRAME + 1, 1, 0, 0, 0, 0, 0, 0, "a".repeat(64));
        Terminal two = new Terminal(FIRST_FRAME + 1, 1, 1, 0, 0, 0, 0, 0, "b".repeat(64));
        assertEquals(CompleteRunAudioReport.Kind.TERMINAL_COUNT,
                CompleteRunAudioComparator.difference(one, two).kind());
    }

    @Test
    void terminalSemanticRootDigestIsComparedAfterAllRecordCounts() {
        Terminal reference = new Terminal(FIRST_FRAME + 1, 1, 0, 0, 0, 0, 0, 0,
                "a".repeat(64));
        Terminal engine = new Terminal(FIRST_FRAME + 1, 1, 0, 0, 0, 0, 0, 0,
                "b".repeat(64));

        CompleteRunAudioComparator.Difference difference =
                CompleteRunAudioComparator.difference(reference, engine);

        assertEquals(CompleteRunAudioReport.Kind.TERMINAL_DIGEST, difference.kind());
        assertEquals("terminal.semantic_digest", difference.location());
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
            if (row == 12) return requestAndDecisionFrame(row, request(0, 0xc0),
                    decision(0, 1, 2, owner(0, 0xc0)), 0);
            if (row == 24) return chipFrame(row, 1, 0x33);
            return plainFrame(row);
        };
        IntFunction<Frame> engineFrames = row -> {
            if (row == 12) return requestAndDecisionFrame(row, request(0, 0xc1),
                    decision(0, 1, 2, owner(0, 0xc1)), 0);
            if (row == 24) return chipFrame(row, 1, 0x99);
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
    void unavailableFilesystemIdentityFailsClosedThroughInjectedProvider() throws Exception {
        TestProfile profile = registerProfile(1);
        Path reference = writeCapture("reference", profile, ProducerKind.REFERENCE, 1, this::plainFrame);
        Path engine = writeCapture("engine", profile, ProducerKind.OPENGGF, 1, this::plainFrame);

        CompleteRunAudioReport report = CompleteRunAudioComparator.compare(reference, engine, () -> { },
                (path, attributes) -> null);

        assertSemanticFailure(report, CompleteRunAudioReport.Side.REFERENCE,
                CompleteRunAudioComparator.ValidationException.Kind.PUBLICATION_IDENTITY_UNAVAILABLE);
    }

    @Test
    void rejectsArbitraryRootSymlinksBeforeResolvingTheirTargets() throws Exception {
        TestProfile profile = registerProfile(1);
        Path reference = writeCapture("canonical-reference", profile, ProducerKind.REFERENCE, 1,
                this::plainFrame);
        Path arbitrary = temp.resolve("arbitrary-link");
        Files.createSymbolicLink(arbitrary, reference.toRealPath());

        CompleteRunAudioComparator.ValidationException failure = org.junit.jupiter.api.Assertions.assertThrows(
                CompleteRunAudioComparator.ValidationException.class,
                () -> CompleteRunAudioComparator.validate(arbitrary, ProducerKind.REFERENCE));
        assertEquals(CompleteRunAudioComparator.ValidationException.Kind.IO_FAILURE, failure.kind());
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
        assertEquals(CompleteRunAudioComparator.ValidationException.Kind.RUNTIME_IDENTITY_INVALID,
                report.validationKind());
        assertEquals("reference", report.failureSource());
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
        DriverService reference = service(0, List.of(ownershipDecision(1, true, "accepted",
                owner(0, 0xc0), owner(1, 0xc0))), List.of(), state(1));
        DriverService engine = service(0, List.of(ownershipDecision(1, true, "accepted",
                owner(0, 0xc0), owner(0, 0xc0))), List.of(), state(1));

        CompleteRunAudioComparator.Difference difference = CompleteRunAudioComparator.difference(
                frame(reference), frame(engine));

        assertEquals(CompleteRunAudioReport.Kind.OWNER, difference.kind());
        assertTrue(difference.referenceValue().contains("originOrdinal=1"));
        assertTrue(difference.engineValue().contains("originOrdinal=0"));
    }

    @Test
    void rejectsRepeatedDisplacedNoneAfterTheRoleHasAnOwner() throws Exception {
        TestProfile profile = registerProfile(2);
        Path reference = writeCapture("reference", profile, ProducerKind.REFERENCE, 2, this::plainFrame);
        Path engine = writeCapture("engine", profile, ProducerKind.OPENGGF, 2, row -> {
            Decision next = ownershipDecision(row, true, "accepted", NONE, owner(row, 0xc0));
            return requestAndDecisionFrame(row, request(row, 0xc0), next, row);
        });

        CompleteRunAudioReport report = CompleteRunAudioComparator.compare(reference, engine);

        assertSemanticFailure(report, CompleteRunAudioReport.Side.ENGINE,
                CompleteRunAudioComparator.ValidationException.Kind.OWNER_INVALID);
    }

    @Test
    void rejectsDisplacingAnExistingButNoLongerLiveOwner() throws Exception {
        TestProfile profile = registerProfile(3);
        Path reference = writeCapture("reference", profile, ProducerKind.REFERENCE, 3, this::plainFrame);
        Path engine = writeCapture("engine", profile, ProducerKind.OPENGGF, 3, row -> {
            OwnerRef displaced = row == 0 ? NONE : row == 1 ? owner(0, 0xc0) : owner(0, 0xc0);
            Decision next = ownershipDecision(row, true, "accepted", displaced, owner(row, 0xc0));
            return requestAndDecisionFrame(row, request(row, 0xc0), next, row);
        });

        CompleteRunAudioReport report = CompleteRunAudioComparator.compare(reference, engine);

        assertSemanticFailure(report, CompleteRunAudioReport.Side.ENGINE,
                CompleteRunAudioComparator.ValidationException.Kind.OWNER_INVALID);
    }

    @Test
    void rejectsAcceptedDecisionWhoseFinalOwnerIsNotTheCurrentAdmission() throws Exception {
        TestProfile profile = registerProfile(1);
        Path reference = writeCapture("reference", profile, ProducerKind.REFERENCE, 1, this::plainFrame);
        Decision invalid = ownershipDecision(0, true, "accepted", NONE, NONE);
        Path engine = writeCapture("engine", profile, ProducerKind.OPENGGF, 1,
                row -> requestAndDecisionFrame(row, request(0, 0xc0), invalid, 0));

        CompleteRunAudioReport report = CompleteRunAudioComparator.compare(reference, engine);

        assertSemanticFailure(report, CompleteRunAudioReport.Side.ENGINE,
                CompleteRunAudioComparator.ValidationException.Kind.OWNER_INVALID);
    }

    @Test
    void acceptsSameIdRetriggerOnlyWhenAIsDisplacedByOrdinalDistinctB() throws Exception {
        TestProfile profile = registerProfile(2);
        IntFunction<Frame> frames = row -> {
            OwnerRef displaced = row == 0 ? NONE : owner(0, 0xc0);
            Decision next = ownershipDecision(row, true, "accepted", displaced, owner(row, 0xc0));
            return requestAndDecisionFrame(row, request(row, 0xc0), next, row);
        };
        Path reference = writeCapture("reference", profile, ProducerKind.REFERENCE, 2, frames);
        Path engine = writeCapture("engine", profile, ProducerKind.OPENGGF, 2, frames);

        assertEquals(CompleteRunAudioReport.Kind.MATCH,
                CompleteRunAudioComparator.compare(reference, engine).kind());
    }

    @Test
    void rejectedDecisionPreservesTheCurrentLiveOwner() throws Exception {
        TestProfile profile = registerProfile(2);
        IntFunction<Frame> frames = row -> {
            Decision next = row == 0
                    ? ownershipDecision(0, true, "accepted", NONE, owner(0, 0xc0))
                    : ownershipDecision(1, false, "rejected", owner(0, 0xc0), owner(0, 0xc0));
            return requestAndDecisionFrame(row, request(row, 0xc0), next, row);
        };
        Path reference = writeCapture("reference", profile, ProducerKind.REFERENCE, 2, frames);
        Path engine = writeCapture("engine", profile, ProducerKind.OPENGGF, 2, frames);

        assertEquals(CompleteRunAudioReport.Kind.MATCH,
                CompleteRunAudioComparator.compare(reference, engine).kind());
    }

    @Test
    void baselineOwnerCanBeDisplacedWithoutCollidingWithRequestOrdinalZero() throws Exception {
        OwnerRef baselineOwner = new OwnerRef(OwnerClass.MUSIC, "music.81", 0x81,
                OwnerOrigin.BASELINE, 0);
        TestProfile profile = profile("comparator.test." + PROFILE_SEQUENCE.incrementAndGet(), 1);
        profile.baselineRoleOwners = List.of(new RoleOwner(HardwareRole.FM1, baselineOwner));
        CompleteRunAudioProfiles.register(profile);
        IntFunction<Frame> frames = row -> requestAndDecisionFrame(row, request(0, 0xc0),
                ownershipDecision(0, true, "accepted", baselineOwner, owner(0, 0xc0)), 0);
        Path reference = writeCapture("reference", profile, ProducerKind.REFERENCE, 1, frames);
        Path engine = writeCapture("engine", profile, ProducerKind.OPENGGF, 1, frames);

        assertEquals(CompleteRunAudioReport.Kind.MATCH,
                CompleteRunAudioComparator.compare(reference, engine).kind());
    }

    @Test
    void oneUpAdmissionRestoresMusicThroughRequestIndependentLifecycleBeforeLaterSfx() throws Exception {
        OwnerRef music = baselineMusic();
        TestProfile profile = profile("comparator.lifecycle." + PROFILE_SEQUENCE.incrementAndGet(), 2);
        profile.baselineRoleOwners = List.of(new RoleOwner(HardwareRole.FM1, music));
        profile.ownershipTransitions = Map.of(
                "accepted", OwnershipTransition.ACQUIRE_REQUEST,
                "rejected", OwnershipTransition.REJECT_PRESERVE,
                "one-up", OwnershipTransition.SAVE_AND_ACQUIRE_REQUEST);
        profile.restoreStackPolicy = new RestoreStackPolicy(1, List.of(), null);
        profile.lifecycleRules = Map.of("restore",
                new LifecycleRule("restore", List.of(), LifecycleOwnershipAction.RESTORE_SAVED,
                        List.of(List.of(HardwareRole.FM1))));
        CompleteRunAudioProfiles.register(profile);
        List<CompleteRunAudioTrace.Record> records = List.of(
                new Baseline(FIRST_FRAME, activeState(1), profile.baselineRoleOwners),
                requestAndDecisionFrame(0, request(0, 0xc0),
                        ownershipDecision(0, true, "one-up", music, owner(0, 0xc0)), 0),
                lifecycle(0, 0, "restore", owner(0, 0xc0), music),
                requestAndDecisionFrame(1, request(1, 0xc1),
                        ownershipDecisionFor(1, 0xc1, true, "accepted", music, owner(1, 0xc1)), 1));
        Path reference = writeRecords("reference", metadata(profile, ProducerKind.REFERENCE,
                profile.producerRuntimeIdentities().get(ProducerKind.REFERENCE)), records);
        Path engine = writeRecords("engine", metadata(profile, ProducerKind.OPENGGF,
                profile.producerRuntimeIdentities().get(ProducerKind.OPENGGF)), records);

        assertEquals(CompleteRunAudioReport.Kind.MATCH,
                CompleteRunAudioComparator.compare(reference, engine).kind());
    }

    @Test
    void lifecycleSaveOnlyReleaseAndRestoreUseTheBoundedRoleStack() throws Exception {
        OwnerRef music = baselineMusic();
        TestProfile profile = lifecycleProfile(2, music, 1, Map.of(
                "save", new LifecycleRule("save", List.of(), LifecycleOwnershipAction.SAVE_CURRENT,
                        List.of(List.of(HardwareRole.FM1))),
                "release", new LifecycleRule("release", List.of(), LifecycleOwnershipAction.RELEASE_TO_NONE,
                        List.of(List.of(HardwareRole.FM1))),
                "restore", new LifecycleRule("restore", List.of(), LifecycleOwnershipAction.RESTORE_SAVED,
                        List.of(List.of(HardwareRole.FM1)))));
        List<CompleteRunAudioTrace.Record> records = List.of(
                new Baseline(FIRST_FRAME, activeState(1), profile.baselineRoleOwners),
                lifecycle(0, 0, "save", music, music),
                lifecycle(1, 0, "release", music, NONE),
                plainFrame(0),
                lifecycle(2, 1, "restore", NONE, music),
                new Frame(FIRST_FRAME + 1, "test", false, List.of(),
                        List.of(service(0, List.of(), List.of(), activeState(1)))));

        assertLifecycleMatch(profile, records, "save-release-restore");
    }

    @Test
    void lifecycleOwnershipTransitionsAreComparedForIndividuallyValidCaptures() throws Exception {
        OwnerRef music = baselineMusic();
        TestProfile profile = profile("comparator.lifecycle." + PROFILE_SEQUENCE.incrementAndGet(), 1);
        profile.hardwareRoles = List.of(HardwareRole.FM1, HardwareRole.FM2);
        profile.baselineRoleOwners = List.of(
                new RoleOwner(HardwareRole.FM1, music),
                new RoleOwner(HardwareRole.FM2, music));
        profile.restoreStackPolicy = new RestoreStackPolicy(1, List.of(), null);
        profile.lifecycleRules = Map.of(
                "save",
                new LifecycleRule("save", List.of(), LifecycleOwnershipAction.SAVE_CURRENT,
                        List.of(List.of(HardwareRole.FM1), List.of(HardwareRole.FM2))),
                "restore",
                new LifecycleRule("restore", List.of(), LifecycleOwnershipAction.RESTORE_SAVED,
                        List.of(List.of(HardwareRole.FM1), List.of(HardwareRole.FM2))));
        CompleteRunAudioProfiles.register(profile);
        NormalizedState active = new NormalizedState(List.of(new StateField("tempo", 1)), List.of(
                new RoleState(HardwareRole.FM1, true, List.of(new StateField("cursor", 0))),
                new RoleState(HardwareRole.FM2, true, List.of(new StateField("cursor", 0)))));
        List<CompleteRunAudioTrace.Record> referenceRecords = List.of(
                new Baseline(FIRST_FRAME, active, profile.baselineRoleOwners),
                new Lifecycle(0, FIRST_FRAME, "save", Map.of(), List.of(
                        new LifecycleOwnership(HardwareRole.FM1, music, music))),
                new Lifecycle(1, FIRST_FRAME, "restore", Map.of(), List.of(
                        new LifecycleOwnership(HardwareRole.FM1, music, music))),
                plainFrame(0));
        List<CompleteRunAudioTrace.Record> engineRecords = List.of(
                new Baseline(FIRST_FRAME, active, profile.baselineRoleOwners),
                new Lifecycle(0, FIRST_FRAME, "save", Map.of(), List.of(
                        new LifecycleOwnership(HardwareRole.FM2, music, music))),
                new Lifecycle(1, FIRST_FRAME, "restore", Map.of(), List.of(
                        new LifecycleOwnership(HardwareRole.FM2, music, music))),
                plainFrame(0));
        Path reference = writeRecords("lifecycle-role-reference", metadata(profile,
                ProducerKind.REFERENCE, profile.producerRuntimeIdentities().get(ProducerKind.REFERENCE)),
                referenceRecords);
        Path engine = writeRecords("lifecycle-role-engine", metadata(profile,
                ProducerKind.OPENGGF, profile.producerRuntimeIdentities().get(ProducerKind.OPENGGF)),
                engineRecords);

        CompleteRunAudioReport report = CompleteRunAudioComparator.compare(reference, engine);

        assertEquals(CompleteRunAudioReport.Kind.LIFECYCLE_VALUE, report.kind());
        assertEquals("lifecycle[0].ownership_transitions[0].role", report.location());
    }

    @Test
    void lifecycleRulesRequireOneExactProfileOwnedRoleSet() throws Exception {
        OwnerRef music = baselineMusic();
        TestProfile profile = profile("comparator.lifecycle." + PROFILE_SEQUENCE.incrementAndGet(), 1);
        profile.hardwareRoles = List.of(HardwareRole.FM1, HardwareRole.FM2);
        profile.baselineRoleOwners = List.of(
                new RoleOwner(HardwareRole.FM1, music),
                new RoleOwner(HardwareRole.FM2, music));
        profile.restoreStackPolicy = new RestoreStackPolicy(1, List.of(), null);
        profile.lifecycleRules = Map.of(
                "save",
                new LifecycleRule("save", List.of(), LifecycleOwnershipAction.SAVE_CURRENT,
                        List.of(List.of(HardwareRole.FM1, HardwareRole.FM2))),
                "restore",
                new LifecycleRule("restore", List.of(), LifecycleOwnershipAction.RESTORE_SAVED,
                        List.of(List.of(HardwareRole.FM1, HardwareRole.FM2))));
        CompleteRunAudioProfiles.register(profile);
        NormalizedState active = new NormalizedState(List.of(new StateField("tempo", 1)), List.of(
                new RoleState(HardwareRole.FM1, true, List.of(new StateField("cursor", 0))),
                new RoleState(HardwareRole.FM2, true, List.of(new StateField("cursor", 0)))));
        List<CompleteRunAudioTrace.Record> allRoles = List.of(
                new Baseline(FIRST_FRAME, active, profile.baselineRoleOwners),
                new Lifecycle(0, FIRST_FRAME, "save", Map.of(), List.of(
                        new LifecycleOwnership(HardwareRole.FM1, music, music),
                        new LifecycleOwnership(HardwareRole.FM2, music, music))),
                new Lifecycle(1, FIRST_FRAME, "restore", Map.of(), List.of(
                        new LifecycleOwnership(HardwareRole.FM1, music, music),
                        new LifecycleOwnership(HardwareRole.FM2, music, music))),
                plainFrame(0));
        assertLifecycleMatch(profile, allRoles, "exact-all-lifecycle-roles");

        List<CompleteRunAudioTrace.Record> omittedRole = List.of(
                new Baseline(FIRST_FRAME, active, profile.baselineRoleOwners),
                new Lifecycle(0, FIRST_FRAME, "save", Map.of(), List.of(
                        new LifecycleOwnership(HardwareRole.FM1, music, music))),
                plainFrame(0));
        assertInvalidLifecycleCapture(profile, omittedRole, "omitted-lifecycle-role",
                CompleteRunAudioComparator.ValidationException.Kind.LIFECYCLE_INVALID);

        TestProfile extraProfile = profile("comparator.lifecycle." + PROFILE_SEQUENCE.incrementAndGet(), 1);
        extraProfile.hardwareRoles = List.of(HardwareRole.FM1, HardwareRole.FM2);
        extraProfile.baselineRoleOwners = profile.baselineRoleOwners;
        extraProfile.restoreStackPolicy = new RestoreStackPolicy(1, List.of(), null);
        extraProfile.lifecycleRules = Map.of("save",
                new LifecycleRule("save", List.of(), LifecycleOwnershipAction.SAVE_CURRENT,
                        List.of(List.of(HardwareRole.FM1))));
        CompleteRunAudioProfiles.register(extraProfile);
        List<CompleteRunAudioTrace.Record> extraRole = List.of(
                new Baseline(FIRST_FRAME, active, extraProfile.baselineRoleOwners),
                new Lifecycle(0, FIRST_FRAME, "save", Map.of(), List.of(
                        new LifecycleOwnership(HardwareRole.FM1, music, music),
                        new LifecycleOwnership(HardwareRole.FM2, music, music))),
                plainFrame(0));
        assertInvalidLifecycleCapture(extraProfile, extraRole, "extra-lifecycle-role",
                CompleteRunAudioComparator.ValidationException.Kind.LIFECYCLE_INVALID);

        assertThrows(IllegalArgumentException.class, () -> new Lifecycle(0, FIRST_FRAME, "save", Map.of(),
                List.of(new LifecycleOwnership(HardwareRole.FM2, music, music),
                        new LifecycleOwnership(HardwareRole.FM1, music, music))));
    }

    @Test
    void lifecycleOwnershipMismatchLocationsDistinguishActionDisplacedAndFinalOwner() {
        OwnerRef music = baselineMusic();
        Lifecycle save = lifecycle(0, 0, "save", music, music);
        Lifecycle restore = lifecycle(0, 0, "restore", music, music);
        Lifecycle displaced = lifecycle(0, 0, "save", NONE, music);
        Lifecycle finalOwner = lifecycle(0, 0, "save", music, NONE);

        CompleteRunAudioComparator.Difference actionDifference =
                CompleteRunAudioComparator.difference(save, restore);
        assertEquals(CompleteRunAudioReport.Kind.LIFECYCLE_VALUE, actionDifference.kind());
        assertEquals("lifecycle[0].kind", actionDifference.location());

        CompleteRunAudioComparator.Difference displacedDifference =
                CompleteRunAudioComparator.difference(save, displaced);
        assertEquals(CompleteRunAudioReport.Kind.OWNER, displacedDifference.kind());
        assertEquals("lifecycle[0].ownership_transitions[0].displaced_owner",
                displacedDifference.location());

        CompleteRunAudioComparator.Difference finalDifference =
                CompleteRunAudioComparator.difference(save, finalOwner);
        assertEquals(CompleteRunAudioReport.Kind.OWNER, finalDifference.kind());
        assertEquals("lifecycle[0].ownership_transitions[0].final_owner", finalDifference.location());
    }

    @Test
    void lifecycleRestoreRejectsAnEmptyStack() throws Exception {
        OwnerRef music = baselineMusic();
        TestProfile profile = profile("comparator.lifecycle." + PROFILE_SEQUENCE.incrementAndGet(), 1);
        profile.baselineRoleOwners = List.of(new RoleOwner(HardwareRole.FM1, music));
        profile.ownershipTransitions = Map.of(
                "accepted", OwnershipTransition.ACQUIRE_REQUEST,
                "rejected", OwnershipTransition.REJECT_PRESERVE,
                "save-and-acquire", OwnershipTransition.SAVE_AND_ACQUIRE_REQUEST);
        profile.restoreStackPolicy = new RestoreStackPolicy(1, List.of(), null);
        profile.lifecycleRules = Map.of("restore",
                new LifecycleRule("restore", List.of(), LifecycleOwnershipAction.RESTORE_SAVED,
                        List.of(List.of(HardwareRole.FM1))));
        CompleteRunAudioProfiles.register(profile);
        List<CompleteRunAudioTrace.Record> invalid = List.of(
                new Baseline(FIRST_FRAME, activeState(1), profile.baselineRoleOwners),
                lifecycle(0, 0, "restore", music, music), plainFrame(0));

        assertInvalidLifecycleCapture(profile, invalid, "empty-restore",
                CompleteRunAudioComparator.ValidationException.Kind.OWNER_INVALID);
    }

    @Test
    void terminalRejectsSavedOwnersWhenTheProfileRequiresAnEmptyRestoreStack() throws Exception {
        OwnerRef music = baselineMusic();
        TestProfile profile = profile("comparator.lifecycle." + PROFILE_SEQUENCE.incrementAndGet(), 1);
        profile.baselineRoleOwners = List.of(new RoleOwner(HardwareRole.FM1, music));
        profile.restoreStackPolicy = new RestoreStackPolicy(1, List.of(), null);
        profile.lifecycleRules = Map.of("save",
                new LifecycleRule("save", List.of(), LifecycleOwnershipAction.SAVE_CURRENT,
                        List.of(List.of(HardwareRole.FM1))));
        CompleteRunAudioProfiles.register(profile);
        List<CompleteRunAudioTrace.Record> omittedRestore = List.of(
                new Baseline(FIRST_FRAME, activeState(1), profile.baselineRoleOwners),
                lifecycle(0, 0, "save", music, music), plainFrame(0));

        assertInvalidLifecycleCapture(profile, omittedRestore, "omitted-lifecycle-restore",
                CompleteRunAudioComparator.ValidationException.Kind.RESTORE_STACK_INVALID);
    }

    @Test
    void profileCanDeliberatelyPermitOneExactTerminalRestoreStack() throws Exception {
        OwnerRef music = baselineMusic();
        TestProfile profile = profile("comparator.lifecycle." + PROFILE_SEQUENCE.incrementAndGet(), 1);
        profile.baselineRoleOwners = List.of(new RoleOwner(HardwareRole.FM1, music));
        profile.restoreStackPolicy = new RestoreStackPolicy(1,
                List.of(new SavedOwnerDepth(HardwareRole.FM1, 1)),
                "driver deliberately carries one saved FM owner beyond this comparison epoch");
        profile.lifecycleRules = Map.of("save",
                new LifecycleRule("save", List.of(), LifecycleOwnershipAction.SAVE_CURRENT,
                        List.of(List.of(HardwareRole.FM1))));
        CompleteRunAudioProfiles.register(profile);
        List<CompleteRunAudioTrace.Record> records = List.of(
                new Baseline(FIRST_FRAME, activeState(1), profile.baselineRoleOwners),
                lifecycle(0, 0, "save", music, music), plainFrame(0));

        assertLifecycleMatch(profile, records, "allowed-terminal-restore-stack");
    }

    @Test
    void lifecycleRuleRejectsMissingPayloadAndRolesOutsideTheProfileInventory() throws Exception {
        OwnerRef music = baselineMusic();
        TestProfile profile = lifecycleProfile(1, music, 1, Map.of("save",
                new LifecycleRule("save", List.of(), LifecycleOwnershipAction.SAVE_CURRENT,
                        List.of(List.of(HardwareRole.FM1)))));
        List<CompleteRunAudioTrace.Record> missingPayload = List.of(
                new Baseline(FIRST_FRAME, activeState(1), profile.baselineRoleOwners),
                new Lifecycle(0, FIRST_FRAME, "save", Map.of(), List.of()), plainFrame(0));
        List<CompleteRunAudioTrace.Record> outsideInventory = List.of(
                new Baseline(FIRST_FRAME, activeState(1), profile.baselineRoleOwners),
                new Lifecycle(0, FIRST_FRAME, "save", Map.of(), List.of(
                        new LifecycleOwnership(HardwareRole.FM2, NONE, NONE))), plainFrame(0));

        assertInvalidLifecycleCapture(profile, missingPayload, "missing-lifecycle-payload",
                CompleteRunAudioComparator.ValidationException.Kind.LIFECYCLE_INVALID);
        assertInvalidLifecycleCapture(profile, outsideInventory, "outside-lifecycle-role",
                CompleteRunAudioComparator.ValidationException.Kind.LIFECYCLE_INVALID);
    }

    @Test
    void lifecycleOwnershipRejectsWrongDisplacedAndFinalOwners() throws Exception {
        OwnerRef music = baselineMusic();
        TestProfile profile = lifecycleProfile(1, music, 1, Map.of("save",
                new LifecycleRule("save", List.of(), LifecycleOwnershipAction.SAVE_CURRENT,
                        List.of(List.of(HardwareRole.FM1)))));
        List<CompleteRunAudioTrace.Record> wrongDisplaced = List.of(
                new Baseline(FIRST_FRAME, activeState(1), profile.baselineRoleOwners),
                lifecycle(0, 0, "save", NONE, music), plainFrame(0));
        List<CompleteRunAudioTrace.Record> wrongFinal = List.of(
                new Baseline(FIRST_FRAME, activeState(1), profile.baselineRoleOwners),
                lifecycle(0, 0, "save", music, NONE), plainFrame(0));

        assertInvalidLifecycleCapture(profile, wrongDisplaced, "wrong-lifecycle-displaced",
                CompleteRunAudioComparator.ValidationException.Kind.OWNER_INVALID);
        assertInvalidLifecycleCapture(profile, wrongFinal, "wrong-lifecycle-final",
                CompleteRunAudioComparator.ValidationException.Kind.OWNER_INVALID);
    }

    @Test
    void lifecycleSaveRejectsDepthBeyondTheProfileBound() throws Exception {
        OwnerRef music = baselineMusic();
        TestProfile profile = lifecycleProfile(1, music, 1, Map.of("save",
                new LifecycleRule("save", List.of(), LifecycleOwnershipAction.SAVE_CURRENT,
                        List.of(List.of(HardwareRole.FM1)))));
        List<CompleteRunAudioTrace.Record> invalid = List.of(
                new Baseline(FIRST_FRAME, activeState(1), profile.baselineRoleOwners),
                lifecycle(0, 0, "save", music, music),
                lifecycle(1, 0, "save", music, music), plainFrame(0));

        assertInvalidLifecycleCapture(profile, invalid, "excess-lifecycle-depth",
                CompleteRunAudioComparator.ValidationException.Kind.OWNER_INVALID);
    }

    @Test
    void lifecycleReleaseResetRequiresTheNextServiceStateToBeInactive() throws Exception {
        OwnerRef music = baselineMusic();
        TestProfile profile = lifecycleProfile(1, music, 0, Map.of("reset",
                new LifecycleRule("reset", List.of(), LifecycleOwnershipAction.RELEASE_TO_NONE,
                        List.of(List.of(HardwareRole.FM1)))));
        List<CompleteRunAudioTrace.Record> records = List.of(
                new Baseline(FIRST_FRAME, activeState(1), profile.baselineRoleOwners),
                lifecycle(0, 0, "reset", music, NONE),
                new Frame(FIRST_FRAME, "test", false, List.of(),
                        List.of(service(0, List.of(), List.of(), state(1)))));

        assertLifecycleMatch(profile, records, "release-reset");
    }

    @Test
    void noTransitionLifecycleRequiresAnEmptyOwnershipPayloadAndReportsItCanonically() throws Exception {
        OwnerRef music = baselineMusic();
        TestProfile profile = lifecycleProfile(1, music, 1, Map.of(
                "save", new LifecycleRule("save", List.of(), LifecycleOwnershipAction.SAVE_CURRENT,
                        List.of(List.of(HardwareRole.FM1))),
                "restore", new LifecycleRule("restore", List.of(), LifecycleOwnershipAction.RESTORE_SAVED,
                        List.of(List.of(HardwareRole.FM1))),
                "noop", new LifecycleRule("noop", List.of(), LifecycleOwnershipAction.NONE)));
        List<CompleteRunAudioTrace.Record> referenceRecords = List.of(
                new Baseline(FIRST_FRAME, activeState(1), profile.baselineRoleOwners),
                lifecycle(0, 0, "save", music, music),
                lifecycle(1, 0, "restore", music, music), plainFrame(0));
        List<CompleteRunAudioTrace.Record> engineRecords = List.of(
                new Baseline(FIRST_FRAME, activeState(1), profile.baselineRoleOwners),
                new Lifecycle(0, FIRST_FRAME, "noop", Map.of(), List.of()),
                new Lifecycle(1, FIRST_FRAME, "noop", Map.of(), List.of()), plainFrame(0));
        Path reference = writeRecords("noop-reference", metadata(profile, ProducerKind.REFERENCE,
                profile.producerRuntimeIdentities().get(ProducerKind.REFERENCE)), referenceRecords);
        Path engine = writeRecords("noop-engine", metadata(profile, ProducerKind.OPENGGF,
                profile.producerRuntimeIdentities().get(ProducerKind.OPENGGF)), engineRecords);

        CompleteRunAudioReport report = CompleteRunAudioComparator.compare(reference, engine);

        assertEquals(CompleteRunAudioReport.Kind.LIFECYCLE_VALUE, report.kind());
        assertTrue(report.toJson().contains("\\\"ownershipTransitions\\\":[{\\\"role\\\":\\\"FM1\\\""));
        assertTrue(report.toText().contains("ownershipTransitions"));

        List<CompleteRunAudioTrace.Record> invalid = List.of(
                new Baseline(FIRST_FRAME, activeState(1), profile.baselineRoleOwners),
                lifecycle(0, 0, "noop", music, music), plainFrame(0));
        assertInvalidLifecycleCapture(profile, invalid, "noop-with-transition",
                CompleteRunAudioComparator.ValidationException.Kind.LIFECYCLE_INVALID);
    }

    @Test
    void normalizedStateRejectsInactiveOwnedAndActiveNoneAtBaseline() throws Exception {
        OwnerRef music = baselineMusic();
        TestProfile ownedProfile = lifecycleProfile(1, music, 0, Map.of("noop",
                new LifecycleRule("noop", List.of(), LifecycleOwnershipAction.NONE)));
        List<CompleteRunAudioTrace.Record> inactiveOwned = List.of(
                new Baseline(FIRST_FRAME, state(1), ownedProfile.baselineRoleOwners), plainFrame(0));
        assertInvalidLifecycleCapture(ownedProfile, inactiveOwned, "inactive-owned-baseline",
                CompleteRunAudioComparator.ValidationException.Kind.STATE_INVALID);

        TestProfile noneProfile = registerProfile(1);
        Metadata referenceMetadata = metadata(noneProfile, ProducerKind.REFERENCE,
                noneProfile.producerRuntimeIdentities().get(ProducerKind.REFERENCE));
        Metadata engineMetadata = metadata(noneProfile, ProducerKind.OPENGGF,
                noneProfile.producerRuntimeIdentities().get(ProducerKind.OPENGGF));
        Path reference = writeCapture("active-none-reference", referenceMetadata, 1, this::plainFrame);
        Path engine = writeRecords("active-none-engine", engineMetadata, List.of(
                new Baseline(FIRST_FRAME, activeState(1), noneProfile.baselineRoleOwners), plainFrame(0)));
        assertSemanticFailure(CompleteRunAudioComparator.compare(reference, engine),
                CompleteRunAudioReport.Side.ENGINE,
                CompleteRunAudioComparator.ValidationException.Kind.STATE_INVALID);
    }

    @Test
    void normalizedStateRejectsInactiveOwnedAndActiveNoneAfterService() throws Exception {
        TestProfile acquireProfile = registerProfile(1);
        IntFunction<Frame> validAcquire = row -> requestAndDecisionFrame(row, request(0, 0xc0),
                ownershipDecision(0, true, "accepted", NONE, owner(0, 0xc0)), 0);
        Path acquireReference = writeCapture("inactive-owned-reference", acquireProfile,
                ProducerKind.REFERENCE, 1, validAcquire);
        Path inactiveOwned = writeCapture("inactive-owned-engine", acquireProfile,
                ProducerKind.OPENGGF, 1, row -> requestAndDecisionFrame(row, request(0, 0xc0),
                        ownershipDecision(0, true, "accepted", NONE, owner(0, 0xc0)), 0, state(1)));
        assertSemanticFailure(CompleteRunAudioComparator.compare(acquireReference, inactiveOwned),
                CompleteRunAudioReport.Side.ENGINE,
                CompleteRunAudioComparator.ValidationException.Kind.STATE_INVALID);

        TestProfile releaseProfile = profile("comparator.release." + PROFILE_SEQUENCE.incrementAndGet(), 1);
        releaseProfile.ownershipTransitions = Map.of(
                "accepted", OwnershipTransition.ACQUIRE_REQUEST,
                "rejected", OwnershipTransition.REJECT_PRESERVE,
                "release", OwnershipTransition.RELEASE_TO_NONE);
        CompleteRunAudioProfiles.register(releaseProfile);
        Decision release = ownershipDecision(0, true, "release", NONE, NONE);
        Path releaseReference = writeCapture("active-none-service-reference", releaseProfile,
                ProducerKind.REFERENCE, 1, row -> requestAndDecisionFrame(row, request(0, 0xc0),
                        release, 0, state(1)));
        Path activeNone = writeCapture("active-none-service-engine", releaseProfile,
                ProducerKind.OPENGGF, 1, row -> requestAndDecisionFrame(row, request(0, 0xc0),
                        release, 0, activeState(1)));
        assertSemanticFailure(CompleteRunAudioComparator.compare(releaseReference, activeNone),
                CompleteRunAudioReport.Side.ENGINE,
                CompleteRunAudioComparator.ValidationException.Kind.STATE_INVALID);
    }

    @Test
    void terminalRejectsAnUnobservedLifecycleOwnershipStateChange() throws Exception {
        OwnerRef music = baselineMusic();
        TestProfile profile = lifecycleProfile(1, music, 0, Map.of("reset",
                new LifecycleRule("reset", List.of(), LifecycleOwnershipAction.RELEASE_TO_NONE,
                        List.of(List.of(HardwareRole.FM1)))));
        Metadata referenceMetadata = metadata(profile, ProducerKind.REFERENCE,
                profile.producerRuntimeIdentities().get(ProducerKind.REFERENCE));
        Metadata engineMetadata = metadata(profile, ProducerKind.OPENGGF,
                profile.producerRuntimeIdentities().get(ProducerKind.OPENGGF));
        Path reference = writeCapture("terminal-lifecycle-reference", referenceMetadata, 1,
                this::plainFrame);
        Path engine = writeRecords("terminal-lifecycle-engine", engineMetadata, List.of(
                new Baseline(FIRST_FRAME, activeState(1), profile.baselineRoleOwners), plainFrame(0),
                lifecycle(0, 0, "reset", music, NONE)));

        assertSemanticFailure(CompleteRunAudioComparator.compare(reference, engine),
                CompleteRunAudioReport.Side.ENGINE,
                CompleteRunAudioComparator.ValidationException.Kind.STATE_INVALID);
    }

    @Test
    void rejectsPendingRequestsBeyondTheProfileBoundAndAtTerminal() throws Exception {
        TestProfile capacityProfile = registerProfile(1);
        List<Request> requests = new ArrayList<>();
        for (int ordinal = 0; ordinal < 5; ordinal++) requests.add(request(ordinal, 0xc0));
        Path capacityReference = writeCapture("capacity-reference", capacityProfile,
                ProducerKind.REFERENCE, 1, this::plainFrame);
        Path overCapacity = writeCapture("over-capacity", capacityProfile, ProducerKind.OPENGGF, 1,
                row -> new Frame(FIRST_FRAME, "test", false, requests, List.of()));

        assertSemanticFailure(CompleteRunAudioComparator.compare(capacityReference, overCapacity),
                CompleteRunAudioReport.Side.ENGINE,
                CompleteRunAudioComparator.ValidationException.Kind.PENDING_CAPACITY_INVALID);

        TestProfile terminalProfile = registerProfile(1);
        Path terminalReference = writeCapture("terminal-reference", terminalProfile,
                ProducerKind.REFERENCE, 1, this::plainFrame);
        Path unresolved = writeCapture("unresolved", terminalProfile, ProducerKind.OPENGGF, 1,
                row -> requestFrame(row, request(0, 0xc0)));

        assertSemanticFailure(CompleteRunAudioComparator.compare(terminalReference, unresolved),
                CompleteRunAudioReport.Side.ENGINE,
                CompleteRunAudioComparator.ValidationException.Kind.PENDING_UNRESOLVED);
    }

    @Test
    void explicitBoundedTerminalPendingAllowanceIsHonored() throws Exception {
        TestProfile profile = profile("comparator.test." + PROFILE_SEQUENCE.incrementAndGet(), 1);
        profile.pendingPolicy = new PendingRequestPolicy(4, 1,
                "fixture ends after mailbox submission but before the next service");
        CompleteRunAudioProfiles.register(profile);
        IntFunction<Frame> frames = row -> requestFrame(row, request(0, 0xc0));
        Path reference = writeCapture("reference", profile, ProducerKind.REFERENCE, 1, frames);
        Path engine = writeCapture("engine", profile, ProducerKind.OPENGGF, 1, frames);

        assertEquals(CompleteRunAudioReport.Kind.MATCH,
                CompleteRunAudioComparator.compare(reference, engine).kind());
    }

    @Test
    void semanticRetentionRemainsConstantAcrossHalfAMillionCompletedRequests() throws Exception {
        int frames = 500_000;
        TestProfile profile = profile("comparator.stress." + PROFILE_SEQUENCE.incrementAndGet(), frames);
        Metadata metadata = metadata(profile, ProducerKind.OPENGGF,
                profile.producerRuntimeIdentities().get(ProducerKind.OPENGGF));

        CompleteRunAudioComparator.ValidationDiagnostics diagnostics =
                CompleteRunAudioComparator.validateSemanticsForDiagnostics(metadata, profile,
                        CompleteRunAudioReport.Side.ENGINE, stressRecords(frames));

        assertEquals(1, diagnostics.peakPendingRequests());
        assertEquals(0, diagnostics.terminalPendingRequests());
        assertEquals(0, diagnostics.peakSavedOwners());
        assertEquals(1, diagnostics.liveRoleOwners());
        assertEquals(500_000, diagnostics.completedRequests());
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
        outside.add(baseline(state(1)));
        outside.add(plainFrame(0));
        outside.add(plainFrame(1));
        outside.add(new Lifecycle(0, FIRST_FRAME + 2, "pulse", Map.of("payload", "outside"), List.of()));
        Path outsideCapture = writeRecords("outside", engineMetadata, outside);

        CompleteRunAudioReport outsideReport = CompleteRunAudioComparator.compare(reference, outsideCapture);
        assertSemanticFailure(outsideReport, CompleteRunAudioReport.Side.ENGINE,
                CompleteRunAudioComparator.ValidationException.Kind.LIFECYCLE_INVALID);

        List<CompleteRunAudioTrace.Record> regressing = new ArrayList<>();
        regressing.add(baseline(state(1)));
        regressing.add(new Lifecycle(0, FIRST_FRAME + 1, "pulse", Map.of("payload", "later"), List.of()));
        regressing.add(new Lifecycle(1, FIRST_FRAME, "pulse", Map.of("payload", "earlier"), List.of()));
        regressing.add(plainFrame(0));
        regressing.add(plainFrame(1));
        Path regressingCapture = writeRecords("regressing", engineMetadata, regressing);

        CompleteRunAudioReport regressingReport = CompleteRunAudioComparator.compare(reference, regressingCapture);
        assertSemanticFailure(regressingReport, CompleteRunAudioReport.Side.ENGINE,
                CompleteRunAudioComparator.ValidationException.Kind.LIFECYCLE_INVALID);

        List<CompleteRunAudioTrace.Record> futureLifecycleBeforeEarlierFrame = new ArrayList<>();
        futureLifecycleBeforeEarlierFrame.add(baseline(state(1)));
        futureLifecycleBeforeEarlierFrame.add(
                new Lifecycle(0, FIRST_FRAME + 1, "pulse", Map.of("payload", "future"), List.of()));
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
        records.add(baseline(state(1)));
        records.add(new Lifecycle(0, FIRST_FRAME, "pulse", Map.of("wrong", "field"), List.of()));
        records.add(plainFrame(0));
        Path engine = writeRecords("engine", engineMetadata, records);

        CompleteRunAudioReport report = CompleteRunAudioComparator.compare(reference, engine);

        assertSemanticFailure(report, CompleteRunAudioReport.Side.ENGINE,
                CompleteRunAudioComparator.ValidationException.Kind.LIFECYCLE_INVALID);
    }

    @Test
    void streamsMaximumCompleteRunHighEntropyFramesInSeparateThirtyTwoMiBJvm() throws Exception {
        int frames = 434_417;
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
        assertTrue(uncompressedBytes > 256L * 1024 * 1024,
                () -> "combined canonical inputs were only " + uncompressedBytes + " bytes");

        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process process = new ProcessBuilder(java, "-Xmx32m", "-cp", System.getProperty("java.class.path"),
                TestCompleteRunAudioComparator.class.getName(), "compare-probe", reference.toString(),
                engine.toString(), profile.id(), Integer.toString(frames))
                .redirectErrorStream(true).start();
        int status = process.waitFor();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, status, output);
        assertTrue(output.contains("MATCH frames=" + frames + " drained=true"), output);
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

    private void assertLifecycleMatch(TestProfile profile, List<CompleteRunAudioTrace.Record> records,
            String name) throws Exception {
        Path reference = writeRecords(name + "-reference", metadata(profile, ProducerKind.REFERENCE,
                profile.producerRuntimeIdentities().get(ProducerKind.REFERENCE)), records);
        Path engine = writeRecords(name + "-engine", metadata(profile, ProducerKind.OPENGGF,
                profile.producerRuntimeIdentities().get(ProducerKind.OPENGGF)), records);
        assertEquals(CompleteRunAudioReport.Kind.MATCH,
                CompleteRunAudioComparator.compare(reference, engine).kind());
    }

    private void assertInvalidLifecycleCapture(TestProfile profile,
            List<CompleteRunAudioTrace.Record> invalid, String name,
            CompleteRunAudioComparator.ValidationException.Kind kind) throws Exception {
        Metadata referenceMetadata = metadata(profile, ProducerKind.REFERENCE,
                profile.producerRuntimeIdentities().get(ProducerKind.REFERENCE));
        Metadata engineMetadata = metadata(profile, ProducerKind.OPENGGF,
                profile.producerRuntimeIdentities().get(ProducerKind.OPENGGF));
        Path reference = writeCapture(name + "-reference", referenceMetadata,
                profile.fixture().exclusiveEnd() - FIRST_FRAME, this::plainFrame);
        Path engine = writeRecords(name + "-engine", engineMetadata, invalid);
        assertSemanticFailure(CompleteRunAudioComparator.compare(reference, engine),
                CompleteRunAudioReport.Side.ENGINE, kind);
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

    private TestProfile lifecycleProfile(int frames, OwnerRef baselineOwner, int maximumRestoreDepth,
            Map<String, LifecycleRule> lifecycleRules) {
        TestProfile profile = profile("comparator.lifecycle." + PROFILE_SEQUENCE.incrementAndGet(), frames);
        profile.baselineRoleOwners = List.of(new RoleOwner(HardwareRole.FM1, baselineOwner));
        profile.restoreStackPolicy = new RestoreStackPolicy(maximumRestoreDepth, List.of(), null);
        profile.lifecycleRules = lifecycleRules;
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
        CompleteRunAudioProfile profile = CompleteRunAudioProfiles.require(metadata.profileId());
        return writeCapture(name, metadata,
                new Baseline(FIRST_FRAME, baselineState(profile), profile.baselineRoleOwners()),
                frames, framesFactory);
    }

    private Path writeCapture(String name, Metadata metadata, Baseline baseline, int frames,
            IntFunction<Frame> framesFactory) throws Exception {
        List<CompleteRunAudioTrace.Record> records = new ArrayList<>();
        CompleteRunAudioProfile profile = CompleteRunAudioProfiles.require(metadata.profileId());
        records.add(baseline);
        for (int row = 0; row < frames; row++) records.add(framesFactory.apply(row));
        CutoffFrontier cutoff = emptyFrontier(records);
        if (metadata.observerRuntimeIdentity() instanceof BufferedNativeObserverIdentity) {
            cutoff = new CutoffFrontier(cutoff.activeStack(), cutoff.pendingDescendants(),
                    cutoff.rawChipEvents(), new CutoffNativeDiagnostics(List.of(), List.of(), List.of(),
                            List.of(), 0, false, "f".repeat(64)), cutoff.ymPort0Latch(),
                    cutoff.ymPort1Latch(), cutoff.terminalState());
        }
        records.add(cutoff);
        Terminal terminal = terminal(metadata.fixture().exclusiveEnd(), records);
        NativeCapabilitySummary capability = profile.completeRunCapabilities().get(metadata.producerKind());
        if (capability != null) {
            terminal = new Terminal(terminal.exclusiveEnd(), terminal.frameCount(), terminal.requestCount(),
                    terminal.serviceCount(), terminal.decisionCount(), terminal.ymCount(), terminal.psgCount(),
                    terminal.lifecycleCount(), terminal.cutoffActiveCount(), terminal.cutoffPendingCount(),
                    capability, terminal.rootDigest(), terminal.semanticDigest());
        }
        records.add(terminal);
        Path output = temp.resolve(name);
        new CompleteRunAudioCaptureStore().writeNew(output, metadata, records.iterator());
        return output;
    }

    private Path writeCaptureWithCutoff(String name, TestProfile profile, ProducerKind kind,
            Frame frame, CutoffFrontier cutoff) throws Exception {
        return writeCaptureWithCutoff(name, profile, kind,
                new Baseline(FIRST_FRAME, baselineState(profile), profile.baselineRoleOwners()),
                frame, cutoff);
    }

    private Path writeCaptureWithCutoff(String name, TestProfile profile, ProducerKind kind,
            Baseline baseline, Frame frame, CutoffFrontier cutoff) throws Exception {
        Metadata metadata = metadata(profile, kind, profile.producerRuntimeIdentities().get(kind));
        List<CompleteRunAudioTrace.Record> records = new ArrayList<>();
        records.add(baseline);
        records.add(frame);
        records.add(cutoff);
        Terminal terminal = terminal(metadata.fixture().exclusiveEnd(), records);
        NativeCapabilitySummary capability = profile.completeRunCapabilities().get(kind);
        if (capability != null) {
            terminal = new Terminal(terminal.exclusiveEnd(), terminal.frameCount(), terminal.requestCount(),
                    terminal.serviceCount(), terminal.decisionCount(), terminal.ymCount(), terminal.psgCount(),
                    terminal.lifecycleCount(), terminal.cutoffActiveCount(), terminal.cutoffPendingCount(),
                    capability, terminal.rootDigest(), terminal.semanticDigest());
        }
        records.add(terminal);
        Path output = temp.resolve(name);
        new CompleteRunAudioCaptureStore().writeNew(output, metadata, records.iterator());
        return output;
    }

    private Path writeSequence(String name, TestProfile profile, ProducerKind kind,
            List<CompleteRunAudioTrace.Record> body) throws Exception {
        Metadata metadata = metadata(profile, kind, profile.producerRuntimeIdentities().get(kind));
        List<CompleteRunAudioTrace.Record> records = new ArrayList<>();
        records.add(new Baseline(FIRST_FRAME, baselineState(profile), profile.baselineRoleOwners()));
        records.addAll(body);
        CutoffFrontier cutoff = CutoffFrontier.empty(body.stream()
                .filter(Frame.class::isInstance).map(Frame.class::cast).toList().getLast()
                .services().getLast().state());
        if (kind == ProducerKind.REFERENCE
                && metadata.observerRuntimeIdentity() instanceof BufferedNativeObserverIdentity) {
            cutoff = new CutoffFrontier(List.of(), List.of(), List.of(),
                    new CutoffNativeDiagnostics(List.of(), List.of(), List.of(), List.of(),
                            0, false, "f".repeat(64)), 0, 0, cutoff.terminalState());
        }
        records.add(cutoff);
        Terminal terminal = terminal(metadata.fixture().exclusiveEnd(), records);
        NativeCapabilitySummary capability = profile.completeRunCapabilities().get(kind);
        if (capability != null) {
            terminal = new Terminal(terminal.exclusiveEnd(), terminal.frameCount(), terminal.requestCount(),
                    terminal.serviceCount(), terminal.decisionCount(), terminal.ymCount(), terminal.psgCount(),
                    terminal.lifecycleCount(), terminal.cutoffActiveCount(), terminal.cutoffPendingCount(),
                    capability, terminal.rootDigest(), terminal.semanticDigest());
        }
        records.add(terminal);
        Path output = temp.resolve(name);
        new CompleteRunAudioCaptureStore().writeNew(output, metadata, records.iterator());
        return output;
    }

    private Path writeRecords(String name, Metadata metadata, List<CompleteRunAudioTrace.Record> records)
            throws Exception {
        List<CompleteRunAudioTrace.Record> complete = new ArrayList<>(records);
        complete.add(emptyFrontier(complete));
        complete.add(terminal(metadata.fixture().exclusiveEnd(), complete));
        Path output = temp.resolve(name);
        new CompleteRunAudioCaptureStore().writeNew(output, metadata, complete.iterator());
        return output;
    }

    private Path writeStreamingCapture(String name, TestProfile profile, ProducerKind kind, int frames)
            throws Exception {
        String digest = streamingRoot(frames, false);
        String semanticDigest = streamingRoot(frames, true);
        Metadata metadata = metadata(profile, kind, profile.producerRuntimeIdentities().get(kind));
        Iterator<CompleteRunAudioTrace.Record> records = new Iterator<>() {
            private long cursor;
            @Override public boolean hasNext() { return cursor < 2L * frames + 3; }
            @Override public CompleteRunAudioTrace.Record next() {
                if (!hasNext()) throw new NoSuchElementException();
                long current = cursor++;
                if (current == 0) return baseline(state(1));
                if (current == 2L * frames + 1) return CutoffFrontier.empty(
                        entropyFrame(frames - 1).services().getFirst().state());
                if (current == 2L * frames + 2) {
                    return new Terminal(FIRST_FRAME + frames, frames, frames, frames, frames,
                            frames, frames, frames, 0, 0, digest, semanticDigest);
                }
                int row = (int) ((current - 1) / 2);
                return (current & 1) == 1 ? entropyLifecycle(row, frames) : entropyFrame(row);
            }
        };
        Path output = temp.resolve(name);
        new CompleteRunAudioCaptureStore().writeNew(output, metadata, records);
        return output;
    }

    private static String streamingRoot(int frames, boolean semantic) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, baseline(state(1)), semantic);
            for (int row = 0; row < frames; row++) {
                update(digest, entropyLifecycle(row, frames), semantic);
                update(digest, entropyFrame(row), semantic);
            }
            update(digest, CutoffFrontier.empty(entropyFrame(frames - 1).services().getFirst().state()), semantic);
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }

    private static Frame entropyFrame(int row) {
        Request request = request(row, 0xc0);
        OwnerRef owner = owner(row, 0xc0);
        Decision decision = new Decision(row, 0xc0, "sfx.c0", true, "accepted",
                row & 0xff, (row + 1) & 0xff, List.of(HardwareRole.FM1),
                List.of(new RoleDecision(HardwareRole.FM1,
                        row == 0 ? NONE : owner(row - 1L, 0xc0), owner)));
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
        return new Lifecycle(row, FIRST_FRAME + row, "pulse", Map.of("payload", entropy(frames + row)),
                List.of());
    }

    private static Iterator<CompleteRunAudioTrace.Record> stressRecords(int frames) {
        return new Iterator<>() {
            private int cursor = -1;
            @Override public boolean hasNext() { return cursor <= frames + 1; }
            @Override public CompleteRunAudioTrace.Record next() {
                if (!hasNext()) throw new NoSuchElementException();
                if (cursor++ == -1) return baseline(state(1));
                int row = cursor - 1;
                if (row == frames) return CutoffFrontier.empty(
                        requestAndDecisionFrame(frames - 1, request(frames - 1, 0xc0),
                                ownershipDecision(frames - 1, true, "accepted",
                                        frames == 1 ? NONE : owner(frames - 2L, 0xc0),
                                        owner(frames - 1L, 0xc0)), frames - 1).services().getFirst().state());
                if (row == frames + 1) {
                    return new Terminal(FIRST_FRAME + frames, frames, frames, frames, frames,
                            0, 0, 0, "a".repeat(64));
                }
                OwnerRef displaced = row == 0 ? NONE : owner(row - 1L, 0xc0);
                return requestAndDecisionFrame(row, request(row, 0xc0),
                        ownershipDecision(row, true, "accepted", displaced, owner(row, 0xc0)), row);
            }
        };
    }

    private static String entropy(int row) {
        StringBuilder value = new StringBuilder(160);
        long state = 0x9e3779b97f4a7c15L ^ row;
        for (int index = 0; index < 20; index++) {
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
                profile.observerRuntimeIdentities().get(kind),
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
                            List.of(), row >= admissionRow ? activeState(1) : state(1))));
        }
        return new Frame(FIRST_FRAME + row, "test", false, requests, List.of());
    }

    private Frame plainFrame(int row) {
        return new Frame(FIRST_FRAME + row, "test", false, List.of(), List.of());
    }

    private static Frame bufferedFrame(int row, List<DriverService> semanticServices,
            List<FrontierService> nativeServices, NativeManagedCorrelation... correlations) {
        return new Frame(FIRST_FRAME + row, "test", false, List.of(), semanticServices, List.of(),
                new FrameNativeDiagnostics(nativeServices, List.of(), List.of(), List.of(),
                        List.of(correlations)));
    }

    private static Frame requestFrame(int row, Request request) {
        return new Frame(FIRST_FRAME + row, "test", false, List.of(request), List.of());
    }

    private static Frame requestAndDecisionFrame(int row, Request request, Decision decision,
            long serviceOrdinal) {
        return new Frame(FIRST_FRAME + row, "test", false, List.of(request),
                List.of(service(serviceOrdinal, List.of(decision), List.of(), activeState(1))));
    }

    private static Frame requestAndDecisionFrame(int row, Request request, Decision decision,
            long serviceOrdinal, NormalizedState state) {
        return new Frame(FIRST_FRAME + row, "test", false, List.of(request),
                List.of(service(serviceOrdinal, List.of(decision), List.of(), state)));
    }

    private static Frame chipFrame(int row, int serviceOrdinal, int value) {
        return new Frame(FIRST_FRAME + row, "test", false, List.of(),
                List.of(service(serviceOrdinal, List.of(),
                        List.of(new YmWrite(0, 0, 0x22, value)), activeState(1))));
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

    private static Decision ownershipDecision(long requestOrdinal, boolean accepted, String reason,
            OwnerRef displacedOwner, OwnerRef finalOwner) {
        return ownershipDecisionFor(requestOrdinal, 0xc0, accepted, reason, displacedOwner, finalOwner);
    }

    private static Decision ownershipDecisionFor(long requestOrdinal, int nativeId, boolean accepted,
            String reason, OwnerRef displacedOwner, OwnerRef finalOwner) {
        return new Decision(requestOrdinal, nativeId, "sfx." + Integer.toHexString(nativeId), accepted,
                reason, 1, accepted ? 2 : 1,
                List.of(HardwareRole.FM1),
                List.of(new RoleDecision(HardwareRole.FM1, displacedOwner, finalOwner)));
    }

    private static Lifecycle lifecycle(long ordinal, int row, String kind,
            OwnerRef displacedOwner, OwnerRef finalOwner) {
        return new Lifecycle(ordinal, FIRST_FRAME + row, kind, Map.of(),
                List.of(new LifecycleOwnership(HardwareRole.FM1, displacedOwner, finalOwner)));
    }

    private static OwnerRef baselineMusic() {
        return new OwnerRef(OwnerClass.MUSIC, "music.81", 0x81, OwnerOrigin.BASELINE, 0);
    }

    private static Decision decisionForKey(long requestOrdinal, String key) {
        int nativeId = key.endsWith("a") ? 0xc0 : 0xc1;
        OwnerRef finalOwner = new OwnerRef(OwnerClass.SFX, key, nativeId,
                OwnerOrigin.REQUEST, requestOrdinal);
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
                OwnerOrigin.REQUEST, requestOrdinal);
    }

    private static DriverService service(long ordinal, List<Decision> decisions, List<ChipEvent> chipEvents,
            NormalizedState state) {
        return service(ordinal, decisions, chipEvents, state, "driver");
    }

    private static DriverService service(long ordinal, List<Decision> decisions, List<ChipEvent> chipEvents,
            NormalizedState state, String kind) {
        return new DriverService(ordinal, kind, ServiceCompletion.COMPLETED, decisions, state, chipEvents);
    }

    private static NormalizedState state(int tempo) {
        return new NormalizedState(List.of(new StateField("tempo", tempo)), inactiveRoles());
    }

    private static NormalizedState activeState(int tempo) {
        return new NormalizedState(List.of(new StateField("tempo", tempo)),
                List.of(new RoleState(HardwareRole.FM1, true,
                        List.of(new StateField("cursor", 0)))));
    }

    private static NormalizedState baselineState(CompleteRunAudioProfile profile) {
        List<RoleState> roles = profile.baselineRoleOwners().stream()
                .map(owner -> owner.owner().origin() == OwnerOrigin.NONE
                        ? new RoleState(owner.role(), false, List.of())
                        : new RoleState(owner.role(), true, List.of(new StateField("cursor", 0))))
                .toList();
        return new NormalizedState(List.of(new StateField("tempo", 1)), roles);
    }

    private static Baseline baseline(NormalizedState state) {
        return new Baseline(FIRST_FRAME, state,
                List.of(new RoleOwner(HardwareRole.FM1, NONE)));
    }

    private static List<RoleState> inactiveRoles() {
        return List.of(new RoleState(HardwareRole.FM1, false, List.of()));
    }

    private static Terminal terminal(int exclusiveEnd, List<CompleteRunAudioTrace.Record> records) {
        long frames = 0, requests = 0, services = 0, decisions = 0, ym = 0, psg = 0, lifecycles = 0;
        long cutoffActive = 0, cutoffPending = 0;
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
            } else if (record instanceof CutoffFrontier frontier) {
                cutoffActive = frontier.activeStack().size();
                cutoffPending = frontier.pendingDescendants().size();
            }
        }
        return new Terminal(exclusiveEnd, frames, requests, services, decisions, ym, psg, lifecycles,
                cutoffActive, cutoffPending, root(records), semanticRoot(records));
    }

    private static CutoffFrontier emptyFrontier(List<CompleteRunAudioTrace.Record> records) {
        for (int index = records.size() - 1; index >= 0; index--) {
            if (records.get(index) instanceof Frame frame && !frame.services().isEmpty()) {
                return CutoffFrontier.empty(frame.services().getLast().state());
            }
            if (records.get(index) instanceof Baseline baseline) {
                return CutoffFrontier.empty(baseline.state());
            }
        }
        throw new IllegalArgumentException("capture records lack a state boundary");
    }

    private static String root(List<CompleteRunAudioTrace.Record> records) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (CompleteRunAudioTrace.Record record : records) update(digest, record, false);
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }

    private static String semanticRoot(List<CompleteRunAudioTrace.Record> records) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (CompleteRunAudioTrace.Record record : records) update(digest, record, true);
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }

    private static void update(MessageDigest digest, CompleteRunAudioTrace.Record record) throws IOException {
        update(digest, record, true);
    }

    private static void update(MessageDigest digest, CompleteRunAudioTrace.Record record, boolean semantic)
            throws IOException {
        digest.update(((semantic ? CompleteRunAudioJson.writeSemanticRecord(record)
                : CompleteRunAudioJson.writeRecord(record)) + "\n")
                .getBytes(StandardCharsets.UTF_8));
    }

    private static final class TestProfile implements CompleteRunAudioProfile {
        private final String id;
        private final CompleteRunFixture fixture;
        private final Map<ProducerKind, ProducerRuntimeIdentity> runtimes = new LinkedHashMap<>();
        private final Map<ProducerKind, ObserverProof> observers = new LinkedHashMap<>();
        private final Map<ProducerKind, ObserverRuntimeIdentity> observerRuntimeIdentities = new LinkedHashMap<>();
        private List<HardwareRole> hardwareRoles = List.of(HardwareRole.FM1);
        private List<RoleOwner> baselineRoleOwners = List.of(new RoleOwner(HardwareRole.FM1, NONE));
        private Map<String, OwnershipTransition> ownershipTransitions = Map.of(
                "accepted", OwnershipTransition.ACQUIRE_REQUEST,
                "rejected", OwnershipTransition.REJECT_PRESERVE);
        private PendingRequestPolicy pendingPolicy = new PendingRequestPolicy(4, 0, null);
        private RestoreStackPolicy restoreStackPolicy = new RestoreStackPolicy(0, List.of(), null);
        private Map<String, LifecycleRule> lifecycleRules = Map.of(
                "pulse", new LifecycleRule("pulse", List.of("payload"),
                        LifecycleOwnershipAction.NONE));
        private CutoffFrontierPolicy cutoffPolicy = new CutoffFrontierPolicy(List.of(), 0, 0, 0, 0, 0,
                0, 0, 0, false, "f".repeat(64), CutoffFrontierPolicy.capabilityDigest(
                        CutoffFrontier.empty(new NormalizedState(List.of(), List.of()))), null);
        private Map<ProducerKind, NativeCapabilitySummary> capabilities = Map.of();

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
            observerRuntimeIdentities.put(ProducerKind.REFERENCE,
                    new CallbackObserverIdentity("bizhawk.test.callback.v1"));
            observerRuntimeIdentities.put(ProducerKind.OPENGGF,
                    new CallbackObserverIdentity("openggf.test.callback.v1"));
            observers.put(ProducerKind.REFERENCE,
                    new ObserverProof("reference.test.v1", "m68k.execute",
                            List.of(new CallbackProof("driver.service", 1))));
            observers.put(ProducerKind.OPENGGF,
                    new ObserverProof("openggf.test.v1", "java.observer",
                            List.of(new CallbackProof("driver.service", 1))));
        }

        private void useBufferedReference(FrontierServiceRule... serviceRules) {
            EnumMap<RuntimeArtifact, String> hashes = new EnumMap<>(RuntimeArtifact.class);
            for (RuntimeArtifact artifact : RuntimeArtifact.values()) {
                if (artifact != RuntimeArtifact.BIZHAWK_OBSERVER_MANAGED_PATCH
                        && artifact != RuntimeArtifact.BIZHAWK_OBSERVER_CORES_DLL
                        && artifact != RuntimeArtifact.OPENGGF_PRODUCER) {
                    hashes.put(artifact, "a".repeat(64));
                }
            }
            runtimes.put(ProducerKind.REFERENCE, new ProducerRuntimeIdentity(
                    "BizHawk", "2.11", "BizHawk", "2.11", "GPGX", "1.0",
                    ManagedObserverAdapter.REFLECTION, hashes));
            observerRuntimeIdentities.put(ProducerKind.REFERENCE, new BufferedNativeObserverIdentity(
                    CompleteRunAudioProfiles.GPGX_AUDIO_TRACE_ABI_NAME,
                    CompleteRunAudioProfiles.GPGX_AUDIO_TRACE_ABI_VERSION,
                    CompleteRunAudioProfiles.GPGX_AUDIO_TRACE_EVENT_SIZE,
                    CompleteRunAudioProfiles.GPGX_AUDIO_TRACE_CONFIG_SIZE,
                    CompleteRunAudioProfiles.GPGX_AUDIO_TRACE_KIND_SIZE,
                    CompleteRunAudioProfiles.GPGX_AUDIO_TRACE_HOOK_SIZE,
                    CompleteRunAudioProfiles.GPGX_AUDIO_TRACE_RANGE_SIZE,
                    CompleteRunAudioProfiles.GPGX_AUDIO_TRACE_CAPACITY,
                    "bizhawk-2.11-gpgx-audio-observer-v3", "gpgx-audio-observer-v3",
                    "0123456789abcdef", "a".repeat(64), "a".repeat(64), true, 1, 0));
            CutoffNativeDiagnostics emptyNative = new CutoffNativeDiagnostics(List.of(), List.of(),
                    List.of(), List.of(), 0, false, "f".repeat(64));
            cutoffPolicy = new CutoffFrontierPolicy(List.of(serviceRules), 0, 0, 0, 0, 0,
                    0, 0, 0, false, "f".repeat(64), CutoffFrontierPolicy.capabilityDigest(
                            CutoffFrontier.empty(new NormalizedState(List.of(), List.of()))),
                    CutoffFrontierPolicy.nativeCapabilityDigest(emptyNative));
            capabilities = Map.of(ProducerKind.REFERENCE,
                    new NativeCapabilitySummary(1, 1, "b".repeat(64), "c".repeat(64)));
        }

        @Override public String id() { return id; }
        @Override public CompleteRunFixture fixture() { return fixture; }
        @Override public List<HardwareRole> hardwareRoles() { return hardwareRoles; }
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
        @Override public Map<ProducerKind, ObserverRuntimeIdentity> observerRuntimeIdentities() {
            return Map.copyOf(observerRuntimeIdentities);
        }
        @Override public CutoffFrontierPolicy cutoffFrontierPolicy() {
            return cutoffPolicy;
        }
        @Override public Map<ProducerKind, NativeCapabilitySummary> completeRunCapabilities() {
            return capabilities;
        }
        @Override public Map<NativeSoundIdentity, List<NativeSoundIdentity>> decisionResolutions() {
            NativeSoundIdentity c0 = new NativeSoundIdentity(OwnerClass.SFX, "sfx.c0", 0xc0);
            NativeSoundIdentity c1 = new NativeSoundIdentity(OwnerClass.SFX, "sfx.c1", 0xc1);
            return Map.of(c0, List.of(c0), c1, List.of(c1));
        }
        @Override public List<RoleOwner> baselineRoleOwners() { return baselineRoleOwners; }
        @Override public Map<String, OwnershipTransition> ownershipTransitions() {
            return ownershipTransitions;
        }
        @Override public PendingRequestPolicy pendingRequestPolicy() {
            return pendingPolicy;
        }
        @Override public RestoreStackPolicy restoreStackPolicy() { return restoreStackPolicy; }
        @Override public Map<String, LifecycleRule> lifecycleRules() {
            return lifecycleRules;
        }
    }
}
