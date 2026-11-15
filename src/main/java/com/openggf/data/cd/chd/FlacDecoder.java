package com.openggf.data.cd.chd;

/**
 * FLAC frame decoder for CHD {@code cdfl} hunks, written from the FLAC format specification
 * (RFC 9639). A CHD hunk holds bare frames with no stream header: 16-bit stereo at 44.1 kHz, as MAME's
 * CD FLAC codec configures it. Samples are written interleaved, big-endian, which is the CHD's stored
 * byte order for every CD sector. Header CRC-8 and frame CRC-16 are verified.
 */
final class FlacDecoder {
    private static final int CHANNELS = 2;
    private static final int BITS = 16;

    private final int[][] channels = new int[CHANNELS][];

    /**
     * Decodes frames from {@code src[offset, offset+length)} until {@code out} holds
     * {@code out.length / 4} stereo samples; returns the number of source bytes the frames used.
     */
    int decode(byte[] src, int offset, int length, byte[] out, int outLength) throws ChdFormatException {
        if ((outLength & 3) != 0) {
            throw new ChdFormatException("FLAC output is not whole stereo samples");
        }
        int totalSamples = outLength / 4;
        int done = 0;
        FlacBits bits = new FlacBits(src, offset, length);
        while (done < totalSamples) {
            int blockSize = frame(bits, src, offset);
            if (blockSize > totalSamples - done) {
                throw new ChdFormatException("FLAC frames exceed the hunk");
            }
            int write = done * 4;
            for (int i = 0; i < blockSize; i++) {
                for (int channel = 0; channel < CHANNELS; channel++) {
                    int sample = channels[channel][i];
                    out[write++] = (byte) (sample >> 8);
                    out[write++] = (byte) sample;
                }
            }
            done += blockSize;
        }
        return bits.bytePosition();
    }

    private int frame(FlacBits bits, byte[] src, int base) throws ChdFormatException {
        int frameStart = bits.bytePosition();
        if (bits.read(15) != 0x7FFC) {
            throw new ChdFormatException("FLAC frame sync not found");
        }
        bits.read(1);
        int blockCode = bits.read(4);
        int rateCode = bits.read(4);
        int assignment = bits.read(4);
        int sizeCode = bits.read(3);
        if (bits.read(1) != 0) {
            throw new ChdFormatException("FLAC frame header reserved bit set");
        }
        int first = bits.read(8);
        int extra = first < 0x80 ? 0 : first >= 0xFE ? 6 : Integer.numberOfLeadingZeros(~(first << 24)) - 1;
        if (extra < 0 || first == 0xFF || (first & 0xC0) == 0x80) {
            throw new ChdFormatException("FLAC frame number is malformed");
        }
        for (int i = 0; i < extra; i++) {
            if ((bits.read(8) & 0xC0) != 0x80) {
                throw new ChdFormatException("FLAC frame number is malformed");
            }
        }
        int blockSize = switch (blockCode) {
            case 0 -> throw new ChdFormatException("FLAC reserved block size");
            case 1 -> 192;
            case 2, 3, 4, 5 -> 576 << (blockCode - 2);
            case 6 -> bits.read(8) + 1;
            case 7 -> bits.read(16) + 1;
            default -> 256 << (blockCode - 8);
        };
        switch (rateCode) {
            case 12 -> bits.read(8);
            case 13, 14 -> bits.read(16);
            case 15 -> throw new ChdFormatException("FLAC invalid sample rate");
            default -> { }
        }
        if (sizeCode != 0 && sizeCode != 4) {
            throw new ChdFormatException("FLAC frame is not 16-bit");
        }
        if (assignment > 10 || (assignment < 8 && assignment != 1)) {
            throw new ChdFormatException("FLAC frame is not stereo");
        }
        int headerEnd = bits.bytePosition();
        int crc8 = bits.read(8);
        if (crc8(src, base + frameStart, headerEnd - frameStart) != crc8) {
            throw new ChdFormatException("FLAC frame header CRC mismatch");
        }
        for (int channel = 0; channel < CHANNELS; channel++) {
            if (channels[channel] == null || channels[channel].length < blockSize) {
                channels[channel] = new int[Math.max(blockSize, 4608)];
            }
            int depth = BITS + ((assignment == 8 || assignment == 10) && channel == 1
                    || assignment == 9 && channel == 0 ? 1 : 0);
            subframe(bits, channels[channel], blockSize, depth);
        }
        bits.alignToByte();
        int frameEnd = bits.bytePosition();
        int crc16 = bits.read(16);
        if (crc16(src, base + frameStart, frameEnd - frameStart) != crc16) {
            throw new ChdFormatException("FLAC frame CRC mismatch");
        }
        int[] left = channels[0];
        int[] right = channels[1];
        for (int i = 0; i < blockSize; i++) {
            switch (assignment) {
                case 8 -> right[i] = left[i] - right[i];
                case 9 -> left[i] = left[i] + right[i];
                case 10 -> {
                    int side = right[i];
                    int mid = (left[i] << 1) | (side & 1);
                    left[i] = (mid + side) >> 1;
                    right[i] = (mid - side) >> 1;
                }
                default -> { }
            }
            if (left[i] < Short.MIN_VALUE || left[i] > Short.MAX_VALUE
                    || right[i] < Short.MIN_VALUE || right[i] > Short.MAX_VALUE) {
                throw new ChdFormatException("FLAC sample outside 16 bits");
            }
        }
        return blockSize;
    }

