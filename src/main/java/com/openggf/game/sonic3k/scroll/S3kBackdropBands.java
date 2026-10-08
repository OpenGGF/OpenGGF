package com.openggf.game.sonic3k.scroll;

import com.openggf.level.render.ZonePictureSource;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

/**
 * Static parallax bands for S3K zone backdrops in mod scenes. Each zone walks its scroll
 * handler's own deformation height table from background row 0, exactly as
 * {@code ApplyDeformation} does ({@code $8000|n} = n one-line bands, {@code $7FFF} = the
 * rest), and gives every table word its closed-form rate: the fraction of camera X it
 * multiplies and any per-frame drift the deform routine adds. Rows past the table keep the
 * last word, as the handlers' fallback does. Adjacent bands with equal rates are merged.
 * Engine-internal.
 */
public final class S3kBackdropBands {
    /** One 16.16 pixel in the drift accumulators. */
    private static final double SUBPIXELS = 0x10000;

    private S3kBackdropBands() {
    }

    /** One table word: fraction of camera X and drift in pixels per frame. */
    private record Rate(double speed, double drift) {
    }

    /**
     * {@code AIZ1_Deform} (s3.asm:70272-70330) with {@code AIZ1_DeformArray}. With
     * {@code base = (camX - $1300) / 32}: words 0-5 are {@code (11 - 2i) * base / 2} plus
     * {@code 6 - i} times the {@code HScroll_table+$3C} accumulator ({@code +$2000} a frame),
     * word 6 is {@code base}, words 7-19 the one-line ocean ramp {@code base * (8 + k) / 8},
     * and words 20-24 {@code base * 14, 16, 18, 20, 18}.
     */
    public static List<ZonePictureSource.Band> aiz1(int imageHeight) {
        int[] tail = {14, 16, 18, 20, 18};
        return walk(SwScrlAiz.AIZ1_DEFORM_HEIGHTS, true, 0, imageHeight, i -> {
            if (i <= 5) {
                return new Rate((11 - 2 * i) / 64.0, (6 - i) * 0x2000 / SUBPIXELS);
            }
            if (i == 6) {
                return new Rate(1 / 32.0, 0);
            }
            if (i <= 19) {
                return new Rate((i + 2) / 256.0, 0);
            }
            return new Rate(tail[Math.min(i - 20, tail.length - 1)] / 32.0, 0);
        });
    }

    /**
     * {@code AIZ2_Deform}/{@code AIZ2_BGDeformMake} (s3.asm:70805-70842): seven levels
     * {@code (32 + 3k) / 64} of camera X scattered by {@code AIZ2_SPEED_MAP} over
     * {@code AIZ2_BGDeformArray}.
     */
    public static List<ZonePictureSource.Band> aiz2(int imageHeight) {
        int[] map = SwScrlAiz.AIZ2_SPEED_MAP;
        return walk(SwScrlAiz.AIZ2_DEFORM_HEIGHTS, false, 0, imageHeight,
                i -> new Rate((32 + 3 * map[Math.min(i, map.length - 1)]) / 64.0, 0));
    }

    /**
     * {@code HCZ1_Deform} (sonic3k.asm:105801-105960) held in its below-the-waterline state
     * (camera at least {@code $80} below the {@code $610} equilibrium): cave words 0-6 are
     * {@code (8 - k) / 32} of camera X mirrored in words 7-12; the 192 one-line waterline words
     * are a ramp from 1 down by {@code 1/128} a line for the upper 96 and the slowest cave word
     * for the lower 96, which also fills the rest.
     */
    public static List<ZonePictureSource.Band> hcz1BelowWaterline(int imageHeight) {
        return walk(SwScrlHcz.HCZ1_DEFORM_HEIGHTS, true, 0, imageHeight, i -> {
            if (i <= 6) {
                return new Rate((8 - i) / 32.0, 0);
            }
            if (i < SwScrlHcz.HCZ1_WATERLINE_START) {
                return new Rate((i - 4) / 32.0, 0);
            }
            if (i < SwScrlHcz.HCZ1_WATERLINE_MIDPOINT) {
                return new Rate((128 - (i - SwScrlHcz.HCZ1_WATERLINE_START)) / 128.0, 0);
            }
            return new Rate(2 / 32.0, 0);
        });
    }

    /**
     * {@code LBZ1_Deform} with {@code LBZ1_BGDeformArray} (sonic3k.asm:111291), entered at
     * {@code HScroll_table+$008}: {@code camX/16} for the sky, then {@code camX/8} rising by
     * {@code camX/128} a band. The ROM's constant pixel offsets (+10, +4, -2, +7) only shift
     * phase and are left out.
     */
    public static List<ZonePictureSource.Band> lbz1(int imageHeight) {
        return walk(SwScrlLbz.LBZ1_BG_DEFORM, false, 4, imageHeight, i -> switch (i) {
            case 4 -> new Rate(1 / 16.0, 0);
            case 5 -> new Rate(16 / 128.0, 0);
            case 6 -> new Rate(17 / 128.0, 0);
            case 7 -> new Rate(18 / 128.0, 0);
            default -> new Rate(19 / 128.0, 0);
        });
    }

