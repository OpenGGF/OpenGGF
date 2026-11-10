package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.media.*;
import com.openggf.game.soniccd.resources.CdBlockDefinitionStaging;
import com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Tests authored before implementation. All Java/discovery/runtime UNRUN.
 * This validates a pure source-bound plane factory, never loading/Ready/current-bank authority. */
class TestCdR11aStandardBackgroundPlane {
    private static final CdPpzDataLayout.Programme R11=CdPpzDataLayout.Programme.R11A;
    private record Fixture(CdDiscHeader header,CdObservedMediaIdentity identity,CdProgramCatalogue catalogue,CdMmdImage image) { }
    private static Fixture retained;
    private static synchronized Fixture actual()throws Exception {
        if(retained==null)try(var media=TestCdCczProgrammeData.open()) {
            retained=new Fixture(media.header(),media.identity(),media.catalogue().orElseThrow(),media.mmd(CdNativeFile.R11A));
        }
        return retained;
    }
    private static CdProgramAssets source(Fixture f,CdMmdImage image)throws IOException {
        var s=mock(CdProgramAssets.class);when(s.header()).thenReturn(f.header());when(s.identity()).thenReturn(f.identity());
        when(s.catalogue()).thenReturn(Optional.of(f.catalogue()));when(s.mmd(CdNativeFile.R11A)).thenReturn(image);return s;
    }
    private static CdProgrammeReader reader()throws Exception {
        var f=actual();return CdProgrammeReader.open(source(f,f.image()),R11.payload().stage());
    }
    private static Object field(CdR11aBackgroundPlane plane,String name)throws Exception {
        var f=CdR11aBackgroundPlane.class.getDeclaredField(name);f.setAccessible(true);return f.get(plane);
    }
    private static CdR11aBackgroundPlane.Camera camera(int x,int y){return new CdR11aBackgroundPlane.Camera(x<<16,y<<16);}
    private static CdR11aBackgroundPlane.LiveCameras live(){return new CdR11aBackgroundPlane.LiveCameras(camera(0,0),camera(0,0),camera(0,0));}
    private static CdR11aBackgroundPlane.State before(){byte[] b=new byte[4096];Arrays.fill(b,(byte)0x5A);return new CdR11aBackgroundPlane.State(CdAssetBytes.copyOf(b));}
    private static void sameResult(CdR11aBackgroundPlane.Result a,CdR11aBackgroundPlane.Result b){
        assertArrayEquals(a.state().nameTable().copy(),b.state().nameTable().copy());assertEquals(a.flags(),b.flags());assertEquals(a.sections(),b.sections());assertEquals(a.blocks(),b.blocks());
    }
    private static CdLevelLoadingSource fakeStandard(CdLevelLoadingSource real)throws Exception {
        var fake=mock(CdLevelLoadingSource.class);when(fake.completion()).thenReturn(CdLevelLoadingSource.Completion.STANDARD);
        when(fake.view(R11.range("BackgroundPointers"))).thenReturn(real.view(R11.range("BackgroundPointers")));
        when(fake.view(R11.range("BackgroundRows"))).thenReturn(real.view(R11.range("BackgroundRows")));
        when(fake.backgroundSectionIds()).thenReturn(real.backgroundSectionIds());when(fake.background()).thenReturn(real.background());when(fake.terrain()).thenReturn(real.terrain());return fake;
    }
    @Test void originalStandardFactoryBorrowsTheSameReaderTerrainRowsAndLivePointers()throws Exception {
        var r=reader();var s=r.levelLoadingSource();var t=s.terrain();var plane=CdR11aBackgroundPlane.fromLoadingSource(s);
        assertSame(r.terrain(),t);assertSame(t.blocks(),field(plane,"terrainBlocks"));assertSame(t.chunks(),field(plane,"terrainChunks"));assertSame(t.layout().ram(),field(plane,"terrainLayout"));
        assertSame(r.view(R11.range("BackgroundRows")),field(plane,"ids"));assertSame(r.view(R11.range("BackgroundPointers")),field(plane,"livePointers"));
        assertNull(field(plane,"blockDefinitions")); // No current bank or manufactured clear.
        var tables=assertInstanceOf(CdLevelLoadingSource.PointerTables.class,s.background());assertEquals(1,tables.tables().size());assertEquals(CdLevelLoadingSource.PointerKind.LIVE,tables.tables().getFirst().kind());
        assertEquals(List.of(0xFFF708,0xFFF708,0xFFF710,0xFFF718),tables.tables().getFirst().values());assertSame(field(plane,"ids"),tables.rowSelectors().orElseThrow());
    }
    @Test void standardUsesActualR11Mask7f0NotTheRejected1f0Inference()throws Exception {
        var s=reader().levelLoadingSource();var plane=CdR11aBackgroundPlane.fromLoadingSource(s);assertEquals(0x7F0,field(plane,"initialRowMask"));
        var flags=new CdR11aBackgroundPlane.FlagWords(0x12AB,0x3456,0x789A);var result=plane.initialize(before(),live(),flags);
        assertEquals(16,result.sections().size());for(var row:result.sections())assertEquals(1+((row.relativeY()&0x7F0)>>>4),row.tableIndex());
        assertEquals(128,result.sections().getFirst().tableIndex());assertNotEquals(1+((-16&0x1F0)>>>4),result.sections().getFirst().tableIndex());
        assertEquals(flags,result.flags());
    }
    @Test void pureActualAggregateAndStandardFactoryHaveEqualNativeTransformValues()throws Exception {
        var f=actual();var media=source(f,f.image());var assets=CdR11aAssetLoader.load(media,CdReleaseVariant.USA,R11.payload().stage());
        var standard=CdR11aBackgroundPlane.fromLoadingSource(reader().levelLoadingSource());var old=CdR11aBackgroundPlane.bind(assets);
        var b=before();var c=live();var flags=new CdR11aBackgroundPlane.FlagWords(0x12AB,0x3456,0x789A);
        sameResult(old.initialize(b,c,flags),standard.initialize(b,c,flags));
        var aggregate=CdLevelLoadingSource.fromOriginalR11(assets);assertTrue(aggregate.ownsOriginalR11(assets));
        sameResult(old.initialize(b,c,flags),aggregate.backgroundPlane().initialize(b,c,flags));assertEquals(0x7F0,field(old,"initialRowMask"));
    }
    @Test void exactRowsAndPointersRemainImmutableCanonicalViews()throws Exception {
        var r=reader();var s=r.levelLoadingSource();var plane=CdR11aBackgroundPlane.fromLoadingSource(s);
        var rows=(CdAssetBytes)field(plane,"ids");var ptrs=(CdAssetBytes)field(plane,"livePointers");assertEquals(130,rows.length());assertEquals(16,ptrs.length());
        var expected=rows.copy();var copy=rows.copy();Arrays.fill(copy,(byte)0);assertArrayEquals(expected,rows.copy());
        assertThrows(IllegalArgumentException.class,()->s.view(new CdPpzDataLayout.Range("BackgroundRows",R11.range("BackgroundRows").fileOffset(),130,R11.range("BackgroundRows").sha256())));
        for(int i=0;i<4;i++)assertEquals(new int[]{0xFFF708,0xFFF708,0xFFF710,0xFFF718}[i],(int)ptrs.unsignedLong(i*4));
    }
    @Test void sourceRevalidationOccursBeforeAnyBackgroundBorrowOnFailure()throws Exception {
        var f=actual();var image=spy(f.image());var r=CdProgrammeReader.open(source(f,image),R11.payload().stage());var s=r.levelLoadingSource();
        int bytes=image.fileBytes();var changed=f.image().readFile(0,bytes);changed[0x10000]^=1;doReturn(changed).when(image).readFile(0,bytes);
        assertThrows(IllegalStateException.class,()->CdR11aBackgroundPlane.fromLoadingSource(s));
        var fake=mock(CdLevelLoadingSource.class);var original=new IllegalStateException("retained source fault");doThrow(original).when(fake).requireRetained();
        assertSame(original,assertThrows(IllegalStateException.class,()->CdR11aBackgroundPlane.fromLoadingSource(fake)));verify(fake,never()).completion();verify(fake,never()).background();verify(fake,never()).terrain();
    }
    @Test void standardRejectsCopiedExtraOrMissingLiveMetadataBeforeTerrainBorrow()throws Exception {
        var real=reader().levelLoadingSource();var rows=real.backgroundSectionIds();
        var live=new CdLevelLoadingSource.PointerTable(CdLevelLoadingSource.PointerKind.LIVE,List.of(0xFFF708,0xFFF708,0xFFF710,0xFFF718));
        var copied=new CdLevelLoadingSource.PointerTable(CdLevelLoadingSource.PointerKind.COPIED,List.of(0xFF192E,0xFF192E,0xFF1936,0xFF193E));
        for(var list:List.of(List.<CdLevelLoadingSource.PointerTable>of(),List.of(copied),List.of(live,copied),List.of(live,live),List.of(new CdLevelLoadingSource.PointerTable(CdLevelLoadingSource.PointerKind.LIVE,List.of(0))))) {
            var fake=fakeStandard(real);when(fake.background()).thenReturn(new CdLevelLoadingSource.PointerTables(list,Optional.of(rows)));clearInvocations(fake);
            var e=assertThrows(CdR11aBackgroundPlane.InvalidPlane.class,()->CdR11aBackgroundPlane.fromLoadingSource(fake));assertEquals(CdR11aBackgroundPlane.Boundary.VIEW_BINDING,e.boundary());verify(fake,never()).terrain();
        }
    }
    @Test void standardRejectsForeignEqualRowsAndMissingRowSelectors()throws Exception {
        var real=reader().levelLoadingSource();var parsed=assertInstanceOf(CdLevelLoadingSource.PointerTables.class,real.background());
        var equalForeign=CdAssetBytes.copyOf(real.backgroundSectionIds().copy());
        for(var rows:List.of(Optional.<CdAssetBytes>empty(),Optional.of(equalForeign))) {
            var fake=fakeStandard(real);when(fake.background()).thenReturn(new CdLevelLoadingSource.PointerTables(parsed.tables(),rows));clearInvocations(fake);
            assertThrows(CdR11aBackgroundPlane.InvalidPlane.class,()->CdR11aBackgroundPlane.fromLoadingSource(fake));verify(fake,never()).terrain();
        }
        var fake=fakeStandard(real);when(fake.backgroundSectionIds()).thenReturn(equalForeign);assertThrows(CdR11aBackgroundPlane.InvalidPlane.class,()->CdR11aBackgroundPlane.fromLoadingSource(fake));
    }
    @Test void currentBlockUnknownStillStopsAtTheActualReadAndRetainsItsPrefix()throws Exception {
        var source=reader().levelLoadingSource();var plane=CdR11aBackgroundPlane.fromLoadingSource(source).withBlockDefinitions(CdBlockDefinitionStaging.unknown());
        var b=before();var flags=new CdR11aBackgroundPlane.FlagWords(0x12AB,0x3456,0x789A);
        var e=assertThrows(CdR11aBackgroundPlane.InvalidPlane.class,()->plane.initialize(b,live(),flags));assertEquals(CdR11aBackgroundPlane.Boundary.BLOCK_READ,e.boundary());
        assertInstanceOf(CdBlockDefinitionStaging.UnknownRead.class,e.getCause());assertTrue(e.completedPrefix().isPresent());assertArrayEquals(b.nameTable().copy(),e.completedPrefix().orElseThrow().state().nameTable().copy());
        assertEquals(flags,e.completedPrefix().orElseThrow().flags());
    }
    @Test void ttStillUsesCopiedMetadataAndItsExistingMaskWithoutPpzViews()throws Exception {
        try(var media=TestCdCczProgrammeData.open()) {
            var source=CdProgrammeReader.open(media,CdStagePayload.R41A.stage()).levelLoadingSource();var plane=CdR11aBackgroundPlane.fromLoadingSource(source);
            assertEquals(CdLevelLoadingSource.Completion.WATER_PALETTE_AFTER_FADE,source.completion());assertEquals(0x7F0,field(plane,"initialRowMask"));
            assertThrows(UnsupportedOperationException.class,()->source.view(R11.range("BackgroundPointers")));
        }
        var fake=mock(CdLevelLoadingSource.class);when(fake.completion()).thenReturn(CdLevelLoadingSource.Completion.WATER_PALETTE_AFTER_FADE);when(fake.background()).thenReturn(CdLevelLoadingSource.DirectCamera.INSTANCE);
        assertThrows(UnsupportedOperationException.class,()->CdR11aBackgroundPlane.fromLoadingSource(fake));verify(fake,never()).view(any(CdPpzDataLayout.Range.class));
    }
    @Test void factoryCreatesNoReadyPermitOrCurrentClearAndPreservesInputState()throws Exception {
        var r=reader();var source=r.levelLoadingSource();var plane=CdR11aBackgroundPlane.fromLoadingSource(source);var b=before();var original=b.nameTable().copy();
        var result=plane.initialize(b,live(),new CdR11aBackgroundPlane.FlagWords(0,0,0));assertArrayEquals(original,b.nameTable().copy());assertEquals(16,result.sections().size());
        assertNull(field(plane,"blockDefinitions"));assertSame(r.terrain(),source.terrain());assertSame(r.playerSource(),source.playerSource());
        assertThrows(CdStageTerrain.UnavailableRead.class,()->source.terrain().blockWord(804,0,0));
    }
}
