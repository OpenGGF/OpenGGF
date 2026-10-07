package survivors;

import com.openggf.audio.GameSound;
import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.*;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import java.util.List;

/**
 * Something Sonic collects: a dropped ring (worth {@code value} rings and experience, drawn with
 * the ROM ring), a monitor dropped by an elite badnik (ROM monitor art; any touch breaks it), or
 * the Chaos Emerald a zone boss leaves behind. Rings bounce to rest on the floor and fly to Sonic
 * inside his magnet radius. Collection is a simple overlap test rather than a touch response, so
 * a pile of rings never competes with badniks for the touch pass.
 */
public final class Pickup extends AbstractObjectInstance implements RewindRecreatable {
    static final int REWARD_LIFETIME = 60 * 60;
    static final int LOST_RING_LIFETIME = 45;
    static final int RING = 0, MONITOR = 1, EMERALD = 2, CHEST = 3;
    /** The invincibility monitor matches Fever's scale rather than stock's twenty seconds. */
    static final int MONITOR_INVINCIBILITY = 10 * 60;
    // Monitor contents, by ROM monitor mapping frame.
    static final int MON_RINGS = 5, MON_SHOES = 6, MON_SHIELD = 7, MON_STARS = 8, MON_EGGMAN = 4, MON_MAGNET = 10;

    private int x;
    private int y;
    private int vx;
    private int vy;
    private int subX;
    private int subY;
    private int age;
    private int rewardAge;
    private boolean resting;
    private boolean homing;
    private boolean lostRing;
    private int collected = -1;
    private int value;

    public Pickup(ObjectSpawn spawn) {
        this(spawn, 0, 0, 1);
    }

    /** {@code value}: a ring pickup's worth, a monitor's ROM frame, or an emerald's stage. */
    public Pickup(ObjectSpawn spawn, int vx, int vy, int value) {
        super(spawn, "Survivors pickup");
        x = spawn.x();
        y = spawn.y();
        this.vx = vx;
        this.vy = vy;
        this.value = value;
    }

    /** A ring hanging in the air, as stock level rings do, until Sonic or his magnet takes it. */
    static Pickup floating(int x, int y, int value) {
        var ring = new Pickup(spawnAt(x, y, RING), 0, 0, Math.max(1, value));
        ring.resting = true;
        ring.age = 20;
        return ring;
    }

    static Pickup lost(ObjectSpawn spawn, int vx, int vy) {
        var ring = new Pickup(spawn, vx, vy, 1);
        ring.lostRing = true;
        return ring;
    }

    boolean lostRing() { return lostRing; }

    static ObjectSpawn spawnAt(int x, int y, int kind) {
        return new ObjectSpawn(x, y, 0, kind & 0xFF, 0, false, y, -1, SurvivorsMod.ID, SurvivorsMod.ID + ":pickup");
    }

    int kind() { return spawn.subtype(); }
    boolean collectedAlready() { return collected >= 0; }
    int value() { return value; }
    /** Folds extra rings into this pickup, so a big ring pile never exhausts object slots. */
    void addValue(int more) { value += more; rewardAge = 0; }
    int ringTier() { return value >= 125 ? 3 : value >= 25 ? 2 : value >= 5 ? 1 : 0; }

    boolean mergeable() {
        return kind() == RING && !lostRing && !isDestroyed() && collected < 0 && age > 12;
    }

    void absorb(Pickup other) {
        value += other.value;
        if (other.homing) homeIn();
        // Existing rewards are not fresh drops: preserve the younger remaining lifetime,
        // never reset it to a minute on each consolidation.
        rewardAge = Math.min(rewardAge, other.rewardAge);
        other.setDestroyed(true);
    }

    /** The ring magnet monitor: fly to Sonic from anywhere. */
    void homeIn() {
        if (lostRing) return;
        homing = true;
        resting = false;
        age = Math.max(age, 21);
    }

    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public boolean isPersistent() { return !isDestroyed(); }
    @Override public AbstractObjectInstance recreateForRewind(RewindRecreateContext context) {
        return new Pickup(context.spawn());
    }

