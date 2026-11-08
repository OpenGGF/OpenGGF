package com.openggf.game.sonic2.competition.view;

import com.openggf.data.RomByteReader;
import com.openggf.game.ObjectArtProvider;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic2.Sonic2ObjectArt;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.*;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@RequiresRom(SonicGame.SONIC_2)
class TestSonic2AnimalFrameProjection {
    @Test void combinedOrdinarySheetFramesResolveToTheActualRomMappingAndAnimalBank() throws Exception {
        var rom=TestEnvironment.currentRom();var reader=RomByteReader.fromRom(rom);
        var sheet=new Sonic2ObjectArt(rom,reader).loadForZone(0).animalSheet();
        assertEquals(30,sheet.getFrameCount(),"five mapping sets, two animal banks, three frames");
        var renderer=new PatternSpriteRenderer(sheet,new GraphicsManager());
        var provider=mock(ObjectArtProvider.class);
        when(provider.getRenderer(ObjectArtKeys.ANIMAL)).thenReturn(renderer);
        var owner=new AnimalDisplayProbe();
        // Obj28_Properties (s2.asm:24513-24524), A=chicken,B=squirrel,C=pig,D=seal,E=rabbit.
        int[] properties={1,6,4,3,0};
        for(int set=0;set<5;set++) for(int variant=0;variant<2;variant++) for(int frame=0;frame<3;frame++) {
            int combined=(set*2+variant)*3+frame;
            var display=Sonic2CompetitionObjectDisplay.project(owner,provider,
                    ()->renderer.drawFrameIndex(combined,100,80,false,false));
            assertEquals(reader.readU32BE(0x118F0+properties[set]*8+4),display.mapping().address());
            assertEquals(frame,display.mapping().frame());
            int root=display.mapping().address(),address=root+reader.readS16BE(root+frame*2);
            var pieces=sheet.getFrame(combined).pieces();
            assertEquals(reader.readU16BE(address),pieces.size());
            for(int i=0;i<pieces.size();i++) {
                int p=address+2+i*8,size=reader.readU8(p+1),word=reader.readU16BE(p+2);
                var piece=pieces.get(i);
                assertEquals(reader.readS16BE(p+6),piece.xOffset());assertEquals((byte)reader.readU8(p),piece.yOffset());
                assertEquals((size>>2)+1,piece.widthTiles());assertEquals((size&3)+1,piece.heightTiles());
                // The ordinary composite bank offsets Animal_2 by its actual native art gap.
                assertEquals(word&2047,piece.tileIndex()-variant*(0x594-0x580));
                assertEquals((word&0x800)!=0,piece.hFlip());assertEquals((word&0x1000)!=0,piece.vFlip());
                assertEquals((word>>13)&3,piece.paletteIndex());assertEquals((word&0x8000)!=0,piece.priority());
            }
            assertEquals((variant==0?0x580:0x594)/2,display.artTile());
        }
    }
    private static final class AnimalDisplayProbe extends AbstractObjectInstance {
        AnimalDisplayProbe(){super(new ObjectSpawn(100,80,0x28,0,0,false,0),"animal-frame-probe");}
        @Override public void update(int vInt,PlayableEntity player) { }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }
}
