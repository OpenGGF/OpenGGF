package com.openggf.graphics;

import com.openggf.level.render.SpritePieceRenderer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

class TestSpriteSatMaskPostProcessor {

    @Test
    void publicMaskedPassReturnsFreshRetainableResult() {
        SpriteSatEntry marker = SpriteSatEntry.of(108, 24, 2, 2, 0x7C0, 0,
                false, false, false, false);
        SpriteSatEntry companion = SpriteSatEntry.of(100, 24, 2, 2, 0x25F, 0,
                false, false, false, false);
        SpriteSatEntry visible = SpriteSatEntry.of(100, 16, 4, 4, 0x200, 0,
                false, false, false, false);

        List<SpriteSatEntry> first = SpriteSatMaskPostProcessor.process(
                List.of(marker, companion, visible), true);
        List<SpriteSatEntry> retained = List.copyOf(first);
        SpriteSatEntry otherVisible = SpriteSatEntry.of(200, 16, 1, 1, 0x300, 0,
                false, false, false, false);
        List<SpriteSatEntry> second = SpriteSatMaskPostProcessor.process(
                List.of(marker, companion, otherVisible), true);

        assertFalse(first == second, "public results may be retained across later calls");
        assertEquals(retained, first);
        assertEquals(1, second.size());
    }

    @Test
    void reusableMaskedPassReusesEphemeralStorageAndNextCallInvalidatesContent() {
        SpriteSatEntry marker = SpriteSatEntry.of(108, 24, 2, 2, 0x7C0, 0,
                false, false, false, false);
        SpriteSatEntry companion = SpriteSatEntry.of(100, 24, 2, 2, 0x25F, 0,
                false, false, false, false);
        SpriteSatEntry firstVisible = SpriteSatEntry.of(100, 16, 4, 4, 0x200, 0,
                false, false, false, false);
        SpriteSatEntry secondVisible = SpriteSatEntry.of(200, 16, 1, 1, 0x300, 0,
                false, false, false, false);

        List<SpriteSatEntry> first = SpriteSatMaskPostProcessor.processReusable(
                List.of(marker, companion, firstVisible), true);
        List<SpriteSatEntry> second = SpriteSatMaskPostProcessor.processReusable(
                List.of(marker, companion, secondVisible), true);

        assertSame(first, second);
        assertEquals(1, first.size());
        assertEquals(0x300, first.get(0).firstPatternIndex());
    }

    @Test
    void maskPair_convertsToMaskBand_andClipsLaterPieceScanlines() {
        SpriteSatEntry earlierPiece = SpriteSatEntry.of(100, 0, 2, 2, 0x100, 0,
                false, false, false, false);
        SpriteSatEntry maskMarker = SpriteSatEntry.of(108, 24, 2, 2, 0x7C0, 0,
                false, false, false, false);
        SpriteSatEntry maskCompanion = SpriteSatEntry.of(100, 24, 2, 2, 0x25F, 0,
                false, false, false, false);
        SpriteSatEntry laterPiece = SpriteSatEntry.of(100, 16, 4, 4, 0x200, 0,
                false, false, false, false);

        List<SpriteSatEntry> processed = SpriteSatMaskPostProcessor.process(
                List.of(earlierPiece, maskMarker, maskCompanion, laterPiece), true);

        assertEquals(3, processed.size());
        assertSame(earlierPiece, processed.get(0), "entries before the mask are untouched");
        assertEquals(0x200, processed.get(1).firstPatternIndex());
        assertEquals(16, processed.get(1).visibleTopY());
        assertEquals(24, processed.get(1).visibleBottomY());
        assertEquals(0x200, processed.get(2).firstPatternIndex());
        assertEquals(40, processed.get(2).visibleTopY());
        assertEquals(48, processed.get(2).visibleBottomY());
        assertEquals(rows(16, 24, 40, 48), visibleScanlines(processed.subList(1, 3)).keySet());
    }

    @Test
    void bandStartingAndEndingMidTile_hidesOnlyThoseScanlines() {
        // One-tile mask pair at y=20..27; the later piece's tiles start at 16 and 24.
        SpriteSatEntry maskMarker = SpriteSatEntry.of(108, 20, 1, 1, 0x7C0, 0,
                false, false, false, false);
        SpriteSatEntry maskCompanion = SpriteSatEntry.of(100, 20, 1, 1, 0, 0,
                false, false, false, false);
        SpriteSatEntry laterPiece = SpriteSatEntry.of(100, 16, 1, 2, 0x200, 0,
                false, false, false, false);

        List<SpriteSatEntry> processed = SpriteSatMaskPostProcessor.process(
                List.of(maskMarker, maskCompanion, laterPiece), true);

        // Whole-tile clipping would have hidden rows 16..31 entirely.
        assertEquals(rows(16, 20, 28, 32), visibleScanlines(processed).keySet());
    }

