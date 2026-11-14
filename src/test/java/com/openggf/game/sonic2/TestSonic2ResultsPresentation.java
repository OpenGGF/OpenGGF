package com.openggf.game.sonic2;

import com.openggf.data.RomByteReader;
import com.openggf.game.CharacterPresentation;
import com.openggf.game.presentation.CharacterNameComposer;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.*;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_2)
class TestSonic2ResultsPresentation {
    @Test void micaGotThroughUsesNativeLettersAndKeepsSuffixAndOtherFrames() throws Exception {
        var rom = TestEnvironment.currentRom();
        var stock = new Sonic2ObjectArt(rom, RomByteReader.fromRom(rom)).loadForZone(0).resultsSheet();
        assertSame(stock,Sonic2ResultsName.apply(rom,stock,null));
        var custom = Sonic2ResultsName.apply(rom,stock,new CharacterPresentation("MICA"));
        var expected = CharacterNameComposer.tiles(CharacterNameComposer.compose("MICA",
                Sonic2CharacterFont.title(rom),Map.of(),0).image());
        for (int i=0;i<expected.length;i++) for(int y=0;y<8;y++) for(int x=0;x<8;x++)
            assertEquals(expected[i].getPixel(x,y),custom.getPatterns()[stock.getPatterns().length+i].getPixel(x,y));
        for (int frame = 0; frame < 3; frame++) {
            var mapping = custom.getFrame(frame).pieces();
            for (int i = 0; i < expected.length; i++) {
                var piece = mapping.get(i);
                assertEquals(stock.getPatterns().length + i, piece.tileIndex());
                assertEquals(8 - 64 + i / 2 * 8, piece.xOffset());
                assertEquals(i % 2 * 8, piece.yOffset());
                assertTrue(piece.priority());
            }
        }
        var pieces=custom.getFrame(0).pieces();
        assertEquals(stock.getFrame(0).pieces().subList(5,8),pieces.subList(pieces.size()-3,pieces.size()));
        for(int i=3;i<stock.getFrameCount();i++) assertSame(stock.getFrame(i),custom.getFrame(i));
        for(int i=0;i<stock.getPatterns().length;i++) assertSame(stock.getPatterns()[i],custom.getPatterns()[i]);
    }
}
