package com.openggf.mods.code;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openggf.game.GameServices;
import com.openggf.mods.scene.host.SceneHostTestAccess;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The Flappy Tails tutorial's checkpoints ({@code examples/flappy-tails/tutorial/part-*}) keep
 * building and passing the mod validator; the classes they share with the finished example stay
 * byte-for-byte the finished example's, so the tutorial never teaches stale code; and each
 * checkpoint's scene opens, flaps and draws on a real S3K session without a fault.
 */
class TestFlappyTailsTutorial {
    private static final Path FINISHED = Path.of("examples/flappy-tails/src/main/java/flappytails");
    private static final Path TUTORIAL = Path.of("examples/flappy-tails/tutorial");

    @TempDir
    Path work;

    private SharedLevel level;

    @AfterEach
    void dispose() {
        if (level != null) level.dispose();
    }

    @ParameterizedTest
    @ValueSource(strings = {"part-2-first-scene", "part-3-tails", "part-4-flight", "part-5-pillars"})
    void checkpointBuildsAndPassesValidation(String part) throws Exception {
        try (ExampleModHarness harness = ExampleModHarness.build(TUTORIAL.resolve(part), work)) {
            assertEquals("flappy-tails-" + part.substring(0, 6).replace("-", ""), harness.manifest().id());
            assertEquals("WIDE_16_9", harness.plan().requiredDisplayAspect());
        }
    }

    @Test
    void sharedClassesAreTheFinishedExamplesOwn() throws Exception {
        try (Stream<Path> parts = Files.list(TUTORIAL)) {
            for (Path part : parts.sorted().toList()) {
                Path sources = part.resolve("src/main/java/flappytails");
                try (Stream<Path> files = Files.list(sources)) {
                    for (Path file : files.sorted().toList()) {
                        if (file.getFileName().toString().equals("FlappyScene.java")) continue;   // each part's own
                        assertArrayEquals(Files.readAllBytes(FINISHED.resolve(file.getFileName())), Files.readAllBytes(file),
                                part.getFileName() + " has a stale copy of " + file.getFileName());
                    }
                }
            }
        }
    }

    @ParameterizedTest
    @RequiresRom(SonicGame.SONIC_3K)
    @ValueSource(strings = {"part-2-first-scene", "part-3-tails", "part-4-flight", "part-5-pillars"})
    void checkpointScenePlaysOnTheRom(String part) throws Exception {
        level = SharedLevel.load(SonicGame.SONIC_3K, 0, 0);
        try (ExampleModHarness harness = ExampleModHarness.build(TUTORIAL.resolve(part), work.resolve("build"))) {
            harness.open(harness.apply(GameServices.module()), work.resolve("saves"), 400, 224);
            for (int i = 0; i < 300; i++) {
                if (i % 40 == 0) harness.press(org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE);
                harness.tick();
                harness.host().draw(null, null);
            }
            assertTrue(SceneHostTestAccess.lastFrameOps(harness.host()) > 3, part + " drew its world");
            assertEquals(Map.of(), harness.findings());
            assertEquals(List.of(), harness.exits());
        }
    }
}
