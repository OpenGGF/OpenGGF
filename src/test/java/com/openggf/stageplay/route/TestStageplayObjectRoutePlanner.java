package com.openggf.stageplay.route;

import com.openggf.level.objects.StageplayObjectDirection.*;
import com.openggf.physics.RouteTerrainProbe;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.runtime.StageplayObjectMotion;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntUnaryOperator;

import static com.openggf.stageplay.StageplayDocument.*;
import static com.openggf.stageplay.route.StageplayRouteResult.Status.*;
import static org.junit.jupiter.api.Assertions.*;

class TestStageplayObjectRoutePlanner {
    private static final StageplayRouteFootprint FOOT = new StageplayRouteFootprint(2, 20, 20, 0);
    private static StageplayDocument scene() {
        return StageplayDocument.empty("s2", 0, 0, 0, 0, 320, 224).withDurationFrames(180).withObjectActors(List.of(
                new ObjectActor("walker", "Walker", "s2", 0, 0, 0x9F, 0, -1, List.of(
                        new ObjectKey(0, 20, 80, 0, -1, false, true, Interpolation.HOLD, ObjectMode.PUPPET)), List.of())));
    }
    private static StageplayRouteRequest request(double x, double y, double speed) {
        return new StageplayRouteRequest("walker", 0, x, y, 150, 1024, 2000, 10000, 1, FOOT, speed);
    }
    private static StageplayRouteResult plan(StageplayDocument document, StageplayRouteRequest request, RouteTerrainProbe probe) {
        return StageplayObjectRoutePlanner.plan(document, request, "exact-input-fingerprint", probe, () -> false);
    }
    private static final class Terrain implements RouteTerrainProbe {
        IntUnaryOperator heights = x -> 100;
        int gapLeft = Integer.MAX_VALUE, gapRight = Integer.MIN_VALUE;
        int wall = Integer.MAX_VALUE, ceiling = Integer.MIN_VALUE;
        boolean dynamic;
        int expectedPath = 0, queries;
        public Floor floor(int x, int y, int path) {
            assertEquals(expectedPath, path); queries++;
            return x >= gapLeft && x <= gapRight ? Floor.missing() : new Floor(true, heights.applyAsInt(x), 0);
        }
        public boolean clear(int left, int top, int right, int bottom, int path) {
            assertEquals(expectedPath, path); queries++;
            return !(left <= wall && right >= wall) && top >= ceiling;
        }
        public boolean dynamicCollision() { return dynamic; }
    }

    @Test void fractionalMovementUsesPlaybackKernelAndStopsOnTheFollowingFrameWithoutAddingKeys() {
        var original = scene(); var result = plan(original, request(60, 80, .7), new Terrain());
        assertEquals(VISUAL_TERRAIN, result.status(), result.message()); assertFalse(result.nativeVerified());
        assertTrue(result.message().contains("VISUAL_TERRAIN_ROUTE"));
        assertEquals(2, result.objectCommands().size()); assertTrue(result.commands().isEmpty());
        assertEquals(0, result.objectCommands().getFirst().frame());
        assertEquals(58, result.objectCommands().getLast().frame());
        assertEquals(ObjectCommandKind.STOP, result.objectCommands().getLast().kind());
        assertEquals(original.objectActors().getFirst().keys(), result.candidate().objectActors().getFirst().keys());
        assertTrue(original.objectActors().getFirst().commands().isEmpty(), "Planning cannot mutate source or active state");
        double x = 20, y = 80;
        for (var point : result.path()) {
            double[] moved = StageplayObjectMotion.move(x, y, 60, 80, .7); x = moved[0]; y = moved[1];
            assertEquals(x, point.x()); assertEquals(y, point.y()); assertTrue(point.grounded());
        }
        assertEquals(60, x); assertEquals(58, result.simulatedTicks());
        assertEquals(result, plan(original, request(60, 80, .7), new Terrain()));
    }

