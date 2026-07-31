package com.openggf.game.resources;

import com.openggf.game.GameId;
import com.openggf.level.render.SpriteDplcFrame;
import com.openggf.level.render.TileLoadRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestDynamicArtLifecycleService {

    private static final int SONIC_ART = 0x22610;
    private static final int SONIC_VRAM = 0xF000;

    @Test
    void duplicateAndEmptyDplcsAdvanceTheOwnerCursorWithoutSubmittingWork() {
        DynamicArtLifecycleService service = new DynamicArtLifecycleService();
        startOpen(service);

        DynamicArtLifecycleService.ArtUpdate empty =
                service.observeRomDplc("sonic", 2, List.of(), SONIC_ART, SONIC_VRAM);
        DynamicArtLifecycleService.ArtUpdate duplicateEmpty =
                service.observeRomDplc("sonic", 2, List.of(), SONIC_ART, SONIC_VRAM);
        DynamicArtLifecycleService.ArtUpdate loaded =
                service.observeRomDplc("sonic", 3,
                        List.of(new TileLoadRequest(4, 2)), SONIC_ART, SONIC_VRAM);
        DynamicArtLifecycleService.ArtUpdate duplicateLoaded =
                service.observeRomDplc("sonic", 3,
                        List.of(new TileLoadRequest(4, 2)), SONIC_ART, SONIC_VRAM);

        assertTrue(empty.mappingChanged());
        assertFalse(empty.submitted());
        assertFalse(duplicateEmpty.mappingChanged());
        assertTrue(loaded.submitted());
        assertFalse(duplicateLoaded.mappingChanged());
        assertEquals(1, service.publishRow(0, false).edges().size());
    }

    @Test
    void multipleDplcRunsRemainOneOrderedSubmissionBatch() {
        DynamicArtLifecycleService service = new DynamicArtLifecycleService();
        startOpen(service);

        DynamicArtLifecycleService.ArtUpdate update =
                service.observeRomDplc("tails", 7,
                        List.of(new TileLoadRequest(3, 2),
                                new TileLoadRequest(11, 1)),
                        0x64320, 0xF400);

        DynamicArtDiagnosticsSnapshot snapshot = service.publishRow(0, false);
        assertEquals(1, snapshot.edges().size());
        assertEquals(List.of(
                new DynamicArtDiagnosticsSnapshot.Request(
                        0x64380, 3, -1, 0xF400, 0x40),
                new DynamicArtDiagnosticsSnapshot.Request(
                        0x64480, 11, -1, 0xF440, 0x20)),
                snapshot.edges().getFirst().requests());
        assertEquals(List.of(update.transferId()), snapshot.outstandingTransferIds());
    }

    @Test
    void completionIsPublishedOnlyAfterRuntimeArtApplication() {
        DynamicArtLifecycleService service = new DynamicArtLifecycleService();
        startOpen(service);
        DynamicArtLifecycleService.ArtUpdate update =
                service.observeRomDplc("sonic", 4,
                        List.of(new TileLoadRequest(1, 1)), SONIC_ART, SONIC_VRAM);

        DynamicArtDiagnosticsSnapshot submitted = service.publishRow(0, false);
        assertEquals("submitted", submitted.edges().getFirst().phase());
        assertEquals(List.of(update.transferId()), submitted.outstandingTransferIds());

        service.completeApplied(update);
        DynamicArtDiagnosticsSnapshot completed = service.publishRow(1, false);
        assertEquals("completed", completed.edges().getFirst().phase());
        assertTrue(completed.outstandingTransferIds().isEmpty());
    }

    @Test
    void sonicOneCompletionUsesThePhysicalStagingTransfer() {
        DynamicArtLifecycleService service = new DynamicArtLifecycleService();
        startOpen(service);
        DynamicArtLifecycleService.ArtUpdate update =
                service.observeRomDplc("sonic", 4,
                        List.of(new TileLoadRequest(1, 1)), SONIC_ART, SONIC_VRAM);

        service.completeApplied(update,
                List.of(DynamicArtDiagnosticsSnapshot.Request.ram(
                        0xC800, SONIC_VRAM, 0x2E0)));

        DynamicArtDiagnosticsSnapshot snapshot = service.publishRow(0, false);
        assertEquals(2, snapshot.edges().size());
        assertEquals(List.of(DynamicArtDiagnosticsSnapshot.Request.ram(
                        0xC800, SONIC_VRAM, 0x2E0)),
                snapshot.edges().get(1).requests());
    }

    @Test
    void specialStageRamOwnersHaveIndependentDuplicateCursors() {
        DynamicArtLifecycleService service = new DynamicArtLifecycleService();
        startOpen(service);

        DynamicArtLifecycleService.ArtUpdate sonic =
                service.observeRamDplc("ss-sonic", 0,
                        List.of(new TileLoadRequest(0, 2)),
                        0xFF0000, 0x5CA0);
        DynamicArtLifecycleService.ArtUpdate tails =
                service.observeRamDplc("ss-tails", 0,
                        List.of(new TileLoadRequest(0x183, 3)),
                        0xFF0000, 0x6000);
        DynamicArtLifecycleService.ArtUpdate tailsTail =
                service.observeRamDplc("ss-tails-tails", 0,
                        List.of(new TileLoadRequest(0x2AE, 1)),
                        0xFF0000, 0x62C0);

        assertTrue(sonic.submitted());
        assertTrue(tails.submitted());
        assertTrue(tailsTail.submitted());
        assertEquals(List.of("ss-sonic", "ss-tails", "ss-tails-tails"),
                service.publishRow(0, false).edges().stream()
                        .map(DynamicArtDiagnosticsSnapshot.Edge::owner).toList());
    }

    @Test
    void lagRowsKeepThePublishedLedgerAndForwardEdgesToTheNextNonLagRow() {
        DynamicArtLifecycleService service = new DynamicArtLifecycleService();
        startOpen(service);
        DynamicArtLifecycleService.ArtUpdate update =
                service.observeRomDplc("sonic", 1,
                        List.of(new TileLoadRequest(0, 1)), SONIC_ART, SONIC_VRAM);
        service.completeApplied(update);

        DynamicArtDiagnosticsSnapshot lag = service.publishRow(0, true);
        DynamicArtDiagnosticsSnapshot next = service.publishRow(1, false);

        assertTrue(lag.edges().isEmpty());
        assertTrue(lag.outstandingTransferIds().isEmpty());
        assertEquals(2, next.edges().size());
        assertEquals(0, next.edges().getFirst().logicalFrame());
        assertEquals(1, next.edges().getFirst().publicationFrame());
    }

    @Test
    void terminalPublicationForwardsBufferedEdgesOnTheFinalRow() {
        DynamicArtLifecycleService service = new DynamicArtLifecycleService();
        startOpen(service);
        service.observeRomDplc("sonic", 1,
                List.of(new TileLoadRequest(0, 1)), SONIC_ART, SONIC_VRAM);

        DynamicArtDiagnosticsSnapshot terminal = service.publishTerminal(4);

        assertTrue(terminal.edges().getFirst().terminalForwarded());
        assertEquals(4, terminal.edges().getFirst().publicationFrame());
        assertEquals(List.of(0L), terminal.outstandingTransferIds());
    }

    @Test
    void closingCannotInventATerminalRowBeforeAnyProductionIteration() {
        DynamicArtLifecycleService service = new DynamicArtLifecycleService();
        startOpen(service);
        service.observeRomDplc("sonic", 1,
                List.of(new TileLoadRequest(0, 1)), SONIC_ART, SONIC_VRAM);

        assertThrows(IllegalStateException.class,
                service::closeComparisonSegment);
    }

    @Test
    void comparisonSegmentOpenRejectsPendingWorkSubmittedByPriorSegment() {
        DynamicArtLifecycleService service = new DynamicArtLifecycleService();
        startOpen(service);
        service.observeRomDplc("sonic", 1,
                List.of(new TileLoadRequest(0, 1)), SONIC_ART, SONIC_VRAM);
        service.publishTerminal(0);
        service.closeComparisonSegment();

        assertThrows(IllegalStateException.class, service::openComparisonSegment);
        service.resetForMissingSnapshot();
        startOpen(service);
        assertEquals(-1, service.latestSnapshot().frame());
        assertTrue(service.latestSnapshot().edges().isEmpty());
        assertTrue(service.latestSnapshot()
                .outstandingTransferIds().isEmpty());
    }

    @Test
    void rewindRestoresLedgerBufferAndPublicationCursorTogether() {
        DynamicArtLifecycleService service = new DynamicArtLifecycleService();
        startOpen(service);
        DynamicArtLifecycleService.ArtUpdate first =
                service.observeRomDplc("sonic", 1,
                        List.of(new TileLoadRequest(0, 1)), SONIC_ART, SONIC_VRAM);
        DynamicArtLifecycleService.RewindState saved = service.capture();

        service.completeApplied(first);
        service.publishRow(0, false);
        service.restore(saved);

        DynamicArtDiagnosticsSnapshot restored = service.publishTerminal(3);
        assertEquals(1, restored.edges().size());
        assertEquals("submitted", restored.edges().getFirst().phase());
        assertEquals(List.of(first.transferId()), restored.outstandingTransferIds());
    }

    @Test
    void deliverySerialSurvivesRewindAndAdvancesOnRepublish() {
        DynamicArtLifecycleService service = new DynamicArtLifecycleService();
        startOpen(service);
        DynamicArtLifecycleService.RewindState beforePublication =
                service.capture();
        long generation =
                service.latestSnapshot().segmentGeneration();

        DynamicArtDiagnosticsSnapshot first = service.publishRow(0, false);
        service.restore(beforePublication);

        assertEquals(first.deliverySerial(),
                service.latestSnapshot().deliverySerial(),
                "rewind restore must preserve the current delivery epoch");
        assertEquals(generation,
                service.latestSnapshot().segmentGeneration(),
                "rewind restore must preserve the service-lifetime segment generation");
        assertFalse(service.latestSnapshot().published(),
                "rewind restore must restore payload publication state");
        DynamicArtDiagnosticsSnapshot republished =
                service.publishRow(0, false);
        assertTrue(republished.deliverySerial() > first.deliverySerial());
    }

    @Test
    void deliverySerialSurvivesFinishBeginAndFirstPublication() {
        DynamicArtLifecycleService service = new DynamicArtLifecycleService();
        startOpen(service);
        DynamicArtDiagnosticsSnapshot first = service.publishRow(0, false);
        service.closeComparisonSegment();
        service.finishRun();

        service.beginRun();
        service.openComparisonSegment();

        assertEquals(first.deliverySerial(),
                service.latestSnapshot().deliverySerial());
        DynamicArtDiagnosticsSnapshot next = service.publishRow(0, false);
        assertTrue(next.deliverySerial() > first.deliverySerial());
    }

    @Test
    void s2PendingBatchesCompleteFifoAtTheNextProductionVblank() {
        DynamicArtLifecycleService service = new DynamicArtLifecycleService();
        startOpen(service);

        DynamicArtLifecycleService.ArtUpdate sonic =
                service.observePlayerDplc(GameId.S2, "sonic", 1,
                        new SpriteDplcFrame(List.of(new TileLoadRequest(0, 1))));
        DynamicArtLifecycleService.ArtUpdate tails =
                service.observePlayerDplc(GameId.S2, "tails", 2,
                        new SpriteDplcFrame(List.of(new TileLoadRequest(3, 2))));
        DynamicArtDiagnosticsSnapshot submitted =
                service.publishRow(0, false);

        assertEquals(List.of("submitted", "submitted"),
                submitted.edges().stream()
                        .map(DynamicArtDiagnosticsSnapshot.Edge::phase).toList());
        assertEquals(List.of(sonic.transferId(), tails.transferId()),
                submitted.outstandingTransferIds());

        service.serviceProductionVBlank();
        service.observePlayerDplc(GameId.S2, "sonic", 3,
                new SpriteDplcFrame(List.of(new TileLoadRequest(7, 1))));
        DynamicArtDiagnosticsSnapshot next = service.publishRow(1, false);

        assertEquals(List.of("completed", "completed", "submitted"),
                next.edges().stream()
                        .map(DynamicArtDiagnosticsSnapshot.Edge::phase).toList());
        assertEquals(List.of(sonic.transferId(), tails.transferId()),
                next.edges().subList(0, 2).stream()
                        .map(DynamicArtDiagnosticsSnapshot.Edge::transferId).toList());
    }

    @Test
    void terminalTransferCompletesInClosedGapAndNextSegmentCanOpen() {
        DynamicArtLifecycleService service = new DynamicArtLifecycleService();
        startOpen(service);
        DynamicArtLifecycleService.ArtUpdate terminal =
                service.observePlayerDplc(GameId.S2, "sonic", 1,
                        new SpriteDplcFrame(List.of(new TileLoadRequest(0, 1))));
        service.publishRow(0, false);
        service.closeComparisonSegment();

        service.serviceProductionVBlank();

        assertEquals(List.of(terminal.transferId()),
                service.gapEdges().stream()
                        .map(DynamicArtGapTransition.GapEdge::transferId).toList());
        assertEquals("completed", service.gapEdges().getFirst().phase());
        service.openComparisonSegment();
        assertTrue(service.isComparisonSegmentOpen());
    }

    @Test
    void unpublishedS1PreparationSurvivesArmAndPromotesAtVblank() {
        DynamicArtLifecycleService service = new DynamicArtLifecycleService();
        service.beginRun();
        DynamicArtLifecycleService.ArtUpdate pending =
                service.observePlayerDplc(GameId.S1, "sonic", 48,
                        new SpriteDplcFrame(List.of(new TileLoadRequest(700, 16))));

        assertFalse(pending.submitted());
        assertTrue(service.gapEdges().isEmpty());
        service.openComparisonSegment();
        assertTrue(service.latestSnapshot().outstandingTransferIds().isEmpty());

        service.serviceProductionVBlank();
        DynamicArtDiagnosticsSnapshot first = service.publishRow(0, false);
        assertEquals(List.of(0L, 0L),
                first.edges().stream()
                        .map(DynamicArtDiagnosticsSnapshot.Edge::transferId).toList());
        assertEquals(List.of("submitted", "completed"),
                first.edges().stream()
                        .map(DynamicArtDiagnosticsSnapshot.Edge::phase).toList());
        assertTrue(first.outstandingTransferIds().isEmpty());
    }

    @Test
    void s1PreparationReplacementIsRewindAtomic() {
        DynamicArtLifecycleService service = new DynamicArtLifecycleService();
        service.beginRun();
        service.observePlayerDplc(GameId.S1, "sonic", 9,
                new SpriteDplcFrame(List.of(new TileLoadRequest(84, 6))));
        DynamicArtLifecycleService.RewindState snapshot = service.capture();
        service.observePlayerDplc(GameId.S1, "sonic", 1,
                new SpriteDplcFrame(List.of(new TileLoadRequest(0, 3))));

        service.restore(snapshot);
        service.openComparisonSegment();
        service.serviceProductionVBlank();
        DynamicArtDiagnosticsSnapshot row = service.publishRow(0, false);
        assertEquals(9, row.edges().getFirst().mappingFrame());
        assertEquals(SONIC_ART + 84 * 0x20,
                row.edges().getFirst().requests().getFirst().romSourceAddress());
    }

    @Test
    void s1PreparationCannotLeakAcrossMissingSnapshotOrRunReset() {
        DynamicArtLifecycleService service = new DynamicArtLifecycleService();
        service.beginRun();
        service.observePlayerDplc(GameId.S1, "sonic", 9,
                new SpriteDplcFrame(List.of(new TileLoadRequest(84, 6))));
        service.resetForMissingSnapshot();

        service.beginRun();
        service.openComparisonSegment();
        service.serviceProductionVBlank();
        assertTrue(service.publishRow(0, false).edges().isEmpty());
        service.finishRun();

        service.beginRun();
        service.openComparisonSegment();
        service.serviceProductionVBlank();
        assertTrue(service.publishRow(0, false).edges().isEmpty());
    }

    @Test
    void completeLifecycleInGapIsJournaledWithoutResettingOwnerCursor() {
        DynamicArtLifecycleService service = new DynamicArtLifecycleService();
        service.beginRun();

        DynamicArtLifecycleService.ArtUpdate first =
                service.observePlayerDplc(GameId.S2, "tails", 5,
                        new SpriteDplcFrame(List.of(new TileLoadRequest(1, 1))));
        service.serviceProductionVBlank();

        assertEquals(List.of("submitted", "completed"),
                service.gapEdges().stream()
                        .map(DynamicArtGapTransition.GapEdge::phase).toList());
        assertEquals(List.of(first.transferId(), first.transferId()),
                service.gapEdges().stream()
                        .map(DynamicArtGapTransition.GapEdge::transferId).toList());

        service.openComparisonSegment();
        DynamicArtLifecycleService.ArtUpdate duplicate =
                service.observePlayerDplc(GameId.S2, "tails", 5,
                        new SpriteDplcFrame(List.of(new TileLoadRequest(1, 1))));
        assertFalse(duplicate.mappingChanged());
    }

    @Test
    void rewindRestoresClosedGapFifoLedgerAndRunCursorsAtomically() {
        DynamicArtLifecycleService service = new DynamicArtLifecycleService();
        service.beginRun();
        DynamicArtLifecycleService.ArtUpdate pending =
                service.observePlayerDplc(GameId.S2, "sonic", 9,
                        new SpriteDplcFrame(List.of(new TileLoadRequest(2, 1))));
        DynamicArtLifecycleService.RewindState saved = service.capture();

        service.serviceProductionVBlank();
        service.openComparisonSegment();
        service.restore(saved);
        service.serviceProductionVBlank();

        assertFalse(service.isComparisonSegmentOpen());
        assertEquals(List.of("submitted", "completed"),
                service.gapEdges().stream()
                        .map(DynamicArtGapTransition.GapEdge::phase).toList());
        assertEquals(List.of(pending.transferId(), pending.transferId()),
                service.gapEdges().stream()
                        .map(DynamicArtGapTransition.GapEdge::transferId).toList());
    }

    @Test
    void finishRunAllowsNativePendingWorkButRejectsFurtherDecisions() {
        DynamicArtLifecycleService service = new DynamicArtLifecycleService();
        service.beginRun();
        service.observePlayerDplc(GameId.S2, "sonic", 1,
                new SpriteDplcFrame(List.of(new TileLoadRequest(0, 1))));

        service.finishRun();

        assertFalse(service.isRunActive());
        assertThrows(IllegalStateException.class,
                () -> service.observePlayerDplc(GameId.S2, "sonic", 2,
                        new SpriteDplcFrame(List.of(new TileLoadRequest(1, 1)))));
    }

    private static void startOpen(DynamicArtLifecycleService service) {
        service.beginRun();
        service.openComparisonSegment();
    }
}
