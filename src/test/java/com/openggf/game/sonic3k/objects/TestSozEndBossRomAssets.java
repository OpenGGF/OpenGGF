package com.openggf.game.sonic3k.objects;
import com.openggf.data.*;
import com.openggf.game.sonic3k.*;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestSozEndBossRomAssets {
    @Test void nativeTablesAndRegisteredArtResolveFromLockedOnRom() throws Exception {
        try(var rom=new Rom()){
            assertTrue(rom.open(RomTestUtils.ensureSonic3kRomAvailable().getAbsolutePath()));
            assertEquals(5,rom.read16BitAddr(0x782FA));assertEquals(2,rom.read16BitAddr(0x78320));
            assertEquals(2,rom.read16BitAddr(0x78326));assertEquals(19,rom.read16BitAddr(0x78334));
            assertEquals(1,rom.read16BitAddr(0x7833A));
            int[] callbacks={0x77CB4,0x77D1A,0x77D82,0x77FFC,0x77DD8,0x67CCE};
            for(int i=0;i<5;i++)assertEquals(callbacks[i],rom.read32BitAddr(0x782FC+i*6));
            var plan=Sonic3kPlcArtRegistry.getPlan(8,1);var art=new Sonic3kObjectArt(null,RomByteReader.fromRom(rom));
            var entry=plan.standaloneArt().stream().filter(e->e.key().equals(Sonic3kObjectArtKeys.SOZ_END_BOSS)).findFirst().orElseThrow();
            var sheet=art.loadStandaloneSheet(rom,entry);assertEquals(16,sheet.getFrameCount());
            for(int i=0;i<16;i++)assertFalse(sheet.getFrame(i).pieces().isEmpty(),"frame "+i);
            assertEquals(3,Sonic3kPlcLoader.parsePlc(rom,0x6D).entries().size());
        }
    }
}
