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
    void aNewGameTellsItsStoryReachesTheMapAndSavesAtTheVillage() throws Exception {
        try (ExampleModHarness harness = open()) {
            play(harness, 10);
            assertEquals("TITLE", screen(harness));
            assertFalse(harness.host().recordedFrame().isEmpty());
            harness.press(GLFW_KEY_ENTER);
            play(harness, 2);
            assertEquals("STORY", screen(harness), "the prologue plays");
            // Enter reveals and advances lines; Start (Backspace on the keyboard) skips a scene.
            harness.press(GLFW_KEY_ENTER);
            play(harness, 2);
            harness.press(GLFW_KEY_BACKSPACE);
            play(harness, 2);
            assertEquals("STORY", screen(harness), "the arrival scene follows");
            harness.press(GLFW_KEY_BACKSPACE);
            assertTrue(until(harness, "MAP", 60), "then the island map: " + screen(harness));
            play(harness, 30);
            for (int i = 0; i < 4; i++) {
                harness.press(GLFW_KEY_LEFT);
                play(harness, 12);
            }
            harness.press(GLFW_KEY_ENTER);
            play(harness, 2);
            assertEquals("VILLAGE", screen(harness));
            harness.press(GLFW_KEY_ENTER);
            play(harness, 4);
            Path save;
            try (Stream<Path> files = Files.walk(work.resolve("saves"))) {
                save = files.filter(p -> p.getFileName().toString().equals("save.txt")).findFirst().orElseThrow();
            }
            String text = Files.readString(save);
            assertTrue(text.startsWith("three-islands-save 1"), text);
            assertTrue(text.contains("scene=prologue"), text);
            // Leave the village, walk to the first zone and enter it through its loading card.
            harness.press(GLFW_KEY_DOWN);
            harness.press(GLFW_KEY_DOWN);
            harness.press(GLFW_KEY_DOWN);
            harness.press(GLFW_KEY_ENTER);
            play(harness, 2);
            assertEquals("MAP", screen(harness));
            harness.press(GLFW_KEY_RIGHT);
            play(harness, 40);
            harness.press(GLFW_KEY_ENTER);
            assertTrue(until(harness, "LOADING", 60), screen(harness));
            assertTrue(until(harness, "STORY", 600), "the zone's opening scene: " + screen(harness));
            harness.press(GLFW_KEY_BACKSPACE);
            play(harness, 2);
            assertEquals("FIELD", screen(harness));
            harness.input().handleKeyEvent(GLFW_KEY_RIGHT, GLFW_PRESS);
            assertTrue(until(harness, "BATTLE", 2000), "walking right meets a badnik: " + screen(harness));
            harness.input().handleKeyEvent(GLFW_KEY_RIGHT, GLFW_RELEASE);
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
                assertTrue(after.equals("STORY") || after.startsWith("MAP") || after.equals("ENDING"), zone + " -> " + after);
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
}