    @Test
    void verticallyFlippedPiece_keepsSourceRowsOnTheirFlippedScanlines() {
        SpriteSatEntry maskMarker = SpriteSatEntry.of(108, 24, 1, 1, 0x7C0, 0,
                false, false, false, false);
        SpriteSatEntry maskCompanion = SpriteSatEntry.of(100, 24, 1, 1, 0, 0,
                false, false, false, false);
        SpriteSatEntry flipped = SpriteSatEntry.of(100, 16, 1, 4, 0x200, 0,
                false, true, false, false);

        Map<Integer, Integer> scanlines = visibleScanlines(SpriteSatMaskPostProcessor.process(
                List.of(maskMarker, maskCompanion, flipped), true));

        assertEquals(rows(16, 24, 32, 48), scanlines.keySet());
        // vFlip draws source row 3 at the top, row 0 at the bottom; screen row 24..31
        // (source row 2) is the one the mask removes.
        assertEquals(0x203, scanlines.get(16));
        assertEquals(0x201, scanlines.get(32));
        assertEquals(0x200, scanlines.get(47));
    }

    @Test
    void anyEarlierSpriteOnTheLineArmsTheMask_notOnlyTheMarker() {
        SpriteSatEntry earlier = SpriteSatEntry.of(10, 30, 1, 1, 0x100, 0,
                false, false, false, false);
        SpriteSatEntry maskMarker = SpriteSatEntry.of(108, 24, 1, 1, 0x7C0, 0,
                false, false, false, false);
        // X=0 companion is two tiles tall: lines 24..39.
        SpriteSatEntry maskCompanion = SpriteSatEntry.of(100, 24, 1, 2, 0, 0,
                false, false, false, false);
        SpriteSatEntry laterPiece = SpriteSatEntry.of(100, 16, 1, 4, 0x200, 0,
                false, false, false, false);

        List<SpriteSatEntry> withEarlier = SpriteSatMaskPostProcessor.process(
                List.of(earlier, maskMarker, maskCompanion, laterPiece), true);
        // Marker arms 24..31, the earlier sprite 30..37; 38..39 have no earlier sprite.
        assertSame(earlier, withEarlier.get(0));
        assertEquals(rows(16, 24, 38, 48),
                visibleScanlines(withEarlier.subList(1, withEarlier.size())).keySet());

        List<SpriteSatEntry> markerOnly = SpriteSatMaskPostProcessor.process(
                List.of(maskMarker, maskCompanion, laterPiece), true);
        assertEquals(rows(16, 24, 32, 48), visibleScanlines(markerOnly).keySet());
    }

    @Test
    void tileWordWithFlipOrPaletteBitsIsNotAMaskMarker() {
        SpriteSatEntry flippedMarker = SpriteSatEntry.of(108, 24, 1, 1, 0x7C0, 0,
                true, false, false, false);
        SpriteSatEntry paletteMarker = SpriteSatEntry.of(108, 24, 1, 1, 0x7C0, 1,
                false, false, false, false);
        SpriteSatEntry next = SpriteSatEntry.of(100, 24, 1, 1, 0, 0,
                false, false, false, false);
        SpriteSatEntry laterPiece = SpriteSatEntry.of(100, 16, 1, 4, 0x200, 0,
                false, false, false, false);

        for (SpriteSatEntry marker : List.of(flippedMarker, paletteMarker)) {
            List<SpriteSatEntry> input = List.of(marker, next, laterPiece);
            assertEquals(input, SpriteSatMaskPostProcessor.process(input, true));
        }
    }

    @Test
    void disabledMask_keepsEntriesUnchanged() {
        SpriteSatEntry maskMarker = SpriteSatEntry.of(108, 24, 2, 2, 0x7C0, 0,
                false, false, false, false);
        SpriteSatEntry maskCompanion = SpriteSatEntry.of(100, 24, 2, 2, 0x25F, 0,
                false, false, false, false);

        List<SpriteSatEntry> processed = SpriteSatMaskPostProcessor.process(
                List.of(maskMarker, maskCompanion), false);

        assertEquals(2, processed.size());
        assertEquals(maskMarker, processed.get(0));
        assertEquals(maskCompanion, processed.get(1));
    }

