/*
 * Stamp-map and stamp-art addressing translated from MAME src/mame/sega/megacd.cpp
 * (sega_segacd_device: the SCD_GET_TILE_INFO_* stamp-map decoders, the
 * get_stampmap_*_tile_info_pixel samplers and the 16x16/32x32 stamp gfx_layout tables),
 * https://github.com/mamedev/mame commit 9cf98d883e72e2ab8d4d73a7104989b4b306dda7
 * (BSD-3-Clause; text below and in LICENSES/MAME-BSD-3-Clause.txt).
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
 * Modified 2026-11 by the OpenGGF contributors: translated from C++ to Java. The eight
 * gfx_layout tables become one coordinate transform; the flipped quarter turns follow the
 * native captures rather than upstream (see artNibble).
 */
package com.openggf.game.soniccd.device;

/**
 * Where a stamp-map pixel's colour lives: the stamp-map entry format and the stamp-art layout of the
 * Mega-CD graphics ("image rotation") unit, shared by the general device {@link CdStampGraphics} and
 * the special-stage floor renderer.
 *
 * <p>A stamp-map entry is one big-endian word: bit 15 flips the stamp horizontally, bits 14-13
 * rotate it by quarter turns, and the low bits are the stamp number in 16-pixel-stamp units
 * (bits 10-0 for 16-pixel stamps, bits 10-2 for 32-pixel stamps). Stamp number 0 is transparent and
 * its art is never read. Stamp {@code n}'s art starts at Word RAM byte {@code n * 128}; it is 8 x 8
 * cells of 32 bytes, stored down each column of cells, four bytes per cell row, high nibble first.
 */
public final class CdStampGeometry {
    private CdStampGeometry() {
    }

    /** Stamp number in 16-pixel-stamp units for a map entry, or 0 for a transparent stamp. */
    public static int stampNumber(int mapEntry, int stampEdge) {
        return mapEntry & (stampEdge == 16 ? 0x7FF : 0x7FC);
    }

    /**
     * Nibble offset from the start of a stamp's art of stamp-local pixel {@code (u, v)} under the
     * entry's orientation ({@code mapEntry >>> 13}, flip in bit 2, quarter turns in bits 0-1).
     *
     * <p>Unflipped, the rotations are MAME's {@code r00}-{@code r11} layouts. Flipped, the stamp is
     * mirrored before it is rotated: this matches MAME for half turns, but for one and three quarter
     * turns MAME's {@code r01_f1}/{@code r11_f1} layouts are exchanged relative to the native
     * special-stage floor captures (BizHawk 2.11, {@code native/rehost-special}, recorded by its
     * {@code _tools/special_probe.cs}): with MAME's order, {@code TestCdSpecialStageFloorNative}
     * and {@code TestCdSpecialStageSessionNative} fail on 8 compared frames.
     */
    public static int artNibble(int stampEdge, int orientation, int u, int v) {
        int last = stampEdge - 1;
        if ((orientation & 4) != 0) {
            u = last - u;
        }
        int x;
        int y;
        switch (orientation & 3) {
            case 0 -> { x = u; y = v; }
            case 1 -> { x = last - v; y = u; }
            case 2 -> { x = last - u; y = last - v; }
            default -> { x = v; y = last - u; }
        }
        int cell = (x >>> 3) * (stampEdge >>> 3) + (y >>> 3);
        return (cell << 6) | ((y & 7) << 3) | (x & 7);
    }
}
