package com.openggf.game;

import java.util.function.BooleanSupplier;

/** Routes global shortcuts around title-owned menus without changing physical input. */
public final class TitleInputOwnership {
    private TitleInputOwnership() { }

    /** Consume the title's confirmed quit request through the existing host exit flow. */
    public static void routeQuit(MasterTitleScreen title, Runnable exit) {
        if (title != null && title.consumeQuitRequest()) exit.run();
    }

    /**
     * An already-open global picker keeps its input until it closes. Otherwise the
     * action pane and its children own all keys, including configurable shortcuts
     * that overlap ordinary typing such as V, brackets and backslash.
     *
     * @return whether the global picker consumed this presentation frame
     */
    public static boolean routeDisplay(GameMode mode, MasterTitleScreen title, boolean pickerAlreadyOpen,
                                       BooleanSupplier updatePicker, Runnable updateColor,
                                       Runnable updateShaders) {
        return routeDisplay(mode, title, false, pickerAlreadyOpen, updatePicker, updateColor, updateShaders);
    }

    /**
     * As {@link #routeDisplay(GameMode, MasterTitleScreen, boolean, BooleanSupplier, Runnable, Runnable)},
     * where {@code sceneCapturesText} is true while an open mod scene claims keyboard text input:
     * the scene then owns the keys exactly as a blocking title page does.
     */
    public static boolean routeDisplay(GameMode mode, MasterTitleScreen title, boolean sceneCapturesText,
                                       boolean pickerAlreadyOpen, BooleanSupplier updatePicker,
                                       Runnable updateColor, Runnable updateShaders) {
        if (pickerAlreadyOpen) return updatePicker.getAsBoolean();
        if (!allowsGlobalShortcuts(mode, title, sceneCapturesText)) return false;
        boolean consumed = updatePicker.getAsBoolean();
        if (!consumed) {
            updateColor.run();
            updateShaders.run();
        }
        return consumed;
    }

    /** Keep physical chord history current even when a menu suppresses its effect. */
    public static boolean routeCapture(GameMode mode, MasterTitleScreen title, BooleanSupplier updateChord) {
        return routeCapture(mode, title, false, updateChord);
    }

    /** As {@link #routeCapture(GameMode, MasterTitleScreen, BooleanSupplier)} with a scene text claim. */
    public static boolean routeCapture(GameMode mode, MasterTitleScreen title, boolean sceneCapturesText,
                                       BooleanSupplier updateChord) {
        boolean pressed = updateChord.getAsBoolean();
        return pressed && allowsGlobalShortcuts(mode, title, sceneCapturesText);
    }

    private static boolean allowsGlobalShortcuts(GameMode mode, MasterTitleScreen title, boolean sceneCapturesText) {
        if (mode == GameMode.MOD_SCENE) return !sceneCapturesText;
        return mode != GameMode.MASTER_TITLE_SCREEN || (title != null && !title.blocksGlobalShortcuts());
    }

    /** Playback shortcuts are gameplay tooling, not commands within the title GUI. */
    public static void routePlayback(GameMode mode, Runnable updatePlayback) {
        if (mode != GameMode.MASTER_TITLE_SCREEN) updatePlayback.run();
    }
}