    @Test
    void maskBand_clipsLaterHighPriorityPieceToo() {
        SpriteSatEntry maskMarker = SpriteSatEntry.of(108, 24, 2, 2, 0x7C0, 0,
                false, false, false, false);
        SpriteSatEntry maskCompanion = SpriteSatEntry.of(100, 24, 2, 2, 0x25F, 0,
                false, false, false, false);
        SpriteSatEntry laterHighPiece = SpriteSatEntry.of(100, 16, 4, 4, 0x220, 0,
                false, false, true, false);

        List<SpriteSatEntry> processed = SpriteSatMaskPostProcessor.process(
                List.of(maskMarker, maskCompanion, laterHighPiece), true);

        assertEquals(2, processed.size());
        assertEquals(rows(16, 24, 40, 48), visibleScanlines(processed).keySet());
        assertEquals(0x220, processed.get(0).firstPatternIndex());
        assertEquals(0x220, processed.get(1).firstPatternIndex());
    }

    @Test
    void helperPair_isConsumedAsMaskControl_andNotReplayedAsVisibleArt() {
        SpriteSatEntry maskMarker = SpriteSatEntry.of(108, 24, 2, 2, 0x7C0, 0,
                false, false, false, false);
        SpriteSatEntry maskCompanion = SpriteSatEntry.of(100, 24, 2, 2, 0x25F, 0,
                false, false, false, false);
        SpriteSatEntry laterPiece = SpriteSatEntry.of(100, 16, 4, 4, 0x200, 0,
                false, false, false, false);

        List<SpriteSatEntry> processed = SpriteSatMaskPostProcessor.process(
                List.of(maskMarker, maskCompanion, laterPiece), true);

        assertFalse(processed.stream().anyMatch(entry -> entry.rawTileWordLow11() == 0x7C0));
        assertFalse(processed.stream().anyMatch(entry -> entry.rawTileWordLow11() == 0x25F));
    }

    @Test
    void preMaskFrontPieces_areReplayedAheadOfMaskedPileEvenWhenCollectedLater() {
        SpriteSatEntry maskMarker = SpriteSatEntry.of(108, 24, 2, 2, 0x7C0, 0,
                false, false, false, false);
        SpriteSatEntry maskCompanion = SpriteSatEntry.of(100, 24, 2, 2, 0x25F, 0,
                false, false, false, false);
        SpriteSatEntry frontGlass = SpriteSatEntry.of(100, 16, 4, 4, 0x180, 0,
                false, false, true, false).withMaskReplayRole(SpriteMaskReplayRole.PRE_MASK_FRONT);
        SpriteSatEntry pile = SpriteSatEntry.of(100, 16, 4, 4, 0x200, 0,
                false, false, false, false);

        List<SpriteSatEntry> processed = SpriteSatMaskPostProcessor.process(
                List.of(maskMarker, maskCompanion, frontGlass, pile), true);

        assertEquals(3, processed.size());
        assertEquals(0x180, processed.get(0).firstPatternIndex());
        assertEquals(0x200, processed.get(1).firstPatternIndex());
        assertEquals(24, processed.get(1).visibleBottomY());
        assertEquals(0x200, processed.get(2).firstPatternIndex());
        assertEquals(40, processed.get(2).visibleTopY());
    }

    /** Screen scanline to the source pattern drawn on it (first column only). */
    static Map<Integer, Integer> visibleScanlines(List<SpriteSatEntry> entries) {
        Map<Integer, Integer> lines = new TreeMap<>();
        for (SpriteSatEntry entry : entries) {
            SpritePieceRenderer.renderPreparedPiece(entry.toPreparedPiece(),
                    (patternIndex, hFlip, vFlip, paletteIndex, drawX, drawY) -> {
                        if (drawX != entry.x()) {
                            return;
                        }
                        for (int row = entry.visibleTileRowStart(drawY); row < entry.visibleTileRowEnd(drawY); row++) {
                            lines.put(drawY + row, patternIndex);
                        }
                    });
        }
        return lines;
    }

    /** Union of half-open scanline ranges given as start/end pairs. */
    static TreeSet<Integer> rows(int... ranges) {
        TreeSet<Integer> rows = new TreeSet<>();
        for (int i = 0; i < ranges.length; i += 2) {
            for (int line = ranges[i]; line < ranges[i + 1]; line++) {
                rows.add(line);
            }
        }
        return rows;
    }
}
