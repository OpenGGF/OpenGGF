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
    /** Explicit project-library loading only. Never called by drawing or ordinary editor snapshots. */
    public static Level authoringLibrary(Level source) throws java.io.IOException {
        if (!(source instanceof AbstractLevel base)
                || !(base.objectPreviewProvider instanceof com.openggf.level.metadata.ObjectArtPreviewSource extra)) return source;
        Level detached = extra.editorAssetLibrary(source);
        if (detached != source) publish(detached, base.objectPreviewProvider);
        return detached;
    }
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
    /** First native-library copy selects its static art phase; authored copies retain their edited colors. */
    public static <T extends Level> T initialize(T target, Level source) {
        inherit(target, source);
        if (target instanceof AbstractLevel base && source instanceof AbstractLevel nativeSource)
            nativeSource.objectPreviewPalettes.forEach((line, palette) -> base.palettes[line] = palette.deepCopy());
        return target;
    }
    static void publish(Level level, ObjectArtProvider provider) throws java.io.IOException {
        if (!(level instanceof AbstractLevel base)) return;
        base.objectPreviewProvider = provider;
        var sheets = new java.util.LinkedHashMap<String, ObjectSpriteSheet>();
        var sources = new java.util.LinkedHashMap<String, ObjectSpriteSheet>();
        for (var key : provider.getRendererKeys()) {
            var sheet = provider.getSheet(key);
            if (sheet != null) sources.put(key, sheet);
        }
        if (provider instanceof com.openggf.level.metadata.ObjectArtPreviewSource extra) {
            sources.putAll(extra.editorPreviewSheets(level));
            base.objectPreviewPalettes = Map.copyOf(extra.editorPreviewPalettes(level));
        }
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
