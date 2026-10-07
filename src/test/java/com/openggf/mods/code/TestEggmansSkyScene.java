package com.openggf.mods.code;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_C;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_UP;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_X;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;

import com.openggf.game.GameModule;
import com.openggf.game.GameServices;
import com.openggf.mods.scene.host.ModSceneHost;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Eggman's Sky against a real S3K session: builds and validates the example, then plays it
 * through the scene host (recording draws, no GL): a crash landing, the mining laser, scanner,
 * visor and menus on a surface, a launch into space, docking at the Egg Station, a warp from
 * the galaxy map, and a landing on a planet remixed from every supplied biome. The engine's
 * fault boundary must catch nothing, and the expedition must save and load back unchanged.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestEggmansSkyScene {
    private static final Path PROJECT = Path.of("examples/eggmans-sky");

    @TempDir
    Path work;

    private SharedLevel level;

    @AfterEach
    void dispose() {
        if (level != null) {
            level.dispose();
        }
    }

    private ExampleModHarness open() throws Exception {
        level = SharedLevel.load(SonicGame.SONIC_3K, 0, 0);
        ExampleModHarness harness = ExampleModHarness.build(PROJECT, work.resolve("build"));
        GameModule effective = harness.apply(GameServices.module());
        harness.open(effective, work.resolve("saves"), 400, 224);
        return harness;
    }

    @Test
    void anExpeditionFromCrashLandingToWarpPlaysWithoutFaults() throws Exception {
        try (ExampleModHarness harness = open()) {
            play(harness, 30);
            assertEquals("TitleMode", mode(harness));
            assertTrue(harness.debugJump("new:42"));
            play(harness, 320);
            assertEquals("SurfaceMode", mode(harness), "landed after the descent");
            // Mining laser, flying, the scan pulse and the analysis visor.
            hold(harness, GLFW_KEY_SPACE, 150);
            hold(harness, GLFW_KEY_RIGHT, 60);
            harness.press(GLFW_KEY_X);
            play(harness, 30);
            hold(harness, GLFW_KEY_X, 120);
            // The menu: every tab, then close.
            harness.press(GLFW_KEY_ENTER);
            play(harness, 5);
            assertEquals("MenuMode", mode(harness));
            for (int tab = 0; tab < 7; tab++) {
                harness.press(GLFW_KEY_RIGHT);
                play(harness, 4);
            }
            harness.press(GLFW_KEY_BACKSPACE);
            play(harness, 5);
            assertEquals("SurfaceMode", mode(harness));
            // Fuel up and launch: hold up and boost.
            assertTrue(harness.debugJump("rich"));
            harness.press(org.lwjgl.glfw.GLFW.GLFW_KEY_3);
            play(harness, 2);
            harness.input().handleKeyEvent(GLFW_KEY_UP, GLFW_PRESS);
            harness.input().handleKeyEvent(GLFW_KEY_C, GLFW_PRESS);
            play(harness, 90);
            harness.input().handleKeyEvent(GLFW_KEY_UP, GLFW_RELEASE);
            harness.input().handleKeyEvent(GLFW_KEY_C, GLFW_RELEASE);
            play(harness, 160);
            assertEquals("SpaceMode", mode(harness), "launched to orbit");
            hold(harness, GLFW_KEY_SPACE, 40);
            play(harness, 60);
            // Dock, browse every station tab, leave.
            assertTrue(harness.debugJump("station"));
            play(harness, 30);
            assertEquals("StationMode", mode(harness));
            for (int tab = 0; tab < 5; tab++) {
                harness.press(GLFW_KEY_RIGHT);
                play(harness, 4);
            }
            harness.press(GLFW_KEY_ENTER);
            play(harness, 30);
            assertEquals("SpaceMode", mode(harness), "undocked");
            // The galaxy map, and a warp to the next star along.
            assertTrue(harness.debugJump("galaxy"));
            play(harness, 20);
            assertEquals("GalaxyMode", mode(harness));
            harness.press(GLFW_KEY_RIGHT);
            play(harness, 5);
            harness.press(GLFW_KEY_ENTER);
            play(harness, 260);
            String after = mode(harness);
            assertTrue(after.equals("SpaceMode") || after.equals("GalaxyMode"), "warped or refused: " + after);
            assertEquals(Map.of(), harness.findings(), "no faults");
            assertEquals(List.of(), harness.exits(), "the scene never left");
            // Saved on the way: the expedition file holds the player's state.
            Path save = findSave(work.resolve("saves"));
            assertNotNull(save, "expedition saved");
            String text = Files.readString(save);
            assertTrue(text.contains("galaxySeed=") && text.contains("cargo="), text);
        }
    }

    @Test
    void everySuppliedBiomeRemixesIntoALandablePlanet() throws Exception {
        try (ExampleModHarness harness = open()) {
            play(harness, 10);
            Object game = harness.scene().getClass().getMethod("game").invoke(harness.scene());
            List<?> biomes = (List<?>) game.getClass().getField("usableBiomes").get(game);
            assertTrue(biomes.size() >= 20, "S3K alone has over 20 biomes: " + biomes.size());
            for (Object biome : List.copyOf(biomes)) {
                String key = (String) biome.getClass().getMethod("key").invoke(biome);
                String[] parts = key.split("-");
                assertTrue(harness.debugJump("biome:" + parts[0] + ":" + parts[1] + ":" + parts[2] + ":7"), key);
                play(harness, 200);
                assertEquals("SurfaceMode", mode(harness), "landed on " + key);
                Object planet = game.getClass().getMethod("planet").invoke(game);
                Object terrain = planet.getClass().getField("terrain").get(planet);
                int columns = (int) terrain.getClass().getMethod("columns").invoke(terrain);
                assertTrue(columns >= 16, key + " has " + columns + " columns");
                List<?> placements = (List<?>) planet.getClass().getField("placements").get(planet);
                assertFalse(placements.isEmpty(), key + " has deposits and points of interest");
                assertEquals(Map.of(), harness.findings(), "faults after " + key);
                System.out.println("biome " + key + ": " + columns + " columns, " + placements.size() + " placements");
            }
        }
    }

    @Test
    void anExpeditionSavesAndLoadsBackUnchanged() throws Exception {
        try (ExampleModHarness harness = open()) {
            play(harness, 5);
            ClassLoader loader = harness.loader();
            Class<?> catalogClass = loader.loadClass("eggsky.game.Catalog");
            Class<?> playerClass = loader.loadClass("eggsky.game.Player");
            Object catalog = catalogClass.getConstructor().newInstance();
            Object player = playerClass.getConstructor(catalogClass).newInstance(catalog);
            playerClass.getField("rings").setLong(player, 123456L);
            playerClass.getField("emeralds").setInt(player, 0b1011);
            Object cargo = playerClass.getField("cargo").get(player);
            cargo.getClass().getMethod("add", int.class, int.class).invoke(cargo, 3, 400);
            @SuppressWarnings("unchecked")
            java.util.Set<String> discovered = (java.util.Set<String>) playerClass.getField("discovered").get(player);
            discovered.add("12:0:F1");
            String text = (String) playerClass.getMethod("encode").invoke(player);
            Object loaded = playerClass.getConstructor(catalogClass).newInstance(catalog);
            playerClass.getMethod("decode", String.class).invoke(loaded, text);
            assertEquals(text, playerClass.getMethod("encode").invoke(loaded), "round trip");
            Object loadedCargo = playerClass.getField("cargo").get(loaded);
            assertEquals(400, loadedCargo.getClass().getMethod("count", int.class).invoke(loadedCargo, 3));
        }
    }

    private static Path findSave(Path root) throws Exception {
        if (!Files.exists(root)) {
            return null;
        }
        try (var files = Files.walk(root)) {
            return files.filter(p -> p.getFileName().toString().equals("expedition.txt")).findFirst().orElse(null);
        }
    }

    private static String mode(ExampleModHarness harness) throws Exception {
        Object scene = harness.scene();
        Object game = scene.getClass().getMethod("game").invoke(scene);
        Object mode = game.getClass().getMethod("mode").invoke(game);
        return mode.getClass().getSimpleName();
    }

    private static void hold(ExampleModHarness harness, int key, int frames) {
        harness.input().handleKeyEvent(key, GLFW_PRESS);
        play(harness, frames);
        harness.input().handleKeyEvent(key, GLFW_RELEASE);
        play(harness, 2);
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
