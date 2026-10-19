package com.openggf.mods.code;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_UP;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_Z;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;

import com.openggf.game.GameServices;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Super Sonic Bros. against a real S3K session through the production scene host (draws are
 * recorded, no GL): the title and menus by keyboard, choosing a fighter with the select-screen
 * cursor, every stage built from its act's level kit, a four-CPU battle played to the results
 * screen, and the move list. The fault boundary must catch nothing.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestSuperSonicBrosScene {
    private static final Path PROJECT = Path.of("examples/super-sonic-bros");

    @TempDir
    Path work;

    private SharedLevel level;

    @AfterEach
    void dispose() {
        if (level != null) level.dispose();
    }

    private ExampleModHarness open() throws Exception {
        level = SharedLevel.load(SonicGame.SONIC_3K, 0, 0);
        ExampleModHarness harness = ExampleModHarness.build(PROJECT, work.resolve("build"));
        harness.open(harness.apply(GameServices.module()), work.resolve("saves"), 400, 224);
        return harness;
    }

    private static String screen(ExampleModHarness harness) throws Exception {
        return (String) harness.scene().getClass().getMethod("screen").invoke(harness.scene());
    }

    private static void play(ExampleModHarness harness, int ticks) {
        for (int i = 0; i < ticks; i++) {
            harness.tick();
            harness.host().draw(null, null);
        }
    }

    private static boolean until(ExampleModHarness harness, String prefix, int limit) throws Exception {
        for (int i = 0; i < limit; i++) {
            if (screen(harness).startsWith(prefix)) return true;
            play(harness, 1);
        }
        return screen(harness).startsWith(prefix);
    }

    private static void clean(ExampleModHarness harness) {
        assertEquals(Map.of(), harness.findings(), "no faults");
        assertEquals(List.of(), harness.exits(), "the scene never left");
    }

    @Test
    void menusAndCharacterSelectByKeyboard() throws Exception {
        try (ExampleModHarness harness = open()) {
            play(harness, 60);
            assertEquals("title", screen(harness));
            harness.press(GLFW_KEY_ENTER);
            assertTrue(until(harness, "menu", 120), "title -> menu");
            harness.press(GLFW_KEY_ENTER);
            assertTrue(until(harness, "select", 120), "VERSUS -> character select");
            // P1's cursor starts below the roster: move it up onto a fighter and pick.
            harness.input().handleKeyEvent(GLFW_KEY_UP, GLFW_PRESS);
            play(harness, 16);
            harness.input().handleKeyEvent(GLFW_KEY_UP, GLFW_RELEASE);
            play(harness, 2);
            harness.press(GLFW_KEY_Z);
            play(harness, 4);
            harness.press(GLFW_KEY_ENTER);
            assertTrue(until(harness, "stage", 120), "a picked fighter and the default CPU can start");
            harness.press(GLFW_KEY_ENTER);
            assertTrue(until(harness, "battle", 400), "stage select -> battle");
            play(harness, 300);
            assertTrue(screen(harness).startsWith("battle"));
            clean(harness);
        }
    }

    @Test
    void everyStageBuildsAndAFullBattleReachesResults() throws Exception {
        try (ExampleModHarness harness = open()) {
            play(harness, 2);
            for (int stage = 0; stage < 10; stage++) {
                assertTrue(harness.debugJump("battle:" + stage + ":sonic/c9,tails/c9,knuckles/c9,robotnik/c9:stock1:items3"));
                play(harness, 200);
                assertTrue(screen(harness).startsWith("battle"), "stage " + stage + " fights");
            }
            assertTrue(harness.debugJump("battle:0:mecha/c9,eggrobo/c9,rhinobot/c9,sonic/c9:stock1:items3"));
            assertTrue(until(harness, "results", 60 * 60 * 8), "a one-stock four-CPU battle is decided");
            play(harness, 90);
            harness.press(GLFW_KEY_ENTER);
            assertTrue(until(harness, "select", 120), "results -> character select");
            clean(harness);
        }
    }

    @Test
    void classicRoundLostLeadsToContinueAndGameOver() throws Exception {
        try (ExampleModHarness harness = open()) {
            play(harness, 2);
            assertTrue(harness.debugJump("classic"));
            play(harness, 20);
            assertEquals("classic-setup", screen(harness));
            harness.press(GLFW_KEY_ENTER);
            assertTrue(until(harness, "classic-intro", 120), "setup -> round 1 card");
            harness.press(GLFW_KEY_ENTER);
            assertTrue(until(harness, "battle", 400), "round 1 starts");
            // Standing still against a CPU loses both stocks eventually.
            assertTrue(until(harness, "classic-continue", 60 * 60 * 10), "a lost round offers a continue");
            harness.press(GLFW_KEY_DOWN);
            harness.press(GLFW_KEY_ENTER);
            assertTrue(until(harness, "classic-game_over", 120), "declining ends the run");
            play(harness, 100);
            harness.press(GLFW_KEY_ENTER);
            assertTrue(until(harness, "menu", 120), "game over -> menu");
            clean(harness);
        }
    }

    @Test
    void moveListDemonstratesEveryFighter() throws Exception {
        try (ExampleModHarness harness = open()) {
            play(harness, 2);
            for (String fighter : List.of("sonic", "tails", "knuckles", "robotnik", "mecha", "eggrobo", "rhinobot")) {
                for (int move = 0; move < 18; move += 3) {
                    assertTrue(harness.debugJump("moves:" + fighter + ":" + move));
                    play(harness, 90);
                    assertEquals("moves", screen(harness));
                }
            }
            clean(harness);
        }
    }
}
