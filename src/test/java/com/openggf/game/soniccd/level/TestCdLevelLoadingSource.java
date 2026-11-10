package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.resources.*;
import java.lang.reflect.Modifier;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** SOURCE ONLY; no discovery/execution claim. Configured original media tests remain enabled. */
class TestCdLevelLoadingSource {
    private static CdProgrammeReader fresh(CdR4DataLayout.Programme p)throws Exception {
        try(var media=CdProgramAssets.open(TestCdR4TerrainData.actual().cue(),Optional.of(CdReleaseVariant.USA),Optional.empty())) {
            return CdProgrammeReader.open(media,p.payload().stage());
        }
    }
    @ParameterizedTest @EnumSource(CdR4DataLayout.Programme.class)
    void retainedPlayerAndLoadingBorrowsShareActualReaderAndCaches(CdR4DataLayout.Programme p)throws Exception {
        var reader=TestCdR4TerrainData.reader(p);var data=CdProgrammeData.fromReader(reader);
        var source=data.levelLoadingSource();assertSame(source,data.levelLoadingSource());
        assertSame(reader.playerSource(),data.playerSource());assertSame(data.playerSource(),source.playerSource());
        assertSame(reader.terrain(),source.terrain());assertSame(reader.sonic(),source.sonic());
        assertSame(reader.placements(),data.placements());assertSame(data.placements(),source.placements());
        assertEquals(reader.ordinaryZoneActWord(),data.playerSource().nativeZoneActWord());
        assertEquals(reader.playerBinding().ordinaryPeriod(),source.ordinaryPeriod());
        assertSame(source.loadTables(),source.loadTables());
        source.requirePlayerSource(data.playerSource());
        var another=fresh(p);
        assertThrows(IllegalArgumentException.class,()->source.requirePlayerSource(another.playerSource()));
    }
    @ParameterizedTest @EnumSource(CdR4DataLayout.Programme.class)
    void placementBytesRetainNativePhysicalOrderNullSentinelsAndMasks(CdR4DataLayout.Programme p)throws Exception {
        var r=TestCdR4TerrainData.reader(p);var placements=CdProgrammeData.fromReader(r).placements();
        var bytes=r.view(p.range("StageObjectMaps"));int rows=(bytes.length()-26)/8;
        assertEquals(rows,placements.records().size());
        assertArrayEquals(bytes.slice(0,4).copy(),placements.layoutHeader().orElseThrow().copy());
        assertArrayEquals(bytes.slice(4,8).copy(),placements.backwardSentinel().orElseThrow().copy());
        assertArrayEquals(bytes.slice(bytes.length()-14,8).copy(),placements.forwardSentinel().copy());
        assertArrayEquals(bytes.slice(bytes.length()-6,6).copy(),placements.nullList().orElseThrow().copy());
        for(int i=0;i<rows;i++) {
            var raw=bytes.slice(12+i*8,8);var row=placements.records().get(i);
            assertEquals(i,row.index());assertArrayEquals(raw.copy(),placements.rawRows().get(i).copy());
            assertEquals(raw.unsignedByte(6),row.periodFlags());assertEquals(raw.unsignedWord(2),row.packedY());
            for(var period:CdPeriod.values())assertEquals((raw.unsignedByte(6)&switch(period){
                case PAST->32;case PRESENT->64;case GOOD_FUTURE,BAD_FUTURE->128;})!=0,row.appliesTo(period));
        }
        assertEquals(bytes.unsignedWord(12),placements.records().getFirst().centreX());
    }
    @Test void r41aFactsAreSourceReadsAndDoNotDecodeTerrainOrPlayer()throws Exception {
        var reader=spy(fresh(CdR4DataLayout.Programme.R41A));
        var source=CdProgrammeData.fromReader(reader).levelLoadingSource();clearInvocations(reader);
        assertEquals(new CdR11aLoadPlan.InitialCentre(80,64),source.initialCentre());
        assertEquals(new CdR11aLoadPlan.Bounds(4,0,0x1C97,0,0x510,0x60),source.bounds());
        assertEquals(0xA0,source.cameraCentreX());assertEquals(0x200,source.nativeZoneActWord());
        assertEquals(1,source.ordinaryPeriod());assertEquals(0x280,source.initialWaterHeight(1));
        assertEquals(source.initialWaterHeight(1),source.initialWaterHeight(0x81));
        verify(reader,never()).terrain();verify(reader,never()).playerSource();verify(reader,never()).loadTables();
        verify(reader,never()).decoded(any());verify(reader,never()).nativeInput(any());
    }
    @Test void immutableSourcesRejectForeignEqualKeysBeforeReadingOrDecoding()throws Exception {
        var p=CdR4DataLayout.Programme.R41A;var r=spy(fresh(p));
        var source=CdProgrammeData.fromReader(r).levelLoadingSource();var key=p.range("StageGfx");
        var equal=new CdR4DataLayout.Range(key.key(),key.fileOffset(),key.length(),key.sha256(),key.nativePath(),key.line());
        clearInvocations(r);assertThrows(IllegalArgumentException.class,()->source.view(equal));
        verify(r,never()).nativeInput(any());verify(r,never()).decoded(any());
        assertSame(r.view(key),source.view(key));
    }
    @Test void publicFacadeCannotExposeReaderOrMintLiveInvocation() {
        assertEquals(0,CdLevelLoadingSource.class.getConstructors().length);
        assertFalse(Arrays.stream(CdLevelLoadingSource.class.getMethods()).anyMatch(m->m.getReturnType()==CdProgrammeReader.class));
        assertTrue(Modifier.isFinal(CdLevelLoadingSource.class.getModifiers()));
    }
    @Test void originalR11AdapterRetainsActualProductsAndExistingArtIdentity()throws Exception {
        try(var media=TestCdCczProgrammeData.open()) {
            var assets=CdR11aAssetLoader.load(media,CdReleaseVariant.USA,CdStagePayload.R11A.stage());
            var source=CdLevelLoadingSource.fromOriginalR11(assets);
            assertSame(assets.placements(),source.placements());assertSame(assets.player(),source.playerSource().player());
            assertSame(assets.terrain().stageTerrain(),source.terrain());assertSame(assets.sonic(),source.sonic());
            assertSame(assets.loadPlan().initialCentre(),source.initialCentre());assertSame(assets.loadPlan().bounds(),source.bounds());
            assertEquals(0,source.nativeZoneActWord());assertEquals(1,source.ordinaryPeriod());
            assertThrows(UnsupportedOperationException.class,()->source.initialWaterHeight(1));
            var art=CdR11aSectionArt.fromLoadingSource(source);assertTrue(art.ownsLoadingAssets(assets));
            assertTrue(CdR11aCameraArtResidency.unknownForLoading(source).ownsLoadingAssets(assets));
        }
    }
    @Test void ttArtUsesCurrentUnknownSourceEnvelopeAndRejectsForeignState()throws Exception {
        var source=CdProgrammeData.fromReader(TestCdR4TerrainData.reader(CdR4DataLayout.Programme.R41A)).levelLoadingSource();
        var art=CdR11aSectionArt.fromLoadingSource(source);var resident=CdR11aCameraArtResidency.unknownForLoading(source);
        assertTrue(resident.ownsLoadingSource(source));assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class,
            ()->CdR11aCameraArtResidency.readTile(resident,0));
        var zero=CdAssetBytes.copyOf(new byte[512]);var state=art.fromInactiveValues(resident,zero,0,
            CdR11aPlcBuffer.cleared(),new CdR11aSectionArt.SavedDecoder(CdR11aSectionArt.Writer.CLEARED,0,0,0,0,0),0,0,0,0);
        assertSame(state,art.serviceFast(state));
        var other=CdR11aSectionArt.fromLoadingSource(source);
        assertThrows(IllegalArgumentException.class,()->other.validateState(state));
        assertEquals(0,state.queue().headSource());
        assertFalse(state.stopped());assertEquals(0,state.remainingTilesWord());
    }
    @ParameterizedTest @EnumSource(CdR4DataLayout.Programme.class)
    void bootstrapVariantsRetainNativeWordsWithoutPlayerOrTerrainConstruction(CdR4DataLayout.Programme p)throws Exception {
        var source=CdProgrammeData.fromReader(fresh(p)).levelLoadingSource();
        int act=p.payload().stage().actNumber();
        assertEquals(0x200+act-1,source.nativeZoneActWord());
        assertEquals(p.name().endsWith("A")?1:p.name().endsWith("B")?0:2,source.ordinaryPeriod());
        assertEquals(new CdR11aLoadPlan.InitialCentre(80,act==1?64:act==2?1260:364),source.initialCentre());
        assertEquals(act==3?0xD97:0x1C97,source.bounds().right());
        assertEquals(act==1?0x510:0x800,source.bounds().bottom());assertEquals(0xA0,source.cameraCentreX());
        var index=source.levelIndex();var raw=source.view(p.range("StageDataIndex"));
        assertEquals(raw.unsignedLong(0),index.levelArt().encoded());assertEquals(raw.unsignedLong(4),index.blocks().encoded());
        assertArrayEquals(raw.slice(14,2).copy(),index.paletteIds().copy());
    }
    @Test void artPreparationWritesOnlyReachedTilesAndUnknownListFaultKeepsPrefix()throws Exception {
        var reader=fresh(CdR4DataLayout.Programme.R41A);var source=CdProgrammeData.fromReader(reader).levelLoadingSource();
        var art=CdR11aSectionArt.fromLoadingSource(source);
        var initial=art.fromInactiveValues(CdR11aCameraArtResidency.unknownForLoading(source),CdAssetBytes.copyOf(new byte[512]),
            0,CdR11aPlcBuffer.cleared(),new CdR11aSectionArt.SavedDecoder(CdR11aSectionArt.Writer.CLEARED,0,0,0,0,0),0,0,0,0);
        var queued=art.appendList(initial,1);var entry=source.loadTables().plcs().get(1).entries().getFirst();
        assertEquals(entry.sourceNativeAddress(),queued.queue().headSource());assertEquals(entry.vramAddress(),queued.queue().headDestination());
        assertEquals(0,initial.queue().headSource());var prepared=art.prepare(queued);
        assertEquals(entry.outputBytes()/32,prepared.remainingTilesWord());
        // ProcessPLCs publishes the reached compressed cursor; destination and descriptor tail are nonwrites.
        assertEquals(prepared.decoder().orElseThrow().sourcePointer(),prepared.queue().headSource());
        assertTrue(prepared.queue().headSource()>queued.queue().headSource());
        assertArrayEquals(queued.queue().bytes().slice(4,92).copy(),prepared.queue().bytes().slice(4,92).copy());
        assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class,()->CdR11aCameraArtResidency.readTile(prepared.residency(),0x5000/32));
        var serviced=art.serviceFast(prepared);assertTrue(serviced.remainingTilesWord()<prepared.remainingTilesWord());
        var canonical=reader.decoded(CdR4DataLayout.Programme.R41A.range("WaterSurfaceGfx")).bytes();
        assertArrayEquals(canonical.slice(0,32).copy(),CdR11aCameraArtResidency.readTile(serviced.residency(),0x5000/32).copy());
        var fault=assertThrows(CdR11aSectionArt.SectionArtException.class,()->art.appendList(serviced,65535));
        assertEquals(CdR11aSectionArt.Reason.PLC_LIST,fault.reason());assertTrue(fault.state().stopped());
        assertEquals(serviced.queue(),fault.state().queue());assertEquals(serviced.residency(),fault.state().residency());
        assertThrows(IllegalStateException.class,()->art.prepare(fault.state()));
    }
    @Test void sourceRebindingRetainsActualKnownBytesAndUnknownNonreads()throws Exception {
        var source=CdProgrammeData.fromReader(fresh(CdR4DataLayout.Programme.R41A)).levelLoadingSource();
        byte[] pixels=new byte[32];for(int i=0;i<32;i++)pixels[i]=(byte)(i+1);
        var current=CdR11aCameraArtResidency.writeTile(CdR11aCameraArtResidency.unknown(),0x7400,CdAssetBytes.copyOf(pixels));
        var bound=CdR11aCameraArtResidency.withLoadingSource(current,source);
        assertArrayEquals(pixels,CdR11aCameraArtResidency.readTile(bound,0x7400/32).copy());
        assertArrayEquals(pixels,CdR11aCameraArtResidency.readTile(current,0x7400/32).copy());
        assertTrue(bound.ownsLoadingSource(source));assertSame(bound,CdR11aCameraArtResidency.withLoadingSource(bound,source));
        assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class,()->CdR11aCameraArtResidency.readTile(bound,0));
        assertThrows(IllegalStateException.class,()->bound.region(CdR11aCameraArtResidency.Region.AT_7400));
    }

}
