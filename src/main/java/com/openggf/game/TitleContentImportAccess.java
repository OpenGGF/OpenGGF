package com.openggf.game;

import java.util.Objects;

/** Host-owned import entry point; importing is separate from the creator title-screen API. */
public final class TitleContentImportAccess {
    private TitleContentImportAccess() { }

    public static void bind(MasterTitleScreen screen, Runnable open) {
        Objects.requireNonNull(screen).contentImportOpenHandler = Objects.requireNonNull(open);
    }

    public static boolean acceptsDrop(MasterTitleScreen screen) {
        return screen != null && screen.acceptsContentDrop();
    }

    public static void openMods(MasterTitleScreen screen) { Objects.requireNonNull(screen).openModManager(); }
}
