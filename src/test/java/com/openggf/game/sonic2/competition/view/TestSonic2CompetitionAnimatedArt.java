package com.openggf.game.sonic2.competition.view;

import com.openggf.data.RomByteReader;
import com.openggf.game.sonic2.resources.Sonic2PlcService;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_2)
class TestSonic2CompetitionAnimatedArt {
    @Test void flowersAndTwoPlayerFlipScriptsPublishOnlyAtTheFollowingDmaService() throws Exception {
        var rom=TestEnvironment.currentRom();var reader=RomByteReader.fromRom(rom);
        for(int zone:new int[]{0,12}) {
            var art=new Sonic2CompetitionArtResidency(rom,new Sonic2PlcService(rom));
            var animator=new Sonic2CompetitionAnimatedArt(rom,zone,art,null);
            int root=zone==0?0x3FF94:0x40160;
            var rows=decode(reader,root);var before=art.capture();
            assertTrue(before.pending().isEmpty(),"construction does not prime an ordinary atlas or submit a frame");
            for(int tick=0;tick<420;tick++) {
                var expected=new ArrayList<Sonic2CompetitionArtResidency.Upload>();
                var oldSlots=art.capture().slots();
                for(var row:rows) {
                    int old=row.timer;row.timer=(row.timer-1)&255;
                    if(old!=0)continue;
                    int f=row.frame>=row.count?0:row.frame;row.frame=f+1;
                    int index=reader.readU8(row.data+f*(row.perFrame?2:1));
                    row.timer=row.perFrame?reader.readU8(row.data+f*2+1):row.duration;
                    expected.add(new Sonic2CompetitionArtResidency.Upload(row.destination,
                            reader.slice(row.source+index*32,row.tiles*32)));
                }
                animator.update();
                assertEquals(expected,art.capture().pending(),"raw Dynamic_Normal operands at tick "+tick+" zone "+zone);
                assertArrayEquals(oldSlots,art.capture().slots(),"submission never writes resident patterns early");
                if(zone==12) assertTrue(expected.stream().allMatch(u->u.firstSlot()==0x740 || u.firstSlot()==0x330));
                art.serviceDmaQueueVBlank();
                for(var upload:expected) for(int tile=0;tile<upload.patterns().length/32;tile++)
                    assertArrayEquals(Arrays.copyOfRange(upload.patterns(),tile*32,(tile+1)*32),art.patternBytes(upload.firstSlot()+tile));
                for(int i=0;i<rows.size();i++) {
                    assertEquals(rows.get(i).timer,animator.capture().counters().get(i).timer());
                    assertEquals(rows.get(i).frame,animator.capture().counters().get(i).nextFrame());
                }
            }
            if(zone==12) assertArrayEquals(new byte[32],art.patternBytes(0x540),"1P second flip destination is untouched");
        }
    }
    @Test void countersAndPendingPublicationReplayFromOneOwner() throws Exception {
        var rom=TestEnvironment.currentRom();
        for(int zone:new int[]{0,11,12}) {
            var art=new Sonic2CompetitionArtResidency(rom,new Sonic2PlcService(rom));
            var animator=new Sonic2CompetitionAnimatedArt(rom,zone,art,null);
            for(int i=0;i<77;i++){animator.update();art.serviceDmaQueueVBlank();}
            animator.update();var state=animator.capture();var resident=art.capture();
            for(int i=0;i<40;i++){art.serviceDmaQueueVBlank();animator.update();}
            var expectedArt=art.capture();var expectedState=animator.capture();
            art.restore(resident);animator.restore(state);
            for(int i=0;i<40;i++){art.serviceDmaQueueVBlank();animator.update();}
            assertEquals(expectedArt,art.capture());assertEquals(expectedState,animator.capture());
        }
    }
    @Test void fullUploadBufferDropsOnlyThePayloadWhileScriptCountersAdvance() throws Exception {
        var rom=TestEnvironment.currentRom();var art=new Sonic2CompetitionArtResidency(rom,new Sonic2PlcService(rom));
        var owner=new Sonic2CompetitionAnimatedArt(rom,0,art,null);
        for(int i=0;i<18;i++)assertTrue(art.queueUpload(i,new byte[32]));
        var pending=art.capture().pending();owner.update();
        assertEquals(pending,art.capture().pending());
        assertTrue(owner.capture().counters().stream().allMatch(c->c.nextFrame()==1));
    }
    @Test void reelDrawQueuesBeforeTheZoneAnimationAsInTheNativeDeformationTail() throws Exception {
        var rom=TestEnvironment.currentRom();var art=new Sonic2CompetitionArtResidency(rom,new Sonic2PlcService(rom));
        var manager=new com.openggf.game.sonic2.slotmachine.CNZSlotMachineManager();
        var pictures=new Sonic2CompetitionSlotPictures(RomByteReader.fromRom(rom),art,manager::snapshot);
        manager.update(0);pictures.update(); // Init has no DrawSlot.
        var animator=new Sonic2CompetitionAnimatedArt(rom,12,art,pictures);
        manager.update(1);animator.update();
        // LevEvents_CNZ (s2.asm:21511-21513), loc_C4D0 (s2.asm:15174-15176),
        // Level_MainLoop (s2.asm:5092-5105): the event draw precedes AniArt_Load.
        var expected=new ArrayList<Integer>();expected.add(0x750);
        for(var row:decode(RomByteReader.fromRom(rom),0x40160))expected.add(row.destination);
        assertEquals(expected,art.capture().pending().stream().map(Sonic2CompetitionArtResidency.Upload::firstSlot).toList());
    }
    private static final class Row {
        int duration,source,destination,count,tiles,data,timer,frame;boolean perFrame;
    }
    private static List<Row> decode(RomByteReader rom,int root) {
        var result=new ArrayList<Row>();int p=root+2;
        for(int i=0;i<=rom.readU16BE(root);i++) {
            var r=new Row();r.duration=rom.readU8(p);r.perFrame=(r.duration&128)!=0;
            r.source=rom.readU32BE(p)&0xFFFFFF;r.destination=rom.readU16BE(p+4)/32;
            r.count=rom.readU8(p+6);r.tiles=rom.readU8(p+7);r.data=p+8;
            p=r.data+((r.count*(r.perFrame?2:1)+1)&~1);result.add(r);
        }
        return result;
    }
}
