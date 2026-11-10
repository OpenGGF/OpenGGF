package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.media.CdMmdImage;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import static com.openggf.game.soniccd.level.CdR11aAsset.*;
import static org.junit.jupiter.api.Assertions.*;

/** Written before the central source/geometry implementation. All Java UNRUN. */
class TestCdR11aSameReaderPlayerGeometry {
    private static CdR11aAssets actual()throws Exception {
        try(var media=TestCdCczProgrammeData.open()){return CdR11aAssetLoader.load(media,CdReleaseVariant.USA,CdStagePayload.R11A.stage());}
    }
    private static CdR11aAssets component(CdR11aAssets a,CdMmdImage image)throws IOException {
        var t=CdR11aTerrain.decode(a.decoded(LEVEL_BLOCKS).copy(),a.decoded(ART_LEVEL_TILES).copy(),a.view(LEVEL_CHUNKS).bytes().copy(),
                a.view(LAYOUT_FG).bytes().copy(),a.view(LAYOUT_BG).bytes().copy(),a.view(LEVEL_COLLISION).bytes().copy(),a.view(COL_ANGLE_MAP).bytes().copy(),a.view(COL_HEIGHT_MAP).bytes().copy(),a.view(COL_WIDTH_MAP).bytes().copy());
        return new CdR11aAssets(a.profile(),a.identity(),image,a.blocksDecode(),a.levelTilesDecode(),t,a.loadPlan(),a.placements(),a.sonic(),a.player(),a.shield());
    }
    @Test void presentFactoryReturnsTheSamePrivateReaderPlayerSourceAndRetainsAggregateOwnership()throws Exception {
        var a=actual();var loading=a.retainedStandardLoadingSource();var source=CdStagePlayerSource.fromPresent(a);
        assertSame(loading.playerSource(),source);assertSame(source,CdStagePlayerSource.fromPresent(a));assertTrue(source.ownsPresent(a));assertFalse(source.ownsPresent(null));
        assertTrue(source.ownsProgrammeReader(a.internalReader()));assertTrue(loading.ownsPlayerSource(source));assertSame(a.terrain().stageTerrain(),source.terrain());assertSame(a.player(),source.player());assertSame(a.sonic(),source.sonic());
        assertSame(a.internalReader().placements(),a.placements());assertSame(a.placements(),loading.placements());assertSame(a.placements(),SonicCdLevel.fromLoadingSource(loading).nativePlacements());
        assertEquals(CdR11aPlayerAssets.SourceGraph.PPZ,source.player().sourceGraph());assertEquals(0,source.nativeZoneActWord());assertEquals(1,source.ordinaryPeriod());
        assertThrows(UnsupportedOperationException.class,source.terrain()::shape);
    }
    @Test void equalSeparateAggregatesAndCopiedComponentMetadataCannotAcquireTheRelation()throws Exception {
        var first=actual();var second=actual();assertEquals(first.identity(),second.identity());var source=CdStagePlayerSource.fromPresent(first);
        assertFalse(source.ownsPresent(second));assertNotSame(source,CdStagePlayerSource.fromPresent(second));assertFalse(second.retainedStandardLoadingSource().ownsPlayerSource(source));
        try(var media=TestCdCczProgrammeData.open()) {
            var copied=component(first,media.mmd(CdNativeFile.R11A));assertEquals(first.identity(),copied.identity());assertFalse(source.ownsPresent(copied));
            var legacy=CdStagePlayerSource.fromPresent(copied);assertTrue(legacy.ownsPresent(copied));assertFalse(legacy.ownsPresent(first));assertFalse(legacy.ownsProgrammeReader(first.internalReader()));
            assertEquals(CdStageTerrain.Shape.PPZ1_PRESENT,legacy.terrain().shape());assertFalse(copied.hasRetainedStandardLoadingSource());
        }
    }
    @Test void typedR11GeometryAndStandardGeometryDoNotDemandOrFabricateALegacyShape()throws Exception {
        var a=actual();var source=a.retainedStandardLoadingSource();assertThrows(UnsupportedOperationException.class,a.terrain().stageTerrain()::shape);
        var aggregateGeometry=new SonicCdLevel(a);assertSame(a,aggregateGeometry.assets());assertTrue(aggregateGeometry.loadingSource().ownsOriginalR11(a));
        var standardGeometry=SonicCdLevel.fromLoadingSource(source);assertSame(source,standardGeometry.loadingSource());assertFalse(source.hasOriginalR11Aggregate());
        assertThrows(UnsupportedOperationException.class,standardGeometry::assets);assertThrows(UnsupportedOperationException.class,source.terrain()::shape);
        assertEquals(804,source.terrain().blockCount());assertEquals(800,source.terrain().patternCount());assertEquals(121,source.terrain().chunkCount());
    }
    @Test void retainedAggregateOriginStillKeepsItsPublicLevelAssetsRelation()throws Exception {
        var a=actual();var aggregate=CdLevelLoadingSource.fromOriginalR11(a);assertTrue(aggregate.hasOriginalR11Aggregate());
        var level=SonicCdLevel.fromLoadingSource(aggregate);assertSame(a,level.assets());assertSame(aggregate,level.loadingSource());
        assertTrue(aggregate.playerSource().ownsPresent(a));assertSame(a.retainedStandardLoadingSource().playerSource(),aggregate.playerSource());
    }
    @Test void pureCallerJoinsPreserveTheLoadedProductsAndDoNotPromoteUnknownBlockBacking()throws Exception {
        var a=actual();var p=a.player();var plan=a.loadPlan();var t=a.terrain();var blocks=a.blocksDecode();var tiles=a.levelTilesDecode();
        var s=CdStagePlayerSource.fromPresent(a);new SonicCdLevel(a);SonicCdLevel.fromLoadingSource(a.retainedStandardLoadingSource());
        assertSame(p,a.player());assertSame(plan,a.loadPlan());assertSame(t,a.terrain());assertSame(blocks,a.blocksDecode());assertSame(tiles,a.levelTilesDecode());
        assertThrows(CdStageTerrain.UnavailableRead.class,()->s.terrain().blockWord(804,0,0));
    }
}
