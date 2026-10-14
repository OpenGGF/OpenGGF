package com.openggf.game.sonic3k.ending;

import com.openggf.game.sonic3k.Sonic3kLevel;
import com.openggf.game.sonic3k.Sonic3kObjectArt;
import com.openggf.game.sonic3k.Sonic3kObjectArtProvider;
import com.openggf.graphics.PatternAtlasRange;
import com.openggf.level.Level;
import com.openggf.level.Pattern;
import com.openggf.level.objects.ObjectServices;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Draws native ending mappings against the level's current ROM-backed VRAM.
 * The cache is derived presentation data. Registered VRAM ranges let DMA
 * refresh in-place pixel writes; rewind replacement rebinds source patterns
 * before drawing. No art job is created here.
 */
public final class EndingSpriteRenderSupport {
    private static final Map<Level, Map<String, Binding>> BINDINGS = new WeakHashMap<>();

    private EndingSpriteRenderSupport() { }

    public static void draw(ObjectServices services, String key, int mappingAddress,
                            int tileBase, int paletteBase, int frame, int worldX, int worldY,
                            boolean flipX, boolean highPriority, int priorityBucket) {
        if (!(services.currentLevel() instanceof Sonic3kLevel level)) return;
        if (!(services.levelManager().getGameModule().getObjectArtProvider()
                instanceof Sonic3kObjectArtProvider provider)) return;
        String sheetKey = key + ":" + mappingAddress + ":" + tileBase + ":" + paletteBase;
        Map<String, Binding> bindings = BINDINGS.computeIfAbsent(level, ignored -> new HashMap<>());
        Binding binding = bindings.get(sheetKey);
        if (binding == null || provider.getSheet(sheetKey) == null || binding.changed(level)) {
            try {
                var art = new Sonic3kObjectArt(level, services.romReader());
                var sheet = art.buildLevelArtSheetFromRom(mappingAddress, tileBase, paletteBase);
                if (sheet == null) return;
                provider.refreshLevelArtSheet(sheetKey, sheet, art);
                binding = new Binding(art.getLastBuildStartTile(), art.getLastBuildTileCount());
                binding.observe(level);
                bindings.put(sheetKey, binding);
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        }
        var graphics = services.graphicsManager();
        if (!graphics.isGlInitialized()) return;
        var renderer = provider.getRenderer(sheetKey);
        if (!renderer.isReady()) provider.ensurePatternsCached(graphics, PatternAtlasRange.OBJECTS.base());
        int previousMask = graphics.getCurrentSpriteTileOcclusionPaletteMask();
        graphics.setCurrentSpriteSatBucket(priorityBucket);
        graphics.setCurrentSpriteHighPriority(highPriority);
        try {
            // Mapping-piece priority is retained. art_tile's high bit composes
            // through the ordinary sprite context, independently of SAT bucket.
            renderer.drawFrameIndex(frame, worldX, worldY, flipX, false, paletteBase);
        } finally {
            graphics.setCurrentSpriteTileOcclusionPaletteMask(previousMask);
        }
    }

    private static final class Binding {
        private final int firstTile;
        private final Pattern[] sources;

        private Binding(int firstTile, int count) {
            this.firstTile = firstTile;
            this.sources = new Pattern[count];
        }

        private Pattern source(Level level, int offset) {
            int tile = firstTile + offset;
            return tile >= 0 && tile < level.getPatternCount() ? level.getPattern(tile) : null;
        }

        private boolean changed(Level level) {
            for (int i = 0; i < sources.length; i++) if (sources[i] != source(level, i)) return true;
            return false;
        }

        private void observe(Level level) {
            for (int i = 0; i < sources.length; i++) sources[i] = source(level, i);
        }
    }
}
