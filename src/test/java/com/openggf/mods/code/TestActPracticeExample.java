package com.openggf.mods.code;

import com.openggf.control.InputHandler;
import com.openggf.game.run.GhostPose;
import com.openggf.game.run.PlayerPose;
import com.openggf.game.run.RunEndReason;
import com.openggf.game.run.RunHandle;
import com.openggf.game.run.RunHost;
import com.openggf.game.run.RunInput;
import com.openggf.game.run.RunLevelStart;
import com.openggf.game.run.RunSpec;
import com.openggf.game.run.RunStep;
import com.openggf.game.session.GameplayRunPolicy;
import com.openggf.mods.scene.SceneGameplay;
import com.openggf.mods.scene.host.ModSceneHost;
import com.openggf.mods.scene.host.SceneServices;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The non-Time-Attack sample of the title-entry and gameplay-run seams
 * ({@code examples/act-practice}) keeps building and validating, registers its master-title
 * entry, launches a practice run with its own policy, times it from the run steps and races the
 * previous attempt's ghost after a retry.
 */
@RequiresRom(SonicGame.SONIC_2)
class TestActPracticeExample {
    private static final Path PROJECT = Path.of("examples/act-practice");

    @TempDir
    Path work;

    private SharedLevel level;
    private final ModSceneHost host = new ModSceneHost();

    @AfterEach
    void dispose() {
        host.close();
        if (level != null) {
            level.dispose();
        }
    }

    @Test
    void entryLaunchesAPracticeRunTimesItAndRacesThePreviousAttempt() throws Exception {
        level = SharedLevel.load(SonicGame.SONIC_2, 0, 0);
        try (ExampleModHarness harness = ExampleModHarness.build(PROJECT, work.resolve("build"))) {
            OwnedTitleEntry entry = harness.titleEntry();
            assertEquals("Act Practice", entry.label());
            assertEquals(Map.of(), harness.findings(), "registration raised no findings");

            List<RunSpec> launched = new ArrayList<>();
            List<RunHost> hosts = new ArrayList<>();
            int[] retries = new int[1];
            SceneGameplay gameplay = new SceneGameplay() {
                @Override public List<String> availableGames() { return List.of("s2"); }
                @Override public RunHandle launch(RunSpec spec, RunHost runHost) {
                    launched.add(spec);
                    hosts.add(runHost);
                    return new RunHandle() {
                        @Override public void retry() { retries[0]++; }
                        @Override public void leave() { }
                        @Override public void spectate(boolean active, int dx, int dy) { }
                        @Override public boolean isActive() { return true; }
                    };
                }
            };
            List<String> exits = new ArrayList<>();
            host.open(entry.scene(), new SceneServices(null, null, work.resolve("saves"), null,
                    () -> exits.add("game"), () -> exits.add("master"), null, gameplay), 320, 224);
            InputHandler input = new InputHandler();
            press(input, org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);

            assertEquals(1, launched.size());
            RunSpec spec = launched.get(0);
            assertEquals("s2", spec.gameId());
            assertEquals(new GameplayRunPolicy(true, true, GameplayRunPolicy.ActCompletion.RETURN_TO_HOST, true, false),
                    spec.policy(), "practice keeps stages and rewind but returns after the act");

            RunHost run = hosts.get(0);
            run.onLevelReady(new RunLevelStart(spec, "fp", false, 4096, 1024));
            for (int i = 0; i < 10; i++) run.afterStep(step(i, i < 3 ? 0 : 0x08, 100 + i * 4, false));
            run.afterStep(step(10, 0x08, 140, true));
            assertTrue(run.ghosts().isEmpty(), "no previous attempt to race on the first run");

            assertTrue(run.admitStep(keys(org.lwjgl.glfw.GLFW.GLFW_KEY_R)));
            assertEquals(1, retries[0], "R retries through the run handle");
            run.onLevelReady(new RunLevelStart(spec, "fp", false, 4096, 1024));
            run.afterStep(step(0, 0, 100, false));
            run.afterStep(step(1, 0x08, 104, false));
            List<GhostPose> ghosts = run.ghosts();
            assertEquals(1, ghosts.size(), "the retry races the previous attempt");
            assertEquals(104, ghosts.get(0).pose().centreX());

            run.onRunEnded(RunEndReason.ACT_COMPLETED);
            host.resume(RunEndReason.ACT_COMPLETED);
            host.draw(null, null);
            assertEquals(List.of(), exits, "the scene stays open after the run");
        }
    }

    private void press(InputHandler input, int glfwKey) {
        input.handleKeyEvent(glfwKey, org.lwjgl.glfw.GLFW.GLFW_PRESS);
        host.update(input);
        input.update();
        input.handleKeyEvent(glfwKey, org.lwjgl.glfw.GLFW.GLFW_RELEASE);
        host.update(input);
        input.update();
    }

    private static RunStep step(long ordinal, int heldMask, int x, boolean complete) {
        return new RunStep(ordinal, heldMask, false, new PlayerPose(x, 300, 0, false, false, 2, false),
                complete, -1, false);
    }

    private static RunInput keys(int pressedKey) {
        return new RunInput() {
            @Override public com.openggf.control.LogicalInputSnapshot input() {
                return com.openggf.control.LogicalInputSnapshot.neutral();
            }
            @Override public boolean keyDown(int key) { return key == pressedKey; }
            @Override public boolean keyPressed(int key) { return key == pressedKey; }
        };
    }
}
