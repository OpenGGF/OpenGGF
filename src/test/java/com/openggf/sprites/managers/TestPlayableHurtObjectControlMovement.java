package com.openggf.sprites.managers;

import com.openggf.game.GameServices;
import com.openggf.game.rules.GameRules;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.sprites.playable.ObjectControlState;
import com.openggf.tests.FullReset;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Isolated;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SingletonResetExtension.class)
@FullReset
@Isolated
class TestPlayableHurtObjectControlMovement {
    @BeforeEach
    void setup() {
        TestEnvironment.configureGameModuleFixture(new Sonic2GameModule());
    }

    @Test
    void injuredTubeRiderStillIntegratesOldVelocityAndNativeHurtGravity() throws Exception {
        var player = controlledPlayer(true);
        int beforeY = player.getCentreY();
        player.setYSpeed((short) 0x100);

        invoke(player, "doObjectMoveAndFall");

        assertEquals(beforeY + 1, player.getCentreY(), "Hurt runs ObjectMove before adding gravity");
        assertEquals(0x130, player.getYSpeed(), "Obj01_Hurt_Normal adds $30 even with obj_control=$81");
        assertTrue(player.isHurt(), "fixBugs=0 does not wake the hurt player on tube capture");
        assertTrue(player.isObjectControlled());
    }

    @Test
    void ordinaryTubeControlStillSuppressesGravityAndBoundaries() throws Exception {
        var player = controlledPlayer(false);
        player.setYSpeed((short) 0x100);
        GameServices.camera().setMinX((short) 0x200);
        GameServices.camera().setMaxX((short) 0x300);
        int beforeX = player.getCentreX();

        invoke(player, "applyGravity");
        invoke(player, "doLevelBoundary");

        assertEquals(0x100, player.getYSpeed());
        assertEquals(beforeX, player.getCentreX());
    }

    @Test
    void hurtRoutineKeepsItsBoundaryPassWhileATubeControlsNormalMovement() throws Exception {
        var player = controlledPlayer(true);
        GameServices.camera().setMinX((short) 0x200);
        GameServices.camera().setMaxX((short) 0x300);
        player.setXSpeed((short) -0x100);

        invoke(player, "doLevelBoundary");

        assertEquals(0x210, player.getCentreX(), "Hurt calls Sonic_LevelBound after HurtStop");
        assertEquals(0, player.getXSpeed());
        assertTrue(player.isObjectControlled(), "boundary clamping does not release the tube");
    }

    private static TestablePlayableSprite controlledPlayer(boolean hurt) {
        var player = new TestablePlayableSprite("sonic", (short) 100, (short) 100);
        player.setGameRulesForTest(GameRules.SONIC_2);
        player.setAir(true);
        player.setHurt(hurt);
        ObjectControlState.nativeBit7FullControl().applyTo(player);
        return player;
    }

    private static void invoke(TestablePlayableSprite player, String name) throws Exception {
        var method = PlayableSpriteMovement.class.getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(player.getMovementManager());
    }
}
