package infinite;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.*;
import com.openggf.physics.TrigLookupTable;
import java.util.List;

/**
 * A {@link HazardPlan} hazard drawn with the zone's ROM art and hit boxes from the S1 touch
 * table, with mod motion. These are mod objects rather than the stock ones so every hit goes
 * through {@link CourseGuard} (shield, then the 20-ring toll, without knockback) exactly as a
 * badnik hit does, and so they follow the world shift; none is solid, so none can stall Sonic
 * against the scrolling edge. The spawn's Y is the floor; its subtype holds the kind (low
 * nybble) and a seeded timing phase.
 */
public final class CourseHazard extends AbstractObjectInstance
        implements RewindRecreatable, TouchResponseProvider, TouchResponseListener {
    // Touch flags: col_hurt ($80) with React_Sizes entries.
    private static final int HURT_40X32 = 0x8c;  // spike bed (the stock 3-spike mapping is 40x32)
    private static final int HURT_16X16 = 0x8b;  // Obj14 fireball, LZ chain spikeball
    private static final int HURT_32X32 = 0x86;  // Obj58 giant spiked ball
    private static final int HURT_24X48 = 0xa3;  // Obj6D flamethrower
    private static final int HURT_40X40 = 0x81;  // GHZ giant ball

    // Spike bed: Map_Spike frame 0 (three upright spikes) spans y -$10 to +$F.
    private static final int SPIKE_HALF_HEIGHT = 16;

    // Fireball (Obj14 types 1 and 2): launch -$500 or -$600, gravity $18, deleted back at the start.
    private static final int FIREBALL_DEPTH = 24;
    private static final int FIREBALL_GRAVITY = 0x18;
    private static final int FIREBALL_PERIOD = 72;

    // Giant spiked ball (Map_BBall spans +-$18): rolls BALL_TRAVEL px left of its anchor and back.
    static final int BALL_TRAVEL = 96;
    private static final int BALL_RADIUS = 0x18;
    private static final int BALL_PERIOD_SHIFT = 1; // angle step per tick: a 128-tick round trip

    // LZ spiked ball and chain: links every $10 (Obj57 with length 5), outer radius $50, angle
    // step -$180 per tick (subtype $D5); the pivot leaves the spikeball's lowest point on the floor.
    static final int CHAIN_RADIUS = 0x50;
    private static final int CHAIN_PIVOT_HEIGHT = CHAIN_RADIUS + 16;
    private static final int CHAIN_SPEED = -0x180;

    // Flamethrower (Ani_Flame .pipe1/.pipe2): pipe at the bottom of the mapping (+$28 to +$37).
    private static final int FLAME_BASE = 0x38;
    private static final int FLAME_HIT_CENTRE = 40;
    private static final int FLAME_IDLE = 48, FLAME_GROW = 40, FLAME_FULL = 32, FLAME_SHRINK = 6;
    private static final int FLAME_FULL_FRAME = 0x0a;

    // Wrecking ball: a 96px chain from a pivot 120px up; swinging +-$2C (about 62 degrees) in
    // 150 ticks, the 48px ball sweeps the ground at the bottom and clears Sonic's head at the ends.
    static final int WRECKING_REACH = 104;
    private static final int WRECKING_LENGTH = 96;
    private static final int WRECKING_PIVOT_HEIGHT = 120;
    private static final int WRECKING_SWING = 0x2c;
    private static final int WRECKING_PERIOD = 150;

    private int x;
    private int floor;
    private int ticks;
    // Fireball flight: Y in 1/256 px and speed (a -$600 launch passes exactly 0 at the apex).
    private boolean flying;
    private int ballY;
    private int ballSpeed;
    private int wait;
    private int launches;
    private int chainAngle;

    public CourseHazard(ObjectSpawn spawn) {
        super(spawn, "Course hazard");
        x = spawn.x();
        floor = spawn.y();
        ticks = phase();
        chainAngle = phase() << 8;
        wait = 1 + phase() % FIREBALL_PERIOD;
    }
    public int kind() { return spawn.subtype() & 0x0f; }
    private int phase() { return (spawn.subtype() >> 4) & 0xff; }
    public int floor() { return floor; }
    /** The hurt boxes this tick, as {x, y} centres (empty while harmless). */
    public int[][] hurtCentres() {
        var regions = regions();
        int[][] centres = new int[regions.length][];
        for (int i = 0; i < regions.length; i++) centres[i] = new int[]{regions[i].x(), regions[i].y()};
        return centres;
    }
    @Override public int getX() { return x; }
    @Override public int getY() { return floor; }
    @Override public boolean isPersistent() { return !isDestroyed(); }
    @Override public boolean participatesInLevelRepeatOffset() { return true; }
    @Override public void applyLevelRepeatOffset(int dx, int dy) {
        x += dx;
        floor += dy;
        ballY += dy * 256;
        updateDynamicSpawn(x, floor);
    }
    @Override public AbstractObjectInstance recreateForRewind(RewindRecreateContext context) {
        return new CourseHazard(context.spawn());
    }

    @Override public void update(int vIntRunCount, PlayableEntity player) {
        var camera = services().camera();
        if (x < camera.getX() - 384 || x > camera.getX() + camera.getWidth() + 512) {
            setDestroyed(true);
            return;
        }
        ticks++;
        chainAngle = (chainAngle + CHAIN_SPEED) & 0xffff;
        if (kind() == HazardPlan.FIREBALL) updateFireball(camera);
    }

    private void updateFireball(com.openggf.camera.Camera camera) {
        int anchor = (floor + FIREBALL_DEPTH) * 256;
        if (!flying) {
            // LavaM: the maker only fires while on screen.
            boolean visible = x >= camera.getX() && x < camera.getX() + camera.getWidth();
            if (wait > 1) wait--;
            if (wait > 1 || !visible) return;
            flying = true;
            ballY = anchor;
            ballSpeed = (launches++ & 1) == 0 ? -0x500 : -0x600;
            return;
        }
        ballY += ballSpeed;
        ballSpeed += FIREBALL_GRAVITY;
        if (ballSpeed > 0 && ballY >= anchor) {
            flying = false;
            wait = FIREBALL_PERIOD;
        }
    }
    public int launches() { return launches; }
    private boolean fireballFlying() { return flying; }

    private int flameFrame() {
        int t = Math.floorMod(ticks, FLAME_IDLE + FLAME_GROW + FLAME_FULL + FLAME_SHRINK);
        if (t < FLAME_IDLE) return 0;
        t -= FLAME_IDLE;
        if (t < FLAME_GROW) return Math.min(FLAME_FULL_FRAME, 1 + t / 4); // .pipe1 speed 3
        t -= FLAME_GROW;
        if (t < FLAME_FULL) return FLAME_FULL_FRAME;
        return switch (t - FLAME_FULL) { case 0 -> 9; case 1 -> 7; case 2 -> 5; case 3 -> 3; case 4 -> 1; default -> 0; };
    }

    private int ballOffset() {
        int angle = (ticks << BALL_PERIOD_SHIFT) & 0xff;
        return BALL_TRAVEL / 2 - (TrigLookupTable.cosHex(angle) * (BALL_TRAVEL / 2) >> 8);
    }

    private int[] chainPoint(int radius) {
        int angle = (chainAngle >> 8) & 0xff;
        int pivotY = floor - CHAIN_PIVOT_HEIGHT;
        return new int[]{x + (TrigLookupTable.cosHex(angle) * radius >> 8),
                pivotY + (TrigLookupTable.sinHex(angle) * radius >> 8)};
    }

    private int[] wreckingPoint(int radius) {
        int swing = TrigLookupTable.sinHex((ticks * 256 / WRECKING_PERIOD) & 0xff) * WRECKING_SWING >> 8;
        int angle = swing & 0xff;
        int pivotY = floor - WRECKING_PIVOT_HEIGHT;
        return new int[]{x + (TrigLookupTable.sinHex(angle) * radius >> 8),
                pivotY + (TrigLookupTable.cosHex(angle) * radius >> 8)};
    }

    private TouchRegion[] regions() {
        return switch (kind()) {
            case HazardPlan.SPIKES -> new TouchRegion[]{new TouchRegion(x, floor - SPIKE_HALF_HEIGHT, HURT_40X32)};
            case HazardPlan.FIREBALL -> fireballFlying()
                    ? new TouchRegion[]{new TouchRegion(x, ballY / 256, HURT_16X16)} : new TouchRegion[0];
            case HazardPlan.BIG_BALL -> new TouchRegion[]{
                    new TouchRegion(x - ballOffset(), floor - BALL_RADIUS, HURT_32X32)};
            case HazardPlan.CHAIN -> {
                int[] tip = chainPoint(CHAIN_RADIUS);
                yield new TouchRegion[]{new TouchRegion(tip[0], tip[1], HURT_16X16)};
            }
            case HazardPlan.FLAME -> flameFrame() == FLAME_FULL_FRAME
                    ? new TouchRegion[]{new TouchRegion(x, floor - FLAME_HIT_CENTRE, HURT_24X48)} : new TouchRegion[0];
            case HazardPlan.WRECKING_BALL -> {
                int[] ball = wreckingPoint(WRECKING_LENGTH);
                yield new TouchRegion[]{new TouchRegion(ball[0], ball[1], HURT_40X40)};
            }
            default -> new TouchRegion[0];
        };
    }

    @Override public int getCollisionFlags() { return 0; }
    @Override public int getCollisionProperty() { return 0; }
    @Override public TouchRegion[] getMultiTouchRegions() {
        if (isDestroyed()) return null;
        var regions = regions();
        return regions.length == 0 ? null : regions;
    }
    /** Runs before the engine's hurt pass: a shield or 20 rings absorb the hit (see {@link CourseGuard}). */
    @Override public void onTouchResponse(PlayableEntity entity, TouchResponseResult result, int frameCounter) {
        if (!isDestroyed() && entity instanceof com.openggf.sprites.playable.AbstractPlayableSprite player) {
            CourseGuard.absorb(services(), player, result, frameCounter);
        }
    }

    @Override public int getPriorityBucket() { return RenderPriority.bucket(4); }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed()) return;
        switch (kind()) {
            case HazardPlan.SPIKES -> draw(ObjectArtKeys.SPIKE, 0, x, floor - SPIKE_HALF_HEIGHT, false, false);
            case HazardPlan.FIREBALL -> {
                if (!fireballFlying()) return;
                // Ani_Fire .vertical alternates frames 0 and 1; the falling ball is flipped.
                boolean slz = services().gameService(TerrainLibrary.class).romZone() == 3;
                draw(slz ? ObjectArtKeys.SLZ_FIREBALL : ObjectArtKeys.MZ_FIREBALL, (ticks / 6) & 1, x, ballY / 256,
                        false, ballSpeed > 0);
            }
            case HazardPlan.BIG_BALL -> draw(ObjectArtKeys.SYZ_BIG_SPIKED_BALL, 0, x - ballOffset(), floor - BALL_RADIUS,
                    false, false);
            case HazardPlan.CHAIN -> {
                // Map_SBall2: frame 2 the wall base at the pivot, frame 0 the links, frame 1 the spikeball.
                for (int radius = 0; radius < CHAIN_RADIUS; radius += 0x10) {
                    int[] link = chainPoint(radius);
                    draw(ObjectArtKeys.LZ_SPIKEBALL_CHAIN, radius == 0 ? 2 : 0, link[0], link[1], false, false);
                }
                int[] tip = chainPoint(CHAIN_RADIUS);
                draw(ObjectArtKeys.LZ_SPIKEBALL_CHAIN, 1, tip[0], tip[1], false, false);
            }
            case HazardPlan.FLAME -> draw(ObjectArtKeys.SBZ_FLAMETHROWER, flameFrame(), x, floor - FLAME_BASE, false, false);
            case HazardPlan.WRECKING_BALL -> {
                // Map_Swing_GHZ: frame 2 the anchor, frame 1 a chain link; Map_GBall frames 1-3 roll.
                int[] pivot = wreckingPoint(0);
                draw(ObjectArtKeys.SWING_GHZ, 2, pivot[0], pivot[1], false, false);
                for (int radius = 0x10; radius < WRECKING_LENGTH; radius += 0x10) {
                    int[] link = wreckingPoint(radius);
                    draw(ObjectArtKeys.SWING_GHZ, 1, link[0], link[1], false, false);
                }
                int[] ball = wreckingPoint(WRECKING_LENGTH);
                draw(ObjectArtKeys.SWING_GIANT_BALL, 1 + (ticks / 8) % 3, ball[0], ball[1], false, false);
            }
            default -> { }
        }
    }
    private void draw(String key, int frame, int drawX, int drawY, boolean hFlip, boolean vFlip) {
        var renderer = getRenderer(key);
        if (renderer != null) renderer.drawFrameIndex(frame, drawX, drawY, hFlip, vFlip);
    }
}
