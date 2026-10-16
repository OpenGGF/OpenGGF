package com.openggf.game.sonic2.objects;

import com.openggf.game.GameModuleRegistry;
import com.openggf.game.session.SessionManager;
import com.openggf.game.solid.ContactKind;
import com.openggf.game.solid.PlayerSolidContactResult;
import com.openggf.game.solid.PostContactState;
import com.openggf.game.solid.PreContactState;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.TestablePlayableSprite;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class TestFlipperRollOwnership {
    @BeforeEach
    void setUp() {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_2);
    }

    @AfterEach
    void tearDown() {
        SessionManager.clear();
        GameModuleRegistry.reset();
    }

    @Test
    void sustainedRideAndLeaveKeepNativeStateOwnedByItsWriter() throws Exception {
        for (boolean initial : new boolean[] {false, true}) {
            var player = new TestablePlayableSprite("sonic", (short) 0x120, (short) 0xF4);
            var flipper = new FlipperObjectInstance(new ObjectSpawn(0x120, 0x100, 0x86, 0, 0, false, 0), "Flipper");
            player.setPinballMode(initial);
            player.setSpindash(initial);
            for (int frame = 0; frame < 8; frame++) {
                contact(flipper, player, true);
                assertTrue(player.getRolling(), "Obj86 sets rolling and suppresses ordinary movement throughout the ride");
                assertTrue(player.isObjectControlSuppressesMovement());
                assertEquals(initial, player.getPinballMode(), "Obj86 does not own $39");
                assertEquals(initial, player.getSpindash());
            }
            // Obj84 can change its native alias after capture. Release must not
            // overwrite it with the value observed by an earlier object owner.
            player.setPinballMode(!initial);
            player.setSpindash(!initial);
            contact(flipper, player, false);
            assertFalse(player.isObjectControlSuppressesMovement());
            assertEquals(!initial, player.getPinballMode());
            assertEquals(!initial, player.getSpindash());
            assertTrue(player.getRolling(), "leaving changes control ownership, not roll status");
        }
    }

    private static void contact(FlipperObjectInstance flipper, AbstractPlayableSprite player, boolean standing)
            throws Exception {
        Method method = FlipperObjectInstance.class.getDeclaredMethod(
                "applyCheckpointContact", AbstractPlayableSprite.class, PlayerSolidContactResult.class);
        method.setAccessible(true);
        method.invoke(flipper, player, new PlayerSolidContactResult(
                standing ? ContactKind.TOP : ContactKind.NONE, standing, false, false, false,
                PreContactState.ZERO, PostContactState.ZERO, 0));
    }
}
