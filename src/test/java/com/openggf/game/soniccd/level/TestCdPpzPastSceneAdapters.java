package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.resources.CdR11aCameraArtResidency;
import com.openggf.game.soniccd.resources.CdR11aPlcBuffer;
import com.openggf.game.soniccd.resources.CdR11aSectionArt;
import com.openggf.game.soniccd.scroll.CdR11aBackgroundScroll;
import com.openggf.game.soniccd.scroll.CdR11aForegroundScroll;
import com.openggf.game.soniccd.scroll.CdR11bBackgroundScroll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/** Tests authored before the adapter delta. Original media DATA only; all Java UNRUN.
 * Constructed inactive buffers below certify no original clear, Frame or invocation. */
class TestCdPpzPastSceneAdapters {
    private static CdLevelLoadingSource source(CdStagePayload payload) throws Exception {
        try (var media = TestCdCczProgrammeData.open()) {
            return CdProgrammeReader.open(media, payload.stage()).levelLoadingSource();
        }
    }

    @Test void pastSelectsItsAuthenticSemanticScrollProgramWithoutAVisit() throws Exception {
        var source = source(CdStagePayload.R11B);
        assertEquals(CdLevelLoadingSource.StandardBackgroundProgram.PARALLAX_CLOUD_BANDS,
                source.standardBackgroundProgram());
        assertEquals(0, source.ordinaryPeriod());
        assertEquals(0, source.nativeZoneActWord());
        assertFalse(source.hasOriginalR11Aggregate());
        assertThrows(UnsupportedOperationException.class, source::originalR11Assets);
    }

    @Test void presentAndWaterRetainTheirDistinctBackgroundContracts() throws Exception {
        assertEquals(CdLevelLoadingSource.StandardBackgroundProgram.CLOUDS_AND_WATER,
                source(CdStagePayload.R11A).standardBackgroundProgram());
        var water = source(CdStagePayload.R41A);
        assertEquals(CdLevelLoadingSource.Completion.WATER_PALETTE_AFTER_FADE, water.completion());
        assertThrows(UnsupportedOperationException.class, water::standardBackgroundProgram);
    }

    @Test void pastStaticSceneBorrowsTheSameFacadeAndActualProducts() throws Exception {
        var source = source(CdStagePayload.R11B);
        var scene = SonicCdLevel.fromLoadingSource(source);
        assertSame(source, scene.loadingSource());
        assertSame(source.initialCentre(), scene.initialCentre());
        assertSame(source.placements(), scene.nativePlacements());
        assertEquals(2, scene.getBackdropPaletteLine());
        assertEquals(256, scene.getBlockPixelSize());
        assertThrows(UnsupportedOperationException.class, scene::assets);
    }

    @Test void pastObjectMapperIsOneSameReaderCacheWithoutPresentOrWaterRelabelling() throws Exception {
        CdProgrammeReader reader;
        try (var media = TestCdCczProgrammeData.open()) {
            reader = CdProgrammeReader.open(media, CdStagePayload.R11B.stage());
        }
        var source = reader.levelLoadingSource();
        var objects = source.standardPastObjectSource();
        assertSame(objects, source.standardPastObjectSource());
        assertTrue(objects.ownsReader(reader));
        assertSame(reader.view(CdPpzProgrammeProfiles.R11PastLoadingView.OBJECT_INDEX),
                objects.view(CdR11bObjectData.Table.DISPATCH).bytes());
        var foreign = source(CdStagePayload.R11B).standardPastObjectSource();
        assertNotSame(objects, foreign);
        assertThrows(IllegalArgumentException.class,
                () -> foreign.requireView(objects.view(CdR11bObjectData.Table.DISPATCH)));
        assertThrows(UnsupportedOperationException.class,
                () -> source(CdStagePayload.R11A).standardPastObjectSource());
        assertThrows(UnsupportedOperationException.class,
                () -> source(CdStagePayload.R41A).standardPastObjectSource());
    }

    @Test void currentArtRequiresBothItsActualFacadeAndItsActualPlayer() throws Exception {
        var source = source(CdStagePayload.R11B);
        var foreign = source(CdStagePayload.R11B);
        var present = source(CdStagePayload.R11A);
        var art = CdR11aSectionArt.fromLoadingSource(source);
        assertTrue(art.ownsLoadingSource(source, source.playerSource()));
        assertFalse(art.ownsLoadingSource(source, foreign.playerSource()));
        assertFalse(art.ownsLoadingSource(foreign, foreign.playerSource()));
        assertFalse(art.ownsLoadingSource(present, present.playerSource()));
        assertFalse(art.ownsLoadingSource(null, source.playerSource()));
        assertFalse(art.ownsLoadingSource(source, null));
    }

