package com.openggf;

import com.openggf.control.InputHandler;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.RecordingFrameDriver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sonic 1 zone/act requests load through the ordinary level path: the S3K fresh
 * title boundary (held players, carried ring count) must not apply, in either
 * the live loop or the headless recording driver.
 */
@RequiresRom(SonicGame.SONIC_1)
class TestGameLoopZoneActHandoffS1 {
    @AfterEach
    void tearDown() {
        SessionManager.clear();
    }

    @ParameterizedTest
    @CsvSource({ "5, 1, 5, 2, 0x0B80, 0x0000, 0x0AE0, 0x0000", "5, 2, 6, 0, 0x2140, 0x05AC, 0x20A0, 0x0510" })
    void zoneActRequestPlacesDestinationPlayersAndResetsRings(
            int fromZone, int fromAct, int toZone, int toAct,
            int playerX, int playerY, int cameraX, int cameraY) throws Exception {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_1);
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(fromZone, fromAct).build();
        GameLoop loop = new GameLoop(new InputHandler());
        loop.changeGameModeWithoutRewindBoundary(GameMode.LEVEL);
        var level = SessionManager.getCurrentGameplayMode().getLevelManager();
        level.getLevelGamestate().setRings(42);

        Method doZoneAct = GameLoop.class.getDeclaredMethod(
                "doZoneAct", int.class, int.class, int.class);
        doZoneAct.setAccessible(true);
        doZoneAct.invoke(loop, toZone, toAct, -1);

        assertFalse(level.hasPendingFreshLevelTransitionBoundary());
        assertEquals(0, level.getLevelGamestate().getRings());
        assertEquals(playerX, fixture.sprite().getCentreX() & 0xFFFF, "player x");
        assertEquals(playerY, fixture.sprite().getCentreY() & 0xFFFF, "player y");
        assertEquals(cameraX, GameServices.camera().getX() & 0xFFFF, "camera x");
        assertEquals(cameraY, GameServices.camera().getY() & 0xFFFF, "camera y");
    }

    @ParameterizedTest
    @CsvSource({ "5, 1, 5, 2, 0x0B80, 0x0000", "5, 2, 6, 0, 0x2140, 0x05AC" })
    void recordingDriverLoadsDestinationThroughItsTitleCard(
            int fromZone, int fromAct, int toZone, int toAct,
            int playerX, int playerY) throws Exception {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_1);
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(fromZone, fromAct).build();
        var level = SessionManager.getCurrentGameplayMode().getLevelManager();
        level.getLevelGamestate().setRings(42);
        level.requestZoneAndAct(toZone, toAct, true);
        RecordingFrameDriver driver = new RecordingFrameDriver(fixture.sprite());

        boolean loaded = false;
        boolean titleCardSeen = false;
        for (int frame = 0; frame < 600; frame++) {
            driver.stepFrame(false, false, false, false, false);
            assertFalse(level.hasPendingFreshLevelTransitionBoundary(), "frame " + frame);
            if (!loaded && level.getCurrentZone() == toZone && level.getCurrentAct() == toAct) {
                loaded = true;
                assertEquals(0, level.getLevelGamestate().getRings());
                assertEquals(playerX, fixture.sprite().getCentreX() & 0xFFFF, "player x");
                assertEquals(playerY, fixture.sprite().getCentreY() & 0xFFFF, "player y");
            }
            titleCardSeen |= loaded && titleCardActive(driver);
            if (titleCardSeen && !titleCardActive(driver)) {
                break;
            }
        }
        assertTrue(loaded, "destination loaded");
        assertTrue(titleCardSeen, "destination title card ran");
    }

    private static boolean titleCardActive(RecordingFrameDriver driver) throws Exception {
        java.lang.reflect.Field field = RecordingFrameDriver.class.getDeclaredField("normalTitleCardActive");
        field.setAccessible(true);
        return field.getBoolean(driver);
    }
}
