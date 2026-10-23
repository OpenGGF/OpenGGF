package com.openggf.graphics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Mode-5 SAT evaluation before mapping pieces expand into tiles. Admission follows
 * SAT order, independent of the sprite/background priority bit. The optional S3K
 * Render_Sprites rewrite changes whole-word $07C0 entries to X=1 and their successor
 * to X=0; hardware budgets and ordinary X=0 masking apply even without that rewrite.
 *
 * <p>Hardware rules: Genesis Plus GX, vdp_render.c, parse_satb_m5/render_obj_m5,
 * revision 49c584764893b0505ac7f768a754f97330fa4392. Offscreen, transparent and masked
 * entries consume declared width. Reaching (not only exceeding) the pixel budget
 * arms X=0 masking on the next line; count overflow alone does not. An overflowing
 * piece retains its leftmost screen columns, including when horizontally flipped.
 * Evaluation is frame-local, with no state retained across render/rewind calls.</p>
 *
 * <p>Gameplay ghosts are presentation-only: native entries retain their table, line
 * and pixel budgets when ghosts are interleaved. Ghosts are clipped by the mask at
 * their submission position, but never arm one or become a native marker/companion.</p>
 */
public final class SpriteSatMaskPostProcessor {
    private static final int MASK_TILE_WORD = 0x7C0;
    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

    private SpriteSatMaskPostProcessor() { }

    /** Logical raster, not the physical window size or a trace's native camera width. */
    public record Raster(int x, int y, int width, int height, int hardwareWidth) {
        public Raster(int x, int y, int width, int height) {
            this(x, y, width, height, width == 256 ? 256 : 320);
        }
        public Raster {
            if ((hardwareWidth != 256 && hardwareWidth != 320)
                    || width < hardwareWidth || (width & 7) != 0 || height <= 0) {
                throw new IllegalArgumentException("SAT raster needs a tile-aligned width >= 256 and positive height");
            }
        }

        public static Raster h40(int x, int y, int height) { return new Raster(x, y, 320, height); }
        public static Raster h32(int x, int y, int height) { return new Raster(x, y, 256, height); }

        /**
         * Wider gameplay is a presentation extension with fixed H40 hardware capacity.
         * Side pieces compete for the same 20/320/80 budget; masks span the full logical
         * raster, whose coordinates do not wrap at nine bits. This is not a native-wide
         * parity claim. H32 consumers pass their fixed inner 256px raster.
         */
        public static Raster extended(int x, int y, int width, int height) {
            return new Raster(x, y, width, height);
        }

        int lineSprites() { return hardwareWidth / 16; }
        int tableSprites() { return hardwareWidth / 4; }
    }

    /** Fresh, retainable native H40 result. The flag controls only the ROM marker rewrite. */
    public static List<SpriteSatEntry> process(List<SpriteSatEntry> entries, boolean spriteMaskEnabled) {
        return process(entries, spriteMaskEnabled, Raster.h40(0, 0, 224));
    }

    public static List<SpriteSatEntry> process(List<SpriteSatEntry> entries, boolean spriteMaskEnabled, Raster raster) {
        return new ArrayList<>(processReusable(entries, spriteMaskEnabled, raster));
    }

    /** Thread-owned synchronous result, invalidated by the next call on this thread. */
    static List<SpriteSatEntry> processReusable(List<SpriteSatEntry> entries, boolean spriteMaskEnabled) {
        return processReusable(entries, spriteMaskEnabled, Raster.h40(0, 0, 224));
    }