    private static void subframe(FlacBits bits, int[] out, int blockSize, int depth) throws ChdFormatException {
        if (bits.read(1) != 0) {
            throw new ChdFormatException("FLAC subframe padding bit set");
        }
        int type = bits.read(6);
        int wasted = 0;
        if (bits.read(1) == 1) {
            wasted = bits.readUnary() + 1;
            if (wasted >= depth) {
                throw new ChdFormatException("FLAC wasted bits exceed sample depth");
            }
        }
        int width = depth - wasted;
        if (type == 0) {
            int value = bits.readSigned(width);
            for (int i = 0; i < blockSize; i++) {
                out[i] = value;
            }
        } else if (type == 1) {
            for (int i = 0; i < blockSize; i++) {
                out[i] = bits.readSigned(width);
            }
        } else if (type >= 8 && type <= 12) {
            int order = type - 8;
            warmup(bits, out, order, width, blockSize);
            residual(bits, out, order, blockSize);
            fixed(out, order, blockSize);
        } else if (type >= 32) {
            int order = type - 31;
            warmup(bits, out, order, width, blockSize);
            int precision = bits.read(4) + 1;
            if (precision == 16) {
                throw new ChdFormatException("FLAC invalid LPC precision");
            }
            int shift = bits.readSigned(5);
            if (shift < 0) {
                throw new ChdFormatException("FLAC negative LPC shift");
            }
            int[] coefficients = new int[order];
            for (int i = 0; i < order; i++) {
                coefficients[i] = bits.readSigned(precision);
            }
            residual(bits, out, order, blockSize);
            for (int i = order; i < blockSize; i++) {
                long sum = 0;
                for (int j = 0; j < order; j++) {
                    sum += (long) coefficients[j] * out[i - j - 1];
                }
                out[i] += (int) (sum >> shift);
            }
        } else {
            throw new ChdFormatException("FLAC reserved subframe type " + type);
        }
        if (wasted > 0) {
            for (int i = 0; i < blockSize; i++) {
                out[i] <<= wasted;
            }
        }
    }

    private static void warmup(FlacBits bits, int[] out, int order, int width, int blockSize)
            throws ChdFormatException {
        if (order > blockSize) {
            throw new ChdFormatException("FLAC predictor order exceeds block size");
        }
        for (int i = 0; i < order; i++) {
            out[i] = bits.readSigned(width);
        }
    }

