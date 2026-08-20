package com.openggf.tests.trace.runs;

import com.openggf.game.resources.DynamicArtGapTransition;
import com.openggf.game.resources.DynamicArtLifecycleService;
import com.openggf.trace.DynamicArtTransfer;
import com.openggf.trace.TraceRunManifest;
import com.openggf.trace.replay.runs.TraceRunDynamicArtGapJournal;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestTraceRunDynamicArtGapJournal {

    @Test
    void requiresObservedCloseBeforeGapAndComparesObservedDestinationOpen() {
        DynamicArtLifecycleService lifecycle = new DynamicArtLifecycleService();
        lifecycle.beginRun();
        lifecycle.openComparisonSegment();
        TraceRunDynamicArtGapJournal journal =
                new TraceRunDynamicArtGapJournal(manifest(), lifecycle);

        assertThrows(IllegalStateException.class, () -> journal.gapOpened(0));
        lifecycle.closeComparisonSegment();
        journal.sourceClosed(0);
        journal.gapOpened(0);
        lifecycle.openComparisonSegment();

        assertFalse(journal.destinationOpened(1, 0).hasError());
    }

    @Test
    void terminalTailClosesWithoutFabricatingADestinationSegment() {
        DynamicArtLifecycleService lifecycle = new DynamicArtLifecycleService();
        lifecycle.beginRun();
        lifecycle.openComparisonSegment();
        TraceRunDynamicArtGapJournal journal =
                new TraceRunDynamicArtGapJournal(terminalManifest(), lifecycle);

        lifecycle.closeComparisonSegment();
        journal.sourceClosed(0);
        journal.gapOpened(0);

        var comparison = journal.terminalTailClosed(120);

        assertFalse(comparison.hasError(), comparison.divergentFields()::toString);
        assertTrue(comparison.fields().containsKey("run_tail.edge_count"));
        assertFalse(comparison.fields().keySet().stream()
                .anyMatch(field -> field.contains("destination")),
                "terminal tails have no synthetic destination identity or ledger hash");
    }

    /**
     * A stale gap stamp is re-rowed against the last movie row the engine
     * actually ran, not against the row the frozen cursor is announcing.
     *
     * <p>The two differ by exactly one across the ROM's title-card arm frame:
     * a boundary that admits after the destination's row zero has run last ran
     * that row, and one that admits on the arm frame itself last ran the row
     * before it (docs/architecture/audits/trace/
     * 2026-08-20-s2-title-card-mode-flip-phase.md). The unannounced-row count
     * moves with the phase too, so the recovered row must not: the recorder
     * stamps a gap edge with the row it is executing
     * ("tools/bizhawk-headless/src/Recording/S2RunCaptureRunner.cs":207-222)
     * and that row is a property of the movie, not of when the engine happens
     * to poll admission.
     */
    @Test
    void staleGapStampsRecoverTheSameRowOnEitherSideOfTheArmFrame() {
        int frozenRow = 9701;

        // Admitted after the destination's row zero ran: last row run is that
        // row, and one more unannounced row elapsed after the edge.
        List<DynamicArtGapTransition> fused =
                TraceRunDynamicArtGapJournal.rowsCountedBackFromAdmission(
                        List.of(staleEdge(frozenRow, 5839)),
                        frozenRow, 5840, frozenRow);

        // Admitted on the arm frame, before row zero ran: last row run is the
        // row before it, and that trailing unannounced row has not happened.
        List<DynamicArtGapTransition> armFrame =
                TraceRunDynamicArtGapJournal.rowsCountedBackFromAdmission(
                        List.of(staleEdge(frozenRow, 5839)),
                        frozenRow, 5839, frozenRow - 1);

        assertEquals(frozenRow - 1, fused.get(0).edge().movieLogicalFrame(),
                "an edge one unannounced row before a fused admission belongs "
                        + "to the row before the frozen stamp");
        assertEquals(fused.get(0).edge().movieLogicalFrame(),
                armFrame.get(0).edge().movieLogicalFrame(),
                "the recovered row is a property of the movie, not of the "
                        + "admission phase");
    }

    /**
     * The reference is load-bearing in both directions: a one-row error either
     * way moves every recovered gap edge by that much, which reads as a
     * physics divergence rather than as a stamping fault.
     */
    @Test
    void aShiftedLastRunRowShiftsEveryRecoveredRowByTheSameAmount() {
        int frozenRow = 9701;
        for (int shift : new int[] {-1, 1}) {
            List<DynamicArtGapTransition> shifted =
                    TraceRunDynamicArtGapJournal.rowsCountedBackFromAdmission(
                            List.of(staleEdge(frozenRow, 5839)),
                            frozenRow, 5840, frozenRow + shift);
            assertEquals(frozenRow - 1 + shift,
                    shifted.get(0).edge().movieLogicalFrame(),
                    "recovered rows must track the last run row exactly, "
                            + "shift " + shift);
        }
    }

    /**
     * A live stamp — one that is not the admission's own row — was taken while
     * the cursor was still announcing, so it keeps its own row whatever the
     * last run row was.
     */
    @Test
    void aLiveGapStampIgnoresTheLastRunRow() {
        List<DynamicArtGapTransition> rowed =
                TraceRunDynamicArtGapJournal.rowsCountedBackFromAdmission(
                        List.of(staleEdge(9600, 5000)), 9701, 5840, 9701);

        assertEquals(9600, rowed.get(0).edge().movieLogicalFrame());
    }

    private static DynamicArtGapTransition staleEdge(
            int movieLogicalFrame, int unannouncedRowsAtEmit) {
        return new DynamicArtGapTransition(
                new DynamicArtGapTransition.GapEdge(
                        1L, 2L, "submitted", "sonic", 0, movieLogicalFrame, 0,
                        unannouncedRowsAtEmit, List.of()),
                List.of(), List.of());
    }

    private static TraceRunManifest manifest() {
        TraceRunManifest.Segment source = new TraceRunManifest.Segment(
                "source", "level", "gameplay_unlock", 100, 10,
                0, 1, null, null, List.of(), null);
        TraceRunManifest.Segment destination = new TraceRunManifest.Segment(
                "destination", "level", "gameplay_unlock", 120, 10,
                0, 1, null, null, List.of(),
                DynamicArtTransfer.ledgerHash(List.of()));
        return new TraceRunManifest(
                "s2", "run", "movie.bk2", "crc",
                List.of(source, destination), List.of(), List.of(),
                TraceRunManifest.ExpectedMovieEndMode.UNSPECIFIED);
    }

    private static TraceRunManifest terminalManifest() {
        TraceRunManifest.Segment source = new TraceRunManifest.Segment(
                "source", "level", "gameplay_unlock", 100, 10,
                0, 1, null, null, List.of(), null);
        return new TraceRunManifest(
                "s1", "terminal", "movie.bk2", "crc",
                List.of(source), List.of(), List.of(),
                TraceRunManifest.ExpectedMovieEndMode.LEVEL);
    }
}
