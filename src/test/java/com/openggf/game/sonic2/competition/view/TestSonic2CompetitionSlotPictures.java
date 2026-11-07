package com.openggf.game.sonic2.competition.view;

import com.openggf.data.RomByteReader;
import com.openggf.game.sonic2.slotmachine.CNZSlotMachineManager;
import com.openggf.game.sonic2.resources.Sonic2PlcService;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_2)
class TestSonic2CompetitionSlotPictures {
    @Test void columnMajorPicturesCrossEveryNativeSequenceBoundaryUsingRomRows() throws Exception {
        var rom=TestEnvironment.currentRom();var reader=RomByteReader.fromRom(rom);
        var art=new Sonic2CompetitionArtResidency(rom,new Sonic2PlcService(rom));
        var pictures=new Sonic2CompetitionSlotPictures(reader,art,()->null);
        for(int reel=0;reel<3;reel++) for(int index=0;index<8;index++) for(int firstRow:new int[]{0,1,15,31}) {
            var expected=new byte[512];
            int firstFace=reader.readU8(0x2C401+reel*8+index),nextFace=reader.readU8(0x2C401+reel*8+(index+1)%8);
            for(int column=0;column<4;column++) {
                int tail=(32-firstRow)*4;
                System.arraycopy(reader.slice(0x4EEFE+firstFace*512+column*128+firstRow*4,tail),0,expected,column*128,tail);
                if(firstRow>0)System.arraycopy(reader.slice(0x4EEFE+nextFace*512+column*128,firstRow*4),0,expected,column*128+tail,firstRow*4);
            }
            assertArrayEquals(expected,pictures.compose(reel,(index<<8)|(firstRow<<3)));
        }
    }
    @Test void actualInitialRollQueuesOnlyTheOneReelDrawAndSurvivesRestoreWithoutDuplicateUploads() throws Exception {
        var rom=TestEnvironment.currentRom();var manager=new CNZSlotMachineManager();
        var art=new Sonic2CompetitionArtResidency(rom,new Sonic2PlcService(rom));
        var pictures=new Sonic2CompetitionSlotPictures(RomByteReader.fromRom(rom),art,manager::snapshot);
        manager.update(0);pictures.update();
        assertTrue(art.capture().pending().isEmpty(),"Routine1 seeds all reels but has no DrawSlot call");
        for(int reel=0;reel<3;reel++) {
            manager.update(reel+1);pictures.update();
            var pending=art.capture().pending();assertEquals(1,pending.size());assertEquals(0x750+reel*16,pending.getFirst().firstSlot());
            assertArrayEquals(new byte[32],art.patternBytes(0x750+reel*16));
            var saved=pictures.capture();var resident=art.capture();
            pictures.update();assertEquals(pending,art.capture().pending(),"observing a second time never advances the manager");
            art.serviceDmaQueueVBlank();boolean visible=false;
            for(int tile=0;tile<16;tile++)visible|=!Arrays.equals(new byte[32],art.patternBytes(0x750+reel*16+tile));
            assertTrue(visible,"the complete resident picture must contain art");
            art.restore(resident);pictures.restore(saved);pictures.update();assertEquals(pending,art.capture().pending());
            art.serviceDmaQueueVBlank();
        }
        for(int tile=0x550;tile<0x580;tile++)assertArrayEquals(new byte[32],art.patternBytes(tile),"1P picture slots untouched");
    }
}
