package com.openggf.game.sonic2.kis2;

import com.openggf.configuration.SonicConfigurationService;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.sonic2.objects.MonitorObjectInstance;
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

/** 3325M/5373S EHZ1: the edge monitor contact unrolls superspeed Knuckles. */
@RequiresRom(SonicGame.SONIC_2)
class TestTasKis2MonitorMadness {
    @AfterEach void cleanup() { TestEnvironment.resetAll(); }
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void leftPixelRideThenFastRollClearsTheMonitorAndReturnsToRunning(boolean fast) throws Exception {
        TestEnvironment.resetAll();
        Kis2TestRoms.configureExplicitCatalogue(SonicConfigurationService.getInstance());
        assumeTrue(Kis2TestRoms.lockOnDumpOrNull() != null);
        var settings = new GameplayCaptureSession.Settings(320, "knuckles", "", "off", null, null, null);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(Path.of(System.getProperty("sonic2.rom.path")), 0, 0, settings);
            int waits = 0;
            do {
                com.openggf.tests.reel.TestReelCapture.step(session,null); session.renderFrame();
                assertTrue(++waits < 600);
            } while (session.loop().getCurrentGameMode() != GameMode.LEVEL
                    || GameServices.level().hasPendingFreshLevelTransitionBoundary());
            var player = GameServices.sprites().getMainPlayable();
            while (player.getAir() && waits++ < 800) com.openggf.tests.reel.TestReelCapture.step(session,null);
            assertFalse(player.getAir());
            int floorY = player.getCentreY() + player.getYRadius();
            int monitorX = player.getCentreX() + 120;
            var monitor = new MonitorObjectInstance(new ObjectSpawn(monitorX, floorY - 16,
                    0x26, 4, 0, false, 0), "Monitor");
            GameServices.level().getObjectManager().addDynamicObject(monitor);
            com.openggf.tests.reel.TestReelCapture.step(session,null); // initialize the real monitor before the authored landing
            // Valid falling entry at the leftmost visible pixel (width_pixels=$0F).
            // Acquire the standing bit through actual SolidObject, never seed it.
            player.setCentreX((short) (monitorX - 15));
            player.setCentreY((short) (floorY - 16 - 15 - player.getYRadius()));
            player.setAir(true);
            player.setYSpeed((short) 0x100);
            player.setAnimationId(0);
            int landing = 0;
            while (!GameServices.level().getObjectManager().isRidingObject(player, monitor) && landing++ < 8)
                com.openggf.tests.reel.TestReelCapture.step(session,null);
            assertTrue(GameServices.level().getObjectManager().isRidingObject(player, monitor),
                    "must acquire actual monitor support at its left visible pixel");
            assertFalse(player.getAir());
            assertFalse(player.getRolling());
            // Declared legal superspeed on support, not a seeded roll/air exit.
            player.setGSpeed((short) (fast ? 0x4000 : 0x400));
            player.setXSpeed(player.getGSpeed());
            com.openggf.tests.reel.TestReelCapture.step(session,new Bk2FrameInput(0, AbstractPlayableSprite.INPUT_DOWN, 0, false, "roll"));
            assertTrue(player.getRolling(), "actual Down must enter the roll path");
            if (fast) {
                assertTrue(player.getCentreX() > monitorX + 26, "one movement pass clears the monitor");
                assertTrue(player.getAir(), "Obj26 edge check releases support after the roll");
                assertEquals(0x46, monitor.getCollisionFlags(), "the fast edge trick leaves the monitor intact");
                short retained = player.getXSpeed();
                for (int i = 0; i < 20 && player.getAir(); i++) com.openggf.tests.reel.TestReelCapture.step(session,null);
                assertFalse(player.getDead());
                assertFalse(player.getAir(), "must land through actual terrain");
                assertFalse(player.getRolling(), "the airborne roll returns to running on landing");
                assertEquals(retained, player.getGSpeed(), "descending air and landing preserve horizontal speed");
                assertTrue(player.getGSpeed() > 0x3000);
            } else {
                assertTrue(player.getCentreX() <= monitorX + 26, "slower roll cannot clear the shell in one pass");
                assertEquals(0x46, monitor.getCollisionFlags(), "the slower roll still rides the intact monitor");
                assertTrue(GameServices.level().getObjectManager().isRidingObject(player, monitor));
                assertFalse(player.getAir(), "slower control has not acquired the airborne conversion");
                assertTrue(player.getXSpeed() < 0x600);
            }
        }
    }
}
