package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.media.CdProgramAssets;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** Requires Root's ordinary verified USA media property. All written cases are UNRUN here. */
class TestCdR5PlayerBlocks {
    @Test void allTenOriginalReadersBorrowTheFourAuthenticatedDirectories() throws Exception {
        var root=System.getProperty("soniccd.media.root");assertNotNull(root,"Root must select verified media explicitly");
        try(var assets=CdProgramAssets.open(Path.of(root,"USA","Sonic CD (US).cue"))){
            for(var p:CdR5DataLayout.Programme.values()){
                var reader=CdProgrammeReader.open(assets,p.payload().stage());var d=CdR5PlayerBlocks.fromReader(reader,p);
                assertTrue(d.ownsReader(reader));assertEquals(p,d.programme());
                int[] counts={49,44,49,51};int[] offsets={110,8,334,212};
                for(int slot=0;slot<4;slot++){assertEquals(offsets[slot],d.directoryWord(slot));assertEquals(counts[slot],d.countMinusOne(slot));assertEquals(1,d.entryWord(slot,0));}
                assertEquals(0x1E5,d.entryWord(0,49));assertEquals(0x1C2,d.entryWord(1,44));
                assertEquals(0x1F4,d.entryWord(2,49));assertEquals(0x1ED,d.entryWord(3,51));
                assertThrows(IndexOutOfBoundsException.class,()->d.entryWord(1,45));
                var declared=p.range("PlayerBlocksDirectory");var foreign=new CdR5DataLayout.Range(declared.key(),declared.fileOffset(),declared.length(),declared.sha256(),declared.nativePath(),declared.line());
                assertThrows(IllegalArgumentException.class,()->d.view(foreign));
                if(p.payload().stage().actNumber()==3)assertThrows(UnsupportedOperationException.class,d::switchVisual);
                else {assertEquals(3,d.switchVisual().mappingFrames().size());assertEquals(42,d.switchVisual().mappings().length());assertEquals(0x200000+p.range("ConveyorSwitchSprites").fileOffset(),d.switchMappingAddress());assertSame(d.switchVisual(),d.switchVisual());}
            }
        }
    }
    @Test void foreignProgrammeCannotBorrowAnEqualNamedRange() throws Exception {
        var root=System.getProperty("soniccd.media.root");assertNotNull(root);
        try(var assets=CdProgramAssets.open(Path.of(root,"USA","Sonic CD (US).cue"))){
            var d=CdR5PlayerBlocks.open(assets,CdR5DataLayout.Programme.R51A);
            assertThrows(IllegalArgumentException.class,()->d.view(CdR5DataLayout.Programme.R51B.range("PlayerBlocksDirectory")));
            assertThrows(IllegalArgumentException.class,()->d.view(CdR5DataLayout.Programme.R51A.range("StageChunks")));
        }
    }
    @Test void mismatchedCanonicalProgrammeRejectsBeforeAnyDataBorrow() throws Exception {
        var root=System.getProperty("soniccd.media.root");assertNotNull(root);
        try(var assets=CdProgramAssets.open(Path.of(root,"USA","Sonic CD (US).cue"))){
            var r=CdProgrammeReader.open(assets,CdR5DataLayout.Programme.R51A.payload().stage());
            assertThrows(IllegalArgumentException.class,()->CdR5PlayerBlocks.fromReader(r,CdR5DataLayout.Programme.R51B));
        }
    }
}
