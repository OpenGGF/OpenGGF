# NOTICE

OpenGGF is distributed under the GNU General Public License, version 3
(`LICENSE`). This file lists the third-party components that carry their own
licence terms and notices, which must accompany every copy of OpenGGF,
including the executable JAR (`META-INF/openggf/`) and the release archives.

## Nuked OPN2

The FM sound core in `src/main/java/com/openggf/audio/synth/nuked/` is a Java
port of Nuked OPN2, a cycle-accurate Yamaha YM3438 / YM2612 emulator.

- Copyright (C) 2017-2022 Alexey Khokholov (Nuke.YKT)
- Upstream: https://github.com/nukeykt/Nuked-OPN2, commit
  `335747d78cb0abbc3b55b004e62dad9763140115` (`ym3438.c` 1.0.12,
  `ym3438.h` 1.0.9), pinned and hash-verified in
  `tools/audio/nuked-opn2/PIN.md`
- Licence: GNU Lesser General Public License, version 2.1 or (at your option)
  any later version (`LGPL-2.1-or-later`); the licence text is in
  `LICENSES/LGPL-2.1.txt`
- Modified 2026-08 by the OpenGGF contributors: translated from C to Java;
  chip type made per instance; state copy helpers and value equality added.
  Each modified file records this in its header (LGPL-2.1 section 2(b)).

The ported files remain licensed under LGPL-2.1-or-later and may be extracted
and reused under that licence on their own. OpenGGF as a combined work is
conveyed under GPL-3 by applying the upgrade option of LGPL-2.1 section 3 to
the combination; the LGPL headers and the upstream copyright notice are
retained in every ported file.

## blip_buf

The band-limited delta buffer in `src/main/java/com/openggf/audio/synth/blip/`
is a Java port of blip_buf 1.1.0 as modified for Genesis Plus GX. It is used
only by the opt-in native-compatible PCM output stage, a capture and diagnostic
configuration.

- Copyright (C) 2003-2009 Shay Green; modified for Genesis Plus GX by EkeEke
- Source: `core/sound/blip_buf.c` and `blip_buf.h` of the Genesis Plus GX tree
  in BizHawk commit `427556b5ef3ac437eba754d90c5e7e9096c9a8df` (sha256 recorded
  in the package documentation)
- Licence: GNU Lesser General Public License, version 2.1 or (at your option)
  any later version (`LGPL-2.1-or-later`); the licence text is in
  `LICENSES/LGPL-2.1.txt`
- Modified 2026-10 by the OpenGGF contributors: translated from C to Java;
  stereo configuration only; state copy helpers and value equality added.
  Each modified file records this in its header (LGPL-2.1 section 2(b)).

The ported files remain licensed under LGPL-2.1-or-later and may be extracted
and reused under that licence on their own. OpenGGF as a combined work is
conveyed under GPL-3 by applying the upgrade option of LGPL-2.1 section 3 to
the combination; the LGPL headers and the upstream copyright notice are
retained in every ported file.

## musl libm sin

`src/main/java/com/openggf/audio/synth/MuslSin.java` is a Java port of musl's
double-precision `sin` (`sin.c`, `__sin.c`, `__cos.c`, `__rem_pio2.c` without
its large-argument path). `tools/audio/native-pcm/musl_sin.c` reproduces the
`__sin` kernel branch for the vector generator. The BizHawk 2.11 Genesis Plus GX core links this
`sin` for its equaliser cutoffs. The file retains both notices:

- musl: Copyright © 2005-2020 Rich Felker, et al., MIT licence (text in the
  file header)
- origin FreeBSD msun: Copyright (C) 1993 by Sun Microsystems, Inc. All rights
  reserved. Developed at SunPro, a Sun Microsystems, Inc. business. Permission
  to use, copy, modify, and distribute this software is freely granted,
  provided that this notice is preserved. `__rem_pio2` optimized by
  Bruce D. Evans.

## 3 Band EQ

`NativeCompatiblePcmOutput.ThreeBandEq` ports `eq.c` from Genesis Plus GX:
"3 Band EQ", (c) Neil C / Etanza Systems 2K6, after Paul Kellet's filter. The
author placed it in the public domain.

Full contributor and third-party acknowledgements are in `CREDITS.md`.
