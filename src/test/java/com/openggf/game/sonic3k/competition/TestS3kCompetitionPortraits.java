package com.openggf.game.sonic3k.competition;

import com.openggf.game.*;
import com.openggf.tests.rules.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TestS3kCompetitionPortraits {
    @Test void presentationCopiesPaletteAndKeepsPortraitsWhenReelsAreDecorated() {
        int[] colors=new int[16]; colors[1]=0x24926d;
        var portrait=new CharacterPresentation.CompetitionPortrait(new PresentationFrameSet.Pose(
                new CharacterPresentation.Image(8,8,new byte[64]),4,8),colors);
        colors[1]=0;
        var p=new CharacterPresentation("MICA").withCompetitionPortraits(Map.of(
                CharacterPresentation.CompetitionSurface.S3K_PLAYER,portrait));
        assertEquals(0x24926d,portrait.rgb()[1]);
        var copy=portrait.rgb();copy[1]=0;
        assertEquals(0x24926d,portrait.rgb()[1]);
        assertEquals(p.competitionPortraits(),p.withReelFaces(Map.of()).competitionPortraits());
        assertTrue(new CharacterPresentation("MICA").competitionPortraits().isEmpty());
        assertThrows(IllegalArgumentException.class,()->new CharacterPresentation.CompetitionPortrait(portrait.pose(),new int[15]));
    }

    @Test @RequiresRom(SonicGame.SONIC_3K)
    void partialOverridePreservesNativePositionsDepthPlanesAndOtherCharacters() throws Exception {
        var rom=GameServices.rom().getRom();var assets=new S3kCompetitionAssets(rom);
        byte[] data=new byte[32*40];Arrays.fill(data,(byte)1);int[] rgb=new int[16];rgb[1]=0x24926d;
        var p=new CharacterPresentation("MICA").withCompetitionPortraits(Map.of(CharacterPresentation.CompetitionSurface.S3K_PLAYER,
                new CharacterPresentation.CompetitionPortrait(new PresentationFrameSet.Pose(new CharacterPresentation.Image(32,40,data),20,40),rgb)));
        var view=new S3kCompetitionScreen.MenuView(S3kCompetitionScreen.Phase.PLAYER_SELECT,8,0,true,
                S3kCompetitionSession.Mode.GRAND_PRIX,0,2,List.of(0,1),List.of(0x40,0x95),List.of(false,false),0,0,null);
        var records=new S3kCompetitionRecords(new byte[80]);
        var nativeScene=new S3kCompetitionMenuScene(rom,assets).compose(view,records);
        var actual=new S3kCompetitionMenuScene(rom,assets,Map.of(0,p)).compose(view,records);
        assertArrayEquals(nativeScene.planeA(),actual.planeA());assertArrayEquals(nativeScene.planeB(),actual.planeB());
        assertEquals(nativeScene.sprites().size(),actual.sprites().size());
        for(int i=0;i<actual.sprites().size();i++) {
            var n=nativeScene.sprites().get(i);var a=actual.sprites().get(i);
            assertEquals(n.x(),a.x());assertEquals(n.y(),a.y());assertEquals(n.drawPriority(),a.drawPriority());
            if(!n.mappingKey().equals("competition_menu_player")||n.mappingFrame()!=0)assertEquals(n,a);
            else assertNotEquals(n.pieces(),a.pieces());
        }
        assertFalse(actual.portraitBanks().isEmpty());
        var pixels=S3kCompetitionMenuRenderer.rasterize(actual).rgb(actual.palettes(),new com.openggf.graphics.PaletteFadePresentation(),
                com.openggf.graphics.color.DisplayColorProfile.RAW_RGB,com.openggf.graphics.color.DisplayColorExpansion.FULL);
        assertTrue(Arrays.stream(pixels).anyMatch(c->c==0x24926d));
        var absent=new S3kCompetitionMenuScene(rom,assets,Map.of(0,new CharacterPresentation("MICA"))).compose(view,records);
        assertEquals(nativeScene.sprites(),absent.sprites());
        assertArrayEquals(S3kCompetitionMenuRenderer.rasterize(nativeScene).indexedPixels(),S3kCompetitionMenuRenderer.rasterize(absent).indexedPixels());
    }
}
