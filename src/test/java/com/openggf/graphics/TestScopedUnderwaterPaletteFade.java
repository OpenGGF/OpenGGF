package com.openggf.graphics;

import com.openggf.level.Palette;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

/** Draw-only reveals recompose published water colours without admitting pending native writes. */
class TestScopedUnderwaterPaletteFade {
    private GraphicsManager graphics;
    private CapturedUploads uploads;

    @BeforeEach
    void setUp() {
        RenderContext.reset();
        graphics = new GraphicsManager();
        graphics.initHeadless();
        uploads = new CapturedUploads();
        graphics.paletteTextures().setUnderwaterPaletteUploadOps(uploads);
    }

    @AfterEach
    void tearDown() {
        graphics.clearPaletteTextures();
        RenderContext.reset();
    }

    @ParameterizedTest
    @CsvSource({"FROM_BLACK,0", "TO_BLACK,21"})
    void revealUploadsFrozenWaterThenRestoresBlackWithoutPublishingPendingWriters(
            PaletteFadePresentation.Mode mode, int steps) {
        var pending = prepareBlackWater(mode, steps);
        Integer texture = graphics.getUnderwaterPaletteTextureId();

        PaletteUploadPresentation.withoutDisplayedFade(graphics, () -> {
            assertEquals(2, uploads.recorder.uploadCount(), "the reveal uploads before drawing starts");
            assertPublishedWaterColours();
            assertEquals(PaletteFadePresentation.Mode.NONE,
                    PaletteUploadPresentation.captureDisplayedFade(graphics).mode());
            assertPendingWater(pending);
        });

        assertEquals(3, uploads.recorder.uploadCount(), "the native black texture is restored before returning");
        assertUploadedColour(0, 0, 0, 0);
        assertUploadedColour(1, 0, 0, 0);
        assertEquals(texture, graphics.getUnderwaterPaletteTextureId(), "the scope keeps the resident texture");
        assertEquals(pending.fade(), PaletteUploadPresentation.captureDisplayedFade(graphics));
        assertPendingWater(pending);

        PaletteUploadPresentation.publishAndLatch(graphics);
        assertEquals(4, uploads.recorder.uploadCount(), "only native publication admits the changed CPU colours");
        assertUploadedColour(0, 219, 36, 73);
        assertUploadedColour(1, 36, 219, 109);
        assertNull(graphics.paletteTextures().pendingUnderwaterPalettes());
        assertNull(graphics.paletteTextures().pendingUnderwaterLine0());
        assertFalse(PaletteUploadPresentation.captureDisplayedFade(graphics).pending());
        assertTrue(graphics.paletteTextures().isPaletteUploadLatched());
    }

