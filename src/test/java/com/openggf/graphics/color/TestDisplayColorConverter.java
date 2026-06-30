package com.openggf.graphics.color;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TestDisplayColorConverter {

    @Test
    void rawRgb_keepsPaletteColorBytesUnchanged() {
        assertArrayEquals(new int[] {146, 73, 219},
                DisplayColorConverter.toRgbBytes(146, 73, 219, DisplayColorProfile.RAW_RGB));
    }

    @Test
    void mdAnalog_usesDarkerMegaDriveRamp() {
        assertArrayEquals(new int[] {238, 238, 238},
                DisplayColorConverter.toRgbBytes(255, 255, 255, DisplayColorProfile.MD_ANALOG));
        assertArrayEquals(new int[] {126, 126, 126},
                DisplayColorConverter.toRgbBytes(146, 146, 146, DisplayColorProfile.MD_ANALOG));
        assertArrayEquals(new int[] {238, 0, 0},
                DisplayColorConverter.toRgbBytes(255, 0, 0, DisplayColorProfile.MD_ANALOG));
    }

    @Test
    void ntscSoft_usesAnalogRampAndMildDesaturation() {
        assertArrayEquals(new int[] {196, 18, 18},
                DisplayColorConverter.toRgbBytes(255, 0, 0, DisplayColorProfile.NTSC_SOFT));
        assertArrayEquals(new int[] {35, 214, 35},
                DisplayColorConverter.toRgbBytes(0, 255, 0, DisplayColorProfile.NTSC_SOFT));
        assertArrayEquals(new int[] {7, 7, 185},
                DisplayColorConverter.toRgbBytes(0, 0, 255, DisplayColorProfile.NTSC_SOFT));
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
