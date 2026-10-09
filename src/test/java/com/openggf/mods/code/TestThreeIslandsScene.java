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
            for (double[] position : new double[][] {{736, 516}}) {
                setPosition.invoke(field, position[0], position[1]);
                harness.press(GLFW_KEY_ENTER); play(harness, 3);
                assertEquals("STORY", screen(harness), "a discovery opens dialogue");
                harness.press(GLFW_KEY_BACKSPACE); play(harness, 3);
                assertEquals("FIELD", screen(harness));
            }
            assertEquals(1, field.getClass().getMethod("discoveries").invoke(field));
            // The relay saves at the eastern sanctuary; dungeon discoveries are checked separately.
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
            assertEquals(1, resumed.getClass().getMethod("discoveries").invoke(resumed));
            assertEquals(784.0, resumed.getClass().getMethod("x").invoke(resumed));
            assertEquals(264.0, resumed.getClass().getMethod("y").invoke(resumed));
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

    private static Object value(Object owner, String method) throws Exception {
        return owner.getClass().getMethod(method).invoke(owner);
    }

    private static void position(Object field, double x, double y) throws Exception {
        field.getClass().getMethod("setPosition", double.class, double.class).invoke(field, x, y);
    }

    private static Object spot(Object field, String kind) throws Exception {
        for (Object entry : (List<?>) field.getClass().getField("spots").get(field)) {
            if (entry.getClass().getField("kind").get(entry).toString().equals(kind)) return entry;
        }
        throw new AssertionError("No " + kind);
    }

    private static void finishDialogue(ExampleModHarness harness) throws Exception {
        for (int i = 0; i < 4 && screen(harness).equals("STORY"); i++) {
            harness.press(GLFW_KEY_BACKSPACE); play(harness, 2);
        }
    }

    @Test
    void allStoryLandmarksHavePlayableInteriorsWithIndependentGatesSavesAndRewards() throws Exception {
        try (ExampleModHarness harness = open()) {
            int played = 0;
            for (String zone : ZONES) {
                if (!harness.debugJump("field:" + zone)) {
                    assertFalse(S3K_ZONES.contains(zone));
                    continue;
                }
                played++;
                Object game = value(harness.scene(), "game");
                Object outside = value(game, "screen");
                position(value(outside, "field"), 240, 176);
                harness.press(GLFW_KEY_ENTER); play(harness, 2);
                finishDialogue(harness);
                assertEquals("DUNGEON", screen(harness), zone + " enters through its actual doorway");
                Object room = value(game, "screen");
                Object field = value(room, "field");
                Object progress = game.getClass().getField("progress").get(game);
                Object cleared = value(progress, "clearedMask");
                Object joined = value(progress, "joinedMask");
                Object emeralds = value(progress, "emeralds");
                assertTrue((boolean) value(progress, "resumeDungeon"));
                assertFalse(harness.host().recordedFrame().isEmpty(), zone + " renders indoors");
                for (int gate = 0; gate < 2; gate++) {
                    List<?> spots = (List<?>) field.getClass().getField("spots").get(field);
                    Object guard = null;
                    for (Object candidate : spots) if (candidate.getClass().getField("id").get(candidate)
                            .equals("dungeon-guard-" + gate)) guard = candidate;
                    org.junit.jupiter.api.Assertions.assertNotNull(guard);
                    position(field, (double) guard.getClass().getField("homeX").get(guard) - 24,
                            (double) guard.getClass().getField("homeY").get(guard));
                    harness.press(GLFW_KEY_ENTER); play(harness, 2);
                    assertTrue(screen(harness).startsWith("BATTLE"), zone + " gate " + gate + " starts a fight, got " + screen(harness));
                    Object battle = value(game, "screen");
                    assertEquals(room, value(battle, "battlefield"), "battle retains interior artwork/camera");
                    assertTrue(harness.debugJump("win"));
                    assertTrue(until(harness, "BATTLE_RESULTS", 300));
                    play(harness, 40); harness.press(GLFW_KEY_ENTER); play(harness, 3);
                    assertEquals("DUNGEON", screen(harness));
                    assertEquals(cleared, value(progress, "clearedMask"), "indoor sentries never clear the outdoor chapter");
                    assertEquals(joined, value(progress, "joinedMask"), "no premature Knuckles recruitment");
                    assertEquals(emeralds, value(progress, "emeralds"), "no duplicate chapter emerald");
                    if (gate == 0) {
                        var saved = (java.util.Optional<?>) value(game, "readSave");
                        assertTrue(saved.isPresent());
                        game.getClass().getMethod("continueGame", saved.get().getClass()).invoke(game, saved.get());
                        assertTrue(until(harness, "LOADING", 60));
                        for (int tick = 0; tick < 700 && !screen(harness).equals("DUNGEON"); tick++) {
                            finishDialogue(harness); play(harness, 1);
                        }
                        assertEquals("DUNGEON", screen(harness), zone + " reloads inside");
                        room = value(game, "screen"); field = value(room, "field");
                        progress = game.getClass().getField("progress").get(game);
                        assertEquals(true, field.getClass().getMethod("guardDefeated", int.class).invoke(field, 0));
                        assertEquals(false, field.getClass().getMethod("guardDefeated", int.class).invoke(field, 1));
                    }
                }
                Object relic = spot(field, "RELIC");
                position(field, (double) relic.getClass().getField("homeX").get(relic),
                        (double) relic.getClass().getField("homeY").get(relic) + 24);
                harness.press(GLFW_KEY_ENTER); play(harness, 2); finishDialogue(harness);
                assertEquals(true, value(field, "dungeonComplete"), zone + " reward collected in the room");
                assertEquals(true, progress.getClass().getMethod("seen", String.class).invoke(progress, zone + "-field-memory"));
                Class<?> bagType = Class.forName("threeislands.core.Item", true, progress.getClass().getClassLoader());
                Object superRing = bagType.getField("SUPER_RING").get(null);
                Object count = progress.getClass().getMethod("count", bagType).invoke(progress, superRing);
                harness.press(GLFW_KEY_ENTER); play(harness, 2); finishDialogue(harness);
                assertEquals(count, progress.getClass().getMethod("count", bagType).invoke(progress, superRing), "no repeated rewards");
                position(field, 56, 336); harness.press(GLFW_KEY_LEFT); play(harness, 2); finishDialogue(harness);
                assertEquals("FIELD", screen(harness));
                assertEquals(false, value(progress, "resumeDungeon"));
                assertEquals(240.0, value(value(value(game, "screen"), "field"), "x"), "return to the actual doorway");
                harness.press(GLFW_KEY_ENTER); play(harness, 2); finishDialogue(harness);
                assertEquals("DUNGEON", screen(harness), "completed entrances remain usable");
                assertEquals(true, value(value(value(game, "screen"), "field"), "dungeonComplete"));
                clean(harness);
            }
            System.out.println("Three Islands interiors exercised: " + played + " of " + ZONES.size());
            assertTrue(played >= S3K_ZONES.size());
        }
    }

}
