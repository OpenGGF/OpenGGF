package threeislands.field;

import java.util.ArrayList;
import java.util.List;
import threeislands.core.EnemyKind;
import threeislands.core.Item;
import threeislands.core.Rng;
import threeislands.core.Zone;

/**
 * One visit to a zone: the party walking along the {@link FieldPath} and the things placed on
 * it. Badniks are visible on the path and a battle starts when the leader touches one; monitors
 * give items or rings; Starposts heal and save; the zone boss waits at the end. Placement is
 * fixed per zone, so a zone always has the same layout, but cleared spots come back on the next
 * visit, which lets the party train.
 */
public final class Field {
    public enum Kind { ENCOUNTER, MONITOR, STARPOST, MIDBOSS, BOSS }

    /** Something on the path. */
    public static final class Spot {
        public final Kind kind;
        public final double homeX;
        public final List<EnemyKind> group;
        public final Item item;
        public final int rings;
        public boolean done;

        Spot(Kind kind, double x, List<EnemyKind> group, Item item, int rings) {
            this.kind = kind;
            this.homeX = x;
            this.group = group;
            this.item = item;
            this.rings = rings;
        }

        /** Patrolling badniks drift around their home; everything else stays put. */
        public double x(long ticks) {
            if (kind != Kind.ENCOUNTER) return homeX;
            return homeX + Math.sin(ticks / 70.0 + homeX * 0.01) * 22;
        }
    }

    public static final double WALK = 2.0;
    public static final double RUN = 3.75;
    public static final int TRAIL = 64;
    public static final int FOLLOW_GAP = 14;
    public static final int TOUCH = 14;

    public final Zone zone;
    public final FieldPath path;
    public final List<Spot> spots = new ArrayList<>();
    private double x;
    private boolean facingLeft;
    private boolean moving;
    private boolean running;
    private final double[] trail = new double[TRAIL];
    private int trailHead;
    private double distance;
    private int grace;

    public Field(Zone zone, FieldPath path) {
        this.zone = zone;
        this.path = path;
        x = path.startX() + 8;
        for (int i = 0; i < TRAIL; i++) trail[i] = x;
        place();
    }

    private void place() {
        Rng rng = new Rng(0x5EED0000L + zone.ordinal() * 7919L);
        double start = path.startX();
        double length = path.length();
        List<EnemyKind> enemies = zone.enemyKinds();
        List<EnemyKind> bosses = zone.bossKinds();
        spots.add(new Spot(Kind.BOSS, start + length - 120, List.of(bosses.get(bosses.size() - 1)), null, 0));
        for (int i = 0; i < bosses.size() - 1; i++) {
            spots.add(new Spot(Kind.MIDBOSS, start + length * (0.55 + 0.1 * i), List.of(bosses.get(i)), null, 0));
        }
        double[] starposts = {0.34, 0.74};
        for (double f : starposts) spots.add(new Spot(Kind.STARPOST, start + length * f, List.of(), null, 0));
        int encounters = Math.max(6, Math.min(10, (int) (length / 900)));
        double first = start + 320;
        double last = start + length - 360;
        double step = (last - first) / Math.max(1, encounters - 1);
        for (int i = 0; i < encounters; i++) {
            double ex = first + step * i;
            if (near(ex, 100)) ex += 110;
            // Sonic travels alone on South Island, so its groups are smaller.
            int size = 1 + rng.nextInt(zone.islandIndex == 0 ? 2 : 3);
            if (i == 0) size = 1;
            List<EnemyKind> group = new ArrayList<>();
            for (int k = 0; k < size; k++) group.add(enemies.get(rng.nextInt(enemies.size())));
            spots.add(new Spot(Kind.ENCOUNTER, ex, List.copyOf(group), null, 0));
            if (i < encounters - 1) {
                double mx = ex + step * 0.5;
                if (near(mx, 60)) continue;
                int roll = rng.nextInt(100);
                Item item;
                int rings = 0;
                if (roll < 30) {
                    item = null;
                    rings = 10 + 5 * zone.tier;
                } else if (roll < 50) {
                    item = Item.SUPER_RING;
                } else if (roll < 62) {
                    item = Item.BLUE_SPHERE;
                } else if (roll < 72) {
                    item = Item.SPEED_SHOES;
                } else if (roll < 92) {
                    Item[] shields = {Item.FLAME_SHIELD, Item.BUBBLE_SHIELD, Item.LIGHTNING_SHIELD};
                    item = shields[rng.nextInt(3)];
                } else if (roll < 97) {
                    item = Item.ONE_UP;
                } else {
                    item = Item.INVINCIBILITY;
                }
                spots.add(new Spot(Kind.MONITOR, mx, List.of(), item, rings));
            }
        }
        spots.sort((a, b) -> Double.compare(a.homeX, b.homeX));
    }

