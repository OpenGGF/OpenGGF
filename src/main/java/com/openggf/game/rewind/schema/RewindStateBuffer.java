package com.openggf.game.rewind.schema;

import java.util.Arrays;
import java.util.Objects;

public final class RewindStateBuffer {
    private static final int DEFAULT_CAPACITY = 64;

    private byte[] data = new byte[DEFAULT_CAPACITY];
    private int size;

    public void writeByte(int value) {
        ensureCapacity(1);
        data[size++] = (byte) value;
    }

    public void writeBoolean(boolean value) {
        writeByte(value ? 1 : 0);
    }

    public void writeShort(int value) {
        ensureCapacity(Short.BYTES);
        data[size++] = (byte) value;
        data[size++] = (byte) (value >>> 8);
    }

    public void writeInt(int value) {
        ensureCapacity(Integer.BYTES);
        data[size++] = (byte) value;
        data[size++] = (byte) (value >>> 8);
        data[size++] = (byte) (value >>> 16);
        data[size++] = (byte) (value >>> 24);
    }

    public void writeLong(long value) {
        ensureCapacity(Long.BYTES);
        data[size++] = (byte) value;
        data[size++] = (byte) (value >>> 8);
        data[size++] = (byte) (value >>> 16);
        data[size++] = (byte) (value >>> 24);
        data[size++] = (byte) (value >>> 32);
        data[size++] = (byte) (value >>> 40);
        data[size++] = (byte) (value >>> 48);
        data[size++] = (byte) (value >>> 56);
    }

    public void writeFloat(float value) {
        writeInt(Float.floatToRawIntBits(value));
    }

    public void writeDouble(double value) {
        writeLong(Double.doubleToRawLongBits(value));
    }

    public void writeBytes(byte[] values) {
        Objects.requireNonNull(values, "values");
        ensureCapacity(values.length);
        System.arraycopy(values, 0, data, size, values.length);
        size += values.length;
    }

    public byte[] toByteArray() {
        return Arrays.copyOf(data, size);
    }

    OwnedBytes takeBytes() {
        OwnedBytes owned = new OwnedBytes(data, size);
        data = new byte[DEFAULT_CAPACITY];
        size = 0;
        return owned;
    }

    public Reader reader() {
        return new Reader(data, size, true);
    }

    public static Reader reader(byte[] data) {
        return new Reader(data);
    }

    static Reader readerForOwnedBytes(byte[] data, int length) {
        return new Reader(data, length, false);
    }

    private void ensureCapacity(int bytesToWrite) {
        int required = size + bytesToWrite;
        if (required <= data.length) {
            return;
        }

        int newCapacity = data.length;
        while (newCapacity < required) {
            newCapacity = Math.multiplyExact(newCapacity, 2);
        }
        data = Arrays.copyOf(data, newCapacity);
    }

    static final class OwnedBytes {
        private final byte[] bytes;
        private final int length;

        private OwnedBytes(byte[] bytes, int length) {
            this.bytes = Objects.requireNonNull(bytes, "bytes");
            if (length < 0 || length > bytes.length) {
                throw new IllegalArgumentException("length must be between 0 and bytes.length: " + length);
            }
            this.length = length;
        }

        byte[] bytes() {
            return bytes;
        }

        int length() {
            return length;
        }
    }

    public static final class Reader {
        private final byte[] data;
        private final int length;
        private int position;

        private Reader(byte[] data) {
            this(data, Objects.requireNonNull(data, "data").length, true);
        }

        private Reader(byte[] data, int length, boolean copy) {
            Objects.requireNonNull(data, "data");
            if (length < 0 || length > data.length) {
                throw new IllegalArgumentException("length must be between 0 and data.length: " + length);
            }
            this.data = copy ? Arrays.copyOf(data, length) : data;
            this.length = length;
        }

        public byte readByte() {
            requireAvailable(1);
            return data[position++];
        }

        public boolean readBoolean() {
            return readByte() != 0;
        }

        public short readShort() {
            requireAvailable(Short.BYTES);
            int value = (data[position] & 0xFF)
                    | ((data[position + 1] & 0xFF) << 8);
            position += Short.BYTES;
            return (short) value;
        }

        public int readInt() {
            requireAvailable(Integer.BYTES);
            int value = (data[position] & 0xFF)
                    | ((data[position + 1] & 0xFF) << 8)
                    | ((data[position + 2] & 0xFF) << 16)
                    | ((data[position + 3] & 0xFF) << 24);
            position += Integer.BYTES;
            return value;
        }

        public long readLong() {
            requireAvailable(Long.BYTES);
            long value = (long) data[position] & 0xFFL;
            value |= ((long) data[position + 1] & 0xFFL) << 8;
            value |= ((long) data[position + 2] & 0xFFL) << 16;
            value |= ((long) data[position + 3] & 0xFFL) << 24;
            value |= ((long) data[position + 4] & 0xFFL) << 32;
            value |= ((long) data[position + 5] & 0xFFL) << 40;
            value |= ((long) data[position + 6] & 0xFFL) << 48;
            value |= ((long) data[position + 7] & 0xFFL) << 56;
            position += Long.BYTES;
            return value;
        }

        public float readFloat() {
            return Float.intBitsToFloat(readInt());
        }

        public double readDouble() {
            return Double.longBitsToDouble(readLong());
        }

        public byte[] readBytes(int length) {
            if (length < 0) {
                throw new IllegalArgumentException("length must be non-negative: " + length);
            }
            requireAvailable(length);
            byte[] values = Arrays.copyOfRange(data, position, position + length);
            position += length;
            return values;
        }

        private void requireAvailable(int bytes) {
            if (position + bytes <= length) {
                return;
            }
            throw new IllegalStateException("Attempted to read past end of rewind state buffer: requested "
                    + bytes + " bytes at offset " + position + ", available " + (length - position) + ".");
        }
    }
}
