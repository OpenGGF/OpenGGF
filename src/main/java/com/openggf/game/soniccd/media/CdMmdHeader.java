package com.openggf.game.soniccd.media;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Raw big-endian facts from mmdFlags through mmdVInt (MMD.inc:18-27).
 * Word RAM geometry comes from WORDRAM2M/WORDRAM2MS (Main CPU.inc:83-85).
 * Paths are relative to docs/scddisasm/src/_Include; runtime only consumes supplied media.
 */
public record CdMmdHeader(int flags, int reserved, long origin, int sizeCounter,
                          long entry, long horizontalInterrupt, long verticalInterrupt) {
    public static final int HEADER_BYTES = 0x100;
    public static final int WORD_RAM_ADDRESS = 0x200000;
    public static final int WORD_RAM_BYTES = 0x40000;
    public static final long ADDRESS_SPACE_BYTES = 0x1000000L;

    public CdMmdHeader {
        if ((flags & ~0xff) != 0 || (reserved & ~0xff) != 0 || (sizeCounter & ~0xffff) != 0
                || !unsigned32(origin) || !unsigned32(entry) || !unsigned32(horizontalInterrupt)
                || !unsigned32(verticalInterrupt)) {
            throw new IllegalArgumentException("MMD fields exceed their encoded widths");
        }
    }

    public static CdMmdHeader parse(byte[] header) throws IOException {
        if (header.length < HEADER_BYTES) {
            throw new IOException("Truncated 256-byte MMD header");
        }
        ByteBuffer bytes = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN);
        return new CdMmdHeader(Byte.toUnsignedInt(bytes.get()), Byte.toUnsignedInt(bytes.get()),
                Integer.toUnsignedLong(bytes.getInt()), Short.toUnsignedInt(bytes.getShort()),
                Integer.toUnsignedLong(bytes.getInt()), Integer.toUnsignedLong(bytes.getInt()),
                Integer.toUnsignedLong(bytes.getInt()));
    }

    private static boolean unsigned32(long value) { return value >= 0 && value <= 0xffffffffL; }

    /** The 68000 bus uses 24 bits; the raw encoded long remains available above. */
    public static int nativeAddress(long encoded) {
        if (!unsigned32(encoded)) {
            throw new IllegalArgumentException("Not an unsigned 32-bit address");
        }
        return (int) (encoded & 0xffffff);
    }

    /** RunMMD tests the complete encoded entry long before copy, interrupts and flags. */
    public boolean hasEntry() { return entry != 0; }
    public boolean requestsSubCpuWordRam() { return (flags & 0x40) != 0; }
    public boolean appliesSubCpuWordRamRequest() { return hasEntry() && requestsSubCpuWordRam(); }

    /** RunMMD .CopyFile (IPX.asm:1334-1340) uses an unsigned DBF counter. */
    public int declaredCopyBytes() {
        return hasEntry() && origin != 0 ? (sizeCounter + 1) * 4 : 0;
    }
}
