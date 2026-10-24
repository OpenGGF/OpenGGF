package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;

import java.util.List;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;

import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * S3K Obj $8D - Rhinobot (AIZ).
 * Core routine mapping: Obj_Rhinobot (sonic3k.asm loc_86E7E..loc_8714A).
 */
public final class RhinobotBadnikInstance extends AbstractS3kBadnikInstance implements SpawnRewindRecreatable, ObjectVisualFacing.Source {
    @Override public ObjectVisualFacing.Basis nativeFacing() {
        return facingLeft ? ObjectVisualFacing.Basis.LEFT : ObjectVisualFacing.Basis.RIGHT;
    }

    @Override public boolean supportsGroundedFieldMotion() { return true; }

    private static final int COLLISION_SIZE_INDEX = 0x0B; // ObjSlot_Rhinobot flags $0B
    private static final int PRIORITY_BUCKET = 5;         // ObjSlot_Rhinobot priority $280

    private static final int PATROL_ACCEL = 0x10;         // loc_86E7E d0 magnitude
    private static final int PATROL_TOP_SPEED = 0x300;    // loc_86E7E d1 magnitude
    private static final int DASH_SPEED = 0x400;          // loc_86FCE
    private static final int CHARGE_PREP_FRAMES = 0x20;   // loc_86F92
    private static final int DASH_TIMEOUT_FRAMES = 0x80;  // safety fallback in flat terrain

    private static final int FLOOR_PROBE_X = 4;           // sub_870CA d0
    private static final int Y_RADIUS = 0x10;             // loc_86E7E
    private static final int FLOOR_MIN_DIST = -1;
    private static final int FLOOR_MAX_DIST = 0x0C;

    private static final int DETECT_X = 0x60;             // sub_870A4 d2 horizontal compare
    private static final int DETECT_Y = 0x20;             // sub_870A4 d3 vertical compare
    private static final int WAIT_OFFSCREEN_HALF_SIZE = 0x20; // Obj_WaitOffscreen width/height_pixels
    private static final int RENDER_HALF_WIDTH = 0x14;     // ObjSlot_Rhinobot width_pixels
    private static final int RENDER_HALF_HEIGHT = 0x10;    // ObjSlot_Rhinobot height_pixels

    private static final int FRAME_SLOW = 0;
    private static final int FRAME_RUN = 1;
    private static final int FRAME_BRAKE = 2;
    private static final int FRAME_THRESHOLD_SLOW = 0x80;
    private static final int FRAME_THRESHOLD_BRAKE = 0x280;

    private static final int FLAG_CHARGE_EFFECT = 1 << 1; // $38 bit 1
    private static final int FLAG_ACCEL_PHASE = 1 << 2;   // $38 bit 2
    private static final int FLAG_FACING_RIGHT = 1 << 3;  // $38 bit 3

    private enum State {
        PATROL,
        CHARGE_PREP,
        CHARGE_DASH
    }

    private enum SpeedCallback {
        REACH_TURN_POINT,
        TOGGLE_FACING
    }

    private State state = State.PATROL;
    /** ROM loc_85B02 restores the saved operation pointer exactly once. */
    private boolean waitOffscreenReleased;
    private boolean waitOffscreenDispatched;
    /** Prior Render_Sprites sign bit for the empty $20 by $20 placeholder. */
    private boolean placeholderRenderedOnscreen;
    /** Native status7/Delete_Current_Sprite marker; the physical SST clears next dispatch. */
    private boolean nativeRetirementPending;
    private int statusFlags;
    private int accelStep;
    private int targetSpeed;
    private int stateTimer;
    private SpeedCallback speedCallback = SpeedCallback.REACH_TURN_POINT;
    /**
     * routine 0. Obj_WaitOffscreen's release pass (loc_85B02) returns without
     * dispatching and leaves routine at 0, so the first dispatch after the gate
     * releases runs Rhinobot_Init, not Rhinobot_Patrol.
     */
    private boolean initPending = true;

