package com.openggf.mods.code;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openggf.game.GameModule;
import com.openggf.game.GameServices;
import com.openggf.mods.scene.host.ModSceneHost;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Typing of the Hedgehog's hub against a real Sonic 3 &amp; Knuckles session: builds and
 * validates the mod, opens its startup scene through the scene host with the ROM's own art
 * (Sonic, Tails, rings, zone backdrops, level kits), walks every screen and plays a Typing
 * School drill, recording draws without GL. The fault boundary must catch nothing.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestTypingHedgehogHubRom {
    @TempDir
    Path work;

    private SharedLevel level;

    @AfterEach
    void dispose() {
        if (level != null) level.dispose();
    }

    @Test
    void everyScreenRunsOnTheRomWithoutFaults() throws Exception {
        level = SharedLevel.load(SonicGame.SONIC_3K, 0, 0);
        try (ExampleModHarness harness = ExampleModHarness.build(Path.of("examples/typing-of-the-hedgehog"),
                work.resolve("build"))) {
            GameModule effective = harness.apply(GameServices.module());
            harness.open(effective, work.resolve("saves"), 320, 224);
            play(harness, 60);
            assertEquals("TITLE", screen(harness));
            assertTrue(harness.debugJump("type:start"));
            play(harness, 50);
            assertEquals("MENU", screen(harness));
            assertTrue(harness.debugJump("type:story"));
            play(harness, 30);
            assertEquals("STORY", screen(harness));
            assertTrue(harness.debugJump("demo"));
            for (String command : List.of("story:s3k", "arcade", "endless", "rush", "school", "options",
                    "records:ACTS", "records:SCHOOL", "records:KEYS", "results:sample", "menu")) {
                assertTrue(harness.debugJump(command), command);
                play(harness, 40);
            }
            assertTrue(harness.debugJump("drill:RING_RUSH"));
            play(harness, 100);
            assertEquals("DRILL", screen(harness));
            for (int i = 0; i < 30; i++) {
                harness.debugJump("type:e");
                play(harness, 4);
            }
            assertTrue(harness.findings().isEmpty(), "fault boundary findings: " + harness.findings());
            assertTrue(harness.debugJump("save"));
            play(harness, 1);
            assertTrue(Files.isRegularFile(work.resolve("saves").resolve("typing-of-the-hedgehog").resolve("profile.txt"))
                    || Files.list(work.resolve("saves")).findAny().isPresent(), "the profile is saved");
        }
    }

    private static String screen(ExampleModHarness harness) throws Exception {
        Object scene = harness.scene();
        return (String) scene.getClass().getMethod("screen").invoke(scene);
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
