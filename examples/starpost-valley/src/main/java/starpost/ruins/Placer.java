package starpost.ruins;

import com.openggf.mods.state.SnapshotRandom;
import java.util.ArrayList;
import java.util.List;
import starpost.valley.Ground;

/**
 * Puts the exit and the contents into a chamber whose reachable spots are known: the exit on the
 * reached spot farthest from the entry, then ring rows and arcs, rocks, a ring monitor now and
 * then, the band's badniks, spikes that do not cut the route to the exit, and air bubbles under
 * water. Everything lands on reached spots, so nothing is placed out of reach.
 */
final class Placer {
    private final Chamber chamber;
    private final Reach reach;
    private final int entry;
    private final SnapshotRandom rng;
    private int exit = -1;

    Placer(Chamber chamber, Reach reach, int entry, SnapshotRandom rng) {
        this.chamber = chamber;
        this.reach = reach;
        this.entry = entry;
        this.rng = rng;
    }

    /** The shaft down on the farthest flat reached spot (the last chamber has none). */
    void exit() {
        if (chamber.number >= RuinsRules.CHAMBERS) {
            return;
        }
        int best = -1;
        int bestD = -1;
        for (int s = 0; s < reach.spotCount(); s++) {
            if (!usable(s, 8) || springNear(s)) {
                continue;
            }
            int d = ChamberGen.distance(reach, entry, s) + (dry(s) ? 64 : 0);
            if (d > bestD) {
                bestD = d;
                best = s;
            }
        }
        if (best < 0) {
            best = entry;
        }
        exit = best;
        chamber.exitX = reach.x(best);
        chamber.exitY = reach.y(best);
    }

    /** A Star Post elevator on a flat reached spot about half way between the entry and the exit. */
    void elevator() {
        int best = -1;
        float bestScore = Float.MAX_VALUE;
        float mx = (chamber.entryX + (chamber.exitX < 0 ? chamber.width - 64 : chamber.exitX)) / 2f;
        for (int s = 0; s < reach.spotCount(); s++) {
            if (!usable(s, 16) || springNear(s) || !free(s, 64)) {
                continue;
            }
            float score = Math.abs(reach.x(s) - mx) + Math.abs(reach.y(s) - chamber.entryY) / 2f;
            if (score < bestScore) {
                bestScore = score;
                best = s;
            }
        }
        if (best < 0) {
            best = entry;
        }
        chamber.elevatorX = reach.x(best);
        chamber.elevatorY = reach.y(best);
    }

    /**
     * A landmark's finds in monitors, each on the reached flat spot nearest its hand-picked point
     * (or, without one, as far from the entry as possible).
     */
    void prizes() {
        List<String> prizes = Landmarks.prizes(chamber.number);
        for (int i = 0; i < prizes.size(); i++) {
            int[] hint = Landmarks.hint(chamber.number, i);
            int best = -1;
            float bestScore = Float.MAX_VALUE;
            for (int s = 0; s < reach.spotCount(); s++) {
                if (!usable(s, 8) || springNear(s) || !free(s, 40)) {
                    continue;
                }
                float score = hint == null ? -ChamberGen.distance(reach, entry, s)
                        : Math.abs(reach.x(s) - hint[0]) + Math.abs(reach.y(s) - hint[1]);
                if (score < bestScore) {
                    bestScore = score;
                    best = s;
                }
            }
            if (best >= 0) {
                chamber.prizes.add(prizes.get(i));
                chamber.add(Chamber.MONITOR, reach.x(best), reach.y(best), chamber.prizes.size() - 1);
            }
        }
    }

    /** A reached spot without hazards, flat for {@code half} pixels either side. */
    boolean usable(int s, int half) {
        return reach.flat(s, half);
    }

    private boolean dry(int s) {
        return chamber.waterY == Chamber.NO_WATER || reach.y(s) - GenerationRunner.STAND_HEIGHT < chamber.waterY;
    }

    private boolean springNear(int s) {
        return chamber.hasThingNear(Chamber.SPRING, reach.x(s), reach.y(s), 40);
    }

    /** Clear of the entry, the exit and anything placed already. */
    private boolean free(int s, int room) {
        int x = reach.x(s), y = reach.y(s);
        return Math.abs(x - chamber.entryX) + Math.abs(y - chamber.entryY) > room
                && (chamber.exitX < 0 || Math.abs(x - chamber.exitX) + Math.abs(y - chamber.exitY) > room)
                && (chamber.elevatorX < 0 || Math.abs(x - chamber.elevatorX) + Math.abs(y - chamber.elevatorY) > room)
                && !chamber.hasThingNear(-1, x, y - 12, room / 2 + 8);
    }

