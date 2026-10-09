package com.openggf.mods.code;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * farmer on the belt farm and in the valley, then visits the Marble Ruins' three zones, a
 * neighbour's talk and heart event, Waterfall Lake and the Bubble Bar, every festival, the board,
 * a night's tally and the year's end through the scene's debug jumps, playing and drawing frames
 * (recording only, no GL). The engine's fault boundary must catch nothing and the scene must
 * never leave. Skipped when Sonic 1 is not supplied (the scene then only explains what is missing).
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestStarpostValleyScene {
    private static final Path PROJECT = Path.of("examples/starpost-valley");
    private static final String[] FESTIVALS = {"ring_hunt", "sunflower_parade", "valley_race", "flickies",
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
                step(harness, "demo", 1);
                step(harness, "farm 400 30", 90);
                assertEquals("PlayScreen", screen(harness), farmer + " on the farm");
                step(harness, "valley 700", 120);
            }
            for (String chamber : new String[] {"1", "5", "20", "25", "35", "40"}) {
                step(harness, "new sonic", 10);
                step(harness, "ruins " + chamber, 150);
                assertEquals("RuinsScreen", screen(harness), "chamber " + chamber);
            }
            step(harness, "new sonic", 10);
            step(harness, "people talk tails", 90);
            step(harness, "close", 5);
            step(harness, "people social", 30);
            step(harness, "close", 5);
            step(harness, "people hearts tails 2", 1);
            step(harness, "time 1200", 1);
            step(harness, "people event tails_2", 240);
            step(harness, "new sonic", 10);
            step(harness, "fish lake", 90);
            assertEquals("LakeScreen", screen(harness));
            step(harness, "fish bar bubble_bass", 150);
            for (String festival : FESTIVALS) {
                step(harness, "new sonic", 10);
                step(harness, "festival day " + festival, 60);
                step(harness, "festival start " + festival, 240);
                assertTrue(!screen(harness).equals("PlayScreen"), festival + " is under way: " + screen(harness));
            }
            step(harness, "new sonic", 10);
            step(harness, "board", 30);
            step(harness, "close", 5);
            step(harness, "sleep", 420);
            step(harness, "yearend", 360);
            assertEquals(List.of(), harness.exits(), "the scene never left");
        }
    }

    /** The simple class name of the scene's current screen ("PlayScreen", "RuinsScreen"...). */
    private static String screen(ExampleModHarness harness) throws Exception {
        Object scene = harness.scene();
        java.lang.reflect.Field field = scene.getClass().getDeclaredField("shell");
        field.setAccessible(true);
        Object shell = field.get(scene);
        return shell.getClass().getMethod("screen").invoke(shell).getClass().getSimpleName();
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