    @Override public void update(int vIntRunCount, PlayableEntity entity) {
        if (collected >= 0) {
            if (++collected > 24) setDestroyed(true);
            return;
        }
        var run = services().gameService(RunState.class);
        if (run != null && run.paused) return;
        age++;
        // Uncollected rewards must eventually free their slots during long/endless runs.
        // Merging fresh rings refreshes rewardAge without restarting their movement clock.
        boolean expired = lostRing ? age >= LOST_RING_LIFETIME
                : kind() != EMERALD && kind() != CHEST && ++rewardAge >= REWARD_LIFETIME;
        if (expired) { setDestroyed(true); return; }
        if (!(entity instanceof AbstractPlayableSprite player) || player.getDead()) { fall(); return; }
        int dx = player.getCentreX() - x, dy = player.getCentreY() - y;
        int magnet = kind() == RING && run != null ? run.magnetRadius() : 0;
        if (kind() == RING && !lostRing && age > 20 && (homing || dx * dx + dy * dy < magnet * magnet)) {
            homing = true;
            double length = Math.max(1, Math.hypot(dx, dy));
            int speed = 0x500 + Math.min(age, 120) * 8;
            vx = (int) (dx / length * speed);
            vy = (int) (dy / length * speed);
            move();
        } else {
            fall();
        }
        if (Math.abs(dx) < 16 && Math.abs(dy) < 20 && (age > 12 || kind() != RING)
                && player.getInvulnerableFrames() < 90) {
            collect(player);
        }
        updateDynamicSpawn(x, y);
    }

    private void move() {
        subX += vx;
        subY += vy;
        x += subX >> 8;
        y += subY >> 8;
        subX &= 0xFF;
        subY &= 0xFF;
    }

    private void fall() {
        if (resting) return;
        vy = Math.min(vy + 0x30, 0x700);
        move();
        var arena = services().gameService(Arena.class);
        if (arena != null) {
            int clamped = arena.clampX(x, 8);
            if (clamped != x) { x = clamped; vx = -vx; }
        }
        var r = ObjectTerrainUtils.checkFloorDist(services().levelManager(), x, y + 8);
        if (vy > 0 && r.foundSurface() && r.distance() <= 0 && r.distance() > -16) {
            y += r.distance();
            if (vy > 0x200 && kind() == RING) {
                vy = -vy / 2;
                vx = vx * 3 / 4;
            } else {
                vy = 0;
                vx = 0;
                resting = true;
            }
        }
        if (arena != null && y > arena.floorBottom() + 200) setDestroyed(true);
    }

    private void collect(AbstractPlayableSprite player) {
        collected = 0;
        var run = services().gameService(RunState.class);
        Stage stage = Stage.find(services());
        switch (kind()) {
            case RING -> {
                playRingSound(run, value);
                player.addRings(value);
                if (run != null && !lostRing) {
                    run.ringsCollected += value;
                    double xp = value * run.xpScale();
                    int whole = (int) xp;
                    if (run.chance(xp - whole)) whole++;
                    run.gainXp(whole);
                }
            }
            case EMERALD -> {
                if (stage != null) stage.onEmeraldCollected();
            }
            case CHEST -> {
                if (stage != null) stage.openChest(value);
            }
            default -> breakMonitor(player, run, stage);
        }
    }

    private void playRingSound(RunState run, int amount) {
        if (run == null || run.ringSound(amount)) services().audioManager().playSecondarySfx(GameSound.RING);
    }

    private void breakMonitor(AbstractPlayableSprite player, RunState run, Stage stage) {
        services().playSfx(0xC1);
        switch (value) {
            case MON_RINGS -> {
                // Two hits' worth: a fixed ten rings would be nothing against a late-route toll.
                int rings = superRingValue(run, player);
                playRingSound(run, rings);
                player.addRings(rings);
                if (run != null) { run.ringsCollected += rings; run.gainXp((int) Math.round(rings * run.xpScale())); }
                if (stage != null) stage.banner("SUPER RING +" + rings, 90);
                return;
            }
            case MON_SHOES -> player.giveSpeedShoes();
            case MON_SHIELD -> player.giveShield();
            case MON_STARS -> {
                player.giveInvincibility();
                player.setInvincibleFrames(MONITOR_INVINCIBILITY);
            }
            case MON_EGGMAN -> { if (stage != null) stage.screenNuke(); }
            default -> { if (stage != null) stage.magnetSweep(); }
        }
        if (stage != null) stage.banner(monitorName(value), 90);
    }

    static int superRingValue(RunState run, AbstractPlayableSprite player) {
        // The base toll (rings held excluded): reading the held total would let rings compound.
        return run == null ? 10 : Math.max(10, 2 * run.toll(0));
    }

    static String monitorName(int frame) {
        return switch (frame) {
            case MON_RINGS -> "SUPER RING +10";
            case MON_SHOES -> "SPEED SHOES";
            case MON_SHIELD -> "SHIELD";
            case MON_STARS -> "INVINCIBLE";
            case MON_EGGMAN -> "EGGMAN BOMB!";
            default -> "RING MAGNET";
        };
    }

    float lostRingAlpha() { return Math.max(0f, 1f - age / (float) LOST_RING_LIFETIME); }

    @Override public int getPriorityBucket() { return RenderPriority.bucket(collected >= 0 ? 1 : 3); }

