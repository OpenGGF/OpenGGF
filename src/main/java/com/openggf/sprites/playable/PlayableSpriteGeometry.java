package com.openggf.sprites.playable;

import com.openggf.game.GroundMode;
import com.openggf.physics.Sensor;

/** Stateless radii, sensor-offset and ground-orientation geometry for playable sprites. */
final class PlayableSpriteGeometry {
    private PlayableSpriteGeometry() { }

    static void updateSensorOffsets(AbstractPlayableSprite sprite, Sensor[] groundSensors,
            Sensor[] ceilingSensors, Sensor[] pushSensors, CharacterPhysicsSpec specification) {
        if (groundSensors == null || ceilingSensors == null || pushSensors == null) {
            return;
        }
        byte xRad = (byte) sprite.xRadius;
        byte yRad = (byte) sprite.yRadius;
        // SPG: Push sensors always use x = +/-10, regardless of rolling state.
        byte push = specification == null ? 10 : specification.sensors().pushRadius();
        if (groundSensors != null && groundSensors.length >= 2) {
            groundSensors[0].setOffset((byte) -xRad, yRad);
            groundSensors[1].setOffset(xRad, yRad);
        }
        if (ceilingSensors != null && ceilingSensors.length >= 2) {
            ceilingSensors[0].setOffset((byte) -xRad, (byte) -yRad);
            ceilingSensors[1].setOffset(xRad, (byte) -yRad);
        }
        if (pushSensors != null && pushSensors.length >= 2) {
            pushSensors[0].setOffset((byte) -push, (byte) 0);
            pushSensors[1].setOffset(push, (byte) 0);
        }
        // Preserve the character's virtual push-offset customization after radii updates.
        sprite.updatePushSensorYOffset();
    }

    static void updatePushSensorYOffset(AbstractPlayableSprite sprite, Sensor[] pushSensors,
            CharacterPhysicsSpec specification) {
        if (pushSensors == null || pushSensors.length < 2) {
            return;
        }
        // ROM: Y offset = +8 when (angle & 0x38) == 0, including near-flat angles.
        // See s2.asm:43517-43519 in CalcRoomInFront.
        boolean onFlatGround = !sprite.air && sprite.runningMode == GroundMode.GROUND
                && (sprite.angle & 0x38) == 0;
        byte yOffset = onFlatGround
                ? (specification == null ? (byte) 8 : specification.sensors().groundPushYOffset())
                : (byte) 0;
        // SPG: Push sensors always use x = +/-10, regardless of rolling state.
        byte push = specification == null ? 10 : specification.sensors().pushRadius();
        pushSensors[0].setOffset((byte) -push, yOffset);
        pushSensors[1].setOffset(push, yOffset);
    }

    static void updateShapeForRunningMode(AbstractPlayableSprite sprite,
            GroundMode newRunningMode, GroundMode oldRunningMode) {
        if (((GroundMode.CEILING.equals(newRunningMode) || GroundMode.GROUND.equals(newRunningMode))
                && (GroundMode.LEFTWALL.equals(oldRunningMode) || GroundMode.RIGHTWALL.equals(oldRunningMode)))
                || ((GroundMode.RIGHTWALL.equals(newRunningMode) || GroundMode.LEFTWALL.equals(newRunningMode))
                && (GroundMode.CEILING.equals(oldRunningMode) || GroundMode.GROUND.equals(oldRunningMode)))) {
            int oldHeight = sprite.getHeight();
            int oldWidth = sprite.getWidth();
            short oldCentreX = sprite.getCentreX();
            short oldCentreY = sprite.getCentreY();
            sprite.setHeight(oldWidth);
            sprite.setWidth(oldHeight);
            sprite.setX((short) (oldCentreX - (sprite.getWidth() / 2)));
            sprite.setY((short) (oldCentreY - (sprite.getHeight() / 2)));
        }
    }
}
