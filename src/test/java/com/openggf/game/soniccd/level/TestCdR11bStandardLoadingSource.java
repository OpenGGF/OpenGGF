package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdCommand;
import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.resources.CdR11aCameraArtResidency;
import com.openggf.game.soniccd.resources.CdR11aSectionArt;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual-media immutable DATA obligations, written before implementation. Java UNRUN. */
class TestCdR11bStandardLoadingSource {
    private static final CdPpzDataLayout.Programme B=CdPpzDataLayout.Programme.R11B;
    private static CdProgrammeReader reader(CdStagePayload stage)throws Exception {
        try(var media=TestCdCczProgrammeData.open()){return CdProgrammeReader.open(media,stage.stage());}
    }
    private static CdProgrammeReader past()throws Exception{return reader(CdStagePayload.R11B);}
    @Test void pastFacadeIsCachedStandardDataWithItsOwnNativePeriod()throws Exception {
        var r=past();var s=r.levelLoadingSource();
        assertSame(s,r.levelLoadingSource());assertSame(s,CdPpzProgrammeData.fromReader(r).levelLoadingSource());
        assertEquals(CdLevelLoadingSource.Completion.STANDARD,s.completion());assertEquals(0,s.nativeZoneActWord());
        assertEquals(0,s.ordinaryPeriod());assertEquals(CdNativeFile.R11B,s.file());assertFalse(s.hasOriginalR11Aggregate());
        assertThrows(UnsupportedOperationException.class,()->s.initialWaterHeight(0));
    }
    @Test void pastEntryRetainsTheGenuineTaggedSourcesAndOrdinaryStart()throws Exception {
        var r=past();var s=r.levelLoadingSource();var entry=r.standardPpzEntry();
        assertSame(entry.bounds(),s.bounds());assertSame(entry.centre(),s.initialCentre());assertSame(entry.index(),s.levelIndex());
        assertEquals(0x23CABE,s.levelIndex().levelArt().nativeAddress());assertEquals(3,s.levelPlcId());
        assertEquals(0x23BA3A,s.levelIndex().blocks().nativeAddress());assertEquals(2,s.blocksPlcId());
        assertEquals(0x210000,s.levelIndex().chunksNativeAddress());assertEquals(0x81,s.levelIndex().uninterpretedTrailing().unsignedWord(0));
        assertEquals(0x50,s.initialCentre().centreX());assertEquals(0x1E8,s.initialCentre().centreY());
        assertEquals(0x2897,s.bounds().right());assertEquals(0x310,s.bounds().bottom());assertEquals(0xA0,s.cameraCentreX());
    }
    @Test void pastTablesBorrowOneCommonParserAndOwnPaletteRows()throws Exception {
        var r=past();var s=r.levelLoadingSource();var tables=s.loadTables();
        assertSame(tables,s.loadTables());assertEquals(19,tables.plcs().size());assertEquals(6,tables.palettes().size());
        for(int i=0;i<6;i++)assertSame(r.loadTables().palettes().get(i).bytes(),tables.palettes().get(i).bytes());
        assertSame(tables.palettes().get(3).bytes(),s.playerPalette());assertSame(tables.palettes().get(5).bytes(),s.levelPalette());
        assertInstanceOf(CdLevelLoadingSource.CameraSections.class,tables.sections());
    }
    @Test void pastPlayerTerrainVisualAndBlocksKeepSameReaderIdentity()throws Exception {
        var r=past();var s=r.levelLoadingSource();var p=s.playerSource();
        assertSame(r.playerSource(),p);assertSame(r.terrain(),s.terrain());assertSame(p.terrain(),s.terrain());
        assertSame(r.sonic(),s.sonic());assertEquals(CdR11aPlayerAssets.SourceGraph.PPZ,p.player().sourceGraph());
        assertEquals(CdSonicVisualAssets.SourceFormat.FRAMES_198_WITH_EVEN_BYTE,s.sonic().sourceFormat());
        assertSame(r.decoded(B.range("LevelBlocks")).bytes(),s.decodedBlocks());
        assertSame(r.nativeInput(B.range("LevelBlocks")),s.blocksSource().readerBytes());
        assertThrows(IllegalArgumentException.class,()->r.view(CdPpzProgrammeProfiles.R11LoadingView.LIFE_ICON));
    }
    @Test void pastFiniteViewsUseDistinctCanonicalKeysAndActualAddresses()throws Exception {
        var r=past();var s=r.levelLoadingSource();
        assertSame(r.view(CdPpzProgrammeProfiles.R11PastLoadingView.LIFE_ICON),s.lifeIcon());
        assertEquals(0x2362D6,s.lifeIconNativeAddress());assertEquals(384,s.lifeIcon().length());
        assertEquals(0x23B274,s.collisionPointer(0));assertEquals(s.collisionSourceAddress(),s.collisionPointer(0));
        assertThrows(IllegalArgumentException.class,()->s.collisionPointer(1));
        assertSame(r.view(B.range("BackgroundRows")),s.backgroundSectionIds());assertEquals(34,s.backgroundSectionIds().length());
        assertSame(r.view(B.range("BackgroundRead")),s.backgroundLayout());assertEquals(0x1F0,s.standardBackgroundRowMask());
        assertSame(r.view(B.range("BackgroundPointers")),s.standardBackgroundPointers());
    }
    @Test void pastBackgroundUsesSharedPlaneAlgorithmWithItsNativeShortRowTable()throws Exception {
        var s=past().levelLoadingSource();var plane=s.backgroundPlane();
        var field=plane.getClass().getDeclaredField("initialRowMask");field.setAccessible(true);assertEquals(0x1F0,field.getInt(plane));
        var ids=plane.getClass().getDeclaredField("ids");ids.setAccessible(true);assertSame(s.backgroundSectionIds(),ids.get(plane));
    }
    @Test void pastMusicConsumesOwnFiniteTableBeforeByteSelection()throws Exception {
        var r=spy(past());var s=r.levelLoadingSource();clearInvocations(r);
        assertEquals(CdCommand.fromId(0x80),s.selectLevelMusic(0,0,0,0));
        var order=inOrder(r);order.verify(r).view(CdPpzProgrammeProfiles.R11PastLoadingView.MUSIC_CONSUMER);
        order.verify(r).view(CdPpzProgrammeProfiles.R11PastLoadingView.MUSIC);
        var edge=assertThrows(CdLevelLoadingSource.StandardMusicReadBoundary.class,()->s.selectLevelMusic(0x700,0,0,0));
        assertEquals(28,edge.index());assertEquals(0x201B60L,edge.address());
    }
    @Test void pastArtDescriptorsRemainLazyAndForeignReaderDescriptorsReject()throws Exception {
        var r=spy(past());var s=r.levelLoadingSource();clearInvocations(r);var art=s.queuedArtSources();
        verify(r,never()).nativeInput(any());assertEquals(r.loadTables().encounteredSources().size(),art.size());
        for(var a:art){assertSame(a,s.queuedArtSources().get(art.indexOf(a)));assertTrue(a.belongsTo(s));}
        var foreign=past().levelLoadingSource();assertThrows(IllegalArgumentException.class,()->foreign.sectionArtBytes(art.getFirst()));
        assertFalse(s.ownsPlayerSource(foreign.playerSource()));assertThrows(IllegalArgumentException.class,()->s.requirePlayerSource(foreign.playerSource()));
    }
    @Test void pastHudAndPowerupWindowsAreDerivedFromItsOwnWriters()throws Exception {
        var s=past().levelLoadingSource();
        assertEquals(List.of(0xA880,0xA8A0,0xA8C0,0xA8E0,0xA900,0xA920,0xA940,0xA960,0xA980,0xA9A0,0xA9C0,0xA9E0,0xAA00,0xAA20,0xAA40,0xAA60,0xAA80,0xAAA0,0xAAC0,0xAAE0,0xAB00,0xAB20,0xAB40,0xAB60,0xAB80,0xABA0,0xABC0,0xABE0,0xAC00,0xAC20,0xAC40,0xAC60,0xAC80,0xACA0,0xACC0,0xACE0),s.standardPowerupDestinations());
        assertTrue(s.standardHudDestination(0xB060));assertTrue(s.standardHudDestination(0xB4A0));
        assertFalse(s.standardHudDestination(0x7060));assertFalse(s.standardHudDestination(0x74A0));
        assertFalse(s.standardHudDestination(0xA880));assertFalse(s.standardPowerupDestination(0xA860));
    }
    @Test void pastResidencyConstructionMarksEveryAdvertisedWindowUnknown()throws Exception {
        var s=past().levelLoadingSource();var state=CdR11aCameraArtResidency.unknownForLoading(s);
        assertTrue(state.ownsLoadingSource(s));assertFalse(state.ownsLoadingSource(past().levelLoadingSource()));
        for(var w:state.windows())for(int d=w.destinationWord();d<w.destinationWord()+w.byteLength();d+=32)
            {int tile=d/32;assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class,()->CdR11aCameraArtResidency.readTile(state,tile));}
        assertNotNull(CdR11aSectionArt.fromLoadingSource(s));
    }
    @Test void immutableFacadeLeavesUnbackedBlockAndPatternReadsTypedUnknown()throws Exception {
        var terrain=past().levelLoadingSource().terrain();
        var block=assertThrows(CdStageTerrain.UnavailableRead.class,()->terrain.blockWord(terrain.blockCount(),0,0));
        assertEquals(CdStageTerrain.ReadOwner.BLOCK_STAGING,block.owner());
        var pattern=assertThrows(CdStageTerrain.UnavailableRead.class,()->terrain.pixel(terrain.patternCount(),0,0));
        assertEquals(CdStageTerrain.ReadOwner.PATTERN_SOURCE,pattern.owner());assertThrows(UnsupportedOperationException.class,terrain::shape);
    }
    @Test void immutableFacadeDoesNotConstructInvocationOrRestoreAuthority()throws Exception {
        var s=past().levelLoadingSource();assertTrue(s.ordinaryObjectSource().isEmpty());
        assertThrows(UnsupportedOperationException.class,s::originalR11Assets);
        for(var constructor:CdLevelLoadingSource.class.getDeclaredConstructors())assertFalse(java.lang.reflect.Modifier.isPublic(constructor.getModifiers()));
        for(var method:CdLevelLoadingSource.class.getMethods())assertFalse(Set.of("invoke","restore","fullPermit","framePermit","beginService").contains(method.getName()));
    }
    @Test void lazyEntryFailureStopsBeforeLaterEntryReadsAndDoesNotPoisonCache()throws Exception {
        var r=spy(past());var fault=new IllegalArgumentException("exact index fault");
        doThrow(fault).when(r).view(B.range("LevelDataIndex"));
        assertSame(fault,assertThrows(IllegalArgumentException.class,()->r.levelLoadingSource().bounds()));
        verify(r,never()).view(B.range("LevelStartLoc"));verify(r,never()).view(B.range("CamBounds"));
        doCallRealMethod().when(r).view(B.range("LevelDataIndex"));assertEquals(0x2897,r.levelLoadingSource().bounds().right());
    }
    @Test void presentLegacyPlanAndWaterCompletionRemainTheirExistingPaths()throws Exception {
        var a=reader(CdStagePayload.R11A);var s=a.levelLoadingSource();var plan=a.originalR11LoadPlan();
        assertSame(plan.bounds(),s.bounds());assertSame(plan.initialCentre(),s.initialCentre());assertSame(plan.levelIndex(),s.levelIndex());
        assertEquals(0x7F0,s.standardBackgroundRowMask());assertEquals(130,s.backgroundSectionIds().length());
        var water=reader(CdStagePayload.R41A).levelLoadingSource();assertEquals(CdLevelLoadingSource.Completion.WATER_PALETTE_AFTER_FADE,water.completion());
        assertThrows(UnsupportedOperationException.class,water::standardBackgroundPointers);
    }
    @Test void otherEightPpzAndForeignLoadingRolesRemainUnavailable()throws Exception {
        for(var p:CdPpzDataLayout.Programme.values())if(p!=B&&p!=CdPpzDataLayout.Programme.R11A){
            var r=reader(p.payload());assertThrows(IllegalArgumentException.class,r::levelLoadingSource);
        }
        var b=past();assertThrows(IllegalArgumentException.class,b::retainedOriginalR11Image);
        assertThrows(IllegalArgumentException.class,()->b.view(CdPpzDataLayout.Programme.R11A.range("BackgroundRows")));
    }
}
