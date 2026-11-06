package openggf.timeattack;

import com.openggf.control.GamepadStateSource;
import com.openggf.control.InputBindings;
import com.openggf.control.InputHandler;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.mods.code.ModContextTestAccess;
import com.openggf.mods.code.ModFaultBoundary;
import com.openggf.mods.run.RunHandle;
import com.openggf.mods.run.RunHost;
import com.openggf.mods.run.RunSpec;
import com.openggf.mods.scene.SceneGameplay;
import com.openggf.mods.scene.host.ModSceneHost;
import com.openggf.mods.scene.host.SceneServices;
import openggf.timeattack.ui.MenuPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.glfw.GLFW.*;

/**
 * The menu's key caps name what the real input path does: the pad family's physical buttons for
 * Genesis A, B and Start, and on a keyboard Enter and Escape unless the player bound one of them to
 * the opposite button, when one press does one thing and the caps name the bound keys.
 */
class TestTimeAttackControllerPrompts {
    @TempDir
    Path temp;

    private final ModSceneHost host = new ModSceneHost();
    private final AtomicReference<List<GamepadStateSource.DeviceState>> pads = new AtomicReference<>(List.of());
    private final AtomicReference<InputBindings> bindings = new AtomicReference<>(bindings(-1));
    private final InputHandler input = new InputHandler(bindings::get, pads::get);
    private final List<String> exits = new ArrayList<>();
    private final List<RunSpec> launched = new ArrayList<>();
    private TimeAttackScene scene;

    @AfterEach
    void close() {
        host.close();
    }

    /** Default keyboard (A Space, Start Backspace) with an optional key for B. */
    private static InputBindings bindings(int p1B) {
        return new InputBindings(GLFW_KEY_UP, GLFW_KEY_DOWN, GLFW_KEY_LEFT, GLFW_KEY_RIGHT, GLFW_KEY_SPACE, p1B, -1,
                GLFW_KEY_BACKSPACE, GLFW_KEY_I, GLFW_KEY_K, GLFW_KEY_J, GLFW_KEY_L, GLFW_KEY_RIGHT_SHIFT,
                GLFW_KEY_RIGHT_CONTROL, GLFW_KEY_RIGHT_ALT, GLFW_KEY_KP_ENTER, true, 0.35, "auto", "auto",
                GLFW_KEY_F2, GLFW_KEY_R, GLFW_KEY_F3);
    }

    private void open() {
        SceneGameplay gameplay = new SceneGameplay() {
            @Override public List<String> availableGames() { return List.of("s2"); }
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
        ModFaultBoundary boundary = new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                owners -> new ModStateSaveResult.Saved(), owners -> { });
        host.open(ModContextTestAccess.ownedScene("time-attack", () -> {
                    scene = new TimeAttackScene(temp.resolve("ghosts"), temp.resolve("identity"));
                    return scene;
                }, boundary),
                new SceneServices(null, null, temp, null, () -> exits.add("game"), () -> exits.add("master"),
                        null, gameplay), 320, 224);
        tick();
        if (scene.isPromptingForName()) {
            "Tester".codePoints().forEach(cp -> { com.openggf.control.MenuInput.handleCharEvent(input, cp); tick(); });
            key(GLFW_KEY_ENTER);
        }
    }

    private void tick() {
        input.refreshLogicalSnapshot();
        host.update(input);
        input.update();
    }

    private void key(int glfwKey) {
        input.handleKeyEvent(glfwKey, GLFW_PRESS);
        tick();
        input.handleKeyEvent(glfwKey, GLFW_RELEASE);
        tick();
    }

    private static GamepadStateSource.DeviceState pad(String name, int... down) {
        boolean[] buttons = new boolean[15];
        for (int button : down) buttons[button] = true;
        return GamepadStateSource.DeviceState.connected(0, name, buttons, 0, 0);
    }

    private void button(String name, int glfwButton) {
        pads.set(List.of(pad(name, glfwButton)));
        tick();
        pads.set(List.of(pad(name)));
        tick();
    }

    @Test
    void padCapsNameEachFamilysButtonsAndThoseButtonsConfirmAndGoBack() {
        open();
        String[][] families = {{"Xbox Wireless Controller", "X", "A", "Start"},
                {"Sony DualSense Wireless Controller", "Square", "Cross", "Options"},
                {"Switch Pro Controller", "West", "South", "Start"}};
        for (String[] family : families) {
            pads.set(List.of(pad(family[0])));
            tick();
            // The left stick press has no Genesis action: it only says a pad is in use.
            button(family[0], GLFW_GAMEPAD_BUTTON_LEFT_THUMB);
            MenuPage.Keys keys = scene.keysForTest();
            assertTrue(keys.pad(), family[0]);
            assertEquals(family[1], keys.confirm(), "Genesis A's physical button on " + family[0]);
            assertEquals(family[2], keys.back(), "Genesis B's physical button on " + family[0]);
            assertEquals(family[3], keys.start(), family[0]);
            assertEquals("R3", keys.details());
            // Up from the first row wraps to Settings; the named confirm opens it and the named back closes it.
            button(family[0], GLFW_GAMEPAD_BUTTON_DPAD_UP);
            button(family[0], GLFW_GAMEPAD_BUTTON_X);
            assertTrue(scene.isShowingSettings(), "the west button (named " + family[1] + ") confirms");
            button(family[0], GLFW_GAMEPAD_BUTTON_A);
            assertFalse(scene.isShowingSettings(), "the south button (named " + family[2] + ") goes back");
            button(family[0], GLFW_GAMEPAD_BUTTON_DPAD_DOWN);
        }
        assertEquals(List.of(), exits, "Back closed only the page it was on");
        assertTrue(launched.isEmpty());
        key(GLFW_KEY_LEFT_BRACKET); // an unbound key: still a keyboard press
        assertFalse(scene.keysForTest().pad(), "a key press names the keyboard again");
        assertEquals("ENTER", scene.keysForTest().confirm());
        assertEquals("ESC", scene.keysForTest().back());
        assertEquals("Space", scene.keysForTest().select(), "the bound A, for rows when Enter is Play");
    }

    @Test
    void enterBoundToBGoesBackOnlyAndTheCapsNameTheBoundConfirm() {
        bindings.set(bindings(GLFW_KEY_ENTER));
        open();
        key(GLFW_KEY_UP);
        MenuPage.Keys keys = scene.keysForTest();
        assertEquals("Space", keys.confirm(), "Enter belongs to B, so the bound A confirms");
        assertEquals("ESC", keys.back());
        assertEquals("Backspace", keys.start(), "the bound Start, not Enter");
        // Up wrapped from the first row to Settings; the bound A opens it.
        key(GLFW_KEY_SPACE);
        assertTrue(scene.isShowingSettings());
        key(GLFW_KEY_ENTER);
        assertFalse(scene.isShowingSettings(), "Enter, bound to B, closed the page");
        assertFalse(host.capturesTextInput(), "and did not also open the focused row's editor");
        key(GLFW_KEY_ENTER);
        assertEquals(List.of("master"), exits, "on the menu Enter goes back to the title, once");
        assertTrue(launched.isEmpty());
    }
}