    private List<Integer> candidates(int half, int room) {
        List<Integer> out = new ArrayList<>();
        for (int s = 0; s < reach.spotCount(); s++) {
            if (usable(s, half) && free(s, room) && !springNear(s)) {
                out.add(s);
            }
        }
        return out;
    }

    void contents() {
        int blocks = chamber.cols * chamber.rows;
        badniks(Math.min(blocks * 2, 1 + chamber.number / 8 + rng.nextInt(2) + blocks / 2));
        rocks(2 + blocks / 2 + rng.nextInt(2));
        if (rng.nextInt(100) < 20) {
            List<Integer> spots = candidates(8, 48);
            if (!spots.isEmpty()) {
                int s = spots.get(rng.nextInt(spots.size()));
                chamber.add(Chamber.MONITOR, reach.x(s), reach.y(s), -1);
            }
        }
        trail();
        rings(1 + blocks / 3 + rng.nextInt(2));
        if (chamber.number > 3) {
            spikes(rng.nextInt(chamber.number > 20 ? 3 : 2));
        }
        if (chamber.waterY != Chamber.NO_WATER) {
            bubbles();
        }
    }

    /**
     * A trail of rings along the route the traversal found from the entry to the exit, the way
     * Sonic's levels point the way: along floors every 24 pixels, and in an arc over each jump
     * or drop.
     */
    private void trail() {
        if (exit < 0) {
            return;
        }
        List<Integer> route = reach.route(exit);
        int placed = 0;
        float since = 12;
        for (int i = 1; i < route.size() && placed < 30; i++) {
            int a = route.get(i - 1), b = route.get(i);
            int ax = reach.x(a), ay = reach.y(a), bx = reach.x(b), by = reach.y(b);
            int dx = bx - ax, dy = by - ay;
            List<Integer> floor = followFloor(ax, ay, bx, by);
            if (floor != null) {
                for (int spot : floor) {
                    since += Reach.STEP;
                    if (since >= 24 && ring(reach.x(spot), reach.y(spot) - 20)) {
                        since = 0;
                        placed++;
                    }
                }
            } else {
                int peak = Math.min(ay, by) - 48;
                for (int k = 1; k <= 3; k++) {
                    float t = k / 4f;
                    float rx = ax + dx * t;
                    float ry = (1 - t) * (1 - t) * (ay - 20) + 2 * (1 - t) * t * peak + t * t * (by - 20);
                    if (ring(Math.round(rx), Math.round(ry))) {
                        placed++;
                    }
                }
                since = 0;
            }
        }
    }

    /**
     * The reached spots along one floor from a to b, column by column, following slopes (each
     * within 12 pixels of the last); null when the floor breaks (the hop was a jump or a drop).
     */
    private List<Integer> followFloor(int ax, int ay, int bx, int by) {
        List<Integer> out = new ArrayList<>();
        int step = bx >= ax ? Reach.STEP : -Reach.STEP;
        int y = ay;
        for (int x = ax + step; step > 0 ? x <= bx : x >= bx; x += step) {
            int spot = nearSpot(x, y);
            if (spot < 0) {
                return null;
            }
            y = reach.y(spot);
            out.add(spot);
        }
        return Math.abs(y - by) <= 12 ? out : null;
    }

    /** A reached spot in the column at {@code x} near height {@code y}, or -1. */
    private int nearSpot(int x, float y) {
        for (int dy = 0; dy <= 12; dy += 2) {
            for (int sign = -1; sign <= 1; sign += 2) {
                int s = reach.spotAt(x, y + sign * dy);
                if (s >= 0 && reach.reached(s) && !reach.hazard(s)) {
                    return s;
                }
            }
        }
        return -1;
    }

    /** A ring at a centre, if the air there is clear and it is not crowding the doorways or another thing. */
    private boolean ring(int x, int y) {
        if (!clear(x, y, 6) || Math.abs(x - chamber.entryX) < 24 && Math.abs(y - chamber.entryY) < 48
                || chamber.exitX >= 0 && Math.abs(x - chamber.exitX) < 20 && Math.abs(y - chamber.exitY) < 40
                || chamber.hasThingNear(-1, x, y, 12)) {
            return false;
        }
        for (Chamber.Lava l : chamber.lava) {
            if (l.contains(x, y) || l.contains(x, y + 8)) {
                return false;
            }
        }
        chamber.add(Chamber.RING, x, y, 0);
        return true;
    }

