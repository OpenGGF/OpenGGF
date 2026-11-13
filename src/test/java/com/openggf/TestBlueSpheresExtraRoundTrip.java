package com.openggf;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.control.InputHandler;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.bluespheres.S3kBlueSpheresExtra;
import com.openggf.game.sonic3k.bluespheres.S3kBlueSpheresScreen;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;

/**
 * Blue Spheres through the production GameLoop: the extra's title, a stage run
 * by the S3K special stage, the return to the results and the title again.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestBlueSpheresExtraRoundTrip {
    private InputHandler input;
    private GameLoop loop;

    @Test
    void stageReturnsToResultsThenTitleAndAFailedStageReturnsToTheTitle() throws Exception {
        var shared = SharedLevel.load(SonicGame.SONIC_3K, 0, 0);
        try {
            HeadlessTestFixture.builder().withSharedLevel(shared).build();
            var config = GameServices.configuration();
            config.setConfigValue(SonicConfiguration.DEBUG_VIEW_ENABLED, true);
            input = new InputHandler();
            loop = new GameLoop(input);
            loop.setGameMode(GameMode.LEVEL);
            var extra = new S3kBlueSpheresExtra();
            assertTrue(extra.available(GameServices.rom()), "S3K and Sonic 1 images are in the catalogue");
            loop.openNativeExtra(extra);
            assertEquals(GameMode.BLUE_SPHERES, loop.getCurrentGameMode());
            var screen = (S3kBlueSpheresScreen) loop.getNativeMenuScreen();

            steps(60);
            assertEquals("TITLE", screen.phaseName());
            tap(config.getInt(SonicConfiguration.JUMP));
            steps(30);
            tap(config.getInt(SonicConfiguration.START));
            stepUntil(GameMode.SPECIAL_STAGE, 10);
            assertSame(screen, loop.getNativeMenuScreen(), "the title stays open under the stage's entry fade");

            steps(90);
            tap(config.getInt(SonicConfiguration.SPECIAL_STAGE_COMPLETE_KEY));
            stepUntil(GameMode.BLUE_SPHERES, 300);
            assertTrue(screen.phaseName().startsWith("RESULTS"), screen.phaseName());
            steps(60);
            assertEquals("RESULTS", screen.phaseName());
            tap(config.getInt(SonicConfiguration.JUMP));
            steps(3);
            assertEquals(1, screen.level(), "an imperfect clear advances one level");
            steps(60);
            assertEquals("TITLE", screen.phaseName());

            tap(config.getInt(SonicConfiguration.START));
            stepUntil(GameMode.SPECIAL_STAGE, 10);
            steps(90);
            tap(config.getInt(SonicConfiguration.SPECIAL_STAGE_FAIL_KEY));
            stepUntil(GameMode.BLUE_SPHERES, 300);
            assertTrue(screen.phaseName().startsWith("TITLE"), screen.phaseName());
            assertEquals(1, screen.level(), "a failed stage keeps the level");
            steps(60);
            assertEquals("TITLE", screen.phaseName());
            assertEquals(0, GameServices.gameState().getEmeraldCount(), "Blue Spheres awards no emeralds");
        } finally {
            shared.dispose();
        }
    }

    private void tap(int key) {
        input.handleKeyEvent(key, GLFW_PRESS);
        loop.step();
        input.handleKeyEvent(key, GLFW_RELEASE);
        loop.step();
    }

    private void steps(int count) {
        for (int i = 0; i < count; i++) loop.step();
    }

    private void stepUntil(GameMode mode, int limit) {
        for (int i = 0; i < limit && loop.getCurrentGameMode() != mode; i++) loop.step();
        assertEquals(mode, loop.getCurrentGameMode());
    }
}
