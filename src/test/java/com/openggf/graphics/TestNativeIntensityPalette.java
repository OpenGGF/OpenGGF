package com.openggf.graphics;

import com.openggf.graphics.color.DisplayColorConverter;
import com.openggf.graphics.color.DisplayColorExpansion;
import com.openggf.graphics.color.DisplayColorProfile;
import com.openggf.level.Palette;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestNativeIntensityPalette {
    @Test
    void everyNativeWordUsesTheShadowDacCodeBeforeDigitalExpansion() {
        int[] full = {0, 18, 36, 55, 73, 91, 109, 128};
        int[] limited = {0, 17, 34, 51, 68, 85, 102, 119};
        var fade = new PaletteFadePresentation();
        for (int r = 0; r < 8; r++) for (int g = 0; g < 8; g++) for (int b = 0; b < 8; b++) {
            var palette = new Palette();
            palette.getColor(1).fromSegaFormat((b << 9) | (g << 5) | (r << 1));
            var shadow = NativeIntensityPalette.shadow(palette, 2);
            assertEquals(rgb(full[r], full[g], full[b]), shadow.displayedRgb(1, fade,
                    DisplayColorProfile.RAW_RGB, DisplayColorExpansion.FULL));
            assertEquals(rgb(limited[r], limited[g], limited[b]), shadow.displayedRgb(1, fade,
                    DisplayColorProfile.RAW_RGB, DisplayColorExpansion.LIMITED));
            assertEquals(palette.red(1), shadow.red(1), "wrapper retains unfaded native source bytes");
        }
    }

    @Test
    void oddNativeChannelsAreNotHalvedAfterEightBitNormalRounding() {
        var palette = new Palette();
        palette.getColor(1).fromSegaFormat((6 << 9) | (3 << 5) | (2 << 1));
        var shadow = NativeIntensityPalette.shadow(palette, 0);
        assertEquals(rgb(36, 55, 109), shadow.displayedRgb(1, new PaletteFadePresentation(),
                DisplayColorProfile.RAW_RGB, DisplayColorExpansion.FULL));
        assertEquals(rgb(34, 51, 102), shadow.displayedRgb(1, new PaletteFadePresentation(),
                DisplayColorProfile.RAW_RGB, DisplayColorExpansion.LIMITED));
    }

    @Test
    void sourceLineFadeRunsInNativeCramBeforeShadowAndNeverMutatesTheOwner() {
        var palette = new Palette();
        palette.getColor(1).fromSegaFormat(0xEEE);
        var shadow = NativeIntensityPalette.shadow(palette, 2);
        var fade = new PaletteFadePresentation();
        fade.set(PaletteFadePresentation.Mode.TO_BLACK, 1, 1 << 2);
        assertEquals(rgb(109, 128, 128), shadow.displayedRgb(1, fade,
                DisplayColorProfile.RAW_RGB, DisplayColorExpansion.FULL));
        fade.set(PaletteFadePresentation.Mode.FROM_BLACK, 8, 1 << 2);
        assertEquals(rgb(0, 18, 128), shadow.displayedRgb(1, fade,
                DisplayColorProfile.RAW_RGB, DisplayColorExpansion.FULL));
        fade.set(PaletteFadePresentation.Mode.TO_BLACK, 21, 1 << 1);
        assertEquals(rgb(128, 128, 128), shadow.displayedRgb(1, fade,
                DisplayColorProfile.RAW_RGB, DisplayColorExpansion.FULL));
        assertEquals(255, palette.red(1) & 255);
    }

    @Test
    void analogHalfStepsPrecedeNtscMixingAndOutputGain() {
        int[] analogShadow = {0, 9, 18, 30, 42, 57, 72, 99};
        for (int channel = 0; channel < 8; channel++) {
            assertEquals(rgb(analogShadow[channel], analogShadow[channel], analogShadow[channel]),
                    DisplayColorConverter.toNativeShadowPackedRgb(channel, channel, channel,
                            DisplayColorProfile.MD_ANALOG, DisplayColorExpansion.FULL));
        }
        assertEquals(rgb(86, 35, 19), DisplayColorConverter.toNativeShadowPackedRgb(7, 3, 1,
                DisplayColorProfile.NTSC_SOFT, DisplayColorExpansion.FULL));
        assertEquals(rgb(80, 33, 18), DisplayColorConverter.toNativeShadowPackedRgb(7, 3, 1,
                DisplayColorProfile.NTSC_SOFT, DisplayColorExpansion.LIMITED));
    }

    @Test
    void recipeRejectsNestedIntensityAndNonNativeLineOrChannel() {
        var palette = new Palette();
        var shadow = NativeIntensityPalette.shadow(palette, 0);
        assertThrows(IllegalArgumentException.class, () -> NativeIntensityPalette.shadow(shadow, 0));
        assertThrows(IllegalArgumentException.class, () -> NativeIntensityPalette.shadow(palette, 4));
        assertThrows(IllegalArgumentException.class, () -> DisplayColorConverter.toNativeShadowPackedRgb(8, 0, 0,
                DisplayColorProfile.RAW_RGB, DisplayColorExpansion.FULL));
    }

    private static int rgb(int r, int g, int b) { return (r << 16) | (g << 8) | b; }
}
