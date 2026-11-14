package com.openggf.data.cd.chd;

/**
 * Regenerates the MODE1 sync pattern and the ECMA-130 (second edition, Annex A) P and Q Reed-Solomon
 * parity bytes that CHD CD codecs strip from sectors whose parity verified at compression time. The EDC
 * is stored, never regenerated.
 */
public final class CdEcc {
    private static final int[] F = new int[256];
    private static final int[] B = new int[256];
    private static final byte[] SYNC = {0, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0};

    static {
        for (int i = 0; i < 256; i++) {
            int j = (i << 1) ^ ((i & 0x80) != 0 ? 0x11D : 0);
            F[i] = j & 0xFF;
            B[i ^ F[i]] = i;
        }
    }

    private CdEcc() { }

    /** Writes the sync pattern and recomputes P then Q parity for the 2352-byte sector at {@code offset}. */
    public static void regenerate(byte[] frame, int offset) {
        System.arraycopy(SYNC, 0, frame, offset, SYNC.length);
        block(frame, offset + 12, 86, 24, 2, 86, offset + 0x81C);
        block(frame, offset + 12, 52, 43, 86, 88, offset + 0x8C8);
    }

    private static void block(byte[] frame, int source, int majorCount, int minorCount,
                              int majorMultiplier, int minorIncrement, int destination) {
        int size = majorCount * minorCount;
        for (int major = 0; major < majorCount; major++) {
            int index = (major >> 1) * majorMultiplier + (major & 1);
            int a = 0;
            int b = 0;
            for (int minor = 0; minor < minorCount; minor++) {
                int value = frame[source + index] & 0xFF;
                index += minorIncrement;
                if (index >= size) {
                    index -= size;
                }
                a ^= value;
                b ^= value;
                a = F[a];
            }
            a = B[F[a] ^ b];
            frame[destination + major] = (byte) a;
            frame[destination + major + majorCount] = (byte) (a ^ b);
        }
    }
}
