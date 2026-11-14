package com.openggf.sprites.playable;

import com.openggf.audio.GameSound;
import com.openggf.camera.Camera;
import com.openggf.game.CanonicalAnimation;
import com.openggf.level.LevelManager;
import com.openggf.physics.Direction;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;

/**
 * Sonic-specific respawn strategy: walks or spindashes in from the nearest floor
 * at the screen edge, opposite to the leader's movement direction.
 * <p>
 * If the leader is moving fast (above {@link #SPINDASH_SPEED_THRESHOLD}), Sonic
 * enters in a rolling state at spindash speed. Otherwise, he walks in at a moderate pace.
 * <p>
 * Engine extension with no ROM counterpart (the originals have no walking CPU
 * partner): when the walk-in finds no clear terrain floor on
 * {@link #JUMP_IN_AFTER_FAILED_FLOOR_PROBES} consecutive attempts, the sidekick
 * jumps in instead. It rises from below the screen in its rolling animation,
 * with the jump sound, on an arc that peaks above the leader, then falls under
 * normal physics and takes up following when it lands on terrain or on a solid
 * object. It takes no damage until then. If it falls out again it returns to
 * the spawn wait and the probe count starts over, which also rate-limits the
 * jump sound. See "Multi-Sidekick Daisy Chain" in
 * {@code docs/status/known-discrepancies.md}.
 */
public class SonicRespawnStrategy implements SidekickRespawnStrategy {

    /** Maximum downward probe depth in pixels (8 steps x 16px). */
    private static final int FLOOR_SEARCH_DEPTH = 128;

    /** Horizontal distance to scan inward when the first edge floor is embedded in terrain. */
    private static final int SPAWN_CLEARANCE_SEARCH_WIDTH = 96;

    /** Step size for horizontal clearance search. */
    private static final int SPAWN_CLEARANCE_STEP = 8;

    /** Leader ground speed threshold for spindash vs walk entry (subpixels). */
    private static final int SPINDASH_SPEED_THRESHOLD = 0x600;

    /** Initial rolling ground speed when entering via spindash (subpixels). */
    private static final int SPINDASH_RELEASE_SPEED = 0x800;

    /** Initial walking ground speed (subpixels). */
    private static final int WALK_ENTRY_SPEED = 0x200;

    /** Extra ground speed used to close distance on a moving leader. */
    private static final int CATCH_UP_SPEED_MARGIN = 0x200;

    /** Distance in pixels at which the sidekick is considered close enough to the leader. */
    private static final int APPROACH_COMPLETE_THRESHOLD = 32;

    /**
     * Consecutive walk-in attempts without a clear terrain floor before the
     * jump-in fallback. The spawn wait retries every 64 frames, so the third
     * failure comes at least 128 frames after the first.
     */
    static final int JUMP_IN_AFTER_FAILED_FLOOR_PROBES = 3;

    /** Horizontal distance behind the leader at which the jump-in starts. */
    private static final int JUMP_IN_START_X_OFFSET = 96;

    /** Distance below the bottom of the screen at which the jump-in starts. */
    private static final int JUMP_IN_START_BELOW_SCREEN = 32;

    /** Height of the arc's peak above the leader's centre. */
    private static final int JUMP_IN_APEX_CLEARANCE = 48;

    /** Smallest rise, so a leader at the bottom of the screen still gets an arc. */
    private static final int JUMP_IN_MIN_RISE = 64;

    /** Gravity for the scripted rise (subpixels per frame); the players' normal air gravity. */
    private static final int JUMP_IN_GRAVITY = 0x38;

    /** Horizontal speed limit while rising (subpixels). */
    private static final int JUMP_IN_MAX_RISE_X_SPEED = 0x600;

    /** Horizontal speed limit while falling toward the leader (subpixels). */
    private static final int JUMP_IN_MAX_FALL_X_SPEED = 0x300;

    /** Distance below the bottom of the screen at which a falling jump-in gives up. */
    private static final int JUMP_IN_FALL_OUT_MARGIN = 32;

    private enum JumpInPhase { NONE, RISING, FALLING }

    private final SidekickCpuController controller;
    private final int rollAnimId;

