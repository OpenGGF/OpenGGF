package openggf.timeattack;

import com.openggf.control.InputHandler;
import com.openggf.game.run.RunEndReason;
import com.openggf.game.run.RunHandle;
import com.openggf.game.run.RunHost;
import com.openggf.game.run.RunSpec;
import com.openggf.game.session.GameplayRunPolicy;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.mods.code.ModContextTestAccess;
import com.openggf.mods.code.ModFaultBoundary;
import com.openggf.mods.scene.SceneGameplay;
import com.openggf.mods.scene.host.ModSceneHost;
import com.openggf.mods.scene.host.SceneServices;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Time Attack title-entry scene, driven through the engine's scene host. */
class TestTimeAttackScene {
    @TempDir
    Path temp;

    private final ModSceneHost host = new ModSceneHost();
    private final InputHandler input = new InputHandler();
    private final List<String> exits = new ArrayList<>();
    private final List<RunSpec> launched = new ArrayList<>();
    private final List<RunHost> hosts = new ArrayList<>();

    @AfterEach
    void close() {
        host.close();
    }

    private void open(List<String> games) {
        SceneGameplay gameplay = new SceneGameplay() {
            @Override public List<String> availableGames() { return games; }
            @Override public RunHandle launch(RunSpec spec, RunHost runHost) {
                launched.add(spec);
                hosts.add(runHost);
                return new RunHandle() {
                    @Override public void retry() { }
                    @Override public void leave() { }
                    @Override public void spectate(boolean active, int dx, int dy) { }
                    @Override public boolean isActive() { return true; }
                };
            }
        };
        ModFaultBoundary boundary = new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                owners -> new ModStateSaveResult.Saved(), owners -> { });
        host.open(ModContextTestAccess.ownedScene("time-attack", TimeAttackScene::new, boundary),
                new SceneServices(null, null, temp, null, () -> exits.add("game"), () -> exits.add("master"),
                        null, gameplay),
                320, 224);
    }

    private void press(int glfwKey) {
        input.handleKeyEvent(glfwKey, org.lwjgl.glfw.GLFW.GLFW_PRESS);
        host.update(input);
        input.update();
        input.handleKeyEvent(glfwKey, org.lwjgl.glfw.GLFW.GLFW_RELEASE);
        host.update(input);
        input.update();
    }

    @Test
    void fieldSelectionDoesNotLaunchButTheStartRowLaunchesAnIsolatedAct() {
        open(List.of("s2"));
        for (int i = 0; i < 4; i++) press(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);
        assertTrue(launched.isEmpty(), "field selection must not launch a run");
        press(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);
        assertEquals(1, launched.size());
        RunSpec spec = launched.get(0);
        assertEquals("s2", spec.gameId());
        assertEquals(GameplayRunPolicy.isolatedAct(), spec.policy());
        assertInstanceOf(TimeAttackRuntime.class, hosts.get(0));
    }

    @Test
    void backLeavesForTheMasterTitle() {
        open(List.of("s1"));
        press(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE);
        assertEquals(List.of("master"), exits);
        assertTrue(launched.isEmpty());
    }

    @Test
    void withoutGameRomsTheSceneOnlyOffersTheWayBack() {
        open(List.of());
        press(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);
        assertEquals(List.of("master"), exits);
        assertTrue(launched.isEmpty());
    }

    @Test
    void resumingAfterARunKeepsTheMenuUsable() {
        open(List.of("s2"));
        for (int i = 0; i < 5; i++) press(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);
        assertEquals(1, launched.size());
        host.resume(RunEndReason.ACT_COMPLETED);
        press(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);
        assertEquals(2, launched.size(), "the menu returns focused on START RUN");
    }
}
