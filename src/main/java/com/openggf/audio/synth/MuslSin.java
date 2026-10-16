/*
 * Java port of musl libc's double-precision sin (src/math/sin.c, __sin.c,
 * __cos.c and the non-large paths of __rem_pio2.c) as linked into the BizHawk
 * 2.11 Genesis Plus GX waterbox core.
 *
 * origin: FreeBSD /usr/src/lib/msun/src/s_sin.c, k_sin.c, k_cos.c, e_rem_pio2.c
 * ====================================================
 * Copyright (C) 1993 by Sun Microsystems, Inc. All rights reserved.
 *
 * Developed at SunPro, a Sun Microsystems, Inc. business.
 * Permission to use, copy, modify, and distribute this
 * software is freely granted, provided that this notice
 * is preserved.
 * ====================================================
 * __rem_pio2: Optimized by Bruce D. Evans.
 *
 * musl: Copyright © 2005-2020 Rich Felker, et al.
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
 * CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 * Modified 2026-10 by the OpenGGF contributors: translated from C to Java;
 * __rem_pio2_large (|x| >= 2^20 * pi/2) is not ported and is rejected.
 */
package com.openggf.audio.synth;

/**
 * The {@code sin} the BizHawk 2.11 GPGX core calls for its three-band
 * equaliser cutoffs ({@code init_3band_state}, inlined into
 * {@code audio_reset} of the stock core {@code gpgx.wbx.zst} sha256
 * {@code c4231296...b220d12}, the TraceChaser source-lock "stock" core of
 * BizHawk commit {@code 427556b5}). That core links musl 1.2.0 from
 * {@code waterbox/musl} (BizHawk submodule commit {@code 2063abc4}):
 * {@code sin.c} sha256 {@code 24e53a9d...}, {@code __sin.c} {@code f3a37ef9...},
 * {@code __cos.c} {@code 3f0f774a...}, {@code __rem_pio2.c} {@code 938329e0...}.
 * The stock core's {@code sin}, {@code __sin}, {@code __cos} and
 * {@code __rem_pio2} machine code follows these sources' operation order and
 * constants, and executing that machine code for every 16-bit cutoff gives
 * the same bits as this port (see the native-compatible PCM vector README).
 * {@code double_t} is {@code double} on x86-64 ({@code FLT_EVAL_METHOD 0}),
 * so Java's IEEE double arithmetic reproduces it. Host libms differ: glibc's
 * {@code sin} is one ulp away for 880 Hz at 44.1 kHz.
 */
final class MuslSin {

    // __sin.c
    private static final double S1 = -1.66666666666666324348e-01;
    private static final double S2 = 8.33333333332248946124e-03;
    private static final double S3 = -1.98412698298579493134e-04;
    private static final double S4 = 2.75573137070700676789e-06;
    private static final double S5 = -2.50507602534068634195e-08;
    private static final double S6 = 1.58969099521155010221e-10;
    // __cos.c
    private static final double C1 = 4.16666666666666019037e-02;
    private static final double C2 = -1.38888888888741095749e-03;
    private static final double C3 = 2.48015872894767294178e-05;
    private static final double C4 = -2.75573143513906633035e-07;
    private static final double C5 = 2.08757232129817482790e-09;
    private static final double C6 = -1.13596475577881948265e-11;
    // __rem_pio2.c (EPS = DBL_EPSILON)
    private static final double TOINT = 1.5 / 0x1p-52;
    private static final double PIO4 = 0x1.921fb54442d18p-1;
    private static final double INVPIO2 = 6.36619772367581382433e-01;
    private static final double PIO2_1 = 1.57079632673412561417e+00;
    private static final double PIO2_1T = 6.07710050650619224932e-11;
    private static final double PIO2_2 = 6.07710050630396597660e-11;
    private static final double PIO2_2T = 2.02226624879595063154e-21;
    private static final double PIO2_3 = 2.02226624871116645580e-21;
    private static final double PIO2_3T = 8.47842766036889956997e-32;

    private MuslSin() {
    }

    private static int highWord(double x) {
        return (int) (Double.doubleToRawLongBits(x) >>> 32);
    }

