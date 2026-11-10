package com.openggf.game.soniccd.scroll;

import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.media.CdProgramAssets;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import static com.openggf.game.soniccd.scroll.CdR4BackgroundScroll.*;
import static com.openggf.game.soniccd.scroll.TestCdR4BackgroundScroll.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Independently pinned original media component borrows, NOT production descriptors or loader.
 * Full-file pins from catalogue AppendixA; source graph N1ecc5f3a R41[A-D] Legacy Shared/R4 Shared.
 * No native source file is opened or used as runtime asset/fallback. State is a constructed control.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestCdR4ScrollMediaPorts {
    private CdProgramAssets programs;
    @BeforeAll void open() throws Exception {
        String root=System.getProperty("soniccd.media.root");assumeTrue(root!=null&&!root.isBlank(),"Optional original Sonic CD USA media root");
        Path cue;try(var files=Files.list(Path.of(root).resolve("USA"))){var cues=files.filter(p->p.toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();assertEquals(1,cues.size());cue=cues.getFirst();}
        programs=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty());
    }
    @AfterAll void close() throws Exception {if(programs!=null)programs.close();}
    @ParameterizedTest(name="{0} full-file and reached WobbleTable operand")
    @CsvSource({
        "R41A,6ddd5b53e1530c15099948053280809271319d35a3c0424b424393ce513054ca,ACT1_PRESENT",
        "R41B,4d9698d1bb3ef967171fcb65e31ae6bb74fbadce706a2016385fba091ec0d4f2,ACT1_OTHER",
        "R41C,da73414c78fd8108816f39633c9b39fc8d9e8761296b8dfb5bbf457b1ef93e90,ACT1_OTHER",
        "R41D,101a602503dd1db59bdd52125c450690a816e303e97f2c08907c72f3cc89da11,ACT1_OTHER",
        "R42A,5ccaa4f12da09f0aaaf9308be81f8345b02c68e11f78173693c347dec1784024,ACT2_OR_3",
        "R42B,c3c987ae4113d94d0ae03e893dd44c66988ada3baa2f657b26391e654bab6355,ACT2_OR_3",
        "R42C,e478ce24bf9fdd5216102b42b08c908ee383f6bdb02258ef53802b00b5b27dc8,ACT2_OR_3",
        "R42D,eb1f5c2d3cb11735438f74b799e0f8626426ce4beb66cbf577359d2ed7454496,ACT2_OR_3",
        "R43C,c27a455859c4820182c8119d0de3618dde55dd47d34d32c396963e677ac1994c,ACT2_OR_3",
        "R43D,a11d1d547841697d87d2a1b5fd5c3fe3ebab9aeaf5e8f1d3964f165a521b4789,ACT2_OR_3"
    })
    void originalMediaBindsOnlyFiniteTableControls(CdNativeFile file,String fullHash,Profile profile) throws Exception {
        var image=programs.mmd(file);assertEquals(0x40000,image.fileBytes());var full=CdAssetBytes.copyOf(image.readFile(0,0x40000));assertEquals(fullHash,hash(full));
        // Actual LEA WobbleTable,a4 followed by MOVE.B bg_water_deform,d3, not ambiguous equal bytes.
        var roots=new ArrayList<Integer>();int operand=-1;
        for(int at=0x100;at<0x40000-8;at+=2){
            if(full.unsignedWord(at)!=0x49F9||full.unsignedWord(at+6)!=0x1638)continue;
            int target=(int)full.unsignedLong(at+2)-0x200000;
            if(target>=0&&target<=0x40000-256&&hash(full.slice(target,256)).equals("2dca8f2df5a879ac88fa474f4f87e3aba075d2c604480dbc334c05d8ea9dd267")){roots.add(target);operand=at+2;}
        }
        assertEquals(1,roots.size(),"Actual scroll LEA must resolve the source, including ambiguous data matches");
        CdAssetBytes repeats=b();if(profile==Profile.ACT1_PRESENT){
            var locations=new ArrayList<Integer>();byte[] needle={13,9,3,1,1,0};for(int at=0x100;at<0x40000-6;at++){if(Arrays.equals(needle,full.slice(at,6).copy()))locations.add(at);}assertEquals(1,locations.size());repeats=full.slice(locations.getFirst(),6);
        }
        var before=initialize(profile,state(0,0,0,0,0,0),0,0);
        var out=step(profile,before,input(0,0,0,0,0,0,0),tables(full.slice(roots.getFirst(),256),repeats,b()));
        int[] literal={0,0,0,0,0,0,1,1,1,1,1,2,2,2,2,2,2,2,3,3,3,3,3,3,3,3,3,3,3,3,3,3};
        for(int line=0;line<literal.length;line++){assertEquals(0,out.state().hscrollRam().unsignedWord(line*4));assertEquals(literal[line],out.state().hscrollRam().unsignedWord(line*4+2));}
        assertEquals(192,out.bandWordsWritten());assertEquals(464,out.hscrollWordsWritten());assertEquals(0x80,out.deform());
        System.out.printf(Locale.ROOT,"TT finite media %s full=%s LEA operand=$%X wobble=$%X output=%s%n",file,fullHash,operand,roots.getFirst(),hash(out.state().hscrollRam()));
    }
    static String hash(CdAssetBytes b)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b.copy()));}
}
