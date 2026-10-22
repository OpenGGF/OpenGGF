package com.openggf.game.sonic1.credits;

import com.openggf.GameLoop;
import com.openggf.game.GameServices;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.GameplayCaptureSession;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_1)
class TestSonic1CreditsScrollBoundary {
    @Test
    void realCreditsLoadUsesEachFinalCameraAndTheRomLamppostBank() throws Exception {
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off", null, null, null);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic1RomAvailable().toPath(), 0, 0, settings);
            Sonic1CreditsManager credits = new Sonic1CreditsManager();
            Sonic1EndingProvider ending = new Sonic1EndingProvider();
            ending.bindCreditsManagerForLifecycleTest(credits);
            set(session.loop(), GameLoop.class, "endingProvider", ending);
            Method load = GameLoop.class.getDeclaredMethod("loadEndingDemoZone");
            load.setAccessible(true);

            set(credits, Sonic1CreditsManager.class, "creditsNum", 0);
            load.invoke(session.loop());
            assertEquals(0, GameServices.camera().getX());
            GameServices.camera().setX((short) 0x900);
            GameServices.parallax().update(0, 0, GameServices.camera(), 400);

            set(credits, Sonic1CreditsManager.class, "creditsNum", 7);
            load.invoke(session.loop());
            assertEquals(0x1360, GameServices.camera().getX());
            assertEquals(0x1360, com.openggf.level.ParallaxCheckpointAccess.capture(GameServices.parallax(), 0).bg2X());
            assertEquals(0x1360, com.openggf.level.ParallaxCheckpointAccess.capture(GameServices.parallax(), 0).bg3X());
            assertEquals((short) -0x1360, (short) GameServices.parallax().getHScroll()[80]);
            session.renderFrame();

            set(credits, Sonic1CreditsManager.class, "creditsNum", 3);
            load.invoke(session.loop());
            assertEquals(0x957, GameServices.camera().getX(), "credits lamppost camera remains caller-owned");
            assertEquals(0x4AB, com.openggf.level.ParallaxCheckpointAccess.capture(GameServices.parallax(), 3).bgX(), "EndDemo_LampVar saved BG X");
            assertEquals(0x5CC >> 1, GameServices.parallax().getVscrollFactorBG(),
                    "BgScroll_LZ replaces Y even though Lamp_LoadInfo restored its bank");
            assertEquals((short) -0x4AB, (short) GameServices.parallax().getHScroll()[0]);
            session.renderFrame();
        }
    }

    private static void set(Object target, Class<?> owner, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
