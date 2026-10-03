package com.openggf;

import com.openggf.control.InputHandler;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Sonic 2 zone/act requests load through the ordinary level path: the S3K fresh
 * title boundary (held players, carried ring count) must not apply.
 */
@RequiresRom(SonicGame.SONIC_2)
class TestGameLoopZoneActHandoffS2 {
    @AfterEach
    void tearDown() {
        SessionManager.clear();
    }

    @ParameterizedTest
    @CsvSource({ "8, 0, 9, 0, 0x0060, 0x04CC, 0x0000, 0x046C", "9, 0, 10, 0, 0x0060, 0x012D, 0x0000, 0x00C8" })
    void zoneActRequestPlacesDestinationPlayersAndResetsRings(
            int fromZone, int fromAct, int toZone, int toAct,
            int playerX, int playerY, int cameraX, int cameraY) throws Exception {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_2);
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
}
