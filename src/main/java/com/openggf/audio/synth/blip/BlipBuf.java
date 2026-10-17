/*
 * blip_buf 1.1.0. http://www.slack.net/~ant/
 * Library Copyright (C) 2003-2009 Shay Green.
 * Modified for Genesis Plus GX by EkeEke: assertions disabled, multiple
 * time-frame support fixed (m->avail removed), stereo buffers added.
 * Java port Copyright (C) 2026 the OpenGGF contributors.
 * Modified 2026-10 by the OpenGGF contributors: translated from C to Java;
 * only the stereo (non-BLIP_MONO, non-BLIP_INVERT) configuration with
 * BLIP_ASSERT undefined is ported; blip_mix_samples, blip_add_delta_fast and
 * blip_clocks_needed are not ported; state copy helpers and value equality
 * added.
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or (at
 * your option) any later version. This library is distributed in the hope
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details. You should have
 * received a copy of the GNU Lesser General Public License along with this
 * module; if not, write to the Free Software Foundation, Inc., 51 Franklin
 * Street, Fifth Floor, Boston, MA 02110-1301 USA
 */
package com.openggf.audio.synth.blip;

import java.util.Arrays;
import java.util.Objects;

/**
 * Stereo band-limited delta buffer: Shay Green's blip_buf 1.1.0 as shipped,
 * modified, in Genesis Plus GX ({@code core/sound/blip_buf.c}). The source of
 * this port is exactly that file at BizHawk {@code 427556b5ef3a} (GPGX
 * {@code 051d430d}), sha256 {@code f807ee67...0fc80461}; see the package
 * documentation. {@code fixed_t} is the 64-bit unsigned type with
 * {@code pre_shift = 32}, as on every 64-bit host, so a Java {@code long}
 * with unsigned shifts carries it exactly. C {@code int} arithmetic
 * (including the wrap-around of the step products and the integrator) is
 * Java {@code int} arithmetic.
 */
public final class BlipBuf {

    // blip_buf.c:38-66 (64-bit fixed_t branch).
    private static final int PRE_SHIFT = 32;
    private static final int TIME_BITS = PRE_SHIFT + 20;
    private static final long TIME_UNIT = 1L << TIME_BITS;
    private static final int BASS_SHIFT = 9;
    private static final int END_FRAME_EXTRA = 2;
    private static final int HALF_WIDTH = 8;
    private static final int BUF_EXTRA = HALF_WIDTH * 2 + END_FRAME_EXTRA;
    private static final int PHASE_BITS = 5;
    private static final int PHASE_COUNT = 1 << PHASE_BITS;
    private static final int DELTA_BITS = 15;
    private static final int DELTA_UNIT = 1 << DELTA_BITS;
    private static final int FRAC_BITS = TIME_BITS - PRE_SHIFT;
    private static final int PHASE_SHIFT = FRAC_BITS - PHASE_BITS;
    /** blip_buf.h {@code blip_max_ratio}. */
    private static final int MAX_RATIO = 1 << 20;
    private static final int MAX_SAMPLE = 32767;
    private static final int MIN_SAMPLE = -32768;

