package com.openggf.level.objects;

import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The dial takes its inks from the game's HUD art and palette and empties clockwise from twelve. */
class TestHudHungerDial {
    private static Pattern[] tiles(int count, int main, int second) {
        var tiles = new Pattern[count];
        for (int i = 0; i < tiles.length; i++) {
            tiles[i] = new Pattern();
            for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++)
                tiles[i].setPixel(x, y, (byte) (x < 5 ? main : x < 7 ? second : 0));
        }
        return tiles;
    }

    private static int pixel(Pattern[] frames, int frame, int x, int y) {
        return frames[frame * 4 + (y / 8) * 2 + x / 8].getPixel(x % 8, y % 8);
    }

    @Test
    void ordinaryInksAreTheLabelFaceOutlinedByTheDigitShadow() {
        // S2-like: white digits (6) shadowed in black (1), yellow label ink (12).
        assertArrayEquals(new int[] { 12, 1 }, HudHungerDial.ordinaryInks(tiles(10, 12, 1), tiles(20, 6, 1)));
        // S3K-like: white digits (1) with a dark shadow (15); yellow label (5).
        assertArrayEquals(new int[] { 5, 15 }, HudHungerDial.ordinaryInks(tiles(10, 5, 14), tiles(20, 1, 15)));
        assertArrayEquals(new int[] { 6, 1 }, HudHungerDial.ordinaryInks(null, tiles(20, 6, 1)), "digits alone");
        assertNull(HudHungerDial.ordinaryInks(null, new Pattern[] { new Pattern() }), "no ink, no dial");
    }

    @Test
    void warningInksAreTheLinesReddestAndDarkestEntries() {
        var line = new Palette();
        for (int i = 0; i < 16; i++) { line.colors[i].r = (byte) 0x92; line.colors[i].g = (byte) 0x92; line.colors[i].b = (byte) 0x92; }
        line.colors[0].r = 0; line.colors[0].g = 0; line.colors[0].b = 0; // transparent slot is never chosen
        line.colors[6].r = (byte) 0xFF; line.colors[6].g = 0; line.colors[6].b = 0;
        line.colors[7].r = (byte) 0x92; line.colors[7].g = 0; line.colors[7].b = 0;
        line.colors[15].r = 0x24; line.colors[15].g = 0x24; line.colors[15].b = 0x24;
        assertArrayEquals(new int[] { 6, 15 }, HudHungerDial.warningInks(line));
        assertNull(HudHungerDial.warningInks(null));
        assertEquals(15, HudHungerDial.darkest(line, 5, 9), "the ordinary outline is the text line's darkest entry");
        assertEquals(9, HudHungerDial.darkest(null, 5, 9), "without a palette the digit shadow remains");
    }

    @Test
    void framesEmptyClockwiseAndKeepTheOutline() {
        var frames = HudHungerDial.build(0xE, 0x3);
        assertEquals(HudHungerDial.SET_SIZE, frames.length);
        assertEquals(0xE, pixel(frames, 16, 11, 4), "a full dial is all face");
        assertEquals(0xE, pixel(frames, 16, 4, 11));
        // Three quarters left: the first quarter after twelve o'clock (upper right) has emptied.
        assertEquals(0, pixel(frames, 12, 11, 4));
        assertEquals(0xE, pixel(frames, 12, 4, 11));
        assertEquals(0, pixel(frames, 0, 4, 11), "an empty dial has no face");
        for (int frame : new int[] { 0, 8, 16 }) {
            assertEquals(0x3, pixel(frames, frame, 8, 1), "outline at twelve o'clock");
            assertEquals(0x3, pixel(frames, frame, 1, 8), "outline at nine o'clock");
            assertEquals(0, pixel(frames, frame, 0, 0), "corners stay transparent");
        }
    }
}
