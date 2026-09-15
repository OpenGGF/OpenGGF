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
        long[] words = new long[4];
        for (int i = 0; i < 64; i++) {
            words[i / 16] |= (long) (pattern.getPixel(i % 8, i / 8) & 15) << ((i % 16) * 4);
        }
        return new PatternVersion(words[0], words[1], words[2], words[3]);
    }

    public static Pattern pattern(PatternVersion version) {
        Pattern pattern = new Pattern();
        long[] words = {version.a(), version.b(), version.c(), version.d()};
        for (int i = 0; i < 64; i++) {
            pattern.setPixel(i % 8, i / 8, (byte) ((words[i / 16] >>> ((i % 16) * 4)) & 15));
        }
        return pattern;
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
            frame.patternVersions().forEach((id, version) -> graphics.cachePatternTexture(pattern(version), id));
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
