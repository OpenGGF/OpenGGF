package com.openggf.game.soniccd.scene;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.media.CdTimeWarpPrograms;
import com.openggf.graphics.SpritePresentation;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Constructed inputs test the pure projection format; only the final retail case authenticates source assets. */
class TestCdWarpPresentation {
    private static final int BASE = 20000;

    @Test void disabledSceneSamplesNeitherPaletteNorAssetNorSatState() {
        var assets = mock(CdWarpAssets.class); var state = mock(CdWarpScene.State.class);
        assertEquals(SpritePresentation.Frame.empty(), CdWarpPresentation.background(assets, state, BASE));
        assertEquals(SpritePresentation.Frame.empty(), CdWarpPresentation.sprites(assets, state, BASE));
        verify(state, times(2)).displayEnabled(); verifyNoMoreInteractions(state); verifyNoInteractions(assets);
    }

    @Test void nullsAndWhole101PatternLogicalExtentRejectBeforeSourceReads() {
        var assets = mock(CdWarpAssets.class); var state = mock(CdWarpScene.State.class);
        assertThrows(NullPointerException.class, () -> CdWarpPresentation.background(null, state, BASE));
        assertThrows(NullPointerException.class, () -> CdWarpPresentation.sprites(assets, null, BASE));
        for (int base : new int[]{-1, Integer.MIN_VALUE, Integer.MAX_VALUE - 99, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> CdWarpPresentation.background(assets, state, base));
            assertThrows(IllegalArgumentException.class, () -> CdWarpPresentation.sprites(assets, state, base));
        }
        verifyNoInteractions(assets, state);
    }

    @Test void lastRepresentable101PatternExtentKeepsItsActualFinalId() {
        byte[] plane = new byte[4096]; word(plane, 0, 100);
        var result = CdWarpPresentation.background(assets(plane, art()), selected(0, new byte[640], 0), Integer.MAX_VALUE - 100);
        assertEquals(Integer.MAX_VALUE, result.tiles().getFirst().patternId());
        assertTrue(result.patternVersions().containsKey(Integer.MAX_VALUE));
    }

    @Test void backgroundLiteralSubtileAndNegativeScrollWrapRetainThePhysical32Rows() {
        byte[] plane = new byte[4096];
        for (int row = 0; row < 32; row++) for (int x = 0; x < 64; x++) word(plane, (row * 64 + x) * 2, row + 1);
        var assets = assets(plane, art());
        var aligned = CdWarpPresentation.background(assets, selected(0, new byte[640], 0), BASE);
        assertEquals(40 * 28, aligned.tiles().size());
        assertTile(aligned.tiles().get(0), BASE + 1, 0, 0);
        assertTile(aligned.tiles().get(40), BASE + 2, 0, 8);
        assertTile(aligned.tiles().get(40 * 27 + 39), BASE + 28, 312, 216);
        var wrapped = CdWarpPresentation.background(assets, selected(0xFFFC, new byte[640], 0), BASE);
        assertEquals(40 * 29, wrapped.tiles().size());
        assertTile(wrapped.tiles().get(0), BASE + 32, 0, -4);
        assertTile(wrapped.tiles().get(40), BASE + 1, 0, 4);
        assertTile(wrapped.tiles().get(40 * 28 + 39), BASE + 28, 312, 220);
        assertEquals(wrapped, CdWarpPresentation.background(assets, selected(252, new byte[640], 0), BASE));
    }

    @Test void backgroundPreservesRawPaletteFlipsPriorityAndUsedPatternVersions() {
        byte[] plane = new byte[4096]; word(plane, 0, 0xF800 | 18); word(plane, 2, 0x2001);
        var frame = CdWarpPresentation.background(assets(plane, art()), selected(0, new byte[640], 0), BASE);
        var first = frame.tiles().getFirst(); assertEquals(3, first.palette()); assertTrue(first.hFlip());
        assertTrue(first.vFlip()); assertTrue(first.priority()); assertEquals(BASE + 18, first.patternId());
        assertFalse(first.priorityShader()); assertEquals(0, first.occlusionMask()); assertFalse(first.ghost());
        assertEquals(1, frame.tiles().get(1).palette()); assertEquals(3, frame.patternVersions().size());
        assertEquals(java.util.Set.of(BASE, BASE + 1, BASE + 18), frame.patternVersions().keySet());
        assertTrue(frame.primitives().isEmpty());
    }

