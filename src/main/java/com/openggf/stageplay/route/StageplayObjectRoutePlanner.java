package com.openggf.stageplay.route;

import static com.openggf.stageplay.StageplayDocument.*;
import static com.openggf.stageplay.route.StageplayRouteResult.Status.*;

import com.openggf.physics.RouteTerrainProbe;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.runtime.StageplayObjectMotion;

import java.util.*;
import java.util.function.BooleanSupplier;

/**
 * Bounded floor-following pathfinding for visual Puppet objects: slopes and steps up to eight
 * pixels, without detours, jumps or gap crossing. No AI, collision response, native controller,
 * teleport or terrain mutation is executed. These are authoring limits, not native physics.
 */
public final class StageplayObjectRoutePlanner {
    // Authoring clearance for a small walking step. Native object routines remain suspended.
    private static final int MAX_STEP_HEIGHT = 8;

    private StageplayObjectRoutePlanner() {}

    private record Point(double x, double y) {}

    private static final class Stop extends RuntimeException {
        final StageplayRouteResult.Status status;

        Stop(StageplayRouteResult.Status status, String message) {
            super(message);
            this.status = status;
        }
    }

    private static final class Budget {
        final StageplayRouteRequest request;
        final BooleanSupplier cancelled;
        final long deadline;
        int nodes, ticks;

        Budget(StageplayRouteRequest request, BooleanSupplier cancelled) {
            this.request = request;
            this.cancelled = cancelled;
            deadline = System.nanoTime() + request.maxMillis() * 1_000_000L;
        }

        void check() {
            if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted())
                throw new Stop(CANCELLED, "Object route cancelled");
            if (System.nanoTime() >= deadline)
                throw new Stop(BUDGET_EXCEEDED, "Object route exceeded its time budget");
        }

        void node() {
            check();
            if (++nodes > request.maxNodes())
                throw new Stop(BUDGET_EXCEEDED, "Object floor graph exceeded its node budget");
        }

