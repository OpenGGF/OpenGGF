package starpost.ruins;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import starpost.valley.Runner;

/**
 * Where a player can get to in a chamber, found by playing it: from every standing spot reached
 * so far, the real controller ({@link Runner}, with the chamber's springs and water through
 * {@link Chamber#afterStep}) runs a set of short input programs (walks, drops, standing and
 * running jumps, short hops, steered and reversed jumps, spin dashes and dash jumps), and every
 * spot it stands on is reached. Spikes and lava end a program (a route through a hazard does not
 * count), and so does falling out of the bottom, which reaches the shaft down.
 *
 * <p>Spots are floor points every {@link #STEP} pixels with room to stand. The exit is placed on
 * a reached spot, so every chamber's exit is reachable by construction; this class is also the
 * check that proves it.
 */
public final class Reach {
    public static final int STEP = 8;
    private static final int MAX_FRAMES = 180;
    private static final int PROGRAMS = 11;
    private static final int MAX_SPOTS = 900;

    // Input bits.
    private static final int L = 1;
    private static final int R = 2;
    private static final int D = 4;
    private static final int JP = 8;   // jump pressed this frame
    private static final int JH = 16;  // jump held

    private final Chamber chamber;
    private final List<int[]> spots = new ArrayList<>();    // {x, y}
    private final List<List<Integer>> byColumn = new ArrayList<>();
    private boolean[] hazard;
    private boolean[] reached;
    private int[] parent;
    private boolean pit;
    private int steps;

    public Reach(Chamber chamber) {
        this.chamber = chamber;
        findSpots();
    }

    private void findSpots() {
        int columns = chamber.width / STEP;
        for (int c = 0; c <= columns; c++) {
            byColumn.add(new ArrayList<>());
        }
        for (int c = 2; c < columns - 1; c++) {
            int x = c * STEP;
            for (int y = 24; y < chamber.height; y++) {
                if (standable(x, y)) {
                    byColumn.get(c).add(spots.size());
                    spots.add(new int[] {x, y});
                }
            }
        }
        hazard = new boolean[spots.size()];
        reached = new boolean[spots.size()];
        parent = new int[spots.size()];
        refreshHazards();
    }

    /** A floor surface with nothing in the way of standing on it. */
    private boolean standable(int x, int y) {
        if (!chamber.floor(x, y) || chamber.floor(x, y - 1)) {
            return false;
        }
        for (int k = 2; k <= Runner.WALL; k++) {
            if (chamber.floor(x, y - k)) {
                return false;
            }
        }
        for (int k = Runner.WALL + 1; k <= Runner.STAND_HEIGHT; k++) {
            if (chamber.ceiling(x, y - k)) {
                return false;
            }
        }
        // Not inside a wall's picture: S1 often gives a solid mass collision only at its edges.
        int covered = 0;
        for (int dy = 4; dy <= 28; dy += 12) {
            for (int dx = -8; dx <= 8; dx += 8) {
                if (chamber.opaque(x + dx, y - dy)) {
                    covered++;
                }
            }
        }
        return covered < 8;
    }

    /** Re-reads which spots touch spikes or lava (after placing hazards). */
    public void refreshHazards() {
        for (int i = 0; i < spots.size(); i++) {
            hazard[i] = chamber.hazard(spots.get(i)[0], spots.get(i)[1]);
        }
    }

    public int spotCount() {
        return spots.size();
    }

    public int x(int spot) {
        return spots.get(spot)[0];
    }

    public int y(int spot) {
        return spots.get(spot)[1];
    }

    public boolean reached(int spot) {
        return reached[spot];
    }

    public boolean hazard(int spot) {
        return hazard[spot];
    }

    /** The spot a runner may have come from on the route found (-1 at the start). */
    public int parent(int spot) {
        return parent[spot];
    }

    /** Whether falling out of the bottom (the shaft down) is possible. */
    public boolean pitReached() {
        return pit;
    }

    public int reachedCount() {
        int n = 0;
        for (boolean r : reached) {
            if (r) {
                n++;
            }
        }
        return n;
    }

    /** Runner steps simulated so far (the check's cost). */
    public int steps() {
        return steps;
    }

