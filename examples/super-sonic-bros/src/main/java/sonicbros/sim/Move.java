package sonicbros.sim;

import java.util.ArrayList;
import java.util.List;

/**
 * Frame data for one attack or special: its length, the pose shown each frame, its hitboxes,
 * the velocity it applies, projectiles it fires, and the few behaviours (charging, looping until
 * landing, homing, gliding) that make the specials individual. Built once per roster with
 * {@link Builder}; immutable afterwards.
 */
public final class Move {
    /** Velocity changes at a frame, relative to facing; NaN leaves that axis alone. */
    public record Impulse(int frame, double vx, double vy, boolean add, boolean scaleByCharge) { }

    /** A projectile fired at a frame from an offset (x forward, y down from body centre). */
    public record Spawn(int frame, Projectile.Def def, double x, double y) { }

    /** A sound effect id played at a frame. */
    public record Sound(int frame, int sfx) { }

    /** Custom behaviour for specials that need more than frame data. */
    public enum Custom { NONE, HOMING, GLIDE, FLIGHT, SPIN_DASH, SPRING, HOVER_ROLL, COUNTER, ROLLING }

    public final String id;
    public final String name;
    public final int duration;
    public final int[] pose;
    public final List<Hit> hits;
    public final List<Impulse> impulses;
    public final List<Spawn> spawns;
    public final List<Sound> sounds;
    public final int landingLag;
    public final int intangibleFrom;
    public final int intangibleTo;
    public final int armorFrom;
    public final int armorTo;
    public final int reflectFrom;
    public final int reflectTo;
    public final int chargeFrame;
    public final int holdButton;
    public final int loopFrom;
    public final int loopTo;
    public final int maxHold;
    public final int landLoopFrom;
    public final int landLoopTo;
    public final int landFrame;
    public final boolean helpless;
    public final double airControl;
    public final int gravityFrom;
    public final int gravityTo;
    public final double gravityMul;
    public final Custom custom;
    public final boolean ledgeGrab;
    public final int fastFallFrom;
    public final boolean groundFriction;

    private Move(Builder b) {
        id = b.id;
        name = b.name;
        duration = b.duration;
        pose = new int[duration];
        int cursor = 0;
        int lastPose = b.poses.isEmpty() ? 0 : b.poses.get(0)[0];
        for (int[] entry : b.poses) {
            for (int i = 0; i < entry[1] && cursor < duration; i++) pose[cursor++] = entry[0];
            lastPose = entry[0];
        }
        while (cursor < duration) pose[cursor++] = lastPose;
        hits = List.copyOf(b.hits);
        impulses = List.copyOf(b.impulses);
        spawns = List.copyOf(b.spawns);
        sounds = List.copyOf(b.sounds);
        landingLag = b.landingLag;
        intangibleFrom = b.intangibleFrom;
        intangibleTo = b.intangibleTo;
        armorFrom = b.armorFrom;
        armorTo = b.armorTo;
        reflectFrom = b.reflectFrom;
        reflectTo = b.reflectTo;
        chargeFrame = b.chargeFrame;
        holdButton = b.holdButton;
        loopFrom = b.loopFrom;
        loopTo = b.loopTo;
        maxHold = b.maxHold;
        landLoopFrom = b.landLoopFrom;
        landLoopTo = b.landLoopTo;
        landFrame = b.landFrame;
        helpless = b.helpless;
        airControl = b.airControl;
        gravityFrom = b.gravityFrom;
        gravityTo = b.gravityTo;
        gravityMul = b.gravityMul;
        custom = b.custom;
        ledgeGrab = b.ledgeGrab;
        fastFallFrom = b.fastFallFrom;
        groundFriction = b.groundFriction;
    }

    public static Builder of(String id, String name, int duration) {
        return new Builder(id, name, duration);
    }

    /** The first frame any hitbox is live, or -1. */
    public int startup() {
        int first = -1;
        for (Hit h : hits) if (first < 0 || h.start() < first) first = h.start();
        return first;
    }

    /** The largest single hit's damage. */
    public double maxDamage() {
        double best = 0;
        for (Hit h : hits) best = Math.max(best, h.damage());
        for (Spawn s : spawns) best = Math.max(best, s.def().hit().damage());
        return best;
    }

    /** A rough knockback score at 100% on a middleweight, for the CPU to pick finishers. */
    public double killPower() {
        double best = 0;
        for (Hit h : hits) best = Math.max(best, Knockback.of(h.damage(), 100 + h.damage(), 100, h.baseKb(), h.growth()));
        return best;
    }

    /** {forward min, forward max, up (negative), down} reach of the hitboxes, for the CPU. */
    public double[] reach() {
        double minX = 0, maxX = 0, minY = 0, maxY = 0;
        boolean any = false;
        for (Hit h : hits) {
            if (!any) { minX = h.x() - h.r(); maxX = h.x() + h.r(); minY = h.y() - h.r(); maxY = h.y() + h.r(); any = true; }
            minX = Math.min(minX, h.x() - h.r());
            maxX = Math.max(maxX, h.x() + h.r());
            minY = Math.min(minY, h.y() - h.r());
            maxY = Math.max(maxY, h.y() + h.r());
        }
        return new double[] {minX, maxX, minY, maxY};
    }

