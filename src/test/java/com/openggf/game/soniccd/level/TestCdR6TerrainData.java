package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.media.*;
import java.nio.file.Path;
import java.util.*;
import java.io.IOException;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static com.openggf.game.soniccd.level.CdProgrammeReader.Role.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Authenticated immutable terrain DATA only; no legacy shape/player/World admission. */
class TestCdR6TerrainData {
    private static CdProgramAssets source;
    private static synchronized CdProgramAssets media()throws Exception {
        if(source==null)source=CdProgramAssets.open(Path.of(System.getProperty("soniccd.media.root")).resolve("USA/Sonic CD (US).cue"),Optional.of(CdReleaseVariant.USA),Optional.empty());
        return source;
    }
    @AfterAll static void close()throws Exception {if(source!=null)source.close();source=null;}
    @ParameterizedTest @ValueSource(strings={"R61A","R61B"})
    void authenticInitialTerrainUsesOneCommonAlgorithm(String id)throws Exception {
        var terrain=CdStageTerrain.fromProgramme(media(),CdStageKey.parse(id));
        assertEquals(id.equals("R61A")?680:700,terrain.blockCount());
        assertEquals(id.equals("R61A")?554:592,terrain.patternCount());
        assertEquals(36,terrain.layout().columns(CdStageTerrain.Plane.FOREGROUND));
        assertEquals(8,terrain.layout().rows(CdStageTerrain.Plane.FOREGROUND));
        var r=CdProgrammeReader.open(media(),CdStageKey.parse(id));assertSame(r.terrain(),r.terrain());assertNotSame(terrain,r.terrain());
        assertEquals(terrain.profile(),r.terrain().profile());assertEquals(CdStageTerrain.LayoutEncoding.EXACT_READ_VIEW,terrain.profile().layoutEncoding());
        assertEquals(CdStageTerrain.ReferencePolicy.SELECTED_KNOWN_SOURCE,terrain.profile().referencePolicy());
        assertThrows(UnsupportedOperationException.class,terrain::shape);assertThrows(UnsupportedOperationException.class,r::terrainShape);assertThrows(UnsupportedOperationException.class,r::ordinaryZoneActWord);
        assertThrows(IllegalArgumentException.class,()->CdProgrammeData.fromReader(r));
    }

