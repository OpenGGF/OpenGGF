package com.openggf.tests;

import com.openggf.game.GroundMode;
import com.openggf.physics.CollisionSystem;
import com.openggf.physics.Direction;
import com.openggf.physics.FrameCollisionPlan;
import com.openggf.physics.TerrainCollisionManager;
import com.openggf.sprites.managers.PlayableSpriteMovement;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Tails;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TestPlayableSpriteRollSpeed {

    @Test
    void s3kTailsStopsRollingBelowMinimumRollSpeedThreshold() throws Exception {
        Tails tails = new Tails("tails", (short) 0, (short) 0);
        tails.setCpuControlled(true);
        tails.setGroundMode(GroundMode.GROUND);
        tails.setDirection(Direction.LEFT);
        tails.setAir(false);
        tails.setRolling(true);
        tails.setCentreX((short) 0x1DBD);
        tails.setCentreY((short) 0x0471);
        tails.setGSpeed((short) 0xFF7B);
        tails.setXSpeed((short) 0xFF7B);
        tails.setYSpeed((short) 0);

        PlayableSpriteMovement movement = new PlayableSpriteMovement(
                tails, new NoWallCollisionSystem(), null);
        Method doRollSpeed = PlayableSpriteMovement.class.getDeclaredMethod("doRollSpeed");
        doRollSpeed.setAccessible(true);
        doRollSpeed.invoke(movement);

        assertFalse(tails.getRolling(),
                "Tails_RollSpeed clears Status_Roll once abs(ground_vel) falls below $80");
        assertEquals(0x0470, tails.getCentreY() & 0xFFFF,
                "Unrolling restores Tails' default y_radius=$0F from rolling y_radius=$0E and shifts y_pos up one pixel");
        assertEquals(9, tails.getXRadius(),
                "Unrolling restores Tails' default x_radius");
        assertEquals(15, tails.getYRadius(),
                "Unrolling restores Tails' default y_radius");
        assertEquals(0xFF81, tails.getGSpeed() & 0xFFFF,
                "Roll-stop preserves the post-friction ground velocity used for velocity conversion");
    }

    private static final class NoWallCollisionSystem extends CollisionSystem {
        private NoWallCollisionSystem() {
            super(new TerrainCollisionManager());
        }

        @Override
        public void resolveGroundWallCollision(FrameCollisionPlan plan, AbstractPlayableSprite sprite) {
        }
    }
}
