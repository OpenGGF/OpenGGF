/*
 * Translated from MAME src/mame/sega/megacd.cpp (sega_segacd_device::segacd_trace_vector_base_address_w,
 * read_pixel_from_stampmap and write_pixel), https://github.com/mamedev/mame commit
 * 9cf98d883e72e2ab8d4d73a7104989b4b306dda7 (BSD-3-Clause; text below and in
 * LICENSES/MAME-BSD-3-Clause.txt).
 *
 * Copyright-holders: David Haywood
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * Modified 2026-11 by the OpenGGF contributors: translated from C++ to Java. The operation runs
 * line by line from caller-held cursors instead of all at once; addresses are nibble indices into
 * a byte array rather than upstream's 16-bit word RAM; reserved priority mode 3 keeps the
 * destination (upstream writes random data).
 */
package com.openggf.game.soniccd.device;

import java.util.Objects;

/**
 * The Mega-CD graphics ("image rotation") operation over 2M Word RAM: for each image line, read
 * one trace vector, then for each dot sample the stamp map at the vector's position, step the
 * position and write the dot into the cell-ordered image buffer under the priority mode.
 *
 * <p>Translated from MAME's {@code sega_segacd_device} (see the header): a trace vector is four
 * big-endian words, X and Y start in unsigned 13.3 fixed point and X and Y steps in signed 5.11;
 * positions wrap at 24 bits; a dot is sampled before the step. Outside the map the dot is colour
 * 0 unless repeat is set, which wraps both axes. Dot {@code n} of a line lands at nibble
 * {@code (cursor & ~7) + ((n + (cursor & 7)) / 8) * cells * 64 + (n + (cursor & 7)) % 8}, and
 * each line moves the cursor eight nibbles (one cell row). Stamp-map and art addressing is
 * {@link CdStampGeometry}. Priority: normal writes; underwrite writes only over colour 0;
 * overwrite writes only nonzero dots; reserved mode 3 leaves the destination unchanged (this is
 * the expectation of {@code TestCdStampGraphics}, with no document or measurement behind it;
 * Sonic CD never selects it).
 *
 * <p>No CPU, game program, timing or ownership policy: callers own the RAM and decide when a
 * bounded number of lines may run; running them asserts nothing about native completion time.
 */
public final class CdStampGraphics {
    public static final int WORD_RAM_BYTES = 0x40000;
    private static final int NIBBLE_MASK = 2 * WORD_RAM_BYTES - 1;
    private static final int POSITION_MASK = 0xFFFFFF;

    private CdStampGraphics() {
    }

    /**
     * Run at most {@code lineBudget} of the remaining lines in place and return the new progress.
     * RAM must be exactly 262144 bytes in 68000 order (big-endian words); it is neither retained
     * nor copied, and must not be written concurrently. Each vector is read in full before its
     * line writes; map, art and later vectors are read from the RAM as it is at that moment.
     * A zero budget or completed state changes nothing.
     */
    public static CdStampRenderState executeLines(byte[] wordRam, CdStampRenderState state, int lineBudget) {
        Objects.requireNonNull(wordRam, "wordRam");
        Objects.requireNonNull(state, "state");
        if (wordRam.length != WORD_RAM_BYTES) {
            throw new IllegalArgumentException("2M Word RAM must be 262144 bytes");
        }
        if (lineBudget < 0) {
            throw new IllegalArgumentException("Line budget must not be negative");
        }
        int lines = Math.min(lineBudget, state.remainingLines());
        if (lines == 0) {
            return state;
        }
        CdStampRenderRequest request = state.request();
        int trace = state.traceByteCursor();
        long image = state.imageNibbleCursor();
        for (int line = 0; line < lines; line++) {
            int x = word(wordRam, trace) << 8;
            int y = word(wordRam, trace + 2) << 8;
            int dx = (short) word(wordRam, trace + 4);
            int dy = (short) word(wordRam, trace + 6);
            drawLine(wordRam, request, image, x, y, dx, dy);
            trace = (trace + 8) & (WORD_RAM_BYTES - 1);
            image = (image + 8) & 0xFFFF_FFFFL;
        }
        return new CdStampRenderState(request, trace, image, state.completedLines() + lines,
                state.remainingLines() - lines);
    }

    private static void drawLine(byte[] ram, CdStampRenderRequest request, long cursor,
                                 int x, int y, int dx, int dy) {
        long lineBase = cursor & ~7L;
        int phase = (int) (cursor & 7);
        long columnNibbles = request.imageColumnStrideCells() * 64L;
        for (int dot = 0; dot < request.widthPixels(); dot++) {
            int colour = sample(ram, request, x >>> 11, y >>> 11);
            x = (x + dx) & POSITION_MASK;
            y = (y + dy) & POSITION_MASK;
            int column = dot + phase;
            int nibble = (int) ((lineBase + (column >>> 3) * columnNibbles + (column & 7)) & NIBBLE_MASK);
            write(ram, request.priority(), nibble, colour);
        }
    }

    /** Colour of map pixel {@code (px, py)}, each 0-8191. */
    private static int sample(byte[] ram, CdStampRenderRequest request, int px, int py) {
        int mapEdge = request.mapSize().pixels();
        if (request.repeat()) {
            px &= mapEdge - 1;
            py &= mapEdge - 1;
        } else if (px >= mapEdge || py >= mapEdge) {
            return 0;
        }
        int edge = request.stampSize().pixels();
        int stampsPerRow = mapEdge / edge;
        int entry = word(ram, request.mapByteAddress() + 2 * ((py / edge) * stampsPerRow + px / edge));
        int number = CdStampGeometry.stampNumber(entry, edge);
        if (number == 0) {
            return 0;
        }
        int nibble = ((number << 8) + CdStampGeometry.artNibble(edge, entry >>> 13, px & (edge - 1), py & (edge - 1)))
                & NIBBLE_MASK;
        int pair = ram[nibble >>> 1] & 0xFF;
        return (nibble & 1) == 0 ? pair >>> 4 : pair & 0x0F;
    }

    private static void write(byte[] ram, CdStampRenderRequest.Priority priority, int nibble, int colour) {
        int index = nibble >>> 1;
        int shift = (nibble & 1) == 0 ? 4 : 0;
        int existing = (ram[index] >>> shift) & 0x0F;
        boolean store = switch (priority) {
            case NORMAL -> true;
            case UNDERWRITE -> existing == 0;
            case OVERWRITE -> colour != 0;
            case PRESERVE -> false;
        };
        if (store) {
            ram[index] = (byte) ((ram[index] & ~(0x0F << shift)) | (colour << shift));
        }
    }

    private static int word(byte[] ram, int address) {
        return ((ram[address] & 0xFF) << 8) | (ram[address + 1] & 0xFF);
    }
}
