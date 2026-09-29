package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestS3kLrzLavaFallArt {
    @Test
    void dropsUseTerrainLavaTiles() {
        HeadlessTestFixture.builder().withZoneAndAct(9, 0).build();
        var manager = GameServices.level();
        var sheet = manager.getObjectRenderManager().getSheet(Sonic3kObjectArtKeys.LRZ_LAVA_FALL);
        assertNotNull(sheet);
        assertEquals(2, sheet.getPaletteIndex());
        assertEquals(1, sheet.getFrameCount());
        var pieces = sheet.getFrame(0).pieces();
        assertEquals(4, pieces.size());
        for (int i = 0; i < 4; i++) {
            var piece = pieces.get(i);
            assertEquals(4, piece.widthTiles());
            assertEquals(4, piece.heightTiles());
            assertEquals(i < 2 ? 0 : 0x10, piece.tileIndex());
            for (int tile = piece.tileIndex(); tile < piece.tileIndex() + 16; tile++) {
                // loc_436EE writes make_art_tile($0D3,2,0), not ArtTile_LRZMisc.
                assertSame(manager.getCurrentLevel().getPattern(0xD3 + 0x48 + tile),
                        sheet.getPatterns()[tile], "wrong lava tile " + tile);
            }
        }
    }
}
