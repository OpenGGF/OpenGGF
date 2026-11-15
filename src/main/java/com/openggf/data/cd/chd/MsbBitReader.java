package com.openggf.data.cd.chd;

/**
 * Most-significant-bit-first reader over a byte range. Bits past the end read as zero and set
 * {@link #overflowed()}, as the CHD v5 map stream is defined; callers decide whether that is an error.
 */
final class MsbBitReader {
    private final byte[] data;
    private final int start;
    private final int end;
    private long bitPosition;

    MsbBitReader(byte[] data, int offset, int length) {
        this.data = data;
        this.start = offset;
        this.end = offset + length;
    }

    /** Up to 32 bits without consuming them. */
    int peek(int count) {
        if (count == 0) {
            return 0;
        }
        long window = 0;
        int index = start + (int) (bitPosition >>> 3);
        for (int i = 0; i < 5; i++, index++) {
            window = (window << 8) | (index >= start && index < end ? data[index] & 0xFF : 0);
        }
        int skip = (int) (bitPosition & 7);
        return (int) ((window >>> (40 - skip - count)) & ((1L << count) - 1));
    }

    void remove(int count) {
        bitPosition += count;
    }

    int read(int count) {
        int value = peek(count);
        bitPosition += count;
        return value;
    }

    long readLong(int count) {
        if (count <= 32) {
            return Integer.toUnsignedLong(read(count));
        }
        long high = Integer.toUnsignedLong(read(count - 32));
        return (high << 32) | Integer.toUnsignedLong(read(32));
    }

    boolean overflowed() {
        return bitPosition > (long) (end - start) * 8;
    }
}
