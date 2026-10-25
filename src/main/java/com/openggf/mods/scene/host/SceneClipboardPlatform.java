package com.openggf.mods.scene.host;

import com.openggf.mods.scene.SceneClipboard.Result;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import org.lwjgl.glfw.GLFW;

/** Engine-internal platform owner. Native handles and clipboard reads never enter the scene API. */
public final class SceneClipboardPlatform {
    private final Thread owner = Thread.currentThread();
    private final Consumer<String> writer;
    private final LongSupplier clock;
    private boolean focused = true;
    private boolean iconified;

    SceneClipboardPlatform(Consumer<String> writer, LongSupplier clock) {
        this.writer = writer;
        this.clock = java.util.Objects.requireNonNull(clock);
    }

    public static SceneClipboardPlatform unavailable() {
        return new SceneClipboardPlatform(null, System::nanoTime);
    }

    /** Construct on the GLFW event thread, after window creation. */
    public static SceneClipboardPlatform glfw(long window) {
        if (window == 0) return unavailable();
        var platform = new SceneClipboardPlatform(text -> {
            // Consume any previous error so it cannot turn this operation into a false failure.
            GLFW.glfwGetError(null);
            GLFW.glfwSetClipboardString(window, text);
            if (GLFW.glfwGetError(null) != GLFW.GLFW_NO_ERROR) {
                throw new IllegalStateException("Clipboard unavailable");
            }
        }, System::nanoTime);
        platform.focused = GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE;
        platform.iconified = GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_ICONIFIED) == GLFW.GLFW_TRUE;
        return platform;
    }

    boolean isOwnerThread() { return Thread.currentThread() == owner; }
    boolean foreground() { return focused && !iconified; }
    boolean available() { return writer != null; }
    long nanoTime() { return clock.getAsLong(); }

    boolean focusChanged(boolean value) {
        requireOwner();
        boolean wasForeground = foreground();
        focused = value;
        return wasForeground && !foreground();
    }

    boolean iconifyChanged(boolean value) {
        requireOwner();
        boolean wasForeground = foreground();
        iconified = value;
        return wasForeground && !foreground();
    }

    Result write(String text) {
        requireOwner();
        if (!foreground()) return Result.DENIED;
        if (writer == null) return Result.UNAVAILABLE;
        try {
            writer.accept(text);
            return Result.SUCCESS;
        } catch (RuntimeException | LinkageError unavailable) {
            // Native exception messages can contain clipboard text. Never propagate or log them.
            return Result.UNAVAILABLE;
        }
    }

    private void requireOwner() {
        if (!isOwnerThread()) throw new IllegalStateException("Clipboard requires the platform thread");
    }
}