    @Test void backgroundReadsSelectedScrollOnlyAndDoesNotBorrowCurrentCpuOrPalette() {
        var assets = assets(new byte[4096], art()); var state = selected(8, new byte[640], 0);
        CdWarpPresentation.background(assets, state, BASE);
        verify(state).displayedVscrollBgWord(); verify(state, never()).vscrollBgWord();
        verify(state, never()).cpuSprites(); verify(state, never()).displayedSprites();
        verify(state, never()).palette(); verify(state, never()).displayedPalette();
    }

    @Test void spriteTilesUseColumnMajorSourcesAndAllFourWholePieceFlips() {
        int[] nativeIds = {10, 13, 11, 14, 12, 15};
        int[] sourceColumns = {0, 1, 0, 1, 0, 1}, sourceRows = {0, 0, 1, 1, 2, 2};
        var assets = assets(new byte[4096], art());
        for (int flip = 0; flip < 4; flip++) {
            byte[] sat = new byte[640]; int attributes = 0xE000 | ((flip & 1) != 0 ? 0x0800 : 0) | ((flip & 2) != 0 ? 0x1000 : 0);
            entry(sat, 0, 128, 6, 1, attributes | 10, 128);
            var frame = CdWarpPresentation.sprites(assets, selected(0, sat, 1), BASE); assertEquals(6, frame.tiles().size());
            for (int tile = 0; tile < nativeIds.length; tile++) {
                var actual = frame.tiles().get(tile);
                int x = ((flip & 1) == 0 ? sourceColumns[tile] : 1 - sourceColumns[tile]) * 8;
                int y = ((flip & 2) == 0 ? sourceRows[tile] : 2 - sourceRows[tile]) * 8;
                assertTile(actual, BASE + nativeIds[tile], x, y);
                assertEquals((flip & 1) != 0, actual.hFlip()); assertEquals((flip & 2) != 0, actual.vFlip());
                assertEquals(3, actual.palette()); assertTrue(actual.priority()); assertTrue(actual.priorityShader());
            }
            assertEquals(6, frame.patternVersions().size()); assertTrue(frame.primitives().isEmpty());
        }
    }

    @Test void nativeEntryOrderIsReversedExactlyOnceWithoutPriorityOrPositionSorting() {
        byte[] sat = new byte[640]; entry(sat, 0, 148, 0, 1, 19, 138); entry(sat, 1, 168, 0, 2, 0x8000 | 70, 158);
        var result = CdWarpPresentation.sprites(assets(new byte[4096], art()), selected(0, sat, 2), BASE);
        assertEquals(2, result.tiles().size()); assertTile(result.tiles().get(0), BASE + 70, 30, 40);
        assertTile(result.tiles().get(1), BASE + 19, 10, 20);
        assertTrue(result.tiles().get(0).priority()); assertFalse(result.tiles().get(1).priority());
    }

    @Test void duplicateNativeEntriesRemainDuplicateGeometryWithOneImmutablePixelVersion() {
        byte[] sat = new byte[640]; entry(sat, 0, 128, 0, 1, 70, 128); entry(sat, 1, 128, 0, 2, 70, 128);
        var result = CdWarpPresentation.sprites(assets(new byte[4096], art()), selected(0, sat, 2), BASE);
        assertEquals(2, result.tiles().size()); assertEquals(result.tiles().get(0), result.tiles().get(1));
        assertEquals(1, result.patternVersions().size());
    }

    @Test void selectedCoordinatesUseNativeLowNineAndTenBitsAndRetainOffscreenPieces() {
        byte[] sat = new byte[640]; entry(sat, 0, 0xFFE8, 0, 1, 1, 0xFFFF);
        var result = CdWarpPresentation.sprites(assets(new byte[4096], art()), selected(0, sat, 1), BASE);
        assertEquals(1, result.tiles().size()); assertTile(result.tiles().getFirst(), BASE + 1, 383, 872);
    }

    @Test void full80SpritePrefixDoesNotFollowTheFinalLinkOutsideItsSelectedTable() {
        byte[] sat = new byte[640];
        for (int index = 0; index < 80; index++) entry(sat, index, 128 + index, 0, index + 1, index % 101, 128 + index);
        var result = CdWarpPresentation.sprites(assets(new byte[4096], art()), selected(0, sat, 80), BASE);
        assertEquals(80, result.tiles().size()); assertTile(result.tiles().getFirst(), BASE + 79, 79, 79);
        assertTile(result.tiles().getLast(), BASE, 0, 0);
    }

