package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.media.*;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Written before accessor implementation. All Java and lifecycle execution UNRUN. */
class TestCdR11aRetainedStandardLoadingSource {
    private record Fixture(CdDiscHeader header,CdObservedMediaIdentity identity,CdProgramCatalogue catalogue,CdMmdImage image) { }
    private static Fixture retained;
    private static synchronized Fixture actual()throws Exception {
        if(retained==null)try(var media=TestCdCczProgrammeData.open()) {
            retained=new Fixture(media.header(),media.identity(),media.catalogue().orElseThrow(),media.mmd(CdNativeFile.R11A));
        }
        return retained;
    }
    private static CdProgramAssets source(Fixture f,CdMmdImage image)throws IOException {
        var media=mock(CdProgramAssets.class);when(media.header()).thenReturn(f.header());when(media.identity()).thenReturn(f.identity());
        when(media.catalogue()).thenReturn(Optional.of(f.catalogue()));when(media.mmd(CdNativeFile.R11A)).thenReturn(image);return media;
    }
    private static CdR11aAssets load(CdMmdImage image)throws Exception{return CdR11aAssetLoader.load(source(actual(),image),CdReleaseVariant.USA,CdStagePayload.R11A.stage());}
    @Test void actualUsaAccessorReturnsOnlyItsOwnReaderCachedStandardSource()throws Exception {
        var f=actual();var media=source(f,f.image());var assets=CdR11aAssetLoader.load(media,CdReleaseVariant.USA,CdStagePayload.R11A.stage());
        assertTrue(assets.hasRetainedStandardLoadingSource());var s=assets.retainedStandardLoadingSource();assertSame(s,assets.retainedStandardLoadingSource());assertSame(s,assets.internalReader().levelLoadingSource());
        assertEquals(CdLevelLoadingSource.Completion.STANDARD,s.completion());assertSame(assets.terrain().stageTerrain(),s.terrain());assertSame(assets.player(),s.playerSource().player());assertSame(assets.sonic(),s.sonic());
        verify(media,times(1)).mmd(CdNativeFile.R11A);
    }
    @Test void equalIndependentMediaReadersCannotSubstituteTheirSourceOrPlayerRelation()throws Exception {
        var a=load(actual().image());var b=load(actual().image());assertEquals(a.identity(),b.identity());
        var first=a.retainedStandardLoadingSource();var second=b.retainedStandardLoadingSource();assertNotSame(first,second);
        assertTrue(first.ownsPlayerSource(first.playerSource()));assertFalse(first.ownsPlayerSource(second.playerSource()));
        assertThrows(IllegalArgumentException.class,()->first.requirePlayerSource(second.playerSource()));
        assertSame(first,a.retainedStandardLoadingSource());assertSame(second,b.retainedStandardLoadingSource());
    }
    @Test void constructedComponentAggregateCannotCreateAReaderFromCopiedMetadataOrProducts()throws Exception {
        var f=actual();var a=load(f.image());
        var component=new CdR11aAssets(a.profile(),a.identity(),f.image(),a.blocksDecode(),a.levelTilesDecode(),a.terrain(),a.loadPlan(),a.placements(),a.sonic(),a.player(),a.shield());
        assertEquals(a.identity(),component.identity());assertFalse(component.hasRetainedStandardLoadingSource());
        assertThrows(UnsupportedOperationException.class,component::retainedStandardLoadingSource);assertThrows(UnsupportedOperationException.class,component::internalReader);
    }
    @Test void cachedSourceBorrowStillRejectsAChangedRetainedOriginalBeforeReturning()throws Exception {
        var f=actual();var image=spy(f.image());var assets=load(image);assets.retainedStandardLoadingSource();
        int size=image.fileBytes();byte[] changed=f.image().readFile(0,size);changed[0x10000]^=1;doReturn(changed).when(image).readFile(0,size);
        assertThrows(IOException.class,assets::retainedStandardLoadingSource);
    }
    @Test void sourceBorrowDoesNotRetargetOtherAggregateProducts()throws Exception {
        var a=load(actual().image());var player=a.player();var plan=a.loadPlan();var terrain=a.terrain();var sonic=a.sonic();var blocks=a.blocksDecode();var tiles=a.levelTilesDecode();
        var source=a.retainedStandardLoadingSource();source.requireRetained();assertSame(player,a.player());assertSame(plan,a.loadPlan());assertSame(terrain,a.terrain());assertSame(sonic,a.sonic());assertSame(blocks,a.blocksDecode());assertSame(tiles,a.levelTilesDecode());
        assertThrows(CdStageTerrain.UnavailableRead.class,()->source.terrain().blockWord(804,0,0));
    }
}
