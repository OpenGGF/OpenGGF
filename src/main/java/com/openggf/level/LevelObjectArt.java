package com.openggf.level;

import com.openggf.game.ObjectArtProvider;
import com.openggf.level.objects.ObjectSpriteSheet;
import java.util.Map;

/**
 * Detached authoring-art catalogue published by the normal ROM load. Levels and editor
 * copies retain CPU sheets, not live renderers, admission queues, or gameplay objects.
 * This transient view is rebuilt from the source ROM when a saved project opens.
 */
public final class LevelObjectArt {
    private LevelObjectArt() { }
    public static Map<String, ObjectSpriteSheet> sheets(Level level) {
        return level instanceof AbstractLevel base ? base.objectPreviewSheets : Map.of();
    }
    public static java.util.Set<Integer> sourceIds(Level level) {
        return level instanceof AbstractLevel base ? base.objectPreviewSourceIds : java.util.Set.of();
    }
    public static <T extends Level> T inherit(T target, Level source) {
        if (target instanceof AbstractLevel base) {
            base.objectPreviewSheets = sheets(source);
            base.objectPreviewSourceIds = sourceIds(source);
        }
        return target;
    }
    static void publish(Level level, ObjectArtProvider provider) {
        if (!(level instanceof AbstractLevel base)) return;
        var sheets = new java.util.LinkedHashMap<String, ObjectSpriteSheet>();
        var sources = new java.util.LinkedHashMap<String, ObjectSpriteSheet>();
        for (var key : provider.getRendererKeys()) {
            var sheet = provider.getSheet(key);
            if (sheet != null) sources.put(key, sheet);
        }
        if (provider instanceof com.openggf.level.metadata.ObjectArtPreviewSource extra)
            sources.putAll(extra.editorPreviewSheets(level));
        for (var entry : sources.entrySet()) {
            var key = entry.getKey();
            var source = entry.getValue();
            var patterns = new Pattern[source.getPatterns().length];
            for (int i = 0; i < patterns.length; i++) {
                patterns[i] = new Pattern();
                if (source.getPatterns()[i] != null) patterns[i].copyFrom(source.getPatterns()[i]);
            }
            var frames = new java.util.ArrayList<com.openggf.level.render.SpriteMappingFrame>();
            for (int i = 0; i < source.getFrameCount(); i++)
                frames.add(new com.openggf.level.render.SpriteMappingFrame(java.util.List.copyOf(source.getFrame(i).pieces())));
            sheets.put(key, new ObjectSpriteSheet(patterns, java.util.List.copyOf(frames), source.getPaletteIndex(), source.getFrameDelay()));
        }
        base.objectPreviewSheets = Map.copyOf(sheets);
        base.objectPreviewSourceIds = level.getObjects().stream().filter(spawn -> spawn.objectKey() == null)
                .map(com.openggf.level.objects.ObjectSpawn::objectId).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
