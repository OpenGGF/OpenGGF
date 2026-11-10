package com.openggf.game.presentation;

import com.openggf.game.*;
import com.openggf.sprites.playable.SecondaryAbility;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TestTitleCharacterPresentation {
    @Test void slotExchangeIsExplicitAndLegacyBanksKeepNativeRoles() {
        var legacy = new CharacterPresentation.TitleMascot(Map.of(), Map.of(), new int[16]);
        var exchanged = new CharacterPresentation.TitleMascot(Map.of(), Map.of(), new int[16], true);
        assertFalse(new TitleCharacterPresentation(legacy, false).swapsS2Slots());
        assertTrue(new TitleCharacterPresentation(exchanged, false).swapsS2Slots());
    }
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
    @Test void ownedAperturePixelsRemainOpaqueUnderEveryNativeTileFlip() {
        var source = new com.openggf.level.Pattern();
        for (int y=0;y<8;y++) for (int x=0;x<8;x++) source.setPixel(x,y,(byte)3);
        byte[] mask = new byte[320*224];
        // A sparse ownership mask must never clear the rest of the sprite's rectangular cell.
        mask[11*320+18] = 1; mask[11*320+19] = 1;
        byte[] replacements = new byte[320*224]; replacements[11*320+18]=15;
        int cell = 1*40+2;
        for (int flip : new int[]{0,0x800,0x1000,0x1800}) {
            var opaque = TitleCharacterPresentation.maskedPattern(
                    mask,replacements,cell,flip,source);
            var transparent = TitleCharacterPresentation.maskedPattern(
                    mask,new byte[320*224],cell,flip,source);
            for (int y=0;y<8;y++) for (int x=0;x<8;x++) {
                int px=(flip&0x800)==0 ? x : 7-x, py=(flip&0x1000)==0 ? y : 7-y;
                assertEquals(y==3 && x==2 ? 15 : y==3 && x==3 ? 0 : 3,opaque.getPixel(px,py));
                assertEquals(y==3 && (x==2 || x==3) ? 0 : 3,transparent.getPixel(px,py));
                assertEquals(3,source.getPixel(x,y),"shared native tiles must stay unchanged");
            }
        }
        assertNull(TitleCharacterPresentation.maskedPattern(
                mask,replacements,0,0,source));
        var value=new CharacterPresentation.TitlePlaneMask(mask,replacements);
        replacements[11*320+18]=0; assertEquals(15,value.clearedPixels()[11*320+18]);
        value.clearedPixels()[11*320+18]=0; assertEquals(15,value.clearedPixels()[11*320+18]);
        replacements[11*320+18]=16;
        assertThrows(IllegalArgumentException.class,()->new CharacterPresentation.TitlePlaneMask(mask,replacements));
        assertThrows(IllegalArgumentException.class,()->new CharacterPresentation.TitlePlaneMask(mask,new byte[4]));
        assertThrows(IllegalArgumentException.class,()->new CharacterPresentation.TitlePlaneMask(new byte[320*224],replacements));
    }
}