    @Test void inactiveSatTailAndCurrentCpuDoNotSelectSourcePixels() {
        byte[] sat = new byte[640]; Arrays.fill(sat, (byte) 0xFF); entry(sat, 0, 128, 0, 1, 5, 128);
        var state = selected(0, sat, 1); var result = CdWarpPresentation.sprites(assets(new byte[4096], art()), state, BASE);
        assertEquals(1, result.tiles().size()); assertEquals(java.util.Set.of(BASE + 5), result.patternVersions().keySet());
        verify(state, never()).cpuSprites(); verify(state, never()).spriteCount(); verify(state, never()).palette();
    }

    @Test void emptySelectedSatNeedsNoArtAndMalformedCountsOrTableExtentsReject() {
        var assets = mock(CdWarpAssets.class); var empty = selected(0, new byte[640], 0);
        assertEquals(SpritePresentation.Frame.empty(), CdWarpPresentation.sprites(assets, empty, BASE)); verifyNoInteractions(assets);
        for (int count : new int[]{-1, 81, Integer.MAX_VALUE})
            assertThrows(IllegalArgumentException.class, () -> CdWarpPresentation.sprites(assets, selected(0, new byte[640], count), BASE));
        for (int size : new int[]{0, 639, 641})
            assertThrows(IllegalArgumentException.class, () -> CdWarpPresentation.sprites(assets, selected(0, new byte[size], 1), BASE));
    }

    @Test void selectedOutOfArtPatternsAndHighSizeBitsAreExplicitProjectionFailures() {
        var assets = assets(new byte[4096], art());
        for (int tile : new int[]{101, 2047}) {
            byte[] sat = new byte[640]; entry(sat, 0, 128, 0, 1, tile, 128);
            assertThrows(IllegalArgumentException.class, () -> CdWarpPresentation.sprites(assets, selected(0, sat, 1), BASE));
            byte[] plane = new byte[4096]; word(plane, 0, tile);
            assertThrows(IllegalArgumentException.class, () -> CdWarpPresentation.background(assets(plane, art()), selected(0, new byte[640], 0), BASE));
        }
        byte[] crossing = new byte[640]; entry(crossing, 0, 128, 4, 1, 100, 128);
        assertThrows(IllegalArgumentException.class, () -> CdWarpPresentation.sprites(assets, selected(0, crossing, 1), BASE));
        byte[] highSize = new byte[640]; entry(highSize, 0, 128, 0x10, 1, 1, 128);
        assertThrows(IllegalArgumentException.class, () -> CdWarpPresentation.sprites(assets, selected(0, highSize, 1), BASE));
    }

    @Test void malformedSourceExtentsNeverBecomePaddedPatternsOrBackgroundRows() {
        for (int bytes : new int[]{0, 3231, 3233}) {
            var assets = assets(new byte[4096], new byte[bytes]);
            assertThrows(IllegalArgumentException.class, () -> CdWarpPresentation.background(assets, selected(0, new byte[640], 0), BASE));
        }
        for (int bytes : new int[]{0, 4095, 4097})
            assertThrows(IllegalArgumentException.class, () -> CdWarpPresentation.background(assets(new byte[bytes], art()), selected(0, new byte[640], 0), BASE));
    }

    @Test void nibblePackingAndReturnedFramesAreFrozenValuesRatherThanLiveAssets() {
        byte[] art = new byte[3232]; art[0] = 0x12; art[31] = (byte) 0xEF;
        var assets = assets(new byte[4096], art); var state = selected(0, new byte[640], 0);
        var result = CdWarpPresentation.background(assets, state, BASE);
        assertEquals(new SpritePresentation.PatternVersion(0x21, 0, 0, 0xFE00000000000000L), result.patternVersions().get(BASE));
        Arrays.fill(art, (byte) 0x77); when(assets.art()).thenReturn(CdAssetBytes.copyOf(art));
        assertEquals(new SpritePresentation.PatternVersion(0x21, 0, 0, 0xFE00000000000000L), result.patternVersions().get(BASE));
        assertThrows(UnsupportedOperationException.class, () -> result.tiles().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.patternVersions().clear());
        assertNotEquals(result.patternVersions(), CdWarpPresentation.background(assets, state, BASE).patternVersions());
    }

