package com.openggf.tests.trace.runs;

import com.openggf.game.resources.DynamicArtLifecycleService;
import com.openggf.trace.DynamicArtTransfer;
import com.openggf.trace.TraceRunManifest;
import com.openggf.trace.replay.runs.TraceRunDynamicArtGapJournal;
import org.junit.jupiter.api.Test;

import java.util.List;

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

        assertFalse(journal.destinationOpened(1).hasError());
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
