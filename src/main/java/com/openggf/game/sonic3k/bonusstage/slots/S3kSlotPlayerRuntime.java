package com.openggf.game.sonic3k.bonusstage.slots;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;

public final class S3kSlotPlayerRuntime {
    private static final int DEBUG_MOVE_SPEED = 3;
    private static final short GROUND_ACCEL = S3kSlotBonusPlayer.GROUND_ACCEL;
    private static final short GROUND_DECEL = S3kSlotBonusPlayer.GROUND_DECEL;
    private static final short GROUND_REVERSAL_DECEL = S3kSlotBonusPlayer.GROUND_REVERSAL_DECEL;
    private static final short GROUND_MAX_SPEED = S3kSlotBonusPlayer.GROUND_MAX_SPEED;
    private static final short AIR_GRAVITY = 0x2A;
    private static final int POSITION_SHIFT = 16;
    private static final int SPEED_TO_POSITION_SHIFT = 8;

    private final S3kSlotStageState stageState;
    private final S3kSlotCollisionSystem collisionSystem;
    private int slotOriginX;
    private int slotOriginY;
    // ROM x_pos(a0)/y_pos(a0) as they stand right after sub_4BABC's ground-velocity
    // projection (sonic3k.asm:98789-98848) -- the value sub_4BDCA (ring pickup,
    // sonic3k.asm:99144) and sub_4BE3A (tile dispatch, sonic3k.asm:99195) both read
    // directly from memory. sub_4BCB0 (air gravity, sonic3k.asm:99010-99075) only
    // updates x_vel/y_vel from LOCAL copies (d2/d3); it never writes x_pos/y_pos.
    // MoveSprite2 -- which folds x_vel/y_vel into x_pos/y_pos -- runs AFTER both
    // sub_4BDCA and sub_4BE3A (sonic3k.asm:98776-98780). So the ring/tile checks
    // must see the ground-only position, not the fully-stepped one.
    private int groundProjectedOriginX;
    private int groundProjectedOriginY;
    private S3kSlotExitSequence exitSequence;
    private boolean debugActive;
    private int debugSavedStatTable;
    private int debugSavedScalarIndex1;

    public S3kSlotPlayerRuntime(S3kSlotStageState stageState, S3kSlotCollisionSystem collisionSystem) {
        this.stageState = stageState;
        this.collisionSystem = collisionSystem;
    }

    public S3kSlotStageState stageState() {
        return stageState;
    }