    @Test void slopedFloorsAndSecondaryPathAreQueriedForTheWholeFootprint() {
        var terrain = new Terrain(); terrain.heights = x -> 100 + Math.floorDiv(x - 20, 2); terrain.expectedPath = 1;
        var actor = new ObjectActor("walker", "Walker", "s2", 0, 0, 1, 0, -1, List.of(
                new ObjectKey(0, 20, 79, 0, -1, false, true, Interpolation.HOLD, ObjectMode.PUPPET)), List.of());
        var document = scene().withObjectActors(List.of(actor));
        var request = new StageplayRouteRequest("walker", 0, 40, 89, 150, 1024, 2000, 10000, 1,
                new StageplayRouteFootprint(2, 20, 20, 1), .375);
        var result = plan(document, request, terrain);
        assertEquals(VISUAL_TERRAIN, result.status(), result.message());
        assertEquals(40, result.path().getLast().x()); assertEquals(89, result.path().getLast().y());
        assertTrue(result.objectCommands().size() > 2, "Slope changes retain floor-following waypoints");
        assertTrue(terrain.queries > result.path().size());
    }

    @Test void missingSupportSteepStepsWallsHeadroomAndAirborneAnchorsFailClearly() {
        var gap = new Terrain(); gap.gapLeft = 35; gap.gapRight = 40;
        var wall = new Terrain(); wall.wall = 38;
        var roof = new Terrain(); roof.ceiling = 81;
        var steep = new Terrain(); steep.heights = x -> x < 35 ? 100 : 90;
        for (var terrain : List.of(gap, wall, roof, steep)) {
            var result = plan(scene(), request(60, 80, 1), terrain);
            assertEquals(SEARCH_EXHAUSTED, result.status(), result.message());
            assertNull(result.candidate()); assertTrue(result.objectCommands().isEmpty());
        }
        var unsupported = new Terrain(); unsupported.dynamic = true;
        assertEquals(UNSUPPORTED, plan(scene(), request(60, 80, 1), unsupported).status());
        var actor = scene().objectActors().getFirst();
        var airborne = new ObjectActor(actor.id(), actor.name(), actor.gameId(), 0, 0, actor.objectId(), 0, -1,
                List.of(new ObjectKey(0, 20, 10, 0, -1, false, true, Interpolation.HOLD, ObjectMode.PUPPET)), List.of());
        assertTrue(plan(scene().withObjectActors(List.of(airborne)), request(60, 80, 1), new Terrain()).message().contains("START_NOT_GROUNDED"));
    }

    @Test void keysCastClearsAndBudgetsBoundTheWholeReplacementWindow() {
        var actor = scene().objectActors().getFirst();
        var keyed = new ObjectActor(actor.id(), actor.name(), "s2", 0, 0, 1, 0, -1,
                List.of(actor.keys().getFirst(), new ObjectKey(10, 20, 80, 0, -1, false, true, Interpolation.HOLD, ObjectMode.PUPPET)), List.of());
        assertEquals(UNSUPPORTED, plan(scene().withObjectActors(List.of(keyed)), request(60, 80, 1), new Terrain()).status());
        var commanded = new ObjectActor(actor.id(), actor.name(), "s2", 0, 0, 1, 0, -1, actor.keys(),
                List.of(new ObjectCommand("existing", 0, ObjectCommandKind.STOP, 0, 0, 0)));
        var replaced = plan(scene().withObjectActors(List.of(commanded)), request(60, 80, 1), new Terrain());
        assertEquals(VISUAL_TERRAIN, replaced.status(), replaced.message());
        assertFalse(replaced.candidate().objectActors().getFirst().commands().stream().anyMatch(c -> c.id().equals("existing")));
        var cleared = scene().withWorldCommands(List.of(new WorldCommand("clear", 10, WorldCommandKind.CLEAR_OBJECTS, "", ObjectTarget.none())));
        assertEquals(UNSUPPORTED, plan(cleared, request(60, 80, 1), new Terrain()).status());
        var tiny = new StageplayRouteRequest("walker", 0, 60, 80, 150, 2, 2000, 10000, 1, FOOT, 1);
        assertEquals(BUDGET_EXCEEDED, plan(scene(), tiny, new Terrain()).status());
        assertEquals(CANCELLED, StageplayObjectRoutePlanner.plan(scene(), request(60, 80, 1), "fp", new Terrain(), () -> true).status());
        var checks = new AtomicInteger();
        assertEquals(CANCELLED, StageplayObjectRoutePlanner.plan(scene(), request(60, 80, 1), "fp", new Terrain(), () -> checks.incrementAndGet() > 12).status());
    }