        void tick() {
            check();
            if (++ticks > request.maxTicks())
                throw new Stop(BUDGET_EXCEEDED, "Object route exceeded its movement-tick budget");
        }
    }

    public static StageplayRouteResult plan(
            StageplayDocument document,
            StageplayRouteRequest request,
            String fingerprint,
            RouteTerrainProbe terrain,
            BooleanSupplier cancelled) {
        Objects.requireNonNull(document);
        Objects.requireNonNull(request);
        Objects.requireNonNull(fingerprint);
        Objects.requireNonNull(terrain);
        Objects.requireNonNull(cancelled);
        var budget = new Budget(request, cancelled);
        var path = new ArrayList<StageplayRoutePoint>();
        StageplayRoutePoint initialPoint = null;
        try {
            budget.check();
            if (request.footprint() == null)
                throw new Stop(
                        UNSUPPORTED,
                        "A visual object route needs an explicit footprint and foot offset");
            if (terrain.dynamicCollision())
                throw new Stop(
                        UNSUPPORTED,
                        "Moving background-plane collision has no fixed visual route graph");
            ObjectActor actor =
                    document.objectActors().stream()
                            .filter(a -> a.id().equals(request.actorId()))
                            .findFirst()
                            .orElseThrow(
                                    () -> new Stop(UNSUPPORTED, "Select a Puppet object actor"));
            if (request.startFrame() >= document.durationFrames())
                throw new Stop(UNSUPPORTED, "Route starts outside the scene");
            int shotStart = 0, shotEnd = document.durationFrames();
            for (var change : document.sceneChanges()) {
                if (change.frame() <= request.startFrame()) {
                    shotStart = change.frame();
                } else {
                    shotEnd = change.frame();
                    break;
                }
            }
            ObjectKey active = actor.keys().getFirst();
            int end = request.startFrame() + request.maxFrames();
            if (end > shotEnd || end > document.durationFrames())
                throw new Stop(UNSUPPORTED, "Route replacement window crosses a shot or scene boundary");
            for (var key : actor.keys()) {
                if (key.frame() <= request.startFrame()) active = key;
                else {
                    if (key.frame() < end)
                        throw new Stop(UNSUPPORTED, "Route replacement window crosses an object key, visibility or mode boundary");
                    break;
                }
            }
            if (!active.visible() || active.mode() != ObjectMode.PUPPET)
                throw new Stop(
                        UNSUPPORTED, "Route starts on a continuously visible Puppet object key");
            for (var command : document.worldCommands())
                if (command.frame() >= shotStart && command.frame() <= request.startFrame()
                        // beforeTick applies an entry clear before the role's first admission.
                        && !(command.frame() == shotStart
                                && command.kind() == WorldCommandKind.CLEAR_OBJECTS))
                    throw new Stop(
                            UNSUPPORTED,
                            "A preceding cast-clear/removal command makes the selected object's"
                                + " lifetime ambiguous");
            for (var command : document.worldCommands())
                if (command.frame() > request.startFrame() && command.frame() < end)
                    throw new Stop(UNSUPPORTED, "Route replacement window crosses a cast-clear/removal boundary");
            Point start = startPosition(document, actor, request.startFrame(), shotStart, budget);
            initialPoint = new StageplayRoutePoint(request.startFrame(), start.x(), start.y(), true);
            var footprint = request.footprint();
            Point supported = support(start.x(), start.y(), footprint, terrain);
            if (Math.abs(supported.y() - start.y()) > 1 || !clear(start, footprint, terrain))
                throw new Stop(
                        SEARCH_EXHAUSTED,
                        "START_NOT_GROUNDED: the visual footprint must already rest on a clear"
                            + " floor; no initial snap is performed");
            Point target = support(request.targetX(), request.targetY(), footprint, terrain);
            if (Math.abs(target.y() - request.targetY()) > 1)
                throw new Stop(
                        SEARCH_EXHAUSTED,
                        "TARGET_NOT_GROUNDED: target Y does not match the visual foot offset");
            var graph = new ArrayList<Point>();
            graph.add(start);
            budget.node();
            Point previous = start;
            double remaining = request.targetX() - start.x();
            Stop graphStop = null;
            try {
                while (Math.abs(remaining) > 1e-9) {
                    budget.node();
                    double x =
                            previous.x() + Math.copySign(Math.min(1, Math.abs(remaining)), remaining);
                    Point point = support(x, previous.y(), footprint, terrain);
                    double rise = previous.y() - point.y();
                    if (Math.abs(rise) > Math.abs(x - previous.x()) + 1e-9) {
                        if (Math.abs(rise) > MAX_STEP_HEIGHT)
                            throw new Stop(
                                    SEARCH_EXHAUSTED,
                                    "STEP_TOO_HIGH: walking steps are limited to " + MAX_STEP_HEIGHT
                                            + " pixels; no jump or detour is attempted");
                        // Lift before crossing a riser, or cross a descending edge before lowering.
                        // Explicit MOVE_TO corners keep playback on the same collision-checked path;
                        // interpolating diagonally through the riser would cut through solid terrain.
                        Point corner = rise > 0
                                ? new Point(previous.x(), point.y())
                                : new Point(point.x(), previous.y());
                        if (!clear(corner, footprint, terrain))
                            throw new Stop(SEARCH_EXHAUSTED, "BLOCKED: insufficient step headroom");
                        budget.node();
                        graph.add(corner);
                    }
                    if (!clear(point, footprint, terrain))
                        throw new Stop(
                                SEARCH_EXHAUSTED,
                                "BLOCKED: wall or ceiling intersects the visual footprint");
                    graph.add(point);
                    previous = point;
                    remaining = request.targetX() - point.x();
                }
                if (Math.abs(previous.y() - target.y()) > 1)
                    throw new Stop(
                            SEARCH_EXHAUSTED,
                            "NO_CONTINUOUS_FLOOR: the target is on another floor branch");
                if (graph.size() == 1 && Math.abs(start.y() - target.y()) > 1e-9) graph.add(target);
            } catch (Stop stop) {
                // Simulate only the checked prefix so the editor can show where walking stopped.
                graphStop = stop;
            }
            var waypoints = compact(graph);
            var commands = new ArrayList<ObjectCommand>();

            var used = ids(document);
            int frame = request.startFrame(), ordinal = 0;
            Point current = start;

            for (int i = 1; i < waypoints.size(); i++) {
                Point goal = waypoints.get(i);
                if (current.equals(goal)) continue;
                commands.add(
                        new ObjectCommand(
                                nextId(used, request, ordinal++),
                                frame,
                                ObjectCommandKind.MOVE_TO,
                                goal.x(),
                                goal.y(),
                                request.objectSpeed()));
                while (!current.equals(goal)) {
                    budget.tick();
                    if (frame >= end - 1)
                        throw new Stop(
                                BUDGET_EXCEEDED,
                                "Route reaches a frame/key/shot/clear/command boundary before it"
                                    + " can stop");
                    double[] moved =
                            StageplayObjectMotion.move(
                                    current.x(),
                                    current.y(),
                                    goal.x(),
                                    goal.y(),
                                    request.objectSpeed());
                    Point next = new Point(moved[0], moved[1]);
                    sweep(current, next, footprint, terrain, budget);
                    current = next;
                    Point floor = support(current.x(), current.y(), footprint, terrain);
                    path.add(new StageplayRoutePoint(frame++, current.x(), current.y(),
                            Math.abs(floor.y() - current.y()) <= 1));
                }
            }
            if (graphStop != null) {
                if (path.isEmpty()) path.add(initialPoint);
                return StageplayRouteResult.failure(graphStop.status, graphStop.getMessage(), fingerprint,
                        request, budget.nodes, budget.ticks, path);
            }
            budget.check();
            if (frame >= end)
                throw new Stop(BUDGET_EXCEEDED, "No frame remains for the route's STOP command");
            commands.add(
                    new ObjectCommand(
                            nextId(used, request, ordinal),
                            frame,
                            ObjectCommandKind.STOP,
                            0,
                            0,
                            0));
            if (path.isEmpty())
                path.add(new StageplayRoutePoint(frame, current.x(), current.y(), true));
            var merged = new ArrayList<>(actor.commands().stream()
                    .filter(command -> command.frame() < request.startFrame() || command.frame() >= end).toList());
            merged.addAll(commands);
            merged.sort(Comparator.comparingInt(ObjectCommand::frame));
            var replacement =
                    new ObjectActor(
                            actor.id(),
                            actor.name(),
                            actor.gameId(),
                            actor.zone(),
                            actor.act(),
                            actor.objectId(),
                            actor.subtype(),
                            actor.layoutIndex(),
                            actor.keys(),
                            merged,
                            actor.sourceKind());
            var actors = new ArrayList<>(document.objectActors());
            actors.set(actors.indexOf(actor), replacement);
            StageplayDocument candidate = document.withObjectActors(actors);
            return new StageplayRouteResult(
                    VISUAL_TERRAIN,
                    "VISUAL_TERRAIN_ROUTE: slopes, steps up to " + MAX_STEP_HEIGHT
                        + " pixels and visual clearance checked; no gaps or detours;"
                        + " native AI, contacts and world effects remain suspended",
                    fingerprint,
                    request,
                    candidate,
                    List.of(),
                    commands,
                    path,
                    budget.nodes,
                    budget.ticks,
                    false);
        } catch (Stop stop) {
            if (path.isEmpty() && initialPoint != null) path.add(initialPoint);
            return StageplayRouteResult.failure(
                    stop.status,
                    stop.getMessage(),
                    fingerprint,
                    request,
                    budget.nodes,
                    budget.ticks, path);
        } catch (IllegalArgumentException failure) {
            return StageplayRouteResult.failure(
                    UNSUPPORTED,
                    failure.getMessage(),
                    fingerprint,
                    request,
                    budget.nodes,
                    budget.ticks, path);
        }
    }

    /** Rechecks a cold replay's exact visual position using the proposal's geometry owner. */
    public static String validateSample(RouteTerrainProbe terrain, StageplayRouteFootprint footprint,
                                        double x, double y) {
        Objects.requireNonNull(terrain);
        Objects.requireNonNull(footprint);
        if (!Double.isFinite(x) || !Double.isFinite(y)) return "Visual route position is not finite";
        if (terrain.dynamicCollision()) return "Active background-plane collision is not fixed foreground geometry";
        try {
            Point point = new Point(x, y);
            Point floor = support(x, y, footprint, terrain);
            if (!withinStepClearance(point, floor))
                return "Visual route leaves floor support or the eight-pixel walking-step clearance";
            if (!clear(point, footprint, terrain)) return "Visual route intersects a wall or ceiling";
            return "";
        } catch (Stop | IllegalArgumentException failure) {
            return failure.getMessage();
        }
    }

    /**
     * Conservative ROM-mapping envelope across captured parts/poses, not native collision radii.
     * Authors may supply an explicit footprint instead; very large composites exceed the probe
     * bound.
     */
    public static StageplayRouteFootprint visualFootprint(
            com.openggf.level.objects.StageplayObjectDirection.VisualTemplate template,
            int collisionPath) {
        double left = Double.POSITIVE_INFINITY, right = Double.NEGATIVE_INFINITY;
        double top = Double.POSITIVE_INFINITY, bottom = Double.NEGATIVE_INFINITY;
        for (var part : template.parts())
            for (var pose : part.poses())
                for (var tile : pose.tiles()) {
                    if (tile.rowStart() == tile.rowEnd()) continue;
                    left = Math.min(left, tile.x());
                    right = Math.max(right, tile.x() + tile.width() - 1);
                    top = Math.min(top, tile.y() + tile.rowStart());
                    bottom = Math.max(bottom, tile.y() + tile.rowEnd() - 1);
                }
        if (!Double.isFinite(left))
            throw new IllegalArgumentException("Object has no bounded visual footprint");
        return new StageplayRouteFootprint(
                Math.max(1, (int) Math.ceil(Math.max(Math.abs(left), Math.abs(right)))),
                Math.max(1, (int) Math.ceil(bottom - top)),
                (int) Math.ceil(bottom),
                collisionPath);
    }

    private static Point support(
            double x, double nearY, StageplayRouteFootprint f, RouteTerrainProbe terrain) {
        int feet = (int) Math.round(nearY + f.footOffsetY()), y = Integer.MAX_VALUE;
        for (int probe :
                new int[] {
                    (int) Math.round(x - f.halfWidth()),
                    (int) Math.round(x),
                    (int) Math.round(x + f.halfWidth())
                }) {
            var floor = terrain.floor(probe, feet, f.collisionPath());
            if (!floor.found())
                throw new Stop(
                        SEARCH_EXHAUSTED, "NO_FLOOR: the walking footprint cannot cross a gap");
            y = Math.min(y, floor.y());
        }
        return new Point(x, y - f.footOffsetY());
    }

    private static boolean withinStepClearance(Point point, Point floor) {
        double clearance = floor.y() - point.y();
        return clearance >= -1 && clearance <= MAX_STEP_HEIGHT;
    }

    private static boolean clear(Point p, StageplayRouteFootprint f, RouteTerrainProbe terrain) {
        int feet = (int) Math.round(p.y() + f.footOffsetY());
        return terrain.clear(
                (int) Math.round(p.x() - f.halfWidth()),
                feet - f.height(),
                (int) Math.round(p.x() + f.halfWidth()),
                feet,
                f.collisionPath());
    }

    private static void sweep(
            Point from,
            Point to,
            StageplayRouteFootprint f,
            RouteTerrainProbe terrain,
            Budget budget) {
        int count =
                Math.max(
                        1,
                        (int)
                                Math.ceil(
                                        Math.max(
                                                Math.abs(to.x() - from.x()),
                                                Math.abs(to.y() - from.y()))));
        for (int i = 1; i <= count; i++) {
            budget.check();
            double t = i / (double) count;
            Point p =
                    new Point(
                            from.x() + (to.x() - from.x()) * t, from.y() + (to.y() - from.y()) * t);
            Point floor = support(p.x(), p.y(), f, terrain);
            if (!withinStepClearance(p, floor) || !clear(p, f, terrain))
                throw new Stop(
                        SEARCH_EXHAUSTED,
                        "BLOCKED_SWEEP: fractional visual movement leaves floor support or"
                            + " intersects terrain");
        }
    }

    private static List<Point> compact(List<Point> source) {
        var result = new ArrayList<Point>();
        for (Point p : source) {
            while (result.size() >= 2) {
                Point a = result.get(result.size() - 2), b = result.getLast();
                double cross =
                        (b.x() - a.x()) * (p.y() - b.y()) - (b.y() - a.y()) * (p.x() - b.x());
                if (Math.abs(cross) > 1e-9) break;
                result.removeLast();
            }
            result.add(p);
        }
        return result;
    }

    private static Point startPosition(
            StageplayDocument document,
            ObjectActor actor,
            int start,
            int shotStart,
            Budget budget) {
        int currentIndex = 0;
        for (int i = 0; i < actor.keys().size() && actor.keys().get(i).frame() <= start; i++)
            currentIndex = i;
        int first = currentIndex;
        while (first > 0
                && actor.keys().get(first - 1).frame() >= shotStart
                && actor.keys().get(first - 1).visible()
                && actor.keys().get(first - 1).mode() == ObjectMode.PUPPET) first--;
        ObjectKey key = actor.keys().get(first);
        Point point = new Point(key.x(), key.y());
        int index = first, commandIndex = 0;
        ObjectCommand goal = null;
        boolean stopped = false;
        while (commandIndex < actor.commands().size()
                && actor.commands().get(commandIndex).frame() < key.frame()) commandIndex++;
        for (int frame = key.frame(); frame <= start; frame++) {
            budget.check();
            if (index + 1 < actor.keys().size() && actor.keys().get(index + 1).frame() <= frame) {
                key = actor.keys().get(++index);
                point = new Point(key.x(), key.y());
                stopped = false;
            }
            if (frame == start) return point;
            while (commandIndex < actor.commands().size()
                    && actor.commands().get(commandIndex).frame() <= frame) {
                var command = actor.commands().get(commandIndex++);
                if (command.kind() == ObjectCommandKind.MOVE_TO) {
                    goal = command;
                    stopped = false;
                } else {
                    goal = null;
                    stopped = true;
                }
            }
            if (goal != null) {
                budget.tick();
                double[] moved =
                        StageplayObjectMotion.move(
                                point.x(), point.y(), goal.x(), goal.y(), goal.speed());
                point = new Point(moved[0], moved[1]);
            } else if (!stopped) {
                var next = index + 1 < actor.keys().size() ? actor.keys().get(index + 1) : key;
                int keyFrame = key.frame();
                int shotEnd =
                        document.sceneChanges().stream()
                                .filter(c -> c.frame() > keyFrame)
                                .mapToInt(SceneChange::frame)
                                .min()
                                .orElse(document.durationFrames());
                double t =
                        next.frame() >= shotEnd
                                        || next.frame() == key.frame()
                                        || key.interpolation() == Interpolation.HOLD
                                ? 0
                                : Math.clamp(
                                        (frame - key.frame())
                                                / (double) (next.frame() - key.frame()),
                                        0,
                                        1);
                if (key.interpolation() == Interpolation.SMOOTH) t = t * t * (3 - 2 * t);
                point =
                        new Point(
                                key.x() + (next.x() - key.x()) * t,
                                key.y() + (next.y() - key.y()) * t);
            }
        }
        throw new AssertionError();
    }

    private static Set<String> ids(StageplayDocument d) {
        var ids = new HashSet<String>();
        d.actors().forEach(a -> ids.add(a.id()));
        d.objectActors()
                .forEach(
                        a -> {
                            ids.add(a.id());
                            a.commands().forEach(c -> ids.add(c.id()));
                        });
        d.puppets().forEach(p -> p.commands().forEach(c -> ids.add(c.id())));
        d.captions().forEach(c -> ids.add(c.id()));
        d.audio().forEach(c -> ids.add(c.id()));
        d.cameraEffects().forEach(c -> ids.add(c.id()));
        d.sceneChanges().forEach(c -> ids.add(c.id()));
        d.dialogue().forEach(c -> ids.add(c.id()));
        d.worldCommands().forEach(c -> ids.add(c.id()));
        return ids;
    }

    private static String nextId(Set<String> ids, StageplayRouteRequest request, int ordinal) {
        String base =
                "route.object."
                        + Integer.toUnsignedString(request.actorId().hashCode(), 36)
                        + "."
                        + request.startFrame()
                        + "."
                        + ordinal;
        String id = base;
        for (int attempt = 0; !ids.add(id); attempt++) id = base + "." + attempt;
        return id;
    }
}
