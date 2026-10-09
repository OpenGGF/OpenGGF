package threeislands.field;

import java.util.ArrayList;
import java.util.List;
import threeislands.core.EnemyKind;
import threeislands.core.Item;
import threeislands.core.Progress;
import threeislands.core.Zone;

/** An explorable two-dimensional area. Terrain, interaction and encounter distance share coordinates. */
public final class Field {
    public enum Kind { ENCOUNTER, MONITOR, STARPOST, DISCOVERY, FRIEND, MERCHANT, MIDBOSS, BOSS, DUNGEON, GUARDIAN, RELIC, CLUE, MECHANISM }
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
        public boolean hostile() { return kind == Kind.ENCOUNTER || kind == Kind.MIDBOSS || kind == Kind.BOSS || kind == Kind.GUARDIAN; }
    }

    public final Zone zone;
    public final Dungeon dungeon;
    /** The stock act route is retained solely for battle scenery. */
    public final FieldPath path;
    public final List<Spot> spots = new ArrayList<>();
    private double x = 88, y = 336;
    private boolean facingLeft, moving, running;
    private final double[] trail = new double[TRAIL], trailY = new double[TRAIL];
    private int trailHead, grace;
    private double distance;
    private int bellPhrase;

    public boolean expanded() { return dungeon == null && zone == Zone.GREEN_HILL; }
    public int width() { return expanded() ? 1536 : WIDTH; }
    public int height() { return expanded() ? 1024 : HEIGHT; }
    public int exitX() { return width() - 64; }

    public Field(Zone zone, FieldPath path) {
        this(zone, path, null);
    }

    public Field(Zone zone, FieldPath path, Dungeon dungeon) {
        this.zone = zone;
        this.path = path;
        this.dungeon = dungeon;
        if (dungeon == null) place();
        else placeDungeon();
        resetTrail();
    }

    private void placeDungeon() {
        add(Kind.STARPOST, 176, 368, "dungeon-rest", "Refuge Starpost");
        for (int i = 0; i < 2; i++) {
            spots.add(new Spot(Kind.GUARDIAN, i == 0 ? 352 : 656, i == 0 ? 336 : dungeon.y(272),
                    dungeon.guards(i), null, 0, "dungeon-guard-" + i, i == 0 ? "Outer sentries" : "Inner sentries"));
        }
        spots.add(new Spot(Kind.ENCOUNTER, 520, dungeon.y(208), dungeon.guards(2), null, 0,
                "dungeon-patrol", "Chamber patrol"));
        spots.add(new Spot(Kind.MONITOR, 544, dungeon.y(432), List.of(), Item.BLUE_SPHERE, 0,
                "dungeon-cache", "Sealed supplies"));
        add(Kind.RELIC, 832, dungeon.y(176), "dungeon-goal", dungeon.goal());
    }

    public boolean dungeonComplete() {
        for (Spot spot : spots) if (spot.kind == Kind.RELIC && spot.done) return true;
        return false;
    }

    public boolean guardDefeated(int index) {
        String id = index == 0 ? "dungeon-guard-0" : "dungeon-guard-1";
        for (Spot spot : spots) if (spot.id.equals(id)) return spot.done;
        return false;
    }

    /** Physical portcullises cannot be bypassed by walking around the sentries. */
    public boolean sealed(double px, double py) {
        if (dungeon == null) return false;
        return (!guardDefeated(0) && px >= 384 && px < 400 && py >= 304 && py <= 368)
                || (!guardDefeated(1) && px >= 688 && px < 704
                    && py >= dungeon.y(272) - 32 && py <= dungeon.y(272) + 32);
    }

    private void add(Kind kind, int x, int y, String id, String label) {
        spots.add(new Spot(kind, x, y, List.of(), null, 0, id, label));
    }

    private void place() {
        add(Kind.STARPOST, 144, 336, "camp", "Trail camp");
        add(Kind.MERCHANT, 192, 416, "merchant", "Pocky's travelling stall");
        add(Kind.FRIEND, 208, 288, "friend", "Stranded traveller");
        add(Kind.DUNGEON, 240, 144, "memory", Dungeon.of(zone).label());
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
        if (expanded()) {
            add(Kind.CLUE, 1152, 672, "garden-verse", "Weathered inscription");
            add(Kind.MECHANISM, 1088, 576, "bell-dawn", "Sunrise bell");
            add(Kind.MECHANISM, 1216, 512, "bell-noon", "High sun bell");
            add(Kind.MECHANISM, 1344, 576, "bell-dusk", "Sunset bell");
            add(Kind.CLUE, 832, 688, "orchard-note", "Water-stained notebook");
            add(Kind.MECHANISM, 880, 768, "sluice-west", "Root-bound wheel");
            add(Kind.MECHANISM, 1456, 864, "sluice-east", "Salt-crusted wheel");
            add(Kind.CLUE, 1232, 832, "orchard-letter", "Tin beneath the roots");
            add(Kind.CLUE, 1440, 160, "horizon", "Split reflection");
            spots.add(new Spot(Kind.MONITOR, 272, 864, List.of(), Item.BLUE_SPHERE, 0, "cache-grove", "Overgrown monitor"));
        }
    }

    public String key(Spot spot) { return zone.key + "-field-" + spot.id; }
    public void restore(Progress progress) {
        for (Spot spot : spots) spot.done = progress.seen(key(spot))
                || (dungeon == null && progress.isCleared(zone) && (spot.kind == Kind.BOSS || spot.kind == Kind.MIDBOSS));
    }
    public void complete(Progress progress, Spot spot) {
        if (spot.kind == Kind.RELIC && (!guardDefeated(0) || !guardDefeated(1))) return;
        spot.done = true;
        progress.markSeen(key(spot));
        if (spot.kind == Kind.RELIC) {
            progress.markSeen(dungeon.completionKey());
            progress.markSeen(zone.key + "-field-memory");
        }
    }
    public int discoveries() {
        int n = 0;
        for (Spot spot : spots) if ((spot.kind == Kind.DISCOVERY || spot.kind == Kind.DUNGEON) && spot.done) n++;
        return n;
    }
    public boolean bossReady() {

        for (Spot spot : spots) if (spot.kind == Kind.MIDBOSS && !spot.done) return false;
        return true;
    }
    /** Optional mysteries never participate in chapter/boss admission. */
    public boolean orchardOpen() { return done("sluice-west") && done("sluice-east"); }
    private boolean done(String id) {
        return spots.stream().anyMatch(spot -> spot.id.equals(id) && spot.done);
    }
    public boolean bellsOpen() { return done("bell-dawn") && done("bell-noon") && done("bell-dusk"); }

    /** A failed phrase starts afresh; solved mechanisms and each wheel survive saves. */
    public String mechanism(Progress progress, Spot spot) {
        if (spot.kind != Kind.MECHANISM || !expanded()) return "";
        if (spot.id.startsWith("sluice-")) {
            if (spot.done) return "The wheel rests against its stop. Water trickles through the stonework.";
            complete(progress, spot);
            return orchardOpen() ? "The two channels answer each other. Water slips away from the orchard's old stone crossing."
                    : "The wheel turns. Water runs along one channel, but the pool still presses back from the other side.";
        }
        if (bellsOpen()) return "The bells carry their little song across the garden. Nothing answers from beneath the stone.";
        String[] phrase = {"bell-dusk", "bell-dawn", "bell-noon"};
        if (!spot.id.equals(phrase[bellPhrase])) {
            bellPhrase = spot.id.equals(phrase[0]) ? 1 : 0;
            return bellPhrase == 1 ? "A low note. Beneath the garden, something begins to hum." : "A lone note fades. The answering hum falls silent.";
        }
        bellPhrase++;
        if (bellPhrase < phrase.length) return bellPhrase == 1 ? "A low note. Beneath the garden, something begins to hum." : "A bright note joins the low one. The stone holds both sounds.";
        bellPhrase = 0;
        for (Spot bell : spots) if (bell.id.startsWith("bell-")) complete(progress, bell);
        progress.markSeen("ghz-garden-song");
        progress.addItem(Item.LIGHTNING_SHIELD, 1);
        return "Three notes linger together. A hollow stone opens: a Lightning Shield beside a child's drawing of three separate skies.";
    }

    /** Water and raised banks are shared with the renderer, including the revealed causeway. */
    public boolean orchardCrossing(double px, double py) {
        return expanded() && orchardOpen() && px >= 1200 && px <= 1248 && py >= 736 && py <= 816;
    }
    public boolean water(double px, double py) {
        if (px > 400 && px < 560 && py > 224 && py < 432 && !(py >= 308 && py <= 356)) return true;
        if (!expanded()) return false;
        if (px > 384 && px < 656 && py > 672 && py < 928 && !(py >= 784 && py <= 832)) return true;
        if (px > 896 && px <= 960 && py > 752 && py < 784) return true;
        if (px >= 1408 && px < 1440 && py > 848 && py < 880) return true;
        boolean pool = px > 960 && px < 1408 && py > 736 && py < 928;
        boolean island = px >= 1184 && px <= 1296 && py >= 800 && py <= 864;
        boolean crossing = orchardCrossing(px, py);
        return pool && !island && !crossing;
    }
    public int[][] banks() {
        return expanded() ? new int[][] {{112,144,64,80},{784,400,96,64},{240,624,112,128},
                {736,832,112,112},{1008,176,192,112},{1040,352,224,64}}
                : new int[][] {{112,144,64,80},{784,400,96,64}};
    }
    public boolean walkable(double px, double py) {
        if (dungeon != null) return dungeon.floor(px, py) && !sealed(px, py);
        if (px < 48 || px > width() - 48 || py < 88 || py > height() - 48 || water(px, py)) return false;
        for (int[] bank : banks()) if (px > bank[0] && px < bank[0] + bank[2]
                && py > bank[1] && py < bank[1] + bank[3]) return false;
        return true;
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
    public void setX(double value) { setPosition(Math.max(48, Math.min(width() - 48, value)), y); }

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
            if (spot.kind == Kind.ENCOUNTER || (spot.done && spot.kind != Kind.STARPOST && spot.kind != Kind.FRIEND && spot.kind != Kind.MERCHANT && spot.kind != Kind.DUNGEON && spot.kind != Kind.RELIC && spot.kind != Kind.CLUE && spot.kind != Kind.MECHANISM)) continue;
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
        for (Spot spot : spots) if (!spot.done && (spot.kind == Kind.DISCOVERY || spot.kind == Kind.DUNGEON || spot.kind == Kind.RELIC)) return spot;
        return boss();
    }
    /** Debug only. Runtime resumes restore a camp, never infer completed content from position. */
    public void skipTo(double toX) { setPosition(Math.max(48, Math.min(exitX(), toX)), 336); }
    public Spot boss() {
        for (Spot spot : spots) if (spot.kind == Kind.BOSS) return spot;
        throw new IllegalStateException("Missing anchor");
    }
    public void resumeAtCamp(int saved) {
        // New checkpoints use small identifiers; old side-on X coordinates return to the entrance camp.
        setPosition(saved == 2 ? 784 : 144, saved == 2 ? 264 : 360);
    }
}
