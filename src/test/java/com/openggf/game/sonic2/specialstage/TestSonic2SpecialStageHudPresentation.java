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
        for (String name : new String[]{"Q", "MMMMMM"})
            assertSame(hud,Sonic2SpecialStageHudPresentation.apply(hud,tails,new CharacterPresentation(name)));
        var sonic = Sonic2SpecialStageHudPresentation.apply(hud,tails,new CharacterPresentation("SONIC"));
        for(int i=0;i<hud.length;i++) for(int y=0;y<8;y++) for(int x=0;x<8;x++)
            assertEquals(hud[i].getPixel(x,y),sonic[i].getPixel(x,y));
    }
}