    private int failedFloorProbes;
    private JumpInPhase jumpInPhase = JumpInPhase.NONE;
    /** 8.8 fixed-point centre position and speed for the scripted rise. */
    private int jumpInX;
    private int jumpInY;
    private int jumpInYSpeed;
    private int jumpInRiseFramesLeft;

    public SonicRespawnStrategy(SidekickCpuController controller) {
        this.controller = controller;
        this.rollAnimId = controller != null
                ? controller.resolveAnimationId(CanonicalAnimation.ROLL)
                : -1;
    }

    @Override
    public boolean beginApproach(AbstractPlayableSprite sidekick, AbstractPlayableSprite leader) {
        jumpInPhase = JumpInPhase.NONE;
        Camera camera = sidekick.currentCamera();
        if (camera == null) {
            return false;
        }
        LevelManager levelManager = sidekick.currentLevelManager();
        if (levelManager == null || levelManager.getCurrentLevel() == null) {
            return false;
        }

        // Determine screen edge: leader moving right -> enter from left, and vice versa
        int edgeX;
        if (leader.getXSpeed() > 0) {
            edgeX = camera.getX() - 32;
        } else if (leader.getXSpeed() < 0) {
            edgeX = camera.getX() + 320 + 32;
        } else {
            // Stopped — default to left edge
            edgeX = camera.getX() - 32;
        }

        int probeY = leader.getCentreY();
        int searchDirection = leader.getCentreX() >= edgeX ? 1 : -1;
        SpawnPlacement placement = findClearFloorPlacement(sidekick, edgeX, probeY, searchDirection);
        if (placement == null) {
            // No clear ground found. Stay in SPAWNING until the walk-in has had
            // its chances, then jump in instead.
            failedFloorProbes++;
            if (failedFloorProbes < JUMP_IN_AFTER_FAILED_FLOOR_PROBES) {
                return false;
            }
            failedFloorProbes = 0;
            beginJumpIn(sidekick, leader, camera);
            return true;
        }
        failedFloorProbes = 0;

        // Place sidekick on the floor at the screen edge, or the nearest inward
        // floor position whose body probes are clear of all-sides terrain.
        sidekick.setCentreX((short) placement.centreX());
        sidekick.setCentreY((short) placement.centreY());

        // Reset state flags
        sidekick.setAir(false);
        sidekick.setDead(false);
        sidekick.setHurt(false);
        sidekick.clearDrowningDeathState();
        sidekick.setSpindash(false);
        sidekick.setSpindashCounter((short) 0);
        sidekick.setObjectMappingFrameControl(false);

        // Let normal physics drive movement
        sidekick.setControlLocked(false);
        ObjectControlState.none().applyTo(sidekick);
        sidekick.setForcedAnimationId(-1);

        // Face toward the leader
        boolean leaderIsRight = leader.getCentreX() >= placement.centreX();
        sidekick.setDirection(leaderIsRight ? Direction.RIGHT : Direction.LEFT);

        // Choose walk vs spindash based on the fastest relevant leader speed.
        // Staggered multi-sidekick entries may approach a slow direct parent for
        // spacing, but still need enough speed to catch the main player.
        int speedReference = entrySpeedReference(leader);
        if (speedReference > SPINDASH_SPEED_THRESHOLD) {
            sidekick.setRolling(true);
            sidekick.setGSpeed(entrySpeedTowardLeader(speedReference, leaderIsRight, SPINDASH_RELEASE_SPEED));
        } else {
            sidekick.setGSpeed(entrySpeedTowardLeader(speedReference, leaderIsRight, WALK_ENTRY_SPEED));
        }

        // gSpeed drives ground movement; clear axis speeds
        sidekick.setXSpeed((short) 0);
        sidekick.setYSpeed((short) 0);

        return true;
    }

