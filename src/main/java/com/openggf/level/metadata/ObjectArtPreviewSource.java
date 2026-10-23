package com.openggf.level.metadata;

import com.openggf.level.Level;
import com.openggf.level.objects.ObjectSpriteSheet;
import java.util.Map;

/** ROM-backed static sheets for objects that normally draw directly against level tiles. */
public interface ObjectArtPreviewSource {
    Map<String, ObjectSpriteSheet> editorPreviewSheets(Level level);
}
