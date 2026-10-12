package com.openggf.mods.code;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.openggf.game.GameModule;
import com.openggf.game.GameServices;
import com.openggf.mods.scene.host.ModSceneHost;
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
 * Smoke test for Starpost Valley against a real S3K session with Sonic 1 supplied: it plays each
 * farmer on the belt farm, then visits a
 * neighbour's talk and heart event, the farm pond Bubble Bar, festival scenes, the orchard's
 * trees and a ring burst, the museum and Hazel's scene, the board,
 * a night's tally and the year's end through the scene's debug jumps, playing and drawing frames
 * (recording only, no GL). The engine's fault boundary must catch nothing and the scene must
 * never leave. Skipped when Sonic 1 is not supplied (the scene then only explains what is missing).
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestStarpostValleyScene {
    private static final Path PROJECT = Path.of("examples/starpost-valley");
    // Native competitions suspend this scene host and are covered by TestStarpostRealFestivals.
    private static final String[] FESTIVALS = {"sunflower_parade", "flickies",
        "valley_fair", "scrap_brain_night", "ice_cap", "star_light_feast"};

    @TempDir
    Path work;

    private SharedLevel level;

    @AfterEach
    void dispose() {
        if (level != null) {
            level.dispose();
        }
    }

    @Test
    void wateringCanWatersTheFacingTileInsteadOfHarvestingUnderfoot() throws Exception {
        level = SharedLevel.load(SonicGame.SONIC_3K, 0, 0);
        try (ExampleModHarness harness = ExampleModHarness.build(PROJECT, work.resolve("build"))) {
            harness.open(harness.apply(GameServices.module()), work.resolve("saves"), 400, 224);
            assumeTrue(harness.debugJump("new sonic"), "Sonic 1 was not supplied");
            for (String farmer : new String[] {"sonic", "tails", "knuckles"}) {
                for (boolean left : new boolean[] {false, true}) {
                    for (int withinTile : new int[] {1, 8, 15}) {
                        Object game = prepareFarm(harness, farmer, 208 + withinTile, left);
                        Object farm = member(game, "farm");
                        Object underfoot = plot(farm, 1, 2);
                        Object ahead = plot(farm, 1, left ? 1 : 3);
                        for (Object crop : new Object[] {underfoot, ahead}) {
                            crop.getClass().getField("crop").set(crop, "ring_radish");
                            crop.getClass().getField("age").setInt(crop, 99);
                        }
                        harness.press(org.lwjgl.glfw.GLFW.GLFW_KEY_X);
                        assertTrue((boolean) member(ahead, "watered"), farmer + " facing " + (left ? "left" : "right"));
                        assertFalse((boolean) member(underfoot, "watered"), "the tile underfoot stays dry");
                        assertEquals("ring_radish", member(underfoot, "crop"), "no harvest underfoot");
                        assertEquals("ring_radish", member(ahead, "crop"), "the can waters even a ripe crop");
                        assertEquals(9, member(game, "waterCharges"), "one charge per pour");
                        Object further = plot(farm, 1, left ? 0 : 4);
                        assertEquals(farmer.equals("tails"), member(further, "watered"), "Tails fans forward from the target");
                        play(harness, 32);
                        assertEquals(Map.of(), harness.findings());
                    }
                }
            }
        }
    }

    @Test
    void wateringAtFieldEdgesOrInvalidTargetsNeverFallsBackUnderfoot() throws Exception {
        level = SharedLevel.load(SonicGame.SONIC_3K, 0, 0);
        try (ExampleModHarness harness = ExampleModHarness.build(PROJECT, work.resolve("build"))) {
            harness.open(harness.apply(GameServices.module()), work.resolve("saves"), 400, 224);
            assumeTrue(harness.debugJump("new sonic"), "Sonic 1 was not supplied");
            Object game = prepareFarm(harness, "sonic", 168, false);
            Object farm = member(game, "farm");
            harness.press(org.lwjgl.glfw.GLFW.GLFW_KEY_X);
            assertTrue((boolean) member(plot(farm, 1, 0), "watered"), "first tile is reachable from beside the field");
            assertEquals(9, member(game, "waterCharges"));

            for (int x : new int[] {184, 552, 1128}) {
                game = prepareFarm(harness, "sonic", x, x == 184);
                farm = member(game, "farm");
                int column = (x - 176) / 16;
                Object standing = farm.getClass().getMethod("raw", int.class, int.class).invoke(farm, 1, column);
                standing.getClass().getField("tilled").setBoolean(standing, true);
                standing.getClass().getField("cover").setInt(standing, 0); // GRASS: a fallback really could water it.
                harness.press(org.lwjgl.glfw.GLFW.GLFW_KEY_X);
                assertEquals(10, member(game, "waterCharges"), "outside/locked targets spend nothing");
                assertFalse((boolean) member(standing, "watered"), "no underfoot fallback at " + x);
            }
            for (String refusal : new String[] {"untilled", "wet", "empty", "object"}) {
                game = prepareFarm(harness, "sonic", 216, false);
                farm = member(game, "farm");
                Object target = plot(farm, 1, 3);
                if (refusal.equals("untilled")) target.getClass().getField("tilled").setBoolean(target, false);
                if (refusal.equals("wet")) target.getClass().getField("watered").setBoolean(target, true);
                if (refusal.equals("empty")) game.getClass().getField("waterCharges").setInt(game, 0);
                if (refusal.equals("object")) target.getClass().getField("object").set(target, "item_monitor");
                harness.press(org.lwjgl.glfw.GLFW.GLFW_KEY_X);
                assertEquals(refusal.equals("empty") ? 0 : 10, member(game, "waterCharges"), refusal);
                assertEquals(refusal.equals("wet"), member(target, "watered"), refusal);
                assertFalse((boolean) member(plot(farm, 1, 2), "watered"), "no underfoot fallback");
                play(harness, 2);
                assertEquals(Map.of(), harness.findings());
            }
        }
    }

    @Test
    void plantingStillUsesTheTileUnderfootAndThePondStillRefillsTheCan() throws Exception {
        level = SharedLevel.load(SonicGame.SONIC_3K, 0, 0);
        try (ExampleModHarness harness = ExampleModHarness.build(PROJECT, work.resolve("build"))) {
            harness.open(harness.apply(GameServices.module()), work.resolve("saves"), 400, 224);
            assumeTrue(harness.debugJump("new sonic"), "Sonic 1 was not supplied");
            Object game = prepareFarm(harness, "sonic", 216, false);
            step(harness, "select 1", 1);
            harness.press(org.lwjgl.glfw.GLFW.GLFW_KEY_X);
            Object farm = member(game, "farm");
            assertEquals("ring_radish", member(plot(farm, 1, 2), "crop"));
            assertNull(member(plot(farm, 1, 3), "crop"));
            step(harness, "select 0", 1);
            game.getClass().getField("waterCharges").setInt(game, 0);
            step(harness, "farm 100 38", 1);
            harness.press(org.lwjgl.glfw.GLFW.GLFW_KEY_X);
            assertEquals(member(game, "waterCapacity"), member(game, "waterCharges"));
            assertEquals(Map.of(), harness.findings());
        }
    }

    private static Object prepareFarm(ExampleModHarness harness, String farmer, int x, boolean left) throws Exception {
        step(harness, "new " + farmer, 1);
        step(harness, "close", 1);
        step(harness, "farm " + x + " 22", 1);
        Object shell = shell(harness);
        Object screen = shell.getClass().getMethod("screen").invoke(shell);
        Object runner = member(screen.getClass().getMethod("farm").invoke(screen), "runner");
        runner.getClass().getField("facingLeft").setBoolean(runner, left);
        Object game = member(shell, "game");
        for (int column = 0; column < 6; column++) {
            Object plot = plot(member(game, "farm"), 1, column);
            plot.getClass().getField("tilled").setBoolean(plot, true);
        }
        return game;
    }

    private static Object member(Object object, String name) throws Exception {
        return object.getClass().getField(name).get(object);
    }

    private static Object plot(Object farm, int row, int column) throws Exception {
        return farm.getClass().getMethod("plot", int.class, int.class).invoke(farm, row, column);
    }

    @Test
    void aYearOfScreensOpensPlaysAndDrawsWithoutFaults() throws Exception {
        level = SharedLevel.load(SonicGame.SONIC_3K, 0, 0);
        try (ExampleModHarness harness = ExampleModHarness.build(PROJECT, work.resolve("build"))) {
            GameModule effective = harness.apply(GameServices.module());
            harness.open(effective, work.resolve("saves"), 400, 224);
            play(harness, 60);
            assumeTrue(harness.debugJump("new sonic"), "Sonic 1 was not supplied");
            play(harness, 30);
            for (String farmer : new String[] {"sonic", "tails", "knuckles"}) {
                step(harness, "new " + farmer, 30);
                Object art = shell(harness).getClass().getField("art").get(shell(harness));
                var dust = (com.openggf.mods.scene.SceneSpriteSet) art.getClass()
                        .getMethod("dust", String.class).invoke(art, farmer);
                for (int index = 0x11; index <= 0x14; index++) {
                    assertTrue(java.util.Arrays.stream(dust.frame(index).image().pixels())
                            .anyMatch(pixel -> (pixel >>> 24) != 0), farmer + " skid puff " + index);
                }
                step(harness, "demo", 1);
                step(harness, "farm 400 30", 90);
                assertEquals("PlayScreen", screen(harness), farmer + " on the farm");
                step(harness, "valley 700", 120);
            }
            assertFalse(harness.debugJump("town scene"));
            assertFalse(harness.debugJump("ruins scene"));
            step(harness, "new sonic", 10);
            step(harness, "people talk tails", 90);
            step(harness, "close", 5);
            step(harness, "people social", 30);
            step(harness, "close", 5);
            step(harness, "people hearts tails 2", 1);
            step(harness, "time 1200", 1);
            step(harness, "people event tails_2", 240);
            step(harness, "new sonic", 10);
            step(harness, "close", 5);
            step(harness, "farm 400 30", 5);
            step(harness, "give fishing_rod 1", 1);
            step(harness, "select 2", 1);
            step(harness, "fish bar bubble_bass", 150);
            for (String festival : FESTIVALS) {
                step(harness, "new sonic", 10);
                step(harness, "festival day " + festival, 60);
                step(harness, "festival start " + festival, 240);
                assertTrue(!screen(harness).equals("PlayScreen"), festival + " is under way: " + screen(harness));
            }
            step(harness, "new sonic", 10);
            step(harness, "orchard sneakers 3", 1);
            step(harness, "orchard plant palm 1 10 14 3", 1);
            step(harness, "orchard plant ring 2 14 28 30", 1);
            step(harness, "orchard plant cherry 3 18 21 4", 1);
            step(harness, "farm 400 30", 30);
            step(harness, "orchard shake 2 14", 120);           // the Ring Fruit Tree's burst flies and is drawn
            step(harness, "museum fill relics 5", 1);
            step(harness, "valley 1283", 30);
            step(harness, "museum open scrap", 60);
            assertEquals("PlayScreen", screen(harness), "the museum opens over the valley");
            step(harness, "close", 5);
            step(harness, "museum missing", 1);
            step(harness, "museum returned", 240);              // Hazel's scene for her Star Post Cap
            step(harness, "new sonic", 10);
            step(harness, "board", 30);
            step(harness, "close", 5);
            step(harness, "sleep", 420);
            step(harness, "yearend", 360);
            assertEquals(List.of(), harness.exits(), "the scene never left");
        }
    }

    /** The simple class name of the scene's current screen ("PlayScreen", "RaceScreen"...). */
    private static String screen(ExampleModHarness harness) throws Exception {
        Object shell = shell(harness);
        return shell.getClass().getMethod("screen").invoke(shell).getClass().getSimpleName();
    }

    private static Object shell(ExampleModHarness harness) throws Exception {
        Object scene = harness.scene();
        java.lang.reflect.Field field = scene.getClass().getDeclaredField("shell");
        field.setAccessible(true);
        return field.get(scene);
    }

    private static void step(ExampleModHarness harness, String command, int frames) {
        assertTrue(harness.debugJump(command), "jumped: " + command);
        play(harness, frames);
        assertEquals(Map.of(), harness.findings(), "faults after " + command);
    }

    /** Ticks and draws (recording the canvas without rendering), as the frame loop would. */
    private static void play(ExampleModHarness harness, int frames) {
        ModSceneHost host = harness.host();
        for (int i = 0; i < frames; i++) {
            harness.tick();
            host.draw(null, null);
        }
        assertTrue(com.openggf.mods.scene.host.SceneHostTestAccess.lastFrameOps(host) > 0, "the scene drew something");
    }
}
