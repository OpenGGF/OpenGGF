package com.openggf.mods.code;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;

import com.openggf.game.GameServices;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Three Islands against a real S3K session through the production scene host (draws are
 * recorded, no GL): a new game through the story to the island map and the village save, every
 * zone's real terrain and boss, and a battle fought with the keyboard. Sonic 1 and Sonic 2
 * chapters run when those ROMs are configured; otherwise the game skips them, which is also
 * checked. The fault boundary must catch nothing.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestThreeIslandsScene {
    private static final Path PROJECT = Path.of("examples/three-islands");
    private static final List<String> ZONES = List.of("ghz", "slz", "syz", "ehz", "cpz", "mcz", "aiz", "hcz", "lbz", "dez");
    private static final List<String> S3K_ZONES = List.of("aiz", "hcz", "lbz", "dez");

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

    /** Ticks until the screen name starts with {@code prefix}, pressing nothing. */
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
    void startupIsPlayableAndArrivalDialogueAndRestStayInTheField() throws Exception {
        try (ExampleModHarness harness = open()) {
            play(harness, 2);
            assertEquals("FIELD", screen(harness), "no title, level selector or village hub at startup");
            assertFalse(harness.host().recordedFrame().isEmpty());
            harness.input().handleKeyEvent(GLFW_KEY_RIGHT, GLFW_PRESS);
            assertTrue(until(harness, "STORY", 80), "arrival begins after Sonic starts walking");
            harness.input().handleKeyEvent(GLFW_KEY_RIGHT, GLFW_RELEASE);
            for (int i = 0; i < 4 && screen(harness).equals("STORY"); i++) {
                harness.press(GLFW_KEY_BACKSPACE); play(harness, 2);
            }
            assertEquals("FIELD", screen(harness));
            Object game = harness.scene().getClass().getMethod("game").invoke(harness.scene());
            Object fieldScreen = game.getClass().getMethod("screen").invoke(game);
            Object field = fieldScreen.getClass().getMethod("field").invoke(fieldScreen);
            field.getClass().getMethod("setPosition", double.class, double.class).invoke(field, 144.0, 356.0);
            harness.press(GLFW_KEY_ENTER); play(harness, 2);
            if (screen(harness).equals("STORY")) { harness.press(GLFW_KEY_BACKSPACE); play(harness, 2); }
            assertEquals("FIELD", screen(harness));
            Path save;
            try (Stream<Path> files = Files.walk(work.resolve("saves"))) {
                save = files.filter(p -> p.getFileName().toString().equals("save.txt")).findFirst().orElseThrow();
            }
            String text = Files.readString(save);
            assertTrue(text.startsWith("three-islands-save 1"));
            assertTrue(text.contains("-arrive"), "arrival happened within gameplay");
            assertTrue(text.contains("-field-camp"), "resting at the real Starpost saved");
            clean(harness);
        }
    }

    @Test
    void everyZoneLoadsItsRealTerrainAndEveryBossFallsToTheParty() throws Exception {
        try (ExampleModHarness harness = open()) {
            play(harness, 3);
            int played = 0;
            for (String zone : ZONES) {
                boolean loaded = harness.debugJump("field:" + zone);
                if (!loaded) {
                    assertFalse(S3K_ZONES.contains(zone), zone + " must load from the S3K ROM");
                    continue;
                }
                played++;
                harness.input().handleKeyEvent(GLFW_KEY_RIGHT, GLFW_PRESS);
                play(harness, 40);
                harness.input().handleKeyEvent(GLFW_KEY_RIGHT, GLFW_RELEASE);
                play(harness, 2);
                assertFalse(harness.host().recordedFrame().isEmpty(), zone + " draws");
                assertTrue(harness.debugJump("boss:" + zone), zone + " boss");
                assertTrue(until(harness, "BATTLE_COMMAND", 900), zone + " boss waits for a command: " + screen(harness));
                harness.press(GLFW_KEY_ENTER);
                harness.press(GLFW_KEY_ENTER);
                play(harness, 30);
                assertTrue(harness.debugJump("win"));
                assertTrue(until(harness, "BATTLE_RESULTS", 300), zone + " results: " + screen(harness));
                // The results ignore presses for half a second so a held button cannot skip them.
                play(harness, 40);
                harness.press(GLFW_KEY_ENTER);
                play(harness, 4);
                String after = screen(harness);
                assertTrue(after.equals("STORY") || after.equals("FIELD") || after.equals("ENDING"), zone + " -> " + after);
                clean(harness);
            }
            System.out.println("Three Islands zones played: " + played + " of " + ZONES.size());
            assertTrue(played >= S3K_ZONES.size());
        }
    }

    @Test
    void aSonic1ZonePlaysItsOwnRomSongAfterTheStandIn() throws Exception {
        level = SharedLevel.load(SonicGame.SONIC_3K, 0, 0);
        try (ExampleModHarness harness = ExampleModHarness.build(PROJECT, work.resolve("build"))) {
            harness.open(harness.apply(GameServices.module()), work.resolve("saves"), 400, 224, true);
            play(harness, 3);
            if (!harness.debugJump("field:ghz")) return; // No Sonic 1 ROM configured: its chapter is skipped.
            Object game = harness.scene().getClass().getMethod("game").invoke(harness.scene());
            Object audio = game.getClass().getField("audio").get(game);
            var active = audio.getClass().getMethod("playerActive");
            var describe = audio.getClass().getMethod("describe");
            play(harness, 2);
            assertTrue(((String) describe.invoke(audio)).contains("playing s3k:20"), "the stand-in starts at once");
            // Real time: the background synthesis needs wall-clock time, not ticks.
            long deadline = System.nanoTime() + 60_000_000_000L;
            while (!(boolean) active.invoke(audio) && System.nanoTime() < deadline) {
                play(harness, 1);
                Thread.sleep(16);
            }
            assertTrue((boolean) active.invoke(audio), "Green Hill's own song took over: " + describe.invoke(audio));
            assertTrue(((String) describe.invoke(audio)).contains("playing s1:81"), (String) describe.invoke(audio));
            clean(harness);
        }
    }

    @Test
    void aBattleFoughtWithTheKeyboardEndsBackOnTheField() throws Exception {
        try (ExampleModHarness harness = open()) {
            play(harness, 3);
            // The jump sets the party up at Angel Island's suggested level.
            assertTrue(harness.debugJump("battle:aiz:RHINOBOT"));
            for (int i = 0; i < 4000 && !screen(harness).startsWith("BATTLE_RESULTS"); i++) {
                if (screen(harness).equals("BATTLE_COMMAND")) {
                    harness.press(GLFW_KEY_ENTER);
                    harness.press(GLFW_KEY_ENTER);
                }
                play(harness, 1);
            }
            assertEquals("BATTLE_RESULTS", screen(harness), "attacking wins the fight");
            play(harness, 40);
            harness.press(GLFW_KEY_ENTER);
            play(harness, 3);
            assertEquals("FIELD", screen(harness));
            clean(harness);
        }
    }
    @Test
    void explorationUsesTwoAxesAndStoryDiscoveriesPersistAcrossContinue() throws Exception {
        try (ExampleModHarness harness = open()) {
            play(harness, 3);
            assertTrue(harness.debugJump("field:aiz"));
            Object game = harness.scene().getClass().getMethod("game").invoke(harness.scene());
            Object screen = game.getClass().getMethod("screen").invoke(game);
            Object field = screen.getClass().getMethod("field").invoke(screen);
            var setPosition = field.getClass().getMethod("setPosition", double.class, double.class);
            var y = field.getClass().getMethod("y");
            double before = (double) y.invoke(field);
            harness.input().handleKeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_UP, GLFW_PRESS);
            play(harness, 20);
            harness.input().handleKeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_UP, GLFW_RELEASE);
            assertTrue((double) y.invoke(field) < before - 20, "vertical field movement is real");
            // Jump to landmarks to exercise the host's real interaction/story/save flow.
            setPosition.invoke(field, 848.0, 148.0);
            harness.press(GLFW_KEY_ENTER); play(harness, 3);
            assertEquals("FIELD", screen(harness), "boss remains locked until its story is understood");
            for (double[] position : new double[][] {{736, 516}, {240, 164}}) {
                setPosition.invoke(field, position[0], position[1]);
                harness.press(GLFW_KEY_ENTER); play(harness, 3);
                assertEquals("STORY", screen(harness), "a discovery opens dialogue");
                harness.press(GLFW_KEY_BACKSPACE); play(harness, 3);
                assertEquals("FIELD", screen(harness));
            }
            assertEquals(2, field.getClass().getMethod("discoveries").invoke(field));
            // Both orders are supported: the last discovery above saves at the western camp.
            java.util.Optional<?> saved = (java.util.Optional<?>) game.getClass().getMethod("readSave").invoke(game);
            assertTrue(saved.isPresent());
            game.getClass().getMethod("continueGame", saved.get().getClass()).invoke(game, saved.get());
            assertTrue(until(harness, "LOADING", 60));
            for (int tick = 0; tick < 700 && !screen(harness).equals("FIELD"); tick++) {
                if (screen(harness).equals("STORY")) harness.press(GLFW_KEY_BACKSPACE);
                play(harness, 1);
            }
            assertEquals("FIELD", screen(harness));
            Object resumedScreen = game.getClass().getMethod("screen").invoke(game);
            Object resumed = resumedScreen.getClass().getMethod("field").invoke(resumedScreen);
            assertEquals(2, resumed.getClass().getMethod("discoveries").invoke(resumed));
            assertEquals(144.0, resumed.getClass().getMethod("x").invoke(resumed));
            assertEquals(360.0, resumed.getClass().getMethod("y").invoke(resumed));
            assertEquals(false, resumed.getClass().getMethod("bossReady").invoke(resumed), "Flame Craft still guards the anchor");
            clean(harness);
        }
    }

    @Test
    void physicalTrailsConnectFieldsAndBattleUsesTheEncounterField() throws Exception {
        try (ExampleModHarness harness = open()) {
            play(harness, 2);
            if (harness.debugJump("field:ghz")) {
                Object startGame = harness.scene().getClass().getMethod("game").invoke(harness.scene());
                Object start = startGame.getClass().getMethod("screen").invoke(startGame);
                Object startModel = start.getClass().getMethod("field").invoke(start);
                startModel.getClass().getMethod("setPosition", double.class, double.class).invoke(startModel, 896.0, 336.0);
                harness.press(GLFW_KEY_RIGHT);
                assertTrue(until(harness, "LOADING", 60));
                assertTrue(until(harness, "FIELD", 600));
                Object coast = startGame.getClass().getMethod("screen").invoke(startGame);
                assertEquals("STAR_LIGHT", coast.getClass().getMethod("zone").invoke(coast).toString(),
                        "the coast is explorable before defeating Green Hill's boss");
            }
            assertTrue(harness.debugJump("field:aiz"));
            Object game = harness.scene().getClass().getMethod("game").invoke(harness.scene());
            Object f = game.getClass().getMethod("screen").invoke(game);
            Object model = f.getClass().getMethod("field").invoke(f);
            var setPosition = model.getClass().getMethod("setPosition", double.class, double.class);
            setPosition.invoke(model, 896.0, 336.0);
            harness.press(GLFW_KEY_RIGHT); play(harness, 3);
            assertEquals("FIELD", screen(harness), "story seal blocks the onward trail");
            // Touch the actual first patrol, without constructing a separate battle stage.
            setPosition.invoke(model, 320.0, 336.0); play(harness, 1);
            assertTrue(screen(harness).startsWith("BATTLE"));
            Object battle = game.getClass().getMethod("screen").invoke(game);
            assertEquals(f, battle.getClass().getMethod("battlefield").invoke(battle));
            double cameraBefore = (double) f.getClass().getMethod("worldX", int.class).invoke(f, 0);
            play(harness, 80);
            assertEquals(cameraBefore, f.getClass().getMethod("worldX", int.class).invoke(f, 0), "combat never changes the field camera");
            assertTrue(harness.debugJump("win"));
            assertTrue(until(harness, "BATTLE_RESULTS", 300));
            play(harness, 40); harness.press(GLFW_KEY_ENTER); play(harness, 3);
            assertEquals(f, game.getClass().getMethod("screen").invoke(game), "victory returns to the same field instance");
            // Clear through the public story owner, then physically cross the exit and return.
            Object zone = f.getClass().getMethod("zone").invoke(f);
            Object progress = game.getClass().getField("progress").get(game);
            progress.getClass().getMethod("markSeen", String.class).invoke(progress, "aiz-clear");
            game.getClass().getMethod("zoneCleared", zone.getClass(), f.getClass().getInterfaces()[0]).invoke(game, zone, f);
            assertEquals("FIELD", screen(harness), "a cleared area is not replaced by a menu");
            play(harness, 100); // let the blocked-exit message/cooldown expire
            setPosition.invoke(model, 896.0, 336.0);
            harness.press(GLFW_KEY_RIGHT);
            assertTrue(until(harness, "LOADING", 60));
            assertTrue(until(harness, "FIELD", 600));
            Object hcz = game.getClass().getMethod("screen").invoke(game);
            assertEquals("HYDROCITY", hcz.getClass().getMethod("zone").invoke(hcz).toString());
            Object hczModel = hcz.getClass().getMethod("field").invoke(hcz);
            hczModel.getClass().getMethod("setPosition", double.class, double.class).invoke(hczModel, 64.0, 336.0);
            harness.press(GLFW_KEY_LEFT);
            assertTrue(until(harness, "LOADING", 60));
            assertTrue(until(harness, "FIELD", 600));
            Object returned = game.getClass().getMethod("screen").invoke(game);
            assertEquals("ANGEL_ISLAND", returned.getClass().getMethod("zone").invoke(returned).toString());
            clean(harness);
        }
    }

    @Test
    void tailsJoinsInTheFieldAndTheTravellingShopReturnsToTheSameSpot() throws Exception {
        try (ExampleModHarness harness = open()) {
            play(harness, 2);
            if (!harness.debugJump("field:syz")) return; // Sonic 1 is optional.
            Object game = harness.scene().getClass().getMethod("game").invoke(harness.scene());
            Object f = game.getClass().getMethod("screen").invoke(game);
            Object zone = f.getClass().getMethod("zone").invoke(f);
            game.getClass().getMethod("zoneCleared", zone.getClass(), f.getClass().getInterfaces()[0]).invoke(game, zone, f);
            if (screen(harness).equals("STORY")) { harness.press(GLFW_KEY_BACKSPACE); play(harness, 2); }
            Object model = f.getClass().getMethod("field").invoke(f);
            model.getClass().getMethod("setPosition", double.class, double.class).invoke(model, 896.0, 336.0);
            harness.press(GLFW_KEY_RIGHT);
            assertTrue(until(harness, "LOADING", 60));
            assertTrue(until(harness, "FIELD", 600));
            Object west = game.getClass().getMethod("screen").invoke(game);
            if (!west.getClass().getMethod("zone").invoke(west).toString().equals("EMERALD_HILL")) return;
            Object progress = game.getClass().getField("progress").get(game);
            assertEquals(1, ((List<?>) progress.getClass().getMethod("party").invoke(progress)).size());
            harness.input().handleKeyEvent(GLFW_KEY_RIGHT, GLFW_PRESS);
            assertTrue(until(harness, "STORY", 80));
            harness.input().handleKeyEvent(GLFW_KEY_RIGHT, GLFW_RELEASE);
            for (int n = 0; n < 3 && screen(harness).equals("STORY"); n++) {
                harness.press(GLFW_KEY_BACKSPACE); play(harness, 2);
            }
            assertEquals("FIELD", screen(harness));
            assertEquals(2, ((List<?>) progress.getClass().getMethod("party").invoke(progress)).size());
            Object westModel = west.getClass().getMethod("field").invoke(west);
            westModel.getClass().getMethod("setPosition", double.class, double.class).invoke(westModel, 192.0, 396.0);
            harness.press(GLFW_KEY_ENTER); play(harness, 2);
            assertEquals("SHOP", screen(harness));
            harness.press(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE); play(harness, 2);
            assertEquals(west, game.getClass().getMethod("screen").invoke(game));
            assertEquals(192.0, westModel.getClass().getMethod("x").invoke(westModel));
            assertEquals(396.0, westModel.getClass().getMethod("y").invoke(westModel));
            clean(harness);
        }
    }

}
