package com.openggf.game.soniccd.device;

import java.util.Objects;

/**
 * Immutable line-boundary progress of a stamp operation. The trace cursor is an eight-byte-aligned
 * byte offset. The image cursor is an unsigned 32-bit nibble index (two pixels per byte), retained
 * without reducing it to a RAM address. Destination access wraps independently at 256 KiB.
 *
 * A snapshot consists of this value AND the caller's Word RAM snapshot. There are no hidden workers,
 * retained buffers, generations, or clocks to recreate. A captured continuation supplies its actual
 * cursors here; completed lines are never reconstructed from comparison pixels.
 *
 * Register decode follows MAME 9cf98d88 src/mame/sega/megacd.cpp,
 * {@code segacd_trace_vector_base_address_w}: trace vectors at {@code (FF8066 & FFFE) * 4}, eight
 * bytes per line; the image at {@code (FF805E & FFF8) * 4} bytes, plus FF8060's dot offset (bits
 * 0-2) and line offset (bits 3-5) within the first cell; FF8064 lines. The 8-bit line count is
 * pinned by {@code TestCdStampGraphics} without a document or measurement (MAME keeps 16 bits).
 */
public record CdStampRenderState(CdStampRenderRequest request, int traceByteCursor, long imageNibbleCursor,
                                 int completedLines, int remainingLines) {
    public CdStampRenderState {
        Objects.requireNonNull(request, "request");
        if (traceByteCursor < 0 || traceByteCursor >= CdStampGraphics.WORD_RAM_BYTES || (traceByteCursor & 7) != 0) {
            throw new IllegalArgumentException("Trace byte cursor must identify an aligned eight-byte vector");
        }
        if (imageNibbleCursor < 0 || imageNibbleCursor > 0xffffffffL) {
            throw new IllegalArgumentException("Image nibble cursor must fit an unsigned 32-bit value");
        }
        if (completedLines < 0 || completedLines > 255 || remainingLines < 0 || remainingLines > 255
                || completedLines + remainingLines > 255) {
            throw new IllegalArgumentException("An operation must contain 0 through 255 lines");
        }
    }

    /** Start at explicit semantic cursors, without interpreting service time or RAM ownership. */
    public static CdStampRenderState start(CdStampRenderRequest request, int traceByteAddress,
                                            long imageNibbleAddress, int heightLines) {
        return new CdStampRenderState(request, traceByteAddress, imageNibbleAddress, 0, heightLines);
    }

    /** Decode unsigned register words 66, 5E, 60 and 64 at submission, including their ignored bits. */
    public static CdStampRenderState fromStartRegisterWords(CdStampRenderRequest request, int traceBaseWord,
                                                             int imageBaseWord, int imageOffsetWord,
                                                             int verticalDotsWord) {
        CdStampRenderRequest.requireWord(traceBaseWord);
        CdStampRenderRequest.requireWord(imageBaseWord);
        CdStampRenderRequest.requireWord(imageOffsetWord);
        CdStampRenderRequest.requireWord(verticalDotsWord);
        int trace = (traceBaseWord << 2) & 0x3fff8;
        long image = ((imageBaseWord << 3) & 0x7ffc0) + (imageOffsetWord & 63);
        return start(request, trace, image, verticalDotsWord & 255);
    }
}