    private void beginJumpIn(AbstractPlayableSprite sidekick, AbstractPlayableSprite leader, Camera camera) {
        // Enter from behind the leader, as the walk-in does.
        int side = leader.getXSpeed() < 0 ? 1 : -1;
        int startX = leader.getCentreX() + side * JUMP_IN_START_X_OFFSET;
        int startY = camera.getY() + camera.getHeight() + JUMP_IN_START_BELOW_SCREEN;
        int rise = Math.max(JUMP_IN_MIN_RISE, startY - (leader.getCentreY() - JUMP_IN_APEX_CLEARANCE));
        // v^2 = 2gh, with h converted to subpixels.
        int launchSpeed = (int) Math.ceil(Math.sqrt(2.0 * JUMP_IN_GRAVITY * rise * 256));

        jumpInPhase = JumpInPhase.RISING;
        jumpInX = startX << 8;
        jumpInY = startY << 8;
        jumpInYSpeed = -launchSpeed;
        jumpInRiseFramesLeft = (launchSpeed + JUMP_IN_GRAVITY - 1) / JUMP_IN_GRAVITY;

        sidekick.setDead(false);
        sidekick.setHurt(false);
        sidekick.clearDrowningDeathState();
        sidekick.setSpindash(false);
        sidekick.setSpindashCounter((short) 0);
        sidekick.setObjectMappingFrameControl(false);
        sidekick.setOnObject(false);
        sidekick.setPushing(false);
        sidekick.setAir(true);
        sidekick.setRolling(true);
        sidekick.setDirection(side < 0 ? Direction.RIGHT : Direction.LEFT);
        sidekick.setCentreX((short) startX);
        sidekick.setCentreY((short) startY);
        sidekick.setXSpeed((short) 0);
        sidekick.setYSpeed((short) jumpInYSpeed);
        sidekick.setGSpeed((short) 0);
        holdScriptedRise(sidekick);

        PlayerAbilityAudio.play(sidekick, GameSound.JUMP);
    }

    /** The rise is positioned by this strategy: no physics, no collision, no touch responses. */
    private void holdScriptedRise(AbstractPlayableSprite sidekick) {
        sidekick.setControlLocked(true);
        ObjectControlState.nativeBit7FullControl().applyTo(sidekick);
        sidekick.setForcedAnimationId(rollAnimId);
    }

    private boolean updateJumpIn(AbstractPlayableSprite sidekick, AbstractPlayableSprite leader) {
        controller.setApproachInput(false, false);
        if (jumpInPhase == JumpInPhase.RISING) {
            holdScriptedRise(sidekick);
            // Steer to be over the leader at the top of the arc.
            int dx = leader.getCentreX() - (jumpInX >> 8);
            int xSpeed = clamp((dx << 8) / Math.max(1, jumpInRiseFramesLeft), JUMP_IN_MAX_RISE_X_SPEED);
            jumpInX += xSpeed;
            jumpInY += jumpInYSpeed;
            jumpInYSpeed += JUMP_IN_GRAVITY;
            jumpInRiseFramesLeft--;
            sidekick.setCentreX((short) (jumpInX >> 8));
            sidekick.setCentreY((short) (jumpInY >> 8));
            sidekick.setXSpeed((short) xSpeed);
            sidekick.setYSpeed((short) Math.min(jumpInYSpeed, 0));
            if (dx != 0) {
                sidekick.setDirection(dx > 0 ? Direction.RIGHT : Direction.LEFT);
            }
            if (jumpInYSpeed >= 0) {
                // Top of the arc: hand over to normal physics so the fall can
                // land on terrain or on a solid object.
                jumpInPhase = JumpInPhase.FALLING;
                sidekick.setControlLocked(false);
                ObjectControlState.none().applyTo(sidekick);
                sidekick.setForcedAnimationId(-1);
                sidekick.setAir(true);
            }
            return false;
        }

        if (!sidekick.getAir()) {
            jumpInPhase = JumpInPhase.NONE;
            return true;
        }
        Camera camera = sidekick.currentCamera();
        if (camera != null
                && sidekick.getCentreY() > camera.getY() + camera.getHeight() + JUMP_IN_FALL_OUT_MARGIN) {
            // Nothing to land on. Wait off screen and try again later.
            jumpInPhase = JumpInPhase.NONE;
            controller.returnFailedJumpInToSpawning();
            return false;
        }
        int dx = leader.getCentreX() - sidekick.getCentreX();
        sidekick.setXSpeed((short) clamp(dx << 5, JUMP_IN_MAX_FALL_X_SPEED));
        return false;
    }

    private static int clamp(int value, int limit) {
        return Math.max(-limit, Math.min(limit, value));
    }

