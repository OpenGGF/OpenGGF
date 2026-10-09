package threeislands.field;

import java.util.ArrayList;
import java.util.List;
import threeislands.core.EnemyKind;
import threeislands.core.Item;
import threeislands.core.Progress;
import threeislands.core.Zone;

/** An explorable two-dimensional area. Terrain, interaction and encounter distance share coordinates. */
public final class Field {
    public enum Kind { ENCOUNTER, MONITOR, STARPOST, DISCOVERY, FRIEND, MERCHANT, MIDBOSS, BOSS }
    public static final int WIDTH = 960, HEIGHT = 640;
    public static final double WALK = 1.6, RUN = 2.8;
    public static final int TRAIL = 96, FOLLOW_GAP = 12, TOUCH = 18;

    public static final class Spot {
        public final Kind kind;
        public final double homeX, homeY;
        public final List<EnemyKind> group;
        public final Item item;
        public final int rings;
        public final String id, label;
        public boolean done;

        Spot(Kind kind, int x, int y, List<EnemyKind> group, Item item, int rings, String id, String label) {
            this.kind = kind;
            homeX = x;
            homeY = y;
            this.group = group;
            this.item = item;
            this.rings = rings;
            this.id = id;
            this.label = label;
        }
        public double x(long ticks) {
            return homeX + (kind == Kind.ENCOUNTER ? Math.sin(ticks / 70.0 + homeX) * 12 : 0);
        }
        public boolean hostile() { return kind == Kind.ENCOUNTER || kind == Kind.MIDBOSS || kind == Kind.BOSS; }
    }

    public final Zone zone;
    /** The stock act route is retained solely for battle scenery. */
    public final FieldPath path;
    public final List<Spot> spots = new ArrayList<>();
    private double x = 88, y = 336;
    private boolean facingLeft, moving, running;
    private final double[] trail = new double[TRAIL], trailY = new double[TRAIL];
    private int trailHead, grace;
    private double distance;

    public Field(Zone zone, FieldPath path) {
        this.zone = zone;
        this.path = path;
        place();
        resetTrail();
    }

    private void add(Kind kind, int x, int y, String id, String label) {
        spots.add(new Spot(kind, x, y, List.of(), null, 0, id, label));
    }

    private String memoryLabel() {
        return switch (zone) {
            case GREEN_HILL -> "Broken shrine";
            case STAR_LIGHT -> "Observatory log";
            case SPRING_YARD -> "Freight records";
            case EMERALD_HILL -> "Old workshop";
            case CHEMICAL_PLANT -> "Pump chart";
            case MYSTIC_CAVE -> "Mine ledger";
            case ANGEL_ISLAND -> "Guardian memorial";
            case HYDROCITY -> "Ancient mural";
            case LAUNCH_BASE -> "Flight records";
            case DEATH_EGG -> "Sky archive";
        };
    }

    private void place() {
        add(Kind.STARPOST, 144, 336, "camp", "Trail camp");
        add(Kind.MERCHANT, 192, 416, "merchant", "Pocky's travelling stall");
        add(Kind.FRIEND, 208, 288, "friend", "Stranded traveller");
        add(Kind.DISCOVERY, 240, 144, "memory", memoryLabel());
        add(Kind.DISCOVERY, 736, 496, "signal", "Anchor relay");
        add(Kind.STARPOST, 784, 240, "sanctuary", "Sanctuary");
        spots.add(new Spot(Kind.MONITOR, 112, 528, List.of(), Item.SUPER_RING, 0, "cache-a", "Medicine cache"));
        spots.add(new Spot(Kind.MONITOR, 512, 112, List.of(), Item.BLUE_SPHERE, 0, "cache-b", "Supply cache"));
        spots.add(new Spot(Kind.MONITOR, 864, 544, List.of(), Item.ONE_UP, 0, "cache-c", "Hidden cache"));
        int[][] camps = {{320,336}, {352,160}, {600,160}, {304,512}, {624,480}, {832,352}};
        List<EnemyKind> enemies = zone.enemyKinds();
        for (int i = 0; i < camps.length; i++) {
            List<EnemyKind> group = new ArrayList<>();
            int count = i == 0 ? 1 : zone.islandIndex == 0 ? 1 + i % 2 : 2;
            for (int n = 0; n < count; n++) group.add(enemies.get((i + n + zone.ordinal()) % enemies.size()));
            spots.add(new Spot(Kind.ENCOUNTER, camps[i][0], camps[i][1], List.copyOf(group), null, 0,
                    "foe-" + i, "Badnik patrol"));
        }
        List<EnemyKind> bosses = zone.bossKinds();
        for (int i = 0; i < bosses.size() - 1; i++) {
            spots.add(new Spot(Kind.MIDBOSS, 704, 112 + i * 64, List.of(bosses.get(i)), null, 0,
                    "mid-" + i, "Anchor guardian"));
        }
        spots.add(new Spot(Kind.BOSS, 848, 128, List.of(bosses.get(bosses.size() - 1)), null, 0,
                "boss", "Rift anchor"));
    }

