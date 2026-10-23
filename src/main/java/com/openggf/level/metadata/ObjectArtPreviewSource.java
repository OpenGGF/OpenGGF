package com.openggf.level.metadata;

import com.openggf.level.Level;
import com.openggf.level.objects.ObjectSpriteSheet;
import java.util.Map;

/** ROM-backed static sheets for objects that normally draw directly against level tiles. */
public interface ObjectArtPreviewSource {
    /** Optional complete detached source view, before intro/front-end residency overlays. */
    default Level editorAssetLibrary(Level level) throws java.io.IOException { return level; }
    default Map<String, ObjectSpriteSheet> editorPreviewSheets(Level level) throws java.io.IOException { return Map.of(); }
    /** Static authoring phase, applied only when first copying a native library, never on reopen. */
    default Map<Integer, com.openggf.level.Palette> editorPreviewPalettes(Level level) throws java.io.IOException { return Map.of(); }
}
