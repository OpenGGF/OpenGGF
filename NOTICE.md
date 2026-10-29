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

## Bundled fonts: Fusion Pixel, Noto and GNU Unifont

Host interface text (`com.openggf.graphics.text.HostText`, drawn by `MenuPixelFont`) and
`com.openggf.mods.ui.NameFont` (player-chosen display names) use these bundled fonts in
`src/main/resources/fonts/`. None is modified: the BDFs are only gzip-compressed, the
Unifont file is the upstream `.hex.gz`, and the TrueType files are byte-for-byte copies.
`fonts/host-fonts.json` lists their fallback order.

- `fusion-pixel-10px-proportional-ja.bdf.gz`, `-zh_hans.bdf.gz` and `-zh_hant.bdf.gz`:
  Fusion Pixel Font 10px proportional with Japanese, Simplified Chinese and Traditional
  Chinese glyph forms, release 2026.10.16, from https://github.com/TakWolf/fusion-pixel-font
  (uncompressed BDF sha256 `29e86234c5c3f571a588a974868e72574400a936396f46e4ba69d0e33bb886cd`,
  `39503d0b7d5632342440186e9595bbef84ebdc49ae712669a0488b0a380bc577` and
  `94b4350dac442a05a5418961cb276c8bb09be36cd21da1d88df5612f6ba98878`).
  Copyright (c) 2022, TakWolf. It incorporates Ark Pixel Font (Copyright (c)
  2021, TakWolf), BoutiqueBitmap9x9 (Copyright 2026 fancy type foundry /
  Cen-cyun, Liu) and Galmuri (Copyright (c) 2019-2025 Lee Minseo). Licence:
  SIL Open Font License 1.1, with no Reserved Font Names; texts in
  `LICENSES/OFL-1.1-Fusion-Pixel.txt`, `LICENSES/OFL-1.1-Ark-Pixel.txt`,
  `LICENSES/OFL-1.1-BoutiqueBitmap9x9.txt` and `LICENSES/OFL-1.1-Galmuri.txt`.
- Noto Sans Arabic UI, Noto Sans Devanagari UI, Noto Sans Bengali UI and Noto Sans Thai,
  Regular and Bold hinted TrueType, from https://github.com/notofonts/notofonts.github.io
  at commit `c1d3718e2a0eb25c2567a694ee017061fdaa5f51` (`fonts/<family>/hinted/ttf/`).
  Copyright 2022 The Noto Project Authors (https://github.com/notofonts/arabic,
  `.../devanagari`, `.../bengali`, `.../thai`). Licence: SIL Open Font License 1.1; texts in
  `LICENSES/OFL-1.1-Noto-Sans-Arabic.txt`, `LICENSES/OFL-1.1-Noto-Sans-Devanagari.txt`,
  `LICENSES/OFL-1.1-Noto-Sans-Bengali.txt` and `LICENSES/OFL-1.1-Noto-Sans-Thai.txt`.
  sha256:
  `NotoSansArabicUI-Regular.ttf` `c56275c744ded6ff6df13de04963e6174632f0405a54a83f44d0fe5395f45ae6`,
  `NotoSansArabicUI-Bold.ttf` `ba511a9cf3712cc801203f5fcaf5b35221830f975ec0fc91678e9a4ed07a1f6a`,
  `NotoSansDevanagariUI-Regular.ttf` `43c9218d3b4fc2c19a40ed83b374dab9b212115e8daadd3a75bd3bec965bab6b`,
  `NotoSansDevanagariUI-Bold.ttf` `4c0e2fd89ba7af5ee31844dc1bd652aa6dc389819bc52f31a635164090c7568b`,
  `NotoSansBengaliUI-Regular.ttf` `ab2f54c0922024eb8813c3f33e335f137505861dba38d78195cf32b7c3ed8be2`,
  `NotoSansBengaliUI-Bold.ttf` `0d46caf69318377337ca4c4e92907af11ea4bdef5465c8d5cfe17f8771d824a7`,
  `NotoSansThai-Regular.ttf` `61cf814eec46b294d6ea4401ac295d0cecd5207bd2331dcc5a15e7301d30ee44`,
  `NotoSansThai-Bold.ttf` `2ac6c6e8a478e23b15f76e4894af1fa2210f8f350e4e6e54aad530bec03efbfb`.
- `unifont-18.0.01.hex.gz`: GNU Unifont 18.0.01, from https://unifoundry.com/unifont/
  (sha256 `e66385c79a0b8b24a466f3129930e08a966a935b4bf3b28c6bb17a9df9bf791d`).
  Originally by Roman Czyborra, maintained by Paul Hardy with many glyph
  contributors (upstream `README`). Licence: SIL Open Font License 1.1, or GNU GPL 2.0 or later with the GNU
  font embedding exception; the upstream `COPYING` is
  `LICENSES/Unifont-OFL-1.1-GPL-2.0-font-exception.txt`.

## FreeType and HarfBuzz

Host text rasterizes and shapes OpenType fonts with the FreeType 2.14.1 and HarfBuzz 12.3.2
libraries contained in the LWJGL 3.4.1 `lwjgl-freetype` native artifacts (HarfBuzz's API is
exported by that same library and bound through `lwjgl-harfbuzz`).

- FreeType: Portions of this software are copyright © 2025 The FreeType Project
  (www.freetype.org). All rights reserved. Used under the FreeType License; text in
  `LICENSES/FreeType-FTL.txt`.
- HarfBuzz: "Old MIT" licence with the copyright notices in `LICENSES/HarfBuzz-MIT.txt`.

Full contributor and third-party acknowledgements are in `CREDITS.md`.
