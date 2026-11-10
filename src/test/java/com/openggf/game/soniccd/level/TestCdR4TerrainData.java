package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.media.CdProgramAssets;
import java.io.IOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static com.openggf.game.soniccd.level.CdStageTerrain.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.*;
import static org.mockito.AdditionalAnswers.delegatesTo;

/** Native/original-byte goldens; known DATA prefixes, not a complete initial staging or water-service state. */
class TestCdR4TerrainData {
    record Fixture(Path cue,Map<CdR4DataLayout.Programme,CdProgrammeReader> readers,Map<CdR4DataLayout.Programme,CdMmdImage> images) { }
    private static Fixture retained;
    static synchronized Fixture actual()throws Exception {
        if(retained!=null)return retained;
        var root=System.getProperty("soniccd.media.root");assumeTrue(root!=null&&!root.isBlank(),"Original Sonic CD media absent");
        var cue=Path.of(root,"USA","Sonic CD (US).cue");
        try(var source=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())) {
            assertEquals(CdR4DataLayout.USA_DISC_SHA256,source.identity().normalizedSha256());
            var readers=new EnumMap<CdR4DataLayout.Programme,CdProgrammeReader>(CdR4DataLayout.Programme.class);
            var images=new EnumMap<CdR4DataLayout.Programme,CdMmdImage>(CdR4DataLayout.Programme.class);
            for(var p:CdR4DataLayout.Programme.values()) {readers.put(p,CdProgrammeReader.open(source,p.payload().stage()));images.put(p,source.mmd(p.file()));}
            retained=new Fixture(cue,Map.copyOf(readers),Map.copyOf(images));return retained;
        }
    }
    static CdProgrammeReader reader(CdR4DataLayout.Programme p)throws Exception {return actual().readers().get(p);}

    private record Expected(int blocks,int patterns,int chunks,int fgWidth,int fgHeight,int bgWidth,int bgHeight,int ids,
                            String blockSha,String patternSha,String ramSha,String querySha,int storeOffset,String storeHex,
                            int dirtyChunk,int dirtyX,int dirtyY,int dirtyWord,int dirtyId,
                            int initialUnbacked,int eligibleMax,int initialNonSolidBeyond) { }
    private static Expected expected(CdR4DataLayout.Programme p) {
        return switch(p) {
            case R41A -> new Expected(5728,19200,54272,30,6,4,6,800,
                "382fed6403c27611ea6294b65390ace46c95edeb8047abdcf4e979972a8eb4a7","307edc2f7f7ae5c6d411fd52c6f0f12948f9fcb46f01dd55f0d42d033eb5316d",
                "5793337e68d7b9459a0ad97e462441a458dc07dde13006734dcb217f670fd002","8f421813ad3d6eb7e798a620920f5c4182f363b3a2e49d35dfcfa9794a35f23f",0x19b2,"21fc0023bb42f7964e75",103,0,8,1019,-1,184,712,184);
            case R41B -> new Expected(4544,18112,54272,30,6,4,6,700,
                "f8447efdaaa04404e384b6522232883885f1de888b7943c7aa51e4a750785b93","12de0183eca4b00029c808f704824beb38d9d0905b5ca01cf8da452b7e8c5969",
                "b2e2debbf192559928451fd82065f7a0113c63675a81f001ef0d0e76588cbecf","8161e655c0295fcfd446fbd7566f27a515d5a1725bcd98ffc7274cf8d521453e",0x18f8,"21fc0023ba76f7964e75",6,9,7,604,0,144,504,0);
            case R41C -> new Expected(7456,17376,54272,30,6,4,4,800,
                "d94e6648e001edadb6e39cdeca11692a08bf8d24be64872cefc60c59e4df5ef6","c1e8785738b0c65f335527c080d1a2a1996fc3e16046e3c80d3452cc63dfd87f",
                "9695d73c790e25aadaa0bdf9ec07a9ef5db148ba9e0201f956d07785b386a6c8","3e554f5aa43c3c2c55961129b66f353c6065b4ad61e33fef34771bb77149ce7b",0x1932,"21fc0023ba76f7964e75",6,9,10,861,-1,5,757,240);
            case R41D -> new Expected(4800,18144,54272,30,6,4,4,700,
                "1d4b2087f0eddfc9e32d76235946427175f3443afc3167b3ca7687933434d86b","9bddf6e5c343fbe367f780dca7fa75b01fa86f6df2b0ec9ce88fdc4e4f54df54",
                "d5d76de09aa60a07d24dc087bd38509cf64399ac38fdc906e6d14d6c2dcf0937","2f267d23255362004ef69ec9dcd3ebdb02799ff94ca3bb250d9ae42a1dd28a11",0x1920,"21fc0023ba76f7964e75",20,10,8,2652,0,20,464,0);
            case R42A -> new Expected(3616,17504,54272,30,8,4,8,600,
                "e4efef34be22f37f3225411b6283590c6ebf71a68834277a670e8d7bb27078e7","27db26947a1370e03e57193d3b63a7c12912bb1fc908b3d05ec62af0aae9bde9",
                "8d0235f5b1805832b94b97fcc0ce03415a7afea596b97eb661ecd7951704ea51","e1690a9f30d089d5bc29501c988116c0c97d236333772021246f6b5750599bcd",0x19b2,"21fc0023ba76f7964e75",7,11,6,503,0,23,175,0);
            case R42B -> new Expected(4544,18112,54272,30,8,4,8,700,
                "f8447efdaaa04404e384b6522232883885f1de888b7943c7aa51e4a750785b93","12de0183eca4b00029c808f704824beb38d9d0905b5ca01cf8da452b7e8c5969",
                "8d0235f5b1805832b94b97fcc0ce03415a7afea596b97eb661ecd7951704ea51","aa0784d47c46d3e37bf694deef62627d0488d287c23c15cbc8a20b27fc559774",0x18f8,"21fc0023ba76f7964e75",11,2,11,604,0,42,134,0);
            case R42C -> new Expected(7456,17376,54272,30,8,4,8,800,
                "d94e6648e001edadb6e39cdeca11692a08bf8d24be64872cefc60c59e4df5ef6","c1e8785738b0c65f335527c080d1a2a1996fc3e16046e3c80d3452cc63dfd87f",
                "8d0235f5b1805832b94b97fcc0ce03415a7afea596b97eb661ecd7951704ea51","5bdedf2ea4d68fb266ff6dfef19f41cc705352f6c16975687b1603e04719aa27",0x1932,"21fc0023ba76f7964e75",6,9,10,861,-1,0,756,236);
            case R42D -> new Expected(4576,18432,54272,30,8,4,8,700,
                "934ed7af0f73cbef95d42995d1e268c9caaa8bf543dd5d977bce5a6054c56848","beba50dd97828769e7528540ee98eda0d6211081b27f845d8e22fd429a806354",
                "8d0235f5b1805832b94b97fcc0ce03415a7afea596b97eb661ecd7951704ea51","bed3998d20af1e34354e0d9499c3cf663c8341cde3ca83ce4e69cebc47f69ee8",0x1920,"21fc0023ba76f7964e75",17,0,14,2632,0,40,464,0);
            case R43C -> new Expected(7456,17376,54272,15,8,4,8,900,
                "d94e6648e001edadb6e39cdeca11692a08bf8d24be64872cefc60c59e4df5ef6","c1e8785738b0c65f335527c080d1a2a1996fc3e16046e3c80d3452cc63dfd87f",
                "1841cdbf1ca9f163c4f274f4e82bdf6b5cdc5d3cd6bf4e538e7870a248d0ff64","d8b3499969493eb73bf3c43d5d9791c8a17200982624d72cb68ed8d23d8c91b3",0x1932,"21fc0021f500f7964e75",11,6,7,920,-1,0,755,2);
            case R43D -> new Expected(5632,18144,54272,15,8,4,8,700,
                "e6d242395d75a5b2f761b5cb3e64cde0c24dbf9a799d311ffa0c0da1795e27fc","9bddf6e5c343fbe367f780dca7fa75b01fa86f6df2b0ec9ce88fdc4e4f54df54",
                "1841cdbf1ca9f163c4f274f4e82bdf6b5cdc5d3cd6bf4e538e7870a248d0ff64","1dd999f9bb8cc5811f840c2ad816f8839fdb5e399b238640f04bafc7ecfb1629",0x1920,"21fc0021f500f7964e75",0,0,0,0,-1,0,466,0);
        };
    }
    @ParameterizedTest @EnumSource(CdR4DataLayout.Programme.class)
    void originalInitialTerrainUsesTheExistingReader(CdR4DataLayout.Programme p)throws Exception {
        var reader=reader(p);var terrain=reader.terrain();var f=expected(p);
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
        assertEquals(reader.playerBinding().ordinaryZoneActWord(),reader.ordinaryZoneActWord());
        var playerSource=CdStagePlayerSource.fromProgramme(reader);
        assertSame(terrain,playerSource.terrain());assertSame(reader.sonic(),playerSource.sonic());
        assertEquals(CdR11aPlayerAssets.SourceGraph.R4,playerSource.player().sourceGraph());
        assertSame(p,reader.definition().tt().orElseThrow());
        assertEquals(CdSonicVisualAssets.SourceFormat.FRAMES_188,reader.sonic().sourceFormat());
        assertEquals(76928,reader.sonic().rawArt().length());assertEquals(82944,reader.sonic().artReadView().length());assertEquals(188,reader.sonic().mappingFrames().size());
        var foreign=p==CdR4DataLayout.Programme.R41A?CdR4DataLayout.Programme.R41B:CdR4DataLayout.Programme.R41A;
        assertThrows(IllegalArgumentException.class,()->reader.view(foreign.range("StageChunks")));
        var copy=terrain.blocks().copy();copy[0]^=1;assertEquals(f.blockSha,hash(terrain.blocks()));
    }
    @ParameterizedTest @EnumSource(CdR4DataLayout.Programme.class)
    void originalPublicIngressAndKnownQueriesPreserveNativeReadBoundaries(CdR4DataLayout.Programme p)throws Exception {
        try(var actual=open()) {
            var t=CdStageTerrain.fromProgramme(actual,p.payload().stage());var f=expected(p);var r=reader(p);
            assertNotSame(r.terrain(),t);assertNotSame(r.terrain().blocks(),t.blocks());
            var directory=r.view(p.range("StageMaps"));var image=actual().images().get(p);
            assertEquals(72,directory.length());int unbacked=0,eligibleMax=0,nonSolidBeyond=0;
            for(var plane:Plane.values()) {
                int slot=plane==Plane.FOREGROUND?0:1;var key=p.range(slot==0?"StageMapFg":"StageMapBg");var source=r.view(key);
                assertEquals(key.fileOffset(),p.range("StageMaps").fileOffset()+(short)directory.unsignedWord(slot*2));
                int width=source.unsignedByte(0)+1,height=source.unsignedByte(1)+1;assertEquals(2+width*height,source.length());
                for(int y=0;y<height;y++)for(int x=0;x<width;x++) {
                    int raw=source.unsignedByte(2+y*width+x);assertEquals(raw,t.layout().cell(plane,x,y).rawByte());
                    if((raw&127)!=0)for(int cy=0;cy<16;cy++)for(int cx=0;cx<16;cx++) {
                        var ref=t.chunkWord(raw&127,cx,cy);
                        if(ref.renderBlockIndex()>=f.blocks/8) {
                            unbacked++;unavailable(ReadOwner.BLOCK_STAGING,ref.renderBlockIndex(),f.blocks/8,()->t.blockWord(ref.renderBlockIndex(),0,0));
                        } else t.blockWord(ref.renderBlockIndex(),0,0);
                        // Static consumer predicate only; no actor bit/coordinate/physics policy inferred.
                        if(ref.collisionBlockIndex()!=0&&ref.solidity()!=0) {
                            eligibleMax=Math.max(eligibleMax,ref.collisionBlockIndex());assertTrue(ref.collisionBlockIndex()<f.ids);
                            t.collision().idForBlock(ref.collisionBlockIndex());
                        } else if(ref.collisionBlockIndex()>=f.ids) {
                            nonSolidBeyond++;unavailable(ReadOwner.COLLISION_SOURCE,ref.collisionBlockIndex(),f.ids,()->t.collision().idForBlock(ref.collisionBlockIndex()));
                        }
                    }
                }
                assertThrows(IndexOutOfBoundsException.class,()->t.layout().cell(plane,width,0));
            }
            assertEquals(f.initialUnbacked,unbacked);assertEquals(f.eligibleMax,eligibleMax);assertEquals(f.initialNonSolidBeyond,nonSolidBeyond);
            assertEquals(f.ramSha,hash(t.layout().ram()));var nativeRam=new byte[0x800];
            for(int y=0;y<16;y++)for(int x=0;x<64;x++)for(var plane:Plane.values())nativeRam[y*0x80+(plane==Plane.FOREGROUND?0:0x40)+x]=(byte)t.layout().nativeCell(plane,x,y).rawByte();
            assertEquals(f.ramSha,CdR11aProfile.sha256(nativeRam));
            for(var pair:Map.of("StageChunks",t.chunks(),"StageCollision",t.collision().ids(),"StageCollisionAngles",t.collision().angles(),"StageCollisionColumns",t.collision().heights(),"StageCollisionRows",t.collision().widths()).entrySet()) {
                var key=p.range(pair.getKey());assertArrayEquals(image.readFile(key.fileOffset(),key.length()),pair.getValue().copy());assertEquals(key.sha256(),hash(pair.getValue()));
            }
            assertEquals(f.querySha,queryHash(t));
            assertEquals(f.storeHex,HexFormat.of().formatHex(image.readFile(f.storeOffset,f.storeHex.length()/2)));
            assertEquals(5,r.definition().operands().size()-p.operands().size());
            for(var op:r.definition().operands())assertEquals(op.hex(),HexFormat.of().formatHex(image.readFile(op.offset(),op.hex().length()/2)));
        }
    }
    @ParameterizedTest @EnumSource(CdR4DataLayout.Programme.class)
    void rawUnusedReferencesAndSelectedReadBoundsRetainIndependentKnownCounts(CdR4DataLayout.Programme p)throws Exception {
        var t=reader(p).terrain();var f=expected(p);
        unavailable(ReadOwner.BLOCK_STAGING,f.blocks/8,f.blocks/8,()->t.blockWord(f.blocks/8,0,0));
        unavailable(ReadOwner.PATTERN_SOURCE,f.patterns/32,f.patterns/32,()->t.pixel(f.patterns/32,0,0));
        unavailable(ReadOwner.CHUNK_SOURCE,f.chunks/512+1,f.chunks/512,()->t.chunkWord(f.chunks/512+1,0,0));
        unavailable(ReadOwner.COLLISION_SOURCE,f.ids,f.ids,()->t.collision().idForBlock(f.ids));
        assertEquals(reader(p).view(p.range("StageCollision")).unsignedByte(f.ids-1),t.collision().idForBlock(f.ids-1));
        if(f.dirtyChunk!=0) {
            var raw=t.chunkWord(f.dirtyChunk,f.dirtyX,f.dirtyY);assertEquals(f.dirtyWord,raw.rawWord());
            if(raw.renderBlockIndex()>=f.blocks/8)unavailable(ReadOwner.BLOCK_STAGING,raw.renderBlockIndex(),f.blocks/8,()->t.blockWord(raw.renderBlockIndex(),0,0));
            else t.blockWord(raw.renderBlockIndex(),0,0);
            if(raw.collisionBlockIndex()>=f.ids)unavailable(ReadOwner.COLLISION_SOURCE,raw.collisionBlockIndex(),f.ids,()->t.collision().idForBlock(raw.collisionBlockIndex()));
            else assertEquals(f.dirtyId,t.collision().idForBlock(raw.collisionBlockIndex()));
        }
        assertThrows(IndexOutOfBoundsException.class,()->t.chunkWord(0,0,0));assertThrows(IndexOutOfBoundsException.class,()->t.blockWord(-1,0,0));
        assertThrows(IndexOutOfBoundsException.class,()->t.pixel(-1,0,0));assertThrows(IndexOutOfBoundsException.class,()->t.collision().idForBlock(-1));
        assertThrows(IndexOutOfBoundsException.class,()->t.chunkWord(1,16,0));assertThrows(IndexOutOfBoundsException.class,()->t.blockWord(0,2,0));
        assertThrows(IndexOutOfBoundsException.class,()->t.collision().angle(256));assertThrows(IndexOutOfBoundsException.class,()->t.collision().width(0,16));
    }
    @ParameterizedTest @EnumSource(CdR4DataLayout.Programme.class)
    void genuineProductAndTruncatedRoleFaultsPublishOnlyAfterSuccessfulRetry(CdR4DataLayout.Programme p)throws Exception {
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
        for(var p:CdR4DataLayout.Programme.values()) {
            var original=reader(p);var definition=spy(original.definition());doReturn(Optional.empty()).when(definition).terrain();
            var r=spy(constructor.newInstance(definition,actual().images().get(p),original.discSha()));
            assertEquals(p.range("StageChunks").length(),r.view(p.range("StageChunks")).length());clearInvocations(r);
            assertEquals(p.name()+" has no reviewed terrain DATA binding",assertThrows(UnsupportedOperationException.class,r::terrain).getMessage());
            verify(r,never()).decoded(any());verify(r,never()).role(any());assertEquals(188,r.sonic().mappingFrames().size());
        }
    }
    @Test void realForeignProgrammeImagesFailAuthenticationBeforePublicTerrainIngress()throws Exception {
        try(var actual=open()) {
            for(var p:CdR4DataLayout.Programme.values()) {
                var wrong=p==CdR4DataLayout.Programme.R41A?CdR4DataLayout.Programme.R41B:CdR4DataLayout.Programme.R41A;
                var source=mock(CdProgramAssets.class,delegatesTo(actual));doReturn(actual.mmd(wrong.file())).when(source).mmd(p.file());
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
    private static CdProgramAssets open()throws Exception {return CdProgramAssets.open(actual().cue(),Optional.of(CdReleaseVariant.USA),Optional.empty());}
    private static void unavailable(ReadOwner owner,int index,int count,org.junit.jupiter.api.function.Executable call) {
        var failure=assertThrows(UnavailableRead.class,call);assertEquals(owner,failure.owner());assertEquals(index,failure.index());assertEquals(count,failure.availableCount());
    }
}