    /**
     * {@code bl_step[phase_count + 1][half_width]} (blip_buf.c:423-457),
     * Sinc_Generator(0.9, 0.55, 4.5), flattened row-major so that the C
     * pointer arithmetic {@code rev[k - half_width]} reads the previous row.
     */
    private static final short[] BL_STEP = {
            43, -115, 350, -488, 1136, -914, 5861, 21022,
            44, -118, 348, -473, 1076, -799, 5274, 21001,
            45, -121, 344, -454, 1011, -677, 4706, 20936,
            46, -122, 336, -431, 942, -549, 4156, 20829,
            47, -123, 327, -404, 868, -418, 3629, 20679,
            47, -122, 316, -375, 792, -285, 3124, 20488,
            47, -120, 303, -344, 714, -151, 2644, 20256,
            46, -117, 289, -310, 634, -17, 2188, 19985,
            46, -114, 273, -275, 553, 117, 1758, 19675,
            44, -108, 255, -237, 471, 247, 1356, 19327,
            43, -103, 237, -199, 390, 373, 981, 18944,
            42, -98, 218, -160, 310, 495, 633, 18527,
            40, -91, 198, -121, 231, 611, 314, 18078,
            38, -84, 178, -81, 153, 722, 22, 17599,
            36, -76, 157, -43, 80, 824, -241, 17092,
            34, -68, 135, -3, 8, 919, -476, 16558,
            32, -61, 115, 34, -60, 1006, -683, 16001,
            29, -52, 94, 70, -123, 1083, -862, 15422,
            27, -44, 73, 106, -184, 1152, -1015, 14824,
            25, -36, 53, 139, -239, 1211, -1142, 14210,
            22, -27, 34, 170, -290, 1261, -1244, 13582,
            20, -20, 16, 199, -335, 1301, -1322, 12942,
            18, -12, -3, 226, -375, 1331, -1376, 12293,
            15, -4, -19, 250, -410, 1351, -1408, 11638,
            13, 3, -35, 272, -439, 1361, -1419, 10979,
            11, 9, -49, 292, -464, 1362, -1410, 10319,
            9, 16, -63, 309, -483, 1354, -1383, 9660,
            7, 22, -75, 322, -496, 1337, -1339, 9005,
            6, 26, -85, 333, -504, 1312, -1280, 8355,
            4, 31, -94, 341, -507, 1278, -1205, 7713,
            3, 35, -102, 347, -506, 1238, -1119, 7082,
            1, 40, -110, 350, -499, 1190, -1021, 6464,
            0, 43, -115, 350, -488, 1136, -914, 5861};

    private final int size;
    private long factor;
    private long offset;
    private final int[] integrator = new int[2];
    private final int[] left;
    private final int[] right;

    /** {@code blip_new(size)} (blip_buf.c:129-160): factor {@code time_unit / blip_max_ratio}, then cleared. */
    public BlipBuf(int size) {
        if (size < 0) {
            throw new IllegalArgumentException("size must be non-negative");
        }
        this.size = size;
        left = new int[size + BUF_EXTRA];
        right = new int[size + BUF_EXTRA];
        factor = TIME_UNIT / MAX_RATIO;
        clear();
    }

    /** {@code blip_set_rates} (blip_buf.c:179-196): factor rounded up to the next integer. */
    public void setRates(double clockRate, double sampleRate) {
        double exact = TIME_UNIT * sampleRate / clockRate;
        factor = (long) exact;
        if (factor < exact) {
            factor++;
        }
    }

    /** {@code blip_clear} (blip_buf.c:198-216). */
    public void clear() {
        offset = factor / 2;
        integrator[0] = 0;
        integrator[1] = 0;
        Arrays.fill(left, 0);
        Arrays.fill(right, 0);
    }

    /** {@code blip_end_frame} (blip_buf.c:234-242); {@code t} is a C {@code unsigned}. */
    public void endFrame(int t) {
        offset += Integer.toUnsignedLong(t) * factor;
    }

    /** {@code blip_samples_avail} (blip_buf.c:244-247). */
    public int samplesAvail() {
        return (int) (offset >>> TIME_BITS);
    }

    /** {@code remove_samples} (blip_buf.c:249-266). */
    private void removeSamples(int count) {
        int remain = (int) (offset >>> TIME_BITS) + BUF_EXTRA - count;
        offset -= count * TIME_UNIT;
        System.arraycopy(left, count, left, 0, remain);
        Arrays.fill(left, remain, remain + count, 0);
        System.arraycopy(right, count, right, 0, remain);
        Arrays.fill(right, remain, remain + count, 0);
    }

    /**
     * {@code blip_read_samples} (blip_buf.c:268-330) with BLIP_ASSERT undefined,
     * so {@code count} is not clamped to the available samples. Writes
     * {@code count} interleaved stereo samples at {@code out[outOffset]}.
     */
    public int readSamples(short[] out, int outOffset, int count) {
        Objects.requireNonNull(out, "out");
        int sum = integrator[0];
        int sum2 = integrator[1];
        int o = outOffset;
        for (int i = 0; i < count; i++) {
            int s = sum >> DELTA_BITS;
            sum += left[i];
            s = clamp(s);
            out[o++] = (short) s;
            sum -= s << (DELTA_BITS - BASS_SHIFT);

            s = sum2 >> DELTA_BITS;
            sum2 += right[i];
            s = clamp(s);
            out[o++] = (short) s;
            sum2 -= s << (DELTA_BITS - BASS_SHIFT);
        }
        integrator[0] = sum;
        integrator[1] = sum2;
        removeSamples(count);
        return count;
    }

