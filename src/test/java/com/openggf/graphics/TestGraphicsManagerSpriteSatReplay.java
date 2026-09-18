package com.openggf.graphics;

import com.openggf.game.session.SessionManager;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.EngineContext;
import com.openggf.level.Pattern;
import com.openggf.level.render.SpritePieceRenderer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestGraphicsManagerSpriteSatReplay {

    private GraphicsManager graphicsManager;

    @BeforeEach
    public void setUp() {
        GraphicsManager.destroyForReinit();
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        graphicsManager = GraphicsManager.getInstance();
        graphicsManager.initHeadless();
    }

    @AfterEach
    public void tearDown() {
        SessionManager.clear();
        GraphicsManager.destroyForReinit();
    }

    @Test
    public void spriteSatReplay_replaysBucketsBackToFrontWhileReversingSatOrderWithinEachBucket() throws Exception {
        graphicsManager.cachePatternTexture(createSolidPattern((byte) 1), 0);
        graphicsManager.cachePatternTexture(createSolidPattern((byte) 2), 1);
        graphicsManager.cachePatternTexture(createSolidPattern((byte) 3), 2);

        graphicsManager.beginSpriteSatCollection();
        graphicsManager.setCurrentSpriteSatBucket(2);
        graphicsManager.submitSpriteSatPiece(new SpritePieceRenderer.PreparedPiece(
                10, 20,
                1, 1,
                0, 0,
                1,
                false, false,
                false, true,
                SpriteMaskReplayRole.NORMAL,
                0, 1,
                0, 1,
                "first"));
        graphicsManager.setCurrentSpriteSatBucket(4);
        graphicsManager.submitSpriteSatPiece(new SpritePieceRenderer.PreparedPiece(
                20, 20,
                1, 1,
                1, 1,
                1,
                false, false,
                false, true,
                SpriteMaskReplayRole.NORMAL,
                0, 1,
                0, 1,
                "second"));
        graphicsManager.setCurrentSpriteSatBucket(2);
        graphicsManager.submitSpriteSatPiece(new SpritePieceRenderer.PreparedPiece(
                30, 20,
                1, 1,
                2, 2,
                2,
                false, false,
                false, true,
                SpriteMaskReplayRole.NORMAL,
                0, 1,
                0, 1,
                "third"));

        graphicsManager.endSpriteSatCollectionAndReplay();

        assertEquals(3, graphicsManager.commands.size());
        assertTrue(graphicsManager.commands.get(0) instanceof PatternRenderCommand);
        assertTrue(graphicsManager.commands.get(1) instanceof PatternRenderCommand);
        assertTrue(graphicsManager.commands.get(2) instanceof PatternRenderCommand);
        assertEquals(20f, getFloatField(graphicsManager.commands.get(0), "x"));
        assertEquals(30f, getFloatField(graphicsManager.commands.get(1), "x"));
        assertEquals(10f, getFloatField(graphicsManager.commands.get(2), "x"));
    }

    @Test
    public void spriteMaskReplay_cropsTilesToVisibleScanlines() throws Exception {
        graphicsManager.cachePatternTexture(createSolidPattern((byte) 1), 0);
        graphicsManager.cachePatternTexture(createSolidPattern((byte) 2), 1);

        graphicsManager.beginSpriteSatCollection();
        graphicsManager.requestSpriteMask();
        // Map_SpriteMask-style pair covering scanlines 20..27.
        graphicsManager.submitSpriteSatControlEntry(108, 20, 1, 1, 0x7C0);
        graphicsManager.submitSpriteSatControlEntry(100, 20, 1, 1, 0);
        graphicsManager.submitSpriteSatPiece(new SpritePieceRenderer.PreparedPiece(
                100, 16,
                1, 2,
                0, 0,
                1,
                false, false,
                false, false,
                SpriteMaskReplayRole.NORMAL,
                0, 1,
                0, 2,
                "masked"));

        graphicsManager.endSpriteSatCollectionAndReplay();

        assertEquals(2, graphicsManager.commands.size());
        float[] heights = new float[2];
        for (int i = 0; i < 2; i++) {
            Object command = graphicsManager.commands.get(i);
            assertTrue(command instanceof PatternRenderCommand);
            assertTrue(getBooleanField(command, "textureCoordinatesResolved"));
            heights[i] = getFloatField(command, "height");
            float v0 = getFloatField(command, "v0");
            float v1 = getFloatField(command, "v1");
            // Four of eight texel rows: half the atlas entry's V span.
            PatternAtlas.Entry entry = graphicsManager.getPatternAtlas().getEntry(i == 0 ? 1 : 0);
            assertEquals((entry.v1() - entry.v0()) / 2f, Math.abs(v0 - v1), 1e-6f);
        }
        assertEquals(4f, heights[0]);
        assertEquals(4f, heights[1]);
    }

    private static boolean getBooleanField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getBoolean(target);
    }

    private static Pattern createSolidPattern(byte color) {
        Pattern pattern = new Pattern();
        for (int y = 0; y < Pattern.PATTERN_HEIGHT; y++) {
            for (int x = 0; x < Pattern.PATTERN_WIDTH; x++) {
                pattern.setPixel(x, y, color);
            }
        }
        return pattern;
    }

    private static float getFloatField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getFloat(target);
    }
}