    private boolean near(double at, double radius) {
        for (Spot spot : spots) if (spot.kind != Kind.ENCOUNTER && Math.abs(spot.homeX - at) < radius) return true;
        return false;
    }

    public double x() { return x; }
    public boolean facingLeft() { return facingLeft; }
    public boolean moving() { return moving; }
    public boolean running() { return running; }
    public double distance() { return distance; }

    /** Where follower {@code n} (1 or 2) stands: the leader's position a few steps ago. */
    public double followerX(int n) {
        int back = Math.min(TRAIL - 1, n * FOLLOW_GAP);
        return trail[Math.floorMod(trailHead - back, TRAIL)];
    }

    /** Places the leader (debug jumps, returning from a battle, resuming at a Starpost). */
    public void setX(double value) {
        x = Math.max(path.startX(), Math.min(path.endX(), value));
        for (int i = 0; i < TRAIL; i++) trail[i] = x;
    }

    /**
     * Moves the party one tick: {@code direction} is -1, 0 or 1. Returns the first spot the
     * leader touches, or null. Walking off the start of the path is reported by
     * {@link #atStart()}; reaching the end is impossible past an unbeaten boss.
     */
    public Spot step(int direction, boolean run, long ticks) {
        moving = direction != 0;
        running = moving && run;
        if (moving) {
            facingLeft = direction < 0;
            double speed = running ? RUN : WALK;
            double next = x + direction * speed;
            Spot blocker = firstUndone(Kind.BOSS);
            if (blocker != null && next > blocker.homeX) next = blocker.homeX;
            x = Math.max(path.startX(), Math.min(path.endX(), next));
            distance += speed;
            trailHead = (trailHead + 1) % TRAIL;
            trail[trailHead] = x;
        }
        if (grace > 0) grace--;
        for (Spot spot : spots) {
            if (spot.done || (grace > 0 && spot.kind == Kind.ENCOUNTER)) continue;
            if (Math.abs(spot.x(ticks) - x) <= TOUCH) return spot;
        }
        return null;
    }

    /** After escaping a battle: step back and ignore badniks for a moment. */
    public void retreat(Spot from) {
        setX(x + (x >= from.homeX ? 48 : -48));
        grace = 120;
    }

    private Spot firstUndone(Kind kind) {
        for (Spot spot : spots) if (spot.kind == kind && !spot.done) return spot;
        return null;
    }

    public boolean atStart() {
        return x <= path.startX() + 0.01;
    }

    /** The spot nearest ahead of the leader that still matters, for the HUD arrow. */
    public Spot nextAhead() {
        for (Spot spot : spots) if (!spot.done && spot.homeX > x) return spot;
        return null;
    }

    /** Debug/test helper: marks everything before {@code toX} done and walks there. */
    public void skipTo(double toX) {
        for (Spot spot : spots) if (spot.homeX < toX && spot.kind != Kind.BOSS) spot.done = true;
        setX(toX);
    }

    public Spot boss() {
        for (Spot spot : spots) if (spot.kind == Kind.BOSS) return spot;
        throw new IllegalStateException("A zone always has a boss");
    }
}