    public String key(Spot spot) { return zone.key + "-field-" + spot.id; }
    public void restore(Progress progress) {
        for (Spot spot : spots) spot.done = progress.seen(key(spot))
                || (progress.isCleared(zone) && (spot.kind == Kind.BOSS || spot.kind == Kind.MIDBOSS));
    }
    public void complete(Progress progress, Spot spot) {
        spot.done = true;
        progress.markSeen(key(spot));
    }
    public int discoveries() {
        int n = 0;
        for (Spot spot : spots) if (spot.kind == Kind.DISCOVERY && spot.done) n++;
        return n;
    }
    public boolean bossReady() {

        for (Spot spot : spots) if (spot.kind == Kind.MIDBOSS && !spot.done) return false;
        return true;
    }
    public String objective() {
        if (boss().done) return "The eastern trail is open. Keep exploring, or follow it.";
        if (!bossReady()) return "Defeat the guardian at the northern crossing.";
        return "Reach the rift anchor in the northeast.";
    }

    /** A lake/shaft divides the clearing; north and south paths reconnect around it. */
    public boolean walkable(double px, double py) {
        if (px < 48 || px > WIDTH - 48 || py < 88 || py > HEIGHT - 48) return false;
        // The central bridge is broad enough for the whole formation.
        boolean lake = px > 400 && px < 560 && py > 224 && py < 432;
        boolean bridge = py >= 308 && py <= 356;
        if (lake && !bridge) return false;
        return !(px > 112 && px < 176 && py > 144 && py < 224)
                && !(px > 784 && px < 880 && py > 400 && py < 464);
    }
    public double x() { return x; }
    public double y() { return y; }
    public boolean facingLeft() { return facingLeft; }
    public boolean moving() { return moving; }
    public boolean running() { return running; }
    public double distance() { return distance; }
    public double followerX(int n) { return trail[Math.floorMod(trailHead - Math.min(TRAIL - 1, n * FOLLOW_GAP), TRAIL)]; }
    public double followerY(int n) { return trailY[Math.floorMod(trailHead - Math.min(TRAIL - 1, n * FOLLOW_GAP), TRAIL)]; }
    private void resetTrail() {
        for (int back = 0; back < TRAIL; back++) {
            int index = Math.floorMod(trailHead - back, TRAIL);
            double offset = Math.min(back, FOLLOW_GAP * 2) * WALK;
            double px = x - offset, py = y;
            if (!walkable(px, py)) { px = x; py = y - offset; }
            trail[index] = walkable(px, py) ? px : x;
            trailY[index] = walkable(px, py) ? py : y;
        }
    }
    public void setPosition(double px, double py) {
        if (!walkable(px, py)) return;
        x = px; y = py; resetTrail();
    }
    public void setX(double value) { setPosition(Math.max(48, Math.min(WIDTH - 48, value)), y); }

    public Spot step(int dx, boolean run, long ticks) { return step(dx, 0, run, ticks); }
    public Spot step(int dx, int dy, boolean run, long ticks) {
        double speed = run ? RUN : WALK;
        double norm = dx != 0 && dy != 0 ? Math.sqrt(2) : 1;
        double oldX = x, oldY = y;
        if (dx != 0) facingLeft = dx < 0;
        double nx = x + Math.signum(dx) * speed / norm;
        if (walkable(nx, y)) x = nx;
        double ny = y + Math.signum(dy) * speed / norm;
        if (walkable(x, ny)) y = ny;
        moving = oldX != x || oldY != y;
        running = moving && run;
        if (moving) {
            distance += Math.hypot(x - oldX, y - oldY);
            trailHead = (trailHead + 1) % TRAIL;
            trail[trailHead] = x; trailY[trailHead] = y;
        }
        if (grace > 0) grace--;
        for (Spot spot : spots) {
            if (spot.done || !spot.hostile() || grace > 0) continue;
            // Bosses are deliberate interactions so players can prepare first.
            if (spot.kind != Kind.ENCOUNTER) continue;
            if (Math.hypot(spot.x(ticks) - x, spot.homeY - y) <= TOUCH) return spot;
        }
        return null;
    }
    public Spot nearby(long ticks) {
        Spot best = null;
        double nearest = 38;
        for (Spot spot : spots) {
            if (spot.kind == Kind.ENCOUNTER || (spot.done && spot.kind != Kind.STARPOST && spot.kind != Kind.FRIEND && spot.kind != Kind.MERCHANT)) continue;
            double d = Math.hypot(spot.x(ticks) - x, spot.homeY - y);
            if (d < nearest) { nearest = d; best = spot; }
        }
        return best;
    }
    public void retreat(Spot from) {
        // Stay on valid ground, even when the encounter was on a bridge.
        for (int direction : new int[] {-1, 1}) {
            double nx = x + direction * 48;
            if (walkable(nx, y)) { setPosition(nx, y); break; }
        }
        grace = 150;
    }
    public boolean atStart() { return x <= 64 && Math.abs(y - 336) < 44; }
    public Spot nextAhead() {
        for (Spot spot : spots) if (!spot.done && spot.kind == Kind.DISCOVERY) return spot;
        return boss();
    }
    /** Debug only. Runtime resumes restore a camp, never infer completed content from position. */
    public void skipTo(double toX) { setPosition(Math.max(48, Math.min(900, toX)), 336); }
    public Spot boss() {
        for (Spot spot : spots) if (spot.kind == Kind.BOSS) return spot;
        throw new IllegalStateException("Missing anchor");
    }
    public void resumeAtCamp(int saved) {
        // New checkpoints use small identifiers; old side-on X coordinates return to the entrance camp.
        setPosition(saved == 2 ? 784 : 144, saved == 2 ? 264 : 360);
    }
}
