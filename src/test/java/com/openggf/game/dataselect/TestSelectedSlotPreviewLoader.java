package com.openggf.game.dataselect;

import com.openggf.graphics.RgbaImage;
import com.openggf.level.Palette;
import com.openggf.level.render.SpriteMappingPiece;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TestSelectedSlotPreviewLoader {
    private static final int WIDTH = 80;
    private static final int HEIGHT = 56;

    @Test
    void load_preservesAlphaAndQuantizationBoundariesAcrossTileEdges() {
        RgbaImage image = image(0x7FFFFFFF);
        image.setArgb(0, 0, 0x7FFF0000);
        image.setArgb(1, 0, 0x80000000);
        image.setArgb(2, 0, 0xFF240000);
        image.setArgb(3, 0, 0xFF250000);
        image.setArgb(4, 0, 0xFF480000);
        image.setArgb(5, 0, 0xFF490000);
        image.setArgb(6, 0, 0xFF6D0000);
        image.setArgb(7, 0, 0xFF6E0000);
        image.setArgb(8, 0, 0xFF900000);
        image.setArgb(0, 8, 0xFFFF0000);
        image.setArgb(79, 55, 0xFFFF0000);

        var preview = new SelectedSlotPreviewLoader().load(image);

        // Each visible color occurs twice: ties sort by the ascending 9-bit RGB key.
        assertPalette(preview.palette(), 0, 0, 0x240000, 0x490000, 0x6D0000, 0xFF0000);
        int[] expected = new int[WIDTH * HEIGHT];
        System.arraycopy(new int[]{0, 1, 1, 2, 2, 3, 3, 4, 4}, 0, expected, 0, 9);
        expected[8 * WIDTH] = 5;
        expected[expected.length - 1] = 5;
        assertPixels(preview, expected);
    }

    @Test
    void load_capsEqualFrequencyColorsByKeyAndResolvesNearestTiesToEarlierEntry() {
        RgbaImage image = image(0);
        int[] colors = {
                0xFF000000, 0xFF000025, 0xFF000049, 0xFF00006E,
                0xFF000092, 0xFF0000B7, 0xFF0000DB, 0xFF0000FF,
                0xFF002500, 0xFF002525, 0xFF002549, 0xFF00256E,
                0xFF002592, 0xFF0025B7, 0xFF0025DB, 0xFF0025FF
        };
        // Reverse encounter order to distinguish key ordering from scan order.
        for (int i = 0; i < colors.length; i++) {
            image.setArgb(i, 0, colors[colors.length - 1 - i]);
        }

        var preview = new SelectedSlotPreviewLoader().load(image);

        assertPalette(preview.palette(), 0,
                0, 0x000024, 0x000049, 0x00006D, 0x000092, 0x0000B6, 0x0000DB, 0x0000FF,
                0x002400, 0x002424, 0x002449, 0x00246D, 0x002492, 0x0024B6, 0x0024DB);
        int[] expected = new int[WIDTH * HEIGHT];
        System.arraycopy(new int[]{8, 15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1},
                0, expected, 0, colors.length);
        assertPixels(preview, expected);
    }

    @Test
    void load_selectsFrequentColorsBeforeKeysAndUsesThatOrderForNearestTies() {
        RgbaImage image = image(0);
        int[] colors = {
                0xFF002500, 0xFF000000, 0xFF000049, 0xFF250000,
                0xFF250025, 0xFF250049, 0xFF25006E, 0xFF250092,
                0xFF2500B7, 0xFF2500DB, 0xFF2500FF, 0xFF252500,
                0xFF252525, 0xFF252549, 0xFF25256E
        };
        int[] expected = new int[WIDTH * HEIGHT];
        // Green occurs three times, all other retained colors twice. The two omitted
        // colors each have two equally near retained colors, resolved by palette order.
        image.setArgb(0, 0, colors[0]);
        expected[0] = 1;
        for (int i = 0; i < colors.length; i++) {
            image.setArgb(1 + i * 2, 0, colors[i]);
            image.setArgb(2 + i * 2, 0, colors[i]);
            expected[1 + i * 2] = i + 1;
            expected[2 + i * 2] = i + 1;
        }
        image.setArgb(31, 0, 0xFF000025);
        expected[31] = 2;
        image.setArgb(32, 0, 0xFF252592);
        expected[32] = 8;

        var preview = new SelectedSlotPreviewLoader().load(image);

        assertPalette(preview.palette(), 0,
                0x002400, 0, 0x000049, 0x240000, 0x240024, 0x240049, 0x24006D,
                0x240092, 0x2400B6, 0x2400DB, 0x2400FF, 0x242400, 0x242424, 0x242449, 0x24246D);
        assertPixels(preview, expected);
    }

    @Test
    void load_allTransparentKeepsZeroIndicesAndBlankPalette() {
        var preview = new SelectedSlotPreviewLoader().load(image(0x7FFFFFFF));
        assertPalette(preview.palette(), 0);
        assertPixels(preview, new int[WIDTH * HEIGHT]);
    }

    @Test
    void frame_preservesCompleteRowMajorMappingAndSharedIdentity() {
        var preview = new SelectedSlotPreviewLoader().load(image(0));
        assertSame(SelectedSlotPreviewLoader.frame(), preview.frame());
        assertEquals(70, preview.frame().pieces().size());
        int[] xs = {-40, -32, -24, -16, -8, 0, 8, 16, 24, 32};
        int[] ys = {-28, -20, -12, -4, 4, 12, 20};
        int index = 0;
        for (int y : ys) {
            for (int x : xs) {
                assertEquals(new SpriteMappingPiece(x, y, 1, 1, 0x31B + index,
                        false, false, 2, false), preview.frame().pieces().get(index));
                index++;
            }
        }
        assertThrows(UnsupportedOperationException.class, () -> preview.frame().pieces().clear());
    }

    @Test
    void loadAll_preservesKeysAndDetachedPatternsAndPalette() {
        SelectedSlotPreviewLoader loader = new SelectedSlotPreviewLoader();
        RgbaImage source = image(0xFFFFFFFF);
        Map<Integer, RgbaImage> inputs = new LinkedHashMap<>();
        inputs.put(9, source);
        inputs.put(-2, source);
        var previews = loader.loadAll(inputs);
        assertEquals(inputs.keySet(), previews.keySet());
        assertNotSame(previews.get(9).patterns(), previews.get(-2).patterns());
        assertNotSame(previews.get(9).palette(), previews.get(-2).palette());
        source.setArgb(0, 0, 0);
        inputs.clear();
        assertEquals(1, previews.get(9).patterns()[0].getPixel(0, 0));
        assertPalette(previews.get(9).palette(), 0, 0xFFFFFF);
        assertThrows(UnsupportedOperationException.class, () -> previews.clear());
        assertTrue(loader.loadAll(null).isEmpty());
        assertTrue(loader.loadAll(Map.of()).isEmpty());
    }

    @Test
    void load_rejectsNullAndEveryWrongDimensionWithExistingException() {
        SelectedSlotPreviewLoader loader = new SelectedSlotPreviewLoader();
        assertInvalid(loader, null);
        for (int[] size : new int[][]{{79, 56}, {81, 56}, {80, 55}, {80, 57}, {56, 80}}) {
            RgbaImage invalid = new RgbaImage(size[0], size[1], new int[size[0] * size[1]]);
            assertInvalid(loader, invalid);
            assertThrows(IllegalArgumentException.class, () -> loader.loadAll(Map.of(0, invalid)));
        }
        Map<Integer, RgbaImage> nullImage = new LinkedHashMap<>();
        nullImage.put(0, null);
        assertThrows(IllegalArgumentException.class, () -> loader.loadAll(nullImage));
        Map<Integer, RgbaImage> nullKey = new LinkedHashMap<>();
        nullKey.put(null, image(0));
        assertThrows(NullPointerException.class, () -> loader.loadAll(nullKey));
    }

    private static void assertInvalid(SelectedSlotPreviewLoader loader, RgbaImage image) {
        assertEquals("S1 selected preview images must be 80x56",
                assertThrows(IllegalArgumentException.class, () -> loader.load(image)).getMessage());
    }

    private static RgbaImage image(int argb) {
        int[] pixels = new int[WIDTH * HEIGHT];
        Arrays.fill(pixels, argb);
        return new RgbaImage(WIDTH, HEIGHT, pixels);
    }

    private static void assertPalette(Palette palette, int... expected) {
        for (int i = 0; i < 16; i++) {
            var color = palette.getColor(i);
            int actual = ((color.r & 0xFF) << 16) | ((color.g & 0xFF) << 8) | (color.b & 0xFF);
            assertEquals(i < expected.length ? expected[i] : 0, actual, "palette entry " + i);
        }
    }

    private static void assertPixels(SelectedSlotPreviewLoader.LoadedPreview preview, int[] expected) {
        assertEquals(70, preview.patterns().length);
        int[] actual = new int[WIDTH * HEIGHT];
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                actual[y * WIDTH + x] = preview.patterns()[(y / 8) * 10 + x / 8].getPixel(x % 8, y % 8);
            }
        }
        assertArrayEquals(expected, actual);
    }
}