    public static final class Builder {
        private final String id;
        private final String name;
        private final int duration;
        private final List<int[]> poses = new ArrayList<>();
        private final List<Hit> hits = new ArrayList<>();
        private final List<Impulse> impulses = new ArrayList<>();
        private final List<Spawn> spawns = new ArrayList<>();
        private final List<Sound> sounds = new ArrayList<>();
        private int landingLag = 8;
        private int intangibleFrom = -1;
        private int intangibleTo = -1;
        private int armorFrom = -1;
        private int armorTo = -1;
        private int reflectFrom = -1;
        private int reflectTo = -1;
        private int chargeFrame = -1;
        private int holdButton;
        private int loopFrom = -1;
        private int loopTo = -1;
        private int maxHold;
        private int landLoopFrom = -1;
        private int landLoopTo = -1;
        private int landFrame = -1;
        private boolean helpless;
        private double airControl = 1.0;
        private int gravityFrom = -1;
        private int gravityTo = -1;
        private double gravityMul = 1.0;
        private Custom custom = Custom.NONE;
        private boolean ledgeGrab;
        private int fastFallFrom = -1;
        private boolean groundFriction = true;

        private Builder(String id, String name, int duration) {
            this.id = id;
            this.name = name;
            this.duration = duration;
        }

        /** Shows {@code pose} for {@code frames} frames, after the poses already given. */
        public Builder pose(int pose, int frames) { poses.add(new int[] {pose, frames}); return this; }

        /** Cycles through {@code poses}, {@code each} frames apiece, for {@code frames} frames. */
        public Builder cycle(int frames, int each, int... cycle) {
            for (int i = 0; i < frames; i += each) poses.add(new int[] {cycle[(i / each) % cycle.length], Math.min(each, frames - i)});
            return this;
        }

        public Builder hit(Hit hit) { hits.add(hit); return this; }

        /** A hit from {@code start} to {@code end} in its own new group. */
        public Builder hit(int start, int end, double x, double y, double r, double damage, int angle, double bkb, double kbg) {
            hits.add(Hit.of(start, end, x, y, r, damage, angle, bkb, kbg).group(hits.size()));
            return this;
        }

        public Builder setV(int frame, double vx, double vy) { impulses.add(new Impulse(frame, vx, vy, false, false)); return this; }

        public Builder addV(int frame, double vx, double vy) { impulses.add(new Impulse(frame, vx, vy, true, false)); return this; }

        /** Sets velocity scaled by the move's charge (1 to 2). */
        public Builder chargedV(int frame, double vx, double vy) { impulses.add(new Impulse(frame, vx, vy, false, true)); return this; }

        public Builder spawn(int frame, Projectile.Def def, double x, double y) { spawns.add(new Spawn(frame, def, x, y)); return this; }

        public Builder sound(int frame, int sfx) { sounds.add(new Sound(frame, sfx)); return this; }

        public Builder landingLag(int frames) { landingLag = frames; return this; }

        public Builder intangible(int from, int to) { intangibleFrom = from; intangibleTo = to; return this; }

        public Builder armor(int from, int to) { armorFrom = from; armorTo = to; return this; }

        public Builder reflect(int from, int to) { reflectFrom = from; reflectTo = to; return this; }

        /** A smash attack: holding attack at {@code frame} charges it for up to a second. */
        public Builder charge(int frame) { chargeFrame = frame; return this; }

        /** While {@code button} is held, frames {@code from..to-1} repeat, up to {@code max} frames. */
        public Builder holdLoop(int button, int from, int to, int max) {
            holdButton = button; loopFrom = from; loopTo = to; maxHold = max; return this;
        }

        /** Frames {@code from..to-1} repeat until the fighter lands, then the move jumps to {@code land}. */
        public Builder untilLand(int from, int to, int land) { landLoopFrom = from; landLoopTo = to; landFrame = land; return this; }

        public Builder helpless() { helpless = true; return this; }

        public Builder airControl(double factor) { airControl = factor; return this; }

        public Builder gravity(int from, int to, double mul) { gravityFrom = from; gravityTo = to; gravityMul = mul; return this; }

        public Builder custom(Custom value) { custom = value; return this; }

        /** The fighter may catch a ledge during this move (recoveries). */
        public Builder ledgeGrab() { ledgeGrab = true; return this; }

        public Builder fastFallFrom(int frame) { fastFallFrom = frame; return this; }

        /** Grounded momentum is kept rather than slowed by friction (dashes and rolls). */
        public Builder slide() { groundFriction = false; return this; }

        public Move build() { return new Move(this); }
    }
}
