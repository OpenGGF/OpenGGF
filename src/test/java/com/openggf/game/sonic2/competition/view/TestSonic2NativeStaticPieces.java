package com.openggf.game.sonic2.competition.view;

import com.openggf.data.RomByteReader;
import com.openggf.game.sonic2.objects.CollapsingPlatformObjectInstance.CollapsingPlatformFragmentInstance;
import com.openggf.game.sonic2.objects.MonitorContentsObjectInstance;
import com.openggf.level.objects.*;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_2)
class TestSonic2NativeStaticPieces {
    @Test void collapsingChildrenPointAtActualIndividualPiecesAndRetainInheritedDisplayFields() throws Exception {
        var rom=RomByteReader.fromRom(TestEnvironment.currentRom());
        var services=services(rom);
        var row=Sonic2CompetitionSpriteArtTable.STATIC_ROWS.getFirst();
        // Obj1F_CreateFragments (s2.asm:23868-23912): BSET static, copy mapping/art/width/priority.
        bytes(rom,0x10B84,"08e800050001");bytes(rom,0x10BA8,"234b0004");
        bytes(rom,0x10BAC,"13450001");bytes(rom,0x10BBC,"336800020002");
        bytes(rom,0x10BC2,"136800180018");bytes(rom,0x10BC8,"136800190019");
        int frame=row.parentMappings()+rom.readS16BE(row.parentMappings()+row.frame()*2);
        assertEquals(6,rom.readU16BE(frame));
        for(int piece=0;piece<6;piece++) {
            // Existing recreation spawn format: MCZ config1 in bits3-4, native piece index in bits0-2.
            var owner=new CollapsingPlatformFragmentInstance(
                    new ObjectSpawn(100,80,0x1F,8|piece,0,false,0));
            owner.setServices(services);
            var display=owner.nativeDisplaySlot();
            assertEquals(frame+2+piece*8,display.mapping().address());
            assertEquals(0,display.mapping().frame());
            assertEquals(row.renderFlags(),display.renderFlags());assertEquals(row.width(),display.width());
            assertEquals(Sonic2CompetitionSpriteArtTable.adjust2PArtPointer(row.artTile1P()),display.artTile());
            assertEquals(row.bucket(),owner.getPriorityBucket());
        }
    }
    @Test void monitorRewardIconsUseTheSelectedRomFramesFirstPiece() throws Exception {
        var rom=RomByteReader.fromRom(TestEnvironment.currentRom());var services=services(rom);
        var row=Sonic2CompetitionSpriteArtTable.STATIC_ROWS.getLast();
        for(int reward=0;reward<=8;reward++) {
            var owner=new MonitorContentsObjectInstance(new ObjectSpawn(100,80,0x2E,reward,0,false,0),null);
            owner.setServices(services);
            var display=owner.nativeDisplaySlot();
            int frame=row.parentMappings()+rom.readS16BE(row.parentMappings()+(reward+1)*2);
            assertNotNull(display);assertEquals(frame+2,display.mapping().address());
            assertEquals(row.renderFlags(),display.renderFlags());assertEquals(row.width(),display.width());
            assertEquals(Sonic2CompetitionSpriteArtTable.adjust2PArtPointer(row.artTile1P()),display.artTile());
            assertEquals(row.bucket(),owner.getPriorityBucket());
        }
    }
    private static ObjectServices services(RomByteReader reader) {
        return new StubObjectServices(){@Override public RomByteReader romReader(){return reader;}};
    }
    private static void bytes(RomByteReader rom,int address,String hex) {
        assertArrayEquals(java.util.HexFormat.of().parseHex(hex),rom.slice(address,hex.length()/2));
    }
}
