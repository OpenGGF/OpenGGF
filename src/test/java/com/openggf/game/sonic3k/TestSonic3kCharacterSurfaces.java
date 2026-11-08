package com.openggf.game.sonic3k;

import com.openggf.game.CharacterPresentation;
import com.openggf.game.CharacterPresentation.*;
import com.openggf.game.GameServices;
import com.openggf.game.presentation.CharacterNameComposer;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.util.PatternDecompressor;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestSonic3kCharacterSurfaces {
    static CharacterPresentation mica() {
        byte[] pixels = new byte[256];
        java.util.Arrays.fill(pixels, (byte) 7);
        return new CharacterPresentation("MICA", null,
                Map.of(Host.S3K, new Art(new Image(16,16,pixels), null, null, null)), Map.of());
    }
    @Test void hudUsesNativeMicaLettersAndIndependentFace() throws Exception {
        var rom = GameServices.rom().getRom();
        var nativeTiles = PatternDecompressor.nemesis(rom, Sonic3kConstants.ART_NEM_SONIC_LIFE_ICON_ADDR);
        var tiles = Sonic3kCharacterPresentation.hud(rom, nativeTiles, mica());
        var expected = CharacterNameComposer.compose("MICA", Sonic3kCharacterFont.hud(rom), Map.of(), 0).image();
        assertArrayEquals(expected.pixels(), CharacterNameComposer.extract(tiles,4,4,2,0,0,expected.width(),expected.height()).pixels());
        assertArrayEquals(mica().art().get(Host.S3K).hudLifeIcon().pixels(), CharacterNameComposer.extract(tiles,0,2,2,0,0,16,16).pixels());
        for (int address : new int[]{Sonic3kConstants.ART_NEM_SONIC_LIFE_ICON_ADDR,
                Sonic3kConstants.ART_NEM_TAILS_LIFE_ICON_ADDR, Sonic3kConstants.ART_NEM_KNUCKLES_LIFE_ICON_ADDR}) {
            var stock = PatternDecompressor.nemesis(rom,address);
            assertSame(stock, Sonic3kCharacterPresentation.hud(rom,stock,null));
        }
    }
}
