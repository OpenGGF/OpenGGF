package com.openggf.graphics.color;

import com.openggf.level.Palette;

public final class DisplayColorConverter {
    private static final int[] MD_ANALOG_RAMP = {0, 18, 42, 72, 126, 162, 202, 238};

    private DisplayColorConverter() {
    }

    public static int[] toRgbBytes(Palette.Color color, DisplayColorProfile profile) {
        int r = Byte.toUnsignedInt(color.r);
        int g = Byte.toUnsignedInt(color.g);
        int b = Byte.toUnsignedInt(color.b);

        return switch (profile) {
            case RAW_RGB -> new int[] {r, g, b};
            case MD_ANALOG -> new int[] {analog(r), analog(g), analog(b)};
            case NTSC_SOFT -> ntscSoft(r, g, b);
        };
    }

    private static int analog(int value) {
        int level = Math.min(7, Math.max(0, (value * 7 + 127) / 255));
        return MD_ANALOG_RAMP[level];
    }

    private static int[] ntscSoft(int r, int g, int b) {
        int ar = analog(r);
        int ag = analog(g);
        int ab = analog(b);
        int luma = Math.round(ar * 0.299f + ag * 0.587f + ab * 0.114f);
        return new int[] {
                blend(ar, luma),
                blend(ag, luma),
                blend(ab, luma)
        };
    }

    private static int blend(int channel, int luma) {
        return Math.round(channel * 0.75f + luma * 0.25f);
    }
}
