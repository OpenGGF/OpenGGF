package com.openggf.game.soniccd.level;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Objects;

/** Owned immutable bytes. Slices share only immutable storage, never a caller's array or buffer cursor. */
public final class CdAssetBytes {
    private final byte[] storage;
    private final int start;
    private final int length;

    private CdAssetBytes(byte[] storage, int start, int length) {
        this.storage = storage;
        this.start = start;
        this.length = length;
    }

    public static CdAssetBytes copyOf(byte[] bytes) {
        return new CdAssetBytes(bytes.clone(), 0, bytes.length);
    }

    public int length() { return length; }
    public int unsignedByte(int index) { return Byte.toUnsignedInt(signedByte(index)); }
    public byte signedByte(int index) {
        Objects.checkIndex(index, length);
        return storage[start + index];
    }
    public int unsignedWord(int byteOffset) {
        Objects.checkFromIndexSize(byteOffset, 2, length);
        return (unsignedByte(byteOffset) << 8) | unsignedByte(byteOffset + 1);
    }
    public long unsignedLong(int byteOffset) {
        Objects.checkFromIndexSize(byteOffset, 4, length);
        return ((long) unsignedWord(byteOffset) << 16) | unsignedWord(byteOffset + 2);
    }
    public CdAssetBytes slice(int offset, int count) {
        Objects.checkFromIndexSize(offset, count, length);
        return new CdAssetBytes(storage, start + offset, count);
    }
    public byte[] copy() { return Arrays.copyOfRange(storage, start, start + length); }
    public ByteBuffer buffer() {
        return ByteBuffer.wrap(storage, start, length).slice().asReadOnlyBuffer().order(ByteOrder.BIG_ENDIAN);
    }
}