    @Test
    void nestedRevealKeepsOuterWaterVisibleAndRestoresNativeWaterWhenDrawingThrows() {
        var pending = prepareBlackWater(PaletteFadePresentation.Mode.FROM_BLACK, 0);
        var failure = new IllegalStateException("aborted draw");

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> PaletteUploadPresentation.withoutDisplayedFade(graphics, () -> {
                    assertPublishedWaterColours();
                    assertSame(failure, assertThrows(IllegalStateException.class,
                            () -> PaletteUploadPresentation.withoutDisplayedFade(graphics, () -> {
                                assertPublishedWaterColours();
                                throw failure;
                            })));
                    assertEquals(2, uploads.recorder.uploadCount(), "nested NONE does not upload another generation");
                    assertPublishedWaterColours();
                    assertPendingWater(pending);
                    throw failure;
                })));

        assertEquals(3, uploads.recorder.uploadCount());
        assertUploadedColour(0, 0, 0, 0);
        assertUploadedColour(1, 0, 0, 0);
        assertEquals(pending.fade(), PaletteUploadPresentation.captureDisplayedFade(graphics));
        assertPendingWater(pending);
        PaletteUploadPresentation.publishAndLatch(graphics);
        assertUploadedColour(0, 219, 36, 73);
        assertUploadedColour(1, 36, 219, 109);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void uploadFailureRestoresNativeHoldAndLeavesWaterRestorationRetryable(boolean failAtEntry) throws Exception {
        var pending = prepareBlackWater(PaletteFadePresentation.Mode.FROM_BLACK, 0);
        var failure = new IllegalStateException("failed texture upload");
        int[] draws = {0};
        if (failAtEntry) {
            uploads.nextFailure = failure;
        }

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> PaletteUploadPresentation.withoutDisplayedFade(graphics, () -> {
                    draws[0]++;
                    assertPublishedWaterColours();
                    uploads.nextFailure = failure;
                })));

        assertEquals(failAtEntry ? 0 : 1, draws[0]);
        assertEquals(2, uploads.recorder.uploadCount(), "failed uploads never count as successful residency");
        assertEquals(pending.fade(), PaletteUploadPresentation.captureDisplayedFade(graphics));
        assertFalse((Boolean) PaletteTextureTestAccess.get(graphics, "displayedPaletteFadeHeld"),
                "an upload failure must not retain the scope's temporary rewind hold");
        assertPendingWater(pending);
        if (!failAtEntry) {
            assertPublishedWaterColours();
            // The failed restore must leave residency dirty for the next draw boundary.
            graphics.paletteTextures().refreshScopedUnderwaterResidency();
            assertEquals(3, uploads.recorder.uploadCount());
        }
        assertUploadedColour(0, 0, 0, 0);
        assertUploadedColour(1, 0, 0, 0);
        assertPendingWater(pending);

        PaletteUploadPresentation.publishAndLatch(graphics);
        assertUploadedColour(0, 219, 36, 73);
        assertUploadedColour(1, 36, 219, 109);
    }

    private PendingWater prepareBlackWater(PaletteFadePresentation.Mode mode, int steps) {
        Palette[] water = {palette(72, 109, 182), palette(144, 72, 36), new Palette(), new Palette()};
        Palette normal = palette(144, 218, 255);
        graphics.setPaletteFadePresentation(mode, steps, PaletteFadePresentation.ALL_LINES);
        graphics.cacheUnderwaterPaletteTexture(water, normal);
        assertEquals(1, uploads.recorder.uploadCount());
        assertUploadedColour(0, 0, 0, 0);
        assertUploadedColour(1, 0, 0, 0);
        PaletteUploadPresentation.publishAndLatch(graphics);

        // Mutate the same palette objects after publication: only the detached RGB
        // basis is eligible for a draw-only reveal. The native clear also stays pending.
        water[0].setColor(1, color(219, 36, 73));
        water[1].setColor(1, color(36, 219, 109));
        normal.setColor(1, color(255, 36, 73));
        graphics.clearPaletteFadePresentation();
        graphics.cacheUnderwaterPaletteTexture(water, normal);
        assertEquals(1, uploads.recorder.uploadCount());
        var fade = PaletteUploadPresentation.captureDisplayedFade(graphics);
        assertEquals(mode, fade.mode());
        assertTrue(fade.pending());
        return new PendingWater(water, normal, fade);
    }

    private void assertPendingWater(PendingWater pending) {
        assertTrue(graphics.paletteTextures().isPaletteUploadLatched());
        assertSame(pending.rows(), graphics.paletteTextures().pendingUnderwaterPalettes());
        assertSame(pending.normalLine0(), graphics.paletteTextures().pendingUnderwaterLine0());
        assertEquals(PaletteFadePresentation.Mode.NONE, graphics.getPaletteFadePresentation().mode(),
                "the native clear request is unchanged by the draw scope");
        assertTrue(PaletteUploadPresentation.captureDisplayedFade(graphics).pending());
    }

    private void assertPublishedWaterColours() {
        assertUploadedColour(0, 72, 109, 182);
        assertUploadedColour(1, 144, 72, 36);
    }

    private void assertUploadedColour(int line, int red, int green, int blue) {
        int offset = (line * 16 + 1) * 4;
        assertArrayEquals(new int[]{red, green, blue, 255}, new int[]{
                Byte.toUnsignedInt(uploads.lastUpload[offset]),
                Byte.toUnsignedInt(uploads.lastUpload[offset + 1]),
                Byte.toUnsignedInt(uploads.lastUpload[offset + 2]),
                Byte.toUnsignedInt(uploads.lastUpload[offset + 3])});
    }

    private static Palette palette(int red, int green, int blue) {
        Palette palette = new Palette();
        palette.setColor(1, color(red, green, blue));
        return palette;
    }

    private static Palette.Color color(int red, int green, int blue) {
        return new Palette.Color((byte) red, (byte) green, (byte) blue);
    }

    private record PendingWater(Palette[] rows, Palette normalLine0, PaletteUploadPresentation.DisplayedFade fade) { }

    /** Count with the shared recorder and retain the bytes handed to that upload boundary. */
    private static final class CapturedUploads implements UnderwaterPaletteUploadOps {
        private final RecordingUnderwaterPaletteUploadOps recorder = new RecordingUnderwaterPaletteUploadOps();
        private byte[] lastUpload;
        private RuntimeException nextFailure;

        @Override
        public int createTexture() {
            return recorder.createTexture();
        }

        @Override
        public void configureTexture(int textureId) {
            recorder.configureTexture(textureId);
        }

        @Override
        public void uploadTexture(int textureId, int totalLines, ByteBuffer rgbaBytes) {
            if (nextFailure != null) {
                RuntimeException failure = nextFailure;
                nextFailure = null;
                throw failure;
            }
            recorder.uploadTexture(textureId, totalLines, rgbaBytes);
            assertEquals(16 * totalLines * 4, rgbaBytes.remaining());
            lastUpload = new byte[rgbaBytes.remaining()];
            rgbaBytes.duplicate().get(lastUpload);
        }
    }
}
