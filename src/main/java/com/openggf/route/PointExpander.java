package com.openggf.route;

import com.openggf.sprites.playable.route.NativeRouteSteering;
import com.openggf.sprites.playable.route.NativeRouteSteering.EndingAction;
import com.openggf.sprites.playable.route.NativeRouteSteering.TravelSpeed;

import java.util.List;
import java.util.TreeSet;

/**
 * Point goals: reach a target and settle, steered by native buttons on the shared frontier.
 * Moved here from {@code StageplayRoutePlanner.searchPass} (static-terrain route search, 2026-10)
 * so the search records the rows it ticked instead of re-encoding commands.
 *
 * <p>The root edge prepares the start: a falling spawn lands with neutral rows, a spindash start
 * charges and releases through {@link NativeRouteSteering#charge}. Each later edge holds the
 * steering policy for up to twelve rows toward the target (or, from the root only, a run-up
 * detour), optionally holding jump for 6, 12 or 24 rows from the ground. Rank is elapsed rows
 * plus a distance heuristic scaled by native top speed, with penalties for jumps and detours;
 * arrival is accepted only when a node is taken from the frontier, so a cheaper walk can still
 * beat an earlier jump. The finished route appends its settle tail (braking or a gentle coast,
 * then any single facing tap) recorded on the same host, so the frozen rows cover the whole
 * window and nothing falls back to a command.
 */
final class PointExpander implements PrefixSearch.Expander {
    /** Authoring-search limits, not fitted or native movement values. */
    static final int EDGE_ROWS = 12;
    private static final int[] GROUND_HOLDS = {0, 6, 12, 24};
    private static final int JUMP_START_HOLD = 12;
    private static final int FACING_DELAY = 8;
    private static final int BOUND_X = 1536;
    private static final int BOUND_Y = 768;
    private static final int MARGIN = 256;
    private static final int JUMP_PENALTY = 2000;
    private static final int DETOUR_PENALTY = 600;

    private final PointGoal goal;
    private String failure = "";
    private StopReason reason = StopReason.NO_PROGRESS;
    private double minX;
    private double maxX;
    private double minY;
    private double maxY;
    private double initialX;

    /** Controller cursor of a point-route node; immutable. */
    record State(int lastRow, int jumpEnd, int hold, double goalX, boolean returning, int approach,
                 int elapsed, int jumpCost, int detourCost, boolean arrived) { }

    PointExpander(PointGoal goal) {
        this.goal = goal;
    }

    @Override public String failure() {
        return failure;
    }

    @Override public StopReason failureReason() {
        return reason;
    }

    private void fail(StopReason why, String text) {
        reason = why;
        failure = text;
    }

    @Override public String checkPrefixRow(int row, RouteSample sample) {
        return null;
    }

    @Override public int rootProgress(RouteSample sample) {
        return 0;
    }

    @Override public int target() {
        return goal.maxRows();
    }

    @Override public boolean rejected(InputTape tape) {
        return false;
    }

    @Override public boolean done(PrefixSearch.Node node) {
        if (!(node.data instanceof State state) || !state.arrived()) return false;
        if (goal.steering().endingAction() == EndingAction.OVERSHOOT && !state.returning()) return false;
        // A jump or spindash start cannot count its preparation as the arrival.
        return !(isPreparation(node) && goal.start() != PointGoal.Start.WALK);
    }

    private static boolean isPreparation(PrefixSearch.Node node) {
        return node.data instanceof State state && state.hold() == -1;
    }

    @Override public PrefixSearch.Node expand(PrefixSearch search, PrefixSearch.Node node, TreeSet<PrefixSearch.Node> frontier) {
        if (!(node.data instanceof State)) return prepare(search, node);
        State state = (State) node.data;
        search.materialize(node);
        search.ensureCheckpoint(node);
        int next = state.lastRow() + 1;
        if (next >= goal.maxRows()) return null;
        boolean completingDetour = state.goalX() != goal.targetX() && !isPreparation(node)
                && (Math.abs(node.sample.p1().x() - state.goalX()) > 6 || Math.abs(node.sample.p1().groundSpeed()) > 12);
        double[] goals = isPreparation(node)
                ? new double[]{goal.targetX(), initialX - 64, initialX + 64, initialX - 128, initialX + 128}
                : new double[]{completingDetour ? state.goalX() : goal.targetX()};
        int[] holds = isPreparation(node) && goal.start() == PointGoal.Start.JUMP ? new int[]{JUMP_START_HOLD}
                : !node.sample.p1().air() && next > state.jumpEnd() + 1 ? GROUND_HOLDS : new int[]{0};
        for (double target : goals) {
            for (int hold : holds) {
                if (target != goal.targetX() && hold > 0) continue;
                if (target < 0 || target > 32767 || hold > goal.maxRows() - next) continue;
                if (search.cancelled() || search.outOfSteps()) return null;
                PrefixSearch.Node child = edge(search, node, state, target, hold, next);
                if (child != null) search.push(frontier, child);
            }
        }
        return null;
    }