    @Test void currentResidencyRequiresBothItsActualFacadeAndItsActualPlayer() throws Exception {
        var source = source(CdStagePayload.R11B);
        var foreign = source(CdStagePayload.R11B);
        var present = source(CdStagePayload.R11A);
        var resident = CdR11aCameraArtResidency.unknownForLoading(source);
        assertTrue(resident.ownsLoadingSource(source, source.playerSource()));
        assertFalse(resident.ownsLoadingSource(source, foreign.playerSource()));
        assertFalse(resident.ownsLoadingSource(foreign, foreign.playerSource()));
        assertFalse(resident.ownsLoadingSource(present, present.playerSource()));
        assertFalse(resident.ownsLoadingSource(null, source.playerSource()));
        assertFalse(resident.ownsLoadingSource(source, null));
    }

    @Test void presentPlayerAndArtKeepTheirExistingPositiveRelation() throws Exception {
        var source = source(CdStagePayload.R11A);
        var player = source.playerSource();
        var art = CdR11aSectionArt.fromLoadingSource(source);
        var resident = CdR11aCameraArtResidency.unknownForLoading(source);
        assertTrue(art.ownsLoadingSource(source));
        assertTrue(resident.ownsLoadingSource(source));
        assertTrue(art.ownsLoadingSource(source, player));
        assertTrue(resident.ownsLoadingSource(source, player));
        assertFalse(art.ownsLoadingAssets(null));
        assertFalse(resident.ownsLoadingAssets(null));
    }

    @Test void sourceOwnershipChecksNeverClearOrCertifyCurrentPixels() throws Exception {
        var source = source(CdStagePayload.R11B);
        var art = CdR11aSectionArt.fromLoadingSource(source);
        var resident = CdR11aCameraArtResidency.unknownForLoading(source);
        int pattern = resident.windows().getFirst().destinationWord() / 32;
        assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class,
                () -> CdR11aCameraArtResidency.readTile(resident, pattern));
        assertTrue(art.ownsLoadingSource(source, source.playerSource()));
        assertTrue(resident.ownsLoadingSource(source, source.playerSource()));
        assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class,
                () -> CdR11aCameraArtResidency.readTile(resident, pattern));
        var saved = new CdR11aSectionArt.SavedDecoder(CdR11aSectionArt.Writer.CLEARED, 0, 0, 0, 0, 0);
        var state = art.fromInactiveValues(resident, bytes(512, 0x37), 0xABCD,
                CdR11aPlcBuffer.cleared(), saved, 0, 0x45, 0x6789, 0xABCD);
        art.validateState(state);
        assertSame(resident, state.residency());
        assertEquals(0xABCD, state.sectionWord());
        assertEquals(0x6789, state.hintWord());
        var foreign = CdR11aCameraArtResidency.unknownForLoading(source(CdStagePayload.R11B));
        assertThrows(IllegalArgumentException.class, () -> art.fromInactiveValues(foreign,
                bytes(512, 0x37), 0xABCD, CdR11aPlcBuffer.cleared(), saved, 0, 0x45, 0x6789, 0xABCD));
    }

    @Test void pastScrollCapabilityUsesTheExistingDifferentNativeKernelAndOwnRows() throws Exception {
        var source = source(CdStagePayload.R11B);
        assertEquals(CdLevelLoadingSource.StandardBackgroundProgram.PARALLAX_CLOUD_BANDS,
                source.standardBackgroundProgram());
        assertEquals(34, source.backgroundSectionIds().length());
        assertEquals(0x1F0, source.standardBackgroundRowMask());
        var before = new CdR11aBackgroundScroll.State(0x11223344, 0x10203040,
                0x55667788, 0x00123456, 0x99AABBCC, 0x00789ABC,
                bytes(8, 0xA5), 0x1234, 0x5678, 0x9ABC,
                bytes(512, 0x5A), bytes(512, 0x6B), bytes(1024, 0x7C), 0x12345678);
        var setup = new CdR11aForegroundScroll.State(0x120, 0x00A01234,
                0x10, 0x10, 0, 0, 0, 0x310, 0, 0, 0);
        var after = CdR11bBackgroundScroll.init(before, setup);
        assertEquals(0x00903344, after.cameraBgX());
        assertEquals(0x00367788, after.cameraBg2X());
        assertEquals(0x0024BBCC, after.cameraBg3X());
        assertEquals(0x00140000, after.cameraBgY());
        assertSame(before.cloudRam(), after.cloudRam());
        assertArrayEquals(new byte[16], after.deformationRam().slice(0, 16).copy());
        assertEquals(0x6B, after.deformationRam().unsignedByte(16));
        assertSame(before.hscrollRam(), after.hscrollRam());
    }

    private static CdAssetBytes bytes(int size, int value) {
        var bytes = new byte[size];
        Arrays.fill(bytes, (byte) value);
        return CdAssetBytes.copyOf(bytes);
    }
}