    @Test void actualUsaProgrammeProjectsOnlyItsOriginalArtAndSelectedSceneProducts() throws Exception {
        String root = System.getProperty("soniccd.media.root");
        Assumptions.assumeTrue(root != null && !root.isBlank(), "Set soniccd.media.root for authenticated projection check");
        CdTimeWarpPrograms programs;
        try (var files = Files.list(Path.of(root).resolve("USA"))) {
            var cues = files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size());
            try (var owner = CdProgramAssets.open(cues.getFirst(), Optional.of(CdReleaseVariant.USA), Optional.empty())) {
                programs = CdTimeWarpPrograms.fromPrograms(owner);
            }
        }
        var assets = CdWarpAssets.fromPrograms(programs); var scene = new CdWarpScene(assets); var state = scene.initial(0);
        assertTrue(CdWarpPresentation.background(assets, state, BASE).tiles().isEmpty());
        assertTrue(CdWarpPresentation.sprites(assets, state, BASE).tiles().isEmpty());
        for (int row = 0; row < 115; row++) state = scene.step(state, 8).state();
        assertEquals(CdWarpScene.Phase.MAIN, state.phase());
        var background = CdWarpPresentation.background(assets, state, BASE); assertFalse(background.tiles().isEmpty());
        for (var tile : background.tiles()) assertTrue(tile.patternId() >= BASE && tile.patternId() <= BASE + 18);
        for (int row = 0; state.displayedSpriteCount() == 0 && row < 8; row++) state = scene.step(state, 8).state();
        assertTrue(state.displayedSpriteCount() > 0); var sprites = CdWarpPresentation.sprites(assets, state, BASE);
        assertFalse(sprites.tiles().isEmpty());
        for (var tile : sprites.tiles()) assertTrue(tile.patternId() >= BASE + 19 && tile.patternId() <= BASE + 94);
        assertEquals(background, CdWarpPresentation.background(assets, new CdWarpScene(assets).step(
                stepSceneToPrep(scene), 8).state(), BASE));
    }

    private static CdWarpScene.State stepSceneToPrep(CdWarpScene scene) {
        var state = scene.initial(0);
        for (int row = 0; row < 114; row++) state = scene.step(state, 8).state();
        return state;
    }
    private static CdWarpAssets assets(byte[] plane, byte[] pixels) {
        var assets = mock(CdWarpAssets.class); when(assets.backgroundPlane()).thenReturn(CdAssetBytes.copyOf(plane));
        when(assets.art()).thenReturn(CdAssetBytes.copyOf(pixels)); return assets;
    }
    private static CdWarpScene.State selected(int scroll, byte[] sat, int count) {
        var state = mock(CdWarpScene.State.class); when(state.displayEnabled()).thenReturn(true);
        when(state.displayedVscrollBgWord()).thenReturn(scroll); when(state.displayedSprites()).thenReturn(CdAssetBytes.copyOf(sat));
        when(state.displayedSpriteCount()).thenReturn(count); return state;
    }
    private static byte[] art() {
        byte[] art = new byte[3232];
        for (int tile = 0; tile < 101; tile++) for (int at = 0; at < 32; at++)
            art[tile * 32 + at] = (byte) ((((tile + at) & 15) << 4) | ((tile + at + 1) & 15));
        return art;
    }
    private static void entry(byte[] sat, int index, int y, int size, int link, int tile, int x) {
        int at = index * 8; word(sat, at, y); sat[at + 2] = (byte) size; sat[at + 3] = (byte) link;
        word(sat, at + 4, tile); word(sat, at + 6, x);
    }
    private static void word(byte[] bytes, int at, int value) { bytes[at] = (byte) (value >>> 8); bytes[at + 1] = (byte) value; }
    private static void assertTile(SpritePresentation.Tile tile, int pattern, int x, int y) {
        assertEquals(SpritePresentation.Layer.OBJECT, tile.layer()); assertEquals(pattern, tile.patternId());
        assertEquals((float) x, tile.x()); assertEquals((float) y, tile.y()); assertEquals(8f, tile.width()); assertEquals(8f, tile.height());
        assertEquals(0, tile.rowStart()); assertEquals(8, tile.rowEnd());
    }
}