    private record Fact(String blocks,String patterns,String ids,String angles,String heights,String widths,
                        String blockSha,String patternSha,int chunkBytes,int collisionBytes) { }
    private static Fact fact(String id) {
        return switch(id) {
            case "R61A" -> new Fact("LevelBlocks","Art_LevelTiles","LevelCollision","ColAngleMap","ColHeightMap","ColWidthMap",
                    "fbe24abb0657027a67eedffb4ed425e935f672e0b561a4e004dbb2f7f2a86ec2","21e4bfbb3e84c2c3d5dd379717bc711d06e73abb11624d81c2f9799450fef45a",59904,600);
            case "R61B" -> new Fact("StageBlocks","StageGfx","StageCollision","StageCollisionAngles","StageCollisionColumns","StageCollisionRows",
                    "ca2b95dc40df6fa2ed9094caa3e5fd2214713717fce0550c3aa60b595bcc0859","8de8d11209b2a2b8d6b7c671e4fb53f839700aaaaea8b5729a7f36a89ca12d74",58880,800);
            default -> throw new IllegalArgumentException("No bounded R61 terrain fact");
        };
    }
    @ParameterizedTest @ValueSource(strings={"R61A","R61B"})
    void originalProductsAndEveryRawGeometryFieldUseTheCommonDecoder(String id)throws Exception {
        var p=CdR6DataLayout.Programme.valueOf(id);var f=fact(id);var r=CdProgrammeReader.open(media(),p.payload().stage());var t=r.terrain();
        assertEquals(f.blockSha(),CdR11aProfile.sha256(t.blocks().copy()));assertEquals(f.patternSha(),CdR11aProfile.sha256(t.patterns().copy()));
        assertEquals(f.chunkBytes(),t.chunks().length());assertEquals(f.collisionBytes(),t.collision().ids().length());
        assertArrayEquals(r.decoded(p.range(f.blocks())).bytes().copy(),t.blocks().copy());assertArrayEquals(r.decoded(p.range(f.patterns())).bytes().copy(),t.patterns().copy());
        assertArrayEquals(r.view(p.range("DataChunks")).copy(),t.chunks().copy());assertArrayEquals(r.view(p.range(f.ids())).copy(),t.collision().ids().copy());
        assertArrayEquals(r.view(p.range(f.angles())).copy(),t.collision().angles().copy());assertArrayEquals(r.view(p.range(f.heights())).copy(),t.collision().heights().copy());assertArrayEquals(r.view(p.range(f.widths())).copy(),t.collision().widths().copy());
        for(int i=0;i<t.blockCount();i++)for(int y=0;y<2;y++)for(int x=0;x<2;x++) {
            int raw=t.blocks().unsignedWord(i*8+(y*2+x)*2);var word=t.blockWord(i,x,y);assertEquals(raw,word.rawWord());assertEquals(raw&2047,word.patternIndex());assertEquals((raw>>>13)&3,word.paletteLine());
        }
        for(int i=1;i<=t.chunkCount();i++)for(int y=0;y<16;y++)for(int x=0;x<16;x++) {
            int raw=t.chunks().unsignedWord((i-1)*512+(y*16+x)*2);var word=t.chunkWord(i,x,y);assertEquals(raw,word.rawWord());assertEquals(raw&1023,word.renderBlockIndex());assertEquals(raw&2047,word.collisionBlockIndex());
        }
        for(int i=0;i<t.patternCount();i++)for(int y=0;y<8;y++)for(int x=0;x<8;x++) {
            int packed=t.patterns().unsignedByte(i*32+y*4+x/2);assertEquals((x&1)==0?packed>>>4:packed&15,t.pixel(i,x,y));
        }
    }
    @ParameterizedTest @ValueSource(strings={"R61A","R61B"})
    void nativeClearCopyAndSolidityQualifiedDataQueriesPreserveReadConstraints(String id)throws Exception {
        var p=CdR6DataLayout.Programme.valueOf(id);var r=CdProgrammeReader.open(media(),p.payload().stage());var t=r.terrain();int maxSolid=0;
        for(var plane:CdStageTerrain.Plane.values()) {
            int slot=plane==CdStageTerrain.Plane.FOREGROUND?0:1;var original=r.view(p.range("LayoutRead"+slot));int w=original.unsignedByte(0)+1,h=original.unsignedByte(1)+1;
            assertEquals(2+w*h,original.length());assertEquals(w,t.layout().columns(plane));assertEquals(h,t.layout().rows(plane));
            for(int y=0;y<16;y++)for(int x=0;x<64;x++) {
                var cell=t.layout().nativeCell(plane,x,y);int raw=x<w&&y<h?original.unsignedByte(2+y*w+x):0; // Native explicit $800 clear, not missing asset padding.
                assertEquals(raw,cell.rawByte());assertEquals(raw&127,cell.chunkId());
                if(x<w&&y<h)assertEquals(cell,t.layout().cell(plane,x,y));
                if(plane==CdStageTerrain.Plane.FOREGROUND && x<w && y<h && cell.chunkId()>0)
                    for(int cy=0;cy<16;cy++)for(int cx=0;cx<16;cx++) {
                        var word=t.chunkWord(cell.chunkId(),cx,cy);
                        if(word.solidity()!=0) {maxSolid=Math.max(maxSolid,word.collisionBlockIndex());assertEquals(t.collision().ids().unsignedByte(word.collisionBlockIndex()),t.collision().idForBlock(word.collisionBlockIndex()));}
                    }
            }
        }
        assertEquals(id.equals("R61A")?543:690,maxSolid);assertTrue(maxSolid<t.collision().ids().length());
        var first=t.chunkWord(18,0,0);assertEquals(id.equals("R61A")?24624:24774,first.rawWord());assertEquals(81,t.collision().idForBlock(first.collisionBlockIndex()));
        for(int cid=0;cid<256;cid++)for(int at=0;at<16;at++) {
            assertEquals((byte)t.collision().heights().unsignedByte(cid*16+at),t.collision().height(cid,at));
            assertEquals((byte)t.collision().widths().unsignedByte(cid*16+at),t.collision().width(cid,at));assertEquals(t.collision().angles().unsignedByte(cid),t.collision().angle(cid));
        }
    }
    private static void unavailable(CdStageTerrain.ReadOwner owner,int index,int count,org.junit.jupiter.api.function.Executable read) {
        var fault=assertThrows(CdStageTerrain.UnavailableRead.class,read);assertEquals(owner,fault.owner());assertEquals(index,fault.index());assertEquals(count,fault.availableCount());
    }
    @Test void originalBMissingStagingWordAndKnownPrefixesNeverBecomePadding()throws Exception {
        var r=CdProgrammeReader.open(media(),CdStagePayload.R61B.stage());var t=r.terrain();assertEquals(25,t.layout().cell(CdStageTerrain.Plane.FOREGROUND,14,1).chunkId());
        var word=t.chunkWord(25,0,14);assertEquals(1019,word.rawWord());assertEquals(0,word.solidity());
        unavailable(CdStageTerrain.ReadOwner.BLOCK_STAGING,1019,700,()->t.blockWord(word.renderBlockIndex(),0,0));
        unavailable(CdStageTerrain.ReadOwner.COLLISION_SOURCE,1019,800,()->t.collision().idForBlock(word.collisionBlockIndex()));
        unavailable(CdStageTerrain.ReadOwner.PATTERN_SOURCE,592,592,()->t.pixel(592,0,0));
        unavailable(CdStageTerrain.ReadOwner.CHUNK_SOURCE,t.chunkCount()+1,t.chunkCount(),()->t.chunkWord(t.chunkCount()+1,0,0));
        assertThrows(IndexOutOfBoundsException.class,()->t.blockWord(-1,0,0));assertThrows(IndexOutOfBoundsException.class,()->t.chunkWord(0,0,0));assertThrows(IndexOutOfBoundsException.class,()->t.pixel(0,8,0));
        var d=t.profile();var eager=new CdStageTerrain.Profile(d.blockBytes(),d.patternBytes(),d.chunkBytes(),d.foregroundColumns(),d.foregroundRows(),d.backgroundColumns(),d.backgroundRows(),d.collisionIndexBytes(),d.layoutEncoding(),CdStageTerrain.ReferencePolicy.EAGER_VALIDATED);
        assertThrows(IllegalArgumentException.class,()->CdStageTerrain.decodeData(eager,Optional.empty(),t.blocks().copy(),t.patterns().copy(),t.chunks().copy(),r.role(FG).copy(),r.role(BG).copy(),t.collision().ids().copy(),t.collision().angles().copy(),t.collision().heights().copy(),t.collision().widths().copy()));
    }
    @ParameterizedTest @ValueSource(strings={"R61A","R61B"})
    void originalReadFailuresAndMalformedLayoutPreserveCacheAndRetry(String id)throws Exception {
        var p=CdR6DataLayout.Programme.valueOf(id);var r=CdProgrammeReader.open(media(),p.payload().stage());var binding=r.definition().terrain().orElseThrow();var c=spy(r);var first=new IOException("original graphics input unavailable");
        doThrow(first).when(c).decoded(binding.graphics());doReturn(CdAssetBytes.copyOf(new byte[]{0,0})).when(c).role(FG);clearInvocations(c);
        assertSame(first,assertThrows(IOException.class,c::terrain));verify(c,never()).role(CHUNKS);
        var blocks=r.decoded(binding.blocks());doReturn(r.decoded(binding.graphics())).when(c).decoded(binding.graphics());assertThrows(IllegalArgumentException.class,c::terrain);assertSame(blocks,r.decoded(binding.blocks()));
        doReturn(r.role(FG)).when(c).role(FG);var result=c.terrain();clearInvocations(c);assertSame(result,c.terrain());verify(c,never()).decoded(any());verify(c,never()).role(any());
        var reached=spy(CdProgrammeReader.open(media(),p.payload().stage()));var fault=new AssertionError("first terrain raw chunk source unavailable");doThrow(fault).when(reached).role(CHUNKS);clearInvocations(reached);
        assertSame(fault,assertThrows(AssertionError.class,reached::terrain));verify(reached,never()).role(FG);doReturn(r.role(CHUNKS)).when(reached).role(CHUNKS);assertEquals(result.profile(),reached.terrain().profile());
    }
    @ParameterizedTest @ValueSource(strings={"R61C","R61D","R62A","R62B","R62C","R62D","R63C","R63D"})
    void eightOtherAuthenticatedOriginalsRejectBeforeAnyTerrainRoleOrDecode(String id)throws Exception {
        var r=spy(CdProgrammeReader.open(media(),CdStageKey.parse(id)));clearInvocations(r);
        assertEquals(id+" has no reviewed terrain DATA binding",assertThrows(UnsupportedOperationException.class,r::terrain).getMessage());verify(r,never()).role(any());verify(r,never()).decoded(any());
        assertThrows(UnsupportedOperationException.class,()->CdStageTerrain.fromProgramme(media(),CdStageKey.parse(id)));
    }
    @Test void registeredIngressAuthenticatesBeforeUnsupportedQueryAndKeepsClosedApi()throws Exception {
        var wrong=mock(CdProgramAssets.class,delegatesTo(media()));doReturn(media().mmd(CdNativeFile.R61D)).when(wrong).mmd(CdNativeFile.R61C);
        assertThrows(IOException.class,()->CdStageTerrain.fromProgramme(wrong,CdStagePayload.R61C.stage()));
        assertFalse(Modifier.isPublic(CdStageTerrain.class.getDeclaredMethod("decodeData",CdStageTerrain.Profile.class,Optional.class,byte[].class,byte[].class,byte[].class,byte[].class,byte[].class,byte[].class,byte[].class,byte[].class,byte[].class).getModifiers()));
        for(var constructor:CdStageTerrain.class.getDeclaredConstructors())assertTrue(Modifier.isPrivate(constructor.getModifiers()));
    }
}
