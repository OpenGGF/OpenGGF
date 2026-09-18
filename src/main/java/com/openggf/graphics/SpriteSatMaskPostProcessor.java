package com.openggf.graphics;

import java.util.ArrayList;
import java.util.List;

/**
 * Applies the S3K sprite-mask post-pass to SAT-like mapping-piece entries before
 * they are expanded into 8x8 tiles.
 *
 * <p>ROM (sonic3k.asm, end of {@code Render_Sprites}, loc_1AE34): while
 * {@code Spritemask_flag} is set, every built SAT entry whose whole tile word is
 * {@code $07C0} gets X=1 and the entry after it gets X=0. The VDP then applies
 * sprite masking; Genesis Plus GX {@code render_obj_m5} (vdp_render.c) walks each
 * scanline's sprite list in SAT order: any sprite with xpos != 0 arms masking, and
 * a later sprite with xpos == 0 hides itself and every remaining sprite on that
 * line, across the whole line width. The arming sprite need not be the $7C0
 * marker; any earlier SAT sprite on the line qualifies.</p>
 *
 * <p>The software replay models that per scanline: for each marker pair the
 * masked lines are the X=0 companion's lines that some earlier entry also covers,
 * and every later entry loses exactly those scanlines (pixel precise, carried as a
 * visible scanline window per entry). The pair itself is not drawn. Not modelled:
 * the 20-sprites/line and 320-pixel/line limits, and GPGX's carry of a previous
 * line's pixel overflow, which also arms masking on the next line.</p>
 */
public final class SpriteSatMaskPostProcessor {

    /** ROM {@code move.w #$7C0,d0 / cmp.w (a0),d0}: the whole SAT tile word. */
    private static final int MASK_TILE_WORD = 0x7C0;
    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

    private SpriteSatMaskPostProcessor() {
    }

    /**
     * Returns a retainable fresh result when masking is enabled. When masking is
     * off (or the input is empty), the input list itself is returned for legacy
     * identity compatibility.
     */
    public static List<SpriteSatEntry> process(List<SpriteSatEntry> entries, boolean spriteMaskEnabled) {
        if (!spriteMaskEnabled || entries == null || entries.isEmpty()) {
            return entries == null ? List.of() : entries;
        }
        return new ArrayList<>(processReusable(entries, true));
    }

    /**
     * Allocation-minimized synchronous render path. Masked results are backed by
     * thread-owned scratch and are invalidated by the next masked call on the
     * same thread; callers must consume them before calling this method again.
     */
    static List<SpriteSatEntry> processReusable(List<SpriteSatEntry> entries, boolean spriteMaskEnabled) {
        if (!spriteMaskEnabled || entries == null || entries.isEmpty()) {
            return entries == null ? List.of() : entries;
        }

        Scratch scratch = SCRATCH.get();
        scratch.reset();
        int preMaskInsertIndex = -1;

        for (int i = 0; i < entries.size(); i++) {
            SpriteSatEntry entry = entries.get(i);
            if (isMaskMarker(entries, i)) {
                SpriteSatEntry companion = entries.get(i + 1);
                if (addArmedBands(entries, i + 1, companion, scratch) && preMaskInsertIndex < 0) {
                    preMaskInsertIndex = scratch.processed.size();
                }
                scratch.companionIndexes.set(i + 1);
                i++; // The helper pair becomes the mask; neither piece replays visibly.
                continue;
            }

            if (entry.maskReplayRole() == SpriteMaskReplayRole.PRE_MASK_FRONT && preMaskInsertIndex >= 0) {
                scratch.processed.add(preMaskInsertIndex, entry);
                preMaskInsertIndex++;
                continue;
            }
            clipAgainstBands(entry, scratch);
        }

        return scratch.processed;
    }

    /**
     * The ROM compares the whole SAT word, so a $7C0 tile carrying flip, palette or
     * mapping priority bits is not a marker. (The object's art_tile priority is an
     * engine-side flag here; every current mask producer uses art_tile 0.)
     */
    private static boolean isMaskMarker(List<SpriteSatEntry> entries, int index) {
        if (index + 1 >= entries.size()) {
            return false;
        }
        SpriteSatEntry entry = entries.get(index);
        return entry.tileWordLow11() == MASK_TILE_WORD
                && !entry.hFlip()
                && !entry.vFlip()
                && !entry.piecePriority()
                && (entry.paletteIndex() & 0x3) == 0;
    }