    private static int clamp(int n) {
        if (n > MAX_SAMPLE) {
            return MAX_SAMPLE;
        }
        return n < MIN_SAMPLE ? MIN_SAMPLE : n;
    }

    /**
     * Stereo {@code blip_add_delta} (blip_buf.c:467-617). {@code time} is a C
     * {@code unsigned}. The C file's equal-delta branch computes exactly the
     * per-side values of its general branch, so one path serves both.
     */
    public void addDelta(int time, int deltaL, int deltaR) {
        if ((deltaL | deltaR) == 0) {
            return;
        }
        int fixed = (int) ((Integer.toUnsignedLong(time) * factor + offset) >>> PRE_SHIFT);
        int phase = (fixed >>> PHASE_SHIFT) & (PHASE_COUNT - 1);
        int in = phase * HALF_WIDTH;
        int rev = (PHASE_COUNT - phase) * HALF_WIDTH;
        int interp = (fixed >>> (PHASE_SHIFT - DELTA_BITS)) & (DELTA_UNIT - 1);
        int pos = fixed >>> FRAC_BITS;

        int delta = (deltaL * interp) >> DELTA_BITS;
        addStep(left, pos, in, rev, deltaL - delta, delta);
        delta = (deltaR * interp) >> DELTA_BITS;
        addStep(right, pos, in, rev, deltaR - delta, delta);
    }

    private static void addStep(int[] out, int pos, int in, int rev, int deltaFirst, int delta) {
        for (int k = 0; k < HALF_WIDTH; k++) {
            out[pos + k] += BL_STEP[in + k] * deltaFirst + BL_STEP[in + HALF_WIDTH + k] * delta;
        }
        for (int k = 0; k < HALF_WIDTH; k++) {
            int j = HALF_WIDTH - 1 - k;
            out[pos + HALF_WIDTH + k] += BL_STEP[rev + j] * deltaFirst + BL_STEP[rev + j - HALF_WIDTH] * delta;
        }
    }

    // ---------------------------------------------------------------- state

    /** Immutable copy of every mutable field: factor, offset, both integrators and both sample buffers. */
    public record State(int size, long factor, long offset, int integratorLeft, int integratorRight,
            int[] left, int[] right) {
        public State {
            left = left.clone();
            right = right.clone();
        }

        @Override
        public int[] left() {
            return left.clone();
        }

        @Override
        public int[] right() {
            return right.clone();
        }

        @Override
        public boolean equals(Object candidate) {
            return candidate instanceof State other
                    && size == other.size
                    && factor == other.factor
                    && offset == other.offset
                    && integratorLeft == other.integratorLeft
                    && integratorRight == other.integratorRight
                    && Arrays.equals(left, other.left)
                    && Arrays.equals(right, other.right);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(size, factor, offset, integratorLeft, integratorRight);
            result = 31 * result + Arrays.hashCode(left);
            return 31 * result + Arrays.hashCode(right);
        }
    }

    public State captureState() {
        return new State(size, factor, offset, integrator[0], integrator[1], left, right);
    }

    public void restoreState(State state) {
        Objects.requireNonNull(state, "state");
        if (state.size != size) {
            throw new IllegalArgumentException("blip state size " + state.size + " does not match " + size);
        }
        factor = state.factor;
        offset = state.offset;
        integrator[0] = state.integratorLeft;
        integrator[1] = state.integratorRight;
        System.arraycopy(state.left, 0, left, 0, left.length);
        System.arraycopy(state.right, 0, right, 0, right.length);
    }

    /** Copies every mutable field from {@code source} without allocating; sizes must match. */
    public void copyFrom(BlipBuf source) {
        if (source.size != size) {
            throw new IllegalArgumentException("blip size " + source.size + " does not match " + size);
        }
        factor = source.factor;
        offset = source.offset;
        integrator[0] = source.integrator[0];
        integrator[1] = source.integrator[1];
        System.arraycopy(source.left, 0, left, 0, left.length);
        System.arraycopy(source.right, 0, right, 0, right.length);
    }
}