    @Override public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed()) return;
        boolean blinking = !lostRing && rewardAge >= REWARD_LIFETIME - 60;
        if (collected < 0 && kind() != EMERALD && blinking && (age & 4) != 0) return;
        if (kind() == RING) {
            var rings = services().ringManager();
            if (rings == null) return;
            if (ringTier() == 0 && !lostRing && Stage.menuShowing(services())) return;
            if (collected >= 0) {
                rings.drawSparkleAt(x, y, collected / Math.max(1, rings.getSparkleFrameDelay()));
                return;
            }
            if (lostRing) {
                // A translucent golden loop uses the mod's alpha-capable geometry path.
                Draw.circleWorld(services(), x, y, 5, 1, Draw.YELLOW, lostRingAlpha());
                return;
            }
            int tier = ringTier();
            if (tier == 0) rings.drawRingAt(x, y, age);
            else drawCluster(tier);
            return;
        }
        if (kind() == CHEST) {
            if (collected >= 0) return;
            drawChest(x, y);
            return;
        }
        if (kind() == EMERALD) {
            if (collected >= 0) return;
            drawEmerald(x, y + (int) Math.round(Math.sin(age * 0.1) * 3));
            return;
        }
        var renderer = getRenderer(ObjectArtKeys.MONITOR);
        if (renderer == null) return;
        renderer.drawFrameIndex(collected >= 0 ? 11 : (age / 4) % 8 == 0 ? 1 : value, x, y, false, false);
    }

    /** Cached, hollow coloured rings, without per-frame value strings or geometry building.
     * The normal ROM ring remains the small yellow denomination. */
    private void drawCluster(int tier) {
        int radius = 7 + tier * 3;
        int colour = tier == 1 ? Draw.CYAN : tier == 2 ? Draw.PURPLE : Draw.RED;
        var s = services();
        int[] rectangles = s.gameService(MenuArt.class).ringShapes[tier - 1];
        for (int p = 0; p < rectangles.length; p += 4) {
            Draw.rectWorld(s, x - radius + rectangles[p], y - radius + rectangles[p + 1],
                    rectangles[p + 2], rectangles[p + 3], colour, 1f);
        }
        Draw.rectWorld(s, x - radius * 3 / 5, y - radius * 4 / 5, 2, 2, Draw.WHITE, 1f);
    }

    /** A code-drawn treasure chest: gold-trimmed, a five-prize chest glows pink. */
    private void drawChest(int cx, int cy) {
        var s = services();
        int glow = value >= 5 ? Draw.PINK : value >= 3 ? Draw.GOLD : Draw.CYAN;
        float pulse = 0.35f + 0.25f * (float) Math.sin(age * 0.15);
        Draw.circleWorld(s, cx, cy - 2, 14, 2, glow, pulse);
        Draw.rectWorld(s, cx - 10, cy - 8, 20, 15, Draw.NAVY, 1f);
        Draw.rectWorld(s, cx - 9, cy - 7, 18, 13, 0x8A4A1C, 1f);
        Draw.rectWorld(s, cx - 9, cy - 7, 18, 4, 0xB0642A, 1f);
        Draw.rectWorld(s, cx - 9, cy - 3, 18, 2, Draw.GOLD, 1f);
        Draw.rectWorld(s, cx - 2, cy - 4, 4, 5, Draw.GOLD, 1f);
        Draw.rectWorld(s, cx - 1, cy - 3, 2, 2, Draw.NAVY, 1f);
        if (age / 8 % 4 == 0) Draw.rectWorld(s, cx + 6, cy - 9, 2, 2, Draw.WHITE, 1f);
    }

    /** A code-drawn cut gem, sparkling: the run's emerald colour from the boss's stage. */
    private void drawEmerald(int cx, int cy) {
        int colour = switch (value % 7) {
            case 0 -> 0x40E040;
            case 1 -> 0xE0E040;
            case 2 -> 0x4080FF;
            case 3 -> 0xFF60A0;
            case 4 -> 0xE04040;
            case 5 -> 0xE0E0E0;
            default -> 0x40E0E0;
        };
        var s = services();
        for (int dy = -8; dy <= 8; dy++) {
            int half = dy < 0 ? 4 + (8 + dy) / 2 : 8 - dy;
            Draw.rectWorld(s, cx - half - 1, cy + dy, half * 2 + 3, 1, Draw.NAVY, 1f);
            Draw.rectWorld(s, cx - half, cy + dy, half * 2 + 1, 1, colour, 1f);
        }
        Draw.rectWorld(s, cx - 2, cy - 6, 2, 3, Draw.WHITE, 1f);
        if (age / 8 % 4 == 0) {
            Draw.rectWorld(s, cx + 5, cy - 9, 1, 5, Draw.WHITE, 1f);
            Draw.rectWorld(s, cx + 3, cy - 7, 5, 1, Draw.WHITE, 1f);
        }
    }
}
