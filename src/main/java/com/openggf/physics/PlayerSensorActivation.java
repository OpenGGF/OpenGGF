package com.openggf.physics;

import com.openggf.game.GroundMode;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * Decides which of a player's six sensors scan this frame.
 *
 * <p>The ROM has no per-sensor enable. {@code SonicKnux_DoLevelCollision}
 * (sonic3k.asm:24035-24052, and the S1/S2 routines it descends from) computes a movement
 * quadrant from {@code GetArcTan(x_vel, y_vel)} and then calls only the wall, floor and
 * ceiling routines that quadrant needs; the engine models the same dispatch a second time
 * as an active flag on each sensor, so that {@link Sensor#scan()} can return {@code null}
 * for a probe the native routine would never have run.
 *
 * <p>Extracted from {@code AbstractPlayableSprite.updateSensors} so the quadrant table and
 * the reverse-gravity swap live beside the collision code that consumes them.
 */
public final class PlayerSensorActivation {

    private PlayerSensorActivation() { }

    /**
     * Updates sensor active states from the sprite's movement direction and ground mode.
     * Sets the flags in place rather than allocating, because this runs every frame for
     * every playable sprite.
     */
    public static void update(AbstractPlayableSprite sprite,
                              Sensor[] groundSensors,
                              Sensor[] ceilingSensors,
                              Sensor[] pushSensors) {
        // Which array each airborne probe scans. sub_11FD6 and sub_11FEE
        // (sonic3k.asm:24127-24151) are the wrappers every quadrant's floor and ceiling
        // check goes through: with Reverse_gravity_flag set the floor probe runs
        // Sonic_CheckCeiling and the ceiling probe runs Sonic_CheckFloor. The ROM simply
        // calls the other routine, so the activation has to follow the same swap that
        // CollisionSystem.floorProbeSensors/ceilingProbeSensors make, or the quadrant
        // switches off exactly the array the probe is about to scan.
        //
        // The grounded branch below is deliberately not swapped: Call_Player_AnglePos
        // (:22329) mirrors angle(a0) around Player_AnglePos instead, so ground attachment
        // keeps using the ground sensors with a ceiling ground mode.
        var gameState = sprite.currentGameStateOrNull();
        boolean reverseGravity = gameState != null && gameState.isReverseGravityActive();
        Sensor[] floorPair = reverseGravity ? ceilingSensors : groundSensors;
        Sensor[] ceilingPair = reverseGravity ? groundSensors : ceilingSensors;

        Sensor groundA = floorPair[0];
        Sensor groundB = floorPair[1];
        Sensor ceilingC = ceilingPair[0];
        Sensor ceilingD = ceilingPair[1];
        Sensor pushE = pushSensors[0];
        Sensor pushF = pushSensors[1];

        if (sprite.getAir()) {
            // ROM-accurate angle calculation via TrigLookupTable.calcAngle.
            // ROM: Sonic_DoLevelCollision (s2.asm:37547-37557)
            int motionAngle = TrigLookupTable.calcAngle(sprite.getXSpeed(), sprite.getYSpeed());

            // ROM quadrant calculation: subi.b #$20,d0 / andi.b #$C0,d0
            // This creates quadrants offset by 32 degrees:
            // - 0xC0: Angles 0-31 or 224-255 (mostly right)
            // - 0x00: Angles 32-95 (mostly down)
            // - 0x40: Angles 96-159 (mostly left)
            // - 0x80: Angles 160-223 (mostly up)
            int quadrant = ((motionAngle - 0x20) & 0xC0) & 0xFF;

            switch (quadrant) {
                case 0xC0 -> {
                    // Mostly Right (angles 0-31, 224-255): A, B, C, D, F active; E inactive
                    groundA.setActive(true);
                    groundB.setActive(true);
                    ceilingC.setActive(true);
                    ceilingD.setActive(true);
                    pushE.setActive(false);
                    pushF.setActive(true);
                }
                case 0x40 -> {
                    // Mostly Left (angles 96-159): A, B, C, D, E active; F inactive
                    groundA.setActive(true);
                    groundB.setActive(true);
                    ceilingC.setActive(true);
                    ceilingD.setActive(true);
                    pushE.setActive(true);
                    pushF.setActive(false);
                }
                case 0x80 -> {
                    // Mostly Up (angles 160-223): C, D, E, F active; A, B inactive
                    groundA.setActive(false);
                    groundB.setActive(false);
                    ceilingC.setActive(true);
                    ceilingD.setActive(true);
                    pushE.setActive(true);
                    pushF.setActive(true);
                }
                default -> {
                    // 0x00: Mostly Down (angles 32-95): A, B, E, F active; C, D inactive
                    groundA.setActive(true);
                    groundB.setActive(true);
                    ceilingC.setActive(false);
                    ceilingD.setActive(false);
                    pushE.setActive(true);
                    pushF.setActive(true);
                }
            }
        } else {
            // Ground sensors always active when grounded
            groundSensors[0].setActive(true);
            groundSensors[1].setActive(true);
            // Ceiling sensors always inactive when grounded
            ceilingSensors[0].setActive(false);
            ceilingSensors[1].setActive(false);

            // Push sensors active on floor/ceiling, disabled on walls
            GroundMode runningMode = sprite.getGroundMode();
            boolean pushActive = (runningMode == GroundMode.GROUND || runningMode == GroundMode.CEILING);
            // Use gSpeed (speed along surface) instead of xSpeed for direction
            if (sprite.getGSpeed() > 0) {
                pushE.setActive(false);
                pushF.setActive(pushActive);
            } else if (sprite.getGSpeed() < 0) {
                pushE.setActive(pushActive);
                pushF.setActive(false);
            } else {
                pushE.setActive(false);
                pushF.setActive(false);
            }
        }
    }
}
