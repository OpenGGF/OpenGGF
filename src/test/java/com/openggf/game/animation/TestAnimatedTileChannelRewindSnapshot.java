package com.openggf.game.animation;

import com.openggf.game.rewind.snapshot.AnimatedTileChannelSnapshot;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TestAnimatedTileChannelRewindSnapshot {

    @Test
    void roundTripPreservesLastPhase() {
        AnimatedTileChannelGraph g = new AnimatedTileChannelGraph();
        g.recordPhase("ch-1", 5);
        g.recordPhase("ch-2", 12);
        AnimatedTileChannelSnapshot snap = g.capture();
        g.recordPhase("ch-1", 0);
        g.restore(snap);
        assertEquals(5, g.getLastPhase("ch-1"));
        assertEquals(12, g.getLastPhase("ch-2"));
    }

    @Test
    void keyIsAnimatedTileChannels() {
        assertEquals("animated-tile-channels", new AnimatedTileChannelGraph().key());
    }

    @Test
    void captureIsImmutable() {
        AnimatedTileChannelGraph g = new AnimatedTileChannelGraph();
        g.recordPhase("ch-1", 3);
        AnimatedTileChannelSnapshot snap = g.capture();
        // Map.copyOf in the record constructor should throw on mutation
        assertThrows(UnsupportedOperationException.class,
                () -> snap.lastPhaseByChannel().put("ch-1", 99));
    }

    @Test
    void emptyGraphRoundTrips() {
        AnimatedTileChannelGraph g = new AnimatedTileChannelGraph();
        AnimatedTileChannelSnapshot snap = g.capture();
        g.recordPhase("ch-x", 7);
        g.restore(snap);
        assertEquals(-1, g.getLastPhase("ch-x"));
    }

    @Test
    void restoreReconcilesExternalSnapshotAgainstCurrentInstalledOrder() {
        AnimatedTileChannelGraph graph = new AnimatedTileChannelGraph();
        graph.install(List.of(channel("B"), channel("A"), channel("D")));
        graph.recordPhase("B", 2);
        graph.recordPhase("old-diagnostic", 8);
        LinkedHashMap<String, Integer> external = new LinkedHashMap<>();
        external.put("A", 1);
        external.put("C", 3);

        graph.restore(new AnimatedTileChannelSnapshot(external));

        assertEquals(-1, graph.getLastPhase("B"));
        assertEquals(1, graph.getLastPhase("A"));
        assertEquals(-1, graph.getLastPhase("D"));
        assertEquals(3, graph.getLastPhase("C"));
        assertEquals(-1, graph.getLastPhase("old-diagnostic"));
        assertEquals(Map.of("A", 1, "C", 3), graph.capture().lastPhaseByChannel());
        assertEquals(2, graph.recordedPhaseCount());
    }

    @Test
    void emptyRestoreKeepsInstalledChannelsButClearsAllPhaseState() {
        AnimatedTileChannelGraph graph = new AnimatedTileChannelGraph();
        graph.install(List.of(channel("A"), channel("B")));
        graph.recordPhase("A", 1);
        graph.recordPhase("diagnostic", 2);

        graph.restore(new AnimatedTileChannelSnapshot(Map.of()));

        assertEquals(List.of("A", "B"), graph.channels().stream()
                .map(AnimatedTileChannel::channelId).toList());
        assertEquals(0, graph.recordedPhaseCount());
    }

    @Test
    void installAndClearEraseInstalledAndDiagnosticPhases() {
        AnimatedTileChannelGraph graph = new AnimatedTileChannelGraph();
        graph.install(List.of(channel("A")));
        graph.recordPhase("A", 1);
        graph.recordPhase("diagnostic", 2);

        graph.install(List.of(channel("B")));
        assertEquals(0, graph.recordedPhaseCount());
        graph.recordPhase("B", 3);
        graph.recordPhase("diagnostic-2", 4);
        graph.clear();

        assertTrue(graph.channels().isEmpty());
        assertEquals(0, graph.recordedPhaseCount());
    }

    @Test
    void nullDiagnosticPhaseIsLiveReadableButCaptureUsesExistingMapCopyOfRejection() {
        AnimatedTileChannelGraph graph = new AnimatedTileChannelGraph();
        graph.recordPhase(null, 17);

        assertEquals(17, graph.getLastPhase(null));
        assertEquals(1, graph.recordedPhaseCount());
        assertThrows(NullPointerException.class, graph::capture);
    }

    private static AnimatedTileChannel channel(String id) {
        return new AnimatedTileChannel(id, () -> true, ctx -> 0,
                DestinationPlan.single(0), AnimatedTileCachePolicy.ALWAYS, ctx -> { });
    }
}
