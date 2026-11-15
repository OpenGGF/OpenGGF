/*
 * Translated from buram.c of buram, the Mega CD BackupRAM management tool,
 * https://github.com/superctr/buram commit 98c27a650be9c8f8e35271f44f5d127ba450edb6
 * (MIT licence; text below and in LICENSES/buram-MIT.txt).
 *
 * Copyright (c) 2022 Ian Karlsson
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 * Modified 2026-11 by the OpenGGF contributors: translated from C to Java
 * (buram_rs_init, buram_rs_add_mod, buram_rs_encode, buram_rs_decode,
 * buram_crc_init, buram_crc, the data/RS6/RS8 interleave functions,
 * buram_encode and buram_decode); the interleave helpers are inlined, and a
 * block with an uncorrectable symbol or a failed CRC decodes to null.
 */
package com.openggf.game.soniccd.backupram;

/**
 * The firmware's protected-block code: how the Sega CD BIOS stores 32 bytes in one 64-byte backup-RAM
 * block (directory entries always; file data when written with protection). A CRC-16/CCITT pair guards the
 * payload, the 36 bytes are spread into 6-bit symbols, and two Reed-Solomon codes (over GF(64) and
 * GF(256)) add parity across the block. The BIOS is not on the disc, so this layout is firmware
 * behaviour, not game data: this class is translated from the MIT-licensed {@code superctr/buram}
 * (buram.c, {@code buram_encode}/{@code buram_decode} and the functions they call; see the header) and
 * is checked byte-for-byte against the directory block the native USA BIOS wrote on a fresh boot
 * ({@code TestCdBackupRamNative}).
 */
public final class CdBramEcc {
    private CdBramEcc() { }

    /** One Reed-Solomon code: 6 data symbols, 2 parity symbols. */
    private static final class Code {
        final int nn;
        final int[] indexOf = new int[256];
        final int[] alphaTo = new int[256];
        final int[] polygen;

        Code(int poly, int symbolBits, int[] polygen) {
            this.polygen = polygen;
            nn = (1 << symbolBits) - 1;
            for (int i = 1, sr = 1; i <= nn; i++) {
                indexOf[sr] = i;
                alphaTo[i] = sr;
                sr <<= 1;
                if ((sr & (1 << symbolBits)) != 0) sr ^= poly;
                sr &= nn;
            }
        }

        private int addMod(int i, int d) {
            d += polygen[i];
            while (d >= nn) d -= nn;
            return alphaTo[d + 1];
        }

        void encode(int[] data) {
            int p1 = 0, p2 = 0;
            for (int i = 0; i < 6; i++) {
                int d = data[i];
                if (d != 0) {
                    d = indexOf[d] - 1;
                    p1 ^= addMod(i, d);
                    p2 ^= addMod(i + 6, d);
                }
            }
            data[6] = p1;
            data[7] = p2;
        }

        /** Corrects one symbol; returns false when an error was found that it could not correct. */
        boolean decode(int[] data) {
            int mask = 0, loc = 0;
            for (int i = 0; i < 8; i++) {
                int d = data[i];
                mask ^= d;
                if (d != 0) {
                    d = indexOf[d] + 6 - i;
                    while (d >= nn) d -= nn;
                    loc ^= alphaTo[d + 1];
                }
            }
            if (mask == 0) return true;
            int d = nn + indexOf[loc] - indexOf[mask];
            while (d >= nn) d -= nn;
            if (d < 8) {
                data[7 - d] ^= mask;
                return true;
            }
            return false;
        }
    }

    private static final Code RS8 = new Code(0x1D, 8, new int[] {87, 166, 113, 75, 198, 25, 167, 114, 76, 199, 26, 1});
    private static final Code RS6 = new Code(3, 6, new int[] {20, 58, 56, 18, 26, 6, 59, 57, 19, 27, 7, 1});
    private static final int[] LAST_DATA_BYTE = {0x2D, 0x2E, 0x2F, 0x08, 0x11, 0x1A, 0x23, 0x2C};
    private static final int[] CRC = new int[256];

    static {
        for (int i = 0; i < 256; i++) {
            int d = i << 8;
            for (int j = 0; j < 8; j++) d = ((d << 1) ^ ((d & 0x8000) != 0 ? 0x1021 : 0)) & 0xFFFF;
            CRC[i] = d;
        }
    }

    private static int crc(int[] buf) {
        int d = 0;
        for (int i = 2; i < 34; i++) d = ((d << 8) & 0xFFFF) ^ CRC[(buf[i] ^ (d >> 8)) & 0xFF];
        return d;
    }

