package com.openggf.control;

import org.lwjgl.glfw.GLFWGamepadState;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_AXIS_LAST;
import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_LAST;
import static org.lwjgl.glfw.GLFW.GLFW_JOYSTICK_1;
import static org.lwjgl.glfw.GLFW.GLFW_JOYSTICK_LAST;
import static org.lwjgl.glfw.GLFW.glfwGetGamepadName;
import static org.lwjgl.glfw.GLFW.glfwGetGamepadState;
import static org.lwjgl.glfw.GLFW.glfwGetJoystickGUID;
import static org.lwjgl.glfw.GLFW.glfwGetJoystickName;
import static org.lwjgl.glfw.GLFW.glfwJoystickIsGamepad;
import static org.lwjgl.glfw.GLFW.glfwJoystickPresent;
import static org.lwjgl.glfw.GLFW.glfwUpdateGamepadMappings;
import static org.lwjgl.system.MemoryStack.stackPush;

public class GlfwGamepadStateSource implements GamepadStateSource {

    private static final Logger LOGGER = Logger.getLogger(GlfwGamepadStateSource.class.getName());

    private boolean mappingsInstalled;
    private String bundledMappings;
    /** GUIDs already given a fallback attempt, so an unknown joystick is examined once. */
    private final Set<String> examinedGuids = new HashSet<>();

    @Override
    public List<DeviceState> pollDevices() {
        installMappingsOnce();
        List<DeviceState> devices = new ArrayList<>();
        try (MemoryStack stack = stackPush()) {
            GLFWGamepadState state = GLFWGamepadState.malloc(stack);
            for (int joystickId = GLFW_JOYSTICK_1; joystickId <= GLFW_JOYSTICK_LAST; joystickId++) {
                if (!glfwJoystickPresent(joystickId)) {
                    continue;
                }
                if (!glfwJoystickIsGamepad(joystickId) && !adoptFallbackMapping(joystickId)) {
                    continue;
                }
                if (!glfwGetGamepadState(joystickId, state)) {
                    continue;
                }
                boolean[] buttons = new boolean[GLFW_GAMEPAD_BUTTON_LAST + 1];
                for (int button = 0; button <= GLFW_GAMEPAD_BUTTON_LAST; button++) {
                    buttons[button] = state.buttons(button) != 0;
                }
                float[] axes = new float[GLFW_GAMEPAD_AXIS_LAST + 1];
                for (int axis = 0; axis <= GLFW_GAMEPAD_AXIS_LAST; axis++) {
                    axes[axis] = state.axes(axis);
                }
                devices.add(DeviceState.connected(
                        joystickId,
                        glfwGetGamepadName(joystickId),
                        buttons,
                        axes));
            }
        }
        return List.copyOf(devices);
    }

    /**
     * Adds the bundled SDL table, then {@code SDL_GAMECONTROLLERCONFIG}, on top of GLFW's
     * compiled-in mappings. Runs on the first poll, which is after {@code glfwInit}.
     */
    private void installMappingsOnce() {
        if (mappingsInstalled) return;
        mappingsInstalled = true;
        bundledMappings = GamepadMappings.bundled();
        updateMappings(bundledMappings);
        String extra = System.getenv(GamepadMappings.ENVIRONMENT_VARIABLE);
        if (extra != null && !extra.isBlank()) {
            updateMappings(extra);
        }
    }

    /** Maps an unknown joystick from the nearest firmware version of the same device. */
    private boolean adoptFallbackMapping(int joystickId) {
        String guid = glfwGetJoystickGUID(joystickId);
        if (guid == null || !examinedGuids.add(guid)) return false;
        Optional<String> mapping = GamepadMappings.fallbackFor(
                guid, GamepadMappings.currentPlatform(), bundledMappings);
        if (mapping.isEmpty()) return false;
        updateMappings(mapping.get());
        boolean adopted = glfwJoystickIsGamepad(joystickId);
        if (adopted) {
            LOGGER.info(() -> "Mapped controller '" + glfwGetJoystickName(joystickId) + "' (" + guid
                    + ") from the nearest known firmware version");
        }
        return adopted;
    }

    private static void updateMappings(String mappings) {
        ByteBuffer text = MemoryUtil.memUTF8(mappings, true);
        try {
            if (!glfwUpdateGamepadMappings(text)) {
                LOGGER.warning("GLFW rejected part of the gamepad mapping table");
            }
        } finally {
            MemoryUtil.memFree(text);
        }
    }
}
