package com.openggf.data.cd.chd;

import java.util.Arrays;

/**
 * Raw LZMA decoder for CHD {@code lzma}/{@code cdlz} hunks, written from the LZMA specification in the
 * public-domain LZMA SDK ({@code lzma-specification.txt}). CHD stores no properties header: MAME's codec
 * normalizes encoder level 9, which fixes lc=3, lp=0, pb=2; the dictionary never exceeds one hunk, so the
 * output buffer is the whole window. There is no end marker; decoding stops at the known output size.
 */
final class LzmaDecoder {
    private static final int LC = 3;
    private static final int LP = 0;
    private static final int PB = 2;
    private static final int STATES = 12;
    private static final int POS_STATES = 1 << PB;
    private static final int INIT = 1024;

    private final short[] isMatch = new short[STATES * POS_STATES];
    private final short[] isRep = new short[STATES];
    private final short[] isRepG0 = new short[STATES];
    private final short[] isRepG1 = new short[STATES];
    private final short[] isRepG2 = new short[STATES];
    private final short[] isRep0Long = new short[STATES * POS_STATES];
    private final short[] posSlot = new short[4 << 6];
    private final short[] specPos = new short[115];
    private final short[] align = new short[16];
    private final short[] literal = new short[0x300 << (LC + LP)];
    private final LengthDecoder lengths = new LengthDecoder();
    private final LengthDecoder repLengths = new LengthDecoder();

    private byte[] in;
    private int inPos;
    private int inEnd;
    private int range;
    private int code;

    private final class LengthDecoder {
        final short[] choice = new short[2];
        final short[] low = new short[POS_STATES << 3];
        final short[] mid = new short[POS_STATES << 3];
        final short[] high = new short[256];

        void reset() {
            Arrays.fill(choice, (short) INIT);
            Arrays.fill(low, (short) INIT);
            Arrays.fill(mid, (short) INIT);
            Arrays.fill(high, (short) INIT);
        }

        int decode(int posState) throws ChdFormatException {
            if (bit(choice, 0) == 0) {
                return tree(low, posState << 3, 3);
            }
            if (bit(choice, 1) == 0) {
                return 8 + tree(mid, posState << 3, 3);
            }
            return 16 + tree(high, 0, 8);
        }
    }

    /** Decodes exactly {@code out.length} bytes from {@code src[offset, offset+length)}. */
    void decode(byte[] src, int offset, int length, byte[] out) throws ChdFormatException {
        reset();
        in = src;
        inPos = offset;
        inEnd = offset + length;
        if (length < 5 || src[offset] != 0) {
            throw new ChdFormatException("Invalid LZMA stream start");
        }
        inPos++;
        range = -1;
        code = 0;
        for (int i = 0; i < 4; i++) {
            code = (code << 8) | next();
        }
        int state = 0;
        int rep0 = 0;
        int rep1 = 0;
        int rep2 = 0;
        int rep3 = 0;
        int pos = 0;
        int total = out.length;
        while (pos < total) {
            int posState = pos & (POS_STATES - 1);
            if (bit(isMatch, (state << 2) + posState) == 0) {
                int previous = pos > 0 ? out[pos - 1] & 0xFF : 0;
                int base = 0x300 * (((pos & ((1 << LP) - 1)) << LC) + (previous >>> (8 - LC)));
                int symbol = 1;
                if (state >= 7) {
                    if (rep0 >= pos) {
                        throw new ChdFormatException("LZMA match distance precedes the hunk");
                    }
                    int matchByte = out[pos - rep0 - 1] & 0xFF;
                    do {
                        int matchBit = (matchByte >>> 7) & 1;
                        matchByte <<= 1;
                        int decoded = bit(literal, base + ((1 + matchBit) << 8) + symbol);
                        symbol = (symbol << 1) | decoded;
                        if (matchBit != decoded) {
                            break;
                        }
                    } while (symbol < 0x100);
                }
                while (symbol < 0x100) {
                    symbol = (symbol << 1) | bit(literal, base + symbol);
                }
                out[pos++] = (byte) symbol;
                state = state < 4 ? 0 : state < 10 ? state - 3 : state - 6;
                continue;
            }
            int len;
            if (bit(isRep, state) == 0) {
                rep3 = rep2;
                rep2 = rep1;
                rep1 = rep0;
                len = lengths.decode(posState);
                state = state < 7 ? 7 : 10;
                rep0 = distance(len);
                if (rep0 == -1) {
                    throw new ChdFormatException("LZMA end marker before the hunk is complete");
                }
            } else {
                if (pos == 0) {
                    throw new ChdFormatException("LZMA repeat match at hunk start");
                }
                if (bit(isRepG0, state) == 0) {
                    if (bit(isRep0Long, (state << 2) + posState) == 0) {
                        state = state < 7 ? 9 : 11;
                        if (rep0 >= pos) {
                            throw new ChdFormatException("LZMA match distance precedes the hunk");
                        }
                        out[pos] = out[pos - rep0 - 1];
                        pos++;
                        continue;
                    }
                } else {
                    int dist;
                    if (bit(isRepG1, state) == 0) {
                        dist = rep1;
                    } else {
                        if (bit(isRepG2, state) == 0) {
                            dist = rep2;
                        } else {
                            dist = rep3;
                            rep3 = rep2;
                        }
                        rep2 = rep1;
                    }
                    rep1 = rep0;
                    rep0 = dist;
                }
                len = repLengths.decode(posState);
                state = state < 7 ? 8 : 11;
            }
            len += 2;
            if (Integer.compareUnsigned(rep0, pos) >= 0) {
                throw new ChdFormatException("LZMA match distance precedes the hunk");
            }
            if (len > total - pos) {
                throw new ChdFormatException("LZMA match runs past the hunk");
            }
            int from = pos - rep0 - 1;
            for (int i = 0; i < len; i++) {
                out[pos++] = out[from++];
            }
        }
    }

