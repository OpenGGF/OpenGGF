package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.media.CdProgramAssets;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static com.openggf.game.soniccd.level.CdStageTerrain.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.AdditionalAnswers.delegatesTo;

/** Goldens independently derived from pinned native consumers/original media; never runtime input. */
class TestCdR8RemainingTerrainData {
    private record Expected(int blocks,int patterns,int chunks,int fgWidth,int fgHeight,int bgWidth,int bgHeight,int ids,
                            String blockSha,String patternSha,String ramSha,String querySha,int pointers,String pointerHex,
                            int dirtyChunk,int dirtyX,int dirtyY,int dirtyWord,int dirtyId) { }
    private static Expected expected(CdR8DataLayout.Programme p) {
        return switch(p) {
            case R81C -> new Expected(4128,18112,56832,32,8,24,3,800,
                "e8b27f355f17261d556125fc044a3d176ca5169886b443e3da893cb52e87d4b2","7366548bf9ab0ad374c8f99107f28ad9550524d8c4722df3df0720f14f9549b7",
                "14183477fc92ed1d17da688c750dee0a6c0f0daf986e95d6175805b2a1f3a18a","ebb42a768d689a324daf7263443dda7eda36d02a4dd620809774949b83339f3d",0x1b3c,"002378ca",17,0,8,4618,81);
            case R81D -> new Expected(3872,18816,56832,32,8,24,3,700,
                "584237b712ba37c84669724be2665c9ebaca34700b0fc425917bfcc14cd3153d","ff9797bf114465865cf9f0c823d1e1870ffdbff278f51ffed795d75b69c9dbd1",
                "14183477fc92ed1d17da688c750dee0a6c0f0daf986e95d6175805b2a1f3a18a","ffcb299f7fea98c01a1110ba2379f3034b1891d7a8480eb7020a0715dc1d1c99",0x1c84,"00236134",7,11,6,503,0);
            case R82A -> new Expected(4480,18752,55808,33,8,24,2,600,
                "5a5c1c0ce372a291d33f2b560f462935cc4fa6a51593a02dbde58226e20daca9","c0a7645690bbe1b8d68aa1fd183667d1595bb4d786f894058010195677cb4b2c",
                "60efaade4ff8c7c0f90ced65b23f6afe7ab336750be3c6f8d5dfe9332d2df4c2","da01eeb7f73a4fe53f31cc9237b4b859811f4f23819629a466a7590d93ba6996",0x1a82,"00235a8c",0,0,0,0,0);
            case R82B -> new Expected(4256,18368,56320,33,8,24,2,600,
                "b83bba2b730235280b3917e776d4d505c0a16618b6d714cbca45efa42bf77bad","59ca7f9275855e02f74b0bd41cc4bdbd68b6817ba17abc0443aeb7f4a42c3259",
                "45ccdd0a6cf3c2fe1beb3db210fa23ed638880cf1b2baf026888186120d188c5","097f56a60c69c76c6f86be83953a1b2bcbecce54e56c2dd872028b42a55854c9",0x1a0a,"00238d7e",0,0,0,0,0);
            case R82C -> new Expected(4864,18624,55808,33,8,24,2,700,
                "eba8120402b6c7d859be598838cc78a6438b92081f509420d247fd715ff39257","59ddb6f0c766c81a90841b5ab2575c33055309562733824383d10a0523d84741",
                "9e13d1949dd438c983d64e178643d39de4dd877f75e12ae3edb569f714ec1c6f","318c57e76c98c939e77458489a15461112be7d38e2e6ccd83c917df39b4b3cc0",0x1b3c,"00238d7e",0,0,0,0,0);
            case R82D -> new Expected(4416,19360,55808,33,8,24,2,700,
                "5b69c5687bf5a41523f72e13adb94fe99047cd0189f114d7eec1169b611d2009","01f1fea3b8af45a7ea74e603d51912e70e9aeb0e81bd71b946307b8b6bec3abd",
                "9e13d1949dd438c983d64e178643d39de4dd877f75e12ae3edb569f714ec1c6f","900055df43cbbdf6d0d9ec5b5d2cfbc3981c681f2b67ecdaa0e957b9aa1941f3",0x1c84,"00238d7e",0,0,0,0,0);
            case R83C -> new Expected(3488,17056,56832,16,4,9,2,700,
                "4ae56cd25fb4f40f2b3dcd2f12a9977c2d299a80bb76dd41dd4fd92973032536","0e70bb62852391cf1ae5cb7ce161be2e7bf9f843d098e08609ff749001da66c9",
                "2657fcc877fc666b57c2b79cb9049cd25baeac75da8662c544726228157a6aa5","c20fa309f46e7a9a5ed6e51113fbaac039cad7e27d6731f6f0d66341fb21bb4e",0x1b3c,"00239426",4,4,9,464,0);
            case R83D -> new Expected(3840,16224,56832,16,4,9,2,700,
                "ab708e910ec50e3cf715b52fcca2a1ee17142a27c015367631b9c651f40df0f9","708ea54f7603982559f50a6a89e2eccafbdeccd575c7db98734a6d32723756d4",
                "2657fcc877fc666b57c2b79cb9049cd25baeac75da8662c544726228157a6aa5","15a62b18a234733dcfa58d05c872abcec1a99d0c02298397583ac9b5af25e08f",0x1c84,"00238e78",4,5,9,498,0);
            default -> throw new IllegalArgumentException("This independent census covers the remaining eight originals");
        };
    }
    @ParameterizedTest @EnumSource(value=CdR8DataLayout.Programme.class,names={"R81A","R81B"},mode=EnumSource.Mode.EXCLUDE)
    void originalInitialTerrainUsesTheExistingReader(CdR8DataLayout.Programme p)throws Exception {
        var reader=TestCdR8DataLayout.reader(p);var terrain=reader.terrain();var f=expected(p);
        assertSame(terrain,reader.terrain());assertEquals(p.fileSha256(),reader.mmdSha());assertEquals(p.file(),reader.file());
        assertEquals(f.blocks/8,terrain.blockCount());assertEquals(f.patterns/32,terrain.patternCount());assertEquals(f.chunks/512,terrain.chunkCount());
        assertEquals(f.blockSha,hash(terrain.blocks()));assertEquals(f.patternSha,hash(terrain.patterns()));
        assertArrayEquals(reader.decoded(p.range("StageBlocks")).bytes().copy(),terrain.blocks().copy());
        assertArrayEquals(reader.decoded(p.range("StageGfx")).bytes().copy(),terrain.patterns().copy());
        assertEquals(f.fgWidth,terrain.layout().columns(Plane.FOREGROUND));assertEquals(f.fgHeight,terrain.layout().rows(Plane.FOREGROUND));
        assertEquals(f.bgWidth,terrain.layout().columns(Plane.BACKGROUND));assertEquals(f.bgHeight,terrain.layout().rows(Plane.BACKGROUND));
        assertEquals(f.ids,terrain.collision().ids().length());assertEquals(LayoutEncoding.EXACT_READ_VIEW,terrain.profile().layoutEncoding());
        assertEquals(ReferencePolicy.SELECTED_KNOWN_SOURCE,terrain.profile().referencePolicy());
        assertThrows(UnsupportedOperationException.class,terrain::shape);assertThrows(UnsupportedOperationException.class,reader::terrainShape);
        assertThrows(UnsupportedOperationException.class,reader::ordinaryZoneActWord);
        assertEquals("R3 requires retained 167-frame visuals",assertThrows(IllegalArgumentException.class,()->CdStagePlayerSource.fromProgramme(reader)).getMessage());
        assertEquals(59232,reader.sonic().rawArt().length());assertEquals(188,reader.sonic().mappingFrames().size());
        assertThrows(IllegalArgumentException.class,()->reader.view(CdR8DataLayout.Programme.R81A.range("StageChunks")));
        var copy=terrain.blocks().copy();copy[0]^=1;assertEquals(f.blockSha,hash(terrain.blocks()));
    }
    @ParameterizedTest @EnumSource(value=CdR8DataLayout.Programme.class,names={"R81A","R81B"},mode=EnumSource.Mode.EXCLUDE)
    void originalPublicIngressDirectoryCopyAndCompleteQueriesMatchIndependentNativeGoldens(CdR8DataLayout.Programme p)throws Exception {
        try(var actual=open()) {
            var t=CdStageTerrain.fromProgramme(actual,p.payload().stage());var f=expected(p);var r=TestCdR8DataLayout.reader(p);
            assertNotSame(r.terrain(),t);assertNotSame(r.terrain().blocks(),t.blocks());
            var directory=r.view(p.range("StageMaps"));var image=TestCdR8DataLayout.actual().images().get(p);
            assertEquals(p==CdR8DataLayout.Programme.R82A?4:72,directory.length());
            for(var plane:Plane.values()) {
                int slot=plane==Plane.FOREGROUND?0:1;var key=p.range(slot==0?"StageMapFg":"StageMapBg");var source=r.view(key);
                assertEquals(key.fileOffset(),p.range("StageMaps").fileOffset()+(short)directory.unsignedWord(slot*2));
                int width=source.unsignedByte(0)+1,height=source.unsignedByte(1)+1;assertEquals(2+width*height,source.length());
                for(int y=0;y<height;y++)for(int x=0;x<width;x++) {
                    int raw=source.unsignedByte(2+y*width+x);assertEquals(raw,t.layout().cell(plane,x,y).rawByte());
                    if((raw&127)!=0)for(int cy=0;cy<16;cy++)for(int cx=0;cx<16;cx++) {
                        var ref=t.chunkWord(raw&127,cx,cy);assertTrue(ref.renderBlockIndex()<f.blocks/8);assertTrue(ref.collisionBlockIndex()<f.ids);
                        t.blockWord(ref.renderBlockIndex(),0,0);t.collision().idForBlock(ref.collisionBlockIndex());
                    }
                }
                assertThrows(IndexOutOfBoundsException.class,()->t.layout().cell(plane,width,0));
            }
            assertEquals(f.ramSha,hash(t.layout().ram()));var nativeRam=new byte[0x800];
            for(int y=0;y<16;y++)for(int x=0;x<64;x++)for(var plane:Plane.values())nativeRam[y*0x80+(plane==Plane.FOREGROUND?0:0x40)+x]=(byte)t.layout().nativeCell(plane,x,y).rawByte();
            assertEquals(f.ramSha,CdR11aProfile.sha256(nativeRam));
            for(var pair:Map.of("StageChunks",t.chunks(),"StageCollision",t.collision().ids(),"StageCollisionAngles",t.collision().angles(),"StageCollisionColumns",t.collision().heights(),"StageCollisionRows",t.collision().widths()).entrySet()) {
                var key=p.range(pair.getKey());assertArrayEquals(image.readFile(key.fileOffset(),key.length()),pair.getValue().copy());assertEquals(key.sha256(),hash(pair.getValue()));
            }
            assertEquals(f.querySha,queryHash(t));assertEquals(-15,t.collision().height(57,8));assertEquals(-14,t.collision().width(57,14));
            assertEquals(15,t.pixel(1,6,1));assertEquals(14,t.pixel(1,7,1));
            assertEquals("7000103900ff1506e54821fb0006f7964e75",HexFormat.of().formatHex(image.readFile(f.pointers-18,18)));
            assertEquals(f.pointerHex.repeat(8),HexFormat.of().formatHex(image.readFile(f.pointers,32)));
            assertEquals(6,r.definition().operands().size()-p.operands().size());
            for(var op:r.definition().operands())assertEquals(op.hex(),HexFormat.of().formatHex(image.readFile(op.offset(),op.hex().length()/2)));
        }
    }
    @ParameterizedTest @EnumSource(value=CdR8DataLayout.Programme.class,names={"R81A","R81B"},mode=EnumSource.Mode.EXCLUDE)
    void rawUnusedReferencesAndSelectedReadBoundsRetainIndependentKnownCounts(CdR8DataLayout.Programme p)throws Exception {
        var t=TestCdR8DataLayout.reader(p).terrain();var f=expected(p);
        unavailable(ReadOwner.BLOCK_STAGING,f.blocks/8,f.blocks/8,()->t.blockWord(f.blocks/8,0,0));
        unavailable(ReadOwner.PATTERN_SOURCE,f.patterns/32,f.patterns/32,()->t.pixel(f.patterns/32,0,0));
        unavailable(ReadOwner.CHUNK_SOURCE,f.chunks/512+1,f.chunks/512,()->t.chunkWord(f.chunks/512+1,0,0));
        unavailable(ReadOwner.COLLISION_SOURCE,f.ids,f.ids,()->t.collision().idForBlock(f.ids));
        assertEquals(0,t.collision().idForBlock(f.ids-1));
        if(f.dirtyChunk!=0) {
            var raw=t.chunkWord(f.dirtyChunk,f.dirtyX,f.dirtyY);assertEquals(f.dirtyWord,raw.rawWord());
            unavailable(ReadOwner.BLOCK_STAGING,raw.renderBlockIndex(),f.blocks/8,()->t.blockWord(raw.renderBlockIndex(),0,0));
            assertEquals(f.dirtyId,t.collision().idForBlock(raw.collisionBlockIndex()));
        }
        assertThrows(IndexOutOfBoundsException.class,()->t.chunkWord(0,0,0));assertThrows(IndexOutOfBoundsException.class,()->t.blockWord(-1,0,0));
        assertThrows(IndexOutOfBoundsException.class,()->t.pixel(-1,0,0));assertThrows(IndexOutOfBoundsException.class,()->t.collision().idForBlock(-1));
        assertThrows(IndexOutOfBoundsException.class,()->t.chunkWord(1,16,0));assertThrows(IndexOutOfBoundsException.class,()->t.blockWord(0,2,0));
        assertThrows(IndexOutOfBoundsException.class,()->t.collision().angle(256));assertThrows(IndexOutOfBoundsException.class,()->t.collision().width(0,16));
    }
    @ParameterizedTest @EnumSource(value=CdR8DataLayout.Programme.class,names={"R81A","R81B"},mode=EnumSource.Mode.EXCLUDE)
    void genuineProductAndTruncatedRoleFaultsPublishOnlyAfterSuccessfulRetry(CdR8DataLayout.Programme p)throws Exception {
        try(var source=open()) {
            var r=spy(CdProgrammeReader.open(source,p.payload().stage()));var key=p.range("StageGfx");var fault=new IOException("Selected genuine product fault");
            doThrow(fault).when(r).decoded(key);assertSame(fault,assertThrows(IOException.class,r::terrain));var blocks=r.decoded(p.range("StageBlocks"));
            doCallRealMethod().when(r).decoded(key);var t=r.terrain();assertSame(t,r.terrain());assertSame(blocks,r.decoded(p.range("StageBlocks")));verify(r,times(2)).decoded(key);
            for(var role:List.of(CdProgrammeReader.Role.CHUNKS,CdProgrammeReader.Role.FG,CdProgrammeReader.Role.BG,CdProgrammeReader.Role.COLLISION,CdProgrammeReader.Role.ANGLE,CdProgrammeReader.Role.HEIGHT,CdProgrammeReader.Role.WIDTH)) {
                var probe=spy(CdProgrammeReader.open(source,p.payload().stage()));var bytes=probe.role(role);
                doReturn(bytes.slice(0,bytes.length()-1)).when(probe).role(role);assertThrows(IllegalArgumentException.class,probe::terrain);
                doReturn(bytes).when(probe).role(role);var retried=probe.terrain();assertSame(retried,probe.terrain());assertEquals(expected(p).querySha,queryHash(retried));
            }
        }
    }
    /** Conservative post-authentication unsupported-definition control; genuine bytes still authenticate. */
    @Test void authenticMediaWithoutSelectedTerrainBindingRejectsBeforeAnyProductOrRoleRead()throws Exception {
        var constructor=CdProgrammeReader.class.getDeclaredConstructor(CdProgrammeDefinition.class,CdMmdImage.class,String.class);constructor.setAccessible(true);
        for(var p:CdR8DataLayout.Programme.values())if(p!=CdR8DataLayout.Programme.R81A&&p!=CdR8DataLayout.Programme.R81B) {
            var original=TestCdR8DataLayout.reader(p);var definition=spy(original.definition());doReturn(Optional.empty()).when(definition).terrain();
            var r=spy(constructor.newInstance(definition,TestCdR8DataLayout.actual().images().get(p),original.discSha()));
            assertEquals(p.range("StageChunks").length(),r.view(p.range("StageChunks")).length());clearInvocations(r);
            assertEquals(p.name()+" has no reviewed terrain DATA binding",assertThrows(UnsupportedOperationException.class,r::terrain).getMessage());
            verify(r,never()).decoded(any());verify(r,never()).role(any());assertEquals(188,r.sonic().mappingFrames().size());
        }
    }
    @Test void realForeignProgrammeImagesFailAuthenticationBeforePublicTerrainIngress()throws Exception {
        try(var actual=open()) {
            for(var p:CdR8DataLayout.Programme.values())if(p!=CdR8DataLayout.Programme.R81A&&p!=CdR8DataLayout.Programme.R81B) {
                var source=mock(CdProgramAssets.class,delegatesTo(actual));doReturn(actual.mmd(CdR8DataLayout.Programme.R81A.file())).when(source).mmd(p.file());
                assertEquals("Original USA "+p.name()+" image/header differs",assertThrows(IOException.class,()->CdStageTerrain.fromProgramme(source,p.payload().stage())).getMessage());
            }
        }
    }
    private static String queryHash(CdStageTerrain t)throws Exception {
        var hash=MessageDigest.getInstance("SHA-256");
        for(int c=1;c<=t.chunkCount();c++)for(int y=0;y<16;y++)for(int x=0;x<16;x++) {
            var r=t.chunkWord(c,x,y);ints(hash,r.rawWord(),r.renderBlockIndex(),r.collisionBlockIndex(),r.flipX()?1:0,r.flipY()?1:0,r.solidity());
        }
        for(int b=0;b<t.blockCount();b++)for(int y=0;y<2;y++)for(int x=0;x<2;x++) {
            var r=t.blockWord(b,x,y);ints(hash,r.rawWord(),r.patternIndex(),r.flipX()?1:0,r.flipY()?1:0,r.paletteLine(),r.priority()?1:0);
        }
        for(int p=0;p<t.patternCount();p++)for(int y=0;y<8;y++)for(int x=0;x<8;x++)ints(hash,t.pixel(p,x,y));
        for(int i=0;i<t.collision().ids().length();i++)ints(hash,t.collision().idForBlock(i));
        for(int i=0;i<256;i++)ints(hash,t.collision().angle(i));
        for(int i=0;i<256;i++)for(int sample=0;sample<16;sample++)ints(hash,t.collision().height(i,sample));
        for(int i=0;i<256;i++)for(int sample=0;sample<16;sample++)ints(hash,t.collision().width(i,sample));
        return HexFormat.of().formatHex(hash.digest());
    }
    private static void ints(MessageDigest hash,int...values) {for(int v:values)hash.update(new byte[]{(byte)(v>>>24),(byte)(v>>>16),(byte)(v>>>8),(byte)v});}
    private static String hash(CdAssetBytes bytes) {return CdR11aProfile.sha256(bytes.copy());}
    private static CdProgramAssets open()throws Exception {return CdProgramAssets.open(TestCdR8DataLayout.actual().cue(),Optional.of(CdReleaseVariant.USA),Optional.empty());}
    private static void unavailable(ReadOwner owner,int index,int count,org.junit.jupiter.api.function.Executable call) {
        var failure=assertThrows(UnavailableRead.class,call);assertEquals(owner,failure.owner());assertEquals(index,failure.index());assertEquals(count,failure.availableCount());
    }
}
