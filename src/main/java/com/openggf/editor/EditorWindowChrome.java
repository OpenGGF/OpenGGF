package com.openggf.editor;

import com.openggf.control.InputHandler;

import java.util.List;

/**
 * Editor user interface drawn at window resolution around the integer-scaled game, supplied by
 * a separate editor build such as OpenGGF-Editor and discovered with {@link java.util.ServiceLoader}
 * (see {@link EditorWindowChromes}). Without one, or with {@code -Dopenggf.editor.ui=classic},
 * the in-game editor chrome stays in charge. Engine-internal host extension, not creator API.
 *
 * <p>Lifecycle: {@link #init} once a GL context exists; for each displayed frame
 * {@link #setActive}, then {@link #gameViewport} before the game draws and {@link #render} after
 * the final display pass; {@link #close} at shutdown. Window input passes through the filters
 * before the engine sees it, and each editor tick reaches {@link #update} before the classic
 * keyboard and mouse handler. All edits go through {@link Host#dispatch} or the controller, so
 * undo, persistence and validation are the engine's.
 */
public interface EditorWindowChrome extends AutoCloseable {
    /** What the engine offers a chrome. */
    interface Host {
        LevelEditorController controller();

        void dispatch(EditorInputHandler.Action action);

        /** Project, playtest and import commands, as listed in the command palette. */
        List<EditorCommandPalette.HostCommand> commands();
    }

    /** Requires a current GL context; a failure leaves the classic chrome in charge. */
    void init(Host host, float framebufferScale) throws Exception;

    /** Called once per displayed frame; returns whether this chrome owns the editor frame. */
    boolean setActive(boolean editorFrame);

    boolean isActive();

    /**
     * Game viewport {@code {x, y, width, height}} in framebuffer pixels with OpenGL's bottom-left
     * origin; an empty array restores the engine's default layout; null leaves it unchanged.
     */
    int[] gameViewport(int windowWidth, int windowHeight, int framebufferWidth, int framebufferHeight,
                       int logicalWidth, int logicalHeight);

    /** Draws over the finished game frame. */
    void render(int windowWidth, int windowHeight, int framebufferWidth, int framebufferHeight);

    // Window input filters: return true when the engine should also receive the event.

    default boolean mouseMove(double x, double y) { return true; }

    default boolean mouseButton(int button, int action) { return true; }

    default boolean scroll(double xOffset, double yOffset) { return true; }

    default boolean key(int key, int action, int mods) { return true; }

    default boolean character(int codepoint) { return true; }

    default void focus(boolean focused) { }

    /** One editor tick before the classic handler; true when the chrome owned the whole tick. */
    default boolean update(InputHandler input) { return false; }

    /** Commit any edit in progress, e.g. before undo or save. */
    default void finishPendingEdits() { }

    /**
     * True when the chrome reads the gamepads itself; while it is active the engine's Genesis and
     * menu mappings, debug buttons and rewind then see a neutral pad.
     */
    default boolean ownsGamepads() { return false; }

    @Override
    void close();
}
