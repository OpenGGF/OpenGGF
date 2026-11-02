package com.openggf.game.sonic3k;

import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestSozRapelWireArtWord {
    @Test
    void pulleyLinksAndHandleResolveTheCompleteNativeArtWord() {
        HeadlessTestFixture.builder().withZoneAndAct(8, 1).build();
        var provider = (Sonic3kObjectArtProvider) GameModuleRegistry.getCurrent().getObjectArtProvider();
        var sheet = provider.getSheet(Sonic3kObjectArtKeys.SOZ_RAPEL_WIRE);
        assertNotNull(sheet);
        assertEquals(39, sheet.getFrameCount());
        var level = GameServices.level().getCurrentLevel();
        var pulley = sheet.getFrame(0x20).pieces().getFirst();
        // Map_SOZRapelWire word_4B31E: $FFC4 + $4411 = $43D5.
        assertEquals(2, pulley.paletteIndex());
        assertFalse(pulley.priority());
        assertFalse(pulley.hFlip());
        assertFalse(pulley.vFlip());
        assertEquals(2, pulley.widthTiles());
        assertEquals(2, pulley.heightTiles());
        for (int i = 0; i < 4; i++) {
            assertSame(level.getPattern(0x3D5 + i), sheet.getPatterns()[pulley.tileIndex() + i]);
        }
        for (int frame : new int[]{0, 0x21}) {
            var piece = sheet.getFrame(frame).pieces().getFirst();
            assertEquals(2, piece.paletteIndex());
            assertFalse(piece.priority());
            assertSame(level.getPattern(frame == 0 ? 0x427 : 0x429),
                    sheet.getPatterns()[piece.tileIndex()]);
        }
        assertEquals(0, sheet.getPaletteIndex(), "resolved palettes must not receive a second addition");
    }
}
