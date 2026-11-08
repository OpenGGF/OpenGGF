package com.openggf.game.sonic1;

import com.openggf.data.Rom;
import com.openggf.game.CharacterPresentation;
import com.openggf.game.CharacterPresentation.*;
import com.openggf.game.presentation.CharacterNameComposer;
import com.openggf.level.Pattern;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.util.PatternDecompressor;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_1)
class TestSonic1CharacterPresentation {
    static Image image(int width, int height, int pixel) {
        byte[] pixels = new byte[width * height];
        java.util.Arrays.fill(pixels, (byte) pixel);
        return new Image(width, height, pixels);
    }
    static CharacterPresentation mica() {
        return new CharacterPresentation("MICA", null,
                Map.of(Host.S1, new Art(image(16,16,3), image(16,16,4), null, null)),
                Map.of(new Font(Host.S1, Style.HUD), Map.of('M',image(8,8,5),'A',image(7,8,6))));
    }
    static Rom rom() throws Exception { return com.openggf.tests.TestEnvironment.currentRom(); }
    static Pattern[] lives(Rom rom) throws Exception {
        return PatternDecompressor.nemesis(rom, Sonic1RomProfile.resolve(rom,
                com.openggf.game.sonic1.constants.Sonic1Constants.ART_NEM_LIFE_ICON_ADDR));
    }
    @Test void hudUsesMicaIconAndSmallNativeICWithOnlySupplementaryMA() throws Exception {
        Rom rom = rom();
        var nativeTiles = lives(rom);
        var result = Sonic1CharacterPresentation.hud(rom, nativeTiles, mica());
        var name = Sonic1CharacterPresentation.name(rom, mica(), Style.HUD);
        assertEquals(Set.of('I','C'), name.nativeGlyphs());
        assertEquals(Set.of('M','A'), name.supplementaryGlyphs());
        assertTrue(name.complete());
        assertArrayEquals(image(16,16,3).pixels(), CharacterNameComposer.extract(result,0,2,2,0,0,16,16).pixels());
        assertArrayEquals(name.image().pixels(), CharacterNameComposer.extract(result,4,4,2,0,0,name.image().width(),8).pixels());
        for (int i = 5; i < 12; i += 2) assertSame(nativeTiles[i], result[i], "Lower row stays native");
        assertSame(nativeTiles, Sonic1CharacterPresentation.hud(rom,nativeTiles,null));
    }
    @Test void missingHudGlyphKeepsNativeNameAndOptionalIconStillWorks() throws Exception {
        Rom rom = rom();
        var nativeTiles = lives(rom);
        var result = Sonic1CharacterPresentation.hud(rom,nativeTiles,new CharacterPresentation("MICA"));
        for (int i=0;i<nativeTiles.length;i++) assertSame(nativeTiles[i],result[i]);
    }
}