    public void initialize(AbstractPlayableSprite player) {
        int initialOriginX = player.getCentreX();
        int initialOriginY = player.getCentreY();
        stageState.clearCollision();
        stageState.setBounceTimer(0);
        player.setAir(true);
        player.setRolling(true);
        player.setGSpeed((short) 0);
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);
        player.setControlLocked(false);
        ObjectControlState.none().applyTo(player);
        player.setOnObject(false);
        slotOriginX = initialOriginX << POSITION_SHIFT;
        slotOriginY = initialOriginY << POSITION_SHIFT;
        groundProjectedOriginX = slotOriginX;
        groundProjectedOriginY = slotOriginY;
        exitSequence = null;
        debugActive = false;
        debugSavedStatTable = 0;
        debugSavedScalarIndex1 = 0;
        primeSpawnFrameFallthrough(player);
        syncPlayerToSlotOrigin(player);
    }

    /**
     * ROM {@code Obj_Sonic_RotatingSlotBonus}'s routine==0 init handler
     * ({@code loc_4B9CE}, sonic3k.asm:98710-98741) falls straight through --
     * with no intervening {@code rts} -- into the per-frame movement
     * dispatcher ({@code loc_4BA4E}, sonic3k.asm:98742-98784) on the very
     * same call that creates the object: {@code routine(a0)} is bumped from
     * 0 to 2 ({@code addq.b #2,routine(a0)}), {@code Status_InAir} is set,
     * and then that SAME invocation immediately runs {@code sub_4BABC}
     * (ground velocity), {@code sub_4BCB0} (air gravity/velocity),
     * {@code sub_4BDCA}, {@code sub_4BE3A}, and {@code MoveSprite2}
     * (position update, sonic3k.asm:98780) before returning -- a full
     * physics tick baked into the object's own spawn frame. That spawn
     * frame belongs to the scripted level-reload/fade transition that
     * precedes the first frame the headless trace fixture drives (the same
     * Restart_level_flag handoff already modelled in applyBonusStageEntry
     * for Gumball/Pachinko), so bootstrap must run this fallthrough tick
     * once before the first driven frame -- otherwise the exposed
     * y_speed/x_sub/y_sub lag the ROM by exactly one gravity/velocity
     * application (observed: engine y_speed=0x002A vs ROM 0x0054, engine
     * y_sub=0x2A00 vs ROM 0x7E00 at trace frame 0).
     */
    private void primeSpawnFrameFallthrough(AbstractPlayableSprite player) {
        applyMoveWithCollision(player, false, false);
        captureGroundProjectedOrigin();
        applyAirMotionWithCollision(player);
        applyVelocityStep(player);
        advanceRotation(false);
    }

    public void syncFromController(S3kSlotStageController controller) {
        if (controller == null) {
            return;
        }
        stageState.setStatTable(controller.rawStatTable());
        stageState.setScalarIndex1(controller.scalarIndex());
        stageState.setPaletteCycleEnabled(controller.isPaletteCycleEnabled());
    }

    public void resetSlotOrigin(AbstractPlayableSprite player) {
        if (player == null) {
            return;
        }
        captureSlotOriginFromPlayer(player);
    }

    public void advanceRotation(boolean objectControlled) {
        int delta = objectControlled ? (stageState.scalarIndex1() << 4) : stageState.scalarIndex1();
        stageState.setStatTable((stageState.rawStatTable() + delta) & 0xFFFF);
    }

    /**
     * ROM Obj_Sonic_RotatingSlotBonus and its subroutines (sonic3k.asm:98656-99567:
     * loc_4B9CE/loc_4BA4E per-frame dispatch, sub_4BABC ground velocity,
     * sub_4BCB0 air gravity, sub_4BD5A collision, loc_4BC1E/loc_4BC46/loc_4BC54
     * goal-exit rotation/fade) never write {@code angle(a0)} -- the whole
     * routine range has zero references to the angle field. The object's
     * "rotation" is expressed purely through {@code Stat_table} (read directly
     * by {@link #applyMoveWithCollision} and {@link #applyAirMotionWithCollision}
     * for the ground/gravity projection), not through the player sprite's own
     * angle byte. Deriving {@code player.angle} from {@code Stat_table} was a
     * fabricated behaviour with no ROM analog and diverged the trace-replay
     * angle field starting at frame 4 (once Stat_table's high byte first went
     * non-zero) even though angle should stay at whatever value the player had
     * on bonus-stage entry for the entire stage. This method (and
     * {@link #tickExitFrame}, {@link #syncDebugState}, {@link #leaveDebugMode})
     * intentionally leave {@code player.angle} untouched.
     */
    public void tick(AbstractPlayableSprite player, boolean up, boolean down,
                     boolean left, boolean right, boolean jump, int frameCounter) {
        short originalX = player.getX();
        short originalY = player.getY();

        stageState.clearCollision();
        captureExternalSlotOriginIfNeeded(player);
        boolean wasDebugActive = debugActive;
        syncDebugState(player);
        player.setMovementInputActive(player.isDebugMode()
                ? (up || down || left || right)
                : (left != right));

        if (wasDebugActive && !debugActive && !player.isDebugMode()) {
            syncPlayerToSlotOrigin(player);
            player.updateSensors(originalX, originalY);
            return;
        }

        if (exitSequence != null) {
            if (debugActive) {
                leaveDebugMode(player);
            }
            syncPlayerToSlotOrigin(player);
            player.updateSensors(originalX, originalY);
            return;
        }

        if (debugActive) {
            tickDebugMove(player, up, down, left, right);
            syncPlayerToSlotOrigin(player);
            player.updateSensors(originalX, originalY);
            return;
        }

        if (player.isObjectControlled()) {
            captureSlotOriginFromPlayer(player);
            advanceRotation(true);
            syncPlayerToSlotOrigin(player);
            player.updateSensors(originalX, originalY);
            return;
        }

        boolean startedAir = player.getAir();
        if (!startedAir && jump && player.isJumpJustPressed()) {
            launchJump(player);
        }

        applyMoveWithCollision(player, left, right);
        captureGroundProjectedOrigin();
        applyAirMotionWithCollision(player);
        applyVelocityStep(player);
        advanceRotation(false);
        syncPlayerToSlotOrigin(player);

        player.updateSensors(originalX, originalY);
    }

    public int slotOriginX() {
        return slotOriginX;
    }

    public int slotOriginY() {
        return slotOriginY;
    }

    private void captureGroundProjectedOrigin() {
        groundProjectedOriginX = slotOriginX;
        groundProjectedOriginY = slotOriginY;
    }

    /**
     * Pixel-space player origin as of ROM sub_4BABC's completion (ground-velocity
     * projection already applied, air-gravity/MoveSprite2 step not yet applied).
     * This is the position sub_4BDCA (ring pickup) and sub_4BE3A (tile dispatch)
     * read -- see the field javadoc above.
     */
    public int groundProjectedOriginX() {
        return groundProjectedOriginX >> POSITION_SHIFT;
    }

    public int groundProjectedOriginY() {
        return groundProjectedOriginY >> POSITION_SHIFT;
    }

    public void startGoalExit(AbstractPlayableSprite player) {
        if (exitSequence != null) {
            return;
        }
        player.setControlLocked(true);
        ObjectControlState.none().applyTo(player);
        player.setAir(true);
        player.setRolling(true);
        player.setOnObject(false);
        exitSequence = new S3kSlotExitSequence(new S3kSlotStageController(stageState));
    }

    public boolean isExiting() {
        return exitSequence != null;
    }

    public boolean isExitFading() {
        return exitSequence != null && exitSequence.isFading();
    }

    public boolean isExitComplete() {
        return exitSequence != null && exitSequence.isComplete();
    }

    public S3kSlotExitSequence activeExitSequence() {
        return exitSequence;
    }

    public void tickExitFrame(AbstractPlayableSprite player) {
        if (exitSequence == null || player == null) {
            return;
        }
        short originalX = player.getX();
        short originalY = player.getY();
        exitSequence.tick();
        syncPlayerToSlotOrigin(player);
        player.updateSensors(originalX, originalY);
    }

    public boolean isDebugActive() {
        return debugActive;
    }

    private void syncDebugState(AbstractPlayableSprite player) {
        if (player.isDebugMode()) {
            if (!debugActive) {
                enterDebugMode(player);
            }
            stageState.setStatTable(0);
            stageState.setScalarIndex1(0);
        } else if (debugActive) {
            leaveDebugMode(player);
        }
    }

    private void enterDebugMode(AbstractPlayableSprite player) {
        captureSlotOriginFromPlayer(player);
        debugSavedStatTable = stageState.rawStatTable();
        debugSavedScalarIndex1 = stageState.scalarIndex1();
        debugActive = true;
        stageState.setStatTable(0);
        stageState.setScalarIndex1(0);
        resetDebugMovementState(player);
    }

    private void leaveDebugMode(AbstractPlayableSprite player) {
        debugActive = false;
        stageState.setStatTable(debugSavedStatTable);
        stageState.setScalarIndex1(debugSavedScalarIndex1);
        resetDebugMovementState(player);
        player.setRolling(true);
    }

    private void tickDebugMove(AbstractPlayableSprite player, boolean up, boolean down, boolean left, boolean right) {
        if (left) {
            slotOriginX -= DEBUG_MOVE_SPEED << POSITION_SHIFT;
            player.setDirection(com.openggf.physics.Direction.LEFT);
        }
        if (right) {
            slotOriginX += DEBUG_MOVE_SPEED << POSITION_SHIFT;
            player.setDirection(com.openggf.physics.Direction.RIGHT);
        }
        if (up) {
            slotOriginY -= DEBUG_MOVE_SPEED << POSITION_SHIFT;
        }
        if (down) {
            slotOriginY += DEBUG_MOVE_SPEED << POSITION_SHIFT;
        }
        resetDebugMovementState(player);
    }

    private void resetDebugMovementState(AbstractPlayableSprite player) {
        player.setGSpeed((short) 0);
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);
        player.setAir(true);
        player.setJumping(false);
        player.setRolling(false);
        player.setOnObject(false);
        stageState.clearCollision();
    }

    private void launchJump(AbstractPlayableSprite player) {
        int angle = (-((stageState.angle() & 0xFC)) - 0x40) & 0xFF;
        player.setXSpeed((short) ((TrigLookupTable.cosHex(angle) * 0x680) >> 8));
        player.setYSpeed((short) ((TrigLookupTable.sinHex(angle) * 0x680) >> 8));
        player.setAir(true);
        if (GameServices.audio() != null) {
            GameServices.audio().playSfx(Sonic3kSfx.JUMP.id);
        }
    }

    private void applyMoveWithCollision(AbstractPlayableSprite player, boolean left, boolean right) {
        int gSpeed = player.getGSpeed();
        if (left == right) {
            if (gSpeed > 0) {
                gSpeed = Math.max(0, gSpeed - GROUND_DECEL);
            } else if (gSpeed < 0) {
                gSpeed = Math.min(0, gSpeed + GROUND_DECEL);
            }
        } else if (left) {
            if (gSpeed > 0) {
                // ROM sub_4BB54 loc_4BB76 (sonic3k.asm:98871-98878): subi.w #$40,d0 is
                // stored unconditionally -- the trailing "bcc.s loc_4BB7E / nop" is dead
                // code (both paths fall into the same move.w d0,ground_vel(a0)). Unlike
                // the neutral-decel path (loc_4BAF0, which DOES clamp at 0 via a real
                // branch-around-move), reversal decel is allowed to overshoot straight
                // through zero into the opposite sign in a single frame.
                gSpeed -= GROUND_REVERSAL_DECEL;
            } else {
                gSpeed = Math.max(-GROUND_MAX_SPEED, gSpeed - GROUND_ACCEL);
            }
            player.setDirection(com.openggf.physics.Direction.LEFT);
        } else {
            if (gSpeed < 0) {
                // ROM sub_4BB84 loc_4BBA4 (sonic3k.asm:98899-98905): addi.w #$40,d0
                // is likewise stored unconditionally with no zero clamp -- mirror image
                // of the left-reversal case above.
                gSpeed += GROUND_REVERSAL_DECEL;
            } else {
                gSpeed = Math.min(GROUND_MAX_SPEED, gSpeed + GROUND_ACCEL);
            }
            player.setDirection(com.openggf.physics.Direction.RIGHT);
        }
        player.setGSpeed((short) gSpeed);

        int statAngle = stageState.angle() & 0xFF;
        int projAngle = (-((statAngle + 0x20) & 0xC0)) & 0xFF;
        int sin = TrigLookupTable.sinHex(projAngle);
        int cos = TrigLookupTable.cosHex(projAngle);
        int deltaX = cos * gSpeed;
        int deltaY = sin * gSpeed;

        int preOriginX = slotOriginX;
        int preOriginY = slotOriginY;
        slotOriginX += deltaX;
        slotOriginY += deltaY;
        player.setJumping(false);

        S3kSlotCollisionSystem.Collision collision = collisionSystem.checkCollision(
                slotOriginX >> POSITION_SHIFT, slotOriginY >> POSITION_SHIFT);
        if (collision.solid()) {
            slotOriginX = preOriginX;
            slotOriginY = preOriginY;
            player.setGSpeed((short) 0);
        }
    }

    private void applyAirMotionWithCollision(AbstractPlayableSprite player) {
        int statAngle = stageState.angle() & 0xFC;
        int sin = TrigLookupTable.sinHex(statAngle);
        int cos = TrigLookupTable.cosHex(statAngle);

        long accX = ((long) player.getXSpeed() << 8) + (long) sin * AIR_GRAVITY;
        long accY = ((long) player.getYSpeed() << 8) + (long) cos * AIR_GRAVITY;

        int probeOriginX = slotOriginX;
        int probeOriginY = slotOriginY;

        probeOriginX += (int) accX;
        S3kSlotCollisionSystem.Collision colX = collisionSystem.checkCollision(
                probeOriginX >> POSITION_SHIFT, probeOriginY >> POSITION_SHIFT);
        boolean xCollided = colX.solid();
        if (xCollided) {
            accX = 0;
            player.setAir(false);
            stageState.setBounceTimer(4);
        }

        int probeOriginY2 = slotOriginY;
        probeOriginY2 += (int) accY;
        S3kSlotCollisionSystem.Collision colY = collisionSystem.checkCollision(
                (xCollided ? slotOriginX : probeOriginX) >> POSITION_SHIFT, probeOriginY2 >> POSITION_SHIFT);
        boolean yCollided = colY.solid();
        if (yCollided) {
            accY = 0;
            if (!xCollided) {
                player.setAir(false);
                stageState.setBounceTimer(4);
            }
        }

        player.setXSpeed((short) (accX >> 8));
        player.setYSpeed((short) (accY >> 8));

        if (!xCollided && !yCollided) {
            if (stageState.bounceTimer() > 0) {
                stageState.tickBounceTimer();
            } else {
                player.setAir(true);
            }
        }
    }

    /**
     * ROM MoveSprite2: after slot-specific ground and gravity handling, the updated
     * x_vel/y_vel still advance the player's fixed-point origin for the frame.
     */
    private void applyVelocityStep(AbstractPlayableSprite player) {
        slotOriginX += player.getXSpeed() << SPEED_TO_POSITION_SHIFT;
        slotOriginY += player.getYSpeed() << SPEED_TO_POSITION_SHIFT;
    }

    /**
     * Inverse of {@link #syncPlayerToSlotOrigin}: must round-trip the full
     * 16.16 ROM position, not just the truncated pixel word. Reading only
     * {@code getCentreX()/getCentreY()} here would silently drop the
     * subpixel fraction on every external resync (e.g. {@code
     * resetSlotOrigin} right after {@link #initialize}, or the
     * object-controlled/debug-mode transitions below) -- which previously
     * discarded exactly the fraction {@link #primeSpawnFrameFallthrough}
     * establishes, reproducing the same ROM x_pos/y_pos 32-bit-word
     * (sonic3k.asm:98780 MoveSprite2) truncation bug from the opposite
     * direction.
     */
    private void captureSlotOriginFromPlayer(AbstractPlayableSprite player) {
        slotOriginX = (player.getCentreX() << POSITION_SHIFT) | player.getXSubpixelRaw();
        slotOriginY = (player.getCentreY() << POSITION_SHIFT) | player.getYSubpixelRaw();
    }

    private void captureExternalSlotOriginIfNeeded(AbstractPlayableSprite player) {
        int playerX = player.getCentreX();
        int playerY = player.getCentreY();
        if (Math.abs((slotOriginX >> POSITION_SHIFT) - playerX) > 1
                || Math.abs((slotOriginY >> POSITION_SHIFT) - playerY) > 1) {
            captureSlotOriginFromPlayer(player);
        }
    }

    /**
     * ROM Obj_Sonic_RotatingSlotBonus stores the player's position as a
     * standard 32-bit ROM position word (pixel:16 | subpixel:16), and
     * MoveSprite2 (sonic3k.asm) writes the full 32-bit result back with
     * {@code move.l d2,x_pos} -- the subpixel fraction is never truncated
     * between frames. {@code slotOriginX}/{@code slotOriginY} mirror that
     * same 16.16 layout (pixel in the high word, subpixel in the low word --
     * see {@link com.openggf.sprites.AbstractSprite#move}). Using
     * {@code setCentreX}/{@code setCentreY} here would zero the sprite's
     * subpixel fields every frame (AbstractSprite.java:67-75), silently
     * discarding the fractional position this runtime already tracks
     * correctly and desyncing the player's exposed x_sub/y_sub (and any
     * gravity/velocity math downstream that reads them back via
     * captureSlotOriginFromPlayer) from the ROM's 32-bit accumulation.
     */
    private void syncPlayerToSlotOrigin(AbstractPlayableSprite player) {
        player.setCentreXPreserveSubpixel((short) (slotOriginX >> POSITION_SHIFT));
        player.setCentreYPreserveSubpixel((short) (slotOriginY >> POSITION_SHIFT));
        player.setSubpixelRaw(slotOriginX & 0xFFFF, slotOriginY & 0xFFFF);
    }
}
