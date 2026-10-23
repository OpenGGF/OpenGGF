package com.openggf.level;

import com.openggf.game.ObjectArtProvider;
import com.openggf.level.objects.ObjectSpriteSheet;
import java.util.Map;

/**
 * Detached authoring-art catalogue published by the normal ROM load. A weak sidecar
 * retains CPU sheets outside gameplay/rewind graphs, never live providers or objects.
 * This transient view is rebuilt from the source ROM when a saved project opens.
 */
public final class LevelObjectArt {
    private record Catalogue(Map<String, ObjectSpriteSheet> sheets, java.util.Set<Integer> sourceIds,
                             Map<Integer, Palette> palettes, boolean nativeLibrary) { }
    private static final Catalogue EMPTY = new Catalogue(Map.of(), java.util.Set.of(), Map.of(), false);
    // Values must not reference their Level keys (including through providers/closures).
    private static final Map<Level, Catalogue> CATALOGUES = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private LevelObjectArt() { }
    private static Catalogue catalogue(Level level) { return CATALOGUES.getOrDefault(level, EMPTY); }
    /** Explicit project-library loading only. Never called by drawing or ordinary editor snapshots. */
    public static Level authoringLibrary(Level source, ObjectArtProvider provider) throws java.io.IOException {
        if (!catalogue(source).nativeLibrary()
                || !(provider instanceof com.openggf.level.metadata.ObjectArtPreviewSource extra)) return source;
        Level detached = extra.editorAssetLibrary(source);
        if (detached != source) publish(detached, provider);
        return detached;
    }
    public static Map<String, ObjectSpriteSheet> sheets(Level level) {
        return catalogue(level).sheets();
    }
    public static java.util.Set<Integer> sourceIds(Level level) {
        return catalogue(level).sourceIds();
    }
    public static <T extends Level> T inherit(T target, Level source) {
        var data = catalogue(source);
        CATALOGUES.put(target, new Catalogue(data.sheets(), data.sourceIds(), Map.of(), false));
        return target;
    }
    /** First native-library copy selects its static art phase; authored copies retain their edited colors. */
    public static <T extends Level> T initialize(T target, Level source) {
        var data = catalogue(source);
        inherit(target, source);
        if (target instanceof AbstractLevel base && data.nativeLibrary())
            data.palettes().forEach((line, palette) -> base.palettes[line] = palette.deepCopy());
        return target;
    }
    static void publish(Level level, ObjectArtProvider provider) throws java.io.IOException {
        if (!(level instanceof AbstractLevel)) return;
        var sheets = new java.util.LinkedHashMap<String, ObjectSpriteSheet>();
        var sources = new java.util.LinkedHashMap<String, ObjectSpriteSheet>();
        for (var key : provider.getRendererKeys()) {
            var sheet = provider.getSheet(key);
            if (sheet != null) sources.put(key, sheet);
        }
        Map<Integer, Palette> palettes = Map.of();
        if (provider instanceof com.openggf.level.metadata.ObjectArtPreviewSource extra) {
            sources.putAll(extra.editorPreviewSheets(level));
            palettes = Map.copyOf(extra.editorPreviewPalettes(level));
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
        var sourceIds = level.getObjects().stream().filter(spawn -> spawn.objectKey() == null)
                .map(com.openggf.level.objects.ObjectSpawn::objectId).collect(java.util.stream.Collectors.toUnmodifiableSet());
        CATALOGUES.put(level, new Catalogue(Map.copyOf(sheets), sourceIds, palettes, true));
    }
}
