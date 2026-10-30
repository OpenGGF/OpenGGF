package com.openggf.tools;

import com.openggf.game.GameServices;
import com.openggf.game.sonic1.Sonic1ZoneFeatureProvider;
import com.openggf.sprites.ghost.GhostTraceRenderer;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.trace.TraceFixtures;
import com.openggf.trace.TraceFrame;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.time.Duration;
import java.lang.management.ManagementFactory;
import java.util.List;

import static com.openggf.tools.TestTraceGhostPresentationCadence.*;
import static org.junit.jupiter.api.Assertions.*;

/** Historical pictures must use the private presentation owner observed at admission. */
class TestTraceGhostHistoricalContext {
    @Test
    @RequiresRom(SonicGame.SONIC_1)
    void seeksRestoreObservedRollWalkHistoryAcrossProductionTunnelChanges() throws Exception {
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off", null, null, null);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(rom("s1"), 3, 0, settings);
            var features = (Sonic1ZoneFeatureProvider) GameServices.level().getZoneFeatureProvider();
            assertNotNull(features.getWaterEvents());
            var rows = new ArrayList<TraceFrame>();
            for (int i = 0; i < 32; i++) rows.add(rollingRow(i, session.player(), i < 3));
            var trace = TraceFixtures.trace(TraceFixtures.metadata("s1", 1, 0), rows);
            var renderer = new GhostTraceRenderer();
            var comparator = comparator(trace, renderer);
            var observed = new ArrayList<List<Object>>();
            for (int i = 0; i < rows.size(); i++) {
                if (i == 12) tunnel(session, features, true);
                if (i == 16) tunnel(session, features, false);
                comparator.afterFrameAdvanced(input(i), false);
                observed.add(pose(ghost(renderer, "main:sonic")));
            }
            assertNotEquals(observed.get(1).getFirst(), observed.get(8).getFirst(),
                    "the actual script must change Roll to Walk before entering the tunnel");
            tunnel(session, features, true);
            var realBefore = session.player().captureRewindState(false);
            for (int count : new int[]{10, 3, 14, 1, 32, 17, 0}) {
                comparator.seekForRewind(count);
                assertEquals(observed.get(Math.max(0, count - 1)), pose(ghost(renderer, "main:sonic")),
                        "seeking must not reinterpret old animation under the current tunnel flag: " + count);
            }
            comparator.seekForRewind(3);
            comparator.afterFrameAdvanced(input(3), false);
            var branch = pose(ghost(renderer, "main:sonic"));
            assertEquals(observed.get(1).getFirst(), branch.getFirst(),
                    "new forward work reads the actual current tunnel owner and retains Roll");
            assertNotEquals(observed.get(3), branch);
            comparator.seekForRewind(10);
            assertFalse(renderer.isVisualStateAvailable(), "resumed work invalidates the old future");
            comparator.seekForRewind(4);
            assertEquals(branch, pose(ghost(renderer, "main:sonic")));
            assertEquals(realBefore, session.player().captureRewindState(false),
                    "private ghost restore must not write the registered playable");
        }
    }

    @Test
    @RequiresRom(SonicGame.SONIC_2)
    void unobservedHistoryAndReplacementNeverBorrowAnotherPresentationLifetime() throws Exception {
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "tails", "off", null, null, null);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(rom("s2"), 0, 0, settings);
            var rows = java.util.stream.IntStream.range(0, 24).mapToObj(i ->
                    withSidekick(row(i, session.player()), i, i, sideState(session.player(), i < 2, true))).toList();
            var trace = TraceFixtures.trace(TraceFixtures.metadata("s2", 0, 0), rows);
            var renderer = new GhostTraceRenderer();
            var comparator = new com.openggf.trace.live.LiveTraceComparator(trace,
                    com.openggf.trace.ToleranceConfig.DEFAULT, 8, () -> null);
            comparator.setVisualRowObserver(renderer::synchronizeVisualRows);
            assertFalse(renderer.isVisualStateAvailable(), "a late bind has no historical context");
            comparator.afterFrameAdvanced(input(8), false);
            assertFalse(renderer.isVisualStateAvailable(), "a missing predecessor cannot seed a script");
            assertEquals(0, slotCount(renderer));
            comparator.seekForRewind(0);
            assertTrue(renderer.isVisualStateAvailable(), "the actual start boundary can establish a new lifetime");
            comparator.afterFrameAdvanced(input(0), false);
            for (int i = 1; i < 12; i++) comparator.afterFrameAdvanced(input(i), false);
            var expected = pose(ghost(renderer, "main:sonic"));
            var old = ghost(renderer, "main:sonic");
            comparator.seekForRewind(20);
            assertFalse(renderer.isVisualStateAvailable());
            var terrain = session.render(false);
            assertArrayEquals(terrain.pixels(), picture(session, renderer).pixels(),
                    "unknown history must draw no stale or fabricated ghost");
            comparator.seekForRewind(12);
            assertEquals(expected, pose(ghost(renderer, "main:sonic")));

            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.node.ObjectNode metadata = mapper.valueToTree(trace.metadata());
            metadata.put("main_character", "tails");
            metadata.putArray("characters").add("tails").add("sonic");
            metadata.putArray("sidekicks").add("sonic");
            var replacement = TraceFixtures.trace(mapper.treeToValue(metadata,
                    com.openggf.trace.TraceMetadata.class), rows);
            renderer.synchronizeVisualRows(replacement, 0, true);
            assertEquals(0, slotCount(renderer));
            renderer.synchronizeVisualRows(replacement, 1, false);
            assertInstanceOf(com.openggf.sprites.playable.Tails.class, ghost(renderer, "main:tails"));
            assertInstanceOf(com.openggf.sprites.playable.Sonic.class, ghost(renderer, "sidekick0:sonic"));
            assertNotSame(old, ghost(renderer, "main:tails"));
            renderer.synchronizeVisualRows(trace, 12, true);
            assertFalse(renderer.isVisualStateAvailable(), "switching fixtures retires all old checkpoints");
            assertEquals(0, slotCount(renderer));
            renderer.reset();
            assertFalse(renderer.isVisualStateAvailable());
        }
    }

    @Test
    @RequiresRom(SonicGame.SONIC_2)
    void heldBackwardSteppingRestoresTwoOwnersWithoutPrefixWork() throws Exception {
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "tails", "off", null, null, null);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(rom("s2"), 0, 0, settings);
            // Two different prefix sizes witness that seeking performs no Animate
            // replay. The generous deadline catches an accidental quadratic rebuild;
            // timings are diagnostic, not a calibrated performance qualification.
            for (int count : new int[]{2_000, 20_000}) {
                var trace = TraceFixtures.trace(TraceFixtures.metadata("s2", 0, 0),
                        java.util.stream.IntStream.range(0, count).mapToObj(i ->
                                withSidekick(rollingRow(i, session.player(), i < 3), i, i,
                                        sideState(session.player(), i < 5, true))).toList());
                var renderer = new GhostTraceRenderer();
                var comparator = comparator(trace, renderer);
                var allocations = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
                long allocatedBefore = allocations.getCurrentThreadAllocatedBytes();
                List<Object> firstMain = null, firstTail = null;
                for (int i = 0; i < count; i++) {
                    comparator.afterFrameAdvanced(input(i), false);
                    if (i == 0) {
                        firstMain = pose(ghost(renderer, "main:sonic"));
                        firstTail = pose(ghost(renderer, "sidekick0:tails"));
                    }
                }
                long allocated = allocations.getCurrentThreadAllocatedBytes() - allocatedBefore;
                var main = ghost(renderer, "main:sonic");
                var tail = ghost(renderer, "sidekick0:tails");
                var finalMain = pose(main);
                var finalTail = pose(tail);
                var samples = new long[count];
                long started = System.nanoTime();
                assertTimeout(Duration.ofSeconds(5), () -> {
                    for (int cursor = count; cursor > 0; cursor--) {
                        long start = System.nanoTime();
                        comparator.seekForRewind(cursor);
                        samples[cursor - 1] = System.nanoTime() - start;
                    }
                });
                long elapsed = System.nanoTime() - started;
                assertSame(main, ghost(renderer, "main:sonic"), "seeks restore the existing private owner");
                assertSame(tail, ghost(renderer, "sidekick0:tails"));
                assertEquals(firstMain, pose(main));
                assertEquals(firstTail, pose(tail));
                comparator.seekForRewind(count);
                assertEquals(finalMain, pose(main));
                assertEquals(finalTail, pose(tail));
                java.util.Arrays.sort(samples);
                System.out.printf("Ghost backward %,d rows/two owners: %.3f ms total, %.3f us p95, "
                                + "%.3f us max; forward allocation %.1f bytes/row (not retained heap)%n",
                        count, elapsed / 1e6, samples[(count * 95) / 100] / 1e3,
                        samples[count - 1] / 1e3, (double) allocated / count);
                renderer.reset();
            }
        }
    }

    private static void tunnel(GameplayCaptureSession session, Sonic1ZoneFeatureProvider features,
                               boolean active) {
        // Exercise the production semantic owner, including entering and leaving its ROM region.
        session.player().setCentreX((short) (active ? 0xB00 : 0x900));
        session.player().setCentreY((short) 0x340);
        features.getWaterEvents().updateWindTunnels(session.player(), true);
        assertEquals(active, features.isWaterTunnelActive());
    }

    private static TraceFrame rollingRow(int index, com.openggf.sprites.playable.AbstractPlayableSprite real,
                                         boolean rolling) {
        var row = row(index, real);
        return new TraceFrame(row.frame(), row.input(), row.x(), row.y(), row.xSpeed(), row.ySpeed(),
                row.gSpeed(), row.angle(), row.air(), rolling, row.groundMode(), 0, 0, 2, -1, -1, -1,
                0, index, -1, index, 0);
    }
}
