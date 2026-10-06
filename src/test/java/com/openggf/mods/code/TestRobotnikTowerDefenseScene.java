package com.openggf.mods.code;

import com.openggf.game.GameServices;
import com.openggf.mods.scene.host.SceneHostTestAccess;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

/** Exercises real scene input, art decoding and owner fault isolation, with no GL. */
@RequiresRom(SonicGame.SONIC_3K)
class TestRobotnikTowerDefenseScene {
    @TempDir Path work;
    private SharedLevel level;
    private ExampleModHarness harness;

    @AfterEach void close() throws Exception {
        if (harness != null) harness.close();
        if (level != null) level.dispose();
    }

    private void open() throws Exception {
        level = SharedLevel.load(SonicGame.SONIC_3K, 0, 0);
        harness = ExampleModHarness.build(Path.of("examples/robotnik-tower-defense"), work.resolve("build"));
        harness.open(harness.apply(GameServices.module()), work.resolve("saves"), 400, 224);
        play(2);
    }

    @Test void keyboardBuildsAndPauseAndHelpFreezeEveryGameplayTimer() throws Exception {
        open();
        harness.press(GLFW_KEY_ENTER); // title -> preparation
        assertEquals("BATTLE", scene("screen"));
        harness.press(GLFW_KEY_ENTER); // open build selection
        harness.press(GLFW_KEY_ENTER); // construct Snale at the first site
        assertEquals(120, battle("scrap"));
        harness.press(GLFW_KEY_RIGHT);
        harness.press(GLFW_KEY_2);
        harness.press(GLFW_KEY_ENTER);
        assertEquals(75, battle("scrap"));
        harness.press(GLFW_KEY_E);
        play(60);
        harness.press(GLFW_KEY_P);
        assertEquals(true, scene("paused"));
        Object ticks = battle("ticks");
        play(120);
        assertEquals(ticks, battle("ticks"));
        harness.press(GLFW_KEY_P);
        play(20);
        assertNotEquals(ticks, battle("ticks"));
        harness.press(GLFW_KEY_H);
        ticks = battle("ticks");
        play(60);
        assertEquals(ticks, battle("ticks"));
        harness.press(GLFW_KEY_ENTER);
        assertEquals("BATTLE", scene("screen"));
        assertEquals(Map.of(), harness.findings());
    }

    @Test void mouseCanBuyUpgradeAndSellWithoutActingOnButtonRelease() throws Exception {
        open();
        click(200, 149);
        click(132, 195); // anti-air card
        click(66, 109); // upper-left pad
        assertEquals(95, battle("scrap"));
        click(91, 219); // upgrade selected tower
        assertEquals(40, battle("scrap"));
        click(158, 219); // sell; 75% of total 110
        assertEquals(122, battle("scrap"));
        assertEquals(Map.of(), harness.findings());
    }

    @Test void backspaceCancelsTheShopBeforeItsMappedStartCanPause() throws Exception {
        open();
        harness.press(GLFW_KEY_ENTER);
        harness.press(GLFW_KEY_ENTER);
        harness.press(GLFW_KEY_BACKSPACE);
        assertEquals(false, scene("paused"));
        assertEquals(150, battle("scrap"));
        harness.press(GLFW_KEY_ENTER);
        harness.press(GLFW_KEY_ENTER);
        assertEquals(120, battle("scrap"));
        harness.press(GLFW_KEY_E);
        Object ticks = battle("ticks");
        for (int i = 0; i < 50; i++) harness.host().draw(null, null);
        assertEquals(ticks, battle("ticks"), "rendering cannot advance the simulation");
    }

    @Test void allWavesAndTerminalScreensDecodeAndDrawWithoutFaults() throws Exception {
        open();
        for (int wave = 1; wave <= 15; wave++) {
            assertTrue(harness.debugJump("wave:" + wave));
            play(80);
            assertEquals(wave, battle("wave"));
            assertEquals(Map.of(), harness.findings(), "wave " + wave);
        }
        assertTrue(harness.debugJump("win"));
        play(3);
        assertEquals("RESULT", scene("screen"));
        assertEquals("WON", battle("phase"));
        assertTrue(harness.debugJump("lose"));
        play(3);
        assertEquals("RESULT", scene("screen"));
        assertEquals("LOST", battle("phase"));
        assertFalse(harness.debugJump("wave:999"));
        assertFalse(harness.debugJump("wave:bad"));
        assertTrue(harness.debugJump("title"));
        harness.press(GLFW_KEY_DOWN);
        harness.press(GLFW_KEY_DOWN);
        harness.press(GLFW_KEY_ENTER);
        assertEquals(java.util.List.of("game"), harness.exits());
        assertEquals(Map.of(), harness.findings());
    }

    @Test void demonstrationWaveRecordsCannotLeakIntoANormalGame() throws Exception {
        open();
        assertTrue(harness.debugJump("wave:1"));
        for (int i = 0; i < 5000 && !battle("phase").equals("PREP"); i++) harness.tick();
        assertEquals("PREP", battle("phase"));
        Path records = work.resolve("saves/mods/robotnik-tower-defense/records.txt");
        assertFalse(Files.exists(records));
        assertTrue(harness.debugJump("title"));
        harness.press(GLFW_KEY_ENTER);
        harness.close();
        harness = null;
        assertEquals("0,0,0", Files.readString(records));
    }

    private Object scene(String method) throws Exception {
        return harness.scene().getClass().getMethod(method).invoke(harness.scene());
    }

    private Object battle(String method) throws Exception {
        Object b = scene("battlefield");
        return b.getClass().getMethod(method).invoke(b);
    }

    private void click(int x, int y) {
        harness.input().handleMouseMove(x, y);
        harness.input().handleMouseButton(GLFW_MOUSE_BUTTON_LEFT, GLFW_PRESS);
        harness.tick();
        harness.input().handleMouseButton(GLFW_MOUSE_BUTTON_LEFT, GLFW_RELEASE);
        harness.tick();
        play(1);
    }

    private void play(int ticks) {
        for (int i = 0; i < ticks; i++) {
            harness.tick();
            harness.host().draw(null, null);
        }
        assertTrue(SceneHostTestAccess.lastFrameOps(harness.host()) > 0);
    }
}
