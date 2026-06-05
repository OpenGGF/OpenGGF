package com.openggf.level.rings;

import com.openggf.camera.Camera;
import com.openggf.game.GameModule;
import com.openggf.game.PhysicsFeatureSet;
import com.openggf.game.PlayableEntity;
import com.openggf.game.PhysicsProvider;
import com.openggf.game.rewind.RewindTransient;
import com.openggf.graphics.GLCommand;
import com.openggf.level.ChunkDesc;
import com.openggf.level.LevelManager;
import com.openggf.level.SolidTile;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectServices;
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
public class LostRingObjectInstance extends AbstractObjectInstance
        implements TouchResponseProvider {

    /** ROM Obj37 collision_flags while uncollected: $40 SPECIAL + size index $07. */
    public static final int LOST_RING_COLLISION_FLAGS = 0x47;
    /** ROM size index → collision_flags low bits = $07. */
    private static final int COLLISION_SIZE_INDEX = 7;

    /**
     * ROM: Obj37_Init sets y_radius(a1) = 8. RingCheckFloorDist adds y_radius to y_pos before
     * probing, so the floor sensor leaves from the ring's bottom edge
     * (relocated from RingManager.java:1089).
     */
    private static final int RING_Y_RADIUS = 8;
    /** ROM RingCheckFloorDist top-solidity bit (s2.asm Obj37 floor probe). */
    private static final int SOLIDITY_TOP = 0x0C;

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

    protected LostRingObjectInstance(ObjectSpawn spawn) {
        super(spawn, "Obj37_LostRing");
    }

    /**
     * Test seam: initialise the fixed-point state in place (used by probe-recording subclasses that
     * need to construct via the protected ctor). Mirrors {@link #forTest}'s {@code xSubpixel = x << 8}
     * contract.
     */
    protected final void initFixedPointForTest(int xPixel, int yPixel, int xVel, int yVel,
                                               int phaseOffset, int lifetime) {
        this.xSubpixel = xPixel << 8;
        this.ySubpixel = yPixel << 8;
        this.xVel = xVel;
        this.yVel = yVel;
        this.phaseOffset = phaseOffset;
        this.lifetime = lifetime;
        this.collected = false;
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
     * One ROM Obj37 physics step: velocity integrate + gravity, then a per-game-cadence floor (normal
     * gravity) or ceiling (S3K reverse gravity) probe. Relocated verbatim from the retired
     * {@code RingManager.LostRingPool.updatePhysics} per-game branches (RingManager.java:1242-1306,
     * s2.asm Obj37 RLoss_Move / sonic3k.asm Obj_Bouncing_Ring_Reverse_Gravity).
     * <p>
     * The per-game floor-check cadence is read from {@link PhysicsFeatureSet#ringFloorCheckMask()}
     * (S1 every 4 frames {@code andi.b #3}; S2/S3K every 8 {@code andi.b #7}); reverse gravity is the
     * ROM {@code Reverse_gravity_flag} runtime state, NOT a zone/game carve-out. {@code floorCheck}
     * skips the world probe entirely (unit-testable pure-integrate path with no loaded level).
     */
    private void stepPhysics(int gravity, boolean floorCheck) {
        if (collected) {
            return;
        }
        // ROM: S3K Reverse_gravity_flag negates the gravity accumulation and swaps the floor probe
        // for a ceiling probe. The flag is only ever set by S3K runtime state, so this is ROM-state
        // driven, not a game-id branch.
        boolean reverseGravity = isReverseGravityActive();
        int effectiveGravity = reverseGravity ? -gravity : gravity;

        // ROM (LostRingPool.updatePhysics, RingManager.java:1276-1278 / s2.asm RLoss_Move):
        //   xSubpixel += xVel;  ySubpixel += yVel;  yVel += gravity.
        xSubpixel += xVel;
        ySubpixel += yVel;
        yVel += effectiveGravity;

        if (!floorCheck) {
            return;
        }

        // Per-game floor-check cadence: S1 every 4 frames (#3), S2/S3K every 8 (#7). The ROM uses
        // v_vbla_byte (not the gameplay frame counter) for this gate (RingManager.java:1242-1248).
        int floorCheckMask = resolveFloorCheckMask();
        int vblaCounter = resolveVblaCounter();
        if (((vblaCounter + phaseOffset) & floorCheckMask) != 0) {
            return;
        }

        if (reverseGravity) {
            // S3K reverse gravity: probe the ceiling (top edge, y - y_radius) while rising
            // (RingManager.java:1282-1294, sonic3k.asm RingCheckFloorDist_ReverseGravity).
            if (yVel <= 0) {
                int dist = ringCheckCeilingDist(getX(), getY() - RING_Y_RADIUS);
                if (dist < 0) {
                    ySubpixel += (-dist) << 8;
                    yVel -= (yVel >> 2);
                    yVel = -yVel;
                }
            }
        } else {
            // Normal gravity: probe the floor (bottom edge, y + y_radius) while falling
            // (RingManager.java:1296-1305, s2.asm RingCheckFloorDist).
            if (yVel >= 0) {
                int dist = ringCheckFloorDist(getX(), getY() + RING_Y_RADIUS);
                if (dist < 0) {
                    ySubpixel += dist << 8;
                    yVel -= (yVel >> 2);
                    yVel = -yVel;
                }
            }
        }
    }

    /** Object-loop entry: one ring physics step. */
    public void updateMovement() {
        stepPhysics(0x18 /* GRAVITY */, true);
    }

    /**
     * Object exec-loop entry (ROM Obj37_Main, s2.asm:25203-25245). Drives the per-ring
     * physics step then applies the ROM boundary delete.
     * <p>
     * {@link AbstractObjectInstance#update} is a no-op by default and only
     * {@code AbstractBadnikInstance} overrides it — so without this override the ring
     * stays frozen at its spawn point. The override mirrors {@code Obj37_Main}:
     * <ol>
     *   <li>{@link #updateMovement()} = ObjectMove + gravity + cadence floor/ceiling probe;</li>
     *   <li>{@code Obj37_CheckBoundary}: delete when the shared
     *       {@code Ring_spill_anim_counter} (the {@link SpillAnimationState#counter()})
     *       reaches 0, OR when {@code y_pos} has passed below
     *       {@code Camera_Max_Y_pos + screen_height}.</li>
     * </ol>
     * The shared counter is the lifetime owner (ROM non-{@code fixBugs} path); it is
     * ticked once per frame by {@code RingManager.LostRingPool}, so every live ring
     * deletes on the same frame the spin animation finishes, matching the ROM.
     */
    @Override
    public void update(int frameCounter, PlayableEntity player) {
        if (isDestroyed()) {
            return;
        }
        updateMovement();

        // Obj37_CheckBoundary: shared Ring_spill_anim_counter == 0 → delete.
        if (spillAnimation != null && spillAnimation.counter() == 0) {
            setDestroyed(true);
            return;
        }

        // Obj37_CheckBoundary: y_pos below (Camera_Max_Y_pos + screen_height) → delete.
        Camera camera = cameraOrNull();
        if (camera != null) {
            int boundary = (camera.getMaxY() & 0xFFFF) + (camera.getHeight() & 0xFFFF);
            if (getY() > boundary) {
                setDestroyed(true);
            }
        }
    }

    private Camera cameraOrNull() {
        ObjectServices services = servicesOrNull();
        return services != null ? services.camera() : null;
    }

    // ── Per-game / runtime-state seams (overridable for unit tests) ────────────

    /**
     * Per-game floor-check cadence mask from {@link PhysicsFeatureSet#ringFloorCheckMask()}
     * (S1 {@code #3}, S2/S3K {@code #7}); S2 default when no feature set is resolvable.
     */
    protected int resolveFloorCheckMask() {
        PhysicsFeatureSet featureSet = resolveFeatureSet();
        return featureSet != null
                ? featureSet.ringFloorCheckMask()
                : PhysicsFeatureSet.RING_FLOOR_CHECK_MASK_S2;
    }

    /** ROM {@code Reverse_gravity_flag} runtime state (S3K only ever sets it). */
    protected boolean isReverseGravityActive() {
        ObjectServices services = servicesOrNull();
        return services != null && services.gameState() != null
                && services.gameState().isReverseGravityActive();
    }

    /**
     * ROM {@code v_vbla_byte} cadence clock (kept aligned across lag frames by ObjectManager so the
     * scattered-ring floor-check cadence matches the trace).
     */
    protected int resolveVblaCounter() {
        ObjectServices services = servicesOrNull();
        ObjectManager objectManager = services != null ? services.objectManager() : null;
        return objectManager != null ? objectManager.getVblaCounter() : 0;
    }

    private PhysicsFeatureSet resolveFeatureSet() {
        ObjectServices services = servicesOrNull();
        GameModule module = services != null ? services.gameModule() : null;
        PhysicsProvider provider = module != null ? module.getPhysicsProvider() : null;
        return provider != null ? provider.getFeatureSet() : null;
    }

    private ObjectServices servicesOrNull() {
        try {
            return services();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    // ── Floor / ceiling distance probes (relocated from RingManager.LostRingPool) ──

    /**
     * ROM RingCheckFloorDist (s2.asm Obj37): probe the floor below {@code (x, y)} using the level's
     * top-solidity sensor. Negative distance means penetration. Relocated from RingManager.java:1313.
     */
    protected int ringCheckFloorDist(int x, int y) {
        LevelManager levelManager = levelManagerOrNull();
        if (levelManager == null) {
            return 0;
        }
        ChunkDesc chunkDesc = levelManager.getChunkDescAt((byte) 0, x, y);
        SolidTile tile = solidTile(levelManager, chunkDesc);
        int metric = heightMetric(tile, chunkDesc, x);
        if (metric == 0) {
            return 0;
        }
        if (metric == 16) {
            // ROM: sub.w a3,d2 with a3=$10 → check the tile above.
            int prevY = y - 16;
            ChunkDesc prevDesc = levelManager.getChunkDescAt((byte) 0, x, prevY);
            int prevMetric = heightMetric(solidTile(levelManager, prevDesc), prevDesc, x);
            if (prevMetric > 0 && prevMetric < 16) {
                return distance(prevMetric, y, prevY);
            }
            return distance(metric, y, y);
        }
        return distance(metric, y, y);
    }

    /**
     * ROM RingCheckFloorDist_ReverseGravity (sonic3k.asm): same as the floor probe but strides upward
     * ({@code -$10} → check the tile below when fully solid). Relocated from RingManager.java:1342.
     */
    protected int ringCheckCeilingDist(int x, int y) {
        LevelManager levelManager = levelManagerOrNull();
        if (levelManager == null) {
            return 0;
        }
        ChunkDesc chunkDesc = levelManager.getChunkDescAt((byte) 0, x, y);
        SolidTile tile = solidTile(levelManager, chunkDesc);
        int metric = heightMetric(tile, chunkDesc, x);
        if (metric == 0) {
            return 0;
        }
        if (metric == 16) {
            // ROM: sub.w a3,d2 with a3=-$10 → check the tile below.
            int nextY = y + 16;
            ChunkDesc nextDesc = levelManager.getChunkDescAt((byte) 0, x, nextY);
            int nextMetric = heightMetric(solidTile(levelManager, nextDesc), nextDesc, x);
            if (nextMetric > 0 && nextMetric < 16) {
                return distance(nextMetric, y, nextY);
            }
            return distance(metric, y, y);
        }
        return distance(metric, y, y);
    }

    private LevelManager levelManagerOrNull() {
        ObjectServices services = servicesOrNull();
        return services != null ? services.levelManager() : null;
    }

    private static SolidTile solidTile(LevelManager levelManager, ChunkDesc chunkDesc) {
        if (chunkDesc == null || !chunkDesc.isSolidityBitSet(SOLIDITY_TOP)) {
            return null;
        }
        return levelManager.getSolidTileForChunkDesc(chunkDesc, SOLIDITY_TOP);
    }

    private static int heightMetric(SolidTile tile, ChunkDesc desc, int x) {
        if (tile == null) {
            return 0;
        }
        int index = x & 0x0F;
        if (desc != null && desc.getHFlip()) {
            index = 15 - index;
        }
        byte metric = tile.getHeightAt((byte) index);
        if (metric != 0 && metric != 16 && desc != null && desc.getVFlip()) {
            metric = (byte) (16 - metric);
        }
        return metric & 0xFF;
    }

    private static int distance(int metric, int y, int checkY) {
        int tileY = checkY & ~0x0F;
        return (tileY + 16 - metric) - y;
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
