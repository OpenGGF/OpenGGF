package com.openggf.game.sonic2.objects;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.GameServices;
import com.openggf.game.GameMode;
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

/** 600M/1198S WFZ sliders: actual ObjC0 landing, charge input, transport and launch.
 * Authored catapult on a clear EHZ inlet; the original WFZ approach is not replayed.
 * Native loc_3BF66 / loc_3C020, s2.asm:80906-80980, pin 34fc7f13. */
@RequiresRom(SonicGame.SONIC_2)
class TestTasS2WfzSpindashStorage {
    @AfterEach void cleanup() { TestEnvironment.resetAll(); }
    @ParameterizedTest @ValueSource(booleans = {true, false})
    void catapultTransportsAndLaunchesTheInputChargedRiderWithoutClearingStorage(boolean charge) throws Exception {
        TestEnvironment.resetAll();
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off", null, null, null);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(Path.of(System.getProperty("sonic2.rom.path")), 0, 0, settings);
            int waits = 0;
            do { com.openggf.tests.reel.TestReelCapture.step(session,null); session.renderFrame(); assertTrue(++waits < 600); }
            while (session.loop().getCurrentGameMode() != GameMode.LEVEL
                    || GameServices.level().hasPendingFreshLevelTransitionBoundary());
            var player = GameServices.sprites().getMainPlayable();
            while (player.getAir() && waits++ < 800) com.openggf.tests.reel.TestReelCapture.step(session,null);
            var objects = GameServices.level().getObjectManager();
            int start = player.getCentreX() + 96, y = player.getCentreY() - 32;
            var platform = new SpeedLauncherObjectInstance(new ObjectSpawn(start, y, 0xC0, 8, 1, false, 0), "Slider");
            objects.addDynamicObject(platform);
            player.setCentreX((short) start); player.setCentreY((short) (y - 0x11 - player.getYRadius() + 3));
            player.setAir(true); player.setYSpeed((short) 0x100);
            com.openggf.tests.reel.TestReelCapture.step(session,null);
            assertTrue(objects.isRidingObject(player, platform), "actual top landing must arm the slider");
            boolean charged = false;
            int frame = 0;
            do {
                int held = charge ? AbstractPlayableSprite.INPUT_DOWN : 0;
                if (charge && (frame == 1 || frame == 3 || frame == 5)) held |= AbstractPlayableSprite.INPUT_JUMP;
                com.openggf.tests.reel.TestReelCapture.step(session,new Bk2FrameInput(frame, held, (held & AbstractPlayableSprite.INPUT_JUMP) != 0 ? 1 : 0, false, "catapult charge"));
                charged |= player.getSpindash();
                assertTrue(++frame < 30, "catapult must reach its endpoint");
            } while (!player.getAir());
            assertEquals(charge, charged, "recorded input must create the charge");
            assertEquals(start + 128, platform.getX());
            assertEquals(-0x400, player.getYSpeed());
            assertTrue(player.getXSpeed() > 0xC00);
            assertEquals(charge, player.getSpindash(), "ObjC0 launch leaves the spindash flag intact");
            if (charge) assertTrue(player.getSpindashCounter() > 0);
            assertTrue(player.getCentreX() > start + 96, "the real slider carried the rider");
        }
    }
}
