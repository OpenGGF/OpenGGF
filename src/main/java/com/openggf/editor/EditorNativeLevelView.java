package com.openggf.editor;

import com.openggf.level.Level;
import com.openggf.level.LevelObjectArt;
import com.openggf.level.MutableLevel;
import java.io.IOException;

/** Native editing retains ROM collision remaps and bounds instead of flattening them into a project. */
public final class EditorNativeLevelView {
    private EditorNativeLevelView() { }

    public static MutableLevel prepare(Level source, com.openggf.game.ObjectArtProvider provider) throws IOException {
        if (source instanceof MutableLevel mutable) return mutable;
        Level library = LevelObjectArt.authoringLibrary(source, provider);
        // Initial authoring uses the complete native ROM library, including its
        // collision delegate. Intro-only VRAM residency is not an editable art bank.
        // Re-entry above retains the existing mutable document and all its edits.
        return LevelObjectArt.initialize(MutableLevel.snapshot(library), library);
    }
}
