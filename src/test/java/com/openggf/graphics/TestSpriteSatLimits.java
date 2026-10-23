package com.openggf.graphics;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Independent boundary examples from Mode-5 SAT evaluation, not gameplay fixtures. */
class TestSpriteSatLimits {
    private static SpriteSatEntry piece(int x, int y, int width, int height, int pattern) {
        return SpriteSatEntry.of(x, y, width, height, pattern, 0, false, false, false, false);
    }

    @Test void countIsPerLineAndIndependentOfMaskFlagOrTilePriority() {
        for (boolean mask : new boolean[]{false, true}) {
            var entries = new ArrayList<SpriteSatEntry>();
            for (int i = 0; i < 20; i++) entries.add(piece(0, 16, 1, 1, i));
            assertEquals(20, SpriteSatMaskPostProcessor.process(entries, mask).size());
            entries.add(SpriteSatEntry.of(80, 20, 1, 1, 100, 0, false, false, true, true));
            var result = SpriteSatMaskPostProcessor.process(entries, mask);
            var last = result.stream().filter(e -> e.piece().firstPatternIndex() == 100).findFirst().orElseThrow();
            assertEquals(24, last.visibleTopY());
            assertEquals(28, last.visibleBottomY());
            assertTrue(last.piece().effectiveHighPriority(), "tile priority must not rescue the overflowing lines");
        }
    }

    @Test void pixelBudgetCountsOverlappingTransparentAndOffscreenWidths() {
        var entries = new ArrayList<SpriteSatEntry>();
        for (int i = 0; i < 9; i++) entries.add(piece(i % 2 == 0 ? -80 : 400, 20, 4, 1, i * 4));
        entries.add(piece(40, 20, 3, 1, 100)); // 288 + 24 = 312
        entries.add(piece(100, 20, 4, 1, 200)); // only the first 8 pixels fit
        entries.add(piece(200, 20, 1, 1, 300));
        var result = SpriteSatMaskPostProcessor.process(entries, false);
        var finalPiece = result.stream().filter(e -> e.piece().firstPatternIndex() == 200).findFirst().orElseThrow();
        assertEquals(0, finalPiece.piece().startColTile()); assertEquals(1, finalPiece.piece().colCountTiles());
        assertFalse(result.stream().anyMatch(e -> e.piece().firstPatternIndex() == 300));
    }

    @Test void overflowKeepsScreenLeftColumnsUnderBothFlipsAndPreexistingSourceClip() {
        for (boolean h : new boolean[]{false, true}) for (boolean v : new boolean[]{false, true}) {
            var entries = new ArrayList<SpriteSatEntry>();
            for (int i = 0; i < 9; i++) entries.add(piece(0, 20, 4, 1, 0));
            entries.add(piece(0, 20, 3, 1, 0)); // 312 pixels, 10 sprites
            entries.add(SpriteSatEntry.of(100, 20, 4, 2, 100, 0, h, v, false, false));
            var result = SpriteSatMaskPostProcessor.process(entries, false).stream()
                    .filter(e -> e.piece().firstPatternIndex() == 100).toList();
            assertEquals(2, result.size(), "overflow clips only the overlapping scanlines");
            var top = result.getFirst();
            assertEquals(h ? 3 : 0, top.piece().startColTile()); assertEquals(1, top.piece().colCountTiles());
            var draws = new ArrayList<Integer>();
            top.piece().forEachTile( (p, hf, vf, pal, x, y) -> {
                assertEquals(100, x); draws.add(p);
            });
            assertTrue(draws.contains(h ? 106 : 100));
            assertEquals(28, top.visibleBottomY());
            assertEquals(4, result.getLast().piece().colCountTiles());
        }
        var clipped = piece(100, 20, 4, 1, 100).clipRect(1, 3, 0, 1);
        var entries = new ArrayList<SpriteSatEntry>();
        for (int i = 0; i < 9; i++) entries.add(piece(0, 20, 4, 1, 0));
        entries.add(piece(0, 20, 3, 1, 0)); entries.add(clipped);
        assertFalse(SpriteSatMaskPostProcessor.process(entries, false).stream().anyMatch(e -> e.piece().firstPatternIndex() == 100));
    }

    @Test void exactlyFullPixelBudgetArmsFirstXZeroOnNextLineAndEmptyLineClearsIt() {
        var entries = overflowPrelude(10, 4); // ten 32px pieces cover lines 10..17
        entries.add(piece(-128, 18, 1, 1, 100)); // first X=0, with no same-line arming sprite
        entries.add(piece(20, 18, 1, 1, 200));
        entries.add(piece(-128, 27, 1, 1, 300)); // an empty line intervenes
        entries.add(piece(20, 27, 1, 1, 400));
        var result = SpriteSatMaskPostProcessor.process(entries, false);
        var hidden = result.stream().filter(e -> e.piece().firstPatternIndex() == 200).findFirst().orElseThrow();
        assertEquals(19, hidden.visibleTopY(), "only line 18 inherits the previous line's pixel overflow");
        assertSame(entries.getLast(), result.getLast(), "empty line clears carry");
    }

