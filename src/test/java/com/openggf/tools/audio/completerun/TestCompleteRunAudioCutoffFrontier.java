package com.openggf.tools.audio.completerun;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.CaptureCounts;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.CutoffFrontier;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.CutoffFrontierPolicy;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.CutoffService;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.FrontierChipEvent;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.FrontierOwnedChip;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.FrontierOwnedSnapshot;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.FrontierService;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.FrontierServiceState;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.FrontierSnapshot;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.FrontierServiceRule;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.FrontierSnapshotRule;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.NormalizedState;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.NativeAncestryTransition;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.PsgWrite;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.ServiceAncestry;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.ServiceAncestryTransition;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.ServiceCoordinate;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.Terminal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

class TestCompleteRunAudioCutoffFrontier {
    @Test
    void promotedOpenServiceRetainsCanonicalAncestryAfterItsParentWasPublished() throws Exception {
        ServiceCoordinate parentBegin = new ServiceCoordinate(800, 0);
        ServiceAncestryTransition transition = new ServiceAncestryTransition(
                parentBegin, 1, null, 0, 900, 0);
        ServiceAncestry ancestry = new ServiceAncestry(parentBegin, 1, null, 0,
                List.of(transition));
        NativeAncestryTransition rawTransition = new NativeAncestryTransition(
                50, 900, 4, 1, 1, 0, 0, 6, "Z80", 0xac);
        FrontierService promoted = new FrontierService(2, 1, 1, "update",
                FrontierServiceState.OPEN, 801, 0, 0x71b4c, 3, "M68K",
                null, null, null, null, List.of(), List.of(), 0, 0,
                List.of(rawTransition), ancestry);
        CutoffFrontier reference = CutoffFrontier.fromNative(List.of(promoted), List.of(),
                List.of(), List.of(), 0, 0, 1, true, STATE, "a".repeat(64));
        CutoffFrontier engine = new CutoffFrontier(reference.activeStack(), List.of(), List.of(),
                null, 0, 0, STATE);

        assertEquals(null, CompleteRunAudioComparator.difference(reference, engine));
        assertEquals(ancestry, reference.activeStack().getFirst().ancestry());
        assertEquals(reference, CompleteRunAudioJson.readRecord(
                CompleteRunAudioJson.writeRecord(reference)));
        CutoffFrontier missingActive = CutoffFrontier.empty(STATE);
        assertNotEquals(null, CompleteRunAudioComparator.difference(engine, missingActive));
        assertThrows(IllegalArgumentException.class, () -> new CutoffFrontier(
                reference.activeStack(), List.of(), List.of(),
                new CompleteRunAudioTrace.CutoffNativeDiagnostics(
                        List.of(), List.of(), List.of(), List.of(), 1, true, "a".repeat(64)),
                0, 0, STATE));

        CutoffService outer = new CutoffService(null, -1, 0, "outer",
                FrontierServiceState.OPEN, 901, 0, null, null, List.of());
        CutoffService pending = new CutoffService(901, 0, 1, "child",
                FrontierServiceState.COMPLETED, 901, 1, 901, 2L, List.of());
        CutoffFrontier withPending = new CutoffFrontier(
                List.of(outer), List.of(pending), List.of(), null, 0, 0, STATE);
        CutoffFrontier missingPending = new CutoffFrontier(
                List.of(outer), List.of(), List.of(), null, 0, 0, STATE);
        assertNotEquals(null, CompleteRunAudioComparator.difference(withPending, missingPending));

        ServiceAncestry beforeBegin = new ServiceAncestry(parentBegin, 1, null, 0,
                List.of(new ServiceAncestryTransition(parentBegin, 1, null, 0, 801, 0)));
        assertThrows(IllegalArgumentException.class, () -> new CutoffService(800, 0, 1, "open",
                FrontierServiceState.OPEN, 801, 0, null, null, List.of(), beforeBegin));
        ServiceAncestry atCompletedEnd = new ServiceAncestry(parentBegin, 1, null, 0,
                List.of(new ServiceAncestryTransition(parentBegin, 1, null, 0, 802, 2)));
        assertThrows(IllegalArgumentException.class, () -> new CutoffService(800, 0, 1, "complete",
                FrontierServiceState.COMPLETED, 801, 0, 802, 2L, List.of(), atCompletedEnd));
    }

