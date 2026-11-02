package com.openggf.route;

import java.util.List;
import java.util.Optional;

/**
 * A deterministic one-axis platform world for exercising the route core without a ROM. Right and
 * Left move one pixel a row; a fresh A press on the ground jumps for eleven rows; standing in a
 * pit is death; crossing a boundary x starts a new epoch. Checkpoints hold every field, including
 * the previous row (the host's press-edge history), unless built incomplete on purpose. Optional
 * objects: a boss that loses a hit when the player lands on it from a jump and reports its hits
 * (owner facts, as an engine boss's {@code HitPointSource} would), and a placed platform whose
 * phase alternates every forty rows.
 */
final class SyntheticRouteWorlds implements RouteWorldFactory {
    record Pit(int from, int to) { }

    private final List<Pit> pits;
    private final int boundaryX;
    private final int leftBoundaryX;
    private final boolean incompleteCheckpoints;
    private final int bossX;
    private final int bossHits;
    private final int platformX;
    int boots;

    SyntheticRouteWorlds(List<Pit> pits, int boundaryX, int leftBoundaryX, boolean incompleteCheckpoints) {
        this(pits, boundaryX, leftBoundaryX, incompleteCheckpoints, Integer.MIN_VALUE, 0, Integer.MIN_VALUE);
    }

    private SyntheticRouteWorlds(List<Pit> pits, int boundaryX, int leftBoundaryX, boolean incompleteCheckpoints,
                                 int bossX, int bossHits, int platformX) {
        this.pits = List.copyOf(pits);
        this.boundaryX = boundaryX;
        this.leftBoundaryX = leftBoundaryX;
        this.incompleteCheckpoints = incompleteCheckpoints;
        this.bossX = bossX;
        this.bossHits = bossHits;
        this.platformX = platformX;
    }

    SyntheticRouteWorlds withBoss(int x, int hits) {
        return new SyntheticRouteWorlds(pits, boundaryX, leftBoundaryX, incompleteCheckpoints, x, hits, platformX);
    }

    SyntheticRouteWorlds withPhasePlatform(int x) {
        return new SyntheticRouteWorlds(pits, boundaryX, leftBoundaryX, incompleteCheckpoints, bossX, bossHits, x);
    }

    static SyntheticRouteWorlds flat() {
        return new SyntheticRouteWorlds(List.of(), Integer.MAX_VALUE, Integer.MIN_VALUE, false);
    }

    static LaunchContract launch() {
        return LaunchContract.coldLevel(RomIdentity.standard("s2"), new LevelId("s2", 0, 0), new Roster("sonic", ""), 320, 0);
    }

    static InputTape right(int rows) {
        var builder = new InputTape.Builder();
        for (int i = 0; i < rows; i++) builder.add(PadRow.of(PlayerSlot.P1, PhysicalButton.RIGHT));
        return builder.build();
    }

    @Override public RouteWorld openSearch(LaunchContract launch) {
        boots++;
        return new World();
    }

    @Override public ReplayWorld openColdReplay(LaunchContract launch) {
        boots++;
        return new World();
    }

    @Override public String environment() {
        return "synthetic/1";
    }

    private record State(int x, int y, int vy, boolean air, boolean dead, int previous, int rows, long epoch,
                         boolean crossed, boolean crossedLeft, int hits, int cooldown) { }

    private final class World implements RouteWorld {
        private State s = new State(0, 0, 0, false, false, 0, 0, 0, false, false, bossHits, 0);
        private boolean closed;

        @Override public RouteSample observe() {
            var p1 = new PlayerView(s.x, 1000 + s.y, 0, 0, 0, 0, 0, 0, s.air, false, false, false, false, s.dead, 10,
                    false, false, false, PlayerView.Motion.NONE);
            var objects = new java.util.ArrayList<ObjectView>();
            if (bossX != Integer.MIN_VALUE) {
                objects.add(new ObjectView(new InstanceKey(s.epoch, 1, 0, 0, 1, "DYNAMIC"), Optional.empty(),
                        "SyntheticBoss", bossX, 1000, java.util.Set.of(Capability.HIT_POINTS), List.of(), "", s.hits,
                        s.hits == 0));
            }
            if (platformX != Integer.MIN_VALUE) {
                objects.add(new ObjectView(new InstanceKey(s.epoch, 2, 0, 3, 0, "LAYOUT"),
                        Optional.of(new PlacementKey(0, 0, 3, 0x51, 0)), "SyntheticPlatform", platformX, 1000,
                        java.util.Set.of(Capability.PHASE), List.of("solid", "gone"), (s.rows / 40) % 2 == 0 ? "solid" : "gone",
                        -1, false));
            }
            return new RouteSample(s.rows, s.epoch, new RouteSample.Level(0, 0, 0, "LEVEL"),
                    new RouteSample.Clocks(s.rows, s.rows), p1, Optional.empty(), 0, 0, 0, objects, List.of());
        }

        @Override public RecordedStep step(PadRow offered) {
            if (closed) throw new IllegalStateException("closed");
            int lane = offered.p1();
            int x = s.x, y = s.y, vy = s.vy;
            boolean air = s.air, dead = s.dead;
            if (!dead) {
                if ((lane & PhysicalButton.RIGHT.bit()) != 0) x++;
                if ((lane & PhysicalButton.LEFT.bit()) != 0) x--;
                boolean press = (lane & PhysicalButton.A.bit()) != 0 && (s.previous & PhysicalButton.A.bit()) == 0;
                if (press && !air) {
                    air = true;
                    vy = -5;
                }
                if (air) {
                    y += vy;
                    vy++;
                    if (y >= 0) {
                        y = 0;
                        vy = 0;
                        air = false;
                    }
                }
                for (Pit pit : pits) {
                    if (!air && x >= pit.from() && x < pit.to()) dead = true;
                }
            }
            int hits = s.hits;
            int cooldown = Math.max(0, s.cooldown - 1);
            if (bossX != Integer.MIN_VALUE && hits > 0 && cooldown == 0 && air && vy > 0 && Math.abs(x - bossX) <= 6
                    && y >= -8) {
                hits--;
                vy = -5;
                cooldown = 20;
            }
            long epoch = s.epoch;
            boolean crossed = s.crossed, crossedLeft = s.crossedLeft;
            if (!crossed && x >= boundaryX) { crossed = true; epoch++; }
            if (!crossedLeft && x <= leftBoundaryX) { crossedLeft = true; epoch++; }
            s = new State(x, y, vy, air, dead, lane, s.rows + 1, epoch, crossed, crossedLeft, hits, cooldown);
            return new RecordedStep(offered, observe());
        }

        @Override public int rowsOffered() {
            return s.rows;
        }

        @Override public WorldCheckpoint capture() {
            // An incomplete token forgets host-owned state the way an unsaved driver counter would.
            State saved = incompleteCheckpoints ? new State(s.x, s.y, s.vy, s.air, s.dead, s.previous, 0, s.epoch,
                    s.crossed, s.crossedLeft, s.hits, s.cooldown) : s;
            return new Saved(this, saved);
        }

        @Override public void restore(WorldCheckpoint checkpoint) {
            if (!(checkpoint instanceof Saved saved) || saved.owner != this || saved.state.epoch != s.epoch) {
                throw new RouteHostException(RouteHostException.Reason.CHECKPOINT_MISMATCH, "synthetic checkpoint mismatch");
            }
            s = saved.state;
        }

        @Override public void close() {
            closed = true;
        }
    }

    private record Saved(Object owner, State state) implements WorldCheckpoint { }
}