    private static void residual(FlacBits bits, int[] out, int order, int blockSize) throws ChdFormatException {
        int method = bits.read(2);
        if (method > 1) {
            throw new ChdFormatException("FLAC reserved residual coding method");
        }
        int parameterBits = method == 0 ? 4 : 5;
        int escape = (1 << parameterBits) - 1;
        int partitionOrder = bits.read(4);
        int partitions = 1 << partitionOrder;
        if ((blockSize & (partitions - 1)) != 0 || (blockSize >> partitionOrder) < order) {
            throw new ChdFormatException("FLAC residual partitions do not fit the block");
        }
        int position = order;
        for (int partition = 0; partition < partitions; partition++) {
            int count = (blockSize >> partitionOrder) - (partition == 0 ? order : 0);
            int parameter = bits.read(parameterBits);
            if (parameter == escape) {
                int width = bits.read(5);
                for (int i = 0; i < count; i++) {
                    out[position++] = width == 0 ? 0 : bits.readSigned(width);
                }
            } else {
                for (int i = 0; i < count; i++) {
                    int quotient = bits.readUnary();
                    if (quotient > (Integer.MAX_VALUE >> Math.max(parameter, 1))) {
                        throw new ChdFormatException("FLAC residual out of range");
                    }
                    int value = (quotient << parameter) | bits.read(parameter);
                    out[position++] = (value >>> 1) ^ -(value & 1);
                }
            }
        }
    }

    private static void fixed(int[] out, int order, int blockSize) {
        for (int i = order; i < blockSize; i++) {
            int prediction = switch (order) {
                case 0 -> 0;
                case 1 -> out[i - 1];
                case 2 -> 2 * out[i - 1] - out[i - 2];
                case 3 -> 3 * out[i - 1] - 3 * out[i - 2] + out[i - 3];
                default -> 4 * out[i - 1] - 6 * out[i - 2] + 4 * out[i - 3] - out[i - 4];
            };
            out[i] += prediction;
        }
    }

    private static int crc8(byte[] data, int offset, int length) {
        int crc = 0;
        for (int i = offset; i < offset + length; i++) {
            crc ^= data[i] & 0xFF;
            for (int bit = 0; bit < 8; bit++) {
                crc = (crc & 0x80) != 0 ? ((crc << 1) ^ 0x07) & 0xFF : (crc << 1) & 0xFF;
            }
        }
        return crc;
    }

    private static final int[] CRC16 = new int[256];

    static {
        for (int i = 0; i < 256; i++) {
            int crc = i << 8;
            for (int bit = 0; bit < 8; bit++) {
                crc = (crc & 0x8000) != 0 ? (crc << 1) ^ 0x8005 : crc << 1;
            }
            CRC16[i] = crc & 0xFFFF;
        }
    }

    private static int crc16(byte[] data, int offset, int length) {
        int crc = 0;
        for (int i = offset; i < offset + length; i++) {
            crc = ((crc << 8) ^ CRC16[((crc >>> 8) ^ data[i]) & 0xFF]) & 0xFFFF;
        }
        return crc;
    }

    /** MSB-first reader that refuses to read past its range. */
    private static final class FlacBits {
        private final byte[] data;
        private final int start;
        private final int end;
        private int index;
        private long cache;
        private int cached;

        FlacBits(byte[] data, int offset, int length) {
            this.data = data;
            this.start = offset;
            this.end = offset + length;
            this.index = offset;
        }

        private void fill(int needed) throws ChdFormatException {
            while (cached < needed) {
                if (index >= end) {
                    throw new ChdFormatException("Truncated FLAC frame");
                }
                cache = (cache << 8) | (data[index++] & 0xFF);
                cached += 8;
            }
        }

        int read(int count) throws ChdFormatException {
            if (count == 0) {
                return 0;
            }
            fill(count);
            cached -= count;
            return (int) ((cache >>> cached) & ((1L << count) - 1));
        }

        int readSigned(int count) throws ChdFormatException {
            int value = read(count);
            return count == 32 ? value : (value << (32 - count)) >> (32 - count);
        }

        int readUnary() throws ChdFormatException {
            int zeros = 0;
            while (true) {
                if (cached == 0) {
                    fill(8);
                }
                long window = cache & ((1L << cached) - 1);
                if (window == 0) {
                    zeros += cached;
                    cached = 0;
                    if (zeros > 1 << 20) {
                        throw new ChdFormatException("FLAC unary run too long");
                    }
                    continue;
                }
                int leading = 63 - Long.numberOfLeadingZeros(window);
                zeros += cached - 1 - leading;
                cached = leading;
                return zeros;
            }
        }

        void alignToByte() {
            cached -= cached & 7;
        }

        /** Bytes consumed from the range start, counting only whole bytes handed out. */
        int bytePosition() {
            return index - start - cached / 8;
        }
    }
}
