package com.openggf.level.render;

import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Pattern;
import com.openggf.level.PatternDesc;
import com.openggf.level.objects.ObjectSpriteSheet;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Load-time art registration writes every tile of a sheet or DPLC bank inside one
 * pattern-atlas batch, so the atlas stages each page once instead of once per
 * 8x8 tile, and the batch closes before any draw can sample the new tiles.
 * GL-free: a recording {@link GraphicsManager} logs the call order.
 */
class TestArtRegistrationAtlasBatching {

    @Test
    void spriteSheetCachingWritesEveryTileInsideOneBatchBeforeDrawing() {
        Pattern[] patterns = {new Pattern(), new Pattern(), new Pattern()};
        RecordingGraphics graphics = new RecordingGraphics();
        PatternSpriteRenderer renderer = new PatternSpriteRenderer(new ObjectSpriteSheet(patterns,
                List.of(new SpriteMappingFrame(List.of(new SpriteMappingPiece(-4, -4, 1, 1, 0, false, false, 0)))),
                0, 1), graphics);

        renderer.ensurePatternsCached(graphics, 0x400);
        renderer.drawFrameIndex(0, 32, 48);

        assertEquals(List.of("begin", "cache 1024", "cache 1025", "cache 1026", "end", "draw 1024"),
                graphics.events);
    }

    @Test
    void dplcBankCachingWritesEveryTileInsideOneBatch() {
        RecordingGraphics graphics = new RecordingGraphics();
        DynamicPatternBank bank = new DynamicPatternBank(0x20, 3);

        bank.ensureCached(graphics);
        bank.ensureCached(graphics);

        assertEquals(List.of("begin", "cache 32", "cache 33", "cache 34", "end"), graphics.events);
    }

    private static final class RecordingGraphics extends GraphicsManager {
        final List<String> events = new ArrayList<>();

        @Override
        public boolean isGlInitialized() {
            return true;
        }

        @Override
        public void beginPatternAtlasBatch() {
            events.add("begin");
        }

        @Override
        public void endPatternAtlasBatch() {
            events.add("end");
        }

        @Override
        public void cachePatternTexture(Pattern pattern, int patternId) {
            events.add("cache " + patternId);
        }

        @Override
        public void renderPatternWithId(int patternId, PatternDesc desc, int x, int y) {
            events.add("draw " + patternId);
        }
    }
}
