package com.openggf.game.soniccd.level;
import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.media.*;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static com.openggf.game.soniccd.level.CdStageTerrain.*;
/** Independent N1ecc/native-style + original-media INITIAL DATA goldens; no residency/World. */
class TestCdR5RemainingTerrainData {
    record Facts(int blocks,int patterns,int chunks,int fgW,int fgH,int bgW,int bgH,String blocksSha,String gfxSha,String chunksSha,String collisionSha,String querySha,int hId,int hSample,int hValue,int wId,int wSample,int wValue,int badBlock,int badX,int badY,int badWord,int badPattern,int descriptor,int blockLoader,int mapsAt,String mapLiteral,int collisionTable,int queueCopy,int plcBody,int[] plcSources,int[] plcDestinations) {}
    private static Facts expected(CdR5DataLayout.Programme p) {return switch(p) {
        case R51C -> new Facts(760,688,109,44,5,2,2,"d326748d13deccdfb660a84675572934b669e8711a9cc38efe7a2eb63e73c361","853c75234035355c460741c3ef267079f69f115290c316d90c5600e8b7d5e87b","2c0956f2d4d1a20fae87074bfb60c6493cc584e019600ffb272ca8d5b3674508","b59466e8956955561be57a3a9e65e2e5aaecb861748879df6398b49f3666c6a6","e73f44a0a8036d7b2bc67397cdfa30e4db777fee13b442606b9e6384ca0c41b2",57,8,-15,57,14,-14,742,0,0,25264,688,63574,13440,13508,"47f8a000323c01ff700026c051c9fffc47f8a00072006100000847f8a04072027000d04143f9002379543031000043f1000072003401121914193001204b10d951c8fffc47eb008051cafff04e75",6584,9434,63628,new int[]{232922,246078},new int[]{0,18944});
        case R51D -> new Facts(792,691,109,44,5,2,2,"01edc91b0b03a47dc717d92498da7225529601222786d79adbf457e2ff5c5d26","d90e2b1e27fa3b605f69db2237c88539afcbdf0dc8798500a256be88db1829cf","9b0463107bae9974bfe903778f73e0620312a66709131cb0928b861c8cfc7ebc","88fe27f688bf7f474a1be9b43eba6f4e86c3ed78b00bf5c62e9e9819e23614d0","c383a6f1d2b20e2e157ad0477f8aa48409b6161e9fbe9f189f0a6c4984f96767",57,8,-15,57,14,-14,-1,0,0,0,-1,63822,13688,13756,"47f8a000323c01ff700026c051c9fffc47f8a00072006100000847f8a04072027000d04143f9002379543031000043f1000072003401121914193001204b10d951c8fffc47eb008051cafff04e75",6682,9532,63876,new int[]{233170,246872},new int[]{0,19072});
        case R52A -> new Facts(780,716,113,44,5,55,3,"88cb05e79353c57c571a87bdc4da24028168d0aca43e7fb70543f48ed49babdd","ea619c3857d9caeaee1ce3b6e29184c97d023249fd4ac4c897a0bf48a0744dc2","ac30614ca68bff639516a722ca9ff8f0d090254a4cb768caba3a4f9d859ac60f","d14d3e744b5bbf816d48e2d36f15def215028f45552ea322107362b731e940fd","b894f675e86f488b8f5edc68d5847e69db11b272a1336661fe157ed578c070d0",57,8,-15,57,14,-14,-1,0,0,0,-1,63520,13912,13980,"47f8a000323c01ff700026c051c9fffc47f8a00072006100000847f8a04072027000d04143f9002379543031000043f1000072003401121914193001204b10d951c8fffc47eb008051cafff04e75",6708,9558,63574,new int[]{233150,247070,210408},new int[]{0,19264,55648});
        case R52B -> new Facts(836,690,108,44,5,2,2,"99323cc173bdc9b87c8b433a710afc13badb280c8f81f2efa1e0da2d13d6d484","7dca52adfd393fea56dde1854b2056878af08ec3715b7d294a7e481c1b014030","45152c77a513969e35c56cbaae287f112e87b21e8e44126e326692594b50942a","6750f5919393d1fd306d461fd4d1264e62f3d183b8b28448699dfab25079fdfe","53292d1e2a3b17d23f70d323ccfbba6862ed9969703ecc4debd83aef6d771123",57,8,-15,57,14,-14,821,1,0,25266,690,63266,13644,13726,"47f8a000323c01ff700026c051c9fffc47f8a00072006100000847f8a04072027000d04143f9002379543031000043f1000072003401121914193001204b10d951c8fffc47eb008051cafff04e75",6590,9440,63320,new int[]{233422,246134},new int[]{0,16736});
        case R52C -> new Facts(760,688,108,44,5,2,2,"d326748d13deccdfb660a84675572934b669e8711a9cc38efe7a2eb63e73c361","853c75234035355c460741c3ef267079f69f115290c316d90c5600e8b7d5e87b","ae4e9de3926977b338514cc3bb943e7008d8ed1994799a0d8de0985283b7de6d","b59466e8956955561be57a3a9e65e2e5aaecb861748879df6398b49f3666c6a6","2a70afa176b3a1ce57a76d0e9745e9d6f882243211033297c4b774796bf08df4",57,8,-15,57,14,-14,742,0,0,25264,688,63042,13434,13502,"47f8a000323c01ff700026c051c9fffc47f8a00072006100000847f8a04072027000d04143f9002379543031000043f1000072003401121914193001204b10d951c8fffc47eb008051cafff04e75",6584,9434,63096,new int[]{232922,246078},new int[]{0,18944});
        case R52D -> new Facts(792,691,108,44,5,2,2,"01edc91b0b03a47dc717d92498da7225529601222786d79adbf457e2ff5c5d26","d90e2b1e27fa3b605f69db2237c88539afcbdf0dc8798500a256be88db1829cf","d2f3174bb89d9cd37bd8a305312e5855ae7a1d0c0900262ba710d88c29dfd006","88fe27f688bf7f474a1be9b43eba6f4e86c3ed78b00bf5c62e9e9819e23614d0","31f923e969891bdb0921ef71c3b5fa244ad9df3293ee03c712bd43fd66e9fce5",57,8,-15,57,14,-14,-1,0,0,0,-1,63290,13682,13750,"47f8a000323c01ff700026c051c9fffc47f8a00072006100000847f8a04072027000d04143f9002379543031000043f1000072003401121914193001204b10d951c8fffc47eb008051cafff04e75",6682,9532,63344,new int[]{233170,246872},new int[]{0,19072});
        case R53C -> new Facts(760,688,108,16,5,2,2,"cb9e2086805cac16084f0c53b31b5f13bd8548025a099bad8193a25a2c536a4d","853c75234035355c460741c3ef267079f69f115290c316d90c5600e8b7d5e87b","30188251d212d71603b603d8486bac23a3d086717c770e5bddc27c8f59540da2","e85c5b7896463bbc2a8fb28487b3b96ee8cfb8203b844039149aa1d1ca361f68","6887a0693690a01149e5d91eb9924df254dde7a2ec58418b845fecb7c7fae862",57,8,-15,57,14,-14,742,0,0,25264,688,64994,13116,13184,"47f8a000323c01ff700026c051c9fffc47f8a00072006100000847f8a04072027000d04143f900237a703031000043f1000072003401121914193001204b10d951c8fffc47eb008051cafff04e75",6560,9258,65048,new int[]{233090,247900},new int[]{0,18944});
        case R53D -> new Facts(792,691,104,16,5,2,2,"01edc91b0b03a47dc717d92498da7225529601222786d79adbf457e2ff5c5d26","d90e2b1e27fa3b605f69db2237c88539afcbdf0dc8798500a256be88db1829cf","bbd85bf406c199e9f55d4924bc1b8b6caa4b6d202405d1a5d148a02c6f57cfc0","88fe27f688bf7f474a1be9b43eba6f4e86c3ed78b00bf5c62e9e9819e23614d0","f742d93529be3fb7b0748356ea1c356269199640d4f98a6026e1e0729fff6c4e",57,8,-15,57,14,-14,-1,0,0,0,-1,65214,13336,13404,"47f8a000323c01ff700026c051c9fffc47f8a00072006100000847f8a04072027000d04143f900237a583031000043f1000072003401121914193001204b10d951c8fffc47eb008051cafff04e75",6658,9356,65268,new int[]{233332,247034},new int[]{0,19072});
        default -> throw new IllegalArgumentException("Remaining-profile fixture only");
    };}
    private static CdProgrammeReader reader(CdR5DataLayout.Programme p)throws Exception {return TestCdR5DataLayout.actual().readers().get(p);}
    private static CdProgramAssets open()throws Exception {return CdProgramAssets.open(TestCdR5DataLayout.actual().cue(),Optional.of(CdReleaseVariant.USA),Optional.empty());}
    @ParameterizedTest @EnumSource(value=CdR5DataLayout.Programme.class,mode=EnumSource.Mode.EXCLUDE,names={"R51A","R51B"})
    void authenticatedRemainingInitialTerrainBindsThroughCommonReader(CdR5DataLayout.Programme p)throws Exception {
        var r=reader(p);assertEquals(p.payload().stage(),r.stage());assertEquals(p.fileSha256(),r.mmdSha());var f=expected(p);var t=r.terrain();assertSame(t,r.terrain());
        assertEquals(f.blocks,t.blockCount());assertEquals(f.patterns,t.patternCount());assertEquals(f.chunks,t.chunkCount());assertEquals(800,t.collision().ids().length());
        assertEquals(f.blocksSha,hash(t.blocks()));assertEquals(f.gfxSha,hash(t.patterns()));assertEquals(f.chunksSha,hash(t.chunks()));assertEquals(f.collisionSha,hash(t.collision().ids()));
        assertEquals(f.fgW,t.layout().columns(Plane.FOREGROUND));assertEquals(f.fgH,t.layout().rows(Plane.FOREGROUND));assertEquals(f.bgW,t.layout().columns(Plane.BACKGROUND));assertEquals(f.bgH,t.layout().rows(Plane.BACKGROUND));
        assertThrows(UnsupportedOperationException.class,t::shape);assertThrows(UnsupportedOperationException.class,r::terrainShape);assertThrows(UnsupportedOperationException.class,r::ordinaryZoneActWord);assertThrows(UnsupportedOperationException.class,()->CdStagePlayerSource.fromProgramme(r));
    }
    @ParameterizedTest @EnumSource(value=CdR5DataLayout.Programme.class,mode=EnumSource.Mode.EXCLUDE,names={"R51A","R51B"})
    void publicIngressCompleteQueriesMatchIndependentNativeGoldenAndSignedSamples(CdR5DataLayout.Programme p)throws Exception {
        try(var actual=open()) {var t=CdStageTerrain.fromProgramme(actual,p.payload().stage());var f=expected(p);assertNotSame(reader(p).terrain(),t);assertEquals(f.querySha,queryHash(t));
            assertEquals(f.hValue,t.collision().height(f.hId,f.hSample));assertEquals(f.wValue,t.collision().width(f.wId,f.wSample));assertTrue(f.hValue<0);assertTrue(f.wValue<0);
            for(var plane:Plane.values())for(int y=0;y<16;y++)for(int x=0;x<64;x++) {var c=t.layout().nativeCell(plane,x,y);assertEquals(c.rawByte()&127,c.chunkId());assertEquals((c.rawByte()&128)!=0,c.loopFlag());}
            var directory=reader(p).view(p.range("StageMapsDirectory"));
            for(var plane:Plane.values()) {int slot=plane==Plane.FOREGROUND?0:1;var key=p.range("StageMaps"+slot+"Read");assertEquals(key.fileOffset(),p.range("StageMapsDirectory").fileOffset()+(short)directory.unsignedWord(slot*2));
                var bytes=reader(p).view(key);int w=t.layout().columns(plane),h=t.layout().rows(plane);assertEquals(2+w*h,bytes.length());for(int y=0;y<h;y++)for(int x=0;x<w;x++)assertEquals(bytes.unsignedByte(2+y*w+x),t.layout().cell(plane,x,y).rawByte());}
        }
    }
    @ParameterizedTest @EnumSource(value=CdR5DataLayout.Programme.class,mode=EnumSource.Mode.EXCLUDE,names={"R51A","R51B"})
    void literalLoadStoreDescriptorAndInitialPlcRowsRemainSourceBound(CdR5DataLayout.Programme p)throws Exception {
        var f=expected(p);var r=reader(p);var image=TestCdR5DataLayout.actual().images().get(p);
        assertEquals(f.mapLiteral,HexFormat.of().formatHex(image.readFile(f.mapsAt,f.mapLiteral.length()/2)));
        assertEquals("7000103900ff1506e54821fb0006f7964e75",HexFormat.of().formatHex(image.readFile(f.collisionTable-18,18)));
        assertEquals(HexFormat.of().formatHex(java.nio.ByteBuffer.allocate(4).putInt(0x200000+p.range("StageCollision").fileOffset()).array()).repeat(8),HexFormat.of().formatHex(image.readFile(f.collisionTable,32)));
        assertEquals("2f0a588a221a0281003fffff204149f900ff2000",HexFormat.of().formatHex(image.readFile(f.blockLoader,20)));
        var index=CdAssetBytes.copyOf(image.readFile(f.descriptor,12));assertEquals(0x200000+p.range("StageBlocks").fileOffset(),index.unsignedLong(4)&0x3FFFFF);assertEquals(0x210000,index.unsignedLong(8));assertEquals(3,index.unsignedLong(0)>>>24);assertEquals(2,index.unsignedLong(4)>>>24);
        String queue="48e7006043f9"+HexFormat.of().formatHex(java.nio.ByteBuffer.allocate(4).putInt(0x200000+p.range("GfxLists").fileOffset()).array())+"d0403031000043f1000045f8f6804a9267045c4a60f830196b0824d934d951c8fffa4cdf06004e75";
        assertEquals(queue,HexFormat.of().formatHex(image.readFile(f.queueCopy,queue.length()/2)));
        var plc=CdAssetBytes.copyOf(image.readFile(f.plcBody,2+f.plcSources.length*6));assertEquals(f.plcSources.length,plc.unsignedWord(0)+1);
        for(int i=0;i<f.plcSources.length;i++) {assertEquals(0x200000L+f.plcSources[i],plc.unsignedLong(2+i*6));assertEquals(f.plcDestinations[i],plc.unsignedWord(6+i*6));}
        assertEquals(p.range("StageGfx").fileOffset(),f.plcSources[0]);assertEquals(0,f.plcDestinations[0]);
        // Other rows are retained directory DATA, never stitched into the StageGfx product/residency.
        assertEquals(f.gfxSha,hash(r.decoded(p.range("StageGfx")).bytes()));
    }
    @ParameterizedTest @EnumSource(value=CdR5DataLayout.Programme.class,mode=EnumSource.Mode.EXCLUDE,names={"R51A","R51B"})
    void authenticDirtyReferencesAndSelectedUnavailableReadsKeepTheirOwners(CdR5DataLayout.Programme p)throws Exception {
        var f=expected(p);var t=reader(p).terrain();unavailable(ReadOwner.BLOCK_STAGING,f.blocks,f.blocks,()->t.blockWord(f.blocks,0,0));unavailable(ReadOwner.PATTERN_SOURCE,f.patterns,f.patterns,()->t.pixel(f.patterns,0,0));unavailable(ReadOwner.CHUNK_SOURCE,f.chunks+1,f.chunks,()->t.chunkWord(f.chunks+1,0,0));unavailable(ReadOwner.COLLISION_SOURCE,800,800,()->t.collision().idForBlock(800));
        if(f.badBlock>=0) {var word=t.blockWord(f.badBlock,f.badX,f.badY);assertEquals(f.badWord,word.rawWord());assertEquals(f.badPattern,word.patternIndex());unavailable(ReadOwner.PATTERN_SOURCE,f.badPattern,f.patterns,()->t.pixel(word.patternIndex(),0,0));}
        if(p==CdR5DataLayout.Programme.R53C) {var raw=t.chunkWord(7,0,15);assertEquals(1019,raw.rawWord());assertEquals(1019,raw.renderBlockIndex());assertEquals(0,raw.solidity());unavailable(ReadOwner.BLOCK_STAGING,1019,760,()->t.blockWord(raw.renderBlockIndex(),0,0));unavailable(ReadOwner.COLLISION_SOURCE,1019,800,()->t.collision().idForBlock(raw.collisionBlockIndex()));}
        assertThrows(IndexOutOfBoundsException.class,()->t.chunkWord(0,0,0));assertThrows(IndexOutOfBoundsException.class,()->t.pixel(-1,0,0));
    }
    @ParameterizedTest @EnumSource(value=CdR5DataLayout.Programme.class,mode=EnumSource.Mode.EXCLUDE,names={"R51A","R51B"})
    void realProductAndTruncatedInitialRoleFaultsKeepRetryAndSourceCacheIdentity(CdR5DataLayout.Programme p)throws Exception {
        try(var actual=open()) {
            var r=spy(CdProgrammeReader.open(actual,p.payload().stage()));var gfx=p.range("StageGfx");var fault=new IOException("selected actual source failure");doThrow(fault).when(r).decoded(gfx);assertSame(fault,assertThrows(IOException.class,r::terrain));var blocks=r.decoded(p.range("StageBlocks"));doCallRealMethod().when(r).decoded(gfx);assertSame(r.terrain(),r.terrain());assertSame(blocks,r.decoded(p.range("StageBlocks")));
            for(var role:List.of(CdProgrammeReader.Role.CHUNKS,CdProgrammeReader.Role.FG,CdProgrammeReader.Role.BG,CdProgrammeReader.Role.COLLISION,CdProgrammeReader.Role.ANGLE,CdProgrammeReader.Role.HEIGHT,CdProgrammeReader.Role.WIDTH)) {var probe=spy(CdProgrammeReader.open(actual,p.payload().stage()));var view=probe.role(role);doReturn(view.slice(0,view.length()-1)).when(probe).role(role);assertThrows(IllegalArgumentException.class,probe::terrain);doReturn(view).when(probe).role(role);assertEquals(expected(p).querySha,queryHash(probe.terrain()));assertSame(probe.terrain(),probe.terrain());}
        }
    }
    @ParameterizedTest @EnumSource(value=CdR5DataLayout.Programme.class,mode=EnumSource.Mode.EXCLUDE,names={"R51A","R51B"})
    void foreignActualImagesRejectBeforePublicProductAndMissingBindingRejectsAfterAuthentication(CdR5DataLayout.Programme p)throws Exception {
        try(var actual=open()) {var wrong=mock(CdProgramAssets.class,delegatesTo(actual));doReturn(actual.mmd(CdNativeFile.R51A)).when(wrong).mmd(p.file());assertEquals("Original USA "+p.name()+" image/header differs",assertThrows(IOException.class,()->CdStageTerrain.fromProgramme(wrong,p.payload().stage())).getMessage());}
        var absent=withoutTerrain(p);assertEquals(p.name()+" has no reviewed terrain DATA binding",assertThrows(UnsupportedOperationException.class,absent::terrain).getMessage());verify(absent,never()).decoded(any());verify(absent,never()).role(any());
    }
    // Conservative post-auth control pattern from immutable40437bf8d R8 helper136-144;
    // only the binding is absent. The real complete original image authenticates normally.
    static CdProgrammeReader withoutTerrain(CdR5DataLayout.Programme p)throws Exception {
        var original=reader(p);var definition=spy(original.definition());doReturn(Optional.empty()).when(definition).terrain();
        var ctor=CdProgrammeReader.class.getDeclaredConstructor(CdProgrammeDefinition.class,CdMmdImage.class,String.class);ctor.setAccessible(true);
        var r=spy(ctor.newInstance(definition,TestCdR5DataLayout.actual().images().get(p),original.discSha()));assertEquals(p.range("StageChunks").length(),r.view(p.range("StageChunks")).length());clearInvocations(r);return r;
    }
    private static String queryHash(CdStageTerrain t)throws Exception {
        var h=MessageDigest.getInstance("SHA-256");for(var plane:Plane.values())for(int y=0;y<16;y++)for(int x=0;x<64;x++)h.update((byte)t.layout().nativeCell(plane,x,y).rawByte());
        for(int c=1;c<=t.chunkCount();c++)for(int y=0;y<16;y++)for(int x=0;x<16;x++)word(h,t.chunkWord(c,x,y).rawWord());
        for(int b=0;b<t.blockCount();b++)for(int y=0;y<2;y++)for(int x=0;x<2;x++)word(h,t.blockWord(b,x,y).rawWord());
        for(int p=0;p<t.patternCount();p++)for(int y=0;y<8;y++)for(int x=0;x<8;x++)h.update((byte)t.pixel(p,x,y));
        for(int i=0;i<t.collision().ids().length();i++)h.update((byte)t.collision().idForBlock(i));
        for(int i=0;i<256;i++) {h.update((byte)t.collision().angle(i));for(int j=0;j<16;j++){h.update((byte)t.collision().height(i,j));h.update((byte)t.collision().width(i,j));}}
        return HexFormat.of().formatHex(h.digest());
    }
    private static void word(MessageDigest h,int v){h.update((byte)(v>>>8));h.update((byte)v);}
    private static String hash(CdAssetBytes b){return CdR11aProfile.sha256(b.copy());}
    private static void unavailable(ReadOwner owner,int index,int count,org.junit.jupiter.api.function.Executable read){var e=assertThrows(UnavailableRead.class,read);assertEquals(owner,e.owner());assertEquals(index,e.index());assertEquals(count,e.availableCount());}
}