    /** sin.c {@code sin}. */
    static double sin(double x) {
        int ix = highWord(x) & 0x7fffffff;
        if (ix <= 0x3fe921fb) {
            if (ix < 0x3e500000) {
                return x; // |x| < 2^-26; FORCE_EVAL only raises flags
            }
            return kernelSin(x, 0.0, 0);
        }
        if (ix >= 0x7ff00000) {
            return x - x;
        }
        double[] y = new double[2];
        int n = remPio2(x, y);
        return switch (n & 3) {
            case 0 -> kernelSin(y[0], y[1], 1);
            case 1 -> kernelCos(y[0], y[1]);
            case 2 -> -kernelSin(y[0], y[1], 1);
            default -> -kernelCos(y[0], y[1]);
        };
    }

    /** __sin.c {@code __sin}. */
    private static double kernelSin(double x, double y, int iy) {
        double z = x * x;
        double w = z * z;
        double r = S2 + z * (S3 + z * S4) + z * w * (S5 + z * S6);
        double v = z * x;
        if (iy == 0) {
            return x + v * (S1 + z * r);
        }
        return x - ((z * (0.5 * y - v * r) - y) - v * S1);
    }

    /** __cos.c {@code __cos}. */
    private static double kernelCos(double x, double y) {
        double z = x * x;
        double w = z * z;
        double r = z * (C1 + z * (C2 + z * C3)) + w * w * (C4 + z * (C5 + z * C6));
        double hz = 0.5 * z;
        w = 1.0 - hz;
        return w + (((1.0 - w) - hz) + (z * r - x * y));
    }

    /** __rem_pio2.c {@code __rem_pio2} for {@code |x| < 2^20 * pi/2}; caller handles {@code |x| ~<= pi/4}. */
    private static int remPio2(double x, double[] y) {
        long bits = Double.doubleToRawLongBits(x);
        boolean sign = bits < 0;
        int ix = (int) (bits >>> 32) & 0x7fffffff;
        boolean medium = false;
        if (ix <= 0x400f6a7a) {
            if ((ix & 0xfffff) == 0x921fb) {
                medium = true;
            } else if (ix <= 0x4002d97c) {
                return fixedRound(x, y, sign ? -1 : 1);
            } else {
                return fixedRound(x, y, sign ? -2 : 2);
            }
        } else if (ix <= 0x401c463b) {
            if (ix <= 0x4015fdbc) {
                if (ix == 0x4012d97c) {
                    medium = true;
                } else {
                    return fixedRound(x, y, sign ? -3 : 3);
                }
            } else if (ix == 0x401921fb) {
                medium = true;
            } else {
                return fixedRound(x, y, sign ? -4 : 4);
            }
        } else if (ix < 0x413921fb) {
            medium = true;
        }
        if (!medium) {
            if (ix >= 0x7ff00000) {
                y[0] = y[1] = x - x;
                return 0;
            }
            throw new IllegalArgumentException("__rem_pio2_large is not ported: |x| = " + Math.abs(x));
        }
        double fn = x * INVPIO2 + TOINT - TOINT;
        int n = (int) fn;
        double r = x - fn * PIO2_1;
        double w = fn * PIO2_1T;
        if (r - w < -PIO4) {
            n--;
            fn--;
            r = x - fn * PIO2_1;
            w = fn * PIO2_1T;
        } else if (r - w > PIO4) {
            n++;
            fn++;
            r = x - fn * PIO2_1;
            w = fn * PIO2_1T;
        }
        y[0] = r - w;
        int ey = (int) (Double.doubleToRawLongBits(y[0]) >>> 52) & 0x7ff;
        int ex = ix >>> 20;
        if (ex - ey > 16) {
            double t = r;
            w = fn * PIO2_2;
            r = t - w;
            w = fn * PIO2_2T - ((t - r) - w);
            y[0] = r - w;
            ey = (int) (Double.doubleToRawLongBits(y[0]) >>> 52) & 0x7ff;
            if (ex - ey > 49) {
                t = r;
                w = fn * PIO2_3;
                r = t - w;
                w = fn * PIO2_3T - ((t - r) - w);
                y[0] = r - w;
            }
        }
        y[1] = (r - y[0]) - w;
        return n;
    }

    /** The {@code k * pio2_1} branches of {@code __rem_pio2} for {@code n = k} or {@code -k}. */
    private static int fixedRound(double x, double[] y, int n) {
        int k = Math.abs(n);
        double z;
        if (n > 0) {
            z = x - k * PIO2_1;
            y[0] = z - k * PIO2_1T;
            y[1] = (z - y[0]) - k * PIO2_1T;
        } else {
            z = x + k * PIO2_1;
            y[0] = z + k * PIO2_1T;
            y[1] = (z - y[0]) + k * PIO2_1T;
        }
        return n;
    }
}
