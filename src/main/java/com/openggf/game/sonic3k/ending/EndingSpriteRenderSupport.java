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

    /**
     * The character-owned sprites among the native ending mappings; everything else (vehicles,
     * flames, propellers, the Master Emerald) has no surface and always draws natively.
     * Full-size cockpit: Map_SonicPlane frame $C is Tails in the seat (loc_5EB72,
     * sonic3k.asm:125171-125175), and Map_SonicPlaneEnding frame $B is Sonic in the seat for
     * Tails's route (loc_5EB88, sonic3k.asm:125178-125182).
     * Mid-size plane: byte_5ED0E (sonic3k.asm:125315-125320) selects the cockpit head $C/$D and
     * the rider $E/$F by Player_mode. Frames $16/$17 are that plane's exhaust flame
     * (loc_5EC4E, sonic3k.asm:125244-125262), not a character.
     */
    static com.openggf.game.EndingSpriteSet.Surface characterSurface(ObjectServices services, int mappingAddress, int frame) {
        if (mappingAddress == S3kEndingResources.MAP_ENDING_PLANE)
            return frame == 12 ? com.openggf.game.EndingSpriteSet.Surface.S3K_TORNADO_PILOT : null;
        if (mappingAddress == S3kEndingResources.MAP_SONIC_TAILS_POSE && frame <= 4)
            return S3kEndingCoordinator.state(services).progress() < 0
                    ? com.openggf.game.EndingSpriteSet.Surface.S3K_NORMAL_POSE
                    : com.openggf.game.EndingSpriteSet.Surface.S3K_POWERED_POSE;
        if (mappingAddress != S3kEndingResources.MAP_ENDING_PLANE_EXTRA) return null;
        return switch (frame) {
            case 11 -> com.openggf.game.EndingSpriteSet.Surface.S3K_TORNADO_PILOT;
            case 12, 13 -> com.openggf.game.EndingSpriteSet.Surface.S3K_MINI_TORNADO_PILOT;
            case 14, 15 -> com.openggf.game.EndingSpriteSet.Surface.S3K_PASSENGER;
            default -> null;
        };
    }

    public static void draw(ObjectServices services, String key, int mappingAddress,
                            int tileBase, int paletteBase, int frame, int worldX, int worldY,
                            boolean flipX, boolean highPriority, int priorityBucket) {
        draw(services,key,mappingAddress,tileBase,paletteBase,frame,worldX,worldY,flipX,false,highPriority,priorityBucket);
    }

    public static void draw(ObjectServices services, String key, int mappingAddress,
                            int tileBase, int paletteBase, int frame, int worldX, int worldY,
                            boolean flipX, boolean flipY, boolean highPriority, int priorityBucket) {
        var surface = characterSurface(services, mappingAddress, frame);
        if (surface != null && EndingCharacterPresentation.draw(services, surface, frame, worldX, worldY,
                flipX, flipY, highPriority, priorityBucket)) return;
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
            renderer.drawFrameIndex(frame, worldX, worldY, flipX, flipY, paletteBase);
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
