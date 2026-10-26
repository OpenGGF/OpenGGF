package com.openggf.game.soniccd.device;

import java.util.Objects;

/**
 * Finite 2M Word RAM stamp-to-image transform. This has no CPU, game-program execution, engine state,
 * file access, timing estimates, or readiness policy. Callers own RAM and decide when a bounded
 * semantic step is permitted; executing a step does not assert native service completion time.
 *
 * Behavior is derived from GPGX 051d430d3d1b54625f9900c8f152d7f232e06daf,
 * core/cd_hw/gfx.c: gfx_init orientation/priority semantics (359-418),
 * gfx_render (468-601), gfx_start geometry (609-653), gfx_update line advance (738-745).
 * Coordinates below express the same orientation as whole-stamp geometry instead of native LUTs.
 * Runtime input is solely the caller's memory and semantic state.
 */
public final class CdStampGraphics {
    public static final int WORD_RAM_BYTES = 0x40000;
    private static final int BYTE_MASK = WORD_RAM_BYTES - 1;
    private static final long UNSIGNED_32_MASK = 0xffffffffL;

    private CdStampGraphics() { }

    /**
     * Transform at most {@code lineBudget} remaining lines in place, returning new immutable
     * progress. RAM must contain exactly 262144 bytes in 68000 address order, with big-endian words.
     * No RAM reference is retained or copied. External writes must not race this operation.
     *
     * All descriptors and the complete RAM extent are checked before writing. There are at most
     * 255 lines and 511 pixels per line. A zero budget or completed state changes nothing.
     * A vector is read completely before its line writes pixels; later vectors/map/art reads see
     * earlier writes, including when source and destination overlap. No completed line is replayed.
     */
    public static CdStampRenderState executeLines(byte[] wordRam, CdStampRenderState state, int lineBudget) {
        Objects.requireNonNull(wordRam, "wordRam");
        Objects.requireNonNull(state, "state");
        if (wordRam.length != WORD_RAM_BYTES) {
            throw new IllegalArgumentException("Expected exactly 256 KiB of 2M Word RAM");
        }
        if (lineBudget < 0) { throw new IllegalArgumentException("Line budget must be nonnegative"); }
        int lines = Math.min(lineBudget, state.remainingLines());
        if (lines == 0) { return state; }
        int trace = state.traceByteCursor();
        long image = state.imageNibbleCursor();
        for (int line = 0; line < lines; line++) {
            // Starts are unsigned 13.3, converted to 13.11. Deltas are signed 5.11.
            int x = word(wordRam, trace) << 8;
            int y = word(wordRam, trace + 2) << 8;
            int dx = (short) word(wordRam, trace + 4);
            int dy = (short) word(wordRam, trace + 6);
            trace = (trace + 8) & BYTE_MASK;
            renderLine(wordRam, state.request(), image, x, y, dx, dy);
            image = (image + 8) & UNSIGNED_32_MASK;
        }
        return new CdStampRenderState(state.request(), trace, image,
                state.completedLines() + lines, state.remainingLines() - lines);
    }

    private static void renderLine(byte[] ram, CdStampRenderRequest request, long destinationNibble,
                                   int x, int y, int dx, int dy) {
        int sourceMask = request.sourcePositionMask();
        int coordinateMask = request.repeat() ? sourceMask : 0xffffff;
        for (int pixel = 0; pixel < request.widthPixels(); pixel++) {
            x &= coordinateMask;
            y &= coordinateMask;
            int color = 0;
            if (((x | y) & ~sourceMask) == 0) {
                color = sourceColor(ram, request, x, y);
            }
            int destinationByte = (int) ((destinationNibble >>> 1) & BYTE_MASK);
            int previous = Byte.toUnsignedInt(ram[destinationByte]);
            int shift = (destinationNibble & 1) == 0 ? 4 : 0;
            int previousColor = (previous >>> shift) & 15;
            int result = switch (request.priority()) {
                case NORMAL -> color;
                case UNDERWRITE -> previousColor == 0 ? color : previousColor;
                case OVERWRITE -> color != 0 ? color : previousColor;
                case PRESERVE -> previousColor;
            };
            ram[destinationByte] = (byte) ((previous & ~(15 << shift)) | (result << shift));
            long step = (destinationNibble & 7) == 7 ? request.columnStepNibbles() : 1;
            destinationNibble = (destinationNibble + step) & UNSIGNED_32_MASK;
            x += dx;
            y += dy;
        }
    }

    private static int sourceColor(byte[] ram, CdStampRenderRequest request, int x13_11, int y13_11) {
        int column = x13_11 >>> request.stampPositionShift();
        int row = y13_11 >>> request.stampPositionShift();
        int mapEntry = word(ram, request.mapByteAddress() + 2 * (column | (row << request.mapRowShift())));
        int stampNumber = mapEntry & request.stampNumberMask();
        if (stampNumber == 0) { return 0; }

        int edgeMask = request.stampSize().pixels() - 1;
        int x = (x13_11 >>> 11) & edgeMask;
        int y = (y13_11 >>> 11) & edgeMask;
        int orientation = mapEntry >>> 13;
        if ((orientation & 4) != 0) { x ^= edgeMask; }
        if ((orientation & 2) != 0) { x ^= edgeMask; y ^= edgeMask; }
        if ((orientation & 1) != 0) {
            int previousX = x;
            x = y ^ edgeMask;
            y = previousX;
        }
        // Cells are stored down each column; pixels inside each 8x8 cell are stored by row.
        int cell = (x >>> 3) * (request.stampSize().pixels() >>> 3) + (y >>> 3);
        int sourceNibble = (stampNumber << 8) | (cell << 6) | ((y & 7) << 3) | (x & 7);
        int pair = Byte.toUnsignedInt(ram[sourceNibble >>> 1]);
        return (pair >>> ((sourceNibble & 1) == 0 ? 4 : 0)) & 15;
    }

    private static int word(byte[] ram, int address) {
        return (Byte.toUnsignedInt(ram[address]) << 8) | Byte.toUnsignedInt(ram[address + 1]);
    }
}
