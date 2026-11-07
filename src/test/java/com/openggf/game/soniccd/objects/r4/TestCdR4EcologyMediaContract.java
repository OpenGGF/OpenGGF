package com.openggf.game.soniccd.objects.r4;
import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.media.CdProgramAssets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
/** Independent complete original USA MMD identity controls. No TT data loader, source ingress or route proof. */
class TestCdR4EcologyMediaContract {
    @Test void allTenOriginalProgrammesMatchIndependentCataloguePins() throws Exception {
        String root=System.getProperty("soniccd.media.root");
        Assumptions.assumeTrue(root!=null&&!root.isBlank(),"Set soniccd.media.root for supplied-media checks");
        Path cue=Path.of(root,"USA","Sonic CD (US).cue");assertTrue(Files.isRegularFile(cue));
        String[] names={"R41A","R41B","R41C","R41D","R42A","R42B","R42C","R42D","R43C","R43D"};
        String[] hashes={"6ddd5b53e1530c15099948053280809271319d35a3c0424b424393ce513054ca",
            "4d9698d1bb3ef967171fcb65e31ae6bb74fbadce706a2016385fba091ec0d4f2",
            "da73414c78fd8108816f39633c9b39fc8d9e8761296b8dfb5bbf457b1ef93e90",
            "101a602503dd1db59bdd52125c450690a816e303e97f2c08907c72f3cc89da11",
            "5ccaa4f12da09f0aaaf9308be81f8345b02c68e11f78173693c347dec1784024",
            "c3c987ae4113d94d0ae03e893dd44c66988ada3baa2f657b26391e654bab6355",
            "e478ce24bf9fdd5216102b42b08c908ee383f6bdb02258ef53802b00b5b27dc8",
            "eb1f5c2d3cb11735438f74b799e0f8626426ce4beb66cbf577359d2ed7454496",
            "c27a455859c4820182c8119d0de3618dde55dd47d34d32c396963e677ac1994c",
            "a11d1d547841697d87d2a1b5fd5c3fe3ebab9aeaf5e8f1d3964f165a521b4789"};
        try(var assets=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())){
            for(int i=0;i<names.length;i++){
                var file=CdNativeFile.valueOf(names[i]);var entry=assets.entry(file);
                assertEquals(262144,entry.size(),names[i]);assertEquals(3512+128*i,entry.extentBlock(),names[i]);
                byte[] actual=assets.read(file,0,262144);
                assertEquals(hashes[i],HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(actual)),names[i]);
                assertTrue(CdR4EcologyManifest.PROGRAMMES.contains(file));
            }
        }
    }
}