    /** The 64 stored bytes of a protected block holding {@code payload} (32 bytes). */
    public static byte[] encode(byte[] payload) {
        if (payload.length != 32) throw new IllegalArgumentException("A protected block holds 32 bytes");
        int[] buf = new int[36];
        for (int i = 0; i < 32; i++) buf[i + 2] = payload[i] & 0xFF;
        int crc = crc(buf);
        buf[0] = crc >> 8;
        buf[1] = crc & 0xFF;
        buf[34] = ~buf[0] & 0xFF;
        buf[35] = ~buf[1] & 0xFF;
        int[] out = new int[64];
        // 36 bytes as 48 six-bit symbols, each in the top of a byte.
        for (int i = 0, in = 0, o = 0; i < 12; i++) {
            int sr = buf[in++];
            out[o++] = sr & 0xFF;
            sr = ((sr << 8) | buf[in++]) & 0xFFFF;
            out[o++] = (sr >> 2) & 0xFF;
            sr = ((sr << 8) | buf[in++]) & 0xFFFF;
            out[o++] = (sr >> 4) & 0xFF;
            out[o++] = (sr << 2) & 0xFF;
        }
        int[] block = new int[8];
        for (int b = 0; b < 8; b++) {
            for (int i = 0; i < 5; i++) block[i] = out[b + i * 9] >> 2;
            block[5] = out[LAST_DATA_BYTE[b]] >> 2;
            block[6] = out[0x30 + b] >> 2;
            block[7] = out[0x38 + b] >> 2;
            RS6.encode(block);
            for (int i = 0; i < 5; i++) out[b + i * 9] = (block[i] << 2) & 0xFF;
            out[LAST_DATA_BYTE[b]] = (block[5] << 2) & 0xFF;
            out[0x30 + b] = (block[6] << 2) & 0xFF;
            out[0x38 + b] = (block[7] << 2) & 0xFF;
        }
        for (int b = 0; b < 8; b++) {
            bitColumns(out, b, block);
            RS8.encode(block);
            bitRows(block, b, out);
        }
        byte[] bytes = new byte[64];
        for (int i = 0; i < 64; i++) bytes[i] = (byte) out[i];
        return bytes;
    }

    /** The 32 payload bytes of a stored protected block, or null when the block cannot be corrected or its CRC fails. */
    public static byte[] decode(byte[] stored, int offset) {
        int[] in = new int[64];
        for (int i = 0; i < 64; i++) in[i] = stored[offset + i] & 0xFF;
        int[] block = new int[8];
        boolean ok = true;
        for (int b = 0; b < 8; b++) {
            bitColumns(in, b, block);
            ok &= RS8.decode(block);
            bitRows(block, b, in);
        }
        for (int b = 0; b < 8; b++) {
            for (int i = 0; i < 5; i++) block[i] = in[b + i * 9] >> 2;
            block[5] = in[LAST_DATA_BYTE[b]] >> 2;
            block[6] = in[0x30 + b] >> 2;
            block[7] = in[0x38 + b] >> 2;
            ok &= RS6.decode(block);
            for (int i = 0; i < 5; i++) in[b + i * 9] = (block[i] << 2) & 0xFF;
            in[LAST_DATA_BYTE[b]] = (block[5] << 2) & 0xFF;
            in[0x30 + b] = (block[6] << 2) & 0xFF;
            in[0x38 + b] = (block[7] << 2) & 0xFF;
        }
        int[] buf = new int[36];
        for (int i = 0, p = 0, o = 0; i < 12; i++) {
            int sr = in[p++];
            sr <<= 6;
            sr = (sr & 0xFF00) | in[p++];
            buf[o++] = (sr >> 6) & 0xFF;
            sr = (sr << 6) & 0xFFFF;
            sr = (sr & 0xFF00) | in[p++];
            buf[o++] = (sr >> 4) & 0xFF;
            sr = (sr << 6) & 0xFFFF;
            sr = (sr & 0xFF00) | in[p++];
            buf[o++] = (sr >> 2) & 0xFF;
        }
        int crc = crc(buf);
        int first = (buf[0] << 8) | buf[1];
        int second = ~((buf[34] << 8) | buf[35]) & 0xFFFF;
        if (!ok || (crc != first && crc != second)) return null;
        byte[] payload = new byte[32];
        for (int i = 0; i < 32; i++) payload[i] = (byte) buf[i + 2];
        return payload;
    }

    /** Bit {@code 7-j} of the eight bytes {@code b, b+8, ..., b+56} as code symbol {@code j}. */
    private static void bitColumns(int[] bytes, int b, int[] block) {
        java.util.Arrays.fill(block, 0);
        for (int i = 0; i < 8; i++) {
            int tmp = bytes[b + i * 8];
            for (int j = 0; j < 8; j++) {
                block[j] = ((block[j] << 1) | (tmp >> 7)) & 0xFF;
                tmp = (tmp << 1) & 0xFF;
            }
        }
    }

    private static void bitRows(int[] block, int b, int[] bytes) {
        for (int i = 0; i < 8; i++) {
            int tmp = block[i];
            for (int j = 0; j < 8; j++) {
                bytes[b + j * 8] = ((bytes[b + j * 8] << 1) | (tmp >> 7)) & 0xFF;
                tmp = (tmp << 1) & 0xFF;
            }
        }
    }
}