    @Test
    void ordinaryCutoffChipWritesRejectResetOwnership() {
        FrontierChipEvent reset = new FrontierChipEvent(1, 1, "RESET", 0, 4, 0, 0x9f,
                true, null, null);
        assertThrows(IllegalArgumentException.class, () -> withChip(
                service(1, 0, 0, FrontierServiceState.OPEN, 10), reset));
    }

    @Test
    void resetRootRuleAcceptsOnlyTheExactHooklessResetShape() {
        FrontierChipEvent resetWrite = new FrontierChipEvent(1, 1, "RESET", 0, 4, 0, 0x9f,
                true, null, null);
        FrontierService reset = new FrontierService(1, 0, 0, "reset", FrontierServiceState.COMPLETED,
                3, 1, 0, 0, "RESET", 3, 3L, 0, 0, List.of(), List.of(resetWrite));
        FrontierServiceRule rule = new FrontierServiceRule("reset", FrontierServiceState.COMPLETED,
                0, "RESET", 0, 0, 0, List.of());
        assertEquals(true, rule.matches(reset) && rule.acceptsChipSources(reset));
        assertThrows(IllegalArgumentException.class, () -> new FrontierServiceRule("ordinary",
                FrontierServiceState.COMPLETED, 0, "Z80", 0x38, 0, 0, List.of()));
    }

    @Test
    void nativePerFrameOrdinalsNormalizeToCutoffLocalSemanticOrder() {
        FrontierChipEvent first = new FrontierChipEvent(10, 0, "Z80", 0x100, 4, 0, 0x90,
                true, null, null);
        FrontierChipEvent second = new FrontierChipEvent(20, 0, "Z80", 0x101, 4, 0, 0x91,
                true, null, null);
        FrontierService open = new FrontierService(1, 0, 0, "service", FrontierServiceState.OPEN,
                900, 1, 0x38, 1, "Z80", null, null, null, null, List.of(), List.of(first, second));
        CutoffFrontier value = CutoffFrontier.fromNative(List.of(open), List.of(),
                List.of(new FrontierOwnedChip(1, first), new FrontierOwnedChip(1, second)), List.of(),
                0, 0, 1, true, STATE, "b".repeat(64));

        assertEquals(List.of(0L, 1L), value.rawChipEvents().stream().map(event -> event.ordinal()).toList());
        assertEquals(value.rawChipEvents(), value.activeStack().getFirst().chipEvents());
    }

    @Test
    void semanticHierarchyUsesTheFullParentCoordinate() {
        CutoffService outer = new CutoffService(null, -1, 0, "outer", FrontierServiceState.OPEN,
                900, 0, null, null, List.of());
        CutoffService child = new CutoffService(900, 0, 1, "child", FrontierServiceState.OPEN,
                901, 0, null, null, List.of());
        new CutoffFrontier(List.of(outer, child), List.of(), List.of(), null, 0, 0, STATE);
        CutoffService wrongParent = new CutoffService(901, 0, 1, "child", FrontierServiceState.OPEN,
                902, 0, null, null, List.of());
        assertThrows(IllegalArgumentException.class, () -> new CutoffFrontier(
                List.of(outer, wrongParent), List.of(), List.of(), null, 0, 0, STATE));
    }

    @Test
    void rawYmProjectionRejectsWrongPortAndWrongPriorLatch() {
        assertThrows(IllegalArgumentException.class, () -> new FrontierChipEvent(
                1, 0, "Z80", 0x100, 3, 2, 0x2a, false, 0, 0));
        FrontierChipEvent address = new FrontierChipEvent(1, 0, "Z80", 0x100,
                3, 0, 0x2a, false, 0, 0);
        FrontierChipEvent wrongData = new FrontierChipEvent(2, 1, "Z80", 0x101,
                3, 1, 0x7f, true, 0, 0x22);
        FrontierService open = new FrontierService(1, 0, 0, "service", FrontierServiceState.OPEN,
                900, 1, 0x38, 1, "Z80", null, null, null, null,
                List.of(), List.of(address, wrongData));
        assertThrows(IllegalArgumentException.class, () -> new CompleteRunAudioTrace.CutoffNativeDiagnostics(
                List.of(open), List.of(), List.of(new FrontierOwnedChip(1, address),
                        new FrontierOwnedChip(1, wrongData)), List.of(), 1, true, "b".repeat(64)));
    }

