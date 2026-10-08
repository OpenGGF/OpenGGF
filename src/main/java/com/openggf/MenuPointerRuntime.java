package com.openggf;

import com.openggf.control.InputHandler;
import com.openggf.control.MenuInput;
import com.openggf.game.session.EngineServices;
import com.openggf.graphics.LogicalMouse;

import static org.lwjgl.glfw.GLFW.*;

/** Host composition for physical menu pointers; control values do not depend on rendering. */
public final class MenuPointerRuntime {
    private MenuPointerRuntime() { }

    public static MenuInput.Pointer pointer(InputHandler input, int width, int height) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Invalid logical pointer dimensions");
        if (input == null || input.hasLogicalOverride() || !input.hasMouseInputSeen()) return MenuInput.Pointer.none();
        var graphics = EngineServices.current().graphics();
        if (graphics.isHeadlessMode()) return MenuInput.Pointer.none();
        var point = LogicalMouse.map(glfwGetCurrentContext(), graphics,
                input.getMouseX(), input.getMouseY(), width, height);
        return point == null ? MenuInput.Pointer.none() : new MenuInput.Pointer(point[0], point[1], point[2] != 0,
                input.isMouseButtonPressed(GLFW_MOUSE_BUTTON_LEFT), input.isMouseButtonPressed(GLFW_MOUSE_BUTTON_RIGHT));
    }
}