    /** True from the jump sound until the sidekick lands or falls out. */
    boolean isJumpingIn() {
        return jumpInPhase != JumpInPhase.NONE;
    }

    /** The controller has taken the sidekick out of the approach (for example at the level's bottom edge). */
    void cancelJumpIn() {
        jumpInPhase = JumpInPhase.NONE;
    }

    private SpawnPlacement findClearFloorPlacement(AbstractPlayableSprite sidekick, int edgeX,
                                                   int probeY, int searchDirection) {
        for (int offset = 0; offset <= SPAWN_CLEARANCE_SEARCH_WIDTH; offset += SPAWN_CLEARANCE_STEP) {
            int candidateX = edgeX + (offset * searchDirection);
            Integer floorY = findFloorY(candidateX, probeY);
            if (floorY == null) {
                continue;
            }
            int centreY = floorY - (sidekick.getHeight() / 2);
            if (isWallClearForSpawn(sidekick, candidateX, centreY)) {
                return new SpawnPlacement(candidateX, centreY);
            }
        }
        return null;
    }

    private Integer findFloorY(int probeX, int probeY) {
        TerrainCheckResult floorResult = null;
        int foundProbeY = probeY;
        for (int step = 0; step <= FLOOR_SEARCH_DEPTH / 16; step++) {
            int testY = probeY + (step * 16);
            TerrainCheckResult result = ObjectTerrainUtils.checkFloorDist(probeX, testY);
            if (result.foundSurface() && result.distance() >= 0) {
                floorResult = result;
                foundProbeY = testY;
                break;
            }
        }

        if (floorResult == null) {
            return null;
        }
        return foundProbeY + floorResult.distance();
    }

    private static boolean isWallClearForSpawn(AbstractPlayableSprite sidekick, int centreX, int centreY) {
        int xRadius = sidekick.getXRadius();
        int yRadius = sidekick.getYRadius();
        int leftX = centreX - xRadius;
        int rightX = centreX + xRadius;
        int lowerBodyY = centreY + yRadius - 1;

        return !ObjectTerrainUtils.checkLeftWallDist(leftX, centreY).hasCollision()
                && !ObjectTerrainUtils.checkLeftWallDist(leftX, lowerBodyY).hasCollision()
                && !ObjectTerrainUtils.checkRightWallDist(rightX, centreY).hasCollision()
                && !ObjectTerrainUtils.checkRightWallDist(rightX, lowerBodyY).hasCollision();
    }

    private record SpawnPlacement(int centreX, int centreY) {}

    private int entrySpeedReference(AbstractPlayableSprite leader) {
        int speed = Math.abs(leader.getGSpeed());
        AbstractPlayableSprite rootLeader = controller.getRootLeader();
        if (rootLeader != null) {
            speed = Math.max(speed, Math.abs(rootLeader.getGSpeed()));
        }
        return speed;
    }

    private static short entrySpeedTowardLeader(int speedReference, boolean leaderIsRight, int minimumSpeed) {
        int speed = Math.max(minimumSpeed, speedReference + CATCH_UP_SPEED_MARGIN);
        speed = Math.min(Short.MAX_VALUE, speed);
        return (short) (leaderIsRight ? speed : -speed);
    }

    @Override
    public boolean updateApproaching(AbstractPlayableSprite sidekick, AbstractPlayableSprite leader,
                                     int frameCounter) {
        if (jumpInPhase != JumpInPhase.NONE) {
            return updateJumpIn(sidekick, leader);
        }
        // Keep pressing toward the leader so friction doesn't stop the sidekick.
        int dx = leader.getCentreX() - sidekick.getCentreX();
        controller.setApproachInput(dx < 0, dx > 0);

        return Math.abs(dx) <= APPROACH_COMPLETE_THRESHOLD;
    }

    @Override
    public boolean requiresPhysics() {
        return jumpInPhase != JumpInPhase.RISING;
    }

    /**
     * The jump-in homes on its target and completes on landing, so the shared
     * controller must not complete it early when it crosses the main player.
     */
    @Override
    public boolean approachMovementUsesPhysics() {
        return jumpInPhase == JumpInPhase.NONE;
    }
}
