package com.openggf.game;

import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class TestMasterTitleRomPreview {

    @Test
    void composeTilemapImage_rendersPaletteIndexedPatterns() {
        Pattern pattern = new Pattern();
        pattern.setPixel(0, 0, (byte) 1);
        pattern.setPixel(7, 7, (byte) 2);
        Palette palette = palette(0x00_00_00, 0xFF_00_00, 0x00_FF_00);

        MasterTitleRomPreview.Image image = MasterTitleRomPreview.composeTilemapImage(
                new Pattern[] { pattern },
                new Palette[] { palette },
                new int[] { 0 },
                1,
                1);

        assertEquals(8, image.width());
        assertEquals(8, image.height());
        assertPixel(image, 0, 0, 0xFF, 0x00, 0x00, 0xFF);
        assertPixel(image, 7, 7, 0x00, 0xFF, 0x00, 0xFF);
        assertPixel(image, 1, 0, 0x00, 0x00, 0x00, 0x00);
    }

    @Test
    void composeTilemapImage_appliesHorizontalAndVerticalFlips() {
        Pattern pattern = new Pattern();
        pattern.setPixel(7, 7, (byte) 1);
        Palette palette = palette(0x00_00_00, 0xFF_FF_FF);

        MasterTitleRomPreview.Image image = MasterTitleRomPreview.composeTilemapImage(
                new Pattern[] { pattern },
                new Palette[] { palette },
                new int[] { 0x1800 },
                1,
                1);

        assertPixel(image, 0, 0, 0xFF, 0xFF, 0xFF, 0xFF);
        assertPixel(image, 7, 7, 0x00, 0x00, 0x00, 0x00);
    }

    @Test
    void overlaySpriteFrame_drawsMappingPiecesOverExistingImage() {
        MasterTitleRomPreview.Image image = new MasterTitleRomPreview.Image(16, 16, new byte[16 * 16 * 4]);
        Pattern pattern = new Pattern();
        pattern.setPixel(0, 0, (byte) 1);
        pattern.setPixel(7, 7, (byte) 2);
        Palette palette = palette(0x00_00_00, 0xAA_00_00, 0x00_AA_00);
        SpriteMappingFrame frame = new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(0, 0, 1, 1, 0, false, false, 0, false)));

        MasterTitleRomPreview.overlaySpriteFrame(image, new Pattern[] { pattern }, new Palette[] { palette },
                frame, 4, 5, 0);

        assertPixel(image, 4, 5, 0xAA, 0x00, 0x00, 0xFF);
        assertPixel(image, 11, 12, 0x00, 0xAA, 0x00, 0xFF);
        assertPixel(image, 3, 5, 0x00, 0x00, 0x00, 0x00);
    }

    @Test
    void loadFor_returnsEmptyWhenRomPathIsMissing() {
        assertTrue(MasterTitleRomPreview.loadFor(
                MasterTitleScreen.GameEntry.SONIC_2,
                Path.of("definitely-missing-rom-file.gen")).isEmpty());
    }

    @Test
    void hasVisiblePixels_reportsWhetherImageContainsNonTransparentData() {
        MasterTitleRomPreview.Image blank = new MasterTitleRomPreview.Image(1, 1, new byte[] { 0, 0, 0, 0 });
        MasterTitleRomPreview.Image visible = new MasterTitleRomPreview.Image(1, 1, new byte[] { 1, 2, 3, 4 });

        assertFalse(blank.hasVisiblePixels());
        assertTrue(visible.hasVisiblePixels());
    }

    @Test
    void previewSequence_clampsToLastFrameAfterTimelineCompletes() {
        MasterTitleRomPreview.Image first = new MasterTitleRomPreview.Image(1, 1, new byte[] { 1, 0, 0, 1 });
        MasterTitleRomPreview.Image second = new MasterTitleRomPreview.Image(1, 1, new byte[] { 2, 0, 0, 1 });
        MasterTitleRomPreview.PreviewSequence sequence =
                MasterTitleRomPreview.sequenceForTest(new MasterTitleRomPreview.Image[] { first, second });

        assertEquals(0, sequence.frameTokenAt(0));
        assertEquals(1, sequence.frameTokenAt(1));
        assertEquals(1, sequence.frameTokenAt(99));
        assertEquals(first, sequence.imageAt(0));
        assertEquals(second, sequence.imageAt(99));
    }

    @Test
    void sonic2PreviewTimelineStartsWithHiddenSonicAndSettlesToFinalFrame() {
        assertEquals(5, MasterTitleRomPreview.sonic2PreviewSonicFrameAt(0));
        assertEquals(96 + MasterTitleRomPreview.sonic2PreviewCharacterYOffset(),
                MasterTitleRomPreview.sonic2PreviewSonicYAt(0));

        assertEquals(5, MasterTitleRomPreview.sonic2PreviewSonicFrameAt(128));
        assertEquals(80 + MasterTitleRomPreview.sonic2PreviewCharacterYOffset(),
                MasterTitleRomPreview.sonic2PreviewSonicYAt(128));

        assertEquals(0x12, MasterTitleRomPreview.sonic2PreviewSonicFrameAt(288));
        assertEquals(24 + MasterTitleRomPreview.sonic2PreviewCharacterYOffset(),
                MasterTitleRomPreview.sonic2PreviewSonicYAt(288));
    }

    @Test
    void sonic1PreviewUsesSettledIdleLoopFrame() {
        assertEquals(6, MasterTitleRomPreview.sonic1PreviewSonicFrameIndex());
    }

    @Test
    void sonic1PreviewUsesNativeScreenAndRuntimePlaneAOffset() {
        assertEquals(320, MasterTitleRomPreview.sonic1PreviewWidth());
        assertEquals(224, MasterTitleRomPreview.sonic1PreviewHeight());
        assertEquals(24, MasterTitleRomPreview.sonic1PlaneAX());
        assertEquals(32, MasterTitleRomPreview.sonic1PlaneAY());
    }

    @Test
    void sonic2PreviewUsesCurvedLowerLogoOcclusion() {
        assertEquals(104, MasterTitleRomPreview.sonic2LogoOcclusionStartPixel(159));
        assertEquals(120, MasterTitleRomPreview.sonic2LogoOcclusionStartPixel(0));
        assertEquals(120, MasterTitleRomPreview.sonic2LogoOcclusionStartPixel(319));
    }

    @Test
    void sonic2PreviewOffsetsCharactersDownFromTitleOrigin() {
        assertEquals(8, MasterTitleRomPreview.sonic2PreviewCharacterYOffset());
    }

    @Test
    void sonic2PreviewDrawsHandsBehindLogoOcclusion() {
        assertTrue(MasterTitleRomPreview.sonic2PreviewDrawsHandsBehindLogoOcclusion());
    }

    @Test
    void sonic2PreviewDoesNotHideHandsBehindFullLogo() {
        assertFalse(MasterTitleRomPreview.sonic2PreviewDrawsHandsBehindFullLogoText());
    }

    @Test
    void sonic2PreviewClearsLogoChromaGreen() {
        MasterTitleRomPreview.Image image = new MasterTitleRomPreview.Image(2, 1, new byte[] {
                (byte) 0x92, (byte) 0xFF, 0x00, (byte) 0xFF,
                (byte) 0xFF, 0x00, 0x00, (byte) 0xFF
        });

        MasterTitleRomPreview.clearSonic2LogoChromaGreen(image);

        assertPixel(image, 0, 0, 0x00, 0x00, 0x00, 0x00);
        assertPixel(image, 1, 0, 0xFF, 0x00, 0x00, 0xFF);
    }

    @Test
    void sonic3kPreviewOmitsInteractiveMenuSelection() {
        assertFalse(MasterTitleRomPreview.sonic3kPreviewDrawsMenuSelection());
    }

    @Test
    void sonic3kPreviewLowersBannerBelowSonicFace() {
        assertEquals(112, MasterTitleRomPreview.sonic3kPreviewBannerY());
    }

    @Test
    void loadFor_decodesRealRomPreviewWhenSuppliedRomExists() {
        for (MasterTitleScreen.GameEntry entry : MasterTitleScreen.GameEntry.values()) {
            Path path = Path.of(MasterTitleScreen.expectedRomFilename(entry));
            assumeTrue(path.toFile().isFile(), "ROM not present: " + path);

            Optional<MasterTitleRomPreview.Image> image = MasterTitleRomPreview.loadFor(entry, path);

            assertTrue(image.isPresent(), "preview should decode for " + entry.gameId);
            assertTrue(image.orElseThrow().hasVisiblePixels(), "preview should contain visible pixels");
        }
    }

    private static Palette palette(int... colors) {
        Palette palette = new Palette();
        for (int i = 0; i < colors.length; i++) {
            int rgb = colors[i];
            palette.setColor(i, new Palette.Color(
                    (byte) ((rgb >> 16) & 0xFF),
                    (byte) ((rgb >> 8) & 0xFF),
                    (byte) (rgb & 0xFF)));
        }
        return palette;
    }

    private static void assertPixel(MasterTitleRomPreview.Image image,
                                    int x,
                                    int y,
                                    int r,
                                    int g,
                                    int b,
                                    int a) {
        int offset = ((y * image.width()) + x) * 4;
        byte[] rgba = image.rgba();
        assertEquals(r, rgba[offset] & 0xFF);
        assertEquals(g, rgba[offset + 1] & 0xFF);
        assertEquals(b, rgba[offset + 2] & 0xFF);
        assertEquals(a, rgba[offset + 3] & 0xFF);
    }
}
