package com.openggf.game.sonic3k.objects;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.sonic3k.S3kSpriteDataLoader;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestSozSandMechanismRomAssets {
    @Test void owningInstructionsPointToLockedOnMapsAndArtWords() throws Exception {
        try(var rom=new Rom()) {
            assertTrue(rom.open(RomTestUtils.ensureSonic3kRomAvailable().getAbsolutePath()));
            int[][] expected={{0x40276,0x4043A,0x43C0,1},{0x40B16,0x40D10,0x4432,13},{0x41CC2,0x41EAE,0x43BD,3}};
            for(var spec:expected) {
                assertEquals(0x217C,rom.read16BitAddr(spec[0]));
                assertEquals(spec[1],rom.read32BitAddr(spec[0]+2));
                assertEquals(spec[2],rom.read16BitAddr(spec[0]+10));
                var frames=S3kSpriteDataLoader.loadMappingFrames(RomByteReader.fromRom(rom),spec[1],spec[3]);
                assertEquals(spec[3],frames.size());
                for(var frame:frames) assertFalse(frame.pieces().isEmpty());
            }
            var cork=S3kSpriteDataLoader.loadMappingFrames(RomByteReader.fromRom(rom),0x41EAE,3);
            assertEquals(6,cork.get(1).pieces().size());assertEquals(16,cork.get(2).pieces().size());
            for(int i=0;i<16;i++)assertEquals(-128+i*16,cork.get(2).pieces().get(i).yOffset());
        }
    }
}
