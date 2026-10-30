package com.openggf.mods.code;

import com.openggf.control.InputHandler;
import com.openggf.mods.run.GhostPose;
import com.openggf.mods.run.PlayerPose;
import com.openggf.mods.run.RunEndReason;
import com.openggf.mods.run.RunHandle;
import com.openggf.mods.run.RunHost;
import com.openggf.mods.run.RunInput;
import com.openggf.mods.run.RunLevelStart;
import com.openggf.mods.run.RunSpec;
import com.openggf.mods.run.RunStep;
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
            assertEquals(new GameplayRunPolicy(true, true, GameplayRunPolicy.ActCompletion.RETURN_TO_HOST, true, false,
                GameplayRunPolicy.DeathRestart.STOCK, true, true),
                    spec.policy(), "practice keeps stages and rewind but returns after the act");

            RunHost run = hosts.get(0);
            run.onLevelReady(new RunLevelStart(spec, "fp", false, 4096, 1024, false));
            for (int i = 0; i < 10; i++) run.afterStep(step(i, i < 3 ? 0 : 0x08, 100 + i * 4, false));
            run.afterStep(step(10, 0x08, 140, true));
            assertTrue(run.ghosts().isEmpty(), "no previous attempt to race on the first run");

            assertTrue(run.admitStep(keys(org.lwjgl.glfw.GLFW.GLFW_KEY_R)));
            assertEquals(1, retries[0], "R retries through the run handle");
            run.onLevelReady(new RunLevelStart(spec, "fp", false, 4096, 1024, false));
            run.afterStep(step(0, 0, 100, false));
            run.afterStep(step(1, 0x08, 104, false));
            List<GhostPose> ghosts = run.ghosts();
            assertEquals(1, ghosts.size(), "the retry races the previous attempt");
            assertEquals(104, ghosts.get(0).pose().centreX());

            run.onRunEnded(RunEndReason.ACT_COMPLETED);
            host.resume(RunEndReason.ACT_COMPLETED);
            host.draw(null, null);
            assertEquals(List.of(), exits, "the scene stays open after the run");
            Object scene = openScene();
            assertEquals("GOOD: Act complete in 0:00.11, a new best", call(scene, "resultForTest"),
                    "the result names the time in the compact face's m:ss.cc and says it set the best");
            host.resume(RunEndReason.LOAD_FAILED);
            assertEquals("ERROR: That act could not be loaded", call(scene, "resultForTest"));
        }
    }

    @Test
    void withoutGamesTheEntryIgnoresSelectionAndLeavesOnEnter() throws Exception {
        try (ExampleModHarness harness = ExampleModHarness.build(PROJECT, work.resolve("build"))) {
            SceneGameplay gameplay = new SceneGameplay() {
                @Override public List<String> availableGames() { return List.of(); }
                @Override public RunHandle launch(RunSpec spec, RunHost runHost) {
                    throw new AssertionError("nothing to launch without a game");
                }
            };
            List<String> exits = new ArrayList<>();
            host.open(harness.titleEntry().scene(), new SceneServices(null, null, work.resolve("saves"), null,
                    () -> exits.add("game"), () -> exits.add("master"), null, gameplay), 320, 224);
            InputHandler input = new InputHandler();
            press(input, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT);
            press(input, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT);
            assertTrue(host.isOpen(), "changing the game with an empty list is inert, not a scene fault");
            assertEquals(List.of(), exits);

            press(input, org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);
            assertEquals(List.of("master"), exits, "Enter offers the way back when no game is available");
        }
    }

    @Test
    void keyCapsNameTheDevicePressedLastAndControllerAConfirmsAsInEveryScene() throws Exception {
        try (ExampleModHarness harness = ExampleModHarness.build(PROJECT, work.resolve("build"))) {
            List<RunSpec> launched = new ArrayList<>();
            SceneGameplay gameplay = new SceneGameplay() {
                @Override public List<String> availableGames() { return List.of("s1", "s2"); }
                @Override public RunHandle launch(RunSpec spec, RunHost runHost) {
                    launched.add(spec);
                    return new RunHandle() {
                        @Override public void retry() { }
                        @Override public void leave() { }
                        @Override public void spectate(boolean active, int dx, int dy) { }
                        @Override public boolean isActive() { return true; }
                    };
                }
            };
            List<String> exits = new ArrayList<>();
            host.open(harness.titleEntry().scene(), new SceneServices(null, null, work.resolve("saves"), null,
                    () -> exits.add("game"), () -> exits.add("master"), null, gameplay), 320, 224);
            boolean[] buttons = new boolean[15];
            InputHandler input = new InputHandler(com.openggf.InputBindingFactory.standaloneSupplier(),
                    () -> List.of(com.openggf.control.GamepadStateSource.DeviceState.connected(0, "Xbox Controller",
                            buttons.clone(), 0f, 0f)));
            Object scene = openScene();
            input.refreshLogicalSnapshot(); // the pad connects with nothing held: its baseline
            keyPress(input, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT);
            assertEquals(List.of("LEFT/RIGHT Game", "ENTER Practise", "ESC Back"), call(scene, "hintsForTest"));
            // Any button press names the pad's buttons; the left stick press has no Genesis action.
            padPress(input, buttons, org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_LEFT_THUMB);
            assertEquals(List.of("LEFT/RIGHT Game", "X Practise", "A Back"), call(scene, "hintsForTest"),
                    "a controller press names the Xbox buttons that produce Genesis A and B");
            assertTrue(launched.isEmpty());
            // Genesis A is the pad's west button; it confirms, as the scene convention says.
            padPress(input, buttons, org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_X);
            assertEquals(1, launched.size(), "A confirms the choice");
            assertEquals("s2", launched.getFirst().gameId(), "the game Right chose is the one A launches");
            keyPress(input, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT);
            assertEquals(List.of("LEFT/RIGHT Game", "ENTER Practise", "ESC Back"), call(scene, "hintsForTest"),
                    "a key press names keyboard keys again");
            assertEquals(List.of(), exits);
        }
    }

    @Test
    void enterBoundToBGoesBackOnceAndTheCapsNameTheBoundConfirm() throws Exception {
        try (ExampleModHarness harness = ExampleModHarness.build(PROJECT, work.resolve("build"))) {
            List<RunSpec> launched = new ArrayList<>();
            SceneGameplay gameplay = new SceneGameplay() {
                @Override public List<String> availableGames() { return List.of("s1", "s2"); }
                @Override public RunHandle launch(RunSpec spec, RunHost runHost) {
                    launched.add(spec);
                    return null;
                }
            };
            List<String> exits = new ArrayList<>();
            host.open(harness.titleEntry().scene(), new SceneServices(null, null, work.resolve("saves"), null,
                    () -> exits.add("game"), () -> exits.add("master"), null, gameplay), 320, 224);
            var base = com.openggf.InputBindingFactory.standaloneSupplier().get();
            com.openggf.control.InputBindings enterIsB = new com.openggf.control.InputBindings(base.p1Up(), base.p1Down(),
                    base.p1Left(), base.p1Right(), org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE, org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER,
                    base.p1C(), base.p1Start(), base.p2Up(), base.p2Down(), base.p2Left(), base.p2Right(), base.p2A(),
                    base.p2B(), base.p2C(), base.p2Start(), base.controllerEnabled(), base.controllerDeadzone(),
                    base.controllerPlayer1(), base.controllerPlayer2(), base.debugModeKey(), base.rewindKey(),
                    base.frameStepKey());
            InputHandler input = new InputHandler(() -> enterIsB, List::of);
            Object scene = openScene();
            keyPress(input, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT);
            assertEquals(List.of("LEFT/RIGHT Game", "Space Practise", "ESC Back"), call(scene, "hintsForTest"),
                    "Enter belongs to B, so the bound A is the confirm named");
            keyPress(input, org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);
            assertEquals(List.of("master"), exits, "Enter went back");
            assertTrue(launched.isEmpty(), "and did not also launch the chosen act");
        }
    }

    private Object openScene() throws Exception {
        java.lang.reflect.Field field = ModSceneHost.class.getDeclaredField("scene");
        field.setAccessible(true);
        return OwnedSceneFactory.unwrap((com.openggf.mods.scene.ModScene) field.get(host));
    }

    private static Object call(Object target, String name) throws Exception {
        java.lang.reflect.Method method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(target);
    }

    /** One key press with the logical snapshot refreshed each tick, as the engine's frame does. */
    private void keyPress(InputHandler input, int glfwKey) {
        input.handleKeyEvent(glfwKey, org.lwjgl.glfw.GLFW.GLFW_PRESS);
        input.refreshLogicalSnapshot();
        host.update(input);
        input.update();
        input.handleKeyEvent(glfwKey, org.lwjgl.glfw.GLFW.GLFW_RELEASE);
        input.refreshLogicalSnapshot();
        host.update(input);
        input.update();
    }

    /** One press of a pad button through the real physical and logical (Genesis) mapping. */
    private void padPress(InputHandler input, boolean[] buttons, int button) {
        buttons[button] = true;
        input.refreshLogicalSnapshot();
        host.update(input);
        input.update();
        buttons[button] = false;
        input.refreshLogicalSnapshot();
        host.update(input);
        input.update();
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