    private void reset() {
        Arrays.fill(isMatch, (short) INIT);
        Arrays.fill(isRep, (short) INIT);
        Arrays.fill(isRepG0, (short) INIT);
        Arrays.fill(isRepG1, (short) INIT);
        Arrays.fill(isRepG2, (short) INIT);
        Arrays.fill(isRep0Long, (short) INIT);
        Arrays.fill(posSlot, (short) INIT);
        Arrays.fill(specPos, (short) INIT);
        Arrays.fill(align, (short) INIT);
        Arrays.fill(literal, (short) INIT);
        lengths.reset();
        repLengths.reset();
    }

    private int distance(int len) throws ChdFormatException {
        int lenState = Math.min(len, 3);
        int slot = tree(posSlot, lenState << 6, 6);
        if (slot < 4) {
            return slot;
        }
        int directBits = (slot >>> 1) - 1;
        int dist = (2 | (slot & 1)) << directBits;
        if (slot < 14) {
            return dist + reverseTree(specPos, dist - slot, directBits);
        }
        dist += direct(directBits - 4) << 4;
        return dist + reverseTree(align, 0, 4);
    }

    private int next() throws ChdFormatException {
        if (inPos >= inEnd) {
            throw new ChdFormatException("Truncated LZMA stream");
        }
        return in[inPos++] & 0xFF;
    }

    private int bit(short[] probs, int index) throws ChdFormatException {
        int prob = probs[index];
        int bound = (range >>> 11) * prob;
        int result;
        if (Integer.compareUnsigned(code, bound) < 0) {
            range = bound;
            probs[index] = (short) (prob + ((2048 - prob) >>> 5));
            result = 0;
        } else {
            range -= bound;
            code -= bound;
            probs[index] = (short) (prob - (prob >>> 5));
            result = 1;
        }
        if (Integer.compareUnsigned(range, 1 << 24) < 0) {
            range <<= 8;
            code = (code << 8) | next();
        }
        return result;
    }

    private int direct(int count) throws ChdFormatException {
        int result = 0;
        for (int i = 0; i < count; i++) {
            range >>>= 1;
            int bit = Integer.compareUnsigned(code, range) >= 0 ? 1 : 0;
            if (bit == 1) {
                code -= range;
            }
            result = (result << 1) | bit;
            if (Integer.compareUnsigned(range, 1 << 24) < 0) {
                range <<= 8;
                code = (code << 8) | next();
            }
        }
        return result;
    }

    private int tree(short[] probs, int base, int bits) throws ChdFormatException {
        int m = 1;
        for (int i = 0; i < bits; i++) {
            m = (m << 1) | bit(probs, base + m);
        }
        return m - (1 << bits);
    }

    /** Reverse bit tree whose node {@code m} lives at {@code probs[base + m]}. */
    private int reverseTree(short[] probs, int base, int bits) throws ChdFormatException {
        int m = 1;
        int symbol = 0;
        for (int i = 0; i < bits; i++) {
            int decoded = bit(probs, base + m);
            m = (m << 1) | decoded;
            symbol |= decoded << i;
        }
        return symbol;
    }
}