    static List<SpriteSatEntry> processReusable(List<SpriteSatEntry> entries, boolean spriteMaskEnabled, Raster raster) {
        Scratch scratch = SCRATCH.get();
        scratch.processed.clear();
        if (entries == null || entries.isEmpty()) return scratch.processed;
        int size = entries.size();
        scratch.reset(size, raster.height());
        int firstMarker = -1;
        for (int i = 0; i < size; i++) {
            int companion = spriteMaskEnabled ? maskCompanion(entries, i) : -1;
            if (companion >= 0) {
                scratch.control[i] = true;
                scratch.control[companion] = true;
                scratch.xOverride[i] = 1;
                scratch.xOverride[companion] = 0;
                if (firstMarker < 0) firstMarker = i;
            }
        }
        // Existing front-shell producers describe native entries preceding the mask,
        // although the object can submit them later. Place them before evaluation too:
        // they must compete for the same budgets as every other entry.
        int ordered = 0;
        for (int i = 0; i < size; i++) {
            if (i == firstMarker) {
                for (int j = i + 1; j < size; j++) {
                    if (entries.get(j).piece().maskReplayRole() == SpriteMaskReplayRole.PRE_MASK_FRONT && !scratch.control[j]) {
                        scratch.order[ordered++] = j;
                    }
                }
            }
            if (firstMarker >= 0 && i > firstMarker
                    && entries.get(i).piece().maskReplayRole() == SpriteMaskReplayRole.PRE_MASK_FRONT && !scratch.control[i]) continue;
            scratch.order[ordered++] = i;
        }
        int total = 0;
        int nativeEntries = 0;
        for (int slot = 0; slot < ordered; slot++) {
            int index = scratch.order[slot];
            if (entries.get(index).isGhost() || nativeEntries++ < raster.tableSprites()) {
                scratch.order[total++] = index;
            }
        }
        boolean carry = false;
        for (int row = 0; row < raster.height(); row++) {
            int line = raster.y() + row;
            boolean armed = carry;
            boolean masked = false;
            carry = false;
            int count = 0;
            int pixels = 0;
            boolean nativeExhausted = false;
            for (int slot = 0; slot < total; slot++) {
                int index = scratch.order[slot];
                SpriteSatEntry entry = entries.get(index);
                if (line < entry.piece().y() || line >= entry.piece().endYExclusive()) continue;
                if (entry.isGhost()) {
                    if (!masked && entry.piece().endXExclusive() > raster.x() && entry.piece().x() < raster.x() + raster.width()) {
                        scratch.columns[index * raster.height() + row] = entry.piece().widthTiles();
                    }
                    continue;
                }
                if (nativeExhausted) continue;
                if (count++ == raster.lineSprites()) {
                    nativeExhausted = true;
                    continue;
                }
                int rawX = scratch.xOverride[index];
                if (rawX < 0) {
                    int biasedX = entry.piece().x() - raster.x() + 128;
                    // Expanded raster coordinates exceed the native nine-bit SAT field.
                    rawX = raster.width() <= 320 ? biasedX & 0x1FF : biasedX;
                }
                if (rawX != 0) armed = true;
                else if (armed) masked = true;
                int columns = Math.min(entry.piece().widthTiles(), Math.max(0, (raster.hardwareWidth() - pixels) / 8));
                pixels += entry.piece().widthTiles() * 8;
                if (!masked && !scratch.control[index]
                        && entry.piece().endXExclusive() > raster.x() && entry.piece().x() < raster.x() + raster.width()) {
                    scratch.columns[index * raster.height() + row] = columns;
                }
                if (pixels >= raster.hardwareWidth()) {
                    carry = true;
                    nativeExhausted = true;
                }
            }
        }
        for (int slot = 0; slot < total; slot++) {
            int index = scratch.order[slot];
            if (!scratch.control[index]) appendVisible(entries.get(index), index, raster, scratch);
        }
        return scratch.processed;
    }

    private static int maskCompanion(List<SpriteSatEntry> entries, int index) {
        SpriteSatEntry entry = entries.get(index);
        if (entry.isGhost() || entry.piece().rawTileWordLow11() != MASK_TILE_WORD || entry.piece().hFlip() || entry.piece().vFlip()
                || entry.piece().piecePriority() || (entry.piece().paletteIndex() & 3) != 0) return -1;
        // Ghosts do not occupy the native SAT link that selects the companion.
        for (int next = index + 1; next < entries.size(); next++) {
            if (!entries.get(next).isGhost()) return next;
        }
        return -1;
    }

