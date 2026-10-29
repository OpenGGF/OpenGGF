package com.openggf.mods.scene.host;

import com.openggf.control.ButtonPrompts;
import com.openggf.control.GamepadStateSource;
import com.openggf.control.InputBindings;
import com.openggf.control.InputHandler;
import com.openggf.mods.code.ModContextTestAccess;
import com.openggf.mods.code.ModFaultBoundary;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.mods.code.OwnedSceneFactory;
import com.openggf.mods.scene.ModScene;
import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneContext;
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
import static org.lwjgl.glfw.GLFW.*;

/**
 * A scene names player 1's logical buttons for the device its prompt names, from the live bindings
 * and pad family, through the real host and input handler; the labels never change input.
 */
class TestScenePromptLabels {
    @TempDir
    Path temp;

    private final ModSceneHost host = new ModSceneHost();
    private final AtomicReference<List<GamepadStateSource.DeviceState>> pads = new AtomicReference<>(List.of());
    private final AtomicReference<InputBindings> bindings = new AtomicReference<>(bindings(GLFW_KEY_SPACE, -1));
    private final InputHandler input = new InputHandler(bindings::get, pads::get);

    @AfterEach
    void close() {
        host.close();
    }

    /** Records each tick's labels and confirm/back so a test can compare what it names with what it does. */
    static final class LabelScene implements ModScene {
        SceneContext context;
        final List<String> ticks = new ArrayList<>();

        @Override public void enter(SceneContext ctx) { context = ctx; }
        @Override public void update(SceneContext ctx) {
            ticks.add(ctx.buttonLabel(ButtonPrompts.Button.A, false).orElse("-") + "|"
                    + ctx.buttonLabel(ButtonPrompts.Button.A, true).orElse("-") + "|"
                    + ctx.buttonLabel(ButtonPrompts.Button.B, true).orElse("-") + "|"
                    + ctx.buttonLabel(ButtonPrompts.Button.START, true).orElse("-") + "|"
                    + ctx.input().player1().actionPressedMask());
        }
        @Override public void draw(SceneContext ctx, SceneCanvas canvas) { }
    }

    private static InputBindings bindings(int p1A, int p1B) {
        return new InputBindings(GLFW_KEY_UP, GLFW_KEY_DOWN, GLFW_KEY_LEFT, GLFW_KEY_RIGHT, p1A, p1B, -1,
                GLFW_KEY_BACKSPACE, GLFW_KEY_I, GLFW_KEY_K, GLFW_KEY_J, GLFW_KEY_L, GLFW_KEY_RIGHT_SHIFT,
                GLFW_KEY_RIGHT_CONTROL, GLFW_KEY_RIGHT_ALT, GLFW_KEY_KP_ENTER, true, 0.35, "auto", "auto",
                GLFW_KEY_F2, GLFW_KEY_R, GLFW_KEY_F3);
    }

    private static GamepadStateSource.DeviceState pad(String name, int... down) {
        boolean[] buttons = new boolean[15];
        for (int button : down) buttons[button] = true;
        return GamepadStateSource.DeviceState.connected(0, name, buttons, 0, 0);
    }

    private static OwnedSceneFactory owned(ModScene scene) {
        ModFaultBoundary boundary = new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                owners -> new ModStateSaveResult.Saved(), owners -> { });
        return ModContextTestAccess.ownedScene("prompts", () -> scene, boundary);
    }

    private void tick() {
        input.refreshLogicalSnapshot();
        host.update(input);
        input.update();
    }

    @Test
    void labelsNameEachDeviceFromLiveBindingsAndFamiliesWithoutChangingInput() {
        LabelScene scene = new LabelScene();
        host.open(owned(scene), new SceneServices(null, null, temp, null, () -> { }, () -> { }), 320, 224);
        assertEquals(Optional.empty(), scene.context.buttonLabel(ButtonPrompts.Button.A, false),
                "before its first tick the context has no live input: callers use the Genesis name");
        tick();
        assertEquals("Space|-|-|-|0", scene.ticks.getLast(), "the bound key; no pad assigned yet");

        bindings.set(bindings(GLFW_KEY_J, GLFW_KEY_ENTER));
        tick();
        assertEquals("J|-|-|-|0", scene.ticks.getLast(), "a remapped A is named at once");
        assertEquals("Enter", scene.context.buttonLabel(ButtonPrompts.Button.B, false).orElseThrow(),
                "a scene can see that Enter is bound to B and stop it also confirming");

        for (String[] family : new String[][] {{"Xbox Wireless Controller", "X", "A", "Start"},
                {"Sony DualSense Wireless Controller", "Square", "Cross", "Options"},
                {"Switch Pro Controller", "West", "South", "Start"}}) {
            pads.set(List.of(pad(family[0])));
            tick();
            // Named per device the scene asks about, whichever device was pressed last.
            assertEquals("J|" + family[1] + "|" + family[2] + "|" + family[3] + "|0", scene.ticks.getLast(), family[0]);
            pads.set(List.of(pad(family[0], GLFW_GAMEPAD_BUTTON_X)));
            tick();
            String pressed = scene.ticks.getLast();
            assertEquals("J|" + family[1] + "|" + family[2] + "|" + family[3],
                    pressed.substring(0, pressed.lastIndexOf('|')), "labels do not move with the press");
            assertEquals(false, pressed.endsWith("|0"), "the west button still produces Genesis A");
            pads.set(List.of(pad(family[0])));
            tick();
            pads.set(List.of());
            tick();
        }
        assertEquals("J|-|-|-|0", scene.ticks.getLast(), "a disconnected pad has no labels");
    }
}
