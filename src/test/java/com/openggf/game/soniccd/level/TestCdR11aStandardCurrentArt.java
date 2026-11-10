package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.resources.*;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Frozen before production; SOURCE-proposed controls only. All Java UNRUN. */
class TestCdR11aStandardCurrentArt {
    private static CdR11aAssets assets()throws Exception {
        try(var media=TestCdCczProgrammeData.open()){return CdR11aAssetLoader.load(media,CdReleaseVariant.USA,CdStagePayload.R11A.stage());}
    }
    private static CdLevelLoadingSource standard(CdR11aAssets assets){return assets.internalReader().levelLoadingSource();}
    private static CdR11aSectionArt.State blank(CdR11aSectionArt art,CdR11aCameraArtResidency.State residency){
        return art.fromInactiveValues(residency,CdAssetBytes.copyOf(new byte[512]),0,CdR11aPlcBuffer.cleared(),
            new CdR11aSectionArt.SavedDecoder(CdR11aSectionArt.Writer.CLEARED,0,0,0,0,0),0,0,0,0);
    }
    private static CdAssetBytes tile(int value){byte[] b=new byte[32];Arrays.fill(b,(byte)value);return CdAssetBytes.copyOf(b);}
    @Test void standardAndOldAggregateFactoryOriginsStayDistinct()throws Exception {
        var a=assets();var standard=standard(a);var old=CdLevelLoadingSource.fromOriginalR11(a);
        assertFalse(standard.hasOriginalR11Aggregate());assertTrue(old.hasOriginalR11Aggregate());
        var shared=CdR11aSectionArt.fromLoadingSource(standard);var legacy=CdR11aSectionArt.fromLoadingSource(old);
        assertTrue(shared.ownsLoadingSource(standard));assertTrue(shared.ownsLoadingAssets(a));assertTrue(legacy.ownsLoadingAssets(a));
    }
    @Test void standardUnknownGeometryEqualsTheOriginalReservedAndHudUnion()throws Exception {
        var a=assets();var old=CdR11aCameraArtResidency.unknownForLoading(a.loadingArtInputs());
        var source=standard(a);var current=CdR11aCameraArtResidency.unknownForLoading(source);
        assertEquals(old.windows(),current.windows());assertTrue(current.ownsLoadingSource(source));assertTrue(current.ownsLoadingAssets(a));
        for(var window:current.windows()) {
            assertEquals(CdR11aCameraArtResidency.Boundary.UNKNOWN_PATTERN,assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class,
                ()->CdR11aCameraArtResidency.readTile(current,window.destinationWord()/32)).boundary());
            assertEquals(CdR11aCameraArtResidency.Boundary.UNKNOWN_PATTERN,assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class,
                ()->CdR11aCameraArtResidency.readTile(current,(window.destinationWord()+window.byteLength()-32)/32)).boundary());
        }
    }
    @Test void descriptorsDoNotTurnAnyLegacyOrReservedTileKnown()throws Exception {
        var source=standard(assets());source.queuedArtSources();source.blocksSource();source.lifeIcon();
        var resident=CdR11aCameraArtResidency.unknownForLoading(source);
        for(int address:new int[]{0x6400,0x7400,0xA880,0xACC0,0xB420,0xD960})assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class,
            ()->CdR11aCameraArtResidency.readTile(resident,address/32));
    }
    @Test void standardHudMembershipMatchesTheExistingAuthenticatedStartupSource()throws Exception {
        var a=assets();var source=standard(a);var startup=a.startupObjects();var addresses=source.standardHudDestinations();
        assertFalse(addresses.isEmpty());assertThrows(UnsupportedOperationException.class,addresses::clear);
        for(int at=0;at<65536;at+=32)assertEquals(startup.hudDestination(at),source.standardHudDestination(at));
        for(int at:addresses)assertTrue(startup.hudDestination(at));assertFalse(source.standardHudDestination(0xF000));
    }
    @Test void standardPowerupMembershipComesFromTheActualSourceOperandWindow()throws Exception {
        var source=standard(assets());var tiles=source.standardPowerupDestinations();assertEquals(0x480/32,tiles.size());
        assertEquals(0xA880,tiles.getFirst());assertEquals(0xACE0,tiles.getLast());assertThrows(UnsupportedOperationException.class,tiles::clear);
        for(int at:tiles)assertTrue(source.standardPowerupDestination(at));
        assertFalse(source.standardPowerupDestination(0xA860));assertFalse(source.standardPowerupDestination(0xAD00));assertFalse(source.standardPowerupDestination(0xA881));
    }
    @Test void currentSourceEnvelopesKeepBothRealAdjacentBytesAndOldFiniteEof()throws Exception {
        var a=assets();var source=standard(a);var old=a.loadingArtInputs();
        for(var input:source.queuedArtSources()) {
            var expected=old.sources().stream().filter(s->s.include().nativeAddress()==input.nativeAddress()).findFirst().orElseThrow();
            assertArrayEquals(expected.readerBytes().copy(),source.sectionArtBytes(input).copy());
            assertSame(source.sectionArtBytes(input),source.sectionArtBytes(input));
        }
        assertArrayEquals(old.blocks().readerBytes().copy(),source.sectionArtBytes(source.blocksSource()).copy());
    }
    @Test void foreignDescriptorCannotSelectStandardCurrentBytes()throws Exception {
        var one=standard(assets());var two=standard(assets());var foreign=two.blocksSource();
        assertThrows(IllegalArgumentException.class,()->one.sectionArtBytes(foreign));
    }
    @Test void standardFactorySharesTheExistingPreparedPlcKernel()throws Exception {
        var a=assets();var source=standard(a);var art=CdR11aSectionArt.fromLoadingSource(source);
        var initial=blank(art,CdR11aCameraArtResidency.unknownForLoading(source));var queued=art.appendList(initial,0);var prepared=art.prepare(queued);var serviced=art.serviceFast(prepared);
        assertTrue(serviced.remainingTilesWord()<prepared.remainingTilesWord());
        var expected=a.internalReader().decoded(CdPpzDataLayout.Programme.R11A.range("Art_LevelTiles")).bytes().slice(0,32);
        assertArrayEquals(expected.copy(),CdR11aCameraArtResidency.readTile(serviced.residency(),0).copy());
        assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class,()->CdR11aCameraArtResidency.readTile(initial.residency(),0));
    }
    @Test void anUnknownListRetainsItsReachedCurrentPrefix()throws Exception {
        var source=standard(assets());var art=CdR11aSectionArt.fromLoadingSource(source);var start=blank(art,CdR11aCameraArtResidency.unknownForLoading(source));
        var written=art.writeCurrentPowerupTile(start,0xA880,tile(7));var fault=assertThrows(CdR11aSectionArt.SectionArtException.class,()->art.appendList(written,65535));
        assertEquals(written.residency(),fault.state().residency());assertEquals(written.queue(),fault.state().queue());assertTrue(fault.state().stopped());
    }
    @Test void standardHudWriterAdmitsOnlySourceAuthenticatedFontTiles()throws Exception {
        var source=standard(assets());var art=CdR11aSectionArt.fromLoadingSource(source);var initial=blank(art,CdR11aCameraArtResidency.unknownForLoading(source));
        int destination=source.standardHudDestinations().getFirst();var written=art.writeCurrentHudTile(initial,destination,tile(9));
        assertArrayEquals(tile(9).copy(),CdR11aCameraArtResidency.readTile(written.residency(),destination/32).copy());
        assertThrows(IllegalArgumentException.class,()->art.writeCurrentHudTile(initial,0xA880,tile(1)));
        assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class,()->CdR11aCameraArtResidency.readTile(initial.residency(),destination/32));
    }
    @Test void standardPowerupWriterKeepsTheWholeUnknownTailUntilWritten()throws Exception {
        var source=standard(assets());var art=CdR11aSectionArt.fromLoadingSource(source);var initial=blank(art,CdR11aCameraArtResidency.unknownForLoading(source));
        var written=art.writeCurrentPowerupTile(initial,0xA880,tile(5));assertArrayEquals(tile(5).copy(),CdR11aCameraArtResidency.readTile(written.residency(),0xA880/32).copy());
        assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class,()->CdR11aCameraArtResidency.readTile(written.residency(),0xACC0/32));
        assertThrows(IllegalArgumentException.class,()->art.writeCurrentPowerupTile(initial,0xAD00,tile(4)));
    }
    @Test void equalSourceProductsCannotAdoptAnotherArtsState()throws Exception {
        var source=standard(assets());var first=CdR11aSectionArt.fromLoadingSource(source);var other=CdR11aSectionArt.fromLoadingSource(source);var initial=blank(first,CdR11aCameraArtResidency.unknownForLoading(source));
        assertThrows(IllegalArgumentException.class,()->other.validateState(initial));
        var foreign=assets();assertFalse(first.ownsLoadingAssets(foreign));assertFalse(initial.residency().ownsLoadingAssets(foreign));
    }
    @Test void rebindingCopiesOnlyPreviouslyKnownPhysicalBytes()throws Exception {
        var source=standard(assets());var original=CdR11aCameraArtResidency.writeTile(CdR11aCameraArtResidency.unknown(),0x7400,tile(11));
        var bound=CdR11aCameraArtResidency.withLoadingSource(original,source);assertTrue(bound.ownsLoadingSource(source));
        assertArrayEquals(tile(11).copy(),CdR11aCameraArtResidency.readTile(bound,0x7400/32).copy());
        assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class,()->CdR11aCameraArtResidency.readTile(bound,0xA880/32));
    }
    @Test void currentSourceReadFailureKeepsItsOriginalException()throws Exception {
        var source=spy(standard(assets()));var original=new IOException("unavailable current source");
        doThrow(original).when(source).sectionArtBytes(any(CdLevelLoadingSource.ArtSource.class));
        assertSame(original,assertThrows(IOException.class,()->CdR11aSectionArt.fromLoadingSource(source)));
    }
    @Test void standardHudSourceFailureStopsBeforeUnknownGeometryCanBeReturned()throws Exception {
        var source=spy(standard(assets()));var original=new IllegalStateException("HUD source unavailable");
        doThrow(original).when(source).standardHudDestinations();
        assertSame(original,assertThrows(IllegalStateException.class,()->CdR11aCameraArtResidency.unknownForLoading(source)));
    }
    @Test void oldAggregateFactoriesRetainTheirExactSourceOwnership()throws Exception {
        var a=assets();var old=CdLevelLoadingSource.fromOriginalR11(a);var art=CdR11aSectionArt.fromLoadingSource(old);var resident=CdR11aCameraArtResidency.unknownForLoading(old);
        assertTrue(art.ownsLoadingAssets(a));assertTrue(resident.ownsLoadingAssets(a));assertFalse(resident.ownsLoadingSource(standard(a)));
    }
    @Test void ttStillOwnsItsShieldWindowWithoutBorrowingAnyPpzRegion()throws Exception {
        CdLevelLoadingSource source;try(var media=TestCdCczProgrammeData.open()){source=CdProgrammeReader.open(media,CdStagePayload.R41A.stage()).levelLoadingSource();}
        assertFalse(source.hasOriginalR11Aggregate());var resident=CdR11aCameraArtResidency.unknownForLoading(source);
        assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class,()->CdR11aCameraArtResidency.readTile(resident,0xA880/32));
        assertTrue(resident.windows().stream().anyMatch(w->w.destinationWord()<=0xA880&&w.destinationWord()+w.byteLength()>=0xAD00));
        assertEquals(CdR11aCameraArtResidency.Boundary.UNKNOWN_PATTERN,assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class,
            ()->CdR11aCameraArtResidency.readTile(resident,0x8120/32)).boundary());
        var outside=assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class,()->CdR11aCameraArtResidency.readTile(resident,0xFFE0/32));assertEquals(CdR11aCameraArtResidency.Boundary.OUTSIDE_OWNED_WINDOW,outside.boundary());
    }
    @Test void ttWriterAdmissionRetainsItsAuthenticatedSourceGate()throws Exception {
        CdLevelLoadingSource source;try(var media=TestCdCczProgrammeData.open()){source=CdProgrammeReader.open(media,CdStagePayload.R41A.stage()).levelLoadingSource();}
        var art=CdR11aSectionArt.fromLoadingSource(source);var initial=blank(art,CdR11aCameraArtResidency.unknownForLoading(source));
        assertThrows(IllegalArgumentException.class,()->art.writeCurrentPowerupTile(initial,0xAD00,tile(1)));
        assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class,()->CdR11aCameraArtResidency.readTile(initial.residency(),0xA880/32));
    }
    @Test void standardAccessorsRejectWaterAndForeignOriginalSources()throws Exception {
        CdLevelLoadingSource water;try(var media=TestCdCczProgrammeData.open()){water=CdProgrammeReader.open(media,CdStagePayload.R41A.stage()).levelLoadingSource();}
        assertThrows(UnsupportedOperationException.class,water::standardHudDestinations);assertThrows(UnsupportedOperationException.class,water::standardPowerupDestinations);
        try(var media=TestCdCczProgrammeData.open()){var foreign=CdProgrammeReader.open(media,CdStagePayload.R12A.stage());assertThrows(IllegalArgumentException.class,()->CdR11aStartupSource.standardHudWriter(foreign));}
    }
    @Test void publicPureFactoriesDoNotMintAnyLivePermit() {
        for(var type:List.of(CdR11aSectionArt.class,CdR11aCameraArtResidency.class,CdLevelLoadingSource.class))for(var method:type.getMethods()){
            String result=method.getReturnType().getSimpleName().toLowerCase(Locale.ROOT);for(var forbidden:List.of("invocation","readypermit","fullpermit","restorepermit"))assertFalse(result.contains(forbidden));
        }
    }
}
