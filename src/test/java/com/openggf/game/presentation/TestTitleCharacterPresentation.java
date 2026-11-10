package com.openggf.game.presentation;

import com.openggf.game.*;
import com.openggf.sprites.playable.SecondaryAbility;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TestTitleCharacterPresentation {
    @Test void stockAndUnknownDefaultsLeaveNativeTitlesUntouched() throws Exception {
        for (var host : CharacterPresentation.Host.values()) {
            var registry=PlayableCharacterRegistry.empty();
            assertNull(TitleCharacterPresentation.resolve(host,"sonic",registry));
            assertNull(TitleCharacterPresentation.resolve(host,"tails",registry));
            assertNull(TitleCharacterPresentation.resolve(host,"none",registry));
            assertNull(TitleCharacterPresentation.resolve(host,"disabled:mica",registry));
        }
    }
    @Test void defaultIdentityAndHostOwnTheBank() throws Exception {
        var image=new CharacterPresentation.Image(8,8,new byte[64]);
        var set=new StageSpriteSet(Map.of(0,new StageSpriteSet.Frame(image,3,7)));
        int[] rgb=new int[16];rgb[2]=0x24926d;
        var bank=new CharacterPresentation.TitleMascot(Map.of(CharacterPresentation.TitlePart.BODY,set),Map.of(),rgb);
        var presentation=new CharacterPresentation("MICA").withTitleMascots(Map.of(CharacterPresentation.Host.S1,bank));
        var key=CharacterKey.mod("title-test","mica");
        var definition=new CharacterDefinition(key,"MICA",(code,x,y)->null,null,PlayerCharacter.SONIC_AND_TAILS,
                SecondaryAbility.NONE,false,code->null,null,Set.of(),null,code->presentation,null);
        var registry=PlayableCharacterRegistry.empty().register(key,definition);
        assertSame(bank,TitleCharacterPresentation.resolve(CharacterPresentation.Host.S1,key.persisted(),registry));
        assertNull(TitleCharacterPresentation.resolve(CharacterPresentation.Host.S2,key.persisted(),registry));
        rgb[2]=0;bank.rgb()[2]=0;assertEquals(0x24926d,bank.rgb()[2]);
        assertEquals(3,bank.parts().get(CharacterPresentation.TitlePart.BODY).frames().get(0).hotspotX());
        assertSame(bank,presentation.withReelFaces(Map.of()).titleMascots().get(CharacterPresentation.Host.S1));
    }
    @Test void incompletePlaneBankRetainsEveryNativeTile() {
        var image=new CharacterPresentation.Image(160,224,new byte[160*224]);
        var frames=new StageSpriteSet(Map.of(13,new StageSpriteSet.Frame(image,0,0)));
        var masks=Map.of(13,new CharacterPresentation.TitlePlaneMask(new byte[320*224]));
        var partial=new TitleCharacterPresentation(new CharacterPresentation.TitleMascot(
                Map.of(CharacterPresentation.TitlePart.PLANE_LEFT,frames),masks,new int[16]),false);
        assertFalse(partial.hasPlane(13));
        assertEquals(-1,partial.maskedTile(13,0,0,new com.openggf.level.Pattern[0]));
        var complete=new TitleCharacterPresentation(new CharacterPresentation.TitleMascot(Map.of(
                CharacterPresentation.TitlePart.PLANE_LEFT,frames,
                CharacterPresentation.TitlePart.PLANE_RIGHT,frames),masks,new int[16]),false);
        assertTrue(complete.hasPlane(13));
    }
    @Test void masksAreBoundedBinaryAndDefensivelyCopied() {
        byte[] pixels=new byte[320*224];pixels[13]=1;
        var mask=new CharacterPresentation.TitlePlaneMask(pixels);pixels[13]=0;mask.pixels()[13]=0;
        assertEquals(1,mask.pixels()[13]);
        assertThrows(IllegalArgumentException.class,()->new CharacterPresentation.TitlePlaneMask(new byte[64]));
        pixels[0]=2;assertThrows(IllegalArgumentException.class,()->new CharacterPresentation.TitlePlaneMask(pixels));
    }
}