    public RhinobotBadnikInstance(ObjectSpawn spawn) {
        super(spawn, "Rhinobot",
                Sonic3kObjectArtKeys.RHINOBOT, COLLISION_SIZE_INDEX, PRIORITY_BUCKET);
        // Rhinobot_Init's field writes are NOT done here: the ROM performs them on
        // the routine-0 dispatch (see runInit), which costs a whole frame.
    }

    /**
     * Rhinobot_Init (sonic3k.asm:182480-182499): loads ObjSlot_Rhinobot through
     * SetUp_ObjAttributesSlotted (whose own tail is
     * `addq.b #2,routine(a0)` then `rts`, sonic3k.asm:178971-178987), sets
     * x_radius/y_radius, picks d0/d1 = -$10/-$300 (negated, with $38 bits 2 and 3
     * set, when render_flags bit 0 is set), stores them in $40/$3E and points
     * $34 at Rhinobot_ReverseAcceleration — then `rts`. It does NOT fall through
     * to Rhinobot_Patrol, so this dispatch performs no floor probe, no
     * acceleration and no MoveSprite2; the patrol begins on the next dispatch.
     */
    private void runInit() {
        accelStep = -PATROL_ACCEL;
        targetSpeed = -PATROL_TOP_SPEED;
        if (!facingLeft) {
            statusFlags |= FLAG_FACING_RIGHT | FLAG_ACCEL_PHASE;
            accelStep = PATROL_ACCEL;
            targetSpeed = PATROL_TOP_SPEED;
        }
        mappingFrame = FRAME_SLOW;
        speedCallback = SpeedCallback.REACH_TURN_POINT;
    }

    @Override protected boolean independentPresentationBody() { return true; }

    @Override
    protected void updateMovement(int vIntRunCount, PlayableEntity playerEntity) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        if (isDestroyed()) {
            return;
        }
        if (nativeRetirementPending) {
            ObjectLifetimeOps.destroyRespawnableOffscreen(this);
            return;
        }
        // Obj_WaitOffscreen / loc_85AD2 (sonic3k.asm:180362-180392)
        // queues Map_Offscreen, then consumes the preceding Render_Sprites
        // sign bit. X-only current bounds would start a vertically hidden
        // Rhinobot. loc_85B02 restores the saved operation and returns; Init
        // still owns a separate following dispatch.
        if (!waitOffscreenReleased) {
            waitOffscreenDispatched = true;
            if (!placeholderRenderedOnscreen) {
                // loc_85AFC clears the physical slot immediately while waiting.
                if (isOutsideNativeCoarseWindow()) {
                    ObjectLifetimeOps.releaseSpawnForRespawn(services().objectManager(), this, spawn);
                    ObjectLifetimeOps.destroyRespawnableOffscreen(this);
                }
                return;
            }
            waitOffscreenReleased = true;
            return;
        }

        if (initPending) {
            initPending = false;
            runInit();
            checkNativeRetirement();
            return;
        }

