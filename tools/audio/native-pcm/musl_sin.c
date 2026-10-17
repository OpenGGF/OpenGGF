/* The `sin` the stock BizHawk 2.11 GPGX core links (musl 1.2.0, BizHawk waterbox/musl at 2063abc4), for the
 * native-compatible PCM vector generator's equaliser cutoffs. Host libms differ: glibc's sin moves the 880 Hz cutoff
 * by one ulp. For 2^-26 <= |x| ~< pi/4 musl's sin is exactly __sin(x, 0.0, 0) (src/math/sin.c); that branch is
 * reproduced here from src/math/__sin.c (origin FreeBSD msun k_sin.c) and any other argument aborts. build.sh renames
 * the equaliser object's `sin` reference to `musl_sin`. com.openggf.audio.synth.MuslSin ports the general function.
 *
 * ====================================================
 * Copyright (C) 1993 by Sun Microsystems, Inc. All rights reserved.
 *
 * Developed at SunPro, a Sun Microsystems, Inc. business.
 * Permission to use, copy, modify, and distribute this
 * software is freely granted, provided that this notice
 * is preserved.
 * ====================================================
 * musl: Copyright (c) 2005-2020 Rich Felker, et al.; MIT licence (see NOTICE.md, "musl libm sin").
 */
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static const double
S1 = -1.66666666666666324348e-01,
S2 = 8.33333333332248946124e-03,
S3 = -1.98412698298579493134e-04,
S4 = 2.75573137070700676789e-06,
S5 = -2.50507602534068634195e-08,
S6 = 1.58969099521155010221e-10;

double musl_sin(double x) {
  uint64_t bits;
  memcpy(&bits, &x, sizeof bits);
  uint32_t ix = (uint32_t) (bits >> 32) & 0x7fffffff;
  if (ix > 0x3fe921fb || ix < 0x3e500000) {
    fprintf(stderr, "musl_sin: argument outside the reproduced branch\n");
    abort();
  }
  double z = x * x, w = z * z;
  double r = S2 + z * (S3 + z * S4) + z * w * (S5 + z * S6);
  double v = z * x;
  return x + v * (S1 + z * r);
}
