package com.openggf.game.soniccd.player;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.media.CdProgramAssets;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual CdProgramAssets ingress, original USA only. Literal N-source WaterHeights operands.
 * This independent byte correspondence supplies no live R4 metadata/admission or stage proof.
 * Missing explicitly required media is a failure, not a skip. No disassembly/runtime raw-source reads.
 */
class TestCdR4WaterMedia {
    @Test void allTenActualUsaProgramsContainOriginalWaterHeightOperands() throws Exception {
        String root=System.getProperty("soniccd.media.root");assertNotNull(root,"original media property required");
        Path cue;
        try(var paths=Files.list(Path.of(root).resolve("USA"))){
            var cues=paths.filter(p->p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1,cues.size());cue=cues.getFirst();
        }
        byte[] expected=HexFormat.of().parseHex("0300028001d00fff05b0053005100fff0490049004900fff");
        var programs=List.of(CdNativeFile.R41A,CdNativeFile.R41B,CdNativeFile.R41C,CdNativeFile.R41D,
                CdNativeFile.R42A,CdNativeFile.R42B,CdNativeFile.R42C,CdNativeFile.R42D,CdNativeFile.R43C,CdNativeFile.R43D);
        try(var assets=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())){
            assertTrue(assets.hasExpectedTrackShape());
            // Independently supplied catalogue identity, not a digest fitted to this execution.
            assertEquals("a6b026a2381f926485cebb2da0bded29319cfd515ef7956a3ca79b18645bdb2b",
                    assets.identity().normalizedSha256());
            for(var file:programs){
                byte[] image=assets.read(file,0,0x40000);int matches=0,at=-1;
                for(int i=0;i<=image.length-expected.length;i++)if(Arrays.equals(expected,0,expected.length,image,i,i+expected.length)){matches++;at=i;}
                assertEquals(1,matches,file+" exact WaterHeights table correspondence");
                assertTrue(at>=0x100,file+" table must lie beyond MMD header");
                System.out.println("R4 water-source correspondence "+file+" fileOffset=$"+Integer.toHexString(at)+" bytes=24");
            }
        }
    }
}