    /**
     * Whether a reached, safe spot has reached ground {@code half} pixels to each side within
     * ten pixels of its height (room to stand a hatch, a rock or a badnik on; gentle slopes count).
     */
    public boolean flat(int s, int half) {
        if (!reached[s] || hazard[s]) {
            return false;
        }
        for (int dx = -half; dx <= half; dx += STEP) {
            if (dx == 0) {
                continue;
            }
            int c = Math.round((spots.get(s)[0] + dx) / (float) STEP);
            boolean ok = false;
            if (c >= 0 && c < byColumn.size()) {
                for (int id : byColumn.get(c)) {
                    ok |= reached[id] && !hazard[id] && Math.abs(spots.get(id)[1] - spots.get(s)[1]) <= 10;
                }
            }
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether a spot has hazard-free floor {@code half} pixels to each side within ten pixels of its
     * height, reached or not: somewhere safe to arrive (no lava or drop one step away).
     */
    public boolean footing(int s, int half) {
        if (hazard[s]) {
            return false;
        }
        for (int dx = -half; dx <= half; dx += STEP) {
            int c = Math.round((spots.get(s)[0] + dx) / (float) STEP);
            boolean ok = false;
            if (c >= 0 && c < byColumn.size()) {
                for (int id : byColumn.get(c)) {
                    ok |= !hazard[id] && Math.abs(spots.get(id)[1] - spots.get(s)[1]) <= 10;
                }
            }
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    /** The standing spot at this floor point, or -1. */
    public int spotAt(float x, float y) {
        int c = Math.round(x / STEP);
        if (c < 0 || c >= byColumn.size()) {
            return -1;
        }
        for (int id : byColumn.get(c)) {
            if (Math.abs(spots.get(id)[1] - y) <= 3) {
                return id;
            }
        }
        return -1;
    }

    /** The nearest spot to a point (any column), or -1 when the chamber has none. */
    public int nearest(float x, float y) {
        int best = -1;
        float bestD = Float.MAX_VALUE;
        for (int i = 0; i < spots.size(); i++) {
            float dx = spots.get(i)[0] - x, dy = spots.get(i)[1] - y;
            float d = dx * dx + dy * dy;
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    /** Explores everything reachable from {@code start}, forgetting earlier results. */
    public void explore(int start) {
        java.util.Arrays.fill(reached, false);
        java.util.Arrays.fill(parent, -1);
        pit = false;
        if (start < 0) {
            return;
        }
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        reached[start] = true;
        queue.add(start);
        int expanded = 0;
        Runner runner = new Runner(0, 0);
        List<Integer> found = new ArrayList<>();
        while (!queue.isEmpty() && expanded < MAX_SPOTS) {
            int from = queue.poll();
            expanded++;
            for (int dir = -1; dir <= 1; dir += 2) {
                for (int program = 0; program < PROGRAMS; program++) {
                    if (program == 5 && dir < 0) {
                        continue; // the straight-up jump is the same both ways
                    }
                    found.clear();
                    run(runner, from, program, dir, found);
                    for (int spot : found) {
                        if (!reached[spot]) {
                            reached[spot] = true;
                            parent[spot] = from;
                            queue.add(spot);
                        }
                    }
                }
            }
        }
    }

    /** Plays one input program from a spot, collecting the spots stood on. */
    private void run(Runner r, int from, int program, int dir, List<Integer> found) {
        int[] s = spots.get(from);
        reset(r, s[0], s[1]);
        boolean wasAirborne = false;
        int stillFrames = 0;
        for (int frame = 0; frame < MAX_FRAMES; frame++) {
            int in = input(program, dir, frame, r, wasAirborne);
            if (in < 0) {
                return;
            }
            r.step(chamber, (in & L) != 0, (in & R) != 0, (in & D) != 0, (in & JP) != 0, (in & JH) != 0, frame);
            chamber.afterStep(r);
            steps++;
            if (r.y > chamber.height + 48) {
                pit = true;
                return;
            }
            if (chamber.hazard(r.x, r.y)) {
                return;
            }
            if (r.onGround) {
                int spot = spotAt(r.x, r.y);
                if (spot >= 0 && !hazard[spot]) {
                    found.add(spot);
                }
                if (wasAirborne && endsOnLanding(program)) {
                    return;
                }
                wasAirborne = false;
                stillFrames = Math.abs(r.speed) < 0.01f && !r.dashing ? stillFrames + 1 : 0;
                if (stillFrames > 6 && frame > 12) {
                    return; // stopped against something
                }
            } else {
                wasAirborne = true;
            }
        }
    }

    private static void reset(Runner r, int x, int y) {
        r.x = x;
        r.y = y;
        r.speed = 0;
        r.ySpeed = 0;
        r.onGround = true;
        r.rolling = false;
        r.ducking = false;
        r.dashing = false;
        r.dashCharge = 0;
        r.sprung = false;
        r.hurt = false;
        r.jumpedAt = -1;
        r.underwater = false;
        r.facingLeft = false;
    }

    private static boolean endsOnLanding(int program) {
        return program != 0 && program != 8;
    }

    /**
     * The pad on a frame of a program: 0 walk (keep walking after landings), 1 walk off and drop
     * straight, 2 jump held while steering, 3 short hop, 4 jump then steer late, 5 straight up,
     * 6 short run-up jump, 7 long run-up jump, 8 spin dash, 9 spin dash then jump, 10 jump and
     * turn back. Returns -1 when the program is over.
     */
    private static int input(int program, int dir, int frame, Runner r, boolean airborne) {
        int d = dir < 0 ? L : R;
        int back = dir < 0 ? R : L;
        return switch (program) {
            case 0 -> d;
            case 1 -> r.onGround && !airborne ? d : 0;
            case 2 -> (frame == 0 ? JP : 0) | JH | d;
            case 3 -> (frame == 0 ? JP | JH : 0) | d;
            case 4 -> (frame == 0 ? JP : 0) | JH | (frame >= 14 ? d : 0);
            case 5 -> (frame == 0 ? JP : 0) | JH;
            case 6 -> frame < 24 ? d : (frame == 24 ? JP : 0) | JH | d;
            case 7 -> frame < 60 ? d : (frame == 60 ? JP : 0) | JH | d;
            case 8 -> dash(frame, d, false);
            case 9 -> dash(frame, d, true);
            case 10 -> (frame == 0 ? JP : 0) | JH | (frame < 20 ? d : back);
            default -> -1;
        };
    }

    /** Face the way, crouch, rev three times, let go; optionally jump out of the roll. */
    private static int dash(int frame, int d, boolean jump) {
        if (frame == 0) {
            return d;
        }
        if (frame < 8) {
            return D | (frame == 3 || frame == 5 || frame == 7 ? JP | JH : 0);
        }
        if (jump && frame == 16) {
            return JP | JH | d;
        }
        return jump && frame > 16 ? JH | d : 0;
    }

    /** The route from the start to a spot, as spots (start first). */
    public List<Integer> route(int spot) {
        List<Integer> out = new ArrayList<>();
        for (int s = spot; s >= 0 && out.size() < spots.size(); s = parent[s]) {
            out.add(0, s);
        }
        return out;
    }
}
