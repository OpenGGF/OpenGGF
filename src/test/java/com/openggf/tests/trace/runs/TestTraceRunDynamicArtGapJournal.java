package com.openggf.tests.trace.runs;

import com.openggf.game.resources.DynamicArtLifecycleService;
import com.openggf.trace.DynamicArtTransfer;
import com.openggf.trace.TraceRunManifest;
import com.openggf.trace.replay.runs.TraceRunDynamicArtGapJournal;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    private static TraceRunManifest manifest() {
        TraceRunManifest.Segment source = new TraceRunManifest.Segment(
                "source", "level", "gameplay_unlock", 100, 10,
                0, 1, null, null, List.of(), null);
        TraceRunManifest.Segment destination = new TraceRunManifest.Segment(
                "destination", "level", "gameplay_unlock", 120, 10,
                0, 1, null, null, List.of(),
                DynamicArtTransfer.ledgerHash(List.of()));
        return new TraceRunManifest(
                1, "s2", "run", "movie.bk2", "crc", "lua",
                List.of(source, destination), List.of(), List.of(),
                TraceRunManifest.ExpectedMovieEndMode.UNSPECIFIED);
    }
}
