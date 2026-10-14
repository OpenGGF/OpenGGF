package com.openggf;

import com.openggf.game.GameMode;
import com.openggf.game.MasterTitleEntry;
import com.openggf.game.MasterTitleScreen;
import com.openggf.graphics.RgbaImage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestSuspendedGame {

    @Test
    void thumbnailSamplesEachGamePixelFromTheScaledViewport() {
        // A 3x-scaled 2x2 picture: each game pixel is a 3x3 block of one colour.
        int[] colours = { 0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFFFFFFFF };
        RgbaImage viewport = new RgbaImage(6, 6, new int[36]);
        for (int y = 0; y < 6; y++) {
            for (int x = 0; x < 6; x++) viewport.setArgb(x, y, colours[(y / 3) * 2 + x / 3]);
        }

        SuspendedGame.Thumbnail thumbnail = SuspendedGame.thumbnail(viewport, 2, 2);

        assertEquals(2, thumbnail.width());
        assertEquals(2, thumbnail.height());
        byte[] rgba = thumbnail.rgba();
        for (int i = 0; i < 4; i++) {
            int argb = colours[i];
            assertEquals((byte) (argb >>> 16), rgba[i * 4], "red of pixel " + i);
            assertEquals((byte) (argb >>> 8), rgba[i * 4 + 1], "green of pixel " + i);
            assertEquals((byte) argb, rgba[i * 4 + 2], "blue of pixel " + i);
            assertEquals((byte) 0xFF, rgba[i * 4 + 3], "opaque");
        }
    }

    @Test
    void cardNamesTheZoneInALevelAndTheScreenElsewhere() {
        assertEquals("Emerald Hill, Act 2", SuspendedGame.location(GameMode.LEVEL, "EMERALD HILL", 2));
        assertEquals("Special Stage", SuspendedGame.location(GameMode.SPECIAL_STAGE, "EMERALD HILL", 1));
        assertEquals("", SuspendedGame.location(GameMode.LEVEL, null, 1));
        assertEquals("", SuspendedGame.location(GameMode.CREDITS_TEXT, "EMERALD HILL", 1));
    }

    @Test
    void cardIsTheStockGameItCameFrom() {
        MasterTitleEntry.Suspended card = SuspendedGame.card("s3k", "Special Stage");

        assertEquals(new MasterTitleEntry.Stock(MasterTitleScreen.GameEntry.SONIC_3K), card.game());
        assertEquals("s3k", card.gameId());
        assertEquals("Sonic 3K", card.menuLabel());
    }
}
