package com.openggf.data.cd.chd;

/**
 * Zstandard frame decoder for CHD {@code zstd}/{@code cdzs} hunks, written from RFC 8878. It decodes
 * single-frame content into a caller-sized buffer; dictionaries are refused, skippable frames are skipped,
 * and the optional content checksum is not verified (the CHD hunk CRC-16 covers the output).
 */
final class ZstdDecoder {
    private static final int MAGIC = 0xFD2FB528;
    private static final int MAX_BLOCK = 128 * 1024;

    private static final int[] LL_BASE = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
            16, 18, 20, 22, 24, 28, 32, 40, 48, 64, 128, 256, 512, 1024, 2048, 4096, 8192, 16384, 32768, 65536};
    private static final int[] LL_BITS = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            1, 1, 1, 1, 2, 2, 3, 3, 4, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
    private static final int[] ML_BASE = new int[53];
    private static final int[] ML_BITS = new int[53];
    private static final short[] LL_DEFAULT = {4, 3, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 1, 1, 1,
            2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 2, 1, 1, 1, 1, 1, -1, -1, -1, -1};
    private static final short[] ML_DEFAULT = {1, 4, 3, 2, 2, 2, 2, 2, 2, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            -1, -1, -1, -1, -1, -1, -1};
    private static final short[] OF_DEFAULT = {1, 1, 1, 1, 1, 1, 2, 2, 2, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1, -1, -1, -1, -1, -1};

    static {
        int[] tailBase = {35, 37, 39, 41, 43, 47, 51, 59, 67, 83, 99, 131, 259, 515, 1027, 2051, 4099, 8195,
                16387, 32771, 65539};
        int[] tailBits = {1, 1, 1, 1, 2, 2, 3, 3, 4, 4, 5, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
        for (int i = 0; i < 32; i++) {
            ML_BASE[i] = i + 3;
        }
        System.arraycopy(tailBase, 0, ML_BASE, 32, tailBase.length);
        System.arraycopy(tailBits, 0, ML_BITS, 32, tailBits.length);
    }

    private static final Fse LL_PREDEFINED;
    private static final Fse ML_PREDEFINED;
    private static final Fse OF_PREDEFINED;

    static {
        try {
            LL_PREDEFINED = Fse.build(LL_DEFAULT, LL_DEFAULT.length, 6);
            ML_PREDEFINED = Fse.build(ML_DEFAULT, ML_DEFAULT.length, 6);
            OF_PREDEFINED = Fse.build(OF_DEFAULT, OF_DEFAULT.length, 5);
        } catch (ChdFormatException impossible) {
            throw new ExceptionInInitializerError(impossible);
        }
    }

    // Per-frame state.
    private byte[] src;
    private byte[] out;
    private int outPos;
    private int outEnd;
    private final int[] repeats = new int[3];
    private Fse llTable;
    private Fse ofTable;
    private Fse mlTable;
    private Huffman huffman;
    private final byte[] literals = new byte[MAX_BLOCK];

    /** Decodes every frame in {@code src[offset, offset+length)} into exactly {@code outLength} bytes of {@code out}. */
    void decode(byte[] source, int offset, int length, byte[] output, int outLength) throws ChdFormatException {
        src = source;
        out = output;
        outPos = 0;
        outEnd = outLength;
        int pos = offset;
        int end = offset + length;
        while (pos < end) {
            if (end - pos < 4) {
                throw new ChdFormatException("Truncated zstd frame");
            }
            int magic = le32(pos);
            if ((magic & 0xFFFFFFF0) == 0x184D2A50) {
                if (end - pos < 8) {
                    throw new ChdFormatException("Truncated zstd skippable frame");
                }
                long skip = Integer.toUnsignedLong(le32(pos + 4));
                if (skip > end - pos - 8) {
                    throw new ChdFormatException("Truncated zstd skippable frame");
                }
                pos += 8 + (int) skip;
                continue;
            }
            if (magic != MAGIC) {
                throw new ChdFormatException("Not a zstd frame");
            }
            pos = frame(pos + 4, end);
        }
        if (outPos != outLength) {
            throw new ChdFormatException("zstd output is " + outPos + " bytes, expected " + outLength);
        }
    }

    private int frame(int pos, int end) throws ChdFormatException {
        need(pos, 1, end);
        int descriptor = src[pos++] & 0xFF;
        int fcsFlag = descriptor >>> 6;
        boolean singleSegment = (descriptor & 0x20) != 0;
        boolean checksum = (descriptor & 0x04) != 0;
        int dictFlag = descriptor & 3;
        if ((descriptor & 0x08) != 0) {
            throw new ChdFormatException("zstd frame header reserved bit set");
        }
        if (!singleSegment) {
            pos++;
        }
        int dictBytes = dictFlag == 3 ? 4 : dictFlag;
        need(pos, dictBytes, end);
        for (int i = 0; i < dictBytes; i++) {
            if (src[pos + i] != 0) {
                throw new ChdFormatException("zstd dictionaries are not supported");
            }
        }
        pos += dictBytes;
        int fcsBytes = fcsFlag == 0 ? (singleSegment ? 1 : 0) : 1 << fcsFlag;
        need(pos, fcsBytes, end);
        pos += fcsBytes;
        repeats[0] = 1;
        repeats[1] = 4;
        repeats[2] = 8;
        llTable = ofTable = mlTable = null;
        huffman = null;
        int frameStart = outPos;
        boolean last = false;
        while (!last) {
            need(pos, 3, end);
            int header = (src[pos] & 0xFF) | (src[pos + 1] & 0xFF) << 8 | (src[pos + 2] & 0xFF) << 16;
            pos += 3;
            last = (header & 1) != 0;
            int type = (header >>> 1) & 3;
            int size = header >>> 3;
            switch (type) {
                case 0 -> {
                    need(pos, size, end);
                    room(size);
                    System.arraycopy(src, pos, out, outPos, size);
                    outPos += size;
                    pos += size;
                }
                case 1 -> {
                    need(pos, 1, end);
                    room(size);
                    java.util.Arrays.fill(out, outPos, outPos + size, src[pos]);
                    outPos += size;
                    pos += 1;
                }
                case 2 -> {
                    if (size > MAX_BLOCK) {
                        throw new ChdFormatException("zstd block exceeds 128 KiB");
                    }
                    need(pos, size, end);
                    compressedBlock(pos, pos + size, frameStart);
                    pos += size;
                }
                default -> throw new ChdFormatException("zstd reserved block type");
            }
        }
        if (checksum) {
            need(pos, 4, end);
            pos += 4;
        }
        return pos;
    }

    private void compressedBlock(int pos, int end, int frameStart) throws ChdFormatException {
        int[] literalCount = new int[1];
        pos = literalsSection(pos, end, literalCount);
        int literalLength = literalCount[0];
        need(pos, 1, end);
        int first = src[pos++] & 0xFF;
        int sequences;
        if (first == 0) {
            sequences = 0;
        } else if (first < 128) {
            sequences = first;
        } else if (first < 255) {
            need(pos, 1, end);
            sequences = ((first - 128) << 8) + (src[pos++] & 0xFF);
        } else {
            need(pos, 2, end);
            sequences = (src[pos] & 0xFF) + ((src[pos + 1] & 0xFF) << 8) + 0x7F00;
            pos += 2;
        }
        int literalPos = 0;
        if (sequences > 0) {
            need(pos, 1, end);
            int modes = src[pos++] & 0xFF;
            if ((modes & 3) != 0) {
                throw new ChdFormatException("zstd sequence modes reserved bits set");
            }
            int[] cursor = {pos};
            llTable = table(modes >>> 6, cursor, end, LL_PREDEFINED, llTable, 35, 9);
            ofTable = table((modes >>> 4) & 3, cursor, end, OF_PREDEFINED, ofTable, 31, 8);
            mlTable = table((modes >>> 2) & 3, cursor, end, ML_PREDEFINED, mlTable, 52, 9);
            pos = cursor[0];
            BackwardBits bits = new BackwardBits(src, pos, end);
            int llState = bits.read(llTable.log);
            int ofState = bits.read(ofTable.log);
            int mlState = bits.read(mlTable.log);
            for (int n = 0; n < sequences; n++) {
                int ofCode = ofTable.symbol[ofState];
                int mlCode = mlTable.symbol[mlState];
                int llCode = llTable.symbol[llState];
                if (ofCode > 31) {
                    throw new ChdFormatException("zstd offset code out of range");
                }
                long offsetValue = (1L << ofCode) + bits.readLong(ofCode);
                int matchLength = ML_BASE[mlCode] + bits.read(ML_BITS[mlCode]);
                int litLength = LL_BASE[llCode] + bits.read(LL_BITS[llCode]);
                int offset;
                if (offsetValue > 3) {
                    if (offsetValue - 3 > Integer.MAX_VALUE) {
                        throw new ChdFormatException("zstd offset out of range");
                    }
                    offset = (int) (offsetValue - 3);
                    repeats[2] = repeats[1];
                    repeats[1] = repeats[0];
                    repeats[0] = offset;
                } else {
                    int index = (int) offsetValue - 1;
                    if (litLength == 0) {
                        index++;
                    }
                    if (index == 0) {
                        offset = repeats[0];
                    } else {
                        offset = index < 3 ? repeats[index] : repeats[0] - 1;
                        if (offset <= 0) {
                            throw new ChdFormatException("zstd repeat offset is zero");
                        }
                        if (index > 1) {
                            repeats[2] = repeats[1];
                        }
                        repeats[1] = repeats[0];
                        repeats[0] = offset;
                    }
                }
                if (litLength > literalLength - literalPos) {
                    throw new ChdFormatException("zstd sequence uses more literals than decoded");
                }
                room(litLength);
                System.arraycopy(literals, literalPos, out, outPos, litLength);
                literalPos += litLength;
                outPos += litLength;
                if (offset > outPos - frameStart) {
                    throw new ChdFormatException("zstd match offset precedes the frame");
                }
                room(matchLength);
                int from = outPos - offset;
                for (int i = 0; i < matchLength; i++) {
                    out[outPos++] = out[from++];
                }
                if (n + 1 < sequences) {
                    llState = llTable.base[llState] + bits.read(llTable.bits[llState]);
                    mlState = mlTable.base[mlState] + bits.read(mlTable.bits[mlState]);
                    ofState = ofTable.base[ofState] + bits.read(ofTable.bits[ofState]);
                }
            }
            if (!bits.finished()) {
                throw new ChdFormatException("zstd sequence bitstream not fully consumed");
            }
        } else if (pos != end) {
            throw new ChdFormatException("zstd block has bytes after an empty sequence section");
        }
        int remaining = literalLength - literalPos;
        room(remaining);
        System.arraycopy(literals, literalPos, out, outPos, remaining);
        outPos += remaining;
    }

    private Fse table(int mode, int[] cursor, int end, Fse predefined, Fse previous, int maxSymbol, int maxLog)
            throws ChdFormatException {
        switch (mode) {
            case 0:
                return predefined;
            case 1: {
                need(cursor[0], 1, end);
                int symbol = src[cursor[0]++] & 0xFF;
                if (symbol > maxSymbol) {
                    throw new ChdFormatException("zstd RLE symbol out of range");
                }
                return Fse.rle(symbol);
            }
            case 2: {
                ForwardBits bits = new ForwardBits(src, cursor[0], end);
                Fse table = Fse.read(bits, maxSymbol, maxLog);
                cursor[0] = bits.alignedPosition();
                return table;
            }
            default:
                if (previous == null) {
                    throw new ChdFormatException("zstd repeat table with no previous table");
                }
                return previous;
        }
    }

    private int literalsSection(int pos, int end, int[] count) throws ChdFormatException {
        need(pos, 1, end);
        int b0 = src[pos] & 0xFF;
        int type = b0 & 3;
        int format = (b0 >>> 2) & 3;
        if (type <= 1) {
            int regenerated;
            int headerBytes;
            if ((format & 1) == 0) {
                regenerated = b0 >>> 3;
                headerBytes = 1;
            } else if (format == 1) {
                need(pos, 2, end);
                regenerated = (b0 >>> 4) + ((src[pos + 1] & 0xFF) << 4);
                headerBytes = 2;
            } else {
                need(pos, 3, end);
                regenerated = (b0 >>> 4) + ((src[pos + 1] & 0xFF) << 4) + ((src[pos + 2] & 0xFF) << 12);
                headerBytes = 3;
            }
            if (regenerated > MAX_BLOCK) {
                throw new ChdFormatException("zstd literals exceed 128 KiB");
            }
            pos += headerBytes;
            if (type == 0) {
                need(pos, regenerated, end);
                System.arraycopy(src, pos, literals, 0, regenerated);
                pos += regenerated;
            } else {
                need(pos, 1, end);
                java.util.Arrays.fill(literals, 0, regenerated, src[pos]);
                pos += 1;
            }
            count[0] = regenerated;
            return pos;
        }
        int headerBytes = format <= 1 ? 3 : format == 2 ? 4 : 5;
        int sizeBits = format <= 1 ? 10 : format == 2 ? 14 : 18;
        boolean fourStreams = format != 0;
        need(pos, headerBytes, end);
        long header = 0;
        for (int i = headerBytes - 1; i >= 0; i--) {
            header = (header << 8) | (src[pos + i] & 0xFF);
        }
        int regenerated = (int) ((header >>> 4) & ((1 << sizeBits) - 1));
        int compressed = (int) ((header >>> (4 + sizeBits)) & ((1 << sizeBits) - 1));
        if (regenerated > MAX_BLOCK) {
            throw new ChdFormatException("zstd literals exceed 128 KiB");
        }
        pos += headerBytes;
        need(pos, compressed, end);
        int streamEnd = pos + compressed;
        if (type == 2) {
            int[] cursor = {pos};
            huffman = Huffman.read(src, cursor, streamEnd);
            pos = cursor[0];
        } else if (huffman == null) {
            throw new ChdFormatException("zstd treeless literals with no previous tree");
        }
        if (!fourStreams) {
            huffman.decodeStream(src, pos, streamEnd, literals, 0, regenerated);
        } else {
            need(pos, 6, streamEnd);
            int size1 = (src[pos] & 0xFF) | (src[pos + 1] & 0xFF) << 8;
            int size2 = (src[pos + 2] & 0xFF) | (src[pos + 3] & 0xFF) << 8;
            int size3 = (src[pos + 4] & 0xFF) | (src[pos + 5] & 0xFF) << 8;
            int start1 = pos + 6;
            int start2 = start1 + size1;
            int start3 = start2 + size2;
            int start4 = start3 + size3;
            if (start4 > streamEnd) {
                throw new ChdFormatException("zstd literal jump table overruns its section");
            }
            int segment = (regenerated + 3) / 4;
            if (3 * segment > regenerated) {
                throw new ChdFormatException("zstd four-stream literals are too short");
            }
            huffman.decodeStream(src, start1, start2, literals, 0, segment);
            huffman.decodeStream(src, start2, start3, literals, segment, segment);
            huffman.decodeStream(src, start3, start4, literals, 2 * segment, segment);
            huffman.decodeStream(src, start4, streamEnd, literals, 3 * segment, regenerated - 3 * segment);
        }
        count[0] = regenerated;
        return streamEnd;
    }

    private void room(int bytes) throws ChdFormatException {
        if (bytes < 0 || bytes > outEnd - outPos) {
            throw new ChdFormatException("zstd output exceeds the hunk");
        }
    }

    private static void need(int pos, int bytes, int end) throws ChdFormatException {
        if (bytes < 0 || pos > end || bytes > end - pos) {
            throw new ChdFormatException("Truncated zstd data");
        }
    }

    private int le32(int pos) {
        return (src[pos] & 0xFF) | (src[pos + 1] & 0xFF) << 8 | (src[pos + 2] & 0xFF) << 16
                | (src[pos + 3] & 0xFF) << 24;
    }

    /** Finite State Entropy decoding table. */
    static final class Fse {
        final int log;
        final int[] symbol;
        final int[] bits;
        final int[] base;

        private Fse(int log) {
            this.log = log;
            int size = 1 << log;
            symbol = new int[size];
            bits = new int[size];
            base = new int[size];
        }

        static Fse rle(int value) {
            Fse table = new Fse(0);
            table.symbol[0] = value;
            return table;
        }

        static Fse read(ForwardBits in, int maxSymbol, int maxLog) throws ChdFormatException {
            int log = in.read(4) + 5;
            if (log > maxLog) {
                throw new ChdFormatException("zstd FSE accuracy log too large");
            }
            short[] counts = new short[maxSymbol + 1];
            int remaining = (1 << log) + 1;
            int symbol = 0;
            while (remaining > 1 && symbol <= maxSymbol) {
                int width = 32 - Integer.numberOfLeadingZeros(remaining);
                int value = in.read(width);
                int lowerMask = (1 << (width - 1)) - 1;
                int threshold = (1 << width) - 1 - remaining;
                if ((value & lowerMask) < threshold) {
                    in.rewind(1);
                    value &= lowerMask;
                } else if (value > lowerMask) {
                    value -= threshold;
                }
                int probability = value - 1;
                remaining -= Math.abs(probability);
                counts[symbol++] = (short) probability;
                if (probability == 0) {
                    int repeat = in.read(2);
                    while (true) {
                        for (int i = 0; i < repeat; i++) {
                            if (symbol > maxSymbol) {
                                throw new ChdFormatException("zstd FSE zero run past last symbol");
                            }
                            counts[symbol++] = 0;
                        }
                        if (repeat != 3) {
                            break;
                        }
                        repeat = in.read(2);
                    }
                }
            }
            if (remaining != 1 || in.overflowed()) {
                throw new ChdFormatException("zstd FSE distribution is corrupt");
            }
            return build(counts, symbol, log);
        }

        static Fse build(short[] counts, int symbols, int log) throws ChdFormatException {
            Fse table = new Fse(log);
            int size = 1 << log;
            int[] next = new int[symbols];
            int high = size - 1;
            for (int s = 0; s < symbols; s++) {
                if (counts[s] == -1) {
                    table.symbol[high--] = s;
                    next[s] = 1;
                }
            }
            int step = (size >>> 1) + (size >>> 3) + 3;
            int mask = size - 1;
            int position = 0;
            for (int s = 0; s < symbols; s++) {
                if (counts[s] <= 0) {
                    continue;
                }
                next[s] = counts[s];
                for (int i = 0; i < counts[s]; i++) {
                    table.symbol[position] = s;
                    do {
                        position = (position + step) & mask;
                    } while (position > high);
                }
            }
            if (position != 0) {
                throw new ChdFormatException("zstd FSE table spread is corrupt");
            }
            for (int i = 0; i < size; i++) {
                int state = next[table.symbol[i]]++;
                int width = log - (31 - Integer.numberOfLeadingZeros(state));
                table.bits[i] = width;
                table.base[i] = (state << width) - size;
            }
            return table;
        }
    }

    /** Huffman literal decoding table. */
    static final class Huffman {
        private final int maxBits;
        private final int[] symbol;
        private final int[] length;

        private Huffman(int maxBits) {
            this.maxBits = maxBits;
            symbol = new int[1 << maxBits];
            length = new int[1 << maxBits];
        }

        static Huffman read(byte[] src, int[] cursor, int end) throws ChdFormatException {
            int pos = cursor[0];
            need(pos, 1, end);
            int header = src[pos++] & 0xFF;
            int[] weights = new int[256];
            int count;
            if (header < 128) {
                need(pos, header, end);
                ForwardBits forward = new ForwardBits(src, pos, pos + header);
                Fse table = Fse.read(forward, 255, 6);
                int streamStart = forward.alignedPosition();
                BackwardBits bits = new BackwardBits(src, streamStart, pos + header);
                int state1 = bits.read(table.log);
                int state2 = bits.read(table.log);
                count = 0;
                while (true) {
                    if (count >= 255) {
                        throw new ChdFormatException("zstd Huffman weights overflow");
                    }
                    weights[count++] = table.symbol[state1];
                    state1 = table.base[state1] + bits.read(table.bits[state1]);
                    if (bits.overread()) {
                        weights[count++] = table.symbol[state2];
                        break;
                    }
                    if (count >= 255) {
                        throw new ChdFormatException("zstd Huffman weights overflow");
                    }
                    weights[count++] = table.symbol[state2];
                    state2 = table.base[state2] + bits.read(table.bits[state2]);
                    if (bits.overread()) {
                        if (count >= 255) {
                            throw new ChdFormatException("zstd Huffman weights overflow");
                        }
                        weights[count++] = table.symbol[state1];
                        break;
                    }
                }
                pos += header;
            } else {
                count = header - 127;
                int bytes = (count + 1) / 2;
                need(pos, bytes, end);
                for (int i = 0; i < count; i++) {
                    int b = src[pos + i / 2] & 0xFF;
                    weights[i] = (i & 1) == 0 ? b >>> 4 : b & 0xF;
                }
                pos += bytes;
            }
            long total = 0;
            for (int i = 0; i < count; i++) {
                if (weights[i] > 11) {
                    throw new ChdFormatException("zstd Huffman weight too large");
                }
                if (weights[i] > 0) {
                    total += 1L << (weights[i] - 1);
                }
            }
            if (total == 0) {
                throw new ChdFormatException("zstd Huffman weights are empty");
            }
            int maxBits = 64 - Long.numberOfLeadingZeros(total);
            long rest = (1L << maxBits) - total;
            if (maxBits > 11 || Long.bitCount(rest) != 1) {
                throw new ChdFormatException("zstd Huffman weights do not complete a tree");
            }
            weights[count++] = 63 - Long.numberOfLeadingZeros(rest) + 1;
            Huffman huffman = new Huffman(maxBits);
            int[] rankStart = new int[maxBits + 2];
            int[] rankCount = new int[maxBits + 2];
            for (int i = 0; i < count; i++) {
                if (weights[i] > maxBits) {
                    throw new ChdFormatException("zstd Huffman weight exceeds table");
                }
                rankCount[weights[i]]++;
            }
            int next = 0;
            for (int w = 1; w <= maxBits; w++) {
                rankStart[w] = next;
                next += rankCount[w] << (w - 1);
            }
            if (next != 1 << maxBits) {
                throw new ChdFormatException("zstd Huffman table is incomplete");
            }
            for (int s = 0; s < count; s++) {
                int w = weights[s];
                if (w == 0) {
                    continue;
                }
                int span = 1 << (w - 1);
                for (int i = rankStart[w]; i < rankStart[w] + span; i++) {
                    huffman.symbol[i] = s;
                    huffman.length[i] = maxBits + 1 - w;
                }
                rankStart[w] += span;
            }
            cursor[0] = pos;
            return huffman;
        }

        void decodeStream(byte[] src, int start, int end, byte[] out, int outOffset, int count)
                throws ChdFormatException {
            BackwardBits bits = new BackwardBits(src, start, end);
            for (int i = 0; i < count; i++) {
                int index = bits.peek(maxBits);
                out[outOffset + i] = (byte) symbol[index];
                bits.skip(length[index]);
            }
            if (!bits.finished()) {
                throw new ChdFormatException("zstd Huffman stream size mismatch");
            }
        }
    }

    /** Little-endian, least-significant-bit-first forward reader for FSE table descriptions. */
    static final class ForwardBits {
        private final byte[] data;
        private final int start;
        private final int end;
        private long position;

        ForwardBits(byte[] data, int start, int end) {
            this.data = data;
            this.start = start;
            this.end = end;
        }

        int read(int count) {
            int value = 0;
            for (int i = 0; i < count; i++) {
                long bit = position + i;
                int index = start + (int) (bit >>> 3);
                int b = index < end ? data[index] & 0xFF : 0;
                value |= ((b >>> (bit & 7)) & 1) << i;
            }
            position += count;
            return value;
        }

        void rewind(int count) {
            position -= count;
        }

        boolean overflowed() {
            return position > (long) (end - start) * 8;
        }

        int alignedPosition() throws ChdFormatException {
            if (overflowed()) {
                throw new ChdFormatException("Truncated zstd FSE table description");
            }
            return start + (int) ((position + 7) >>> 3);
        }
    }

    /**
     * Reverse bitstream: the byte range is one little-endian integer, the highest set bit of its last
     * byte is padding, and bits are consumed from the top down. Reads below bit zero return zeros.
     */
    static final class BackwardBits {
        private final byte[] data;
        private final int start;
        private final int end;
        private long remaining;

        BackwardBits(byte[] data, int start, int end) throws ChdFormatException {
            if (end <= start || data[end - 1] == 0) {
                throw new ChdFormatException("zstd bitstream has no end mark");
            }
            this.data = data;
            this.start = start;
            this.end = end;
            int last = data[end - 1] & 0xFF;
            remaining = (long) (end - start) * 8 - (8 - (31 - Integer.numberOfLeadingZeros(last)));
        }

        int peek(int count) {
            return (int) bits(remaining - count, count);
        }

        void skip(int count) {
            remaining -= count;
        }

        int read(int count) {
            int value = peek(count);
            remaining -= count;
            return value;
        }

        long readLong(int count) {
            if (count <= 31) {
                return read(count);
            }
            long high = read(count - 16);
            return (high << 16) | read(16);
        }

        boolean overread() {
            return remaining < 0;
        }

        boolean finished() {
            return remaining == 0;
        }

        /** Bits {@code [low, low+count)} of the little-endian integer; bits below zero are zero. */
        private long bits(long low, int count) {
            if (count == 0) {
                return 0;
            }
            long value = 0;
            long first = Math.floorDiv(low, 8);
            for (int i = 7; i >= 0; i--) {
                long index = first + i;
                int b = index >= 0 && index < end - start ? data[start + (int) index] & 0xFF : 0;
                value = (value << 8) | b;
            }
            int shift = (int) Math.floorMod(low, 8);
            return (value >>> shift) & ((1L << count) - 1);
        }
    }
}
