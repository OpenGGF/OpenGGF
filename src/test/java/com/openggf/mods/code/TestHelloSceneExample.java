package com.openggf.mods.code;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openggf.game.GameModule;
import com.openggf.game.GameServices;
import com.openggf.mods.scene.host.ModSceneHost;
import com.openggf.mods.scene.host.SceneHostTestAccess;
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
 * The starter example {@code examples/hello-scene} keeps building, passing the mod validator
 * and running: its scene opens against a real S3K session, draws, takes input and saves its best
 * ring count without the fault boundary catching anything.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestHelloSceneExample {
    private static final Path PROJECT = Path.of("examples/hello-scene");

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
    void buildsRunsCollectsRingsAndSavesTheBest() throws Exception {
        level = SharedLevel.load(SonicGame.SONIC_3K, 0, 0);
        try (ExampleModHarness harness = ExampleModHarness.build(PROJECT, work.resolve("build"))) {
            assertEquals("hello-scene", harness.manifest().id());
            GameModule effective = harness.apply(GameServices.module());
            Path saves = work.resolve("saves");
            harness.open(effective, saves, 320, 224);
            ModSceneHost host = harness.host();
            // Run right along the ground for three seconds: Sonic passes the low rings.
            harness.input().handleKeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT, org.lwjgl.glfw.GLFW.GLFW_PRESS);
            for (int i = 0; i < 180; i++) {
                harness.tick();
                host.draw(null, null);
            }
            assertTrue(SceneHostTestAccess.lastFrameOps(host) > 0, "the scene drew something");
            assertEquals(Map.of(), harness.findings(), "the fault boundary caught nothing");
            assertEquals(List.of(), harness.exits(), "the scene stayed open");
            try (var files = Files.walk(saves)) {
                Path best = files.filter(p -> p.getFileName().toString().equals("best.txt")).findFirst()
                        .orElseThrow(() -> new AssertionError("no best.txt saved under " + saves));
                assertTrue(Integer.parseInt(Files.readString(best).trim()) >= 2,
                        "rings collected: " + Files.readString(best));
            }
        }
    }
}
