package com.openggf.data;

import com.openggf.game.ModApi;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

/**
 * Read-only byte/word access helper for Sega ROM data.
 * All multi-byte reads are big-endian and unsigned unless stated otherwise.
 *
 * <p>A reader is either a plain window over one backing array or a
 * concatenation of other readers. Both are bounds-checked views: a read
 * outside the window is a hard {@link IndexOutOfBoundsException}, never a
 * read into the neighbouring bytes of the backing image. Views share the
 * backing array of the reader they were derived from, so a 2 MiB window over
 * a 4 MiB lock-on dump costs no copy.
 */
@ModApi
public class RomByteReader {

    /** Backing array for a plain window; {@code null} for a concatenation. */
    private final byte[] data;
    /** First backing index of a plain window. */
    private final int base;
    private final int length;
    /** Ordered parts of a concatenation; {@code null} for a plain window. */
    private final RomByteReader[] parts;
    /** Start address of each part inside this reader, parallel to {@link #parts}. */
    private final int[] partStarts;

    public RomByteReader(byte[] data) {
        this(Arrays.copyOf(data, data.length), 0, data.length);
    }

    private RomByteReader(byte[] data, int base, int length) {
        this.data = data;
        this.base = base;
        this.length = length;
        this.parts = null;
        this.partStarts = null;
    }

    private RomByteReader(RomByteReader[] parts) {
        this.data = null;
        this.base = 0;
        this.parts = parts;
        this.partStarts = new int[parts.length];
        long total = 0;
        for (int i = 0; i < parts.length; i++) {
            partStarts[i] = (int) total;
            total += parts[i].length;
            if (total > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Concatenated ROM view exceeds 2 GiB");
            }
        }
        this.length = (int) total;
    }

    /**
     * Returns the immutable reader shared for the current open ROM. Closing,
     * reopening, or writing through Rom invalidates its cached reader; already
     * issued readers remain immutable snapshots of their original bytes.
     */
    public static RomByteReader fromRom(Rom rom) throws IOException {
        return rom.byteReader();
    }

    /** Builds a reader over the given bytes (used for logical-ROM windows and tests). */
    public static RomByteReader fromBytes(byte[] data) {
        return new RomByteReader(data);
    }

    /** Builds a reader over a defensively copied byte range. */
    public static RomByteReader fromBytes(byte[] data, int from, int to) {
        Objects.checkFromToIndex(from, to, data.length);
        return new RomByteReader(Arrays.copyOfRange(data, from, to), 0, to - from);
    }

    /**
     * Returns a view of {@code length} bytes starting at {@code offset} of this
     * reader. Address 0 of the view is {@code offset} here; reads past
     * {@code length} fail even though the backing image continues. The window
     * must lie entirely inside this reader.
     */
    public RomByteReader window(int offset, int length) {
        Objects.checkFromIndexSize(offset, length, this.length);
        if (parts == null) {
            return new RomByteReader(data, base + offset, length);
        }
        // A window over a concatenation keeps the seam structure: gather the
        // overlapping slices of each part as sub-windows.
        java.util.List<RomByteReader> slices = new java.util.ArrayList<>();
        int end = offset + length;
        for (int i = 0; i < parts.length; i++) {
            int partStart = partStarts[i];
            int partEnd = partStart + parts[i].length;
            int from = Math.max(offset, partStart);
            int to = Math.min(end, partEnd);
            if (from < to) {
                slices.add(parts[i].window(from - partStart, to - from));
            }
        }
        if (slices.size() == 1) {
            return slices.get(0);
        }
        return new RomByteReader(slices.toArray(RomByteReader[]::new));
    }

    /**
     * Returns a view that presents the given readers back to back: the second
     * reader starts at the first reader's size, and so on. Multi-byte reads
     * that straddle a seam are assembled across it. No bytes are copied.
     */
    public static RomByteReader concat(RomByteReader... readers) {
        Objects.requireNonNull(readers, "readers");
        if (readers.length == 0) {
            throw new IllegalArgumentException("concat needs at least one reader");
        }
        for (RomByteReader reader : readers) {
            if (Objects.requireNonNull(reader, "reader").length == 0) {
                throw new IllegalArgumentException("concat parts must be non-empty");
            }
        }
        if (readers.length == 1) {
            return readers[0];
        }
        return new RomByteReader(readers.clone());
    }

    public int size() {
        return length;
    }

    public int readU8(int addr) {
        boundsCheck(addr, 1);
        return Byte.toUnsignedInt(at(addr));
    }

    public int readU16BE(int addr) {
        boundsCheck(addr, 2);
        return (Byte.toUnsignedInt(at(addr)) << 8) | Byte.toUnsignedInt(at(addr + 1));
    }

    public int readS16BE(int addr) {
        boundsCheck(addr, 2);
        return (short) readU16BE(addr);
    }

    public int readU32BE(int addr) {
        boundsCheck(addr, 4);
        return (Byte.toUnsignedInt(at(addr)) << 24)
             | (Byte.toUnsignedInt(at(addr + 1)) << 16)
             | (Byte.toUnsignedInt(at(addr + 2)) << 8)
             | Byte.toUnsignedInt(at(addr + 3));
    }

    public byte[] slice(int addr, int len) {
        boundsCheck(addr, len);
        byte[] out = new byte[len];
        copyTo(addr, out, 0, len);
        return out;
    }

    /**
     * Read a 16-bit offset from a word table and return the absolute address.
     * The table stores word offsets relative to {@code baseAddr}.
     */
    public int readPointer16(int baseAddr, int index) {
        int offset = readU16BE(baseAddr + index * 2);
        return baseAddr + offset;
    }

    /** Copies {@code len} bytes starting at {@code addr} into {@code dst}; callers bounds-check first. */
    void copyTo(int addr, byte[] dst, int dstOffset, int len) {
        if (parts == null) {
            System.arraycopy(data, base + addr, dst, dstOffset, len);
            return;
        }
        int remaining = len;
        int cursor = addr;
        int out = dstOffset;
        int index = partIndex(cursor);
        while (remaining > 0) {
            RomByteReader part = parts[index];
            int local = cursor - partStarts[index];
            int chunk = Math.min(remaining, part.length - local);
            part.copyTo(local, dst, out, chunk);
            remaining -= chunk;
            cursor += chunk;
            out += chunk;
            index++;
        }
    }

    private byte at(int addr) {
        if (parts == null) {
            return data[base + addr];
        }
        int index = partIndex(addr);
        return parts[index].at(addr - partStarts[index]);
    }

    private int partIndex(int addr) {
        int index = Arrays.binarySearch(partStarts, addr);
        return index >= 0 ? index : -index - 2;
    }

    private void boundsCheck(int addr, int len) {
        if (addr < 0 || len < 0 || addr + len > length) {
            throw new IndexOutOfBoundsException(
                    String.format("Read out of bounds: addr=0x%X len=%d size=0x%X", addr, len, length));
        }
    }
}
