package com.openggf.graphics.color;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TestDisplayColorConverter {

    @Test
    void limitedCanonicalRomColoursMatchNormalGpgxDigitalChannels() {
        // GPGX normal palette LUT: 3-bit channels become even 4-bit nibbles,
        // then MAKE_PIXEL replicates the nibble. This is not shadow/highlight.
        for (int r = 0; r < 8; r++) for (int g = 0; g < 8; g++) for (int b = 0; b < 8; b++) {
            var colour = new com.openggf.level.Palette.Color();
            colour.fromSegaFormat((b << 9) | (g << 5) | (r << 1));
            assertArrayEquals(new int[]{r * 34, g * 34, b * 34},
                    DisplayColorConverter.toRgbBytes(colour.r & 255, colour.g & 255, colour.b & 255,
                            DisplayColorProfile.RAW_RGB, DisplayColorExpansion.LIMITED));
        }
    }

    @Test
    void expansionIsIndependentOfProfilesAndRetainsIntermediateCreatorLevels() {
        int prior = -1;
        java.util.Set<Integer> levels = new java.util.HashSet<>();
        for (int value = 0; value < 256; value++) {
            for (DisplayColorProfile profile : DisplayColorProfile.values()) {
                int[] full = DisplayColorConverter.toRgbBytes(value, 255 - value, 173, profile);
                assertArrayEquals(full, DisplayColorConverter.toRgbBytes(value, 255 - value, 173,
                        profile, DisplayColorExpansion.FULL));
                int[] limited = DisplayColorConverter.toRgbBytes(value, 255 - value, 173,
                        profile, DisplayColorExpansion.LIMITED);
                for (int channel = 0; channel < 3; channel++) {
                    assertEquals(Math.round(full[channel] * 238.0 / 255), limited[channel]);
                    org.junit.jupiter.api.Assertions.assertTrue(limited[channel] <= 238);
                }
            }
            int limited = DisplayColorConverter.toRgbBytes(value, value, value,
                    DisplayColorProfile.RAW_RGB, DisplayColorExpansion.LIMITED)[0];
            org.junit.jupiter.api.Assertions.assertTrue(limited >= prior);
            prior = limited;
            levels.add(limited);
        }
        assertEquals(239, levels.size(), "raw creator values must not be quantized to eight ROM levels");
    }

    @Test
    void bothLimitedWritersPreserveBufferAndAlphaBoundaries() {
        for (DisplayColorProfile profile : DisplayColorProfile.values()) {
            ByteBuffer target = ByteBuffer.allocate(6);
            target.put((byte) 42);
            DisplayColorConverter.writeRgbBytes(146, 73, 219, profile, DisplayColorExpansion.LIMITED, target);
            assertEquals(4, target.position());
            int[] expected = DisplayColorConverter.toRgbBytes(146, 73, 219, profile, DisplayColorExpansion.LIMITED);
            assertEquals(42, target.get(0));
            for (int i = 0; i < 3; i++) assertEquals(expected[i], target.get(i + 1) & 255);
            int[] rgba = {-1, -1, -1, -1, 77};
            DisplayColorConverter.writeRgbBytes(146, 73, 219, profile, DisplayColorExpansion.LIMITED, rgba, 1);
            assertEquals(-1, rgba[0]);
            assertEquals(77, rgba[4]);
            assertArrayEquals(expected, java.util.Arrays.copyOfRange(rgba, 1, 4));
        }
        assertEquals(DisplayColorExpansion.FULL, DisplayColorExpansion.parse(null));
        assertEquals(DisplayColorExpansion.FULL, DisplayColorExpansion.parse("unknown"));
        assertEquals(DisplayColorExpansion.LIMITED, DisplayColorExpansion.parse(" limited "));
    }

    @Test
    void callerProvidedBufferMatchesAllocatingApiForEveryProfile() {
        for (DisplayColorProfile profile : DisplayColorProfile.values()) {
            ByteBuffer target = ByteBuffer.allocate(8);
            target.position(2);

            DisplayColorConverter.writeRgbBytes(146, 73, 219, profile, target);

            int[] expected = DisplayColorConverter.toRgbBytes(146, 73, 219, profile);
            assertEquals(5, target.position());
            assertEquals(expected[0], Byte.toUnsignedInt(target.get(2)));
            assertEquals(expected[1], Byte.toUnsignedInt(target.get(3)));
            assertEquals(expected[2], Byte.toUnsignedInt(target.get(4)));
        }
    }

    @Test
    void callerProvidedRgbaArrayMatchesAllocatingApiForWholePalette() {
        for (DisplayColorProfile profile : DisplayColorProfile.values()) {
            int[] target = new int[2 + 16 * 4 + 1];
            java.util.Arrays.fill(target, -1);

            for (int colorIndex = 0; colorIndex < 16; colorIndex++) {
                int offset = 2 + colorIndex * 4;
                int r = colorIndex * 17;
                int g = 255 - colorIndex * 13;
                int b = colorIndex * 11;
                target[offset + 3] = colorIndex == 0 ? 0 : 255;

                DisplayColorConverter.writeRgbBytes(r, g, b, profile, target, offset);

                int[] expected = DisplayColorConverter.toRgbBytes(r, g, b, profile);
                assertEquals(expected[0], target[offset]);
                assertEquals(expected[1], target[offset + 1]);
                assertEquals(expected[2], target[offset + 2]);
                assertEquals(colorIndex == 0 ? 0 : 255, target[offset + 3]);
            }
            assertEquals(-1, target[0]);
            assertEquals(-1, target[1]);
            assertEquals(-1, target[target.length - 1]);
        }
    }

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
