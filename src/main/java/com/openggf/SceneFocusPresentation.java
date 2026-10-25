package com.openggf;

import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import org.lwjgl.system.MemoryStack;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;

/** Tracks scene pixels in the last swapped frame, including scenes drawn over gameplay. */
final class SceneFocusPresentation {
    private boolean scenePresented;

    boolean displayAndSwap(BooleanSupplier display, Runnable swap, LongSupplier sceneSubmissions) {
        long before = sceneSubmissions.getAsLong();
        if (!Engine.displayAndSwap(display, swap)) return false;
        scenePresented = sceneSubmissions.getAsLong() != before;
        return true;
    }

    void withdraw(Runnable swap) {
        if (!scenePresented) return;
        // A neutral frame withdraws even a suspended/closed visit without reentering creator
        // drawing, display shaders, capture, or any of the normal display() update owners.
        int framebuffer = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        boolean scissor = glIsEnabled(GL_SCISSOR_TEST);
        try (var stack = MemoryStack.stackPush()) {
            var mask = stack.malloc(4);
            glGetBooleani_v(GL_COLOR_WRITEMASK, 0, mask);
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, 0);
            int drawBuffer = glGetInteger(GL_DRAW_BUFFER);
            try {
                glDrawBuffer(glGetBoolean(GL_DOUBLEBUFFER) ? GL_BACK : GL_FRONT);
                glDisable(GL_SCISSOR_TEST);
                glColorMaski(0, true, true, true, true);
                glClearBufferfv(GL_COLOR, 0, stack.floats(0, 0, 0, 1));
                swap.run();
                scenePresented = false;
            } finally {
                glDrawBuffer(drawBuffer);
                glColorMaski(0, mask.get(0) != 0, mask.get(1) != 0, mask.get(2) != 0, mask.get(3) != 0);
                if (scissor) glEnable(GL_SCISSOR_TEST);
                glBindFramebuffer(GL_DRAW_FRAMEBUFFER, framebuffer);
            }
        }
    }
}
