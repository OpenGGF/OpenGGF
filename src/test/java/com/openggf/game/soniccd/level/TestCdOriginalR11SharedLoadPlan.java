package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.media.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Tests frozen before production. Source-proposed only; all Java UNRUN.
 * Validates the actual R11A loader's immutable shared parser join, not a live loading lifecycle. */
class TestCdOriginalR11SharedLoadPlan {
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
    private static CdR11aProfile profile(CdReleaseVariant variant){return CdR11aProfile.forStage(variant,R11.payload().stage());}
    private static void sameValues(CdR11aLoadPlan expected,CdR11aLoadPlan actual) {
        var a=expected.levelIndex();var b=actual.levelIndex();assertEquals(a.levelArt(),b.levelArt());assertEquals(a.blocks(),b.blocks());assertEquals(a.chunksNativeAddress(),b.chunksNativeAddress());
        assertArrayEquals(a.uninterpretedTrailing().copy(),b.uninterpretedTrailing().copy());assertArrayEquals(a.paletteIds().copy(),b.paletteIds().copy());
        assertEquals(expected.initialCentre(),actual.initialCentre());assertEquals(expected.bounds(),actual.bounds());
        assertEquals(expected.plcs(),actual.plcs());assertEquals(expected.sections(),actual.sections());
        assertEquals(expected.paletteBindings().size(),actual.paletteBindings().size());
        for(int i=0;i<expected.paletteBindings().size();i++) {
            var e=expected.paletteBindings().get(i);var r=actual.paletteBindings().get(i);
            assertEquals(e.nativeId(),r.nativeId());assertEquals(e.destinationWord(),r.destinationWord());assertEquals(e.longwordCounter(),r.longwordCounter());assertSame(e.source(),r.source());assertArrayEquals(e.bytes().copy(),r.bytes().copy());
        }
        assertArrayEquals(expected.initialPalette().copy(),actual.initialPalette().copy());for(int i=0;i<64;i++)assertEquals(expected.colorWord(i),actual.colorWord(i));
    }
    @Test void actualLoaderReturnsTheSameReaderCachedPlanAlongsideThePreservedPlayerCache()throws Exception {
        var f=actual();var media=source(f,f.image());var assets=CdR11aAssetLoader.load(media,CdReleaseVariant.USA,R11.payload().stage());var r=assets.internalReader();
        assertSame(assets.loadPlan(),r.originalR11LoadPlan());assertSame(assets.loadPlan(),r.originalR11LoadPlan());
        assertSame(assets.player(),r.originalR11PlayerAssets());assertSame(assets.sonic(),r.sonic());verify(media,times(1)).mmd(CdNativeFile.R11A);
    }
    @Test void originalUsaPlanValuesRemainEqualToTheRetainedComponentDecoder()throws Exception {
        var f=actual();var r=reader(f.image());var expected=CdR11aLoadPlan.decode(profile(CdReleaseVariant.USA),f.image());var joined=r.originalR11LoadPlan();
        assertNotSame(expected,joined);sameValues(expected,joined);
        assertEquals(19,joined.plcs().size());assertEquals(5,joined.sections().size());assertEquals(0x0081,joined.levelIndex().uninterpretedTrailing().unsignedWord(0));
    }
    @Test void projectedPlcRowsRetainEveryActualBodyRangeSourceAndAlias()throws Exception {
        var r=reader(actual().image());var plan=r.originalR11LoadPlan();var shared=r.loadTables();var profile=profile(CdReleaseVariant.USA);
        for(int i=0;i<19;i++) {
            var legacy=plan.plcs().get(i);var common=shared.plcs().get(i);assertEquals(i,legacy.nativeId());assertEquals(common.body().fileOffset(),legacy.bodyRange().fileOffset());
            assertEquals(2+6*common.body().entries().size(),legacy.bodyRange().length());assertEquals(common.body().entries().size(),legacy.entries().size());
            for(int j=0;j<legacy.entries().size();j++) {
                var a=legacy.entries().get(j);var b=common.body().entries().get(j);
                assertEquals(b.sourceNativeAddress(),Integer.toUnsignedLong(a.sourceRange().nativeAddress()));assertEquals(b.vramAddress(),a.vramAddress());assertEquals(profile.range(a.source()),a.sourceRange());
                assertEquals(CdR11aAsset.Encoding.NEMESIS,a.source().encodingForReviewedConsumer());assertSame(b.source(),r.loadSource(b.sourceNativeAddress()));
            }
            for(int j=0;j<i;j++)assertEquals(shared.plcs().get(j).body().fileOffset()==common.body().fileOffset(),plan.plcs().get(j).bodyRange().equals(legacy.bodyRange()));
        }
        assertSame(shared.plcs().get(0).body(),shared.plcs().get(3).body());
    }
    @Test void sectionAndPaletteAdaptersConsumeTheCommonTypedValuesAndCachedBytes()throws Exception {
        var r=reader(actual().image());var plan=r.originalR11LoadPlan();var tables=r.loadTables();var sections=assertInstanceOf(CdProgrammeDefinition.CameraSections.class,tables.sectionProgramme());
        for(int i=0;i<5;i++) {
            var a=plan.sections().get(i);var b=sections.rows().get(i);assertEquals(b.threshold(),a.rightThresholdWord());assertEquals(b.initial(),a.initialPlcId());assertEquals(b.update(),a.updatePlcId());
        }
        for(int i=0;i<2;i++) {
            int id=i==0?3:5;var a=plan.paletteBindings().get(i);var b=tables.palettes().get(id);
            assertEquals(id,a.nativeId());assertSame(b.bytes(),a.bytes());assertEquals(b.destinationWord(),a.destinationWord());assertEquals(b.longwordCounter(),a.longwordCounter());
            assertSame(r.view(R11.range("Palette"+id)),a.bytes());
        }
        assertArrayEquals(plan.paletteBindings().get(0).bytes().copy(),plan.initialPalette().slice(0,32).copy());
        assertArrayEquals(plan.paletteBindings().get(1).bytes().copy(),plan.initialPalette().slice(32,96).copy());
    }
    @Test void actualUsaLoaderBypassesTheDisplacedLocalPlanParser()throws Exception {
        var f=actual();var media=source(f,f.image());
        try(var component=mockStatic(CdR11aLoadPlan.class,CALLS_REAL_METHODS);var parser=mockStatic(CdProgrammeLoadTables.class,CALLS_REAL_METHODS)) {
            var assets=CdR11aAssetLoader.load(media,CdReleaseVariant.USA,R11.payload().stage());var r=assets.internalReader();
            component.verify(()->CdR11aLoadPlan.decode(any(CdR11aProfile.class),any(CdMmdImage.class)),never());
            component.verify(()->CdR11aLoadPlan.fromOriginalReader(r),times(1));parser.verify(()->CdProgrammeLoadTables.decode(r),times(1));
            r.originalR11LoadPlan();assets.paletteCycle();assertSame(assets.loadPlan(),r.originalR11LoadPlan());parser.verify(()->CdProgrammeLoadTables.decode(r),times(1));
        }
    }
    @Test void indexCentreBoundsAndOriginalTerrainChecksPrecedeTheCommonParserRequest()throws Exception {
        var image=spy(actual().image());var r=spy(reader(image));clearInvocations(image,r);
        r.originalR11LoadPlan();var order=inOrder(r,image);order.verify(r).retainedOriginalR11Image();
        order.verify(r).view(R11.range("LevelDataIndex"));order.verify(r).view(R11.range("LevelStartLoc"));order.verify(r).view(R11.range("CamBounds"));
        order.verify(image).nativeSlice(CdMmdImage.Domain.ORIGINAL_WORD_RAM,0x23AA3C,72);
        order.verify(image).nativeSlice(CdMmdImage.Domain.ORIGINAL_WORD_RAM,0x201A9A,32);order.verify(r).loadTables();
    }
    @Test void foreignProgrammeRejectsBeforeViewsOrCommonParserAreBorrowed()throws Exception {
        try(var media=TestCdCczProgrammeData.open()) {
            var r=spy(CdProgrammeReader.open(media,CdStagePayload.R12A.stage()));clearInvocations(r);
            assertThrows(IllegalArgumentException.class,r::originalR11LoadPlan);verify(r,never()).view(any());verify(r,never()).loadTables();
        }
    }
    @Test void commonParserFailureKeepsItsOriginalThrowableAndPublishesNoPlanCache()throws Exception {
        var r=reader(actual().image());var original=new IllegalStateException("controlled common parser fault");
        try(var parser=mockStatic(CdProgrammeLoadTables.class,CALLS_REAL_METHODS)) {
            parser.when(()->CdProgrammeLoadTables.decode(r)).thenThrow(original).thenCallRealMethod();
            parser.clearInvocations(); // Exclude only CALLS_REAL_METHODS setup; count the fault and retry below.
            assertSame(original,assertThrows(IllegalStateException.class,r::originalR11LoadPlan));
            var plan=r.originalR11LoadPlan();assertSame(plan,r.originalR11LoadPlan());parser.verify(()->CdProgrammeLoadTables.decode(r),times(2));
        }
    }
    @Test void changedRetainedOriginalRejectsEvenAfterSuccessfulPlanCaching()throws Exception {
        var f=actual();var image=spy(f.image());var r=reader(image);r.originalR11LoadPlan();
        int fileBytes=image.fileBytes();byte[] changed=f.image().readFile(0,fileBytes);changed[0xCF08]^=1;doReturn(changed).when(image).readFile(0,fileBytes);
        assertThrows(IllegalStateException.class,r::originalR11LoadPlan);
    }
    @Test void equalIndependentReadersAndLegacyComponentPlansNeverShareCacheIdentity()throws Exception {
        var f=actual();var first=reader(f.image());var second=reader(f.image());var a=first.originalR11LoadPlan();var b=second.originalR11LoadPlan();
        assertNotSame(a,b);assertNotSame(a,CdR11aLoadPlan.decode(profile(CdReleaseVariant.USA),f.image()));sameValues(a,b);
        assertThrows(UnsupportedOperationException.class,()->a.plcs().clear());assertThrows(UnsupportedOperationException.class,()->a.sections().clear());
        assertThrows(UnsupportedOperationException.class,()->a.paletteBindings().clear());byte[] changed=a.initialPalette().copy();Arrays.fill(changed,(byte)0);assertArrayEquals(b.initialPalette().copy(),a.initialPalette().copy());
    }
    @Test void planBorrowDoesNotConstructOrWarmTheLaterPlayerDataProduct()throws Exception {
        var r=reader(actual().image());var cache=CdProgrammeReader.class.getDeclaredField("originalR11Player");cache.setAccessible(true);assertNull(cache.get(r));
        r.originalR11LoadPlan();assertNull(cache.get(r));var p=r.originalR11PlayerAssets();assertSame(p,cache.get(r));
    }
    @Test void loaderPlanFailurePrecedesTheActualPlayerInputReadCut()throws Exception {
        var f=actual();var image=spy(f.image());var media=source(f,image);var original=new IllegalStateException("plan parser before Player DATA");
        try(var parser=mockStatic(CdProgrammeLoadTables.class,CALLS_REAL_METHODS)) {
            parser.when(()->CdProgrammeLoadTables.decode(any(CdProgrammeReader.class))).thenThrow(original);
            assertSame(original,assertThrows(IllegalStateException.class,()->CdR11aAssetLoader.load(media,CdReleaseVariant.USA,R11.payload().stage())));
            for(var key:CdPpzProgrammeProfiles.R11PlayerView.values())verify(image,never()).readFile(key.offset(),key.length());
        }
    }
    @ParameterizedTest @EnumSource(value=CdReleaseVariant.class,names={"JAPAN","EUROPE"})
    void regionalLoaderKeepsItsExistingComponentPlanPath(CdReleaseVariant variant)throws Exception {
        String root=System.getProperty("soniccd.media.root");assertNotNull(root);String folder=variant==CdReleaseVariant.JAPAN?"Japan":"Europe";Path cue;
        try(var files=Files.list(Path.of(root,folder))) {var cues=files.filter(x->x.toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();assertEquals(1,cues.size());cue=cues.getFirst();}
        try(var media=CdProgramAssets.open(cue,Optional.of(variant),Optional.empty());var component=mockStatic(CdR11aLoadPlan.class,CALLS_REAL_METHODS)) {
            var assets=CdR11aAssetLoader.load(media,variant,R11.payload().stage());
            component.verify(()->CdR11aLoadPlan.decode(any(CdR11aProfile.class),any(CdMmdImage.class)),times(1));
            component.verify(()->CdR11aLoadPlan.fromOriginalReader(any(CdProgrammeReader.class)),never());
            assertThrows(UnsupportedOperationException.class,assets::internalReader);
        }
    }
}