    @Test void routeStartsFromExistingVisualMotionAtItsInclusiveCommandFrame() {
        var a = scene().objectActors().getFirst();
        var actor = new ObjectActor(a.id(), a.name(), a.gameId(), 0, 0, a.objectId(), 0, -1, a.keys(),
                List.of(new ObjectCommand("earlier", 0, ObjectCommandKind.MOVE_TO, 30, 80, .5)));
        var request = new StageplayRouteRequest("walker", 10, 60, 80, 150, 1024, 2000, 10000, 1, FOOT, .5);
        var result = plan(scene().withObjectActors(List.of(actor)), request, new Terrain());
        assertEquals(VISUAL_TERRAIN, result.status(), result.message());
        assertEquals(10, result.path().getFirst().frame()); assertEquals(25.5, result.path().getFirst().x());
        assertEquals("earlier", result.candidate().objectActors().getFirst().commands().getFirst().id());
    }

    @Test void replacementPreservesCommandsOutsideTheWindowAndColdSamplesUseTheSameFootprint() {
        var actor = scene().objectActors().getFirst();
        var after = new ObjectCommand("after", 160, ObjectCommandKind.STOP, 0, 0, 0);
        var replacement = new ObjectActor(actor.id(), actor.name(), actor.gameId(), 0, 0, actor.objectId(), 0, -1,
                actor.keys(), List.of(new ObjectCommand("inside", 70, ObjectCommandKind.MOVE_TO, 90, 80, 1), after));
        var result = plan(scene().withObjectActors(List.of(replacement)), request(60, 80, .7), new Terrain());
        assertEquals(VISUAL_TERRAIN, result.status(), result.message());
        assertEquals(after, result.candidate().objectActors().getFirst().commands().getLast());
        assertEquals(result.objectCommands(), result.candidate().objectActors().getFirst().commands().stream()
                .filter(c -> c.frame() < 150).toList());
        var terrain = new Terrain();
        for (var point : result.path()) assertEquals("", StageplayObjectRoutePlanner.validateSample(terrain, FOOT, point.x(), point.y()));
        assertFalse(StageplayObjectRoutePlanner.validateSample(terrain, FOOT, 20, 10).isEmpty());
        terrain.dynamic = true;
        assertFalse(StageplayObjectRoutePlanner.validateSample(terrain, FOOT, 20, 80).isEmpty());
    }

    @Test void multipartVisualEnvelopeIsConservativeAndCannotFitThroughANarrowPassage() {
        var tile = new VisualTile(0, 0, false, false, false, -8, -8, 8, 8, false, 0, 0, 8);
        var distant = new VisualTile(0, 0, false, false, false, 40, -8, 8, 8, false, 0, 0, 8);
        var template = new VisualTemplate(1, 0, 0, List.of(new VisualPart("root", 1, false, 0, 0,
                List.of(new VisualPose(0, List.of(tile, distant), List.of())), List.of())),
                List.of(new PatternPixels(0, 0, 0, 0)), List.of(new PaletteColors(java.util.Collections.nCopies(16, 0))), List.of());
        var footprint = StageplayObjectRoutePlanner.visualFootprint(template, 0);
        assertEquals(47, footprint.halfWidth()); assertEquals(-1, footprint.footOffsetY());
        var terrain = new Terrain(); terrain.wall = 38;
        var request = new StageplayRouteRequest("walker", 0, 60, 80, 150, 1024, 2000, 10000, 1,
                new StageplayRouteFootprint(47, 20, 20, 0), .5);
        assertEquals(SEARCH_EXHAUSTED, plan(scene(), request, terrain).status());
    }
}
