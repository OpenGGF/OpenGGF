package com.openggf.level.rings;

import com.openggf.game.rewind.RewindTransient;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchResponseProvider;

import java.util.List;

/**
 * ROM Obj37 spilled ("lost") ring as a real dynamic object whose per-ring physics
 * runs in the object exec loop at the ROM frame point.
 * <p>
 * Game-agnostic. Carries the same per-ring fixed-point state as the legacy
 * {@link LostRing} twin (xSubpixel / ySubpixel / xVel / yVel / lifetime /
 * phaseOffset / collected). The per-ring bounce math here is relocated verbatim
 * from {@code RingManager.LostRingPool.updatePhysics} (RingManager.java:1245-1247,
 * s2.asm Obj37 RLoss_Move): each frame {@code xSubpixel += xVel; ySubpixel += yVel;
 * yVel += gravity}, then a per-game-cadence floor/ceiling probe (Stage 5).
 * <p>
 * The decelerating spin animation is NOT per-ring — it lives in the shared
 * {@link SpillAnimationState} owner; the displayed mapping frame is
 * {@code sharedSpillAnimFrame + phaseOffset}.
 * <p>
 * Collision flags are {@code 0x47} (category $40 SPECIAL + size index $07) while
 * uncollected, mirroring {@code Sonic1RingInstance.java:167}. The
 * {@link #isLostRingCollectible()} marker (NOT the {@code 0x47} byte shape) is the
 * type key the Stage-2 unified touch branch uses so S1 placed rings keep their own
 * listener path.
 */
