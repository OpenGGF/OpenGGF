package com.openggf.game;

import com.openggf.level.Palette;
import com.openggf.game.preview.TitlePreviewProvider.Image;
import com.openggf.game.preview.TitlePreviewProvider.Sequence;
import com.openggf.game.preview.GeneratedTitlePreviewSequence;
import com.openggf.game.preview.TitlePreviewRaster;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import com.openggf.level.Pattern;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
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

        Image image = TitlePreviewRaster.composeTilemapImage(
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

        Image image = TitlePreviewRaster.composeTilemapImage(
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
        Image image = new Image(16, 16, new byte[16 * 16 * 4]);
        Pattern pattern = new Pattern();
        pattern.setPixel(0, 0, (byte) 1);
        pattern.setPixel(7, 7, (byte) 2);
        Palette palette = palette(0x00_00_00, 0xAA_00_00, 0x00_AA_00);
        SpriteMappingFrame frame = new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(0, 0, 1, 1, 0, false, false, 0, false)));

        TitlePreviewRaster.overlaySpriteFrame(image, new Pattern[] { pattern }, new Palette[] { palette },
                frame, 4, 5, 0);

        assertPixel(image, 4, 5, 0xAA, 0x00, 0x00, 0xFF);
        assertPixel(image, 11, 12, 0x00, 0xAA, 0x00, 0xFF);
        assertPixel(image, 3, 5, 0x00, 0x00, 0x00, 0x00);
    }

    @Test
    void overlaySpriteFrameClipped_hidesMaskedDestinationPixels() {
        Image image = new Image(16, 16, new byte[16 * 16 * 4]);
        Pattern pattern = new Pattern();
        pattern.setPixel(0, 0, (byte) 1);
        pattern.setPixel(0, 1, (byte) 1);
        Palette palette = palette(0x00_00_00, 0xAA_00_00);
        SpriteMappingFrame frame = new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(0, 0, 1, 1, 0, false, false, 0, false)));

        TitlePreviewRaster.overlaySpriteFrameClipped(image, new Pattern[] { pattern }, new Palette[] { palette },
                frame, 4, 5, 0, (x, y) -> y < 6);

        assertPixel(image, 4, 5, 0xAA, 0x00, 0x00, 0xFF);
        assertPixel(image, 4, 6, 0x00, 0x00, 0x00, 0x00);
    }

    @Test
    void scaleImageIntoNativeCanvas_usesAreaSamplingWhenDownscalingSmallText() throws Exception {
        Image source = new Image(2, 2, new byte[] {
                (byte) 0xFF, 0x00, 0x00, (byte) 0xFF,
                0x00, (byte) 0xFF, 0x00, (byte) 0xFF,
                0x00, 0x00, (byte) 0xFF, (byte) 0xFF,
                (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF
        });

        Image scaled = invokeScaleHelper(source, 1, 1);

        assertPixel(scaled, 0, 0, 0x80, 0x80, 0x80, 0xFF);
    }

    @Test
    void loadFor_returnsEmptyWhenRomPathIsMissing() {
        assertTrue(MasterTitleRomPreview.loadFor(
                MasterTitleScreen.GameEntry.SONIC_2,
                Path.of("definitely-missing-rom-file.gen")).isEmpty());
    }

    @Test
    void hasVisiblePixels_reportsWhetherImageContainsNonTransparentData() {
        Image blank = new Image(1, 1, new byte[] { 0, 0, 0, 0 });
        Image visible = new Image(1, 1, new byte[] { 1, 2, 3, 4 });

        assertFalse(blank.hasVisiblePixels());
        assertTrue(visible.hasVisiblePixels());
    }

    @Test
    void previewSequence_clampsToLastFrameAfterTimelineCompletes() {
        Image first = new Image(1, 1, new byte[] { 1, 0, 0, 1 });
        Image second = new Image(1, 1, new byte[] { 2, 0, 0, 1 });
        Sequence sequence =
                new GeneratedTitlePreviewSequence(1, 1, 1, tick -> Math.min(tick, 1), token -> token == 0 ? first : second);

        assertEquals(0, sequence.frameTokenAt(0));
        assertEquals(1, sequence.frameTokenAt(1));
        assertEquals(1, sequence.frameTokenAt(99));
        assertEquals(first, sequence.imageAt(0));
        assertEquals(second, sequence.imageAt(99));
    }

    @Test
    void sonic2PreviewPreservesOpaqueEmblemInteriorBehindCharacters() {
        MasterTitleScreen.GameEntry entry = MasterTitleScreen.GameEntry.SONIC_2;
        Path path = providedRomPath(entry);
        assumeTrue(path.toFile().isFile(), "ROM not present: " + path);

        Image image = MasterTitleRomPreview.loadFor(entry, path).orElseThrow();

        int opaqueGreenPixels = 0;
        for (int y = 32; y < 104; y++) {
            for (int x = 72; x < 248; x++) {
                int offset = ((y * image.width()) + x) * 4;
                byte[] rgba = image.rgba();
                if ((rgba[offset] & 0xFF) == 0x92
                        && (rgba[offset + 1] & 0xFF) == 0xFF
                        && (rgba[offset + 2] & 0xFF) == 0x00
                        && (rgba[offset + 3] & 0xFF) == 0xFF) {
                    opaqueGreenPixels++;
                }
            }
        }
        assertTrue(opaqueGreenPixels > 500,
                "the emblem interior should remain opaque behind Sonic and Tails, got " + opaqueGreenPixels);
    }

    @Test
    void sonic2PreviewRendersCopyrightRow() {
        MasterTitleScreen.GameEntry entry = MasterTitleScreen.GameEntry.SONIC_2;
        Path path = providedRomPath(entry);
        assumeTrue(path.toFile().isFile(), "ROM not present: " + path);

        Image image = MasterTitleRomPreview.loadFor(entry, path).orElseThrow();

        // Copyright line "@ 1992 SEGA" sits on tile row 26, columns 28-38 (x 224-311, y 208-215).
        int visible = 0;
        for (int y = 208; y < 216; y++) {
            for (int x = 224; x < 312; x++) {
                if ((image.rgba()[(y * image.width() + x) * 4 + 3] & 0xFF) != 0) {
                    visible++;
                }
            }
        }
        assertTrue(visible > 40, "copyright glyph pixels should render, got " + visible);
        int columnsLeft = 0;
        for (int y = 208; y < 216; y++) {
            for (int x = 0; x < 224; x++) {
                if ((image.rgba()[(y * image.width() + x) * 4 + 3] & 0xFF) != 0) {
                    columnsLeft++;
                }
            }
        }
        assertEquals(0, columnsLeft, "nothing else is drawn on the copyright row left of column 28");
    }

    @ParameterizedTest
    @EnumSource(MasterTitleScreen.GameEntry.class)
    void loadFor_decodesRealRomPreviewWhenSuppliedRomExists(MasterTitleScreen.GameEntry entry) {
            Path path = providedRomPath(entry);
            assumeTrue(path.toFile().isFile(), "ROM not present: " + path);

            Optional<Image> image = MasterTitleRomPreview.loadFor(entry, path);

            assertTrue(image.isPresent(), "preview should decode for " + entry.gameId);
            assertTrue(image.orElseThrow().hasVisiblePixels(), "preview should contain visible pixels");
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

    private static Image invokeScaleHelper(Image source,
                                                                 int scaledWidth,
                                                                 int scaledHeight) throws Exception {
        Method method = TitlePreviewRaster.class.getDeclaredMethod(
                "scaleImageIntoNativeCanvas", Image.class, int.class, int.class);
        method.setAccessible(true);
        return (Image) method.invoke(null, source, scaledWidth, scaledHeight);
    }

    private static void assertPixel(Image image,
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
    private static Path providedRomPath(MasterTitleScreen.GameEntry entry) {
        var rom = switch (entry) {
            case SONIC_1 -> com.openggf.tests.RomTestUtils.ensureSonic1RomAvailable();
            case SONIC_2 -> com.openggf.tests.RomTestUtils.ensureSonic2RomAvailable();
            case SONIC_3K -> com.openggf.tests.RomTestUtils.ensureSonic3kRomAvailable();
        };
        return rom == null ? Path.of(MasterTitleScreen.expectedRomFilename(entry)) : rom.toPath();
    }

}
