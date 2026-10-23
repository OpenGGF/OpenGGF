package com.openggf.game.competition;

import com.openggf.graphics.PaletteFadePresentation;
import com.openggf.graphics.PaletteView;
import com.openggf.graphics.SpriteSatEntry;
import com.openggf.graphics.color.DisplayColorExpansion;
import com.openggf.graphics.color.DisplayColorProfile;
import com.openggf.level.Pattern;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestNativeMenuRaster {
    @Test void transparentHighPlaneRaisesLowBackgroundIntensityWithoutCoveringItsColor() {
        var patterns = patterns();
        var normal = picture(0x8000, 1, patterns, List.of(), true);
        assertEquals(5, normal.colorIndex(0, 0));
        assertFalse(normal.shadow(0, 0));
        var window = picture(0, 1, patterns, List.of(), true);
        assertEquals(5, window.colorIndex(0, 0));
        assertTrue(window.shadow(0, 0));
    }

    @Test void lowSpriteColorEOnTheFirstThreePalettesForcesNormalButColorDDoesNot() {
        for (int palette = 0; palette < 3; palette++) {
            var normal = picture(0, 1, patterns(), List.of(sprite(2, palette, false)), true);
            assertEquals(palette * 16 + 14, normal.colorIndex(0, 0));
            assertFalse(normal.shadow(0, 0));
        }
        var shadow = picture(0, 1, patterns(), List.of(sprite(3, 2, false)), true);
        assertEquals(0x2D, shadow.colorIndex(0, 0));
        assertTrue(shadow.shadow(0, 0));
        assertFalse(picture(0, 1, patterns(), List.of(sprite(3, 2, true)), true).shadow(0, 0));
    }

    @Test void highBackgroundHidesTheFirstOpaqueSpriteAndTheLaterHighSpriteCannotReplaceIt() {
        var picture = picture(0, 0x8001, patterns(),
                List.of(sprite(3, 0, false), sprite(2, 1, true)), true);
        assertEquals(5, picture.colorIndex(0, 0));
        assertFalse(picture.shadow(0, 0));
    }

    @Test void planePaletteThreeColorFIsOrdinaryCramAndSpriteOperatorsFailClosed() {
        assertEquals(0x3F, picture(0xE004, 0, patterns(), List.of(), true).colorIndex(0, 0));
        assertThrows(IllegalArgumentException.class, () ->
                picture(0, 1, patterns(), List.of(sprite(4, 3, false)), true));
        assertEquals(0x3F, picture(0, 1, patterns(), List.of(sprite(4, 3, false)), false).colorIndex(0, 0));
    }

    @Test void invisibleEntriesStillSpendTheSharedNativeScanlineBudget() {
        var sat = new ArrayList<SpriteSatEntry>();
        for (int i = 0; i < 20; i++) sat.add(SpriteSatEntry.of(-64, 0, 1, 1, 0, 0, false, false, false, false));
        sat.add(sprite(2, 0, true));
        assertEquals(5, picture(0, 1, patterns(), sat, false).colorIndex(0, 0));
    }

    @Test void nativePlaneCoordinatesWrapWithIndependentFlipsAndSpriteColumnMajorTiles() {
        var p = patterns();
        p[5].setPixel(7, 7, (byte) 9);
        var a = new NativeMenuRaster.Plane(2, 1, new int[]{0, 0x1805}, -8, 0);
        var b = new NativeMenuRaster.Plane(1, 1, new int[]{1}, 0, 0);
        var frame = NativeMenuRaster.compose(a, b, p, List.of(), false, 0);
        assertEquals(9, frame.colorIndex(0, 0));
        assertEquals(9, frame.colorIndex(16, 0));
        var multi = SpriteSatEntry.of(0, 0, 2, 2, 1, 0, false, false, false, false);
        var multiFrame = picture(0, 0, p, List.of(multi), false);
        assertEquals(5, multiFrame.colorIndex(0, 0));
        assertEquals(14, multiFrame.colorIndex(0, 8));
        assertEquals(13, multiFrame.colorIndex(8, 0));
        assertEquals(15, multiFrame.colorIndex(8, 8));
    }

    @Test void shadowUsesNativeDacBeforeExpansionAndFadeIsAppliedBeforeIntensity() {
        var frame = picture(0, 1, patterns(), List.of(), true);
        PaletteView white = new PaletteView() {
            public byte red(int index) { return (byte) 255; }
            public byte green(int index) { return (byte) 255; }
            public byte blue(int index) { return (byte) 255; }
        };
        PaletteView[] palettes = {white, white, white, white};
        var fade = new PaletteFadePresentation();
        assertEquals(0x808080, frame.rgb(palettes, fade, DisplayColorProfile.RAW_RGB, DisplayColorExpansion.FULL)[0]);
        assertEquals(0x777777, frame.rgb(palettes, fade, DisplayColorProfile.RAW_RGB, DisplayColorExpansion.LIMITED)[0]);
        fade.set(PaletteFadePresentation.Mode.FROM_BLACK, 7, 1);
        assertEquals(0x000080, frame.rgb(palettes, fade, DisplayColorProfile.RAW_RGB, DisplayColorExpansion.FULL)[0]);
        int[] detached = frame.indexedPixels(); detached[0] = 0;
        assertEquals(5, frame.colorIndex(0, 0));
    }

    private static NativeMenuRaster.Picture picture(int a, int b, Pattern[] patterns,
            List<SpriteSatEntry> entries, boolean intensity) {
        return NativeMenuRaster.compose(new NativeMenuRaster.Plane(1, 1, new int[]{a}, 0, 0),
                new NativeMenuRaster.Plane(1, 1, new int[]{b}, 0, 0), patterns, entries, intensity, 0);
    }
    private static SpriteSatEntry sprite(int pattern, int palette, boolean high) {
        return SpriteSatEntry.of(0, 0, 1, 1, pattern, palette, false, false, high, false);
    }
    private static Pattern[] patterns() {
        var result = new Pattern[6];
        int[] colors = {0, 5, 14, 13, 15, 0};
        for (int i = 0; i < result.length; i++) {
            result[i] = new Pattern();
            for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) result[i].setPixel(x, y, (byte) colors[i]);
        }
        return result;
    }
}