    /** Rows of rings along the floor, and arcs over a jump. */
    private void rings(int groups) {
        for (int g = 0; g < groups; g++) {
            List<Integer> spots = candidates(16, 40);
            if (spots.isEmpty()) {
                return;
            }
            int s = spots.get(rng.nextInt(spots.size()));
            int x = reach.x(s), y = reach.y(s);
            int n = 3 + rng.nextInt(3);
            if (rng.nextInt(3) == 0 && clear(x, y - 80, 40)) {
                for (int i = 0; i < 5; i++) {
                    int dx = (i - 2) * 16;
                    int dy = 28 + (int) (Math.cos((i - 2) * 0.6) * 36);
                    chamber.add(Chamber.RING, x + dx, y - dy, 0);
                }
                continue;
            }
            for (int i = 0; i < n; i++) {
                int rx = x + (i - n / 2) * 16;
                int spot = reach.spotAt(Math.round(rx / (float) Reach.STEP) * Reach.STEP, y);
                int ry = spot >= 0 ? reach.y(spot) : y;
                if (Math.abs(ry - y) <= 8 && clear(rx, ry - 20, 6)) {
                    chamber.add(Chamber.RING, rx, ry - 20, 0);
                }
            }
        }
    }

    private void rocks(int count) {
        for (int i = 0; i < count; i++) {
            List<Integer> spots = candidates(16, 56);
            if (spots.isEmpty()) {
                return;
            }
            int s = spots.get(rng.nextInt(spots.size()));
            chamber.add(Chamber.ROCK, reach.x(s), reach.y(s), rng.nextInt(4));
        }
    }

    private void badniks(int count) {
        int[] roster = Badnik.roster(chamber.band);
        for (int i = 0; i < count; i++) {
            int kind = roster[rng.nextInt(roster.length)];
            List<Integer> spots = candidates(Badnik.walker(kind) ? 24 : 8, 96);
            spots.removeIf(s -> Math.abs(reach.x(s) - chamber.entryX) < 120 && Math.abs(reach.y(s) - chamber.entryY) < 96);
            if (spots.isEmpty()) {
                return;
            }
            for (int tries = 0; tries < 8; tries++) {
                int s = spots.get(rng.nextInt(spots.size()));
                int[] at = Badnik.home(kind, chamber, reach.x(s), reach.y(s));
                if (at != null) {
                    chamber.add(Chamber.BADNIK, at[0], at[1], kind);
                    break;
                }
            }
        }
    }

    /** Spikes off the route: each one is kept only if the exit is still reached around it. */
    private void spikes(int count) {
        for (int i = 0; i < count; i++) {
            List<Integer> spots = candidates(24, 64);
            if (spots.isEmpty() || exit < 0) {
                return;
            }
            int s = spots.get(rng.nextInt(spots.size()));
            chamber.add(Chamber.SPIKES, reach.x(s), reach.y(s), 0);
            reach.refreshHazards();
            reach.explore(entry);
            if (!everythingReached()) {
                chamber.things.remove(chamber.things.size() - 1);
                reach.refreshHazards();
                reach.explore(entry);
            }
        }
    }

    /** Whether the exit, the elevator and everything standing on the floor are still reached. */
    private boolean everythingReached() {
        if (exit >= 0 && !reach.reached(exit)) {
            return false;
        }
        if (chamber.elevatorX >= 0 && !reachedAt(chamber.elevatorX, chamber.elevatorY)) {
            return false;
        }
        for (Chamber.Thing t : chamber.things) {
            if ((t.type() == Chamber.ROCK || t.type() == Chamber.MONITOR || t.type() == Chamber.SPRING
                    || t.type() == Chamber.BUBBLES) && !reachedAt(t.x(), t.y())) {
                return false;
            }
        }
        return true;
    }

    private boolean reachedAt(int x, int y) {
        int s = reach.spotAt(x, y);
        return s >= 0 && reach.reached(s);
    }

    /** Air bubble vents on reached floor under the water (Sonic 1's LZ bubble makers). */
    private void bubbles() {
        List<Integer> wet = new ArrayList<>();
        for (int s = 0; s < reach.spotCount(); s++) {
            if (usable(s, 0) && !dry(s) && free(s, 48)) {
                wet.add(s);
            }
        }
        int n = Math.min(wet.size(), chamber.waterY < 0 ? 2 : 1);
        for (int i = 0; i < n; i++) {
            int s = wet.remove(rng.nextInt(wet.size()));
            chamber.add(Chamber.BUBBLES, reach.x(s), reach.y(s), 0);
            wet.removeIf(o -> Math.abs(reach.x(o) - reach.x(s)) < 160);
            if (wet.isEmpty()) {
                return;
            }
        }
    }

    private boolean clear(int cx, int cy, int half) {
        for (int y = cy - half; y <= cy + half; y += 4) {
            for (int x = cx - half; x <= cx + half; x += 4) {
                if (chamber.floor(x, y) || y < 8) {
                    return false;
                }
            }
        }
        return cx > 8 && cx < chamber.width - 8;
    }
}
