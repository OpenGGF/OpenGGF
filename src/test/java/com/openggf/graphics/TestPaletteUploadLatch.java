package com.openggf.graphics;

import com.openggf.level.Palette;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S3K V-int uploads {@code Normal_palette} to CRAM beside the sprite table, so a palette written
 * by frame N's logic is displayed with frame N's published sprites. Native CNZ1 screenshots show
 * the previous frame's palette RAM in 400 of 400 changed frames; the latch holds uploads until the
 * next publication.
 */
class TestPaletteUploadLatch {
    private GraphicsManager graphics;

    @BeforeEach
    void setUp() {
        GraphicsManager.destroyForReinit();
        graphics = GraphicsManager.getInstance();
        graphics.initHeadless();
    }

    @AfterEach
    void tearDown() {
        GraphicsManager.destroyForReinit();
    }

    @SuppressWarnings("unchecked")
    private boolean uploaded(int line) throws Exception {
        Field f = GraphicsManager.class.getDeclaredField("paletteTextureMap");
        f.setAccessible(true);
        return ((Map<String, Integer>) f.get(graphics)).containsKey("palette_" + line);
    }

    @Test
    void latchedWritesReachTheGpuAtTheNextPublication() throws Exception {
        PaletteUploadPresentation.publishAndLatch(graphics);
        PaletteUploadPresentation.cacheLevelPalette(graphics, new Palette(), 2);
        assertFalse(uploaded(2), "a latched write waits for the next V-int publication");
        PaletteUploadPresentation.publishAndLatch(graphics);
        assertTrue(uploaded(2), "the publication uploads the pending line");
    }

    @Test
    void releaseUploadsPendingLinesAndRestoresImmediateUploads() throws Exception {
        PaletteUploadPresentation.publishAndLatch(graphics);
        PaletteUploadPresentation.cacheLevelPalette(graphics, new Palette(), 1);
        PaletteUploadPresentation.release(graphics);
        assertTrue(uploaded(1), "release flushes pending lines");
        graphics.cachePaletteTexture(new Palette(), 3);
        assertTrue(uploaded(3), "without a latch uploads are immediate");
    }

    @Test
    void scenesOutsideTheLevelPipelineUploadImmediatelyWhileLatched() throws Exception {
        // Special stages, menus and loads write CRAM directly; a stale level latch must not hold them.
        PaletteUploadPresentation.publishAndLatch(graphics);
        graphics.cachePaletteTexture(new Palette(), 0);
        assertTrue(uploaded(0));
    }
}