    /**
     * Adds the companion's scanlines on which an earlier SAT entry with X != 0 is
     * present (GPGX {@code spr_ovr}). Earlier X=0 companions do not arm.
     */
    private static boolean addArmedBands(List<SpriteSatEntry> entries, int companionIndex,
            SpriteSatEntry companion, Scratch scratch) {
        int top = companion.y();
        int bottom = companion.endYExclusive();
        boolean added = false;
        int runStart = Integer.MIN_VALUE;
        for (int line = top; line <= bottom; line++) {
            boolean armed = line < bottom && isArmed(entries, companionIndex, line, scratch);
            if (armed && runStart == Integer.MIN_VALUE) {
                runStart = line;
            } else if (!armed && runStart != Integer.MIN_VALUE) {
                scratch.addBand(runStart, line);
                runStart = Integer.MIN_VALUE;
                added = true;
            }
        }
        return added;
    }

    private static boolean isArmed(List<SpriteSatEntry> entries, int companionIndex, int line, Scratch scratch) {
        for (int j = 0; j < companionIndex; j++) {
            if (scratch.companionIndexes.get(j)) {
                continue;
            }
            SpriteSatEntry earlier = entries.get(j);
            if (line >= earlier.y() && line < earlier.endYExclusive()) {
                return true;
            }
        }
        return false;
    }

    private static void clipAgainstBands(SpriteSatEntry entry, Scratch scratch) {
        int top = Math.max(entry.visibleTopY(), entry.y());
        int bottom = Math.min(entry.visibleBottomY(), entry.endYExclusive());
        boolean touched = false;
        scratch.rangeA[0] = top;
        scratch.rangeA[1] = bottom;
        int[] remaining = scratch.rangeA;
        int remainingCount = 1;

        for (int bandIndex = 0; bandIndex < scratch.bandCount && remainingCount > 0; bandIndex++) {
            int bandStart = scratch.bandStarts[bandIndex];
            int bandEnd = scratch.bandEnds[bandIndex];
            if (bandEnd <= top || bandStart >= bottom) {
                continue;
            }
            touched = true;
            boolean usingA = remaining == scratch.rangeA;
            scratch.ensureRangeCapacity((remainingCount + 1) * 2);
            remaining = usingA ? scratch.rangeA : scratch.rangeB;
            int[] next = usingA ? scratch.rangeB : scratch.rangeA;
            int nextCount = 0;
            for (int i = 0; i < remainingCount; i++) {
                int start = remaining[i * 2];
                int end = remaining[i * 2 + 1];
                if (bandEnd <= start || bandStart >= end) {
                    next[nextCount * 2] = start;
                    next[nextCount * 2 + 1] = end;
                    nextCount++;
                    continue;
                }
                if (bandStart > start) {
                    next[nextCount * 2] = start;
                    next[nextCount * 2 + 1] = bandStart;
                    nextCount++;
                }
                if (bandEnd < end) {
                    next[nextCount * 2] = bandEnd;
                    next[nextCount * 2 + 1] = end;
                    nextCount++;
                }
            }
            remaining = next;
            remainingCount = nextCount;
        }

        if (!touched) {
            scratch.processed.add(entry);
            return;
        }
        for (int i = 0; i < remainingCount; i++) {
            int start = remaining[i * 2];
            int end = remaining[i * 2 + 1];
            if (end > start) {
                scratch.processed.add(entry.withVisibleScanlines(start, end));
            }
        }
    }

    private static final class Scratch {
        private final ArrayList<SpriteSatEntry> processed = new ArrayList<>();
        private final java.util.BitSet companionIndexes = new java.util.BitSet();
        private int[] bandStarts = new int[8];
        private int[] bandEnds = new int[8];
        private int bandCount;
        private int[] rangeA = new int[8];
        private int[] rangeB = new int[8];

        private void reset() {
            processed.clear();
            companionIndexes.clear();
            bandCount = 0;
        }

        private void addBand(int start, int end) {
            if (bandCount == bandStarts.length) {
                int size = bandStarts.length * 2;
                bandStarts = java.util.Arrays.copyOf(bandStarts, size);
                bandEnds = java.util.Arrays.copyOf(bandEnds, size);
            }
            bandStarts[bandCount] = start;
            bandEnds[bandCount] = end;
            bandCount++;
        }

        private void ensureRangeCapacity(int required) {
            if (rangeA.length >= required) return;
            int size = Math.max(required, rangeA.length * 2);
            rangeA = java.util.Arrays.copyOf(rangeA, size);
            rangeB = java.util.Arrays.copyOf(rangeB, size);
        }
    }
}
