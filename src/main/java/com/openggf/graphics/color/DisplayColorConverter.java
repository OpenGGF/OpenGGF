package com.openggf.graphics.color;

import java.nio.ByteBuffer;

public final class DisplayColorConverter {
    private static final int[] MD_ANALOG_RAMP = {0, 18, 42, 72, 126, 162, 202, 238};

    private DisplayColorConverter() {
    }

    public static int[] toRgbBytes(int r, int g, int b, DisplayColorProfile profile) {
        return toRgbBytes(r, g, b, profile, DisplayColorExpansion.FULL);
    }

    public static int[] toRgbBytes(int r, int g, int b, DisplayColorProfile profile,
                                   DisplayColorExpansion expansion) {
        int packed = expand(toPackedRgb(r, g, b, profile), expansion);
        return new int[] {(packed >>> 16) & 0xFF, (packed >>> 8) & 0xFF, packed & 0xFF};
    }

    /**
     * Writes the converted RGB channels directly into a caller-owned buffer.
     * The buffer position advances by three bytes and no temporary array is allocated.
     */
    public static void writeRgbBytes(int r, int g, int b, DisplayColorProfile profile, ByteBuffer target) {
        writeRgbBytes(r, g, b, profile, DisplayColorExpansion.FULL, target);
    }

    public static void writeRgbBytes(int r, int g, int b, DisplayColorProfile profile,
                                     DisplayColorExpansion expansion, ByteBuffer target) {
        int packed = expand(toPackedRgb(r, g, b, profile), expansion);
        target.put((byte) (packed >>> 16));
        target.put((byte) (packed >>> 8));
        target.put((byte) packed);
    }

    /** Writes converted RGB channels at {@code offset} without changing adjacent RGBA data. */
    public static void writeRgbBytes(int r, int g, int b, DisplayColorProfile profile,
                                     int[] target, int offset) {
        writeRgbBytes(r, g, b, profile, DisplayColorExpansion.FULL, target, offset);
    }

    public static void writeRgbBytes(int r, int g, int b, DisplayColorProfile profile,
                                     DisplayColorExpansion expansion, int[] target, int offset) {
        int packed = expand(toPackedRgb(r, g, b, profile), expansion);
        target[offset] = (packed >>> 16) & 0xFF;
        target[offset + 1] = (packed >>> 8) & 0xFF;
        target[offset + 2] = packed & 0xFF;
    }

    /** Packed 0xRRGGBB exactly as palette uploads display it: profile first, then expansion. */
    public static int toPackedRgb(int r, int g, int b, DisplayColorProfile profile,
                                  DisplayColorExpansion expansion) {
        return expand(toPackedRgb(r, g, b, profile), expansion);
    }

    private static int toPackedRgb(int r, int g, int b, DisplayColorProfile profile) {
        return switch (profile) {
            case RAW_RGB -> pack(r, g, b);
            case MD_ANALOG -> pack(analog(r), analog(g), analog(b));
            case NTSC_SOFT -> ntscSoftPacked(r, g, b);
        };
    }

    private static int expand(int packed, DisplayColorExpansion expansion) {
        if (expansion != DisplayColorExpansion.LIMITED) return packed;
        // Forward output gain, not inverse inference of a ROM colour code. Canonical
        // 3-bit Full values map exactly to GPGX's normal 34*n ramp; arbitrary RGB
        // colours retain their intermediate levels and are bounded by 238 too.
        return pack(limited((packed >>> 16) & 255), limited((packed >>> 8) & 255), limited(packed & 255));
    }

    private static int limited(int channel) { return (channel * 238 + 127) / 255; }

    private static int analog(int value) {
        int level = Math.min(7, Math.max(0, (value * 7 + 127) / 255));
        return MD_ANALOG_RAMP[level];
    }

    private static int ntscSoftPacked(int r, int g, int b) {
        int ar = analog(r);
        int ag = analog(g);
        int ab = analog(b);
        int luma = Math.round(ar * 0.299f + ag * 0.587f + ab * 0.114f);
        return pack(blend(ar, luma), blend(ag, luma), blend(ab, luma));
    }

    private static int blend(int channel, int luma) {
        return Math.round(channel * 0.75f + luma * 0.25f);
    }

    private static int pack(int r, int g, int b) {
        return (r << 16) | (g << 8) | b;
    }
}