    @Test void countOverflowAloneDoesNotArmNextLineMask() {
        var entries = overflowPrelude(10, 1);
        for (int i = 10; i < 21; i++) entries.add(piece(0, 10, 1, 1, i));
        entries.add(piece(-128, 18, 1, 1, 100));
        entries.add(piece(20, 18, 1, 1, 200));
        var result = SpriteSatMaskPostProcessor.process(entries, false);
        assertSame(entries.getLast(), result.getLast());
    }

    @Test void maskedPiecesStillConsumeWidthAndArmCarry() {
        var entries = new ArrayList<SpriteSatEntry>();
        entries.add(piece(0, 10, 1, 1, 1)); // 8px arms current line
        entries.add(piece(-128, 10, 1, 1, 2)); // 8px masks the rest
        for (int i = 0; i < 9; i++) entries.add(piece(0, 10, 4, 1, 10 + i)); // 288px
        entries.add(piece(0, 10, 2, 1, 30)); // 16px reaches 320 while masked
        entries.add(piece(-128, 18, 1, 1, 100));
        entries.add(piece(20, 18, 1, 1, 200));
        var result = SpriteSatMaskPostProcessor.process(entries, false);
        assertEquals(2, result.size());
        assertEquals(1, result.getFirst().piece().firstPatternIndex());
        assertEquals(19, result.getLast().visibleTopY());
    }

    @Test void helperBehindCountLimitCannotMaskLaterLines() {
        var entries = overflowPrelude(10, 1);
        for (int i = 10; i < 20; i++) entries.add(piece(0, 10, 1, 1, i));
        entries.add(piece(0, 10, 1, 1, 0x7C0));
        entries.add(piece(0, 10, 1, 2, 0));
        entries.add(piece(20, 18, 1, 1, 200));
        // On lines 18..25 the zero-X companion has no arming piece and no carry.
        assertSame(entries.getLast(), SpriteSatMaskPostProcessor.process(entries, true).getLast());
    }

    @Test void markerAndCompanionConsumeCountAndPixels() {
        var entries = new ArrayList<SpriteSatEntry>();
        for (int i = 0; i < 18; i++) entries.add(piece(0, 10, 1, 1, i));
        entries.add(piece(0, 10, 1, 1, 0x7C0));
        entries.add(piece(0, 10, 1, 1, 0));
        entries.add(piece(40, 10, 1, 1, 200));
        assertEquals(18, SpriteSatMaskPostProcessor.process(entries, true).size());
        assertEquals(20, SpriteSatMaskPostProcessor.process(entries, false).size());
    }

    @Test void h32UsesSixteenPiecesAnd256PixelsRegardlessOfOuterViewport() {
        var raster = SpriteSatMaskPostProcessor.Raster.h32(32, 0, 224);
        var entries = new ArrayList<SpriteSatEntry>();
        for (int i = 0; i < 17; i++) entries.add(piece(40, 10, 1, 1, i));
        assertEquals(16, SpriteSatMaskPostProcessor.process(entries, false, raster).size());
        entries.clear();
        for (int i = 0; i < 10; i++) entries.add(piece(40, 10, 3, 1, i * 3));
        entries.add(piece(80, 10, 3, 1, 100));
        var last = SpriteSatMaskPostProcessor.process(entries, false, raster).getLast();
        assertEquals(100, last.piece().firstPatternIndex()); assertEquals(2, last.piece().colCountTiles());
    }

    @Test void nativeTableWalkStopsAt80Or64EvenWhenEarlierEntriesAreOnOtherLines() {
        for (int width : new int[]{256, 320}) {
            var entries = new ArrayList<SpriteSatEntry>();
            for (int i = 0; i < width / 4; i++) entries.add(piece(0, 100, 1, 1, 0));
            entries.add(piece(0, 10, 1, 1, 100));
            assertFalse(SpriteSatMaskPostProcessor.process(entries, false,
                    new SpriteSatMaskPostProcessor.Raster(0, 0, width, 224)).stream().anyMatch(e -> e.piece().firstPatternIndex() == 100));
        }
    }

    @Test void extendedRasterKeepsNativeCapacityAndMaskCarryWithoutNineBitWrapping() {
        var raster = SpriteSatMaskPostProcessor.Raster.extended(0, 0, 800, 224);
        var entries = new ArrayList<SpriteSatEntry>();
        for (int i = 0; i < 21; i++) entries.add(piece(0, 10, 1, 1, i));
        assertEquals(20, SpriteSatMaskPostProcessor.process(entries, false, raster).size());
        entries.clear();
        entries.add(piece(384, 10, 1, 1, 1)); // raw 512 must NOT turn into an X=0 mask
        entries.add(piece(600, 10, 1, 1, 2));
        assertEquals(2, SpriteSatMaskPostProcessor.process(entries, false, raster).size());
        entries.clear();
        for (int i = 0; i < 10; i++) entries.add(piece(0, 10, 4, 1, i));
        entries.add(piece(-128, 18, 1, 1, 100)); entries.add(piece(600, 18, 1, 1, 200));
        assertEquals(19, SpriteSatMaskPostProcessor.process(entries, false, raster).getLast().visibleTopY());
    }

