package com.openggf.ghost;

/** Fixed 7-byte wire/file layout for {@link GhostFrame} (main spec §7). */
public final class GhostFrameCodec {
    public static final int BYTES = 7;

    private GhostFrameCodec() {
    }

    public static void encode(GhostFrame f, byte[] out, int off) {
        out[off] = (byte) (f.x() >>> 8);
        out[off + 1] = (byte) f.x();
        out[off + 2] = (byte) (f.y() >>> 8);
        out[off + 3] = (byte) f.y();
        out[off + 4] = (byte) f.mappingFrame();
        out[off + 5] = (byte) ((f.hFlip() ? 0x01 : 0)
                | (f.vFlip() ? 0x02 : 0) | (f.finished() ? 0x04 : 0));
        out[off + 6] = (byte) ((f.priorityBucket() & 0x07)
                | (f.highPriority() ? 0x08 : 0));
    }

    public static GhostFrame decode(byte[] in, int off) {
        int x = ((in[off] & 0xFF) << 8) | (in[off + 1] & 0xFF);
        int y = ((in[off + 2] & 0xFF) << 8) | (in[off + 3] & 0xFF);
        int mapping = in[off + 4] & 0xFF;
        int flags = in[off + 5] & 0xFF;
        int layer = in[off + 6] & 0xFF;
        return new GhostFrame(x, y, mapping, (flags & 0x01) != 0,
                (flags & 0x02) != 0, (flags & 0x04) != 0,
                layer & 0x07, (layer & 0x08) != 0);
    }
}
