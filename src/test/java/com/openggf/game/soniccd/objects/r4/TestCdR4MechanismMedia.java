package com.openggf.game.soniccd.objects.r4;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.media.CdProgramAssets;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual USA media pipeline check, independently pinned by ISO reads under E.
 * Verifies literal source operands, not object admission, helper execution or a TT route.
 * No new stage loader/descriptors, native source assets or synthetic CdProgramAssets. */
class TestCdR4MechanismMedia {
    @Test void actualTenUsaProgrammeImagesAndFiniteNativeOperandSpans() throws Exception {
        String root=System.getProperty("soniccd.media.root");
        org.junit.jupiter.api.Assumptions.assumeTrue(root!=null&&!root.isBlank(),"Explicit original USA media required");
        List<Path> cues; try(var files=Files.list(Path.of(root).resolve("USA"))) { cues=files.filter(p->p.toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList(); }
        assertEquals(1,cues.size());
        String[] names={"R41A","R41B","R41C","R41D","R42A","R42B","R42C","R42D","R43C","R43D"};
        String[] hashes={"6ddd5b53e1530c15099948053280809271319d35a3c0424b424393ce513054ca","4d9698d1bb3ef967171fcb65e31ae6bb74fbadce706a2016385fba091ec0d4f2","da73414c78fd8108816f39633c9b39fc8d9e8761296b8dfb5bbf457b1ef93e90","101a602503dd1db59bdd52125c450690a816e303e97f2c08907c72f3cc89da11","5ccaa4f12da09f0aaaf9308be81f8345b02c68e11f78173693c347dec1784024","c3c987ae4113d94d0ae03e893dd44c66988ada3baa2f657b26391e654bab6355","e478ce24bf9fdd5216102b42b08c908ee383f6bdb02258ef53802b00b5b27dc8","eb1f5c2d3cb11735438f74b799e0f8626426ce4beb66cbf577359d2ed7454496","c27a455859c4820182c8119d0de3618dde55dd47d34d32c396963e677ac1994c","a11d1d547841697d87d2a1b5fd5c3fe3ebab9aeaf5e8f1d3964f165a521b4789"};
        int[] door={52612,-1,-1,-1,52442,-1,54734,54716,54716,54716};
        int[] elevator={51990,-1,62462,62444,51820,-1,-1,-1,-1,-1};
        int[] chain={-1,-1,60722,60704,-1,-1,63264,63246,47488,47508};
        int[] prize={-1,-1,61380,61362,-1,-1,-1,-1,-1,-1};
        try(var programs=CdProgramAssets.open(cues.getFirst(),Optional.of(CdReleaseVariant.USA),Optional.empty())) {
            assertEquals("a6b026a2381f926485cebb2da0bded29319cfd515ef7956a3ca79b18645bdb2b",programs.identity().normalizedSha256());
            for(int i=0;i<10;i++) {
                CdNativeFile file=CdNativeFile.valueOf(names[i]); var image=programs.mmd(file);
                assertEquals(3512+128*i,programs.entry(file).extentBlock()); assertEquals(262144,image.fileBytes());
                assertEquals(hashes[i],HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(image.readFile(0,image.fileBytes()))));
                literal(image,door[i],new int[]{64,8,128,2,64,8,128,2,8,32,64,1,8,64,128,0,8,64,128,0});
                literal(image,elevator[i],new int[]{1,0,255,0,0,160,1,0,255,0,0,160,2,0,254,0,0,80});
                literal(image,chain[i],new int[]{32,0,0,0,224,0,24,1,16,1,8,1,248,1,240,1,232,1});
                literal(image,prize[i],new int[]{2,1,0,3,3,2,1,0,3,3,2,2,0,3,3,2,2,1,2,2,1,0,3,3,3,2,2,1,0,3,3,3,2,2,1,0,0,0,0,0,0,0});
            }
        }
    }
    private static void literal(com.openggf.game.soniccd.media.CdMmdImage image,int offset,int[] values) {
        if(offset<0) return; byte[] expected=new byte[values.length]; for(int i=0;i<values.length;i++) expected[i]=(byte)values[i];
        assertArrayEquals(expected,image.readFile(offset,expected.length),"independent native operand span at "+offset);
    }
}
