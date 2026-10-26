package com.openggf.game.sonic2.kis2;

import com.openggf.configuration.SonicConfigurationService;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.GameServices;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.sonic2.objects.badniks.NebulaBadnikInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.GameplayCaptureSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 3339M/5385S: stay on the SCZ plane to retain the enemy chain; actual jump and
 * landing are the reset control. Two authored nearby Nebulas replace the long
 * enemy route; the native autoscroller, plane, input, touch and scoring run.
 * This does not claim the author's maximum campaign score of 372900.
 */
@RequiresRom(SonicGame.SONIC_2)
class TestTasKis2SkyChaseChain {
    @AfterEach void cleanup() { TestEnvironment.resetAll(); }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void planeSupportRetainsChainButJumpAndRelandingResetIt(boolean jumpBetweenKills) throws Exception {
        TestEnvironment.resetAll();
        Kis2TestRoms.configureExplicitCatalogue(SonicConfigurationService.getInstance());
        assumeTrue(Kis2TestRoms.lockOnDumpOrNull() != null, "KiS2 lock-on image required");
        var settings = new GameplayCaptureSession.Settings(320, "knuckles", "", "off", null, null, null);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(Path.of(System.getProperty("sonic2.rom.path")), 8, 0, settings);
            assertEquals("kis2", assertInstanceOf(DelegatingGameModule.class, GameServices.module()).patchId());
            var player = GameServices.sprites().getMainPlayable();
            var objects = GameServices.level().getObjectManager();
            int waits = 0;
            while (!objects.isRidingObject(player) && waits++ < 180) com.openggf.tests.reel.TestReelCapture.step(session,null);
            assertTrue(objects.isRidingObject(player), "native SCZ spawn must land on ObjB2");
            assertFalse(player.getAir());
            int cameraX = GameServices.camera().getX();
            // Legal moving, supported entry. Down must execute the actual roll.
            player.setGSpeed((short) 0x300);
            player.setXSpeed((short) 0x300);
            com.openggf.tests.reel.TestReelCapture.step(session,input(AbstractPlayableSprite.INPUT_DOWN));
            assertTrue(player.getRolling(), "Down must enter the attacking roll on the plane");
            int before = GameServices.gameState().getScore();
            killNearbyEnemy(session, player);
            assertEquals(100, GameServices.gameState().getScore() - before);
            assertFalse(player.getAir(), "grounded enemy rebound writes velocity without setting air");

            if (jumpBetweenKills) {
                com.openggf.tests.reel.TestReelCapture.step(session,input(AbstractPlayableSprite.INPUT_JUMP));
                assertTrue(player.getAir(), "fresh jump must leave real Tornado support");
                assertTrue(player.isJumping());
                waits = 0;
                while (player.getAir() && waits++ < 180) com.openggf.tests.reel.TestReelCapture.step(session,null);
                assertFalse(player.getAir(), "control must really reland, not time out");
                assertTrue(objects.isRidingObject(player));
                player.setGSpeed((short) 0x300);
                player.setXSpeed((short) 0x300);
                com.openggf.tests.reel.TestReelCapture.step(session,input(AbstractPlayableSprite.INPUT_DOWN));
            } else {
                for (int i = 0; i < 4; i++) {
                    com.openggf.tests.reel.TestReelCapture.step(session,input(AbstractPlayableSprite.INPUT_DOWN));
                    assertTrue(objects.isRidingObject(player));
                    assertFalse(player.getAir());
                }
            }
            before = GameServices.gameState().getScore();
            killNearbyEnemy(session, player);
            assertEquals(jumpBetweenKills ? 100 : 200, GameServices.gameState().getScore() - before);
            assertTrue(GameServices.camera().getX() > cameraX, "the autoscroller must actually advance");
            assertFalse(player.getDead());
        }
    }

    private static void killNearbyEnemy(GameplayCaptureSession session, AbstractPlayableSprite player) {
        var enemy = new NebulaBadnikInstance(new ObjectSpawn(
                player.getCentreX() + 2, player.getCentreY(), 0x99, 0x12, 0, false, 0));
        GameServices.level().getObjectManager().addDynamicObject(enemy);
        com.openggf.tests.reel.TestReelCapture.step(session,input(AbstractPlayableSprite.INPUT_DOWN));
        assertTrue(enemy.isDestroyed(), "real ROM-backed touch dispatch must destroy the authored Nebula");
    }

    private static Bk2FrameInput input(int mask) {
        return new Bk2FrameInput(0, mask, (mask & AbstractPlayableSprite.INPUT_JUMP) != 0 ? 1 : 0, false, "");
    }
}