    /** Lands a falling spawn and performs a spindash start; the result is the search root. */
    private PrefixSearch.Node prepare(PrefixSearch search, PrefixSearch.Node root) {
        search.materialize(root);
        var rows = new InputTape.Builder();
        RouteSample sample = search.last;
        // A falling spawn lands before its starting action. Never manufacture grounding.
        while (sample.p1().air()) {
            if (rows.size() >= goal.maxRows() || search.outOfSteps()) {
                fail(StopReason.BUDGET_EXHAUSTED, "Starting actor did not land within the route budget");
                return null;
            }
            rows.add(search.step(PadRow.NEUTRAL));
            sample = search.last;
        }
        if (goal.start() == PointGoal.Start.SPINDASH) {
            boolean charged = false, released = false;
            for (int elapsed = 0; elapsed <= NativeRouteSteering.CHARGE_FRAMES + 1; elapsed++) {
                if (rows.size() >= goal.maxRows() || search.outOfSteps()) {
                    fail(StopReason.BUDGET_EXHAUSTED, "Spindash charge exceeded route budget");
                    return null;
                }
                PlayerView p = sample.player(goal.player());
                var buttons = NativeRouteSteering.charge(p.x(), goal.targetX(), elapsed, p.groundSpeed(), p.air(),
                        p.crouching(), p.spindash(), p.motion().friction(), p.motion().slopeImpulse());
                rows.add(search.step(row(buttons.direction(), buttons.down(), buttons.jump())));
                sample = search.last;
                p = sample.player(goal.player());
                charged |= p.spindash();
                released |= charged && !p.spindash() && !buttons.down() && p.groundSpeed() != 0;
            }
            if (!charged || !released) {
                fail(StopReason.NO_PROGRESS, "Native spindash charge and release did not occur near x="
                        + Math.round(sample.player(goal.player()).x())
                        + "; could not settle and establish Duck within the charge window");
                return null;
            }
        }
        PlayerView start = sample.player(goal.player());
        if (Math.abs(goal.targetX() - start.x()) > BOUND_X || Math.abs(goal.targetY() - start.y()) > BOUND_Y) {
            fail(StopReason.UNSUPPORTED_GOAL,
                    "Basic routes are bounded to 1536 horizontal and 768 vertical pixels from shot entry");
            return null;
        }
        initialX = start.x();
        minX = Math.min(start.x(), goal.targetX()) - MARGIN;
        maxX = Math.max(start.x(), goal.targetX()) + MARGIN;
        minY = Math.min(start.y(), goal.targetY()) - MARGIN;
        maxY = Math.max(start.y(), goal.targetY()) + MARGIN;
        InputTape edge = rows.build();
        int lastRow = root.rows + edge.size() - 1;
        boolean arrived = goal.arrivedAt(start, PointGoal.ARRIVE_SPEED);
        var state = new State(lastRow, -2, -1, goal.targetX(), false, goal.targetX() < start.x() ? -1 : 1, 0, 0, 0,
                arrived);
        PrefixSearch.Node prepared = search.node(root, edge, new PrefixSearch.Origin.Authored(edge.size()), 0,
                rank(score(start, 0)), 0, 0, state, sample);
        search.arrivedAt(prepared);
        return prepared;
    }