public final class LostRingObjectInstance extends AbstractObjectInstance
        implements TouchResponseProvider {

    /** ROM Obj37 collision_flags while uncollected: $40 SPECIAL + size index $07. */
    public static final int LOST_RING_COLLISION_FLAGS = 0x47;
    /** ROM size index → collision_flags low bits = $07. */
    private static final int COLLISION_SIZE_INDEX = 7;

    private int xSubpixel;
    private int ySubpixel;
    private int xVel;
    private int yVel;
    private int lifetime;
    private int phaseOffset;
    private boolean collected;
    private int sparkleStartFrame = -1;

    /**
     * Shared spin owner; the displayed frame = owner.frame() + phaseOffset. This is
     * GLOBAL state shared across every live spilled ring (not per-ring), captured and
     * restored once via {@link SpillAnimationState#snapshot()}/{@link SpillAnimationState#restore(int[])}
     * by the ring-manager snapshot — so it is excluded from per-ring rewind capture and
     * re-injected by the spawner / {@code LostRingRewindCodec} on recreate.
     */
    @RewindTransient(reason = "global shared spin owner; captured once via ring-manager snapshot, re-injected on recreate")
    private SpillAnimationState spillAnimation;

    private LostRingObjectInstance(ObjectSpawn spawn) {
        super(spawn, "Obj37_LostRing");
    }

    /**
     * Test factory: build a lost ring directly with fixed-point state, matching the
     * {@link LostRing#reset} contract (xSubpixel = x &lt;&lt; 8). No level / services
     * needed — physics is exercised via {@link #stepPhysicsForTest(int, boolean)}.
     */
    public static LostRingObjectInstance forTest(int xPixel, int yPixel, int xVel, int yVel,
                                                 int phaseOffset, int lifetime) {
        LostRingObjectInstance ring =
                new LostRingObjectInstance(new ObjectSpawn(xPixel & 0xFFFF, yPixel & 0xFFFF,
                        0x37, 0, 0, false, 0));
        ring.xSubpixel = xPixel << 8;
        ring.ySubpixel = yPixel << 8;
        ring.xVel = xVel;
        ring.yVel = yVel;
        ring.phaseOffset = phaseOffset;
        ring.lifetime = lifetime;
        ring.collected = false;
        return ring;
    }

    /**
     * Production spawn factory: build a spilled ring at the given pixel position with the
     * ROM bounce velocity / phase / lifetime, sharing the {@code spillAnimation} owner.
     * Mirrors the {@link LostRing#reset} fixed-point contract (xSubpixel = x &lt;&lt; 8).
     */
    public static LostRingObjectInstance spawn(int xPixel, int yPixel, int xVel, int yVel,
                                               int phaseOffset, int lifetime,
                                               SpillAnimationState spillAnimation) {
        LostRingObjectInstance ring = forTest(xPixel, yPixel, xVel, yVel, phaseOffset, lifetime);
        ring.spillAnimation = spillAnimation;
        return ring;
    }

    /** Inject the shared spill-spin owner (frame source for rendering). */
    public void setSpillAnimation(SpillAnimationState spillAnimation) {
        this.spillAnimation = spillAnimation;
    }

    // ── ROM per-ring physics step ─────────────────────────────────────────────

    /**
     * One ROM Obj37 physics step: velocity integrate + gravity (RingManager.java:1245-1247).
     * Floor/ceiling probing is gated by {@code floorCheck} and lands in Stage 5; for now a
     * {@code false} value runs the pure integrate path so physics is unit-testable without a
     * loaded level.
     */
    private void stepPhysics(int gravity, boolean floorCheck) {
        if (collected) {
            return;
        }
        // ROM (LostRingPool.updatePhysics, RingManager.java:1245-1247 / s2.asm RLoss_Move):
        //   xSubpixel += xVel;  ySubpixel += yVel;  yVel += gravity.
        xSubpixel += xVel;
        ySubpixel += yVel;
        yVel += gravity;
        // floorCheck (per-game cadence floor/ceiling probe) relocates here in Stage 5.
    }

    /** Object-loop entry: one ring physics step. Full level wiring lands in later tasks. */
    public void updateMovement() {
        stepPhysics(0x18 /* GRAVITY */, true);
    }

    // ── Type marker + collision ───────────────────────────────────────────────

    /** Type marker consumed by the Stage-2 unified touch branch (NOT the $47 byte shape). */
    public boolean isLostRingCollectible() {
        return true;
    }

    /** ROM size index $07 → collision_flags low bits. */
    public int getCollisionSizeIndex() {
        return COLLISION_SIZE_INDEX;
    }

    @Override
    public int getCollisionFlags() {
        // ROM Obj37: collision_flags = $47 while collectible; cleared on collection
        // (mirrors Sonic1RingInstance.java:167).
        return collected ? 0 : LOST_RING_COLLISION_FLAGS;
    }

    @Override
    public int getCollisionProperty() {
        return 0;
    }

    public boolean isCollected() {
        return collected;
    }

    public void markCollected(int frameCounter) {
        collected = true;
        sparkleStartFrame = frameCounter;
    }

    public int getSparkleStartFrame() {
        return sparkleStartFrame;
    }

    public int getPhaseOffset() {
        return phaseOffset;
    }

    // ── Position (subpixel-backed; overrides spawn-derived defaults) ───────────

    @Override
    public int getX() {
        return (xSubpixel >> 8) & 0xFFFF;
    }

    @Override
    public int getY() {
        return (ySubpixel >> 8) & 0xFFFF;
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Full spilled-ring draw (shared spin frame + phaseOffset) relocates here in a
        // later task; no-op until the spawn/render path is wired.
    }

    // ── Test accessors (fixed-point, so the sub-pixel carry is exercised exactly) ──

    public static LostRingObjectInstance create(ObjectSpawn spawn) {
        return new LostRingObjectInstance(spawn);
    }

    public int getXSubpixelForTest() {
        return xSubpixel;
    }

    public int getYSubpixelForTest() {
        return ySubpixel;
    }

    public int getXVelForTest() {
        return xVel;
    }

    public int getYVelForTest() {
        return yVel;
    }

    /** Run one physics step with a fixed gravity, skipping world collision when {@code !floorCheck}. */
    public void stepPhysicsForTest(int gravity, boolean floorCheck) {
        stepPhysics(gravity, floorCheck);
    }
}
