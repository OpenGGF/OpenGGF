package com.openggf;

import com.openggf.control.InputHandler;
import com.openggf.editor.EditorInputHandler;
import com.openggf.game.GameMode;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import static org.lwjgl.glfw.GLFW.*;

/** Editor/project input ownership, including the quit prompt's native-frame suspension barrier. */
final class GameLoopEditorController {
    private EditorInputHandler editorInputHandler;
    private Predicate<InputHandler> projectInputHandler;
    private BooleanSupplier projectPlaytestSupplier = () -> false;
    private boolean modalSuspended;
    private Runnable playtestToggleHandler;
    private Runnable freshStartHandler;
    private Runnable stateSyncHandler;

    void setInputHandler(EditorInputHandler handler) {
        editorInputHandler = handler;
    }

    void setProjectInputHandler(Predicate<InputHandler> handler) {
        projectInputHandler = handler;
    }

    void setProjectPlaytestSupplier(BooleanSupplier supplier) {
        projectPlaytestSupplier = Objects.requireNonNull(supplier);
    }

    void setPlaytestToggleHandler(Runnable handler) {
        playtestToggleHandler = handler;
    }

    void setFreshStartHandler(Runnable handler) {
        freshStartHandler = handler;
    }

    void setStateSyncHandler(Runnable handler) {
        stateSyncHandler = handler;
    }

    void setModalSuspended(boolean suspended) {
        modalSuspended = suspended;
    }

    /** Called before PLC/VBlank/fade admission so pending native callbacks cannot retire the hidden run. */
    boolean consumeSuspendedFrame(InputHandler input, Runnable beginSuspendedFrame) {
        if (!modalSuspended) return false;
        if (input == null) {
            throw new IllegalStateException("InputHandler must be set before calling step()");
        }
        beginSuspendedFrame.run();
        if (projectInputHandler != null) projectInputHandler.test(input);
        input.update();
        // Cancellation may clear modalSuspended above. Its closing input frame still belongs to this prompt.
        return true;
    }

    boolean consumeProjectInput(GameMode mode, InputHandler input) {
        if ((mode == GameMode.MASTER_TITLE_SCREEN || mode == GameMode.EDITOR || mode == GameMode.LEVEL)
                && projectInputHandler != null && projectInputHandler.test(input)) {
            input.update();
            return true;
        }
        return false;
    }

    boolean consumeEditorFrame(GameMode mode, InputHandler input, BooleanSupplier paused,
                               BooleanSupplier editorEntryAllowed, BooleanSupplier editorEnabled) {
        if (!paused.getAsBoolean()
                && editorEntryAllowed.getAsBoolean()
                && (mode == GameMode.EDITOR
                || (mode == GameMode.LEVEL && (editorEnabled.getAsBoolean() || projectPlaytestSupplier.getAsBoolean())))
                && input.isKeyPressed(GLFW_KEY_TAB)
                && (input.isKeyDown(GLFW_KEY_LEFT_SHIFT) || input.isKeyDown(GLFW_KEY_RIGHT_SHIFT))
                && playtestToggleHandler != null) {
            playtestToggleHandler.run();
            input.update();
            return true;
        }
        if (mode != GameMode.EDITOR) return false;
        if (input.isKeyPressed(GLFW_KEY_F5) && freshStartHandler != null) {
            freshStartHandler.run();
        } else {
            if (editorInputHandler != null) editorInputHandler.update(input);
            if (stateSyncHandler != null) stateSyncHandler.run();
        }
        input.update();
        return true;
    }

    void finishPendingEdits(GameMode mode) {
        if (mode == GameMode.EDITOR && editorInputHandler != null) {
            editorInputHandler.finishPendingEdits();
        }
    }
}
