package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.media.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.io.IOException;
import java.nio.ReadOnlyBufferException;
import java.nio.file.*;
import java.util.*;
import static com.openggf.game.soniccd.level.CdR11aAsset.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Tests written before production. SOURCE-proposed; all Java UNRUN.
 * Known immutable DATA is compared separately from current block-staging authority.
 * No Ready, ordinary, renderer, bank-clear or regional-equivalence grant. */
class TestCdOriginalR11SharedTerrainData {
    private static final CdPpzDataLayout.Programme R11=CdPpzDataLayout.Programme.R11A;
    private record Fixture(CdDiscHeader header,CdObservedMediaIdentity identity,CdProgramCatalogue catalogue,
                           CdMmdImage image,CdMmdImage foreign) { }
    private static Fixture retained;
    private static synchronized Fixture actual()throws Exception {
        if(retained==null)try(var media=TestCdCczProgrammeData.open()) {
            retained=new Fixture(media.header(),media.identity(),media.catalogue().orElseThrow(),media.mmd(CdNativeFile.R11A),media.mmd(CdNativeFile.R12A));
        }
        return retained;
    }
    private static CdProgramAssets source(Fixture f,CdMmdImage image)throws IOException {
        var media=mock(CdProgramAssets.class);when(media.header()).thenReturn(f.header());when(media.identity()).thenReturn(f.identity());
        when(media.catalogue()).thenReturn(Optional.of(f.catalogue()));when(media.mmd(CdNativeFile.R11A)).thenReturn(image);return media;
    }
    private static CdProgrammeReader reader(CdMmdImage image)throws Exception{return CdProgrammeReader.open(source(actual(),image),R11.payload().stage());}
    private static CdR11aTerrain legacy(CdR11aProfile p,CdMmdImage image)throws IOException {
        var b=p.range(LEVEL_BLOCKS);var t=p.range(ART_LEVEL_TILES);
        var blocks=CdNemesisAsset.decode(b.read(image).copy(),6432,8192);
        var input=new CdR11aProfile.FileRange(p.file(),p.domain(),t.fileOffset(),t.length()+1);
        var tiles=CdNemesisAsset.decode(input.read(image).copy(),25600,25600);
        return CdR11aTerrain.decode(blocks.bytes().copy(),tiles.bytes().copy(),p.range(LEVEL_CHUNKS).read(image).copy(),
                p.range(LAYOUT_FG).read(image).copy(),p.range(LAYOUT_BG).read(image).copy(),p.range(LEVEL_COLLISION).read(image).copy(),
                p.range(COL_ANGLE_MAP).read(image).copy(),p.range(COL_HEIGHT_MAP).read(image).copy(),p.range(COL_WIDTH_MAP).read(image).copy());
    }
    private static void sameKnownValues(CdR11aTerrain expected,CdR11aTerrain actual) {
        assertEquals(expected.blockCount(),actual.blockCount());assertEquals(expected.patternCount(),actual.patternCount());assertEquals(expected.chunkCount(),actual.chunkCount());
        assertArrayEquals(expected.blocks().copy(),actual.blocks().copy());assertArrayEquals(expected.patterns().copy(),actual.patterns().copy());assertArrayEquals(expected.chunks().copy(),actual.chunks().copy());
        assertArrayEquals(expected.layout().ram().copy(),actual.layout().ram().copy());
        assertArrayEquals(expected.collision().ids().copy(),actual.collision().ids().copy());assertArrayEquals(expected.collision().angles().copy(),actual.collision().angles().copy());
        assertArrayEquals(expected.collision().heights().copy(),actual.collision().heights().copy());assertArrayEquals(expected.collision().widths().copy(),actual.collision().widths().copy());
        for(int id=0;id<804;id++)for(int y=0;y<2;y++)for(int x=0;x<2;x++)assertEquals(expected.blockWord(id,x,y),actual.blockWord(id,x,y));
        for(int id=1;id<=121;id++)for(int y=0;y<16;y++)for(int x=0;x<16;x++)assertEquals(expected.chunkWord(id,x,y),actual.chunkWord(id,x,y));
        for(int id=0;id<800;id++)for(int y=0;y<8;y++)for(int x=0;x<8;x++)assertEquals(expected.pixel(id,x,y),actual.pixel(id,x,y));
    }
    @Test void actualOriginalSourceValuesMatchTheIndependentLegacyReadAndDecodePath()throws Exception {
        var f=actual();var r=reader(f.image());var joined=r.originalR11TerrainProducts();var p=CdR11aProfile.forStage(CdReleaseVariant.USA,R11.payload().stage());
        sameKnownValues(legacy(p,f.image()),joined.terrain());
        assertEquals("0bfe16586ccdd38a27b3437441852f49b888ed527927d9ea635986579eddf0d7",CdR11aProfile.sha256(joined.blocks().bytes().copy()));
        assertEquals("314d9df246ee9c58371d6512d2bf496a2aecd08835e8698752c8f21a1e41223d",CdR11aProfile.sha256(joined.tiles().bytes().copy()));
        assertEquals("4948fc8fa494d920e729cf5c81961f372bd3f3494218d74263530dbef7c273eb",CdR11aProfile.sha256(joined.terrain().layout().ram().copy()));
    }
    @Test void retainedReaderTerrainAndDecoderProductsAreBorrowedWithoutASecondDecode()throws Exception {
        var r=reader(actual().image());var blocks=r.decoded(R11.range("LevelBlocks"));var tiles=r.decoded(R11.range("Art_LevelTiles"));var terrain=r.terrain();
        try(var decoder=mockStatic(CdNemesisAsset.class);var parser=mockStatic(CdStageTerrain.class)) {
            var joined=r.originalR11TerrainProducts();assertSame(joined,r.originalR11TerrainProducts());assertSame(terrain,joined.terrain().stageTerrain());
            assertSame(blocks.bytes(),joined.blocks().bytes());assertSame(tiles.bytes(),joined.tiles().bytes());
            assertSame(terrain.blocks(),joined.terrain().blocks());assertSame(terrain.patterns(),joined.terrain().patterns());
            decoder.verifyNoInteractions();parser.verifyNoInteractions();
        }
    }
    @Test void compressedInputRangesAndCursorsRetainActualLookaheadRatherThanInventedPadding()throws Exception {
        var r=reader(actual().image());var joined=r.originalR11TerrainProducts();
        assertEquals(0x3B0A8,joined.blocks().sourceBlobRange().fileOffset());assertEquals(5190,joined.blocks().sourceBlobRange().length());
        assertEquals(joined.blocks().sourceBlobRange(),joined.blocks().readerInputRange());assertEquals(5190,joined.blocks().consumedInputBytes());
        assertEquals(0x3C4EE,joined.tiles().sourceBlobRange().fileOffset());assertEquals(10374,joined.tiles().sourceBlobRange().length());
        assertEquals(10375,joined.tiles().readerInputRange().length());assertEquals(10375,joined.tiles().consumedInputBytes());
        assertEquals(0x3ED74,joined.tiles().readerInputRange().fileOffset()+joined.tiles().readerInputRange().length()-1);
        assertEquals(r.javaInput(R11.range("Art_LevelTiles")).unsignedByte(10374),actual().image().readFile(0x3ED74,1)[0]&255);
    }
    @Test void actualUsaLoaderBypassesTheLegacyTerrainDecoderAndKeepsPlayerAndPlanIdentity()throws Exception {
        var f=actual();var r=reader(f.image());var joined=r.originalR11TerrainProducts();var p=r.originalR11PlayerAssets();var plan=r.originalR11LoadPlan();
        try(var readers=mockStatic(CdProgrammeReader.class);var terrain=mockStatic(CdR11aTerrain.class)) {
            readers.when(()->CdProgrammeReader.open(any(CdProgramAssets.class),eq(R11.payload().stage()))).thenReturn(r);
            terrain.when(()->CdR11aTerrain.decode(any(byte[].class),any(byte[].class),any(byte[].class),any(byte[].class),any(byte[].class),any(byte[].class),any(byte[].class),any(byte[].class),any(byte[].class)))
                    .thenThrow(new AssertionError("USA loader reached displaced legacy terrain decoder"));
            var assets=CdR11aAssetLoader.load(source(f,f.image()),CdReleaseVariant.USA,R11.payload().stage());
            assertSame(joined.terrain(),assets.terrain());assertSame(joined.blocks(),assets.blocksDecode());assertSame(joined.tiles(),assets.levelTilesDecode());
            assertSame(p,assets.player());assertSame(plan,assets.loadPlan());assertSame(r,assets.internalReader());
        }
    }
    @Test void coldDecoderFaultOrderIsBlocksThenTilesBeforeTerrainOrLaterPlanReads()throws Exception {
        var r=reader(actual().image());var blockFault=new IOException("blocks decode fault");
        try(var decoder=mockStatic(CdNemesisAsset.class)) {
            decoder.when(()->CdNemesisAsset.decode(any(byte[].class),anyInt(),anyInt())).thenCallRealMethod();
            decoder.when(()->CdNemesisAsset.decode(any(byte[].class),eq(6432),eq(8192))).thenThrow(blockFault);
            assertSame(blockFault,assertThrows(IOException.class,r::originalR11TerrainProducts));
            decoder.verify(()->CdNemesisAsset.decode(any(byte[].class),eq(25600),anyInt()),never());
        }
        var tileFault=new IOException("tiles decode fault");
        try(var decoder=mockStatic(CdNemesisAsset.class)) {
            decoder.when(()->CdNemesisAsset.decode(any(byte[].class),anyInt(),anyInt())).thenCallRealMethod();
            decoder.when(()->CdNemesisAsset.decode(any(byte[].class),eq(25600),eq(65536))).thenThrow(tileFault);
            assertSame(tileFault,assertThrows(IOException.class,r::originalR11TerrainProducts));
        }
        assertSame(r.terrain(),r.originalR11TerrainProducts().terrain().stageTerrain());
    }
    @Test void sameReaderIdentityRejectsForeignEqualProductsAndChangedRetainedSource()throws Exception {
        var f=actual();var first=reader(f.image());var second=reader(f.image());var a=first.originalR11TerrainProducts();var b=second.originalR11TerrainProducts();
        assertNotSame(a,b);assertNotSame(a.terrain(),b.terrain());a.require(a.blocks(),a.tiles(),a.terrain());
        assertThrows(IllegalArgumentException.class,()->a.require(b.blocks(),a.tiles(),a.terrain()));
        assertThrows(IllegalArgumentException.class,()->a.require(a.blocks(),b.tiles(),a.terrain()));
        assertThrows(IllegalArgumentException.class,()->a.require(a.blocks(),a.tiles(),b.terrain()));
        var t=CdR11aProfile.forStage(CdReleaseVariant.USA,R11.payload().stage());assertThrows(IllegalArgumentException.class,()->a.require(a.blocks(),a.tiles(),legacy(t,f.image())));
        var image=spy(f.image());var r=reader(image);r.originalR11TerrainProducts();int length=image.fileBytes();byte[] changed=f.image().readFile(0,length);changed[0x10000]^=1;
        doReturn(changed).when(image).readFile(0,length);assertThrows(IllegalStateException.class,r::originalR11TerrainProducts);
    }
    @Test void foreignEqualCompressedKeysNeverSelectARegisteredDecoderProduct()throws Exception {
        var r=reader(actual().image());var key=R11.range("LevelBlocks");
        var foreign=new CdPpzDataLayout.Range(key.key(),key.fileOffset(),key.length(),key.sha256());
        assertThrows(IllegalArgumentException.class,()->r.decoded(foreign));
        assertThrows(IllegalArgumentException.class,()->r.javaInput(foreign));
        assertThrows(IllegalArgumentException.class,()->r.decoded(new Object()));
        assertThrows(NullPointerException.class,()->r.decoded(null));
        assertSame(r.decoded(key).bytes(),r.originalR11TerrainProducts().blocks().bytes());
    }
    @Test void wrongProgrammeOrOriginalCannotAcquireTheUsaR11Facade()throws Exception {
        var f=actual();assertThrows(IOException.class,()->CdProgrammeReader.open(source(f,f.foreign()),R11.payload().stage()));
        try(var media=TestCdCczProgrammeData.open()) {
            var other=CdProgrammeReader.open(media,CdStagePayload.R12A.stage());assertThrows(IllegalArgumentException.class,other::originalR11TerrainProducts);
        }
    }
    @Test void selectedKnownReadsPreserveRawWordsAndRejectUnbackedIndicesWithoutAResidentBank()throws Exception {
        var r=reader(actual().image());var t=r.originalR11TerrainProducts().terrain();assertSame(r.terrain(),t.stageTerrain());
        assertEquals(CdStageTerrain.ReferencePolicy.SELECTED_KNOWN_SOURCE,t.stageTerrain().profile().referencePolicy());
        assertEquals(CdStageTerrain.LayoutEncoding.EXACT_READ_VIEW,t.stageTerrain().profile().layoutEncoding());
        assertThrows(UnsupportedOperationException.class,t.stageTerrain()::shape);
        for(int id=1;id<=121;id++)for(int y=0;y<16;y++)for(int x=0;x<16;x++) {
            int offset=(id-1)*512+(y*16+x)*2;assertEquals(t.chunks().unsignedWord(offset),t.chunkWord(id,x,y).rawWord());
        }
        var blocks=assertThrows(CdStageTerrain.UnavailableRead.class,()->t.blockWord(804,0,0));assertEquals(CdStageTerrain.ReadOwner.BLOCK_STAGING,blocks.owner());assertEquals(804,blocks.availableCount());
        var chunks=assertThrows(CdStageTerrain.UnavailableRead.class,()->t.chunkWord(122,0,0));assertEquals(CdStageTerrain.ReadOwner.CHUNK_SOURCE,chunks.owner());
        var patterns=assertThrows(CdStageTerrain.UnavailableRead.class,()->t.pixel(800,0,0));assertEquals(CdStageTerrain.ReadOwner.PATTERN_SOURCE,patterns.owner());
        var ids=assertThrows(CdStageTerrain.UnavailableRead.class,()->t.collision().idForBlock(900));assertEquals(CdStageTerrain.ReadOwner.COLLISION_SOURCE,ids.owner());
        assertThrows(IndexOutOfBoundsException.class,()->t.chunkWord(0,0,0));
    }
    @Test void sourceDataNeitherCreatesNorClaimsOriginalClearKnowledge()throws Exception {
        var t=reader(actual().image()).originalR11TerrainProducts().terrain();
        var unknown=com.openggf.game.soniccd.resources.CdBlockDefinitionStaging.unknown();
        var decoded=unknown.withCompletedPrefix(t.blocks());assertEquals(t.blockWord(803,1,1).rawWord(),decoded.blockWord(803,3));
        assertThrows(com.openggf.game.soniccd.resources.CdBlockDefinitionStaging.UnknownRead.class,()->decoded.blockWord(804,0));
        var pureClear=com.openggf.game.soniccd.resources.CdBlockDefinitionStaging.afterFileBufferClear().withCompletedPrefix(t.blocks());
        assertEquals(0,pureClear.blockWord(804,0)); // Pure value arithmetic, not proof a production clear happened.
        assertThrows(CdStageTerrain.UnavailableRead.class,()->t.blockWord(804,0,0));
    }
    @Test void borrowedProductsAreImmutableAndDoNotWarmLaterPlayerOrPlanCaches()throws Exception {
        var r=reader(actual().image());var joined=r.originalR11TerrainProducts();
        for(String name:List.of("originalR11Player","originalR11Plan")){var f=CdProgrammeReader.class.getDeclaredField(name);f.setAccessible(true);assertNull(f.get(r));}
        var bytes=joined.blocks().bytes();var original=bytes.copy();var changed=bytes.copy();Arrays.fill(changed,(byte)0);assertArrayEquals(original,bytes.copy());
        assertThrows(ReadOnlyBufferException.class,()->bytes.buffer().put((byte)0));
    }
    @ParameterizedTest @EnumSource(value=CdReleaseVariant.class,names={"JAPAN","EUROPE"})
    void regionalLoaderRetainsTheLegacyComponentDecoderAndHasNoSharedUsaReader(CdReleaseVariant variant)throws Exception {
        String root=System.getProperty("soniccd.media.root");Assumptions.assumeTrue(root!=null&&!root.isBlank(),"Set soniccd.media.root for supplied-media checks");
        String directory=variant==CdReleaseVariant.JAPAN?"Japan":"Europe";Path cue;
        try(var files=Files.list(Path.of(root).resolve(directory))) {
            var cues=files.filter(p->p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();assertEquals(1,cues.size());cue=cues.getFirst();
        }
        try(var media=CdProgramAssets.open(cue,Optional.of(variant),Optional.empty())) {
            var assets=CdR11aAssetLoader.load(media,variant,R11.payload().stage());var expected=legacy(assets.profile(),media.mmd(CdNativeFile.R11A));
            sameKnownValues(expected,assets.terrain());assertEquals(CdStageTerrain.Shape.PPZ1_PRESENT,assets.terrain().stageTerrain().shape());
            assertEquals(CdStageTerrain.ReferencePolicy.EAGER_VALIDATED,assets.terrain().stageTerrain().profile().referencePolicy());
            assertThrows(UnsupportedOperationException.class,assets::internalReader);assertThrows(IndexOutOfBoundsException.class,()->assets.terrain().blockWord(804,0,0));
        }
    }
}
