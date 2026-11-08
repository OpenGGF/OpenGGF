package com.openggf.game.sonic2;

import com.openggf.game.CharacterPresentation;
import com.openggf.game.CharacterPresentation.*;
import com.openggf.game.presentation.CharacterNameComposer;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.*;
import com.openggf.util.PatternDecompressor;
import com.openggf.game.sonic2.constants.Sonic2Constants;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_2)
class TestSonic2CharacterPresentation {
    static CharacterPresentation mica() {
        byte[] icon = new byte[256]; java.util.Arrays.fill(icon, (byte) 3);
        return new CharacterPresentation("MICA", null,
                Map.of(Host.S2, new Art(new Image(16,16,icon), new Image(16,16,icon), null,null)), Map.of());
    }
    @Test void hudUsesMicaIconAndSmallNativeNamePreservingCounterRow() throws Exception {
        var rom = TestEnvironment.currentRom();
        var stock = PatternDecompressor.nemesis(rom, Sonic2Constants.ART_NEM_SONIC_LIFE_ADDR);
        assertSame(stock, Sonic2CharacterPresentation.hud(rom,stock,null));
        var custom = Sonic2CharacterPresentation.hud(rom,stock,mica());
        var expected = CharacterNameComposer.compose("MICA",Sonic2CharacterFont.hud(rom),Map.of(),0).image();
        for (int x=0;x<16;x++) for(int y=0;y<16;y++)
            assertEquals(3, custom[x/8*2+y/8].getPixel(x%8,y%8));
        for (int x=0;x<32;x++) for(int y=0;y<8;y++) {
            assertEquals(x<expected.width()?expected.pixels()[y*expected.width()+x]:0,
                    custom[4+x/8*2].getPixel(x%8,y));
            assertEquals(stock[5+x/8*2].getPixel(x%8,y),custom[5+x/8*2].getPixel(x%8,y));
        }
    }
}
