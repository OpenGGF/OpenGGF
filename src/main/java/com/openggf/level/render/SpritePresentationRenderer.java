package com.openggf.level.render;

import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.SpritePresentation;
import com.openggf.graphics.SpritePresentation.*;
import com.openggf.level.Pattern;
import com.openggf.level.PatternDesc;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/** Level-owned conversion between runtime ROM patterns and immutable graphics presentation values. */
public final class SpritePresentationRenderer {
    private SpritePresentationRenderer() { }

    public static Frame prepare(GraphicsManager graphics, int cameraX, int cameraY, Runnable producer) {
        return SpritePresentation.prepare(graphics, cameraX, cameraY, producer, descriptor -> {
            PatternDesc desc = (PatternDesc) descriptor;
            return new Attributes(desc.getPaletteIndex(), desc.getHFlip(), desc.getVFlip(), desc.getPriority());
        });
    }

    public static void bindPatternBank(GraphicsManager graphics, int base, Pattern[] patterns) {
        if (!SpritePresentation.isPreparing(graphics)) return;
        Map<Integer, PatternVersion> versions = new HashMap<>();
        for (int index = 0; index < patterns.length; index++) {
            versions.put(base + index, version(patterns[index]));
        }
        SpritePresentation.bindPatternVersions(graphics, versions);
    }

    /** Publish only this mapping's slots: unused bank capacity may overlap another sprite's bank. */
    public static void bindPatternBank(GraphicsManager graphics, int base, Pattern[] patterns,
                                       SpriteMappingFrame frame) {
        if (!SpritePresentation.isPreparing(graphics)) return;
        Map<Integer, PatternVersion> versions = new HashMap<>();
        for (SpriteMappingPiece piece : frame.pieces()) {
            int end = Math.min(patterns.length, piece.tileIndex() + piece.widthTiles() * piece.heightTiles());
            for (int index = Math.max(0, piece.tileIndex()); index < end; index++) {
                versions.put(base + index, version(patterns[index]));
            }
        }
        SpritePresentation.bindPatternVersions(graphics, versions);
    }

    private static PatternVersion version(Pattern pattern) {
        return new PatternVersion(packWord(pattern, 0), packWord(pattern, 16),
                packWord(pattern, 32), packWord(pattern, 48));
    }

    private static long packWord(Pattern pattern, int start) {
        long word = 0;
        for (int i = 0; i < 16; i++) {
            word |= (long) (pattern.getPixel((start + i) % 8, (start + i) / 8) & 15) << (i * 4);
        }
        return word;
    }

    public static Pattern pattern(PatternVersion version) {
        Pattern pattern = new Pattern();
        unpackPattern(version, pattern);
        return pattern;
    }

    private static void unpackPattern(PatternVersion version, Pattern pattern) {
        for (int group = 0; group < 4; group++) {
            long word = switch (group) {
                case 0 -> version.a();
                case 1 -> version.b();
                case 2 -> version.c();
                default -> version.d();
            };
            for (int i = 0; i < 16; i++) {
                pattern.setPixel(i % 8, group * 2 + i / 8, (byte) ((word >>> (i * 4)) & 15));
            }
        }
    }

    /** Resolve stable art addresses in the cache and restore the published generation of mutable virtual banks. */
    public static void draw(GraphicsManager graphics, Frame frame, int cameraX, int cameraY,
                            Predicate<Layer> visible) {
        boolean priority = graphics.isUseSpritePriorityShader();
        int mask = graphics.getCurrentSpriteTileOcclusionPaletteMask();
        boolean ghost = graphics.isGhostRenderEffectActive();
        float alpha = graphics.getGhostRenderAlpha();
        PatternDesc desc = new PatternDesc();
        graphics.beginPatternAtlasBatch();
        try {
            // Atlas upload copies pixels immediately; one draw-local scratch tile
            // avoids allocating a Pattern and arrays for every immutable DPLC slot.
            Pattern scratch = new Pattern();
            for (var entry : frame.patternVersions().entrySet()) {
                unpackPattern(entry.getValue(), scratch);
                graphics.cachePatternTexture(scratch, entry.getKey());
            }
        } finally { graphics.endPatternAtlasBatch(); }
        Tile previous = null;
        graphics.flushPatternBatch();
        try {
            int primitiveIndex = 0;
            for (int index = 0; index <= frame.tiles().size(); index++) {
                while (primitiveIndex < frame.primitives().size()
                        && frame.primitives().get(primitiveIndex).beforeTile() == index) {
                    var primitive = frame.primitives().get(primitiveIndex++);
                    if (visible.test(primitive.layer())) {
                        graphics.flushPatternBatch();
                        graphics.enqueueDebugLineState();
                        graphics.registerCommand(primitive.primitive().command(cameraX, cameraY));
                        graphics.enqueueDefaultShaderState();
                        previous = null;
                    }
                }
                if (index == frame.tiles().size()) break;
                Tile tile = frame.tiles().get(index);
                if (!visible.test(tile.layer())) continue;
                if (previous == null || previous.priorityShader() != tile.priorityShader()
                        || previous.occlusionMask() != tile.occlusionMask()
                        || previous.ghost() != tile.ghost() || previous.ghostAlpha() != tile.ghostAlpha()) {
                    graphics.flushPatternBatch();
                    graphics.setUseSpritePriorityShader(tile.priorityShader());
                    graphics.setCurrentSpriteTileOcclusionPaletteMask(tile.occlusionMask());
                    if (tile.ghost()) graphics.beginGhostRenderEffect(tile.ghostAlpha());
                    else graphics.endGhostRenderEffect();
                    graphics.beginPatternBatch();
                }
                previous = tile;
                graphics.setUseSpritePriorityShader(tile.priorityShader());
                graphics.setCurrentSpriteTileOcclusionPaletteMask(tile.occlusionMask());
                if (tile.ghost()) graphics.beginGhostRenderEffect(tile.ghostAlpha());
                else graphics.endGhostRenderEffect();
                desc.setPaletteIndex(tile.palette());
                desc.setHFlip(tile.hFlip());
                desc.setVFlip(tile.vFlip());
                desc.setPriority(tile.priority());
                float x = tile.x() + cameraX, y = tile.y() + cameraY;
                if (tile.width() == 8 && tile.height() == 8 && x == (int) x && y == (int) y) {
                    graphics.renderPatternWithId(tile.patternId(), desc, (int) x, (int) y);
                } else {
                    graphics.renderPatternWithIdScaled(tile.patternId(), desc, x, y, tile.width(), tile.height());
                }
            }
        } finally {
            graphics.flushPatternBatch();
            graphics.setUseSpritePriorityShader(priority);
            graphics.setCurrentSpriteTileOcclusionPaletteMask(mask);
            if (ghost) graphics.beginGhostRenderEffect(alpha); else graphics.endGhostRenderEffect();
        }
    }
}
