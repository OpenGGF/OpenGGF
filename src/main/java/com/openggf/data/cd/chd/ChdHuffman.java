/*
 * Follows the CHD v5 map Huffman decoder of MAME src/lib/util/huffman.cpp, https://github.com/mamedev/mame
 * (the RLE tree import, canonical code assignment and lookup table), rewritten in Java
 * (BSD-3-Clause; text below and in LICENSES/MAME-BSD-3-Clause.txt).
 *
 * Copyright-holders: Aaron Giles
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
 * Modified 2026-11 by the OpenGGF contributors: translated from C++ to Java
 * (rf5c68_device::rf5c68_w, rf5c68_r, rf5c68_mem_r, rf5c68_mem_w, sound_stream_update and the
 * rf5c164_map address decode; the pcm_channel fields become per-channel arrays). The stream,
 * memory-space, save-state and sample-end-callback plumbing is not translated. Three behaviours
 * differ from upstream; see the class comment. Added by OpenGGF, not in upstream: stepSplit, which
 * also reports the part of the channel sum from a caller's set of channels, for the host's mix.
 */
package com.openggf.data.cd.chd;

/**
 * Canonical Huffman decoder for the CHD v5 compressed hunk map, from the CHD v5 format as documented by
 * MAME's {@code huffman.cpp}/{@code chd.cpp} (BSD-3-Clause): code lengths arrive run-length coded, codes
 * are assigned canonically from the longest length down, and lookup uses {@code maxBits}-wide peeks.
 */
final class ChdHuffman {
    private final int numCodes;
    private final int maxBits;
    private final int[] lengths;
    private final int[] lookup;

    private ChdHuffman(int numCodes, int maxBits) {
        this.numCodes = numCodes;
        this.maxBits = maxBits;
        this.lengths = new int[numCodes];
        this.lookup = new int[1 << maxBits];
    }

    static ChdHuffman importTreeRle(MsbBitReader bits, int numCodes, int maxBits) throws ChdFormatException {
        ChdHuffman huffman = new ChdHuffman(numCodes, maxBits);
        int numBits = maxBits >= 16 ? 5 : maxBits >= 8 ? 4 : 3;
        int node = 0;
        while (node < numCodes) {
            int nodeBits = bits.read(numBits);
            if (nodeBits != 1) {
                huffman.lengths[node++] = nodeBits;
            } else {
                nodeBits = bits.read(numBits);
                if (nodeBits == 1) {
                    huffman.lengths[node++] = 1;
                } else {
                    int repeat = bits.read(numBits) + 3;
                    if (repeat + node > numCodes) {
                        throw new ChdFormatException("CHD map Huffman tree repeats past its code count");
                    }
                    while (repeat-- > 0) {
                        huffman.lengths[node++] = nodeBits;
                    }
                }
            }
        }
        huffman.build();
        if (bits.overflowed()) {
            throw new ChdFormatException("CHD map Huffman tree is truncated");
        }
        return huffman;
    }

    private void build() throws ChdFormatException {
        long[] histogram = new long[33];
        for (int length : lengths) {
            if (length > maxBits) {
                throw new ChdFormatException("CHD map Huffman code is longer than its table");
            }
            histogram[length]++;
        }
        long start = 0;
        for (int length = 32; length > 0; length--) {
            long next = (start + histogram[length]) >> 1;
            if (length != 1 && next * 2 != start + histogram[length]) {
                throw new ChdFormatException("CHD map Huffman code lengths are inconsistent");
            }
            histogram[length] = start;
            start = next;
        }
        int[] codes = new int[numCodes];
        for (int code = 0; code < numCodes; code++) {
            if (lengths[code] > 0) {
                codes[code] = (int) histogram[lengths[code]]++;
            }
        }
        for (int code = 0; code < numCodes; code++) {
            int length = lengths[code];
            if (length > 0) {
                int shift = maxBits - length;
                int first = codes[code] << shift;
                int last = ((codes[code] + 1) << shift) - 1;
                if (last >= lookup.length) {
                    throw new ChdFormatException("CHD map Huffman code overflows its table");
                }
                for (int i = first; i <= last; i++) {
                    lookup[i] = (code << 5) | length;
                }
            }
        }
    }

    int decodeOne(MsbBitReader bits) throws ChdFormatException {
        int entry = lookup[bits.peek(maxBits)];
        int length = entry & 0x1F;
        if (length == 0) {
            throw new ChdFormatException("CHD map uses an unassigned Huffman code");
        }
        bits.remove(length);
        return entry >>> 5;
    }
}
