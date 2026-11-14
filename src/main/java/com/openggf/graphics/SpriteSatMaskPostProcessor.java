package com.openggf.graphics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Mode-5 SAT evaluation before mapping pieces expand into tiles. Admission follows
 * SAT order, independent of the sprite/background priority bit. The optional S3K
 * Render_Sprites rewrite changes whole-word $07C0 entries to X=1 and their successor
 * to X=0; native budgets and ordinary X=0 masking apply even without that rewrite.
 * Presentation may disable the line budgets while retaining table capacity and masks.
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

    /** Exact presentation span over an aligned native SAT capacity. */
    public static final class LogicalViewport {
        private final Raster alignedRaster;
        private final int logicalWidth;

        private LogicalViewport(Raster alignedRaster, int logicalWidth) {
            this.alignedRaster = alignedRaster;
            this.logicalWidth = logicalWidth;
        }

        /** Wider presentation retains H40 admission without rounding its right edge. */
        public static LogicalViewport h40(int x, int y, int width, int height) {
            if (width < 320) throw new IllegalArgumentException("Logical H40 viewport needs width >= 320");
            return new LogicalViewport(Raster.h40(x, y, height), width);
        }

        static LogicalViewport fromAligned(Raster raster) {
            return new LogicalViewport(raster, raster.width());
        }

        int x() { return alignedRaster.x(); }
        int y() { return alignedRaster.y(); }
        int width() { return logicalWidth; }
        int height() { return alignedRaster.height(); }
        int hardwareWidth() { return alignedRaster.hardwareWidth(); }
        int lineSprites() { return alignedRaster.lineSprites(); }
        int tableSprites() { return alignedRaster.tableSprites(); }
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
        return processLogicalReusable(entries, spriteMaskEnabled,
                entries == null || entries.isEmpty() ? null : LogicalViewport.fromAligned(raster));
    }

    /** Thread-owned result; whole admitted source columns precede destination viewport clipping. */
    static List<SpriteSatEntry> processLogicalReusable(List<SpriteSatEntry> entries, boolean spriteMaskEnabled,
                                                       LogicalViewport raster) {
        return processLogicalReusable(entries, spriteMaskEnabled, raster, true);
    }

    /** Presentation may relax sprite budgets; explicit masks remain native. */
    static List<SpriteSatEntry> processLogicalReusable(List<SpriteSatEntry> entries, boolean spriteMaskEnabled,
                                                       LogicalViewport raster, boolean scanlineLimits) {
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
        int tableSprites = raster.tableSprites();
        for (int slot = 0; slot < ordered; slot++) {
            int index = scratch.order[slot];
            if (!scanlineLimits || entries.get(index).isGhost() || nativeEntries++ < tableSprites) {
                scratch.order[total++] = index;
            }
        }
        // Hoist admitted geometry into slot-ordered primitive arrays: the scanline loop
        // below visits every admitted entry on every raster line.
        int height = raster.height();
        int rasterX = raster.x();
        int rasterRight = raster.x() + raster.width();
        int rasterWidth = raster.width();
        int hardwareWidth = raster.hardwareWidth();
        int lineSprites = raster.lineSprites();
        int firstLine = Integer.MAX_VALUE;
        int endLine = Integer.MIN_VALUE;
        for (int slot = 0; slot < total; slot++) {
            int index = scratch.order[slot];
            SpriteSatEntry entry = entries.get(index);
            SpritePiece piece = entry.piece();
            int top = piece.y();
            int bottom = piece.endYExclusive();
            scratch.top[slot] = top;
            scratch.bottom[slot] = bottom;
            scratch.width[slot] = piece.widthTiles();
            scratch.ghost[slot] = entry.isGhost();
            scratch.inside[slot] = piece.endXExclusive() > rasterX && piece.x() < rasterRight;
            int rawX = scratch.xOverride[index];
            if (rawX < 0) {
                int biasedX = piece.x() - rasterX + 128;
                // Expanded raster coordinates exceed the native nine-bit SAT field.
                rawX = rasterWidth <= 320 ? biasedX & 0x1FF : biasedX;
            }
            scratch.rawX[slot] = rawX;
            if (top < firstLine) firstLine = top;
            if (bottom > endLine) endLine = bottom;
        }
        // A line no entry covers only clears the carried mask arm, so evaluation may
        // start at the first covered line with nothing carried and stop after the last.
        int firstRow = (int) Math.max(0L, Math.min(height, (long) firstLine - raster.y()));
        int endRow = (int) Math.max(0L, Math.min(height, (long) endLine - raster.y()));
        boolean carry = false;
        for (int row = firstRow; row < endRow; row++) {
            int line = raster.y() + row;
            boolean armed = carry;
            boolean masked = false;
            carry = false;
            int count = 0;
            int pixels = 0;
            boolean nativeExhausted = false;
            for (int slot = 0; slot < total; slot++) {
                if (line < scratch.top[slot] || line >= scratch.bottom[slot]) continue;
                int index = scratch.order[slot];
                if (scratch.ghost[slot]) {
                    if (!masked && scratch.inside[slot]) {
                        scratch.columns[index * height + row] = scratch.width[slot];
                    }
                    continue;
                }
                if (nativeExhausted) continue;
                if (scanlineLimits && count++ == lineSprites) {
                    nativeExhausted = true;
                    continue;
                }
                if (scratch.rawX[slot] != 0) armed = true;
                else if (armed) masked = true;
                int widthTiles = scratch.width[slot];
                int columns = scanlineLimits
                        ? Math.min(widthTiles, Math.max(0, (hardwareWidth - pixels) / 8))
                        : widthTiles;
                pixels += widthTiles * 8;
                if (!masked && !scratch.control[index] && scratch.inside[slot]) {
                    scratch.columns[index * height + row] = columns;
                }
                if (scanlineLimits && pixels >= hardwareWidth) {
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

    private static void appendVisible(SpriteSatEntry entry, int index, LogicalViewport raster, Scratch scratch) {
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
                    boolean wholeColumns = left == entry.piece().startColTile()
                            && right - left == entry.piece().colCountTiles();
                    boolean wholeLines = start == Math.max(entry.piece().y(), entry.visibleTopY())
                            && end == Math.min(entry.piece().endYExclusive(), entry.visibleBottomY());
                    SpriteSatEntry clipped;
                    if (wholeColumns && wholeLines) clipped = entry;
                    else if (wholeColumns) clipped = entry.withVisibleScanlines(start, end);
                    else clipped = entry.clipRectAndScanlines(left, right - left, entry.piece().startRowTile(),
                            entry.piece().rowCountTiles(), wholeLines ? entry.visibleTopY() : start,
                            wholeLines ? entry.visibleBottomY() : end);
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
        List<SpriteSatEntry> fragments = admitted.replayFragments();
        for (int i = 0, n = fragments.size(); i < n; i++) {
            SpriteSatEntry fragment = fragments.get(i);
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
                output.add(fragment.clipRectAndScanlines(sourceLeft, sourceRight - sourceLeft,
                        fragment.piece().startRowTile(), fragment.piece().rowCountTiles(), top, bottom));
            }
        }
    }

    private static final class Scratch {
        final ArrayList<SpriteSatEntry> processed = new ArrayList<>();
        int[] columns = new int[0];
        int[] xOverride = new int[0];
        int[] order = new int[0];
        boolean[] control = new boolean[0];
        // Slot-ordered admitted geometry, valid for the current call only.
        int[] top = new int[0];
        int[] bottom = new int[0];
        int[] width = new int[0];
        int[] rawX = new int[0];
        boolean[] ghost = new boolean[0];
        boolean[] inside = new boolean[0];

        void reset(int size, int height) {
            int cells = Math.multiplyExact(size, height);
            if (columns.length < cells) columns = new int[cells];
            else Arrays.fill(columns, 0, cells, 0);
            if (order.length < size) {
                order = new int[size];
                xOverride = new int[size];
                control = new boolean[size];
                top = new int[size];
                bottom = new int[size];
                width = new int[size];
                rawX = new int[size];
                ghost = new boolean[size];
                inside = new boolean[size];
            } else Arrays.fill(control, 0, size, false);
            Arrays.fill(xOverride, 0, size, -1);
        }
    }
}
