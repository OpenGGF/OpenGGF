package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.media.*;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Frozen before production; all Java UNRUN. Immutable source relations never grant a live service. */
class TestCdR11aStandardLoadingSource {
    private static final CdPpzDataLayout.Programme R11=CdPpzDataLayout.Programme.R11A;
    private static CdProgrammeReader reader(CdStagePayload selected)throws Exception {
        try(var media=TestCdCczProgrammeData.open()){return CdProgrammeReader.open(media,selected.stage());}
    }
    private static CdProgrammeReader original()throws Exception{return reader(CdStagePayload.R11A);}
    private static CdLevelLoadingSource standard(CdProgrammeReader r){return r.levelLoadingSource();}
    @Test void standardAndTtWaterHaveDistinctSemanticCompletion()throws Exception {
        var s=standard(original());var water=reader(CdStagePayload.R41A).levelLoadingSource();
        assertEquals(CdLevelLoadingSource.Completion.STANDARD,s.completion());
        assertEquals(CdLevelLoadingSource.Completion.WATER_PALETTE_AFTER_FADE,water.completion());
        assertEquals(0,s.nativeZoneActWord());assertEquals(1,s.ordinaryPeriod());assertEquals(0x200,water.nativeZoneActWord());
    }
    @Test void otherEightPpzDefinitionsDoNotAcquireStandardLoadingOrPlayerAdmission()throws Exception {
        for(var p:CdPpzDataLayout.Programme.values())if(p!=R11&&p!=CdPpzDataLayout.Programme.R11B) {
            var r=reader(p.payload());assertThrows(IllegalArgumentException.class,r::levelLoadingSource);
            if(r.sonic().sourceFormat()==CdSonicVisualAssets.SourceFormat.FRAMES_167)
                assertThrows(UnsupportedOperationException.class,r::playerSource);
            else assertThrows(IllegalArgumentException.class,r::playerSource);
        }
        var past=reader(CdStagePayload.R11B);
        assertTrue(past.playerSource().ownsProgrammeReader(past));assertEquals(0,past.playerSource().ordinaryPeriod());
        assertEquals(CdLevelLoadingSource.Completion.STANDARD,past.levelLoadingSource().completion());
    }
    @Test void actualPpzDataFacadeBorrowsTheSameReaderCachedSources()throws Exception {
        var r=original();var data=CdPpzProgrammeData.fromReader(r);var s=data.levelLoadingSource();
        assertSame(s,r.levelLoadingSource());assertSame(data.playerSource(),s.playerSource());assertTrue(data.ownsReader(r));
        assertEquals(CdNativeFile.R11A,s.file());assertEquals(R11.payload().stage(),s.stage());
    }
    @Test void standardPlayerKeepsItsActualTerrainVisualAndPlayerReaderRelation()throws Exception {
        var r=original();var s=standard(r);var player=s.playerSource();
        assertTrue(player.ownsProgrammeReader(r));assertSame(r.originalR11PlayerAssets(),player.player());
        assertSame(r.terrain(),player.terrain());assertSame(r.sonic(),player.sonic());assertSame(player,s.retainedPlayerSource());
        assertEquals(CdR11aPlayerAssets.SourceGraph.PPZ,player.player().sourceGraph());
        assertEquals(CdSonicVisualAssets.SourceFormat.FRAMES_198_WITH_EVEN_BYTE,player.sonic().sourceFormat());
        assertTrue(s.ownsPlayerSource(player));s.requirePlayerSource(player);
    }
    @Test void standardEntryValuesUseTheOriginalCachedPlanWithoutAnotherDecode()throws Exception {
        var r=original();var s=standard(r);var plan=r.originalR11LoadPlan();
        assertSame(plan.bounds(),s.bounds());assertSame(plan.initialCentre(),s.initialCentre());assertSame(plan.levelIndex(),s.levelIndex());
        assertEquals(0xA0,s.cameraCentreX());assertEquals(3,s.levelPlcId());assertEquals(2,s.blocksPlcId());
        assertArrayEquals(plan.paletteBindings().get(0).bytes().copy(),s.playerPalette().copy());
        assertArrayEquals(plan.paletteBindings().get(1).bytes().copy(),s.levelPalette().copy());
    }
    @Test void standardTablesAreTypedViewsOfTheOneCommonParser()throws Exception {
        var r=original();var s=standard(r);var t=s.loadTables();assertSame(t,s.loadTables());
        assertEquals(19,t.plcs().size());assertEquals(6,t.palettes().size());
        var sections=assertInstanceOf(CdLevelLoadingSource.CameraSections.class,t.sections());assertEquals(5,sections.rows().size());
        for(int i=0;i<19;i++)assertEquals(r.loadTables().plcs().get(i).body().fileOffset(),t.plcs().get(i).bodyFileOffset());
        for(int i=0;i<6;i++)assertSame(r.loadTables().palettes().get(i).bytes(),t.palettes().get(i).bytes());
    }
    @Test void fiveFiniteStandardViewsRetainTheWrittenOriginalValuesAndSourceKeys()throws Exception {
        var r=original();var s=standard(r);
        assertEquals(0x31FC,r.view(CdPpzProgrammeProfiles.R11LoadingView.CAMERA_CENTRE).unsignedWord(0));
        assertEquals(0xA0,s.cameraCentreX());assertEquals(384,s.lifeIcon().length());assertEquals(0x23571A,s.lifeIconNativeAddress());
        assertEquals(0x23A6B8,s.collisionPointer(0));assertEquals(0x23A6B8,s.collisionSourceAddress());
        assertThrows(IllegalArgumentException.class,()->s.collisionPointer(1));
        assertSame(r.view(R11.range("BackgroundRead")),s.backgroundLayout());
        assertSame(r.view(R11.range("BackgroundRows")),s.backgroundSectionIds());
    }
    @Test void validMusicValuesMatchTheExistingOriginalBinding()throws Exception {
        CdR11aAssets assets;try(var media=TestCdCczProgrammeData.open()){assets=CdR11aAssetLoader.load(media,CdReleaseVariant.USA,R11.payload().stage());}
        var old=assets.loadingArtInputs();var s=standard(assets.internalReader());
        for(int raw:new int[]{0,1,2,0x82})for(int attack:new int[]{0,1})for(int future:new int[]{0,1})
            assertEquals(old.selectLevelMusic(0,raw,attack,future),s.selectLevelMusic(0,raw,attack,future));
    }
    @Test void unavailableMusicReadsStopAtTheFiniteByteWithoutDefaultCommands()throws Exception {
        var s=standard(original());var edge=assertThrows(CdLevelLoadingSource.StandardMusicReadBoundary.class,
            ()->s.selectLevelMusic(0x700,0,0,0));assertEquals(28,edge.index());assertEquals(0x201BA4L,edge.address());
        assertThrows(IllegalArgumentException.class,()->s.selectLevelMusic(0,256,0,0));
    }
    @Test void standardArtDescriptorsStayLazyAndBorrowCanonicalNativeInputs()throws Exception {
        var r=spy(original());var s=standard(r);clearInvocations(r);var sources=s.queuedArtSources();
        assertEquals(r.loadTables().encounteredSources().size(),sources.size());verify(r,never()).nativeInput(any());
        for(int i=0;i<sources.size();i++) {
            var key=r.loadTables().encounteredSources().get(i);var source=sources.get(i);assertTrue(source.belongsTo(s));
            assertEquals(0x200000L+r.fileOffset(key),source.nativeAddress());assertEquals(r.loadOutputBytes(key),source.outputBytes());
            assertSame(r.nativeInput(key),source.readerBytes());assertSame(source,s.queuedArtSources().get(i));
        }
    }
    @Test void standardBlocksAndIconUseTheirExistingReaderCache()throws Exception {
        var r=original();var s=standard(r);var key=R11.range("LevelBlocks");var blocks=s.blocksSource();
        assertSame(blocks,s.blocksSource());assertSame(r.nativeInput(key),blocks.readerBytes());assertSame(r.decoded(key).bytes(),s.decodedBlocks());
        assertSame(r.view(CdPpzProgrammeProfiles.R11LoadingView.LIFE_ICON),s.lifeIcon());assertSame(s.terrain(),r.terrain());
    }
    @Test void equalIndependentReadersAndArtDescriptorsDoNotShareCustody()throws Exception {
        var a=standard(original());var b=standard(original());assertNotSame(a,b);assertFalse(a.ownsPlayerSource(b.playerSource()));
        assertThrows(IllegalArgumentException.class,()->a.requirePlayerSource(b.playerSource()));
        assertFalse(a.blocksSource().belongsTo(b));assertFalse(b.blocksSource().belongsTo(a));
    }
    @Test void equalAndForeignPublicRangesCannotSelectStandardBytes()throws Exception {
        var s=standard(original());var key=R11.range("CamBounds");
        assertThrows(IllegalArgumentException.class,()->s.view(new CdPpzDataLayout.Range(key.key(),key.fileOffset(),key.length(),key.sha256())));
        assertThrows(IllegalArgumentException.class,()->s.view(CdPpzDataLayout.Programme.R12A.range("CamBounds")));
        assertThrows(UnsupportedOperationException.class,()->s.view(CdR4DataLayout.Programme.R41A.range("StageDataIndex")));
    }
    @Test void dirtyRetainedOriginalRejectsEvenAReusedFacade()throws Exception {
        CdMmdImage image;CdDiscHeader h;CdObservedMediaIdentity id;CdProgramCatalogue c;
        try(var media=TestCdCczProgrammeData.open()){image=spy(media.mmd(CdNativeFile.R11A));h=media.header();id=media.identity();c=media.catalogue().orElseThrow();}
        var media=mock(CdProgramAssets.class);when(media.header()).thenReturn(h);when(media.identity()).thenReturn(id);when(media.catalogue()).thenReturn(Optional.of(c));when(media.mmd(CdNativeFile.R11A)).thenReturn(image);
        var r=CdProgrammeReader.open(media,R11.payload().stage());var s=standard(r);int fileBytes=image.fileBytes();byte[] changed=image.readFile(0,fileBytes);changed[0x2946]^=1;doReturn(changed).when(image).readFile(0,fileBytes);
        assertThrows(IllegalStateException.class,r::levelLoadingSource);assertThrows(IllegalStateException.class,s::requireRetained);assertThrows(IllegalStateException.class,s::cameraCentreX);
    }
    @Test void readOnlyDataConstructionHasNoPublicPermitOrRestoreApi() {
        for(var type:List.of(CdLevelLoadingSource.class,CdStagePlayerSource.class,CdPpzProgrammeData.class)) {
            assertEquals(0,type.getConstructors().length);
            for(var m:type.getDeclaredMethods())if(Modifier.isPublic(m.getModifiers())) {
                String contract=(m.getName()+" "+m.getReturnType().getSimpleName()).toLowerCase(Locale.ROOT);
                for(String forbidden:List.of("invocation","fullpermit","readypermit","restorepermit","framepermit"))assertFalse(contract.contains(forbidden),contract);
            }
        }
    }
    @Test void oldAggregateFacadeRetainsItsOriginalSourceAndCompletion()throws Exception {
        CdR11aAssets assets;try(var media=TestCdCczProgrammeData.open()){assets=CdR11aAssetLoader.load(media,CdReleaseVariant.USA,R11.payload().stage());}
        var old=CdLevelLoadingSource.fromOriginalR11(assets);assertTrue(old.ownsOriginalR11(assets));assertSame(assets,old.originalR11Assets());
        assertEquals(CdLevelLoadingSource.Completion.STANDARD,old.completion());assertSame(assets.loadPlan().bounds(),old.bounds());assertTrue(old.playerSource().ownsPresent(assets));
        assertFalse(standard(assets.internalReader()).ownsOriginalR11(assets));
        assertTrue(standard(assets.internalReader()).ownsRetainedStandardAssets(assets));
        assertSame(assets.retainedStandardLoadingSource(),standard(assets.internalReader()));
    }
    @Test void standardPlaneUsesItsCompletedFactoryAndStillRejectsWaterInputs()throws Exception {
        var s=standard(original());assertThrows(UnsupportedOperationException.class,()->s.initialWaterHeight(1));
        assertThrows(UnsupportedOperationException.class,()->s.waterPalette(3));assertThrows(UnsupportedOperationException.class,s::waterScrollProfile);
        assertInstanceOf(com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane.class,s.backgroundPlane());
        assertInstanceOf(CdLevelLoadingSource.PointerTables.class,s.background());
    }
    @Test void foreignNonTtReaderRejectsBeforeBorrowingStandardViews()throws Exception {
        var r=spy(reader(CdStagePayload.R12A));clearInvocations(r);assertThrows(IllegalArgumentException.class,r::levelLoadingSource);
        verify(r,never()).view(any());verify(r,never()).originalR11LoadPlan();
    }
    @Test void musicConsumerBorrowPrecedesTheFiniteCommandTable()throws Exception {
        var r=spy(original());var s=standard(r);clearInvocations(r);s.selectLevelMusic(0,1,0,0);
        var order=inOrder(r);order.verify(r).view(CdPpzProgrammeProfiles.R11LoadingView.MUSIC_CONSUMER);order.verify(r).view(CdPpzProgrammeProfiles.R11LoadingView.MUSIC);
    }
    @Test void musicReadFaultPreservesItsOriginalExceptionAndDoesNotInventAValue()throws Exception {
        var r=spy(original());var s=standard(r);var original=new IllegalStateException("finite source unavailable");
        doThrow(original).when(r).view(CdPpzProgrammeProfiles.R11LoadingView.MUSIC);
        assertSame(original,assertThrows(IllegalStateException.class,()->s.selectLevelMusic(0,1,0,0)));
    }
}