    @Test
    void bindsTheActualTask8CompleteRunCutoffVectors() throws Exception {
        Path capability = Path.of("tools/bizhawk-headless/fixtures/gpgx-audio-capability-v1.json");
        assertEquals(CompleteRunAudioProfiles.GPGX_AUDIO_CAPABILITY_SHA256,
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(Files.readAllBytes(capability))));
        JsonNode runs = new ObjectMapper().readTree(capability.toFile()).path("runs");
        JsonNode s2 = runs.path("s2").path("complete");
        assertEquals(0, s2.path("open_services_at_cutoff").asInt());
        assertEquals(0, s2.path("pending_services_at_cutoff").asInt());
        assertEquals("2afa645a9471a7e084fa4273a9cfa0978868fe7be4f9a33f72f73de2ca907804",
                s2.path("frontier_digest_sha256").asText());
        JsonNode s3k = runs.path("s3k").path("complete");
        assertEquals(1, s3k.path("open_services_at_cutoff").asInt());
        assertEquals(4, s3k.path("pending_services_at_cutoff").asInt());
        assertEquals("88d87b134b63df1b819321a95f4244f5204f45aa4be85267d0ab3f0876ec6c0c",
                s3k.path("frontier_digest_sha256").asText());
    }

    private static final NormalizedState STATE = new NormalizedState(List.of(), List.of());

    @Test
    void canonicalFrontierRoundTripsWithoutLosingExclusiveRawOwnership() throws Exception {
        FrontierChipEvent chip = new FrontierChipEvent(0x100000013L, 19, "Z80", 0x1234,
                3, 1, 0x7f, true, 0, 0x2a);
        FrontierSnapshot snapshot = new FrontierSnapshot(7, "Z80", 0x1240, List.of(1, 2, 255));
        FrontierService open = new FrontierService(4, 0, 0, "vint", FrontierServiceState.OPEN,
                900, 10, 0x38, 2, "Z80", null, null, null, null, List.of(snapshot), List.of(chip));
        FrontierService completed = new FrontierService(5, 4, 1, "dpcm",
                FrontierServiceState.COMPLETED, 901, 11, 0x17a, 8, "Z80", 901, 24L,
                0x1b0, 9, List.of(), List.of());
        CutoffFrontier frontier = CutoffFrontier.fromNative(List.of(open), List.of(completed),
                List.of(new FrontierOwnedChip(4, chip)), List.of(new FrontierOwnedSnapshot(4, 0, snapshot)),
                0x2a, 0x22, 3, true, STATE, "a".repeat(64));

        String json = CompleteRunAudioJson.writeRecord(frontier);
        assertEquals(frontier, CompleteRunAudioJson.readRecord(json));
    }

    @Test
    void activeAndPendingOrdersAndOwnershipAreStrict() {
        FrontierService outer = service(1, 0, 0, FrontierServiceState.OPEN, 10);
        FrontierService child = service(2, 1, 1, FrontierServiceState.OPEN, 11);
        FrontierService completed = service(3, 1, 1, FrontierServiceState.COMPLETED, 12);
        assertThrows(IllegalArgumentException.class, () -> frontier(List.of(child, outer), List.of()));
        assertThrows(IllegalArgumentException.class, () -> frontier(List.of(
                service(1, 0, 0, FrontierServiceState.OPEN, 12),
                service(2, 1, 1, FrontierServiceState.OPEN, 11)), List.of()));
        assertThrows(IllegalArgumentException.class, () -> frontier(List.of(outer),
                List.of(service(4, 1, 1, FrontierServiceState.COMPLETED, 14), completed)));
        assertThrows(IllegalArgumentException.class, () -> frontier(List.of(outer), List.of(
                service(3, 2, 2, FrontierServiceState.COMPLETED, 12),
                service(2, 1, 1, FrontierServiceState.COMPLETED, 13))));
        FrontierChipEvent duplicate = new FrontierChipEvent(0x100000009L, 9, "Z80", 1,
                4, 0, 0x9f, true, null, null);
        FrontierService ownedA = withChip(outer, duplicate);
        FrontierService ownedB = withChip(completed, duplicate);
        assertThrows(IllegalArgumentException.class, () -> frontier(List.of(ownedA), List.of(ownedB)));
        FrontierService boundaryA = service(5, 1, 1, FrontierServiceState.COMPLETED, 20);
        FrontierService boundaryB = service(6, 1, 1, FrontierServiceState.COMPLETED, 30);
        assertThrows(IllegalArgumentException.class,
                () -> frontier(List.of(outer), List.of(boundaryA, boundaryB)));

        CutoffService gappedBoundary = new CutoffService(null, -1, 0, "service",
                FrontierServiceState.OPEN, 900, 1, null, null, List.of());
        assertDoesNotThrow(() -> new CutoffFrontier(
                List.of(gappedBoundary), List.of(), List.of(), null, 0, 0, STATE));
        PsgWrite gappedChip = new PsgWrite(1, 0x9f);
        CutoffService gappedOwner = new CutoffService(null, -1, 0, "service",
                FrontierServiceState.OPEN, 900, 0, null, null, List.of(gappedChip));
        assertThrows(IllegalArgumentException.class, () -> new CutoffFrontier(
                List.of(gappedOwner), List.of(), List.of(gappedChip), null, 0, 0, STATE));
    }

    @Test
    void terminalCountsIncludeTheMandatoryFrontierCardinality() {
        Terminal terminal = new Terminal(10, 1, 0, 0, 0, 0, 0, 0, 1, 2, "c".repeat(64));
        terminal.validateObservedCounts(new CaptureCounts(1, 0, 0, 0, 0, 0, 0, 1, 2));
        assertThrows(IllegalArgumentException.class, () -> terminal.validateObservedCounts(
                new CaptureCounts(1, 0, 0, 0, 0, 0, 0, 1, 1)));
    }

    @Test
    void comparatorReportsTheCutoffFrontierBeforeTerminalCounts() {
        CutoffFrontier reference = frontier(List.of(service(1, 0, 0, FrontierServiceState.OPEN, 10)),
                List.of());
        CutoffFrontier engine = new CutoffFrontier(reference.activeStack(), reference.pendingDescendants(),
                reference.rawChipEvents(), null,
                1, 0, STATE);
        var difference = CompleteRunAudioComparator.difference(reference, engine);
        assertEquals(CompleteRunAudioReport.Kind.CUTOFF_FRONTIER_VALUE, difference.kind());
        assertEquals("cutoff_frontier", difference.location());
    }

    @Test
    void rawCallbackCoordinatesAreValidatedButExcludedFromSemanticEquality() throws Exception {
        FrontierChipEvent first = new FrontierChipEvent(0x100000001L, 1, "Z80", 0x100,
                3, 1, 0x7f, true, 0, 0x2a);
        FrontierChipEvent second = new FrontierChipEvent(0x200000009L, 9, "Z80", 0x200,
                3, 1, 0x7f, true, 0, 0x2a);
        FrontierService firstOuter = service(1, 0, 0, FrontierServiceState.OPEN, 10);
        FrontierService firstChild = withChip(service(2, 1, 1, FrontierServiceState.COMPLETED, 20), first);
        FrontierService secondOuter = service(1, 0, 0, FrontierServiceState.OPEN, 100);
        FrontierService secondChild = new FrontierService(2, 1, 1, "service",
                FrontierServiceState.COMPLETED, 900, 200, 0x38, 1, "Z80",
                900, 300L, 0x40, 2, List.of(), List.of(second));
        CutoffFrontier firstFrontier = frontier(List.of(firstOuter), List.of(firstChild));
        CutoffFrontier secondFrontier = frontier(List.of(secondOuter), List.of(secondChild));
        assertEquals(null, CompleteRunAudioComparator.difference(firstFrontier, secondFrontier));
        assertNotEquals(CompleteRunAudioJson.writeRecord(firstFrontier),
                CompleteRunAudioJson.writeRecord(secondFrontier));
        assertEquals(CompleteRunAudioJson.writeSemanticRecord(firstFrontier),
                CompleteRunAudioJson.writeSemanticRecord(secondFrontier));
    }

    @Test
    void pendingDeferredBeginIsRawOnlyImmutableCutoffEvidence() throws Exception {
        FrontierService blocker = service(13, 0, 0, FrontierServiceState.OPEN, 10);
        CompleteRunAudioTrace.NativeDeferredServiceBegin pending =
                new CompleteRunAudioTrace.NativeDeferredServiceBegin(
                        13, 0, 6, 0, 4, 77, 2, 0x71b4c,
                        40, 41, 12, 13, 2, false, 0, 0);
        CutoffFrontier projected = frontier(List.of(blocker), List.of());
        CompleteRunAudioTrace.CutoffNativeDiagnostics raw =
                new CompleteRunAudioTrace.CutoffNativeDiagnostics(
                        List.of(blocker), List.of(), List.of(), List.of(), pending,
                        1, true, "b".repeat(64));
        CutoffFrontier withPending = new CutoffFrontier(projected.activeStack(), List.of(), List.of(),
                raw, 0, 0, STATE);
        CutoffFrontier withoutPending = new CutoffFrontier(projected.activeStack(), List.of(), List.of(),
                null, 0, 0, STATE);

        assertNotEquals(CompleteRunAudioJson.writeRecord(withPending),
                CompleteRunAudioJson.writeRecord(withoutPending));
        assertEquals(CompleteRunAudioJson.writeSemanticRecord(withPending),
                CompleteRunAudioJson.writeSemanticRecord(withoutPending));
        assertEquals(withPending, CompleteRunAudioJson.readRecord(
                CompleteRunAudioJson.writeRecord(withPending)));
        assertThrows(IllegalArgumentException.class, () ->
                new CompleteRunAudioTrace.CutoffNativeDiagnostics(
                        List.of(), List.of(), List.of(), List.of(), pending,
                        1, true, "b".repeat(64)));
    }

    @Test
    void exactPolicyRejectsTruncatedOwnershipAndPinsCanonicalCapabilityBytes() {
        CutoffFrontier empty = CutoffFrontier.empty(STATE);
        assertEquals("2f2c00122d6e952e8f3fe2bdb1aa853acd70dba92630a6fe885fac34c3880d9a",
                CutoffFrontierPolicy.capabilityDigest(empty));

        FrontierService open = service(1, 0, 0, FrontierServiceState.OPEN, 10);
        CutoffFrontier value = frontier(List.of(open), List.of());
        CutoffFrontierPolicy policy = new CutoffFrontierPolicy(List.of(new FrontierServiceRule(
                "service", FrontierServiceState.OPEN, 1, "Z80", 0x38, null, null, List.of())),
                1, 0, 0, 0, 0, 0, 0, 1, true, "b".repeat(64),
                CutoffFrontierPolicy.capabilityDigest(value),
                CutoffFrontierPolicy.nativeCapabilityDigest(value.nativeDiagnostics()));
        policy.validate(value);
        assertThrows(IllegalArgumentException.class, () -> policy.validate(
                new CutoffFrontier(List.of(), List.of(), List.of(), null, 0, 0, STATE)));
        assertThrows(IllegalArgumentException.class, () -> new CutoffFrontierPolicy(List.of(
                new FrontierServiceRule("service", FrontierServiceState.OPEN, 1, "Z80", 0x40,
                        null, null, List.<FrontierSnapshotRule>of())),
                1, 0, 0, 0, 0, 0, 0, 1, true, "b".repeat(64),
                CutoffFrontierPolicy.capabilityDigest(value),
                CutoffFrontierPolicy.nativeCapabilityDigest(value.nativeDiagnostics())).validate(value));
    }

    private static FrontierService service(long token, long parent, int depth,
            FrontierServiceState state, long begin) {
        boolean open = state == FrontierServiceState.OPEN;
        return new FrontierService(token, parent, depth, "service", state, 900, begin, 0x38,
                1, "Z80", open ? null : 900, open ? null : begin + 10, open ? null : 0x40,
                open ? null : 2, List.of(), List.of());
    }

    private static FrontierService withChip(FrontierService value, FrontierChipEvent chip) {
        return new FrontierService(value.token(), value.parentToken(), value.depth(), value.kind(), value.state(),
                value.beginFrame(), value.beginOrdinal(), value.beginPc(), value.beginHookToken(),
                value.beginSourceCpu(), value.endFrame(), value.endOrdinal(), value.endPc(), value.endHookToken(),
                value.snapshots(), List.of(chip));
    }

    private static CutoffFrontier frontier(List<FrontierService> active, List<FrontierService> pending) {
        List<FrontierService> services = java.util.stream.Stream.concat(active.stream(), pending.stream()).toList();
        List<FrontierOwnedChip> chips = services.stream()
                .flatMap(service -> service.chipEvents().stream().map(event -> new FrontierOwnedChip(service.token(), event)))
                .sorted(java.util.Comparator.comparingLong(owned -> owned.event().coordinate())).toList();
        java.util.ArrayList<FrontierOwnedSnapshot> snapshots = new java.util.ArrayList<>();
        for (FrontierService service : services) {
            for (int index = 0; index < service.snapshots().size(); index++) {
                snapshots.add(new FrontierOwnedSnapshot(service.token(), index, service.snapshots().get(index)));
            }
        }
        return CutoffFrontier.fromNative(active, pending, chips, snapshots,
                0, 0, 1, true, STATE, "b".repeat(64));
    }
}
