package com.openggf.game.sonic2;

import com.openggf.game.CharacterPresentation;
import com.openggf.game.presentation.CharacterNameComposer;
import com.openggf.game.sonic2.specialstage.*;
import com.openggf.level.Pattern;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_2)
class TestSonic2SpecialStageNamePresentation {
    @Test void micaGotAAndHasAllUseNameTilesAndRetainNativeSuffixes() throws Exception {
        var rom=TestEnvironment.currentRom();
        var stock=new Pattern[]{new Pattern()};
        assertSame(stock,Sonic2SpecialStageResultsName.apply(rom,stock,null).patterns());
        var result=Sonic2SpecialStageResultsName.apply(rom,stock,new CharacterPresentation("MICA"));
        var expected=CharacterNameComposer.tiles(CharacterNameComposer.compose("MICA",Sonic2CharacterFont.title(rom),Map.of(),0).image());
        for(int i=0;i<expected.length;i++) for(int x=0;x<8;x++) for(int y=0;y<8;y++)
            assertEquals(expected[i].getPixel(x,y),result.patterns()[i+1].getPixel(x,y));
        for(int frame:new int[]{1,22,26,28}) {
            var nativePieces=Sonic2SpecialStageResultsMappings.getFrame(frame);
            var custom=result.frames().get(frame);
            int start = frame == 26 ? 3 : frame == 28 ? 5 : 0;
            assertEquals(Arrays.asList(nativePieces).subList(0, start), Arrays.asList(custom).subList(0, start));
            assertEquals(Arrays.asList(nativePieces).subList(start + 5, nativePieces.length),
                    Arrays.asList(custom).subList(start + expected.length, custom.length));
            for (int i = 0; i < expected.length; i++) {
                assertEquals(1 + i, custom[start + i].tileIndex());
                assertEquals(Sonic2SpecialStageResultsMappings.ART_TYPE_SS_LETTERS, custom[start + i].artType());
            }
            assertEquals(nativePieces[start + 4].xOffset()+16, custom[start + expected.length-1].xOffset()+8);
        }
    }
}
