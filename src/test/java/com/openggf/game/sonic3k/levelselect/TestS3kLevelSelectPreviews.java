package com.openggf.game.sonic3k.levelselect;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.sonic3k.dataselect.S3kDataSelectDataLoader;
import com.openggf.level.Pattern;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestS3kLevelSelectPreviews {
    @Test
    void previewsUseNativeSaveCardsAndProgressionOrder() throws Exception {
        try (Rom rom = new Rom()) {
            assertTrue(rom.open(RomTestUtils.ensureSonic3kRomAvailable().getPath()));
            var cards = new S3kDataSelectDataLoader(RomByteReader.fromRom(rom));
            cards.loadData();
            var loader = new Sonic3kLevelSelectDataLoader();
            var method = Sonic3kLevelSelectDataLoader.class.getDeclaredMethod(
                    "buildZonePreviews", S3kDataSelectDataLoader.class);
            method.setAccessible(true);
            Pattern[] previews = (Pattern[]) method.invoke(loader, cards);
            assertEquals(14 * 12, previews.length);
            assertEquals(7, Sonic3kLevelSelectConstants.ICON_TABLE[8]); // FBZ
            assertEquals(9, Sonic3kLevelSelectConstants.ICON_TABLE[19]); // LRZ2
            assertEquals(-1, Sonic3kLevelSelectConstants.ICON_TABLE[28]);
            for (int card = 0; card < 14; card++) {
                // Independent sprite-piece layout from Map_SaveScreen frame 0x17.
                int[] starts = {0, 16, 32, 40, 52, 64};
                int[] widths = {4, 4, 2, 4, 4, 2};
                for (int piece = 0; piece < 6; piece++) {
                    int height = piece < 3 ? 4 : 3;
                    for (int x = 0; x < widths[piece]; x++) {
                        for (int y = 0; y < height; y++) {
                            assertEquals(starts[piece] + x * height + y,
                                    Sonic3kLevelSelectDataLoader.saveCardTile(piece % 3 * 4 + x,
                                            (piece < 3 ? 0 : 4) + y));
                        }
                    }
                }
                Pattern[] source = cards.getSlotIconPatterns(card);
                assertEquals(source[0].getPixel(0, 0), previews[card * 12].getPixel(0, 0));
                assertEquals(source[69].getPixel(5, 5), previews[card * 12 + 11].getPixel(7, 7));
                assertEquals(Sonic3kLevelSelectConstants.LEVEL_SELECT_PICS_OFFSET + card * 12,
                        loader.getIconMappings()[card * 12]);
                assertNotNull(loader.getIconPalette(card));
            }
        }
    }
}