    private static void appendVisible(SpriteSatEntry entry, int index, Raster raster, Scratch scratch) {
        int top = Math.max(Math.max(entry.piece().y(), entry.visibleTopY()), raster.y());
        int bottom = Math.min(Math.min(entry.piece().endYExclusive(), entry.visibleBottomY()), raster.y() + raster.height());
        int start = top;
        while (start < bottom) {
            int columns = scratch.columns[index * raster.height() + start - raster.y()];
            int end = start + 1;
            while (end < bottom && scratch.columns[index * raster.height() + end - raster.y()] == columns) end++;
            if (columns > 0) {
                int left = entry.piece().hFlip() ? entry.piece().widthTiles() - columns : 0;
                int right = entry.piece().hFlip() ? entry.piece().widthTiles() : columns;
                left = Math.max(left, entry.piece().startColTile());
                right = Math.min(right, entry.piece().startColTile() + entry.piece().colCountTiles());
                if (right > left) {
                    SpriteSatEntry clipped = left == entry.piece().startColTile() && right - left == entry.piece().colCountTiles()
                            ? entry : entry.clipRect(left, right - left, entry.piece().startRowTile(), entry.piece().rowCountTiles());
                    if (start != Math.max(entry.piece().y(), entry.visibleTopY())
                            || end != Math.min(entry.piece().endYExclusive(), entry.visibleBottomY())) {
                        clipped = clipped.withVisibleScanlines(start, end);
                    }
                    appendReplay(clipped, scratch.processed);
                }
            }
            start = end;
        }
    }

    private static void appendReplay(SpriteSatEntry admitted, List<SpriteSatEntry> output) {
        if (admitted.replayFragments() == null) {
            output.add(admitted);
            return;
        }
        int left = admitted.piece().x() + (admitted.piece().hFlip()
                ? admitted.piece().widthTiles() - admitted.piece().startColTile() - admitted.piece().colCountTiles()
                : admitted.piece().startColTile()) * 8;
        int right = left + admitted.piece().colCountTiles() * 8;
        for (SpriteSatEntry fragment : admitted.replayFragments()) {
            int firstScreenCol = Math.max(0, Math.floorDiv(left - fragment.piece().x() + 7, 8));
            int endScreenCol = Math.min(fragment.piece().widthTiles(), Math.floorDiv(right - fragment.piece().x(), 8));
            int sourceLeft = fragment.piece().hFlip() ? fragment.piece().widthTiles() - endScreenCol : firstScreenCol;
            int sourceRight = fragment.piece().hFlip() ? fragment.piece().widthTiles() - firstScreenCol : endScreenCol;
            sourceLeft = Math.max(sourceLeft, fragment.piece().startColTile());
            sourceRight = Math.min(sourceRight, fragment.piece().startColTile() + fragment.piece().colCountTiles());
            int top = Math.max(Math.max(admitted.visibleTopY(), admitted.piece().y()),
                    Math.max(fragment.piece().y(), fragment.visibleTopY()));
            int bottom = Math.min(Math.min(admitted.visibleBottomY(), admitted.piece().endYExclusive()),
                    Math.min(fragment.piece().endYExclusive(), fragment.visibleBottomY()));
            if (sourceRight > sourceLeft && bottom > top) {
                output.add(fragment.clipRect(sourceLeft, sourceRight - sourceLeft,
                        fragment.piece().startRowTile(), fragment.piece().rowCountTiles()).withVisibleScanlines(top, bottom));
            }
        }
    }

    private static final class Scratch {
        final ArrayList<SpriteSatEntry> processed = new ArrayList<>();
        int[] columns = new int[0];
        int[] xOverride = new int[0];
        int[] order = new int[0];
        boolean[] control = new boolean[0];

        void reset(int size, int height) {
            int cells = Math.multiplyExact(size, height);
            if (columns.length < cells) columns = new int[cells];
            else Arrays.fill(columns, 0, cells, 0);
            if (order.length < size) {
                order = new int[size];
                xOverride = new int[size];
                control = new boolean[size];
            } else Arrays.fill(control, 0, size, false);
            Arrays.fill(xOverride, 0, size, -1);
        }
    }
}
