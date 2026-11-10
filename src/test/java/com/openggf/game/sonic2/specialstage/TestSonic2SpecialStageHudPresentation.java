package com.openggf.game.sonic2.specialstage;

import com.openggf.game.CharacterPresentation;
import com.openggf.game.presentation.CharacterNameComposer;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.*;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_2)
class TestSonic2SpecialStageHudPresentation {
    @Test void leaderAndTailsRoleNamesHaveIndependentDomesticAndOverseasBanks() throws Exception {
        var loader = new Sonic2SpecialStageDataLoader(TestEnvironment.currentRom());
        var hud = loader.getHudArtPatterns(); var tails = loader.getTailsTextArtPatterns();
        var both = Sonic2SpecialStageHudPresentation.applyBanks(hud, tails,
                new CharacterPresentation("MICA"), new CharacterPresentation("TALON"));
        var mica = Sonic2SpecialStageHudPresentation.apply(hud, tails, new CharacterPresentation("MICA"));
        var talon = Sonic2SpecialStageHudPresentation.apply(hud, tails, new CharacterPresentation("TALON"));
        for (int i = 0; i < 5; i++) for (int x = 0; x < 8; x++) for (int y = 0; y < 8; y++) {
            assertEquals(mica[i].getPixel(x,y), both.hud()[i].getPixel(x,y));
            assertEquals(talon[i].getPixel(x,y), both.hud()[i+5].getPixel(x,y));
            assertEquals(talon[i].getPixel(x,y), both.tails()[i].getPixel(x,y));
        }
        var stock = Sonic2SpecialStageHudPresentation.applyBanks(hud, tails, null, null);
        assertSame(hud, stock.hud()); assertSame(tails, stock.tails());
    }
    @Test void micaUsesNativeGlyphCellsWithoutNeighbourPixels() throws Exception {
        var loader = new Sonic2SpecialStageDataLoader(TestEnvironment.currentRom());
        var font = Sonic2SpecialStageHudPresentation.font(loader.getHudArtPatterns(), loader.getTailsTextArtPatterns());
        // Independent decoded ROM cells: leading space, ink and shadow; no neighbouring letter.
        String[][] cells = {
            {"067100671", "044614461", "044464461", "044444451", "044141451", "044110451", "044100451", "011100111"},
            {"0671", "0571", "0561", "0571", "0561", "0561", "0561", "0111"},
            {"00676710", "05555561", "05511551", "05500111", "05500671", "05555561", "01555511", "00111110"},
            {"00557710", "04444471", "04411451", "04444451", "04444451", "04411451", "04410451", "01110111"}
        };
        int[] advances = {9, 4, 8, 8};
        var composed = CharacterNameComposer.compose("MICA", font, Map.of(), 0);
        assertTrue(composed.complete());
        assertEquals(29, composed.image().width());
        int offset = 0;
        for (int i = 0; i < advances.length; i++) {
            var glyph = font.get("MICA".charAt(i));
            assertEquals(advances[i], glyph.width());
            assertEquals(8, glyph.height());
            for (int y = 0; y < 8; y++) for (int x = 0; x < advances[i]; x++) {
                int pixel = Character.digit(cells[i][y].charAt(x), 16);
                assertEquals(pixel, glyph.pixels()[y * advances[i] + x], "glyph " + i + " at " + x + "," + y);
                assertEquals(pixel, composed.image().pixels()[y * 29 + offset + x]);
            }
            offset += advances[i];
        }
    }

    @Test void micaUsesOnlyStageLettersAndPreservesEveryOtherTile() throws Exception {
        var loader = new Sonic2SpecialStageDataLoader(TestEnvironment.currentRom());
        var hud = loader.getHudArtPatterns();
        var tails = loader.getTailsTextArtPatterns();
        var composed = CharacterNameComposer.compose("MICA",
                Sonic2SpecialStageHudPresentation.font(hud, tails), Map.of(), 0);
        assertTrue(composed.complete());
        assertTrue(composed.supplementaryGlyphs().isEmpty());
        var custom = Sonic2SpecialStageHudPresentation.apply(hud, tails, new CharacterPresentation("MICA"));
        var expected = CharacterNameComposer.tiles(composed.image());
        for (int i = 0; i < 4; i++) for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++)
            assertEquals(expected[i].getPixel(x,y), custom[i].getPixel(x,y));
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) assertEquals(0,custom[4].getPixel(x,y));
        for (int i = 5; i < hud.length; i++) assertSame(hud[i],custom[i]);
        assertNotSame(hud[0],custom[0]);
    }

    @Test void stockAndUnsupportedNamesKeepOriginalArt() throws Exception {
        var loader = new Sonic2SpecialStageDataLoader(TestEnvironment.currentRom());
        var hud = loader.getHudArtPatterns();
        var tails = loader.getTailsTextArtPatterns();
        assertSame(hud,Sonic2SpecialStageHudPresentation.apply(hud,tails,null));
        for (String name : new String[]{"SONIC", "TAILS", "MILES", "Q", "MMMMMM"})
            assertSame(hud,Sonic2SpecialStageHudPresentation.apply(hud,tails,new CharacterPresentation(name)));
        var sonic = Sonic2SpecialStageHudPresentation.apply(hud,tails,new CharacterPresentation("SONIC"));
        for(int i=0;i<hud.length;i++) for(int y=0;y<8;y++) for(int x=0;x<8;x++)
            assertEquals(hud[i].getPixel(x,y),sonic[i].getPixel(x,y));
    }
}
