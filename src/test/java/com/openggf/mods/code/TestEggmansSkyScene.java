package com.openggf.mods.code;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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

    private ExampleModHarness open() throws Exception { return open(work); }

    private ExampleModHarness open(Path scenario) throws Exception {
        if (level != null) level.dispose();
        level = SharedLevel.load(SonicGame.SONIC_3K, 0, 0);
        ExampleModHarness harness = ExampleModHarness.build(PROJECT, scenario.resolve("build"));
        GameModule effective = harness.apply(GameServices.module());
        harness.open(effective, scenario.resolve("saves"), 400, 224);
        return harness;
    }

    @Test
    void rejectedStoredSaveSurvivesTitleReturnAndOwnedSceneExit() throws Exception {
        List<java.util.function.UnaryOperator<String>> rejected = List.of(text -> "arbitrary prose",
                text -> text.replace("version=1", "version=999"),
                text -> text.replace("bonusSlots=0", "bonusSlots=1000000"),
                text -> text.replaceAll("(?m)^system=.*$", "system=0").replace("planet=0", "planet=4"),
                text -> "x".repeat(1048577));
        for (int index=0; index<rejected.size(); index++) {
            Path scenario=work.resolve("rejected-"+index);
            try (ExampleModHarness harness = open(scenario)) {
                assertTrue(harness.debugJump("new:42"));play(harness,320);
                Object game = harness.scene().getClass().getMethod("game").invoke(harness.scene());
                Object active = game.getClass().getField("player").get(game);
                String text = rejected.get(index).apply((String)active.getClass().getMethod("encode").invoke(active));
                Object activePlanet = game.getClass().getMethod("planet").invoke(game);
                Path save = findSave(scenario.resolve("saves"));Files.writeString(save,text);
                assertTrue(harness.debugJump("title"));play(harness,2);
                Object title=game.getClass().getMethod("mode").invoke(game);
                var options=title.getClass().getDeclaredField("options");options.setAccessible(true);
                assertFalse(((List<?>)options.get(title)).contains("CONTINUE"),"Continue requires a usable save");
                String before=(String)active.getClass().getMethod("encode").invoke(active);
                play(harness,12);
                assertSame(active,game.getClass().getField("player").get(game));
                assertSame(activePlanet,game.getClass().getMethod("planet").invoke(game));
                assertEquals(before,active.getClass().getMethod("encode").invoke(active));
                harness.host().close();
                assertNull(harness.scene(),"the host revoked this scene visit");
                assertEquals(text,Files.readString(save),"owned exit cannot overwrite rejected bytes");
                assertEquals(Map.of(),harness.findings());
            }
        }
    }

    @Test
    void galaxyChangesReplaceSameCoordinatePlanetAndBoundDerivedImages() throws Exception {
        try (ExampleModHarness harness = open()) {
            Object game = harness.scene().getClass().getMethod("game").invoke(harness.scene());
            Class<?> type = game.getClass();
            type.getMethod("newExpedition", long.class, int.class, harness.loader().loadClass("eggsky.game.Player"))
                    .invoke(game, 42L, 1, null);
            Object player = type.getField("player").get(game);
            player.getClass().getField("systemId").setLong(player, 0L);
            Object first = type.getMethod("buildPlanet", int.class).invoke(game, 0);
            Object originalSpec = first.getClass().getField("spec").get(first);
            Object orbit = orbitalTexture(harness, game);
            type.getMethod("newExpedition", long.class, int.class, player.getClass()).invoke(game, 73L, 2, null);
            player = type.getField("player").get(game);
            player.getClass().getField("systemId").setLong(player, 0L);
            Object second = type.getMethod("buildPlanet", int.class).invoke(game, 0);
            assertNotSame(first, second, "same coordinate in another galaxy gets another planet");
            Object spec = second.getClass().getField("spec").get(second);
            assertNotEquals(originalSpec.getClass().getField("seed").getLong(originalSpec), spec.getClass().getField("seed").getLong(spec));
            Object secondOrbit = orbitalTexture(harness, game);
            assertNotSame(orbit, secondOrbit);
            Object fresh = type.getConstructor().newInstance();
            var ctx = (com.openggf.mods.scene.SceneContext)type.getField("ctx").get(game);
            type.getMethod("start",com.openggf.mods.scene.SceneContext.class).invoke(fresh,ctx);
            type.getMethod("newExpedition",long.class,int.class,player.getClass()).invoke(fresh,73L,2,null);
            Object freshPlayer = type.getField("player").get(fresh);
            freshPlayer.getClass().getField("systemId").setLong(freshPlayer,0L);
            Object expected = type.getMethod("buildPlanet",int.class).invoke(fresh,0);
            assertArrayEquals(((com.openggf.mods.scene.SceneImage)expected.getClass().getField("backdrop").get(expected)).pixels(),
                    ((com.openggf.mods.scene.SceneImage)second.getClass().getField("backdrop").get(second)).pixels());
            Object expectedOrbit = orbitalTexture(harness,fresh);
            assertArrayEquals((int[])expectedOrbit.getClass().getField("surface").get(expectedOrbit),
                    (int[])secondOrbit.getClass().getField("surface").get(secondOrbit));
            Object expectedMineral = ((List<?>)expected.getClass().getField("minerals").get(expected)).getFirst();
            Object actualMineral = ((List<?>)second.getClass().getField("minerals").get(second)).getFirst();
            var expectedSprites = (com.openggf.mods.scene.SceneSprite[])expectedMineral.getClass().getField("sprites").get(expectedMineral);
            var actualSprites = (com.openggf.mods.scene.SceneSprite[])actualMineral.getClass().getField("sprites").get(actualMineral);
            for(int i=0;i<expectedSprites.length;i++) assertArrayEquals(expectedSprites[i].image().pixels(),actualSprites[i].image().pixels());
            type.getMethod("land",int.class,boolean.class).invoke(game,0,false);
            play(harness,6);
            Object art = type.getField("art").get(game);
            Class<?> recolor = harness.loader().loadClass("eggsky.core.Recolor");
            Object scheme = recolor.getConstructor(float.class,float.class,float.class,int.class,float.class).newInstance(60f,1f,1f,0,0f);
            var sprite = new com.openggf.mods.scene.SceneSprite(new com.openggf.mods.scene.SceneImage(1,1,new int[]{0xFFFF0000}),0,0);
            var recolored = art.getClass().getMethod("recolored", com.openggf.mods.scene.SceneSprite.class,recolor,String.class);
            var active = (com.openggf.mods.scene.SceneSprite) recolored.invoke(art,sprite,scheme,"active");
            int[] pixels = active.image().pixels();
            for(int i=0;i<600;i++) recolored.invoke(art,sprite,scheme,"explored:"+i);
            var tinted = art.getClass().getDeclaredField("tinted");tinted.setAccessible(true);
            assertTrue(((Map<?,?>) tinted.get(art)).size()<=256,"recolour references have a fixed bound");
            assertArrayEquals(pixels,active.image().pixels(),"evicting a lookup keeps displayed images valid");
            harness.host().draw(null,null);
            assertFalse(harness.host().recordedFrame().isEmpty(),"active planet still draws after lookup eviction");
            // Continue publishes a validated generation and recreates its derived art.
            assertEquals(true,type.getMethod("save").invoke(game));
            assertEquals(true,type.getMethod("load").invoke(game));
            Object continued = type.getMethod("buildPlanet",int.class).invoke(game,0);
            assertNotSame(second,continued);
            assertArrayEquals(((com.openggf.mods.scene.SceneImage)second.getClass().getField("backdrop").get(second)).pixels(),
                    ((com.openggf.mods.scene.SceneImage)continued.getClass().getField("backdrop").get(continued)).pixels());
            assertEquals(Map.of(),harness.findings());
        }
    }

    private Object orbitalTexture(ExampleModHarness harness,Object game) throws Exception {
        Class<?> type=harness.loader().loadClass("eggsky.space.SpaceMode");
        Object mode=type.getConstructor(int.class,int.class).newInstance(0,0);
        type.getMethod("enter",game.getClass()).invoke(mode,game);
        var bodies=type.getDeclaredField("bodies");bodies.setAccessible(true);
        Object body=((List<?>)bodies.get(mode)).getFirst();
        var build=type.getDeclaredMethod("buildTexture",game.getClass(),body.getClass());build.setAccessible(true);build.invoke(mode,game,body);
        return body.getClass().getField("texture").get(body);
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
    void enterOpensTheGalaxyMapAndConfirmsExactlyOneWarp() throws Exception {
        try (ExampleModHarness harness = open()) {
            assertTrue(harness.debugJump("space:0"));
            play(harness, 200);
            Object game = harness.scene().getClass().getMethod("game").invoke(harness.scene());
            Object player = game.getClass().getField("player").get(game);
            Class<?> catalog = harness.loader().loadClass("eggsky.game.Catalog");
            int[] tech = (int[]) player.getClass().getField("tech").get(player);
            for (String drive : List.of("T_HYPERDRIVE", "T_RED_DRIVE", "T_GREEN_DRIVE", "T_BLUE_DRIVE")) {
                tech[catalog.getField(drive).getInt(null)] = drive.equals("T_HYPERDRIVE") ? 5 : 1;
            }
            Object cargo = player.getClass().getField("cargo").get(player);
            int cell = catalog.getField("WARP_CELL").getInt(null);
            int cells = (int) cargo.getClass().getMethod("count", int.class).invoke(cargo, cell);
            cargo.getClass().getMethod("remove", int.class, int.class).invoke(cargo, cell, cells);

            harness.press(GLFW_KEY_ENTER);
            play(harness, 4);
            assertEquals("MenuMode", mode(harness));
            harness.press(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT); // Cargo -> System.
            harness.press(org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN); // Galaxy map.
            harness.press(GLFW_KEY_ENTER);
            play(harness, 4);
            assertEquals("GalaxyMode", mode(harness), "Enter selects the menu item instead of closing");
            harness.press(GLFW_KEY_ENTER);
            assertEquals("GalaxyMode", mode(harness), "confirming the current system stays on the map");
            harness.press(GLFW_KEY_RIGHT);
            play(harness, 4);
            Object map = game.getClass().getMethod("mode").invoke(game);
            var selected = map.getClass().getDeclaredField("selected");
            selected.setAccessible(true);
            Object destination = selected.get(map);
            long destinationId = destination.getClass().getField("id").getLong(destination);
            long origin = player.getClass().getField("systemId").getLong(player);
            assertTrue(destinationId != origin, "selected a different system");
            harness.press(GLFW_KEY_ENTER);
            assertEquals("GalaxyMode", mode(harness), "no fuel refuses the warp without closing the map");
            cargo.getClass().getMethod("add", int.class, int.class).invoke(cargo, cell, 2);
            int warps = player.getClass().getField("statWarps").getInt(player);
            harness.press(org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER);
            assertEquals("WarpMode", mode(harness), "keypad Enter starts the warp");
            assertEquals(1, cargo.getClass().getMethod("count", int.class).invoke(cargo, cell));
            play(harness, 220);
            assertEquals("SpaceMode", mode(harness));
            assertEquals(destinationId, player.getClass().getField("systemId").getLong(player));
            assertEquals(warps + 1, player.getClass().getField("statWarps").getInt(player));
            // Dedicated menu/back controls still dismiss menus and the map.
            for (int close : new int[] {org.lwjgl.glfw.GLFW.GLFW_KEY_TAB,
                    org.lwjgl.glfw.GLFW.GLFW_KEY_I, GLFW_KEY_BACKSPACE, GLFW_KEY_X}) {
                harness.press(GLFW_KEY_ENTER);
                play(harness, 4);
                assertEquals("MenuMode", mode(harness));
                harness.press(close);
                assertEquals("SpaceMode", mode(harness));
                assertTrue(harness.debugJump("galaxy"));
                play(harness, 4);
                harness.press(close);
                assertEquals("SpaceMode", mode(harness));
            }
            assertEquals(Map.of(), harness.findings());
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
                var kit = (com.openggf.mods.scene.SceneLevelKit) planet.getClass().getField("kit").get(planet);
                List<?> bands = (List<?>) planet.getClass().getField("bands").get(planet);
                boolean plain = (boolean) biome.getClass().getMethod("plainSky").invoke(biome);
                if (plain) {
                    assertEquals(1, bands.size(), key + " keeps its generated skyline intact");
                } else {
                    assertEquals(kit.backdrop().bands(), bands, key + " preserves source band boundaries and rates");
                }
                for (Object species : (List<?>) planet.getClass().getField("flora").get(planet)) {
                    var sprites = (com.openggf.mods.scene.SceneSprite[]) species.getClass().getField("sprites").get(species);
                    assertEquals(6, sprites.length, key + " has six specimens per species");
                }
                assertEquals(Map.of(), harness.findings(), "faults after " + key);
                System.out.println("biome " + key + ": " + columns + " columns, " + placements.size() + " placements");
            }
        }
    }

    @Test
    void productionMenusRememberSelectionAndJournalDoesNotChangeExpedition() throws Exception {
        try (ExampleModHarness harness = open()) {
            assertTrue(harness.debugJump("new:42"));
            play(harness, 240);
            Object game = harness.scene().getClass().getMethod("game").invoke(harness.scene());
            Object player = game.getClass().getField("player").get(game);
            Class<?> pt = player.getClass();
            pt.getField("tutorial").setInt(player, 99);
            pt.getField("menuOpened").setBoolean(player, true);
            pt.getField("menuTab").setInt(player, 2);
            harness.press(GLFW_KEY_ENTER);
            play(harness, 5);
            harness.press(GLFW_KEY_ENTER);
            play(harness, 5);
            assertEquals("BatchMode", mode(harness));
            for (int i = 0; i < 3; i++) {
                harness.press(org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN);
                play(harness, 3);
            }
            harness.press(GLFW_KEY_ENTER);
            play(harness, 5);
            assertEquals("MenuMode", mode(harness));
            assertTrue(pt.getField("pinned").getInt(player) > 0, "recipe pinned through actual input");
            harness.press(GLFW_KEY_BACKSPACE);
            play(harness, 5);
            harness.press(GLFW_KEY_ENTER);
            play(harness, 5);
            Object menu = game.getClass().getMethod("mode").invoke(game);
            var tab = menu.getClass().getDeclaredField("tab");
            tab.setAccessible(true);
            assertEquals(2, tab.getInt(menu), "reopening restores craft tab");
            for (int i = 0; i < 3; i++) {
                harness.press(GLFW_KEY_RIGHT);
                play(harness, 4);
            }
            // Record all species as already discovered to exercise each category's portraits.
            Object planet = game.getClass().getMethod("planet").invoke(game);
            @SuppressWarnings("unchecked")
            var discovered = (java.util.Set<String>) pt.getField("discovered").get(player);
            for (Object species : (List<?>) planet.getClass().getMethod("allSpecies").invoke(planet)) {
                discovered.add((String) species.getClass().getField("id").get(species));
            }
            String before = (String) pt.getMethod("encode").invoke(player);
            harness.press(GLFW_KEY_ENTER);
            play(harness, 5);
            assertEquals("JournalMode", mode(harness));
            harness.press(GLFW_KEY_ENTER);
            play(harness, 5);
            for (int i = 0; i < 3; i++) {
                harness.press(GLFW_KEY_RIGHT);
                play(harness, 5);
            }
            assertEquals(before, pt.getMethod("encode").invoke(player), "journal cannot move or reward player");
            harness.press(GLFW_KEY_BACKSPACE);
            play(harness, 5);
            assertEquals("JournalMode", mode(harness), "back first returns to planets");
            harness.press(GLFW_KEY_BACKSPACE);
            play(harness, 5);
            assertEquals("MenuMode", mode(harness));
            assertEquals(Map.of(), harness.findings());
        }
    }

    @Test
    void stationSalesRespectReservesAndRememberTradingSelection() throws Exception {
        try (ExampleModHarness harness = open()) {
            assertTrue(harness.debugJump("new:42"));
            play(harness, 240);
            assertTrue(harness.debugJump("rich"));
            assertTrue(harness.debugJump("station"));
            play(harness, 10);
            Object game = harness.scene().getClass().getMethod("game").invoke(harness.scene());
            Object player = game.getClass().getField("player").get(game);
            Object cargo = player.getClass().getField("cargo").get(player);
            int carbon = harness.loader().loadClass("eggsky.game.Catalog").getField("CARBON").getInt(null);
            @SuppressWarnings("unchecked")
            Map<Integer, Integer> reserves = (Map<Integer, Integer>) player.getClass().getField("reserves").get(player);
            reserves.put(carbon, 10);
            harness.press(GLFW_KEY_ENTER);
            play(harness, 5);
            assertEquals(10, cargo.getClass().getMethod("count", int.class).invoke(cargo, carbon));
            harness.press(GLFW_KEY_RIGHT);
            play(harness, 5);
            harness.press(org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN);
            play(harness, 5);
            harness.press(GLFW_KEY_BACKSPACE);
            play(harness, 5);
            harness.press(GLFW_KEY_ENTER);
            play(harness, 5);
            assertEquals("SpaceMode", mode(harness));
            assertTrue(harness.debugJump("station"));
            play(harness, 10);
            Object station = game.getClass().getMethod("mode").invoke(game);
            var tab = station.getClass().getDeclaredField("tab"); tab.setAccessible(true);
            var row = station.getClass().getDeclaredField("row"); row.setAccessible(true);
            assertEquals(1, tab.getInt(station));
            assertEquals(1, row.getInt(station));
            assertEquals(Map.of(), harness.findings());
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

    @Test
    void blueCrystalsProvideLaunchFuelNearFreshLandingSites() throws Exception {
        try (ExampleModHarness harness = open()) {
            for (long seed : new long[] {7, 42, 123, 2026}) {
                assertTrue(harness.debugJump("new:" + seed));
                play(harness, 320);
                Object game = harness.scene().getClass().getMethod("game").invoke(harness.scene());
                Object surface = game.getClass().getMethod("mode").invoke(game);
                Object ship = surface.getClass().getField("ship").get(surface);
                float startX = ship.getClass().getField("x").getFloat(ship);
                Object terrain = surface.getClass().getField("terrain").get(surface);
                int hydrogen = harness.loader().loadClass("eggsky.game.Catalog").getField("DIHYDROGEN").getInt(null);
                List<?> things = (List<?>) surface.getClass().getField("things").get(surface);
                int nearbyYield = 0;
                for (Object thing : things) {
                    Class<?> type = thing.getClass();
                    if (type.getField("yield").getInt(thing) != hydrogen) continue;
                    float x = type.getField("x").getFloat(thing);
                    float dx = (float) terrain.getClass().getMethod("dx", float.class, float.class)
                            .invoke(terrain, startX, x);
                    if (Math.abs(dx) > 768) continue;
                    assertTrue((boolean) type.getMethod("mineable").invoke(thing));
                    var sprite = (com.openggf.mods.scene.SceneSprite) type.getField("sprite").get(thing);
                    boolean visible = false;
                    for (int y = 0; y < sprite.height(); y++) {
                        for (int px = 0; px < sprite.width(); px++) {
                            visible |= (sprite.image().pixel(px, y) >>> 24) != 0;
                        }
                    }
                    assertTrue(visible, "crystal art is visible");
                    nearbyYield += type.getField("yieldCount").getInt(thing);
                }
                assertTrue(nearbyYield >= 40, "seed " + seed + " has only " + nearbyYield + " nearby hydrogen");
            }
        }
    }

    @Test
    void eggmanPodAndExhaustFaceTheDirectionOfTravel() throws Exception {
        try (ExampleModHarness harness = open()) {
            Object game = harness.scene().getClass().getMethod("game").invoke(harness.scene());
            Class<?> shipType = harness.loader().loadClass("eggsky.surface.Ship");
            Object ship = shipType.getConstructor().newInstance();
            var canvas = org.mockito.Mockito.mock(com.openggf.mods.scene.SceneCanvas.class);
            for (int facing : new int[] {-1, 1}) {
                org.mockito.Mockito.clearInvocations(canvas);
                shipType.getField("facing").setInt(ship, facing);
                shipType.getMethod("draw", game.getClass(), com.openggf.mods.scene.SceneCanvas.class,
                        float.class, float.class, boolean.class, int.class)
                        .invoke(ship, game, canvas, 200f, 100f, true, -1);
                var styles = org.mockito.ArgumentCaptor.forClass(com.openggf.mods.scene.SceneDraw.class);
                org.mockito.Mockito.verify(canvas, org.mockito.Mockito.times(3)).draw(
                        org.mockito.ArgumentMatchers.any(com.openggf.mods.scene.SceneSprite.class),
                        org.mockito.ArgumentMatchers.anyFloat(), org.mockito.ArgumentMatchers.anyFloat(), styles.capture());
                for (var style : styles.getAllValues()) assertEquals(facing > 0, style.flipX());
            }
            // These screens draw ROM frames directly rather than using Ship.draw.
            assertTrue(harness.debugJump("new:42"));
            play(harness, 320);
            Object art = game.getClass().getField("art").get(game);
            Object body = art.getClass().getMethod("frame", String.class, int.class).invoke(art, "ship", 5);
            for (String name : new String[] {"eggsky.ui.TitleMode", "eggsky.ui.IntroMode",
                    "eggsky.ui.EndingMode", "eggsky.station.StationMode"}) {
                Class<?> type = harness.loader().loadClass(name);
                Object screen = type.getConstructor().newInstance();
                type.getMethod("enter", game.getClass()).invoke(screen, game);
                var age = type.getDeclaredField("age");
                age.setAccessible(true);
                age.setInt(screen, 400);
                org.mockito.Mockito.clearInvocations(canvas);
                type.getMethod("draw", game.getClass(), com.openggf.mods.scene.SceneCanvas.class)
                        .invoke(screen, game, canvas);
                org.mockito.Mockito.verify(canvas).draw(
                        org.mockito.ArgumentMatchers.same((com.openggf.mods.scene.SceneSprite) body),
                        org.mockito.ArgumentMatchers.anyFloat(), org.mockito.ArgumentMatchers.anyFloat(),
                        org.mockito.ArgumentMatchers.argThat(style -> style.flipX()));
            }
        }
    }

    @Test
    void skyScrollStaysContinuousAcrossBothPlanetSeams() throws Exception {
        try (ExampleModHarness harness = open()) {
            assertTrue(harness.debugJump("biome:s3k:2:0:7"));
            play(harness, 200);
            Object game = harness.scene().getClass().getMethod("game").invoke(harness.scene());
            Object surface = game.getClass().getMethod("mode").invoke(game);
            Class<?> type = surface.getClass();
            Object ship = type.getField("ship").get(surface);
            Object terrain = type.getField("terrain").get(surface);
            int width = (int) terrain.getClass().getMethod("width").invoke(terrain);
            var scroll = type.getDeclaredField("skyScrollX");
            scroll.setAccessible(true);
            var update = type.getDeclaredMethod("updateCamera", game.getClass());
            update.setAccessible(true);
            var draw = type.getDeclaredMethod("drawSky", game.getClass(), com.openggf.mods.scene.SceneCanvas.class,
                    int.class, float.class, float.class);
            draw.setAccessible(true);
            var canvas = org.mockito.Mockito.mock(com.openggf.mods.scene.SceneCanvas.class);
            Object planet = game.getClass().getMethod("planet").invoke(game);
            var bg = (com.openggf.mods.scene.SceneImage) planet.getClass().getField("backdrop").get(planet);
            @SuppressWarnings("unchecked")
            var bands = (List<com.openggf.mods.scene.SceneBackdrop.Band>) planet.getClass().getField("bands").get(planet);
            assertEquals(1, bands.size()); // This act has a single connected background plane.
            for (int direction : new int[] {-1, 1}) {
                float start = direction > 0 ? width - 1 : 1;
                type.getField("camX").setFloat(surface, start);
                scroll.setDouble(surface, start);
                ship.getClass().getField("x").setFloat(ship, direction > 0 ? 15 : width - 15);
                ship.getClass().getField("vx").setFloat(ship, 0);
                update.invoke(surface, game);
                float camera = type.getField("camX").getFloat(surface);
                assertTrue(direction > 0 ? camera < 2 : camera > width - 2, "crossed the seam");
                assertEquals(direction * 16 * 0.12, scroll.getDouble(surface) - start, 0.00001);
                org.mockito.Mockito.clearInvocations(canvas);
                draw.invoke(surface, game, canvas, -1, camera, 100f);
                var regions = org.mockito.Mockito.mockingDetails(canvas).getInvocations().stream()
                        .filter(call -> call.getMethod().getName().equals("drawRegion")).toList();
                assertFalse(regions.isEmpty());
                int expected = (int) Math.floorMod((long) Math.floor(scroll.getDouble(surface) * bands.getFirst().speed()),
                        (long) bg.width());
                assertEquals(expected, regions.getFirst().<Integer>getArgument(1).intValue(), "draw uses continuous travel, not wrapped X");
                for (var call : regions) {
                    float y = call.getArgument(6);
                    assertEquals(Math.round(y), y, "pixel-aligned background rows");
                    assertEquals(call.<Integer>getArgument(4).floatValue(), call.<Float>getArgument(8), "no vertical stretch");
                }
            }
        }
    }

    @Test
    void newFloraSilhouettesAreRepeatableAndVaryBySeed() throws Exception {
        try (ExampleModHarness harness = ExampleModHarness.build(PROJECT, work.resolve("flora-build"))) {
            var plant = harness.loader().loadClass("eggsky.art.PixelArt").getMethod("plant",
                    int.class, long.class, int.class, int.class, int.class, float.class);
            java.util.Set<Integer> silhouettes = new java.util.HashSet<>();
            for (int shape = 10; shape <= 15; shape++) {
                java.util.Set<Integer> specimens = new java.util.HashSet<>();
                for (long seed = 0; seed < 12; seed++) {
                    var a = (com.openggf.mods.scene.SceneSprite) plant.invoke(null, shape, seed,
                            0xFF44AA66, 0xFFDD88AA, 0xFF886644, 1f);
                    var b = (com.openggf.mods.scene.SceneSprite) plant.invoke(null, shape, seed,
                            0xFF44AA66, 0xFFDD88AA, 0xFF886644, 1f);
                    assertEquals(a.width(), b.width());
                    assertEquals(a.height(), b.height());
                    int hash = 1, occupied = 0;
                    for (int y = 0; y < a.height(); y++) {
                        for (int x = 0; x < a.width(); x++) {
                            int pixel = a.image().pixel(x, y);
                            assertEquals(pixel, b.image().pixel(x, y));
                            boolean filled = (pixel >>> 24) != 0;
                            if (filled) occupied++;
                            hash = 31 * hash + (filled ? 1 : 0);
                        }
                    }
                    assertTrue(occupied > 30, "nonempty silhouette");
                    assertTrue(occupied < a.width() * a.height() * 0.85, "transparent negative space");
                    assertEquals(a.height() - 1, a.originY(), "rooted at the ground");
                    specimens.add(hash);
                    silhouettes.add(hash);
                }
                assertEquals(12, specimens.size(), "each seed changes the shape, not just the colour");
            }
            assertEquals(72, silhouettes.size(), "six distinct families of silhouettes");
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