    @Test void frameOriginAndPriorCallsCannotChangeAdmissionOrCarry() {
        var entries = overflowPrelude(10, 4);
        var baseline = SpriteSatMaskPostProcessor.process(entries, false);
        SpriteSatMaskPostProcessor.process(entries, true);
        assertEquals(baseline, SpriteSatMaskPostProcessor.process(entries, false));
        var origin = SpriteSatMaskPostProcessor.Raster.h40(1000, 2000, 224);
        var translated = List.of(piece(1020, 2020, 1, 1, 1), piece(872, 2020, 1, 1, 2), piece(1040, 2020, 1, 1, 3));
        var result = SpriteSatMaskPostProcessor.process(translated, false, origin);
        assertEquals(List.of(translated.getFirst()), result);
        assertEquals(baseline, SpriteSatMaskPostProcessor.process(entries, false));
    }

    @Test void clippedCompositeFragmentsRetainTheirOwnPresentationAndIndexedOperation() {
        var subject = new SpritePresentation.Subject("fragment", SpritePresentation.Part.BODY, false, 100);
        var ghost = new SpriteSatEntry.Ghost(0.375f, true);
        var fragment = SpriteSatEntry.of(100, 20, 4, 2, 100, 3, true, true, false, false)
                .withGhost(ghost).withTileOcclusionPaletteMask(5)
                .withPixelOperation(SpriteSatEntry.PixelOperation.SHADOW_15)
                .withPresentationSubject(subject).withVisibleScanlines(22, 34)
                .clipRows(0, 2).withMaskReplayRole(SpriteMaskReplayRole.PRE_MASK_FRONT);
        var fragments = new ArrayList<>(List.of(fragment));
        var group = piece(100, 20, 4, 2, 900).withReplayFragments(fragments);
        fragments.clear(); // Admission retains its own immutable art topology.
        var entries = overflowPrelude(20, 4);
        entries.removeLast(); // 288 pixels.
        entries.add(piece(0, 20, 3, 1, 0)); // 312 pixels before the native group.
        entries.add(group);
        var replay = SpriteSatMaskPostProcessor.process(entries, false).stream()
                .filter(entry -> entry.presentationSubject() == subject).toList();
        assertEquals(2, replay.size());
        assertEquals(List.of(22, 28), replay.stream().map(SpriteSatEntry::visibleTopY).toList());
        assertEquals(List.of(28, 34), replay.stream().map(SpriteSatEntry::visibleBottomY).toList());
        assertEquals(3, replay.getFirst().piece().startColTile(), "HFlip keeps the screen-left source column");
        assertEquals(1, replay.getFirst().piece().colCountTiles());
        assertEquals(4, replay.getLast().piece().colCountTiles());
        for (var entry : replay) {
            assertSame(subject, entry.presentationSubject());
            assertSame(ghost, entry.ghost());
            assertEquals(5, entry.tileOcclusionPaletteMask());
            assertEquals(SpriteSatEntry.PixelOperation.SHADOW_15, entry.pixelOperation());
            assertEquals(SpriteMaskReplayRole.PRE_MASK_FRONT, entry.piece().maskReplayRole());
            assertTrue(entry.piece().hFlip()); assertTrue(entry.piece().vFlip());
            assertEquals(3, entry.piece().paletteIndex());
        }
    }

    @Test void emptyFragmentsConsumeNativeAdmissionWhileNullFragmentsReplayThePiece() {
        var nativePiece = piece(20, 20, 1, 1, 100);
        assertNull(nativePiece.replayFragments());
        assertEquals(List.of(nativePiece), SpriteSatMaskPostProcessor.process(List.of(nativePiece), false));
        var empty = nativePiece.withReplayFragments(List.of());
        assertTrue(empty.replayFragments().isEmpty());
        var entries = new ArrayList<SpriteSatEntry>();
        for (int i = 0; i < 20; i++) entries.add(empty);
        entries.add(piece(40, 20, 1, 1, 200));
        assertTrue(SpriteSatMaskPostProcessor.process(entries, false).isEmpty(),
                "empty art still occupies the first twenty native line slots");
        assertEquals(List.of(nativePiece), SpriteSatMaskPostProcessor.process(
                List.of(empty.withReplayFragments(null)), false));
        assertThrows(UnsupportedOperationException.class, () -> empty.replayFragments().add(nativePiece));
    }

    private static ArrayList<SpriteSatEntry> overflowPrelude(int y, int width) {
        var entries = new ArrayList<SpriteSatEntry>();
        for (int i = 0; i < 10; i++) entries.add(piece(0, y, width, 1, i * width));
        return entries;
    }
}