    /** One steering edge from {@code node}; null when it leaves the box, breaks the walk cap or merges. */
    private PrefixSearch.Node edge(PrefixSearch search, PrefixSearch.Node node, State state, double target, int hold,
                                   int next) {
        search.materialize(node);
        search.dirty();
        search.trials++;
        int jumpEnd = hold == 0 ? state.jumpEnd() : next + hold;
        int finalRow = Math.min(next + EDGE_ROWS, goal.maxRows());
        RouteSample sample = node.sample;
        boolean returning = state.returning();
        boolean arrived = false;
        var rows = new InputTape.Builder();
        int row = next;
        NativeRouteSteering.Settings settings = target == goal.targetX() ? goal.steering()
                : new NativeRouteSteering.Settings(goal.steering().travelSpeed(), EndingAction.HARD_STOP);
        for (; row < finalRow; row++) {
            PlayerView p = sample.player(goal.player());
            if (target == goal.targetX() && goal.steering().endingAction() == EndingAction.OVERSHOOT) {
                returning = NativeRouteSteering.returning(Math.floor(p.x()), target, state.approach(), returning);
            }
            int direction = NativeRouteSteering.direction(Math.floor(p.x()), p.xSpeed(), p.groundSpeed(),
                    p.motion().runAccel(), p.motion().runDecel(), p.motion().friction(), p.motion().maxSpeed(),
                    p.motion().slopeImpulse(), p.air(), target, goal.urgency(), settings, returning, state.approach());
            rows.add(search.step(row(direction, false, row < jumpEnd)));
            sample = search.last;
            p = sample.player(goal.player());
            // Discard an unsafe launch/landing branch before it can become a route.
            if (goal.steering().travelSpeed() == TravelSpeed.WALK
                    && Math.max(Math.abs(p.groundSpeed()), Math.abs(p.xSpeed())) > NativeRouteSteering.STROLL_CAP) {
                return null;
            }
            if (p.x() < minX || p.x() > maxX || p.y() < minY || p.y() > maxY || p.dead()) return null;
            if (goal.arrivedAt(p, PointGoal.ARRIVE_SPEED)
                    && (goal.steering().endingAction() != EndingAction.OVERSHOOT || returning)) {
                arrived = true;
                row++;
                break;
            }
        }
        int last = row - 1;
        int elapsed = last + 1;
        int jumpCost = state.jumpCost() + (hold > 0 && goal.start() != PointGoal.Start.JUMP ? JUMP_PENALTY : 0);
        int detourCost = state.detourCost() + (isPreparation(node) && target != goal.targetX()
                ? DETOUR_PENALTY + (int) Math.abs(target - initialX) : 0);
        long cost = elapsed + jumpCost + detourCost;
        var cursor = List.of(last < jumpEnd, Math.max(0, jumpEnd - last), returning, target == goal.targetX() ? 0 : (int) target + 1);
        if (search.visitedCheaper(cursor, cost)) return null;
        PlayerView end = sample.player(goal.player());
        var child = new State(last, jumpEnd, hold, target, returning, state.approach(), elapsed, jumpCost, detourCost,
                arrived);
        return search.node(node, rows.build(), new PrefixSearch.Origin.Authored(rows.size()), 0,
                rank(score(end, elapsed) + jumpCost + detourCost), 0, 0, child, sample);
    }

    /**
     * The settle tail after arrival, to the end of the window: braking toward rest (a gentle stop
     * coasts with no input), then a single facing tap {@value #FACING_DELAY} rows later, then
     * neutral rows. Returns null with a failure when the window is too short.
     */
    @Override public PrefixSearch.Node finish(PrefixSearch search, PrefixSearch.Node arrived) {
        State state = (State) arrived.data;
        int terminal = Math.max(state.lastRow() + 1, arrived.rows);
        if (terminal >= goal.maxRows()) {
            fail(StopReason.BUDGET_EXHAUSTED, "Route needs more frames to settle");
            return null;
        }
        int facingRow = goal.facing() == PointGoal.Facing.INHERIT ? -1 : terminal + FACING_DELAY;
        if (facingRow >= goal.maxRows()) {
            fail(StopReason.BUDGET_EXHAUSTED, "Route needs more frames for its final facing tap");
            return null;
        }
        search.materialize(arrived);
        search.dirty();
        var rows = new InputTape.Builder();
        boolean faced = false;
        for (int row = terminal; row < goal.maxRows(); row++) {
            PlayerView p = search.last.player(goal.player());
            int direction;
            if (row == facingRow) {
                direction = goal.facing() == PointGoal.Facing.LEFT ? -1 : 1;
                faced = true;
            } else if (faced || goal.steering().endingAction() == EndingAction.GENTLE_STOP) {
                direction = 0;
            } else {
                direction = NativeRouteSteering.movementDirection(Math.floor(p.x()), p.xSpeed(), p.motion().runAccel(),
                        p.motion().runDecel(), p.motion().maxSpeed(), p.air(), p.x(), 0);
            }
            rows.add(search.step(row(direction, false, false)));
        }
        InputTape edge = rows.build();
        PrefixSearch.Node tail = search.node(arrived, edge, new PrefixSearch.Origin.Authored(edge.size()), 0,
                arrived.rank, 0, 0, state, search.last);
        search.arrivedAt(tail);
        return tail;
    }

    private double score(PlayerView p, int elapsed) {
        return elapsed + 2 * (Math.abs(goal.targetX() - p.x()) + 2 * Math.abs(goal.targetY() - p.y()))
                / Math.max(1, p.motion().maxSpeed() / 256.0);
    }

    /** Lower scores are better; the frontier takes the highest rank first. */
    private static long rank(double score) {
        return -Math.round(score * 1000);
    }

    /** The physical row for a steering decision; jump is C, the Stageplay live controller's button. */
    static PadRow row(int direction, boolean down, boolean jump) {
        int lane = 0;
        if (direction < 0) lane |= PhysicalButton.LEFT.bit();
        if (direction > 0) lane |= PhysicalButton.RIGHT.bit();
        if (down) lane |= PhysicalButton.DOWN.bit();
        if (jump) lane |= PhysicalButton.C.bit();
        return new PadRow(lane, 0);
    }
}
