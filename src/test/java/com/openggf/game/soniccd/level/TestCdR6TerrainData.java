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
        // Historical case identity retained: immutable terrain DATA now opens, while legacy source gates still reject before terrain reads.
        assertThrows(UnsupportedOperationException.class,r::ordinaryZoneActWord);assertThrows(UnsupportedOperationException.class,r::terrainShape);
        verify(r,never()).role(any());verify(r,never()).decoded(any());assertThrows(IllegalArgumentException.class,()->CdProgrammeData.fromReader(r));
    }
    @Test void registeredIngressAuthenticatesBeforeUnsupportedQueryAndKeepsClosedApi()throws Exception {
        var wrong=mock(CdProgramAssets.class,delegatesTo(media()));doReturn(media().mmd(CdNativeFile.R61D)).when(wrong).mmd(CdNativeFile.R61C);
        assertThrows(IOException.class,()->CdStageTerrain.fromProgramme(wrong,CdStagePayload.R61C.stage()));
        assertFalse(Modifier.isPublic(CdStageTerrain.class.getDeclaredMethod("decodeData",CdStageTerrain.Profile.class,Optional.class,byte[].class,byte[].class,byte[].class,byte[].class,byte[].class,byte[].class,byte[].class,byte[].class,byte[].class).getModifiers()));
        for(var constructor:CdStageTerrain.class.getDeclaredConstructors())assertTrue(Modifier.isPrivate(constructor.getModifiers()));
    }

    // Independent N1ecc original-media audit: a missing/wrong programme binding must fail these literal products.
    private static int[] remainingCounts(String id) {
        return switch(id) {
            case "R61C" -> new int[]{680,568,116,36,8,25,3,700};
            case "R61D" -> new int[]{832,658,116,36,8,24,3,900};
            case "R62A" -> new int[]{740,648,117,37,8,24,3,800};
            case "R62B" -> new int[]{756,627,115,37,8,24,3,800};
            case "R62C" -> new int[]{740,664,116,37,8,25,3,800};
            case "R62D" -> new int[]{836,698,117,37,8,24,3,900};
            case "R63C" -> new int[]{740,670,118,15,8,10,3,900};
            case "R63D" -> new int[]{816,657,118,15,8,12,3,900};
            default -> throw new IllegalArgumentException("No remaining initial source contract");
        };
    }
    @ParameterizedTest @ValueSource(strings={"R61C","R61D","R62A","R62B","R62C","R62D","R63C","R63D"})
    void remainingInitialTerrainUsesAuthenticatedCommonIngress(String id)throws Exception {
        var expected=remainingCounts(id);var t=CdStageTerrain.fromProgramme(media(),CdStageKey.parse(id));
        assertEquals(expected[0],t.blockCount());assertEquals(expected[1],t.patternCount());assertEquals(expected[2],t.chunkCount());
        assertEquals(expected[3],t.layout().columns(CdStageTerrain.Plane.FOREGROUND));assertEquals(expected[4],t.layout().rows(CdStageTerrain.Plane.FOREGROUND));
        assertEquals(expected[5],t.layout().columns(CdStageTerrain.Plane.BACKGROUND));assertEquals(expected[6],t.layout().rows(CdStageTerrain.Plane.BACKGROUND));assertEquals(expected[7],t.collision().ids().length());
        assertThrows(UnsupportedOperationException.class,t::shape);
    }

    // Literal independent N1ecc/media oracles; never expectations computed from the Java programme profiles.
    private static List<String> remainingHashes(String id) {return switch(id) {
            case "R61C" -> List.of("c49903bb1ab987868d8c1564570ec88091647b77a62269d15dd714b94fc774b9","ef046cda75884ea3f588cc2561d093f8163c96f75a7c6de54b84a396a060918b","944500e6ebf325ba158203631d1361ae3384a065c31eb878fc9c752cc1c8f753","9a857d46a80390b36ffc4eece6423cd9b7fdc4319965c8a47aa69f0de46559d9","7c62f66f94d697da086121c753fff1e3c44535c8fcaf23f0d89b2fb6fe19834d","7a2e94b334d2211bf6a50d33e51e77b67b7c17e9ba83ea517433fd8192aac2f7","c05a6e177730d577d6743c1740f636565c3c0061cf0526aaaaf46ac4961ae9fd","103655cfc0a8919807a8a0741a0600771fa7b86838d5eb00f4faaadc42996d9d","90f660b66816de8018412c97a47ebd151801493dff92ae6a67dea86711530cc4");
            case "R61D" -> List.of("a823ea4a2866f80a3d7758a84dbb25cd4483afa7edcbea8ab6ccd804d861b754","31d80b69efa8b10d6ba15dd366e75f0cabb0d9741b3899aebd4dd0e5645e4afd","b7f2428eb2b10df92a27c941f577666b115fad4d46d7753477f9e7cd704e7062","d70a831ec68016f283dda7b57b19c65b0a5917ee689c439d57bb0cdce2b8186f","e92a7e2afa2228b6be23e1cd438c72e9502194823f9e6984fc4657aeff145208","0ca334929c0c25076c2126d696fb719579daf50e63a7d3d5622d432428daaeb6","c05a6e177730d577d6743c1740f636565c3c0061cf0526aaaaf46ac4961ae9fd","103655cfc0a8919807a8a0741a0600771fa7b86838d5eb00f4faaadc42996d9d","90f660b66816de8018412c97a47ebd151801493dff92ae6a67dea86711530cc4");
            case "R62A" -> List.of("c3a3bd5fe5f48a84f8620f3d05b72998a095182e848e8a794605e328b28da534","2eb0f03a74681e541ee902ac1e3aeef415bc233a2b70a3da7caf4f5a91e5204b","3b6827a42ff04b73d3c78abd2c3931cdef40654ef3e2bc632c7ad7ad35d40342","2626e018ccf120312f246491cc9e9af6e99f41073985a35ac740ecee7874583a","b249929cb2a61ec90246aff2b70ec42659e7871acc86eb64b17e259c25ab5f31","e5b75cd24f5fe1aa59b263e9fe7a45c95088dc47b8d81dfd2f728cae5dbf1d82","c05a6e177730d577d6743c1740f636565c3c0061cf0526aaaaf46ac4961ae9fd","103655cfc0a8919807a8a0741a0600771fa7b86838d5eb00f4faaadc42996d9d","90f660b66816de8018412c97a47ebd151801493dff92ae6a67dea86711530cc4");
            case "R62B" -> List.of("3982b9953fcec243fc962364e4c63bb8690edf983f0f48947ffde98f52e4948f","b8b1c51ec014397544ec25e8ecc5b9e414dfbc232cba6f0d8f273acfbdee1e5b","cc87f4e0de535a9633b003b26e5f1f364d3a177c804184657cd8a3f0a69b99e0","456204aaf2bc919d32a77b47eb3acabc32af45da50e6b6fcd5d6230cc4767b49","3e66e2f99798a42648c6d4a700992ea29eb9624f08558fdd43e7cde5685aa524","0b1a75e753487b2aa7b4c31dd133d52dd95898412838ece370f8cb2cf6147f22","c05a6e177730d577d6743c1740f636565c3c0061cf0526aaaaf46ac4961ae9fd","103655cfc0a8919807a8a0741a0600771fa7b86838d5eb00f4faaadc42996d9d","90f660b66816de8018412c97a47ebd151801493dff92ae6a67dea86711530cc4");
            case "R62C" -> List.of("8471d63333ade14910d0d71e8ace297cc7fd0234418f760d01c8abe4b58e57f4","0cddf680c554736a7660842a146466566be8447816bc3a47d1e662f456593808","4bb677e312cccb6b1aaa79ff3c2778a470569372f91f941cc1cae05278370dfa","9402250cde510bcd4a88152a4396c1751887d83f19c0e6dca641ca34086dca8d","6d42824299510eaba820fe47036cf7b73904db5e333a44b06ba8407234cec3b6","3d2a5165c60f4547e64047feb332e58f2fb2a84c29cd7d441c5a2163da407353","c05a6e177730d577d6743c1740f636565c3c0061cf0526aaaaf46ac4961ae9fd","103655cfc0a8919807a8a0741a0600771fa7b86838d5eb00f4faaadc42996d9d","90f660b66816de8018412c97a47ebd151801493dff92ae6a67dea86711530cc4");
            case "R62D" -> List.of("baa45c9f21f0d99a17278276faf2c0048246ceed0b509959956f897587b551b7","b89a46adc88ab505d0b7ea383289d420bdf3bca65b0bfa67850eec9e75684da3","4f89384da3c64e3f9dfc4294dea3b3fdc9924b839416eeced4115daa3a28dca0","9402250cde510bcd4a88152a4396c1751887d83f19c0e6dca641ca34086dca8d","b249929cb2a61ec90246aff2b70ec42659e7871acc86eb64b17e259c25ab5f31","6833af533a4649ce1635fc966c8c8393ab8aa3cec5db173d7e5cd4e54b12c174","c05a6e177730d577d6743c1740f636565c3c0061cf0526aaaaf46ac4961ae9fd","103655cfc0a8919807a8a0741a0600771fa7b86838d5eb00f4faaadc42996d9d","90f660b66816de8018412c97a47ebd151801493dff92ae6a67dea86711530cc4");
            case "R63C" -> List.of("5226867b091056ccdd8070f4262b9efe9289730fc08c5980f7c780e4e1322579","9e4371781a3c9060ac8abee87c65292909d3f079488a5056ebbe7ef2f9a4badc","ff4664329c024b1aeef39b4571978e41b158d8315f9dadc59481cafae2065e42","aca099590357f32b3253fce02ba7692a904cf4784eba02c653164b7aaf8a763a","703d4c7211a097467a9e02ceba49aa7995312f931d2ca8073a01adc0461f16b4","4c66f5c87acb97e57abb7d10871270be3cba9cd8342f481f67cc07738b006a25","c05a6e177730d577d6743c1740f636565c3c0061cf0526aaaaf46ac4961ae9fd","103655cfc0a8919807a8a0741a0600771fa7b86838d5eb00f4faaadc42996d9d","90f660b66816de8018412c97a47ebd151801493dff92ae6a67dea86711530cc4");
            case "R63D" -> List.of("9e50e48522cd38c07f9c74f3c559fb6f0717f21bf3b3d6c1003d9459e2050e88","9727504497fd4c55b4749e99b9016797feb5a73b955618ae4f3636f616734749","be85ff0f04d11b62578fb61a5e2478a862c03d21fef0e07b7727bc2aab31036b","535feb347b8bbd28e09c54bcdde2a781301f84b8a593fa348a4b18e45c16adea","32f4df3f9b9d53ebc220f68e6e6a5fd047750e05fe8ad3733eb9e44fddf7d0b5","4bf2afc3a35fa7f7eba1f5e5523a71db86c7c5bb8e6faaa549ca58058c28a735","c05a6e177730d577d6743c1740f636565c3c0061cf0526aaaaf46ac4961ae9fd","103655cfc0a8919807a8a0741a0600771fa7b86838d5eb00f4faaadc42996d9d","90f660b66816de8018412c97a47ebd151801493dff92ae6a67dea86711530cc4");
            default -> throw new IllegalArgumentException("No remaining hash oracle");
    };}
    private static int[] remainingGeometry(String id) {return switch(id) {
            case "R61C" -> new int[]{653,593,2,24586};
            case "R61D" -> new int[]{820,661,2,24624};
            case "R62A" -> new int[]{1010,719,0,24624};
            case "R62B" -> new int[]{755,753,0,24774};
            case "R62C" -> new int[]{990,719,0,16394};
            case "R62D" -> new int[]{1010,778,0,16432};
            case "R63C" -> new int[]{1019,739,0,24586};
            case "R63D" -> new int[]{813,810,0,24624};
            default -> throw new IllegalArgumentException("No remaining geometry oracle");
    };}
    private static int[] remainingMissing(String id) {return switch(id) {
            case "R61C" -> new int[]{};
            case "R61D" -> new int[]{};
            case "R62A" -> new int[]{27,2,85,4,15,1010};
            case "R62B" -> new int[]{};
            case "R62C" -> new int[]{13,2,77,2,9,990};
            case "R62D" -> new int[]{13,4,80,10,7,1010};
            case "R63C" -> new int[]{5,2,49,0,10,1019};
            case "R63D" -> new int[]{};
            default -> throw new IllegalArgumentException("No remaining selected read oracle");
    };}
    private static long[][] remainingPlcs(String id) {return switch(id) {
            case "R61C" -> new long[][]{{2325794,0,18176}};
            case "R61D" -> new long[][]{{2326960,0,21056}};
            case "R62A" -> new long[][]{{2326310,0,20736},{2307560,55648,384},{2291964,20800,768}};
            case "R62B" -> new long[][]{{2326356,0,20064}};
            case "R62C" -> new long[][]{{2326284,0,21248}};
            case "R62D" -> new long[][]{{2327004,0,22336}};
            case "R63C" -> new long[][]{{2326164,0,21440}};
            case "R63D" -> new long[][]{{2326668,0,21024}};
            default -> throw new IllegalArgumentException("No remaining initial PLC oracle");
    };}
    @ParameterizedTest @ValueSource(strings={"R61C","R61D","R62A","R62B","R62C","R62D","R63C","R63D"})
    void remainingProductsAndRawQueriesMatchIndependentSourceHashes(String id)throws Exception {
        var r=CdProgrammeReader.open(media(),CdStageKey.parse(id));var t=r.terrain();var expected=remainingHashes(id);
        var data=List.of(t.blocks(),t.patterns(),t.chunks(),r.role(FG),r.role(BG),t.collision().ids(),t.collision().angles(),t.collision().heights(),t.collision().widths());
        for(int i=0;i<data.size();i++)assertEquals(expected.get(i),CdR11aProfile.sha256(data.get(i).copy()),"source role "+i);
        for(int block=0;block<t.blockCount();block++)for(int y=0;y<2;y++)for(int x=0;x<2;x++) {
            int raw=t.blocks().unsignedWord(block*8+(y*2+x)*2);var word=t.blockWord(block,x,y);
            assertEquals(raw,word.rawWord());assertEquals(raw&2047,word.patternIndex());assertEquals((raw>>>13)&3,word.paletteLine());
        }
        for(int chunk=1;chunk<=t.chunkCount();chunk++)for(int y=0;y<16;y++)for(int x=0;x<16;x++) {
            int raw=t.chunks().unsignedWord((chunk-1)*512+(y*16+x)*2);var word=t.chunkWord(chunk,x,y);
            assertEquals(raw,word.rawWord());assertEquals(raw&1023,word.renderBlockIndex());assertEquals(raw&2047,word.collisionBlockIndex());assertEquals((raw>>>13)&3,word.solidity());
        }
        for(int pattern=0;pattern<t.patternCount();pattern++)for(int y=0;y<8;y++)for(int x=0;x<8;x++) {
            int packed=t.patterns().unsignedByte(pattern*32+y*4+x/2);assertEquals((x&1)==0?packed>>>4:packed&15,t.pixel(pattern,x,y));
        }
    }
    @ParameterizedTest @ValueSource(strings={"R61C","R61D","R62A","R62B","R62C","R62D","R63C","R63D"})
    void remainingClearCopyKeepsExactDirectoryViewsAndRawActorFlags(String id)throws Exception {
        var r=CdProgrammeReader.open(media(),CdStageKey.parse(id));var t=r.terrain();int flagged=0;
        for(var plane:CdStageTerrain.Plane.values()) {
            var source=r.role(plane==CdStageTerrain.Plane.FOREGROUND?FG:BG);int w=source.unsignedByte(0)+1,h=source.unsignedByte(1)+1;assertEquals(2+w*h,source.length());
            for(int y=0;y<16;y++)for(int x=0;x<64;x++) {
                int expected=x<w&&y<h?source.unsignedByte(2+y*w+x):0; // Only the proven native $800 clear supplies these zeros.
                var cell=t.layout().nativeCell(plane,x,y);assertEquals(expected,cell.rawByte());assertEquals(expected&127,cell.chunkId());
                if(x<w&&y<h)assertEquals(cell,t.layout().cell(plane,x,y));
                if(plane==CdStageTerrain.Plane.FOREGROUND && (expected&128)!=0)flagged++;
            }
        }
        assertEquals(remainingGeometry(id)[2],flagged); // Actor flag-dependent remapping is deliberately not performed by DATA queries.
        assertEquals(CdStageTerrain.LayoutEncoding.EXACT_READ_VIEW,t.profile().layoutEncoding());
        assertEquals(CdStageTerrain.ReferencePolicy.SELECTED_KNOWN_SOURCE,t.profile().referencePolicy());
        assertThrows(IndexOutOfBoundsException.class,()->t.layout().cell(CdStageTerrain.Plane.FOREGROUND,t.profile().foregroundColumns(),0));
    }
    @ParameterizedTest @ValueSource(strings={"R61C","R61D","R62A","R62B","R62C","R62D","R63C","R63D"})
    void remainingCollisionSamplesAndEncodedSolidityCandidatesStayFinite(String id)throws Exception {
        var t=CdStageTerrain.fromProgramme(media(),CdStageKey.parse(id));int maxRaw=0,maxSolid=0;
        for(int y=0;y<t.profile().foregroundRows();y++)for(int x=0;x<t.profile().foregroundColumns();x++) {
            int chunk=t.layout().cell(CdStageTerrain.Plane.FOREGROUND,x,y).chunkId();if(chunk==0)continue;
            for(int cy=0;cy<16;cy++)for(int cx=0;cx<16;cx++) {
                var word=t.chunkWord(chunk,cx,cy);maxRaw=Math.max(maxRaw,word.collisionBlockIndex());
                if(word.solidity()!=0 && word.collisionBlockIndex()!=0) {
                    maxSolid=Math.max(maxSolid,word.collisionBlockIndex());
                    assertEquals(t.collision().ids().unsignedByte(word.collisionBlockIndex()),t.collision().idForBlock(word.collisionBlockIndex()));
                }
            }
        }
        assertEquals(remainingGeometry(id)[0],maxRaw);assertEquals(remainingGeometry(id)[1],maxSolid);
        var first=t.chunkWord(18,0,0);assertEquals(remainingGeometry(id)[3],first.rawWord());assertEquals(81,t.collision().idForBlock(first.collisionBlockIndex()));
        assertEquals(255,t.collision().angle(81));assertEquals(16,t.collision().height(81,0));assertEquals(16,t.collision().width(81,0));
        for(int cid=0;cid<256;cid++)for(int sample=0;sample<16;sample++) {
            assertEquals((byte)t.collision().heights().unsignedByte(cid*16+sample),t.collision().height(cid,sample));
            assertEquals((byte)t.collision().widths().unsignedByte(cid*16+sample),t.collision().width(cid,sample));assertEquals(t.collision().angles().unsignedByte(cid),t.collision().angle(cid));
        }
        assertThrows(IndexOutOfBoundsException.class,()->t.collision().height(256,0));assertThrows(IndexOutOfBoundsException.class,()->t.collision().width(0,16));
    }
    @ParameterizedTest @ValueSource(strings={"R61C","R61D","R62A","R62B","R62C","R62D","R63C","R63D"})
    void remainingSelectedBoundsRetainMissingStagingWithoutTailPadding(String id)throws Exception {
        var t=CdStageTerrain.fromProgramme(media(),CdStageKey.parse(id));var counts=remainingCounts(id);var bad=remainingMissing(id);
        unavailable(CdStageTerrain.ReadOwner.BLOCK_STAGING,counts[0],counts[0],()->t.blockWord(counts[0],0,0));
        unavailable(CdStageTerrain.ReadOwner.PATTERN_SOURCE,counts[1],counts[1],()->t.pixel(counts[1],0,0));
        unavailable(CdStageTerrain.ReadOwner.CHUNK_SOURCE,counts[2]+1,counts[2],()->t.chunkWord(counts[2]+1,0,0));
        unavailable(CdStageTerrain.ReadOwner.COLLISION_SOURCE,counts[7],counts[7],()->t.collision().idForBlock(counts[7]));
        if(bad.length!=0) {
            assertEquals(bad[2],t.layout().cell(CdStageTerrain.Plane.FOREGROUND,bad[0],bad[1]).chunkId());var word=t.chunkWord(bad[2],bad[3],bad[4]);assertEquals(bad[5],word.rawWord());assertEquals(0,word.solidity());
            unavailable(CdStageTerrain.ReadOwner.BLOCK_STAGING,bad[5],counts[0],()->t.blockWord(word.renderBlockIndex(),0,0));
            unavailable(CdStageTerrain.ReadOwner.COLLISION_SOURCE,bad[5],counts[7],()->t.collision().idForBlock(word.collisionBlockIndex()));
        }
        // Every known initial block word stays within the proven initial pattern prefix; no secondary PLC stitching.
        for(int i=0;i<t.blockCount();i++)for(int y=0;y<2;y++)for(int x=0;x<2;x++)assertTrue(t.blockWord(i,x,y).patternIndex()<counts[1]);
    }
    @ParameterizedTest @ValueSource(strings={"R61C","R61D","R62A","R62B","R62C","R62D","R63C","R63D"})
    void remainingInitialPlcRowsKeepOriginalSourceDestinationAndOrder(String id)throws Exception {
        var r=CdProgrammeReader.open(media(),CdStageKey.parse(id));var rows=r.loadTables().plcs().get(3);var expected=remainingPlcs(id);assertEquals(3,rows.nativeId());assertSame(r.loadTables().plcs().getFirst().body(),rows.body());assertEquals(expected.length,rows.body().entries().size());
        for(int i=0;i<expected.length;i++) {
            var entry=rows.body().entries().get(i);assertEquals(expected[i][0],entry.sourceNativeAddress());assertEquals(expected[i][1],entry.vramAddress());assertEquals(expected[i][2],entry.outputBytes());
        }
        assertEquals(r.terrain().patterns().length(),expected[0][2]);
        for(int i=1;i<expected.length;i++)assertTrue(expected[i][1]>=r.terrain().patterns().length());
        // In R62A, actual secondary destinations $D960/$5140 are descriptive and not a padded terrain pattern bank.
    }
    @ParameterizedTest @ValueSource(strings={"R61C","R61D","R62A","R62B","R62C","R62D","R63C","R63D"})
    void remainingLateFaultsRetryWithoutPublishingOrChangingMediaAuthority(String id)throws Exception {
        var p=CdR6DataLayout.Programme.valueOf(id);var r=CdProgrammeReader.open(media(),p.payload().stage());var binding=r.definition().terrain().orElseThrow();var reached=spy(r);
        var first=new IOException("remaining original graphics source unavailable");doThrow(first).when(reached).decoded(binding.graphics());clearInvocations(reached);assertSame(first,assertThrows(IOException.class,reached::terrain));verify(reached,never()).role(CHUNKS);
        var blockProduct=r.decoded(binding.blocks());doReturn(r.decoded(binding.graphics())).when(reached).decoded(binding.graphics());
        doReturn(CdAssetBytes.copyOf(new byte[]{0,0})).when(reached).role(BG);assertThrows(IllegalArgumentException.class,reached::terrain);assertSame(blockProduct,r.decoded(binding.blocks()));
        var rawFault=new AssertionError("original BG unavailable before collision role");doThrow(rawFault).when(reached).role(BG);clearInvocations(reached);assertSame(rawFault,assertThrows(AssertionError.class,reached::terrain));verify(reached,never()).role(COLLISION);
        doReturn(r.role(BG)).when(reached).role(BG);var terrain=reached.terrain();clearInvocations(reached);assertSame(terrain,reached.terrain());verify(reached,never()).role(any());verify(reached,never()).decoded(any());
        assertNotSame(terrain,CdStageTerrain.fromProgramme(media(),p.payload().stage()));
        var wrong=mock(CdProgramAssets.class,delegatesTo(media()));doReturn(media().mmd(CdNativeFile.R61A)).when(wrong).mmd(p.file());assertThrows(IOException.class,()->CdStageTerrain.fromProgramme(wrong,p.payload().stage()));
    }
    @Test void originalR63cChunkDeclarationsRetainThreeAuthenticUsaWordPairs()throws Exception {
        var t=CdStageTerrain.fromProgramme(media(),CdStagePayload.R63C.stage());
        for(int[] row:new int[][]{{0xB946,0x600A,0x2005},{0xB966,0x4014,0x000F},{0xB986,0x601E,0x600A}}) {
            assertEquals(row[1],t.chunks().unsignedWord(row[0]));assertEquals(row[2],t.chunks().unsignedWord(row[0]+2));
        }
        assertEquals(60416,t.chunks().length()); // Full ordered include/dc.w source chain, not the first $B946-byte include slice alone.
    }
}
