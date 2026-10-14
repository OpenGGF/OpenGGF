package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.PlayableEntity;

import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.physics.SwingMotion;
import com.openggf.mods.objects.CaterkillerJrRecipe;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;
import java.util.Objects;

/**
 * S3K Obj $8F - CaterKiller Jr head (AIZ).
 * Multi-segment caterpillar-like badnik. The head is attackable; body segments
 * always hurt the player.
 * <p>
 * Based on Obj_CaterKillerJr (sonic3k.asm lines 183317-183515).
 *
 * <h3>Movement cycle:</h3>
 * <ol>
 *   <li>Routine 4: Swing up/down for 3 peaks (max_vel=0x80, accel=8)</li>
 *   <li>Routine 6: Faster swing (max_vel=0x100), at peak: reverse x_vel, flip sprite</li>
 *   <li>Routine 8: Continue swing, at next peak: return to step 1</li>
 * </ol>
 */
public final class CaterkillerJrHeadInstance extends AbstractS3kBadnikInstance
        implements SpawnRewindRecreatable {

    private static final int COLLISION_SIZE_INDEX = 0x17;
    private static final int PRIORITY_BUCKET = 5;
    private static final int INITIAL_X_VEL = -0x100;
    private static final int SLOW_MAX_VEL = 0x80;
    private static final int FAST_MAX_VEL = 0x100;
    private static final int SWING_ACCEL = 8;
    private static final int SLOW_PEAK_COUNT = 3;
    private static final int BODY_SEGMENT_COUNT = 6;
    // Shipped off_877C6 / byte_877D2: do not derive stock roles/delays from length.
    private static final int[] SEGMENT_WAIT_DELAYS = {0x0B, 0x17, 0x23, 0x2F, 0x37, 0x3F};
    private record SegmentSpec(CaterkillerJrBodyInstance.SegmentRole role, int waitDelay) { }
    // Creator extension: repeat the tall silhouette without adding firing bodies,
    // then double the thin and coconut tail portions. The native -$100 movement
    // makes these explicit 12-pixel body / 8-pixel tail activation spacings.
    // These are intentional recipe values, not a claim about the shipped ROM.
    private static final List<SegmentSpec> DOUBLE_LENGTH_SEGMENTS = List.of(
            new SegmentSpec(CaterkillerJrBodyInstance.SegmentRole.TALL, 0x0B),
            new SegmentSpec(CaterkillerJrBodyInstance.SegmentRole.TALL, 0x17),
            new SegmentSpec(CaterkillerJrBodyInstance.SegmentRole.TALL, 0x23),
            new SegmentSpec(CaterkillerJrBodyInstance.SegmentRole.MIDDLE, 0x2F),
            new SegmentSpec(CaterkillerJrBodyInstance.SegmentRole.MIDDLE, 0x3B),
            new SegmentSpec(CaterkillerJrBodyInstance.SegmentRole.MIDDLE, 0x47),
            new SegmentSpec(CaterkillerJrBodyInstance.SegmentRole.THIN, 0x53),
            new SegmentSpec(CaterkillerJrBodyInstance.SegmentRole.THIN, 0x5F),
            new SegmentSpec(CaterkillerJrBodyInstance.SegmentRole.TAIL, 0x67),
            new SegmentSpec(CaterkillerJrBodyInstance.SegmentRole.TAIL, 0x6F),
            new SegmentSpec(CaterkillerJrBodyInstance.SegmentRole.TAIL, 0x77),
            new SegmentSpec(CaterkillerJrBodyInstance.SegmentRole.TAIL, 0x7F));
    /** Obj_WaitOffscreen's placeholder is width_pixels = height_pixels = $20. */
    private static final int WAIT_OFFSCREEN_MARGIN = 0x20;

    private enum Phase { SWING_COUNTED, SWING_FAST, SWING_FINISH }

    // Non-final: the compact enum codec must restore the intentional recipe even
    // when this owner was captured before its Init dispatch allocated any bodies.
    private CaterkillerJrRecipe recipe;
    private Phase phase;
    private int peakCounter;
    private int swingMaxVel;
    private boolean swingDown;

    private final List<CaterkillerJrBodyInstance> bodySegments = new ArrayList<>();
    private boolean bodySpawned;
    /** ROM loc_85B02 restores the saved operation pointer exactly once. */
    private boolean waitOffscreenReleased;
    /**
     * routine 0. loc_85B02 returns without dispatching and leaves routine at 0, so
     * the first dispatch after the gate releases runs CaterKillerJr_Init.
     */
    private boolean initPending = true;

    public CaterkillerJrHeadInstance(ObjectSpawn spawn) {
        this(spawn, CaterkillerJrRecipe.STOCK);
    }

    public CaterkillerJrHeadInstance(ObjectSpawn spawn, CaterkillerJrRecipe recipe) {
        super(spawn, "CaterKillerJr",
                Sonic3kObjectArtKeys.CATERKILLER_JR, COLLISION_SIZE_INDEX, PRIORITY_BUCKET);
        this.recipe = Objects.requireNonNull(recipe, "recipe");
        this.mappingFrame = 0;
        this.xVelocity = INITIAL_X_VEL;
        initSwingPhase1();
    }

    @Override
    protected void updateMovement(int vIntRunCount, PlayableEntity playerEntity) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        if (isDestroyed()) return;

        // ROM Obj_WaitOffscreen (docs/skdisasm/sonic3k.asm:180271-180305) is a ONE-SHOT
        // latch. It saves the caller's return address in $34(a0) and overwrites the
        // operation pointer with loc_85AD2, so the badnik's own routine does not run
        // while it waits; once the $20-by-$20 placeholder has been drawn, loc_85B02
        // does `move.l $34(a0),(a0) / rts`, restoring the real operation PERMANENTLY.
        // The badnik then runs for the rest of its life on or off screen. Re-testing
        // visibility every frame re-freezes it the moment it leaves the viewport.
        // loc_85B02 returns without running the routine, so the release frame consumes
        // a dispatch and the first real one is the frame after.
        if (!waitOffscreenReleased) {
            if (!isOnScreen(WAIT_OFFSCREEN_MARGIN)) return;
            waitOffscreenReleased = true;
            return;
        }

        if (initPending) {
            // CaterKillerJr_Init (sonic3k.asm:183338-183356) calls
            // SetUp_ObjAttributes (tail `addq.b #2,routine(a0)` / `rts`,
            // sonic3k.asm:176901-176919), sets x_vel = -$100, then creates the
            // body segments with CreateChild3_NormalRepeated. Unlike the other
            // badniks in this family it DOES fall through to a second label,
            // CaterKillerJr_StartSlowSwing, which overwrites routine with 4 and
            // primes the swing ($39 = 3, $3E = y_vel = $80, $40 = 8, $38 bit 0
            // clear) — but that label ends in `rts` too. So the field writes and
            // the child creation all belong to the Init dispatch, while
            // CaterKillerJr_SlowSwing (Swing_UpAndDown_Count + MoveSprite2) does
            // not run until the following dispatch. The constructor already
            // applies the x_vel and StartSlowSwing field writes.
            initPending = false;
            spawnBodySegments();
            return;
        }

        if (!bodySpawned) spawnBodySegments();

        boolean shouldMove = switch (phase) {
            case SWING_COUNTED -> updateSwingCounted();
            case SWING_FAST -> updateSwingFast();
            case SWING_FINISH -> updateSwingFinish();
        };
        if (shouldMove) {
            moveWithVelocity();
        }
    }

    @Override
    public int getCollisionFlags() {
        // Obj_WaitOffscreen replaces the operation before SetUp_ObjAttributes
        // writes collision_flags. The parked placeholder therefore cannot hurt
        // a player that reaches its coordinates while it is vertically hidden.
        // It also stays zero for the whole Init dispatch, because the frame's
        // touch scan runs at the player slot before this object's routine:
        // bodySpawned only turns true partway through that dispatch.
        return bodySpawned ? super.getCollisionFlags() : 0;
    }

    private void spawnBodySegments() {
        if (recipe == CaterkillerJrRecipe.STOCK) {
            for (int i = 0; i < BODY_SEGMENT_COUNT; i++) {
                spawnBodySegment(i, SEGMENT_WAIT_DELAYS[i], CaterkillerJrBodyInstance.stockRole(i));
            }
        } else {
            for (int i = 0; i < DOUBLE_LENGTH_SEGMENTS.size(); i++) {
                SegmentSpec segment = DOUBLE_LENGTH_SEGMENTS.get(i);
                spawnBodySegment(i, segment.waitDelay(), segment.role());
            }
        }
        // CreateChild3_NormalRepeated is one attempt, including partial or full
        // allocation failure. Later free slots must not grow a truncated graph.
        bodySpawned = true;
    }

    private void spawnBodySegment(int index, int delay, CaterkillerJrBodyInstance.SegmentRole role) {
        // AllocateObjectAfterCurrent preserves head-before-body execution/touch order.
        CaterkillerJrBodyInstance segment = spawnChild(
                () -> new CaterkillerJrBodyInstance(spawn, index, delay, role, this));
        if (!segment.isDestroyed() && segment.getSlotIndex() >= 0) {
            bodySegments.add(segment);
        }
    }

    @Override
    protected void afterRewindRestoreSettled() {
        // This structural list is not captured. Rebuild it only after every
        // body's ownerRef has resolved through the exact identity table. Neither
        // proximity nor a namespaced spawn key uniquely identifies adjacent heads.
        bodySegments.clear();
        for (ObjectInstance instance : services().objectManager().getActiveObjects()) {
            if (instance instanceof CaterkillerJrBodyInstance body && body.belongsTo(this)
                    && !body.isDestroyed()) {
                bodySegments.add(body);
            }
        }
        bodySegments.sort(Comparator.comparingInt(CaterkillerJrBodyInstance::segmentIndex));
    }

    void detachBodySegment(CaterkillerJrBodyInstance segment) {
        bodySegments.remove(segment);
    }

    /** Routine 4: swing with counter. Skip movement on transition to phase 2. */
    private boolean updateSwingCounted() {
        if (applySwing()) {
            peakCounter--;
            if (peakCounter < 0) {
                initSwingPhase2();
                return false;
            }
        }
        return true;
    }

    /** Routine 6: faster swing. On peak, reverse direction. Always moves. */
    private boolean updateSwingFast() {
        if (applySwing()) {
            phase = Phase.SWING_FINISH;
            xVelocity = -xVelocity;
            facingLeft = !facingLeft;
        }
        return true;
    }

    /** Routine 8: on peak, reset to phase 1. Skip movement on transition. */
    private boolean updateSwingFinish() {
        if (applySwing()) {
            initSwingPhase1();
            return false;
        }
        return true;
    }

    /** Applies one frame of Swing_UpAndDown. Returns true if peak was reached. */
    private boolean applySwing() {
        SwingMotion.Result r = SwingMotion.update(SWING_ACCEL, yVelocity, swingMaxVel, swingDown);
        yVelocity = r.velocity();
        swingDown = r.directionDown();
        return r.directionChanged();
    }

    private void initSwingPhase1() {
        phase = Phase.SWING_COUNTED;
        peakCounter = SLOW_PEAK_COUNT;
        swingMaxVel = SLOW_MAX_VEL;
        yVelocity = SLOW_MAX_VEL;
        swingDown = false;
    }

    private void initSwingPhase2() {
        phase = Phase.SWING_FAST;
        swingMaxVel = FAST_MAX_VEL;
        yVelocity = FAST_MAX_VEL;
        swingDown = false;
    }

    @Override
    public void onPlayerAttack(PlayableEntity playerEntity, TouchResponseResult result) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        super.onPlayerAttack(player, result);
        destroyBodySegments();
    }

    @Override
    public void onUnload() {
        destroyBodySegments();
    }

    private void destroyBodySegments() {
        for (CaterkillerJrBodyInstance segment : bodySegments) {
            segment.onHeadDestroyed();
        }
    }
}
