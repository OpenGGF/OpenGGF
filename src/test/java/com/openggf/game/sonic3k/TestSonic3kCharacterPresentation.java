package com.openggf.game.sonic3k;

import com.openggf.game.CharacterPresentation;
import com.openggf.game.CharacterPresentation.*;
import com.openggf.game.presentation.CharacterNameComposer;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.util.PatternDecompressor;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestSonic3kCharacterPresentation {
    @Test void creatorNamesAndIconsPreserveNativeCrossAndCounterPixels() throws Exception {
        var rom = TestEnvironment.currentRom();
        byte[] icon = new byte[16 * 16];
        java.util.Arrays.fill(icon, (byte) 3);
        var art = Map.of(Host.S3K, new Art(new Image(16, 16, icon), null, null, null));
        for (int address : new int[]{Sonic3kConstants.ART_NEM_SONIC_LIFE_ICON_ADDR,
                Sonic3kConstants.ART_NEM_TAILS_LIFE_ICON_ADDR,
                Sonic3kConstants.ART_NEM_KNUCKLES_LIFE_ICON_ADDR}) {
            var stock = PatternDecompressor.nemesis(rom, address);
            byte[] cross = CharacterNameComposer.extract(stock, 4, 4, 2, 0, 8, 8, 8).pixels();
            int ink = 0;
            for (byte pixel : cross) if (pixel != 0) ink++;
            assertTrue(ink > 0, "ROM cross must be nonblank: " + address);
            byte[] lowerRow = CharacterNameComposer.extract(stock, 4, 4, 2, 0, 8, 32, 8).pixels();
            for (String name : new String[]{"MICA", "TALON"}) {
                for (var icons : java.util.List.of(Map.<Host, Art>of(), art)) {
                    var presentation = new CharacterPresentation(name, name, icons, Map.of());
                    var result = Sonic3kCharacterPresentation.hud(rom, stock, presentation);
                    assertArrayEquals(cross, CharacterNameComposer.extract(result, 4, 4, 2, 0, 8, 8, 8).pixels(),
                            name + " must retain the ROM cross");
                    assertArrayEquals(lowerRow, CharacterNameComposer.extract(result, 4, 4, 2, 0, 8, 32, 8).pixels());
                    for (int tile = 5; tile < 12; tile += 2) assertSame(stock[tile], result[tile]);
                    if (!icons.isEmpty()) assertArrayEquals(icon,
                            CharacterNameComposer.extract(result, 0, 2, 2, 0, 0, 16, 16).pixels());
                }
            }
            assertSame(stock, Sonic3kCharacterPresentation.hud(rom, stock, null));
        }
    }

    @Test void supplementaryHudGlyphCannotCoverCounterRow() throws Exception {
        var glyph = new Image(8, 16, new byte[8 * 16]);
        var presentation = new CharacterPresentation("Q", "Q", Map.of(),
                Map.of(new Font(Host.S3K, Style.HUD), Map.of('Q', glyph)));
        var rom = TestEnvironment.currentRom();
        var stock = PatternDecompressor.nemesis(rom, Sonic3kConstants.ART_NEM_TAILS_LIFE_ICON_ADDR);
        assertThrows(IllegalArgumentException.class, () -> Sonic3kCharacterPresentation.hud(rom, stock, presentation));
    }
}
