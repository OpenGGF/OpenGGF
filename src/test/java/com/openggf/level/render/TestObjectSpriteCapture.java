package com.openggf.level.render;

import static org.junit.jupiter.api.Assertions.*;

import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Pattern;
import com.openggf.level.PatternDesc;
import com.openggf.level.objects.ObjectSpriteSheet;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

class TestObjectSpriteCapture {
    @Test
    void observerCanReplayNativePoseWithoutRecursingAndScopeRestoresAfterFailure() {
        RecordingGraphics graphics = new RecordingGraphics();
        var sheet =
                new ObjectSpriteSheet(
                        new Pattern[] {new Pattern()},
                        List.of(
                                new SpriteMappingFrame(
                                        List.of(
                                                new SpriteMappingPiece(
                                                        -4, -8, 1, 1, 0, false, false, 0)))),
                        0,
                        1);
        var renderer = new PatternSpriteRenderer(sheet, graphics);
        renderer.ensurePatternsCached(graphics, 512);
        AtomicInteger observations = new AtomicInteger();
        ObjectSpriteCapture.observe(
                draw -> {
                    observations.incrementAndGet();
                    draw.drawPose(0);
                },
                () -> renderer.drawFrameIndex(0, 100, 80));
        assertEquals(1, observations.get());
        assertEquals(1, graphics.tiles);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ObjectSpriteCapture.observe(
                                draw -> {
                                    throw new IllegalArgumentException("capture failed");
                                },
                                () -> renderer.drawFrameIndex(0, 100, 80)));
        renderer.drawFrameIndex(0, 100, 80);
        assertEquals(2, graphics.tiles, "Failed observer must not remain installed");
    }

    @Test
    void satControlNotificationsPrecedeInactiveCpuGuardsAndScopesRestoreAfterRejection() {
        GraphicsManager graphics = new GraphicsManager();
        graphics.setHeadlessMode(true);
        var controls =
                List.<Runnable>of(
                        () ->
                                com.openggf.game.sonic3k.S3kSpriteMaskSupport.submitFrame4(
                                        graphics, 0, 0),
                        () -> {
                            try {
                                com.openggf.game.sonic3k.S3kSpriteMaskSupport.submitFrame(
                                        graphics, null, 0, 0, 0);
                            } catch (java.io.IOException impossible) {
                                throw new AssertionError(impossible);
                            }
                        },
                        graphics::requestSpriteMask,
                        () -> graphics.submitSpriteSatControlEntry(0, 0, 1, 1, 0));
        for (Runnable control : controls) {
            assertThrows(
                    UnsupportedOperationException.class,
                    () ->
                            ObjectSpriteCapture.observe(
                                    draw -> fail("Mask is not a mapping draw"), control));
            assertFalse(ObjectSpriteCapture.isObserving());
            assertFalse(graphics.isSpriteSatCollectionActive());
            control.run(); // Ordinary no-SAT behavior remains a no-op, including null-ROM helper.
        }
    }

    private static final class RecordingGraphics extends GraphicsManager {
        int tiles;

        @Override
        public void cachePatternTexture(Pattern pattern, int index) {}

        @Override
        public void renderPatternWithId(int index, PatternDesc descriptor, int x, int y) {
            tiles++;
        }
    }
}