    /**
     * {@code sub_57A60}'s cloud fan (sonic3k.asm:116385-116708) with
     * {@code SSZ1_BGDeformArray}: the same word assignments as
     * {@link SwScrlSsz#cloudParameters}, with {@code camX/64} plus one drift unit for the
     * first word and {@code camX/32} plus one drift unit as the step. The drift unit is the
     * {@code $500} the accumulator gains each frame.
     */
    public static List<ZonePictureSource.Band> ssz1Clouds(int imageHeight) {
        // Per byte offset from HScroll_table+$004: {speed in camX/64, drift in units}.
        double[][] words = new double[SwScrlSsz.SCROLL_WORD_COUNT][];
        double[] value = {1, 1};
        double[] step = {2, 1};
        assign(words, value, 0x00, 0x06, 0x0A, 0x14);
        add(value, step, 1);
        assign(words, value, 0x08, 0x0E);
        add(value, step, 1);
        assign(words, value, 0x04, 0x0C, 0x12, 0x16, 0x3A);
        add(value, step, 1);
        assign(words, value, 0x02, 0x10, 0x18, 0x38);
        add(value, step, 1);
        assign(words, value, 0x1A, 0x36);
        add(value, step, 1);
        assign(words, value, 0x1C, 0x34);
        add(value, step, 1);
        assign(words, value, 0x1E, 0x32);
        add(value, step, 1);
        assign(words, value, 0x20, 0x30);
        add(value, step, 1);
        assign(words, value, 0x22, 0x2E);
        add(value, step, 2);
        assign(words, value, 0x24, 0x2C);
        add(value, step, 2.5);
        assign(words, value, 0x26, 0x2A);
        add(value, step, 2.5);
        assign(words, value, 0x28);
        int start = SwScrlSsz.DEFORM_TABLE_START_INDEX;
        return walk(SwScrlSsz.SSZ1_BG_DEFORM, false, start, imageHeight, i -> {
            double[] word = words[Math.min(i, words.length - 1)];
            if (word == null) {
                throw new IllegalStateException("SSZ1 cloud word " + i + " is never written");
            }
            return new Rate(word[0] / 64.0, word[1] * SwScrlSsz.CLOUD_DRIFT_PER_FRAME / SUBPIXELS);
        });
    }

    private static void assign(double[][] words, double[] value, int... byteOffsets) {
        for (int offset : byteOffsets) {
            words[SwScrlSsz.DEFORM_TABLE_START_INDEX + (offset >> 1)] = value.clone();
        }
    }

    private static void add(double[] value, double[] step, double times) {
        value[0] += step[0] * times;
        value[1] += step[1] * times;
    }

    /**
     * Walks a deformation height table from row 0. {@code flagged} tables honour the
     * {@code $8000} one-line flag ({@code ApplyDeformation}'s flagged form); value indices
     * start at {@code tableStart} and advance one per band or per line.
     */
    static List<ZonePictureSource.Band> walk(int[] heights, boolean flagged, int tableStart, int imageHeight,
            IntFunction<Rate> rateAt) {
        List<ZonePictureSource.Band> bands = new ArrayList<>();
        int y = 0;
        int index = tableStart;
        Rate last = rateAt.apply(index);
        for (int h = 0; h < heights.length && y < imageHeight; h++) {
            int raw = heights[h];
            boolean perLine = flagged && (raw & 0x8000) != 0;
            int count = raw == 0x7FFF ? imageHeight - y : Math.max(1, raw & 0x7FFF);
            if (perLine) {
                for (int line = 0; line < count && y < imageHeight; line++) {
                    last = rateAt.apply(index++);
                    y = append(bands, y, 1, last);
                }
            } else {
                last = rateAt.apply(index++);
                y = append(bands, y, Math.min(count, imageHeight - y), last);
            }
        }
        if (y < imageHeight) {
            append(bands, y, imageHeight - y, last);
        }
        return List.copyOf(bands);
    }

    private static int append(List<ZonePictureSource.Band> bands, int top, int height, Rate rate) {
        if (!bands.isEmpty()) {
            ZonePictureSource.Band previous = bands.get(bands.size() - 1);
            if (previous.speed() == rate.speed() && previous.drift() == rate.drift()) {
                bands.set(bands.size() - 1, new ZonePictureSource.Band(previous.top(), previous.height() + height,
                        rate.speed(), rate.drift()));
                return top + height;
            }
        }
        bands.add(new ZonePictureSource.Band(top, height, rate.speed(), rate.drift()));
        return top + height;
    }
}
