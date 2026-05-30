package com.openggf.graphics.color;

import com.openggf.level.Palette;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TestDisplayColorConverter {

    @Test
    void rawRgb_keepsPaletteColorBytesUnchanged() {
        Palette.Color color = new Palette.Color((byte) 146, (byte) 73, (byte) 219);

        assertArrayEquals(new int[] {146, 73, 219},
                DisplayColorConverter.toRgbBytes(color, DisplayColorProfile.RAW_RGB));
    }

    @Test
    void mdAnalog_usesDarkerMegaDriveRamp() {
        Palette.Color white = new Palette.Color((byte) 255, (byte) 255, (byte) 255);
        Palette.Color mid = new Palette.Color((byte) 146, (byte) 146, (byte) 146);
        Palette.Color red = new Palette.Color((byte) 255, (byte) 0, (byte) 0);

        assertArrayEquals(new int[] {238, 238, 238},
                DisplayColorConverter.toRgbBytes(white, DisplayColorProfile.MD_ANALOG));
        assertArrayEquals(new int[] {126, 126, 126},
                DisplayColorConverter.toRgbBytes(mid, DisplayColorProfile.MD_ANALOG));
        assertArrayEquals(new int[] {238, 0, 0},
                DisplayColorConverter.toRgbBytes(red, DisplayColorProfile.MD_ANALOG));
    }

    @Test
    void ntscSoft_usesAnalogRampAndMildDesaturation() {
        Palette.Color red = new Palette.Color((byte) 255, (byte) 0, (byte) 0);
        Palette.Color green = new Palette.Color((byte) 0, (byte) 255, (byte) 0);
        Palette.Color blue = new Palette.Color((byte) 0, (byte) 0, (byte) 255);

        assertArrayEquals(new int[] {196, 18, 18},
                DisplayColorConverter.toRgbBytes(red, DisplayColorProfile.NTSC_SOFT));
        assertArrayEquals(new int[] {35, 214, 35},
                DisplayColorConverter.toRgbBytes(green, DisplayColorProfile.NTSC_SOFT));
        assertArrayEquals(new int[] {7, 7, 185},
                DisplayColorConverter.toRgbBytes(blue, DisplayColorProfile.NTSC_SOFT));
    }

    @Test
    void parse_acceptsCaseInsensitiveNamesAndFallsBackToRawRgb() {
        assertEquals(DisplayColorProfile.MD_ANALOG, DisplayColorProfile.parse("md_analog"));
        assertEquals(DisplayColorProfile.NTSC_SOFT, DisplayColorProfile.parse("NTSC_SOFT"));
        assertEquals(DisplayColorProfile.RAW_RGB, DisplayColorProfile.parse("banana"));
        assertEquals(DisplayColorProfile.RAW_RGB, DisplayColorProfile.parse(null));
    }

    @Test
    void next_cyclesProfilesInDisplayOrder() {
        assertEquals(DisplayColorProfile.MD_ANALOG, DisplayColorProfile.RAW_RGB.next());
        assertEquals(DisplayColorProfile.NTSC_SOFT, DisplayColorProfile.MD_ANALOG.next());
        assertEquals(DisplayColorProfile.RAW_RGB, DisplayColorProfile.NTSC_SOFT.next());
    }
}
