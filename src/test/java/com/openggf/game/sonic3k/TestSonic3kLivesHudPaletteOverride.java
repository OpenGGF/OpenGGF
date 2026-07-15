package com.openggf.game.sonic3k;

import com.openggf.level.Pattern;
import com.openggf.level.objects.HudStaticArt;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
class TestSonic3kLivesHudPaletteOverride {

    @Test
    void romArtMatchesTheRomIndependentMixedPaletteUseContract() throws Exception {
        Sonic3kObjectArtProvider provider = new Sonic3kObjectArtProvider();
        provider.loadArtForZone(0x00);
        HudStaticArt art = provider.getHudStaticArt();
        boolean[][] used = new boolean[4][16];
        art.livesFrame().pieces().forEach(piece -> {
            for (int tile = 0; tile < piece.widthTiles() * piece.heightTiles(); tile++) {
                Pattern pattern = art.patterns()[piece.tileIndex() + tile];
                for (int y = 0; y < Pattern.PATTERN_HEIGHT; y++) {
                    for (int x = 0; x < Pattern.PATTERN_WIDTH; x++) {
                        used[piece.paletteIndex()][Byte.toUnsignedInt(pattern.getPixel(x, y))] = true;
                    }
                }
            }
        });
        for (Pattern pattern : provider.getHudLivesNumbers()) {
            for (int y = 0; y < Pattern.PATTERN_HEIGHT; y++) {
                for (int x = 0; x < Pattern.PATTERN_WIDTH; x++) {
                    used[provider.getHudFlashPaletteLine()][Byte.toUnsignedInt(pattern.getPixel(x, y))] = true;
                }
            }
        }
        for (int line = 0; line < used.length; line++) {
            for (int color = 1; color < used[line].length; color++) {
                assertEquals(used[line][color], S3kHudPaletteUseContract.isReserved(line, color),
                        "contract mismatch at palette line " + line + ", color " + color);
            }
        }
    }

    @Test
    void loadArtForZone_exposesHudStaticArtWithMixedLivesPalettes() throws Exception {
        Sonic3kObjectArtProvider provider = new Sonic3kObjectArtProvider();

        assertNull(provider.getHudStaticArt());

        provider.loadArtForZone(0x00);

        HudStaticArt art = provider.getHudStaticArt();

        assertNotNull(art);
        assertEquals(provider.getHudTextPatterns().length + provider.getHudLivesPatterns().length,
                art.patterns().length);
        assertTrue(art.livesFrame().pieces().stream().anyMatch(piece -> piece.paletteIndex() == 0));
        assertTrue(art.livesFrame().pieces().stream().anyMatch(piece -> piece.paletteIndex() == 1));
    }
}
