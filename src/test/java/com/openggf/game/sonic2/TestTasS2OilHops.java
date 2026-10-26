package com.openggf.game.sonic2;

import com.openggf.game.GameServices;
import com.openggf.physics.CollisionSystem;
import com.openggf.physics.GroundSensor;
import com.openggf.physics.Sensor;
import com.openggf.physics.SensorResult;
import com.openggf.physics.TerrainCollisionManager;
import com.openggf.sprites.managers.PlayableSpriteMovement;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.FullReset;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.*;

/** 600M/1198S, 1779M/3101S, 2037M/3557S: jump repeatedly before oil submersion kills. */
@ExtendWith(SingletonResetExtension.class)
@FullReset
class TestTasS2OilHops {
    @Test
    void twoRealShortHopsRetainSuperspeedAndAvoidTheStandingSinkDeath() {
        var moving = fixture();
        int initialX = moving.player().getCentreX();
        for (int hop = 0; hop < 2; hop++) {
            tick(moving, true);
            assertTrue(moving.player().isJumping());
            assertTrue(moving.player().getAir());
            assertFalse(moving.oil().isStandingOnOil(moving.player()));
            int waits = 0;
            do { tick(moving, false); } while (moving.player().getAir() && ++waits < 80);
            assertFalse(moving.player().getAir(), "short jump must actually return to oil support");
            assertTrue(moving.oil().isStandingOnOil(moving.player()));
            assertFalse(moving.player().getRolling());
            assertEquals(moving.player().getXSpeed(), moving.player().getGSpeed());
            assertTrue(moving.player().getGSpeed() > 0x600, "oil landing must retain above-run-cap momentum: hop=" + hop + " g=" + moving.player().getGSpeed());
            assertFalse(moving.player().getDead());
        }
        assertTrue(moving.player().getCentreX() > initialX + 100);
        var sinking = fixture();
        for (int tick = 0; tick < 50 && !sinking.player().getDead(); tick++) tick(sinking, false);
        assertTrue(sinking.player().getDead(), "remaining on the same oil must exhaust submersion");
    }
    private static Fixture fixture() {
        TestEnvironment.configureGameModuleFixture(new Sonic2GameModule());
        GroundSensor.setLevelManager(null); // authored open oil pool, no terrain in its airborne column
        GameServices.camera().setMinX((short) 0);
        GameServices.camera().setMaxX((short) 0x6000);
        GameServices.camera().setMaxY((short) 0x1000);
        var player = new Sonic("sonic", (short) 0, (short) 0);
        player.setCentreX((short) 0x1000);
        player.setCentreY((short) (0x728 + 1 - player.getYRadius()));
        player.setAir(true);
        player.setXSpeed((short) 0x3000);
        player.setYSpeed((short) 0x100);
        var oil = new OilSurfaceManager();
        oil.updateSurface(player);
        assertTrue(oil.isStandingOnOil(player), "entry must land through Obj07");
        var terrain = new TerrainCollisionManager() {
            @Override public SensorResult[] getSensorResult(Sensor[] sensors) { return new SensorResult[] {null, null}; }
        };
        var movement = new PlayableSpriteMovement(player, new CollisionSystem(terrain), GameServices.gameState());
        return new Fixture(player, oil, movement);
    }
    private static void tick(Fixture fixture, boolean jump) {
        // S2 zone feature ordering: slides before Obj01, oil support after Obj01.
        fixture.oil().updateSlides(fixture.player());
        fixture.player().setJumpInputPressed(jump);
        fixture.movement().handleMovement(false, false, false, false, jump, false, false, false);
        fixture.oil().updateSurface(fixture.player());
        fixture.oil().endFrame();
    }
    private record Fixture(Sonic player, OilSurfaceManager oil, PlayableSpriteMovement movement) { }
}
