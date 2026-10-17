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
    void rangeChangesDoNotPublishPendingNativeLinesBeforeVInt() throws Exception {
        PaletteUploadPresentation.publishAndLatch(graphics);
        PaletteUploadPresentation.cacheLevelPalette(graphics, new Palette(), 2);
        graphics.setDisplayColorExpansion(com.openggf.graphics.color.DisplayColorExpansion.LIMITED);
        assertFalse(uploaded(2), "a display preference must not bypass a pending native publication");
        PaletteUploadPresentation.publishAndLatch(graphics);
        assertTrue(uploaded(2));
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
    void immediateScenePublicationReleasesARewindHeldFadeWithoutALevelLatch() {
        graphics.setPaletteFadePresentation(PaletteFadePresentation.Mode.FROM_WHITE, 1,
                PaletteFadePresentation.ALL_LINES);
        var displayed = PaletteUploadPresentation.captureDisplayedFade(graphics);
        for (int cycle = 0; cycle < 2; cycle++) {
            graphics.setPaletteFadePresentation(PaletteFadePresentation.Mode.FROM_WHITE, 2,
                    PaletteFadePresentation.ALL_LINES);
            PaletteUploadPresentation.restoreDisplayedFade(graphics, displayed);
            graphics.setPaletteFadePresentation(PaletteFadePresentation.Mode.FROM_WHITE, 3,
                    PaletteFadePresentation.ALL_LINES);
            assertFalse(graphics.isPaletteUploadLatched(), "a special scene has no level upload latch");
            assertEquals(1, PaletteUploadPresentation.captureDisplayedFade(graphics).steps(),
                    "the immediate restore redraw still uses its captured CRAM generation");
            assertTrue(PaletteUploadPresentation.captureDisplayedFade(graphics).pending());
            PaletteUploadPresentation.release(graphics);
            assertEquals(new PaletteUploadPresentation.DisplayedFade(
                    PaletteFadePresentation.Mode.FROM_WHITE, 3, PaletteFadePresentation.ALL_LINES, false),
                    PaletteUploadPresentation.captureDisplayedFade(graphics),
                    "the next scene publication releases the rewind hold even without a level latch");
            graphics.setPaletteFadePresentation(PaletteFadePresentation.Mode.FROM_WHITE, 4,
                    PaletteFadePresentation.ALL_LINES);
            assertEquals(4, PaletteUploadPresentation.captureDisplayedFade(graphics).steps(),
                    "subsequent immediate writes must not remain parked behind the old picture");
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void restoredOwnerRebindDoesNotInventAPendingFadeWhenTheDisplayedGenerationAlreadyMatches(boolean active) {
        if (active) graphics.setPaletteFadePresentation(PaletteFadePresentation.Mode.FROM_BLACK, 4,
                PaletteFadePresentation.LINES_1_TO_3);
        var displayed = PaletteUploadPresentation.captureDisplayedFade(graphics);
        assertFalse(displayed.pending());
        for (int cycle = 0; cycle < 2; cycle++) {
            PaletteUploadPresentation.release(graphics);
            graphics.setPaletteFadePresentation(PaletteFadePresentation.Mode.FROM_BLACK, active ? 5 : 4,
                    PaletteFadePresentation.LINES_1_TO_3);
            PaletteUploadPresentation.restoreDisplayedFade(graphics, displayed);
            if (active) graphics.setPaletteFadePresentation(PaletteFadePresentation.Mode.FROM_BLACK, 4,
                    PaletteFadePresentation.LINES_1_TO_3);
            else graphics.clearPaletteFadePresentation();
            assertEquals(displayed, PaletteUploadPresentation.captureDisplayedFade(graphics),
                    "restored owner rebind must preserve every captured fade field, including pending, cycle " + cycle);
        }
    }

    @Test
    void matchingRestoredOwnerRebindPreservesAnAlreadyCapturedPendingPublication() {
        graphics.setPaletteFadePresentation(PaletteFadePresentation.Mode.FROM_BLACK, 5,
                PaletteFadePresentation.LINES_1_TO_3);
        var captured = new PaletteUploadPresentation.DisplayedFade(PaletteFadePresentation.Mode.FROM_BLACK,
                4, PaletteFadePresentation.LINES_1_TO_3, true);
        PaletteUploadPresentation.restoreDisplayedFade(graphics, captured);
        graphics.setPaletteFadePresentation(PaletteFadePresentation.Mode.FROM_BLACK, 4,
                PaletteFadePresentation.LINES_1_TO_3);
        assertEquals(captured, PaletteUploadPresentation.captureDisplayedFade(graphics),
                "idempotent owner rebinding cannot erase the snapshot's genuine pending work");
        PaletteUploadPresentation.publishAndLatch(graphics);
        assertEquals(new PaletteUploadPresentation.DisplayedFade(PaletteFadePresentation.Mode.FROM_BLACK,
                4, PaletteFadePresentation.LINES_1_TO_3, false),
                PaletteUploadPresentation.captureDisplayedFade(graphics));
    }

    @Test
    void scenesOutsideTheLevelPipelineUploadImmediatelyWhileLatched() throws Exception {
        // Special stages, menus and loads write CRAM directly; a stale level latch must not hold them.
        PaletteUploadPresentation.publishAndLatch(graphics);
        graphics.cachePaletteTexture(new Palette(), 0);
        assertTrue(uploaded(0));
    }
}
