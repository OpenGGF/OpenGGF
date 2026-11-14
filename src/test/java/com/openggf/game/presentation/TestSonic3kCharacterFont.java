package com.openggf.game.presentation;

import com.openggf.game.sonic3k.Sonic3kCharacterFont;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestSonic3kCharacterFont {
    @Test void inventoriesCoverMicaWithoutSupplements() throws Exception {
        var rom = TestEnvironment.currentRom();
        for (var font : java.util.List.of(Sonic3kCharacterFont.hud(rom),
                Sonic3kCharacterFont.title(rom), Sonic3kCharacterFont.results(rom))) {
            var mica = CharacterNameComposer.compose("MICA", font, Map.of(), 0);
            assertTrue(mica.complete(), mica.missingGlyphs().toString());
            assertEquals(Set.of('M', 'I', 'C', 'A'), mica.nativeGlyphs());
            for (byte pixel : mica.image().pixels()) assertTrue(pixel >= 0 && pixel <= 15);
        }
    }
    @Test void resultsMicaCropsPreserveNativePixelsAcrossTileBoundaries() throws Exception {
        var rom = TestEnvironment.currentRom();
        var font = Sonic3kCharacterFont.results(rom);
        var miles = com.openggf.util.PatternDecompressor.kosinskiModuled(rom,
                com.openggf.game.sonic3k.constants.Sonic3kConstants.ART_KOSM_RESULTS_MILES_ADDR);
        var tails = com.openggf.util.PatternDecompressor.kosinskiModuled(rom,
                com.openggf.game.sonic3k.constants.Sonic3kConstants.ART_KOSM_RESULTS_TAILS_ADDR);
        assertArrayEquals(CharacterNameComposer.extract(miles, 0, miles.length / 2, 2,
                0, 0, 18, 16).pixels(), font.get('M').pixels());
        assertArrayEquals(CharacterNameComposer.extract(tails, 0, tails.length / 2, 2,
                12, 0, 16, 16).pixels(), font.get('A').pixels());
        assertEquals(Set.of('A','C','E','I','K','L','M','N','O','S','T','U'), font.keySet());
    }

    @Test void perZoneSetsIncludeSharedZoneAndSplitCombinedPieces() throws Exception {
        var rom = TestEnvironment.currentRom();
        var hcz = Sonic3kCharacterFont.title(rom, 1);
        assertEquals(16, hcz.get('T').width());
        assertEquals(16, hcz.get('Y').width());
        for (char letter : "HYDROCITYZONE".toCharArray()) assertTrue(hcz.containsKey(letter));
        var mhz = Sonic3kCharacterFont.title(rom, 7);
        assertEquals(16, mhz.get('H').width());
        assertEquals(8, mhz.get('I').width());
        assertEquals(8, mhz.get('L').width());
    }

    @Test void inventoriesDoNotInventMissingLetters() throws Exception {
        var rom = TestEnvironment.currentRom();
        assertFalse(Sonic3kCharacterFont.hud(rom).containsKey('U'));
        var title = Sonic3kCharacterFont.title(rom);
        for (char missing : "JQWX".toCharArray()) assertFalse(title.containsKey(missing));
    }
}