        switch (state) {
            case PATROL -> updatePatrol(player);
            case CHARGE_PREP -> updateChargePrep();
            case CHARGE_DASH -> updateChargeDash();
        }
        checkNativeRetirement();
    }

    private void checkNativeRetirement() {
        // Sprite_CheckDeleteTouchSlotted / Go_Delete_SpriteSlotted
        // (sonic3k.asm:179177-179186,179147-179155) marks status7/code now,
        // after routine and DPLC work, and clears on the next slot dispatch.
        if (isOutsideNativeCoarseWindow()) {
            nativeRetirementPending = true;
            ObjectLifetimeOps.releaseSpawnForRespawn(services().objectManager(), this, spawn);
        }
    }

    private boolean isOutsideNativeCoarseWindow() {
        return tryServices() != null && services().camera() != null
                && isCoarseXOutOfRange(currentX, services().camera().getX(), coarseXCullRange());
    }

    @Override public boolean usesCustomOutOfRangeCheck() { return true; }
    @Override public boolean isCustomOutOfRange(int cameraX) { return false; }

    private void updatePatrol(AbstractPlayableSprite player) {
        if (shouldStartCharge(player)) {
            enterChargePrep();
            return;
        }

        // ROM order in loc_86EC4: floor probe first, then accel + MoveSprite2.
        if (!snapToFloorOrReverse()) {
            return;
        }

        xVelocity += accelStep;
        moveWithVelocity();
        checkTargetSpeedTransition();
        updateMappingFrameFromSpeed();
    }

    private void enterChargePrep() {
        state = State.CHARGE_PREP;
        stateTimer = CHARGE_PREP_FRAMES;
        mappingFrame = FRAME_SLOW;
        statusFlags |= FLAG_CHARGE_EFFECT;
        services().playSfx(Sonic3kSfx.BLAST.id);
    }

    private void updateChargePrep() {
        xVelocity = 0;
        stateTimer--;
        if (stateTimer >= 0) {
            return;
        }

        state = State.CHARGE_DASH;
        stateTimer = DASH_TIMEOUT_FRAMES;
        int dashSpeed = DASH_SPEED;
        if ((statusFlags & FLAG_FACING_RIGHT) == 0) {
            dashSpeed = -dashSpeed;
        }
        xVelocity = dashSpeed;
    }

    private void updateChargeDash() {
        if (!snapToFloorOrReverse()) {
            return;
        }
        moveWithVelocity();

        stateTimer--;
        if (stateTimer >= 0) {
            return;
        }

        // Timeout fallback for long flat platforms: return to patrol acceleration.
        state = State.PATROL;
        xVelocity = accelStep;
    }

    private boolean shouldStartCharge(AbstractPlayableSprite updatePlayer) {
        PlayableEntity target = com.openggf.game.mutators.NativeObjectPolicyAccess.permitsTargeting(services()) ? closestNativePlayerByHorizontalDistance(updatePlayer) : null;
        if (!(target instanceof AbstractPlayableSprite player)) {
            return false;
        }
        // Find_SonicTails selects the native player with the smallest absolute
        // X distance before leaving that player's horizontal distance in d2 and
        // vertical distance in d3 (sonic3k.asm:178334-178368,182626-182644).
        int dx = player.getCentreX() - currentX;
        int dy = Math.abs(player.getCentreY() - currentY);
        if (Math.abs(dx) > DETECT_X || dy > DETECT_Y) {
            return false;
        }

        // Find_SonicTails d0 semantics:
        // 0 when target is left, 2 when target is right.
        int d0 = dx > 0 ? 2 : 0;
        if ((statusFlags & FLAG_FACING_RIGHT) == 0) {
            d0 -= 2;
        }
        return d0 != 0;
    }

    private boolean snapToFloorOrReverse() {
        int probeX = currentX + (((statusFlags & FLAG_FACING_RIGHT) != 0) ? FLOOR_PROBE_X : -FLOOR_PROBE_X);
        TerrainCheckResult floor = ObjectTerrainUtils.checkFloorDist(probeX, currentY, Y_RADIUS);
        if (!floor.foundSurface() || floor.distance() < FLOOR_MIN_DIST || floor.distance() >= FLOOR_MAX_DIST) {
            reverseDirection();
            return false;
        }
        currentY += floor.distance();
        return true;
    }

    private void checkTargetSpeedTransition() {
        if (xVelocity == 0) {
            invokeSpeedCallback();
            return;
        }
        if ((statusFlags & FLAG_ACCEL_PHASE) != 0) {
            if (xVelocity >= targetSpeed) {
                invokeSpeedCallback();
            }
            return;
        }
        if (xVelocity <= targetSpeed) {
            invokeSpeedCallback();
        }
    }

    private void invokeSpeedCallback() {
        if (speedCallback == SpeedCallback.REACH_TURN_POINT) {
            onReachTurnPoint();
            return;
        }
        onToggleFacing();
    }

    private void onReachTurnPoint() {
        statusFlags ^= FLAG_ACCEL_PHASE;
        accelStep = -accelStep;
        targetSpeed = -targetSpeed;
        speedCallback = SpeedCallback.TOGGLE_FACING;
    }

    private void onToggleFacing() {
        statusFlags ^= FLAG_FACING_RIGHT;
        facingLeft = (statusFlags & FLAG_FACING_RIGHT) == 0;
        statusFlags &= ~FLAG_CHARGE_EFFECT;
        speedCallback = SpeedCallback.REACH_TURN_POINT;
    }

    private void updateMappingFrameFromSpeed() {
        int frame = FRAME_SLOW;
        boolean facingRight = (statusFlags & FLAG_FACING_RIGHT) != 0;
        boolean accelPhase = (statusFlags & FLAG_ACCEL_PHASE) != 0;

        if (facingRight) {
            if (accelPhase) {
                if (xVelocity <= FRAME_THRESHOLD_SLOW) {
                    frame = FRAME_RUN;
                }
            } else {
                frame = FRAME_RUN;
                if (xVelocity <= FRAME_THRESHOLD_BRAKE) {
                    frame = FRAME_BRAKE;
                    maybeTriggerBrakeEffect();
                }
            }
        } else {
            if (!accelPhase) {
                if (xVelocity > -FRAME_THRESHOLD_SLOW) {
                    frame = FRAME_RUN;
                }
            } else {
                frame = FRAME_RUN;
                if (xVelocity > -FRAME_THRESHOLD_BRAKE) {
                    frame = FRAME_BRAKE;
                    maybeTriggerBrakeEffect();
                }
            }
        }
        mappingFrame = frame;
    }

    @Override
    public void refreshPostCameraRenderState() {
        // Only a dispatched Draw_Sprite queues the placeholder for the scan;
        // merely installing a fresh slot before its first pass sets no sign.
        if (waitOffscreenDispatched && !waitOffscreenReleased) {
            placeholderRenderedOnscreen = isWithinRenderSpriteBounds(
                    WAIT_OFFSCREEN_HALF_SIZE, WAIT_OFFSCREEN_HALF_SIZE);
        }
    }

    @Override
    public int getOnScreenHalfWidth() {
        return initPending ? WAIT_OFFSCREEN_HALF_SIZE : RENDER_HALF_WIDTH;
    }

    @Override
    public int getOnScreenHalfHeight() {
        return initPending ? WAIT_OFFSCREEN_HALF_SIZE : RENDER_HALF_HEIGHT;
    }

    @Override
    public int getPriorityBucket() {
        // The cleared waiter has priority word0; only Init loads $280.
        return initPending ? 0 : super.getPriorityBucket();
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Map_Offscreen has zero pieces until Rhinobot_Init installs its body.
        if (!initPending && !nativeRetirementPending) {
            super.appendRenderCommands(commands);
        }
    }

    @Override
    public int getCollisionFlags() {
        // SetUp_ObjAttributesSlotted writes collision_flags inside Init
        // (sonic3k.asm:178981), after this pass's player-slot touch scan. The
        // waiter and restored-but-undispatched continuation still read zero.
        return initPending || nativeRetirementPending ? 0 : super.getCollisionFlags();
    }

    private void maybeTriggerBrakeEffect() {
        if ((statusFlags & FLAG_CHARGE_EFFECT) != 0) {
            return;
        }
        statusFlags |= FLAG_CHARGE_EFFECT;
        services().playSfx(Sonic3kSfx.BLAST.id);
    }

    private void reverseDirection() {
        statusFlags &= ~FLAG_ACCEL_PHASE;
        if ((statusFlags & FLAG_FACING_RIGHT) == 0) {
            statusFlags |= FLAG_ACCEL_PHASE;
        }
        xVelocity = 0;
        yVelocity = 0;
        mappingFrame = FRAME_SLOW;
        state = State.PATROL;
        onToggleFacing();
    }
}
